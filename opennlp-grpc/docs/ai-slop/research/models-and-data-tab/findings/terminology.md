# Terminology on the Models & data tab

Every "current" string below is quoted exactly from the UI or the code that
produces it. Precedent links are to the primary documentation of the product or
standard named; excerpts are saved under `reference/`.

## FACT: the vocabulary in play

| Term | Where it appears | What it means here |
| --- | --- | --- |
| "Models & data" | tab button, `index.html:51`; heading `index.html:795` | The tab name. |
| "Server capability inventory" | kicker, `index.html:794` | Subtitle of the same heading. |
| "Pipeline readiness" | `index.html:818` | The 17-row feature grid. |
| "Loaded bundles" / model bundle | `index.html:824`; `ListModelBundles` | A named set of models already loaded in the process, advertising `supportedSteps` and `supportedLanguages`. |
| "Pinned model catalog" / catalog | `index.html:832`; `ListModelCatalog` | The immutable, checksum-pinned list of models this build knows how to download. |
| catalog entry / `catalog_id` | `opennlp_training.proto:102` | One downloadable unit, for example `en-ner-15-person`. |
| `model_id` | `opennlp_training.proto:106` | The runtime serving id, shared by the four members of a language pack (`de-ud-gsd`). |
| artifact / `artifact_hash` | `opennlp_training.proto:129`, `:359` | The installed bytes plus their SHA-256 over the file manifest. |
| `artifact_id` | `StaticModelDescriptor`, `opennlp_training.proto:341` | A trained static model's id, also its embedding model id. |
| role / `ModelArtifactRole` | `opennlp_training.proto:76` | How an installed model participates: teacher, static embedding, parser, chunker, sentence detector, tokenizer, POS tagger, lemmatizer, name finder. |
| "Installed, restart required" | `src/model-data-workbench.ts:214` | The restart-only activation state. The phrase "restart-only" itself never reaches the UI. |
| "Ready-to-serve static table" | `roleLabel`, `src/model-data-workbench.ts:648` | A `STATIC_EMBEDDING` entry. |
| "Training teacher" | `roleLabel`, `src/model-data-workbench.ts:651` | A `DISTILLATION_TEACHER` entry. |
| "Classic pipeline" / "language pack" | `src/model-data-workbench.ts:369`, `:66` | Four catalog entries for one language installed behind one license review. |
| "Verified resource installer" | `index.html:852` | The unrelated CLI `install-resource` command block. |
| "this server node", "node download" | `index.html:833`, `src/model-data-workbench.ts:330` | The single machine the catalog installs onto. |
| dictionary | Trainer tab, `index.html:904` | An imported headword list; formats at `/api/v1/dictionary-formats`. |
| static model | Trainer tab, `index.html:942` | A distilled embedding table trained on this server. |
| vocabulary store | `VocabularyStore` SPI, config key `vocabulary.artifact_root` | The pluggable artifact backend (local filesystem, or S3 via `opennlp-grpc-store-s3`). Never surfaced in the UI. |
| family | `StaticModelDescriptor.family`, `opennlp_training.proto:348` | Already taken: it means the **tokenizer** family ("wordpiece"), not the file format. |

## FACT: how the same ideas are named elsewhere

| Concept | This repo | Hugging Face Hub | MLflow | spaCy | Triton | TorchServe | ONNX Model Zoo |
| --- | --- | --- | --- | --- | --- | --- | --- |
| The downloadable unit | catalog entry / `catalog_id` | model repository / repo id | registered model | trained pipeline package | model directory | model archive (`.mar`) | manifest entry |
| Its pinned version | `revision` (git sha, or `models-1.5`) | `revision` / commit sha | model version, alias | `version` in `meta.json` | version subdirectory | `modelVersion` | `onnx_version` plus `opset_version` |
| What it does | `role` | `pipeline_tag` and `tags` | flavor | `pipeline` component list | `backend` / `platform` | `handler` | `tags` |
| File format | not published | inferred from `siblings` | `flavors` map | not applicable | `platform` | `serializedFile` | `model_path` extension |
| Integrity | `sha256` per file, `artifact_hash` over the manifest | LFS `oid sha256` | `model_uuid` | not published | not published | not published | `model_sha`, `model_with_data_sha` |
| Licence | `license_name` (SPDX) plus `license_uri` | `license` (SPDX id), `license_name`, `license_link` | tag | `license` in `meta.json` | not modelled | not modelled | per-model LICENSE file |
| A group of models | "model bundle" and "language pack" | collection | not modelled | one pipeline is already the group | ensemble | workflow | not modelled |

## OPINION: renames worth making

### P1. "Models & data" has no data on it

Dictionaries, vocabularies, and trained static models all live on the **Trainer**
tab (`index.html:869-960`), and the gateway routes for them
(`/api/v1/dictionaries`, `/api/v1/dictionary-formats`,
`/api/v1/import-dictionary`, `/api/v1/static-models`,
`/api/v1/delete-static-model`, `GrpcJsonApi.java:172-190`) are wired only into
`VocabularyTrainerWorkbench` (`src/main.ts:222-236`). Nothing on the Models &
data tab reads any of them. A user looking for "data" clicks the tab named for
it and finds a model catalog.

- Current: `"Models & data"`.
- Proposed: `"Models"` (and, if the inventory of dictionaries and trained
  models should be browsable outside the training flow, add a read-only "Data"
  section here that lists them and links to Trainer for the write actions).
- Precedent: Hugging Face keeps **Models** and **Datasets** as separate top-level
  sections, https://huggingface.co/docs/hub/index . MLflow separates the model
  registry from artifacts, https://mlflow.org/docs/latest/model-registry.html .

### P1. "family" is already spoken for

