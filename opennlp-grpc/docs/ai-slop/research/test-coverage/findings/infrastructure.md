# Test infrastructure inventory

Everything here is FACT read from the tree, with `path:line` citations.

## 1. Front-end unit tests (vitest)

- Runner config: `opennlp-grpc-webapp-default/vite.config.ts`, `test.include` is
  `["test/**/*.test.ts"]`, with the comment that `e2e/` belongs to Playwright.
- Location: `opennlp-grpc-webapp-default/test/`, **not** `src/test/`. The only
  thing under `src/test/` is one Java file,
  `src/test/java/org/apache/opennlp/grpc/webapp/defaultui/DefaultWebUiExtensionTest.java`.
- Command: `opennlp-grpc-webapp-default/package.json:9`,
  `"test": "tsc --noEmit && vitest run"`. The TypeScript compile is part of the
  gate.
- Measured on this tree: **32 test files, 225 tests, all passing, 756 ms**
  (`npm test`, 2026-08-28).
- Environment: there is no global `environment` setting in `vite.config.ts`.
  Suites that need a document opt in per file with a
  `/** @vitest-environment jsdom */` pragma on line 20; 13 of the 32 files do
  (for example `test/annotation-drawer.test.ts:20`,
  `test/vocabulary-trainer.test.ts:20`). The rest run in the default node
  environment.
- Toolchain pins: `typescript` 7.0.2, `vitest` 4.1.10, `vite` 8.2.1,
  `@playwright/test` 1.62.1, `echarts` 6.1.0 (`package.json:12-22`).
- `tsconfig.json` is strict: `strict: true`, `noUncheckedIndexedAccess: true`,
  and `include` covers `src`, `test`, `e2e`, and both config files, so the e2e
  specs are type-checked by `npm test` even though they are never run by it.

Test file list (32): `analysis-config`, `analysis-controls`,
`annotation-drawer`, `api`, `batch-analysis`, `chunk-projection`,
`collection-adapter`, `corpus-workflow`, `demo-data`, `discovery`,
`document-heatmap-view`, `document-shape`, `document-window`, `index`,
`json-response`, `model-data-workbench`, `normalization-xray`, `offsets`,
`query-builder`, `search-adapter`, `search-heatmap`, `search-view-model`,
`semantic-workbench`, `server-search-workbench`, `term-vector-stack`,
`text-utils`, `theme-toggle`, `ui-extensions`, `ui-utils`,
`visualization-data`, `vocabulary-trainer`, `workbench-navigation`.

There is **no** test file for `main.ts` (48 KB, the sole caller of fifteen
`api.ts` functions), for `charts.ts`, for `chunk-projection-view.ts`, for
`lifecycle-workbench.ts`, or for `document-shape`'s sibling `offsets` consumers
beyond the unit level. `index.test.ts` does string matching over `index.html`
(`test/index.test.ts:24`, `readFileSync` of `../index.html`) and never imports
any module.

## 2. Front-end e2e (Playwright)

- Config: `opennlp-grpc-webapp-default/playwright.config.ts`.
- **baseURL**: `process.env.OPENNLP_E2E_BASE_URL`, and the config **throws** at
  load time if it is unset (`playwright.config.ts:29-31`). There is no default.
- **webServer**: none. Playwright starts nothing; the operator must already have
  a gateway and a gRPC server running. The header comment points at
  `http://127.0.0.1:7072` for the Docker demonstration image.
- Execution model: `fullyParallel: false`, `workers: 1`, `retries: 0`,
  `timeout: 240_000`, `expect.timeout: 15_000`, `trace: "retain-on-failure"`,
  Desktop Chrome. The single worker is deliberate: "The suite drives one shared
  server, so tests must not interleave writes."
- Command: `npm run e2e` (`package.json:10`), after
  `npx playwright install chromium` once
  (`opennlp-grpc-webapp-default/README.md:27-35`).
- Content: 3 spec files, 8 tests. `analysis.spec.ts` (3),
  `corpus-search.spec.ts` (1), `workbench.spec.ts` (6, one of them the long
  write path).

## 3. Maven wiring for the front end

`opennlp-grpc-webapp-default/pom.xml:70-122`, frontend-maven-plugin 1.15.1,
node v22.16.0, npm 10.9.0, all four executions bound to `generate-resources`:

