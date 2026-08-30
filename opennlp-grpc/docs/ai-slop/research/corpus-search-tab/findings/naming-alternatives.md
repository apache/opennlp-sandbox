# Renaming "Explore an immutable index" and "Compound query builder"

Owner's concerns, verbatim:

> "on the corpus search page 'explore an immutable index' sounds good, but I feel like we can say
> something more concise. 'NLP search index creation' might be easier to understand? What other
> choices are there?"

> "The compound query builder: a user may be confused what that means."

External precedent for every proposal is recorded in `../reference/naming-*.md` (fetched
2026-08-28). FACT lines describe today's code; OPINION lines are recommendations with a priority.

## 1. What this tab actually does

FACT. The heading is `<h3 id="server-search-heading">Explore an immutable index</h3>` at
index.html:550, under the kicker "Server-backed semantic search" (index.html:549) and above
"Search a configured corpus, compare transformed chunks with their authoritative source spans, and
inspect the document evidence behind every score." (index.html:551-552). The tab button is labelled
"Corpus search" (index.html:47).

FACT. The tab reads. It never writes. Its only mutating call would be a search POST, and
`ServerSearchWorkbench` exposes exactly three operations: `listIndexes`, `search`, `analyzeSource`
(`src/server-search-workbench.ts:56-60`). Index creation lives on Workflows (`IndexDocuments`,
heading "Build and explore in one flow", index.html:390) and index sealing, checkpointing,
aliasing, and rebuilding live on Lifecycle (index.html:1000-1058).

### FACT: "NLP search index creation" would be wrong on this tab

It describes the Workflows tab, not this one. Nothing here creates an index, and putting a
"creation" heading on a read-only search screen would leave a first-time user hunting for a
create button that does not exist. If that phrase is wanted anywhere, it belongs on Workflows,
whose current heading is "Build and explore in one flow".

### FACT: "immutable" is also wrong, for a different reason

