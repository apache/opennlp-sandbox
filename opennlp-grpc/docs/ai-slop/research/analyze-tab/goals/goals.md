# Goals: Analyze tab

## P1

- [x] Sentiment polarity from ordinal labels (see `../../test-coverage/goals`).
- [x] "Add to server workspace" reports on this tab (`#form-status`) and offers a
      jump to Live index search; rename to "Add to live index".
- [x] "Open" on a live-index hit calls `workbenchNavigation.show("analysis")`.
- [x] Browned-out feature chips plus the three-outcome explanation panel with
      `data-workbench-jump="models" data-workbench-focus="<step>"`.
- [x] One size threshold for JSON view, Copy JSON and Download .pb; warn before
      analyzing above `capabilities.maxTextBytes` with embeddings on.
- [x] Layer titles from `identity.standard` and qualifier (`document-shape.ts:318-326`).
- [x] Renames: Normalization alignment, Typed annotations, Chunk groups, Phrase
      chunks (shallow parse), Passage chunking legend, Model packs, Preset.
- [x] Tests: the seven "not configured" error texts; heatmap, graph and chunk views
      in the browser; the export and import flows.

## P2

- [x] "Download .pb" server-side: `POST /api/v1/analyze-protobuf` returns the serialized
      response without printing JSON; the tab re-runs the stored request through it when the
      reply is past the browser limit, and says so.
- [ ] Synonym expansion (WordNet), Span (UTF-16), Prerequisite steps,
      Installed language model packs.
- [ ] Group heading or tooltip for the four `opennlp:terms:*` layers.
- [ ] Say what the preset actually ran (steps dropped because unconfigured).
- [ ] Rename the `en-basic` pack.
- [ ] Unit seam for `main.ts`; tests for `charts.ts` and the chunk group view.

## P3

- [ ] Server default profile / Server profile 'x'; Chunk size (tokens);
      Geocoding result; Model not reported; Not scored.
