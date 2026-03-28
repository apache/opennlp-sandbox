# Apache OpenNLP gRPC API

This module contains the [gRPC](https://grpc.io) schema used in Apache OpenNLP to provide a service side gRPC backend.

An automatically generated overview of the endpoints and messages can be found [here](opennlp)

# Main concepts

The endpoints and messages described by the API are meant to be a minimum.
It does not support every feature of Apache OpenNLP at the moment, but is open for enhancement or further improvements.

# Maven dependencies

The Java code generated from the schema is available as a Maven dependency.

```
		<dependency>
			<groupId>org.apache.opennlp</groupId>
			<artifactId>opennlp-grpc-api</artifactId>
			<version>VERSION</version>
		</dependency>
```

# Code generation

The Java code can be (re)generated as follows; [docker-protoc](https://github.com/namely/docker-protoc) is used to generate the code for Java : 

```powershell
docker run -v ${PWD}:/defs namely/protoc-all -f opennlp.proto -l java -o src/main/java
```

Since the Java code is provided here and the corresponding JARs will be available from Maven, regenerating from the schema is not necessary.

For other languages, you need to generate the code stubs yourself, as shown here for Python

```
python3 -m venv grpc
python3 -m pip install grpcio-tools
mkdir python
python3 -m grpc_tools.protoc -I. --python_out=python --grpc_python_out=python opennlp.proto
```

# Documentation generation

```powershell
docker run --rm -v ${PWD}:/out -v ${PWD}:/protos pseudomuto/protoc-gen-doc --doc_opt=markdown,opennlp.md 
```

The current version of the documentation can be found [here](opennlp)

