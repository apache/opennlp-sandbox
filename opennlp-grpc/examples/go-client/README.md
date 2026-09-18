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

# Go quickstart

The Go twin of the [Python quickstart](../python-client/README.md). Stubs are
generated locally from the v1 protos in
`../../opennlp-grpc-api/src/main/proto`; the `gen/` directory is disposable.
Requires Go 1.23+, `protoc`, `protoc-gen-go`, and `protoc-gen-go-grpc`:

```bash
go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
```

## Run

Start the gRPC server first (see the [repository README](../../README.md); the
[Docker demonstration stack](../../docker/README.md) also works). The search
half of the example needs a configured embedding model; analysis alone does
not. Then, from this directory:

```bash
./generate.sh
go run . -cleanup
```

Options:

- `-target host:port` - gRPC server (default `localhost:7071`)
- `-embedding-model id` - logical embedding model; inferred when exactly one
  is configured
- `-cleanup` - delete the process-local index after displaying results

The example:

1. Discovers the service and configured embedding models.
2. Analyzes three identified documents into the typed document shape.
3. Produces sentence chunks and overlapping token-window chunks.
4. Creates a bounded, process-local TurboQuant index in the Java server.
5. Sends a document-shaped semantic query to that server.
6. Prints every ranked hit supported by the TurboQuant provider.

Search is not performed in the Go process. The client sends documents and
requests over gRPC; the provider selected by the Java server owns the vectors
and ranking. Without `-cleanup`, the index remains available until explicitly
deleted or the server exits, so the same corpus can then be explored in the
browser workbench's Corpus search tab.
