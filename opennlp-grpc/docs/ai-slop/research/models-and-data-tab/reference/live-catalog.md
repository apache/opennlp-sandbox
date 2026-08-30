# Live catalog inventory of the demo node

Source: the running demo instance at `http://127.0.0.1:7172`, read-only GETs on
`/api/v1/model-catalog`, `/api/v1/installed-models`, `/api/v1/model-bundles`,
`/api/v1/static-models`, `/api/v1/dictionaries`, `/api/v1/teachers`,
`/api/v1/dictionary-formats`, `/api/v1/service-info`. Captured 2026-08-28.

## Node state at capture time

| Endpoint | Body | What the front end derives |
| --- | --- | --- |
| `GET /api/v1/model-catalog` | 26 `models`, **no `installsEnabled` field** | `readModelCatalog` sets `installsEnabled: false` because `body.installsEnabled === true` fails (`src/model-data-workbench.ts:574`). Every consent checkbox renders disabled. |
| `GET /api/v1/installed-models` | `{}` | `readInstalledModels` reads `models` off an absent array, returns `[]` (`src/model-data-workbench.ts:595`). |
| `GET /api/v1/static-models` | `{}` | No trained static models, and `writes_enabled` is absent so trainer writes are off. |
| `GET /api/v1/dictionaries` | `{}` | No imported dictionaries. |
| `GET /api/v1/teachers` | `{"maxPcaDims":512,"maxConcurrentTrainings":1}` | No `teachers` array: the trainer teacher select is empty. |
| `GET /api/v1/model-bundles` | `bundles`: `en-sentiment`, `en-basic` | 7 configured steps. |
| `GET /api/v1/service-info` | 17 pipeline steps in `supportedSteps` plus `PIPELINE_STEP_CHUNK`; **no `configuredResources`** | `subwordModelId` and `wordnetLexiconId` are undefined. |

Derived pipeline readiness on this node (`discoverAnalysisCapabilities`,
`src/analysis-config.ts:136`): configured from bundles are
`LANGUAGE_DETECT, SENTENCE_DETECT, TOKENIZE, POS_TAG, LEMMATIZE, SENTIMENT, EMBED`;
the model-free steps `NORMALIZE, STEM, TERM_VECTOR` are added, giving 10.
The header therefore reads **"10 of 17 features ready"**.
The 7 rows that read **"Needs model or data"** are
`SUBWORD_TOKENIZE, NER, GEOCODE, EXPAND, DOC_CATEGORIZE, PARSE, SYNTACTIC_CHUNK`.
`PIPELINE_STEP_CHUNK` is advertised by the service but is absent from
`PIPELINE_ORDER` (`src/analysis-config.ts:23`), so it never appears at all.

Bundle members returned for `en-basic` and `en-sentiment` carry
`componentType`, `backendId` (`opennlp-me`, `cuda`), `hash`,
`embeddingDimension`, and `embeddingRoutes.vectorSpaceId`. The front end keeps
only `supportedSteps`, `supportedLanguages`, and the embedder ids; the
`backendId` runtime family is discarded.

## The 26 catalog entries

`family / format` is read from the pinned file list in
`opennlp-grpc-installer/.../StandardModelCatalog.java`; **it is not in the JSON
payload**, because `ModelCatalogDescriptor`
(`opennlp-grpc-api/src/main/proto/org/apache/opennlp/grpc/v1/opennlp_training.proto:101`)
carries no file list and no format field.

