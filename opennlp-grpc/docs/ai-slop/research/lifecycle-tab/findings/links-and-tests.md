# Lifecycle tab: cross-tab links, model and backend gating, and test coverage

---

## Part 1. Cross-tab links

FACT. The whole application contains exactly **three** `data-workbench-jump` buttons
(`opennlp-grpc-webapp-default/index.html:555, 592, 735`), wired by
`opennlp-grpc-webapp-default/src/workbench-navigation.ts:40`. Tab ids are declared at
`index.html:42-55`: `analysis`, `workflows`, `corpus-search`, `session-search`, `models`,
`trainer`, `lifecycle`.

**None of the three is on the Lifecycle tab, and the Lifecycle tab contains zero jump links.**
Every dependency it has on another tab is expressed as prose, or as nothing at all.

| # | Situation on the Lifecycle tab | Where the user must go | Link today? | Proposed |
| --- | --- | --- | --- | --- |
| 1 | No dynamic workspaces exist. Every control on the left panel is dead. | **Workflows** (guided build) or **Workspace search** (add analyzed documents) | **No.** Prose only: "Index documents in Workspace search to create a dynamic workspace first." (`src/lifecycle-workbench.ts:136`) | P1. Two buttons in the empty state: `data-workbench-jump="workflows"` and `data-workbench-jump="session-search"`. |
| 2 | The selected workspace uses exact flat float, so persist and seal can never succeed. | **Workspace search** (to pick TurboQuant for the next index) | **No.** Nothing anywhere says flat float cannot be persisted. | P1. See seal-error-and-empty-states.md section 3. |
| 3 | Rebuild is blocked because no trained model exists. | **Trainer** | **No**, but the text names the destination: "No trained model yet: distill one on the Trainer tab" (`src/lifecycle-workbench.ts:238`) | P2. Make it a `data-workbench-jump="trainer"` button. Best-written gate on the tab; it just is not clickable. |
| 4 | The collection editor needs a **Vocabulary artifact id**, typed by hand into a free-text box (`index.html:1084-1086`). | **Trainer**, which produces vocabulary artifacts | **No.** Placeholder `vocabulary-…` is the only hint. | P1. There is no list-vocabularies endpoint (confirmed: `GrpcJsonApi.java:172-180` offers `dictionary-formats`, `dictionaries`, `import-dictionary`, `learn-vocabulary`, `download-vocabulary`, and no listing), so a picker needs a new RPC. Until then, add a jump to Trainer plus text explaining where the id comes from. |
| 5 | The collection editor needs a **Dictionary artifact id**, also free text (`index.html:1080-1082`). | **Models & data**, which imports dictionaries | **No.** | P2, and this one is easy: `/api/v1/dictionaries` already exists and is already consumed elsewhere in this app (`src/main.ts:325`, `listDictionaries`). Replace the text box with a select. |
| 6 | A workspace has just been sealed and the user wants to search it. | **Workspace search**, or **Corpus search** if it were a startup bundle | **No.** The workspace also vanishes from the tab on refresh (`src/lifecycle-workbench.ts:129`). | P1. Confirmation should read "Sealed. It is read-only now and still searchable in Workspace search." with a jump. |
| 7 | An alias has been pointed at an index and the user wants to use it. | **Workspace search** / **Corpus search**, where the alias is accepted as an index id | **No.** Nothing says an alias is usable as an index id at query time. | P2. One line under the alias list: "Use this name anywhere an index id is accepted." |
| 8 | Rebuild finished and produced a new index id. | **Workspace search** to search the new index | **No.** The message names the id (`src/lifecycle-workbench.ts:310-312`) but does not link. | P2. |
| 9 | The coverage meter shows 0% because no vocabulary artifact is set. | **Trainer** | **No.** (`src/lifecycle-workbench.ts:497`) | P1. See vocabulary-drift.md section 3. |

FACT. Traffic flows *into* the Lifecycle tab from nowhere either. Neither Workflows nor Workspace
search, the two tabs that create workspaces, offers a jump to Lifecycle to save one. The tab is
reachable only by clicking its own tab button.

OPINION (P2). At minimum, after Workflows completes an index build, offer "Save this workspace so
it survives a restart" with `data-workbench-jump="lifecycle"`.

---

## Part 2. Model and backend gating

FACT. Every row records what the user sees today, quoted exactly.

