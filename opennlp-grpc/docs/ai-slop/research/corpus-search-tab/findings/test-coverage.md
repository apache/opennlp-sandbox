# Corpus search: test coverage map and gaps

Scope: features of `#server-search` (index.html:546-720) and the modules behind it
(`src/server-search-workbench.ts`, `src/query-builder.ts`, `src/search-adapter.ts`,
`src/search-view-model.ts`, `src/search-heatmap.ts`), plus the gateway and service code they call.

All paths are relative to the repository root. Front-end tests were read, not executed.

## 1. Tests that exercise this tab today

### Front-end unit tests (`opennlp-grpc-webapp-default/test`, run by vitest)

| File | Test | Feature covered |
| --- | --- | --- |
| `test/server-search-workbench.test.ts:112` | "shows the chunk once when it exactly matches the original span" | `compareChunkText` collapse, `#search-original-panel` hidden, `#chunk-comparison-status` |
| `test/server-search-workbench.test.ts:124` | "shows both copies when the indexed chunk differs from the source span" | same, negative case |
| `test/server-search-workbench.test.ts:135` | "marks the analytics counters unavailable when lazy analysis fails" | `renderAnalysisUnavailable`, `#search-analytics` shows "n/a" |
| `test/server-search-workbench.test.ts:152` | "drops the query field's required constraint while clauses exist" | `updateControls` sets `#server-search-query.required = false` when clauses exist |
| `test/server-search-workbench.test.ts:176` | "adds the drafted clause when Enter is pressed in the clause input" | the `keydown` handler on `#builder-text` |
| `test/server-search-workbench.test.ts:190` | "uses the explicit exhaustive contract for a TurboQuant heatmap" | heatmap view sends `{allHits: true}` when `supportsAllHits` |
| `test/server-search-workbench.test.ts:220` | "sends a requested fifty thousand result limit when the index permits it" | `#server-search-top-k` max attribute and the top-k clamp |
| `test/query-builder.test.ts:25, 34, 60, 74` | four tests | `buildQueryNode` single clause, join nesting for and/or/rrf, per-position validation errors, `clauseLabel` chip text |
| `test/search-adapter.test.ts:111-353` | twenty-two tests | descriptor parsing including the mutable-workspace case (:136) and the typed-semantics rejection (:143), request builders for plain, exhaustive and compound queries, hit normalization, offset encodings, score range, truncation flag, matched spans, keyword-only responses with no query route, provider instance capabilities |
| `test/search-view-model.test.ts:60-128` | seven tests | `scoreColor` scale, offset conversion, `compareChunkText`, `documentAnalytics` and `hitAnnotations`, `SearchSelection`, `searchResultStatus` including truncation, `matchedSegments` |
| `test/search-heatmap.test.ts:53-139` | seven tests | `buildDocumentHeat` grouping and ordering, scored and unscored segments, matched-span retention and dropping, overlap suppression, UTF-8 offset mapping, empty input |
| `test/index.test.ts:105` | "provides server-backed corpus and dynamic workspace search" | asserts the presence of the tab's markup ids, the score legend aria label, and the Workflows jump button |
| `test/workbench-navigation.test.ts:84` | "opens workflows from the build-your-own-index action" | the `data-workbench-jump="workflows"` link at index.html:592 actually switches tabs |

### End-to-end (`opennlp-grpc-webapp-default/e2e`, Playwright, not run here)

| File | Test | Feature covered |
| --- | --- | --- |
| `e2e/corpus-search.spec.ts:22` | "searches a corpus index and inspects the top hit" | tab switch, index discovery, plain query submit, chunk comparison panel agreement, all five analytics counters resolving |

FACT. That spec skips itself when the server reports no index
(`e2e/corpus-search.spec.ts:27-32`: `test.skip(!hasIndex, "The server reports no configured search
index.")`). All three demo instances currently return `{}` from `GET /api/v1/search-indexes`, so on
a default demo this is a skip, and the tab has no executing end-to-end coverage at all.

### Gateway tests (`opennlp-grpc-webapp/src/test`)

| File | Test | Feature covered |
| --- | --- | --- |
| `GrpcJsonSearchApiTest.java:84` | `listsSearchIndexesAsProtobufJson` | `GET /api/v1/search-indexes` shape |
| `GrpcJsonSearchApiTest.java:96` | `listsSearchProviderInstancesAsProtobufJson` | `GET /api/v1/search-providers` |
| `GrpcJsonSearchApiTest.java:110` | `parsesExhaustiveDocumentSearchAndRendersDeduplicatedSource` | `POST /api/v1/search` with `all_hits`, source document deduplication |
| `GrpcJsonSearchApiTest.java:130` | `parsesCompoundQueryRequestsAndRendersMatchedSpans` | compound `QueryNode` JSON parsing and matched spans on the wire |
| `GrpcJsonSearchApiTest.java:235` | `rejectsMalformedSearchProtobufJson` | 400 path |
| `GrpcJsonSearchApiTest.java:247` | `enforcesSearchEndpointMethods` | 405 path |
| `GrpcHttpStatusMapperTest.java` | whole file | the gRPC status to HTTP status table, including UNIMPLEMENTED to 501 |
| `OpenNlpGrpcWebServerTest.java:204` | `servesSearchCatalogAndDocumentShapedHitsOverHttp` | the search catalog and a hit over real HTTP |
| `OpenNlpGrpcWebServerTest.java:231` | `rejectsOversizedBodiesAndUnsupportedMethods` | request size cap |

