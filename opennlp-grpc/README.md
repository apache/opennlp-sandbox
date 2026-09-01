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

New here? [QUICKSTART.md](QUICKSTART.md) goes from a clean checkout to
analyzing, training, and searching in a few minutes, in the browser and from
Python, Node.js, Java, or Go.

## The document shape

Every `AnalyzeDocument` response also renders its results as the OpenNLP 3.0
document shape (OPENNLP-1888): `OpenNlpDocument.layers` carries named, typed
annotation layers over `raw_text`, one layer per pipeline step that ran. Layer
ids are namespaced exactly as in the Java container (`opennlp:sentences`,
`opennlp:tokens`, `opennlp:pos`, `opennlp:lemmas`, `opennlp:entities`,
`opennlp:chunks`, `opennlp:parses`, `opennlp:sentiment`, `opennlp:language`,
`opennlp:categories`, `opennlp:embeddings`, `opennlp:word-types`,
`opennlp:stopwords`, `opennlp:terms:<DIMENSION>`, `opennlp:subwords`,
`opennlp:stems`, `opennlp:expansions`, `opennlp:geo`, `opennlp:normalization`,
`opennlp:analytics`, `opennlp:chunk-groups`, `opennlp:term-vectors`), and every annotation value is strongly
typed through the layer's `oneof` value list. First-class payloads retain their complete
types and provenance: entities are `NamedEntity`, syntactic chunks are `ChunkSpan`, UAX
29 classes use `DocumentWordType`, stems carry `StemmerAlgorithm`, lexical expansions
carry `LexicalExpansionKind`, and strategy chunks retain their embeddings and centroids.
Aggregate term vectors retain their mode, source-layer identity, frequency, and optional
original-text occurrence spans.
Each layer also carries a `LayerIdentity` oneof. OpenNLP-owned layers use the closed
`StandardLayer` enum, while extension layers use the open namespaced `custom` string.
Layer families can add a qualifier, so `opennlp:terms:FULL_CASE_FOLD` is represented as
`STANDARD_LAYER_TERMS` plus `FULL_CASE_FOLD`. The original `id` remains the stable lookup
key and preserves compatibility with clients that predate typed identities.
`GetServiceInfo.supported_layers` advertises the complete closed set.
Layer spans use the response's offset encoding like every other span. Server-side, layers
are built through `opennlp.tools.document.Document` itself and the completed shape is
validated before serialization, so container, scope, span, probability, and vector
invariants apply to what goes on the wire. The full inventory is documented in
`opennlp_document.proto`.

Capability discovery separates implementation support from configured readiness.
`GetServiceInfo.supported_steps` and `supported_layers` describe the binary's protocol
surface. It also reports the packaged gRPC service version separately from the wrapped
OpenNLP library version, so operators can identify both sides of a deployed combination.
`ListModelBundles` reports loaded model artifacts and embedding routes, while
`GetServiceInfo.configured_resources` reports loaded non-model resources. Each resource
uses a `ResourceIdentity` oneof with a closed `StandardResource` enum or an open custom
type id, plus its selectable resource id and whether it is the default. The standard
resource families are SentencePiece models, hunspell dictionaries, WordNet lexicons,
and lattice dictionaries. A missing entry means the optional resource was not loaded;
clients can discover that before submitting a profile that selects it.
`GetServiceInfo.max_text_bytes` reports the operator's UTF-8 byte limit. It applies to unary
and streaming analysis plus direct embedding even when a request omits
`AnalysisOptions.max_text_length`. The two limits use different units: `max_text_bytes`
counts UTF-8 bytes while `AnalysisOptions.max_text_length` counts Java UTF-16 code units.
A request-level value can impose a smaller analysis
limit but cannot raise the operator limit. The server keeps the inbound gRPC message cap
at least 1 MiB above this text limit so protobuf envelope data cannot make the advertised
text capacity unreachable. A lower `server.max_inbound_message_size` setting is raised to
that floor at startup.

Beyond the classic pipeline, the service serves the OpenNLP 3.0 feature branches:
SentencePiece subword encoding (`PIPELINE_STEP_SUBWORD_TOKENIZE`, models under
`model.subword.<id>.path`), stemming across snowball, porter, the UniNE light and
minimal tiers, and hunspell affix dictionaries (`PIPELINE_STEP_STEM`,
`model.hunspell.<id>.affix_path`/`.dictionary_path`), lexical expansion over WN-LMF
knowledge bases (`PIPELINE_STEP_EXPAND`, `model.wordnet.<id>.path`), CJK lattice
tokenization over MeCab-format dictionaries
(`tokenizer.standard = STANDARD_TOKENIZER_ENGINE_LATTICE`,
`model.lattice.<id>.dir`), and geocoding of location entities against the bundled
Natural Earth gazetteer (`PIPELINE_STEP_GEOCODE`, no configuration required, filling
`NamedEntity.geo`), and aggregate term vectors from PR #1212
(`PIPELINE_STEP_TERM_VECTOR`). Dependency parsing and rule-based relation extraction expose
the helper stack's typed `opennlp:dependencies` and `opennlp:relations` layers when an
operator supplies a dependency model.

## Modules

- **opennlp-grpc-api** - v1 analysis, document-shape, and immutable-search protos
  (`org.apache.opennlp.grpc.v1`) plus generated stubs
- **opennlp-grpc-spi** - the ServiceLoader contracts (`org.apache.opennlp.grpc.spi.*`) and
  carrier types that add-on backends compile against; deliberately small
- **opennlp-grpc-service** - `OpenNlpGrpcServer`, analysis, search, and vocabulary services,
  and the registries that discover SPI backends; its slim `opennlp-grpc-server` jar carries
  no native inference runtime and no quantized search provider
- **opennlp-grpc-backend-tei** - optional remote embedding backend for HuggingFace Text
  Embeddings Inference (TEI) gRPC endpoints
- **opennlp-grpc-backend-openvino** - optional remote embedding backend for OpenVINO
  Model Server and other KServe v2 compatible inference servers
- **opennlp-grpc-backend-static** - optional in-process embedding backend serving static
  (non-contextual) embedding tables through the `opennlp-embeddings` extension module
- **opennlp-grpc-dl** - optional ONNX Runtime inference add-on: transformer sentence
  embeddings (CPU and CUDA engines), the ONNX name finder, and the ONNX document
  categorizer; built in the cpu (default) or gpu (`-Dgpu`) flavor
- **opennlp-grpc-installer** - optional model download add-on: the built-in installable
  model catalog (metadata only) contributed through the catalog SPI, and the standalone
  `install-resource` CLI; without it the server serves an empty catalog and refuses
  installs honestly
- **opennlp-grpc-search-turboquant** - optional quantized vector search add-on: the
  TurboQuant provider (live workspaces, persistence, exhaustive immutable bundles) and
  the offline bundle builder CLI; without it the flat-float and terms providers still
  serve dynamic search
- **opennlp-grpc-store-s3** - optional S3 vocabulary artifact store, discovered by the
  `s3` scheme of `vocabulary.artifact_root`; its `-all` jar bundles the AWS SDK for
  single-file classpath drop-in (not part of `opennlp-grpc-server-all`)
- **opennlp-grpc-search-lucene** - optional Lucene keyword component (`lucene`
  provider): BM25-scored term and phrase execution for compound queries; its `-all`
  jar bundles Lucene for single-file classpath drop-in (not part of
  `opennlp-grpc-server-all`)
- **opennlp-grpc-formats** - optional hand-written document output formats (CoNLL-U,
  RFC 4180 CSV, Markdown report, WARC) contributed through the format SPI; no
  third-party dependency, bundled in `opennlp-grpc-server-all`
- **opennlp-grpc-sink-grpc** - optional gRPC document sink: streams every analyzed
  document (optionally with a rendering) to a downstream receiver implementing the
  `OpenNlpDocumentSinkService` contract in any protobuf language; bundled in
  `opennlp-grpc-server-all`
- **opennlp-grpc-webapp-api** - typed ServiceLoader API for static browser interface extensions
- **opennlp-grpc-webapp-default** - default TypeScript analysis and semantic-search workbench
- **opennlp-grpc-webapp** - optional standalone HTTP host and protobuf JSON gateway
- **opennlp-grpc-distr** - everything-in-one `opennlp-grpc-server-all` jar (the slim server
  plus every in-tree add-on) used by the docker images and the demos
- **opennlp-grpc-integration-tests** - black-box integration tests that launch the
  shaded server and web application as separate processes and exercise analysis, search,
  and a remote TEI embedding backend over real network listeners
- **examples** - readable Python analysis, training, indexing, and search clients

## Branch boundaries

- `OPENNLP-1833-grpc-expansion` in `apache/opennlp-sandbox` is the canonical server
  and frontend branch. Service, gateway, webapp, deployment, and sandbox-specific
  adapter work lands there directly.
- `OPENNLP-1833-grpc-helper` in `apache/opennlp` is a generated build dependency:
  current apache main plus every open JIRA-backed feature PR head, including drafts.
  It carries only the snapshot version stamp and integration repairs associated with
  those JIRA branches. It is not a service-development branch.
- The former query, search UI, webapp, docview, and graph branches are historical
  consolidation inputs. Do not add new work to them.

## Build

