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

# OpenNLP search: the knowledge-buildup workflow and compound query model

Status: draft for iteration on the OPENNLP-1833 branch. The proto strawman is
`opennlp-grpc-api/src/main/proto/org/apache/opennlp/grpc/v1/opennlp_query.proto`.

## The workflow, not a demo

The product is one additive loop:

1. **Analyze** a document. Layers, offsets, chunks, embeddings.
2. Not enough? **Index** it. Live, in-process, atomic snapshots.
3. Documents are **additive knowledge**: every indexed document is also corpus
   evidence, so multiword terms ("hot dog", "new york", "habeas corpus") earn
   their way into the learned vocabulary by frequency, automatically, through
   the same VocabularyLearner that serves the vocabulary service.
4. Vocabulary drifted enough? **Distill** a new static model (Model2Vec-style)
   from an operator-approved teacher; the trained model serves immediately.
5. Memory is not enough? **Persist** the index through the durable artifact
   store seam.
6. One modality is not enough? **Compound query**: semantic, keyword, phrase,
   metadata filters, and calculated relevancy, fused under one score algebra.

Every step carries provenance hashes: dictionary, vocabulary, model manifest,
index bundle, preparation config. Nothing drifts silently.

## Layer boundary

This repository is the NLP and search knowledge-buildup layer: analysis,
vocabulary, training, indexing, and query semantics, exposed as gRPC with a
JSON gateway and a reference front end. Higher-level orchestration (LLM
assistance, conversational knowledge builders, cross-system search invention)
belongs to the layer that consumes these protobufs. The boundary is the wire
contract: consumers see typed services and descriptors, never our internals,
which is why the front end exists as proof that the boundary is sufficient.

## Provider model: OOTB defaults, swappable everything

OpenNLP features are first class; engines are providers. Three ServiceLoader
seams already exist and stay:

- **Embedding backends** (`EmbeddingBackendFactory`): static tables (trained
  Model2Vec models), ONNX, CUDA, TEI, OpenVINO. Composite routing by model id.
- **Search index providers** (`SearchIndexProviderFactory`): flat float
  (in-core default), TurboQuant. Selected by `SearchProviderSelector`, whose
  `custom` string is the open extension point.
- **Durable stores** (`VocabularyStoreProvider`): filesystem now, keyed by the
  artifact-root URI scheme; S3 or others arrive as JARs.

Two extensions:

1. **Configured instances, not just discovered classes.** The configuration
   declares named instances the way embedding models already do
   (`model.embedder.<id>.*`): several search providers, several store roots,
   selectable per index and per artifact kind. Multiple providers of every
   type run side by side in one server.
2. **Capability declarations.** A search provider declares what it executes:
   `vector`, `keyword`, or both; `live` (mutable snapshots) or `bundle`
   (build once); `persistent` or in-memory. The dynamic registry routes
   through the same SPI the loaded bundles use instead of a hardcoded switch.
   Lucene becomes `opennlp-grpc-search-lucene`, its own module carrying the
   Lucene dependency, declaring keyword plus vector plus persistent. Remote
   engines (a distributed KNN service, a future turbovec-grpc) follow the
   remote-backend pattern TEI established for embeddings. None of it is a
   dependency of the gRPC core; the core ships the SPI, the terms-layer
   keyword executor, flat float, and TurboQuant as defaults.

## The language layer is ours

Keyword search is not a string handed to an engine's analyzer. A term or
phrase clause is analyzed at query time by the exact analysis chain that
built the index: tokenizer, aligned normalizers, stemmer, term profile, and
the learned vocabulary artifact. Consequences:

- A learned multiword vocabulary term is **one match unit** in both the
  Model2Vec term rows and the keyword postings. The same vocabulary drives
  the semantic leg and the term leg.
- Offsets survive the whole stack (aligned normalization, term occurrence
  spans), so highlighting is exact and native; hits will carry matched spans.
- The index descriptor records its **analysis chain identity** next to its
  embedding route, the language-layer twin of the vector-space pin, so
  query-time analysis provably matches index-time analysis.

## Vocabulary accretion vs model versioning

