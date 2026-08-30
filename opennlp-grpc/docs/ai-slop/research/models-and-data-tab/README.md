# Models & data tab

Analysis of `findings/` for the tab labelled "Models & data" (`index.html:51`,
section `model-data-workbench`, controller `src/model-data-workbench.ts`).

## What the tab does

Three panels: a pipeline readiness grid (17 feature rows), the model bundles
loaded in the process, and the checksum-pinned catalog with install buttons.
Installs either register immediately (static embeddings, teachers) or publish a
config key for the next restart (parsers, chunkers, name finders, language
pack members). The complete role-to-feature-to-config-key table is in
`findings/unlocks-and-tags.md` and is correct as written.

## Verdicts

1. **The demo runs the one state nothing tests.** The live catalog reply has
   no `installsEnabled`, so every card renders a live-looking license checkbox
   and "Download and activate" button that do nothing
   (`src/model-data-workbench.ts:574`); the reason ("Configure
   model.catalog_root to enable node downloads") is printed once in a card at
   the bottom. Every unit test passes `installsEnabled: true`. Decision: brown
   out the cards with the reason inline, and add the unit test for the
   disabled state first. This is also why the owner's "button to get the NER
   models" only works on a node with a catalog root; the docker demo must set
   `model.catalog_root` so the button is real there.

2. **Unlock tags are derivable today; format tags are not.** `role` maps to
   the feature label, the tab it appears on, and immediate versus restart
   activation, all in the FE already (`analysis-config.ts:192`,
   `model-data-workbench.ts:640`). But `ModelCatalogDescriptor`
   (`opennlp_training.proto:101`) has no file, format or backend field; the
   `CatalogFile` list never crosses the wire. Decision: add `format` (CNCF
   model-spec values `onnx`, `safetensors`, plus `opennlp-bin`), `unlocks`,
   `requires_restart` and a `files` list to the descriptor. Do not call the
   format field `family`; `StaticModelDescriptor.family` already means the
   tokenizer family. Tag register: FE feature labels ("Named entities"), with
   the wire enum in the tooltip, since first-time users read the labels.

3. **Four readiness rows can never be fixed from this tab.** Subword
   tokenization, lexical expansion (WordNet), document categories and
   sentiment have config keys but no `ModelArtifactRole`, so they show "Needs
   model or data" with no path. Decision: add roles for subword, WordNet and
   doccat (sentiment is a doccat), and catalog entries for at least one of
   each. Tracked in `../roadmap` track 2.

4. **Links.** No `data-workbench-jump` targets this tab, while the Trainer
   tells users to "add training.teacher entries" when installing
   `all-minilm-l6-v2-teacher` here is the supported fix. Inbound: every
   "Needs model or data" row on Analyze and the Trainer's no-teacher state
   jump here and scroll to the card that fixes them. Outbound: after an
   immediate install, offer the tab it unlocked (Analyze with the model
   preselected, Trainer re-initialised).

5. **Install failures.** SHA mismatch, disk full, symlink rejection and
   network failure all collapse to "Catalog model installation failed"
   (`OpenNlpModelTrainingServiceImpl.java:212`); no free-space check exists and
   two entries are ~500 MiB; an install can claim a pipeline slot twice and
   `CatalogModelBootstrap.java:143` then refuses the next boot with no API to
   warn from. Decision: distinct failure types on the wire, a free-space check
   before download, and a slot-occupancy check in the install path rather
   than at boot.

6. **Model zoo and export.** Adopt no standard wholesale. Publish one
   `opennlp-model.json` per model and one `opennlp-catalog.json` index, field
   names from the CNCF model-spec where they overlap (`format`, `licenses`,
   digests with a `sha256:` prefix) and Hugging Face where a client reads both
   (`license`, `revision`, `pipeline_tag` generalised to `unlocks`). The
   publish side already writes `manifest.tsv` and `artifact_hash`, and the S3
   store already has an atomic `published/` convention, so the missing pieces
   are the serialisation of `StandardModelCatalog` (26 hard-coded Java
   constructor calls) and a `FileModelCatalogProvider` that reads a pinned
   index. Security bar for import: operator-config-only index URI, pinned
   index digest, and a detached `.asc` as Apache OpenNLP already ships for its
   own models. The Trainer's `model-card.json` proposal is folded into this
   file format so there is one manifest, not two.

7. **Small bugs.** `"name-finder"` renders as the raw enum because `roleLabel`
   has no entry (`model-data-workbench.ts:653`) and the role test covers 8 of 9
   roles. A catalog load failure leaves the installed-models panel on its
   "Loading the verified model inventory." placeholder forever.

8. **Tests.** No e2e spec references the tab. `configure()` (the readiness
   grid and loaded bundles), the copy button, the empty catalog and the load
   failure have no test.

## Open questions for the owner

- Roles for subword, WordNet and doccat now, or after the tag work?
- Teacher cards: `dimension` is 0 by design; show "chosen at distillation"?
