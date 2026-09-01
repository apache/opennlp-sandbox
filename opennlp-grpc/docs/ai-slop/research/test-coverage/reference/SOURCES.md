# Sources

No external material was consulted. Everything in `findings/` was read from this
repository at commit `7938d722` (`OPENNLP-1833: give the classic NER catalog
entries legal ids and pin the id alphabet`), plus read-only observation of the
running demo instance on 2026-08-28.

## Repository files relied on

### Front end (`opennlp-grpc-webapp-default/`)

| File | Used for |
| --- | --- |
| `src/api.ts` | The 35 routes the browser calls, the hand-written request interfaces, the NDJSON readers, the error path |
| `src/analysis-config.ts` | `buildAnalysisRequest`, numeric coercion helpers |
| `src/search-adapter.ts` | `SearchRequest` shape, the oneof encoding, the index descriptor reader, numeric coercion |
| `src/visualization-data.ts` | Sentiment layer extraction and `signedSentimentScore` |
| `src/semantic-workbench.ts` | The "No typed sentiment layer was returned" path, `IndexDocumentsRequest` assembly |
| `src/document-shape.ts`, `src/document-heatmap-view.ts`, `src/collection-adapter.ts`, `src/model-data-workbench.ts`, `src/vocabulary-trainer.ts`, `src/lifecycle-workbench.ts`, `src/server-search-workbench.ts` | Response readers and numeric handling |
| `src/main.ts` | Sole caller of fifteen `api.ts` functions |
| `index.html` | Tab list, jump attributes, element ids, hard-coded enum strings |
| `test/*.test.ts` (32 files) | Unit coverage inventory; `api.test.ts` and `visualization-data.test.ts` in detail |
| `e2e/*.spec.ts` (3 files) | e2e coverage inventory |
| `package.json`, `vite.config.ts`, `playwright.config.ts`, `tsconfig.json`, `pom.xml`, `README.md` | Test infrastructure |

### Gateway (`opennlp-grpc-webapp/`)

| File | Used for |
| --- | --- |
| `src/main/java/.../GrpcJsonApi.java` | The 32-route switch, `JsonFormat` parser and printer configuration, error mapping |
| `src/main/java/.../OpenNlpGrpcWebServer.java` | The four NDJSON stream routes, `ui-extensions`, `/healthz` |
| `src/main/java/.../GrpcAnalysisRpc.java` | Size-scaled deadlines, `scaledDeadlineNanos` |
| `src/main/java/.../GrpcSearchRpc.java`, `.../GrpcVocabularyRpc.java` | Flat deadlines on the other routes |
| `src/main/java/.../OpenNlpGrpcWebApp.java` | Timeout options and wiring |
| `src/main/java/.../GrpcHttpStatusMapper.java` | gRPC status to HTTP mapping |
| `src/test/java/.../GrpcJsonApiTest.java`, `GrpcJsonSearchApiTest.java`, `GrpcJsonVocabularyApiTest.java`, `GrpcAnalysisRpcTest.java`, `GrpcSearchRpcTest.java`, `OpenNlpGrpcWebServerTest.java`, `OpenNlpGrpcWebAppTest.java`, `GrpcHttpStatusMapperTest.java`, `WebUiCatalogJsonTest.java`, `WebUiAssetResolverTest.java`, `WebUiExtensionRegistryTest.java` | Gateway coverage inventory, stub-versus-real determination |
| `README.md` | The `opennlp.forkCount` claim |

### Service (`opennlp-grpc-service/`)

| File | Used for |
| --- | --- |
| `src/main/java/.../search/DynamicSearchIndexRegistry.java` | The "must carry a complete resolved route" validator |
| `src/main/java/.../search/SearchIndexRegistry.java` | `max_top_k` handling |
| `src/main/java/.../descriptors/DescriptorSetLoader.java` | Existing descriptor-set consumer |
| `src/test/java/**/*WireContractTest.java` (18 files) | The existing proto-pinning pattern |
| `src/test/java/.../search/SearchIndexRegistryTest.java` | The `max_top_k` default parity test |
| `pom.xml` | Test jar, surefire and failsafe configuration |

### Other modules

| File | Used for |
| --- | --- |
| `opennlp-grpc-api/src/main/proto/org/apache/opennlp/grpc/v1/*.proto` | Field names, enum values, oneofs, int64 fields |
| `opennlp-grpc-api/pom.xml` | The shipped descriptor set |
| `opennlp-grpc-spi/src/main/java/.../search/SearchIndexBundleConfiguration.java` | The immutable bundle `max_top_k` default |
| `opennlp-grpc-installer/src/test/java/.../StandardModelCatalogTest.java` | The catalog id alphabet test added in `7938d722` |
| `opennlp-grpc-integration-tests/src/test/java/.../*.java` | Integration coverage inventory, conditional execution |
| `opennlp-grpc-search-turboquant/pom.xml` | The test-jar consumer |
| `docker/test-image.sh`, `docker/README.md`, `docker/docker-compose.yml`, `docker/native/build-native.sh`, `docker/native/start-native.sh` | Image assertions and the native build |
| Repository root `pom.xml` | Surefire and failsafe pluginManagement, `opennlp.forkCount`, the jacoco profile |
| Repository root `.github/workflows/maven.yml` | The CI gate |
| `opennlp-grpc/README.md` | `max_top_k` documentation, catalog documentation |

## Git history consulted

`git log --oneline -60` and `git show` on: `7938d722`, `06595dfa`, `5e0c8e1c`,
`283e03e6`, `1a37a4ec`, `b1dc9a92` (the commit that added the Playwright suite),
and `cc59a39a` (the output formatter SPI, origin of the two routes with no
browser caller).

## Commands run

- `npm test` in `opennlp-grpc-webapp-default` (the only command run against the
  build). Result: 32 files, 225 tests, all passing, 756 ms.
- Static greps and two short Python scripts that diff the front end's enum
  literals and `api.ts` interface keys against the `.proto` sources. Both are
  reproduced in `drift-analysis.md` section 2 as findings; neither modified the
  tree.

## Live observations

Against the read-only demo instance at `http://127.0.0.1:7172`, 2026-08-28.
Only `GET` listings and `POST /api/v1/analyze` were called; no write route was
touched.

| Request | Purpose |
| --- | --- |
| `GET /api/v1/service-info` | Confirm the printer's output shape and the advertised steps and layers |
| `POST /api/v1/analyze` with `"profileId":"en-sentiment"` | Capture the real sentiment layer, its label vocabulary (`5_stars`, `1_star`) and the omission of default-valued `start` fields |
| `POST /api/v1/analyze` with an unknown field `bogusField` | Confirm the strict parser returns 400 `INVALID_ARGUMENT` |
| `POST /api/v1/analyze` with `PIPELINE_STEP_TOKENISE` | Confirm enum spelling is rejected at 400 |
| `POST /api/v1/analyze` with snake_case `raw_text` and `profile_id` | Confirm the parser accepts proto field names on input while the printer emits only camelCase |
