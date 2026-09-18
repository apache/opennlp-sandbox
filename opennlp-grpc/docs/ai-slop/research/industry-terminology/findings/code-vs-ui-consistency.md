# Code versus UI naming consistency

Scope: does the word a user reads match the word the TypeScript, the gateway route,
the proto, and the Java use for the same thing? FACT sections cite `path:line`.
OPINION sections are marked and carry a priority.

Summary of the damage: **thirteen concepts carry more than one name**, and three of them
carry five or more. The worst is the bounded in-memory index, which a user can meet
under eight different names inside one session.

---

## M1. The bounded in-memory index has eight user-visible names (P1)

FACT. All of these name the same object, a server-owned mutable vector index created
by `IndexDocuments`:

| Name shown or written | Where |
| --- | --- |
| `Workspace search` (tab) | opennlp-grpc-webapp-default/index.html:49 |
| `Dynamic gRPC search` (kicker) | opennlp-grpc-webapp-default/index.html:725 |
| `Server memory` (badge) | opennlp-grpc-webapp-default/index.html:726 |
| `On-the-fly workspace index` (h3) | opennlp-grpc-webapp-default/index.html:727 |
| `Dynamic workspace` (label) | opennlp-grpc-webapp-default/index.html:1003 |
| `Build live index` (workflow stage) | opennlp-grpc-webapp-default/index.html:499 |
| `build your own workspace index` (jump link) | opennlp-grpc-webapp-default/index.html:592 |
| `Publishing a live workspace index` (status) | opennlp-grpc-webapp-default/src/corpus-workflow.ts:217 |

FACT. Underneath, the code adds three more:

- DOM and TypeScript call it `session`: panel id `session-search`
  (opennlp-grpc-webapp-default/index.html:722), tab id `session-search-workbench-tab`
  (index.html:48), the union member `"session-search"`
  (opennlp-grpc-webapp-default/src/workbench-navigation.ts:23 and :105), and the field
  `#sessionSearch` (src/workbench-navigation.ts:30).
- The CSS class is `semantic-lab` (index.html:722) and the module is
  `src/semantic-workbench.ts`.
- The proto calls it a **dynamic index**
  (opennlp-grpc-api/src/main/proto/org/apache/opennlp/grpc/v1/opennlp_search.proto:29-30,
  :114, :125, :360-393) with the boolean field named `immutable`
  (opennlp_search.proto:472), and the provider capability for it is `LIVE`
  (opennlp_search.proto:437).
- Java splits the difference: the registry is `DynamicSearchIndexRegistry` while the
  durability store is `WorkspaceCheckpointStore`
  (opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/DynamicSearchIndexRegistry.java:80),
  and generated ids are prefixed `workspace-`
  (DynamicSearchIndexRegistry.java:1075).

FACT. The proto is internally inconsistent about the antonym too. The same file calls
the two kinds "static and dynamic" at opennlp_search.proto:444 and :636, but names the
distinguishing field `immutable` at :472, and the UI heading says
`Explore an immutable index` (index.html:550).

OPINION (P1). Pick one user-facing noun and one wire noun and never mix them.
Recommended: user-facing **"index"**, qualified as **"live index"** and
**"read-only index"**. Rationale is in `findings/flagged-terms.md`. Then:

- Rename the tab `Workspace search` to `Live index search`, and rename the DOM id
  `session-search` to `live-index-search` so the id matches the label
  (index.html:48-49, :722; src/workbench-navigation.ts:23, :30, :105).
- Change the field `SearchIndexDescriptor.immutable` documentation at
  opennlp_search.proto:472 so the comment uses "read-only" rather than
  "operator-loaded ... process-local dynamic", and make :444 and :636 say
  "read-only and live" instead of "static and dynamic". Do not rename the wire field
  itself; `immutable` is defensible and renaming breaks compatibility.
- Retire `session` and `semantic-lab` from the DOM entirely. They name nothing a user
  can see.

Which side to change: **the UI side**, plus proto comments only. The wire field names
(`immutable`, `index_id`) are already reasonable and are a compatibility surface.

---

## M2. "Feature preset" and "Profile" are the same widget (P1)

