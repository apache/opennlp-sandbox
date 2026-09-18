# Workflows tab: test coverage map and gaps

Scope: tests that exercise `section#workflows-workbench` (`index.html:384-543`) and
`src/corpus-workflow.ts`. The full unit suite was run once (`npx vitest run`, 32 files, 225 tests,
all passing, 2026-08-28). Playwright was not run.

FACT sections list what exists. OPINION sections propose additions with a priority.

---

## 1. FACT: every test that touches this tab

### 1.1 Unit tests, `opennlp-grpc-webapp-default/test/`

| File | Test | What it covers |
| --- | --- | --- |
| `corpus-workflow.test.ts:196-249` | `runs the guided corpus-to-search pipeline and renders both result views` | Full happy path: stage order `analyze, analyze, vocabulary, train, embed, embed, index, search`; dictionary id forwarded; two documents in the index request; `onModelTrained` called; six stages complete; two analysis cards; two heat documents; artifact ids rendered; `Open full analysis` fires `onOpenAnalysis` |
| `corpus-workflow.test.ts:251-288` | `uses corpus-only vocabulary and can search the built index again without retraining` | Empty dictionary select yields no `dictionaryArtifactId`; re-submitting `#workflow-search-form` searches again without a second train or index call |
| `index.test.ts:128-142` | `provides a guided corpus-to-search workflow with visible stage status` | Static markup assertions only: the section id, five input ids, all six `data-workflow-stage` values, two result container ids |
| `index.test.ts:94` | (tab list assertion) | `data-workbench-tab="workflows"` exists |
| `index.test.ts:109` | (bridge assertion) | `data-workbench-jump="workflows">build your own workspace index</button>` exists verbatim |
| `workbench-navigation.test.ts:84-92` | `opens workflows from the build-your-own-index action` | Clicking the jump selects the tab and unhides the panel |

That is **two behavioural tests** for a 571 line controller, plus markup assertions.

Adjacent files that cover code this tab depends on but not the tab itself:

- `search-adapter.test.ts` covers `readSearchIndexes`, `readSearchResponse`, provider reading.
- `search-heatmap.test.ts` covers `buildDocumentHeat`, used at `corpus-workflow.ts:331`.
- `search-view-model.test.ts` covers `matchedSegments` and `scoreColor`, used at
  `corpus-workflow.ts:360-366`.
- `document-shape.test.ts` covers `readDocumentShape` and `summarizeDocumentShape`.
- `text-utils.test.ts` covers `splitBlankLineDocuments`, which `workflowDocuments` wraps.
- `vocabulary-trainer.test.ts` covers `readTeachers`, `readLearnedVocabulary`, `readTrainedModel`,
  and notably `disables training when the server has no artifact root`, which is the exact test
  this tab lacks.
- `batch-analysis.test.ts:29-71` covers `splitBatchDocuments`, `buildStreamFrames`,
  `readStreamResponse`. **These belong to the Analyze tab, not this one**: the batch controls are
  `#batch-text` / `#batch-analyze-button` at `index.html:235-242`, wired at `src/main.ts:389-393`
  and `505-533`. No Workflows code imports `batch-analysis.ts`.
- `collection-adapter.test.ts` covers collection and drift reading. **Also not this tab**:
  collections are Lifecycle only (`index.html:1063`, `src/lifecycle-workbench.ts`). No Workflows
  code imports `collection-adapter.ts`.

### 1.2 End to end, `opennlp-grpc-webapp-default/e2e/`

| File | Test | What it covers |
| --- | --- | --- |
| `workbench.spec.ts:33-42` | `bridges configured search to workflows and workspace search` | The `build your own workspace index` jump lands on a visible `#workflows-workbench` |
| `workbench.spec.ts:44-54` | `offers automatic workflow defaults with optional resource choices` | `#workflow-status` contains `Ready`; the dictionary select's first option text; teacher and provider selects are non-empty; run button disabled then enabled after filling the corpus |
| `workbench.spec.ts:56-76` | `builds and searches a live corpus workflow` | The real six stage run against a live server. **Skipped unless `OPENNLP_E2E_WORKFLOW_WRITE=1`** (`workbench.spec.ts:58-59`), because it writes durable artifacts |
| `corpus-search.spec.ts:22-53` | `searches a corpus index and inspects the top hit` | Not this tab, but the file is the reference for the skip-on-empty-catalog pattern (`corpus-search.spec.ts:26-32`) |

