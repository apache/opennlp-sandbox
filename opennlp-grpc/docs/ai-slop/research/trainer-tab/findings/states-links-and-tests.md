# Trainer tab: gating, states, cross-tab links, and test coverage

## 1. FACT: the gating logic, in one place

`VocabularyTrainerWorkbench.initialize` fetches three endpoints in parallel and
computes one boolean (vocabulary-trainer.ts:141-175):

```
this.#writesEnabled = formats.writesEnabled && teachers.writesEnabled;
```

Both `writesEnabled` flags come from the same place: whether
`vocabulary.artifact_root` is configured
(VocabularyArtifactStore.java:85, :148; StaticModelArtifactStore.java:80, :185,
`writesEnabled()` returns `store != null`). They are never independently false.
`updateControls` then disables Import, Learn, Download TSV and Train whenever
`writesEnabled` is false or a request is in flight (vocabulary-trainer.ts:348).

## 2. FACT: the states a user can reach, with exact text

### 2.1 Loading

`Loading the trainer catalog.` (index.html:900)

### 2.2 No artifact root configured (the live demo instance is in this state)

Status line, in the `is-error` style:

> Training is disabled: the server has no vocabulary artifact root or no teachers.

(vocabulary-trainer.ts:160). Teacher selector shows `No teachers configured`
(vocabulary-trainer.ts:157), format selector may still show three formats,
`Imported dictionaries` shows `Corpus terms only` and is **enabled**
(vocabulary-trainer.ts:169), model list shows `No trained models yet.`
(vocabulary-trainer.ts:277). All four action buttons are disabled.

Verified live against http://127.0.0.1:7172, see
../reference/live-api-responses.md.

OPINION P1. That message is wrong, or at best conflates two causes. `writesEnabled`
is purely "no artifact root". The "or no teachers" clause describes a different
state that has its own message one branch below. A first-time operator reading
this cannot tell which of the two settings to change. Split it:

> Training is off: this server has no `vocabulary.artifact_root` configured, so
> it cannot store dictionaries, vocabularies or trained models. An operator sets
> it in the server configuration; see the README section "Import a dictionary
> and learn a vocabulary".

### 2.3 Artifact root configured, no teachers

> No teachers are configured; add training.teacher entries.

(vocabulary-trainer.ts:165). Note the buttons are **not** disabled in this
branch: `writesEnabled` is true, so Import, Learn and Train are all clickable.
Pressing Train with an empty teacher selector produces:

> Learn a vocabulary and select a teacher first.

(vocabulary-trainer.ts:243).

OPINION P1. This is the browned-out state the brief asks about, and it is the
most likely real-world state, because the artifact root is easy to set and a
teacher is a 90 MB or 470 MB download. The message names a config key and stops.
It should name the other route:

> No teachers are configured. Install `all-MiniLM-L6-v2 teacher` from
> **Models and data**, or add a `training.teacher.<id>.ref` entry to the server
> configuration. [Open Models and data]

The link target exists: installing a catalog teacher calls back into
`vocabularyTrainer.initialize()` (main.ts:216, model-data-workbench.ts:498), so
the round trip already works. Only the link is missing.

### 2.4 Ready

> Paste corpus text to learn a vocabulary. A dictionary is optional.

(vocabulary-trainer.ts:167)

### 2.5 Work in progress

Three distinct start messages, all in the same status line, with every button
disabled for the duration (`run`, vocabulary-trainer.ts:331):

- `The server is importing the dictionary.`
- `The server is learning the vocabulary.`
- `The server is distilling the static model.`
- `Downloading the vocabulary TSV.`
- `Deleting the model.`

During a distillation the progress log (`#trainer-progress-log`,
index.html:950) fills with the server's lines, one per distillation phase and
one per forward-pass batch (../reference/opennlp-embeddings-javadoc.md).

OPINION P2. There is no spinner, no elapsed timer, no cancel button and no
indication of expected duration. The e2e spec allows 1,200,000 ms for a two
document workflow (e2e/workbench.spec.ts:57), which is a twenty minute budget.
A user who clicks Train on a 10,000 term vocabulary against MiniLM sees a status
line and a growing log with no idea whether that is normal. The gRPC layer
already supports client cancellation
(OpenNlpModelTrainingServiceImpl.java:238, `setOnCancelHandler`), and
`StaticModelArtifactStoreTest.cancellationAfterDistillationPreventsArtifactPublication`
proves the server honours it. Only the button is missing.

