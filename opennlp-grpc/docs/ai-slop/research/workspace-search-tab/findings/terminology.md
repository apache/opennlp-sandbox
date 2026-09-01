# Workspace search tab: terminology audit

Scope: every user-visible string inside `opennlp-grpc-webapp-default/index.html:722-788`
(section `id="session-search"`, tab label "Workspace search"), plus the two strings this
tab depends on that live on the Analyze tab, plus every runtime string
`src/semantic-workbench.ts` writes into `#semantic-status`, `#search-results`, and
`#index-count`.

FACT sections quote the exact current string with a `path:line` citation. OPINION
sections carry a priority: P1 confusing or broken for a first-time user, P2 worth doing,
P3 polish.

## 1. Inventory of user-visible terms on this tab

| # | Exact current string | Where | Standard, invented, or repurposed |
|---|---|---|---|
| 1 | `Workspace search` | index.html:49 (tab label) | Repurposed. "Workspace" is standard in developer tools but for a *user account container*, not an index. See section 3. |
| 2 | `Dynamic gRPC search` | index.html:725 (`.section-kicker`) | Invented phrase. "Dynamic" is the project's own proto word (opennlp_search.proto:29-31), not a search-industry term of art. |
| 3 | `Server memory` | index.html:726 (`.local-mode-badge`) | Plain language, accurate for the default case, wrong once a workspace is checkpointed to disk (section 4). |
| 4 | `On-the-fly workspace index` | index.html:727 (`h3#semantic-heading`) | Invented. "On-the-fly" has no precedent in any search product's own docs. |
| 5 | `bounded in-memory index` | index.html:729 | "In-memory index" is standard. "Bounded" is an internal-engineering word, see DynamicSearchIndexRegistry.java:65-72. |
| 6 | `stored exact (flat float) or quantized (TurboQuant)` | index.html:730 | "Quantized" is standard vector-search vocabulary. "Flat" is standard (FAISS `IndexFlat`, Qdrant/Weaviate "flat index"). "TurboQuant" is this project's own provider id, `STANDARD_SEARCH_PROVIDER_TURBO_QUANT`. |
| 7 | `The browser renders server scores and never performs vector ranking.` | index.html:730-731 | Accurate and useful, but it answers a question a first-time user has not asked yet. |
| 8 | `This tab searches dynamic workspaces held in server memory.` | index.html:733 | The sentence the owner flagged. It uses the undefined term twice ("dynamic", "workspace") and defines neither. |
| 9 | `Indexed chunks` | index.html:738 (`dt` for `#index-count`) | "Chunk" is now standard in retrieval tooling. Fine. |
| 10 | `Similarity` / `Cosine` | index.html:739 | Standard. Cosine similarity is the metric name in every vector store. |
| 11 | `Storage` / `gRPC server memory` | index.html:740 | "gRPC" is a transport, not a storage medium. See section 4. |
| 12 | `Workspace to search` | index.html:742 | Repurposed, same problem as #1. |
| 13 | `New workspace (created on first add)` | index.html:744 | Invented. "Add" is undefined here: the add button is on a different tab. |
| 14 | `Vector storage for the next new index` | index.html:746 | "Vector storage" is reasonable. "the next new index" is confusing: it silently means "this control is locked once a workspace exists" (semantic-workbench.ts:609). |
| 15 | `Flat float (exact)` / `TurboQuant (quantized)` | index.html:748-749 | See #6. |
| 16 | `How to use workspace search` | index.html:752 (`<summary>`) | Standard disclosure pattern, already used six times in this file. |
| 17 | `Semantic query` | index.html:773 (`<label>`) | "Semantic search" is standard industry vocabulary. As a *field label* it is jargon: the field wants a sentence, not a "semantic query". |
| 18 | `Search workspace` | index.html:777 | Fine once "workspace" is defined. |
| 19 | `Clear workspace index` | index.html:778 | **Mislabelled.** This calls `DeleteSearchIndex` and destroys the index on the server (semantic-workbench.ts:271: `await this.#options.deleteIndex(this.#workspace.id)`). "Clear" reads as "clear the results list". |
| 20 | `Add an embedding-enabled document, then describe what you want to find.` | index.html:775 (placeholder) | "Embedding-enabled document" is invented and backwards: embeddings are a property of the *analysis run*, not of the document. |
| 21 | `Analyze an embedding-enabled document, then add it to the server workspace.` | index.html:782 (initial `#semantic-status`) | Same term. This is the first-run empty state (section 4 of findings/gating-and-links.md). |
| 22 | `No workspace search results yet.` | index.html:785 | Standard empty-state phrasing. |
| 23 | `Add to server workspace` | index.html:252 (Analyze tab) | The single most important control for this tab, and it is on another tab. |
| 24 | `${hit.modelId} · cosine 0.8123` | semantic-workbench.ts:421 | Raw model id plus a four-decimal cosine, with no scale legend on this tab (the Corpus search tab has one at index.html:559-562; this tab has none). |
| 25 | `No compatible vectors were found in the server workspace.` | semantic-workbench.ts:408 | "Compatible" is an internal concept (embedding route match, search-adapter.ts:238-241). A user cannot act on it. |
| 26 | `The server returned no compatible chunks.` | semantic-workbench.ts:314 | Same. |
| 27 | `Detached. The next add creates a new workspace index.` | semantic-workbench.ts:211 | "Detached" is invented UI vocabulary. No mainstream product uses attach/detach for picking an item from a dropdown. |
| 28 | `Attached to 'Workbench index': 12 chunks are searchable.` | semantic-workbench.ts:226-227 | Same. Also see #29. |
| 29 | `Workbench index` | semantic-workbench.ts:331 (`displayName`) | **Every workspace this tab creates gets the same name.** The picker therefore renders `Workbench index · 40 chunks`, `Workbench index · 12 chunks`, ... with nothing to tell them apart. |
| 30 | `Current document heatmap: Sentences` | semantic-workbench.ts:396 | An internal scratch index name. Hidden from this tab's picker (semantic-workbench.ts:183-185) but *not* from the Corpus search picker (server-search-workbench.ts:141-151). |

