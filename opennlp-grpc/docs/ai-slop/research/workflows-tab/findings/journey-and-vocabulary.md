# Workflows tab: what it actually is, and what to call it

Scope: `index.html:384-543` (`section#workflows-workbench`), `src/corpus-workflow.ts`, the wiring
at `src/main.ts:324-378`, and the endpoints in `src/api.ts` that it reaches.

FACT sections describe today's behaviour with citations. OPINION sections are recommendations and
carry a priority.

---

## 1. FACT: there is exactly one "workflow", and it is a six stage pipeline run

The tab's name is plural but the tab contains **one** thing. There is no list of workflows, no
saved workflow, no second workflow to choose. There is one form, one button
(`#workflow-run-button`, `index.html:468`) and one hard-coded sequence of six stages declared at
`src/corpus-workflow.ts:91`:

```ts
const STAGES: WorkflowStage[] = ["analyze", "vocabulary", "train", "embed", "index", "search"];
```

Nothing about the sequence is configurable. Stages cannot be skipped, reordered, or run
individually. Pressing the button always runs all six.

### 1.1 Inputs

| Control | Element id | Default | Read at |
| --- | --- | --- | --- |
| Documents, blank line separated | `workflow-corpus` (`index.html:405`) | empty | `corpus-workflow.ts:169` via `workflowDocuments()` |
| Workflow name | `workflow-name` (`index.html:413`) | `My text workflow` | `corpus-workflow.ts:181` |
| First search | `workflow-query` (`index.html:418`) | `What are these documents about?` | `corpus-workflow.ts:170` |
| Vocabulary source | `workflow-dictionary-select` (`index.html:435`) | `Corpus terms only (default)` | `corpus-workflow.ts:189` |
| Embedding teacher | `workflow-teacher-select` (`index.html:442`) | first server teacher | `corpus-workflow.ts:205` |
| Vector storage | `workflow-provider-select` (`index.html:449`) | first vector+live provider, falls back to flat float | `corpus-workflow.ts:220` |
| Min term frequency | `workflow-min-frequency` (`index.html:456`) | `1` | `corpus-workflow.ts:194` |
| Max corpus terms | `workflow-max-terms` (`index.html:459`) | `10000` | `corpus-workflow.ts:195` |
| PCA dimensions | `workflow-pca-dims` (`index.html:462`) | `0` | `corpus-workflow.ts:207` |

Documents are split on blank lines and given synthetic ids `workflow-doc-1`, `workflow-doc-2`, and
so on (`corpus-workflow.ts:503-508`). There is no file upload and no sample loader on this tab,
unlike the Analyze tab which offers `Use short sample`, `Load Alice novel`, and
`Load Pride and Prejudice` (`index.html:214-216`).

### 1.2 The six stages, their API calls, and their outputs

| # | Stage label (`index.html`) | API call (`src/api.ts`) | Endpoint | Produces |
| --- | --- | --- | --- | --- |
| 1 | `Analyze text` (483) | `analyze` (475) | `POST /api/v1/analyze`, once per document, serially at `corpus-workflow.ts:266-283` | An `AnalyzeDocumentResponse` per document, rendered as cards at `corpus-workflow.ts:298-328` |
| 2 | `Learn vocabulary` (487) | `learnVocabulary` (332) | `POST /api/v1/learn-vocabulary` | A **durable vocabulary artifact** on the server, id shown at `corpus-workflow.ts:392` |
| 3 | `Distill embeddings` (491) | `trainStaticModel` (380) | `POST /api/v1/train-static-model`, NDJSON progress stream | A **durable static embedding model artifact**, served immediately, id at `corpus-workflow.ts:393` |
| 4 | `Embed documents` (495) | `analyze` again, with `chunkEmbedConfigs` | `POST /api/v1/analyze` | The same documents re-analyzed with sentence chunks carrying vectors |
| 5 | `Build live index` (499) | `indexDocuments` (180) | `POST /api/v1/index-documents` | A **new dynamic index in server memory**, id at `corpus-workflow.ts:394` |
| 6 | `Search and visualize` (503) | `searchIndex` (168) | `POST /api/v1/search` | A scored heatmap over the source text |

