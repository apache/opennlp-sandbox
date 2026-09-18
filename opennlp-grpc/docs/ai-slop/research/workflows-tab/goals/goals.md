# Goals: Workflows tab

Priorities: P1 blocks or misleads a first-time user, P2 worth doing, P3 polish.
Cross-tab renames (workspace, checkpoint, seal) are listed in
`../../industry-terminology/goals` and not repeated here.

## P1

- [x] Drive "Automatic defaults" / "Defaults are ready" badges from state; show the
      real reason on the disabled run button (`index.html:395,430`, `corpus-workflow.ts:153`).
- [x] Split the "no writable artifact root or teacher model" message into two
      states; the teacher state links to Models & data with `data-workbench-jump="models"`.
- [x] Degraded mode: when no teacher or artifact root exists but an embedding model
      is installed, run stages 1, 4, 5, 6 only (analyze, index, search).
- [x] Add a "How to use" `details.help-callout` (every other tab has one) with the
      explainer drafted in `findings/journey-and-vocabulary.md` section 5.
- [x] Add a sample corpus button (Alice and Pride and Prejudice chapters are already
      bundled for the Analyze tab).
- [ ] State next to the storage choice that exact storage cannot be saved to disk.
- [x] Refresh the Lifecycle picker in `onIndexChanged` (`main.ts:373-376`).
- [x] Rename tab and headings: "Workflows" to "Build index", "Workflow name" to
      "Index name", "Your text collection" to "Your documents", "Max corpus terms"
      to "Max vocabulary terms".
- [x] Tests: gating states, failing-stage retention, e2e skip guard when no teacher
      (`e2e/workbench.spec.ts:44-54`), help-callout presence.

## P2

- [ ] Warn on duplicate index names; list prior runs on the tab.
- [ ] Retry or clean up after a mid-run failure that stranded artifacts.
- [ ] Tests: duplicate-run behaviour, top-k clamp branch, provider capability
      filter, empty-heatmap message, numeric fallbacks.

## P3

- [ ] Discovery failure text on par with the Analyze tab.
- [ ] Tests: document naming order, result-tab switching, Lifecycle picker after a run.
