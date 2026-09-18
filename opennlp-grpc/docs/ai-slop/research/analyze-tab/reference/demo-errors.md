Source: live demo instance http://127.0.0.1:7172 (read-only calls)
Fetched: 2026-08-28

Every response below is verbatim. The request body in each case is the shape the
Analyze tab builds, reduced to the step under test.

# Steps that are advertised but not configured

Request template:

```
POST /api/v1/analyze
{"document":{"rawText":"Barack Obama visited Paris in March."},
 "options":{"offsetEncoding":"OFFSET_ENCODING_UTF16_CODE_UNIT"},
 "profile":{"steps":["PIPELINE_STEP_SENTENCE_DETECT","PIPELINE_STEP_TOKENIZE",
                     "PIPELINE_STEP_POS_TAG","<STEP UNDER TEST>"]}}
```

| Step | HTTP | Body |
| --- | --- | --- |
| `PIPELINE_STEP_NER` | 404 | `{"code":"NOT_FOUND","message":"PIPELINE_STEP_NER requested but no name finder models are configured on this server; set model.name_finder.<entity_type>.path entries"}` |
| `PIPELINE_STEP_PARSE` | 404 | `{"code":"NOT_FOUND","message":"PIPELINE_STEP_PARSE requested but no parser model is configured on this server; set model.parser.<id>.path"}` |
| `PIPELINE_STEP_SUBWORD_TOKENIZE` | 404 | `{"code":"NOT_FOUND","message":"No subword model is configured on this server"}` |
| `PIPELINE_STEP_DOC_CATEGORIZE` | 404 | `{"code":"NOT_FOUND","message":"PIPELINE_STEP_DOC_CATEGORIZE requested but no document categorizer models are configured on this server; set model.doccat.<id>.path entries"}` |
| `PIPELINE_STEP_SYNTACTIC_CHUNK` | 404 | `{"code":"NOT_FOUND","message":"PIPELINE_STEP_SYNTACTIC_CHUNK requested but no chunker model is configured on this server; set model.chunker.<id>.path"}` |
| `PIPELINE_STEP_EXPAND` | 404 | `{"code":"NOT_FOUND","message":"No WordNet lexicon is configured on this server"}` |
| `PIPELINE_STEP_GEOCODE` | 412 | `{"code":"FAILED_PRECONDITION","message":"PIPELINE_STEP_GEOCODE requires PIPELINE_STEP_NER"}` |

# Named profiles: what each one actually runs

```
POST /api/v1/analyze  {"document":{"rawText":"I loved Paris in March."},"profileId":"<id>"}
```

| profileId | Steps that produced a diagnostic |
| --- | --- |
| `en-basic` | SENTENCE_DETECT, TOKENIZE |
| `en-embed` | SENTENCE_DETECT, TOKENIZE, EMBED (`model 'minilm-gpu'`) |
| `en-sentiment` | SENTENCE_DETECT, TOKENIZE, SENTIMENT (`model 'sst2'`) |
| no profile at all (Server automatic) | SENTENCE_DETECT, TOKENIZE |

The `en-basic` **bundle** advertises LANGUAGE_DETECT, POS_TAG, LEMMATIZE and
EMBED; the `en-basic` **profile** runs none of them.

# Input validation

| Case | HTTP | Body |
| --- | --- | --- |
| empty `rawText` | 400 | `{"code":"INVALID_ARGUMENT","message":"document.raw_text is required"}` |
| 2 MiB `rawText` | 400 | `{"code":"INVALID_ARGUMENT","message":"document.raw_text exceeds server max_text_bytes (1048576)"}` |
| unknown step enum | 400 | `{"code":"INVALID_ARGUMENT","message":"Malformed protobuf JSON request: Invalid enum value: PIPELINE_STEP_BOGUS for enum type: org.apache.opennlp.grpc.v1.PipelineStep"}` |
| `content-type: text/plain` | 415 | `{"code":"INVALID_ARGUMENT","message":"Content-Type must be application/json"}` |

# Saved-response round trip

| Case | HTTP | Body |
| --- | --- | --- |
| `POST /api/v1/response/decode` with junk bytes | 400 | `{"code":"INVALID_ARGUMENT","message":"Malformed protobuf response bytes: Protocol message tag had invalid wire type."}` |
| `POST /api/v1/response/decode` with a zero-byte body | **200** | `{}` |
| `POST /api/v1/response/encode` with `{"nope":1}` | 400 | `{"code":"INVALID_ARGUMENT","message":"Malformed protobuf JSON request: Cannot find field: nope in message org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse"}` |
| body over `--max-request-bytes` | 413 | `{"code":"RESOURCE_EXHAUSTED","message":"HTTP request body exceeds 104857600 bytes"}` (text at `OpenNlpGrpcWebServer.java:219-220`) |

# Batch stream

```
POST /api/v1/analyze-stream
[{"configuration":{}},{"document":{"sequence":"1","document":{"rawText":""}}}]
```

HTTP 200, one NDJSON line:

```json
{"sequence":"1","error":{"code":"GRPC_STATUS_CODE_INVALID_ARGUMENT","message":"document.raw_text is required"}}
```

# Sentiment label vocabulary of the demo's `sst2` model

```
"This was the worst experience of my entire life."  -> label "1_star",  score 0.884
"I hated every second of it."                       -> label "1_star",  score 0.554
"But the dessert was lovely."                       -> label "3_stars", score 0.424
"Barack Obama visited Paris in March."              -> label "5_stars", score 0.282
```

The labels are star buckets, not `positive`/`negative`/`neutral`. See
`findings/error-states-and-links.md` for what that does to the Sentiment
heatmap.

# Offset encoding echo

The service echoes the requested encoding on `document.offsetEncoding`:

- request `OFFSET_ENCODING_UTF16_CODE_UNIT` -> `OFFSET_ENCODING_UTF16_CODE_UNIT`
- request `OFFSET_ENCODING_UNICODE_CODE_POINT` -> `OFFSET_ENCODING_UNICODE_CODE_POINT`
- request omitted -> `OFFSET_ENCODING_UTF8_BYTE`
