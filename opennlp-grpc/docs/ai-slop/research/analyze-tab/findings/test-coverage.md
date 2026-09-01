# Analyze tab: test coverage map

Roots:

- unit: `opennlp-grpc-webapp-default/test/*.test.ts` (vitest). Note the tests
  live in `test/`, not `src/test/`.
- e2e: `opennlp-grpc-webapp-default/e2e/*.spec.ts` (Playwright).
- gateway: `opennlp-grpc-webapp/src/test/java/org/apache/opennlp/grpc/webapp/`.
- service: `opennlp-grpc-service/src/test/java/org/apache/opennlp/grpc/`.

---

## 1. Feature to test map

Every row names a user-facing feature of the tab, the element or function that
implements it, and the tests that touch it.

### 1.1 Composer

| Feature | Implementation | Unit | E2E | Gateway / service |
| --- | --- | --- | --- | --- |
| Capability discovery from `service-info` + `model-bundles` | `analysis-config.ts:136` | `analysis-config.test.ts` `builds a richest-safe feature set from configured bundles and resources`, `derives configured language pipelines from pipeline bundles` | none | `GrpcJsonApiTest.rendersServiceInfoAsProtobufJson`; `GrpcAnalysisRpcTest.delegatesAllUnaryGatewayCalls`; `OpenNlpAnalysisServiceImplTest.serviceInfoAdvertisesConfiguredNonModelResources`, `.serviceInfoAdvertisesEveryStandardDocumentLayer` |
| `Feature preset` = `All available features` | `analysis-controls.ts:150`, `analysis-config.ts:223` | `analysis-config.test.ts` `turns every safe configured feature on and requests both chunk views`; `analysis-controls.test.ts` `starts with every ready feature selectable when named profiles are advertised`, `starts with all available features when only a basic profile is advertised` | indirectly, `analysis.spec.ts` `analyzes text and opens on the calm Highlights overlay` | `BasicDocumentAnalyzerPolicyTest.implementsEveryDefinedPipelineStep` |
| `Feature preset` = `Choose features` + the checklist | `analysis-controls.ts:260-300` | `analysis-config.test.ts` `expands custom feature dependencies in canonical pipeline order`, `adds chunking backbone steps to a custom profile`, `does not attach token configuration to a token-free custom profile` | **none** | not applicable |
| `Feature preset` = `Server automatic` | `analysis-controls.ts:143` | none directly | none | covered by any profile-free analyze test |
| `Feature preset` = a named profile | `analysis-controls.ts:198` | `analysis-config.test.ts` `supports named profiles and either chunk strategy independently` | none | `LanguagePipelineRoutingTest` family |
| `Embedding model` select | `analysis-controls.ts:202` | `analysis-controls.test.ts` `keeps runtime-trained embedding models when discovery configures afterwards`, `selects an offered embedding model programmatically and rejects unknown ids` | none | `BasicDocumentAnalyzerEmbeddingTest` (10 tests), `EmbeddingModelCatalogTest` |
| `Language pipeline` select | `analysis-config.ts:191` | `analysis-config.test.ts` `derives configured language pipelines from pipeline bundles`, `carries the pipeline language, tag set, and ranked-language request` | **none** | `LanguagePipelineRoutingTest.routesByDetectedLanguageToTheConfiguredPipeline` and 5 others |
| `Part-of-speech tag set` select | `analysis-config.ts:252` | `analysis-config.test.ts` `carries the pipeline language, tag set, and ranked-language request` | **none** | `BasicDocumentAnalyzerPosTagConversionTest.posTagFormatConversionChangesReportedTagsOnly`; `BasicDocumentAnalyzerPolicyTest.acceptsNativePosTagFormatWithoutConversion`, `.rejectsCustomPosTagFormat` |
| `Sentence chunks` / `Token windows` and their size and overlap | `analysis-config.ts:381-417` | `analysis-config.test.ts` `turns every safe configured feature on and requests both chunk views`, `rejects invalid token windows before sending a request` | **none** | `BasicDocumentAnalyzerChunkEmbedTest.tokenChunkingAutoRunsTokenizationBackbone`, `.overlappingTokenChunksCountEachTokenOnceInGroupTotal` |
| `Normalization X-ray` toggle | `analysis-config.ts:93`, `main.ts:1003-1009` | `analysis-config.test.ts` `keeps the profile's whitespace variant instead of requesting both` and 3 siblings | **none** | `ParityStepsTest.normalizeProducesAlignedTextAndRuns` and 5 siblings; `AlignmentRunsTest` (4) |
| `Use short sample` | `main.ts:381-385` | none | none | none |
| `Load Alice novel` / `Load Pride and Prejudice` | `demo-data.ts:56,73` | `demo-data.test.ts` (4 tests, including the two integrity rejections) | `analysis.spec.ts` `loads the bundled Alice sample ...`, `loads the bundled Pride and Prejudice sample ...` | none |
| `Analyze text` submit and error path | `main.ts:537-570` | **none** | `analysis.spec.ts` `analyzes text and opens on the calm Highlights overlay` (happy path only) | `GrpcJsonApiTest.parsesAnalyzeRequestAndRendersDocumentShape`, `.mapsGrpcFailuresWithoutLeakingCauseDetails`; `OpenNlpGrpcWebServerTest.servesHealthApiAndSpiAssetsOverHttp` |
| `Batch analyze (streaming)` | `batch-analysis.ts`, `main.ts:508-534` | `batch-analysis.test.ts` (4 tests) | **none** | `OpenNlpGrpcWebServerTest.streamsBatchAnalysisAsNdjson`; `AnalyzeStreamTest` (18), `AnalyzeStreamWireContractTest` (3) |

