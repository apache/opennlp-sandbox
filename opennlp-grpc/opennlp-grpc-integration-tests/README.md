# OpenNLP gRPC Integration Tests

Black-box integration tests for the deployable `opennlp-grpc-server` artifact. The
tests launch the shaded SNAPSHOT jar as a separate JVM process (with the
`opennlp-grpc-backend-tei` module on its classpath) and exercise it over real network
sockets:

```
test client --gRPC--> opennlp-grpc-server process --gRPC--> TEI embedding backend
```

## Default build (no Docker required)

`OpenNlpGrpcServerLiveIT` runs in every `mvn verify`. It starts a stub TEI gRPC server
inside the test JVM as the remote embedding backend and verifies service info, the
model catalog (including `backend_id`), the bundled-model fallback (no model paths are
configured, so the server loads its sentence detector and tokenizer from its own shaded
jar), sentence embeddings, chunk+embed groups, and error mapping.

Because the tests spawn the *packaged* jars, build the reactor first:

```bash
mvn -pl opennlp-grpc verify          # from the sandbox root, or simply:
cd opennlp-grpc && mvn verify
```

The harness puts both remote backend jars (`opennlp-grpc-backend-tei`,
`opennlp-grpc-backend-openvino`) on the spawned server's classpath, so every live test
also exercises `ServiceLoader` discovery with multiple backends present.

## Live tests against a real TEI server (opt-in)

`RealTeiServerLiveIT` runs only when `OPENNLP_TEI_TARGET` is set. It asserts properties
only a real embedding model can deliver: a substantial embedding dimension in the model
catalog, L2-normalized vectors, and topical similarity ordering (related sentences are
closer than unrelated ones).

Start a real TEI server with the helper script — CPU or GPU:

```bash
./scripts/tei-server.sh start --cpu                  # default model: all-MiniLM-L6-v2
./scripts/tei-server.sh start --gpu --port 18081     # requires nvidia-container-toolkit
./scripts/tei-server.sh start --cpu --model BAAI/bge-base-en-v1.5
```

The script pulls the matching `-grpc` flavored TEI image, caches model downloads under
`~/.cache/opennlp-grpc-it-tei-data`, and waits until TEI reports ready. Then:

```bash
OPENNLP_TEI_TARGET=localhost:18080 mvn -pl opennlp-grpc/opennlp-grpc-integration-tests verify
```

Tear down with:

```bash
./scripts/tei-server.sh stop
```

## Live tests against a real OpenVINO Model Server (opt-in)

`RealOpenVinoServerLiveIT` runs only when `OPENNLP_OVMS_TARGET` is set and makes the
same semantic assertions through the KServe v2 gRPC API.

OVMS serves raw tensor models, so the helper script first exports the embedding model
as a single OpenVINO graph (HuggingFace tokenizer fused via `openvino_tokenizers`, plus
mean pooling and L2 normalization) with a string input — see the
`opennlp-grpc-backend-openvino` README for details. Requires Docker and
[uv](https://docs.astral.sh/uv/):

```bash
./scripts/ovms-server.sh prepare                  # one-time model export (~MiniLM default)
./scripts/ovms-server.sh start --cpu              # or --gpu on Intel GPU hosts (no CUDA variant)
OPENNLP_OVMS_TARGET=localhost:19000 mvn -pl opennlp-grpc/opennlp-grpc-integration-tests verify
./scripts/ovms-server.sh stop
```

Both opt-in suites can run in the same build by setting both environment variables.