The gRPC modules pin `opennlp.version` to `3.0.0-OPENNLP-1833-SNAPSHOT`, a local
test coordinate produced by the `OPENNLP-1833-grpc-helper` branch of
[apache/opennlp](https://github.com/apache/opennlp/tree/OPENNLP-1833-grpc-helper).
That branch aggregates every open JIRA-backed feature PR head (drafts included)
on top of apache main and is never deployed anywhere, so build it once into your
local Maven repository first:

```bash
git clone https://github.com/apache/opennlp.git
cd opennlp
git checkout OPENNLP-1833-grpc-helper
./mvnw install -DskipTests
```

Then build this repository (JDK 21+):

```bash
mvn clean install
```

Once the depended-on PRs merge to apache main, the pin reverts to plain
`3.0.0-SNAPSHOT` and the helper step goes away.

## Run the server

```bash
java -jar opennlp-grpc-distr/target/opennlp-grpc-server-all-3.0.0-SNAPSHOT.jar
```

`opennlp-grpc-server-all` bundles every in-tree add-on. The slim
`opennlp-grpc-service/target/opennlp-grpc-server-3.0.0-SNAPSHOT.jar` serves the same APIs
without any native inference runtime; add-on jars are dropped on the classpath instead:

```bash
java -cp "opennlp-grpc-server-3.0.0-SNAPSHOT.jar:backends/*" \
  org.apache.opennlp.grpc.server.OpenNlpGrpcServer
```

Configuring an add-on's models without its jar on the classpath fails startup with
`FAILED_PRECONDITION` naming the missing add-on, never a silently missing model.

Options:

- `-p, --port` - listen port (default `7071`)
- `-c, --config` - key=value config file

Example config (`key=value`, `#` comments):

```ini
server.enable_reflection=false
# Overall protobuf message limit. The effective value is never lower than
# server.max_text_bytes plus 1 MiB of envelope headroom.
server.max_inbound_message_size=10485760
# UTF-8 encoded bytes per analysis document or direct embedding message.
server.max_text_bytes=1048576
# Full-analysis documents admitted concurrently per stream. The default is the
# larger of 2 or the available processor count.
server.analysis_stream_workers=8
# Accepted RPCs may drain for this many seconds during shutdown before the
# server cancels them. Models and provider resources close only after draining.
server.shutdown_grace_seconds=5

# Optional node-local root for checksum-pinned catalog downloads initiated by
# the gRPC API or the Models & data workbench.
model.catalog_root=/srv/opennlp/catalog-models

# Optional explicit model overrides. When omitted, the language detector and the
# en sentence-detector, tokenizer, POS tagger and lemmatizer load from the
# classpath via the opennlp-models-* runtime deps.
# model.language_detector.path=/path/to/langdetect.bin
# model.sentence_detector.path=/path/to/en-sent.bin
# model.tokenizer.path=/path/to/en-token.bin
# model.pos_tagger.path=/path/to/en-pos.bin
# model.lemmatizer.path=/path/to/en-lemmas.bin
# Alternative to the statistical lemmatizer model: an OpenNLP
# word<TAB>postag<TAB>lemma dictionary. The two lemmatizer sources are mutually
# exclusive; the dictionary's tags must match the POS tagger's native tagset.
# model.lemmatizer.dictionary=/path/to/lemmas.tsv

# Additional per-language classic pipelines beside the default models. Each
# language configures all four models; requests route to a pipeline when the
# detected language matches (ISO 639-1 or 639-3), or explicitly through
# AnalysisProfile.pipeline_language. Installed catalog language packs publish
# these keys automatically.
# model.pipeline.de.sentence_detector.path=/path/to/opennlp-de-ud-gsd-sentence.bin
# model.pipeline.de.tokenizer.path=/path/to/opennlp-de-ud-gsd-tokens.bin
# model.pipeline.de.pos_tagger.path=/path/to/opennlp-de-ud-gsd-pos.bin
# model.pipeline.de.lemmatizer.path=/path/to/opennlp-de-ud-gsd-lemmas.bin
```

By default no configuration is required: the server loads the bundled language
detector (103 languages) and the English sentence-detector, tokenizer, POS tagger
and lemmatizer models (Apache-distributed UD models). When running from the
executable jar, the models merged into the jar by the build are used directly; when
running from a regular classpath (e.g. via Maven), they are discovered from the
`opennlp-models-*` runtime dependencies.

Install additional operator-approved models or data before startup with the installer
add-on's standalone CLI (bundled in `opennlp-grpc-server-all`; also usable from the
plain `opennlp-grpc-installer` jar plus its dependencies):

```bash
java -cp opennlp-grpc-distr/target/opennlp-grpc-server-all-3.0.0-SNAPSHOT.jar \
  org.apache.opennlp.grpc.installer.OpenNlpGrpcInstaller \
  install-resource \
  --source https://example.invalid/en-ner-person.bin \
  --checksum <sha256-or-sha512> \
  --target /srv/opennlp/models
```

The checksum is mandatory. The OPENNLP-1909 `ResourceInstaller` bounds the transfer and expansion,
verifies the downloaded bytes, stages extraction on the target filesystem, and publishes the
resource atomically. Add the installed paths to the server configuration, such as
`model.name_finder.person.path=/srv/opennlp/models/en-ner-person.bin`, then start or restart the
server. The web workbench's **Models & data** tab reports which pipeline features are ready and
which still need an operator-provided model or data resource.

### Browse and install the standard model catalog

The catalog itself ships in the `opennlp-grpc-installer` add-on (bundled in
`opennlp-grpc-server-all` and the docker images) and is discovered through the
`org.apache.opennlp.grpc.spi.catalog.ModelCatalogProvider` SPI; without the add-on the
server serves an empty catalog and an install attempt fails naming the missing jar.
When `model.catalog_root` is configured, the model training service exposes the discovered
catalog through `ListModelCatalog`, reports this node's verified downloads through
`ListInstalledModels`, and streams file-level progress from `InstallModel`. The web workbench
requires the user to review and acknowledge the catalog entry's license before it submits an
installation. Model weights are downloaded from checksum-pinned revisions and are never bundled
with the OpenNLP source or binary distribution.

The catalog also offers the seven classic OpenNLP 1.5 English name finders (person,
location, organization, date, money, percentage, time; Apache-2.0). Installing one publishes
`model.name_finder.<type>.path` at the next restart, after which `PIPELINE_STEP_NER` serves
that entity type.

The standard catalog currently distinguishes these roles:

| Catalog id | Upstream model | Role after installation |
| --- | --- | --- |
| `all-minilm-l6-v2-teacher` | `sentence-transformers/all-MiniLM-L6-v2` | Local ONNX teacher selectable by the Model2Vec-style trainer |
| `gum-cc-by-4-parser` | OpenNLP model trained from the GUM academic and court trees | English constituency parser activated on the next server start |
| `gum-cc-by-4-chunker` | OpenNLP model extracted from the same trained parser | English syntactic chunker activated on the next server start |
| `potion-base-8m` | `minishlab/potion-base-8M` | Ready-to-serve 256-dimensional static embedding provider |
| `potion-retrieval-32m` | `minishlab/potion-retrieval-32M` | Ready-to-serve 512-dimensional retrieval embedding provider |
| `potion-multilingual-128m` | `minishlab/potion-multilingual-128M` | Ready-to-serve 256-dimensional multilingual embedding provider |
| `paraphrase-multilingual-minilm-l12-v2-teacher` | `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` | Multilingual ONNX teacher (50+ languages) selectable by the Model2Vec-style trainer |
| `de-ud-gsd-*`, `fr-ud-gsd-*`, `es-ud-gsd-*` | Apache OpenNLP UD 1.3 releases | German, French, and Spanish sentence detector, tokenizer, POS tagger, and lemmatizer packs, verified against the published Apache checksums and activated on the next server start |

The complete flow from a stock server to German analysis and German semantic search is
walked through in [docs/tutorials/german-end-to-end.md](docs/tutorials/german-end-to-end.md).

An installed UD pack model publishes into its language's pipeline slot
(`model.pipeline.<lang>.sentence_detector.path` and the tokenizer, POS tagger, and lemmatizer
equivalents) on the next start, so packs for different languages coexist beside the bundled
English default; a second model for one language's slot fails loud at startup. Each loaded
pipeline is advertised as bundle `pipeline-<lang>`, requests route to it automatically when
`PIPELINE_STEP_LANGUAGE_DETECT` reports that language, and
`AnalysisProfile.pipeline_language` selects one explicitly (an unknown code returns
`NOT_FOUND` naming the configured pipelines).

Every entry fixes the upstream revision, file list, byte sizes, SHA-256 values, model page, and
license identity in server-owned metadata. A static table joins the same embedding provider catalog
as configured static, TEI, OpenVINO, and other ServiceLoader providers without restarting the
process. A teacher does not become an embedding route. It becomes an allowed local input to
`TrainStaticModel`, which distills a new static Model2Vec-style table from a learned vocabulary.
Parser and chunker installations report that a restart is required. On the next start, the server
verifies their catalog descriptors and exact bytes before adding their paths to the parser and
chunker registries. The complete Java-only training process for those two demonstration models is
in `examples/model-training/gum` and does not depend on Python, cTAKES, or an older OpenNLP model.

Installation is intentionally node-local. In a replicated deployment, an operator or deployment
controller calls `InstallModel` on each node, verifies `ListInstalledModels`, then admits that node
to traffic. The service does not claim a distributed replication or consensus protocol.

For the complete demonstration setup, use the checksum-pinned downloader. Its preferred embedding
provider is a static table produced by OpenNLP's `DistillModel` command:

```bash
./demo-model-download.sh \
  --embedding-dir /srv/opennlp/models/legal-minilm-full \
  --embedding-model-id legal-minilm-full
```

The script writes `demo-models/demo-server.properties` and a model-source manifest, then prints the
exact classpath command needed to start the static embedding provider. It also installs the ONNX NER
and sentiment models, SentencePiece data, and Open English WordNet needed by the richer workbench
profiles. Current Apache `opennlp-models-*` Maven dependencies provide language detection plus the
English sentence, token, POS, and lemma models. Parser and syntactic chunker models remain explicit
operator-approved inputs, available from the standard catalog rather than the retired SourceForge
1.5 artifacts.

To create the embedding directory, follow
`opennlp-extensions/opennlp-embeddings/TRAINING.md` in the corresponding OpenNLP checkout. The basic
flow is:

```bash
opennlp-embeddings DistillModel \
  -teacher sentence-transformers/all-MiniLM-L6-v2 \
  -out /srv/opennlp/models/legal-minilm-full \
  -pcaDims 256
```

`DistillModel` writes the static matrix, vocabulary or tokenizer files, and configuration consumed
by `--embedding-dir`. The teacher model's license carries onto the distilled table, so verify it
before publishing the result. For a quick public fallback instead of a locally distilled table,
pass `--public-embedding-fallback`; this choice is explicit and is not the demo default.

