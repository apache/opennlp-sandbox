# Reference: search engine terminology (family B)

Evidence gathered from vendor documentation for the gRPC Workbench terminology audit.
All excerpts are verbatim from the URL listed above them. Fetch date for every source: 2026-08-28.
Excerpts are short by design. Follow the URL for full context.

## Apache Lucene

Source: https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/index/package-summary.html (fetched 2026-08-28)
> "Segments are immutable; updates and deletions may only create new segments and do not modify existing ones."
Source: https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/index/IndexWriter.html (fetched 2026-08-28)
> "Commits all pending changes (added and deleted documents, segment merges, added indexes, etc.) to the index"
> "Moves all in-memory segments to the Directory, but does not commit (fsync) them (call commit() for that)"
Source: https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/codecs/lucene99/package-summary.html (fetched 2026-08-28)
> "Each segment is a fully independent index, which could be searched separately."
Source: https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/search/BooleanQuery.html (fetched 2026-08-28)
> "A Query that matches documents matching boolean combinations of other queries, e.g. TermQuerys, PhraseQuerys or other BooleanQuerys."
> "Return a list of the clauses of this BooleanQuery."
Occur constants named in the javadoc: MUST, SHOULD, FILTER, MUST_NOT.
Source: https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/search/PhraseQuery.html (fetched 2026-08-28)
> "A Query that matches documents containing a particular sequence of terms."
> "The slop is an edit distance between respective positions of terms as defined in this PhraseQuery and the positions of terms in a document."
Source: https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/search/TopDocs.html (fetched 2026-08-28)
> "Represents hits returned by IndexSearcher.search(Query,int)."
> totalHits: "The total number of hits for the query." scoreDocs: "The top hits for the query."
Source: https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/analysis/package-summary.html (fetched 2026-08-28)
> "An Analyzer is responsible for supplying a TokenStream which can be consumed by the indexing and searching processes."
> "A Tokenizer is a TokenStream and is responsible for breaking up incoming text into tokens."
> "A TokenFilter is a TokenStream and is responsible for modifying tokens that have been created by the Tokenizer."
The exact phrase "analysis chain" does not appear on that page. Components are described as chained inside an Analyzer.
Source: https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/store/ByteBuffersDirectory.html (fetched 2026-08-28)
> "A ByteBuffer-based Directory implementation that can be used to store index files on the heap."
Source: https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/search/similarities/TFIDFSimilarity.html (fetched 2026-08-28)
> "Compute any collection-level weight (e.g. IDF, average document length, etc) needed for scoring a query."
The word "corpus" does not appear on that page. Lucene says "collection", "index", "documents".

What Lucene calls X:
- write in-memory state to disk: `commit` (durable, fsync) versus `flush` (buffer to Directory, not durable)
- unit of stored data: `segment`, explicitly immutable
- query pieces: `clause`, `BooleanQuery`, `TermQuery`, `PhraseQuery`, `slop`, `Occur.MUST/SHOULD/FILTER/MUST_NOT`
- results: `hits`, `TopDocs`, `score`
- text pipeline: `Analyzer`, `Tokenizer`, `TokenFilter`
- in-memory index: a `Directory` implementation storing files "on the heap", not a "workspace"
- no use of "seal", "checkpoint", "corpus", "workspace"

