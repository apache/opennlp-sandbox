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

For a runnable client covering bidirectional document analysis, vocabulary learning,
optional model distillation, dynamic indexing, and search, see
[`lifecycle_e2e.py`](../../opennlp-grpc-integration-tests/scripts/lifecycle_e2e.py).
It loads the descriptor set packaged in the shaded server JAR and constructs requests
dynamically, so it does not require checked-in generated Python sources. The integration
test README documents the exact opt-in command and teacher configuration.