| Feature | Requires | Failure mode today | Exact text today | Proposed browned-out state |
| --- | --- | --- | --- | --- |
| Save checkpoint (`#lifecycle-persist-button`) | a provider instance declaring `SEARCH_PROVIDER_CAPABILITY_PERSISTENT`, i.e. the `opennlp-grpc-search-turboquant` add-on **and** the index created with it | **Noisy, after the click.** Button enabled, request sent, HTTP 412. | `Search provider instance 'flat_float' is not persistent` (`DynamicSearchIndexRegistry.java:524-527`) | Disable, with: "This workspace keeps its vectors in memory only. Checkpoints need TurboQuant storage. Rebuild it with TurboQuant below." |
| Save checkpoint / Seal | `search.persist.root` set on the server | **Noisy, after the click.** HTTP 412. | `Index persistence is not configured; set search.persist.root` (`DynamicSearchIndexRegistry.java:519-523`) | Disable both buttons on load, with: "This server has no checkpoint directory, so nothing can be saved to disk. An operator needs to set `search.persist.root`." |
| Seal as read-only (`#lifecycle-seal-button`) | same as above | same as above | same as above | same, plus "Already sealed" once sealed workspaces are visible |
| Rebuild index (`#lifecycle-reindex-button`) | at least one published static model | **Quietly gated, well.** Model select is disabled with a message. | `No trained model yet: distill one on the Trainer tab` (`src/lifecycle-workbench.ts:238`) | Keep the text, add the jump. |
| Rebuild index | an embedding backend able to serve the chosen model | **Noisy.** Replay throws mid-build. | Raw `IllegalStateException` text, or `Status.INTERNAL` "Internal server error" (`OpenNlpSearchServiceImpl.java:992-997`) | "The embedding backend could not serve '<model>'. Check it is installed on Models & data." |
| Rebuild provider select | providers declaring both `vector` and `live` | Silent. TurboQuant simply is not listed when the add-on is absent. | none | P3. When only one option exists, say why: "Only exact flat float is available on this server; the TurboQuant add-on is not installed." |
| Point alias at workspace | target index must exist | **Noisy.** HTTP 404. | `SetIndexAlias index_id names unknown index '<id>'` (`OpenNlpSearchServiceImpl.java:432-435`) | Unreachable from the UI, since the picker only offers live indexes. Low priority. |
| Point alias at workspace | alias must not collide with a real index id | **Noisy.** HTTP 400. | `SetIndexAlias alias '<alias>' collides with an existing index id` (`OpenNlpSearchServiceImpl.java`) | Validate in the browser before sending: "That name is already an index id. Pick another." |
| Aliases | at most 256 (`IndexAliasRegistry.MAX_ALIASES`) | **Noisy**, `INVALID_ARGUMENT`. | wrapped `SetIndexAlias <message>` | P3. |
| Save collection | vocabulary artifact must exist when set | **Noisy.** HTTP 404. | `SetCollection <UnknownVocabularyArtifactException message>` | P1, and this one bites: the field is free text with no picker, so typos are the normal case. Validate and say: "No vocabulary artifact with that id. Learn one on the Trainer tab." |
| Save collection | members must be dynamic, not startup bundles | **Noisy.** HTTP 412. | `SetCollection member '<id>' is a startup bundle; members must be dynamic indexes` | Unreachable from the UI, since the member list is built from `#indexes` which excludes bundles. Low priority. |
| Collection drift recompute | distinct terms under `search.collection.max_distinct_terms` | **Noisy.** HTTP 429. | `Collection drift distinct terms exceed configured maximum <n>` | P3. "This collection has too many distinct terms to measure. An operator can raise `search.collection.max_distinct_terms`." |
| Watch stream | none | **Silently bounded.** The gateway's `--request-timeout-seconds` deadline ends the stream (default 30s); the client reconnects (`src/lifecycle-workbench.ts:431-456`, `GrpcJsonApi.java:505-520`) | Watch status flips between "Watching '<id>'." and "Watching '<id>'. Snapshot received." | Working as designed. P3: the reconnect is invisible, which is correct. |
| Whole tab | dynamic indexing enabled by the operator | **Noisy.** HTTP 501. | `Dynamic search indexing is disabled by the server operator` (`DynamicSearchIndexRegistry.java:1086-1091`) | P2. Detect at load and brown out the entire left panel rather than failing per click. |

FACT, worth stating plainly for the lead: **the search RPCs are never gated on an add-on being
present.** `SearchRpc` is a hard dependency of the gateway (`GrpcJsonApi.java:85-98`) and two
provider factories (`FlatFloatSearchIndexProviderFactory`, `TermsSearchIndexProviderFactory`) ship
inside `opennlp-grpc-service` itself. So the Lifecycle endpoints always answer; they just answer
with a precondition failure. There is no "add-on missing" status distinguishable from any other
`INVALID_ARGUMENT`.

