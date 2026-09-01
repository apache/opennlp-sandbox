# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the specific
# language governing permissions and limitations under the License.

"""Exports a HuggingFace sentence embedding model as one OpenVINO model that maps
raw text strings directly to L2-normalized sentence embeddings.

The exported graph fuses three stages so that OpenVINO Model Server can serve it
through the KServe v2 gRPC API with a BYTES input and an FP32 output, exactly as the
opennlp-grpc-backend-openvino provider requires:

  1. the HuggingFace tokenizer, converted with openvino_tokenizers,
  2. the transformer encoder, exported with optimum-intel,
  3. attention-mask-aware mean pooling plus L2 normalization.

Usage (normally invoked by ovms-server.sh):
  python prepare_ovms_embedder.py --model-id sentence-transformers/all-MiniLM-L6-v2 \
      --output /path/to/models/embedder/1
"""

import argparse
from pathlib import Path

import numpy as np
import openvino as ov

try:
    from openvino import opset13 as ops
except ImportError:  # older openvino releases expose opsets under openvino.runtime
    from openvino.runtime import opset13 as ops

from openvino_tokenizers import connect_models, convert_tokenizer
from optimum.intel.openvino import OVModelForFeatureExtraction
from transformers import AutoTokenizer


def add_mean_pooling(encoder: ov.Model) -> ov.Model:
    """Appends masked mean pooling and L2 normalization to a transformer encoder."""
    attention_mask = None
    for parameter in encoder.get_parameters():
        if "attention_mask" in parameter.output(0).get_names():
            attention_mask = parameter.output(0)
    if attention_mask is None:
        raise ValueError("encoder has no 'attention_mask' input")

    # The producer feeding the Result node, not the Result itself: the new model
    # excludes the old Result, and ops sourced from it would not serialize.
    hidden_states = encoder.output("last_hidden_state").get_node().input_value(0)
    mask = ops.convert(attention_mask, "f32")
    mask = ops.unsqueeze(mask, 2)
    summed = ops.reduce_sum(ops.multiply(hidden_states, mask), [1])
    counts = ops.maximum(ops.reduce_sum(mask, [1]), ops.constant(np.float32(1e-9)))
    mean = ops.divide(summed, counts)
    normalized = ops.normalize_l2(mean, [1], 1e-12, "add")

    pooled = ov.Model([normalized.output(0)], encoder.get_parameters(), "sentence_embedder")
    pooled.outputs[0].get_tensor().set_names({"sentence_embedding"})
    return pooled


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-id", required=True, help="HuggingFace model id")
    parser.add_argument("--output", required=True, type=Path,
                        help="Version directory to write model.xml/model.bin into")
    args = parser.parse_args()

    print(f"Exporting transformer '{args.model_id}' with optimum-intel ...")
    encoder = OVModelForFeatureExtraction.from_pretrained(args.model_id, export=True).model

    print("Appending mean pooling and L2 normalization ...")
    pooled = add_mean_pooling(encoder)

    print("Converting tokenizer with openvino_tokenizers and fusing ...")
    tokenizer = convert_tokenizer(AutoTokenizer.from_pretrained(args.model_id))
    # Clone to detach from the encoder graph, which still owns the original results;
    # serializing the fused model fails on the shared nodes otherwise.
    combined = connect_models(tokenizer, pooled).clone()

    args.output.mkdir(parents=True, exist_ok=True)
    target = args.output / "model.xml"
    ov.save_model(combined, target, compress_to_fp16=False)

    inputs = [(i.get_any_name(), str(i.get_element_type())) for i in combined.inputs]
    outputs = [(o.get_any_name(), str(o.get_element_type())) for o in combined.outputs]
    print(f"Saved {target}")
    print(f"  inputs:  {inputs}")
    print(f"  outputs: {outputs}")


if __name__ == "__main__":
    main()
