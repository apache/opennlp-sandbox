# Workflows tab: model gating, empty states, and failure text

Scope: every path on `section#workflows-workbench` (`index.html:384-543`) that depends on server
configuration, plus the states a first-time user actually lands in.

FACT sections are observed behaviour with citations. OPINION sections are recommendations with a
priority.

---

## 0. FACT: on the live demo instance today, this tab cannot run at all

`GET /api/v1/teachers` on the running instance returns:

```json
{"maxPcaDims":512,"maxConcurrentTrainings":1}
```

(captured 2026-08-28, full transcript in `../reference/live-instance-state.md`)

Protobuf JSON omits defaults, so `writesEnabled` is absent and false and `teachers` is absent and
empty. `readTeachers` (`src/vocabulary-trainer.ts:414-430`) returns
`{ teachers: [], writesEnabled: false }`. `CorpusWorkflowWorkbench.initialize` then sets:

```ts
this.#ready = teachers.writesEnabled && teachers.teachers.length > 0;
```
(`src/corpus-workflow.ts:153`)

`#ready` is false, so `#runButton.disabled` is permanently true
(`src/corpus-workflow.ts:491-492`), and the status line reads:

> Training is unavailable because this server has no writable artifact root or teacher model.

This is the entire tab. Six stage cards, nine inputs, two result panels, all inert.

**The demo instance a reader is most likely to open shows a dead tab.** Priority to fix the
messaging: **P1**.

---

## 1. FACT: gating matrix

| # | Feature | Element | Required | User sees today | Where |
| --- | --- | --- | --- | --- | --- |
| 1 | The whole tab | `#workflow-run-button` | `vocabulary.artifact_root` set **and** at least one `training.teacher.<id>.ref` | `Training is unavailable because this server has no writable artifact root or teacher model.` and a disabled button, forever | `corpus-workflow.ts:153-157`, `491-492` |
| 2 | Resource discovery | all three selects | `/api/v1/teachers` and `/api/v1/search-providers` reachable | `Could not load workflow resources.` or the raw fetch error | `corpus-workflow.ts:158-161` |
| 3 | Vocabulary source | `#workflow-dictionary-select` | An imported dictionary artifact | Only `Corpus terms only (default)`, no explanation that more can exist | `corpus-workflow.ts:398-406` |
| 4 | Embedding teacher | `#workflow-teacher-select` | `training.teacher.<id>.ref` naming a local dir or a model id | `No teacher configured`, select disabled, no link to fix it | `corpus-workflow.ts:415-418` |
| 5 | Vector storage | `#workflow-provider-select` | A provider instance with `vector` and `live` capabilities and a `standard` enum | Falls back to a synthetic `Exact flat float` option even if the server offers none | `corpus-workflow.ts:428-430` |
| 6 | Analyze stages 1 and 4 | `#workflow-stages` | `analysisControls.configure()` must have completed | Stage 1 flips to error with `Analysis capabilities are still loading.` | `src/main.ts:343-344` |
| 7 | Embed stage 4 | stage `embed` | Server must advertise `PIPELINE_STEP_EMBED`; the trained model must serve | Stage 4 errors, or stage 5 errors with `Embedded analysis returned no indexable chunk groups.` | `analysis-config.ts:245-248`, `corpus-workflow.ts:519-521` |
| 8 | Index stage 5 | stage `index` | `search.dynamic.enabled` not false | The gateway error text, verbatim, in the stage line and the status line | `corpus-workflow.ts:231-235` |
| 9 | Saving the result later | Lifecycle `Save checkpoint` | `search.persist.root` **and** a `PERSISTENT` provider instance | Failure only on the Lifecycle tab, after the fact | `DynamicSearchIndexRegistry.java:518-527` |

### 1.1 Exact server-side error strings behind rows 1, 8 and 9

