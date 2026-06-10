# OpenNLP gRPC Python client (v1)

Generate stubs from the v1 protos:

```bash
cd ../../opennlp-grpc-api
python -m grpc_tools.protoc \
  -I src/main/proto \
  --python_out=../examples/python-client \
  --grpc_python_out=../examples/python-client \
  src/main/proto/org/apache/opennlp/grpc/v1/opennlp_document.proto \
  src/main/proto/org/apache/opennlp/grpc/v1/opennlp_pipeline.proto \
  src/main/proto/org/apache/opennlp/grpc/v1/opennlp_service.proto
```

A sample `AnalyzeDocument` client will be added here once the v1 Python workflow is finalized.
