# Terminology audit: Trainer tab

Every term the Trainer tab puts in front of a user, checked against the
literature and against neighbouring products. External excerpts live in
../reference/ (model2vec.md, sentence-transformers-static-embeddings.md,
hinton-2015-distillation.md, word-embeddings-classics.md,
sklearn-countvectorizer-vocabulary.md, drift-terminology.md,
hf-model-cards-and-tags.md, opennlp-embeddings-javadoc.md).

Verdict key: KEEP (term is already the standard one), QUALIFY (right word,
needs a definition next to it), RENAME (wrong or misleading word).

## Summary table

| Current UI string | Where | Verdict | Proposed |
| --- | --- | --- | --- |
| `Trainer` (tab) | index.html:53 | QUALIFY | `Trainer` with kicker `Distill a static embedding model` |
| `Vocabulary to model` (kicker) | index.html:873 | KEEP | as is |
| `Teacher` | index.html:943 | KEEP | `Teacher model` with tooltip |
| `3 · Train a static model` | index.html:941 | RENAME | `3 · Distill a static embedding model` |
| `Train model` (button) | index.html:949 | RENAME | `Distill model` |
| `Legal static model` (placeholder) | index.html:946 | RENAME | `Legal static embedding model` |
| `Trained models on this server` | index.html:954 | QUALIFY | `Static embedding models trained on this server` |
| `2 · Learn a vocabulary` | index.html:916 | KEEP | as is |
| `Learned vocabularies` | index.html:936 | KEEP | as is, **not** "Trained vocabularies" |
| `Min frequency` | index.html:927 | RENAME | `Min term frequency (total occurrences)` |
| `Max terms` | index.html:931 | QUALIFY | `Max terms (vocabulary size cap)` |
| `PCA dimensions (0 = server default)` | index.html:947 | QUALIFY | keep name, add `= the model's vector dimension; default 256` |
| `Download TSV` | index.html:938 | RENAME | `Export vocabulary TSV` |
| `unknown tokenizer` (family fallback) | vocabulary-trainer.ts:290 | RENAME | `tokenizer family unknown` |
| `Vocabulary drift` (Lifecycle) | index.html:1100 | KEEP + flyout | as is, defined |
| `Report vocabulary drift after this many new terms` | index.html:1091 | QUALIFY | `Report drift after this many out-of-vocabulary terms` |

---

## 1. "Teacher"

FACT. The UI label is `Teacher` (index.html:943). The proto calls the message
`TeacherDescriptor` and the catalog role
`MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER` (opennlp_training.proto:79, :287).
A teacher is a sentence-transformer with an ONNX export, allowlisted by the
operator through `training.teacher.<id>.ref` (README.md:582).

Literature. Hinton, Vinyals and Dean 2015 (arXiv:1503.02531) introduced the
distillation idea but the abstract itself says "cumbersome model" and
"distilled", not "teacher"; teacher/student is the downstream convention
(../reference/hinton-2015-distillation.md). Sentence Transformers documents it
directly as "teacher model" and "student model" in its distillation guide
(../reference/sentence-transformers-static-embeddings.md). Model2Vec, the exact
technique implemented here, prefers "Sentence Transformer" or "the base model"
and does not consistently say "teacher"
(../reference/model2vec.md).

OPINION P3. KEEP `Teacher`. It is the dominant term in the distillation
literature, and it is the term the proto, the README and the config keys already
use, so renaming it would fragment the vocabulary across five layers. Add a
tooltip.

Proposed tooltip (the `title` on the select, which today carries the raw
filesystem path or Hugging Face id, vocabulary-trainer.ts:156):
> Teacher model: a sentence embedding model the server runs once per term to
> produce its vector. The teacher is used only during distillation and is never
> needed to serve the finished model.

## 2. "Static model" vs "static embedding model"