## Elasticsearch

Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/glossary.html (fetched 2026-08-28)
> index: "Collection of JSON documents"
> alias: "Secondary name for a group of data streams or indices. Most Elasticsearch APIs accept an alias in place of a data stream or index."
> shard: "Lucene instance containing some or all data for an index."
> segment: "Data file in a shard's Lucene instance."
> flush: "Writes data from the transaction log to disk for permanent storage."
> snapshot: "Backup taken of a running cluster."
> data stream: "Named resource used to manage time series data. A data stream stores data across multiple backing indices."
Glossary term check on the same page: corpus no, provenance no, bundle no, artifact no, catalog no, checkpoint no, seal no, collection no. "workspace" appears, but only as a Kibana UI term: "The main area of the active app in Kibana."
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules-blocks.html (fetched 2026-08-28)
> index.blocks.read_only: "Set to `true` to make the index and index metadata read only, `false` to allow writes and metadata changes."
> index.blocks.write: "Set to `true` to disable data write operations against the index. Unlike `read_only`, this setting does not affect metadata."
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-forcemerge.html (fetched 2026-08-28)
> "We recommend force merging only a read-only index (meaning the index is no longer receiving writes)."
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/aliases.html (fetched 2026-08-28)
> "An alias points to one or more indices or data streams. Most Elasticsearch APIs accept an alias in place of a data stream or index name."
> "During this swap, the `logs` alias has no downtime and never points to both streams at the same time."
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-reindex.html (fetched 2026-08-28)
> "Copy documents from a source to a destination. You can copy all documents to the destination index or reindex a subset of the documents."
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-flush.html (fetched 2026-08-28)
> "Flushing a data stream or index is the process of making sure that any data that is currently only stored in the transaction log is also permanently stored in the Lucene index."
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-refresh.html (fetched 2026-08-28)
> "A refresh makes recent operations performed on one or more indices available for search."
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/index-lifecycle-management.html (fetched 2026-08-28)
> "Index lifecycle management (ILM) automates the management of time-based indices, such as logs and metrics."
Phases listed: hot, warm, cold, frozen, delete. The docs describe the warm transition as "The point at which the index is no longer being updated".
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/data-streams.html (fetched 2026-08-28)
> "A data stream acts as a layer of abstraction over a set of indices that are optimized for storing append-only time series data."
> "A data stream consists of one or more hidden, auto-generated backing indices."
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-bool-query.html (fetched 2026-08-28)
> "A query that matches documents matching boolean combinations of other queries. The bool query maps to Lucene `BooleanQuery`. It is built using one or more boolean clauses, each clause with a typed occurrence."
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/compound-queries.html (fetched 2026-08-28)
> "Compound queries wrap other compound or leaf queries, either to combine their results and scores, to change their behaviour, or to switch from query to filter context."
Family members listed: bool, boosting, constant_score, dis_max, function_score.
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/rrf.html (fetched 2026-08-28)
> RRF is "a method for combining multiple result sets with different relevance indicators into a single result set."
The phrase "hybrid search" did not appear on the RRF reference page fetched.
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/knn-search.html (fetched 2026-08-28)
> "A k-nearest neighbor (kNN) search finds the k nearest vectors to a query vector using a similarity metric such as cosine or L2 norm."
> "Increase `num_candidates` to improve recall and accuracy (at the cost of higher latency)."
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/highlighting.html (fetched 2026-08-28)
> "retrieve the best-matching highlighted snippets from one or more fields in your search results"
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis.html (fetched 2026-08-28)
> "Text analysis is the process of converting unstructured text, like the body of an email or a product description, into a structured format that's optimized for search."
> "Text analysis is performed by an analyzer, a set of rules that govern the entire process."
The phrases "analysis chain" and "analyzer chain" did not appear on that page.

Note on "checkpoint": the replication docs page fetched (https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-replication.html, 2026-08-28) contained no use of "checkpoint". "Global checkpoint" and "local checkpoint" do exist in Elasticsearch internals and cat APIs, but they name a replication sequence-number watermark, not the act of persisting to disk. No primary-source excerpt was captured for that usage here.

