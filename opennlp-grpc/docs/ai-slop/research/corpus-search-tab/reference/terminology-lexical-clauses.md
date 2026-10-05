# Terminology: lexical clause types (phrase, term, slop, match modes)

Sources fetched 2026-08-28:

- https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-match-query-phrase.html
- https://lucene.apache.org/core/9_10_0/core/org/apache/lucene/search/PhraseQuery.html
- https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-terms-query
- https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-bool-query
- https://solr.apache.org/guide/solr/latest/query-guide/standard-query-parser.html
- https://solr.apache.org/guide/solr/latest/query-guide/faceting.html
- https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-fuzzy-query
- https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-boosting-query
- https://www.elastic.co/docs/reference/aggregations/search-aggregations-bucket-terms-aggregation

## 1. "phrase" clause and "slop"

Both words are standard and both are the vendor's own word.

Elasticsearch, `match_phrase` query:

> "The `match_phrase` query analyzes the text and creates a `phrase` query out of the analyzed text."

Elasticsearch, `slop` parameter of `match_phrase`:

> "(Optional, integer) Maximum number of positions allowed between matching tokens. Defaults to `0`. Transposed terms have a slop of `2`."

Apache Lucene, `PhraseQuery`:

> "A Query that matches documents containing a particular sequence of terms. A PhraseQuery is built by QueryParser for input like `"new york"`."

Lucene `getSlop()`:

> "The slop is an edit distance between respective positions of terms as defined in this PhraseQuery and the positions of terms in a document."

and

> "The slop defines the maximum edit distance for a document to match."

Apache Solr exposes the same concept through query syntax rather than a named field. Solr standard query parser, proximity searches:

> "To perform a proximity search, add the tilde character ~ and a numeric value to the end of a search phrase."

Example given by Solr: `"jakarta apache"~10` matches those terms within 10 words of each other.

Notes for the audit:

- "phrase" as a clause label matches Lucene `PhraseQuery` and Elasticsearch `match_phrase`.
- "slop" is the literal parameter name in both Lucene and Elasticsearch. It is not invented.
- Slop is an edit distance over token positions, not a character distance. A UI tooltip that says "words between terms" is a simplification consistent with the Elasticsearch wording ("positions allowed between matching tokens") but not with the stricter Lucene wording.

## 2. "term" clause and match modes

"term" as a clause label is standard.

Elasticsearch `terms` query:

> "Returns documents that contain one or more **exact** terms in a provided field."

Lucene `TermQuery` is the single term equivalent, and `BooleanQuery` combines term clauses.

"Match any term" / "Match all terms" is a plain language restatement of a standard control, but no major product uses those exact strings. The vendor forms are:

Elasticsearch `bool` query clauses:

> must: "The clause (query) must appear in matching documents and will contribute to the score."
> should: "The clause (query) should appear in the matching document" (acts as a logical OR)
> filter: "The clause (query) must appear in matching documents. However unlike `must` the score of the query will be ignored."
> must_not: "The clause (query) must not appear in the matching documents"

Elasticsearch `minimum_should_match`:

> specifies "the number or percentage of `should` clauses returned documents *must* match."

Solr default operator, `q.op`:

> "specifies the default operator for query expressions. Possible values are 'AND' or 'OR'."

Elasticsearch `match` query has the equivalent `operator` parameter with values `OR` (default) and `AND`.

Notes for the audit:

- The underlying control is universal: AND across terms versus OR across terms.
- The standard vendor labels are `operator: AND` / `operator: OR` (Elasticsearch match), `q.op=AND` / `q.op=OR` (Solr), or `minimum_should_match`.
- "Match any term" / "Match all terms" is nonstandard as a string but unambiguous and maps one to one onto OR / AND. It is the plain English form used by consumer search settings rather than by engine APIs.

## 11 (partial). Terms a reviewer may expect but that do not appear

### facet

Apache Solr:

> "Faceting is the arrangement of search results into categories based on indexed terms."

Elasticsearch does not ship a query named "facet" in current versions. It exposes the same capability as aggregations. Terms aggregation:

> "A multi-bucket value source based aggregation where buckets are dynamically built - one per unique value."

Products exposing it: Solr (`facet`, `json.facet`), Elasticsearch and OpenSearch (aggregations), Algolia (`facets`, `refinementList` widget), Typesense (`facet_by`, `facet_counts`).

### filter

Standard meaning: a clause that restricts the result set without contributing to the relevance score. Elasticsearch `bool` `filter`:

> "The clause (query) must appear in matching documents. However unlike `must` the score of the query will be ignored."

Solr uses `fq` (filter query) for the same thing.

### boost

Elasticsearch `boosting` query:

> "Returns documents matching a `positive` query while reducing the [relevance score] of documents that also match a `negative` query."

`negative_boost`:

> "Floating point number between `0` and `1.0` used to decrease the [relevance scores] of documents matching the `negative` query."

Solr standard query parser, term boosting:

> "Boosting allows you to control the relevance of a document by boosting its term."

Solr syntax is a caret and a factor, for example `jakarta^4 apache`. Elasticsearch also accepts a per clause `boost` parameter on most query types.

### fuzzy

Elasticsearch `fuzzy` query:

> "Returns documents that contain terms similar to the search term, as measured by a Levenshtein edit distance."

and

> "An edit distance is the number of one-character changes needed to turn one term into another."

Solr standard query parser:

> supports "fuzzy searches based on the Damerau-Levenshtein Distance or Edit Distance algorithm."

Solr syntax is a trailing tilde with an optional distance 0 to 2, for example `roam~1`. Note the collision with proximity syntax: `~n` after a quoted phrase means slop, `~n` after a bare term means fuzziness.