FACT. The label reads `Feature preset` (index.html:128) on a select whose id is
`profile-select` and whose form field is `name="profile"` (index.html:129). The service
facts row above it has a `<dt>` reading `Profiles` (index.html:89, populated from
`availableProfileIds`, src/main.ts:444). And the options inside that same select are
rendered with the literal prefix `Profile: ` at
opennlp-grpc-webapp-default/src/analysis-controls.ts:198.

So one screen shows a counter called "Profiles", a picker called "Feature preset", and
options inside it called "Profile: ...".

OPINION (P1). Choose one. Recommended: **"profile"** everywhere, because it is what the
server, the proto (`AnalysisProfile`), the API field (`profileId`,
src/analysis-controls.ts:155) and the config all already say. Change the label at
index.html:128 from `Feature preset` to `Analysis profile`, and change the three
built-in options (`All available features`, `Choose features`, `Server automatic`,
index.html:130-132) so they read as profile choices, for example
`All available features (built in)`.

Which side to change: **the UI label**.

---

## M3. "Feature", "step", and "stage" are three words for one thing, and "stage" is also used for something else (P1)

FACT. The proto concept is `PipelineStep`
(opennlp-grpc-api/src/main/proto/org/apache/opennlp/grpc/v1/opennlp_pipeline.proto:34).
The UI calls the same values:

- `Enabled features` and `Enabled analysis features` (index.html:186, :187)
- `Analysis features` (legend, index.html:201)
- `Selectable analysis features` (index.html:203)
- `Choose features` (index.html:131)
- `Pipeline readiness` and `N of M features ready`
  (index.html:818; src/model-data-workbench.ts:266)
- `Required backbone steps are added automatically.` (index.html:202), the only place
  the word "step" is exposed
- `readableStep()` in code (src/analysis-controls.ts:285)

FACT. Meanwhile the Workflows tab uses `Pipeline stages` (index.html:479) as the
heading for six items (`Analyze text`, `Learn vocabulary`, `Distill embeddings`,
`Embed documents`, `Build live index`, `Search and visualize`, index.html:483-503) that
are **not** `PipelineStep` values at all. The data attribute is `data-workflow-stage`
(index.html:482). A user who has just learned that the pipeline has "steps" on the
Analyze tab meets a different set of things called "pipeline stages" one tab away.

OPINION (P1). Two fixes:

1. Standardise on **"step"** for `PipelineStep` values in the UI, matching the proto and
   matching spaCy's "pipeline component" ordering vocabulary closely enough. Change
   `Enabled features` to `Enabled steps`, `Analysis features` to `Analysis steps`,
   `Pipeline readiness` keeps its name but its rows say "step".
2. Rename the Workflows heading `Pipeline stages` (index.html:479) to
   **`Workflow steps`** and change `data-workflow-stage` accordingly, so the word
   "pipeline" is reserved for the analysis pipeline.

Which side to change: **the UI**.

---

## M4. "Chunk" means two unrelated things, and the proto knows it (P1)

FACT. The proto has both `PIPELINE_STEP_CHUNK` (opennlp_pipeline.proto:53,
"Segmentation-style chunking for embedding") and `PIPELINE_STEP_SYNTACTIC_CHUNK`
(opennlp_pipeline.proto:70), whose comment explicitly warns: "Distinct from CHUNK
(which is segmentation for embedding)". Both surface in the UI:

- The result tab `Chunks` (index.html:270), the fieldset `Chunk output`
  (index.html:166), `Sentence chunks` / `Token windows` (index.html:170, :174),
  `Chunk projections` (index.html:310), `Indexed chunks` (index.html:738),
  `Score chunks` (index.html:336) all mean segmentation chunks.
- The feature name `Syntactic chunks` (src/analysis-config.ts:59) and the model role
  label `Syntactic chunker` (src/model-data-workbench.ts:655) mean shallow parsing.
- The inspector `<dt>` just says `Chunks` (index.html:678) with no qualifier.

FACT. Apache OpenNLP's own historic meaning of "chunker" is the shallow-parsing one,
so an existing OpenNLP user arrives with the opposite expectation from a vector-search
user.