TEI and OpenVINO Model Server are optional remote providers. Their endpoints, served-model
identities, TLS choice, and route policy are operator configuration. The repository does not carry
deployment-specific values. For example, add TEI to the generated startup configuration with:

```bash
./demo-model-download.sh \
  --embedding-dir /srv/opennlp/models/legal-minilm-full \
  --embedding-model-id legal-minilm-full \
  --tei-target tei.example.org:8080 \
  --tei-model-id remote-minilm \
  --tei-vector-space-id minilm-v1 \
  --tei-use-tls
```

Or add an OpenVINO/KServe v2 endpoint with:

```bash
./demo-model-download.sh \
  --embedding-dir /srv/opennlp/models/legal-minilm-full \
  --embedding-model-id legal-minilm-full \
  --openvino-target ovms.example.org:9000 \
  --openvino-model-id remote-ovms \
  --openvino-model-name minilm \
  --openvino-model-version 1 \
  --openvino-vector-space-id minilm-v1 \
  --openvino-use-tls
```

The script adds a remote backend jar to the printed server classpath only when that backend is
configured. It writes the supplied values only to the generated `demo-models` configuration, which
is excluded from Git. The local OpenNLP-distilled model remains the default. Use the same logical
model id and exact vector-space id to make compatible engines participate in priority and fallback
routing, or use distinct logical ids when clients should select them explicitly. Run
`./demo-model-download.sh --help` for deadline, priority, TEI normalization/truncation, and OpenVINO
tensor-name options. Remote providers are contacted and validated when the gRPC server starts.

## Export analyzed documents

The reply of `AnalyzeDocument` is itself the first output format: `FormatDocument`
renders one analyzed document into any format contributed through the
`org.apache.opennlp.grpc.spi.format.OutputFormatter` SPI, and `ListOutputFormats`
lists what the running server can render. The server ships `proto` (the reply's
binary protobuf bytes), `protojson` (canonical protobuf JSON), and `tsv` (one token
per row); the `opennlp-grpc-formats` add-on (bundled in `opennlp-grpc-server-all`
and the docker images) adds `conllu`, `csv`, `markdown`, and `warc`, all written by
hand with no third-party dependency. The gateway exposes the same pair as
`GET /api/v1/output-formats` and `POST /api/v1/format-document`.

An unknown format id fails with `NOT_FOUND` naming the available ids; further
formats are one `OutputFormatter` implementation plus a ServiceLoader registration
in a jar on the server classpath. The SPI is generic over the reply type, so future
reply families (search results, catalogs) can grow their own formatter sets without
new plumbing.

## Stream analyzed documents to a sink

Sinks are the push counterpart of output formats: the server tees every document the
analysis service produces (unary and streaming) into destinations contributed through
the `org.apache.opennlp.grpc.spi.sink.DocumentSinkProvider` SPI. A sink failure is
logged and isolated, never failing the analysis that produced the document.

The `opennlp-grpc-sink-grpc` add-on (bundled in `opennlp-grpc-server-all` and the
docker images) streams to a downstream receiver over one client-streaming call:

```ini
sink.archive.provider=grpc
sink.archive.target=localhost:9091
# Optional: attach a deployed output format's rendering to every item.
sink.archive.format=conllu
```

The receiver implements the one-RPC `OpenNlpDocumentSinkService` contract
(`opennlp_sink.proto`) in any protobuf language: generate a Python, Go, or Java
server stub, accept the `StreamDocuments` stream, and reply with a summary when the
sender half-closes at shutdown. Like the JSON gateway, the sink channel carries no
credentials, so targets belong on loopback or a trusted network. An instance naming
an unknown provider fails startup listing the available sink ids.

### Catalog roles, unlock tags, and install failures

Every catalog entry now says what it unlocks. `ModelCatalogDescriptor` carries the artifact
`format` (derived from the pinned file names: OpenNLP `.bin`, ONNX, SentencePiece, WN-LMF,
safetensors), the pipeline steps it `unlocks`, whether it `requires_restart`, and its pinned
`files`; the workbench renders these as tags on each card and uses them to route a
browned-out feature on the Analyze tab to the card that fixes it. Three roles joined the
classic ones: `SUBWORD_MODEL` (published as `model.subword.<id>.path`), `WORDNET_LEXICON`
(`model.wordnet.<id>.path`, plain or gzipped WN-LMF), and `DOC_CATEGORIZER`
(`model.doccat.<id>.path`). The standard catalog offers the T5 small SentencePiece model
and Open English WordNet 2024 for the first two.

An install refuses up front when the catalog root lacks the model's size plus a 64 MiB
margin (`RESOURCE_EXHAUSTED`), when another installed model already claims the same
restart slot (`FAILED_PRECONDITION`, naming the occupant), when a download fails
(`UNAVAILABLE`, naming the file and host), or when a downloaded file fails its pinned
SHA-256 (`FAILED_PRECONDITION`); nothing is published in any of these cases.
`ListSearchProviders` also reports whether live indexing is enabled and whether
`search.persist.root` is set, so the search tabs brown out with the reason instead of
failing per click.

### Gateway deadlines scale with input size

The gateway's per-RPC deadline (`--request-timeout-seconds`, default 30) covers a
sentence, not a novel. Analysis, formatting and indexing calls therefore add
`--request-timeout-per-megabyte-seconds` (default 120) for every mebibyte of document text
they submit, never exceeding `--long-running-timeout-seconds` (default 1800); pass `0` to
disable the scaling.

The gateway also keeps idle HTTP keep-alive connections open for 15 minutes instead of the
JDK's 30 seconds, so a browser that pauses on a result does not get a bare network failure
on its next request; set `-Dsun.net.httpserver.idleInterval=<seconds>` to choose another
value.

## Run the optional web application

With the gRPC service running on its default port, start the separate web application:

```bash
java -jar opennlp-grpc-webapp/target/opennlp-grpc-webapp-3.0.0-SNAPSHOT.jar
```

The gateway uses a 30-second deadline for discovery and ordinary RPCs, and a separate
30-minute deadline for static-model training and catalog installation. Override the latter with
`--long-running-timeout-seconds` when model size or network throughput requires a different bound.

Open `http://127.0.0.1:7072/`. The default TypeScript interface discovers configured profiles,
models, resources, and supported pipeline steps. Its default preset requests the richest safe
combination that is actually available, while named profiles and automatic server selection remain
available. Sentence and token-window chunking can be enabled independently or together. The
Analyze action uses the same-origin progressive NDJSON gateway. It renders complete typed layers
as they arrive, then installs the terminal `AnalyzeDocumentResponse` for copy, download, heatmap,
graph, and indexing operations.

The Analyze workbench gives the output the full page width and provides Document, Chunks, Heatmap,
Graph, and Protobuf JSON projections over the same response. Long source text and annotated output
scroll vertically without a horizontal scrollbar. Selecting an annotation, graph node, or chunk
opens details in a side drawer so the document does not collapse into a narrow column. The Graph
projection switches between a layer overview, labeled dependency arcs, and an entity relation
network when the corresponding typed layers are present.
The Build index tab turns pasted documents into one guided analysis, vocabulary learning, static-model
training, indexing, and search flow. It uses corpus-only vocabulary by default and can pair the
corpus with an imported dictionary selected before the run.
The web host loads additional static interfaces through the `WebUiExtension` ServiceLoader API.
See [opennlp-grpc-webapp/README.md](opennlp-grpc-webapp/README.md) for endpoints, security defaults,
and command-line options.

## Run the demonstration stack with Docker

One container runs the server and the web application together, out of the box
on Linux and on macOS Docker Desktop including Apple silicon:

```bash
mvn clean install
cd docker
docker compose up --build
```

Open `http://127.0.0.1:7072/` for the workbench; gRPC clients use
`127.0.0.1:7071`. The container accepts the same `server.properties`
configuration and carries the optional embedding backends on the classpath.
See [docker/README.md](docker/README.md) for configuration, state, and
security notes.

## Use the service from Python, Node.js, Java, or Go

The [Python quickstart](examples/python-client/README.md) uses standard generated
protobuf stubs and `grpcio`. Its first example analyzes typed document shapes,
creates a process-local TurboQuant index in the Java server, and prints exhaustive
server-ranked results. Its second example streams documents through vocabulary
learning, static-model distillation, index publication, and search.

The [Node.js quickstart](examples/node-client/README.md), the
[Java quickstart](examples/java-client/README.md), and the
[Go quickstart](examples/go-client/README.md) run the same analyze, index,
and search flow with identical output: Node.js loads the v1 protos at runtime
with no code generation, Java uses the generated blocking stubs from
`opennlp-grpc-api`, and Go generates its stubs locally with one script.

These clients are intentionally small enough to adapt in a notebook or data
pipeline. The black-box integration suite runs the first example against the
packaged server and separately exercises the complete lifecycle through the
shipped descriptor set.

## Import a dictionary and learn a vocabulary

`org.apache.opennlp.grpc.v1.OpenNlpVocabularyService` exposes OpenNLP's existing vocabulary
learning workflow without allowing callers to choose server filesystem paths. The service is
always registered so clients can discover formats and limits, but imports, learning, and downloads
are disabled until the operator configures an artifact root:

```ini
vocabulary.artifact_root=/srv/opennlp/vocabulary-artifacts
vocabulary.max_dictionary_bytes=67108864
vocabulary.max_dictionary_entries=1000000
vocabulary.max_corpus_documents=100000
vocabulary.max_corpus_bytes=104857600
vocabulary.max_vocabulary_terms=1000000
vocabulary.max_concurrent_writes=1
```

`vocabulary.artifact_root` is a plain directory path or a URI whose scheme selects the
durable store. A plain path or a `file` URI uses the built-in filesystem store, which
stages each artifact and publishes it with one atomic directory move. Other schemes
resolve through the `VocabularyStoreProvider` ServiceLoader interface in
`org.apache.opennlp.grpc.spi.vocabulary`, so a remote tier such as S3 plugs in by
adding the JAR that provides its scheme to the classpath; the service itself carries
no cloud dependency. A scheme with no provider on the classpath fails loud at startup.
The `opennlp-grpc-store-s3` add-on provides the `s3` scheme: set
`vocabulary.artifact_root=s3://bucket/prefix` and drop its self-contained
`opennlp-grpc-store-s3-<version>-all.jar` (AWS SDK bundled) on the server classpath;
region and credentials resolve through the standard AWS chain.

