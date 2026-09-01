# Corpus search: empty states, gating, and cross-tab links

Scope: `#server-search` in `opennlp-grpc-webapp-default/index.html:546-720`, driven by
`src/server-search-workbench.ts`. FACT lines cite code or observed behaviour; OPINION lines are
recommendations with a priority.

## 1. Empty states the tab renders today

| # | Condition | Exact user-visible text | Where |
| --- | --- | --- | --- |
| E1 | Page load, before discovery | select option "Discovering configured indexes"; description "Discovering server-configured indexes."; status "Search becomes available when the server reports an index." | index.html:583, 641, 643 |
| E2 | Server reports zero indexes | select option "No server indexes configured"; status "The service is available, but it did not report a configured search index."; description "An operator must configure an immutable index bundle at startup." | server-search-workbench.ts:144-146 |
| E3 | `GET /api/v1/search-indexes` throws | status (error style) is the raw error message, else "Search index discovery is unavailable."; description "The analysis workbench remains available." | server-search-workbench.ts:157-160 |
| E4 | Search returned zero usable hits | results panel "No scored chunks were returned."; count "0 hits" | server-search-workbench.ts:289, search-view-model.ts:93-95 |
| E5 | Heatmap view with no hits | "Run a query in heatmap view to shade every chunk of every document." | server-search-workbench.ts:346-347 |
| E6 | Heatmap view showing only top-k | "Only the requested top results are shaded. Search again in heatmap view to score every chunk." | server-search-workbench.ts:361-363 |
| E7 | Response hit the byte budget | status gains "The server response byte limit truncated additional matches." | search-view-model.ts:96-98 |
| E8 | No hit selected | "Select a result to view its original document.", "No chunk comparison yet.", "Select a hit to inspect typed layers.", "No score" | index.html:673, 676, 691, 703, 714 |
| E9 | Lazy source analysis failed | analytics counters read "n/a"; "Typed annotation analysis is unavailable for this source." | server-search-workbench.ts:430-437 |

FACT (E2 is the default demo state). All three running demo instances return `{}` from
`GET /api/v1/search-indexes`, so a first-time visitor to this tab today sees E2 and cannot type
anything: `#server-search-index`, `#server-search-query`, `#server-search-top-k`, and
`#server-search-button` all stay disabled (`server-search-workbench.ts:596-608` in index.html mark
them `disabled` initially; `updateControls`, line 636-644, only enables them when
`this.#indexes.length > 0`).

### OPINION (P1): E2's copy contradicts the tab's own link

The description in E2 says "An operator must configure an immutable index bundle at startup." Two
lines above it in the DOM, the field help offers "Pick a configured index or **build your own
workspace index**" with a working jump to Workflows (index.html:591-592). The prose tells the user
they are blocked; the link tells them they are not. Worse, it is the link that is correct: a
Workflows-built index lands in this very dropdown, because `main.ts:373-375` re-runs
`serverSearchWorkbench.initialize()` on `onIndexChanged`, and `ListSearchIndexes` returns startup
bundles plus dynamic indexes in one list
(`OpenNlpSearchServiceImpl.java:222-224`).

Proposed E2 text:

> "No searchable index yet. Build one from your own text on **Workflows**, or ask an operator to
> configure a prebuilt corpus bundle at startup."

with **Workflows** as a `data-workbench-jump="workflows"` link button, matching index.html:592.

### OPINION (P2): E4 hides real failures

FACT. `readSearchResponse` (`src/search-adapter.ts:232-300`) discards the entire response, returning
`{hits: [], truncated: false}`, when the response's `queryEmbeddingRoute` disagrees with the index
route, and drops individual hits that fail any of a dozen validity checks (missing `chunkGroupId`,
blank `indexedText`, unsupported offset encoding, a source span outside
`COORDINATE_SPACE_CHAR_DOCUMENT`, a score outside [-1, 1]). Every one of those cases shows the user
"No scored chunks were returned.", which reads as "your query matched nothing".

Proposed: keep the strictness, but distinguish "the server returned no hits" from "the server
returned hits this build could not verify", for example "The server returned N results that failed
client-side validation, so none are shown." A user cannot act on a silent drop.

## 2. Model and backend gating: what fails, and what the user sees