### 1.2 Result panel

| Feature | Implementation | Unit | E2E | Gateway / service |
| --- | --- | --- | --- | --- |
| Layer reading and typing | `document-shape.ts:83` | `document-shape.test.ts` (13 tests) | indirectly | `DocumentShapeAssemblerTest` (10), `DocumentShapeWireContractTest` (3), `DocumentLayersValidatorTest` (13), `BasicDocumentAnalyzerDocumentLayersTest` (7) |
| Offset conversion to browser indices | `offsets.ts:24` | `offsets.test.ts` (3), `document-shape.test.ts` `converts UTF-8 byte and Unicode code-point spans to browser string offsets` | none | `OffsetMapperTest` (8), `DocumentOffsetEncoderTest` (5), `DocumentOffsetEncoderLayerTest` |
| `Highlights` default overlay | `main.ts:648-666`, `document-shape.ts:206` | `document-shape.test.ts` `keeps only entity and sentence layers in the calm first-run overlay` | `analysis.spec.ts` `analyzes text and opens on the calm Highlights overlay` | none |
| `All annotations` combined overlay | `document-shape.ts:115` | `document-shape.test.ts` `builds one combined projection across overlapping typed layers`, `builds thirty thousand annotation segments within a bounded time` | `analysis.spec.ts` asserts `[data-layer-kind="all"]` exists | none |
| Layer selection and per-layer markers | `main.ts:703-771` | **none** | partially, via `.annotation-marker` in `analysis.spec.ts` | none |
| `Filter layers` (`#layer-filter`) | `main.ts:917-948` | **none** | **none** | none |
| Document window slider (`#document-window-position`) | `document-window.ts:31`, `main.ts:883-916` | `document-window.test.ts` (3 tests) | **none** | none |
| `Document-wide results` chips and category collapse | `main.ts:790-820`, `document-shape.ts:175` | `document-shape.test.ts` `collapses a document-scoped category layer to its most probable chip` | `analysis.spec.ts` asserts `.document-annotation-chip` | none |
| Term vector stacked bar and pop-out | `term-vector-stack.ts` | `term-vector-stack.test.ts` (11 tests) | **none** | `BasicDocumentAnalyzerTermVectorTest` (8), `TermVectorWireContractTest` (2), `TermLayerWireContractTest` |
| Annotation drawer | `annotation-drawer.ts` | `annotation-drawer.test.ts` (9 tests) | `analysis.spec.ts` opens the drawer, asserts `.structured-value` and `pre`, and closes it | none |
| `Chunks` tab rendering | `chunk-projection.ts`, `chunk-projection-view.ts` | `chunk-projection.test.ts` (2 tests) covers the **reader only**; the view class has no test | **none** | `BasicDocumentAnalyzerChunkEmbedTest` (7), `BasicDocumentAnalyzerSemanticChunkTest` (2) |
| `Heatmap` tab, query mode | `semantic-workbench.ts:343-395`, `document-heatmap-view.ts` | `document-heatmap-view.test.ts` (3), `semantic-workbench.test.ts` `searches every projection exhaustively with TurboQuant and renders selectable lanes` | **none** | `OpenNlpGrpcWebServerTest.servesSearchCatalogAndDocumentShapedHitsOverHttp` |
| `Heatmap` tab, sentiment mode | `visualization-data.ts:68`, `semantic-workbench.ts:727` | `visualization-data.test.ts` `leaves semantic ranking to the server and reads typed sentiment rows`; `semantic-workbench.test.ts` `opens typed annotations when a sentiment segment is selected` | **none** | `BasicDocumentAnalyzerSentimentTest` (6), `DocumentShapeAssemblerTest.sentimentRendersScoredLabelsOnSentenceSpans` |
| `Graph` tab | `visualization-data.ts:100`, `charts.ts:98` | `visualization-data.test.ts` (3 graph tests); `document-window.test.ts` `prevents an unbounded complete graph ...` | **none** | none |
| `Show complete graph` (`#graph-completeness`) | `semantic-workbench.ts:551-590` | asserted only as markup in `index.test.ts` | **none** | none |
| `Protobuf JSON` tab and the large-response guard | `json-response.ts:31` | `json-response.test.ts` (2 tests) | **none** | none |
| `Copy JSON` / `Download JSON` | `main.ts:1013-1032` | **none** | **none** | none |
| `Download .pb` | `main.ts:1034`, `api.ts:491` | `api.test.ts` `encodes the stored response JSON into protobuf bytes` | **none** | `GrpcJsonApiTest.encodesAnalyzeResponseJsonAsProtobufBytes`, `.rejectsMalformedResponseJsonBeforeEncoding`, `.transcodeEndpointsRejectNonPostMethods`; `OpenNlpGrpcWebServerTest.transcodesSavedResponsesOverHttp` |
| `Open saved response` | `main.ts:1057`, `api.ts:507` | `api.test.ts` `decodes protobuf bytes back into the response JSON`, `surfaces the gateway error message when transcoding fails` | **none** | `GrpcJsonApiTest.decodesProtobufBytesBackToAnalyzeResponseJson`, `.rejectsMalformedResponseBytesLoudly` |
| `Add to server workspace` | `semantic-workbench.ts:243` | `semantic-workbench.test.ts` `indexes the current document on the server when the first workspace query is submitted`, `attaches search to a picked existing workspace without adding a document` | **none** | `OpenNlpGrpcWebServerTest.servesSearchCatalogAndDocumentShapedHitsOverHttp` |
| Ranked language chips and the routed-pipeline badge | `main.ts:1089-1131` | **none** | **none** | `RankedLanguageDetectionTest` (3), `RankedLanguageWireContractTest` (2), `LanguagePipelineRoutingTest` (6) |
| Markup contract for every id on the tab | `index.html` | `index.test.ts` (17 tests) | `workbench.spec.ts` `scopes the hero to the Analyze tab` | none |

