# Repository sources relied on

All paths are relative to the repository root
`/work/worktrees/opennlp/sandbox-grpc-query/opennlp-grpc`.

## Front end (opennlp-grpc-webapp-default)

| File | Why |
| --- | --- |
| `index.html:384-543` | The whole `workflows-workbench` section markup and every user-visible string on the tab |
| `index.html:44-45` | The `Workflows` tab button |
| `index.html:545-600` | Corpus search header, its two tab bridges, and the `build your own workspace index` link at line 592 |
| `index.html:722-788` | Workspace search header, its tab bridge, the workspace picker, and the "How to use workspace search" list |
| `index.html:869-960` | Trainer tab, which duplicates workflow stages 2 and 3 |
| `index.html:962-1125` | Lifecycle tab, checkpoint, seal, alias, rebuild, collections |
| `src/corpus-workflow.ts` | The entire tab controller: stages, gating, API calls, rendering |
| `src/main.ts:324-378` | How the tab is wired to `api.ts` and to the other tabs |
| `src/api.ts` | Endpoint surface (`learn-vocabulary`, `train-static-model`, `index-documents`, `search`, and the rest) |
| `src/search-adapter.ts` | `SearchIndex` shape, `immutable`, `supportsAllHits`, provider capability reading |
| `src/collection-adapter.ts` | Collection and drift vocabulary (used by Lifecycle, not by this tab) |
| `src/batch-analysis.ts` | Batch streaming helpers. Note: wired to the **Analyze** tab, not to Workflows |
| `src/semantic-workbench.ts:174-232` | Workspace picker refresh and attach behaviour |
| `src/server-search-workbench.ts:139-161` | Corpus search index listing and its empty state |
| `src/lifecycle-workbench.ts:120-185` | Lifecycle catalog refresh, empty states, index facts |
| `src/vocabulary-trainer.ts:398-495` | `readTeachers`, `readLearnedVocabulary`, `readTrainedModel`, `writesEnabled` |
| `src/analysis-config.ts:219-271` | `buildAnalysisRequest`, the `max` mode used by every workflow analyze call |
| `src/workbench-navigation.ts` | `data-workbench-jump` handling |

## Tests

| File | Why |
| --- | --- |
| `opennlp-grpc-webapp-default/test/corpus-workflow.test.ts` | The only unit tests for this tab |
| `opennlp-grpc-webapp-default/test/index.test.ts:94,109,128-142` | Markup assertions for the tab |
| `opennlp-grpc-webapp-default/test/workbench-navigation.test.ts:84-92` | The jump into Workflows |
| `opennlp-grpc-webapp-default/e2e/workbench.spec.ts:33-76` | Tab bridge and workflow e2e specs |
| `opennlp-grpc-webapp-default/e2e/corpus-search.spec.ts` | Skip-on-empty-catalog pattern worth copying |
| `opennlp-grpc-webapp/src/test/java/.../GrpcJsonVocabularyApiTest.java` | Gateway coverage of learn-vocabulary and train-static-model |
| `opennlp-grpc-webapp/src/test/java/.../GrpcJsonSearchApiTest.java` | Gateway coverage of index-documents and search |

## Server and contract

| File | Why |
| --- | --- |
| `opennlp-grpc-api/src/main/proto/org/apache/opennlp/grpc/v1/opennlp_search.proto:49-51` | "the server creates an opaque workspace id" |
| `opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/DynamicSearchIndexRegistry.java:1075` | Generated ids are `workspace-<uuid>` |
| `.../search/DynamicSearchIndexRegistry.java:518-527` | Exact persist and seal precondition errors |
| `.../search/WorkspaceCheckpointStore.java:19-60` | `search.persist.root`, checkpoint layout |
| `.../training/StaticModelArtifactStore.java:79-205,599-606` | `vocabulary.artifact_root`, `training.teacher.<id>.ref`, `writesEnabled()` |
| `.../training/OpenNlpModelTrainingServiceImpl.java:257-265` | `ListTeachers` fills `writesEnabled` |
| `.../vocabulary/OpenNlpVocabularyServiceImpl.java:54-58,305-315` | Exact learn-vocabulary writes-disabled message |
| `README.md:450-462,780-836` | Prose descriptions of the tab, persistence, and dynamic indexing |
| `QUICKSTART.md:55-72` | The documented five minute journey, which omits this tab |

## Live instance observations

Read-only `GET` calls against the running demo at `http://127.0.0.1:7172` on 2026-08-28. Verbatim
bodies are reproduced in `live-instance-state.md`.
