# Terminology: hits, top-k, truncation, highlighting, chunks and passages

Sources fetched 2026-08-28:

- https://lucene.apache.org/core/9_10_0/core/org/apache/lucene/search/TopDocs.html
- https://lucene.apache.org/core/9_10_0/core/org/apache/lucene/search/Matches.html
- https://www.elastic.co/docs/solutions/search/vector/knn
- https://www.elastic.co/docs/reference/elasticsearch/rest-apis/highlighting
- https://www.elastic.co/docs/reference/elasticsearch/mapping-reference/semantic-text
- https://www.elastic.co/docs/solutions/search/the-search-api
- https://www.algolia.com/doc/api-reference/api-parameters/hitsPerPage/
- https://www.algolia.com/doc/api-reference/api-parameters/attributesToHighlight/
- https://typesense.org/docs/30.2/api/search.html
- https://solr.apache.org/guide/solr/latest/query-guide/standard-query-parser.html
- https://people.ischool.berkeley.edu/~hearst/papers/tilebars-chi95/chi95.html (Hearst, CHI 1995)
- https://www.elastic.co/docs/explore-analyze/visualize/charts/heat-map-charts
- https://microsoft.github.io/msmarco/TREC-Deep-Learning.html

## 6. "hit", "top-k", "Results" count, "truncated"

### hit

Standard, and it is the oldest word in the list. Apache Lucene `TopDocs`:

> "Represents hits returned by `IndexSearcher.search(Query,int)`."
> `totalHits`: "The total number of hits for the query."
> `scoreDocs`: "The top hits for the query."

Elasticsearch responses nest results under `hits.hits`. Algolia is the most literal about it: its response array is `hits`, its page size parameter is `hitsPerPage` ("Number of search results per page"), and its total is `nbHits`. Typesense returns `hits` plus `found`.

### top-k

Standard in vector search. Elasticsearch kNN:

> "A *k-nearest neighbor* (kNN) search finds the *k* nearest vectors to a query vector using a similarity metric such as cosine or L2 norm."

Elasticsearch `num_candidates` is the per shard candidate pool: the API "first finds a `num_candidates` number of approximate neighbors per shard" before selecting the final top `k`. Vespa's `nearestNeighbor` operator "matches the top-k nearest neighbors in a multidimensional vector space" and takes a `targetHits` annotation ("specifies the target hits _per node_", with `totalTargetHits` now preferred).

Lexical engines use a different word for the same thing: Solr `rows`, Elasticsearch `size`, Algolia `hitsPerPage`.

Verdict: "top-k" is standard **for the vector leg**. If the control also caps lexical results, note that `k` in Elasticsearch specifically means the kNN neighbor count and is distinct from `size`.

### "Results" as a count field label

Nonstandard as a label, though harmless. The vendor names are `totalHits` (Lucene), `hits.total.value` (Elasticsearch), `numFound` (Solr), `nbHits` (Algolia), `found` (Typesense). Every one of them uses "hits" or "found", not "results". A UI that calls its per result unit a "hit" but the count "Results" is internally inconsistent.

### "truncated"

The concept is standard; the word is not the usual one. Elasticsearch tracks total hits exactly only up to 10,000 by default, and reports which case applies:

> `hits.total.relation` "will indicate if the value returned in `hits.total.value` is accurate (`eq`) or a lower bound of the total (`gte`)."

`track_total_hits: true` forces an exact count at a performance cost. Elasticsearch also has `terminate_after` for early stopping. Solr signals the same thing with `numFound` plus `numFoundExact` (false meaning the count is a lower bound).

Verdict: "truncated" is a plain English restatement. The standard vocabulary is "lower bound" / `gte` relation (Elasticsearch), `numFoundExact=false` (Solr), or "early terminated". Not wrong, just not a term a reviewer would recognize from a product.

## 7. "heatmap" for shading document text by per-chunk score

Mixed. The visualization has strong academic precedent under a different name; the word "heatmap" in search products means something else.