### 2.6 Training failed

`run` catches everything and writes the message into the status line in error
style (vocabulary-trainer.ts:341), falling back to
`The trainer request failed.` The progress log keeps whatever lines arrived
before the failure, because `train` only clears it at the start
(vocabulary-trainer.ts:247).

Error text reaching the user comes from the gRPC status description, mapped to
HTTP by `GrpcHttpStatusMapper` and re-thrown by the front end. Real examples
from the service:

- `RESOURCE_EXHAUSTED` -> `concurrent trainings exceed configured maximum 1`
  (OpenNlpModelTrainingServiceImpl.java:264)
- `NOT_FOUND` -> `Unknown vocabulary artifact '<id>'`
  (UnknownVocabularyArtifactException, thrown from VocabularyArtifactStore)
- `FAILED_PRECONDITION` -> the message built from
  `vocabulary.artifact_root is not configured`
  (StaticModelArtifactStore.java:602)
- `INTERNAL` -> `TrainStaticModel failed`, with the cause logged server-side and
  deliberately not sent to the client
  (OpenNlpModelTrainingServiceImpl.java:288)

A mid-stream failure is appended as an NDJSON error object and turned into a
thrown `Error` by `trainStaticModel` (api.ts:396); a stream that ends with no
model line yields `The training stream ended without a model.` (api.ts:406).

OPINION P2. `TrainStaticModel failed` is the message a user sees for every
IOException, which includes the most likely real failure: the teacher could not
be downloaded, or the ONNX runtime could not load. Not leaking a stack trace is
right, but the class of failure should survive. At minimum distinguish "the
teacher could not be resolved" from "publication failed".

### 2.7 Zero trained models

`No trained models yet.` (vocabulary-trainer.ts:277), rendered as a bare
paragraph in `#trainer-model-list`.

OPINION P2. This is the emptiest empty state in the tab and it does the least
work. It should say what a trained model would give the user and point at the
alternative:

> No trained models yet. A trained model is a static embedding table you can
> select in **Analyze**, index with, and search with. If you only need general
> English embeddings, install **Potion Base 8M** from Models and data instead of
> training your own.

`potion-base-8m` is already in the catalog with role
`MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING` (StandardModelCatalog.java:204).

### 2.8 Success

> Model '<name>' is serving as embedding model '<id>'. Select it in Analyze,
> then index and search with it.

(vocabulary-trainer.ts:262). This is the single best string on the tab: it names
the next two actions. It is plain text, so neither "Analyze" nor "Workspace
search" is clickable.

## 3. FACT: cross-tab links

The whole application contains exactly three `data-workbench-jump` buttons
(index.html:555, :592, :735) and **none of them is on the Trainer tab**.

| Where the user needs to go | Why | Link today |
| --- | --- | --- |
| Models and data | to install a teacher, which is the only way to unblock the tab on a stock server | none |
| Analyze | to use the model just trained | button per model row, `Use in Analyze` (vocabulary-trainer.ts:298), which switches tabs programmatically (main.ts:246). This one works. |
| Workspace search | to index and search with the model, which the success message tells the user to do | none |
| Lifecycle | to watch vocabulary drift on the vocabulary that produced the model, and to rebuild an index with a newly trained model | none, in either direction |
| Workflows | the guided one-click version of these same three steps | none |
| Models and data | to compare against the pre-distilled Potion models | none |

OPINION P1. Add a jump to **Models and data** in the "no teachers" message
(section 2.3). Without a teacher the tab is inert, and the fix lives one tab
away.

OPINION P2. Add jumps to **Analyze** and **Workspace search** in the success
message (section 2.8). The text already names both tabs.

OPINION P2. Add a jump from the Lifecycle tab's `Vocabulary drift` panel back to
the Trainer, because "coverage dropped" has exactly one remedy and it is on this
tab. The Lifecycle help already describes the loop in prose
(index.html:983-986) with no way to walk it.

OPINION P3. The Trainer and the Workflows tab do the same three steps with
different labels and different defaults, and neither mentions the other. One
sentence in each help callout would do.

## 4. FACT: model gating table