FACT. The tab mixes both. The section heading says
`3 · Train a static model` (index.html:941) and the display-name placeholder
says `Legal static model` (index.html:946), while the tab description one screen
above says "distill a configured teacher into a static embedding model"
(index.html:877). The proto message is `StaticModelDescriptor`, the API paths
are `/api/v1/static-models` and `/api/v1/train-static-model`, and the catalog
role is `MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING` (opennlp_training.proto:81, :338).

Literature. Model2Vec's own material says "static embedding model" and never
bare "static model" (../reference/model2vec.md). The Hugging Face static
embeddings post and the Sentence Transformers docs both use "static embedding
model" and "Static Embeddings"
(../reference/sentence-transformers-static-embeddings.md). GloVe, word2vec and
fastText call their outputs "word vectors" or "word embeddings"
(../reference/word-embeddings-classics.md); "static" is the modern retronym that
distinguishes them from contextual encoders.

OPINION P2. RENAME the user-facing strings to `static embedding model`. "Static
model" on its own reads as "a model that does not change", which is not what it
means. Leave the wire names alone: `StaticModelDescriptor`, `/api/v1/static-models`
and `MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING` are already published API and the
role enum is already the long form.

## 3. "Train" vs "distill"

FACT. The button says `Train model` (index.html:949), the tab is `Trainer`, the
progress status says "The server is distilling the static model."
(vocabulary-trainer.ts:248), the in-page help says "distill a configured teacher"
(index.html:877, :888), and the Workflows tab's stage 3 says
`Distill embeddings` (index.html:492). The proto says "Distills one teacher"
(opennlp_training.proto:41).

Nothing here is gradient training. There is no loss, no optimizer, no epochs, no
learning rate, no validation split. It is one forward pass per vocabulary row
followed by PCA and a frequency reweighting
(../reference/opennlp-embeddings-javadoc.md).

Literature. Model2Vec's command and API verb is `distill`
(../reference/model2vec.md). Sentence Transformers files the technique under
"Model Distillation" (../reference/sentence-transformers-static-embeddings.md).

OPINION P2. RENAME the button to `Distill model` and the step heading to
`3 · Distill a static embedding model`, and add one sentence to the help callout
(index.html:882) saying what is absent:

> Distillation is not gradient training. There are no epochs, no learning rate
> and no loss curve: the server runs each vocabulary row through the teacher
> once, reduces the result with PCA, and reweights the rows by frequency.

The Workflows tab already carries a note in this spirit,
"What 'train' means here" (index.html:508), which the Trainer tab lacks. That
note should be shared rather than duplicated.

OPINION P3. Keep the tab name `Trainer`. "Distiller" would be precise but is
jargon in a tab strip that otherwise reads Analyze / Workflows / Corpus search.
Carry the precision in the kicker instead.

## 4. "Vocabulary": which one

FACT. Three different vocabularies exist in this feature and the UI uses the
bare word for all of them.

1. **Teacher tokenizer vocabulary**, the subword pieces of the teacher's
   `tokenizer.json`. This becomes `vocabulary_size` on the descriptor, described
   in the proto as "Subword vocabulary rows in the embedding table"
   (opennlp_training.proto:352).
2. **Learned corpus vocabulary**, the term-frequency table produced by
   `VocabularyLearner` and published as `vocabulary.tsv`. This is what "Learn a
   vocabulary" produces and what `Download TSV` exports.
3. **Embedding vocabulary**, the union of the two above: the rows of the
   published table. `StaticEmbeddingModel.vocabularySize()` deliberately excludes
   the term rows, and `termCount()` counts them separately
   (../reference/opennlp-embeddings-javadoc.md).

The progress log is the one place that gets this right. It prints
"N tokenizer rows, M learned term rows" (vocabulary-trainer.ts:257).

Literature. scikit-learn's `CountVectorizer` is the mainstream precedent for
sense 2: its docstring literally says "learn the vocabulary dictionary", the
fitted attribute is `vocabulary_`, and the knobs are `min_df`, `max_df` and
`max_features` (../reference/sklearn-countvectorizer-vocabulary.md). fastText is
the standard reference for sense 1 and for "subword" and "out-of-vocabulary"
(../reference/word-embeddings-classics.md).

