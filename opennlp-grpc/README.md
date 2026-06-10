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

## Modules

- **opennlp-grpc-api** - v1 protos (`org.apache.opennlp.grpc.v1`) and generated stubs
- **opennlp-grpc-service** - `OpenNlpGrpcServer` and `OpenNlpAnalysisService` implementation
- **examples** - client samples (v1 Python client TBD)

## Build

```bash
mvn clean install
```

Requires JDK 21+.

## Run the server

```bash
java -jar opennlp-grpc-service/target/opennlp-grpc-server-3.0.0-SNAPSHOT.jar
```

Options:

- `-p, --port` - listen port (default `7071`)
- `-c, --config` - key=value config file

Example config (`key=value`, `#` comments):

```ini
server.enable_reflection=false
server.max_inbound_message_size=10485760

# Optional explicit model overrides. When omitted, the en sentence-detector and
# tokenizer load from the classpath via the opennlp-models-* runtime deps.
# model.sentence_detector.path=/path/to/en-sent.bin
# model.tokenizer.path=/path/to/en-token.bin
```

By default no configuration is required: the server loads the bundled English
sentence-detector and tokenizer from the classpath.

> v1 note: this minimal slice implements sentence detection, tokenization,
> probability reporting, `max_text_length`, offset encoding selection, and the
> default `en-basic` model bundle. Unsupported backends, ONNX embedding model
> selection, non-default bundles, and chunk/embed configs are rejected explicitly
> instead of being silently ignored.

## v1 API

Primary RPC: `org.apache.opennlp.grpc.v1.OpenNlpAnalysisService/AnalyzeDocument`

Send `raw_text`, receive an enriched `OpenNlpDocument` (sentences, tokens, diagnostics; chunk/embed groups as implemented).
