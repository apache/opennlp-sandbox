# Terminology: clause joins, rank fusion, and score scales

Sources fetched 2026-08-28:

- https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-bool-query
- https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-dis-max-query
- https://www.elastic.co/docs/reference/elasticsearch/rest-apis/reciprocal-rank-fusion
- https://www.elastic.co/docs/reference/elasticsearch/rest-apis/retrievers
- https://docs.vespa.ai/en/ranking/phased-ranking.html
- https://docs.opensearch.org/latest/search-plugins/search-pipelines/normalization-processor/
- https://opensearch.org/blog/introducing-reciprocal-rank-fusion-hybrid-search/
- https://dl.acm.org/doi/10.1145/1571941.1572114 (Cormack, Clarke, Buettcher, SIGIR 2009)
- https://www.elastic.co/docs/reference/elasticsearch/mapping-reference/dense-vector
- https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-script-score-query
- https://qdrant.tech/documentation/concepts/search/
- https://docs.weaviate.io/weaviate/config-refs/distances
- https://docs.weaviate.io/weaviate/search/similarity

## 4. Join modes

### "All clauses (AND, mean score)"

The AND half is standard. The "mean score" half is not.

Elasticsearch `bool` query scoring:

> "The score from each matching `must` or `should` clause will be added together to provide the final `_score`"

That is a **sum**, not a mean. Lucene `BooleanQuery` behaves the same way: matching `MUST` and `SHOULD` clause scores are summed via a coordinated sum scorer.

No mainstream engine exposes an arithmetic mean of clause scores for a boolean AND. The nearest thing is OpenSearch hybrid search, where mean is a **normalization pipeline** option rather than a boolean join:

> the normalization processor "supports combination via `arithmetic_mean`, `geometric_mean`, or `harmonic_mean`, with a configurable weights array"

but that operates on normalized sub query scores in a hybrid query, not on boolean clauses.

Verdict for the audit: "AND" is standard, "mean score" is nonstandard for a boolean join. Standard wording would be "sum of clause scores" (Lucene / Elasticsearch `bool`). If the implementation really does average, the closest precedent name is OpenSearch `arithmetic_mean` combination.

### "Any clause (OR, maximum score)"

Both halves are standard, and the combination has a well known name: disjunction max, or "dismax".

Elasticsearch `dis_max` query:

> "Returns documents matching one or more wrapped queries, called query clauses or clauses."

Elasticsearch `tie_breaker`:

> "Floating point number between 0 and 1.0 used to increase the relevance scores of documents matching multiple query clauses."

Behavior: `dis_max` takes the score of the single best matching clause; each other matching clause is multiplied by `tie_breaker` and added. With `tie_breaker` 0.0 the score is exactly the maximum clause score; with 1.0 it degenerates to the `bool` `should` sum.

Lucene has the same construct as `DisjunctionMaxQuery`. Solr exposes it as the `dismax` and `edismax` query parsers with the `tie` parameter.

Verdict: "Any clause (OR, maximum score)" is standard behavior, but the industry name for it is **dismax / disjunction max**, not "OR with maximum score". Note the trap: plain Elasticsearch `bool` `should` (which most users think of as "OR") **sums** rather than maximizes. So labelling OR as "maximum score" is correct for dismax and wrong for `bool` `should`. The UI should say which one it means.

### "Any clause, reciprocal-rank fusion"

Standard, and RRF is the near universal name.

Origin: Cormack, Clarke and Buettcher, "Reciprocal rank fusion outperforms condorcet and individual rank learning methods", SIGIR 2009, pp. 758 to 759. RRF scores a document by summing the reciprocals of its rank in each input result list, offset by a constant k (the paper uses k = 60).

Elasticsearch:

> "A method for combining multiple result sets with different relevance indicators into a single result set. RRF requires no tuning, and the different relevance indicators do not have to be related to each other."

Elasticsearch retriever wording:

> "The rrf retriever produces top documents from reciprocal rank fusion (RRF)."

Elasticsearch parameters:

> `rank_constant`: "This value determines how much influence documents in individual result sets per query have over the final ranked result set. A higher value indicates that lower ranked documents have more influence."
> `rank_window_size`: "This value determines the size of the individual result sets per query. A higher value will improve result relevance at the cost of performance."