## 2. Terms the brief asked about that are NOT on this tab

FACT, verified by `grep` over `index.html`:

- `hybrid`: 0 occurrences anywhere in the page.
- `lexical`: 0 occurrences.
- `rerank` / `reranking`: 0 occurrences.
- `neighbour` / `neighbor` / `nearest neighbour`: 0 occurrences.
- `top-k`: 3 occurrences, all on the Corpus search tab (`id="server-search-top-k"`,
  index.html:600-601, labelled `Results`, not `top-k`). This tab has **no** result-count
  control at all: it sends `allHits` when the index supports it, otherwise
  `min(50, maxTopK)` (semantic-workbench.ts:308-310).
- `discovery`: appears only in internal code (`src/discovery.ts`), never in the UI.
- `document window`: appears only in internal code (`src/document-window.ts`, a 16 000
  character pagination bound). Never user-visible.

OPINION (P3): the absence of "hybrid", "lexical", and "rerank" is correct. This tab is
vector-only. The compound query builder that mixes keyword and semantic clauses is on
the Corpus search tab (index.html:615), and this tab does not offer it even though the
server supports keyword components on dynamic indexes
(DynamicSearchIndexRegistry.java:229 `describesVectorAndKeywordComponentsWithAnalysisChainIdentity`).

## 3. "Semantic query" as a field label

FACT: index.html:773 labels the main textarea `Semantic query`. The Corpus search tab
labels the equivalent field `Natural-language query` (index.html:594) with the
placeholder `What should this corpus help you find?` (index.html:596).

OPINION (P2): use the plainer label the sibling tab already uses. Two labels for one
concept in one app is worse than either label alone.

- Current: `Semantic query`
- Proposed: `What are you looking for?`
- Precedent: this app's own Corpus search tab, plus Carbon's empty-state rule
  "Don't use product-specific terms that the user may not yet understand"
  (`reference/onboarding-design-systems.md`). Elastic shows the same instinct on its
  data-views page, defining a concept as a clause of the task sentence rather than naming
  the algorithm: "analytics features such as Discover require a data view to access the
  Elasticsearch data that you want to explore"
  (`reference/onboarding-product-examples.md`).

## 4. Two factually wrong strings

### 4.1 `Storage` / `gRPC server memory` (index.html:740)

FACT: gRPC is the transport. Storage is the search provider instance. The live server
reports three provider instances (`GET /api/v1/search-providers`, fetched 2026-08-28):
`flat_float` (VECTOR, LIVE), `terms` (KEYWORD, LIVE), and `turbo_quant` (VECTOR, LIVE,
BUNDLE, PERSISTENT). Once a workspace is checkpointed through the Lifecycle tab's
`Save checkpoint` (index.html:1007) the data is also on disk under
`search.persist.root` (WorkspaceCheckpointStore.java:57), and the descriptor's
`persisted` field flips true (opennlp_search.proto:487-489). This tab never reads
`persisted`: `readSearchIndexes` in search-adapter.ts:200-229 does not map it.