Vocabularies grow continuously; models version discretely; indexes pin to a
model artifact hash as their vector space (already enforced: the trained
route's `vector_space_id` is `<model-id>-sha256-<manifest-hash>`). The loop:

- Indexing feeds term counts; the vocabulary accretes.
- The UI surfaces drift: N new terms since the serving model was distilled.
- Retraining is explicit: distill a new model artifact, reindex into its new
  vector space as a tracked operation. No silent embedding drift, ever.

## Compound query: a typed builder, not a parser

Queries are protobuf trees (`QueryNode`), composed clause by clause. A text
query language can be layered later as a parser that emits the tree; CEL
selectors already slot in as two constrained clause roles. Validation happens
at the message layer; the front end becomes a visual query builder.

Clauses:

| Clause | Role | Membership | Score |
|---|---|---|---|
| `semantic` | vector leg via the index's embedding route | similarity threshold-free top-k | `(cosine + 1) / 2` |
| `term` | analyzed keyword leg (`ANY` or `ALL`) | analyzed-term match | executor relevance in [0, 1] |
| `phrase` | ordered terms with slop | in-order match | executor relevance in [0, 1] |
| `join` | logical composition | AND / OR over operands, minus exclusions | mean (AND), max (OR), or reciprocal-rank fusion |
| `boost` | relevancy shaping | operand's membership, unchanged | operand score times a static weight or a CEL calculator, clamped |
| `cel_filter` | metadata predicate | expression must type-check to bool | never scores |
| `cel_calculator` | metadata-derived scored leg | every candidate a sibling admitted | numeric expression through a declared normalization |

The join-vs-boost split is deliberate: a **join** decides membership and can
never rescale relevancy; a **boost** shapes relevancy and can never gate
recall. CEL follows the same split: the **filter** role must type-check to
bool and only gates; the **calculator** role must type-check to a number,
passes through an explicit normalization (`CLAMP`, `MINMAX` over the
candidate set, or `LOGISTIC`), and then fuses like any other scored leg. Both
roles read only the candidate's metadata `Struct` and perform no I/O, so they
stay deterministic and provider-portable.

The full normative score algebra lives as comments in `opennlp_query.proto`
and is pinned by wire-contract and algebra tests; every provider must
reproduce it. Reciprocal-rank fusion is the escape hatch for joining legs
whose scales are not comparable (the classic hybrid case); a calculator leg
is just one more entrant in that fusion.

## Training recall telemetry

The accretion loop is measurable without labeled data, so the workflow can
report the real quality of its own training as documents add up:

1. **Quantization recall**: TurboQuant top-k against exact flat top-k over
   the same vectors. Ground truth is free; the offline eval harness already
   computes recall@k this way, and the server can run it over a live index.
2. **Vocabulary coverage**: the fraction of an incoming document's terms that
   hit learned term rows versus falling through to subword pieces. This is
   the drift meter that motivates the explicit retrain step.
3. **Student-vs-teacher agreement**: the distilled static model's top-k
   against its own teacher's top-k on the accreted corpus. The teacher is
   already configured and cached for training, so agreement is measurable
   continuously, per model version, on the operator's actual documents
   rather than a generic benchmark.

Each metric attaches to a model artifact and an index snapshot by hash, so a
recall curve is provenance-bound: this model version, this corpus size, this
number. Exposure lands as an evaluation RPC after query execution.

## Persistence

A live index that can persist serializes into the artifact store under an
`indexes` kind: staged, hashed, committed atomically, verified on reload,
exactly like trained models. `TurboQuantIndex.write/read` already exists;
Lucene writes its directory into the staged tree; the filesystem store covers
today and a remote scheme covers later without touching providers.

## Migration and build order

`SearchIndexRequest.query` (an `OpenNlpDocument`) remains valid as the
shorthand for a lone semantic clause. The typed tree arrives as a new field;
requests set exactly one of the two.

1. Route the dynamic registry through the search provider SPI with capability
   and instance declarations.
2. `QueryNode` execution: validation (types, CEL checking, algebra rule 8),
   the terms-layer keyword executor OOTB, and hit-level matched spans for
   highlighting.
3. Index persistence through the store seam (`indexes` kind).
4. `opennlp-grpc-search-lucene` as the first external provider module, mapped
   mechanically: join to BooleanQuery, boost to BoostQuery, semantic to
   KnnFloatVectorQuery.

Each step lands red tests first.