OPINION P1. Add a one-line disambiguation in the help callout and use the
qualified form in labels that could mean either:

> A **vocabulary** on this tab is a corpus vocabulary: the list of terms counted
> in your documents, with their frequencies. It is not the teacher's subword
> tokenizer vocabulary; the trained model contains both, the teacher's subword
> rows first and your terms after them.

## 5. "Learn a vocabulary", and should it be "Trained vocabulary"?

The owner asked whether "Trained vocabulary" is the better phrase. The answer is
**no**.

FACT. Step 2 counts word frequencies. It runs no model. The proto RPC is
`LearnVocabulary`, the descriptor is `VocabularyArtifactDescriptor` and the
class is `VocabularyLearner` (opennlp_vocabulary.proto:50, :159).

Literature. scikit-learn's own verbs are "learn a vocabulary dictionary" and
"fit" (../reference/sklearn-countvectorizer-vocabulary.md). Nothing in the
embedding literature calls a frequency table "trained".

OPINION P1. KEEP `Learn a vocabulary` and `Learned vocabularies`. "Trained
vocabulary" would be doubly wrong: it claims a training step that does not
happen, and it collides with `Trained models on this server` (index.html:954)
one section below, which does describe a distilled artifact. If a shorter noun
is wanted, `Corpus vocabulary` is accurate and unambiguous.

Proposed flyout for the `Learned vocabularies` label:
> A learned vocabulary is a frequency-ranked term list cut from your corpus:
> every word that appears at least `Min frequency` times, plus every headword of
> the dictionary you paired, whatever its frequency. Terms are lower-cased and
> multi-word terms are joined by single spaces.

## 6. "Min frequency" and "Max terms"

FACT. `Min frequency` defaults to 2 (index.html:928) and `Max terms` to 10000
(index.html:932). Upstream, `minFrequency` is "the smallest corpus frequency that
keeps a non-dictionary word" and `maxTerms` is "the largest result size,
dictionary terms exempt" (../reference/opennlp-embeddings-javadoc.md).

Two problems.

1. This is **collection frequency** (total occurrences), not **document
   frequency**. scikit-learn's nearest knob, `min_df`, is a document frequency,
   so a user carrying that intuition will misread the field. The label must say
   which.
2. The Workflows tab labels the same two controls `Min term frequency` and
   `Max corpus terms` (index.html:454, :457) with different defaults (1 and
   10000). Two tabs, two names, two defaults, one server behaviour.

OPINION P2. RENAME to `Min term frequency (total occurrences)` and
`Max terms (vocabulary size cap)`, and use the same labels and defaults on both
tabs. Precedent for the cap: `max_features` in scikit-learn's `CountVectorizer`,
documented as building "a vocabulary that only considers the top max_features
ordered by term frequency across the corpus"
(../reference/sklearn-countvectorizer-vocabulary.md).

OPINION P2. Say that dictionary headwords bypass both knobs. The help callout
claims dictionary headwords "become guaranteed terms" (index.html:885) but the
`Min frequency` field sits three inches away with no note, so the interaction is
invisible at the point of use.

## 7. "PCA dimensions"

FACT. Label is `PCA dimensions (0 = server default)` (index.html:947). Server
default is 256, ceiling is `training.max_pca_dims` (default 512, hard limit
4096) (StaticModelArtifactStore.java:72, :78; opennlp_training.proto:320). The
live server reports `maxPcaDims: 512` but the front end never reads it, so the
number input has no `max` attribute and an over-limit value is only rejected
server-side.

Literature. Model2Vec's distillation parameter is literally `pca_dims`
(../reference/model2vec.md), so the name matches the reference implementation
exactly. GloVe pairs "vocabulary size" with "dimensionality" as its headline
stats (../reference/word-embeddings-classics.md).