Vespa, global phase normalizers:

> `reciprocal_rank` "Accepts one or two arguments. The first must be a rank-feature or function name, while the second is an optional numerical constant `k` with a default value of 60.0" and computes `output = 1.0 / (k + rank)`.
> `reciprocal_rank_fusion` is "a convenience function taking at least two arguments, expanding to the sum of their reciprocal_rank operations."

OpenSearch splits the two families explicitly. The `normalization-processor` is score based (`min_max`, `l2` normalization, then `arithmetic_mean`, `geometric_mean` or `harmonic_mean` combination). RRF lives in the separate `score-ranker-processor`, configured with `"technique": "rrf"` and an optional `rank_constant`:

> RRF "merges ranked results from multiple query sources ... into a single relevance-optimized list"
> `rank_constant`: "larger rank constants make the scores more uniform, reducing the impact of top-ranked items. Smaller rank constants create steeper differences between ranks."

How products word the option in configuration and UI:

| Product | String the user sees |
| --- | --- |
| Elasticsearch | `rrf` retriever, docs title "Reciprocal rank fusion" |
| OpenSearch | `technique: "rrf"` on `score-ranker-processor`, docs title "Reciprocal rank fusion" |
| Vespa | `reciprocal_rank_fusion(a, b)` rank expression |

Verdict: "Any clause, reciprocal-rank fusion" is standard in substance. Vendors write it either fully spelled out ("Reciprocal rank fusion") or as the bare acronym `rrf`; the hyphenated "reciprocal-rank fusion" is a house style variant. Every product also exposes the k constant under a name (`rank_constant` in both Elastic and OpenSearch, `k` in Vespa); a UI that fuses without surfacing k differs from all three.

Also note a naming mismatch: in Elasticsearch and OpenSearch, RRF is not a boolean join at all. It is a top level retriever or pipeline processor that combines whole result sets. Presenting it as a third value in the same "how do clauses join" selector is a simplification, though a defensible one.

## 5. "cosine score" and a -1 to +1 scale

Cosine similarity itself has a mathematical range of -1 to +1, and vendors say so. Qdrant documents Cosine similarity with a score range of -1 (opposite) to 1 (identical), notes it normalizes vectors in Cosine collections, and orders results "LargeBetter".

However, **search products almost never show raw cosine in -1..+1**. Every major engine rescales it first, usually to make it non negative.

Elasticsearch `dense_vector` `similarity: cosine`:

> "Computes the cosine similarity. During indexing Elasticsearch automatically normalizes vectors" to unit length.

with the documented `_score` transformation `(1 + cosine(query, vector)) / 2`, giving 0..1. The related `dot_product` option is documented as `(1 + dot_product(query, vector)) / 2`.

The reason is a hard engine constraint. Elasticsearch `script_score` docs:

> "Final relevance scores from the `script_score` query cannot be negative. To support certain search optimizations, Lucene requires scores be positive or `0`."

Hence the standard idiom in Elasticsearch scripts is `cosineSimilarity(params.query_vector, 'my_dense_vector') + 1.0`.

Weaviate offers both a distance and a normalized score. For cosine, `certainty` is `1 - distance / 2`, described as normalizing "the distance score into a value between 0 <= certainty <= 1, where 1 would represent identical vectors and 0 would represent opposite vectors". Weaviate's own docs note `certainty` "is only available with `cosine` distance" and that `distance` is now the preferred field. Weaviate returns negative dot product internally "to stick with the intuition that a smaller value of a distance indicates a more similar result".

Verdict for the audit:

- "cosine" / "cosine similarity" as a metric name: standard (Elasticsearch, Qdrant, Weaviate, Vespa all use the word).
- "cosine score" as a UI label: acceptable but slightly unusual. Vendors say "similarity", "distance", "certainty", or just "_score".
- A -1 to +1 scale in the UI: mathematically correct but **not** what any of the four products above display by default. Elasticsearch shows 0..1 after `(1 + cos)/2`; Weaviate shows either a cosine distance in 0..2 or a certainty in 0..1; Qdrant does return a raw cosine in -1..1 as its `score`, so Qdrant is the one precedent for showing the raw range. A user coming from Elasticsearch will expect 0..1 and may read a negative number as a bug.
