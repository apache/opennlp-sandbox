# Apache OpenNLP gRPC - Python Client

This client was generated using the gRPC tools and the schema provided in the opennlp-grpc-api module.

```
python3 -m pip install grpcio-tools
mkdir python
python3 -m grpc_tools.protoc -I. --python_out=python --grpc_python_out=python opennlp.proto
```