# Naming in OpenSearch, Apache Solr, and Lucene

Sources fetched 2026-08-28:

- https://docs.opensearch.org/latest/dashboards/query-workbench/
- https://github.com/opensearch-project/dashboards-query-workbench
- https://docs.opensearch.org/latest/dashboards/dql/
- https://docs.opensearch.org/latest/im-plugin/ism/index/
- https://docs.opensearch.org/latest/im-plugin/ism/policies/
- https://solr.apache.org/guide/solr/latest/query-guide/query-screen.html
- https://solr.apache.org/guide/solr/latest/getting-started/solr-admin-ui.html
- https://solr.apache.org/guide/solr/latest/query-guide/standard-query-parser.html
- https://solr.apache.org/guide/solr/latest/deployment-guide/collection-management.html
- https://solr.apache.org/guide/solr/latest/configuration-guide/index-segments-merging.html
- https://lucene.apache.org/core/7_5_0/core/org/apache/lucene/index/package-summary.html

## (a) Names for "search an existing index from the UI"

### OpenSearch: "Query Workbench"

Navigation path in the product: "OpenSearch Plugins" > "Query Workbench". Docs page title is
"Using Query Workbench". Repo tagline: "The OpenSearch Dashboards Query Workbench enables you to
query your OpenSearch data using either SQL or PPL".

Buttons inside the workbench: "SQL" and "PPL" (language toggle), "Run", "Clear", "Explain".

"Workbench" is the notable word here. It signals a tool bench for composing and running a query, not
a browse view.

### OpenSearch: "Discover", "Dev Tools", "Console"

OpenSearch Dashboards keeps the Kibana lineage. Under "Exploring data" the docs list "Using Dev
Tools" siblings: "Console", "Grok Debugger", "Query Profiler", "Query Workbench".

### OpenSearch: "Search Relevance Workbench"

Separate feature (opensearch-project/search-relevance), for comparing result quality between query
configurations. Reinforces "Workbench" as an OpenSearch house term.

### Solr: "Query" screen

Solr Admin UI has a per-core / per-collection tab literally labelled "Query". The reference guide
page is titled "Query Screen".

Exact sentence: "You can use the Query screen to submit a search query to a Solr collection and
analyze the results."

The Query screen is a form whose inputs are named after the raw request parameters, exact labels:

- `q` - "The query event."
- `fq` - "The filter queries."
- `sort` - "Sorts the response to a query in either ascending or descending order ..."
- `start`, `rows` - result offset and page size
- `fl` - "Defines the fields to return for each document."
- `wt` - "Specifies the Response Writer to be used to format the query response."
- `indent` - "Click this button to request that the Response Writer use indentation ..."
- `debugQuery` - "Click this button to augment the query response with debugging information."
- `dismax` - "Click this button to enable the DisMax query parser."
- `edismax` - "Click this button to enable the Extended query parser."
- `hl` - "Click this button to enable highlighting in the query response."
- `facet` - "Enables faceting, the arrangement of search results into categories ..."
- `spatial`, `spellcheck`

The submit button is labelled "Execute Query". Results render as JSON to the right of the form, with
the generated request URL shown above them.

This is the closest existing-product precedent for a plain tab named "Query" that both builds a
request and shows the raw response.

## (b) Terms for an index that can no longer be written to

### Solr: "read-only mode"

From the MODIFYCOLLECTION docs: "Setting the `readOnly` attribute to `true` puts the collection in
read-only mode, in which any index update requests are rejected."

Notes from the same page:

- "Other collection-level actions (e.g., adding / removing / moving replicas) are still available in
  this mode."
- New updates are "rejected with 403 FORBIDDEN error code (ongoing long-running requests are
  aborted, too)"
- "a forced commit is performed to flush and commit any in-flight updates"
- "Removing the `readOnly` property or setting it to false enables the processing of updates and
  reloads the collection."

Solr's chosen word is "read-only". It does not say frozen, sealed, or archived.

### OpenSearch: "Index State Management" (ISM) actions

ISM policies apply named actions to a managed index. Action names and their doc descriptions:

- `read_only` - "Sets a managed index to be read only."
- `read_write` - "Sets a managed index to be writeable."
- `close` - "Closes the managed index. Closed indexes remain on disk, but consume no CPU or memory.
  You can't read from, write to, or search closed indexes."
- Others in the same list: `force_merge`, `replica_count`, `open`, `delete`, `rollover`,
  `notification`, `snapshot`, `index_priority`, `allocation`.

Docs on actions generally: "Actions are the steps that the policy sequentially executes on entering
a specific state."

Again the pair is "read only" versus "writeable". "Closed" means unsearchable as well.

### Lucene: "immutable segments", "commit point", "write-once"

Lucene's own vocabulary for the fact that index files are never rewritten:

- "Segments are immutable; updates and deletions may only create new segments and do not modify
  existing ones."
- "Lucene indexes are 'write-once' files: once a segment has been written to permanent storage (to
  disk), it is never altered."
- "The commit point is a list of segments (and deletions) comprising the whole index at the point in
  time when the commit operation was successfully completed."
- Lucene 4.0 notes: "the segments are fully immutable (write-once), and any changes are expressed
  either as new segments or new lists of deletions."

Important for copy: "immutable" in Lucene is a property of a segment file, not of a whole index a
user has decided to stop writing to. A general audience will not read "immutable index" as "we are
done indexing"; it reads as an internals term.

## (c) Names for the structured / boolean query builder

### Solr: "Standard Query Parser"

Formally "Standard Query Parser", also referred to as the "lucene" parser.

Exact framing versus DisMax: "The key advantage of the standard query parser is that it supports a
robust and fairly intuitive syntax allowing you to create a variety of structured queries. The
largest disadvantage is that it's very intolerant of syntax errors, as compared with something like
the DisMax Query Parser which is designed to throw as few errors as possible."

Boolean operators supported, exact: `AND` (`&&`), `OR` (`||`), `NOT` (`!`), `+` required, `-`
prohibited. "When specifying Boolean operators with keywords such as AND or NOT, the keywords must
appear in all uppercase." Also: "The OR operator is the default conjunction operator".

Note the phrase "structured queries" in Solr's own prose.

### Solr: "DisMax Query Parser" and "Extended DisMax" (eDisMax)

Named in the Query screen as checkboxes `dismax` and `edismax`, described respectively as "the
DisMax query parser" and "the Extended query parser".

### OpenSearch: "Dashboards Query Language (DQL)"

Exact name used in the OpenSearch Dashboards navigation and docs page title: "Dashboards Query
Language (DQL)". It is OpenSearch's rename of Kibana's KQL.

### OpenSearch: "Query DSL"

OpenSearch keeps Elasticsearch's "Query DSL" and "bool query" with `must` / `should` / `filter` /
`must_not`, since it forked from the same code.