The defaults shown above are conservative per-operation caps. `max_concurrent_writes` is shared by
dictionary imports and vocabulary builds, so multiple client streams cannot multiply their bounded
working sets without an explicit operator choice. Values are validated against fixed safety
ceilings at startup.

The six RPCs form one explicit artifact flow:

1. `ListDictionaryFormats` returns built-in and extension formats plus the effective limits.
2. `ListDictionaries` returns the imported dictionary artifacts available for vocabulary learning.
3. `ListVocabularies` returns the learned vocabulary artifacts, in artifact-id order, that a
   distillation or a collection's coverage watch can name.
4. `ImportDictionary` accepts a start frame followed by bounded encoded byte frames. The built-ins
   accept UTF-8 headword-and-definition TSV, one UTF-8 headword per line, and OpenNLP dictionary
   XML. The server publishes a normalized, hashed dictionary artifact atomically.
5. `LearnVocabulary` accepts a start frame followed by `OpenNlpDocument` values. Each document's
   `raw_text` contributes corpus counts. An optional imported dictionary preserves required
   headwords alongside those corpus terms.
6. `DownloadVocabulary` streams the exact hashed UTF-8 `term<TAB>count<TAB>source` artifact. The
   downloaded table can be supplied to the OpenNLP embeddings `DistillModel` workflow as its terms
   input.

This RPC learns and persists vocabulary counts; it does not itself run a teacher model. Turning
a learned vocabulary into a static embedding model is the job of the training service below,
which keeps teacher identity, licensing, resource use, and output location under operator
control through an explicit teacher allowlist.

Dictionary encodings are extensible without changing the wire contract. A provider implements
`org.apache.opennlp.grpc.spi.vocabulary.DictionaryFormatProvider`, returns a stable custom selector,
and registers its class in:

```text
META-INF/services/org.apache.opennlp.grpc.spi.vocabulary.DictionaryFormatProvider
```

Provider jars go on the server classpath. Duplicate selectors, malformed descriptors, unspecified
standard values, and unstable custom ids fail server startup. Artifact descriptors retain format,
source and license metadata, SHA-256, byte size, term counts, learning controls, and creation time.
Existing artifacts are verified before they are admitted again after restart.

## Train a static embedding model from a learned vocabulary

`org.apache.opennlp.grpc.v1.OpenNlpModelTrainingService` distills an operator-configured teacher
into a Model2Vec-style static embedding model whose extra term rows come from one learned
vocabulary artifact. The published model serves immediately as a registered embedding model, so
one client flow covers the whole loop: import a dictionary, learn a vocabulary, train a model,
analyze and index documents with it, and search them.

Training shares `vocabulary.artifact_root` (models publish under a `models` kind through the
same durable store seam, so a remote store scheme covers them too) and stays disabled until that
root is configured. Teachers are an explicit allowlist; arbitrary references from clients are
rejected:

```ini
training.teacher.local-mini.ref=/srv/opennlp/teachers/local-mini
training.teacher.local-mini.display_name=Local mini encoder
training.max_pca_dims=512
training.max_concurrent_trainings=1
training.model_cache_dir=/srv/opennlp/trained-model-cache
```

A teacher reference is a local directory holding `tokenizer.json` and `onnx/model.onnx`, plus
any external ONNX data and tokenizer model files needed by that export. It can instead be a
Hugging Face model id (`org/model@revision` is recommended so the input is pinned), downloaded
into a local cache on first use. `training.model_cache_dir` is the local directory verified
models are served from; it defaults to a per-process temporary directory and is rebuilt from
the durable store on startup.

The four RPCs:

1. `ListTeachers` returns the configured teachers plus the effective limits.
2. `TrainStaticModel` names a teacher, a vocabulary artifact, and optional `pca_dims`
   (0 selects the default of 256). It streams one update per distillation progress line; the
   terminal update carries the published `StaticModelDescriptor`, whose `artifact_id` is also
   the embedding model id accepted by `EmbeddingSelector.model_id`.
3. `ListStaticModels` lists every published model with its manifest hash and provenance.
4. `DeleteStaticModel` removes the artifact and stops serving its model id.

`StreamingTraining` is the bidirectional form for clients that want one bounded session instead
of composing the analysis, vocabulary, model, and indexing RPCs themselves. Its first frame fixes
the analysis and vocabulary controls plus optional model and index plans. Each following document
gets a correlated document-shape analysis reply while the server retains only bounded source text
and identity. Client half-close learns the vocabulary, optionally distills and publishes the model,
then reanalyzes the accepted corpus with that model and publishes the index. The final update carries
all published descriptors. Cancellation or a terminal-stage failure rolls back artifacts in reverse
order. The model and index plans remain operator-gated: the dictionary must already be imported, the
teacher must be allowlisted, and persistence requires `search.persist.root`.

The bundled web UI's Trainer tab drives the whole flow in the browser: optionally import a
dictionary, learn a vocabulary from pasted documents, watch the distillation progress stream,
and pick the served model in Analyze to index and search with it.

Every published model carries a manifest naming the exact size and SHA-256 of each model file;
the descriptor's `artifact_hash` is the SHA-256 of that manifest. Models are re-verified against
the manifest before they are served again after a restart, and a tampered artifact fails startup
loudly.

## Build and explore a bounded legal-passage index

The first startup search provider loads one immutable TurboQuant bundle fully into memory. The
separate workspace API can create, replace, persist, seal, reindex, and delete bounded process-local
indexes through gRPC. Neither mode is a distributed search engine. A later provider can implement
the same ServiceLoader contract for another index implementation without changing the gRPC or
browser contracts.

Start with normalized UTF-8 JSON Lines in the `CasePassage` interchange shape. Each physical
record has six string fields:

```json
{"id":"case-001-0-0001","case":"Example v. State","cite":"1 Example 1","date":"2026-01-01","vol":"1","text":"A court may order an appropriate remedy."}
{"id":"case-002-0-0001","case":"Sample v. City","cite":"2 Example 10","date":"2026-02-01","vol":"2","text":"The claimant must establish standing."}
```

Use stable, unique IDs with no line breaks. `text` is both the retained source document and the
text embedded by this first builder. Keep an exact preparation record beside the corpus. The
builder hashes this file into the bundle provenance but does not apply its contents:

```ini
recipe.id=legal-passages-v1
source.artifact.sha256=<sha-256-of-the-source-export>
normalization=unicode-nfc-and-whitespace-v1
chunking=opinion-paragraph-runs-v1
```

Configure any embedding backend already supported by the server. For example, an ONNX route can
be used both while building and while serving queries:

```ini
model.embedder.default_id=legal-encoder
model.embedder.legal-encoder.onnx.path=/srv/opennlp/models/legal-encoder.onnx
model.embedder.legal-encoder.vocab.path=/srv/opennlp/models/vocab.txt
model.embedder.legal-encoder.onnx.vector_space_id=legal-encoder-v1
```

Build a new bundle. The command refuses to replace an existing output path, snapshots both input
files before embedding, and enforces record, input, query, batch, and output limits:

```bash
java -cp opennlp-grpc-distr/target/opennlp-grpc-server-all-3.0.0-SNAPSHOT.jar \
  org.apache.opennlp.grpc.search.turboquant.TurboQuantSearchBundleCommand \
  --server-config /srv/opennlp/legal/server.properties \
  --passages /srv/opennlp/legal/passages.jsonl \
  --preparation-config /srv/opennlp/legal/preparation.properties \
  --output-dir /srv/opennlp/legal/legal-index-v1 \
  --index-id legal-opinions \
  --display-name "Legal opinions" \
  --model-id legal-encoder \
  --bits 4 \
  --seed 42 \
  --corpus-title "Curated legal opinions" \
  --corpus-provenance "Normalized from the verified source export dated 2026-08-16" \
  --corpus-source-uri https://example.org/legal-corpus \
  --license-name CC0-1.0 \
  --license-uri https://creativecommons.org/publicdomain/zero/1.0/
```

The selected model must resolve to one stable nonblank vector-space ID for every build batch.
The built bundle records its corpus digest, bundle digest, builder identity, preparation digest,
embedding route, dimension, metric, bit width, and seed. Verify the corpus source and license for
the material you actually index; the example metadata above is illustrative.

Add the immutable bundle to the same server configuration. `passages.jsonl` is copied into the
bundle, so the serving configuration can remain self-contained:

```ini
search.indexes=legal-opinions
search.max_indexes=32
search.dynamic.enabled=true
search.index.legal-opinions.provider=turbo_quant
search.index.legal-opinions.directory=/srv/opennlp/legal/legal-index-v1
search.index.legal-opinions.passages=/srv/opennlp/legal/legal-index-v1/passages.jsonl
search.index.legal-opinions.max_top_k=50000
search.index.legal-opinions.max_query_bytes=16384
search.index.legal-opinions.max_response_bytes=8388608
search.index.legal-opinions.max_records=100000
search.index.legal-opinions.max_source_document_bytes=10485760
search.index.legal-opinions.max_indexed_text_bytes=1048576
search.index.legal-opinions.max_bundle_bytes=536870912
```

Start the gRPC server with that file, then start the web application on its separate loopback
port:

```bash
java -jar opennlp-grpc-distr/target/opennlp-grpc-server-all-3.0.0-SNAPSHOT.jar \
  --config /srv/opennlp/legal/server.properties

java -jar opennlp-grpc-webapp/target/opennlp-grpc-webapp-3.0.0-SNAPSHOT.jar \
  --grpc-target 127.0.0.1:7071
```

