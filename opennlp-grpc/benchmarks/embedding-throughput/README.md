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

# Embedding throughput: in-process vs gRPC vs a naive Python baseline

Three methodology-matched harnesses measuring static-embedding-table throughput under concurrent load. All three share the same design so the numbers are comparable: N workers round-robin over the same sentence file (`sentences.txt`), one text embedded per call (the request-handler shape), fixed-duration phases with a discarded warmup and a counted measure window.

- `ConcurrentEmbedBench.java`: the in-process ceiling. Loads a model with `opennlp-embeddings` (`StaticEmbeddingModel`) and calls `embed()` from N threads sharing the one immutable model instance.
- `GrpcEmbedBench.java`: the same workload through the wire, in two modes. `unary` sends one blocking `AnalyzeDocument` request per text (profile `PIPELINE_STEP_EMBED`): one call in flight per thread, the classic request-handler shape. `stream` opens one `EmbedText` bidi stream per worker and pumps texts as fast as gRPC flow control admits, so many messages are in flight per stream and per-text round-trip waiting disappears. The backend is whatever the server was configured with, so the same harness measures the `static` backend, ONNX, or a remote backend behind the same RPC. An optional channel-count argument spreads workers over several HTTP/2 connections.
- `bench.py`: the naive Python baseline. Same fixed-duration loop against the reference Python implementation of the same model family, in four modes: `seq` (one worker), `threads` (N Python threads), `procs` (N processes, each with its own model copy), and `batch` (whole-list encode, the vectorization best case).

Correctness precedes the timing: for the same sentence, `StaticEmbeddingModel.embed()` and the Python reference implementation produce numerically identical vectors (cosine 1.0, max element difference 7.2e-9), so the throughput comparison is apples-to-apples.

## Measured results

One run, 2026-07-09, AMD Ryzen 9 9950X3D (16 physical cores, 32 hardware threads), Linux, Temurin 25, Python 3.12 with the GIL. Model: a 29,528 x 256 F32 distilled table (potion-base-8M), downloaded, not bundled. 5 s warmup, 10 s measure per point. Numbers are texts/sec; rerunning moves them by a few percent.

| Path | Workers | Texts/sec | RSS |
|---|---|---|---|
| In-process JVM, shared model | 1 | 540,744 | 340 MB |
| In-process JVM, shared model | 16 | 4,346,467 | 793 MB |
| gRPC unary (static backend), 1 channel | 1 | 28,681 | server 852 MB |
| gRPC unary (static backend), 1 channel | 32 | 111,137 | server 852 MB |
| gRPC unary (static backend), 8 channels | 32 | 238,901 | server 852 MB |
| gRPC EmbedText stream (static backend), 1 channel | 1 | 86,064 | server 2.2 GB (2 GB heap cap) |
| gRPC EmbedText stream (static backend), 1 channel | 32 | 314,687 | server 2.2 GB (2 GB heap cap) |
| gRPC EmbedText stream (static backend), 8 channels | 32 | 690,682 | server 2.2 GB (2 GB heap cap) |
| Python, per-text, 1 process | 1 | 41,926 | 139 MB |
| Python, per-text, 16 threads | 16 | 28,563 | 128 MB |
| Python, per-text, 32 processes | 32 | 625,280 | 3,526 MB |
| Python, batch-40 encode | 1 | 88,668 | 189 MB |

## Reading the numbers

