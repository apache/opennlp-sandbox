# What a model unlocks, and the tags that should say so

Scope: the "Models & data" tab, section `model-data-workbench`
(`opennlp-grpc-webapp-default/index.html:790`), rendered by
`opennlp-grpc-webapp-default/src/model-data-workbench.ts`.

Owner's asks addressed here: "The model page should show what features it
unlocks if you load that model" and "'Models and data' may just need tags to say
which feature(s) it unlocks and maybe the family (onnx, etc)".

## FACT: what a card shows today

One catalog card renders exactly four things
(`src/model-data-workbench.ts:280-314`):

1. `displayName`, for example "Potion Base 8M".
2. One role chip from `roleLabel` (`src/model-data-workbench.ts:646`). The nine
   possible strings are "Ready-to-serve static table", "Training teacher",
   "Constituency parser", "Syntactic chunker", "Sentence detector",
   "Tokenizer", "POS tagger", "Lemmatizer", and, for
   `MODEL_ARTIFACT_ROLE_NAME_FINDER`, the raw enum-derived fallback
   `"name-finder"` (the `labels` map at `src/model-data-workbench.ts:653` has no
   `name-finder` key, so `labels[role] ?? role` returns the lowercase hyphenated
   role string).
3. The `description` sentence from the server.
4. One facts line: `"29.0 MiB · MIT · 256 dimensions · en"`
   (`src/model-data-workbench.ts:298`).

A language-pack card adds the chip `"Classic pipeline"` and a member list of
`"<roleLabel> · <size>"` rows (`src/model-data-workbench.ts:369,385`).

Nothing on any card names a pipeline step, a tab, a file format, or a runtime.
The word "ONNX" appears in the tab only inside two server-written `description`
strings; "safetensors", "maxent", "perceptron", and "WordPiece" appear nowhere.

The only step-level information on the tab is the separate "Pipeline readiness"
list (`index.html:819`), whose rows read `"Ready"`,
`"Needs model or data"`, or `"Not in this build"`
(`src/model-data-workbench.ts:251`). Those rows are not linked to catalog cards
in either direction.

**P1.** A user who reads "Named entities: Needs model or data" and then scrolls
to a card labelled "name-finder" has to know that `en-ner-15-person` is the
thing that turns that row green. Nothing in the UI says so.

## FACT: the actual unlock chain

Installation writes into one of two places, decided by
`CatalogModelStore.requiresRestart`
(`opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/training/CatalogModelStore.java:588`):

- **Immediate roles** are registered in-process by `activate`
  (`CatalogModelStore.java:377`): `STATIC_EMBEDDING` joins the embedding
  registry under its `model_id`, `DISTILLATION_TEACHER` joins the training
  store's teacher list.
- **Restart roles** publish a configuration key at the next start through
  `CatalogModelBootstrap.restartConfigurationKey`
  (`CatalogModelBootstrap.java:156`).

| `ModelArtifactRole` | Runtime key or registry | Feature the user gains (FE label) | Tab where it shows up | Activation |
| --- | --- | --- | --- | --- |
| `STATIC_EMBEDDING` | embedding registry, id = `model_id` | "Document embeddings" (`PIPELINE_STEP_EMBED`) plus chunk embeddings (`PIPELINE_STEP_CHUNK`) and a search vector space | Analyze embedding select, Workflows, Workspace search, Corpus search, Lifecycle reindex | immediate |
| `DISTILLATION_TEACHER` | `trainingStore.registerCatalogTeacher` | Trainer step 3 "Teacher" select becomes non-empty | Trainer | immediate |
| `PARSER` | `model.parser.<model_id>.path` | "Constituency parses" (`PIPELINE_STEP_PARSE`), bundle `en-parse` | Analyze | restart |
| `CHUNKER` | `model.chunker.<model_id>.path` | "Syntactic chunks" (`PIPELINE_STEP_SYNTACTIC_CHUNK`), bundle `en-chunk`; also requires POS tagging | Analyze | restart |
| `NAME_FINDER` | `model.name_finder.<model_id>.path` | "Named entities" for that one entity type, bundle `en-ner`, and "Entity geocoding" as a follow-on | Analyze | restart |
| `SENTENCE_DETECTOR` | `model.pipeline.<lang>.sentence_detector.path` | "Sentence detection" for `<lang>`, bundle `pipeline-<lang>` | Analyze | restart |
| `TOKENIZER` | `model.pipeline.<lang>.tokenizer.path` | "Tokenization" for `<lang>` | Analyze | restart |
| `POS_TAGGER` | `model.pipeline.<lang>.pos_tagger.path` | "Part-of-speech tags" for `<lang>` | Analyze | restart |
| `LEMMATIZER` | `model.pipeline.<lang>.lemmatizer.path` | "Lemmas" for `<lang>` | Analyze | restart |

