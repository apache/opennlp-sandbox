# What the Trainer tab actually does

Scope: the tab `section#vocabulary-trainer`
(opennlp-grpc-webapp-default/index.html:869), its controller
opennlp-grpc-webapp-default/src/vocabulary-trainer.ts, the gateway routes it
calls, and the service code behind them.

Everything under FACT is what the code does today, with a citation. Everything
under OPINION is a recommendation with a priority.

## 1. FACT: the three-step flow

The tab renders one status line and a three-column grid
(opennlp-grpc-webapp-default/index.html:899, :902).

### Step 1 "1 · Import a dictionary" (index.html:903)

A `Format` selector, a display name, a file picker, an `Import dictionary`
button and an `Imported dictionaries` selector.

- `importDictionary` reads the file, base64-encodes it and POSTs
  `ImportDictionaryUpload` to `/api/v1/import-dictionary`
  (opennlp-grpc-webapp-default/src/vocabulary-trainer.ts:177, api.ts:329).
- The gateway composes that single JSON message into the client-streaming
  `ImportDictionary` RPC (opennlp-grpc-webapp/src/main/java/org/apache/opennlp/grpc/webapp/VocabularyRpc.java:123;
  the proto shape is documented at
  opennlp-grpc-api/src/main/proto/org/apache/opennlp/grpc/v1/opennlp_vocabulary.proto:176).
- Three built-in formats exist: headword-and-definition TSV, one headword per
  line, and OpenNLP dictionary XML
  (opennlp_vocabulary.proto:59). Extension formats arrive through
  `DictionaryFormatProvider`
  (opennlp-grpc-spi/src/main/java/org/apache/opennlp/grpc/spi/vocabulary/DictionaryFormatProvider.java).
- The server normalizes the dictionary into `dictionaries/<artifactId>/entries.tsv`
  plus a `dictionary.pb` descriptor and publishes it atomically
  (opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/vocabulary/VocabularyArtifactStore.java:86, :261).

A dictionary here is a **term list**, not a definition source: only the
headwords matter downstream. Definitions are parsed and stored but never used by
the learner.

### Step 2 "2 · Learn a vocabulary" (index.html:915)

A corpus textarea (blank line separates documents), a live document/code-point/
byte counter, a display name, `Min frequency` (default 2), `Max terms`
(default 10000), a `Learn vocabulary` button, a `Learned vocabularies` selector
and a `Download TSV` button.

- `learnVocabulary` splits the textarea on blank lines into
  `{docId: "trainer-doc-N", rawText}` and POSTs a `LearnVocabularyUpload` to
  `/api/v1/learn-vocabulary` (vocabulary-trainer.ts:200, :514).
- Server side, `VocabularyArtifactStore.learnVocabulary` bounds the corpus
  (max 100000 documents, max 100 MB by default) and delegates to
  `opennlp.embeddings.corpus.VocabularyLearner`
  (VocabularyArtifactStore.java:305, :339).
- What the learner actually does (upstream javadoc, quoted in
  ../reference/opennlp-embeddings-javadoc.md): lower-case fold, split into
  maximal runs of letters and digits, count; dictionary headwords are matched
  greedily longest-first as multi-word units, so "habeas corpus" counts once and
  neither word counts separately. Dictionary terms come first at any frequency
  including zero, then corpus words at or above `minFrequency`, truncated to
  `maxTerms`; dictionary terms are exempt from truncation.
- The result is a TSV `term<TAB>count<TAB>source` where source is `dictionary`
  or `corpus`, published as `vocabularies/<artifactId>/vocabulary.tsv` with a
  `vocabulary.pb` descriptor (VocabularyArtifactStore.java:90, :349).
- `Download TSV` POSTs `/api/v1/download-vocabulary` and saves those exact
  bytes client-side as `<artifactId>.tsv` (vocabulary-trainer.ts:226, :598).

So "learn a vocabulary" is **counting term frequencies over pasted text**. No
model is trained and nothing neural happens in this step. The status line after
success reads, verbatim:
`Learned N terms (D dictionary, C corpus).` (vocabulary-trainer.ts:221).

### Step 3 "3 · Train a static model" (index.html:941)

A `Teacher` selector, a display name, `PCA dimensions (0 = server default)`, a
`Train model` button, and a live progress log.

- `train` POSTs `TrainStaticModelRequest` to `/api/v1/train-static-model`
  (vocabulary-trainer.ts:239, api.ts:385). That route is special-cased as an
  NDJSON stream rather than a plain JSON call
  (opennlp-grpc-webapp/src/main/java/org/apache/opennlp/grpc/webapp/OpenNlpGrpcWebServer.java:150, :232).
- The service acquires one of `training.max_concurrent_trainings` permits, then
  calls `StaticModelArtifactStore.trainStaticModel`
  (opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/training/OpenNlpModelTrainingServiceImpl.java:246).