### Service tests (`opennlp-grpc-service/src/test/java/org/apache/opennlp/grpc/search`)

| File | Test | Feature covered |
| --- | --- | --- |
| `OpenNlpSearchServiceImplTest.java:339` | `executesCompoundQueriesOverADynamicWorkspaceWithMatchedSpans` | compound execution and matched spans |
| `OpenNlpSearchServiceImplTest.java:387` | `compoundKeywordQueriesNeedNoEmbeddingRoute` | keyword-only compound queries |
| `OpenNlpSearchServiceImplTest.java:416` | `compoundSemanticClausesUseTheSelectedProvidersVectorSearch` | semantic clause dispatch |
| `OpenNlpSearchServiceImplTest.java:464` | `compoundQueriesOnIndexesWithoutCandidatesReportUnimplemented` | the 501 an immutable bundle returns for any compound query |
| `OpenNlpSearchServiceImplTest.java:505` | `rejectsUnknownIndexBlankQueryAndInvalidTopK` | NOT_FOUND and INVALID_ARGUMENT status codes |
| `OpenNlpSearchServiceImplTest.java:521` | `embedsOnDeclaredRouteAndReturnsStableNegativeScoresWithProvenance` | stable ordering, negative scores, provenance on the response |
| `OpenNlpSearchServiceImplTest.java:540, 561, 608` | exhaustive results, the fifty-thousand ceiling, the refusal when not advertised | the `all_hits` contract behind the heatmap |
| `OpenNlpSearchServiceImplTest.java:192, 237` | alias resolution on search, alias collisions | aliases are searchable names |
| `search/query/CompoundQueryExecutorTest.java`, `CompoundQueryValidatorTest.java`, `TermsKeywordQueryIndexTest.java` | whole files | join algebra, validation, keyword matching |
| `SearchWireContractTest.java`, `QueryWireContractTest.java`, `ProviderWireContractTest.java` | whole files | protobuf field contracts for search, query nodes, providers |

## 2. Features with no test I could find

Numbered for reference; each names the function or element id so the gap is actionable.

### Front end, user-visible states

1. **The empty-catalog state.** `ServerSearchWorkbench.initialize` lines 143-147 add the option
   "No server indexes configured", set the status "The service is available, but it did not report a
   configured search index.", and set the description "An operator must configure an immutable index
   bundle at startup." No test mounts the workbench with `listIndexes: () => Promise.resolve([])`.
   This is the state a first-time visitor to the demo actually sees.
2. **The discovery-failure state.** `initialize`'s catch, lines 157-160 (`#server-search-status`
   error style plus "The analysis workbench remains available."). Untested.
3. **The search-failure state.** The catch at lines 275-280 clears hits, re-renders, and prints
   `errorMessage(error, "Search failed.")`. No test asserts that a rejected `search` puts anything in
   `#server-search-status`, and nothing covers the network-level `TypeError` case that produces
   "Failed to fetch" (see `legal-opinions-failure.md`).
4. **The compound-query byte pre-check gap.** The `max_query_bytes` guard at lines 244-251 is
   untested, and it is also not applied on the compound path at all, so there is neither a test nor
   an implementation for oversized semantic clauses on the client side.
5. **The index description line.** `updateIndexDescription` (lines 536-560) composes
   "corpus title, N vectors, D dimensions, cosine, license, N query bytes max, M response bytes max"
   plus the provenance sentence. Nothing asserts it, including the "unknown size" and "unknown
   dimensions" fallbacks.

### Front end, compound builder

6. **The compound request itself.** No workbench test asserts that submitting with clauses present
   calls `search` with a `compoundQuery` payload. `buildQueryNode` is unit tested in isolation
   (`test/query-builder.test.ts`) and the workbench is tested only for the `required` toggle and the
   Enter key, so the wiring between `#builder-join`'s value and the emitted `JOIN_OPERATOR_*` is
   never exercised through the DOM.
7. **The text field is ignored while clauses exist.** Stated at index.html:608-610 and implemented
   at line 236 (`const compound = this.#clauses.length > 0`). Untested.
8. **Clause removal and clearing.** The per-chip remove button (`renderClauses`, lines 195-201) and
   `#builder-clear-button` (lines 131-135). Untested.
9. **Clause-kind control visibility.** `updateBuilderControls` (lines 205-209) shows `#builder-mode`
   only for term clauses and `#builder-slop` only for phrase clauses. Untested.
