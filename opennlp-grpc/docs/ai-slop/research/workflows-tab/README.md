# Workflows tab

Analysis of `findings/` for the tab labelled "Workflows" (`index.html:45`,
section `workflows-workbench`, controller `src/corpus-workflow.ts`).

## What the tab is

There is exactly one workflow: a six-stage run that reads pasted documents,
learns a vocabulary, distils a static embedding model from a teacher,
re-analyzes the documents with that model, loads the vectors into an in-memory
index, and runs a first search (`findings/journey-and-vocabulary.md` section 1).
Nothing is saved as a re-runnable definition, so the plural "Workflows" and the
word "workflow" itself (which GitHub Actions, Airflow and Temporal use for a
saved definition) promise something the tab does not have.

## Verdicts

1. **The tab is dead on the demo, and does not say so honestly.** With no
   writable artifact root the run button is disabled forever
   (`corpus-workflow.ts:153`) while two static badges still read
   "Automatic defaults" and "Defaults are ready" (`index.html:395,430`).
   Decision: add a degraded "analyze and index" mode that skips the vocabulary
   and distillation stages and uses an installed embedding model; stages 1, 4,
   5 and 6 need no artifact root. The demo instance has `minilm-gpu`, so the
   tab would work out of the box. Until then the badges must be driven by
   state, and the disabled button needs a reason and a jump to Models & data.

2. **The default vector storage cannot be checkpointed.** `flat_float` declares
   only VECTOR and LIVE, so "Save checkpoint" on Lifecycle fails with
   "Search provider instance 'flat_float' is not persistent"
   (`findings/journey-and-vocabulary.md` section 2.6). Decision: keep exact
   ranking as the default, but state the consequence next to the storage
   choice and let Lifecycle explain the failure with a link back. Flipping the
   default to TurboQuant is a ranking-quality trade the owner should make
   explicitly, so it is recorded as a question rather than a goal.

3. **Vocabulary.** The tab mixes an inherited search vocabulary (index,
   document, provider, alias) with an invented layer (workflow, workspace,
   checkpoint, seal). The researcher's table (`findings/journey-and-vocabulary.md`
   section 4) recommends deleting the invented layer. I agree for this tab's
   own strings; the cross-tab words (workspace, checkpoint, seal) are decided
   once in `../industry-terminology` and applied everywhere.
   Accepted for this tab: "Workflows" becomes "Build index"; "Workflow name"
   becomes "Index name"; "Your text collection" becomes "Your documents" (it
   collides with Lifecycle's Collections); "Max corpus terms" becomes
   "Max vocabulary terms" (it bounds the vocabulary request,
   `corpus-workflow.ts:195`).

4. **Cross-tab wiring is missing.** The tab has no outbound jump links, and
   `main.ts:373-376` refreshes Corpus search and Workspace search after a run
   but not Lifecycle, so the new index is invisible there until a manual
   refresh. Corpus search also lists the mutable index the run just built even
   though its heading says immutable (`server-search-workbench.ts:141` has no
   filter); that inconsistency is resolved in `../corpus-search-tab`.

5. **Tests.** Two behavioural unit tests for a 571-line controller, no gating
   or error-path coverage, and `e2e/workbench.spec.ts:46` asserts "Ready" so
   the suite is red against any server without a teacher. The one real
   end-to-end run is opt-in (`OPENNLP_E2E_WORKFLOW_WRITE=1`) and therefore
   never runs. The prioritised test list in `findings/test-coverage.md`
   section 4 is adopted as written.

## Open questions for the owner

- Is a saved, re-runnable workflow planned? If yes, keep the word reserved and
  still rename this tab after the object it builds.
- Should the tab own cleanup of the vocabulary and model artifacts it creates
  on every run? Today nothing removes them.
- Default storage: exact `flat_float` (not checkpointable) or TurboQuant
  (checkpointable, quantized ranking)?