### 1.3 Gateway tests, `opennlp-grpc-webapp/src/test/java/org/apache/opennlp/grpc/webapp/`

| File | Test | Endpoint this tab uses |
| --- | --- | --- |
| `GrpcJsonVocabularyApiTest.java:74` | `composesVocabularyLearningFromOneJsonUpload` | `POST /api/v1/learn-vocabulary` (stage 2) |
| `GrpcJsonVocabularyApiTest.java:106` | `listsFormatsTeachersAndModels` | `GET /api/v1/teachers` (the gate at `corpus-workflow.ts:153`) |
| `GrpcJsonVocabularyApiTest.java:136` | `streamsTrainingProgressLinesThenTheTerminalModel` | `POST /api/v1/train-static-model` (stage 3, the progress callback at `corpus-workflow.ts:209`) |
| `GrpcJsonVocabularyApiTest.java:168` | `returnsBufferedErrorWhenTrainingFailsBeforeStreaming` | stage 3 error path |
| `GrpcJsonVocabularyApiTest.java:183` | `appendsAnErrorLineWhenTrainingFailsMidStream` | stage 3 error path |
| `GrpcJsonSearchApiTest.java:96` | `listsSearchProviderInstancesAsProtobufJson` | `GET /api/v1/search-providers` (`corpus-workflow.ts:421`) |
| `GrpcJsonSearchApiTest.java:255` | `indexesAndDeletesAWorkspaceThroughProtobufJson` | `POST /api/v1/index-documents` (stage 5) |
| `GrpcJsonSearchApiTest.java:110` | `parsesExhaustiveDocumentSearchAndRendersDeduplicatedSource` | `POST /api/v1/search` with `allHits` (`corpus-workflow.ts:292`) |
| `GrpcAnalysisRpcTest.java` | (analysis) | `POST /api/v1/analyze` (stages 1 and 4) |

Gateway coverage of the endpoints this tab calls is good. The gap is entirely in the browser.

---

## 2. FACT: features on this tab with no test

Every item below was searched for across `test/*.test.ts`, `e2e/*.spec.ts`, and the Java test
sources. None has a test.

### 2.1 Gating and browned-out states (the biggest gap)

| Feature | Code | Note |
| --- | --- | --- |
| `#ready = false` when `writesEnabled` is false | `corpus-workflow.ts:153` | The trainer has the equivalent test (`vocabulary-trainer.test.ts`, `disables training when the server has no artifact root`); this tab does not |
| `#ready = false` when `teachers.length === 0` | `corpus-workflow.ts:153` | Untested |
| The exact string `Training is unavailable because this server has no writable artifact root or teacher model.` | `corpus-workflow.ts:156` | Untested, and this is the state the live demo instance is in |
| `#runButton` stays disabled forever when not ready | `corpus-workflow.ts:491` | Untested |
| `initialize()` rejecting, giving `Could not load workflow resources.` | `corpus-workflow.ts:158-161` | Untested. `listDictionaries` has a `.catch(() => [])` at line 146; `listTeachers` and `listProviders` do not, so either one rejecting kills all three selects. Untested |
| `No teacher configured` fallback option and disabled select | `corpus-workflow.ts:415-418` | Untested |
| `Exact flat float` synthetic fallback when the server offers no vector+live+standard provider | `corpus-workflow.ts:428-430` | Untested |
| Provider filter `vector && live && standard` | `corpus-workflow.ts:423-424` | Untested. The `terms` instance on the live server has `keyword`+`live` and must be excluded |
| `providerLabel` naming TurboQuant | `corpus-workflow.ts:568-571` | Untested |

### 2.2 Error paths inside a run