---

## Part 3. Tests

### 3.1 What is covered

FACT. The **service layer is well covered**. Every lifecycle RPC has both a wire-contract test and a
behavioural test.

| Feature | Test |
| --- | --- |
| Persist/Seal wire shape | `opennlp-grpc-service/src/test/java/org/apache/opennlp/grpc/search/LifecycleWireContractTest.java:41` `persistAndSealAreExplicitUnaryCalls` |
| Persist + seal happy path | `opennlp-grpc-search-turboquant/src/test/java/org/apache/opennlp/grpc/search/OpenNlpSearchServiceTurboQuantTest.java:102` `persistsAndSealsAWorkspaceThroughGrpcMethods` |
| **Persist fails on a non-persistent provider** | `opennlp-grpc-service/src/test/java/org/apache/opennlp/grpc/search/OpenNlpSearchServiceImplTest.java:166` `persistingAFlatWorkspaceReportsTheMissingCapability`; registry-level at `DynamicSearchIndexRegistryTest.java:253` `persistRequiresAPersistentProviderInstance` |
| **Persist fails with no `search.persist.root`** | `opennlp-grpc-service/src/test/java/org/apache/opennlp/grpc/search/DynamicSearchIndexRegistryTest.java:267` `persistWithoutAConfiguredRootReportsFailedPrecondition` |
| Checkpoint survives restart | `opennlp-grpc-search-turboquant/src/test/java/org/apache/opennlp/grpc/search/DynamicSearchIndexRegistryTurboQuantTest.java:199` `persistsAndRestoresATurboQuantWorkspaceAcrossRegistries` |
| **Sealed index rejects writes, stays sealed across restart** | `DynamicSearchIndexRegistryTurboQuantTest.java:230` `sealedIndexesRejectMutationAndRestoreImmutable` |
| Reindex blue/green + alias swap | `OpenNlpSearchServiceImplTest.java:267` `reindexesAWorkspaceIntoANewVectorSpaceAndSwapsTheAlias` |
| Reindex validation failures | `OpenNlpSearchServiceImplTest.java:317` `reindexValidatesItsSelectorAndSource` |
| Alias resolve, upsert, list, delete | `OpenNlpSearchServiceImplTest.java:192` `aliasesResolveOnSearchAndSupportUpsertListAndDelete`; registry at `IndexAliasRegistryTest.java:36,47,62,76` |
| **Alias collision and missing target** | `OpenNlpSearchServiceImplTest.java:237` `rejectsAliasCollisionsAndUnknownAliasTargets` |
| Delete index | `OpenNlpSearchServiceImplTest.java:133` `indexesSearchesAndDeletesAWorkspaceThroughGrpcMethods` |
| Collection CRUD, alias member resolution, error codes | `OpenNlpSearchServiceImplTest.java:794` `collectionsResolveMemberAliasesAndAnswerCrudCalls` |
| Collection rejects bundle members | `OpenNlpSearchServiceImplTest.java:873` `collectionsRejectStartupBundleMembers` |
| Watch: snapshot first, then events, delete completes the stream | `OpenNlpSearchServiceTurboQuantTest.java:143` `watchStreamsASnapshotFirstAndLifecycleEventsAfterwards` |
| **Drift arithmetic** | `SearchCollectionRegistryTest.java:54` `recomputesTheTermStatisticsFromLiveMemberContents` (no vocabulary: coverage 0.0); `:91` `countsMultiwordVocabularyTermsAsOneUnit` (coverage 0.5) |
| Drift threshold fires once per crossing | `SearchCollectionRegistryTest.java:263` `driftThresholdCrossingEmitsExactlyOncePerCrossing` |
| Drift distinct-term bound | `SearchCollectionRegistryTest.java:376` `driftRejectsMoreDistinctTermsThanItsConfiguredBound` |
| Collection persistence and tamper detection | `SearchCollectionRegistryTurboQuantTest.java:58` `persistsCollectionsBesideIndexCheckpointsAndRestoresThem` |
| Gateway: whole index lifecycle over JSON | `opennlp-grpc-webapp/src/test/java/org/apache/opennlp/grpc/webapp/GrpcJsonSearchApiTest.java:150` `drivesTheIndexLifecycleThroughProtobufJson` |
| Gateway: collection CRUD and NDJSON watch | `GrpcJsonSearchApiTest.java:189` `drivesTheCollectionLifecycleThroughProtobufJson`; `:218` `streamsCollectionWatchEventsAsNdjsonLinesUntilTheDeadline` |
| Browser: collection JSON decoding | `opennlp-grpc-webapp-default/test/collection-adapter.test.ts:57,84,89,96` |

