# OpenNLP gRPC Python quickstart

These examples use ordinary generated Python stubs and `grpcio`. They are the
shortest path from a running OpenNLP gRPC server to document analysis and
server-side semantic search.

## Prerequisites

- Python 3.10 or newer
- [uv](https://docs.astral.sh/uv/)
- An OpenNLP gRPC server listening on `localhost:7071`
- At least one embedding model configured in that server

The main [OpenNLP gRPC README](../../README.md#run-the-server) covers the server,
bundled language models, richer demo resources, and embedding-provider
configuration. No Python package is installed into the server.

## Generate the Python stubs

From this directory:

```bash
uv sync --locked
mkdir -p generated
uv run python -m grpc_tools.protoc \
  -I ../../opennlp-grpc-api/src/main/proto \
  --python_out=generated \
  --grpc_python_out=generated \
  ../../opennlp-grpc-api/src/main/proto/org/apache/opennlp/grpc/v1/*.proto
export PYTHONPATH="$PWD/generated${PYTHONPATH:+:$PYTHONPATH}"
```

The generated directory is disposable. Regenerate it whenever the v1 protos
change.

## Analyze, index, and search

```bash
uv run python analyze_and_search.py --cleanup
```

If the server advertises more than one embedding model, select one explicitly:

```bash
uv run python analyze_and_search.py \
  --embedding-model legal-minilm-full \
  --cleanup
```

The example:

1. Discovers the service and configured embedding models.
2. Analyzes three identified documents into the typed document shape.
3. Produces sentence chunks and overlapping token-window chunks.
4. Creates a bounded, process-local TurboQuant index in the Java server.
5. Sends a document-shaped semantic query to that server.
6. Prints every ranked hit supported by the TurboQuant provider.

Search is not performed in the browser or the Python process. Python sends
documents and requests over gRPC; the provider selected by the Java server owns
the vectors and ranking. `--cleanup` deletes the temporary index. Without it,
the index remains available until explicitly deleted or the server exits.

TurboQuant supports exhaustive results, but the server still applies configured
record, query, and serialized-response byte limits. A response reports whether
otherwise ranked hits were truncated to honor the response limit.

## Learn a vocabulary, distill a model, and search it

Training is operator-gated because it writes artifacts and resolves an approved
teacher model. Add a writable artifact root and one allowlisted teacher to the
server configuration before startup:

```ini
vocabulary.artifact_root=/srv/opennlp/training-artifacts
training.teacher.local-mini.ref=/srv/opennlp/teachers/local-mini
training.teacher.local-mini.display_name=Local mini encoder
training.model_cache_dir=/srv/opennlp/trained-model-cache
```

The local teacher directory must contain `tokenizer.json` and
`onnx/model.onnx`, plus any external ONNX data and tokenizer model files needed
by that export. A Hugging Face `org/model@revision` reference is also accepted;
pin the revision and verify its license before publishing derived artifacts.

Then run:

```bash
uv run python train_and_search.py \
  --teacher-id local-mini \
  --alias python-trained-current
```

This example imports a small domain dictionary, streams documents through the
typed analysis service, learns a vocabulary, distills a served static embedding
model, creates a TurboQuant index in that model's vector space, and queries the
published alias. It prints the model and index provenance needed by subsequent
clients.

These stages have different jobs:

- Vocabulary learning counts corpus terms and preserves required dictionary
  headwords.
- Distillation uses the allowlisted teacher to create a static embedding table
  for that vocabulary.
- TurboQuant stores and searches the resulting document-chunk vectors.

This workflow does not train NER, POS, parser, or sentiment models. Those are
independent analysis resources configured by the server operator.

## Full lifecycle conformance client

The shorter examples are intended to be read and modified. The integration
suite also contains a descriptor-driven
[`lifecycle_e2e.py`](../../opennlp-grpc-integration-tests/scripts/lifecycle_e2e.py)
client. It exercises aliases, collections, persistence events, blue-green
replacement, compound queries, sealing, cleanup, and optional distillation
without relying on generated Python sources. See the
[integration-test README](../../opennlp-grpc-integration-tests/README.md#cross-language-lifecycle-e2e-in-python-opt-in)
for its opt-in Maven gate.
