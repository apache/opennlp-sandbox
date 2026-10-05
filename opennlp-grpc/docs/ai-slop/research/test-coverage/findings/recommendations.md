# Recommendations, in priority order

Priorities follow the shared convention: **P1** confusing or broken for a
first-time user, **P2** worth doing, **P3** polish. Effort is S (under a day),
M (a few days), L (a week or more). Everything below is OPINION; the FACTs it
rests on are in `coverage-map.md` and `drift-analysis.md`.

The single organising idea: the contract already exists in machine-readable form
and is already shipped. `opennlp-grpc-api/pom.xml:78-82` writes a descriptor set
to `META-INF/opennlp/descriptors/opennlp-grpc-v1.protobin`, the service already
has a `DescriptorSetLoader`
(`opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/descriptors/DescriptorSetLoader.java:42`),
and the Python lifecycle script already loads it
(`opennlp-grpc-integration-tests/scripts/lifecycle_e2e.py:28`). Nothing in the
front end or the gateway test suite uses it. Most of what follows is spending
that asset.

---

## P1-1. A request-fixture round trip through the real parser and the real validators (M)

The highest-value single test. Give the front end one place where every request
it can build is written down as a fixture, then feed those fixtures through the
gateway's actual `JsonFormat.parser()` and the service's actual validators.

Shape:

1. New `opennlp-grpc-webapp-default/test/fixtures/requests/*.json`, one file per
   route, produced by calling the real builder functions
   (`createSearchRequest`, `createCompoundSearchRequest`,
   `buildAnalysisRequest` (`analysis-config.ts:218`), the `IndexDocumentsRequest` assembly in
   `semantic-workbench.ts:330-340`, and so on), not hand-typed. A vitest that
   writes them and fails when a fixture changes without being regenerated keeps
   them honest.
2. The build already copies the front end into the gateway jar. Add
   `opennlp-grpc-webapp/src/test/java/org/apache/opennlp/grpc/webapp/FrontEndRequestFixtureTest.java`
   that walks the fixture directory, maps filename to route, and calls
   `GrpcJsonApi.handle("POST", route, bytes)` with a stub RPC that captures the
   parsed proto. A parse failure is the test failure. This alone would have
   caught every field-name and enum-spelling class of drift, and would fail loudly
   on the strict-parser behaviour proved live in `drift-analysis.md` section 3.
3. Where a service-side validator exists and is static, assert the captured proto
   against it. `DynamicSearchIndexRegistry.validateEmbedding`
   (`opennlp-grpc-service/.../DynamicSearchIndexRegistry.java:980`) is the model
   case: a fixture representing "chunk group from an engine with no declared
   vector space" would have failed before `5e0c8e1c` shipped.

Files that change: new fixture directory, one new vitest, one new Java test,
`opennlp-grpc-webapp/pom.xml` (a test dependency on the built front-end
resources).

Why P1: this is the mechanism behind bug 4.1 and the entire class of "the FE
sent something the gateway rejected".

## P1-2. Pin the front end's enum and field vocabulary against the descriptor set (S)

Cheaper than P1-1 and catches a large slice of the same class. A vitest that
reads `opennlp-grpc-v1.protobin` (or, to avoid a protobuf runtime in the
browser bundle's dev dependencies, the `.proto` sources under
`opennlp-grpc-api/src/main/proto/`) and asserts:

- every uppercase enum-shaped literal in `src/*.ts` and `index.html` is a real
  enum value in some proto (69 such literals today, all currently valid, see
  `drift-analysis.md` section 2);
- every key declared in the `api.ts` request interfaces corresponds to a proto
  field name in camelCase.

An allow-list handles the six prefix constants and the one local sentinel. This
is roughly twenty lines and runs in the existing `npm test` gate, which means it
runs in CI today with no new infrastructure.

Files that change: new
`opennlp-grpc-webapp-default/test/proto-contract.test.ts`, plus a note in
`opennlp-grpc-webapp-default/README.md`.

## P1-3. Fix the sentiment label mapping and pin it against a real model (S for the fix, M with the test)

`visualization-data.ts:162-174` maps only `positive`, `negative`, `neutral`, but
the shipped `en-sentiment` model returns `1_star` through `5_stars`, so a
one-star sentence renders as strongly positive today (evidence in
`drift-analysis.md` section 4.3). Two parts:

- Fix: handle ordinal star labels and any other label vocabulary the catalog can
  install, and stop calling the sentiment lane's numbers `cosine`
  (`document-heatmap-view.ts:104` and `:128`).
- Test: the label vocabulary belongs to the model file, so the honest test is at
  the service or integration layer. Add to
  `opennlp-grpc-integration-tests` a check that the labels a catalog sentiment
  model produces are in a set the front end declares, with the set itself
  exported from the front end and asserted in a vitest. If the model changes,
  one of the two fails.

Files that change: `opennlp-grpc-webapp-default/src/visualization-data.ts`,
`src/document-heatmap-view.ts`, `test/visualization-data.test.ts`,
`opennlp-grpc-integration-tests/src/test/java/org/apache/opennlp/grpc/it/OpenNlpGrpcServerLiveIT.java`.

## P1-4. Run the Playwright smoke suite inside `docker/test-image.sh` (M)

`docker/test-image.sh` already builds the image, waits for health, and asserts
two routes (`:86-100`). It is the only place in the repository where the whole
system runs. Extend it:

```
# after the health check, with the container's HTTP port already known
OPENNLP_E2E_BASE_URL="http://127.0.0.1:$HTTP_PORT" npm --prefix opennlp-grpc-webapp-default run e2e -- --grep-invert @write
```

Gate it behind a flag so the script stays usable without node
(`SKIP_E2E=1`), and tag the two write-heavy specs so the default run stays
read-only. Then call the script from a CI job.

Files that change: `docker/test-image.sh`, `docker/README.md`,
`.github/workflows/maven.yml` (a new job, not a new step on the matrix build,
so the three-OS Java matrix stays fast), `opennlp-grpc-webapp-default/e2e/*.spec.ts`
(tags).

Why P1: three of the six named drift bugs were only observable in a running
system, and today nothing in CI runs one.

---

## P2-1. A size ladder for the deadline logic, and close the `index-documents` hole (M)

FACT restated: only `analyzeDocument` (`GrpcAnalysisRpc.java:138`) and
`formatDocument` (`:145`) get the size-scaled deadline;
`GrpcSearchRpc.indexDocuments` (`GrpcSearchRpc.java:107`) still uses the flat
30 second default. Two pieces:

- Extend the scaling to `indexDocuments`, and to
  `GrpcVocabularyRpc.learnVocabulary`, sized on the submitted document bytes.
  Reuse `GrpcAnalysisRpc.scaledDeadlineNanos` by lifting it to a small shared
  helper.
- Add a size ladder as a parameterized gateway test: 1 KB, 64 KB, 1 MB, 8 MB of
  text through `analyze`, `analyze-stream` and `index-documents`, asserting the
  computed deadline for each and that none exceeds the long-running ceiling. This
  is a unit test of the arithmetic, so it costs milliseconds.
- Separately, add one large-payload case to `docker/test-image.sh`: post the
  bundled Alice sample and assert a 200 and a plausible token count. That is the
  only assertion that would have caught "Failed to read message.", which needed a
  response large enough to release a Netty pool chunk.

Files that change: `GrpcAnalysisRpc.java`, `GrpcSearchRpc.java`,
`GrpcVocabularyRpc.java`, `OpenNlpGrpcWebApp.java`,
`opennlp-grpc-webapp/src/test/java/.../GrpcAnalysisRpcTest.java` (new
parameterized ladder), `docker/test-image.sh`.

## P2-2. e2e specs for Models and data, and for Lifecycle (M)

FACT: those two tabs have no e2e coverage at all
(`coverage-map.md` section 4), and between them they own fourteen of the fifteen
routes with no FE unit test. New specs:

