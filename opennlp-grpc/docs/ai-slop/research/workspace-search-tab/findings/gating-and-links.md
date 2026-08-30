# Workspace search tab: empty states, model gating, and cross-tab links

All FACT lines were verified against the working tree and against the live demo instance
at `http://127.0.0.1:7172` on 2026-08-28. Priorities: P1 confusing or broken for a
first-time user, P2 worth doing, P3 polish.

## 1. Live server state on 2026-08-28

FACT, read-only calls:

```
GET /api/v1/search-indexes   -> {}
GET /api/v1/collections      -> {}
GET /api/v1/index-aliases    -> {}
GET /api/v1/installed-models -> {}
GET /api/v1/static-models    -> {}
GET /api/v1/search-providers -> three instances (below)
```

`search-providers` on this instance:

| instanceId | capabilities |
|---|---|
| `flat_float` | VECTOR, LIVE |
| `terms` | KEYWORD, LIVE |
| `turbo_quant` | VECTOR, LIVE, BUNDLE, PERSISTENT |

`GET /api/v1/search-indexes` returning the bare object `{}` (no `indexes` key) is the
zero-workspace state. `readSearchIndexes` handles it: `Array.isArray(envelope?.indexes)`
is false, so it returns `[]` (search-adapter.ts:179-181). Nothing throws.

Embeddings *are* available on this instance: `GET /api/v1/model-bundles` reports
`minilm-gpu`, `COMPONENT_TYPE_EMBEDDER`, backend `cuda`, in the `en-basic` bundle.

## 2. What the tab renders with zero workspaces

FACT, the complete first-run rendering of `#session-search`:

| Element | State |
|---|---|
| `#workspace-index-select` (index.html:743) | one option, `New workspace (created on first add)` |
| `#index-count` (index.html:738) | `0`, under the label `Indexed chunks` |
| the `Similarity` fact (index.html:739) | fixed text `Cosine` |
| the `Storage` fact (index.html:740) | fixed text `gRPC server memory` |
| `#workspace-provider-select` (index.html:747) | enabled, defaulting to `Flat float (exact)` |
| `#semantic-query` (index.html:774) | **disabled**, placeholder `Add an embedding-enabled document, then describe what you want to find.` |
| `#search-button` (index.html:777) | **disabled** |
| `#clear-index-button` (index.html:778) | **disabled** |
| `#semantic-status` (index.html:781-783) | `Analyze an embedding-enabled document, then add it to the server workspace.` |
| `#search-results` (index.html:784-786) | `No workspace search results yet.` |

The disabled states come from `updateControls()` (semantic-workbench.ts:607-622):
`searchable` is `Boolean(this.#workspace) || indexable`, and both are false before any
analysis.

OPINION (P1): this is a dead end, not an empty state. Every industry empty-state
guideline says the same three things: explain what the thing is, say why the area is
empty, and give one primary action that fills it. See
`reference/onboarding-empty-states.md`. This tab does one of the three, and the
"explanation" is a sentence containing the undefined term.

Specifically, there is **no control on this tab that a first-time user can press**.
Four of the five interactive elements are disabled and the fifth (the storage picker)
is meaningless until a workspace exists. The one action that changes anything,
`Add to server workspace` (index.html:252), lives on the Analyze tab and is not linked
from here.

Proposed first-run empty state, replacing the `#search-results` placeholder:

- Heading: `No workspaces yet`
- Body: `A workspace is a search index the server builds from documents you analyze here. It lives in the server's memory until you save or delete it.`
- Primary action: a `.link-button` with `data-workbench-jump="analysis"`, labelled
  `Analyze a document to start one`
- Precedent: Shopify Polaris empty state (heading, one sentence, one primary action);
  GitHub's empty-repository page; Kibana's "Add data" screen. See
  `reference/onboarding-empty-states.md`.

## 3. Model and backend gating

For each feature: what is required, what the user sees today, and what a browned-out
state should say.

### 3.1 No embedding model configured

- Feature: everything on this tab. Indexing requires chunk embeddings.
- Required: an embedder in a model bundle, or a static model trained on the Trainer tab.
  The Analyze tab's selector is populated from `capabilities.embeddingModels`
  (analysis-controls.ts:131-135) merged with runtime-trained models
  (analysis-controls.ts:101-108).
