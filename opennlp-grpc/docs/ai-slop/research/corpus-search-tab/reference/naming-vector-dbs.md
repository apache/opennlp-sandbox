# Naming in vector and hosted search products

Covers Weaviate, Qdrant, Pinecone, Vespa, Algolia, Typesense, Meilisearch.

Sources fetched 2026-08-28:

- https://docs.weaviate.io/cloud/tools/query-tool
- https://docs.weaviate.io/cloud/tools/explorer-tool
- https://weaviate.io/workbench
- https://qdrant.tech/documentation/web-ui/
- https://qdrant.tech/documentation/concepts/filtering/
- https://qdrant.tech/documentation/snapshots/
- https://docs.pinecone.io/docs/collections
- https://docs.pinecone.io/guides/manage-data/back-up-an-index
- https://docs.vespa.ai/en/query-language.html
- https://docs.vespa.ai/en/querying/query-api.html
- https://www.algolia.com/doc/guides/managing-results/troubleshooting/troubleshooting-search
- https://support.algolia.com/hc/en-us/articles/7964661896849
- https://typesense.org/docs/guide/tips-for-filtering.html
- https://typesense.org/docs/30.2/api/search.html
- https://www.meilisearch.com/docs/learn/getting_started/search_preview
- https://www.meilisearch.com/docs/learn/filtering_and_sorting/filter_expression_reference

## (a) Names for "search an existing index from the UI"

### Weaviate: "Query tool", "Explorer tool", "Collections tool"

Weaviate Cloud groups these under the "Weaviate Workbench". Doc page titles are literally "Query
tool" and "Explorer tool".

- "Query tool": "a browser-based GraphQL IDE". To reach it you "open the Weaviate Cloud console and
  click on the Query button and choose a cluster from the list". Contains an editor, an execute
  button, a response panel, and a panel for variables and headers.
- "Explorer tool": lets users search and inspect object data through a graphical interface, browsing
  "collections, objects, metadata, and vectors" without writing code.

So Weaviate splits the two jobs by name: "Query" for writing a query, "Explorer" for browsing.

### Qdrant: "Console", "Collections", "Visualize", "Search Quality"

Qdrant's self-hosted Web UI tabs, exact labels:

- "Console" - run REST calls in the browser, test endpoints, inspect responses
- "Collections" - see and manage all collections, create them, upload snapshots, track status and
  size
- "Visualize" - explore vector space with an interactive 2D projection
- "Search Quality" - evaluate and benchmark retrieval precision against ground truth

Qdrant does not ship a tab named "Query" or "Search". Ad hoc searching happens in "Console".

### Algolia: "Browse" tab and "Search Preview"

- "Browse" is a per-index tab in the Algolia dashboard. Docs describe typing into it to "see the
  matching results" and clicking "Add Query Parameter" to "search the index with applied query
  parameters".
- "Search Preview" is reached via "Editor > Search Preview" and is described as a way "to search in
  your production index".

Two labels for the same essential act: "Browse" for the index itself, "Search Preview" for testing
relevance configuration.

### Meilisearch: "Search preview"

Meilisearch Cloud ships a screen labelled exactly "Search preview". Docs: "Meilisearch Cloud gives
you access to a dedicated search preview interface. This is useful to test search result relevancy
when you are tweaking an index's settings."

Access path: log in, navigate to your project, click "Search preview". You select an index, run
searches, and can turn on ranking scores.

### Pinecone: "console"

Pinecone's docs refer to "the Pinecone SDK, API, or console" without a distinct named search screen.

### Vespa: no named GUI query screen

Vespa's query entry points are the "Query API" and the "Vespa CLI" (`vespa query '...'`). The Vespa
Cloud Console provides a log view; the docs surveyed did not name a query builder screen.

## (b) Terms for an index that can no longer be written to

### Pinecone: "collection"

Pinecone repurposes an ordinary word for the sealed-copy idea: "A collection is a static copy of a
pod-based index that only consumes storage. It is a non-queryable representation of a set of
records."