- That reads the vocabulary terms, then calls
  `opennlp.embeddings.ModelDistiller.distill(teacherRef, scratchDir, pcaDims, terms, listener)`
  (StaticModelArtifactStore.java:325, StaticModelTrainer.java:47).

## 2. FACT: what a "teacher" is

A teacher is an **operator-allowlisted sentence-transformer with an ONNX
export**, referenced either by local directory or by Hugging Face model id.

- Config: `training.teacher.<id>.ref` and `training.teacher.<id>.display_name`
  (StaticModelArtifactStore.java:81, README.md:582).
- The directory must hold `tokenizer.json` and `onnx/model.onnx`
  (README.md:591; upstream javadoc in ../reference/opennlp-embeddings-javadoc.md).
- Clients cannot name an arbitrary teacher; only allowlisted ids are accepted
  (StaticModelArtifactStore.java `validateTrainingControls`, README.md:578).
- The catalog ships two pinned teachers,
  `sentence-transformers/all-MiniLM-L6-v2` and
  `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`, both with role
  `MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER`
  (opennlp-grpc-installer/src/main/java/org/apache/opennlp/grpc/installer/StandardModelCatalog.java:165, :185).
- Installing one of those on the Models and data tab registers it as a teacher
  in-process and re-initializes the trainer
  (opennlp-grpc-service/.../StaticModelArtifactStore.java:265 `registerCatalogTeacher`;
  opennlp-grpc-webapp-default/src/model-data-workbench.ts:498;
  opennlp-grpc-webapp-default/src/main.ts:216).

So the UI word "teacher" is the knowledge-distillation teacher of
Hinton et al. 2015, used exactly the way Model2Vec uses it. That is correct
usage; see findings/terminology.md.

## 3. FACT: what a "static model" is and how it is built

`ModelDistiller` reproduces Model2Vec's distillation in Java
(../reference/opennlp-embeddings-javadoc.md). Steps:

1. Clean the teacher's tokenizer vocabulary, run every surviving subword token
   through the ONNX graph as `[bos, token, eos]`, mean-pool the last hidden
   states. That gives one vector per subword token.
2. Additionally run each learned vocabulary term (a whole word or multi-word
   phrase) through the teacher as a full sequence and append those rows after
   the subword rows. Terms are lower-cased and space-joined; a term identical to
   a surviving tokenizer token is dropped as a duplicate row.
3. Project the matrix onto its top `pcaDims` principal components (randomized
   SVD). `pcaDims = 0` selects the server default 256
   (StaticModelArtifactStore.java:72 `DEFAULT_PCA_DIMS`; proto comment at
   opennlp_training.proto:320); the request is bounded by
   `training.max_pca_dims`, default 512, hard ceiling 4096.
4. Scale each row by its Zipf weight `sif / (sif + p)` with `sif = 1e-4`, the
   Model2Vec default. The Zipf ranking spans subword rows and term rows as one
   list, which is why the terms must arrive sorted by descending corpus
   frequency, and why the vocabulary TSV keeps its counts.
5. Write `model.safetensors` (F32), the cleaned `tokenizer.json`, a
   `config.json` with `"normalize": true`, `terms.txt` in row order, and for a
   WordPiece teacher a derived `vocab.txt` plus `tokenizer_config.json`.

The result is a **static, non-contextual embedding table**: embedding text is
tokenize, gather rows, weight, mean-pool, L2-normalize, with no forward pass
(StaticEmbeddingModel javadoc, ../reference/opennlp-embeddings-javadoc.md).

`family` is the string `"WordPiece"` or `"SentencePiece"`, taken straight from
`ModelDistiller.Result.family()` and copied onto the descriptor
(StaticModelArtifactStore.java:444; opennlp_training.proto:347 documents it as
"Tokenizer family detected from the teacher, e.g. \"wordpiece\"", which
disagrees in case with what the library returns).

## 4. FACT: output artifacts and where they live

Every artifact kind publishes through the same `VocabularyStore` seam
(opennlp-grpc-spi/src/main/java/org/apache/opennlp/grpc/spi/vocabulary/VocabularyStore.java),
rooted at the single config key `vocabulary.artifact_root` (README.md:510, :576).

| Kind | Path | Entries | Id prefix |
| --- | --- | --- | --- |
| `dictionaries` | `dictionaries/<id>/` | `entries.tsv`, `dictionary.pb` | `dictionary-<uuid>` |
| `vocabularies` | `vocabularies/<id>/` | `vocabulary.tsv`, `vocabulary.pb` | `vocabulary-<uuid>` |
| `models` | `models/<id>/` | the distilled files, `manifest.tsv`, `model.pb` | `static-model-<uuid>` |

(VocabularyArtifactStore.java:86-91, :345; StaticModelArtifactStore.java:88-91, :335.)

`manifest.tsv` is `name<TAB>size<TAB>sha256` per published file; the descriptor's
`artifact_hash` is the SHA-256 **of the manifest**, not of the model weights
(StaticModelArtifactStore.java:418-438; opennlp_training.proto:357).