10. **The blank-clause guard.** `addClause` sets the status "Enter clause text before adding it."
    (lines 163-166). Untested.
11. **Invalid compound query handling.** Lines 240-243 catch `buildQueryNode`'s error and show it.
    Untested at the workbench level.

### Front end, results and heatmap rendering

12. **The result list DOM.** `renderResults` (lines 286-325): the two-digit rank, the
    "chunk X · corpus" provenance line, the 120-character preview, and the score badge colour.
    Untested.
13. **Selecting a hit by clicking a result row.** Only the automatic selection of `hits[0]` is
    exercised (via `searchOneHit` in the test helper). The click handler at line 302 and the
    `aria-pressed` bookkeeping in `selectHit` (lines 407-412) are untested.
14. **The heatmap DOM.** `renderHeatmap` (lines 343-365) and `heatSegmentNode` (lines 368-400):
    `.heat-document` articles, `.heat-chunk` buttons, their `title` and `aria-label` score text, the
    `<mark class="matched-span">` nodes, and clicking a heat chunk to select its hit. Only the pure
    `buildDocumentHeat` function underneath is tested.
15. **The partial-coverage note.** "Only the requested top results are shaded. Search again in
    heatmap view to score every chunk." (lines 358-364), driven by `#fullCoverage` (line 267).
    Untested, and its condition is the most intricate boolean on the tab.
16. **The view toggle.** `setHeatmapView` (lines 330-340): `aria-pressed` on
    `#server-view-list-button` and `#server-view-heatmap-button`, the hidden swap, and the disabling
    of `#server-search-top-k` in heatmap view. Untested.
17. **The nineteen inspector facts.** `renderHit` (lines 465-510) renders Document ID through
    Source, including the conditional artifact and license rows and the external-link form of
    `addFact`. Only the chunk-comparison portion of `renderHit` is tested.
18. **`#server-result-count`.** The "N hits" pluralization at line 287. Untested.

### End to end

19. **Compound queries end to end.** `e2e/corpus-search.spec.ts` never opens `#compound-builder`.
20. **The heatmap end to end.** The spec never clicks `#server-view-heatmap-button`.
21. **Any corpus-search e2e at all on a default demo.** The spec skips without a configured index
    (line 32), and the demo images ship none. There is no fixture that creates a small index first,
    so this tab's e2e is effectively opt-in on an operator's own machine.

### Gateway

22. **Connection reuse.** No test in `OpenNlpGrpcWebServerTest` sends two requests on one keep-alive
    connection, let alone across the JDK's 30-second idle interval. This is the untested behaviour
    that produces the reported "Failed to fetch": a raw-socket reproduction is in
    `legal-opinions-failure.md` section 3.
23. **Error-body text.** `GrpcJsonSearchApiTest` asserts status codes and shapes; no gateway test
    pins the `message` string a user actually reads for a missing index.

### Service

24. **Message text for a missing index.** `OpenNlpSearchServiceImplTest.java:509` asserts
    `Status.Code.NOT_FOUND` only. Nothing pins "Unknown dynamic search index 'X'", so the wording
    that misdescribes a configured immutable index (see `legal-opinions-failure.md` section 3) is
    not protected or challenged by a test.
25. **Keyword clause without a keyword component.** The 501 at
    `OpenNlpSearchServiceImpl.java:722-727` ("has no configured keyword query provider") has no test
    of its own; only the sibling "does not execute compound queries" path is covered
    (`OpenNlpSearchServiceImplTest.java:464`).

## 3. Suggested minimum additions

OPINION (P1). Three cheap unit tests would cover the states a first-time user is most likely to
meet, all of them unprotected today:

- mount with an empty index list and assert the three empty-state strings (gap 1);
- mount with `search: () => Promise.reject(new Error("boom"))` and assert `#server-search-status`
  carries the message and the error class (gap 3);
- mount with `search: () => Promise.reject(new TypeError("Failed to fetch"))` and assert the tab
  shows a network-specific message rather than the raw exception text. That test should be written
  together with the fix, since it fails against the current code by design.

OPINION (P1). One gateway test: issue a request, wait past the idle interval, issue a second request
on the same connection, and assert it succeeds (gap 22). It is the only test that would have caught
the reported bug.

OPINION (P2). One e2e fixture that builds a tiny workspace index through
`POST /api/v1/index-documents` before the corpus-search spec runs, so gaps 19 to 21 stop being
permanently skipped. That turns the existing spec from decorative into real coverage.

## Questions for the lead

1. Is there an environment in CI where a search bundle is configured? If not, the corpus-search e2e
   spec has never run to completion, and we should either add the fixture or state plainly in the
   spec that it is a local-only check.
2. Should the compound builder get its own unit test file (a builder-level DOM test), or should the
   coverage live in `server-search-workbench.test.ts` alongside the two clause tests that exist?