### 3.2 What has NO test

FACT. The gap is almost entirely in the **browser layer**, which is exactly where the owner's
complaints live.

1. **`opennlp-grpc-webapp-default/src/lifecycle-workbench.ts` has no unit test at all.** There is no
   `lifecycle-workbench.test.ts` anywhere in the repo. That is 568 lines of gating, empty-state, and
   error-rendering logic, untested. Compare: `semantic-workbench.ts`,
   `server-search-workbench.ts`, `model-data-workbench.ts`, `corpus-workflow.ts`, and
   `vocabulary-trainer.ts` all have `.test.ts` files. Lifecycle is the only workbench without one.
   Specifically untested functions: `refresh`, `renderIndexOptions`, `renderIndexFacts`,
   `renderProviders`, `renderAliases`, `renderModels`, `persistSelected`, `setAlias`,
   `reindexSelected`, `openSelectedCollection`, `saveCollection`, `deleteCollection`, `startWatch`,
   `stopWatch`, `logEvent`, `renderCollection`, `updateControls`.
2. **Eleven `src/api.ts` client functions are untested**: `persistIndex` (`api.ts:211`),
   `sealIndex` (:215), `reindexIndex` (:219), `setIndexAlias` (:226), `deleteIndexAlias` (:234),
   `getIndexAliases` (:238), `setCollection` (:242), `getCollection` (:249), `getCollections` (:253),
   `deleteCollection` (:257), `watchCollection` (:270). Only `deleteSearchIndex`'s URL is asserted
   (`test/api.test.ts:48`).
3. **No e2e coverage.** Grepping `e2e/analysis.spec.ts`, `e2e/corpus-search.spec.ts`, and
   `e2e/workbench.spec.ts` for persist, seal, reindex, alias, collection, lifecycle, or drift
   returns nothing. `test/workbench-navigation.test.ts:55,73` only asserts that the
   `#lifecycle-workbench` panel toggles `hidden`.
4. **Seal on an already-sealed index** is untested at every layer. The code is idempotent
   (`DynamicSearchIndexRegistry.seal`), but nothing locks that in.
5. **Deleting a sealed dynamic workspace** is untested, and the behaviour is surprising: it succeeds,
   and takes the on-disk checkpoint with it (see the appendix of seal-error-and-empty-states.md).
6. **`GrpcHttpStatusMapper`'s 412 for `FAILED_PRECONDITION`** is the status every lifecycle
   precondition returns, and it is what the user actually experiences, but no test asserts that a
   lifecycle precondition failure surfaces as 412 with the message intact through
   `/api/v1/seal-index`.

### 3.3 Suggested tests, in priority order

OPINION.

- **P1** `test/lifecycle-workbench.test.ts`: with an empty `listIndexes`, assert the select shows the
  empty state, and that `#lifecycle-persist-button` and `#lifecycle-seal-button` are disabled. This
  pins the owner's whole complaint.
- **P1** Same file: with a `flat_float` index and providers lacking `persistent`, assert both buttons
  are disabled and the explanatory text is shown, rather than the request being sent. This test
  fails today and should.
- **P1** Gateway: assert `/api/v1/seal-index` on a non-persistent provider returns 412 with
  `{"code":"FAILED_PRECONDITION","message":"Search provider instance 'flat_float' is not persistent"}`.
  This is the exact payload the browser renders.
- **P2** `test/lifecycle-workbench.test.ts`: after a successful seal, assert the sealed workspace is
  still visible (once the fix in seal-error-and-empty-states.md section 5 lands).
- **P2** Service: seal an already-sealed index; assert it succeeds idempotently.
- **P2** Service: delete a sealed dynamic workspace; assert whichever behaviour the lead decides is
  correct.
- **P2** `test/api.test.ts`: extend the URL-routing test to the eleven lifecycle functions.
- **P3** e2e: one spec that opens the Lifecycle tab with no workspaces and asserts the empty state
  plus a working jump to Workflows.

---

## Questions for the lead

1. Should browning-out be a shared helper? Six controls across this tab need the same
   "requirement missing, here is where to get it" pattern, and other tabs likely need it too.
2. `lifecycle-workbench.ts` is the only workbench module without a unit test. Is that a deliberate
   scope call, or an oversight worth a blocking issue?
3. A vocabulary-artifact picker needs a list RPC that does not exist. Worth adding
   `ListVocabularies`, or is pasting an id from the Trainer tab acceptable for now?