| Feature | Requirement | Server error today (HTTP, message) | Front end shows | Should show |
| --- | --- | --- | --- | --- |
| Any search on a selected index | The index still exists | 404 `Unknown dynamic search index 'X'` (DynamicSearchIndexRegistry.java:711, reached via OpenNlpSearchServiceImpl.java:938-942) | that string, raw | "No index named X exists any more" plus a Workflows / Lifecycle jump |
| Any search | The index's pinned embedding model is loaded on some engine | 404 `No engine serves 'legal-encoder'` or `Engine 'cuda' does not serve 'legal-encoder'` (RankedBackends.java:165, 185) | that string, raw | "This index was built with model 'legal-encoder', which is not loaded. Install or configure it on **Models & data**." |
| Any search | The resolved query route matches the index route and dimension | 412 `Query embedding route for index 'X' resolved to model 'A' vector space 'B', expected model 'C' vector space 'D'` (OpenNlpSearchServiceImpl.java:1049-1059) | that string, raw | plain-language version plus a **Lifecycle / Rebuild with a new model** jump |
| Compound query builder | The index provider exposes retained candidates | 501 `Search index 'X' does not execute compound queries` (OpenNlpSearchServiceImpl.java:717-718) | that string, raw, after the user built clauses | the builder should be disabled up front for such an index |
| Term or phrase clause | The index has a keyword component | 501 `Search index 'X' has no configured keyword query provider` (OpenNlpSearchServiceImpl.java:722-727) | that string, raw | disable the "Term" and "Phrase" options for that index |
| Heatmap view (exhaustive) | `supports_all_hits` on the descriptor | 412 `Search index 'X' does not support exhaustive results` (OpenNlpSearchServiceImpl.java:875-877) | not reachable: the front end already checks `index.supportsAllHits` (server-search-workbench.ts:231-233) and falls back to top-k | no change needed |
| Top-k field | `1 <= top_k <= max_top_k` | 400 `SearchIndex top_k must be between 1 and N` (OpenNlpSearchServiceImpl.java:864-867) | not reachable: clamped client-side (server-search-workbench.ts:224-228, 545-549) | no change needed |
| Plain query length | `<= max_query_bytes` | 400 `SearchIndex query.raw_text uses N UTF-8 bytes, exceeding maximum M` (OpenNlpSearchServiceImpl.java:895-899) | pre-checked client-side with a clear message (server-search-workbench.ts:246-251) | no change needed |
| Compound semantic clause length | same bound, per clause | 400, same message shape (requireSemanticQueryBytes, OpenNlpSearchServiceImpl.java:905-925) | NOT pre-checked: the compound branch skips the byte check entirely (server-search-workbench.ts:236-243) | mirror the plain-query pre-check |

### The compound builder is broken on exactly the indexes this tab is named after

FACT (P1). Immutable TurboQuant bundles, the "immutable index" of the heading, cannot run compound
queries at all. `TurboQuantSearchBundleLoader.TurboQuantProvider`
(`opennlp-grpc-search-turboquant/.../TurboQuantSearchBundleLoader.java:406-425`) implements only
`search(float[], int)`. It does not override `queryCandidates()` or `keywordQueryIndex()`, whose SPI
defaults return `null` (`opennlp-grpc-spi/.../SearchIndexProvider.java:65-76`). The service then
throws UNIMPLEMENTED, mapped to HTTP 501 by `GrpcHttpStatusMapper.java:45`. There is a service test
proving this contract: `OpenNlpSearchServiceImplTest.java:464`
`compoundQueriesOnIndexesWithoutCandidatesReportUnimplemented`.

FACT. The tab nonetheless advertises the feature to every user, unconditionally:
index.html:576-577 "Need exact matches too? Open the compound query builder to combine phrase,
keyword, and semantic clauses in one request." The `<details id="compound-builder">` element
(index.html:605) is never disabled, and no code path consults the index before enabling it.

FACT. Dynamic (workspace) indexes are the opposite case and work fine: the dynamic registry attaches
a keyword leg from the built-in `terms` provider automatically when it is present
(`DynamicSearchIndexRegistry.java:156, 1272-1280`), and `terms` is a built-in with the
`SEARCH_PROVIDER_CAPABILITY_KEYWORD` capability (confirmed live:
`GET /api/v1/search-providers` lists `flat_float`, `terms`, `turbo_quant`).

OPINION (P1). The front end has the data to gate this properly and throws it away.
`SearchIndexDescriptor.components` (opennlp_search.proto:485-487) lists each modality with
`SEARCH_COMPONENT_KIND_VECTOR` or `SEARCH_COMPONENT_KIND_KEYWORD`
(opennlp_search.proto:507-515). `readSearchIndexes` (`src/search-adapter.ts:200-229`) reads
neither `components` nor `persisted`. Reading `components` would let the tab: hide or disable the
whole builder when the index declares no keyword component and cannot take candidates; disable just
the Term and Phrase clause kinds when only a vector component exists; and say why, in place, rather
than after the user has composed a query.

## 3. Optional add-on jars

FACT. Two search add-ons are optional (`README.md:112-122`):

- `opennlp-grpc-search-turboquant`: the TurboQuant provider (live workspaces, persistence, and
  immutable bundles) plus the offline bundle builder CLI. "without it the flat-float and terms
  providers still serve dynamic search". It IS bundled in `opennlp-grpc-server-all` and the docker
  images (README.md:828-830), so the demo has it.
