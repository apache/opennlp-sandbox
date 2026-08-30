# Drift analysis: where the front end, the gateway, the protos and the validators can disagree

All FACT statements are read from the code at the cited `path:line`, or observed
against the running demo instance at `http://127.0.0.1:7172` on 2026-08-28 using
read-only routes only.

## 1. Where the front end's types come from

FACT. Every request and response type the browser uses is **hand-written
TypeScript**. There is no code generation from the protos anywhere in the front
end. Evidence:

- `opennlp-grpc-webapp-default/package.json` has no protobuf, `buf`, `ts-proto`,
  `protoc` or descriptor dependency; `devDependencies` are Playwright, node
  types, jsdom, TypeScript, vite and vitest, and the single runtime dependency is
  `echarts`.
- `opennlp-grpc-webapp-default/pom.xml:79-121` runs exactly four
  frontend-maven-plugin executions: install node, `npm ci --ignore-scripts`,
  `npm test`, `npm run build`. No generation step.
- The request shapes are declared by hand at
  `opennlp-grpc-webapp-default/src/api.ts:23-136` (`AnalyzeRequest`,
  `IndexDocumentsRequest`, `ImportDictionaryUpload`, `LearnVocabularyUpload`,
  `TrainStaticModelRequest`, `InstallModelRequest`, `ReindexIndexRequest`,
  `SetCollectionRequest`) and at
  `opennlp-grpc-webapp-default/src/search-adapter.ts:107-137` (`SearchRequest`).
- Responses are not typed against the proto at all. Every `api.ts` reader returns
  `Promise<unknown>` (`api.ts:148-369`) and each workbench re-parses the payload
  defensively with its own local reader, for example
  `search-adapter.ts:182-215`, `collection-adapter.ts:75-90`,
  `vocabulary-trainer.ts:433-480`, `document-shape.ts:248-271`.

FACT. The gateway's side of the same contract is `protobuf-java-util`'s
`JsonFormat` with **default settings**:
`GrpcJsonApi.java:103` is `JsonFormat.parser()` and `:104` is
`JsonFormat.printer().omittingInsignificantWhitespace()`. That means, on the
wire: lowerCamelCase field names on output, unknown input fields **rejected**,
enums as their full uppercase names, `int64`/`uint64` printed as JSON strings,
and proto3 default values omitted from output.

So the contract has two independent hand-maintained sources of truth, the
`.proto` files and `api.ts` plus a dozen ad hoc readers, with a strict parser in
between. Nothing compares them.

## 2. Is there any test that compares the two?

FACT. No. The closest candidates and why each falls short:

- `opennlp-grpc-webapp-default/test/api.test.ts:47-62` builds a `vi.fn()` fetcher
  that returns `JSON.stringify({ url })` for every call, then asserts the
  captured request body equals `JSON.stringify({...})` of the same literal the
  test wrote three lines earlier. The assertion is the serializer reflected in a
  mirror. It cannot fail on a wrong field name, a wrong enum spelling, or a
  missing required field.
- `opennlp-grpc-webapp/src/test/java/.../GrpcJsonSearchApiTest.java:112-116`
  writes the search request as a Java text block:
  `{"indexId":"...","query":{"docId":"query-1","rawText":"habeas corpus"},"allHits":true}`.
  That is a *second* hand-written copy of the same shape the front end builds at
  `search-adapter.ts:118-137`. When one is edited the other is not.
- The `*WireContractTest` family in the service (18 files, for example
  `opennlp-grpc-service/src/test/java/org/apache/opennlp/grpc/processor/basic/DocumentShapeWireContractTest.java:33-52`)
  pins proto field numbers, oneof arms and enum value sets **inside Java**. They
  are a real defence against proto churn, and they are the right idea. They just
  do not know the front end exists.
- The integration tests reach only two JSON routes:
  `OpenNlpGrpcServerLiveIT.java:237` (`/api/v1/search-indexes`) and `:244`
  (`/api/v1/search`). Everything else goes over gRPC stubs directly.

FACT (measured). Running the current `api.ts` interface keys and the current FE
uppercase string constants against the proto sources today:

- 75 enum-shaped string literals appear in `src/*.ts` and `index.html`. Sixty-nine
  are real proto enum values and all 69 currently match. The other six are
  prefixes used for string stripping (`PIPELINE_STEP_`, `NORMALIZER_`,
  `STANDARD_SEARCH_PROVIDER_`, `SEARCH_PROVIDER_CAPABILITY_`,
  `STANDARD_DICTIONARY_FORMAT_`) and one local sentinel (`ALL_PROJECTIONS`).
- Every key declared in the `api.ts` request interfaces maps to a proto field
  name in camelCase form.

So the contract is correct today and held together by review discipline alone.
That check took twenty lines of Python; it is not in the test suite.

## 3. How strict the gateway actually is (observed live)

