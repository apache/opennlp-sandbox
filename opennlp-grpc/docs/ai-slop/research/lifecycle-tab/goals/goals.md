# Goals: Lifecycle tab

## P1

- [x] Make the `flat_float` provider persistable (declare PERSISTENT, write and
      reload the array); red service test first. Until then gate checkpoint and
      read-only buttons on the capability with the reason shown.
- [x] Empty state: "No live indexes yet. Build one on Build index or add analyzed
      documents on Live index search." with two `data-workbench-jump` buttons.
- [x] Rename "Save checkpoint" to "Save to disk" and "Seal as read-only" to
      "Make read-only"; fact row "Read-only: yes/no";
      helper text "accepts no further documents, stays searchable, cannot be undone".
- [x] Keep read-only live indexes listed (flagged read-only) instead of filtering them
      out (`lifecycle-workbench.ts:129`); confirmation links to Live index search.
- [x] Define "collection" under the heading and in a flyout
      (`findings/state-machine-and-vocabulary.md` section 3.3).
- [x] Rename the panel "Vocabulary drift" to "Vocabulary coverage"; add the flyout;
      threshold label "Alert after this many out-of-vocabulary terms"; show
      "not measured" when no vocabulary artifact is set.
- [x] Validate the vocabulary artifact id before saving a collection: "No vocabulary
      artifact with that id. Learn one on the Trainer tab." with a jump.
- [x] Tests: `test/lifecycle-workbench.test.ts` (empty state, capability gating,
      post-seal listing); gateway test for 412 on a non-persistent provider.
- [x] Correct `docs/rfc/opennlp-search-query-model.md:146` (seal sets a flag on a
      checkpoint; it does not produce a bundle).

## P2

- [ ] Tooltips for every control (`findings/state-machine-and-vocabulary.md` section 4).
- [x] Dictionary artifact field becomes a select fed by `/api/v1/dictionaries`.
- [x] `ListVocabularies` RPC and gateway route (`/api/v1/vocabularies`), then a vocabulary
      artifact picker; a saved id the server no longer lists stays selectable, flagged.
- [ ] Jumps: rebuild empty state to Trainer, post-rebuild to Workspace search,
      alias list note "use this name anywhere an index id is accepted".
- [x] Detect HTTP 501 (dynamic indexing disabled) at load and brown out the panel.
- [ ] Translate the remaining server errors at the boundary (404 unknown vocabulary,
      429 distinct-term limit, 412 not persistent).
- [ ] After a Workflows build, offer "Save this workspace" inline.
- [ ] Rename "Provider instances" to "Vector storage available on this server";
      "Point alias at workspace" to "Point alias here".
- [x] e2e spec for the tab (checkpoint, read-only, alias, collection): `e2e/lifecycle.spec.ts`
      builds its own index through the gateway and removes it afterwards.

## P3

- [ ] Say why only one storage option is listed when the TurboQuant add-on is absent.
- [ ] Friendlier alias-limit and term-limit messages.
