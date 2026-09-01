# Workspace search tab: test coverage map

Every test named below was read, not inferred. Test names are quoted verbatim so they can
be found with a plain grep. Priorities on the gaps: P1 confusing or broken for a
first-time user, P2 worth doing, P3 polish.

## 1. Test files that touch this tab

| Layer | File | Tests relevant to this tab |
|---|---|---|
| Browser unit (vitest) | `opennlp-grpc-webapp-default/test/semantic-workbench.test.ts` | 4 of 4 |
| Browser unit | `opennlp-grpc-webapp-default/test/search-adapter.test.ts` | 21 tests, 3 directly about dynamic workspace descriptors |
| Browser unit | `opennlp-grpc-webapp-default/test/index.test.ts` | 2 of 20 (markup contract) |
| Browser unit | `opennlp-grpc-webapp-default/test/search-view-model.test.ts` | 7, shared with the Corpus search tab |
| Browser unit | `opennlp-grpc-webapp-default/test/workbench-navigation.test.ts` | 5, the jump mechanism this tab uses |
| Browser unit | `opennlp-grpc-webapp-default/test/document-window.test.ts` | 3, only the complete-graph bound this tab reads |
| Browser e2e (Playwright) | `opennlp-grpc-webapp-default/e2e/workbench.spec.ts` | 1 |
| Gateway (JUnit) | `opennlp-grpc-webapp/src/test/java/.../GrpcJsonSearchApiTest.java` | 10, 3 directly on the workspace flow |
| Service (JUnit) | `opennlp-grpc-service/src/test/java/.../search/DynamicSearchIndexRegistryTest.java` | 14 |
| Service (JUnit) | `opennlp-grpc-service/src/test/java/.../search/OpenNlpSearchServiceImplTest.java` | 30, several on the workspace lifecycle |
| Service (JUnit) | `opennlp-grpc-service/src/test/java/.../search/SearchWireContractTest.java` | 6, proto field pinning |

Note: `src/test/` in this module holds only Java assets. The vitest suites live in
`opennlp-grpc-webapp-default/test/`, run by `npm test`
(`package.json:8`, `tsc --noEmit && vitest run`).

## 2. Feature to test map

Legend: **Covered**, **Partial**, **None**.

