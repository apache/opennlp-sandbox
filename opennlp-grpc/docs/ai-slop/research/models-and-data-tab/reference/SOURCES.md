# Repository sources relied on

Read-only, in the worktree at `/work/worktrees/opennlp/sandbox-grpc-query/opennlp-grpc`,
on 2026-08-28. External material is in the other files in this directory.

## Front end (`opennlp-grpc-webapp-default`)

- `index.html:790-868` the `model-data-workbench` section, plus the tab button
  at `:51` and the three `data-workbench-jump` attributes at `:555`, `:592`,
  `:735`.
- `src/model-data-workbench.ts` the whole tab (715 lines).
- `src/analysis-config.ts:23` `PIPELINE_ORDER`, `:43` `FEATURE_NAMES`, `:136`
  `discoverAnalysisCapabilities`.
- `src/analysis-controls.ts:289` the Analyze tab's "Needs model or data" label.
- `src/vocabulary-trainer.ts:157,161,165` the Trainer tab's empty-teacher texts.
- `src/workbench-navigation.ts:40` the jump mechanism.
- `src/main.ts:205-253,441` the wiring of `ModelDataWorkbench`.
- `src/api.ts:411-441,534-561` `installModel` NDJSON handling and error text.
- `test/model-data-workbench.test.ts`, `test/api.test.ts`,
  `test/analysis-config.test.ts`, `test/workbench-navigation.test.ts`.
- `e2e/workbench.spec.ts`, `e2e/analysis.spec.ts`, `e2e/corpus-search.spec.ts`
  (checked for coverage of this tab; there is none).

## Gateway (`opennlp-grpc-webapp`)

- `src/main/java/org/apache/opennlp/grpc/webapp/GrpcJsonApi.java:120-200` the
  route table.
- `src/main/java/org/apache/opennlp/grpc/webapp/OpenNlpGrpcWebServer.java:151`
  and the surrounding NDJSON streaming handler.
- `src/test/java/.../GrpcJsonVocabularyApiTest.java:117-119`,
  `src/test/java/.../OpenNlpGrpcWebServerTest.java:190`.

## Service (`opennlp-grpc-service`)

- `.../training/CatalogModelStore.java` (699 lines), especially `:70`, `:243`,
  `:260`, `:311`, `:334-346`, `:377-398`, `:443-489`, `:588-613`.
- `.../training/CatalogModelBootstrap.java` (181 lines), especially `:129-180`.
- `.../training/OpenNlpModelTrainingServiceImpl.java:157-217`.
- `.../training/StaticModelArtifactStore.java:80-91,410-460`.
- `.../processor/basic/AnalysisRequestValidator.java:777-1020` the gating error
  messages.
- `.../processor/basic/BasicDocumentAnalyzer.java:721-733` the pipeline-language
  error.
- `.../model/SubwordRegistry.java:45`, `.../model/WordNetRegistry.java:46`,
  `.../model/ClassicDocCategorizerBackendFactory.java:49`,
  `.../model/ClassicNerBackendFactory.java:52`,
  `.../model/ClassicParserBackendFactory.java:44`.
- `src/test/java/.../training/CatalogModelStoreTest.java`,
  `src/test/java/.../training/CatalogModelBootstrapTest.java`.

## Installer (`opennlp-grpc-installer`)

- `src/main/java/.../installer/StandardModelCatalog.java` (362 lines), the whole
  hard-coded catalog.
- `src/test/java/.../installer/StandardModelCatalogTest.java`,
  `.../UdLanguageModelCatalogTest.java`.

## SPI (`opennlp-grpc-spi`)

- `.../spi/catalog/CatalogModel.java`, `.../spi/catalog/CatalogFile.java`,
  `.../spi/catalog/ModelCatalogProvider.java`.
- `.../spi/vocabulary/VocabularyStore.java`.

## Store add-on (`opennlp-grpc-store-s3`)

- `src/main/java/.../store/s3/S3VocabularyStore.java` (the module has no
  README).

## Protos (`opennlp-grpc-api`)

- `src/main/proto/org/apache/opennlp/grpc/v1/opennlp_training.proto:37-178`
  (service, `ModelArtifactRole`, `ModelCatalogDescriptor`,
  `InstalledModelDescriptor`, `InstallModelRequest`, `InstallModelStage`,
  `InstallModelProgress`) and `:338-378` (`StaticModelDescriptor`).

## Documentation

- `README.md:105-115,200-230,259-340,1145-1190`.
- `docs/tutorials/german-end-to-end.md` (the only tutorial that walks the
  catalog install flow).

## Live instance

Read-only GETs against `http://127.0.0.1:7172`, captured in
`reference/live-catalog.md`. No write endpoint was called. Two harmless probes
were issued to record error shapes: `POST /api/v1/model-catalog` returns
`{"code":"UNIMPLEMENTED","message":"HTTP method is not allowed for this endpoint"}`
with status 405, and `GET /api/v1/nope` returns
`{"code":"NOT_FOUND","message":"Unknown API endpoint"}` with status 404.
