# Apache OpenNLP gRPC API

v1 protobuf definitions and generated Java stubs for the document-centric OpenNLP gRPC API.

**Package:** `org.apache.opennlp.grpc.v1`

**Protos:** `src/main/proto/org/apache/opennlp/grpc/v1/`

- `opennlp_document.proto` - `OpenNlpDocument`, spans, tokens, chunks, embeddings
- `opennlp_pipeline.proto` - profiles, pipeline steps, model bundles, inference backends
- `opennlp_service.proto` - unary and streaming `OpenNlpAnalysisService` RPCs

`AnalyzeStream` is the bulk document-analysis RPC. Send exactly one
`AnalyzeStreamConfiguration` first, then any number of sequenced
`AnalyzeStreamDocument` messages. Responses arrive in completion order and carry either
the same `AnalyzeDocumentResponse` used by unary analysis or a per-document error.

`AnalyzeDocumentProgressive` analyzes one document while returning ordered events. It
first acknowledges the source document, then emits complete annotation-layer snapshots
as independent analysis branches finish. A branch failure is an event and does not stop
unrelated work. The terminal event contains the canonical `AnalyzeDocumentResponse`.

Every produced annotation is available through `OpenNlpDocument.layers`. The payload
type is selected by `AnnotationLayer.values`, and `LayerIdentity.kind` distinguishes a
closed `StandardLayer` from an open namespaced custom id. Standard layer families use
`LayerIdentity.qualifier` for their variable component. For example,
`opennlp:terms:FULL_CASE_FOLD` is `STANDARD_LAYER_TERMS` qualified by
`FULL_CASE_FOLD`. The string `AnnotationLayer.id` remains available as the stable lookup
key for older clients. `GetServiceInfo.supported_layers` exposes the standard set.
`AnalysisProfile.term_layers` can produce caller-qualified term layers from typed
normalizers and an optional typed stemmer. Term vectors select those results
through the same `LayerIdentity`, so analyzer configuration and aggregate provenance
remain part of the document contract. Tokens whose configured transformation produces
an empty value are omitted from that qualified layer and from aggregates sourced from it.

`AnalysisProfile.tokenizer` and `sentence_detector` use a strongly typed standard enum
case plus an open custom provider-id case. Standard tokenizers cover model, UAX #29,
whitespace, simple, and lattice segmentation; standard sentence detection covers model
and newline modes. Existing clients may continue sending `tokenizer_engine`. Servers
advertise configured custom ids through `GetServiceInfo`.

Document centroids are opt-in through `AnalysisOptions.include_document_centroid`.
`document_centroid_normalization` selects either the raw arithmetic mean or typed L2
normalization, and both `EmbeddingResult` and its document-layer
`EmbeddingAnnotation` retain the applied `VectorNormalization` value.

## Maven dependency

```xml
<dependency>
  <groupId>org.apache.opennlp</groupId>
  <artifactId>opennlp-grpc-api</artifactId>
  <version>VERSION</version>
</dependency>
```

## Code generation

Java stubs are generated at build time via `protobuf-maven-plugin`:

```bash
mvn -pl opennlp-grpc-api compile
```

Generated sources: `target/generated-sources/protobuf/java/org/apache/opennlp/grpc/v1/`

## Lint

```bash
buf lint
```

Runs Buf STANDARD rules using `buf.yaml` in this module.

## Other languages

Generate client stubs from the v1 protos under `src/main/proto`. Example for Python:

```bash
python -m grpc_tools.protoc \
  -I src/main/proto \
  --python_out=python \
  --grpc_python_out=python \
  src/main/proto/org/apache/opennlp/grpc/v1/opennlp_document.proto \
  src/main/proto/org/apache/opennlp/grpc/v1/opennlp_pipeline.proto \
  src/main/proto/org/apache/opennlp/grpc/v1/opennlp_service.proto
```

See `docs/rfc/opennlp-grpc-design.md` for the full API contract.