- What the user sees today: on the **Analyze** tab, `#embedding-model-select` renders the
  single option `No embedding model configured` and is disabled
  (analysis-controls.ts:202-212, matching the static markup at index.html:140-142).
  On the **Workspace search** tab: nothing. The status still reads
  `Analyze an embedding-enabled document, then add it to the server workspace.`, which is
  advice the user cannot follow.
- FACT: `SemanticWorkbench` never reads model capability. It infers indexability only
  from the analysis response shape (`indexableDocument`, semantic-workbench.ts:635-659,
  which requires `document.chunkEmbeddingGroups[].embeddingModelIds`).
- Proposed browned-out state (P1): when the analysis capability set reports zero
  embedding models, replace the tab body with:
  `This server has no embedding model installed, so it cannot build a workspace. Install one on the Models & data tab, or train one on the Trainer tab.`
  plus `data-workbench-jump="models"` and `data-workbench-jump="trainer"` links.

### 3.2 Embeddings available, but the analysis ran without them

- Feature: `Add to server workspace` (index.html:252).
- Required: the Analyze run must select an embedding model **and** at least one chunk
  strategy, so the response carries `chunkEmbeddingGroups`.
- What the user sees today: the button stays disabled
  (semantic-workbench.ts:612, `this.#addButton.disabled = !indexable || this.#busy`).
  No text says why.
- FACT: there **is** an explanatory string,
  `This result has no indexed chunk embeddings. Select an embedding model and chunk strategy.`
  (semantic-workbench.ts:246), but it is unreachable: it is set inside
  `addCurrentDocument()`, which only runs on a click of a button that is disabled in
  exactly that case. This is dead code from the user's point of view.
- Proposed (P2): render that sentence as persistent helper text under the disabled
  button instead of as a click-time status.

### 3.3 Dynamic indexing disabled by the operator

- Feature: creating or extending any workspace.
- Required: `search.dynamic.enabled` must not be `false`
  (OpenNlpGrpcServer.java:169-170; the default is `true`). When false the server
  installs `DynamicSearchIndexRegistry.disabled()`
  (DynamicSearchIndexRegistry.java:172-175).
- What the user sees today: exact server text
  `Dynamic search indexing is disabled by the server operator`
  (DynamicSearchIndexRegistry.java:1087-1090, an `UNIMPLEMENTED`). The gateway forwards
  the message body, `responseError` in api.ts:546-561 extracts `body.message`, and
  `SemanticWorkbench` prints it verbatim into `#semantic-status`
  (semantic-workbench.ts:255). The user only sees it **after** analyzing a document and
  pressing Add on the other tab.
- Also: `ListSearchIndexes` returns no dynamic indexes in this mode, so the picker looks
  identical to the ordinary empty state. There is no way to tell "nothing indexed yet"
  from "this feature is switched off".
- Proposed browned-out state (P1): the tab should ask once at startup and, when dynamic
  indexing is off, show
  `Workspace search is switched off on this server. An operator can enable it with search.dynamic.enabled=true. Read-only indexes are still searchable on the Corpus search tab.`
  with the existing `data-workbench-jump="corpus-search"` link. This needs a capability
  flag on `GET /api/v1/service-info`, which does not exist today (verified: the live
  `service-info` payload has no search fields at all).

### 3.4 The default storage choice cannot be saved

FACT, and the sharpest gap on this tab:

- `#workspace-provider-select` defaults to its first option,
  `Flat float (exact)` = `STANDARD_SEARCH_PROVIDER_FLAT_FLOAT` (index.html:748).
- The live `flat_float` instance declares only VECTOR and LIVE, **not** PERSISTENT.
- `persist()` rejects a non-persistent instance with
  `Search provider instance 'flat_float' is not persistent`
  (DynamicSearchIndexRegistry.java:524-526).
- The help callout on this very tab tells the user to go and do exactly that:
  `To keep a workspace across restarts, or to seal, alias, or rebuild it, use the Lifecycle tab.` (index.html:760-761).
- The Lifecycle tab does not gate on it either: `#lifecycle-persist-button` is enabled
  whenever any index is selected (lifecycle-workbench.ts:545,
  `this.#persistButton.disabled = this.#busy || !hasIndex;`). The provider capability
  list is read (lifecycle-workbench.ts:195-199) but only rendered as a label.

