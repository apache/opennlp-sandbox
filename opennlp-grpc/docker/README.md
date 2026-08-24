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

# Docker demonstration stack

One container runs the OpenNLP gRPC server and the optional JSON gateway with
the browser workbench. The base image is multi-arch (`linux/amd64` and
`linux/arm64`), so the same build works out of the box on Linux and on macOS
Docker Desktop, including Apple silicon. The default configuration needs no
native libraries or external services: the bundled language detector and the
English sentence, token, POS, and lemma models load from the server jar.

## Build and run

Build the repository first, then build and start the stack:

```bash
mvn clean install          # from opennlp-grpc/
cd docker
docker compose up --build
```

Open <http://127.0.0.1:7072/> for the workbench; gRPC clients use
`127.0.0.1:7071`. Plain `docker` works too:

```bash
docker build -f docker/Dockerfile -t opennlp-grpc-demo .   # from opennlp-grpc/
docker run --rm -p 127.0.0.1:7071:7071 -p 127.0.0.1:7072:7072 opennlp-grpc-demo
```

The gateway has no authentication and exposes state-changing operations, so
keep the published ports on loopback as shown, or place the container behind
an authenticated TLS reverse proxy.

## Configuration and state

`/srv/opennlp` is a volume for server-owned state (model catalog downloads,
vocabulary artifacts, persisted indexes, trained models). When
`/srv/opennlp/server.properties` exists it is passed to the server as its
configuration file, so every `key=value` setting from the
[repository README](../README.md) works unchanged:

```bash
docker run --rm \
  -p 127.0.0.1:7071:7071 -p 127.0.0.1:7072:7072 \
  -v "$PWD/server.properties:/srv/opennlp/server.properties:ro" \
  -v "$PWD/models:/models:ro" \
  opennlp-grpc-demo
```

The image also carries the optional embedding backends (static tables, TEI,
OpenVINO) on the server classpath, so a `model.embedder.<id>.static.dir` or a
remote TEI/OpenVINO target is configuration alone; unconfigured providers
register nothing.

Environment variables:

- `OPENNLP_GRPC_PORT` / `OPENNLP_HTTP_PORT`: listen ports (defaults 7071/7072)
- `OPENNLP_SERVER_OPTS` / `OPENNLP_WEBAPP_OPTS`: extra command-line arguments
  for the server and the gateway
- `JDK_JAVA_OPTIONS`: JVM options for both processes

Stopping the container stops the gateway first, then lets the server drain
in-flight RPCs through its configured `server.shutdown_grace_seconds`.
