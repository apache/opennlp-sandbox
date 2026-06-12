# OpenNLP gRPC Backend: OpenVINO Model Server / KServe v2

Remote embedding backend for the OpenNLP gRPC server, delegating inference to
[OpenVINO Model Server](https://docs.openvino.ai/2026/model-server/ovms_what_is_openvino_model_server.html)
(OVMS) — or any other inference server implementing the KServe v2 "open inference
protocol" gRPC API (`inference.GRPCInferenceService`), such as NVIDIA Triton.

```
OpenNLP gRPC client --> opennlp-grpc-server --> OVMS (Docker, CPU or Intel GPU)
```

The backend is discovered via `ServiceLoader`: drop this module's jar on the server
classpath and select it with `model.embedder.backend=openvino`.

## 1. The served model must map text to vectors

Unlike TEI, OVMS serves raw tensor models — it has no built-in tokenizer. This backend
deliberately keeps tokenization server-side, so the served model (or OVMS MediaPipe
graph) must accept a `BYTES` string tensor and return an `FP32` embedding matrix. The
provider enforces this against the model metadata at startup.

For OpenVINO this is a solved problem:
[openvino_tokenizers](https://github.com/openvinotoolkit/openvino_tokenizers) converts
any HuggingFace tokenizer into OpenVINO graph operations that can be fused with the
transformer into a single model. The helper script in
`opennlp-grpc-integration-tests/scripts/` automates the full export — tokenizer +
encoder + attention-mask-aware mean pooling + L2 normalization, fused into one graph
with a string input and a `[batch, dim]` float output:

```bash
cd opennlp-grpc-integration-tests
./scripts/ovms-server.sh prepare --model sentence-transformers/all-MiniLM-L6-v2
./scripts/ovms-server.sh start --cpu        # or --gpu on Intel GPU hosts
```

`prepare` writes the OVMS model layout to `~/.cache/opennlp-grpc-it-ovms-models/embedder/1/`
(exported with `optimum-intel` and `openvino_tokenizers` via `uv`); `start` serves it
with the stock `openvino/model_server` image, which bundles the tokenizer operations.
OVMS runs on CPU (x86_64) everywhere; `--gpu` targets Intel GPUs (iGPU, Arc, Flex)
through `/dev/dri` passthrough — there is no CUDA variant of OVMS.

## 2. Configure the OpenNLP gRPC server

Each registered model id maps to one served model:

```properties
model.embedder.backend=openvino

# Register model id "minilm" against a served model (both required)
model.embedder.minilm.openvino.target=localhost:19000
model.embedder.minilm.openvino.model_name=embedder

# Optional:
model.embedder.minilm.openvino.model_version=1     # default: latest
model.embedder.minilm.openvino.input_name=...      # required only with multiple inputs
model.embedder.minilm.openvino.output_name=...     # required only with multiple outputs
model.embedder.minilm.openvino.use_tls=false       # default false
model.embedder.openvino.deadline_ms=30000          # per-call deadline
model.embedder.default_id=minilm                   # required with multiple models
```

Run the server with this backend on the classpath:

```bash
java -cp opennlp-grpc-server-<version>.jar:opennlp-grpc-backend-openvino-<version>.jar \
  org.apache.opennlp.grpc.server.OpenNlpGrpcServer -p 7071 -c server.properties
```

All endpoints are validated eagerly at startup: `ModelReady` is checked, the metadata
must expose a `BYTES` input and an `FP32` output (input/output names are auto-resolved
when unambiguous), and one probe inference determines the embedding dimension, which is
then published in the model catalog (`ListModelBundles`, with `backend_id: "openvino"`).
Misconfiguration fails the server start, not the first request.

Batches are sent as a single `ModelInfer` call with a leading batch dimension — the
native KServe batching model. Both `raw_output_contents` (little-endian) and typed
`InferTensorContents` responses are supported.

## 3. Verified configuration

The live integration tests (`opennlp-grpc-integration-tests`, see its README) have been
run against a real OVMS (`openvino/model_server:latest`) serving the fused export of
`sentence-transformers/all-MiniLM-L6-v2` (384 dimensions) on CPU, asserting unit-norm
vectors and topical similarity ordering end to end:

```bash
OPENNLP_OVMS_TARGET=localhost:19000 mvn -pl opennlp-grpc/opennlp-grpc-integration-tests verify
```