OPINION (P1): the default choice silently forecloses the documented next step, and the
user finds out only after building a workspace they cannot keep.
- Proposed: helper text under `#workspace-provider-select`:
  `Flat float keeps full-precision vectors but cannot be saved to disk on this server. Pick TurboQuant if you want to checkpoint or seal this workspace later.`
  and disable the persist and seal buttons on the Lifecycle tab when the selected index's
  provider instance lacks the `persistent` capability.
- Precedent: Material Design 3 supporting text under a select, and the Carbon
  "definition tooltip" for the term itself. See `reference/onboarding-design-systems.md`.

### 3.5 Vector-storage picker locks silently

FACT: `this.#providerSelect.disabled = Boolean(this.#workspace) || this.#busy;`
(semantic-workbench.ts:609). Once a workspace is attached the picker greys out, with no
explanation. The reason is real: `IndexDocuments` rejects a provider change with
`IndexDocuments provider must match the existing dynamic index provider instance '<id>'`
(DynamicSearchIndexRegistry.java:271-274).

OPINION (P2): the label already half-admits this,
`Vector storage for the next new index` (index.html:746), but a user reading that label
next to a greyed-out control cannot tell whether it is broken. Add helper text:
`Storage is fixed when a workspace is created. Start a new workspace to change it.`

### 3.6 Bounded limits with no user-facing warning

FACT, from DynamicSearchIndexRegistry.java:65-72: `MAX_INDEXES = 32`,
`MAX_DOCUMENTS_PER_REQUEST = 16`, `MAX_DOCUMENTS_PER_INDEX = 256`,
`MAX_CHUNKS_PER_INDEX = 10_000`, `MAX_SOURCE_DOCUMENT_BYTES_PER_INDEX = 16 MiB`. Hitting
the index cap raises `Dynamic search index count reached 32`
(DynamicSearchIndexRegistry.java:277), a `RESOURCE_EXHAUSTED` shown raw in
`#semantic-status`.

OPINION (P3): the heatmap on the Analyze tab quietly creates one extra index per chunk
projection (`Current document heatmap: <title>`, semantic-workbench.ts:396), so the cap
is closer than a user expects. Worth naming the cap in the help callout.

## 4. Sealed workspaces and the Corpus search boundary

### 4.1 What sealing does

FACT: `SealIndex` writes a checkpoint and sets the in-memory flag
(DynamicSearchIndexRegistry.java:483-490); the descriptor then reports
`immutable = true` (DynamicSearchIndexRegistry.java:1352, `.setImmutable(sealed)`).
`refreshWorkspaces()` filters on exactly that flag
(semantic-workbench.ts:183-185): `indexes.filter((index) => !index.immutable && !index.label.startsWith(HEATMAP_INDEX_PREFIX))`.
So a sealed workspace leaves this tab's picker.

### 4.2 Bug: the picker does not refresh after a seal

FACT: `LifecycleWorkbench` is constructed at main.ts:290-309 with **no**
`onIndexChanged` callback. Compare `CorpusWorkflowWorkbench`, which does have one and
does refresh both search tabs (main.ts:373-376). `SemanticWorkbench.initializeWorkspaces()`
is therefore called only at startup (main.ts:433) and after a corpus-workflow run.

Consequence: seal a workspace on the Lifecycle tab, switch back to Workspace search, and
the stale picker still offers it as writable. Selecting it and adding a document produces
the raw server text `Sealed search index '<id>' is immutable`
(DynamicSearchIndexRegistry.java:266-268).

OPINION (P2): pass an `onIndexChanged` to `LifecycleWorkbench` that calls
`semanticWorkbench.initializeWorkspaces()` and `serverSearchWorkbench.initialize()`,
exactly as the corpus workflow already does.

### 4.3 Bug: `#workspace` is not cleared when the picker is rebuilt

FACT: `refreshWorkspaces()` (semantic-workbench.ts:182-201) rewrites
`#workspaceSelect` but never touches the private `#workspace` field. If the attached
workspace is sealed or deleted elsewhere, the select falls back to
`New workspace (created on first add)` while `#workspace` still holds the old
descriptor, so `Search workspace` keeps querying an index the picker says is not
selected.

