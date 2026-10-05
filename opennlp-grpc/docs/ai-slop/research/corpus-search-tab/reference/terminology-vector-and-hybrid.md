# Terminology: semantic clauses, vector search, provenance labels, and expected-but-absent terms

Sources fetched 2026-08-28:

- https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-semantic-query
- https://www.elastic.co/docs/reference/elasticsearch/mapping-reference/semantic-text
- https://www.elastic.co/docs/solutions/search/vector/knn
- https://www.elastic.co/docs/reference/elasticsearch/rest-apis/retrievers
- https://docs.vespa.ai/en/reference/query-language-reference.html
- https://docs.vespa.ai/en/reference/rank-features.html
- https://docs.weaviate.io/weaviate/search/similarity
- https://qdrant.tech/documentation/concepts/search/
- https://docs.opensearch.org/latest/search-plugins/search-pipelines/normalization-processor/
- https://opensearch.org/blog/introducing-reciprocal-rank-fusion-hybrid-search/
- https://docs.cohere.com/docs/rerank-overview
- https://www.w3.org/TR/prov-overview/
- https://nlp.stanford.edu/IR-book/html/htmledition/an-example-information-retrieval-problem-1.html
- https://dl.acm.org/doi/10.1145/361219.361220 (Salton, Wong, Yang, CACM 1975)
- https://huggingface.co/docs/hub/en/model-cards
- https://huggingface.co/docs/hub/datasets-cards

## 3. "semantic" clause

Standard, and Elastic uses the exact word as a query type name.

Elasticsearch:

> "The `semantic` query type enables you to perform semantic search on data stored in a `semantic_text` field."

`semantic_text` is a field type that accepts natural language text and uses a configured inference endpoint to generate query embeddings for matching documents. It resolves dense or sparse vector handling automatically, and Elastic notes it can be combined with keyword search for hybrid retrieval. Elastic now recommends `match` against a `semantic_text` field for new work, with `semantic` kept as the legacy form.

Other vendors name the same clause after the mechanism rather than the intent:

| Product | Clause name |
| --- | --- |
| Elasticsearch | `semantic` query, `semantic_text` field, `knn` retriever |
| Vespa | `nearestNeighbor` query operator, "matches the top-k nearest neighbors in a multidimensional vector space" |
| Weaviate | `nearText` operator, "find objects with the nearest vector to an input text" |
| Qdrant | vector search / similarity search, points scored by the collection's distance metric |

Verdict: "semantic" as a clause label is standard, backed by Elastic's own query name. It is the most user facing of the four options; `nearestNeighbor` and `nearText` describe the operation, `semantic` describes the intent.

## 10. Result inspector labels: provenance, corpus, vector space, embedding route, artifact hash

None of these appear as labels in a mainstream search result inspector. Search engines expose scoring internals (Elasticsearch `explain`, Vespa `match-features` and `summary-features`, which "are returned alongside the search results for display or analysis purposes"), not lineage. Precedent for the individual words comes from outside search.

### provenance

Standard term, with a W3C recommendation behind it. W3C PROV:

> "Provenance is information about entities, activities, and people involved in producing a piece of data or thing, which can be used to form assessments about its quality, reliability or trustworthiness."

Verdict: standard as a data lineage term (W3C PROV, PROV-O, PROV-DM). Not standard as a search result label; no search vendor ships a panel called "Provenance". Using it is defensible and precise, but it will read as domain vocabulary borrowed from data governance rather than from search.

### corpus

Standard in information retrieval, though less so in product UIs. Stanford's IR textbook:

> "The group of documents over which we perform retrieval is referred to as the (document) collection, and is sometimes also referred to as a corpus (a body of texts)."

Verdict: standard in the literature. Product UIs prefer "index" (Elasticsearch, Algolia, Typesense), "collection" (Solr, Qdrant, Typesense), or "class" (older Weaviate). A reviewer from Elasticsearch will look for "index".

### vector space

Standard, and the founding term of the field: Salton, Wong and Yang, "A vector space model for automatic indexing", Communications of the ACM 18(11), 1975. Vespa's `nearestNeighbor` description uses the phrase directly ("a multidimensional vector space"). As a **label** for a specific named embedding space in a result inspector it has no vendor precedent; the vendor equivalents are the field name plus the model id (Elasticsearch `dense_vector` field and inference endpoint, Qdrant named vectors, Weaviate named vectors).

### embedding route

No precedent found. "Embedding" is standard on its own (Elasticsearch inference endpoints produce embeddings; Vespa has an `embedding` field and embedder components). "Route" attached to it is house vocabulary. The nearest standard concepts are Elasticsearch's `inference_id` / inference endpoint, Vespa's named embedder, and Weaviate's vectorizer module.

### artifact hash

No precedent in search UIs. The parts are standard elsewhere: "artifact" in build and package tooling, content hashes as integrity identifiers, and model or dataset card metadata as the documentation convention for recording which model version produced a set of vectors. Model cards and dataset cards on the Hugging Face Hub are README files whose YAML front matter records license, source datasets, metrics and training details, and are the usual place this identity information lives. A search result inspector showing a hash of the embedding artifact is an original idea, not a copied convention.

## 11 (remainder). Terms a reviewer may expect but that do not appear

### kNN

Elasticsearch:

> "A *k-nearest neighbor* (kNN) search finds the *k* nearest vectors to a query vector using a similarity metric such as cosine or L2 norm."

Exposed as: Elasticsearch `knn` search option and `knn` retriever with `k` and `num_candidates`; OpenSearch `knn` query; Vespa `nearestNeighbor` with `targetHits`; Qdrant, Weaviate, Milvus and Pinecone as their core query. A user arriving from Elasticsearch will look for "kNN" or "k" and may not connect it to a clause labelled "semantic".

### hybrid search

Standard meaning: combining a lexical retriever and a vector retriever into one ranked list. Exposed as: Elasticsearch `rrf` retriever over a `standard` plus a `knn` retriever; OpenSearch `hybrid` query with a search pipeline that either normalizes scores (`normalization-processor`, techniques `min_max` and `l2`, combination `arithmetic_mean`, `geometric_mean`, `harmonic_mean`) or fuses ranks (`score-ranker-processor` with `"technique": "rrf"`); Vespa a rank profile combining `bm25` and `closeness` in a global phase; Weaviate `hybrid` operator with an `alpha` parameter; Qdrant `Query API` with prefetch and fusion.

Note for the audit: a UI that offers term, phrase and semantic clauses joined by RRF **is** hybrid search. Not using the word means a reviewer may not recognize the feature they came looking for.

### rerank

Standard meaning: a second pass that reorders an already retrieved candidate set with a more expensive scorer. Cohere:

> the "Rerank API endpoint is a simple and very powerful tool for semantic search. Given a `query` and a list of `documents`, Rerank indexes the documents from most to least semantically relevant to the query."

Cohere returns a `relevance_score` per document. Elasticsearch exposes it as the `text_similarity_reranker` retriever, which "enhances search results by re-ranking documents based on semantic similarity to a specified inference text". Vespa calls the equivalent stage `global-phase` with a `rerank-count`. OpenSearch has a `rerank` search response processor.

Distinction worth preserving in copy: **fusion** (RRF) merges lists by rank and is cheap; **reranking** rescores candidates with a stronger model and is expensive. They are not synonyms, and a UI offering RRF is not offering reranking.
