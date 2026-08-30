# Roadmap

This theme collects the research tracks around the OpenNLP gRPC stack that are
in flight or queued, so that the per-tab audits under `../` can point at them
instead of restating them. Each track lists what exists today in this reactor,
what is being researched, and the decision it is waiting on. Dates are the
state on 2026-08-28.

## Tracks

### 1. Provider separation (the SPI families)

Shipped in four phases: `opennlp-grpc-spi` holds seven provider families
(embedding, model, search, vocabulary, catalog, format, sink) and the service
loads implementations through `ServiceLoader`. Add-ons ship as separate jars
(`opennlp-grpc-dl`, `-installer`, `-search-turboquant`, `-search-lucene`,
`-store-s3`, `-formats`, `-sink-grpc`) and fail honestly when absent, naming
the missing jar.

Open research:

- A capability manifest per add-on (which pipeline steps, search kinds, or
  storage schemes it unlocks) so the gateway can advertise what is loaded and
  the workbench can brown out what is not. See `../models-and-data-tab/goals`.
- Versioned SPI contracts: a compatibility test-kit that any third-party
  provider can run (the `service` test-jar already does this for turboquant).

### 2. Model distribution and the model zoo

Today: `opennlp-grpc-installer` ships a static catalog (classic OpenNLP 1.5
models, UD language packs, MiniLM and multilingual teachers, the seven English
name finders) with SHA-256 pinning and restart-only roles.

Being researched:

- A manifest format that a trained or installed model can be exported with
  (fields, hashing, licensing, versioning) and that can be pushed to an object
  store through `opennlp-grpc-store-s3` and re-imported as a catalog. Candidate
  standards are compared in `../models-and-data-tab/findings/model-zoo-and-export.md`.
- An "export model" action on the Trainer and Models tabs.
- Catalog roles for document categorizer and sentiment models (only name
  finders, language packs, embedders and teachers have roles today).

### 3. Storage back-ends

Today: local filesystem for indexes and vocabularies; S3-compatible vocabulary
store add-on.

Being researched: index snapshots on object storage (checkpoint to a bucket,
seal from a bucket), and a storage capability that the Lifecycle tab can show
instead of failing on a missing jar.

### 4. Search kinds and lifecycle

Today: dynamic (in-memory, near-real-time) workspaces, checkpoints,
sealed immutable bundles served by TurboQuant or Lucene, logical aliases,
collections, `max_top_k` raised to 1,000 (ceiling 10,000).

Being researched: hybrid lexical plus vector ranking across both providers,
a single query model (`docs/rfc/opennlp-search-query-model.md`), and the
vocabulary the UI should use for each state (see `../lifecycle-tab`).

### 5. Output and delivery

Today: output formatter SPI with protobuf binary, protobuf JSON and TSV
formatters; document sink SPI with a gRPC streaming sink and a Python receiver
example.

Queued: CSV, Markdown, WARC and CoNLL formatters; file and message-queue sinks;
an export-format picker on the Analyze tab; formatter sets for search replies.

### 6. Throughput and accelerators

Today: token-budgeted ONNX sub-batching for embeddings and document
categorizers, batched sentiment, CUDA and OpenVINO containers, deadlines that
scale with input size, fp32 guidance for CUDA (int8 exports partition to CPU).

Being researched: a size ladder benchmark that pins per-megabyte cost per
step, and adaptive batch budgets from the runtime's reported memory.

### 7. Native image

Today: experimental GraalVM native binaries for the server and the gateway
(`docker/native`), with shared-arena support for the Netty FFM path on JDK 25.

Being researched: reachability metadata for optional add-ons, and whether the
native gateway should be the default demo image.

### 8. Gateway and client generation

Today: the JSON gateway maps protos by hand; the workbench's TypeScript types
are hand-written; Python, Node.js, Java and Go clients are documented.

Being researched: generating the workbench's request and response types from
the proto descriptor set, and a contract test that round-trips every workbench
request fixture through the gateway parser and the service validators. This is
the main lever against the drift bugs catalogued in `../test-coverage`.

### 9. Workbench experience

The subject of the per-tab audits in this directory: standard terminology,
browned-out features with redirects to Models & data, first-run explainers for
workspace, collection, checkpoint and seal, and per-tab end-to-end tests.

## Reading order

1. `../industry-terminology` for the glossary decisions that every other theme
   depends on.
2. `../test-coverage` for the gates that keep the rest from regressing.
3. The tab themes in navigation order: analyze, workflows, corpus-search,
   workspace-search, models-and-data, trainer, lifecycle.
