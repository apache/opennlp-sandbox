# Industry terminology

Analysis of `findings/` (127 terms audited in `findings/glossary.md`, the
owner's 27 flagged terms in `findings/flagged-terms.md`, 13 UI-versus-code
mismatches in `findings/code-vs-ui-consistency.md`), reconciled with the
per-tab audits. This README is the glossary decision record; every other
theme defers to it for cross-tab words.

## Answer to "are these proper data science words?"

About one user-visible word in four is not: 70 STANDARD, 30 VARIANT (a
standard concept under a nonstandard name), 19 INVENTED, 8 PRODUCT. The
standard core is sound: corpus, alias, clause, slop, hit, term vectors,
chunk, heatmap, catalog, artifact, teacher, distill, annotation, span,
lifecycle, workbench (Weka and UIMA Ruta both ship one). The damage is
concentrated in the search lifecycle vocabulary and a handful of Analyze
tab labels.

## Decisions

Where the per-tab researchers disagreed, the tie-break was: prefer the word
already on the wire, then the word with the most precedent, then the plainest.

| Current | Decision | Why |
| --- | --- | --- |
| workspace, dynamic workspace, on-the-fly workspace index, session (8 names for one object) | **live index** everywhere in the UI; "dynamic" deleted from every string | Three of four researchers; no search engine or vector DB says workspace; `SEARCH_PROVIDER_CAPABILITY_LIVE` is already on the wire. The Workspace search researcher argued for keeping "workspace" to avoid Java and config churn; that churn is deferred (P3), the UI does not wait for it. |
| immutable index, sealed | **read-only index** | Elasticsearch, Solr and OpenSearch all say read-only; "immutable" is also false for sealed live indexes, which can still be deleted. `immutable` stays on the wire. |
| Save checkpoint | **Save to disk** | Hugging Face and MLflow mean model weights by checkpoint, and the same tab lists model artifacts. Do not introduce "snapshot" for the on-disk copy: Qdrant and Elasticsearch snapshots are restorable point-in-time copies, which the server does not offer. `PersistIndex` stays on the wire. |
| Seal as read-only | **Make read-only** | Elastic's own phrase for `index.blocks.read_only`; "seal" exists only in Milvus. `SealIndex` stays on the wire. |
| collection | **keep, define on screen** | On the wire in five RPCs. It is a false friend (Qdrant, Solr, Weaviate collections are searchable; ours is a watched group of indexes), so the definition and a flyout are mandatory. Fallback if the owner wants a rename: "Drift group". |
| Vocabulary drift | **Vocabulary coverage** | The panel computes `1 - OOV rate` and its own aria-label already says coverage; falling coverage is the drift signal, so the threshold alert keeps the word "drift" in its help text. |
| Trained vocabulary (proposed) | **rejected; keep "Learn a vocabulary" / "Learned vocabularies"** | scikit-learn's verb; nothing is trained in that step. |
| Train model, static model | **Distill model**, **static embedding model** | Model2Vec and Sentence Transformers vocabulary; no epochs or loss exist. |
| Workflows (tab) | **Build index** | There is one six-stage run and no saved definition; GitHub Actions and Airflow own "workflow" for saved definitions. |
| Workspace search (tab) | **Live index search** | Follows the noun decision. |
| Explore an immutable index | **Search an existing index** | The tab lists live and read-only indexes and only reads; "NLP search index creation" would be wrong because nothing is created there. |
| Compound query builder | **Advanced search: mix keyword and semantic clauses** | "Compound query" is legitimate Elasticsearch vocabulary but no product labels a UI that way; "Advanced search" is the end-user precedent. |
| Chunk projections | **Chunk groups** | Matches the wire field `chunkEmbeddingGroups`; "projection" collides with PCA and UMAP one feature away. |
| Syntactic chunks | **Phrase chunks (shallow parse)** | Splits the two meanings of chunk the proto itself warns about (`opennlp_pipeline.proto:70`). |
| Normalization X-ray | **Normalization alignment** | No prior art for X-ray. |
| Document shape | **Typed annotations** | No prior art; "shape" is used for three internal things. |
| Model bundle, Loaded bundles | **Model pack** | No standard noun; "bundle" also means the sealed search bundle. |
| Feature preset / Profiles / Profile: x | **Preset**, with "(server profile x)" in the detail | One widget, one name. |
| Provider instances | **Vector storage available on this server** | SPI vocabulary leaking; capability enums are printed nearly raw (`search-adapter.ts:161-167`). |
| Results (top-k) | **Max hits** | "Results: 50" beside "0 hits" contradicts itself. |
| Lexical expansion | **Synonym expansion (WordNet)** | Names the mechanism. |

Wire names are not changed by any of this. Where the UI and the wire now
differ (live index vs `immutable == false`, Save to disk vs `PersistIndex`,
Make read-only vs `SealIndex`), the help callouts say "called X in the API"
once, as the Trainer tab already does for RPC names.

## Sequencing

1. The noun (live index, read-only index) first, in one commit, because every
   other string depends on it; `test/index.test.ts` asserts several literals.
2. Save to disk, Make read-only, Vocabulary coverage, collection definition
   (Lifecycle).
3. Analyze tab labels (chunk groups, phrase chunks, alignment, typed
   annotations, model pack, preset).
4. Tab labels (Build index, Live index search, Search an existing index,
   Advanced search).
5. P3: align DOM ids (`session-search`) and Java names
   (`WorkspaceCheckpointStore`) with the UI, and fix the proto comments that
   say "static and dynamic" for a field named `immutable`.

A vitest that snapshots every user-visible string against this table is the
guard; see `../test-coverage`.

## Open questions for the owner

- Is "workspace" load-bearing in any published document outside this repo?
- Is a v2 rename of `SearchIndexDescriptor.immutable` to `read_only`
  acceptable eventually? (Assumed not needed; UI copes.)
