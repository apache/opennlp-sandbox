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

# OpenNLP gRPC examples

- [Python quickstart](python-client/README.md): analyze typed documents, build a
  process-local TurboQuant index, search it through the Java server, and then
  extend the flow through vocabulary learning and static-model distillation.
- [Node.js quickstart](node-client/README.md): the same analyze, index, and
  search flow with runtime proto loading and no code generation step.
- [Java quickstart](java-client/README.md): the same flow as a standalone Maven
  project over the generated blocking stubs from `opennlp-grpc-api`.
- [Go quickstart](go-client/README.md): the same flow with locally generated
  `protoc-gen-go` stubs; `generate.sh` maps every proto in one step.
- [Protocol stub generation](../opennlp-grpc-api/README.md): generate clients for
  other supported languages directly from the v1 protos.
- Language-neutral HTTP: the optional
  [web application](../opennlp-grpc-webapp/README.md) exposes the same
  contracts as a protobuf JSON gateway, so `curl` and any HTTP client work
  without gRPC tooling.

The Python, Node.js, Java, and Go quickstarts print identical output for the
same server, so they double as a cross-language contract check.

Legacy per-tool examples were removed. New examples use the document-centric v1
contracts so one client can compose analysis, training, and search.
