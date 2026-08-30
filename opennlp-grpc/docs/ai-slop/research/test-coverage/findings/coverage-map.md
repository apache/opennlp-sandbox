# Coverage map: the JSON gateway routes and the web front end

Scope: every HTTP route the browser can reach on the gateway, and which test at
which layer exercises it. All statements marked FACT are read from the code at
the cited `path:line`. Recommendations live in `recommendations.md`.

## 1. How many routes there are

FACT. The gateway serves **37** routes. Thirty-two are in the `switch` in
`opennlp-grpc-webapp/src/main/java/org/apache/opennlp/grpc/webapp/GrpcJsonApi.java:127-191`,
four are NDJSON streams handled before that switch in
`opennlp-grpc-webapp/src/main/java/org/apache/opennlp/grpc/webapp/OpenNlpGrpcWebServer.java:150-153`
(`train-static-model`, `install-model`, `analyze-stream`, `watch-collection`),
and `ui-extensions` is served by the UI catalog at
`opennlp-grpc-webapp/src/main/java/org/apache/opennlp/grpc/webapp/OpenNlpGrpcWebServer.java:268`.
`/healthz` (`OpenNlpGrpcWebServer.java:205`) is a 38th HTTP endpoint but is not
under `/api/v1`.

FACT. `opennlp-grpc-webapp-default/src/api.ts` calls **35** of those 37. The two
it never calls are `/api/v1/output-formats` and `/api/v1/format-document`
(`GrpcJsonApi.java:134` and `:136`), which exist for the output formatter SPI
added in `cc59a39a` and today have no browser caller.

## 2. Test layers, as they exist

| Layer | Where | How run |
| --- | --- | --- |
| FE unit (vitest) | `opennlp-grpc-webapp-default/test/*.test.ts` (32 files, 225 tests) | `npm test`, wired into `generate-resources` at `opennlp-grpc-webapp-default/pom.xml:101-110` |
| FE e2e (Playwright) | `opennlp-grpc-webapp-default/e2e/*.spec.ts` (3 files, 8 tests) | `npm run e2e`, **not** in any build or CI gate |
| Gateway (JUnit) | `opennlp-grpc-webapp/src/test/java/...` (11 files, 64 tests) | surefire |
| Integration (failsafe) | `opennlp-grpc-integration-tests/src/test/java/...` (6 files, 29 tests) | failsafe, `**/*IT.java` |
| Service (JUnit) | `opennlp-grpc-service/src/test/java/...` (136 files, 792 tests) | surefire |

FACT. `npm test` output on this tree: `Test Files 32 passed (32) / Tests 225 passed (225)`,
756 ms. The `test` script is `tsc --noEmit && vitest run`
(`opennlp-grpc-webapp-default/package.json:9`), so the TypeScript compile is a
real gate; the type check only checks the FE against its own hand-written types.

Note on directory naming: the brief expects FE unit tests under `src/test/`. On
this tree they are in `opennlp-grpc-webapp-default/test/`
(`vite.config.ts` include is `test/**/*.test.ts`). The only thing under
`opennlp-grpc-webapp-default/src/test/` is one Java file,
`DefaultWebUiExtensionTest.java`.

## 3. Route by route

Legend for the verdict column:
- **mock only**: the only FE test is a `vi.fn()` fetcher that echoes the request
  back, so nothing checks the payload against the proto.
- **no FE test**: the route's `api.ts` function is called only from `main.ts`,
  which has no unit test.
- **gateway stub**: the gateway test drives a hand-written `Stub*Rpc`, not the
  real service.