OPINION (P1). Keep `chunk` for segmentation, since that is now the dominant industry
meaning (see `reference/vector-databases.md`), but never let the bare word stand for
shallow parsing. Rename every shallow-parsing surface to **"phrase chunk"** or
**"shallow parse"**: `Syntactic chunks` at src/analysis-config.ts:59 becomes
`Shallow parse (phrase chunks)`, and `Syntactic chunker` at
src/model-data-workbench.ts:655 becomes `Shallow parser (chunker)`. Also qualify the
inspector `<dt>` at index.html:678 as `Indexed chunks`.

Which side to change: **the UI labels**. Do not rename the proto enums; both names are
already explicit and the comment at opennlp_pipeline.proto:70 documents the split.

---

## M5. "Collection" means two unrelated things inside this app (P1)

FACT. On the Lifecycle tab, `Collection` (index.html:1064, :1068) is a first-class
server object: a named group of live indexes with a dictionary, a vocabulary, a serving
model, and a drift threshold (`CollectionDescriptor`, opennlp_search.proto:204;
`/api/v1/collections`, GrpcJsonApi.java:168).

FACT. On the Workflows tab, `Your text collection` (index.html:402) is the heading over
a plain textarea of pasted documents (index.html:404-406). No `Collection` object is
created by that flow.

OPINION (P1). Rename the Workflows heading from `Your text collection` to
**`Your documents`** or `Input corpus` (index.html:402). Reserve `collection` for the
Lifecycle object.

Which side to change: **the UI**, Workflows tab only.

---

## M6. "Bundle" means two unrelated things (P2)

FACT. On the Analyze and Models tabs, a **bundle** is a loaded NLP model set:
`Bundles` (index.html:90), `Loaded model bundles` (index.html:192),
`Available model bundles` (index.html:193), `Loaded bundles` (index.html:824),
`ListModelBundles` (opennlp_service.proto:359), `/api/v1/model-bundles`
(GrpcJsonApi.java:130).

FACT. On the Corpus search tab, a **bundle** is a prebuilt immutable search index
artifact: `An operator must configure an immutable index bundle at startup.`
(src/server-search-workbench.ts:146), the inspector fact `Bundle format`
(src/server-search-workbench.ts:494), `SearchIndexBuildDescriptor.bundle_format_version`
and `bundle_artifact_hash` (opennlp_search.proto:531-535), and the provider capability
rendered to the user as the bare word `bundle`
(src/lifecycle-workbench.ts:196 from src/search-adapter.ts:161-167;
opennlp_search.proto:434-435).

OPINION (P2). Keep "model bundle" for the NLP model set, since the UI always writes it
with the qualifier "model". Rename the search-side use to **"index bundle"** wherever
the bare word appears: the fact name `Bundle format` becomes `Index bundle format`
(src/server-search-workbench.ts:494), and the provider capability `bundle` should render
as `loads index bundles` rather than the raw enum tail
(src/lifecycle-workbench.ts:196).

Which side to change: **the UI**, plus the capability rendering.

---

## M7. Provider capability enums are printed to users nearly raw (P1)

FACT. `src/search-adapter.ts:161-167` strips the `SEARCH_PROVIDER_CAPABILITY_` prefix
and lowercases the tail, and `src/lifecycle-workbench.ts:196` joins the results with a
middle dot. Against the running demo, the Lifecycle tab's `Provider instances` panel
(index.html:1058) therefore prints:

```
flat_float    vector · live
terms         keyword · live
turbo_quant   vector · live · bundle · persistent
```

(confirmed from `GET /api/v1/search-providers` on the demo instance, 2026-08-28).
`vector`, `keyword`, `live`, `bundle`, and `persistent` are never defined anywhere in
the UI, and `flat_float` / `turbo_quant` are raw instance ids.

OPINION (P1). Add a label map beside the existing ones (the pattern already exists at
src/analysis-config.ts:43 and src/chunk-projection.ts:44) rendering
`vector` as `Vector similarity`, `keyword` as `Keyword and phrase`, `live` as
`Live indexes`, `bundle` as `Prebuilt index bundles`, `persistent` as
`Survives restart`.