Trained models are additionally materialized and verified into a local cache
directory, `training.model_cache_dir`, which defaults to a per-process temporary
directory and is rebuilt from the durable store at startup
(StaticModelArtifactStore.java:87, :670; README.md:592).

## 5. FACT: how a trained model becomes usable

- On publication the artifact is loaded with `StaticEmbeddingModel.load(cached)`
  and registered with `TrainedModelEmbeddingProvider`, which resolves trained
  model ids in front of the startup-configured embedding provider
  (StaticModelArtifactStore.java:339-345;
  opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/training/TrainedModelEmbeddingProvider.java:34-45).
- The artifact id **is** the embedding model id accepted by
  `EmbeddingSelector.model_id` for analysis, indexing and search
  (opennlp_training.proto:339).
- **No restart is needed.** The proto says so at opennlp_training.proto:32, the
  in-page help says "serves immediately" (index.html:877, :889), and
  `loadExistingModels` re-registers every published model on the next start
  (StaticModelArtifactStore.java:483). This is the opposite of the classic
  OpenNLP catalog models (parser, chunker, tokenizer, POS, lemmatizer, NER),
  which the Models and data tab flags as restart-required
  (opennlp-grpc-webapp-default/src/model-data-workbench.ts:640).
- The front end mirrors it: `onModelsChanged` pushes every trained model into
  the Analyze embedding selector as `"<displayName> (trained)"`
  (opennlp-grpc-webapp-default/src/main.ts:237). `Use in Analyze` selects the id
  and switches tabs (main.ts:244).
- Deleting a model unregisters the id and deletes the durable artifact
  (StaticModelArtifactStore.java:396). The UI `Delete` button does this with **no
  confirmation dialog** (vocabulary-trainer.ts:309).

## 6. FACT: what the tab does not do

- It never calls `/api/v1/dictionaries`. `readDictionaries` exists in
  vocabulary-trainer.ts:398 but only the Workflows tab wires it up
  (main.ts:325). The trainer seeds the selector with a single option
  `Corpus terms only` (vocabulary-trainer.ts:169) and appends dictionaries only
  as they are imported **in this browser session**. Reload the page and a
  dictionary imported five minutes ago is invisible and unusable.
- It never calls `/api/v1/download-vocabulary` for a vocabulary learned in an
  earlier session, for the same reason: `Learned vocabularies` is
  session-local. There is no `ListVocabularies` RPC at all
  (opennlp_vocabulary.proto:34 lists five RPCs; listing vocabularies is not one).
- It does not expose `StreamingTraining`, the bidirectional
  document-to-vocabulary-to-model-to-index RPC (opennlp_training.proto:59). The
  Workflows tab uses the unary path instead
  (opennlp-grpc-webapp-default/src/corpus-workflow.ts).
- It shows no `provenance_summary`, no `vocabulary_artifact_id`, no
  `explained_variance_ratio` and no `artifact_hash` in the model list, although
  all four are on the descriptor and three of the four are parsed into
  `TrainedModelSummary` (vocabulary-trainer.ts:54, :289).

## 7. OPINION: recommendations

**P1. Say which vocabulary a model was trained from.** The model row renders
`dim 256 · 4,812 terms · WordPiece · teacher all-minilm-l6-v2 · trained ...`
(vocabulary-trainer.ts:289). `vocabularyArtifactId` is on the wire
(opennlp_training.proto:343) but is not even parsed into `TrainedModelSummary`.
Without it a user with three trained models cannot tell which corpus produced
which. Add it to `readTrainedModel` and show the vocabulary display name.

**P1. List server-side dictionaries and vocabularies instead of session-local
ones.** Call `/api/v1/dictionaries` on `initialize` the way the Workflows tab
does (main.ts:325), and add a `ListVocabularies` RPC so `Learned vocabularies`
survives a reload. Today the second and third steps of a documented three-step
flow silently lose their inputs on refresh.

**P2. Surface `explainedVarianceRatio` and `artifactHash` in the model row, not
only in the one-shot progress log.** After a reload the "78.4% variance
retained" line is gone forever (vocabulary-trainer.ts:257).

**P2. Confirm before Delete.** `Delete` (vocabulary-trainer.ts:308) permanently
removes a durable artifact and un-serves a model id that indexes may still
reference. Every other destructive action in the app should be checked against
this, but this one has no guard at all.

**P3. Rename the tab heading.** The kicker is `Vocabulary to model`
(index.html:873) and the heading is `Trainer`; the tab button is `Trainer`
(index.html:53). "Trainer" is the least informative word available for a tab
that distills static embedding models. See findings/terminology.md.

## Questions for the lead

1. Should the trainer own dictionary import at all, or should step 1 move to
   Models and data and leave the trainer as "vocabulary to model"? The tab
   currently teaches three concepts before the first useful output.
2. Is a `ListVocabularies` RPC in scope? Without it the "Learned vocabularies"
   selector cannot be made durable, and the P1 fix above is blocked.