Stage 1 and stage 4 both call `POST /api/v1/analyze`, once per document, in a `for` loop
(`corpus-workflow.ts:271`). For an N document corpus this tab issues **2N analyze calls**. The
`analyze-stream` endpoint that `src/batch-analysis.ts` wraps is not used here; it is wired only to
the Analyze tab's `#batch-analyze-button` (`index.html:239`, `src/main.ts:393,509-533`).

### 1.3 What the run leaves behind

Three artifacts, listed in `#workflow-artifacts` (`index.html:518`, rendered at
`corpus-workflow.ts:387-396`) as three bare id chips labelled `Vocabulary`, `Model`, and `Index`.

Two of the three are **durable server-side writes**. The vocabulary and the static model are
written under the operator's `vocabulary.artifact_root`
(`opennlp-grpc-service/.../training/StaticModelArtifactStore.java:79,180`). Nothing on the tab says
so. The header says only "Paste a small corpus and OpenNLP will analyze it, learn its vocabulary,
distill a static embedding model, build a live index, and search it" (`index.html:391-392`).

The tab has no delete, no cleanup, and no listing of past runs. **Every press of
`Build workflow and search` creates a new vocabulary, a new model, and a new index**, because
`corpus-workflow.ts:218-223` posts `indexDocuments` with no `indexId`, and the proto says the
server then "creates an opaque workspace id"
(`opennlp-grpc-api/src/main/proto/.../opennlp_search.proto:49-51`). Running twice with the default
name leaves two indexes both labelled `My text workflow`, distinguishable only by their
`workspace-<uuid>` ids (`DynamicSearchIndexRegistry.java:1075`).

### 1.4 Outputs on the tab

Two result views behind `[data-workflow-result-tab]` (`index.html:520-525`):

- `Analysis`: one card per document with layer chips and an `Open full analysis` button that
  hands the response back to the Analyze tab (`corpus-workflow.ts:310-314`,
  `src/main.ts:361-372`).
- `Search heatmap`: the search hits shaded over source text, with per-chunk click selection
  (`corpus-workflow.ts:330-385`).

After a successful run the tab switches itself to the search view
(`corpus-workflow.ts:229`).

---

## 2. FACT: the end to end journey

### 2.1 The journey the product intends

`QUICKSTART.md:55-72` documents the five minute tour as: Analyze, Models & data, Trainer,
Analyze again, Workspace search, Lifecycle. **It never mentions the Workflows tab.** The Workflows
tab compresses QUICKSTART steps 3, 4 and 5 into one button, and the quickstart does not say so.
The webapp's own `opennlp-grpc-webapp-default/README.md` does not mention it either. The only
prose is three sentences at `README.md:458-460`.

### 2.2 The journey the code implements

```
  Analyze tab                         Workflows tab                     other tabs
  -----------                         -------------                     ----------

  paste / Load Alice
        |
        |  (no link exists to Workflows)
        v
                              +--------------------------+
                              | 1 Analyze text           |  POST /api/v1/analyze  x N
                              | 2 Learn vocabulary       |  POST /api/v1/learn-vocabulary
                              | 3 Distill embeddings     |  POST /api/v1/train-static-model
                              | 4 Embed documents        |  POST /api/v1/analyze  x N
                              | 5 Build live index       |  POST /api/v1/index-documents
                              | 6 Search and visualize   |  POST /api/v1/search
                              +--------------------------+
                                    |        |        |
              vocabulary artifact <-+        |        +-> dynamic index (workspace-<uuid>)
                       model artifact <------+                  |
                            |                                   |
                            v                                   v
                 Analyze tab embedding                 Workspace search tab
                 picker gains the model                 picker gains the index
                 (main.ts:357-360)                      (main.ts:373-376)
                            |                                   |
                            |                          Corpus search tab
                            |                          picker ALSO gains it
                            |                          (see 2.4 below)
                            v                                   v
                    "Open full analysis"                 Lifecycle tab
                    jumps back to Analyze                does NOT refresh
                    (main.ts:361-372)                    (see 2.5 below)
```

### 2.3 FACT: the loop back into Analyze works

`onOpenAnalysis` (`src/main.ts:361-372`) loads the document text into the Analyze textarea, renders
every projection, and calls `workbenchNavigation.show("analysis")`. This is the one genuinely
finished cross-tab handoff on the tab, and it is triggered from a button labelled
`Open full analysis` (`corpus-workflow.ts:312`) rather than from anything that says "Analyze tab",
so the user does not know where they are about to be sent.