OPINION (P2):
- Current: `Storage` / `gRPC server memory`
- Proposed: `Storage` / a live value: `Server memory` or `Server memory + disk checkpoint`
- Precedent: Elasticsearch index status surfaces are always live values, never a fixed
  label. See `reference/vocab-elasticsearch.md`.

### 4.2 `Clear workspace index` (index.html:778)

FACT: the handler is `clear()` in semantic-workbench.ts, which calls
`this.#options.deleteIndex(this.#workspace.id)` and then reports
`The gRPC server deleted the workspace index.` (semantic-workbench.ts:277). The verb in
the status message is "deleted"; the verb on the button is "Clear".

OPINION (P1):
- Current: `Clear workspace index`
- Proposed: `Delete this workspace`
- Precedent: destructive actions are named by their real effect and confirmed. Shopify
  Polaris: "Use the destructive appearance for actions that will delete data".
  Atlassian's Button guidance says the same. See `reference/onboarding-design-systems.md`.
  P1 because a user who has just spent minutes indexing documents can lose all of it by
  pressing a button that reads like "clear the screen".

## 5. `Attached` and `Detached`

FACT: semantic-workbench.ts:206-232. Selecting a workspace from `#workspace-index-select`
sets `#semantic-status` to `Attached to '<name>': N chunks are searchable.`; selecting
the blank option sets it to `Detached. The next add creates a new workspace index.`

OPINION (P2): attach/detach is storage-engineer vocabulary (mounting a volume, attaching
a debugger). No search product uses it for a dropdown selection.

- Current: `Attached to 'Workbench index': 12 chunks are searchable.`
- Proposed: `Searching 'Contract review': 12 chunks.`
- Current: `Detached. The next add creates a new workspace index.`
- Proposed: `Nothing selected. The next document you add starts a new workspace.`
- Precedent: no mainstream product uses attach or detach for a picker. Compare Grafana,
  which states the concept plainly ("A panel is a container that displays the
  visualization...") and its empty states in the indicative
  ("You haven't created any dashboards yet"), quoted in
  `reference/onboarding-product-examples.md` and `reference/onboarding-empty-states.md`.

## 6. Every workspace is called "Workbench index"

FACT: semantic-workbench.ts:331 hard-codes `displayName: "Workbench index"` on every
`IndexDocuments` call that creates a new index. The picker builds its option label from
that name (semantic-workbench.ts:190-192):
`` `${workspace.label} · ${size} ${size === 1 ? "chunk" : "chunks"}` ``.

OPINION (P1): with two or more workspaces the picker is unusable. Nothing on this tab
lets a user name a workspace, and nothing renames one afterwards. The proto has carried
`display_name` as "Human-readable name suitable for an index selector"
(opennlp_search.proto:457-458) since the beginning; the front end simply never asks.

- Proposed: a `Workspace name` text input beside the storage picker, defaulting to the
  analyzed document's title, sent as `displayName` on the first `IndexDocuments` call.
- Precedent: every vector database that has a UI makes the collection name a required
  user input on create. Qdrant, Weaviate, Pinecone, Chroma. See
  `reference/vocab-vector-databases.md`.

## 7. Recommendation summary

| Priority | Current string | Proposed string |
|---|---|---|
| P1 | `Clear workspace index` (index.html:778) | `Delete this workspace` |
| P1 | `displayName: "Workbench index"` (semantic-workbench.ts:331) | user-entered name, defaulting to the document title |
| P1 | `This tab searches dynamic workspaces held in server memory.` (index.html:733) | see findings/what-is-a-workspace.md section 5 |
| P2 | `Semantic query` (index.html:773) | `What are you looking for?` |
| P2 | `Attached to '...'` / `Detached.` (semantic-workbench.ts:211, 226) | `Searching '...'` / `Nothing selected.` |
| P2 | `Storage` / `gRPC server memory` (index.html:740) | live value derived from the descriptor's `persisted` flag |
| P2 | `Add an embedding-enabled document...` (index.html:775) | `Describe what you want to find.` |
| P3 | `On-the-fly workspace index` (index.html:727) | see findings/what-is-a-workspace.md section 4 |
| P3 | `Dynamic gRPC search` (index.html:725) | `Search what you just analyzed` |
| P3 | no score legend on this tab | reuse the `#score-legend` markup from index.html:559-562 |

## Questions for the lead

1. Should "workspace" survive as the product word at all, or does the whole tab get
   renamed? findings/what-is-a-workspace.md section 4 gives six options; this file
   assumes the word survives.
2. `Clear workspace index` deletes server state with no confirmation step. Is a
   confirm dialog in scope, or only the rename?
3. Should this tab expose a result-count control like the Corpus search tab's
   `Results` field (index.html:600), or is `allHits` the deliberate design?
