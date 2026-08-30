# Analyze tab

Analysis of `findings/` for the tab labelled "Analyze" (`index.html:43`,
heading "Document analysis workbench", controller `src/main.ts` plus the
analysis, drawer, heatmap, shape and chunk modules). The owner called it
mature; the researcher's summary is "mature, but not clean", and I agree.

## Verdicts

1. **Two correctness bugs, both P1.** The sentiment heatmap infers polarity
   from the substrings positive/negative/neutral (`visualization-data.ts:162-174`);
   the demo's model emits `1_star` to `5_stars`, so "This was the worst
   experience of my entire life." scores +0.884 and renders green. Decision
   and fix are in `../test-coverage` (rank-derived polarity, label in the
   tooltip, integration test against the catalog model). Second: "Add to
   server workspace" (`index.html:252`) writes its outcome to `#semantic-status`,
   which lives on another tab, so the button gives no feedback; and "Open" on
   a live-index hit repopulates this tab without switching to it
   (`main.ts:272-286`).

2. **Novel-sized documents break the two-click demo path.** Pride and
   Prejudice with everything on is a 323 MB response in 20 s; the JSON tab
   guards itself but "Copy JSON" and "Download .pb" re-stringify the whole
   thing and the download POSTs it to a gateway capped at 100 MiB.
   `capabilities.maxTextBytes` is discovered and never used. Decision: one
   size threshold shared by the JSON view, copy and download; warn before
   analyzing above it with embeddings on; and let "Download .pb" stream
   through the formatter route instead of re-uploading the response.

3. **Model gating is known to the FE and hidden from the user.**
   `supportedSteps`, `configuredSteps` and `maxSteps` are all in
   `AnalysisCapabilities`, but the only place a user learns a step is
   unavailable is a checklist behind a non-default preset, so "All available
   features" silently omits 7 of 17 features. All seven unconfigured-step
   errors were reproduced verbatim (`reference/demo-errors.md`). Decision:
   adopt the three-part design in `findings/model-gating.md` section 5:
   browned-out chips in the always-visible "Enabled features" list; one
   explanation panel with three outcomes (installable from the catalog, with
   a jump that scrolls to the card; supported but needs an operator config
   key, shown verbatim; not in this build); and `data-workbench-jump="models"`
   extended with a `data-workbench-focus` step so Models & data highlights
   the fixing card. No new API is needed; the catalog roles for subword,
   WordNet and doccat (`../models-and-data-tab`) turn the second outcome into
   the first over time.

4. **Terminology.** Three invented labels with no prior art: "Normalization
   X-ray", "Document shape", "projection" (used for six things; scikit-learn
   readers expect a scatter plot). Renames are recorded in
   `../industry-terminology`. Two collisions fixed here: layer titles derived
   from ids produce `Pos` and a `Stem` term-profile layer beside `Stems`
   stemmer output (`document-shape.ts:318-326`; `identity.standard` and
   `qualifier` are parsed and unused), and "chunk" means both shallow-parse
   phrase chunks and passage chunks on one screen. `en-basic` names both a
   profile and a pack with different behaviour; the pack should be renamed.

5. **Links.** No outbound jump exists on the tab; inbound jumps from Trainer
   and Workflows work. Eight dead ends are listed in
   `findings/error-states-and-links.md`; the first four are the sentiment,
   add-to-index, open-hit and model-gated ones above.

6. **Tests.** 17 unit files with 112 tests cover the pure functions well.
   `main.ts` (1,195 lines), `charts.ts` and `chunk-projection-view.ts` have
   none; the heatmap, graph, chunks, batch, alignment, export and import flows
   have no browser coverage; and no test asserts the "not configured" error
   text that is the only guidance a gated user gets.

## Open questions for the owner

- Should "Download .pb" use the formatter route (`/api/v1/format-document`)
  server-side instead of re-uploading the response? (Recommended yes.)
- Rename the `en-basic` pack so it stops sharing a name with the profile?