| Route | FE unit test | e2e | Gateway test | Integration | Service test | Verdict |
| --- | --- | --- | --- | --- | --- | --- |
| `analyze` | `api.test.ts:73`, `corpus-workflow.test.ts` | `analysis.spec.ts:36` | `GrpcJsonApiTest.parsesAnalyzeRequestAndRendersDocumentShape:69`, `OpenNlpGrpcWebServerTest:82` | `OpenNlpGrpcServerLiveIT.analyzesDocumentWithBundledModels` | `OpenNlpAnalysisServiceImplTest`, `DocumentShapeWireContractTest` | best covered route |
| `analyze-stream` | none | none | `OpenNlpGrpcWebServerTest.streamsBatchAnalysisAsNdjson:111` | `OpenNlpGrpcServerLiveIT.analysisStreamMatchesUnaryAndContinuesAfterADocumentError` | `AnalyzeStreamTest`, `AnalyzeStreamWireContractTest` | no FE test of `analyzeStream()` frame assembly (`api.ts:298`) |
| `service-info` | `api.test.ts` | indirect | `GrpcJsonApiTest.rendersServiceInfoAsProtobufJson:39`, `OpenNlpGrpcWebServerTest:301` | `OpenNlpGrpcServerLiveIT.serviceInfoReportsEmbeddingSupport`; `docker/test-image.sh:97` | `ServiceCapabilityWireContractTest` | good |
| `model-bundles` | `api.test.ts` | none | **none over HTTP** (only `GrpcAnalysisRpcTest.delegatesAllUnaryGatewayCalls`) | none | `OpenNlpAnalysisServiceImplTest` | gateway route itself untested |
| `ui-extensions` | `api.test.ts`, `ui-extensions.test.ts` | none | `OpenNlpGrpcWebServerTest:77`, `WebUiCatalogJsonTest` | none | `WebUiExtensionRegistryTest`, `DefaultWebUiExtensionTest` | good |
| `output-formats` | **no FE caller** | none | `GrpcJsonApiTest.servesOutputFormatsAndFormatDocumentRoutes:51` | none | formats add-on tests | dead route from the browser's view |
| `format-document` | **no FE caller** | none | `GrpcJsonApiTest:57` | none | formats add-on tests | dead route from the browser's view |
| `response/encode` | `api.test.ts` | none | `GrpcJsonApiTest:84`, `OpenNlpGrpcWebServerTest:137` | none | n/a (gateway only) | good |
| `response/decode` | `api.test.ts` | none | `GrpcJsonApiTest:101`, `OpenNlpGrpcWebServerTest:149` | none | n/a | good |
| `search` | `api.test.ts`, `search-adapter.test.ts` | `corpus-search.spec.ts:35` (skips if no index) | `GrpcJsonSearchApiTest:119`, `:139`, `OpenNlpGrpcWebServerTest:216` | `OpenNlpGrpcServerLiveIT.servesSearchWorkbenchAndJsonSearchThroughShadedWebapp:244` | `OpenNlpSearchServiceImplTest`, `QueryWireContractTest` | good, but the FE query builder and the gateway test JSON are two hand-written shapes |
| `search-indexes` | `api.test.ts`, `search-adapter.test.ts` | `corpus-search.spec.ts:25` | `GrpcJsonSearchApiTest:87`, `OpenNlpGrpcWebServerTest:212` | `OpenNlpGrpcServerLiveIT:237` | `SearchIndexRegistryTest` | good |
| `search-providers` | **no FE test** | none | `GrpcJsonSearchApiTest:99` | none | `ProviderWireContractTest`, `SearchProviderCatalogTest` | FE parser `readSearchProviderInstances` (`search-adapter.ts:148`) untested |
| `index-documents` | `api.test.ts` (mock only) | `workbench.spec.ts:56` behind `OPENNLP_E2E_WORKFLOW_WRITE=1` | `GrpcJsonSearchApiTest.indexesAndDeletesAWorkspaceThroughProtobufJson:262` (gateway stub) | none | `DynamicSearchIndexRegistryTest` | **mock only** at FE; see drift note 1 |
| `delete-search-index` | `api.test.ts` (mock only) | none | `GrpcJsonSearchApiTest:263` | none | `DynamicSearchIndexRegistryTest` | mock only |
| `persist-index` | **no FE test** | none | `GrpcJsonSearchApiTest:154` | none | `DynamicSearchIndexRegistryTest`, `LifecycleWireContractTest` | no FE test |
| `seal-index` | **no FE test** | none | `GrpcJsonSearchApiTest:158` | none | same | no FE test |
| `reindex-index` | **no FE test** | none | `GrpcJsonSearchApiTest:162` | none | same | no FE test |
| `set-index-alias` | **no FE test** | none | `GrpcJsonSearchApiTest:169` | none | `IndexAliasRegistryTest` | no FE test |
| `delete-index-alias` | **no FE test** | none | `GrpcJsonSearchApiTest:179` | none | `IndexAliasRegistryTest` | no FE test |
| `index-aliases` | **no FE test** | none | `GrpcJsonSearchApiTest:175` | none | `IndexAliasRegistryTest` | no FE test |
| `set-collection` | **no FE test** (`collection-adapter.test.ts` covers the response parse only) | none | `GrpcJsonSearchApiTest:192` | none | `SearchCollectionRegistryTest`, `CollectionWireContractTest` | request side untested at FE |
| `get-collection` | **no FE test** | none | `GrpcJsonSearchApiTest:198` | none | same | no FE test |
| `collections` | **no FE test** | none | `GrpcJsonSearchApiTest:204` | none | same | no FE test |
| `delete-collection` | **no FE test** | none | `GrpcJsonSearchApiTest:208` | none | same | no FE test |
| `watch-collection` | **no FE test** | none | `GrpcJsonSearchApiTest.streamsCollectionWatchEventsAsNdjsonLinesUntilTheDeadline:225` | none | `CollectionWireContractTest` | the FE NDJSON reader `watchCollection()` (`api.ts:270`) has no test at all |
| `dictionaries` | **no FE test** | none | `GrpcJsonVocabularyApiTest:111` | none | `OpenNlpVocabularyServiceImplTest` | no FE test |
| `dictionary-formats` | `api.test.ts` | none | `GrpcJsonVocabularyApiTest:109` | `OpenNlpGrpcServerLiveIT.discoversServiceLoadedDictionaryFormatsThroughShadedServer` | `DictionaryFormatRegistryTest` | good |
| `import-dictionary` | `api.test.ts`, `vocabulary-trainer.test.ts` | none | `GrpcJsonVocabularyApiTest.composesDictionaryImportFromOneJsonUpload:64` | none | `OpenNlpVocabularyServiceImplTest`, `VocabularyWireContractTest` | mock only at FE |
| `learn-vocabulary` | `api.test.ts`, `vocabulary-trainer.test.ts`, `corpus-workflow.test.ts` | `workbench.spec.ts:56` (opt in) | `GrpcJsonVocabularyApiTest:83` | none | `VocabularyArtifactStoreTest` | mock only at FE |
| `download-vocabulary` | `api.test.ts` | `workbench.spec.ts:85` (disabled-state only) | `GrpcJsonVocabularyApiTest:98` | none | `VocabularyArtifactStoreTest` | good |
| `teachers` | `api.test.ts` | `workbench.spec.ts:49` (option count) | `GrpcJsonVocabularyApiTest:113` | none | `OpenNlpModelTrainingServiceImplTest` | good |
| `static-models` | `api.test.ts`, `vocabulary-trainer.test.ts` | none | `GrpcJsonVocabularyApiTest:115` | none | `StaticModelArtifactStoreTest` | good |
| `model-catalog` | `api.test.ts`, `model-data-workbench.test.ts` | none | `GrpcJsonVocabularyApiTest:117` | `OpenNlpGrpcServerLiveIT.modelCatalogReportsBackendIds`, `OpenNlpGrpcServerNerLiveIT` | `ModelCatalogWireContractTest`, `StandardModelCatalogTest` | good |
| `installed-models` | `api.test.ts`, `model-data-workbench.test.ts` | none | `GrpcJsonVocabularyApiTest:119` | none | `CatalogModelStoreTest` | good |
| `install-model` | `api.test.ts` (mock only) | none | `GrpcJsonVocabularyApiTest.streamsModelInstallationProgressThenTheInstalledModel:159`, `OpenNlpGrpcWebServerTest:190` | none | `CatalogModelBootstrapTest` | mock only at FE |
| `delete-static-model` | `api.test.ts`, `vocabulary-trainer.test.ts` | none | `GrpcJsonVocabularyApiTest:129` | none | `StaticModelArtifactStoreTest` | good |
| `train-static-model` | `api.test.ts`, `vocabulary-trainer.test.ts`, `corpus-workflow.test.ts` | `workbench.spec.ts:56` (opt in) | `GrpcJsonVocabularyApiTest:141`, `:175`, `:189`, `OpenNlpGrpcWebServerTest:175` | none | `StreamingTrainingSessionTest` | good |
| `/healthz` | `api.test.ts` | none | `OpenNlpGrpcWebServerTest:68` | `LiveWebAppHarness:117` | n/a | good |