OPINION (P2): clear `#workspace` whenever the refreshed listing no longer contains it,
and set the status accordingly.

### 4.4 The tab-bridge sentence is not true

FACT, index.html:733-736:
`This tab searches dynamic workspaces held in server memory. Read-only corpus and persisted indexes are searched on the Corpus search tab.`

Two problems:
1. A **persisted** workspace is not sealed. `persist()` does not set the sealed flag
   (DynamicSearchIndexRegistry.java:466-473 calls `persist(index, index.sealed())`), so
   `immutable` stays false and the workspace remains in *this* tab's picker. The sentence
   says it moves to Corpus search. It does not. Only *sealed* workspaces move.
2. The Corpus search tab does **not** filter anything. `ServerSearchWorkbench.initialize()`
   lists every index and adds all of them to `#server-search-index`
   (server-search-workbench.ts:141-151). There is no `immutable` filter anywhere in that
   file (`grep -n immutable src/server-search-workbench.ts` returns only line 146, a
   message string). So dynamic workspaces, *and* the internal
   `Current document heatmap: ...` scratch indexes, appear in the Corpus search picker.

OPINION (P1): fix the sentence and fix the filter. Proposed sentence:
`Everything here lives in this server's memory and disappears when the server restarts, unless you save it on the Lifecycle tab. Indexes an operator shipped with the server are on the Corpus search tab.`
Proposed code change: filter `Current document heatmap:` out of the Corpus search picker
at minimum, and decide deliberately whether writable workspaces belong there.

## 5. Cross-tab links

### 5.1 Links that exist today

`grep -n 'data-workbench-jump' index.html` returns exactly three:

| index.html line | From | To | Text |
|---|---|---|---|
| 555 | Corpus search intro | `session-search` | `Workspace search` |
| 592 | Corpus search index-picker helper | `workflows` | `build your own workspace index` |
| 735 | **Workspace search** intro | `corpus-search` | `Corpus search` |

So this tab has exactly one outbound link, and it points at the tab a user is least
likely to need next.

### 5.2 Links a user needs and does not have

| Need | Trigger | Target tab | Exists? |
|---|---|---|---|
| Analyze a document, the required first step | first-run empty state, index.html:782 | `analysis` | **No.** The status text says "Analyze ... then add it" with no link. |
| Reach the `Add to server workspace` button | the whole tab is inert without it | `analysis` | **No.** |
| Save, seal, alias, or rebuild a workspace | help callout, index.html:760-761 | `lifecycle` | **No.** The tab is named in prose only. |
| Install an embedding model when none exists | section 3.1 | `models` | **No.** |
| Train a static embedding model | section 3.1 | `trainer` | **No.** |
| Group workspaces into a collection | not mentioned on this tab at all | `lifecycle` | **No.** |

Inbound, from other tabs to this one:

| Source | index.html line | Links here? |
|---|---|---|
| Analyze help callout: `query it from the Workspace search tab` | 103-104 | **No**, prose only. |
| Analyze `Add to server workspace` button | 252 | **No.** Pressing it leaves the user on the Analyze tab with a status line and no route onward. |
| Trainer closing paragraph: `index the analyzed documents in Workspace search` | 957 | **No**, prose only. |
| Corpus search intro | 555 | Yes. |
| Lifecycle intro, `Checkpoint and seal dynamic workspaces` | 969-971 | **No.** |

OPINION (P1): the single highest-value change on this tab is a link from the empty state
to the Analyze tab, and a link from the Analyze tab's Add button back here after a
successful add. Every prose mention of another tab in index.html should become a
`.link-button` with `data-workbench-jump`; the mechanism already exists and is wired
generically in workbench-navigation.ts:40-42, so each conversion is a one-line HTML edit.

## Questions for the lead

1. Should the Corpus search picker filter out writable dynamic workspaces so the two
   tabs are actually disjoint, or should the tab-bridge sentences be reworded to admit
   the overlap?
2. `service-info` carries no search capability flags. Adding
   `dynamicSearchEnabled` there would let this tab brown out honestly instead of looking
   empty. Is extending that response in scope?
3. Should `Flat float (exact)` remain the default storage choice, given that it cannot
   be checkpointed on the shipped configuration?
