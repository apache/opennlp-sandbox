# Sources for the corpus-search-tab audit

External sources are excerpted in the sibling `naming-*.md` and `terminology-*.md` files in this
directory, each with its own source URLs and a 2026-08-28 fetch date.

## Repository files relied on

Front end (`opennlp-grpc-webapp-default`):

- `index.html:46-56` workbench tab list; `index.html:546-720` the whole Corpus search section;
  `index.html:725-790` the Workspace search section, for the bridge wording; `index.html:386-440`
  the Workflows section; `index.html:975-1095` the Lifecycle section
- `src/server-search-workbench.ts` (653 lines) the tab controller
- `src/query-builder.ts` clause model and `QueryNode` construction
- `src/search-adapter.ts` descriptor, request and response parsing
- `src/search-view-model.ts` score colour, status text, chunk comparison, matched segments
- `src/search-heatmap.ts` document heat construction
- `src/api.ts` all gateway calls and `responseError`
- `src/ui-utils.ts:65-67` `errorMessage`
- `src/workbench-navigation.ts` `data-workbench-jump` binding
- `src/main.ts:252-440` workbench wiring and boot order
- `src/semantic-workbench.ts:178-195` the Workspace search index filter
- `src/lifecycle-workbench.ts:129, 180` the Lifecycle index filter and the "Sealed" fact
- `test/server-search-workbench.test.ts`, `test/query-builder.test.ts`,
  `test/search-adapter.test.ts`, `test/search-view-model.test.ts`, `test/search-heatmap.test.ts`,
  `test/index.test.ts`, `test/workbench-navigation.test.ts`
- `e2e/corpus-search.spec.ts`

Gateway (`opennlp-grpc-webapp`):

- `src/main/java/org/apache/opennlp/grpc/webapp/OpenNlpGrpcWebServer.java` (JDK HttpServer, request
  body cap, security headers)
- `src/main/java/org/apache/opennlp/grpc/webapp/OpenNlpGrpcWebApp.java:51-95, 140-190` CLI defaults
  and timeout wiring
- `src/main/java/org/apache/opennlp/grpc/webapp/GrpcJsonApi.java:142-170, 283-297` search routes
- `src/main/java/org/apache/opennlp/grpc/webapp/GrpcSearchRpc.java` deadline stub
- `src/main/java/org/apache/opennlp/grpc/webapp/GrpcHttpStatusMapper.java:36-48` status table
- `src/test/java/org/apache/opennlp/grpc/webapp/GrpcJsonSearchApiTest.java`,
  `OpenNlpGrpcWebServerTest.java`

Service (`opennlp-grpc-service`):

- `src/main/java/org/apache/opennlp/grpc/search/OpenNlpSearchServiceImpl.java` search, compound
  search, result limits, route validation, alias resolution
- `src/main/java/org/apache/opennlp/grpc/search/SearchIndexRegistry.java:200-222`,
  `DynamicSearchIndexRegistry.java:156, 711, 1272-1280`
- `src/main/java/org/apache/opennlp/grpc/backend/RankedBackends.java:165, 185` engine lookup errors
- `src/test/java/org/apache/opennlp/grpc/search/OpenNlpSearchServiceImplTest.java` and the
  `search/query` test package

Protocol (`opennlp-grpc-api/src/main/proto/org/apache/opennlp/grpc/v1`):

- `opennlp_query.proto:63-190` `QueryNode`, clause types, `JoinOperator`, `JoinFusion`,
  `BoostClause`, CEL clauses
- `opennlp_search.proto:440-530, 600-635` `SearchIndexDescriptor`, `SearchIndexComponent`,
  `SearchComponentKind`, request result limits, `truncated`

Add-ons and docs:

- `README.md:105-125` the optional add-on list; `README.md:640-710` the "Legal opinions" bundle
  recipe and its serving configuration; `README.md:760-840` provider instances, lifecycle, and
  dynamic indexing
- `opennlp-grpc-search-turboquant/src/main/java/.../TurboQuantSearchBundleLoader.java:200-230,
  406-425` the immutable bundle descriptor and its provider record
- `opennlp-grpc-spi/src/main/java/org/apache/opennlp/grpc/spi/search/SearchIndexProvider.java:60-80`
  the `queryCandidates` and `keywordQueryIndex` defaults

## Live observations, 2026-08-28

Three demo instances were reachable and were queried read-only over HTTP:

- `http://127.0.0.1:7072` (container `docker-opennlp-1`, image `opennlp-grpc-demo`)
- `http://127.0.0.1:7172` (container `opennlp-gpu-demo`, image `opennlp-grpc-demo:gpu`)
- `http://127.0.0.1:7272` (container `opennlp-native-demo`, image `opennlp-grpc-demo:native`)

Endpoints read: `/api/v1/service-info`, `/api/v1/search-indexes`, `/api/v1/index-aliases`,
`/api/v1/search-providers`, `/api/v1/collections`, `/api/v1/installed-models`,
`/api/v1/ui-extensions`. One `POST /api/v1/search` was issued with the exact body the front end
builds, against a nonexistent index id, to capture the real error payload. No mutating endpoint was
called.

### Keep-alive reproduction

The "Failed to fetch" reproduction opened one TCP connection, sent
`GET /api/v1/search-indexes`, idled for a fixed interval, then sent
`POST /api/v1/search` with `{"indexId":"legal-opinions","query":{"rawText":"due process"},"topK":8}`
on the same connection. Results are tabulated in `../findings/legal-opinions-failure.md` section 3:
the connection answers after 5 and 31 seconds of idling and is already closed after 35, 40 and 45
seconds, on both the GPU and the native demo.