### 1.3 Gateway behaviour the tab depends on

| Behaviour | Implementation | Test |
| --- | --- | --- |
| Deadline scaled by input size | `GrpcAnalysisRpc.java:239-244`, applied at `:144-147` | `GrpcAnalysisRpcTest.deadlinesScaleWithInputSizeUnderTheLongRunningCeiling`, `.rejectsANegativePerMebibyteAllowance` |
| Responses above grpc's 4 MiB default | `OpenNlpGrpcWebApp.java:192,210` | `GrpcAnalysisRpcTest.acceptsResponsesBeyondTheGrpcDefaultMessageLimit` (6 MiB) |
| gRPC status to HTTP mapping | `GrpcHttpStatusMapper.java:35-50` | `GrpcHttpStatusMapperTest.mapsCanonicalGrpcCodesToHttp` |
| Oversized request bodies | `OpenNlpGrpcWebServer.java:219-220` | `OpenNlpGrpcWebServerTest.rejectsOversizedBodiesAndUnsupportedMethods` |
| Malformed JSON and bad UTF-8 | `GrpcJsonApi.java:65-67` | `GrpcJsonApiTest.rejectsMalformedJsonWithCanonicalErrorPayload`, `.rejectsMalformedUtf8BeforeParsingProtobufJson` |
| Content type enforcement | `GrpcJsonApi.java` | `OpenNlpGrpcWebServerTest.acceptsTheJsonMediaTypeCaseInsensitivelyWithParameters`, `.transcodesSavedResponsesOverHttp` |

---

## 2. Features with no test at all

Listed by the element id or function that would need one.

### 2.1 Unit-test gaps