The owner's ask is a family tag saying "ONNX, etc". `family` already exists in
the API and means the tokenizer family (`"wordpiece"`,
`opennlp_training.proto:348`). Introducing a second `family` would collide in
the exact place a client reads both, the model list.

- Proposed: call the new one `format` (values `ONNX`, `Safetensors`,
  `OpenNLP .bin`) and rename the existing UI-facing use, if it is ever shown, to
  `tokenizer family`.
- Precedent: MLflow calls this axis `flavors`,
  https://mlflow.org/docs/latest/models.html ; Triton calls it `platform` /
  `backend`, https://github.com/triton-inference-server/server .

### P2. "Ready-to-serve static table"

- Current: `"Ready-to-serve static table"` (`src/model-data-workbench.ts:648`).
- Proposed: role chip `"Static embedding model"`, activation chip
  `"Serves immediately"`.
- Why: the current label fuses two facts, and "table" is an implementation
  detail. spaCy publishes exactly this distinction as `vectors` (what it is)
  separate from `pipeline` (what it does),
  https://spacy.io/api/data-formats#meta .

### P2. "Training teacher"

- Current: `"Training teacher"` (`src/model-data-workbench.ts:651`).
- Proposed: `"Distillation teacher"`.
- Why: matches the proto comment "ONNX teacher used by Model2Vec-style static
  model distillation" (`opennlp_training.proto:78`) and the README's
  "Local ONNX teacher selectable by the Model2Vec-style trainer"
  (`README.md:281`). "Training teacher" reads as a tautology.
- Precedent: the term of art is "teacher model" in knowledge distillation; the
  Model2Vec project documents `distill(model_name=...)` with the teacher as the
  named input, https://github.com/MinishLab/model2vec .

### P2. "name-finder"

- Current: the chip renders the raw string `"name-finder"` because the `labels`
  map at `src/model-data-workbench.ts:653` has no entry for that role, so
  `labels[role] ?? role` falls through (`:661`).
- Proposed: `"Name finder"`, with `Unlocks: Named entities (person)` beside it.
- Precedent: Hugging Face names this task `token-classification` and shows it as
  the human label "Token Classification", https://huggingface.co/tasks .

### P2. Two installers, two vocabularies, one page

The page presents "Pinned model catalog" (`index.html:832`) and
"Verified resource installer" (`index.html:852`) side by side. The first says
"model", the second says "resource", and the two have no relationship in the UI:
the CLI block is a static template with placeholder values
(`https://example.invalid/resource.bin`), and the copy status even tells the
user `"Installer command copied. Replace the source, checksum, and target
values."` (`src/model-data-workbench.ts:517`).

- Proposed: retitle the second card `"Install a model the catalog does not
  offer"` and say plainly that it is for operator-provided files such as subword
  models, WordNet lexicons, and document categorizers, which the catalog has no
  role for (see `findings/unlocks-and-tags.md`). That turns dead template text
  into the answer for the three unfixable readiness rows.

### P3. "node"

`"this server node"`, `"node download"`, and
`"No catalog models have been downloaded to this node."` are correct for a
replicated deployment (`README.md:311` is explicit that installs are node-local)
but read as jargon to a single-server user.

- Proposed: `"this server"` in card copy, keeping "node" in the operator
  documentation and in the installer card.
- Precedent: Triton documents the "model repository" per server instance rather
  than per node, https://github.com/triton-inference-server/server .

### P3. "Loaded bundles" vs "language pack"

Two words for "a group of models" on one screen: a **bundle** is what the server
has loaded, a **pack** is what the catalog installs together. The distinction is
real and worth keeping, but the labels do not carry it.

- Proposed: `"Loaded model bundles"` and `"Language pack (4 models)"`, and say
  on the pack card that installing it will produce the bundle `pipeline-de`,
  which is the name the Analyze tab will show.
- Precedent: Hugging Face "collection" for a curated group,
  https://huggingface.co/docs/hub/collections ; spaCy treats one downloadable
  pipeline as the group and never needs a second word,
  https://spacy.io/models .

## OPINION: the standard tag vocabulary

Proposed chip format `Group: value`, ordered as below. Full rationale and data
sources in `findings/unlocks-and-tags.md`.

```
Task:       Named entities | Constituency parses | Syntactic chunks |
            Sentence detection | Tokenization | Part-of-speech tags | Lemmas |
            Document embeddings | Distillation teacher
Format:     OpenNLP .bin | ONNX | Safetensors | WordPiece vocab | SentencePiece
Runtime:    OpenNLP maxent | ONNX Runtime | ONNX Runtime CUDA
Activation: Serves immediately | Restart required
Language:   English | German | Spanish | French | Multilingual
License:    Apache-2.0 | MIT | CC-BY-4.0
```

`Task` mirrors Hugging Face's `pipeline_tag`, `Format` mirrors MLflow's
`flavors`, `Runtime` mirrors Triton's `backend` and matches the `backendId`
values (`opennlp-me`, `cuda`) this repo already returns from
`/api/v1/model-bundles`, and `License` values are already SPDX identifiers in
`StandardModelCatalog.java:41-46`.

## Questions for the lead

1. Is "Models & data" a naming placeholder for a tab that will eventually own
   dictionaries and vocabularies, or should those stay on Trainer and the tab be
   renamed "Models"?
2. `catalog_id`, `model_id`, `artifact_id`, and `artifact_hash` are four
   identifier namespaces the user can see (the last one is printed verbatim in
   the downloaded-model rows, `src/model-data-workbench.ts:219`). Should the UI
   show all four, or only a display name plus a copyable id?
3. Renaming `family` in `StaticModelDescriptor` is a proto change on a field
   that is already published. Is a new `format` field beside it acceptable, or
   should the tokenizer family be folded into a generic tag list?
