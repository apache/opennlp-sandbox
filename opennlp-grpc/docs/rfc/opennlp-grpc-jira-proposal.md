# JIRA Proposal: Document-Centric gRPC API for Apache OpenNLP 3.x

> **Copy-paste guide:** Use the sections below when filing an issue at https://issues.apache.org/jira/projects/OPENNLP  
> **Issue type:** Improvement / New Feature  
> **Component:** (suggest) `grpc` or `server` if available; otherwise leave default  
> **Affects Version:** 3.0.0-SNAPSHOT  
> **Labels:** `grpc`, `rfc`, `api-design`

---

## Summary (JIRA title field)

**Add document-centric gRPC API - evolve opennlp-sandbox POC with canonical OpenNlpDocument and AnalyzeDocument RPC**

---

## Description (paste into JIRA description)

### Problem

Apache OpenNLP is primarily an **in-process Java library** (API, CLI, UIMA). The README notes embedding in distributed pipelines (Flink, NiFi, Spark), but there is **no standard wire contract** for cross-language clients or remote inference.

A proof-of-concept exists in the sandbox:

- **Repository:** https://github.com/apache/opennlp-sandbox/tree/main/opennlp-grpc
- **Current scope:** Three separate gRPC services (`SentenceDetectorService`, `TokenizerTaggerService`, `PosTaggerService`) with string-based requests and `model_hash` per call
- **Gap:** No unified **document** message, no pipeline orchestration (the POC has three separate string-based services), and clients must chain multiple RPCs. The proposal brings NER, chunking (configurable segmentation + classic ChunkerME), and embeddings (via SentenceVectorsDL + pluggable GPU providers) into the single document-centric contract as first-class steps.

Main OpenNLP (`apache/opennlp`) has **no gRPC modules** on `main`. OpenNLP 3.0 brings thread-safe `*ME` classes (JDK 21+), which makes a long-lived gRPC server practical. The `opennlp-dl` / `opennlp-dl-gpu` modules already support ONNX inference (including sentence embeddings via `SentenceVectorsDL`).

### Proposal

Evolve the sandbox POC into ASF-native modules (target: main repo after consensus):

| Module | Purpose |
|--------|---------|
| `opennlp-grpc-api` | Protocol Buffers + generated stubs (Java first; descriptors for other languages) |
| `opennlp-grpc-server` | gRPC server, model bundle registry, pipeline orchestration |
| `opennlp-grpc-examples` | Sample clients (e.g. Python) |

**Core API change:** Introduce a canonical **`OpenNlpDocument`** message (1:1 text document in, enriched document out) and a primary **`AnalyzeDocument`** RPC that runs a configurable NLP pipeline server-side-similar in spirit to the existing UIMA `OpenNlpTextAnalyzer` composite, but as a language-neutral contract.

**Package naming (proposed):** `org.apache.opennlp.grpc.v1`

### Non-goals (v1 RFC)

- Binary/PDF document parsing (Tika, etc.) - callers supply `raw_text`
- Training, evaluation, or model-update RPCs
- Embedding `.bin` model bytes in request messages (models remain server-side)
- Authentication / multi-tenancy in the core API (deployment concern: mTLS, reverse proxy)
- Coreference (documented in manual but not implemented in current codebase)

### Compatibility

- **Additive** Maven modules; no breaking changes to `opennlp-api` / `opennlp-runtime`
- Sandbox granular services may be deprecated or moved to `opennlp.legacy.v1` after migration

### Phased delivery (high level)

| Phase | Scope |
|-------|--------|
| **0** | This JIRA + community RFC (this ticket) |
| **1** | Design document + full `.proto` definitions (no server code required for consensus) |
| **2+** | Implementation: orchestrator, server, tests, graduation from sandbox to main repo |
| **Later** | Advanced GPU provider modules (CUDA via onnxruntime-gpu, OpenVINO), richer discovery, streaming, additional steps; core `Document` interface graduation if not in 3.0.0-M4 |