### 2.4 FACT: the built index leaks into a tab that says it cannot hold it

Corpus search is titled `Explore an immutable index` (`index.html:550`) and its bridge paragraph
says "This tab searches read-only indexes an operator configured or persisted"
(`index.html:553-554`). But `ServerSearchWorkbench.initialize` lists **every** index with no filter
at all (`src/server-search-workbench.ts:141`), while both Workspace search
(`src/semantic-workbench.ts:184`) and Lifecycle (`src/lifecycle-workbench.ts:129`) filter on
`!index.immutable`. So a workflow-built index appears in all three pickers, and in the one place
whose heading promises it will not be there.

### 2.5 FACT: the Lifecycle tab does not learn about the new index

`onIndexChanged` (`src/main.ts:373-376`) refreshes exactly two workbenches:

```ts
onIndexChanged: () => {
  void serverSearchWorkbench.initialize();
  void semanticWorkbench.initializeWorkspaces();
},
```

`lifecycleWorkbench` is initialised once at `src/main.ts:310` and never again. A user who runs the
workflow and then opens Lifecycle sees the stale empty state
`Index documents in Workspace search to create a dynamic workspace first.`
(`src/lifecycle-workbench.ts:136`) and the disabled picker option `No dynamic workspaces`
(`src/lifecycle-workbench.ts:148`) until they find the `Refresh` button
(`index.html:1010`). Priority: **P1**, the primary follow-on action after a run is silently broken.

### 2.6 FACT: the default vector storage cannot be checkpointed

The Workflows tab defaults to `STANDARD_SEARCH_PROVIDER_FLAT_FLOAT`
(`corpus-workflow.ts:92`, used as the fallback at line 429 and as the request value at line 220).
The live instance reports that `flat_float` declares only `VECTOR` and `LIVE`, while
`turbo_quant` additionally declares `PERSISTENT` (see `reference/live-instance-state.md`). The
server refuses to checkpoint a non-persistent instance:

```java
throw AnalysisException.failedPrecondition("Search provider instance '"
    + index.instance().instanceId() + "' is not persistent");
```
(`DynamicSearchIndexRegistry.java:524-526`)

So the default path is: build a workflow, go to Lifecycle, press `Save checkpoint`, and get
`Search provider instance 'flat_float' is not persistent`. The Workflows help text for the control
says only "Exact storage is the default; choose TurboQuant when the server offers it"
(`index.html:452`), which gives no hint that the choice decides whether the result survives a
restart. Priority: **P1**.

### 2.7 FACT: cross-tab links, present and missing

Only three `data-workbench-jump` elements exist in the entire application:

| From | To | Line |
| --- | --- | --- |
| Corpus search index help | Workflows | `index.html:592`, text `build your own workspace index` |
| Corpus search bridge | Workspace search | `index.html:555` |
| Workspace search bridge | Corpus search | `index.html:735` |

**The Workflows tab contains zero outbound links.** Every link listed below is missing today.

| Needed link | Where it belongs | Why | Priority |
| --- | --- | --- | --- |
| Workflows -> Models & data | beside `#workflow-teacher-select` when the list is empty | A teacher is an operator-installed catalog model; with none, the tab is dead (see `gating-and-empty-states.md`) | P1 |
| Workflows -> Lifecycle | in the `#workflow-artifacts` strip after a successful run | Checkpoint, seal, alias, and rebuild are the only things you can do with the index next | P1 |
| Workflows -> Workspace search | same place | The built index is searchable there with the full inspector, not just a heatmap | P2 |
| Workflows -> Trainer | beside the `Configure the workflow` drawer | Trainer is where you import a dictionary that the `Vocabulary source` picker then offers | P2 |
| Analyze -> Workflows | beside the Analyze sample buttons | Nothing in the app ever points a first-time user at this tab | P2 |
| Trainer -> Workflows | in the Trainer header | Trainer manually repeats workflow stages 2 and 3; a user who lands there first never learns the one-button path exists | P3 |
| Lifecycle -> Workflows | in the `No dynamic workspaces` empty state, which currently names only Workspace search | Workflows is the faster way to get a workspace | P2 |

---

## 3. FACT: the vocabulary in use today