| Probe against `127.0.0.1:7172` | Result |
| --- | --- |
| `POST /api/v1/analyze` with an extra `"bogusField":1` | `400 {"code":"INVALID_ARGUMENT","message":"Malformed protobuf JSON request: Cannot find field: bogusField in message org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest"}` |
| `POST /api/v1/analyze` with `"steps":["PIPELINE_STEP_TOKENISE"]` | `400 ... Invalid enum value: PIPELINE_STEP_TOKENISE for enum type: org.apache.opennlp.grpc.v1.PipelineStep` |
| `POST /api/v1/analyze` with snake_case `"raw_text"` and `"profile_id"` | `200` |

Two consequences worth stating plainly:

1. Any misspelled field name or enum in the front end is a **hard 400 at
   runtime**, never a compile error and never a test failure. The front end's
   `strict: true` TypeScript config (`tsconfig.json`) checks `api.ts` against
   `api.ts`.
2. The parser accepts snake_case on input but the printer emits only
   lowerCamelCase. A reader that guesses `raw_text` gets `undefined` silently
   forever, which is the failure mode of every hand-written response reader in
   `src/`.

## 4. The concrete gap behind each named drift bug

### 4.1 "Selected chunk embedding must carry a complete resolved route"

FACT. Thrown at
`opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/DynamicSearchIndexRegistry.java:980-986`
when the embedding's route has a blank `model_id`, `backend_id` **or**
`vector_space_id`. Fixed in `5e0c8e1c` by deriving a space in
`CompositeEmbeddingProvider`.

The gap. The front end never sends a route. `IndexDocumentsRequest`
(`api.ts:67-75`) carries only `documents`, `embedding.modelId` and
`chunkGroupIds`; the route is whatever the analyze step attached server-side. So
this failure only appears in the *sequence* analyze then index-documents, on a
deployment whose engine declared no `vector_space_id`. Nothing tests that
sequence:

- `api.test.ts` posts `index-documents` to a mock, so the route never exists.
- Every FE fixture that carries a route hard-codes a populated space:
  `test/corpus-workflow.test.ts:51` and `:106` use `vectorSpaceId: "space-1"`,
  `test/search-adapter.test.ts:39` uses `"mini-v1"`,
  `test/search-heatmap.test.ts:41` uses `"space"`. The optimistic fixture is why
  the front end never saw the state that broke.
- `GrpcJsonSearchApiTest.indexesAndDeletesAWorkspaceThroughProtobufJson:262`
  drives a `StubSearchRpc`, so the validator at
  `DynamicSearchIndexRegistry.java:985` is never on the path.
- The integration suite does not call `/api/v1/index-documents` at all.

### 4.2 "Failed to read message."

FACT. Diagnosed in `1a37a4ec`: the native image was built without
`-H:+SharedArenaSupport`, so on JDK 25 `grpc-netty-shaded` freeing a pooled
direct buffer through the FFM API killed the first response large enough to
release a pool chunk.

The gap. There is **no test of the native image at all**.
`docker/native/build-native.sh` builds it and `docker/native/start-native.sh`
runs it; neither issues a request. `docker/test-image.sh` exercises the JVM image
only, and its largest payload is the 44-character sentence at
`docker/test-image.sh:89-95`, which will never release a Netty pool chunk. Two
independent size axes went untested: the native runtime, and any response over a
few kilobytes.

### 4.3 "No typed sentiment layer was returned"

FACT. The string is at
`opennlp-grpc-webapp-default/src/semantic-workbench.ts:535`, reached when
`buildHeatmapRows` returns an empty `sentiment` array. The extractor is
`visualization-data.ts:68-81`: it keeps layers whose `valueType` is `"Category"`
and whose id or standard identity contains `"sentiment"`
(`visualization-data.ts:158-160`), and drops any annotation with no numeric
`score`.

**This one is still open, in a second form, and it is reproducible today.** A
live `POST /api/v1/analyze` with `"profileId":"en-sentiment"` on the demo
instance returns:

```
"id": "opennlp:sentiment", "scope": "LAYER_SCOPE_POSITIONAL",
"categoryValues": { "annotations": [
  { "span": {"end": 12}, "label": "5_stars", "score": 0.8887939453125 },
  { "span": {"start": 13,"end": 28}, "label": "1_star", "score": 0.8420758247375488 } ] }
```

The shipped sentiment model emits star-rating labels. `signedSentimentScore`
(`visualization-data.ts:162-174`) branches only on the substrings `"negative"`,
`"neutral"` and `"positive"`, and otherwise returns the raw score. So `1_star`
with confidence 0.84 is rendered as **+0.84, a strongly positive segment**, on
the same heatmap lane as `5_stars` at +0.89. The negative sentence is coloured
positive.

The gap is the fixture. `test/visualization-data.test.ts:44-53` supplies exactly
the three labels the function knows: `"positive"`, `"negative"`, `"neutral"`.
`test/semantic-workbench.test.ts:299` and `test/document-shape.test.ts:287` do
the same. No test at any layer asserts the label vocabulary a real installed
doc-categorizer produces, and the label vocabulary is a property of the model
file, not of any code in this repository. Two secondary symptoms follow from the
same lane: the tooltip at `document-heatmap-view.ts:104` and the caption at
`:128` both read `cosine`, which is wrong for a sentiment lane.

### 4.4 The catalog id with a `.`

