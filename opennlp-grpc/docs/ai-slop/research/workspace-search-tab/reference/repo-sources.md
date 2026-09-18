# Repository files this audit relied on

Read on 2026-08-28 from the worktree
`/work/worktrees/opennlp/sandbox-grpc-query/opennlp-grpc`, branch tip `7938d722`.

## Front end, opennlp-grpc-webapp-default

| File | What was read |
|---|---|
| `index.html:48-49` | the `Workspace search` tab button, `data-workbench-tab="session-search"` |
| `index.html:95-115` | Analyze tab help callout, the prose reference to this tab |
| `index.html:136-165` | `#embedding-model-select` and its `No embedding model configured` fallback |
| `index.html:252` | `Add to server workspace`, the tab's real entry point |
| `index.html:544-600` | Corpus search intro, its tab bridge, its index picker |
| `index.html:722-788` | the complete `#session-search` panel |
| `index.html:957` | Trainer prose pointing at this tab |
| `index.html:965-1050` | Lifecycle intro, checkpoint and seal controls, their `.field-help` |
| `src/semantic-workbench.ts` | all 759 lines; the tab's controller |
| `src/search-adapter.ts:23-231` | `SearchIndex`, `SearchHit`, `readSearchIndexes`, `readSearchProviderInstances` |
| `src/server-search-workbench.ts:110-161` | the Corpus search index listing, which does not filter `immutable` |
| `src/lifecycle-workbench.ts:129, 180, 195-199, 545` | the persist and seal gating |
| `src/main.ts:260-322, 373-377, 425-440, 960-985` | wiring of `SemanticWorkbench`, `ServerSearchWorkbench`, `LifecycleWorkbench` |
| `src/workbench-navigation.ts:19-108` | the `data-workbench-jump` mechanism |
| `src/search-view-model.ts` | score colour scale, matched segments, result status |
| `src/search-heatmap.ts` | `buildDocumentHeat`, used by the Corpus search tab |
| `src/discovery.ts`, `src/document-window.ts` | internal helpers, never user-visible |
| `src/api.ts:160-210, 534-564` | endpoint wrappers and `responseError`, which surfaces raw server text |
| `src/style.css:476-477, 520-527, 668-686, 1188` | `.tab-bridge`, `.link-button`, `.help-callout`, `.empty-message`, `.visually-hidden` |
| `src/annotation-drawer.ts:38-60` | the hand-rolled accessible drawer, precedent for a help panel |
| `package.json` | Vite 8, vitest 4, TypeScript 7, one runtime dependency (echarts). No UI framework. |

## Tests

| File | What was read |
|---|---|
| `test/semantic-workbench.test.ts` | all four tests |
| `test/search-adapter.test.ts` | 21 test names, three about dynamic descriptors |
| `test/index.test.ts:105-180` | markup contract assertions on this tab |
| `test/workbench-navigation.test.ts` | jump mechanism tests |
| `e2e/workbench.spec.ts:20-45` | the one e2e test that touches this tab |
| `e2e/corpus-search.spec.ts:22` | the sibling tab's end-to-end test, for contrast |
| `opennlp-grpc-webapp/src/test/java/.../GrpcJsonSearchApiTest.java` | 10 test names |
| `opennlp-grpc-service/src/test/java/.../DynamicSearchIndexRegistryTest.java` | 14 test names |
| `opennlp-grpc-service/src/test/java/.../OpenNlpSearchServiceImplTest.java` | 30 test names |
| `opennlp-grpc-service/src/test/java/.../SearchWireContractTest.java` | 6 test names |

## API and service

| File | What was read |
|---|---|
| `opennlp-grpc-api/src/main/proto/org/apache/opennlp/grpc/v1/opennlp_search.proto:29-145` | service comments, the dynamic vs immutable framing, persist, seal, reindex, alias, collection RPCs |
| `... opennlp_search.proto:400-500` | `SearchProviderCapability`, `SearchIndexDescriptor` including `immutable` (field 8) and `persisted` (field 15) |
| `opennlp-grpc-service/.../search/DynamicSearchIndexRegistry.java` | bounds (65-72), disabled registry (172-175), sealed rejection (266-268), provider mismatch (271-274), index cap (277), persist and seal (460-545), `requireEnabled` (1085-1091), descriptor build (1352) |
| `opennlp-grpc-service/.../search/WorkspaceCheckpointStore.java:57-105` | `search.persist.root`, checkpoint vs sealed kinds |
| `opennlp-grpc-service/.../server/OpenNlpGrpcServer.java:169-170` | `search.dynamic.enabled` |
| `README.md:752` | the only prose use of "dynamic workspace indexes" outside the UI |

## Live instance, read-only calls on 2026-08-28

`http://127.0.0.1:7172/api/v1/` : `search-indexes`, `search-providers`, `service-info`,
`collections`, `index-aliases`, `installed-models`, `static-models`, `model-bundles`,
`model-catalog`, and one reproduction of a user-facing error via
`POST /api/v1/search` with an unknown `indexId`, which returned
`{"code":"NOT_FOUND","message":"Unknown dynamic search index 'does-not-exist'"}` with
HTTP 404.