Used for "copying the data from one index into a different index, making a backup of your index, and
experimenting with different index configurations". Not available for serverless indexes.

Cautionary note for copy: Pinecone's "collection" is not searchable at all, and everywhere else in
the industry "collection" means a live index (Solr collection, Weaviate collection, Qdrant
collection). Reusing "collection" to mean "sealed" would be actively confusing.

Newer Pinecone serverless docs use "backup" instead: "Create backups of serverless indexes to
protect data, copy indexes, or experiment with configurations using the Pinecone SDK, API, or
console."

### Qdrant: "snapshot"

"Snapshots are tar archive files that contain data and configuration of a specific collection on a
specific node at a specific time."

A collection level snapshot "only contains data within that collection, including the collection
configuration, all points and payloads". Used "to archive data or easily replicate an existing
deployment".

"Snapshot" reliably means a point-in-time copy you restore from, not a live index you can still
search.

### Weaviate, Typesense, Meilisearch, Algolia, Vespa

None of these surface a first-class "this index is now sealed" state in their UI vocabulary. They
talk about backups, snapshots, and index settings instead. Absence of a term is itself a finding:
there is no widely shared industry word for "an index that is finished being written".

## (c) Names for the structured / boolean query builder

### Qdrant: "Filtering" and "Filtering clauses"

Doc page title is "Filtering". Clause names match Elasticsearch's:

- "Clauses are different logical operations, such as `OR`, `AND`, and `NOT`."
- `must` - "When using `must`, the clause becomes `true` only if every condition listed inside
  `must` is satisfied."
- `should` - "When using `should`, the clause becomes `true` if at least one condition listed inside
  `should` is satisfied."
- `must_not` - "When using `must_not`, the clause becomes `true` if none of the conditions listed
  inside `must_not` is satisfied."

And on nesting: "Clauses can be recursively nested into each other so that you can reproduce an
arbitrary boolean expression."

Condition types include `nested`, `is_empty`, `is_null`, `has_id`, plus prefix, full-text, range and
geo matching.

### Typesense: "filter_by"

Parameter names: `q`, `query_by`, `filter_by`, `sort_by`, `facet_by`.

Docs: "You can use the filter_by search parameter to filter results by a particular value(s) or
logical expressions." Syntax: "The base format for a filter is field: <operator> <value>, and every
filter field must have a : after it."

Operators: `:` (non-exact, word-level partial match on string fields), `:=` (exact), plus range forms
such as `average_rating:>100`, combined with `&&` and `||`.

### Meilisearch: "filter expression"

Docs page title: "Filter expression reference". A filter expression is "made of attributes, values,
and several operators" and "can be written as a string, array, or mix of both". The building block is
a "condition", written in "attribute OPERATOR value format".

Operators listed: `=`, `!=`, `>`, `>=`, `<`, `<=`, `TO`, `EXISTS`, `IN`, `NOT`, `AND`, `OR`,
`IS EMPTY`, `IS NULL`, `CONTAINS`, `STARTS WITH`.

Precedence stated explicitly: "`AND` has higher precedence than `OR`" and `NOT` "has higher
precedence than `AND` and `OR`".

Array form: "Outer array elements are connected by an `AND` operator" while "Inner array elements are
connected by an `OR` operator". "Array filters can have a maximum depth of two."

### Vespa: "Vespa Query Language", "YQL"

Docs page title: "Vespa Query Language - YQL". Framing: "Vespa accepts unstructured human input and
structured queries for application logic separately, then combines them into a single data
structure."

YQL is SQL-shaped: `select * from <source> where ...`, plus `order by`, `limit`, `offset`, and
`group()` / `output()` for grouping.

### Algolia: "filters", "facetFilters", "numericFilters"

Algolia keeps the builder in parameters rather than a language. The dashboard's "Browse" tab exposes
this through an "Add Query Parameter" control rather than a clause builder.

### Weaviate: GraphQL, plus "hybrid" search

Weaviate's structured query surface is GraphQL, edited in the "Query tool" GraphQL IDE. Filter
composition uses `where` style operands rather than a named boolean DSL.
