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

## Hardening

The compose file runs the stack fully hardened, and the same flags work with
plain `docker run`:

```bash
docker run --rm \
  --read-only --tmpfs /tmp \
  --cap-drop=ALL --security-opt no-new-privileges \
  -p 127.0.0.1:7071:7071 -p 127.0.0.1:7072:7072 \
  -v opennlp-state:/srv/opennlp \
  opennlp-grpc-demo
```

The image needs no Linux capabilities, no privilege escalation, and no
writable filesystem beyond `/tmp` and the `/srv/opennlp` state volume; the
process runs as the non-root `opennlp` user.

## Testing the image

`docker/test-image.sh` builds the image from the packaged jars and asserts,
under exactly the hardened flags above: the healthcheck turns healthy, the
process is non-root, the gateway answers `/healthz` and a real analyze round
trip through the gRPC server, a mounted `server.properties` is honored, and
`docker stop` drains within the shutdown grace period.

```bash
mvn package -DskipTests   # or a full build; the script only needs the jars
bash docker/test-image.sh
```

## NVIDIA GPU flavor (ONNX Runtime CUDA)

`docker/Dockerfile.gpu` builds the same stack on the `nvidia/cuda` cuDNN
runtime base so embedding models run on the ONNX Runtime CUDA execution
provider. It needs jars built with the `gpu` Maven flavor, which replaces the
`onnxruntime` jar inside the shaded server jar with `onnxruntime_gpu`, and the
[NVIDIA container toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/)
on the host. The image is `linux/amd64` only; CUDA 12 and cuDNN 9 come from
the base image and the host provides only the driver.

```bash
mvn clean install -Dgpu                                   # from opennlp-grpc/
export OPENNLP_GPU_MODELS=/path/to/models   # holds minilm/model.onnx + vocab.txt
cd docker
docker compose -f docker-compose.gpu.yml up --build
```

`config/server.properties.gpu` registers the mounted model on the `cuda`
backend through `model.embedder.<id>.cuda.path`; every CPU feature of the
default image keeps working unchanged. GPU access needs no extra Linux
capabilities, so the hardened flags stay identical.

## OpenVINO flavor (remote OpenVINO Model Server)

`docker-compose.openvino.yml` pairs the standard CPU image with an
[OpenVINO Model Server](https://docs.openvino.ai/2026/model-server/ovms_what_is_openvino_model_server.html)
container that serves a fused text-to-vector graph over the KServe v2 gRPC
API; see [the backend README](../opennlp-grpc-backend-openvino/README.md).
Export the model once, then start the pair:

```bash
cd ../opennlp-grpc-integration-tests
./scripts/ovms-server.sh prepare --model sentence-transformers/all-MiniLM-L6-v2
cd ../opennlp-grpc/docker
docker compose -f docker-compose.openvino.yml up --build
```

The inference API stays on the internal compose network. OVMS runs on CPU
everywhere; on Intel GPU hosts use the `openvino/model_server:latest-gpu`
image, map `/dev/dri` into the `ovms` service (with the render group in
`group_add`), and add `--target_device HETERO:GPU,CPU`. HETERO matters: the
fused tokenizer's string operations only compile on CPU, while the
transformer layers run on the GPU. There is no CUDA variant of OVMS.

## Experimental native image

`docker/native/` compiles the server and gateway into GraalVM native binaries
and packages them on the CUDA base: about 2 seconds from `docker start` to a
healthy hardened stack (versus about 35 for the JVM pair) at around 440 MiB
total container memory, with CUDA embeddings working. See
[docker/native/README.md](native/README.md) for the build, the curated
reachability metadata, and the known constraints (build-time backend linking,
explicit classic-model paths, slf4j-simple logging).

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

The image runs the `opennlp-grpc-server-all` assembly, which bundles every
in-tree add-on (ONNX Runtime inference, static tables, TEI, OpenVINO), so a
`model.embedder.<id>.static.dir` or a remote TEI/OpenVINO target is
configuration alone; unconfigured providers register nothing. Third-party
add-on jars dropped into `/opt/opennlp/backends/` join the classpath the same
way.

Environment variables:

- `OPENNLP_GRPC_PORT` / `OPENNLP_HTTP_PORT`: listen ports (defaults 7071/7072)
- `OPENNLP_SERVER_OPTS` / `OPENNLP_WEBAPP_OPTS`: extra command-line arguments
  for the server and the gateway
- `JDK_JAVA_OPTIONS`: JVM options for both processes

Stopping the container stops the gateway first, then lets the server drain
in-flight RPCs through its configured `server.shutdown_grace_seconds`.