OPINION P3. KEEP the name. QUALIFY the help text: the PCA component count is the
output vector dimension, so it is the number that must match anything the vector
index already holds. Say the default:

> PCA dimensions: how many principal components to keep, which is the trained
> model's vector dimension. 0 uses the server default of 256. Model2Vec
> recommends 256.

OPINION P2. Read `maxPcaDims` from `/api/v1/teachers` (it is already on the
response, see ../reference/live-api-responses.md) and set it as the input's
`max`. The field is currently the only trainer control whose server bound is
sent to the client and then thrown away.

## 8. "Family"

FACT. The model row renders
`` `${model.family || "unknown tokenizer"}` `` between the term count and the
teacher id (vocabulary-trainer.ts:290), so a user sees
`dim 256 · 4,812 terms · WordPiece · teacher all-minilm-l6-v2`. The value comes
from `ModelDistiller.Result.family()`, which is exactly the string `"WordPiece"`
or `"SentencePiece"` (../reference/opennlp-embeddings-javadoc.md). The proto
comment says `e.g. "wordpiece"` in lower case, which does not match what the
library returns (opennlp_training.proto:347).

This matters for the owner's "tags for family" ask, because there are two
unrelated notions of family in play:

- **Tokenizer family**: WordPiece or SentencePiece. That is what this field is.
- **Model lineage**: which teacher a model descends from. Hugging Face models
  this as `base_model` plus `base_model_relation` (`finetune`, `adapter`,
  `quantized`, `merge`) (../reference/hf-model-cards-and-tags.md). The trainer
  already stores the lineage in `teacher_id` and `vocabulary_artifact_id`.

OPINION P1. Never render a bare `WordPiece` in a facts line. Label it:
`tokenizer WordPiece`. And fix the fallback: `unknown tokenizer` reads as a
tokenizer named "unknown"; `tokenizer family unknown` reads correctly.

OPINION P2. Fix the proto comment at opennlp_training.proto:347 to quote the
actual casing.

## 9. Proposed tag vocabulary for a trained model

The owner asked for "tags for what each trained model unlocks and its family".
Hugging Face's model card metadata is the right precedent: it splits a small set
of structured fields from one free-form `tags` list
(../reference/hf-model-cards-and-tags.md).

Everything below is derivable today from `StaticModelDescriptor` plus the
catalog entry of the teacher; none of it needs a new training input.

| Proposed tag | Precedent field | Value on a trained model | Source today |
| --- | --- | --- | --- |
| Capability, e.g. `Embeds text`, `Semantic search`, `Chunk embedding` | `pipeline_tag` | fixed for this artifact role | `MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING` |
| Runtime, e.g. `static table, in process` | `library_name` | always `static` | `TrainedModelEmbeddingProvider.TRAINED_BACKEND_ID` |
| Distilled from `<teacher display name>` | `base_model` + `base_model_relation: distilled` | teacher | `StaticModelDescriptor.teacher_id` |
| Tokenizer `WordPiece` / `SentencePiece` | free-form `tags` | family | `StaticModelDescriptor.family` |
| `256-dim` | free-form `tags` | dimension | `StaticModelDescriptor.dimension` |
| Vocabulary `<vocabulary display name>` | `datasets` | source corpus | `StaticModelDescriptor.vocabulary_artifact_id` |
| Language | `language` | not captured | **missing**, see below |
| License | `license` | not captured | **missing**, see below |

OPINION P1. Two of these cannot be filled in today and both matter. The catalog
knows the teacher's `languages` and `license_name`
(opennlp_training.proto:110, :114; StandardModelCatalog.java:169) but
`StaticModelDescriptor` carries neither, so a distilled model on this server has
no recorded license even though it is derived from an Apache-2.0 or MIT teacher.
That is a blocker for any export feature; see findings/artifact-and-export.md.

## 10. "Vocabulary drift" (Lifecycle tab, in scope because it names the same artifact)

