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

# Quickstart

From a clean checkout to analyzing, training, and searching in a few minutes.
The [README](README.md) is the complete reference; this page is the shortest
path through it.

## 1. Build

JDK 21+ and Maven are required. Install the pinned OpenNLP snapshot the
service builds against (see [Build](README.md#build) for the helper step),
then:

```bash
mvn clean install
```

## 2. Run

With Docker (Linux and macOS, out of the box):

```bash
cd docker
docker compose up --build
```

Or directly:

```bash
java -jar opennlp-grpc-service/target/opennlp-grpc-server-3.0.0-SNAPSHOT.jar
java -jar opennlp-grpc-webapp/target/opennlp-grpc-webapp-3.0.0-SNAPSHOT.jar
```

Either way the gRPC service listens on `127.0.0.1:7071` and the browser
workbench is at <http://127.0.0.1:7072/>. No configuration is needed: the
bundled language detector and English sentence, token, POS, and lemma models
load from the jar.

## 3. Five minutes in the browser

Every tab has a "How to use" callout with these steps and the matching API
calls. The complete lifecycle, using nothing but public-domain sample data:

1. **Analyze**: press **Load Alice novel**, then **Analyze text**. Explore the
   Document, Chunks, Heatmap, Graph, and Protobuf JSON projections; click any
   annotation for its exact span and payload.
2. **Models & data**: download a verified embedding model from the pinned
   catalog (for example Potion Base 8M), or train your own next.
3. **Trainer**: learn a vocabulary from pasted documents and distill a static
   embedding model against a catalog teacher; it serves immediately.
4. **Analyze** again with the embedding model selected, then press
   **Add to server workspace**.
5. **Workspace search**: ask a natural-language question; the server ranks
   every chunk and links each hit to its source span.
6. **Lifecycle**: persist the workspace so it survives restarts.

## 4. First API call

The gateway mirrors the gRPC contract as protobuf JSON:

```bash
curl -s -X POST -H 'Content-Type: application/json' \
  http://127.0.0.1:7072/api/v1/analyze \
  -d '{"document":{"docId":"hello","rawText":"Alice followed the White Rabbit."},
       "profile":{"steps":["PIPELINE_STEP_SENTENCE_DETECT","PIPELINE_STEP_TOKENIZE",
                           "PIPELINE_STEP_POS_TAG","PIPELINE_STEP_LEMMATIZE"]}}'
```

For gRPC proper, four runnable quickstarts perform the same analyze, index,
and search flow with identical output:

| Language | Where | Stubs |
|---|---|---|
| Python | [examples/python-client](examples/python-client/README.md) | `grpcio-tools` |
| Node.js | [examples/node-client](examples/node-client/README.md) | runtime proto loading |
| Java | [examples/java-client](examples/java-client/README.md) | `opennlp-grpc-api` artifact |
| Go | [examples/go-client](examples/go-client/README.md) | `./generate.sh` |

## 5. Where next

- [Run the server](README.md#run-the-server): configuration keys, model
  paths, operator limits.
- [Build and explore a bounded legal-passage index](README.md#build-and-explore-a-bounded-legal-passage-index):
  the full corpus-to-browser tutorial on real data.
- [German end to end](docs/tutorials/german-end-to-end.md): install a language
  pack from the catalog and search German with a multilingual embedding model,
  with no configuration file edits.
- [docker/README.md](docker/README.md): hardened deployment and image tests.
- [opennlp-grpc-api/README.md](opennlp-grpc-api/README.md): the v1 protos and
  stub generation for any other language.
