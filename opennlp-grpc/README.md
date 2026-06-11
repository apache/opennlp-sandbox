<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# OpenNLP gRPC (sandbox)

Document-centric gRPC API for Apache OpenNLP inference. Design RFC: [docs/rfc/opennlp-grpc-design.md](docs/rfc/opennlp-grpc-design.md).

## Modules

- **opennlp-grpc-api** - v1 protos (`org.apache.opennlp.grpc.v1`) and generated stubs
- **opennlp-grpc-service** - `OpenNlpGrpcServer` and `OpenNlpAnalysisService` implementation
- **examples** - client samples (v1 Python client TBD)

## Build

```bash
mvn clean install
```

Requires JDK 21+.

## Run the server

```bash
java -jar opennlp-grpc-service/target/opennlp-grpc-server-3.0.0-SNAPSHOT.jar
```

Options:

- `-p, --port` - listen port (default `7071`)
- `-c, --config` - key=value config file

Example config (`key=value`, `#` comments):

```ini
server.enable_reflection=false
server.max_inbound_message_size=10485760

# Optional explicit model overrides. When omitted, the en sentence-detector and
# tokenizer load from the classpath via the opennlp-models-* runtime deps.
# model.sentence_detector.path=/path/to/en-sent.bin
# model.tokenizer.path=/path/to/en-token.bin
```

By default no configuration is required: the server loads the bundled English
sentence-detector and tokenizer from the classpath.

> v1 note: this minimal slice implements sentence detection, tokenization,
> sentence-level embeddings (when ONNX models are configured), segmentation chunking
> (`sentence` and `token` algorithms via `chunk_embed_configs` or `PIPELINE_STEP_CHUNK`),
> probability reporting, `max_text_length`, offset encoding selection, and the default
> `en-basic` model bundle. Semantic chunking (`algorithm: semantic`), CPU/GPU ONNX
> embeddings, and segmentation chunking are supported when models are configured.
> OpenVINO, classic syntactic `ChunkerME`, non-default bundles, and per-entry chunk
> profiles are rejected explicitly instead of being silently ignored.

### Embedding models (optional)

Register ONNX sentence-transformer models in the server config:

```ini
model.embedder.default_id=sentence-transformers
model.embedder.sentence-transformers.onnx.path=/path/to/model.onnx
model.embedder.sentence-transformers.vocab.path=/path/to/vocab.txt
# Optional, with these defaults:
model.embedder.sentence-transformers.lowercase=true
model.embedder.sentence-transformers.pooling=mean
```

Request embeddings by adding `PIPELINE_STEP_EMBED` to the analysis profile and
setting `options.onnx_embedding_model_id` (or rely on `default_id` when only
one model is registered). Uses ONNX Runtime via `opennlp-dl` on CPU by default.

The input text is normalized with the full BERT basic tokenization (control
character cleanup, CJK isolation, punctuation splitting and - for uncased
models - lower casing with accent stripping) before wordpiece encoding.
`lowercase` is a property of the model: uncased models such as the
`sentence-transformers` family require `true`, cased models require `false`.
`pooling` selects how token states become one sentence vector: `mean`
(masked mean + L2 normalization, the sentence-transformers convention) or
`cls` (raw classification-token state). With the defaults, embeddings are
numerically equivalent to the Python `sentence-transformers` output for the
same model.

#### GPU embeddings (optional)

Build with the GPU flavor, which replaces the `onnxruntime` jar with
`onnxruntime_gpu` (exactly one of the two is ever on the classpath), and point
the server at CUDA:

```bash
mvn -pl opennlp-grpc/opennlp-grpc-service -Dgpu package
```

```ini
model.embedder.backend=cuda
model.embedder.gpu_device_id=0
model.embedder.default_id=sentence-transformers
model.embedder.sentence-transformers.onnx.path=/path/to/model.onnx
model.embedder.sentence-transformers.vocab.path=/path/to/vocab.txt
```

`model.embedder.backend` accepts `onnx` (default, CPU) or `cuda`; any other value
is rejected at startup with the list of registered backends. `model.embedder.gpu_device_id`
is only valid with the `cuda` backend. Clients should set `inference_backend` to
`INFERENCE_BACKEND_CUDA` (or legacy `INFERENCE_BACKEND_ONNX_RUNTIME_GPU`) when requesting
embeddings or chunk embeddings. Requires an NVIDIA CUDA runtime on the host.

#### Custom embedding backends (SPI)

Embedding backends are discovered through `java.util.ServiceLoader`. To add a backend
(for example OpenVINO or a remote inference endpoint), ship a jar that implements
`org.apache.opennlp.grpc.embedding.EmbeddingBackendFactory`, registers it in
`META-INF/services/org.apache.opennlp.grpc.embedding.EmbeddingBackendFactory`, and put
that jar on the server classpath. The backend then becomes selectable via
`model.embedder.backend=<your-backend-id>` without any change to the server.

### Chunk + embed configs

Request one or more chunking strategies with per-chunk embeddings:

```json
{
  "chunk_embed_configs": [
    {
      "config_id": "sentence-chunks",
      "chunking": { "algorithm": "sentence" },
      "embedding_model_ids": ["sentence-transformers"]
    },
    {
      "config_id": "token-chunks",
      "chunking": { "algorithm": "token", "chunk_size": 128, "chunk_overlap": 16 },
      "embedding_model_ids": ["sentence-transformers"]
    }
  ]
}
```

The server auto-runs sentence detection (and tokenization for `token` windows) once,
then returns each strategy as a `chunk_embedding_groups` entry with embeddings
attached inside each chunk.

#### Semantic chunking

Topic-boundary chunking compares consecutive sentence embeddings and splits when
cosine similarity drops below `semantic_config.similarity_threshold` (default `0.5`)
or below the configured `percentile_threshold`. Example:

```json
{
  "config_id": "semantic-topics",
  "chunking": {
    "algorithm": "semantic",
    "semantic_config": {
      "similarity_threshold": 0.75,
      "min_chunk_sentences": 1,
      "max_chunk_sentences": 8,
      "semantic_embedding_model_id": "sentence-transformers"
    }
  },
  "embedding_model_ids": ["sentence-transformers"]
}
```

## v1 API

Primary RPC: `org.apache.opennlp.grpc.v1.OpenNlpAnalysisService/AnalyzeDocument`

Send `raw_text`, receive an enriched `OpenNlpDocument` (sentences, tokens, diagnostics; chunk/embed groups as implemented).
