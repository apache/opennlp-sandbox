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
- **opennlp-grpc-backend-tei** - optional remote embedding backend for HuggingFace Text
  Embeddings Inference (TEI) gRPC endpoints
- **opennlp-grpc-backend-openvino** - optional remote embedding backend for OpenVINO
  Model Server and other KServe v2 compatible inference servers
- **opennlp-grpc-integration-tests** - black-box integration tests that launch the
  shaded server jar as a separate process and exercise it over the network, including
  a remote TEI embedding backend
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

# Optional explicit model overrides. When omitted, the language detector and the
# en sentence-detector, tokenizer, POS tagger and lemmatizer load from the
# classpath via the opennlp-models-* runtime deps.
# model.language_detector.path=/path/to/langdetect.bin
# model.sentence_detector.path=/path/to/en-sent.bin
# model.tokenizer.path=/path/to/en-token.bin
# model.pos_tagger.path=/path/to/en-pos.bin
# model.lemmatizer.path=/path/to/en-lemmas.bin
```

By default no configuration is required: the server loads the bundled language
detector (103 languages) and the English sentence-detector, tokenizer, POS tagger
and lemmatizer models (Apache-distributed UD models). When running from the
executable jar, the models merged into the jar by the build are used directly; when
running from a regular classpath (e.g. via Maven), they are discovered from the
`opennlp-models-*` runtime dependencies.

> v1 note: this slice implements language detection (`PIPELINE_STEP_LANGUAGE_DETECT`,
> filling `detected_language` with an ISO 639-3 code plus `language_confidence`),
> sentence detection, tokenization, named entity recognition (`PIPELINE_STEP_NER`,
> filling `AnnotatedSentence.entities` when name finder models are configured),
> POS tagging (`PIPELINE_STEP_POS_TAG`, filling
> `Token.pos_tag` with UD tags), lemmatization (`PIPELINE_STEP_LEMMATIZE`, filling
> `Token.lemma`; requires the POS step), sentence-level embeddings, segmentation
> chunking (`sentence` and `token` algorithms via `chunk_embed_configs` or
> `PIPELINE_STEP_CHUNK`), probability reporting, `max_text_length`, offset encoding
> selection, and the default `en-basic` model bundle. Semantic chunking
> (`algorithm: semantic`) and CPU/GPU embeddings are supported when embedding models
> are configured. Classic syntactic `ChunkerME`, parsing, non-default bundles beyond
> `en-ner` when NER models are configured, and per-entry chunk profiles are rejected
> explicitly instead of being silently ignored.

### Name finder models (optional)

Name finder models are operator-supplied: unlike the sentence detector, tokenizer,
POS tagger, lemmatizer and language detector, Apache does not distribute NER models as
`opennlp-models-*` artifacts, so there is no default and NER is only available once you
configure model paths. Register classic OpenNLP `.bin` name finder models — one file per
entity type — in the server config. The middle segment of each key becomes the logical
entity type exposed to clients via `AnalysisProfile.ner_entity_types` and
`NamedEntity.entity_type`. Entity types are case-insensitive: keys are normalized to
lower case, and `ner_entity_types` filters are matched the same way (so `PERSON` and
`person` are equivalent):

```ini
# Classic maxent models from https://opennlp.apache.org/models.html
model.name_finder.person.path=/path/to/en-ner-person.bin
model.name_finder.organization.path=/path/to/en-ner-organization.bin
model.name_finder.location.path=/path/to/en-ner-location.bin
```

Request NER by adding `PIPELINE_STEP_NER` to the analysis profile (or use the
built-in `en-ner` profile / `en-ner` bundle when models are configured). Optionally
restrict which configured types run:

```protobuf
AnalysisProfile {
  profile_id: "en-ner"
  steps: [PIPELINE_STEP_SENTENCE_DETECT, PIPELINE_STEP_TOKENIZE, PIPELINE_STEP_NER]
  model_bundle { bundle_id: "en-ner" }
  ner_entity_types: ["person", "organization"]
}
```

`AnalysisOptions.clear_adaptive_data` (default `true`) controls whether the server
calls `NameFinderME.clearAdaptiveData()` after each request, matching the OpenNLP
manual's per-document reset semantics.

> Pair each name finder with a tokenizer trained for the same tokenization scheme.
> The bundled UD English tokenizer works with `opennlp-models` artifacts; legacy
> 1.5 news-domain NER models (`en-ner-person.bin`, etc.) were trained with Penn-style
> tokenization and may perform best with a matching tokenizer override.

ONNX-backed NER (`NameFinderDL`) is not configured through these keys yet.

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
setting `options.embedding_model_id` (or rely on `default_id` when only
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
is only valid with the `cuda` backend. Requires an NVIDIA CUDA runtime on the host.

Backend selection is purely a server deployment concern: clients request embeddings by
model id and never indicate a backend. The backend serving each model is reported to
clients through `ModelDescriptor.backend_id` in `GetAvailableModels`.

#### Remote backends: HuggingFace TEI (optional)

The `opennlp-grpc-backend-tei` module delegates embedding inference to
[HuggingFace Text Embeddings Inference](https://github.com/huggingface/text-embeddings-inference)
instances over their native gRPC API (`-grpc` flavored TEI Docker images). Tokenization,
truncation, pooling and normalization run inside TEI; the OpenNLP server keeps
orchestrating the document pipeline. One TEI instance serves one model, so each model id
maps to one endpoint. Put the module jar on the server classpath and configure:

```ini
model.embedder.backend=tei
model.embedder.minilm.tei.target=localhost:8080
model.embedder.minilm.tei.use_tls=false      # optional, default false
model.embedder.minilm.tei.truncate=true      # optional, default true
model.embedder.minilm.tei.normalize=true     # optional, default true
model.embedder.tei.deadline_ms=30000         # optional
```

Endpoints are validated at startup (TEI `Info` RPC plus one probe embedding that also
determines the vector dimension). Batches are fanned out as concurrent calls on the
multiplexed HTTP/2 connection; TEI applies its own server-side batching.

See [opennlp-grpc-backend-tei/README.md](opennlp-grpc-backend-tei/README.md) for the
full deployment guide: the TEI Docker image matrix (CPU and per-CUDA-architecture GPU
images), multi-model configuration, and the model/device combinations verified by the
live integration tests.

#### Remote backends: OpenVINO Model Server / KServe v2 (optional)

The `opennlp-grpc-backend-openvino` module delegates embedding inference to an
[OpenVINO Model Server](https://docs.openvino.ai/2026/model-server/ovms_what_is_openvino_model_server.html)
— or any KServe v2 compatible inference server (Triton, KServe, ...) — over the KServe
open inference protocol gRPC API. The served model or OVMS MediaPipe graph must accept a
`BYTES` string tensor and return `FP32` embeddings, i.e. tokenization runs server-side
(for OpenVINO, models converted with `openvino_tokenizers`):

```ini
model.embedder.backend=openvino
model.embedder.minilm.openvino.target=localhost:9000
model.embedder.minilm.openvino.model_name=all-MiniLM-L6-v2
model.embedder.minilm.openvino.model_version=1       # optional
model.embedder.minilm.openvino.input_name=texts      # optional with one input
model.embedder.minilm.openvino.output_name=embeddings # optional with one output
model.embedder.openvino.deadline_ms=30000            # optional
```

Model readiness and tensor metadata are validated at startup, and one probe inference
determines the embedding dimension. Batches are sent as a single `ModelInfer` call with
a leading batch dimension.

See [opennlp-grpc-backend-openvino/README.md](opennlp-grpc-backend-openvino/README.md)
for the full deployment guide, including the scripted model export that fuses the
HuggingFace tokenizer, transformer, mean pooling and L2 normalization into a single
string-input OpenVINO model, and the configuration verified by the live integration
tests.

#### Custom embedding backends (SPI)

Embedding backends are discovered through `java.util.ServiceLoader` — the TEI and
OpenVINO modules above are regular consumers of this SPI. To add another backend
(DJL, a custom native runtime, or any other remote inference service in any language),
ship a jar that implements
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