| # | Feature on this tab | Code | Test | Status |
|---|---|---|---|---|
| 1 | First search indexes the current document, then searches | semantic-workbench.ts:287-322 | semantic-workbench.test.ts:71 `indexes the current document on the server when the first workspace query is submitted` | Covered |
| 2 | Only the projected document fields are sent | semantic-workbench.ts:661-675 | same test, `expect(index).toHaveBeenCalledWith(expect.objectContaining({ documents: [...] }))` | Covered |
| 3 | `allHits` used when the index advertises it | semantic-workbench.ts:308-309 | same test asserts `allHits: true` | Covered |
| 4 | `min(50, maxTopK)` fallback when it does not | semantic-workbench.ts:310 | none | **None** |
| 5 | Picker hides immutable and heatmap indexes | semantic-workbench.ts:183-185 | semantic-workbench.test.ts:317 `attaches search to a picked existing workspace without adding a document` | Covered |
| 6 | Attaching an existing workspace does not re-index | semantic-workbench.ts:206-232 | same test, `expect(index).not.toHaveBeenCalled()` | Covered |
| 7 | Attach status text | semantic-workbench.ts:226-227 | same test asserts `Attached to 'Workbench index'` | Covered |
| 8 | Detach path, blank option selected | semantic-workbench.ts:209-213 | none | **None** |
| 9 | Attach failure, workspace vanished server-side | semantic-workbench.ts:218-220 (`The selected workspace no longer exists on the server.`) | none | **None** |
| 10 | `Add to server workspace` button click | semantic-workbench.ts:243-261 | none. No test dispatches a click on `#add-to-index-button`. | **None** |
| 11 | The unreachable no-embeddings status | semantic-workbench.ts:246 | none | **None** |
| 12 | `Clear workspace index` deletes the server index | semantic-workbench.ts:263-285 | none. `deleteIndex` is stubbed as `vi.fn()` in all four tests and never asserted for the clear path. | **None** |
| 13 | Provider selection reaches `IndexDocuments` | semantic-workbench.ts:333 | **Partial.** semantic-workbench.test.ts:110-111 sets `provider.value = "STANDARD_SEARCH_PROVIDER_TURBO_QUANT"` but the `objectContaining` assertion at line 122 checks only `documents`. The `provider: { standard: ... }` field is never asserted. | **Partial** |
| 14 | Provider select locks once a workspace exists | semantic-workbench.ts:609 | none | **None** |
| 15 | `#index-count` reflects the attached workspace size | semantic-workbench.ts:621 | none | **None** |
| 16 | Result list markup, rank, model, cosine, preview | semantic-workbench.ts:406-434 | none for the workspace list. The projection test at semantic-workbench.test.ts:140 exercises heatmap lanes, not `#search-results`. | **None** |
| 17 | `Open` button calls `openDocument` | semantic-workbench.ts:430 | none | **None** |
| 18 | Empty-result message `No compatible vectors were found in the server workspace.` | semantic-workbench.ts:408 | none | **None** |
| 19 | Error status styling (`is-error`) on a failed search | semantic-workbench.ts:317, 624 | none | **None** |
| 20 | Discovery failure leaves the picker intact | semantic-workbench.ts:199-201 (`refreshWorkspacesQuietly`) | none | **None** |
| 21 | Heatmap query indexes per projection and renders lanes | semantic-workbench.ts:343-401 | semantic-workbench.test.ts:140 `searches every projection exhaustively with TurboQuant and renders selectable lanes` | Covered |
| 22 | Sentiment lane selection opens typed annotations | semantic-workbench.ts:479-491 | semantic-workbench.test.ts:283 `opens typed annotations when a sentiment segment is selected` | Covered |
| 23 | Heatmap scratch indexes are deleted on a new document | semantic-workbench.ts:521-527 | **Partial**, exercised indirectly by test 21 but never asserted | **Partial** |
| 24 | Descriptor parsing, mutable workspace with omitted `immutable` | search-adapter.ts:195 | search-adapter.test.ts:136 `accepts mutable server workspace descriptors whose default false flag is omitted` | Covered |
| 25 | Descriptor rejection without typed semantics | search-adapter.ts:196-199 | search-adapter.test.ts:143 `rejects descriptors without typed search semantics` | Covered |
| 26 | Provider capability parsing | search-adapter.ts:150-177 | search-adapter.test.ts:353 `reads provider instances with lowercased capabilities` | Covered |
| 27 | Static markup of this tab | index.html:722-788 | index.test.ts:105 `provides server-backed corpus and dynamic workspace search` and index.test.ts:166 `scopes the hero to the Analyze panel and bridges the two search tabs` | Covered, markup only |
| 28 | The `corpus-search` jump on this tab works in a browser | index.html:735 | e2e/workbench.spec.ts:33 `bridges configured search to workflows and workspace search` | Covered |
| 29 | Gateway: index then delete a workspace over JSON | GrpcJsonApi | GrpcJsonSearchApiTest:255 `indexesAndDeletesAWorkspaceThroughProtobufJson` | Covered |
| 30 | Gateway: exhaustive search rendering | GrpcJsonApi | GrpcJsonSearchApiTest:110 `parsesExhaustiveDocumentSearchAndRendersDeduplicatedSource` | Covered |
| 31 | Gateway: index lifecycle over JSON | GrpcJsonApi | GrpcJsonSearchApiTest:150 `drivesTheIndexLifecycleThroughProtobufJson` | Covered |
| 32 | Service: create, extend, search, delete | DynamicSearchIndexRegistry | DynamicSearchIndexRegistryTest:65 `createsExtendsSearchesAndDeletesAnInMemoryIndex` | Covered |
| 33 | Service: reject documents without a chunk embedding | DynamicSearchIndexRegistry:? | DynamicSearchIndexRegistryTest:102 `rejectsDocumentsWithoutASelectedChunkEmbedding` | Covered |
| 34 | Service: disabled registry rejects mutation | DynamicSearchIndexRegistry.java:1085-1091 | DynamicSearchIndexRegistryTest:154 `disabledRegistryRejectsMutationWithoutPublishingDescriptors` | Covered |
| 35 | Service: persisting a flat workspace reports the missing capability | DynamicSearchIndexRegistry.java:524-526 | OpenNlpSearchServiceImplTest:166 `persistingAFlatWorkspaceReportsTheMissingCapability`, and DynamicSearchIndexRegistryTest:253 `persistRequiresAPersistentProviderInstance` | Covered on the server, **not surfaced in the UI**, see findings/gating-and-links.md section 3.4 |
| 36 | Service: memory and source budget rejection | DynamicSearchIndexRegistry.java:65-72 | DynamicSearchIndexRegistryTest:113, :125, :142 | Covered |
| 37 | Service: sealed index rejects further documents | DynamicSearchIndexRegistry.java:266-268 | none found in DynamicSearchIndexRegistryTest. The seal path is tested for persistence, not for the rejection message. | **None** |