| Condition | Message the user ends up reading |
| --- | --- |
| No artifact root, learn-vocabulary attempted | `vocabulary.artifact_root is not configured; vocabulary writes are disabled` (`OpenNlpVocabularyServiceImpl.java:56-58`) |
| No artifact root, training attempted | `vocabulary.artifact_root is not configured; model training is disabled` (`StaticModelArtifactStore.java:601-603`) |
| Persistence root unset | `Index persistence is not configured; set search.persist.root` (`DynamicSearchIndexRegistry.java:520-521`) |
| Flat float index, checkpoint attempted | `Search provider instance 'flat_float' is not persistent` (`DynamicSearchIndexRegistry.java:524-526`) |

All four are gRPC status descriptions that reach the browser verbatim through
`responseError` (`src/api.ts:546-561`) and are printed unchanged into `#workflow-status`
(`corpus-workflow.ts:235`). They name **server configuration keys** to a user who is looking at a
web page.

---

## 2. FACT: the four empty and browned-out states, verbatim

### 2.1 No teacher and no artifact root (the live demo state)

- `#workflow-status`: `Training is unavailable because this server has no writable artifact root or teacher model.` with class `is-error` (`corpus-workflow.ts:156-157`, `496-499`).
- `#workflow-teacher-select`: one option, `No teacher configured`, disabled (`corpus-workflow.ts:416-418`).
- `#workflow-run-button`: disabled (`corpus-workflow.ts:491`).
- `#workflow-dictionary-select`: **enabled** with `Corpus terms only (default)` (`corpus-workflow.ts:405`).
- `#workflow-provider-select`: **enabled** with the real provider list (`corpus-workflow.ts:431`).
- The `Automatic defaults` badge (`index.html:395`) and the `Defaults are ready` pill (`index.html:430`) **still say exactly that**. They are static markup; no code in `src/` writes to either (verified by grep). So the tab simultaneously says "Defaults are ready" and "Training is unavailable".

Problems with this state:

1. It names two server configuration concepts ("writable artifact root", "teacher model") with no
   definition, no link, and no instruction.
2. It does not say **who** can fix it (an operator, not the reader) or **how**.
3. It leaves two of three selects enabled, which reads as "some of this works".
4. Nothing points at the **Models & data** tab, which is where a teacher model would be installed
   from the pinned catalog (`index.html:790-868`).
5. The two "everything is fine" badges are not cleared.

### 2.2 No documents pasted

- `#workflow-corpus-stats`: `Add text to preview the workflow batch.` (`index.html:408`, rewritten identically at `corpus-workflow.ts:488`).
- `#workflow-run-button`: disabled with **no title attribute and no reason given** (`corpus-workflow.ts:491-492`). A disabled button with no tooltip is the pattern the project explicitly avoids elsewhere: the Trainer's TSV button carries a reason in its `title` and there is an e2e test asserting it (`e2e/workbench.spec.ts:85-89`).
- `#workflow-analysis-results`: `Run the workflow to inspect each analyzed document.` (`index.html:528`).
- `#workflow-search-heatmap`: `The search stage will shade every returned document chunk.` (`index.html:536`).
- `#workflow-artifacts`: `No artifacts built yet.` (`index.html:518`).
- There is **no sample corpus button** on this tab, while Analyze has three (`index.html:214-216`).

### 2.3 Documents pasted but the query cleared

`#workflow-run-button` is disabled because `!this.#query.value.trim()`
(`corpus-workflow.ts:492`). The corpus stats line still reads `N documents ready`, so the tab looks
ready and the button is dead with no explanation. If the user presses nothing and instead deletes
the query then re-presses, `run()` short-circuits with
`Add at least one document and a first search query.` (`corpus-workflow.ts:172`), but that path is
only reachable when the button was enabled at click time.

### 2.4 A stage fails mid run

`corpus-workflow.ts:231-235`:

```ts
} catch (error) {
  if (this.#activeStage) {
    this.fail(this.#activeStage, errorMessage(error, "Stage failed."));
  }
  this.setStatus(errorMessage(error, "The workflow did not complete."), true);
}
```

The failing stage gets `data-state="error"` and the raw message; the status line gets the same raw
message. Earlier stages keep `data-state="complete"`.