- `opennlp-grpc-webapp-default/e2e/models.spec.ts`: open the tab, assert the
  catalog list renders, assert every rendered catalog id matches `[a-z0-9-]+`
  (the exact rule that took the server down in `7938d722`, now checked from the
  browser's side too), assert an install button is disabled until the licence
  box is ticked. All read-only.
- `opennlp-grpc-webapp-default/e2e/lifecycle.spec.ts`: open the tab, assert the
  index list, alias list and collection list render or show their empty state,
  and assert the disabled reasons on the write buttons. Read-only by default,
  with the write path behind the existing `OPENNLP_E2E_WORKFLOW_WRITE` flag
  pattern from `workbench.spec.ts:58`.

## P2-3. Cover the gateway's one untested route and the front end's stream readers (S)

- `/api/v1/model-bundles` has no test that goes through
  `GrpcJsonApi.handle` (`coverage-map.md` section 3). One assertion in
  `GrpcJsonVocabularyApiTest.listsFormatsTeachersAndModels` closes it.
- `api.ts` has three NDJSON readers with no unit test: `watchCollection:270`,
  `analyzeStream:298`, and the shared `ndjsonLines:443` generator. They handle
  split frames, blank lines, trailing partial lines and mid-stream error
  objects, which is real logic. A vitest that feeds a hand-built
  `ReadableStream` through each is straightforward and would cover
  `watch-collection` and `analyze-stream` at the FE layer for the first time.

Files that change: `opennlp-grpc-webapp/src/test/java/.../GrpcJsonVocabularyApiTest.java`,
new `opennlp-grpc-webapp-default/test/ndjson.test.ts`.

## P2-4. Extend the integration suite past two JSON routes (L)

`OpenNlpGrpcServerLiveIT` reaches the real gateway for `search-indexes` and
`search` only (`:237`, `:244`). The harness (`LiveWebAppHarness`) already exists.
Adding a read-only sweep, one GET per listing route, asserting 200 and a parseable
protobuf JSON envelope, is cheap and would catch route-table regressions and
serialization changes against a real server. The write routes deserve one
end-to-end sequence: `index-documents` then `search` then `persist-index` then
`seal-index` then `delete-search-index`, which is also the sequence that produced
bug 4.1.

## P2-5. Decide what `/api/v1/output-formats` and `/api/v1/format-document` are for (S)

They are served (`GrpcJsonApi.java:134`, `:136`) and tested
(`GrpcJsonApiTest:51-57`) but no browser code calls them. Either wire them into
the front end or document them as a programmatic-only surface, so the next
reader does not assume the front end covers them.

---

## P3-1. Generate the TypeScript request types from the descriptor set (L)

The full version of P1-2. Replacing the hand-written interfaces at
`api.ts:23-136` with generated types would make the contract a compile error
rather than a test failure. It is listed at P3 rather than P1 because it adds a
code-generation step to a front-end build that today is four npm invocations and
no generation, it pulls a protobuf toolchain into the browser build, and P1-1
plus P1-2 capture most of the value at a fraction of the cost. Worth doing when
the number of hand-written readers grows past what review can hold.

## P3-2. Make `opennlp.forkCount` real, or stop documenting it (S)

`opennlp-grpc-webapp/README.md:17` tells readers to run
`mvn ... -Dopennlp.forkCount=1`. FACT: that property is declared at the root
`pom.xml:124` and consumed by exactly one module, `opennlp-similarity/pom.xml:480`.
The surefire pluginManagement block that governs every `opennlp-grpc` module
hard-codes `<forkCount>1</forkCount>` (root `pom.xml:448`). The flag is a no-op
for this subtree. Either interpolate the property in the shared surefire
configuration or drop it from the README.

## P3-3. Give `main.ts` a seam (M)

`opennlp-grpc-webapp-default/src/main.ts` is 48 KB and is the sole caller of
fifteen `api.ts` functions, which is why those routes have no FE test. It does
not need a rewrite; extracting the per-tab wiring into modules that take their
dependencies as parameters, the way `lifecycle-workbench.ts` and
`vocabulary-trainer.ts` already do, would let each of those routes get the same
treatment the tested tabs already have.

---

## Sequencing

If only one thing gets done: P1-2, because it lands inside the existing CI gate
with no new infrastructure. If two: add P1-4, because it is the only proposal
that puts a running system in front of a browser in CI. P1-1 is the durable fix
and should follow.

## Questions for the lead

1. Is a CI job that builds and boots the Docker image acceptable in wall-clock
   terms, or should the Playwright smoke run stay a release-time check?
2. Should the request fixtures in P1-1 live with the front end (regenerated by
   vitest, consumed by Java) or with the gateway (checked in as Java text blocks,
   verified against the front end)? The first keeps the front end as the author
   of its own requests, which I think is right, but it means the gateway test
   depends on a front-end build output.
3. `output-formats` and `format-document`: intended for the browser later, or
   deliberately programmatic only?
4. The sentiment label fix needs a decision on what the workbench should show for
   an ordinal label set. Signed polarity derived from the star rank, or a
   separate ordinal lane that does not pretend to be polarity?
