# Naming in Elastic (Elasticsearch / Kibana)

Sources fetched 2026-08-28:

- https://www.elastic.co/docs/explore-analyze/discover
- https://www.elastic.co/docs/explore-analyze/query-filter/languages/kql
- https://www.elastic.co/docs/explore-analyze/query-filter/filtering
- https://www.elastic.co/docs/explore-analyze/query-filter/tools/console
- https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-bool-query
- https://www.elastic.co/guide/en/kibana/current/playground.html
- https://www.elastic.co/guide/en/kibana/current/playground-query.html
- https://www.elastic.co/docs/solutions/elasticsearch-solution-project/query-rules-ui

## (a) Names for "search an existing index from the UI"

### "Discover"

Top-level Kibana application name. The docs call it "the primary tool for exploring your
Elasticsearch data in Kibana" and summarise it as "Search and filter documents, analyze field
structures, visualize patterns, and save findings".

Section headings inside the Discover docs use plain verbs:

- "Search and explore"
- "Analyze fields and documents"
- "Visualize on the fly"
- "Save and share"

Note: "Discover" is a brand name, not a descriptive one. Nothing in the label tells a first-time
user that it runs queries against an index.

### "Console"

Kibana developer tool. Described as "an interactive UI for sending requests to Elasticsearch APIs
and Kibana APIs and viewing their responses". Lives under Kibana's "Dev Tools".

This is the raw-request tool, closest in spirit to a REST scratchpad. Label is generic and widely
understood by developers, not by end users.

### "Playground"

Kibana UI at Kibana > Playground. It exposes two tabs:

- "Chat"
- "Query"

Docs wording: "Chat mode is the default mode" and "Query mode allows you to view and modify the
Elasticsearch query". The doc page "View and modify queries" says: "Select the Query tab to open
the visual query editor" and "You can modify the query by selecting fields to query per index".

Note the phrase "visual query editor" as Elastic's own description of the Query tab.

### "Query rules" (relevance tuning, not free search)

Kibana navigation menu entry under "Relevance". UI strings observed in the docs: a "query ruleset"
with a "Create ruleset" button, and rule types labelled "Pin" and "Exclude".

Not a search-the-index screen; included because it is a case where a search product ships a
rule-building UI without calling it a "query builder".

## (b) Terms for an index that can no longer be written to

Elastic has several distinct terms and they do not mean the same thing.

### "index blocks"

Reference page title: "Index blocks". Settings:

- `index.blocks.read_only` - makes "the index and index metadata read only"
- `index.blocks.write` - "disable data write operations against the index"
- `index.blocks.read_only_allow_delete` - blocks writes but permits deletion

Error string returned to clients when the read-only block trips:
`"FORBIDDEN/5/index read-only (api)"`.

The docs note `index.blocks.write` differs from `index.blocks.read_only` because "this setting does
not affect metadata".

Takeaway: Elastic's own user-facing wording for "cannot be written any more" is "read only" /
"read-only index", implemented via a "block".

### "closed index"

From the Close index API docs: "A closed index is blocked for read or write operations and does not
allow all operations that opened indices allow." Reads are blocked too, so "closed" is stronger than
"read-only". Failure mode is `index_closed_exception`. API: `POST /<index>/_close`, reopen with
`POST /<index>/_open`.

### "frozen tier" (a data tier, not an index state)

Data tier names, exact: "content tier", "hot tier", "warm tier", "cold tier", "frozen tier".

Frozen tier description: "Once data is no longer being queried, or being queried rarely, it may move
from the cold tier to the frozen tier where it stays for the rest of its life."

"Frozen" describes access frequency and storage cost, not writability. It is a cost tier, and using
it to mean "sealed" would be a misuse of Elastic's vocabulary.

### "searchable snapshot"

"Searchable snapshots let you use snapshots to search infrequently accessed and read-only data in a
very cost-effective fashion."

Two mount modes with exact names:

- "fully mounted index" - "Fully caches the snapshotted index's shards in the Elasticsearch
  cluster. ILM uses this option in the `hot` and `cold` phases."
- "partially mounted index" - "Uses a local cache containing only recently searched parts of the
  snapshotted index's data ... ILM uses this option in the `frozen` phase."

And directly: "Fully mounted indices are read-only."

So even where Elastic uses "frozen" and "snapshot", the property "you cannot write to it" is spelled
out with the words "read-only".

## (c) Names for the structured / boolean query builder

### "Query DSL"

Elastic's name for the JSON query language. Reference section is "query-languages/query-dsl".

### "bool query"

"A query that matches documents matching boolean combinations of other queries."

Clause names and their exact one-line definitions:

- `must` - "The clause (query) must appear in matching documents and will contribute to the score."
- `filter` - "The clause (query) must appear in matching documents. However unlike `must` the score
  of the query will be ignored."
- `should` - "The clause (query) should appear in the matching document."
- `must_not` - "The clause (query) must not appear in the matching documents."

Also: "The bool query maps to Lucene `BooleanQuery`."

### "KQL" / "Kibana Query Language"

"The Kibana Query Language (KQL) is a simple text-based query language for filtering data."

The docs also warn: "KQL is not to be confused with the Lucene query language."

Discover's language switcher offers three options, exact labels: "KQL", "Lucene", "ES|QL".

### Kibana filter pills

Exact UI strings from the filtering docs:

- "Add filter"
- "Edit filter"
- "Edit as Query DSL"
- "Save as preset"
- "Delete preset"

"Add filter" is the closest thing Kibana has to a clause-by-clause builder. "Edit as Query DSL" is
the escape hatch from the pill UI into raw JSON.