**What is not offered:** no retry, no resume, and no cleanup. If stage 5 fails, the vocabulary and
the model from stages 2 and 3 are already written to the artifact store and stay there. Pressing
the button again calls `resetStages()` (`corpus-workflow.ts:179`) and re-runs **all six stages**,
learning a second vocabulary and training a second model from scratch. There is no way to reuse
the ones that succeeded. Priority: **P2**.

---

## 3. FACT: "when the index already exists"

There is no such state. The tab never reuses an index.

`corpus-workflow.ts:218-223` posts `indexDocuments` with `displayName` and no `indexId`, and the
contract says the server "creates an opaque workspace id" when `index_id` is absent
(`opennlp-grpc-api/src/main/proto/.../opennlp_search.proto:49-51`). The generated id is
`workspace-<uuid>` (`DynamicSearchIndexRegistry.java:1075`).

Consequences:

- Running twice with the untouched default name leaves two indexes both labelled
  `My text workflow` (`index.html:413`), distinguishable only by uuid. Both appear in the Workspace
  search picker as `My text workflow - N chunks` (`semantic-workbench.ts:189-193`) and in the
  Lifecycle picker as `My text workflow (workspace-<uuid>)` (`lifecycle-workbench.ts:152`).
- The tab shows no warning, no "an index with this name already exists", and no list of what
  previous runs created. `#workflow-artifacts` is overwritten on each successful run
  (`corpus-workflow.ts:391`), so the previous run's ids are simply lost from the UI.
- There is no delete affordance anywhere on this tab. `deleteSearchIndex` exists in `src/api.ts:195`
  and `deleteStaticModel` at `src/api.ts:371`, but neither is reachable from here.

Priority: **P2** for a duplicate-name warning, **P2** for surfacing prior runs.

---

## 4. FACT: gating that is checked in the wrong place

| Check | Where it happens today | Where the user makes the decision |
| --- | --- | --- |
| Provider must be `PERSISTENT` to checkpoint | Server, at `Save checkpoint` time on the Lifecycle tab (`DynamicSearchIndexRegistry.java:524`) | `#workflow-provider-select` on this tab, minutes earlier (`index.html:449`) |
| `search.persist.root` must be set | Server, same moment | same |
| Teacher must exist | This tab, at load | Correct place |
| Artifact root must be writable | This tab, at load, **but only reported via the teachers call** | Correct place, wrong source: `writesEnabled` is read off `ListTeachers`, so a server with an artifact root but no teacher, and a server with a teacher but no artifact root, produce the identical message |

That last row is worth calling out: `corpus-workflow.ts:153-157` collapses two independent
misconfigurations into one sentence with an "or" in it. The user cannot tell which one is wrong,
and neither can an operator reading a screenshot. `/api/v1/static-models` also carries
`writesEnabled` (`OpenNlpModelTrainingServiceImpl.java:339`) and could disambiguate, but the tab
does not call it. Priority: **P2**.

---

## 5. OPINION: proposed browned-out states

The project already has a good pattern to copy: `ServerSearchWorkbench` sets both a status line
**and** a descriptive sentence naming who must act, `An operator must configure an immutable index
bundle at startup.` (`src/server-search-workbench.ts:146`). This tab has no equivalent.

### 5.1 No teacher configured (P1)

Replace `Training is unavailable because this server has no writable artifact root or teacher
model.` (`corpus-workflow.ts:156`) with a two part state:

- Status line: `This server has no embedding teacher installed, so the model step cannot run.`
- A sentence under it, with a jump: `Install one from the pinned catalog on Models & data, or ask
  the operator to set training.teacher.<id>.ref.` where `Models & data` is a
  `data-workbench-jump="models"` button, matching the existing bridges at `index.html:555` and
  `index.html:735`.
- Disable **all three** selects, not just the teacher one, so the tab reads as one blocked unit.
- Blank or repoint the `Automatic defaults` badge (`index.html:395`) and the `Defaults are ready`
  pill (`index.html:430`); today they contradict the error.