Nine contested words appear on or around this tab. Counting only user-visible strings:

| Word | Where it appears | What it means there |
| --- | --- | --- |
| **workflow** | tab label `index.html:45`; `Workflow name` 412; `Build workflow and search` 469; `Workflow '<name>' is ready to explore.` `corpus-workflow.ts:230` | One execution of the fixed six stage pipeline |
| **workspace** | never on this tab; `build your own workspace index` `index.html:592`; `On-the-fly workspace index` 727; `Add to server workspace` 252; `Dynamic workspace` 1003; also a CSS layout class `class="workspace workspace-wide"` 77 and `.server-search-workspace` 646 | Four different things: a dynamic index, the act of indexing, a Lifecycle noun, and a stylesheet grid |
| **dynamic** | `Dynamic gRPC search` 726; `Dynamic workspace` 1003; `dynamic workspaces held in server memory` 733 | Mutable, in memory, not operator-configured |
| **collection** | `Your text collection` 402 on this tab; `Collections` 1063 and `Collection id` on Lifecycle | On Workflows: the pasted text. On Lifecycle: a named group of member indexes with drift watching. Two unrelated meanings, two tabs |
| **corpus** | `Max corpus terms` 459; `Corpus terms only (default)` 436; `Corpus search` tab 47; `corpusTitle` `search-adapter.ts:48` | The pasted text, and separately the tab name for operator-configured indexes |
| **index** | `Build live index` 499; `Search index` 588; `Index` artifact chip `corpus-workflow.ts:394` | The searchable vector structure |
| **checkpoint** | `Save checkpoint` `index.html:1007`; `search.persist.root` in `WorkspaceCheckpointStore` | Write the in-memory index to disk, keep it writable |
| **seal** | `Seal as read-only` `index.html:1009` | Write to disk and mark permanently immutable |
| **immutable** | `Explore an immutable index` 550; `Sealed` fact row `lifecycle-workbench.ts:180`; `immutable` field `search-adapter.ts:47` | Read-only. Note the UI label says "Sealed" while the wire field says `immutable` |
| **batch** | `Analyze batch` 239, `preview the workflow batch` 408 | On Analyze: a streamed multi-document analyze. On Workflows: the word appears in a status line for something that is not streamed at all |

### 3.1 FACT: the worst single collision

`index.html:592` sends a user to this tab with the promise **"build your own workspace index"**.
The tab it lands on never uses the word *workspace* anywhere in its 160 lines of markup. Its own
status message on success is `Workflow '<name>' is ready to explore.`
(`corpus-workflow.ts:230`). The only place the word survives is a status string the user sees for
about a second during stage five, `Publishing a live workspace index`
(`corpus-workflow.ts:217`). A user who followed the link has no way to confirm they arrived at the
right place.

### 3.2 FACT: "collection" means two different things one tab apart

`index.html:402` labels the paste box `Your text collection`. `index.html:1063` labels a Lifecycle
panel `Collections`, where a collection is a persisted descriptor with member index ids, artifact
lineage, and a drift threshold (`src/collection-adapter.ts:147-161`). These share no properties.

### 3.3 FACT: which of these are standard

Excerpts with links are in `reference/vocabulary-precedents.md`.

| Term | Standard? | Precedent |
| --- | --- | --- |
| `index` | Standard | OpenSearch, Pinecone ("In Pinecone, you store data in indexes"), Lucene |
| `collection` | Standard, but for the *searchable container*, not for input text | Solr ("A collection is the entire group of cores that represent an index"), Weaviate ("Collections are groups of objects that share a schema definition"), Milvus |
| `corpus` | Standard in NLP, not in search infrastructure | Long-standing linguistics and OpenNLP usage |
| `immutable` | Standard as an adjective, not as a UI noun | Lucene segments are immutable; no product labels a button with it |
| `workspace` | **Standard, but for something else entirely** | Notion ("completely separate silos"), Slack ("made up of channels"). Both mean an account-level membership boundary. Neither means a data structure |
| `checkpoint` | Borrowed from ML training and stream processing, **not** from search | Lucene's word is `commit`; Elasticsearch's is `flush`/`refresh`. `checkpoint` in MLflow-adjacent tooling means a saved model state mid-training |
| `seal` | **Invented.** No search or vector product uses it | Nearest precedents: Lucene "commit", S3 Object Lock, Elasticsearch "frozen"/"read-only" index blocks |
| `dynamic workspace` | **Invented compound** | No precedent found |
| `workflow` | Standard, but implies *a definition you author and reuse* | GitHub Actions workflows, Airflow DAGs, Temporal workflows. All are saved, named, versioned, and re-runnable. This tab has none of that |
| `batch` | Standard | Ubiquitous. Correctly used on the Analyze tab, loosely on this one |
| `artifact` | Standard, and correctly used | MLflow: "output files from the run such as model weights, images, etc." |