### Routes with zero test at any layer

None. Every route has at least a gateway test except `/api/v1/model-bundles`,
which is exercised only through the RPC adapter
(`GrpcAnalysisRpcTest.delegatesAllUnaryGatewayCalls`) and never over the HTTP
route table. That is the single most exposed route: a typo in the `case` string
at `GrpcJsonApi.java:130` would ship.

### Routes whose only FE test is a mock that can drift

FACT. `opennlp-grpc-webapp-default/test/api.test.ts:47-62` builds a
`vi.fn()` fetcher that returns `JSON.stringify({ url })` and then asserts the
request body equals `JSON.stringify(...)` of the same literal the test just
wrote. It proves the URL and the serializer, and nothing about the payload. The
routes whose only FE coverage is that assertion are:
`index-documents`, `delete-search-index`, `install-model`, `import-dictionary`,
`learn-vocabulary`, `search` (request side), `analyze` (request side),
`response/encode`, `response/decode`, `download-vocabulary`, `model-bundles`,
`ui-extensions`, `service-info`, `dictionary-formats`, `teachers`,
`static-models`, `model-catalog`, `installed-models`, `delete-static-model`,
`search-indexes`.

### Routes with no FE unit test at all

`analyze-stream`, `search-providers`, `persist-index`, `seal-index`,
`reindex-index`, `set-index-alias`, `delete-index-alias`, `index-aliases`,
`set-collection`, `get-collection`, `collections`, `delete-collection`,
`watch-collection`, `dictionaries`. Fourteen routes. FACT: each of their
`api.ts` functions is imported only by `main.ts`
(48 KB, `opennlp-grpc-webapp-default/src/main.ts`), which has no unit test file.
The only test that reads `main.ts`-adjacent behaviour is `index.test.ts`, which
does string matching over `index.html` and never imports `main.ts`.