Academic precedent, Marti A. Hearst, "TileBars: Visualization of Term Distribution Information in Full Text Information Access", CHI 1995. TileBars represent each retrieved document as a rectangle, each text segment as a square, and "the darkness of a square indicates the frequency of query terms in the segment", so patterns "can be quickly scanned and deciphered, aiding users in making judgments about the potential relevance of the retrieved documents". This is exactly a per passage relevance shading, thirty years old, and it is the citation to use.

What "heatmap" means in search products today: a grid chart over two aggregation dimensions. Kibana heat map charts "display data as a grid of colored cells, where each cell's color represents the magnitude of a value", built over bucket aggregations. Nothing to do with shading document text.

What products actually ship for per passage evidence:

- Elasticsearch: highlighting, which returns "best-matching highlighted snippets"; there is no per passage score shading in the response.
- Vespa: `match-features` and `summary-features`, which return selected rank feature values alongside each hit for display or analysis. Numbers, not shading.
- Elasticsearch `explain`: a scoring breakdown tree, again numeric.

Verdict: the **feature** has precedent (TileBars, and passage level scoring generally). The **label** "heatmap" is a repurposing: in search product vocabulary a heatmap is a matrix chart. Safer labels with product precedent would be "relevance shading", "per chunk score", or borrowing Vespa's "match features".

## 8. "highlight" and "matched span"

"highlight" is standard everywhere.

Elasticsearch:

> highlighting uses the search API's `highlight` parameter to "retrieve the best-matching highlighted snippets from one or more fields in your search results."

Elasticsearch calls the returned pieces **fragments** (parameters `fragment_size`, `number_of_fragments`) and also **snippets**. Solr uses the `hl` parameter family (`hl.fl`, `hl.snippets`, `hl.fragsize`).

Algolia:

> "each hit includes a `_highlightResult` object with metadata about the matches."
> `matchLevel` "Indicates how well the attribute matched the search query", with values `none`, `partial`, `full`.
> `matchedWords`: "List of query words that matched the record."

Typesense returns a `highlights` array per hit, each entry carrying `field`, `snippet`, and `matched_tokens`.

"matched span" is the underlying Lucene concept but is not consumer facing. Lucene `Matches`:

> "Reports the positions and optionally offsets of all matching terms in a query for a single document"

Lucene also has the older `SpanQuery` family, from which "span" comes.

Verdict: "highlight" is standard. "matched span" is a correct low level term (Lucene `Matches`, `SpanQuery`) but products expose the same thing as `matchedWords` (Algolia), `matched_tokens` (Typesense), or simply the marked up fragment (Elasticsearch, Solr).

## 9. "chunk" versus "passage" versus "fragment"

All three are in use, and they mean different layers.

- **chunk** is the indexing time unit. Elasticsearch `semantic_text` "automatically chunks long text documents during indexing" and "automatically processes long text passages by generating smaller chunks", configured with `chunking_settings` (`strategy`, `max_chunk_size`, `overlap`). Elastic documents how to "retrieve indexed chunks". Note that Elastic's own sentence uses both words: it chunks passages into chunks.
- **passage** is the retrieval and evaluation unit. The MS MARCO passage ranking task and the TREC Deep Learning track passage ranking task are the canonical usage; MS MARCO contains 8.8 million passages ranked for relevance. Vespa also uses "passage ranking" in this sense.
- **fragment** is the highlighting unit. Elasticsearch highlighting parameters are `fragment_size` and `number_of_fragments`, and Elastic's search docs talk about how to "highlight the most relevant fragments from search results". Solr calls the same thing a snippet (`hl.snippets`).

Verdict: "chunk" for a stored sub document is standard current vendor usage (Elastic `semantic_text`). "passage" is standard for the same object seen from the ranking side. Using both interchangeably in one UI is what Elastic itself does, but a single UI is better served picking one. "fragment" should be reserved for highlighted excerpts, since that is what every engine means by it.