| Feature | Code |
| --- | --- |
| Stage marked `data-state="error"` on failure | `corpus-workflow.ts:231-234`, `459-461` |
| Status `The workflow did not complete.` fallback | `corpus-workflow.ts:235` |
| `Analysis returned no document text for <id>.` | `corpus-workflow.ts:276` |
| `Embedded analysis returned no indexable chunk groups.` | `corpus-workflow.ts:520` |
| `The workflow index is not available.` | `corpus-workflow.ts:288` |
| `searchAgain` failure path, `Search failed.` | `corpus-workflow.ts:256-258` |
| `Add at least one document and a first search query.` | `corpus-workflow.ts:172` |
| Re-entrancy guard `if (this.#busy \|\| !this.#ready) return;` | `corpus-workflow.ts:166` |

### 2.3 Rendering branches

| Feature | Code |
| --- | --- |
| `No source-mapped chunks matched this query.` when `buildDocumentHeat` returns nothing | `corpus-workflow.ts:333` |
| Clicking a heat chunk: `aria-pressed` toggling and `#workflow-search-selection` text | `corpus-workflow.ts:377-383` |
| `#workflow-corpus-stats` document count and UTF-8 byte count | `corpus-workflow.ts:487-490` |
| Clicking `[data-workflow-result-tab="analysis"]` to switch back | `corpus-workflow.ts:135-138`, `434-442`. The happy-path test only observes the automatic switch to `search` at line 229 |
| Layer chips and `layerAccent` per document card | `corpus-workflow.ts:316-322` |
| `renderArtifacts` early return when any of the three is missing | `corpus-workflow.ts:388-390` |

### 2.4 Input handling

| Feature | Code |
| --- | --- |
| `positiveInteger` falling back for `0`, negative, or non-numeric min frequency and max terms | `corpus-workflow.ts:525-528` |
| `nonNegativeInteger` for PCA dims | `corpus-workflow.ts:530-533` |
| `workflowDocuments` doc id generation `workflow-doc-N` | `corpus-workflow.ts:503-508`. Exported and public, but never directly asserted; the happy path only checks the count |
| Empty `#workflow-name` falling back to `Text workflow` | `corpus-workflow.ts:181` |
| `maxTopK` clamp `Math.min(index.maxTopK ?? 50, 50)` when `supportsAllHits` is false | `corpus-workflow.ts:293`. The test fixture sets `supportsAllHits: true` (`corpus-workflow.test.ts:54`), so the entire non-exhaustive branch is unexecuted |

### 2.5 Cross-tab consequences

| Feature | Code |
| --- | --- |
| `onIndexChanged` refreshing Corpus search and Workspace search | `src/main.ts:373-376`. The unit test passes `vi.fn()` and asserts nothing about it (`corpus-workflow.test.ts:219`) |
| `onModelTrained` publishing into the Analyze embedding picker | `src/main.ts:357-360`. Asserted only as "was called with MODEL" at `corpus-workflow.test.ts:240`, not that the picker gains the option |
| Lifecycle **not** being refreshed after a run | `src/main.ts:373-376`. No test asserts either the current behaviour or the desired one |
| `createAnalysisRequest` throwing `Analysis capabilities are still loading.` | `src/main.ts:343-344`. The unit test supplies its own stub, so the real closure is untested |
| The real `mode: "max"` request built for the workflow, with `sentenceChunks: Boolean(embeddingModelId)` | `src/main.ts:346-353`. Untested; the fixture at `corpus-workflow.test.ts:202-216` hand-builds a different request |

---

## 3. FACT: two problems with the tests that do exist

### 3.1 The main e2e assertion cannot pass on a default server

`e2e/workbench.spec.ts:46`:

```ts
await expect(page.locator("#workflow-status")).toContainText("Ready");
```

On a server with no teacher and no artifact root, `#workflow-status` reads `Training is
unavailable because this server has no writable artifact root or teacher model.`
(`corpus-workflow.ts:156`), which does not contain `Ready`. The test **fails**, it does not skip.

The live demo instance is in exactly that state (see `../reference/live-instance-state.md`), so
this spec is red on the instance a contributor is most likely to point `OPENNLP_E2E_BASE_URL` at.

`e2e/corpus-search.spec.ts:26-32` shows the intended pattern for this situation:

```ts
// Index discovery is asynchronous; an empty catalog is a skip, not a failure.
...
test.skip(!hasIndex, "The server reports no configured search index.");
```

Priority: **P1**. `workbench.spec.ts:44-54` should either skip when no teacher is configured, or
split into two tests: one asserting the ready state on a configured server, one asserting the
browned-out state on an unconfigured one.

### 3.2 The only real end to end run is opt-in and therefore never runs in practice

`workbench.spec.ts:56-76` is guarded by `process.env.OPENNLP_E2E_WORKFLOW_WRITE !== "1"` with a
twenty minute timeout. The guard is justified (the run writes durable artifacts) but the effect is
that the tab's headline feature has **no test that runs by default at any level**: the unit tests
mock every call, and the one real test is skipped.

---

## 4. OPINION: proposed tests, prioritised

### P1

1. `corpus-workflow.test.ts`: `disables the workflow when the server has no teacher` and
   `disables the workflow when the server has no writable artifact root`. Two tests, each
   asserting the exact status string, the `is-error` class, and `#workflow-run-button.disabled`.
   Copy the shape of `vocabulary-trainer.test.ts`'s equivalent, which already exists.
2. `corpus-workflow.test.ts`: `surfaces a failing stage without losing completed stages`. Make
   `index` reject, assert stage 5 has `data-state="error"`, stages 1 to 4 are `complete`, and the
   status carries the server message. This is the most likely real-world path and it is untested.
3. `e2e/workbench.spec.ts:44-54`: add the skip guard from `corpus-search.spec.ts:26-32` so the
   suite is green against an unconfigured server, and add a second test asserting the browned-out
   copy when no teacher is present.
4. `index.test.ts`: assert the tab has a `details.help-callout`, once one is added. Every other
   tab has one and this tab does not.

### P2

5. `corpus-workflow.test.ts`: `builds a new index on every run`. Run twice, assert
   `index` was called twice and neither call carried an `indexId`. This pins the behaviour
   described in `gating-and-empty-states.md` section 3 so a future change is deliberate.
6. `corpus-workflow.test.ts`: `clamps top-k when the index is not exhaustive`. Set
   `supportsAllHits: false` and `maxTopK: 500`, assert the search request carries `topK: 50`.
   Currently zero coverage of that branch.
7. `corpus-workflow.test.ts`: `keeps only vector, live, standard providers`. Feed the three
   instances the live server reports and assert `terms` is excluded and both others are offered.
8. `corpus-workflow.test.ts`: `reports no matching chunks`. Return hits that `buildDocumentHeat`
   cannot map and assert `No source-mapped chunks matched this query.`
9. `corpus-workflow.test.ts`: `selects a heat chunk`. Click a `.heat-chunk`, assert `aria-pressed`
   and the `#workflow-search-selection` text.
10. `corpus-workflow.test.ts`: `falls back to safe numbers for invalid limits`. Set min frequency
    to `0` and PCA dims to `-1`, assert the request carries `1` and `0`.

### P3

11. `corpus-workflow.test.ts`: `names workflow documents in paste order`, a direct assertion on
    the exported `workflowDocuments`.
12. `corpus-workflow.test.ts`: `switches back to the analysis view`, clicking the analysis result
    tab after a run.
13. `main.ts` integration or e2e: assert the Lifecycle picker contains the new index after a
    workflow run, which is the missing refresh described in
    `journey-and-vocabulary.md` section 2.5. This test would fail today, which is the point.

---

## Questions for the lead

1. Is `OPENNLP_E2E_WORKFLOW_WRITE` expected to be set in CI anywhere? If not, the six stage run has
   no automated verification at all, and the P1 unit tests above become the only safety net.
2. Should `workbench.spec.ts:44-54` skip or fail on an unconfigured server? Skipping matches
   `corpus-search.spec.ts`; failing would catch a regression in demo image packaging. A third
   option is a dedicated spec that asserts the demo image **is** configured.
3. Do you want the browned-out copy pinned by tests before or after the copy is rewritten? Pinning
   the current strings first makes the rewrite a visible diff; pinning after is less churn.
