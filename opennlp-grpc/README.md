# OpenNLP gRPC - Proof of Concept

This project demonstrates a proof of concept for creating a backend powered by Apache OpenNLP using gRPC. It comprises of three main modules : 

- **opennlp-grpc-api**
- **opennlp-grpc-service**
- **examples**

## Modules Overview

1. **opennlp-grpc-api**:
    - Contains the gRPC schema for OpenNLP services.
    - Includes generated Java stubs.
    - Provides a README with instructions on generating code stubs for various languages and auto-generated documentation.
 

2. **opennlp-grpc-service**:
    - Features a server implementation.
    - Offers an initial service implementation for POS tagging.

3. **examples**:
    - Provides a sample implementation for interacting with the OpenNLP server backend via gRPC in Python.

## Getting Started

Follow these steps to set up and run the OpenNLP gRPC proof of concept project:

### Prerequisites
Before you begin, ensure you have the following installed on your system:

- Java Development Kit (JDK) 17 or later
- Apache Maven (for building Java components)
- Docker for running the gRPC tools if modifications to the .proto files are needed

You can build the project by running

```
mvn clean install
```

### Running the gRPC Backend

Start the server: Use the following command to run the server with default settings:

```bash
java -jar target/opennlp-grpc-server-2.5.4-SNAPSHOT.jar
```

Configure server options: 

The server supports several command-line options for customization:

```bash
-p or --port: Port on which the server will listen on (default: 7071).
-c or --config: Path to a configuration file.
```

Example with custom options:

```bash
java -jar target/opennlp-grpc-server-1.0-SNAPSHOT.jar -p 8080 -h 127.0.0.1 -c ./server-config.ini
```

Sample configuration file: 

If using a configuration file, it should be in the format:

```bash
# Set to true to enable gRPC server reflection, see https://grpc.io/docs/guides/reflection/
server.enable_reflection = false

# Folder used to scan for models
model.location=extlib
# Set to true to recursively scan for models inside the model.location folder.
model.recursive=true
# A wildcard to search for models in the model.location folder.
model.pos.wildcard.pattern=opennlp-models-pos-*.jar
model.tokenizer.wildcard.pattern=opennlp-models-tokenizer-*.jar
model.sentdetect.wildcard.pattern=opennlp-models-sentdetect-*.jar
```

#### Models

To ensure the server automatically loads models, these must be placed in the `extlib` (or in the location configured via `model.location`) directory. 

## Building a Custom Client in another Programming Language

Details can be found in the README of the [opennlp-grpc-api module](opennlp-grpc-api/README.md).

## Supported Features

Currently, the server supports the following features:

- POS Tagging (using the Universal Dependencies tag format)
- Tokenization
- Sentence Detection