- Give `#workflow-run-button` a `title` naming the blocker, matching the Trainer TSV button
  precedent tested at `e2e/workbench.spec.ts:85-89`.

### 5.2 No writable artifact root (P1)

Distinguish it from 5.1 by reading `writesEnabled` separately:

- Status line: `This server cannot save vocabularies or models, so the workflow cannot run.`
- Under it: `An operator must set vocabulary.artifact_root and restart the server.`
- No jump, because no tab can fix this. Saying so is the point.

### 5.3 No documents pasted (P1)

- Keep `Add text to preview the workflow batch.` but add a sample loader button beside the
  textarea reusing `loadAliceDemo` from `src/demo-data.ts`, so the tab is one click from a
  demonstration. This is the single highest-value change on the tab.
- Add `title="Paste at least one document first"` to the disabled run button.

### 5.4 Flat float chosen, which cannot be saved (P1)

Under `#workflow-provider-select`, replace `Exact storage is the default; choose TurboQuant when
the server offers it.` (`index.html:452`) with:

> Exact flat float keeps full precision but lives only in memory: the index is lost on restart and
> cannot be checkpointed on the Lifecycle tab. TurboQuant quantizes the vectors and can be saved.

and, when the selected instance lacks the `persistent` capability, show a persistent note in the
`#workflow-artifacts` strip after the run: `This index is in memory only.` with a
`data-workbench-jump="lifecycle"` link for the TurboQuant case.

### 5.5 A stage failed (P2)

- Keep the raw server message, it is genuinely useful, but prefix it with the stage name so the
  status line and the stage line are not identical strings.
- Add `Retry from this stage` when the failing stage is 4, 5 or 6, since the vocabulary and model
  artifact ids are still in `#vocabulary` and `#model` (`corpus-workflow.ts:124-125`).
- When stages 2 and 3 succeeded but a later stage failed, say so: `The vocabulary and model were
  created and kept.` with their ids, so the user knows something durable happened.

### 5.6 Discovery failed (P3)

`Could not load workflow resources.` (`corpus-workflow.ts:160`) does not say the service is
unreachable versus misconfigured. The Analyze tab already distinguishes these
(`src/main.ts:414-424`, `Offline` / `The web interface is running, but the analysis service could
not be reached.`). Reuse that wording.

---

## 6. OPINION: priority summary

| Priority | Item | Section |
| --- | --- | --- |
| P1 | Teacher-missing state names a config key, offers no fix, no link to Models & data | 2.1, 5.1 |
| P1 | `Automatic defaults` and `Defaults are ready` are hardcoded and contradict the error state | 2.1, 5.1 |
| P1 | Flat float default cannot be checkpointed, and nothing says so until Lifecycle fails | 1 row 9, 4, 5.4 |
| P1 | No sample corpus on the one tab whose whole purpose is a demonstration | 2.2, 5.3 |
| P1 | Disabled run button gives no reason, against the project's own tested precedent | 2.2, 5.3 |
| P2 | Two distinct misconfigurations share one "or" message | 4, 5.2 |
| P2 | A mid-run failure strands durable artifacts with no retry and no cleanup | 2.4, 5.5 |
| P2 | Every run creates a new index; duplicate names are silent | 3 |
| P2 | Prior runs are not listed anywhere on the tab | 3 |
| P3 | Discovery failure text is less informative than the Analyze tab's | 5.6 |

---

## Questions for the lead

1. Should the tab hard-block when `writesEnabled` is false, as it does now, or degrade to a
   "analyze and index only" mode using an already-installed embedding model from Models & data?
   Stages 1, 4, 5, 6 need no artifact root at all; only stages 2 and 3 do. A four stage degraded
   run would make the tab usable on the demo instance today.
2. Is the demo instance meant to ship with a teacher configured? If yes this is a packaging bug,
   not a copy bug, and the priorities above shift.
3. Should `Save checkpoint` on Lifecycle be disabled with a reason when the selected index's
   provider lacks the `persistent` capability, rather than failing at the server? The capability
   list is already loaded there (`lifecycle-workbench.ts:182-202`).
