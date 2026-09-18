# Analyze tab: model and backend gating

All FACT rows below were reproduced against the live demo at
`http://127.0.0.1:7172` on 2026-08-28 with read-only `POST /api/v1/analyze`
calls. Verbatim requests and responses are in `../reference/demo-errors.md`;
the demo's advertised capability set is in `../reference/demo-state.md`.

---

## 1. What the front end already knows before the user clicks

FACT. `discoverAnalysisCapabilities` (`analysis-config.ts:136-215`) merges two
discovery calls into three step sets:

| Set | How it is built | Meaning |
| --- | --- | --- |
| `supportedSteps` | `service-info.supportedSteps` intersected with `PIPELINE_ORDER` (`analysis-config.ts:210`) | the step exists in this server build |
| `configuredSteps` | union of every bundle's `supportedSteps`, plus the three model-free steps, plus subword and expand when `configuredResources` names a resource, plus geocode when NER is configured (`analysis-config.ts:144-190`) | the step has a model or a resource behind it |
| `maxSteps` | `supportedSteps` and `configuredSteps` (`analysis-config.ts:197`) | safe to request |

FACT. That knowledge already reaches the screen in exactly one place: the
feature checklist, which is hidden unless the preset is `Choose features`
(`analysis-controls.ts:260-262`). Each row gets `Ready`, `Needs model or data`,
or `Not in this server build` (`analysis-controls.ts:288-289`) and the checkbox
is disabled for anything not in `maxSteps` (`analysis-controls.ts:273`).

FACT. Consequence: through the normal path (`All available features`, the
default preset at `index.html:130`) an unconfigured step is **never requested**,
so the user never sees an error and never learns the feature exists. On the
demo, `All available features` silently means:

```
LANGUAGE_DETECT, NORMALIZE, SENTENCE_DETECT, TOKENIZE, POS_TAG, LEMMATIZE,
STEM, TERM_VECTOR, SENTIMENT, EMBED
```

and silently omits NER, geocoding, subword tokenization, lexical expansion,
document categorization, constituency parsing, and syntactic chunking. Seven of
seventeen advertised features are invisible unless the user happens to open
`Choose features`.

---

## 2. Reproduced failures, step by step

The FE will not send these today, but the endpoint is public, the docs point at
it (`index.html:107-108`), and `Server automatic` plus a hand-written profile
reaches it. These are the exact messages a client sees.

| Feature (UI label) | Step | What is required | HTTP + verbatim error text |
| --- | --- | --- | --- |
| `Named entities` | `PIPELINE_STEP_NER` | one or more name finder models; config key `model.name_finder.<entity_type>.path` | 404 `PIPELINE_STEP_NER requested but no name finder models are configured on this server; set model.name_finder.<entity_type>.path entries` |
| `Entity geocoding` | `PIPELINE_STEP_GEOCODE` | NER first, then a gazetteer | 412 `PIPELINE_STEP_GEOCODE requires PIPELINE_STEP_NER` |
| `Constituency parses` | `PIPELINE_STEP_PARSE` | a parser model; `model.parser.<id>.path` | 404 `PIPELINE_STEP_PARSE requested but no parser model is configured on this server; set model.parser.<id>.path` |
| `Syntactic chunks` | `PIPELINE_STEP_SYNTACTIC_CHUNK` | a chunker model; `model.chunker.<id>.path` | 404 `PIPELINE_STEP_SYNTACTIC_CHUNK requested but no chunker model is configured on this server; set model.chunker.<id>.path` |
| `Document categories` | `PIPELINE_STEP_DOC_CATEGORIZE` | doccat models; `model.doccat.<id>.path` | 404 `PIPELINE_STEP_DOC_CATEGORIZE requested but no document categorizer models are configured on this server; set model.doccat.<id>.path entries` |
| `Subword tokenization` | `PIPELINE_STEP_SUBWORD_TOKENIZE` | a subword model registered as `STANDARD_RESOURCE_SUBWORD_MODEL` | 404 `No subword model is configured on this server` |
| `Lexical expansion` | `PIPELINE_STEP_EXPAND` | a WordNet lexicon registered as `STANDARD_RESOURCE_WORDNET_LEXICON` | 404 `No WordNet lexicon is configured on this server` |
| `Sentence sentiment` | `PIPELINE_STEP_SENTIMENT` | a sentiment model | works on the demo (`sst2`, backend `cuda`); when absent, `AnalysisRequestValidator.java:404` emits `PIPELINE_STEP_SENTIMENT requested but no sentiment models are configured on this server` |
| `Document embeddings` | `PIPELINE_STEP_EMBED` | an embedder component in a bundle, or a trained static model | works on the demo (`minilm-gpu`, backend `cuda`, 384 dimensions); when absent, `AnalysisRequestValidator.java:889` emits `PIPELINE_STEP_EMBED requested but no embedding models are configured on this server` |
| `Language detection` | `PIPELINE_STEP_LANGUAGE_DETECT` | a langdetect model in a bundle | works on the demo |
| `Part-of-speech tags`, `Lemmas` | `POS_TAG`, `LEMMATIZE` | POS and lemmatizer models | work on the demo |
| `Stems`, `Offset-aware normalization`, `Term vectors` | `STEM`, `NORMALIZE`, `TERM_VECTOR` | none; treated as model-free (`analysis-config.ts:63-67`) | always available, except `STEM`, which the FE gates on at least one bundle language being known (`analysis-config.ts:179`) |

