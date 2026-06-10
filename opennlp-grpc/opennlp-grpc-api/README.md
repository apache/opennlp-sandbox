# Apache OpenNLP gRPC API

v1 protobuf definitions and generated Java stubs for the document-centric OpenNLP gRPC API.

**Package:** `org.apache.opennlp.grpc.v1`

**Protos:** `src/main/proto/org/apache/opennlp/grpc/v1/`

- `opennlp_document.proto` - `OpenNlpDocument`, spans, tokens, chunks, embeddings
- `opennlp_pipeline.proto` - profiles, pipeline steps, model bundles, inference backends
- `opennlp_service.proto` - `OpenNlpAnalysisService` RPCs

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