Which side to change: **the UI**.

---

## M8. "Checkpoint" in the UI, "persist" on the wire (P2)

FACT. The button says `Save checkpoint` (index.html:1007), the subcard heading says
`Checkpoint and seal` (index.html:1002), the help text says
`Save a checkpoint to keep a dynamic workspace across server restarts`
(index.html:979). The route is `/api/v1/persist-index` (GrpcJsonApi.java:152), the RPC
message is `PersistIndexRequest` (opennlp_search.proto:115), the client function is
`persistIndex` (src/api.ts:211), and the interface method is `persist`
(src/lifecycle-workbench.ts:31). The same tab's own help callout then names the RPC:
`the persist, seal, reindex, alias, and collection RPCs` (index.html:989). So the user
reads "checkpoint" on the button and "persist" one paragraph below it.

FACT. Java uses "checkpoint" as a noun for the on-disk artifact:
`WorkspaceCheckpointStore.CheckpointHeader`, `RestoredCheckpoint`
(DynamicSearchIndexRegistry.java:535, :555-566).

OPINION (P2). See `findings/flagged-terms.md` for the precedent argument. Recommended:
UI button becomes **`Save to disk`** with helper text "the index keeps accepting
documents", and the noun for the on-disk artifact becomes **"snapshot"**, not
"checkpoint". Keep the wire name `PersistIndex`, which is unambiguous.

Which side to change: **the UI**; leave the proto alone.

---

## M9. "Teacher" has three different labels (P3)

FACT. The same object is labelled:
- `Teacher` (index.html:943, Trainer tab)
- `Embedding teacher` (index.html:441, Workflows tab)
- `Training teacher` (src/model-data-workbench.ts:651, Models tab role label)
- proto `MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER` (opennlp_training.proto:79) and
  `TeacherDescriptor` (opennlp_training.proto:287)

OPINION (P3). Standardise on **`Teacher model`** in all three places. The proto name is
the most precise of the four and needs no change.

---

## M10. "Static" is used for two opposite ideas (P2)

FACT. `Train a static model` (index.html:942), `Trained static model`,
`/api/v1/static-models` (GrpcJsonApi.java:188), `StaticModelDescriptor`
(opennlp_training.proto:338), `MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING`
(opennlp_training.proto:81) and the role label `Ready-to-serve static table`
(src/model-data-workbench.ts:648) all mean a non-contextual embedding table.

FACT. But opennlp_search.proto:444 and :636 use "static" to mean the opposite of
"dynamic" for indexes, that is "read-only". Nothing in the UI says "static index", so
the collision is currently proto-internal only, but it is a trap for anyone reading
both files.

OPINION (P2). Fix the proto comments at opennlp_search.proto:444 and :636 to say
"read-only" instead of "static", so "static" only ever qualifies an embedding model.
Also change the Models tab role label `Ready-to-serve static table`
(src/model-data-workbench.ts:648) to **`Static embedding model`**, which is the phrase
practitioners use.

---

## M11. "Catalog" is stretched past its meaning (P3)

FACT. `Pinned model catalog` (index.html:832), `/api/v1/model-catalog`
(GrpcJsonApi.java:184) and `ListModelCatalog` (opennlp_training.proto:118) are a proper
catalog: a curated list of downloadable models.

FACT. But the status strings `Loading the trainer catalog.` (index.html:900,
src/vocabulary-trainer.ts:173) and `Loading the lifecycle catalog.` (index.html:996,
src/lifecycle-workbench.ts:139) call the tab's own startup fetch a "catalog". There is
no trainer catalog and no lifecycle catalog. The user reads a word that promises a
browsable list and gets a spinner.

OPINION (P3). Change both to plain language: `Loading dictionaries, vocabularies, and
teachers.` and `Loading indexes, aliases, and collections.` respectively
(index.html:900, :996; src/vocabulary-trainer.ts:173; src/lifecycle-workbench.ts:139).

---

## M12. Tab identity is split four ways on Corpus search (P3)