FACT. The error text is authored in
`opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/processor/basic/AnalysisRequestValidator.java`
(lines 365, 404, 784, 806, 889, 940 among others) and reaches the browser
untouched: `GrpcJsonApi.java:194-203` copies `status.getDescription()` into the
JSON body, `GrpcHttpStatusMapper.java:35-50` picks the HTTP code, and
`api.ts:546-560` (`responseError`) lifts `body.message` into the thrown `Error`.
`main.ts:566` then prints it in `#form-status`.

FACT. This means a user who does trip one of these sees a message telling them
to edit a server-side properties file. There is no button, no link, and no
mention of the Models & data tab.

---

## 3. Second-order gating: features that depend on a model indirectly

| Surface | Silently degraded when | What the user sees today |
| --- | --- | --- |
| `Embedding model` select (`index.html:139`) | no bundle carries a `COMPONENT_TYPE_EMBEDDER` and no static model has been trained | the select is disabled showing `No embedding model configured` (`index.html:141`, `analysis-controls.ts:202-209`). No route to the Models & data catalog or to the Trainer. |
| `Add to server workspace` (`index.html:252`) | the response has no chunk group carrying an `embeddingModelIds` entry (`semantic-workbench.ts:635-660`) | the button stays disabled (`semantic-workbench.ts:612`). No tooltip, no reason given. |
| Heatmap, `Query similarity` mode | same condition | `#heatmap-status` reads `Enable document embeddings and at least one chunk strategy, then analyze again.` (`semantic-workbench.ts:537`). The query box and button are disabled (`semantic-workbench.ts:612-620`). This is the one well-behaved brown-out on the tab. |
| Heatmap, `Sentiment` mode | no sentiment layer with positional scores | `#heatmap-status` reads `No typed sentiment layer was returned. Enable Sentiment and install its model data first.` (`semantic-workbench.ts:535`) and the canvas reads `This document has no typed sentiment layer with positional scores.` (`semantic-workbench.ts:461`). Good text, but "install its model data" names no destination. |
| `Language pipeline` select (`index.html:146`) | no bundle id starts with `pipeline-` (`analysis-config.ts:191-198`) | the select is disabled with only `Automatic (route by detected language)`. On the demo this is always the case, so the control is permanently dead. No explanation. |
| `Part-of-speech tag set` select (`index.html:154`) | POS is not in the selected step set | the select stays enabled and settable; `analysis-config.ts:252` simply drops `posTagFormat` from the request. A user can pick `Penn Treebank`, run an analysis, and get native tags with no notice. |
| `Normalization X-ray` checkbox (`index.html:220`) | never gated | it forces `PIPELINE_STEP_NORMALIZE` into the profile (`analysis-config.ts:93-105`), which is model-free, so it always works. No gating needed. |
| Batch analyze | inherits whatever the composer built (`main.ts:519`) | a per-document failure renders as `Document 2: NOT_FOUND: <message>` (`batch-analysis.ts:85-90`), which is the clearest gating message on the tab. |

---

## 4. What discovery gives the front end, and what it does not

### 4.1 FACT: `installed-models` is empty on the demo

`GET /api/v1/installed-models` returns `{}` and `GET /api/v1/static-models`
returns `{}` (`../reference/demo-state.md`). So the Models & data tab cannot
tell the user which catalog entries are already present either. The FE's only
usable signal about *what is loaded* is `GET /api/v1/model-bundles`.

### 4.2 FACT: `service-info` has no `configuredResources` on this build

`analysis-config.ts:184-190` looks for `service.configuredResources` to find the
subword model id and the WordNet lexicon id. The demo's `service-info` payload
has no such field, so both come back `undefined` and both steps drop out of
`configuredSteps` even though `PIPELINE_STEP_SUBWORD_TOKENIZE` and
`PIPELINE_STEP_EXPAND` are in `supportedSteps`. The service *does* have the
capability to report it: `OpenNlpAnalysisServiceImplTest` has a test named
`serviceInfoAdvertisesConfiguredNonModelResources`. It is simply not configured
here. The FE's handling is correct; the demo image is the gap.

### 4.3 FACT: catalog coverage does not match the gaps

`GET /api/v1/model-catalog` returns 26 entries
(`../reference/demo-state.md`). Cross-referenced against the seven unconfigured
steps:

| Unconfigured step | Catalog can fix it? | Entries |
| --- | --- | --- |
| `NER` | **yes** | 7 `MODEL_ARTIFACT_ROLE_NAME_FINDER`: `en-ner-15-{person,location,organization,date,time,money,percentage}` |
| `PARSE` | **yes** | 1 `MODEL_ARTIFACT_ROLE_PARSER`: `gum-cc-by-4-parser` |
| `SYNTACTIC_CHUNK` | **yes** | 1 `MODEL_ARTIFACT_ROLE_CHUNKER`: `gum-cc-by-4-chunker` |
| `GEOCODE` | partly | needs NER installed first, then a gazetteer, which is not in the catalog |
| `DOC_CATEGORIZE` | no | no doccat role in the catalog |
| `SUBWORD_TOKENIZE` | no | no subword role in the catalog |
| `EXPAND` | no | no WordNet role in the catalog |

So three of the seven can be repaired entirely from the Models & data tab, one
needs a two-step fix, and three need server configuration. A brown-out design
has to say which of those three cases applies, not just "needs model or data".

### 4.4 FACT: the destination tab has a raw identifier on screen

`model-data-workbench.ts` `roleLabel` maps role slugs to display names but has
no entry for `name-finder`, so the seven NER catalog cards render the badge
`name-finder` verbatim. Fixing the redirect without fixing this sends the user
to a card labelled with an internal slug. P2, one line.

---

## 5. Proposed brown-out and redirect design

OPINION. Everything below is a proposal, not current behaviour.

### 5.1 Surface the gating where the user already looks (P1)

Today the only place a user learns a feature is unavailable is a checklist
hidden behind a non-default preset. Move the signal to the always-visible
`Enabled features` chip list (`index.html:186`, rendered at
`analysis-controls.ts:232-259`):

- Keep the current chips for enabled features.
- Append a muted, non-interactive group headed `Not available on this server`
  carrying one chip per step in `supportedSteps` minus `maxSteps`. Style them as
  browned out: reduced opacity, dotted border, no accent colour.
- Make each browned-out chip a button that opens the same explanation panel as
  5.2.

The data needed already exists in `AnalysisCapabilities`; no new call.

### 5.2 One explanation panel with three outcomes (P1)

When a browned-out feature is selected, show a small panel with a fixed shape:

```
Named entities is not available on this server.
Reason:  no name finder model is loaded.
Fix:     install one of 7 English name finder models.
         [ Open Models & data ]        <- data-workbench-jump="models"
```

The three outcomes, keyed off the catalog scan in 4.3:

| Case | Fix line | Action |
| --- | --- | --- |
| catalog has a matching role | `Install <displayName> from the model catalog.` | a `data-workbench-jump="models"` button that also scrolls the catalog to that card. |
| catalog has no matching role, but the step is in `supportedSteps` | `This server build supports it, but no model or resource is configured. Ask the operator to set <config key>.` | no jump; show the config key verbatim, taken from a static map keyed by step. |
| the step is not in `supportedSteps` | `Not included in this server build.` | no jump. |

The config keys are already stated by the service in its own error text
(section 2), so the static map is a transcription, not an invention. It can be
regression-tested against those strings; see `test-coverage.md` section 5.

### 5.3 Make the redirect land somewhere useful (P1)

`WorkbenchNavigation` already handles `data-workbench-jump`
(`workbench-navigation.ts:40`), and `ModelDataWorkbench.configure` already
renders a per-step readiness list with `Ready` / `Needs model or data` /
`Not in this build` (`model-data-workbench.ts:241-266`). Extend the jump to
carry the step, for example
`data-workbench-jump="models" data-workbench-focus="PIPELINE_STEP_NER"`, and
have the Models & data tab scroll to and outline the matching feature card and
the catalog cards whose role serves that step. That closes the loop without any
new API.

### 5.4 Do not let the tab lie about the tag set (P2)

`Part-of-speech tag set` is settable while POS is not in the selected step set,
and the value is silently dropped (`analysis-config.ts:252`). Disable the select
with the help text `Select Part-of-speech tags to convert the tag set.` when
POS is absent. Same treatment for `Language pipeline`, which is permanently
disabled on the demo with no explanation: give it the help text
`No language pipelines are configured on this server.` instead of leaving a
dead control.

### 5.5 Tell the user what the preset actually did (P2)

After every analysis, the response's `diagnostics` array names every step that
was skipped and why, for example
`PIPELINE_STEP_NER skipped (not requested by profile)`
(`../reference/demo-state.md`). Nothing in the tab reads that array except
`routingDiagnostic` (`main.ts:1122-1131`), which looks only for the string
`Classic pipeline `. Rendering the skipped-step diagnostics as a
`Not run in this analysis` line under the result summary would explain the
seven missing features at exactly the moment the user is looking for them, and
is a pure read of data already on the wire.

---

## Questions for the lead

1. Should the demo image configure `configuredResources` so subword
   tokenization and lexical expansion light up? Both steps are advertised in
   `supportedSteps` and both have service tests, but no demo user can reach
   them today (4.2).
2. Do you want the browned-out chips to be *clickable but refused* (they send
   the request and show the server error) or *never sendable* (current
   behaviour)? Clickable is more honest about the server contract; never
   sendable is friendlier. My proposal above keeps them unsendable and explains
   instead.
3. The static map of step to config key in 5.2 duplicates strings owned by
   `AnalysisRequestValidator`. Would you rather the service returned the config
   key as a structured field on the error, so the FE has nothing to duplicate?