### Design highlights

1. **Three proto layers (NLP-only):** domain types (`OpenNlpDocument`), pipeline config (`AnalysisProfile`), service (`OpenNlpAnalysisService`)
2. **Offset contract:** All exported spans are half-open `[start, end)` ranges in the original `raw_text`; `CoordinateSpace` says what the range is relative to, and `OffsetEncoding` says whether the units are UTF-8 bytes, UTF-16 code units, or Unicode code points
3. **Model bundles:** Replace per-RPC `model_hash` with `ModelBundleRef` + server-defined profiles (reuse sandbox model discovery patterns)
4. **Thread safety:** Leverage OpenNLP 3.0 thread-safe `*ME` instances cached per model bundle

### Sample protobuf (illustrative - full spec in design doc)

The following is a **short sketch** for discussion; field numbers and optional messages may change during RFC.

**Important (per community feedback on OPENNLP-1833):** Chunking and embeddings are **in scope for v1**, not deferred. The full protobuf definitions (including `PipelineStep.CHUNK` and `EMBED`, `ChunkResult`/`ChunkSpan`, `EmbeddingResult`, `InferenceBackend`, richer `ModelBundleInfo` for discovery, etc.) live in the companion design document `docs/rfc/opennlp-grpc-design.md`. The short sketch below is intentionally minimal. GPU hot-swap (CUDA, OpenVINO) is achieved via a provider SPI behind `InferenceBackend`; those provider implementations are separate optional modules.

```protobuf
syntax = "proto3";

package org.apache.opennlp.grpc.v1;

option java_package = "org.apache.opennlp.grpc.v1";
option java_multiple_files = true;

import "google/protobuf/struct.proto";

// --- Layer 1: Document ---

message OpenNlpDocument {
  string doc_id = 1;
  string raw_text = 2;
  optional string detected_language = 3;
  optional float language_confidence = 4;
  repeated AnnotatedSentence sentences = 5;
  optional DocumentAnalytics analytics = 6;
  google.protobuf.Struct metadata = 7;
  OffsetEncoding offset_encoding = 11;
}

message AnnotatedSentence {
  AnnotationSpan sentence_span = 1;
  repeated Token tokens = 2;
  repeated NamedEntity entities = 3;
}

message Token {
  string text = 1;
  AnnotationSpan annotation_span = 2;
  optional string pos_tag = 3;
}

message NamedEntity {
  AnnotationSpan annotation_span = 1;
  string entity_type = 2;
  optional double probability = 3;
}

message AnnotationSpan {
  int32 start = 1;
  int32 end = 2;
  CoordinateSpace space = 3;
  optional string type = 4;
  optional double probability = 5;
}

enum CoordinateSpace {
  COORDINATE_SPACE_UNSPECIFIED = 0;
  COORDINATE_SPACE_CHAR_DOCUMENT = 1;
  COORDINATE_SPACE_TOKEN_SENTENCE = 2;
}

enum OffsetEncoding {
  OFFSET_ENCODING_UNSPECIFIED = 0;
  OFFSET_ENCODING_UTF8_BYTE = 1;
  OFFSET_ENCODING_UTF16_CODE_UNIT = 2;
  OFFSET_ENCODING_UNICODE_CODE_POINT = 3;
}

// --- Layer 2: Pipeline ---

enum PipelineStep {
  PIPELINE_STEP_UNSPECIFIED = 0;
  LANGUAGE_DETECT = 1;
  SENTENCE_DETECT = 2;
  TOKENIZE = 3;
  POS_TAG = 4;
  NER = 5;
}

message AnalysisProfile {
  string profile_id = 1;
  repeated PipelineStep steps = 2;
  ModelBundleRef model_bundle = 3;
}

message ModelBundleRef {
  string bundle_id = 1;
}

message AnalysisOptions {
  bool include_probabilities = 1;
  bool clear_adaptive_data = 2;
}

// --- Layer 3: Service ---

service OpenNlpAnalysisService {
  rpc AnalyzeDocument(AnalyzeDocumentRequest) returns (AnalyzeDocumentResponse);
  rpc GetServiceInfo(GetServiceInfoRequest) returns (GetServiceInfoResponse);
}

message AnalyzeDocumentRequest {
  OpenNlpDocument document = 1;
  AnalysisProfile profile = 2;
  AnalysisOptions options = 3;
}

message AnalyzeDocumentResponse {
  OpenNlpDocument document = 1;
  repeated ProcessingDiagnostic diagnostics = 2;
}

message ProcessingDiagnostic {
  PipelineStep step = 1;
  string message = 2;
  DiagnosticSeverity severity = 3;
}

enum DiagnosticSeverity {
  DIAGNOSTIC_SEVERITY_UNSPECIFIED = 0;
  INFO = 1;
  WARNING = 2;
  ERROR = 3;
}

message GetServiceInfoRequest {}
message GetServiceInfoResponse {
  string opennlp_version = 1;
  string api_version = 2;
  repeated string available_profile_ids = 3;
}
```