| Feature | Requires | What the user sees today | What a browned-out state should say |
| --- | --- | --- | --- |
| Import dictionary (`#trainer-import-button`) | `vocabulary.artifact_root` | button disabled, generic status line | "Artifact storage is off. An operator must set `vocabulary.artifact_root`." |
| Learn vocabulary (`#trainer-learn-button`) | `vocabulary.artifact_root` | same generic status line | same |
| Download TSV (`#trainer-download-tsv-button`) | a vocabulary selected **in this session** | disabled with `title` = "Learn and select a vocabulary first; the TSV export needs one." (vocabulary-trainer.ts:356) | good today; the tooltip is the best gating affordance on the tab |
| Train model (`#trainer-train-button`) | artifact root **and** at least one teacher | enabled when the root is set but no teacher exists; clicking gives "Learn a vocabulary and select a teacher first." | disable it, and say "Install a teacher from Models and data" with a link |
| Use in Analyze | the model id being offered by `AnalysisControls` | falls back to "'<name>' is not offered as an embedding model on this server." (main.ts:249) | acceptable; this path should be unreachable |
| PCA dimensions over `maxPcaDims` | server bound of 512 | no client validation; server rejects with `INVALID_ARGUMENT` | set the input's `max` from `/api/v1/teachers`, which already returns `maxPcaDims` |
| Dictionaries imported in an earlier session | a `ListDictionaries` call the tab never makes | silently absent from the selector | call `/api/v1/dictionaries` on initialize |
| Vocabularies learned in an earlier session | a `ListVocabularies` RPC that does not exist | silently absent from the selector | needs an RPC |

## 5. FACT: tests that exercise this tab

### Front-end unit tests, opennlp-grpc-webapp-default/test/vocabulary-trainer.test.ts

| Test | Feature covered |
| --- | --- |
| `reads formats, teachers, and models defensively` (:53) | `readDictionaryFormats`, `readTeachers`, `readStaticModels` |
| `splits corpus text into blank-line-separated documents` (:90) | `corpusDocuments` |
| `encodes bytes as protobuf JSON base64` (:98) | `base64` |
| `initializes the catalog and reports existing models` (:158) | `initialize` happy path |
| `names teachers without their filesystem reference, kept as a tooltip` (:172) | teacher option label and `title` |
| `renders trained models with their name, training time, and a Use in Analyze action` (:184) | `renderModels` |
| `shows a waiting state and live corpus document and byte counts` (:201) | `renderCorpusStats` |
| `disables training when the server has no artifact root` (:217) | state 2.2 |
| `learns a vocabulary and trains a model with streamed progress` (:232) | steps 2 and 3 end to end |
| `learns directly from the corpus when no optional dictionary is selected` (:264) | corpus-only path |
| `restores the Copy id label after confirming the copy` (:281) | `Copy id` |
| `disables the TSV export with a reason until a vocabulary is selected` (:308) | Download TSV gating |
| `surfaces training failures in the status line` (:328) | state 2.6 |

### Front-end API tests, opennlp-grpc-webapp-default/test/api.test.ts

`uses the vocabulary and training endpoints` (:180),
`downloads vocabulary TSV text` (:237),
`streams training progress and resolves with the terminal model` (:247),
`rejects when the training stream ends with an error line` (:270),
`rejects a pre-stream training failure with the gateway message` (:286).

### Playwright, opennlp-grpc-webapp-default/e2e/workbench.spec.ts

One test touches the Trainer tab:
`disables the TSV export with a reason until a vocabulary exists` (:85).
The live training path is exercised only through the **Workflows** tab and only
when `OPENNLP_E2E_WORKFLOW_WRITE=1` (:56-75), so it does not run by default.

### Gateway, opennlp-grpc-webapp/src/test/java/.../GrpcJsonVocabularyApiTest.java

`composesDictionaryImportFromOneJsonUpload` (:55),
`composesVocabularyLearningFromOneJsonUpload` (:74),
`downloadsVocabularyTsvBytes` (:93),
`listsFormatsTeachersAndModels` (:106),
`deletesStaticModelsThroughJson` (:124),
`streamsTrainingProgressLinesThenTheTerminalModel` (:136),
`returnsBufferedErrorWhenTrainingFailsBeforeStreaming` (:168),
`appendsAnErrorLineWhenTrainingFailsMidStream` (:183).

