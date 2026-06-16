# OpenNLP gRPC Backend: HuggingFace Text Embeddings Inference (TEI)

Remote embedding backend for the OpenNLP gRPC server, delegating inference to
[HuggingFace Text Embeddings Inference](https://github.com/huggingface/text-embeddings-inference)
over TEI's native gRPC API (`tei.v1.Embed`). TEI handles tokenization, truncation,
pooling and normalization server-side, runs on CPU or NVIDIA GPUs, and serves any
embedding model from the HuggingFace Hub — so the OpenNLP server gains GPU-accelerated,
model-agnostic embeddings without any ONNX Runtime or model files of its own.

```
OpenNLP gRPC client --> opennlp-grpc-server --> TEI (Docker, CPU or CUDA)
```

The backend is discovered via `ServiceLoader`: drop this module's jar on the server
classpath and select it with `model.embedder.backend=tei`. No code changes are needed
to switch models or move between CPU and GPU — that is decided entirely by which TEI
container you run.

## 1. Start a TEI server

TEI's gRPC API is only included in image tags with the `-grpc` suffix. GPU images are
published **per CUDA compute capability** (note: the `cuda-<version>-grpc` tag shown in
some TEI docs does not exist on ghcr.io):

| Hardware                              | Image (TEI 1.9)                                                  |
|---------------------------------------|------------------------------------------------------------------|
| CPU                                   | `ghcr.io/huggingface/text-embeddings-inference:cpu-1.9-grpc`     |
| Turing (T4, RTX 2000 series)          | `ghcr.io/huggingface/text-embeddings-inference:turing-1.9-grpc`  |
| Ampere 8.0 (A100, A30)                | `ghcr.io/huggingface/text-embeddings-inference:1.9-grpc`         |
| Ampere 8.6 (A10, A40)                 | `ghcr.io/huggingface/text-embeddings-inference:86-1.9-grpc`      |
| Ada Lovelace (RTX 4000 series)        | `ghcr.io/huggingface/text-embeddings-inference:89-1.9-grpc`      |
| Hopper (H100)                         | `ghcr.io/huggingface/text-embeddings-inference:hopper-1.9-grpc`  |
| Blackwell (B200 / RTX 5000 series)    | `ghcr.io/huggingface/text-embeddings-inference:100-1.9-grpc` / `120-1.9-grpc` |

```bash
model=sentence-transformers/all-MiniLM-L6-v2
volume=$HOME/.cache/tei-data   # cache model weights between runs

# CPU
docker run -d -p 18080:80 -v $volume:/data \
  ghcr.io/huggingface/text-embeddings-inference:cpu-1.9-grpc --model-id $model

# GPU (requires nvidia-container-toolkit; pick the tag for your architecture)
docker run -d --gpus all -p 18080:80 -v $volume:/data \
  ghcr.io/huggingface/text-embeddings-inference:89-1.9-grpc --model-id $model
```

The helper script in `opennlp-grpc-integration-tests/scripts/tei-server.sh` automates
this, including auto-detecting the CUDA compute capability for the right GPU image:

```bash
./scripts/tei-server.sh start --cpu
./scripts/tei-server.sh start --gpu --model Qwen/Qwen3-Embedding-0.6B
```

## 2. Configure the OpenNLP gRPC server

One TEI instance serves exactly one model, so each registered model id maps to one TEI
endpoint:

```properties
model.embedder.backend=tei

# Register model id "minilm" against a TEI endpoint (required)
model.embedder.minilm.tei.target=localhost:18080

# Optional, with defaults:
model.embedder.minilm.tei.use_tls=false      # plaintext by default
model.embedder.minilm.tei.truncate=true      # truncate over-long inputs in TEI
model.embedder.minilm.tei.normalize=true     # L2-normalize vectors in TEI
model.embedder.tei.deadline_ms=30000         # per-call deadline for all TEI endpoints
model.embedder.default_id=minilm             # required when multiple models are registered
```

Multiple models are simply multiple `model.embedder.<id>.tei.target` entries pointing
at different TEI containers; clients select one via `AnalysisOptions.embedding_model_id`.

Run the server with this backend on the classpath:

```bash
java -cp opennlp-grpc-server-<version>.jar:opennlp-grpc-backend-tei-<version>.jar \
  org.apache.opennlp.grpc.server.OpenNlpGrpcServer -p 7071 -c server.properties
```

All endpoints are validated eagerly at startup: the TEI `Info` RPC must report an
embedding model (rerankers and classifiers are rejected), and one probe embedding
determines the vector dimension, which is then published in the model catalog
(`ListModelBundles`, with `backend_id: "tei"`). Misconfiguration fails the server start,
not the first request. Batches are fanned out as concurrent unary calls on the
multiplexed HTTP/2 connection; TEI applies its own server-side batching.

## 3. Verified configurations

The live integration tests (`opennlp-grpc-integration-tests`, see its README) have been
run against real TEI servers with the following models:

| Model                                  | Dim  | Architecture            | Device          |
|----------------------------------------|------|-------------------------|-----------------|
| `sentence-transformers/all-MiniLM-L6-v2` | 384  | BERT encoder            | CPU and CUDA    |
| `Qwen/Qwen3-Embedding-0.6B`             | 1024 | Qwen3 decoder (last-token pooling) | CUDA |

On CUDA the TEI logs confirm GPU execution (e.g. `Starting FlashBert model on
Cuda(CudaDevice(...))`); the OpenNLP server and its clients are unaffected by the
device choice.