What Elasticsearch calls X:
- read-only index: `index.blocks.write` / `index.blocks.read_only`, and prose "a read-only index (meaning the index is no longer receiving writes)". Never "immutable index", never "sealed".
- alias swap: `alias`, atomic, "no downtime"
- rebuild: `reindex`
- group of indices: `data stream` (backing indices), or an `alias`. Not "collection".
- persist: `flush` (translog to Lucene), `refresh` (make searchable), `snapshot` (cluster backup)
- lifecycle: ILM phases hot/warm/cold/frozen/delete
- query families: `bool` query with `clause`s, `compound queries` as the doc-section name
- vector: `kNN`, `similarity metric`, `cosine`, `recall`, `k`, `num_candidates`
- results: `hit`, `score`, `highlight`, `fragment`, `snippet`
- text pipeline: `text analysis`, `analyzer`, `tokenizer`, `token filter`
- "workspace" exists only as a Kibana UI term, never an index

## OpenSearch

Source: https://docs.opensearch.org/1.3/im-plugin/index-alias/ (fetched 2026-08-28)
> "An alias is a virtual index name that can point to one or more indexes."
> "The `add` and `remove` actions occur atomically, which means that at no point will `alias1` point to both `index-1` and `index-2`."
> "Referring to indexes using aliases in your applications allows you to reindex your data without any downtime."
Source: https://docs.opensearch.org/latest/query-dsl/compound/index/ (fetched 2026-08-28)

The current docs site returned navigation only rather than body prose for several pages, so no definition sentence was captured. The navigation itself shows a "Compound queries" section containing Boolean, Boosting, Constant score, Disjunction max, Function score, and Hybrid. Fetches of https://docs.opensearch.org/latest/im-plugin/index-alias/, .../api-reference/alias/aliases-api/, .../search-plugins/hybrid-search/index/, .../query-dsl/compound/hybrid/, and .../im-plugin/ism/index/ all failed to return body text on 2026-08-28. No quotes are offered from them.

What OpenSearch calls X:
- alias, atomic add/remove, reindex without downtime, index (spelled "indexes" in its prose)
- `hybrid` query is classified as a compound query alongside `bool`

## Apache Solr

Source: https://solr.apache.org/guide/solr/latest/getting-started/solr-glossary.html (fetched 2026-08-28)
> Collection: "In Solr, one or more Documents grouped together in a single logical index using a single configuration and Schema."
> Core: "An individual Solr instance (represents a logical index). Multiple cores can run on a single node."
> Shard: "In SolrCloud, a logical partition of a single Collection. Every shard consists of at least one physical Replica."
> Commit: "To make document changes permanent in the index. In the case of added documents, they would be searchable after a commit."
No "corpus" entry appears in the Solr glossary.
Source: https://solr.apache.org/guide/solr/latest/deployment-guide/aliases.html (fetched 2026-08-28)
> "These alternative names for collections are known as aliases."
> "Standard aliases are created and updated using the CREATEALIAS command."
> Use case listed: "Atomically switch to using a newly (re)indexed collection with zero down time"
Source: https://solr.apache.org/guide/solr/latest/configuration-guide/commits-transaction-logs.html (fetched 2026-08-28)
> "A hard commit calls `fsync` on the index files to ensure they have been flushed to stable storage."
> "A soft commit is faster since it only makes index changes visible and does not `fsync` index files, start a new segment, nor start a new transaction log."
Source: https://solr.apache.org/guide/solr/latest/indexing-guide/analyzers.html (fetched 2026-08-28)
> "An analyzer examines the text of fields and generates a token stream."
> Analyzers are built from "a series of discrete, relatively simple processing steps" where "a sequence of more specialized classes are wired together and collectively act as the Analyzer for the field."
> Index time: "when a field is being created, the token stream that results from analysis is added to an index". Query time: "the values being searched for are analyzed and the terms that result are matched against those that are stored".

What Solr calls X:
- group of documents under one schema: `collection` (SolrCloud), `core` (single node)
- persist: `commit`, split into hard commit (fsync) and soft commit (visibility only)
- alias swap: `alias`, `CREATEALIAS`, "zero down time"
- rebuild: "(re)indexed"
- text pipeline: `analyzer`, `tokenizer`, `filter`, index-time and query-time analyzers

