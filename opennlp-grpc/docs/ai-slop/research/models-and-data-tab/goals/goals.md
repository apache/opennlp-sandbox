# Goals: Models & data tab

## P1

- [x] Brown out catalog cards when `installsEnabled` is false, with the reason
      inline; unit test for the disabled state; set `model.catalog_root` in the
      docker demos so installs work there.
- [x] Add `name-finder` to `roleLabel` (`model-data-workbench.ts:653`) and extend
      the role test to all 9 roles.
- [ ] Inbound jumps: Analyze "Needs model or data" rows and the Trainer no-teacher
      state jump here and scroll to the fixing card.
- [x] Outbound jumps after an immediate install: Analyze with the model preselected;
      Trainer after a teacher install.
- [x] Unlock tags per card derived from `role` (feature labels, tab, immediate or
      restart), using the table in `findings/unlocks-and-tags.md`.
- [x] Distinct install failure types on the wire (checksum, disk, network, path);
      free-space check before download; slot-occupancy check at install time.
- [x] Proto: `format`, `unlocks`, `requires_restart`, `files` on
      `ModelCatalogDescriptor`; format tags on the cards.
- [x] Roles and catalog entries for subword, WordNet and document categorizer
      (sentiment) models.

## P2

- [ ] Restart-required banner at the top of the app when any restart-only install
      is pending.
- [ ] Clear both panels on a catalog load failure.
- [ ] `opennlp-model.json` beside every trained and installed model; emit
      `opennlp-catalog.json` from `StandardModelCatalog` and diff it in a test.
- [ ] Rename "Server capability inventory" to "What this server can do";
      "Pinned model catalog" to "Model catalog (checksum pinned)".
- [x] e2e spec: readiness grid, catalog list, disabled install state (`e2e/models-data.spec.ts`).
- [ ] Unit tests for `configure()`, the copy button, the empty catalog.

## P3

- [ ] `FileModelCatalogProvider` reading a pinned, signed index URI.
- [ ] Export a catalog to S3 under `kind = "catalog"`.
- [ ] ORAS/OCI transport if a registry is ever in scope.