Two derived unlocks are easy to miss and worth stating on the card:

- `PIPELINE_STEP_GEOCODE` is switched on purely because NER is
  (`src/analysis-config.ts:192`: `if (configured.has("PIPELINE_STEP_NER") ...)`).
  So a name finder install unlocks two rows, not one.
- `PIPELINE_STEP_STEM` needs at least one bundle language
  (`src/analysis-config.ts:177`), so a first language pack for a new language
  also adds stemming for it.

## FACT: features the catalog cannot fix

Seven rows read "Needs model or data" on the live node. The catalog can only
resolve four of them.

| Pipeline readiness row | Required resource | In the catalog? |
| --- | --- | --- |
| "Named entities" | `model.name_finder.<type>.path` | yes, 7 entries |
| "Entity geocoding" | follows NER | yes, indirectly |
| "Constituency parses" | `model.parser.<id>.path` | yes, `gum-cc-by-4-parser` |
| "Syntactic chunks" | `model.chunker.<id>.path` | yes, `gum-cc-by-4-chunker` |
| "Subword tokenization" | `model.subword.<id>.path` (`SubwordRegistry.java:45`) and `STANDARD_RESOURCE_SUBWORD_MODEL` in `service-info` | **no role exists** |
| "Lexical expansion" | `model.wordnet.<id>.path` (`WordNetRegistry.java:46`) | **no role exists** |
| "Document categories" | `model.doccat.<id>.path` (`ClassicDocCategorizerBackendFactory.java:49`) | **no role exists** |

Sentiment is in the same bucket: `model.sentiment.*` has no
`ModelArtifactRole` either, it is only reachable through a preloaded bundle
(here the `cuda`-backed `sst2` model in `en-sentiment`).

**P1 (OPINION).** A "Needs model or data" row with no catalog answer is a dead
end. Those rows should say so out loud, for example
`"Needs an operator-provided resource (model.subword.<id>.path); the catalog
does not offer one"`, instead of implying the catalog can help.

## OPINION: the tag vocabulary

Six tag dimensions, each with an industry precedent. Format is
`Group: value`, rendered as chips under the card title.

| Group | Example values | Precedent |
| --- | --- | --- |
| `Unlocks` | `Named entities`, `Constituency parses`, `Document embeddings`, `Trainer teacher` | HuggingFace `pipeline_tag` (one primary task per model card), https://huggingface.co/docs/hub/model-cards |
| `Format` | `ONNX`, `Safetensors`, `OpenNLP .bin`, `WordPiece vocab`, `SentencePiece / Unigram` | MLflow `MLmodel` `flavors` block, https://mlflow.org/docs/latest/models.html ; ONNX Model Zoo `ONNX_HUB_MANIFEST.json` `opset_version` |
| `Runtime` | `OpenNLP maxent`, `ONNX Runtime`, `ONNX Runtime CUDA` | Triton `config.pbtxt` `backend` / `platform`, https://github.com/triton-inference-server/server ; this repo already carries the same idea as `backendId` (`opennlp-me`, `cuda`) in `/api/v1/model-bundles` |
| `Activation` | `Serves immediately`, `Restart required` | MLflow model-version `stage`, https://mlflow.org/docs/latest/model-registry.html |
| `Language` | `English`, `German`, `Multilingual` | HuggingFace card `language:` key |
| `License` | `Apache-2.0`, `MIT`, `CC-BY-4.0` | SPDX identifiers, already used verbatim by `StandardModelCatalog.java:41-46` |

Embedding entries additionally deserve a `256d` / `512d` chip; the value is
already in `dimension` and today it is buried mid-sentence in the facts line.

Concrete renaming, current string to proposed string:

| Current | Proposed | Why |
| --- | --- | --- |
| `"name-finder"` (raw role fallback, `src/model-data-workbench.ts:661`) | `"Name finder"` chip plus an `Unlocks: Named entities` chip | The raw hyphenated enum name leaks; it is the only role with no label entry. |
| `"Ready-to-serve static table"` | `"Static embedding table"` plus `Activation: Serves immediately` | "Ready-to-serve" is doing the activation job inside the role name; splitting them lets both be tags. spaCy's `meta.json` separates `pipeline` from `vectors`. |
| `"Training teacher"` | `"Distillation teacher"` plus `Unlocks: Trainer` | Matches the proto comment "ONNX teacher used by Model2Vec-style static model distillation" (`opennlp_training.proto:78`) and the RPC name `TrainStaticModel`. |
| `"Classic pipeline"` (pack chip) | `"Language pack"` plus `Unlocks: Sentence detection, Tokenization, POS tags, Lemmas` | The card title already says "German language pack"; the chip repeats nothing useful. |
| facts line `"29.0 MiB · MIT · 256 dimensions · en"` | keep size only; move license, dimension, and language into chips | Precedent: HuggingFace model cards show license and language as separate pill badges. |