- `opennlp-grpc-search-lucene`: BM25 term and phrase execution for compound queries. Its `-all` jar
  is explicitly "not part of `opennlp-grpc-server-all`".

FACT. The Corpus search tab never mentions providers, never calls
`GET /api/v1/search-providers` (only Lifecycle and the corpus workflow do, `main.ts:292, 327`), and
shows the provider only after a search, as a result-inspector fact "Search provider"
(`server-search-workbench.ts:478`, rendered by `providerLabel`, line 646-649).

OPINION (P3). When a server is started without `turbo_quant` and the operator's config names it,
the failure appears as the 404 in section 2 row 1, with wording that blames a dynamic index. The
server should say which provider id was requested and that it is not on the classpath. This is a
server-side message change, not a front-end one.

## 4. Immutable, sealed, and `max_top_k`

FACT. `immutable` is parsed into the front end's `SearchIndex` type (`src/search-adapter.ts:47,
195, 215`) and then never displayed on this tab. Its only consumers are Workspace search, which
filters it out (`src/semantic-workbench.ts:184`), and Lifecycle, which filters it out of the
workspace picker (`src/lifecycle-workbench.ts:129`) and shows it as a fact "Sealed: yes/no"
(`src/lifecycle-workbench.ts:180`).

FACT. Consequently the Corpus search dropdown mixes read-only bundles and live, mutable workspaces
with no visual distinction, while the tab's own bridge text asserts "This tab searches read-only
indexes an operator configured or persisted." (index.html:553-554). A workspace index built on
Workflows is neither read-only nor persisted, and it appears in this dropdown and in the Workspace
search picker at the same time.

FACT. `max_top_k` is honoured: `updateIndexDescription` sets `#server-search-top-k`'s `max` from the
descriptor and clamps the current value (`server-search-workbench.ts:541-549`), and the search path
clamps again to `min(maxTopK, 50000)` (line 54, 224-228). The static `max="50000"` in the markup
(index.html:598) is the same fixed ceiling as `SEARCH_TOP_K_LIMIT`. The description line shows
"... N query bytes max · M response bytes max" (line 550-560), which is the only place a user sees
the index's limits.

OPINION (P2). Show the index's mutability in the dropdown and in the description line, for example
"Legal opinions (read-only bundle)" versus "My text workflow (live workspace)". It is one field the
adapter already parses, it makes the tab's bridge text true, and it removes the puzzle of the same
index appearing on two tabs.

## 5. Cross-tab links

Present today (all use `data-workbench-jump`, bound in `src/workbench-navigation.ts:38-41`):

| From | To | Text | Line |
| --- | --- | --- | --- |
| Corpus search bridge paragraph | Workspace search | "Workspace search" | index.html:553-556 |
| Corpus search index field help | Workflows | "build your own workspace index" | index.html:591-592 |
| Workspace search bridge paragraph | Corpus search | "Corpus search" | index.html:733-736 |

Missing, in priority order:

| # | Situation | Destination | Priority |
| --- | --- | --- | --- |
| L1 | Empty catalog (E2) prose blames the operator and does not link Workflows, though the field help does | Workflows | P1 |
| L2 | Search fails because the index is gone | Workflows (rebuild) and Lifecycle (save a checkpoint or seal so it survives a restart) | P1 |
| L3 | Search fails because the pinned embedding model is not loaded | Models & data | P1 |
| L4 | Query route or dimension mismatch | Lifecycle, "Rebuild with a new model" (index.html:1035-1058) | P2 |
| L5 | A live workspace appears here and the user wants it to stop disappearing on restart | Lifecycle, "Save checkpoint" / "Seal as read-only" (index.html:1006-1010) | P2 |
| L6 | Compound builder is unusable on an immutable bundle | no destination; the correct fix is to disable it and explain, not to link away | P1 |
| L7 | The user created an alias on Lifecycle and wants to search by that name | the search accepts an alias (`OpenNlpSearchServiceImpl.java:939` `aliasRegistry.resolve`), but the dropdown never lists aliases and this tab never calls `GET /api/v1/index-aliases` (only Lifecycle does, `main.ts:293`) | P3 |

OPINION (P3) on L7. Either list aliases as selectable entries ("legal-current -> Legal opinions") or
say plainly that aliases are managed on Lifecycle. Today an alias is a first-class, searchable name
in the API that this tab pretends does not exist.

## Questions for the lead

1. Should the Corpus search dropdown continue to list live workspaces, or should it list only
   immutable and persisted indexes so that the split with Workspace search matches the bridge text?
   The current overlap is the root of both the heading problem and the "read-only" claim.
2. If the compound builder stays visible on indexes that cannot execute it, do we want a disabled
   `<details>` with an inline reason, or should the whole element be removed from the DOM for those
   indexes?
