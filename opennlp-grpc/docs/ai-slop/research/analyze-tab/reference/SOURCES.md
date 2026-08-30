# Sources consulted

## External pages (excerpts saved beside this file)

| File | Source URL | Fetched |
| --- | --- | --- |
| `spacy-linguistic-features.md` | https://spacy.io/usage/linguistic-features | 2026-08-28 |
| `opennlp-manual-chapters.md` | https://opennlp.apache.org/docs/2.5.4/manual/opennlp.html | 2026-08-28 |
| `inception-annotation-layers.md` | https://inception-project.github.io/releases/34.2/docs/user-guide.html | 2026-08-28 |
| `elasticsearch-term-vectors.md` | https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-termvectors.html | 2026-08-28 |
| `elastic-chunking-settings.md` | https://www.elastic.co/docs/explore-analyze/elastic-inference/inference-api | 2026-08-28 |
| `lsp-position-encoding.md` | https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/ | 2026-08-28 |
| `huggingface-tokenizer-summary.md` | https://huggingface.co/docs/transformers/en/tokenizer_summary | 2026-08-28 |

## Live demo instance (read-only calls, 2026-08-28)

Base URL `http://127.0.0.1:7172`. Responses used in the findings are captured in
`demo-state.md` and `demo-errors.md` beside this file.

- `GET /api/v1/service-info`
- `GET /api/v1/model-bundles`
- `GET /api/v1/installed-models`
- `GET /api/v1/model-catalog`
- `GET /api/v1/static-models`
- `POST /api/v1/analyze` (many shapes, all read-only inference)
- `POST /api/v1/analyze-stream`
- `POST /api/v1/response/decode`, `POST /api/v1/response/encode`
- `GET /data/alice-in-wonderland.txt.gz`, `GET /data/pride-and-prejudice.txt.gz`

## Repository files relied on

Front end (`opennlp-grpc-webapp-default`):
`index.html`, `src/main.ts`, `src/analysis-controls.ts`, `src/analysis-config.ts`,
`src/annotation-drawer.ts`, `src/document-shape.ts`, `src/document-heatmap-view.ts`,
`src/document-window.ts`, `src/normalization-xray.ts`, `src/chunk-projection.ts`,
`src/chunk-projection-view.ts`, `src/term-vector-stack.ts`, `src/charts.ts`,
`src/visualization-data.ts`, `src/json-response.ts`, `src/batch-analysis.ts`,
`src/demo-data.ts`, `src/offsets.ts`, `src/semantic-workbench.ts`,
`src/model-data-workbench.ts`, `src/api.ts`, `src/ui-utils.ts`,
`src/workbench-navigation.ts`, `src/discovery.ts`.

Gateway (`opennlp-grpc-webapp`):
`src/main/java/org/apache/opennlp/grpc/webapp/GrpcAnalysisRpc.java`,
`GrpcJsonApi.java`, `GrpcHttpStatusMapper.java`, `OpenNlpGrpcWebApp.java`,
`OpenNlpGrpcWebServer.java`, and the matching tests under `src/test`.

Service (`opennlp-grpc-service`):
`src/main/java/org/apache/opennlp/grpc/processor/basic/AnalysisRequestValidator.java`
and the tests under `src/test/java/org/apache/opennlp/grpc/processor/basic/`
and `.../v1/server/`.

Third-party artifact inspected: `io.grpc:grpc-core:1.81.0`, class
`io/grpc/internal/ClientCallImpl$ClientStreamListenerImpl$1MessagesAvailable`,
which is where the literal `Failed to read message.` lives.