---

## 4. OPINION: one consistent vocabulary

The failure mode is that the project has two vocabularies fighting: a **search-infrastructure**
vocabulary (index, immutable, provider, alias, collection) inherited correctly from Solr and
Lucene, and an invented **workspace/workflow/checkpoint/seal** vocabulary layered on top of it to
make the same objects sound friendlier. The second one costs a first-time reader more than it
saves, because every one of its four words already means something else in a product they have
used.

Recommendation: **keep the search vocabulary and delete the invented layer.** Concretely:

| Current string | Proposed string | Precedent | Priority |
| --- | --- | --- | --- |
| Tab label `Workflows` (`index.html:45`) | `Build index` | Solr's `Collections API` and Pinecone's `Create index` both name the tab after the object produced, not after the machinery | P1 |
| `Build workflow and search` (`index.html:469`) | `Build index and search` | same | P1 |
| `Workflow name` (`index.html:412`) | `Index name` | Pinecone `Index name`, Weaviate collection name | P1 |
| `Your text collection` (`index.html:402`) | `Your documents` | Avoids colliding with Lifecycle "Collections". "Document" is already the wire noun (`OpenNlpDocument`) and the standard search noun in every product surveyed | P1 |
| `build your own workspace index` (`index.html:592`) | `build an index from your own documents` | Removes the only appearance of a word the destination never uses | P1 |
| `Workflow '<name>' is ready to explore.` (`corpus-workflow.ts:230`) | `Index '<name>' is built and searchable.` | states the object, not the machinery | P1 |
| `Publishing a live workspace index` (`corpus-workflow.ts:217`) | `Building the searchable index` | drops "workspace" and "publish", neither of which is defined anywhere in the UI | P2 |
| `Dynamic workspace` (`index.html:1003`, Lifecycle) | `Live index` | "live" is already the provider capability name on the wire (`SEARCH_PROVIDER_CAPABILITY_LIVE`), so the UI word and the API word match | P2 |
| `Add to server workspace` (`index.html:252`, Analyze) | `Add to live index` | same | P2 |
| `Workspace search` tab (`index.html:49`) | `Live index search` | pairs with `Corpus search`, and the two tab names then differ by *what kind of index*, which is the real distinction | P2 |
| `Save checkpoint` (`index.html:1007`) | `Save to disk` | Lucene's verb is `commit`, but "commit" collides with version control for a general audience. Plain "Save to disk" states the effect | P2 |
| `Seal as read-only` (`index.html:1009`) | `Save and lock` | `seal` has no precedent; `lock` matches S3 Object Lock and Elasticsearch index write blocks | P2 |
| `Explore an immutable index` (`index.html:550`) | `Search a prepared index` | `immutable` is an implementation property, not a user goal | P3 |
| `Max corpus terms` (`index.html:459`) | `Max vocabulary terms` | The number bounds the learned vocabulary, not the corpus (`corpus-workflow.ts:195` feeds `maxTerms` on the vocabulary request) | P2 |
| `preview the workflow batch` (`index.html:408`) | `preview the documents` | Nothing about this stage is a batch in the sense the Analyze tab uses the word | P3 |

Words to keep exactly as they are, because they are already right: **index**, **document**,
**artifact**, **vocabulary**, **model**, **teacher**, **alias**, **provider**, **collection**
(Lifecycle sense only), **corpus** (Corpus search sense only), **batch** (Analyze sense only).

If the product decides it wants a real "workflow" concept later, the word is then free to mean
what GitHub Actions and Airflow mean by it: a **saved, named, re-runnable definition**. That is a
feature this tab does not have and should not pre-empt the word for.

---

## 5. OPINION: a short "what is a workspace" explainer