FACT. The Lifecycle tab has a `Vocabulary drift` panel (index.html:1100) with a
coverage meter, a stats list, and an `Out-of-vocabulary terms` list
(index.html:1101, :1106). The threshold field reads
`Report vocabulary drift after this many new terms` with the helper
`Leave this at 0 to never report drift.` (index.html:1091, :1094).

What is actually computed (opennlp_search.proto:253):
`distinct_terms`, `term_occurrences`, `new_terms` ("distinct terms absent from
the current vocabulary"), `new_term_occurrences`, and `vocabulary_coverage`
("fraction of occurrences hitting vocabulary terms, in [0, 1]").

Literature (../reference/drift-terminology.md):
- "Vocabulary drift" IS a defined term: arXiv:2305.17127 (2023) names
  vocabulary, structural and semantic drift as three dimensions of linguistic
  dataset drift, and defines vocabulary drift as "content word frequency
  divergences".
- Industry drift tooling does not use it as a metric name. Evidently's text
  drift tutorial measures "the share of out-of-vocabulary words", which it also
  calls the "out-of-vocabulary rate". Evidently itself says none of "data
  drift" / "concept drift" is strictly defined.
- "Semantic drift" and "lexical semantic change" (arXiv:1605.09096) are a
  different thing: meaning change at fixed vocabulary. This server measures no
  such thing.

OPINION P2. KEEP the panel name `Vocabulary drift`: it is a real term, it is the
right one of the three dimensions, and there is no better short label. But the
numbers underneath are an out-of-vocabulary rate, not a frequency divergence, so
the flyout has to say what is and is not measured.

Proposed flyout for `Vocabulary drift`:
> **Vocabulary drift** is the gap between the words your indexed documents
> actually use and the vocabulary the serving model was trained on. This server
> measures it as an out-of-vocabulary rate: how many distinct terms in the
> collection have no row in the current vocabulary artifact, and what share of
> all term occurrences do hit one (the coverage meter). It does not measure
> semantic drift, where the same words change meaning. When coverage falls,
> learn a new vocabulary from the current documents, distil a new model, and
> rebuild the index with it.

OPINION P3. RENAME the threshold label from
`Report vocabulary drift after this many new terms` to
`Report drift after this many out-of-vocabulary terms`. "New terms" is the proto
field name (`new_terms`) leaking into the UI; the list right below it already
uses the correct phrase, `Out-of-vocabulary terms`.

## 11. Terms checked and found absent

- **Epochs, steps, learning rate, batch size, loss, optimizer.** None appear
  anywhere in the trainer, correctly, because none exist. Worth stating in the
  help so a reader does not go looking for them.
- **Zipf weighting.** Applied by the distiller but never surfaced. If it is ever
  exposed, Model2Vec's own name for it is "SIF weighting" (Smooth Inverse
  Frequency); Zipf is the justification, not the feature name
  (../reference/model2vec.md).
- **Explained variance ratio.** Printed once in the progress log as
  "78.4% variance retained" (vocabulary-trainer.ts:259). The underlying name
  matches scikit-learn's `explained_variance_ratio_` exactly, so the descriptor
  field name is right. The UI phrasing "variance retained" is clearer than the
  jargon and should stay.
- **Student.** Never used, correctly: the output is a table, not a student
  network. Do not introduce it.
- **Quantization.** `StaticEmbeddingModel` supports a `model.quantized` matrix
  (../reference/opennlp-embeddings-javadoc.md) but the trainer never produces or
  mentions one.

## Questions for the lead

1. Rename the button to `Distill model`, or keep `Train model` on the grounds
   that more users know the word? The rest of the stack already says distill.
2. Do we want `license` and `language` added to `StaticModelDescriptor`? It is a
   proto change, and without it no export or model zoo entry can be honest about
   the teacher's license.
3. Should the Trainer and Workflows tabs share one set of labels and defaults
   for min frequency, max terms and PCA dimensions? They disagree today.