| execution id | arguments |
| --- | --- |
| `install-node-and-npm` | node + npm download |
| `npm-ci` | `ci --ignore-scripts` |
| (test) | `test` |
| (build) | `run build` |

`<frontend.skip>` defaults to `false` (`pom.xml:36`) and gates all of them, so
`-Dfrontend.skip=true` skips the front-end tests entirely. `npm run e2e` appears
in no pom in the repository.

Build output goes to
`target/generated-resources/META-INF/opennlp-grpc-ui/default`
(`vite.config.ts`), with fixed asset names `assets/app.js` and
`assets/[name][extname]`, which is what the gateway's `WebUiAssetResolver`
serves.

## 4. Java test execution

- Surefire configuration is centralised in the root `pom.xml` pluginManagement
  block, `pom.xml:443-455`: `argLine` `-Xmx2048m -Dfile.encoding=UTF-8`,
  `forkCount 1`, `reuseForks true`, `failIfNoSpecifiedTests false`, and
  `<exclude>**/*IT.java</exclude>`.
- Failsafe is at `pom.xml:457-481`: same argLine and fork settings,
  `includes **/*IT.java`, `excludes **/*Test.java`, goals `integration-test` and
  `verify`.
- **`opennlp.forkCount` is a no-op for this subtree.** The property is declared
  at `pom.xml:124` (`1.0C`) and overridden to `1` under the `jacoco` profile
  (`pom.xml:775`), but the only module that interpolates it is
  `opennlp-similarity/pom.xml:480`. The shared surefire block hard-codes
  `<forkCount>1</forkCount>`. `opennlp-grpc-webapp/README.md:17` nevertheless
  tells readers to pass `-Dopennlp.forkCount=1`.
- `opennlp-grpc-service` adds its own surefire and failsafe configuration
  (`opennlp-grpc-service/pom.xml:219-236`), including a
  `junit5-system-exit` javaagent on the failsafe argLine, and unpacks
  integration-test models in `generate-test-resources`.
- `opennlp-grpc-dl` adds a profile-scoped surefire that passes
  `-Ddl.ner.model.dir` (`opennlp-grpc-dl/pom.xml:186-194`).

### The shared test jar

`opennlp-grpc-service/pom.xml:206-218` attaches a `test-jar`, with the comment
that add-on modules need "the search registry and service tests that need their
provider on the classpath, reusing these fixtures". Exactly one module consumes
it: `opennlp-grpc-search-turboquant/pom.xml:111-117` declares
`opennlp-grpc-service` with `<classifier>tests</classifier>` and test scope, so
the TurboQuant provider's registry tests run in the same package as the server's
search tests and share their fixtures. `opennlp-grpc-search-lucene` and
`opennlp-grpc-store-s3` do **not** use it.

### Java test volume

| Module | test/IT files | `@Test` + `@ParameterizedTest` |
| --- | --- | --- |
| `opennlp-grpc-service` | 136 | 792 |
| `opennlp-grpc-webapp` | 11 | 64 |
| `opennlp-grpc-dl` | 12 | 52 |
| `opennlp-grpc-search-turboquant` | 8 | 59 |
| `opennlp-grpc-integration-tests` | 6 | 29 |
| `opennlp-grpc-search-lucene` | 2 | 16 |
| `opennlp-grpc-backend-static` / `-tei` / `-openvino` | 1 each | 14 each |
| `opennlp-grpc-installer` | 3 | 9 |
| `opennlp-grpc-formats` | 1 | 7 |
| `opennlp-grpc-sink-grpc` | 1 | 5 |
| `opennlp-grpc-store-s3` | 2 | 4 |
| `opennlp-grpc-webapp-default` | 1 | 2 |
| `opennlp-grpc-spi` | 1 | 1 |
| `opennlp-grpc-api` | 0 | 0 |

`opennlp-grpc-api` has no tests of its own; the proto contract is defended by
the 18 `*WireContractTest` files that live in `opennlp-grpc-service`.

## 5. CI

`.github/workflows/maven.yml` (at the repository root, one level above
`opennlp-grpc/`):

- Triggers: push to `main` and `experimental/**`, pull requests to `main`.
- Matrix: `ubuntu-latest`, `macos-latest`, `windows-latest` times Java 21 and 25.
  Six jobs.
- Steps: checkout, `~/.m2/repository` cache, setup-java (temurin), then
  `mvn -V clean test verify --no-transfer-progress -Pjacoco` (line 59) and
  `mvn jacoco:report` (line 61).