## Vespa

Source: https://docs.vespa.ai/en/schemas.html (fetched 2026-08-28)
> A schema "defines a type of data and what we want to compute over it", stored "in files named the same as the schema, with the ending '.sd'".
> A document type is "a named collection of fields".
> A rank profile "specifies what should be computed over the data described by the schema, and how the documents of it should be ranked to select the ones to return in a query response."
Source: https://docs.vespa.ai/en/overview.html (fetched 2026-08-28)
> "Content clusters in Vespa are responsible for storing data and execute queries and inferences over the data."
Source: https://docs.vespa.ai/en/ranking.html (fetched 2026-08-28)
> "Ranking in Vespa is the computation that is done on matching documents during query execution."
> The `first-phase` function "will determine the initial rank of the matches, such that the top k can be selected as response to a query."

What Vespa calls X:
- data shape: `schema`, `document type`, `field`
- scoring config: `rank profile`, `first-phase`, `second-phase`, `global-phase`, `top k`
- deployment units: `content cluster`, `container cluster`
- text plus vector blending is expressed as a rank expression, not a named "hybrid query" object

## Cross-family checks

Milvus (vector database), source: https://milvus.io/docs/glossary.md (fetched 2026-08-28)

> Collection: "In Milvus, a collection is equivalent to a table in a relational database management system (RDBMS)."
> Segment: "A segment is an automatically created data file that stores inserted data."
> Sealed segment: "Once sealed, a segment no longer accepts new data and is transferred to object storage."
"Sealed" is vector-database vocabulary, not search-engine vocabulary. No Lucene, Elasticsearch, OpenSearch, Solr, or Vespa page fetched here uses "seal" or "sealed".

MongoDB Atlas Search, source: https://www.mongodb.com/docs/atlas/atlas-search/compound/ (fetched 2026-08-28)

> "The `compound` operator combines two or more operators into a single query. Each element of a `compound` query is called a clause, and each clause consists of one or more sub-queries."
Google Cloud Firestore, source: https://firebase.google.com/docs/firestore/query-data/queries (fetched 2026-08-28)

> Page heading: "Perform simple and compound queries in Cloud Firestore"
So "compound query" is used both by search engines (Elasticsearch doc-section name, OpenSearch nav section) and by document databases, but Lucene itself does not use the phrase in BooleanQuery javadoc.

Apache Flink, source: https://nightlies.apache.org/flink/flink-docs-release-1.19/docs/learn-flink/fault_tolerance/ (fetched 2026-08-28)

> Checkpoint: "a snapshot taken automatically by Flink for the purpose of being able to recover from faults. Checkpoints can be incremental, and are optimized for being restored quickly."
"Checkpoint" as the verb for persisting state belongs to stream processing and to model training, not to search engines. Search engines say `commit`, `flush`, `refresh`, or `snapshot`.

## Follow-up: how search engines expose "show me what the analyzer did" (fetched 2026-08-28)

Apache Solr Admin UI, Analysis screen
https://solr.apache.org/guide/solr/latest/indexing-guide/analysis-screen.html (2026-08-28)
> "Once you've defined a field type in your Schema, and specified the analysis steps that you want applied to it, you should test it out to make sure that it behaves the way you expect it to."
The page describes invoking the analyzer for any text field, providing sample input, and displaying
the resulting token stream, with "a simple output of only the tokens produced by each step of
analysis".

Elasticsearch analyze API
https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-analyze.html (2026-08-28,
reached through search because the newer docs path returned HTTP 404)
> "The analyze API performs analysis on a text string and returns the resulting tokens."
The endpoint is `_analyze`, and setting `explain` to true "will output all token attributes for each
token". Elastic also ships a task page titled "Test an analyzer".

- The family words for a step-by-step view of text transformation are **analyze**, **test an
  analyzer**, and **explain**. Neither product uses a metaphor such as "X-ray".