Open `http://127.0.0.1:7072/` and select **Corpus search**. The browser discovers index limits and
provenance, sends a document-shaped query, maps cosine scores across a fixed red-neutral-green
scale, highlights the authoritative span in the original source text, compares it with indexed
chunk text, and opens typed OpenNLP annotations for a selected source document. All remote query
and response sizes remain bounded by the descriptor advertised to the browser.

The results panel offers two views. The ranked list orders scored chunks best first. A TurboQuant
index with at most 50,000 records advertises `supports_all_hits` independently of the ordinary
`max_top_k` setting. The interactive workbenches send the typed `all_hits` request for such an
index. The server
returns every ranked chunk that fits `max_response_bytes`, reports truncation explicitly, and
emits each referenced source document once rather than once per hit. Other providers remain
bounded by `max_top_k`. The browser renders returned chunks on the same score scale and leaves
unreturned chunks gray. Selecting a shaded span opens it in the result inspector.

The search API is also available directly as
`org.apache.opennlp.grpc.v1.OpenNlpSearchService`. `ListSearchIndexes` returns stable descriptors;
`SearchIndex` accepts `index_id`, exactly one result limit (`top_k` or capability-checked
`all_hits`), and exactly one of two query forms: a complete
`OpenNlpDocument` `query` (shorthand for one semantic clause) or a typed `compound_query`
tree from `opennlp_query.proto`. Query routing
may fall back to another embedding backend only when model ID, vector-space ID, and dimension
remain compatible with the route that built the index.

A compound query composes semantic, `term`, and `phrase` clauses under `join` (AND, OR,
exclusions, optional reciprocal-rank fusion) and `boost` nodes, plus two constrained CEL roles:
a `cel_filter` that gates membership and a `cel_calculator` that scores from document metadata
through a declared normalization. The normative score algebra is documented in
`opennlp_query.proto` and pinned by tests: every score stays in `[0, 1]`, joins decide
membership, boosts shape relevancy, and ranking ties break by chunk id then document id.
Keyword and phrase components analyze query text and indexed chunk text identically (code-point
letter-and-digit terms, lowercased), and hits carry `matched_spans` locating each match in
`indexed_text` by UTF-16 code unit for exact highlighting. Compound queries execute on the
live indexes; a keyword-only tree needs no embedding backend at all. CEL clauses
require an evaluator on the classpath through the `CelQueryEvaluator` ServiceLoader seam; the
core ships none, and without one those clauses report `UNIMPLEMENTED`.

The api jar also ships its complete `FileDescriptorSet` at
`META-INF/opennlp/descriptors/opennlp-grpc-v1.protobin`, so non-Java consumers can load the
wire contract without code generation; `org.apache.opennlp.grpc.descriptors` reads it back
into runtime descriptors, and the gRPC server serves the same descriptors through standard
server reflection. The opt-in `PythonLifecycleLiveIT` in `opennlp-grpc-integration-tests`
proves it: a Python client built purely from those descriptors drives the whole training
lifecycle, from dictionary import through drift watching to the blue/green reindex and a
compound query with matched spans.

Search engines are provider instances behind one ServiceLoader SPI
(`SearchIndexProviderFactory`). Each factory declares capabilities (vector, keyword, live,
bundle, persistent) and registers a default instance named by its provider id; the
configuration adds named instances with `search.provider.<instance-id>.type=<provider-id>`.
Provider-specific options live below that instance, for example:

```ini
search.provider.compact.type=turbo_quant
search.provider.compact.option.bits=4
search.provider.compact.option.seed=1833
```

Each factory parses those strings once at startup into its typed immutable configuration and rejects
unknown or invalid options before the server listens.
`ListSearchProviders` (and `GET /api/v1/search-providers`) lists them, and
`SearchProviderSelector.custom` accepts any listed instance id, with the standard enum values
as shorthand for the built-in defaults. Index descriptors name their per-modality `components`: a
vector component (flat float or TurboQuant) and a keyword component served by the built-in `terms`
provider, which records its analysis-chain identity so query-time analysis provably matches
index-time analysis.

Dynamic indexes have a wire-complete lifecycle. `PersistIndex` writes a checkpoint under the
operator-configured `search.persist.root`. TurboQuant workspaces store immutable quantized vector
segments and provider row references, not a second copy of every raw float vector. New documents
append a bounded segment, so indexing continues after a restart without rehydrating raw vectors.
The server-wide vector budget counts every live segment, including rows superseded by document
replacement, which keeps repeated mutation bounded. The
`search.persist.checkpoint_seconds` enables an auto-checkpoint that rewrites only changed
indexes. `SealIndex` persists and marks an index immutable. `ReindexIndex` runs blue/green:
it replays the source index's retained chunks through a newly selected embedding route,
builds the new index beside the old one, and swaps the requested alias only after the build
succeeds. Aliases (`SetIndexAlias`, `DeleteIndexAlias`, `ListIndexAliases`) are logical names
accepted wherever an index id is; responses always carry the resolved id. Persistence
requires the index's provider instance to declare the persistent capability (TurboQuant
does; flat float is in-memory only). The gateway serves all of it: `/api/v1/persist-index`,
`/api/v1/seal-index`, `/api/v1/reindex-index`, `/api/v1/set-index-alias`,
`/api/v1/delete-index-alias`, and `/api/v1/index-aliases`.

Collections scope vocabulary coverage. A collection (`SetCollection`, `GetCollection`,
`ListCollections`, `DeleteCollection`) names its dynamic member indexes (aliases accepted,
stored resolved), its dictionary, vocabulary, and model artifact lineage, and an optional
drift threshold. Its term statistics are recomputed on every read from the live indexed text of
member chunks with the same analysis chain as the keyword components, so replaced or deleted
documents never leave stale counts; a multiword term of the current vocabulary counts as
one unit, and the drift statistics report how many indexed terms fall outside that
vocabulary (the retrain meter). With a persistence root configured, each collection is one
atomic `collection.pb` file with an integrity hash inside and the last write winning.
`search.collection.max_distinct_terms` bounds the vocabulary and drift maps built during one
recalculation (default 1,000,000, fixed ceiling 10,000,000). Drift descriptor rebuilds and subscriber
callbacks run without holding the registry monitor, so a slow watcher cannot block collection
mutation.
`WatchCollection` is a server-streaming subscription: the first event is always a complete
snapshot, and later events report drift threshold crossings, member index persistence, and
model publication, each self-contained, so a reconnect simply resubscribes. The gateway
serves `/api/v1/set-collection`, `/api/v1/get-collection`, `/api/v1/collections`,
`/api/v1/delete-collection`, and `POST /api/v1/watch-collection`, which streams events as
NDJSON lines until the gateway's RPC deadline ends the watch and the client reconnects for
a fresh snapshot.

`IndexDocuments` also accepts analyzed `OpenNlpDocument` values whose chunk groups already carry
embeddings. It creates or atomically extends a bounded index in server memory, so the browser
never stores vectors or computes similarity. The optional `provider` selector fixes the vector
storage at creation: the exact flat float provider (the default), TurboQuant (from the
`opennlp-grpc-search-turboquant` add-on, bundled in `opennlp-grpc-server-all` and the
docker images), which quantizes
each published snapshot with a fixed bit width and seed, or any configured instance of a
live vector provider; extending an index requires the same instance or an unset selector. `DeleteSearchIndex` releases that process-local
workspace. Dynamic indexing is enabled by default for the workbench and can be disabled with
`search.dynamic.enabled=false`. Per-index document, serialized source-document, chunk, and
dimension limits are combined with server-wide serialized-document and vector-memory ceilings.

