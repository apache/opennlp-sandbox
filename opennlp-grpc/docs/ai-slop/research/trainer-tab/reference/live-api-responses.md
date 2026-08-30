# Live trainer endpoints on the demo instance

Source: http://127.0.0.1:7172 (local demo server, read-only GET calls only)
Fetched: 2026-08-28

All bodies are protobuf JSON, so proto3 default values are omitted from the wire.
An absent `writesEnabled` or `teachers` field therefore means `false` / empty,
which is exactly what the front end reads.

## GET /api/v1/teachers

```json
{"maxPcaDims":512,"maxConcurrentTrainings":1}
```

No `teachers` array and no `writesEnabled`. Read by
`readTeachers` (opennlp-grpc-webapp-default/src/vocabulary-trainer.ts:414) as
`{ teachers: [], writesEnabled: false }`.

## GET /api/v1/static-models

```json
{}
```

No `models` array and no `writesEnabled`. Read by `readStaticModels`
(opennlp-grpc-webapp-default/src/vocabulary-trainer.ts:491) as `[]`.

## GET /api/v1/dictionaries

```json
{}
```

Empty. The trainer tab never calls this endpoint; only the Workflows tab does
(opennlp-grpc-webapp-default/src/main.ts:325).

## GET /api/v1/dictionary-formats

```json
{"formats":[
 {"format":{"standard":"STANDARD_DICTIONARY_FORMAT_HEADWORD_DEFINITION_TSV"},
  "displayName":"Headword and definition TSV",
  "mediaTypes":["text/tab-separated-values"],
  "supportsDefinitions":true,"supportsMultiWordEntries":true},
 {"format":{"standard":"STANDARD_DICTIONARY_FORMAT_HEADWORD_LINES"},
  "displayName":"One headword per line",
  "mediaTypes":["text/plain"],"supportsMultiWordEntries":true},
 {"format":{"standard":"STANDARD_DICTIONARY_FORMAT_OPENNLP_XML"},
  "displayName":"OpenNLP dictionary XML",
  "mediaTypes":["application/xml","text/xml"],"supportsMultiWordEntries":true}],
 "maxDictionaryBytes":67108864,"maxDictionaryEntries":1000000,
 "maxCorpusDocuments":100000,"maxCorpusBytes":104857600,
 "maxVocabularyTerms":1000000,"maxConcurrentWrites":1}
```

Again no `writesEnabled`, so writes are disabled. The three format labels do
render in the Format selector even though nothing can be imported.

## GET /api/v1/installed-models

```json
{}
```

No installed models and no `installsEnabled`, so the Models and data tab cannot
install a teacher on this instance either.

## GET /api/v1/model-catalog (summarised)

26 entries, `installsEnabled` absent (false). Roles present:

| catalogId | role | dimension | license |
| --- | --- | --- | --- |
| all-minilm-l6-v2-teacher | MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER | (0) | Apache-2.0 |
| paraphrase-multilingual-minilm-l12-v2-teacher | MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER | (0) | Apache-2.0 |
| potion-base-8m | MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING | 256 | MIT |
| potion-retrieval-32m | MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING | 512 | MIT |
| potion-multilingual-128m | MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING | 256 | MIT |
| gum-cc-by-4-chunker / gum-cc-by-4-parser | CHUNKER / PARSER | (0) | CC-BY-4.0 |
| en-ner-15-{person,location,organization,date,money,percentage,time} | NAME_FINDER | (0) | Apache-2.0 |
| {de,es,fr}-ud-gsd-{sentence,tokens,pos,lemmas} | SENTENCE_DETECTOR / TOKENIZER / POS_TAGGER / LEMMATIZER | (0) | Apache-2.0 |

## GET /api/v1/service-info (excerpt)

`availableProfileIds`: `en-sentiment`, `en-embed`, `en-basic`.
`PIPELINE_STEP_EMBED` and `STANDARD_LAYER_EMBEDDINGS` are supported, so the
Analyze tab can consume an embedding model id when one exists.

## Resulting UI state on this instance

`VocabularyTrainerWorkbench.initialize` computes
`writesEnabled = formats.writesEnabled && teachers.writesEnabled` = `false`
(opennlp-grpc-webapp-default/src/vocabulary-trainer.ts:148), so the status line
reads exactly:

> Training is disabled: the server has no vocabulary artifact root or no teachers.

the Teacher selector reads exactly `No teachers configured`, the trained model
list reads exactly `No trained models yet.`, and Import, Learn, Download TSV and
Train are all disabled.