If the team keeps the word, it needs a definition in the UI, because there is none today. If the
team takes the rename above, the same paragraph works with "index" substituted. Proposed copy for
a `<details class="help-callout">` on this tab, matching the pattern already used on Corpus
search (`index.html:565`), Workspace search (`index.html:752`), Trainer (`index.html:881`), and
Lifecycle (`index.html:976`). **This tab is the only tab with no "How to use" callout.** Priority
for adding one: **P1**.

> **What this tab builds**
>
> An **index** is a searchable copy of your documents. The server splits each document into
> chunks, turns each chunk into a vector with an embedding model, and keeps the vectors so it can
> rank them against a question.
>
> This tab builds one from scratch in six steps. It reads your documents, collects the words they
> use into a **vocabulary**, distils a small **embedding model** for exactly that vocabulary from
> a teacher model the operator installed, re-reads the documents through the new model, loads the
> result into a **live index** in server memory, and runs your first search against it.
>
> A live index is mutable and lives in memory. It disappears when the server restarts unless you
> save it on the **Lifecycle** tab, and saving requires TurboQuant vector storage: pick it in
> **Configure the workflow** before you run if you want to keep the result.
>
> The vocabulary and the embedding model are written to the server's artifact store and stay
> there. The index is listed on **Workspace search** and on **Lifecycle** as soon as it is built.

Note the three facts in that copy that no current string states: durability of the artifacts,
in-memory-ness of the index, and the flat-float-cannot-be-saved trap.

---

## 6. OPINION: other findings worth a ticket

- **P1: the tab title oversells.** `Text to searchable knowledge` / `Build and explore in one
  flow` (`index.html:388-389`) is the only place in the app that describes the pipeline, and it
  describes it in words ("distill a static embedding model") that assume the reader already knows
  the Trainer tab. Compare the Trainer's own honest framing, `Vocabulary to model`
  (`index.html:873`).
- **P1: no sample data.** Analyze has three sample loaders (`index.html:214-216`) and this tab has
  none, so the fastest path to seeing the tab work requires the user to invent a corpus. The
  bundled novels in `src/demo-data.ts` are right there.
- **P2: duplicate stage 2 and 3 defaults disagree with Trainer.** Workflows defaults
  `Min term frequency` to `1` (`index.html:456`); Trainer defaults `Min frequency` to `2`
  (`index.html:927`). Same server call, same effect, two different defaults, no explanation.
- **P2: the artifact strip is three raw ids.** `corpus-workflow.ts:552-560` renders
  `Vocabulary <id>`, `Model <id>`, `Index <id>` as bare `<code>` with no copy button, no link, and
  no explanation of which tab each id is usable on. The Trainer, by contrast, has a
  `Copy id` affordance (`test/vocabulary-trainer.test.ts`, "restores the Copy id label").
- **P2: `Defaults are ready` is hardcoded.** The `.workflow-option-summary` badge at
  `index.html:430` and the `Automatic defaults` badge at `index.html:395` are static markup; no
  code ever writes to them (verified by grep across `src/`). They still say "Defaults are ready"
  on a server where the tab cannot run at all.
- **P3: serial analyze.** Stages 1 and 4 loop `await` per document (`corpus-workflow.ts:271`).
  For a ten document paste that is twenty sequential round trips, and the only progress signal is
  a text counter (`corpus-workflow.ts:279-280`). The `analyze-stream` endpoint the Analyze tab
  already uses would halve the wall clock and is already wrapped in `src/batch-analysis.ts`.

---

## Questions for the lead

1. Is "Workflows" plural because more workflows are planned? If a saved, named, re-runnable
   workflow is on the roadmap, the rename in section 4 should be deferred and the word reserved.
   If not, the tab should be named after what it produces.
2. Should this tab own cleanup? It creates durable vocabulary and model artifacts on every run and
   offers no way to remove them; the Models & data tab has `delete-static-model` and the Trainer
   lists vocabularies. Repeated demo use will litter the artifact root.
3. Should the default vector storage be TurboQuant when the server offers it, so that
   `Save checkpoint` works out of the box? That changes the default from exact to quantized
   ranking, which is a product call, not a copy fix.
4. Is Corpus search meant to list dynamic indexes (section 2.4)? Either the filter is missing there
   or the heading and bridge paragraph are wrong. Both are one line changes but they point in
   opposite directions.