FACT. `7938d722` renamed `en-ner-1.5-*` to `en-ner-15-*` because the catalog
store rejects ids outside `[a-z0-9-]` at startup, so the server would not boot.
The fix added the missing test,
`StandardModelCatalogTest.everyCatalogIdUsesOnlyLowercaseLettersDigitsAndHyphens`.

The gap was that the store's alphabet rule and the catalog's id list were two
files with no test between them, and the failure was at **startup**, not at
request time, so nothing short of booting a server could see it. The interesting
detail: the catalog id list *was* pinned by
`StandardModelCatalogTest` (a literal `Set.of(...)` of every id), and that test
passed with the illegal ids in it, because it pinned the ids against themselves
rather than against the consumer's rule. That is the same mirror-assertion
pattern as `api.test.ts`.

### 4.5 `DEADLINE_EXCEEDED` on large inputs

FACT. `06595dfa` made analysis deadlines scale with submitted text:
`--request-timeout-seconds` (default 30) plus
`--request-timeout-per-megabyte-seconds` (default 120) per mebibyte, capped by
`--long-running-timeout-seconds` (default 1800), wired at
`OpenNlpGrpcWebApp.java:72-84` and `:150-157`, computed at
`GrpcAnalysisRpc.scaledDeadlineNanos:239-243`, and covered by
`GrpcAnalysisRpcTest.deadlinesScaleWithInputSizeUnderTheLongRunningCeiling`.

The gap that let it through: nothing in the suite ever submitted a document
larger than a sentence over HTTP. `docker/test-image.sh:89` uses one sentence;
`analysis.spec.ts:36` uses two.

**The same gap is still open on a neighbouring route.** FACT: only two calls get
the scaled deadline, `GrpcAnalysisRpc.java:138` (`analyzeDocument`) and `:145`
(`formatDocument`). `GrpcSearchRpc.indexDocuments` at
`GrpcSearchRpc.java:107` uses the flat `deadlineStub()` built from the same
30 second `requestTimeout` (`OpenNlpGrpcWebApp.java:154`). Indexing a novel's
chunks with embeddings is at least as expensive as analysing it, and the
workbench's own workflow does exactly that. `GrpcVocabularyRpc` is in the same
position (`GrpcVocabularyRpc.java:82-130`, all `timeoutNanos`), though
`learn-vocabulary` at least streams.

### 4.6 Immutable results capped at 50

FACT. `283e03e6` raised the immutable bundle default `max_top_k` from 50 to
1000 at
`opennlp-grpc-spi/src/main/java/org/apache/opennlp/grpc/spi/search/SearchIndexBundleConfiguration.java:54`,
and pinned it equal to the dynamic default in
`SearchIndexRegistryTest`. The gap was that two defaults for the same concept
lived in two modules with no test asserting they agreed. The front end reads the
value through `search-adapter.ts:211` (`maxTopK`) and clamps at
`server-search-workbench.ts:232` and `:566`, so the user saw a silently short
result list rather than an error.

## 5. Is the e2e suite run against a real gateway or a mock?

FACT. Against a real gateway, and only if you supply one.
`playwright.config.ts:29-33` reads `OPENNLP_E2E_BASE_URL` and **throws** if it is
unset; there is no `webServer` block, so Playwright starts nothing. The specs
drive real routes: `analysis.spec.ts:24` clicks the sample loader and waits up to
30 s for the gateway's static assets, `:40` runs a real analyze with a 180 s
budget, `corpus-search.spec.ts:35` runs a real search.

## 6. Are the e2e specs part of any CI gate?

FACT. No, at three independent checkpoints:

- `.github/workflows/maven.yml:59` runs `mvn -V clean test verify -Pjacoco` and
  nothing else. No node step beyond what Maven does, no browser, no docker.
- `opennlp-grpc-webapp-default/pom.xml:101-120` runs `npm test` and
  `npm run build` in `generate-resources`. `npm run e2e` appears in no pom.
- `docker/test-image.sh` is referenced only from `docker/README.md:69,77`, its
  own header comment, and a comment in `docker-compose.yml:32`. Nothing invokes
  it.

So the gate is: TypeScript compile, 225 vitest unit tests, and the Java suites.
Everything that crosses a process boundary in a browser is manual.

This is a deliberate choice, not an oversight. The commit that added the suite,
`b1dc9a92`, says so: "It stays out of the Maven build (browsers plus a live
stack are prerequisites) and runs via OPENNLP_E2E_BASE_URL and npm run e2e; tsc
type-checks the specs in the existing npm test gate." The reasoning was sound for
a per-developer `mvn verify`. What the drift bugs show is that the *conclusion*
should be revisited now that a Docker image, a health check and a smoke script
already exist: the prerequisites the commit worried about are one
`docker/test-image.sh` invocation away. See `recommendations.md` P1-4.

## 7. The shape of the gap, in one sentence

Every layer is tested against its own idea of the contract: the front end
against its own literals, the gateway against Java text blocks it wrote itself,
the service against its own proto descriptors. The only place the three meet is
the running system, and the running system is checked by two `curl` calls in a
shell script nobody runs.
