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

 
# Apache OpenNLP gRPC - Python Client

This client was generated using the gRPC tools and the schema provided in the opennlp-grpc-api module.

```
python3 -m pip install grpcio-tools
mkdir python
python3 -m grpc_tools.protoc -I. --python_out=python --grpc_python_out=python opennlp.proto
```

## Running examples with uv

Install dependencies:

```bash
uv sync
```

Run POS tagging:

```bash
uv run python main.py
```

Run sentence detection:

```bash
uv run python sentdetect_example.py
```

(Generated gRPC stubs can be regenerated using the existing `grpc_tools.protoc` workflow above.)