| catalogId | role | family / format (from Java, not from the API) | size | license | languages | activation |
| --- | --- | --- | --- | --- | --- | --- |
| `all-minilm-l6-v2-teacher` | DISTILLATION_TEACHER | ONNX graph `onnx/model.onnx` + HF `tokenizer.json` (WordPiece) | 86.7 MiB | Apache-2.0 | en | immediate |
| `paraphrase-multilingual-minilm-l12-v2-teacher` | DISTILLATION_TEACHER | ONNX graph + HF `tokenizer.json` (Unigram, 9 MB) | 457.2 MiB | Apache-2.0 | multilingual | immediate |
| `potion-base-8m` | STATIC_EMBEDDING | safetensors table + `vocab.txt` WordPiece, 256d | 29.0 MiB | MIT | en | immediate |
| `potion-retrieval-32m` | STATIC_EMBEDDING | safetensors table + `vocab.txt` WordPiece, 512d | 123.7 MiB | MIT | en | immediate |
| `potion-multilingual-128m` | STATIC_EMBEDDING | safetensors table + `tokenizer.json` Unigram, 256d | 506.4 MiB | MIT | multilingual | immediate |
| `gum-cc-by-4-parser` | PARSER | one OpenNLP `.bin` (`en-gum-cc-by-4-parser.bin`), maxent chunking parser | 1.0 MiB | CC-BY-4.0 | en | restart |
| `gum-cc-by-4-chunker` | CHUNKER | one OpenNLP `.bin` (`en-gum-cc-by-4-chunker.bin`), maxent ChunkerME | 192.3 KiB | CC-BY-4.0 | en | restart |
| `en-ner-15-person` | NAME_FINDER | one OpenNLP 1.5 `.bin` maxent NameFinderME | 4.8 MiB | Apache-2.0 | en | restart |
| `en-ner-15-location` | NAME_FINDER | same | 4.9 MiB | Apache-2.0 | en | restart |
| `en-ner-15-organization` | NAME_FINDER | same | 5.1 MiB | Apache-2.0 | en | restart |
| `en-ner-15-date` | NAME_FINDER | same | 4.8 MiB | Apache-2.0 | en | restart |
| `en-ner-15-money` | NAME_FINDER | same | 4.6 MiB | Apache-2.0 | en | restart |
| `en-ner-15-percentage` | NAME_FINDER | same | 4.5 MiB | Apache-2.0 | en | restart |
| `en-ner-15-time` | NAME_FINDER | same | 4.5 MiB | Apache-2.0 | en | restart |
| `de-ud-gsd-sentence` | SENTENCE_DETECTOR | one OpenNLP UD 1.3 `.bin` | 14.7 KiB | Apache-2.0 | de | restart |
| `de-ud-gsd-tokens` | TOKENIZER | one OpenNLP UD 1.3 `.bin` | 511.8 KiB | Apache-2.0 | de | restart |
| `de-ud-gsd-pos` | POS_TAGGER | one OpenNLP UD 1.3 `.bin` | 1.2 MiB | Apache-2.0 | de | restart |
| `de-ud-gsd-lemmas` | LEMMATIZER | one OpenNLP UD 1.3 `.bin` | 834.9 KiB | Apache-2.0 | de | restart |
| `fr-ud-gsd-*` (4 entries) | SENTENCE_DETECTOR, TOKENIZER, POS_TAGGER, LEMMATIZER | one OpenNLP UD 1.3 `.bin` each | 3.0 MiB total | Apache-2.0 | fr | restart |
| `es-ud-gsd-*` (4 entries) | SENTENCE_DETECTOR, TOKENIZER, POS_TAGGER, LEMMATIZER | one OpenNLP UD 1.3 `.bin` each | 3.3 MiB total | Apache-2.0 | es | restart |

Pinned upstream revisions: HuggingFace commit sha for the five HF entries,
`ud-models-1.3-2.5.4` for the UD packs, `models-1.5` for the classic name
finders, `opennlp-grpc-gum-models-v1+gum-<sha>` for the GUM pair.

Only 4 of the 12 `ModelCatalogDescriptor` fields describe the artifact itself
(`byte_size`, `dimension`, `languages`, `description`); nothing states the file
format, the serving runtime, or the pipeline steps unlocked.

## Grouping applied by the front end

`groupCatalogPacks` (`src/model-data-workbench.ts:87`) folds the 12 UD entries
into 3 "language pack" cards. The 7 name finders, 2 GUM models, 3 static tables
and 2 teachers stay as 14 single cards, so the tab renders **17 cards** on this
node.