> v1 note: this slice implements language detection (`PIPELINE_STEP_LANGUAGE_DETECT`,
> filling `detected_language` with an ISO 639-3 code plus `language_confidence`;
> a positive `AnalysisOptions.ranked_language_count` additionally fills
> `OpenNlpDocument.ranked_languages` and the `opennlp:language` layer with that many
> ranked predictions, best first),
> sentence detection, tokenization, named entity recognition (`PIPELINE_STEP_NER`,
> filling `AnnotatedSentence.entities`), POS tagging (`PIPELINE_STEP_POS_TAG`,
> filling `Token.pos_tag`, converted to the requested
> `AnalysisProfile.pos_tag_format` and defaulting to the model's native tagset), lemmatization
> (`PIPELINE_STEP_LEMMATIZE`, filling `Token.lemma`; requires POS), document
> categorization (`PIPELINE_STEP_DOC_CATEGORIZE`, filling
> `OpenNlpDocument.classification`), per-sentence sentiment
> (`PIPELINE_STEP_SENTIMENT`, filling `AnnotatedSentence.sentiment_label` /
> `sentiment_confidence`), constituency parsing (`PIPELINE_STEP_PARSE`, filling
> structured and/or bracketed parse views), classic shallow chunking
> (`PIPELINE_STEP_SYNTACTIC_CHUNK`, filling `AnnotatedSentence.syntactic_chunks`),
> dependency parsing (`PIPELINE_STEP_DEPENDENCY_PARSE`, filling the typed
> `opennlp:dependencies` layer), dependency-path relation extraction
> (`PIPELINE_STEP_RELATION_EXTRACT`, filling `opennlp:relations`),
> sentence and document embeddings (`PIPELINE_STEP_EMBED`), segmentation chunking
> (typed `sentence`, `token`, and `semantic` strategies via `chunk_embed_configs` or
> `PIPELINE_STEP_CHUNK`), category-driven chunking via `category_chunk_configs`,
> probability reporting, caller-specific `max_text_length`, offset encoding selection,
> parse format
> selection, and capability discovery through `GetServiceInfo` / `ListModelBundles`.
> The default `en-basic` profile/bundle is always present; optional `en-ner`,
> `en-doccat`, `en-sentiment`, `en-parse`, and `en-chunk` profiles/bundles, plus the
> `en-dependency` bundle, are advertised only when their operator-supplied models are
> configured, and the
> `en-embed` profile (sentence detect + tokenize + embed, riding the `en-basic`
> bundle) is advertised only when an embedding model is configured. NER, syntactic
> chunking, and parsing support multi-provider engine policy; embeddings support
> ONNX CPU/CUDA plus optional TEI and OpenVINO/KServe backends through SPI modules.
> `DocumentAnalytics`, `AnalysisProfile.pos_tag_format` (UD/Penn conversion), per-entry
> chunk profiles, and `ChunkingSpec.clean_text` / `preserve_urls` are implemented on the
> v1 contract. `ModelDescriptor.hash` / `ModelBundleRef.component_models` pinning is
> implemented for the backbone models, classic NER models, and the primary embedder
> route; DL NER, document categorizer, sentiment, parser, chunker, and fallback
> embedder routes do not yet carry hashes.
> `POS_TAG_FORMAT_CUSTOM` remains unsupported.

#### Typed inference engine selection

NER, syntactic chunking, and parsing share `EnginePolicy`. New clients use its
`selectors` field: `StandardInferenceEngine` strongly types the built-in `OPENNLP_ME`,
`ONNX`, and `CUDA` choices, while `EngineSelector.custom` accepts a ServiceLoader
extension's provider id. The original string-valued `engines` field remains available for
wire-compatible clients, but a request cannot set both `engines` and `selectors`.

For either field, an empty list uses each component's highest-priority engine with fallback,
one selector pins that engine, and multiple selectors run a union in request order. NER and
chunking reconcile union results according to `merge`; parsing returns one tree per engine.
An unspecified standard selector, an empty custom id, or an unknown provider id fails the
request instead of silently falling back.

### Unicode text analysis (model-free parity surfaces)

These request surfaces expose the `opennlp-tools` Unicode stack (offset-aware
normalization, UAX #29 word segmentation, per-token normalization layers) on the
wire. They run entirely rule-based and need no operator-supplied models.

| Feature | Request surface | Response surface | Notes |
| ------- | --------------- | ---------------- | ----- |
| Offset-aware normalization | `PIPELINE_STEP_NORMALIZE` + `AnalysisProfile.normalization` (`NormalizationSpec.normalizers`, `require_alignment`) | `OpenNlpDocument.normalization`: `normalized_text`, `applied_normalizers`, `alignment` runs | Normalizers apply in the library's canonical order. Offset-opaque normalizers (NFC, NFKC, CASE_FOLD, ACCENT_FOLD, CONFUSABLE_FOLD) are rejected unless `require_alignment = false`, which returns `normalized_text` without runs plus a diagnostic. `WHITESPACE`, `WHITESPACE_PRESERVE_LINE_BREAKS`, and `WHITESPACE_PRESERVE_PARAGRAPHS` are mutually exclusive; the paragraph variant unwraps hard-wrapped lines (a run with at most one line break becomes a space, two or more become one newline, CRLF counts once). |
| Alignment run rescale | `AnalysisOptions.offset_encoding` | `AlignmentRun.original_units` / `normalized_units` | Alignment runs are emitted in the response's `OffsetEncoding`: UTF-8 bytes by default, UTF-16 code units on request, matching every other span in the response. |
| Typed tokenizer choice | `AnalysisProfile.tokenizer.standard` | `OpenNlpDocument.sentences[].tokens` and `opennlp:tokens` | `MODEL` is the default. `UAX29` adds `Token.word_type`; `WHITESPACE` retains attached punctuation; `SIMPLE` splits character-class transitions; `LATTICE` selects the configured MeCab dictionary. The compatibility `tokenizer_engine` string remains accepted, but cannot be set with `tokenizer`. |
| Typed sentence detector choice | `AnalysisProfile.sentence_detector.standard` | `OpenNlpDocument.sentences` and `opennlp:sentences` | `MODEL` is the default. `NEWLINE` treats each non-empty line as one sentence and needs no model. |
| Per-token term layers | `AnalysisProfile.term_dimensions` (library `Dimension` names, e.g. `NFC`, `CASE_FOLD`, `FULL_CASE_FOLD`, `EMOJI_FOLD`) | `Token.term_layers` map | Requires `PIPELINE_STEP_TOKENIZE`. Character-level dimensions only: `ORIGINAL`, `STEM` and `LEMMA` are rejected (`PIPELINE_STEP_LEMMATIZE` owns lemmas). |
| Per-language term profile | `AnalysisProfile.term_profile` (registry language, e.g. `"en"`, `"de"`) | `Token.term_layers` map carrying every layer in the profile (including its `STEM` layer) | Requires `PIPELINE_STEP_TOKENIZE`. Mutually exclusive with `term_dimensions`; an unregistered language fails with `NOT_FOUND`. |
| Configurable term layers | `AnalysisProfile.term_layers` (`TermLayerSpec.qualifier`, typed `normalizers`, optional typed `stemmer`) | Qualified `STANDARD_LAYER_TERMS` layers and `Token.term_layers` entries | Requires `PIPELINE_STEP_TOKENIZE`. Each layer applies its normalizers in canonical order and then stems without an implicit case transform. Qualifiers must be non-blank and unique across every term layer the profile produces. Multiple entries can expose folded and case-preserving identities in one pass. Tokens that normalize to an empty value are omitted from that term layer and its aggregates. |
| Aggregate term vectors | `PIPELINE_STEP_TERM_VECTOR` + `AnalysisProfile.term_vector` | Typed document layer `opennlp:term-vectors` | `source_layer` is a `LayerIdentity`: unset means `STANDARD_LAYER_TOKENS`; `LEMMAS`, `STEMS`, and qualified `TERMS` reuse the corresponding produced document layer as term identity. `FULL` includes one original-text occurrence span per token; `SCORING_ONLY` returns frequencies without spans. The result repeats the resolved source and mode as provenance. |

For a conventional English BM25 index over normalized stems, request
`term_profile: "en"`, select `STANDARD_LAYER_TERMS` with qualifier `STEM`, and use
`TERM_VECTOR_MODE_FULL`. When term identity is an index format contract, define it
explicitly with `term_layers`. For example, a `court-folded` layer can select
`STRIP_INVISIBLE`, `WHITESPACE`, `FULL_CASE_FOLD`, and `ACCENT_FOLD`, followed by a
`PORTER` stemmer. Selecting `STANDARD_LAYER_TERMS` with qualifier `court-folded`
then aggregates exactly that layer. A second `court-cased` entry with only `PORTER`
preserves the comparison identity in the same response. Every posting offset remains
anchored to `raw_text`, and unary and `AnalyzeStream` calls share the same path and
document shape.

#### Custom segmentation engines (SPI)

The `TokenizerSelector.custom` and `SentenceDetectorSelector.custom` cases select open
provider ids. Extension jars implement `TokenizerBackendFactory` or
`SentenceDetectorBackendFactory` and register the implementation under the matching
`META-INF/services/org.apache.opennlp.grpc.spi.model.*BackendFactory` file. Each factory
receives the complete server configuration and may return an empty result when it is not
configured. Returned engines must be safe for concurrent calls. Stateful OpenNLP
implementations can meet that requirement with a thread-local delegate.

The server validates that ids are unique, lower-case, and non-blank at startup. Unknown
custom ids fail with `NOT_FOUND`; setting both `tokenizer_engine` and `tokenizer` fails with
`INVALID_ARGUMENT`. `GetServiceInfo.custom_tokenizer_ids` and
`custom_sentence_detector_ids` advertise exactly the configured extension ids.

### Name finder models (optional)

Name finder models are operator-supplied: unlike the sentence detector, tokenizer,
POS tagger, lemmatizer and language detector, Apache does not distribute NER models as
`opennlp-models-*` artifacts, so there is no default and NER is only available once you
configure model paths. Register classic OpenNLP `.bin` name finder models, one file per
entity type, in the server config. The middle segment of each key becomes the logical
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

Two model-free backends serve entities from user-supplied files, so NER works without any
trained model. A dictionary name finder takes either a serialized OpenNLP dictionary (its XML
declares case sensitivity) or a plain wordlist with one entry per line, matched
case-insensitively; a regex name finder takes one Java regular expression per line, with blank
and `#` comment lines ignored:

```ini
# Every "Kansas City"-style entry in the wordlist becomes a city entity.
model.name_finder_dictionary.city.path=/srv/opennlp/dictionaries/cities.txt
# Every INV-[0-9]+ match becomes an invoice entity with its exact span.
model.name_finder_regex.invoice.path=/srv/opennlp/patterns/invoice.regex
```

Dictionary, regex, classic, and ONNX recognizers share the entity-type namespace and the
`opennlp:entities` layer; the same type served by several engines participates in priority and
`EnginePolicy` routing under the open engine ids `dictionary` and `regex`. Every file-backed
namespace accepts a `.priority` key beside `.path` for that routing, and because a dictionary or
regex match is deterministic, it reports probability 1 when the request asks for probabilities.

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
manual's per-document reset semantics. On `AnalyzeStream`, a configuration with
`clear_adaptive_data: false` routes the stream's documents through a single dedicated
worker, so the accumulated adaptive state is confined to one thread, documents see it
in submission order, and it is released when the stream ends.

> Pair each name finder with a tokenizer trained for the same tokenization scheme.
> The bundled UD English tokenizer works with `opennlp-models` artifacts; legacy
> 1.5 news-domain NER models (`en-ner-person.bin`, etc.) were trained with Penn-style
> tokenization and may perform best with a matching tokenizer override.

#### ONNX name finder models (optional)

Served by the `opennlp-grpc-dl` add-on (bundled in `opennlp-grpc-server-all` and the docker
images); without it, these keys fail startup with `FAILED_PRECONDITION`.

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
They participate in NER exactly like classic models: a client requests `PIPELINE_STEP_NER`
and filters by `ner_entity_types`; the server runs each configured model once, attaches each
entity under the model's own label, and merges the results.

> Requires `opennlp-dl` (OpenNLP 3.0.0) with the thread-safe, multi-type `NameFinderDL`.
> CUDA requires an NVIDIA runtime and the GPU build flavor.

An opt-in end-to-end test exercises this backend against a real model. The `dl-ner` build
profile downloads the ONNX export of `dslim/bert-base-NER` (MIT) from HuggingFace into
`target/` at build time and runs `BasicDocumentAnalyzerDlNerTest`:

```bash
mvn -pl opennlp-grpc/opennlp-grpc-dl -Pdl-ner test -Dtest=BasicDocumentAnalyzerDlNerTest
```

The model is fetched at build time only. It is never bundled into a built artifact and is
not redistributed. Without the profile the test skips (no model present).

#### Custom NER backends (SPI)

Name finder backends are discovered through `java.util.ServiceLoader`, mirroring the
embedding SPI: the built-in classic (`opennlp-me`) and ONNX (`onnx`/`cuda`) backends are
themselves regular consumers of it. To add another backend (a remote NER service, a custom
model format, any inference runtime in any language), ship a jar that implements
`org.apache.opennlp.grpc.spi.model.NerBackendFactory`, registers it in
`META-INF/services/org.apache.opennlp.grpc.spi.model.NerBackendFactory`, and put that jar on the
server classpath. Each factory parses its own configuration namespace and returns
`NerModel` recognizers; the `NameFinderRegistry` aggregates the models from every backend, so
several backends are active at once. A backend that needs the server's sentence detector
obtains it from the supplied `NerBackendContext`. The new backend's entity types then
participate in NER exactly like the built-ins, with no change to the server.

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
configured model is used automatically. `default_id` is only required to disambiguate when
more than one is registered. Categorization is document-level, so it runs the selected model
once over the document's tokens and stores one `DocumentClassification`.

#### ONNX document categorizer models (optional)

Served by the `opennlp-grpc-dl` add-on (bundled in `opennlp-grpc-server-all` and the docker
images); without it, these keys (and the aliased `model.sentiment_dl.*`) fail startup with
`FAILED_PRECONDITION`.

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

These are served by the add-on's own batched ONNX classifier, which tokenizes the raw
document text internally, feeds only the inputs the model declares (so DistilBERT exports
without `token_type_ids` load like BERT exports), windows long inputs and averages their
scores, and classifies a whole document's sentences in a few inference calls. They are
reported in the catalog with `backend_id` `onnx` or `cuda`. An optional
`lowercase=false` keeps case for cased vocabularies. On the `cuda` backend prefer the
fp32 export over an int8 `model_quantized.onnx`: ONNX Runtime's CUDA provider cannot run
most quantized operators and partitions them to the CPU, which turned a 1 ms/sentence
model into a 10 ms/sentence one in testing. They participate in `DOC_CATEGORIZE` exactly like classic models, except that, because
they consume the raw text, they need no upstream `TOKENIZE` and run under a `DOC_CATEGORIZE`-only
profile (classic maxent categorizers still require `TOKENIZE`).

> Requires `opennlp-dl` (OpenNLP 3.0.0). CUDA requires an NVIDIA runtime and the GPU build
> flavor.

#### Custom doc categorizer backends (SPI)

Document categorization backends are discovered through `java.util.ServiceLoader`, mirroring
the NER and embedding SPIs: the built-in classic (`opennlp-me`) and ONNX (`onnx`/`cuda`)
backends are themselves regular consumers of it. To add another backend (a remote classifier,
a custom model format, any runtime in any language), ship a jar that implements
`org.apache.opennlp.grpc.model.DocCategorizerBackendFactory`, registers it in
`META-INF/services/org.apache.opennlp.grpc.model.DocCategorizerBackendFactory`, and put that
jar on the server classpath. Each factory parses its own configuration namespace and returns
`DocCategorizerModel`s; the `DocCategorizerRegistry` aggregates the models from every backend.
The new backend's models then participate in `DOC_CATEGORIZE` exactly like the built-ins, with no
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
the labels come from the model itself. A single configured model is used automatically.
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
categorization is automatically available for sentiment; configure its models under the
`model.sentiment.*` namespace instead of `model.doccat.*`.

### Constituency parsing (optional)

A constituency (phrase-structure) parser builds a full parse tree per sentence when a request
runs `PIPELINE_STEP_PARSE`. Parser models are operator-approved and are not bundled. Install the
standard catalog model from the Models & data workbench and restart, or configure another model:

```ini
model.parser.default.path=/path/to/en-parser-chunking.bin
```

When configured, the server advertises the `en-parse` profile/bundle (sentence detect + tokenize
+ parse). The result is written to `AnnotatedSentence.parse_tree`, which carries two independent
views of the same parse so each client takes whichever fits its language and use:

- **Structured** (`ParseTree.root`): a nested `ParseNode` tree. Each node has a `kind`
  (`NONTERMINAL` phrase or `TERMINAL` token), a `label` (phrase tag like `S`/`NP`/`VP`, or a POS
  tag at terminals), a document `span`, and a `probability`. Terminals also carry `token_index`,
  linking back to the sentence's token list instead of repeating token text.
- **Bracketed** (`ParseTree.penn_treebank`): the standard Penn-Treebank-style string, e.g.
  `(TOP (S (NP (DT The)(NN dog))(VP (VBD barked))))`, the universal interchange/debug form.

Choose the representation(s) per request with `AnalysisOptions.parse_formats`
(`PARSE_FORMAT_STRUCTURED`, `PARSE_FORMAT_BRACKETED`); an empty list defaults to both, and
listing fewer trims the response. Parsing consumes tokens, so a parse profile runs sentence
detection and tokenization first.

The immutable OpenNLP parser model is shared. Each analysis thread receives its own parser instance
because the classic parser's inference state is not thread-safe.

### Dependency parsing and relation extraction (optional)

Dependency parsing adds one labeled, directed arc per token when a request runs
`PIPELINE_STEP_DEPENDENCY_PARSE`. Models are operator-supplied:

```ini
model.dependency_parser.english.path=/path/to/en-dependency.bin
model.dependency_parser.default_id=english
```

When at least one model is configured, the server advertises the `en-dependency` bundle with
sentence detection, tokenization, POS tagging, and dependency parsing. Requests select a model
with `AnalysisProfile.dependency_parser_id`, or use the configured default. The resulting typed
`opennlp:dependencies` layer carries the parser id, backend id, source span, relation label, and
head and dependent token indices. A head index of `-1` represents the sentence root.

`PIPELINE_STEP_RELATION_EXTRACT` runs after named entity recognition and dependency parsing. It
matches caller-provided dependency paths and writes typed edges between entity indices:

```textproto
steps: PIPELINE_STEP_NER
steps: PIPELINE_STEP_DEPENDENCY_PARSE
steps: PIPELINE_STEP_RELATION_EXTRACT
relation_patterns {
  type: "acquisition"
  path: "<nsubj >obj"
  trigger: "acquired"
}
```

Each request may contain at most 128 patterns. The workbench's maximal profile uses neutral
subject-object and subject-oblique patterns, then exposes the output as an entity relation network.

### Shallow (syntactic) chunking (optional)

A `ChunkerME` model groups each sentence's tokens into base phrases (`NP`, `VP`, `PP`, and others)
when a request runs `PIPELINE_STEP_SYNTACTIC_CHUNK`, filling
`AnnotatedSentence.syntactic_chunks`. This is shallow parsing, distinct from
`PIPELINE_STEP_CHUNK`, which is segmentation chunking for embedding. Install the standard catalog
model and restart, or configure another model:

```ini
model.chunker.default.path=/path/to/en-chunker.bin
```

When configured, the server advertises the `en-chunk` profile/bundle (sentence detect + tokenize
+ POS tag + syntactic chunk). The chunker classifies the token **and POS-tag** sequence, so
`SYNTACTIC_CHUNK` requires `POS_TAG` (and thus `TOKENIZE`); requesting it without `POS_TAG` fails
with `FAILED_PRECONDITION`, and requesting it with no chunker configured fails with `NOT_FOUND`.
Each chunk carries its document span and the chunker's phrase tag (`chunk_tag`).

> `ChunkerME` is thread-safe (per-thread state), so one instance is shared across requests.

### Embedding models (optional)

Register ONNX sentence-transformer models in the server config (the `onnx` and `cuda`
engines ship in the `opennlp-grpc-dl` add-on, bundled in `opennlp-grpc-server-all` and the
docker images; configuring them without it fails startup with `FAILED_PRECONDITION`):

```ini
model.embedder.default_id=sentence-transformers
model.embedder.sentence-transformers.onnx.path=/path/to/model.onnx
model.embedder.sentence-transformers.vocab.path=/path/to/vocab.txt
# Optional, with these defaults:
model.embedder.sentence-transformers.lowercase=true
model.embedder.sentence-transformers.pooling=mean
```

Request embeddings by adding `PIPELINE_STEP_EMBED` to the analysis profile and
setting `options.embedding_selector.model_id` (or rely on `default_id` when only
one model is registered). Set `embedding_selector.backend.standard` to pin the
built-in ONNX, CUDA, static-table, TEI, or OpenVINO route. ServiceLoader extensions
use `embedding_selector.backend.custom`. Omit the backend choice to use the
highest-priority compatible route with safe fallback. The older string-valued
`backend_id` remains a compatibility input but cannot be set together with `backend`.
The older `embedding_model_id` field remains as an additive compatibility field.
The same selector shape is used by streaming, chunk, category-chunk, and semantic
chunk requests. Set `options.include_document_centroid = true` when the response
should also contain the mean sentence vector at document granularity. It defaults
to false so an unneeded aggregate is not computed or transmitted. Set
`options.document_centroid_normalization` to `VECTOR_NORMALIZATION_L2` when a
unit-length mean is required for cosine search; unset retains the arithmetic mean.
The result records `vector_normalization`, and a zero mean requested as L2 fails with
`FAILED_PRECONDITION`. Sentence vectors and the optional centroid share the typed
`opennlp:embeddings` layer and are distinguished by `EmbeddingGranularity`.

Every configured ServiceLoader backend is active at the same time. When several
backends serve one logical model, configure their priorities and a shared vector-space
identity. Dimensions and vector-space ids must agree, and automatic fallback occurs only
for `UNAVAILABLE` or `RESOURCE_EXHAUSTED` failures:

```ini
model.embedder.sentence-transformers.onnx.priority=10
model.embedder.sentence-transformers.cuda.priority=20
model.embedder.sentence-transformers.onnx.vector_space_id=minilm-v1
model.embedder.sentence-transformers.cuda.vector_space_id=minilm-v1
```

When `vector_space_id` is omitted the server derives one from the model id and the
artifact hash (for example `sentence-transformers@6fd5d72fe4589f18`), so every route is
complete enough to index into a workspace; the derived space is deliberately narrow to
one artifact. Declare the id explicitly, as above, whenever several engines must share
one space for fallback.

```ini
```

`ListModelBundles` reports every `EmbeddingRoute`, including its backend id, priority,
vector-space id, primary status, and artifact hash when known. Each embedding response
also reports the route that actually produced the vector, including after fallback.

For high-throughput embedding of pre-segmented texts (RAG chunk pipelines and the
like) the `EmbedText` bidi streaming RPC bypasses the document pipeline entirely:
the client streams texts, the server streams one vector per text back in request
order, one embedding model per stream (fixed by the first message). No sentence
detection, no diagnostics; each message's text embeds as one unit. Response writes
are gated on transport readiness with a bounded elastic window, so a client that
stops reading gets its stream closed with RESOURCE_EXHAUSTED instead of growing the
server heap. Throughput against the unary path is measured in
[benchmarks/embedding-throughput](benchmarks/embedding-throughput/README.md).

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
mvn -pl opennlp-grpc/opennlp-grpc-distr -am -Dgpu package
```

The `-Dgpu` switch selects the flavor of the `opennlp-grpc-dl` add-on (and of the
`opennlp-grpc-server-all` assembly built from it); build only the add-on jar with
`mvn -pl opennlp-grpc/opennlp-grpc-dl -Dgpu package`.

```ini
model.embedder.gpu_device_id=0
model.embedder.default_id=sentence-transformers
model.embedder.sentence-transformers.cuda.path=/path/to/model.onnx
model.embedder.sentence-transformers.vocab.path=/path/to/vocab.txt
```

`model.embedder.gpu_device_id` applies to CUDA model paths. Requires an NVIDIA CUDA
runtime on the host. CPU and CUDA routes may coexist by configuring both `.onnx.path`
and `.cuda.path` for the same logical model and declaring the shared vector space above.

#### In-process backend: static embedding tables (optional)

The `opennlp-grpc-backend-static` module serves static (non-contextual) embedding
tables through the `opennlp-embeddings` extension module: a per-token vector table
plus WordPiece tokenization, distilled from a sentence-transformer. Embedding is
tokenize, gather, mean-pool, and normalize; there is no model forward pass and no
native runtime, so the backend runs anywhere the server's JVM runs and one immutable,
thread-safe model instance serves every request thread. Put the module jar (and
`opennlp-embeddings`) on the server classpath and configure one of two forms.

The directory form points at a published model directory and reads the tokenizer and
pooling switches from the model's own `config.json` and `tokenizer_config.json`:

```ini
model.embedder.potion.static.dir=/models/my-static-model
```

The explicit form names the files and switches directly, for models laid out
differently:

```ini
model.embedder.potion.static.safetensors.path=/models/table.safetensors
model.embedder.potion.static.vocab.path=/models/vocab.txt
# Optional, with these defaults:
model.embedder.potion.static.lowercase=true
model.embedder.potion.static.normalize=true
```

Mixing the two forms for one model id fails at startup, as does a `lowercase` or
`normalize` key next to the directory form (the directory's own configuration governs
there). Models are reported with `backend_id` `static`. Throughput and the trade-off
against remote transformer backends are measured in
[benchmarks/embedding-throughput](benchmarks/embedding-throughput/README.md).

#### Remote backends: HuggingFace TEI (optional)

The `opennlp-grpc-backend-tei` module delegates embedding inference to
[HuggingFace Text Embeddings Inference](https://github.com/huggingface/text-embeddings-inference)
instances over their native gRPC API (`-grpc` flavored TEI Docker images). Tokenization,
truncation, pooling and normalization run inside TEI; the OpenNLP server keeps
orchestrating the document pipeline. One TEI instance serves one model, so each model id
maps to one endpoint. Put the module jar on the server classpath and configure:

```ini
model.embedder.minilm.tei.target=localhost:8080
model.embedder.minilm.tei.use_tls=false      # optional, default false
model.embedder.minilm.tei.truncate=true      # optional, default true
model.embedder.minilm.tei.normalize=true     # optional, default true
model.embedder.tei.deadline_ms=30000         # optional
```

Endpoints are validated at startup (TEI `Info` RPC plus one probe embedding that also
determines the vector dimension). Each batch is sent on one bidirectional `EmbedStream`
call: every text streams over that single call on the multiplexed HTTP/2 connection and
TEI returns the vectors in order; TEI applies its own server-side batching.

See [opennlp-grpc-backend-tei/README.md](opennlp-grpc-backend-tei/README.md) for the
full deployment guide: the TEI Docker image matrix (CPU and per-CUDA-architecture GPU
images), multi-model configuration, and the model/device combinations verified by the
live integration tests.

#### Remote backends: OpenVINO Model Server / KServe v2 (optional)

The `opennlp-grpc-backend-openvino` module delegates embedding inference to an
[OpenVINO Model Server](https://docs.openvino.ai/2026/model-server/ovms_what_is_openvino_model_server.html)
or any KServe v2 compatible inference server (Triton, KServe, ...) over the KServe
open inference protocol gRPC API. The served model or OVMS MediaPipe graph must accept a
`BYTES` string tensor and return `FP32` embeddings, i.e. tokenization runs server-side
(for OpenVINO, models converted with `openvino_tokenizers`):

```ini
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

Embedding backends are discovered through `java.util.ServiceLoader`; the TEI and
OpenVINO modules above are regular consumers of this SPI. To add another backend
(DJL, a custom native runtime, or any other remote inference service in any language),
ship a jar that implements
`org.apache.opennlp.grpc.spi.embedding.EmbeddingBackendFactory`, registers it in
`META-INF/services/org.apache.opennlp.grpc.spi.embedding.EmbeddingBackendFactory`, and put
that jar on the server classpath. Its configured models join the aggregate provider
without any server change. Clients can leave route choice to priority/fallback or pin
the backend's open id through `EmbeddingSelector.backend.custom`. The shaded server merges
service descriptors, and the integration suite verifies loading and invoking a provider
compiled and packaged outside this reactor.

### Chunk + embed configs

Request one or more chunking strategies with per-chunk embeddings:

```json
{
  "chunk_embed_configs": [
    {
      "config_id": "sentence-chunks",
      "chunking": {
        "strategy": { "standard": "STANDARD_CHUNKING_STRATEGY_SENTENCE" }
      },
      "embedding_model_ids": ["sentence-transformers"]
    },
    {
      "config_id": "token-chunks",
      "chunking": {
        "strategy": { "standard": "STANDARD_CHUNKING_STRATEGY_TOKEN" },
        "chunk_size": 128,
        "chunk_overlap": 16
      },
      "embedding_model_ids": ["sentence-transformers"]
    }
  ]
}
```

The server auto-runs sentence detection (and tokenization for `token` windows) once,
then returns each strategy as a `chunk_embedding_groups` entry with embeddings
attached inside each chunk. `ChunkEmbeddingGroup.strategy` reports the canonical typed
strategy, including for legacy requests. The older string-valued `algorithm` field
remains a compatibility input but cannot be set together with `strategy`. Standard
sentence, token, semantic, and category identities use the enum case; the custom case
keeps extension strategy ids open.

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

Unary RPC: `org.apache.opennlp.grpc.v1.OpenNlpAnalysisService/AnalyzeDocument`

Send `raw_text` plus a named or inline `AnalysisProfile`, receive an enriched
`OpenNlpDocument` with the annotations selected by the profile: sentences, tokens,
entities, POS tags, lemmas, document classification, per-sentence sentiment, parse trees,
syntactic chunks, embeddings, and chunk/embedding groups. `AnalyzeDocumentResponse`
also includes per-step diagnostics; invalid requests fail with precise gRPC status codes
instead of returning partial results.

For one long document, `AnalyzeDocumentProgressive` returns a server stream. The service
finishes the language, normalization, sentence, and token backbone first, then runs independent
model branches concurrently with at most four admitted per request. Each completed branch emits
complete typed layer snapshots. NER, parsing, sentiment, categorization, chunking, and embeddings
can therefore arrive independently. A non-backbone branch failure is reported as an event while
the other branches continue. The last event carries the canonical response and diagnostics.

For bulk analysis, `AnalyzeStream` runs the same `DocumentAnalyzer` and returns the
same `AnalyzeDocumentResponse` shape. The first request frame carries one
`AnalyzeStreamConfiguration`, which fixes the profile, options, offset encoding, and
chunk configurations for the stream. Later frames carry a caller sequence plus one
`OpenNlpDocument`. Documents run concurrently and responses arrive in completion order,
so clients correlate them by sequence rather than arrival position.

A document-local validation or processing failure returns an `AnalyzeStreamError` for
that sequence and the stream continues. Its `code` is the typed `GrpcStatusCode` enum;
the nonzero values retain the canonical gRPC status numbers. Missing, late, or repeated
configuration is a protocol failure and closes the stream with `INVALID_ARGUMENT`. Inbound demand tracks
the configured worker count, outbound writes wait for transport readiness, and a client
that stops draining responses is closed with `RESOURCE_EXHAUSTED`. The server sends
response headers as soon as the call is accepted, which lets clients open the stream
before submitting its configuration. Client cancellation interrupts active analysis on
a best-effort basis and removes queued documents so abandoned streams release their
worker capacity. Unary and streaming calls share the generic
`DocumentAnalyzer` interface; a provider may override `openSession` to validate or
compile the fixed plan once. `DocumentAnalyzer` is `AutoCloseable`. A
`BasicDocumentAnalyzer` created from a configuration map owns and closes its model cache;
constructors that accept a `ModelBundleCache` leave that cache under caller ownership.

The server also registers the standard `grpc.health.v1.Health` service. Check
`org.apache.opennlp.grpc.v1.OpenNlpAnalysisService` or the empty service name for
whole-server readiness.