- In-process is the ceiling: one JVM thread embeds 540k texts/s, 12.9x the Python per-text baseline, and one 16-thread JVM reaches 4.35M texts/s in under 800 MB. Scaling is near-linear to the physical core count; SMT adds nothing (the table gather is memory-bandwidth-bound).
- The gRPC hop costs about 33 microseconds per text single-threaded (28.7k/s vs 540k/s), which buys a language-agnostic seam: any client gets these embeddings without loading a model, and the server can swap the static table for a transformer backend without clients changing.
- A single shared HTTP/2 connection caps unary client throughput near 111k/s; spreading 32 threads over 8 connections reaches 239k/s, after which the server pipeline (sentence detection, span mapping, proto encode/decode) is the limit. Each unary request does more than embed, so this is not a pure embedding number by design; it is the document-shaped one.
- Streaming removes the per-request round trip and the document envelope: one `EmbedText` stream does 86k texts/s (3x a unary thread), and 32 streams over 8 connections reach 691k texts/s, 2.9x the unary peak, above the Python baseline's best local number, over the wire. The response vectors are already packed binary on the wire (`repeated float` encodes as raw little-endian bytes), so a separate binary-blob call would buy little beyond this.
- Streaming throughput depends on bounded server-side elasticity. The server gates response writes on transport readiness with a bounded elastic window (about 1k messages per stream past a not-ready signal): with no gate the heap absorbs every burst without bound (12.5 GB observed against a fast reader, unbounded against a stalled one); gating on every not-ready blip costs about a third of peak throughput. The measured numbers hold under a 2 GB server heap cap.
- Python threading scales negatively for this workload (16 threads run 32% slower than 1; the per-call numpy work is too small for GIL release to matter). Python scales with processes, at a full model + interpreter copy per process: its best local result needs 32 processes and 3.5 GB to reach a seventh of the shared-model JVM's throughput.

## Backend comparison through the same wire

The same EmbedText streaming harness pointed at the same server, with only the configured backend changing (same machine and corpus as above; TEI 1.9 in Docker serving all-MiniLM-L6-v2, the ONNX backend serving the same model in-server). The static table and the transformer differ in embedding quality; this table compares serving paths and model families at their measured speed, not equal-quality systems.

| Backend behind the wire | Model | Best measured | Configuration |
|---|---|---|---|
| static (table lookup) | potion-base-8M, 256-d | 690,682 texts/sec | 32 streams, 8 channels |
| tei on an RTX 4080 SUPER | all-MiniLM-L6-v2, 384-d | 43,426 texts/sec | 256 streams, 8 channels |
| onnx (in-server, CPU) | all-MiniLM-L6-v2, 384-d | 1,345 texts/sec | 32 streams, 8 channels |
| tei (Docker, CPU) | all-MiniLM-L6-v2, 384-d | 1,103 texts/sec | 32 streams, 8 channels |

- The GPU transformer needs stream depth: 2.6k texts/s on one stream, 23.5k at 32 streams, 43.4k at 256, where the remote engine's continuous batching saturates. The CPU transformer paths invert: on the ONNX backend, 8 concurrent streams run slower than 1 (355 vs 600 texts/s) because concurrent sessions fight the intra-op thread pool.
- Ranking by throughput per dollar of hardware: the static table on plain CPU outruns the GPU transformer by 16x on this workload. The GPU earns its place when transformer-quality embeddings are required: against the same model on CPU it is 29 to 39x faster.
- The seam is the point: every row is the same client, same RPC, same server binary, one configuration line apart, and a model id can move between backends without any client change.

## Running it

Both Java harnesses are single files compiled against jars you already build here; the Python baseline needs the reference implementation of the model family (`model2vec`) in a virtualenv. Point every harness at the same model directory and sentence file.

```bash
# In-process (classpath: opennlp-embeddings + its dependencies)
java ConcurrentEmbedBench <model-dir> sentences.txt <threads> <warmup-s> <measure-s>

# Server for the gRPC leg (put opennlp-grpc-backend-static and opennlp-embeddings
# on the classpath, plus an opennlp-models-sentdetect-* jar for sentence detection)
#   bench-server.properties: model.embedder.potion.static.dir=<model-dir>
java org.apache.opennlp.grpc.server.OpenNlpGrpcServer -p 7071 -c bench-server.properties

# gRPC client (classpath: opennlp-grpc-api + grpc-netty-shaded)
java GrpcEmbedBench localhost 7071 sentences.txt <threads> <warmup-s> <measure-s> [channels] [unary|stream]

# Python baseline
python bench.py seq|threads|procs|batch <model-dir> sentences.txt <workers> <warmup-s> <measure-s>
```