`ListSearchIndexes` returns startup bundles AND live dynamic workspaces in one list
(`OpenNlpSearchServiceImpl.java:222-224`), and the front end lists all of them without filtering
(`src/server-search-workbench.ts:150-152`). `readSearchIndexes` explicitly accepts descriptors whose
`immutable` flag is false (`src/search-adapter.ts:195`, and the unit test
`test/search-adapter.test.ts:136` "accepts mutable server workspace descriptors whose default false
flag is omitted"). An index built on Workflows appears in this dropdown, and it is mutable. The
heading and the bridge sentence "This tab searches read-only indexes an operator configured or
persisted." (index.html:553-554) are both false for that case.

## 2. Industry precedent for the heading

From `../reference/naming-elastic.md`, `../reference/naming-opensearch-solr.md`,
`../reference/naming-vector-dbs.md`:

- The most common label for "a screen where you compose a query and see results" is plain
  **"Query"** (Solr Admin's Query tab with its "Execute Query" button, Weaviate Cloud's Query tab,
  Elastic Playground's Query sub-tab) or plain **"Search"**.
- Brand names dominate the rest: Kibana **"Discover"**, OpenSearch **"Query Workbench"**, Amazon
  CloudSearch **"Search Tester"**, Algolia **"Browse"** and **"Search Preview"**, Weaviate
  **"Explorer"**. None of these travel outside their own product.
- Weaviate is the one product that splits the two jobs by name: "Explorer" for browsing records and
  "Query" for writing a query. "Explore" therefore signals browsing, which is not what this tab
  does.

From `../reference/naming-immutable-index.md`, for the read-only concept:

- **"read-only"** is the winner by a wide margin: Elasticsearch `index.blocks.read_only`, Solr's
  `readOnly` collection mode, and OpenSearch ISM's `read_only` / `read_write` actions all mean
  exactly this state, and it is ordinary English.
- **"sealed"** has no precedent in any surveyed product, though the codebase uses it in the
  `SealIndex` RPC and the Lifecycle button "Seal as read-only" (index.html:1008-1009), which is the
  right way round: the jargon is glossed by the standard term.
- **"immutable"** is a Lucene internals word (every live Lucene index is built from immutable
  segments), so it does not communicate "finished, no more writes" to a general audience.
- **"frozen"**, **"closed"**, and **"archived"** all carry the wrong meaning in Elasticsearch
  (frozen is a cost tier; closed indices cannot be searched at all).

## 3. Alternatives for "Explore an immutable index"

Current string: **"Explore an immutable index"**.

| # | Proposed heading | Pros | Cons | Precedent |
| --- | --- | --- | --- | --- |
| A1 | **Search an existing index** | Shortest accurate option. True for both bundles and workspaces, so it survives the mixed dropdown. Verb first, matching every other engine's UI. | Says nothing about the evidence inspector, which is this tab's real differentiator. | Solr Admin "Query" tab; CloudSearch "Search Tester" (`reference/naming-opensearch-solr.md`, `reference/naming-query-builders.md`) |
| A2 | **Search a prepared corpus** | Names the thing that makes this tab different from Workspace search: the index was prepared beforehand. Keeps the tab's existing "corpus" vocabulary. | "corpus" is IR literature, not product vocabulary; vendors say index or collection (`reference/terminology-vector-and-hybrid.md`, item 10). | The tab is already called "Corpus search" (index.html:47) |
| A3 | **Query a read-only index** | Uses the one term three engines agree on. Precise if the dropdown is ever filtered to read-only indexes. | False today, because live workspaces are listed here too. Adopting it means changing the listing or the filter. | Elasticsearch `index.blocks.read_only`; Solr `readOnly`; OpenSearch ISM (`reference/naming-immutable-index.md`) |
| A4 | **Search the server's indexes** | Emphasises "the server ranks, not the browser", which the body copy already stresses ("the browser never ranks", index.html:570). Neutral about mutability. | Slightly abstract; "the server's" is a possessive a newcomer may not parse as meaningful. | Kibana Discover's framing of querying remote data |
| A5 | **Corpus search** (heading matches the tab label) | Maximum concision, zero new vocabulary, no possibility of contradicting itself. | A heading identical to its own tab button carries no extra information; the kicker and the body would have to do all the explaining. | Common in single-purpose consoles (Solr "Query", Qdrant "Console") |
| A6 | **Search a corpus and inspect the evidence** | The only option that names the inspector, which is the tab's genuinely unusual feature. | Longest of the six, and the owner asked for shorter. | Elastic `explain` API framing; TileBars literature (`reference/terminology-hits-and-highlighting.md`, item 7) |

### OPINION (P1): recommended heading

**"Search an existing index"** (A1), with the surrounding copy carrying the specifics:

- kicker stays "Server-backed semantic search" (index.html:549), which already tells the reader who
  does the ranking;
- body sentence keeps naming the inspector, unchanged (index.html:551-552);
- the bridge sentence at index.html:553-554 changes from "This tab searches read-only indexes an
  operator configured or persisted." to something true of the actual list, for example "This tab
  searches every index the server holds: prebuilt read-only corpora and any workspace you built on
  Workflows. Documents you add during this session are searched on Workspace search."

It is shorter than the current heading, it is accurate for every row of the dropdown, and it uses
the verb every competitor uses.

### OPINION (P2): the alternative worth considering instead

If the dropdown is filtered so this tab lists only read-only and persisted indexes (see the open
question in `gating-and-links.md`), then **"Query a read-only index"** (A3) becomes both true and
the single most standard phrasing available, and it makes the split with Workspace search
self-explanatory. That is a product decision, not a copy decision.

## 4. Alternatives for "Compound query builder"

Current strings: `<summary>Compound query builder</summary>` (index.html:606), followed by
"Compose semantic, term, and phrase clauses under one join. While clauses are listed, the search
runs the compound query and the text field above is ignored; keyword matches return highlighted
spans." (index.html:607-611).

FACT (from `../reference/naming-query-builders.md`). No surveyed search product labels a UI
"query builder"; the phrase appears as a library or class name (Coveo's `QueryBuilder`,
Elasticsearch client helpers). Kibana's own clause-by-clause UI is labelled **"Add filter"** with
an **"Edit as Query DSL"** escape hatch, and never uses the words "query builder". The only
end-user-facing label in this space is Coveo's **"Advanced Search"** ("build complex queries using
intuitive wizard modals"). Nobody labels anything "Boolean search". Amazon CloudSearch is the one
product that uses the word "compound": its docs say "compound queries" for multi-clause structured
queries, and its parser mode is called **"structured query"**.

FACT. Two words in the current panel are unexplained jargon. "Compound" appears once as a heading
and never in ordinary prose, and "join" appears as the label of the `#builder-join` select
(index.html:632) with no gloss.

| # | Proposed summary label | Pros | Cons | Precedent |
| --- | --- | --- | --- | --- |
| B1 | **Advanced search: mix keyword and semantic clauses** | Leads with the one phrase general users already know from a hundred other products, then says exactly what is inside. Self-explaining in a collapsed `<summary>`, which costs nothing. | Longest option. "Advanced" is a slightly tired word. | Coveo "Advanced Search" (`reference/naming-query-builders.md`) |
| B2 | **Combine keyword and semantic clauses** | Describes the action, no jargon at all, and names the hybrid nature the tab currently hides. | Does not tell the user this is the optional, more powerful path. | Elastic hybrid retrieval docs; OpenSearch `hybrid` query (`reference/terminology-vector-and-hybrid.md`) |
| B3 | **Structured query** | Matches a real vendor parser mode name and is short. | "Structured" suggests metadata filters and field predicates, which this builder does not expose (no CEL filter, no boost). | Amazon CloudSearch "structured query"; Solr prose |
| B4 | **Add exact-match clauses** | Directly answers the question the user actually has ("how do I search for an exact phrase?"), which the help callout already frames that way at index.html:576-577. | Undersells the semantic and fusion options that are also in the panel. | Kibana "Add filter" |
| B5 | **Query clauses (phrase, term, semantic)** | Enumerates the contents, so nothing is a surprise. Term and phrase are both standard Lucene vocabulary. | Reads like an API reference, not an invitation. | Lucene `TermQuery` / `PhraseQuery`; Elastic `bool` clause vocabulary |
| B6 | **More search options** | Impossible to misread. Zero jargon. | Says nothing; the user has to open it to find out whether it is worth opening. | Generic web convention |

### OPINION (P1): recommended label and intro

Summary: **"Advanced search: mix keyword and semantic clauses"** (B1).

First paragraph, replacing index.html:607-611:

> "Search for exact words and phrases as well as meaning. Add one clause per idea, then choose how
> they combine. While any clause is listed, the box above is ignored and this query runs instead.
> Keyword matches come back highlighted in the results."

Rationale: it keeps every fact the current paragraph carries, drops "compound", drops "join", and
puts the reason to open the panel in the label itself.

### OPINION (P2): the join selector needs the same treatment

FACT. The three options are "All clauses (AND, mean score)", "Any clause (OR, maximum score)",
"Any clause, reciprocal-rank fusion" (index.html:633-636). The scoring words are accurate to the
protocol: `opennlp_query.proto:128-132` defines AND as "score is the mean of operand scores" and OR
as "score is the maximum operand score".

FACT (precedent, `../reference/terminology-fusion-and-scoring.md`). A reviewer arriving from
Elasticsearch will read these differently: Elasticsearch's `bool` query *sums* the scores of
matching `must` and `should` clauses, so "mean" for AND is this project's own choice, and "maximum"
for OR is the *dismax* convention (Lucene `DisjunctionMaxQuery`, Solr `dismax`/`edismax`,
Elasticsearch `dis_max`) rather than `bool.should` behaviour. Reciprocal rank fusion is fully
standard (Cormack, Clarke, Buettcher, SIGIR 2009; Elasticsearch `rrf` retriever; OpenSearch
`score-ranker-processor` `"technique": "rrf"`; Vespa `reciprocal_rank_fusion`).

Proposed option labels, with the mechanics moved to the field help rather than into the option text:

- "Must match every clause"
- "May match any clause"
- "Blend the separate rankings (reciprocal rank fusion)"

and one line of help: "Every clause and any clause score from the clause scores directly (mean and
maximum). Blending ignores the raw scores and combines rank positions, which is the right choice
when keyword and semantic scores are not on the same scale."

## 5. Summary of proposed strings

| Location | Current | Proposed | Priority |
| --- | --- | --- | --- |
| index.html:550 | "Explore an immutable index" | "Search an existing index" | P1 |
| index.html:553-554 | "This tab searches read-only indexes an operator configured or persisted." | "This tab searches every index the server holds: prebuilt read-only corpora and any workspace you built on Workflows." | P1 |
| index.html:606 | "Compound query builder" | "Advanced search: mix keyword and semantic clauses" | P1 |
| index.html:607-611 | "Compose semantic, term, and phrase clauses under one join..." | see section 4 | P1 |
| index.html:633-636 | "All clauses (AND, mean score)" and the two siblings | "Must match every clause" and the two siblings | P2 |
| server-search-workbench.ts:146 | "An operator must configure an immutable index bundle at startup." | see `gating-and-links.md` section 1 | P1 |
| index.html:576-577 | "Need exact matches too? Open the compound query builder..." | "Need exact words or phrases too? Open **Advanced search** below." plus the caveat that it is unavailable on read-only bundles | P1 |

## Questions for the lead

1. Is "corpus" a word we keep? It is correct IR vocabulary and the tab is named for it, but no
   search product uses it in its UI; they all say index or collection. Changing it is a much wider
   edit than this tab.
2. Do we want the heading to promise the evidence inspector (A6), which is the feature nothing else
   in this space ships, at the cost of the concision the owner asked for?
