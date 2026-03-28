# Apache OpenNLP gRPC - Examples

This repository contains examples for the Apache OpenNLP gRPC project.

For other languages, you need to generate the code stubs yourself, as shown here for Python

```
python3 -m pip install grpcio-tools
mkdir python
python3 -m grpc_tools.protoc -I. --python_out=python --grpc_python_out=python opennlp.proto
```

# Documentation generation

```powershell
docker run --rm -v ${PWD}:/out -v ${PWD}:/protos pseudomuto/protoc-gen-doc --doc_opt=markdown,opennlp.md 
```

The current version of the documentation can be found [here](opennlp)