## OPINION: where the tag data comes from

| Tag | Derivable in the FE today? | Recommendation |
| --- | --- | --- |
| `Unlocks` | Yes, from `role` plus `languages`, using the table above | **P2**: ship the FE mapping now (a `ROLE_UNLOCKS: Record<ModelArtifactRole, string[]>` next to `roleLabel`), then move it server-side. |
| `Activation` | Yes: `restartRole()` already exists at `src/model-data-workbench.ts:640` and duplicates Java's `CatalogModelStore.requiresRestart` (`CatalogModelStore.java:588`) | **P2**: add `bool requires_restart` to `ModelCatalogDescriptor` so the two copies cannot drift. |
| `Language`, `License`, `dimension` | Yes, fields exist | **P3**: render as chips instead of a joined sentence. |
| `Format` / `Runtime` | **No.** `ModelCatalogDescriptor` (`opennlp_training.proto:101`) has 12 fields and none of them names a file, a format, or a backend. The `CatalogFile` list with names and SHA-256 values lives only in `opennlp-grpc-spi/.../catalog/CatalogFile.java` and never crosses the wire. | **P1**: add a format field. Two options below. |

Two ways to publish the family:

- **Option A, an enum.** Add
  `ModelArtifactFormat format = 13;` with values
  `MODEL_ARTIFACT_FORMAT_OPENNLP_BIN`, `_ONNX`, `_SAFETENSORS`,
  `_WORDPIECE_VOCAB`, `_SENTENCEPIECE`. Small, stable, and directly renderable
  as one chip. Closest precedent: MLflow's `flavors` map, which is exactly "in
  which runtime formats can this artifact be loaded".
- **Option B, the file list.** Add
  `repeated CatalogFileDescriptor files = 13;` with `relative_path`,
  `byte_size`, `sha256`. Strictly more informative (it also gives the FE the
  data to show a per-file progress bar and to verify an export), and the format
  chip becomes a client-side derivation from the file extensions. Precedent:
  the HuggingFace Hub `GET /api/models/{id}` `siblings` array, and the ONNX Hub
  manifest's `model_path` plus `model_sha`.

Recommendation: **Option B**, with a derived-in-server convenience field
`string format_label = 14;` so every client agrees on the chip text. The
descriptor already publishes `byte_size` for the whole model, so publishing the
per-file breakdown is not a new class of disclosure, and `StandardModelCatalog`
already holds every value.

## OPINION: proposed card layout

```
Potion Base 8M                                  [Serves immediately]
Static embedding table  ·  Safetensors  ·  MIT  ·  English  ·  256d
General-purpose English Model2Vec static embeddings
Unlocks:  Document embeddings    Chunk embeddings    Search vector space
29.0 MiB      Model card      MIT license
[x] I reviewed MIT and approve this node download          [ Download ]
```

```
OpenNLP 1.5 English person names                  [Restart required]
Name finder  ·  OpenNLP .bin  ·  OpenNLP maxent  ·  Apache-2.0  ·  English
Classic maxent name finder for English person names from the OpenNLP 1.5
model release; expects Penn-style tokenization
Unlocks:  Named entities (person)    Entity geocoding
4.8 MiB      Model card      Apache-2.0 license
```

The `Unlocks` chips should be clickable and switch to the Analyze tab with that
step preselected; see `findings/states-links-and-tests.md` for the missing
cross-tab wiring.

## Questions for the lead

1. Should `Unlocks` chips name the front-end feature label ("Named entities") or
   the wire enum (`PIPELINE_STEP_NER`)? The help callout on this tab already
   shows RPC names, so the tab has precedent for both registers.
2. Is a `ModelArtifactRole` for subword models, WordNet, doccat, and sentiment
   planned? Without them, four readiness rows can never be resolved from this
   tab, and the tag work will make that gap more visible, not less.
3. `dimension` is `0` for teachers by design (`opennlp_training.proto:112`).
   Should a teacher card show "dimension chosen at distillation" rather than
   omitting the chip?