FACT. One tab carries: the label `Corpus search` (index.html:47), the data attribute
`corpus-search` (index.html:47), the panel id `server-search` (index.html:545), the CSS
class `server-search-lens` (index.html:545), the module
`src/server-search-workbench.ts`, the kicker `Server-backed semantic search`
(index.html:549) and the heading `Explore an immutable index` (index.html:550). The
button inside it is also labelled `Search index` (index.html:603), which duplicates the
select's own label `Search index` (index.html:587): the same string is both a noun for
the field and a verb for the button on the same form.

OPINION (P3). Rename the submit button at index.html:603 from `Search index` to
**`Search`**. Align the panel id and CSS class with the tab name (`corpus-search`,
`corpus-search-lens`) so a reader can trace one name through markup, CSS and TypeScript.

---

## M13. `top_k` is labelled `Results` (P2)

FACT. The number input is labelled `Results` (index.html:600) with id
`server-search-top-k`, and the wire field is `top_k` with `max_top_k`
(opennlp_search.proto:612; `max_top_k` at :477). The heading two panels down is
also `Search results` (index.html:649) and the count reads `0 hits` (index.html:655).
So `Results` means "how many to ask for" in one place and "what came back" in another,
and a third word, `hits`, is used for the same returned things.

OPINION (P2). Label the input **`Max results (top k)`** (index.html:600) and pick one
of "results" or "hits" for the returned items. `hit` is the Lucene and Elasticsearch
word and matches the proto `SearchHit` (opennlp_search.proto:637), so prefer **hits**:
change `Search results` at index.html:649 to `Ranked hits`, and
`Server search results` (index.html:658) to `Ranked hits`.

---

## Where the wire is right and the UI is wrong (summary table)

| Concept | Proto or route says | UI says | Change which side |
| --- | --- | --- | --- |
| bounded mutable index | `dynamic index`, `immutable=false` | workspace / session / live / on-the-fly | UI (M1) |
| analysis profile | `profileId`, `AnalysisProfile` | Feature preset + Profiles + `Profile: ...` | UI (M2) |
| pipeline step | `PipelineStep` | feature / step / stage | UI (M3) |
| segmentation vs shallow parse | `PIPELINE_STEP_CHUNK` vs `_SYNTACTIC_CHUNK` | `Chunks` vs `Syntactic chunks` | UI (M4) |
| index durability | `PersistIndex` | Save checkpoint | UI (M8) |
| distillation source | `..._DISTILLATION_TEACHER` | Teacher / Embedding teacher / Training teacher | UI (M9) |
| returned result | `SearchHit` | Results / hits | UI (M13) |

| Concept | Proto says | Problem | Change which side |
| --- | --- | --- | --- |
| read-only index | "static and dynamic" at opennlp_search.proto:444, :636 while the field is `immutable` at :472 | "static" collides with `static model` | proto comments only (M1, M10) |

---

## Tests that would lock this down

FACT. There is no unit test anywhere under
`opennlp-grpc-webapp-default/test/` that asserts a user-visible label string as a
contract. The 32 test files there (`test/*.test.ts`) test behaviour and data mapping.
The only places a literal UI string is asserted are three Playwright lines:
`e2e/workbench.spec.ts:48` (`Corpus terms only (default)`),
`e2e/corpus-search.spec.ts:37` (`No chunk comparison yet.`), and
`e2e/workbench.spec.ts:81` / `e2e/corpus-search.spec.ts:52` (the `…` placeholder).

OPINION (P2). Once the glossary is agreed, add a single unit test that reads
`index.html` and fails on any banned word (`session` as a user-visible label, `Feature
preset`, `Pipeline stages`, bare `Syntactic chunks`, `Your text collection`). That is
the cheapest way to keep the vocabulary from drifting back, and it matches the existing
convention of small focused files under `opennlp-grpc-webapp-default/test/`.

## Questions for the lead

1. Is `workspace` load-bearing in any external document, or can the UI move to
   `live index` without breaking anything published?
2. Renaming the DOM id `session-search` breaks any bookmark or deep link that uses it.
   Does anything outside this repo link to `#session-search`?
3. `SearchIndexDescriptor.immutable` is on the wire. Keep it, or is a v2 rename to
   `read_only` acceptable at some point?
