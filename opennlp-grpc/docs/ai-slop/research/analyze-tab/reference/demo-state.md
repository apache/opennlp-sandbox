Source: live demo instance http://127.0.0.1:7172 (read-only calls)
Fetched: 2026-08-28

# What the demo advertises

## GET /api/v1/service-info

```json
{
  "opennlpVersion": "3.0.0-SNAPSHOT",
  "apiVersion": "v1",
  "availableProfileIds": ["en-sentiment", "en-embed", "en-basic"],
  "supportedSteps": [
    "PIPELINE_STEP_LANGUAGE_DETECT", "PIPELINE_STEP_NORMALIZE",
    "PIPELINE_STEP_SENTENCE_DETECT", "PIPELINE_STEP_TOKENIZE",
    "PIPELINE_STEP_SUBWORD_TOKENIZE", "PIPELINE_STEP_NER",
    "PIPELINE_STEP_GEOCODE", "PIPELINE_STEP_POS_TAG",
    "PIPELINE_STEP_LEMMATIZE", "PIPELINE_STEP_STEM",
    "PIPELINE_STEP_TERM_VECTOR", "PIPELINE_STEP_EXPAND",
    "PIPELINE_STEP_DOC_CATEGORIZE", "PIPELINE_STEP_SENTIMENT",
    "PIPELINE_STEP_PARSE", "PIPELINE_STEP_SYNTACTIC_CHUNK",
    "PIPELINE_STEP_EMBED", "PIPELINE_STEP_CHUNK"
  ],
  "supportedLayers": [ ...22 STANDARD_LAYER_* values... ],
  "maxTextBytes": 1048576,
  "serviceVersion": "3.0.0-SNAPSHOT"
}
```

Note the absent field: there is no `configuredResources` array on this instance.
`analysis-config.ts:171` reads `service.configuredResources` to discover the
subword model and the WordNet lexicon, so both come back undefined here.

## GET /api/v1/model-bundles

```
en-sentiment  langs=[en]  steps=[SENTENCE_DETECT, TOKENIZE, SENTIMENT]
    opennlp-models-sentdetect-en  COMPONENT_TYPE_SENTENCE_DETECTOR  opennlp-me
    opennlp-models-tokenizer-en   COMPONENT_TYPE_TOKENIZER          opennlp-me
    sst2                          COMPONENT_TYPE_SENTIMENT          cuda

en-basic      langs=[en]  steps=[LANGUAGE_DETECT, SENTENCE_DETECT, TOKENIZE,
                                 POS_TAG, LEMMATIZE, EMBED]
    opennlp-models-langdetect     COMPONENT_TYPE_LANGUAGE_DETECTOR  opennlp-me
    opennlp-models-sentdetect-en  COMPONENT_TYPE_SENTENCE_DETECTOR  opennlp-me
    opennlp-models-tokenizer-en   COMPONENT_TYPE_TOKENIZER          opennlp-me
    opennlp-models-pos-en         COMPONENT_TYPE_POS_TAGGER         opennlp-me
    opennlp-models-lemmatizer-en  COMPONENT_TYPE_LEMMATIZER         opennlp-me
    minilm-gpu                    COMPONENT_TYPE_EMBEDDER  cuda  384 dimensions
```

`availableProfileIds` lists three profiles; `bundles` lists two bundles. The id
`en-basic` therefore names both a profile and a bundle, and the two are not the
same thing (see `demo-errors.md`).

## GET /api/v1/installed-models

```json
{}
```

## GET /api/v1/static-models

```json
{}
```

## GET /api/v1/model-catalog

26 entries. Roles present:

```
7  MODEL_ARTIFACT_ROLE_NAME_FINDER      en-ner-15-{date,location,money,organization,percentage,person,time}
3  MODEL_ARTIFACT_ROLE_SENTENCE_DETECTOR
3  MODEL_ARTIFACT_ROLE_TOKENIZER
3  MODEL_ARTIFACT_ROLE_POS_TAGGER
3  MODEL_ARTIFACT_ROLE_LEMMATIZER
3  MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING
2  MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER
1  MODEL_ARTIFACT_ROLE_CHUNKER          gum-cc-by-4-chunker
1  MODEL_ARTIFACT_ROLE_PARSER           gum-cc-by-4-parser
```

Roles absent from the catalog: document categorizer, subword model, WordNet
lexicon, geocoding gazetteer, sentiment. Those steps cannot be fixed by an
install from the Models & data tab on this build.

# What one "All available features" analysis returns

Derived set for this demo, in `PIPELINE_ORDER` (`analysis-config.ts:23-41`):

```
LANGUAGE_DETECT, NORMALIZE, SENTENCE_DETECT, TOKENIZE, POS_TAG, LEMMATIZE,
STEM, TERM_VECTOR, SENTIMENT, EMBED
```

Returned layers for a two-sentence document (id, count, scope):

```
opennlp:sentences            2   POSITIONAL
opennlp:tokens              16   POSITIONAL
opennlp:pos                 16   POSITIONAL
opennlp:lemmas              16   POSITIONAL
opennlp:stopwords            6   POSITIONAL
opennlp:terms:ACCENT_FOLD   16   POSITIONAL
opennlp:terms:CASE_FOLD     16   POSITIONAL
opennlp:terms:NFC           16   POSITIONAL
opennlp:terms:STEM          16   POSITIONAL
opennlp:sentiment            2   POSITIONAL
opennlp:language             5   DOCUMENT
opennlp:embeddings           3   POSITIONAL
opennlp:analytics            1   DOCUMENT
opennlp:normalization        1   DOCUMENT
opennlp:chunk-groups         1   DOCUMENT
opennlp:stems               16   POSITIONAL
opennlp:term-vectors        15   DOCUMENT
```

# Bundled novels, measured end to end

| Sample | Text chars | Response bytes | Wall time | Annotations | Layers |
| --- | --- | --- | --- | --- | --- |
| Short sample (`main.ts:132`) | 200 | ~40 KB | < 1 s | ~200 | 16 |
| Load Alice novel | 144,569 | 75,370,306 | 5.1 s | 286,938 (without embeddings) | 16 |
| Load Pride and Prejudice | 685,954 | 322,976,205 | 20.0 s | not counted | 16 |

All three runs used the exact request the Analyze tab builds for
"All available features" plus the `minilm-gpu` embedding model plus
"Sentence chunks".