1. **`src/main.ts` has no unit test file.** Every behaviour that lives only in
   `main.ts` is untested at unit level: `submitAnalysis`
   (`main.ts:538`), `renderDocumentShape` (`main.ts:612`), `selectLayer`
   (`main.ts:735`), `renderCombinedOverlay` (`main.ts:781`),
   `filterLayerButtons` (`main.ts:917`), `renderLanguageSummary`
   (`main.ts:1092`), `routingDiagnostic` (`main.ts:1122`), `storeResponse`
   (`main.ts:1140`), `storedJson` (`main.ts:1152`), `presentLoadedResponse`
   (`main.ts:1071`), `loadLocalResponse` (`main.ts:1057`),
   `downloadResponsePb` (`main.ts:1034`), `copyResponse` (`main.ts:1006`),
   `configureDocumentWindow` (`main.ts:882`), `selectResultTab`
   (`main.ts:962`), `navigateResultTabs` (`main.ts:976`).
2. **`src/charts.ts` has no test.** `renderDocumentGraph` and `renderHeatmap`
   are untested. `renderHeatmap` also appears to be dead on this tab, see 2.4.
3. **`src/chunk-projection-view.ts` has no test.** The reader is tested, the
   rendering class is not. `#chunk-projection` empty state, group columns, and
   the click-to-drawer wiring are unverified.
4. **`signedSentimentScore` has no test for an unknown label family.**
   `visualization-data.test.ts` `leaves semantic ranking to the server and reads
   typed sentiment rows` exercises only labels the function recognises. The
   defect in `error-states-and-links.md` 4.1 would have been caught by one
   assertion with a `1_star` label.
5. **`AnalysisControls.renderFeatureOptions` status text is untested.** The
   three strings `Ready`, `Needs model or data`, `Not in this server build`
   (`analysis-controls.ts:288-289`) drive the only gating signal on the tab and
   no test asserts which step gets which.
6. **`AnalysisControls` downgrade from `max` to `automatic`** when `maxSteps` is
   empty (`analysis-controls.ts:150-152`) has no test.
7. **Profile mode dropping `embeddingModelId`** (`analysis-config.ts:245-248`)
   has no test asserting the resulting request shape.

### 2.2 E2E gaps

`analysis.spec.ts` has three tests. Every one of the following ids exists on the
tab, is wired in `main.ts`, and is never driven in a browser:

| Id | Feature |
| --- | --- |
| `#normalization-xray-toggle` | the entire X-ray flow |
| `#feature-picker` / `#feature-options` | the custom feature checklist and its gating labels |
| `#pos-tag-format-select` | UD and Penn conversion |
| `#pipeline-language-select` | language routing |
| `#token-chunk-size`, `#token-chunk-overlap` | the validation message `Overlap must be smaller than the token window.` (`analysis-controls.ts:178`) |
| `#batch-analyze-button`, `#batch-results` | streaming batch |
| `#chunks-tab`, `#chunk-projection` | the Chunks view |
| `#heatmap-tab`, `#heatmap-mode-query`, `#heatmap-mode-sentiment`, `#heatmap-query`, `#heatmap-projection-select` | the whole Heatmap view |
| `#graph-tab`, `#document-graph`, `#graph-completeness` | the whole Graph view |
| `#json-tab`, `#response-output` | the Protobuf JSON view and its large-response message |
| `#copy-button`, `#download-button`, `#download-pb-button`, `#load-response-button` | every export and import path |
| `#add-to-index-button` | indexing the analysed document |
| `#layer-filter` | layer filtering |
| `#document-window-position` | paging a novel-sized document |
| `#sample-button` | the short sample |

Of these, the highest-value additions are, in order: the round trip
`Download .pb` then `Open saved response` (it crosses three components and two
HTTP endpoints), the Heatmap query flow (it creates and deletes a server index),
and the feature checklist gating labels (they are the subject of
`model-gating.md`).

### 2.3 Gateway and service gaps relevant to this tab

1. **No test asserts any "not configured" error text.** A grep for
   `requested but no` across `opennlp-grpc-service/src/test`,
   `opennlp-grpc-webapp/src/test` and `opennlp-grpc-integration-tests` returns
   nothing. The tests that reach those code paths, for example
   `BasicDocumentAnalyzerParseTest.rejectsParseWhenNoModelConfigured` and
   `BasicDocumentAnalyzerSyntacticChunkTest.rejectsSyntacticChunkWhenNoModelConfigured`,
   assert only `AnalysisException.FailureType.NOT_FOUND`. The strings in
   `AnalysisRequestValidator.java` (lines 365, 404, 784, 806, 889, 940) are the
   only guidance a user gets today and the only source for the config keys the
   brown-out design in `model-gating.md` 5.2 would copy. They should be pinned.
2. **`GET /api/v1/model-bundles` has no `GrpcJsonApiTest` method**, although the
   Analyze tab cannot build a request without it. It is exercised only
   indirectly through `GrpcAnalysisRpcTest.delegatesAllUnaryGatewayCalls`.
