# Trainer tab

Analysis of `findings/` for the tab labelled "Trainer" (`index.html:53`,
section `vocabulary-trainer`, controller `src/vocabulary-trainer.ts`).

## What the tab does

Three steps: import a dictionary, learn a vocabulary from it (term frequency
counting with a minimum frequency and a term cap), then distil a static
embedding model from a teacher: one forward pass per vocabulary row through an
ONNX sentence transformer, PCA to `pca_dims` (default 256), frequency
reweighting, written as `model.safetensors`, `tokenizer.json`, `config.json`,
`terms.txt` and a checksum `manifest.tsv`
(`findings/what-the-trainer-does.md`). This is Model2Vec distillation in Java;
there are no epochs, no loss, no optimizer.

A trained model serves immediately and its artifact id is the embedding model
id; no restart is needed and `loadExistingModels` re-registers it after one.
The tab under-sells this, and it is the opposite of how catalog models behave.

## Verdicts

1. **Terminology is mostly right; qualify rather than rename.** "Teacher" is
   the standard distillation word (Hinton 2015, Model2Vec, Sentence
   Transformers). "Learn a vocabulary" is scikit-learn's own verb, so the
   owner's suggested "Trained vocabulary" is rejected: nothing is trained in
   step 2, frequencies are counted. "Static model" becomes "static embedding
   model" in every string (Model2Vec and Hugging Face use the long form; the
   tab's intro already does). The button "Train model" becomes "Distill model"
   and the step heading "Distill a static embedding model"; the tab keeps the
   name "Trainer" with a kicker, because "Distiller" is jargon in a tab strip.
   Full table: `findings/terminology.md` summary.

2. **Three vocabularies share one word.** Teacher subword vocabulary, learned
   corpus vocabulary, and the embedding table are all "vocabulary"; only the
   progress log distinguishes them. The help callout gets a three-line
   definition and each UI string names which one it means.

3. **"Vocabulary drift" becomes "Vocabulary coverage".** This researcher
   argued to keep "drift": the term is real (arXiv:2305.17127) and the right
   one of the three drift dimensions. The Lifecycle researcher showed the
   panel computes an out-of-vocabulary rate and a coverage share
   (`opennlp_search.proto:253`) with no time axis, and the meter's own
   aria-label already says coverage. `../industry-terminology` settles on
   "Vocabulary coverage" as the heading; the flyout text drafted in
   `findings/terminology.md` section 10 is adopted and keeps the word drift
   for what a falling number means. The threshold label drops the proto
   field name ("new terms") for "out-of-vocabulary terms".

4. **Tags.** Hugging Face's split is the precedent: structured
   `pipeline_tag`, `library_name`, `base_model` + `base_model_relation`,
   `license`, `language`, plus free tags. Everything except license and
   language is derivable today from `StaticModelDescriptor`; those two are
   missing from the descriptor even though the catalog knows them for the
   teacher. The bare `family` value ("WordPiece") is the tokenizer family, not
   lineage, and must be labelled as such.

5. **S3 is not an export feature.** `opennlp-grpc-store-s3` is the artifact
   root itself (`vocabulary.artifact_root=s3://bucket/prefix`); dictionaries,
   vocabularies and models already live in the bucket when it is configured.
   What is missing is a portable descriptor and an import path. Decision:
   record license, languages, teacher reference and revision on the descriptor
   at distillation time (the artifact is immutable, the teacher id can be
   re-pinned later), and emit a `model-card.json` beside `manifest.tsv`
   (`findings/artifact-and-export.md` section 5). The zoo-format comparison
   in `../models-and-data-tab` decides the final field names.

6. **Gating text is wrong on the live instance.** "Training is disabled: the
   server has no vocabulary artifact root or no teachers." conflates two
   causes; `writesEnabled` only means "no artifact root", and the no-teacher
   branch leaves the button enabled and scolds on click. Two states, two
   messages, and the teacher one jumps to Models & data.

7. **Links and tests.** No outbound jump on the tab; the success message names
   Analyze and Workspace search in plain text. Coverage is decent (13 unit, 5
   API, 8 gateway tests, solid service tests) but the UI dictionary import, UI
   delete, TSV save, the no-teacher branch, and the `onModelsChanged` hand-off
   into the Analyze selector are untested, and e2e has a single assertion.

## Open questions for the owner

- Rename the button to "Distill model"? (Recommended yes.)
- Add license, languages and teacher reference to `StaticModelDescriptor`
  now, or start the model card as a side file? (Recommended: proto now, the
  side file is generated from it.)
