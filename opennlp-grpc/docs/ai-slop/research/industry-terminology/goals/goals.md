# Goals: terminology

Apply in the order given; each step is one commit with the string tests updated.

## P1

- [x] "workspace" and "dynamic" out of every UI string; noun "live index",
      read-only kind "read-only index"; tab "Live index search".
- [x] "Save checkpoint" to "Save to disk"; "Seal as read-only" to "Make read-only";
      "Vocabulary drift" to "Vocabulary coverage"; collection defined on screen.
- [x] "Workflows" to "Build index"; "Explore an immutable index" to "Search an
      existing index"; "Compound query builder" to "Advanced search: mix keyword and
      semantic clauses".
- [x] Analyze: "Chunk projections" to "Chunk groups"; "Syntactic chunks" to
      "Phrase chunks (shallow parse)"; "Normalization X-ray" to "Normalization
      alignment"; "Document shape" to "Typed annotations"; "Bundles" to "Model packs";
      one name "Preset" for the profile widget.
- [x] "Train model" to "Distill model"; "static model" to "static embedding model".
- [x] Layer titles from `identity.standard` and qualifier, so `Pos` becomes
      "POS tags" and `Stem` becomes "Terms (stem)".
- [x] Vitest snapshot of all user-visible strings against the decision table.

## P2

- [x] "Provider instances" to "Vector storage available on this server"; render
      capability enums as words.
- [x] "Results" to "Max hits"; "Lexical expansion" to "Synonym expansion (WordNet)";
      "Browser span" to "Span (UTF-16)"; "Required backbone steps" to "Prerequisite steps".
- [ ] One "called X in the API" line per renamed concept in the help callouts.
- [ ] Proto comments: replace "static and dynamic" with "read-only and live"
      where the field is `immutable`.

## P3

- [ ] Align DOM id `session-search` and Java `WorkspaceCheckpointStore` with the UI noun.
- [ ] "Teacher" has three labels across tabs; pick "Teacher model".
