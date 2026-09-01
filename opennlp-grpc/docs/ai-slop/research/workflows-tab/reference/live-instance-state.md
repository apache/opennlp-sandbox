# Live demo instance state

Source: `http://127.0.0.1:7172`, read-only `GET` requests, captured 2026-08-28.
No write endpoint was called.

## GET /api/v1/teachers

```json
{"maxPcaDims":512,"maxConcurrentTrainings":1}
```

There is no `teachers` array and no `writesEnabled` field. Protobuf JSON omits default values, so
`writesEnabled` is `false` and the teacher list is empty. `readTeachers` in
`src/vocabulary-trainer.ts:414-430` therefore returns `{ teachers: [], writesEnabled: false }`,
which forces `CorpusWorkflowWorkbench.#ready = false` at `src/corpus-workflow.ts:153`.

## GET /api/v1/dictionaries

```json
{}
```

## GET /api/v1/static-models

```json
{}
```

## GET /api/v1/search-indexes

```json
{}
```

## GET /api/v1/index-aliases

```json
{}
```

## GET /api/v1/collections

```json
{}
```

## GET /api/v1/search-providers

```json
{"providers":[
 {"instanceId":"flat_float","providerId":"flat_float",
  "capabilities":["SEARCH_PROVIDER_CAPABILITY_VECTOR","SEARCH_PROVIDER_CAPABILITY_LIVE"],
  "standard":"STANDARD_SEARCH_PROVIDER_FLAT_FLOAT"},
 {"instanceId":"terms","providerId":"terms",
  "capabilities":["SEARCH_PROVIDER_CAPABILITY_KEYWORD","SEARCH_PROVIDER_CAPABILITY_LIVE"]},
 {"instanceId":"turbo_quant","providerId":"turbo_quant",
  "capabilities":["SEARCH_PROVIDER_CAPABILITY_VECTOR","SEARCH_PROVIDER_CAPABILITY_LIVE",
                  "SEARCH_PROVIDER_CAPABILITY_BUNDLE","SEARCH_PROVIDER_CAPABILITY_PERSISTENT"],
  "standard":"STANDARD_SEARCH_PROVIDER_TURBO_QUANT"}]}
```

Only `turbo_quant` declares `SEARCH_PROVIDER_CAPABILITY_PERSISTENT`. `flat_float`, which the
Workflows tab picks first at `src/corpus-workflow.ts:421-432`, does not.

## GET /api/v1/service-info (abridged)

```json
{"opennlpVersion":"3.0.0-SNAPSHOT","apiVersion":"v1",
 "availableProfileIds":["en-sentiment","en-embed","en-basic"],
 "supportedSteps":["PIPELINE_STEP_LANGUAGE_DETECT", "...", "PIPELINE_STEP_EMBED",
                   "PIPELINE_STEP_CHUNK"],
 "maxTextBytes":1048576}
```

`PIPELINE_STEP_EMBED` is advertised, so `buildAnalysisRequest` in `src/analysis-config.ts:245-248`
would attach `embeddingModelId` if a model existed. None does on this instance.