## 3. Ranked gaps

### P1

1. **`Clear workspace index` has no test at all** (row 12). It is the only destructive
   action on the tab, it calls `DeleteSearchIndex`, and nothing verifies that it targets
   the right index, that it also cleans up the heatmap scratch indexes
   (semantic-workbench.ts:275), or that it resets `#search-results`. A regression here
   silently destroys user data.
   Suggested test: `deletes the attached workspace and its heatmap scratch indexes`,
   asserting `deleteIndex` is called with `workspace-one` and with every heatmap index id.

2. **`Add to server workspace` has no test** (row 10). It is the entry point for the
   entire tab and the button lives on a different tab, so nothing else exercises it.
   Suggested test: `indexes the current document when the add button is pressed`,
   dispatching a click on `#add-to-index-button` and asserting the status text
   `Indexed by the gRPC server. 3 chunks available.` (semantic-workbench.ts:254).

3. **The provider choice is set up but never asserted** (row 13). A refactor that dropped
   `provider: { standard: this.#providerSelect.value }` from the request
   (semantic-workbench.ts:333) would pass the whole suite, and every workspace would
   silently be created on the server default.
   Suggested change: extend the existing `objectContaining` at
   semantic-workbench.test.ts:122 with
   `provider: { standard: "STANDARD_SEARCH_PROVIDER_TURBO_QUANT" }`.

### P2

4. **The result list is never rendered in a test** (rows 16, 17, 18). Nothing pins the
   `search-hit` markup, the `01`-padded rank, the `cosine 0.8123` detail line, the
   `Open` button, or the empty-result message. `search()` is only ever stubbed to return
   `{ hits: [], truncated: false }` in all four tests.

5. **No error-path test** (rows 9, 19, 20). Every status string that ends in
   `setStatus(..., true)` is untested, including the raw server messages a user will
   actually meet (`Dynamic search indexing is disabled by the server operator`,
   `Sealed search index '<id>' is immutable`).

6. **The sealed-index rejection message has no service test** (row 37), even though the
   UI reaches it whenever the Lifecycle tab seals a workspace while this tab holds a
   stale picker (findings/gating-and-links.md section 4.2).

7. **No e2e test drives the tab.** `e2e/workbench.spec.ts:33` only clicks into
   `session-search` and straight back out through the `corpus-search` jump. Nothing
   asserts the first-run empty state, that `#semantic-query` is disabled, or that the
   status reads `Analyze an embedding-enabled document, then add it to the server workspace.`
   Compare `e2e/corpus-search.spec.ts`, which does drive its tab.

### P3

8. `#index-count` (row 15) and the provider lock (row 14) are one-line behaviours with
   no test. Cheap to add alongside gap 3.

9. The heatmap scratch-index cleanup (row 23) is exercised but not asserted. Given
   `MAX_INDEXES = 32` (DynamicSearchIndexRegistry.java:65), a leak here exhausts the
   registry.

## 4. Note on the markup contract tests

`index.test.ts:105` asserts on literal substrings of `index.html`, including
`'On-the-fly workspace index'` and `'The browser renders server scores and'`. Any rename
proposed in findings/terminology.md or findings/what-is-a-workspace.md must update those
assertions in the same commit. The same applies to `index.test.ts:166`, which asserts
`'data-workbench-jump="session-search"'` and `'data-workbench-jump="corpus-search"'`, and
to `e2e/workbench.spec.ts:39`, which selects on `[data-workbench-tab="session-search"]`.
The HTML **id** `session-search` is therefore load-bearing for the tests even though the
label is "Workspace search".

## Questions for the lead

1. Is there an appetite for an e2e spec that actually drives this tab end to end
   (analyze, add, search), or is that deliberately left to
   `e2e/workbench.spec.ts`'s opt-in `OPENNLP_E2E_WORKFLOW_WRITE=1` workflow test?
2. Gaps 1 to 3 are all small additions to the existing
   `describe("workspace search")` block. Worth doing before or after the renaming work,
   given the markup assertions in section 4 will churn either way?