### Service

`StaticModelArtifactStoreTest`: train/publish/serve/reload (:57), no artifact
root (:103), teacher, vocabulary and PCA bounds (:120), tampered artifact
rejection (:146), delete (:165), failed deletion (:185), publication listener
failure (:211), serving failure rollback (:232), cancellation before publication
(:257).

`OpenNlpModelTrainingServiceImplTest`: teachers listed even when writes are
disabled (:195), train and stream (:221), gRPC status mapping (:247), delete
(:270), cancellation (:292), the shared admission bound (:326).

`TrainedModelEmbeddingProviderTest`: delegation (:42), trained models resolve
first (:55), unregister and duplicate rejection (:86).

`VocabularyArtifactStoreTest`: import/learn/publish/reload (:48), corpus-only
(:90), no artifact root (:118), bounds (:128, :154), tamper rejection (:172),
delete (:197).

`OpenNlpVocabularyServiceImplTest`: formats when writes are disabled (:63),
streaming boundaries (:79, :126), frame ordering and size (:152), writes
disabled (:177), typed statuses (:191), concurrency admission (:226), internal
failure masking (:254).

`opennlp-grpc-store-s3`: `S3VocabularyStoreTest`, `S3VocabularyStoreProviderTest`.

## 6. FACT: features with no test I could find

1. **`downloadTsv` file save.** `saveTextFile` (vocabulary-trainer.ts:598)
   creates a blob URL and clicks an anchor. The e2e spec only checks the
   disabled state; no test asserts the file name `<artifactId>.tsv` or that the
   blob URL is revoked.
2. **`deleteModel` from the UI.** `StaticModelArtifactStore.deleteModel` is well
   tested server-side, but no front-end test clicks the `Delete` button
   (vocabulary-trainer.ts:308) or asserts the `Deleted <id>.` status.
3. **`importDictionary` from the UI.** `vocabulary-trainer.test.ts` never
   exercises `#trainer-import-button`. The file-read path, the `formatSelector`
   standard-vs-custom branch (vocabulary-trainer.ts:548) and the "Choose a
   dictionary file first." error (vocabulary-trainer.ts:180) are untested at the
   workbench level, although `base64` alone is tested.
4. **The `No teachers are configured` branch** (vocabulary-trainer.ts:164). The
   test suite covers `writesEnabled === false` but not
   `writesEnabled === true && teachers.length === 0`, which is the state a real
   operator hits first.
5. **`onModelsChanged` publishing into the Analyze selector.** The callback is
   invoked in `renderModels` (vocabulary-trainer.ts:313) and wired in
   main.ts:237 to `publishRuntimeEmbeddingModels`. No test covers the merge of
   catalog and trained embedding models, so a trained model silently failing to
   appear in Analyze would not be caught.
6. **`onUseInAnalyze` failure branch.** main.ts:249 renders
   `'<name>' is not offered as an embedding model on this server.` and nothing
   tests it.
7. **`copyText` failure branch.** `Copy failed` (vocabulary-trainer.ts:594) is
   untested; only the success path is (`restores the Copy id label`).
8. **`boundedInt` on negative or non-numeric PCA input** (vocabulary-trainer.ts:553).
   A value of `-1` falls back to `0`, which silently becomes 256 on the server.
   No test.
9. **`asRatio` clamping** (vocabulary-trainer.ts:508). An
   `explainedVarianceRatio` above 1 or below 0 becomes 0, which would render
   `0.0% variance retained`. No test.
10. **The `busy` re-entrancy guard** (vocabulary-trainer.ts:332). Clicking Train
    twice quickly is silently ignored; no test asserts that.
11. **Playwright coverage of the Trainer tab beyond the one TSV tooltip.** There
    is no e2e assertion of the status line in the disabled state, of the teacher
    selector's `No teachers configured` option, or of the empty model list.

## Questions for the lead

1. Should Train be disabled, rather than clickable-and-then-scolded, when no
   teacher exists? That is a one-line change and it removes the tab's worst
   dead end.
2. Is a Cancel button in scope? The server already supports cancellation and has
   a test for it; only the client affordance is missing.
3. Should `Delete` get a confirmation? It destroys a durable artifact and
   un-serves a model id that a live index may still reference.
