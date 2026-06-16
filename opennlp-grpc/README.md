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
- **examples** - v1 client stub-generation scaffolding (Python sample TBD)

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
> filling `AnnotatedSentence.entities`), POS tagging (`PIPELINE_STEP_POS_TAG`,
> filling `Token.pos_tag` with the model's native tagset), lemmatization
> (`PIPELINE_STEP_LEMMATIZE`, filling `Token.lemma`; requires POS), document
> categorization (`PIPELINE_STEP_DOC_CATEGORIZE`, filling
> `OpenNlpDocument.classification`), per-sentence sentiment
> (`PIPELINE_STEP_SENTIMENT`, filling `AnnotatedSentence.sentiment_label` /
> `sentiment_confidence`), constituency parsing (`PIPELINE_STEP_PARSE`, filling
> structured and/or bracketed parse views), classic shallow chunking
> (`PIPELINE_STEP_SYNTACTIC_CHUNK`, filling `AnnotatedSentence.syntactic_chunks`),
> sentence and document embeddings (`PIPELINE_STEP_EMBED`), segmentation chunking
> (`sentence`, `token`, and `semantic` algorithms via `chunk_embed_configs` or
> `PIPELINE_STEP_CHUNK`), category-driven chunking via `category_chunk_configs`,
> probability reporting, `max_text_length`, offset encoding selection, parse format
> selection, and capability discovery through `GetServiceInfo` / `ListModelBundles`.
> The default `en-basic` profile/bundle is always present; optional `en-ner`,
> `en-doccat`, `en-sentiment`, `en-parse`, and `en-chunk` profiles/bundles are
> advertised only when their operator-supplied models are configured. NER, syntactic
> chunking, and parsing support multi-provider engine policy; embeddings support
> ONNX CPU/CUDA plus optional TEI and OpenVINO/KServe backends through SPI modules.
> `DocumentAnalytics`, `ModelDescriptor.hash`, `ModelBundleRef.component_models`,
> `AnalysisProfile.pos_tag_format` (UD/Penn conversion), per-entry chunk profiles,
> and `ChunkingSpec.clean_text` / `preserve_urls` are implemented on the v1 contract.
> `POS_TAG_FORMAT_CUSTOM` remains unsupported.

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

#### ONNX name finder models (optional)

Transformer NER models exported to ONNX are registered under a separate namespace.
Each model needs the ONNX file, its wordpiece vocabulary, and a labels file (one BIO
label per line, line number = output index):

```ini
model.name_finder_dl.bert_ner.path=/path/to/ner.onnx
model.name_finder_dl.bert_ner.vocab=/path/to/vocab.txt
model.name_finder_dl.bert_ner.labels=/path/to/labels.txt
# Optional:
model.name_finder_dl.bert_ner.backend=onnx          # onnx (default, CPU) | cuda
model.name_finder_dl.bert_ner.gpu_device_id=0       # only with backend=cuda
```

The `<id>` segment (`bert_ner` above) is an arbitrary model name. The entity types it
produces are derived from the BIO labels file (`B-PER`/`I-PER` → `per`, `B-LOC` → `loc`,
etc.), so one ONNX model serves every type it was trained for. These models are served by
`opennlp-dl`'s `NameFinderDL` and reported in the catalog with `backend_id` `onnx` or `cuda`.
They participate in NER exactly like classic models — a client requests `PIPELINE_STEP_NER`
and filters by `ner_entity_types`; the server runs each configured model once, attaches each
entity under the model's own label, and merges the results.

> Requires `opennlp-dl` (OpenNLP 3.0.0) with the thread-safe, multi-type `NameFinderDL`.
> CUDA requires an NVIDIA runtime and the GPU build flavor.

An opt-in end-to-end test exercises this backend against a real model. The `dl-ner` build
profile downloads the ONNX export of `dslim/bert-base-NER` (MIT) from HuggingFace into
`target/` at build time and runs `BasicDocumentAnalyzerDlNerTest`:

```bash
mvn -pl opennlp-grpc/opennlp-grpc-service -Pdl-ner test -Dtest=BasicDocumentAnalyzerDlNerTest
```

The model is fetched at build time only — it is never bundled into a built artifact and is
not redistributed. Without the profile the test skips (no model present).

#### Custom NER backends (SPI)

Name finder backends are discovered through `java.util.ServiceLoader`, mirroring the
embedding SPI — the built-in classic (`opennlp-me`) and ONNX (`onnx`/`cuda`) backends are
themselves regular consumers of it. To add another backend (a remote NER service, a custom
model format, any inference runtime in any language), ship a jar that implements
`org.apache.opennlp.grpc.model.NerBackendFactory`, registers it in
`META-INF/services/org.apache.opennlp.grpc.model.NerBackendFactory`, and put that jar on the
server classpath. Each factory parses its own configuration namespace and returns
`NerModel` recognizers; the `NameFinderRegistry` aggregates the models from every backend, so
several backends are active at once. A backend that needs the server's sentence detector
obtains it from the supplied `NerBackendContext`. The new backend's entity types then
participate in NER exactly like the built-ins — no change to the server.

### Document categorization models (optional)

Whole-document classifiers (topic, language register, intent, …) populate the document-level
`classification` field (`best_category` plus a `category_scores` map) when a request runs
`PIPELINE_STEP_DOC_CATEGORIZE`. Register classic OpenNLP maxent categorizers per model id:

```ini
model.doccat.topic.path=/path/to/en-doccat-topic.bin
# When several categorizers are configured, pick the one DOC_CATEGORIZE runs:
model.doccat.default_id=topic
```

Each `<id>` is an arbitrary model name; the categories come from the model itself. A single
configured model is used automatically — `default_id` is only required to disambiguate when
more than one is registered. Categorization is document-level, so it runs the selected model
once over the document's tokens and stores one `DocumentClassification`.

#### ONNX document categorizer models (optional)

Transformer classifiers exported to ONNX are registered under a separate namespace. Each model
needs the ONNX file, its wordpiece vocabulary, and a categories file (one category per line,
line number = output index):

```ini
model.doccat_dl.sentiment.path=/path/to/doccat.onnx
model.doccat_dl.sentiment.vocab=/path/to/vocab.txt
model.doccat_dl.sentiment.categories=/path/to/categories.txt
# Optional:
model.doccat_dl.sentiment.backend=onnx          # onnx (default, CPU) | cuda
model.doccat_dl.sentiment.gpu_device_id=0       # only with backend=cuda
```

These are served by `opennlp-dl`'s `DocumentCategorizerDL`, which splits and re-tokenizes the
raw document text internally, and are reported in the catalog with `backend_id` `onnx` or
`cuda`. They participate in `DOC_CATEGORIZE` exactly like classic models — except that, because
they consume the raw text, they need no upstream `TOKENIZE` and run under a `DOC_CATEGORIZE`-only
profile (classic maxent categorizers still require `TOKENIZE`).

> Requires `opennlp-dl` (OpenNLP 3.0.0). CUDA requires an NVIDIA runtime and the GPU build
> flavor.

#### Custom doc categorizer backends (SPI)

Document categorization backends are discovered through `java.util.ServiceLoader`, mirroring
the NER and embedding SPIs — the built-in classic (`opennlp-me`) and ONNX (`onnx`/`cuda`)
backends are themselves regular consumers of it. To add another backend (a remote classifier,
a custom model format, any runtime in any language), ship a jar that implements
`org.apache.opennlp.grpc.model.DocCategorizerBackendFactory`, registers it in
`META-INF/services/org.apache.opennlp.grpc.model.DocCategorizerBackendFactory`, and put that
jar on the server classpath. Each factory parses its own configuration namespace and returns
`DocCategorizerModel`s; the `DocCategorizerRegistry` aggregates the models from every backend.
The new backend's models then participate in `DOC_CATEGORIZE` exactly like the built-ins — no
change to the server.

### Sentiment models (optional)

Sentiment is document categorization applied **per sentence**: when a request runs
`PIPELINE_STEP_SENTIMENT`, the selected model classifies each sentence and the winning label and
its score populate that sentence's `sentiment_label` and `sentiment_confidence`. Because it is
doccat under the hood, it reuses the same backends, just under a dedicated `model.sentiment.*`
namespace so its models stay separate from the document-level categorizers:

```ini
model.sentiment.polarity.path=/path/to/en-sentiment-polarity.bin
# When several sentiment models are configured, pick the one SENTIMENT runs:
model.sentiment.default_id=polarity
```

The model's categories are its sentiment classes (e.g. `positive`/`negative`, or a finer scale);
the labels come from the model itself. A single configured model is used automatically —
`default_id` only disambiguates when more than one is registered.

ONNX transformer sentiment models register under `model.sentiment_dl.*`, with the same keys as
the ONNX doc categorizer (`path`, `vocab`, `categories`, optional `backend`/`gpu_device_id`):

```ini
model.sentiment_dl.bert_sst.path=/path/to/sentiment.onnx
model.sentiment_dl.bert_sst.vocab=/path/to/vocab.txt
model.sentiment_dl.bert_sst.categories=/path/to/categories.txt
# Optional:
model.sentiment_dl.bert_sst.backend=onnx          # onnx (default, CPU) | cuda
model.sentiment_dl.bert_sst.gpu_device_id=0       # only with backend=cuda
```

The `en-sentiment` profile/bundle (sentence detect + tokenize + sentiment) is advertised only
when at least one sentiment model is configured. Custom backends need nothing new: the same
`DocCategorizerBackendFactory` SPI serves both capabilities, so a backend written for doc
categorization is automatically available for sentiment — configure its models under the
`model.sentiment.*` namespace instead of `model.doccat.*`.

### Constituency parsing (optional)

A constituency (phrase-structure) parser builds a full parse tree per sentence when a request
runs `PIPELINE_STEP_PARSE`. The parser model is large and operator-supplied (not bundled), so it
is loaded only when configured:

```ini
model.parser.path=/path/to/en-parser-chunking.bin
```

When configured, the server advertises the `en-parse` profile/bundle (sentence detect + tokenize
+ parse). The result is written to `AnnotatedSentence.parse_tree`, which carries two independent
views of the same parse so each client takes whichever fits its language and use:

- **Structured** (`ParseTree.root`): a nested `ParseNode` tree. Each node has a `kind`
  (`NONTERMINAL` phrase or `TERMINAL` token), a `label` (phrase tag like `S`/`NP`/`VP`, or a POS
  tag at terminals), a document `span`, and a `probability`. Terminals also carry `token_index`,
  linking back to the sentence's token list instead of repeating token text.
- **Bracketed** (`ParseTree.penn_treebank`): the standard Penn-Treebank-style string, e.g.
  `(TOP (S (NP (DT The)(NN dog))(VP (VBD barked))))` — the universal interchange/debug form.

Choose the representation(s) per request with `AnalysisOptions.parse_formats`
(`PARSE_FORMAT_STRUCTURED`, `PARSE_FORMAT_BRACKETED`); an empty list defaults to both, and
listing fewer trims the response. Parsing consumes tokens, so a parse profile runs sentence
detection and tokenization first.

> The classic ME parser is read-only at inference, so one instance is shared across requests.

### Shallow (syntactic) chunking (optional)

A `ChunkerME` model groups each sentence's tokens into base phrases (`NP`, `VP`, `PP`, …) when a
request runs `PIPELINE_STEP_SYNTACTIC_CHUNK`, filling `AnnotatedSentence.syntactic_chunks`. This
is shallow parsing — distinct from `PIPELINE_STEP_CHUNK`, which is segmentation chunking for
embedding. The model is operator-supplied (not bundled):

```ini
model.chunker.path=/path/to/en-chunker.bin
```

When configured, the server advertises the `en-chunk` profile/bundle (sentence detect + tokenize
+ POS tag + syntactic chunk). The chunker classifies the token **and POS-tag** sequence, so
`SYNTACTIC_CHUNK` requires `POS_TAG` (and thus `TOKENIZE`); requesting it without `POS_TAG` fails
with `FAILED_PRECONDITION`, and requesting it with no chunker configured fails with `NOT_FOUND`.
Each chunk carries its document span and the chunker's phrase tag (`chunk_tag`).

> `ChunkerME` is thread-safe (per-thread state), so one instance is shared across requests.

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

Send `raw_text` plus a named or inline `AnalysisProfile`, receive an enriched
`OpenNlpDocument` with the annotations selected by the profile: sentences, tokens,
entities, POS tags, lemmas, document classification, per-sentence sentiment, parse trees,
syntactic chunks, embeddings, and chunk/embedding groups. `AnalyzeDocumentResponse`
also includes per-step diagnostics; invalid requests fail with precise gRPC status codes
instead of returning partial results.