So CI runs: the TypeScript compile, the 225 vitest tests, the vite build, every
surefire suite, and the failsafe integration tests that do not self-skip. CI does
**not** run: Playwright, `docker/test-image.sh`, any native image, or anything
requiring `OPENNLP_TEI_TARGET`, `OPENNLP_OVMS_TARGET`, `OPENNLP_PYTHON_E2E` or
`OPENNLP_E2E_WORKFLOW_WRITE`.

`docker/test-image.sh` is referenced only by `docker/README.md:69,77`, its own
header comment (`:29`) and a comment in `docker-compose.yml:32`. No workflow, no
pom, no script invokes it. Its assertions are: non-root runtime user,
`/healthz` returns `ok`, a nine-word analyze through the gateway returns 10
tokens (`:89-95`), `service-info` reports the mounted `maxTextBytes` of 524288
(`:97-100`), and shutdown within 15 seconds.

The native image (`docker/native/build-native.sh`,
`docker/native/start-native.sh`) has no test at all: neither script issues a
request against what it builds.

## 6. Skipped, conditional and flaky tests

### Front end

Two `test.skip` calls, both conditional and both legitimate:

- `e2e/workbench.spec.ts:58` skips the live workflow build unless
  `OPENNLP_E2E_WORKFLOW_WRITE=1`, because it creates persistent vocabulary and
  model artifacts. It also raises its own timeout to 1,200,000 ms.
- `e2e/corpus-search.spec.ts:32` skips when the server reports no configured
  search index. Note the shape at `:27`:
  `await expect(indexSelect).toBeEnabled({ timeout: 30_000 }).catch(() => undefined)`
  followed by a manual re-check. A swallowed expectation is a soft spot: if the
  select is slow rather than absent, the test skips instead of failing, and a
  regression that empties the catalog would read as a skip, not a failure.

No `xit`, `it.skip`, `describe.skip` or `test.todo` in
`opennlp-grpc-webapp-default/test/`.

### Java

No `@Disabled` anywhere in the tree. Conditional execution instead:

| Location | Condition |
| --- | --- |
| `RealTeiServerLiveIT.java:58` | `@EnabledIfEnvironmentVariable(named = "OPENNLP_TEI_TARGET")` |
| `RealOpenVinoServerLiveIT.java:62` | `@EnabledIfEnvironmentVariable(named = "OPENNLP_OVMS_TARGET")` |
| `PythonLifecycleLiveIT.java:78` | `assumeTrue` on the Python e2e env var |
| `BasicDocumentAnalyzerParseTest.java:95,129,164` | `assumeTrue` a parser model file exists |
| `BasicDocumentAnalyzerSyntacticChunkTest.java:71` | `assumeTrue` a chunker model file exists |
| `OpenNlpAnalysisServiceImplTest.java:327` | `assumeTrue` a parser model file exists |
| `SearchCollectionRegistryTest.java:139`, `StaticModelArtifactStoreTest.java:197` | `assumeTrue` the filesystem supports POSIX attributes (skips on Windows) |
| `OnnxEmbeddingBatchParityTest.java:52,56,81,85,106,110` | `assumeTrue` a real ONNX model directory is configured |
| `OnnxDocumentClassifierTest.java:140,143` | same |
| `BasicDocumentAnalyzerDlNerTest.java:68,71` | `assumeTrue -Ddl.ner.model.dir` |

The pattern is sound: no test is permanently disabled, everything is gated on a
resource that may be absent. The cost is that the tests protecting the most
recently fixed drift bugs, the ONNX batch parity and doc-categorizer tests added
in `1a37a4ec` and `06595dfa`, are exactly the ones that self-skip on a machine
without the real models, which includes every CI runner in `maven.yml`.

The POSIX assumptions mean two of the three CI operating systems silently drop
those assertions.

## 7. What actually gates a merge, in one list

1. `tsc --noEmit` over `src`, `test`, `e2e` and the two config files.
2. 225 vitest tests, all of them against hand-written fixtures.
3. `vite build`.
4. Every surefire suite, roughly 1,050 Java tests, minus the ones that self-skip
   for a missing model or a non-POSIX filesystem.
5. Failsafe integration tests that do not require an external target.
6. Checkstyle, forbiddenapis and RAT.

Not gated: Playwright, the Docker image assertions, the native image, real-model
ONNX parity on CI runners, and anything that compares the front end's JSON to
the proto.