### Comparison: sandbox vs proposed

| Aspect | Sandbox POC | Proposed |
|--------|---------------|----------|
| Services | 3 (sent / token / POS) | 1 primary (`OpenNlpAnalysisService`) |
| I/O | Strings + `StringList` | `OpenNlpDocument` |
| Models | `model_hash` per RPC | `ModelBundleRef` + profiles |
| Pipeline | Client-side chaining | Server-side `AnalysisProfile` |
| Package | `package opennlp` | `org.apache.opennlp.grpc.v1` |

### References

- Sandbox POC: https://github.com/apache/opennlp-sandbox/tree/main/opennlp-grpc
- Current sandbox proto: https://github.com/apache/opennlp-sandbox/blob/main/opennlp-grpc/opennlp-grpc-api/opennlp.proto
- UIMA composite pipeline: `opennlp-extensions/opennlp-uima/descriptors/OpenNlpTextAnalyzer.xml`
- ONNX / GPU: `opennlp-dl`, `opennlp-dl-gpu`, `SentenceVectorsDL`
- Full design document (companion): `docs/rfc/opennlp-grpc-design.md` in contributor branch or attachment

### Questions for the community (with initial feedback summary)

1. Should v1 expose **only** `AnalyzeDocument`, or retain sandbox granular RPCs under a legacy package?
   - **Community preference (Martin + consensus direction):** Retain the existing granular services under a legacy package (`org.apache.opennlp.grpc.legacy.v1` or similar) for a transition period. New development and clients should use the primary document-centric `OpenNlpAnalysisService`.

2. Target release: **3.0.x** (additive) vs **3.1**?
   - **Community view (Martin):** More likely **3.1.x**. 3.0.0 is approaching a release (target end of June / early July 2026 or shortly thereafter). The gRPC work is substantial and additive; landing it after the 3.0 cut reduces risk.

3. Preferred home: graduate into **apache/opennlp** vs remain in **opennlp-sandbox** until stable?
   - **Community direction (Martin):** Start and iterate in the **opennlp-sandbox** (as is already underway on the feature branch). Graduate stable modules into `apache/opennlp` in future cycles once the design has had review and the implementation has proven itself. A neutral core `Document` interface (if adopted) could land earlier in 3.0.0-M4 as a small additive API change.

4. Proto tooling: Maven `protobuf-maven-plugin` only, or also publish to Buf Schema Registry?
   - **Strong community preference (Martin, Richard):** Stay with **Maven + protobuf-maven-plugin** only for consistency with the rest of the OpenNLP project. No Gradle. Buf publication can be considered later as a non-blocking enhancement.

---

## Reporter notes (do not paste)

- Attach or link `docs/rfc/opennlp-grpc-design.md` when available
- Discuss on dev@opennlp.apache.org after filing
- Link this JIRA from any sandbox PR that implements the new protos