## 4. Per-tab e2e coverage

FACT. `index.html` declares seven tabs
(`grep -oE 'data-workbench-tab="[a-z-]+"'`): `analysis`, `corpus-search`,
`session-search`, `workflows`, `models`, `trainer`, `lifecycle`.

| Tab | e2e coverage |
| --- | --- |
| `analysis` | `analysis.spec.ts` (3 tests: two sample loaders, one full analyze plus overlay), `workbench.spec.ts:26` |
| `corpus-search` | `corpus-search.spec.ts` (1 test, self-skips when the catalog is empty), `workbench.spec.ts:33,78` |
| `workflows` | `workbench.spec.ts:44` (defaults), `:56` (live run, skipped unless `OPENNLP_E2E_WORKFLOW_WRITE=1`) |
| `session-search` | one click in `workbench.spec.ts:39` |
| `trainer` | one assertion, `workbench.spec.ts:85`, that the TSV button is disabled |
| `models` | **none** |
| `lifecycle` | **none** |

Models and data is the tab that drives `model-catalog`, `installed-models` and
`install-model`, that is, the exact surface where the catalog id bug in
`7938d722` landed. Lifecycle drives `persist-index`, `seal-index`,
`reindex-index`, the alias routes and all four collection routes, which is the
largest block of routes with no FE test of any kind.

## 5. Cross-layer summary

- 37 gateway routes; 35 reachable from the browser.
- 36 of 37 have a gateway JUnit test that goes through `GrpcJsonApi.handle` or
  the HTTP server; `model-bundles` does not.
- 2 of 37 are touched by an integration test that goes through a real server
  (`search-indexes`, `search`), plus `/healthz` in the harness and `analyze` and
  `service-info` in `docker/test-image.sh`.
- 21 of 35 have an FE unit test; of those, 20 are the echo mock in `api.test.ts`.
- 5 of 35 are touched by an e2e spec, and none of those e2e specs run in CI.