3. **No test covers `Failed to read message.` or any transport-level failure
   text.** The mitigation is tested
   (`GrpcAnalysisRpcTest.acceptsResponsesBeyondTheGrpcDefaultMessageLimit`), the
   failure mode is not. See `error-states-and-links.md` 3.6.
4. **`PIPELINE_STEP_GEOCODE` has the thinnest service coverage of any step**:
   `BasicDocumentAnalyzerGeocodeTest` has two tests,
   `locationEntitiesResolveAgainstTheBundledGazetteer` and
   `geocodeWithoutNerFails`.
5. **No test covers a decode of a zero-byte `.pb` body.**
   `GrpcJsonApiTest.rejectsMalformedResponseBytesLoudly` covers junk bytes;
   an empty body returns HTTP 200 and `{}` (measured, see
   `../reference/demo-errors.md`), which no test pins and which the FE then
   presents as a successful load.

### 2.4 Possible dead code found while mapping

- `charts.ts:46` `renderHeatmap` is not imported by any Analyze-tab module. The
  Heatmap tab uses `document-heatmap-view.ts` instead. It is still used by
  `search-heatmap.ts` for the search tabs, so it is not dead overall, but it is
  dead for this tab and its `Text segment` axis label never appears here.
- `visualization-data.ts:85` `buildSimilarityHeatmapRows` has a test
  (`visualization-data.test.ts` `maps only server-ranked chunks from the current
  document into the shared heatmap`) but no production caller in
  `semantic-workbench.ts`.
- `HeatmapRows.semantic` (`visualization-data.ts:32`) is always assigned `[]`
  (`visualization-data.ts:81`) and is never read on this tab.

Worth confirming before the next cleanup pass; a test that pins unreachable code
is a maintenance cost with no coverage value.

---

## 3. Counts

| Suite | Files touching this tab | Tests touching this tab |
| --- | --- | --- |
| unit (vitest) | 17 | 112 |
| e2e (Playwright) | 2 | 4 (3 in `analysis.spec.ts`, 1 in `workbench.spec.ts`) |
| gateway (JUnit) | 4 | 26 |
| service (JUnit) | 40+ across the pipeline steps | 200+ |

The imbalance is the headline: the pure functions are very well covered, the
browser behaviour is covered by three Playwright tests, and the glue in
`main.ts` (1,195 lines) is covered by none.

---

## 4. Suggested additions, in priority order

| Priority | Test | Why |
| --- | --- | --- |
| P1 | unit: `signedSentimentScore` with `1_star`, `5_stars`, `LABEL_0`, and an unknown label | pins the defect in `error-states-and-links.md` 4.1 |
| P1 | unit: `AnalysisControls.renderFeatureOptions` asserts `Ready` / `Needs model or data` / `Not in this server build` per step, given a fixture matching the demo | pins the gating signal the brown-out design depends on |
| P1 | service: assert the exact "not configured" message text for NER, parse, chunker, doccat, subword, expand | those strings are user-facing and carry the config keys |
| P1 | e2e: `Download .pb` then `Open saved response` round trip | crosses the widest surface of any single flow |
| P2 | e2e: enable `Normalization X-ray`, analyse, assert both panes render paired runs | the flow has good unit coverage and zero browser coverage |
| P2 | e2e: heatmap query flow end to end, including index cleanup | the only flow that creates server state from this tab |
| P2 | unit: `main.ts` `storedJson` and `jsonPresentation` interaction on a large response | pins the export bounding fix |
| P2 | gateway: `GET /api/v1/model-bundles` in `GrpcJsonApiTest` | the tab cannot configure itself without it |
| P2 | gateway: `POST /api/v1/response/decode` with a zero-byte body | pins whatever the agreed behaviour becomes |
| P3 | e2e: `#layer-filter` and `#document-window-position` on the Alice sample | the large-document path is the demo's showpiece |

---

## Questions for the lead

1. Is there an appetite for a `main.ts` split so the glue becomes testable?
   A `analysis-view.ts` holding `renderDocumentShape`, `selectLayer`,
   `renderCombinedOverlay` and `filterLayerButtons` would be unit-testable
   against a jsdom fixture the way `AnalysisControls` already is.
2. Should the Playwright suite grow, or should the browser-level flows be
   covered by jsdom unit tests against `index.html` the way `index.test.ts`
   already reads the real markup? The second is faster and covers more; the
   first catches layout and focus bugs the second cannot.
3. `buildSimilarityHeatmapRows` and `HeatmapRows.semantic` appear unused on this
   tab (2.4). Are they reserved for planned work, or should they and their test
   be removed?
