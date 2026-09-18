# Lifecycle tab: the "seal immutable" error, and every empty state

Scope: the `lifecycle-workbench` section of
`/work/worktrees/opennlp/sandbox-grpc-query/opennlp-grpc/opennlp-grpc-webapp-default/index.html`
(lines 962 to 1115) and `opennlp-grpc-webapp-default/src/lifecycle-workbench.ts`.

Owner's words: *"If I click 'seal immutable' I get an error. Wouldn't it be better to say 'no __
found, click here to create your first'. But 'seal immutable', what is that?"*

---

## 1. The label the owner remembers does not exist

FACT. There is no control labelled "seal immutable" anywhere in the UI. The button reads
**"Seal as read-only"** (`opennlp-grpc-webapp-default/index.html:1008-1009`). The word "immutable"
reaches the user through two other channels:

- the fact row **"Sealed"** rendered by
  `opennlp-grpc-webapp-default/src/lifecycle-workbench.ts:180`;
- the proto and gateway vocabulary, `SearchIndexDescriptor.immutable`, read at
  `opennlp-grpc-webapp-default/src/search-adapter.ts:195`.

OPINION (P2). That the owner recalls the button as "seal immutable" is itself the finding: the
button, its helper text, and the underlying field use three different words (*seal*, *read-only*,
*immutable*) for one concept, so no single word sticks.

---

## 2. Reproducing the error, exactly

I did not call `seal-index` or `persist-index`, per the brief. The error is established from the
code path plus live read-only state.

### 2a. The empty case does NOT produce an error

FACT. Live state on the demo instance right now:

```
$ curl -s http://127.0.0.1:7172/api/v1/search-indexes   -> {}
$ curl -s http://127.0.0.1:7172/api/v1/collections      -> {}
$ curl -s http://127.0.0.1:7172/api/v1/index-aliases    -> {}
```

With no dynamic workspaces, `refresh()` sets `#indexes` to an empty array
(`lifecycle-workbench.ts:129`), `renderIndexOptions()` puts a single disabled option reading
**"No dynamic workspaces"** into the select (`lifecycle-workbench.ts:148`), and `updateControls()`
disables the seal, persist, alias, and rebuild buttons because `selectedIndex()` is undefined
(`lifecycle-workbench.ts:544-548`). A disabled button cannot be clicked, so **the empty state is not
the error path**. The status line reads:

> "Index documents in Workspace search to create a dynamic workspace first."
> (`lifecycle-workbench.ts:136`)

### 2b. The real error path: the default vector storage cannot be sealed

FACT. Every dynamic workspace the UI creates by default uses the `flat_float` provider, and
`flat_float` does not declare the persistence capability, so **persist and seal fail on it every
single time**.

The chain:

1. Workspace search: the vector-storage select's first and therefore default option is
   `STANDARD_SEARCH_PROVIDER_FLAT_FLOAT`, labelled "Flat float (exact)"
   (`index.html:748`). It is sent on index creation at
   `opennlp-grpc-webapp-default/src/semantic-workbench.ts:333`.
2. Workflows: the provider select is populated in server order and the server lists `flat_float`
   first, so "Exact flat float" is the default (`index.html:450`,
   `opennlp-grpc-webapp-default/src/corpus-workflow.ts:420-431`); it is sent at
   `corpus-workflow.ts:220`, with `FLAT_FLOAT_PROVIDER` as the hard fallback.
3. The service default is also flat float:
   `opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/DynamicSearchIndexRegistry.java:845-846`
   resolves `StandardSearchProvider.STANDARD_SEARCH_PROVIDER_FLAT_FLOAT`.
4. `FlatFloatSearchIndexProviderFactory` declares only `VECTOR, LIVE`
   (`opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/FlatFloatSearchIndexProviderFactory.java:46-50`).
   Only TurboQuant declares `SEARCH_PROVIDER_CAPABILITY_PERSISTENT`
   (`opennlp-grpc-search-turboquant/src/main/java/org/apache/opennlp/grpc/search/turboquant/TurboQuantSearchIndexProviderFactory.java:119-125`).
   The live server confirms this:

   ```
   $ curl -s http://127.0.0.1:7172/api/v1/search-providers
   flat_float  -> VECTOR, LIVE
   terms       -> KEYWORD, LIVE
   turbo_quant -> VECTOR, LIVE, BUNDLE, PERSISTENT
   ```

5. Both `PersistIndex` and `SealIndex` funnel into the same private writer,
   `DynamicSearchIndexRegistry.persist(DynamicIndex, boolean)`
   (`DynamicSearchIndexRegistry.java:519-546`), which throws at lines 524-527:

   ```
   Search provider instance '<instanceId>' is not persistent
   ```

   as `FAILED_PRECONDITION`.

6. The gateway passes the gRPC description through verbatim
   (`opennlp-grpc-webapp/src/main/java/org/apache/opennlp/grpc/webapp/GrpcJsonApi.java:194-202`)
   and encodes it as
   `{"code":"...","message":"..."}` (`GrpcJsonApi.java:784-787`), with
   `FAILED_PRECONDITION` mapped to **HTTP 412**
   (`opennlp-grpc-webapp/src/main/java/org/apache/opennlp/grpc/webapp/GrpcHttpStatusMapper.java:34-50`).

7. The browser lifts `message` straight out of the body
   (`opennlp-grpc-webapp-default/src/api.ts:546-560`) into an `Error`, and
   `errorMessage()` prints `error.message` unchanged
   (`opennlp-grpc-webapp-default/src/ui-utils.ts:65-67`), which `run()` writes into
   `#lifecycle-workspace-status` (`lifecycle-workbench.ts:535-536`).

**So the exact text the owner saw, under `#lifecycle-workspace-status`, is:**

> Search provider instance 'flat_float' is not persistent

FACT. No part of the Lifecycle tab, the Workspace search tab, or the Workflows tab warns that
choosing exact flat float forfeits checkpoint and seal. The Workflows helper text says only:

> "Exact storage is the default; choose TurboQuant when the server offers it."
> (`index.html:452`)

OPINION (P1). This is the single worst defect on the tab. The default path through the product
builds a workspace that the tab's two headline buttons can never act on, and the failure is a raw
server-internals sentence naming a provider instance id the user never typed.

### 2c. The second error path: no persistence root

FACT. If the operator has not set `search.persist.root`, the same writer throws first, at
`DynamicSearchIndexRegistry.java:519-523`:

> Index persistence is not configured; set search.persist.root

Also `FAILED_PRECONDITION`, also HTTP 412, also shown verbatim. Config key
`WorkspaceCheckpointStore.ROOT_KEY = "search.persist.root"`
(`opennlp-grpc-service/.../WorkspaceCheckpointStore.java:57`). Note for the lead: the S3 store
add-on is **not** involved. `opennlp-grpc-store-s3` implements `VocabularyStoreProvider` only
(`opennlp-grpc-store-s3/src/main/java/org/apache/opennlp/grpc/store/s3/S3VocabularyStoreProvider.java:33-49`);
there is no S3-backed checkpoint store. Workspace checkpoints are local-filesystem only.

### 2d. Third path: a stale selection

FACT. `refresh()` runs on load and after each action. If the server is restarted or the index is
deleted from another tab between refreshes, the selection goes stale and the seal throws
`NOT_FOUND` from `DynamicSearchIndexRegistry.requireDynamic`
(`DynamicSearchIndexRegistry.java:705-714`), HTTP 404:

> Unknown dynamic search index '<indexId>'

---

## 3. Proposed empty state and gating

OPINION.

### P1: gate the buttons on capability, not just on selection

`updateControls()` (`lifecycle-workbench.ts:543-552`) checks only that an index is selected.
`SearchIndex` already carries `providerId` (`search-adapter.ts`), and
`/api/v1/search-providers` already reports capabilities and is already fetched by `refresh()`
(`lifecycle-workbench.ts:124`). Cross-reference them and disable persist/seal when the selected
workspace's provider lacks `persistent`, with an inline explanation instead of a server error:

> This workspace uses exact flat float storage, which keeps vectors in memory only. Checkpoints and
> sealing need TurboQuant storage. Rebuild it with TurboQuant below, or create the next workspace
> with TurboQuant in Workspace search.

### P1: empty state with a jump, as the owner asked

Current (`lifecycle-workbench.ts:136`), a bare instruction with no link:

> "Index documents in Workspace search to create a dynamic workspace first."

Proposed: keep the sentence but make it actionable, and put it where the empty select is, not only
in the page-level status line. Replace the disabled `"No dynamic workspaces"` option
(`lifecycle-workbench.ts:148`) with a visible empty-state block:

> **No workspaces yet.** A workspace is a search index you build in this session. Build your first
> one in **Workflows**, or add analyzed documents to one in **Workspace search**.

with two `data-workbench-jump` buttons: `data-workbench-jump="workflows"` (primary, it is the
guided end-to-end path) and `data-workbench-jump="session-search"` (secondary). Tab ids are
confirmed at `index.html:44-49`.

### P2: translate the remaining server errors at the boundary

Everything the gateway returns is printed raw. Map the three known `FAILED_PRECONDITION` texts in
`lifecycle-workbench.ts` before display:

| Server text (verbatim) | Proposed user text |
| --- | --- |
| `Search provider instance 'flat_float' is not persistent` | Exact flat float storage keeps vectors in memory only, so this workspace cannot be checkpointed or sealed. Rebuild it with TurboQuant storage first. |
| `Index persistence is not configured; set search.persist.root` | This server has no checkpoint directory configured, so nothing can be saved to disk. An operator needs to set `search.persist.root`. |
| `Unknown dynamic search index '<id>'` | That workspace is gone, probably deleted or lost in a restart. Press Refresh. |
| `Sealed search index '<id>' is immutable` | This workspace is sealed and accepts no more documents. Rebuild it to make an editable copy. |

---

## 4. Every other empty and gated state on the tab

FACT, current text, with the file:line that produces it.

| Panel | Condition | Text today | Assessment |
| --- | --- | --- | --- |
| Page status | no workspaces | "Index documents in Workspace search to create a dynamic workspace first." | `lifecycle-workbench.ts:136`. Accurate, but names only one of the two ways to make one, and does not link. P1. |
| Workspace select | no workspaces | "No dynamic workspaces" | `lifecycle-workbench.ts:148`. Dead end. P1. |
| Aliases | none | "No aliases yet." | `lifecycle-workbench.ts:212`. Says nothing about what an alias is or why to make one. P2. |
| Providers | none reported | "No provider instances reported." | `lifecycle-workbench.ts:186`. Unreachable in practice: two factories ship in `opennlp-grpc-service` itself. P3. |
| Rebuild model select | no trained models | "No trained model yet: distill one on the Trainer tab" | `lifecycle-workbench.ts:238`. **The best empty state on the tab**: it names the blocker and the destination. It is still not a link. P2. |
| Rebuild provider select | always | "Keep the current vector storage" | `lifecycle-workbench.ts:189`. Fine. |
| Collection select | always | "New collection" | `lifecycle-workbench.ts:320`. No explanation of what a collection is. P1, see state-machine-and-vocabulary.md. |
| Collection model select | none | "No model selected" | `lifecycle-workbench.ts:244`. Fine. |
| Coverage label | no collection | "No collection selected." | `lifecycle-workbench.ts:483`. Fine. |
| Term statistics | no collection | "Select or save a collection to see its term statistics." | `lifecycle-workbench.ts:484`. Fine. |
| Term statistics | collection with no terms | "The member indexes hold no analyzable terms yet." | `lifecycle-workbench.ts:499`. Fine, though "analyzable" is jargon. P3. |
| Coverage label | no vocabulary artifact | "No vocabulary artifact is configured; every indexed term counts as new." | `lifecycle-workbench.ts:497`. Honest, but the meter then shows 0% which reads as a failure rather than as "not measured". P2, see vocabulary-drift.md. |
| Watch stream | idle | "Not watching a collection." | `lifecycle-workbench.ts:460`. Fine. |

---

## 5. Two dead or misleading UI elements

FACT. **The "Sealed" fact row can never say "yes".** `renderIndexFacts()` prints
`Sealed: index.immutable ? "yes" : "no"` (`lifecycle-workbench.ts:180`), but `refresh()` has already
filtered every immutable index out of `#indexes` at `lifecycle-workbench.ts:129`
(`indexes.filter((index) => !index.immutable)`). Every index that can ever reach that renderer has
`immutable === false`. The row is a constant.

FACT, and the consequence is worse than a dead row: **a successfully sealed workspace vanishes from
the tab.** `persistSelected(true)` reports

> "Sealed '<label>': it is now read-only and saved to disk."
> (`lifecycle-workbench.ts:268`)

then calls `refresh()` (line 271), which drops the index from the select. The user gets a success
message and watches their workspace disappear, with no sealed-workspace list anywhere on the tab.

OPINION (P1). Show sealed workspaces. Either keep them in the select as disabled entries flagged
"sealed", or add a small read-only "Sealed workspaces" list beside the picker so the seal action has
a visible result. Then the "Sealed" fact row becomes meaningful, and the seal button can be
correctly disabled with "Already sealed" rather than being unreachable.

FACT. Sealing is also weaker than "immutable" implies. `DeleteSearchIndex` does not check the
sealed flag; only the `IndexDocuments` write path does
(`DynamicSearchIndexRegistry.java:266-269`, message `Sealed search index '<id>' is immutable`).
A sealed workspace can still be deleted. The helper text says it becomes "permanently read-only"
(`index.html:1014-1015`), which overstates it. P2: say "accepts no further documents" and drop
"permanently".

---

## Questions for the lead

1. Should the Lifecycle tab quietly steer users to TurboQuant, or should the product make exact flat
   float persistable? The current split means the default choice on two other tabs silently breaks
   this tab.
2. Should sealed workspaces stay visible on this tab (my recommendation), or is disappearing from
   the "dynamic workspace" picker intended because they are no longer dynamic?
3. Is a sealed index meant to be deletable? Today it is, which contradicts "permanently read-only".

---

## Appendix: "immutable" means two different things in one codebase

FACT, verified directly, and it corrects a natural misreading of the delete path.

`OpenNlpSearchServiceImpl.deleteSearchIndex`
(`opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/OpenNlpSearchServiceImpl.java:267-270`)
rejects deletion with:

> DeleteSearchIndex cannot delete immutable index '<id>'

but the guard is `registry.find(indexId) != null`, and `registry` is the **startup bundle** registry,
not the dynamic one. So that message fires only for operator-configured startup indexes. A *sealed
dynamic workspace* falls straight through to `dynamicRegistry.delete(indexId)`
(`DynamicSearchIndexRegistry.java`, `delete`), which never consults `sealed()` and additionally
deletes the on-disk checkpoint.

So the word "immutable" carries two incompatible meanings:

1. **Startup bundle**: loaded by the operator, cannot be deleted, cannot be persisted or sealed
   (`requireLifecycleIndexId`, `OpenNlpSearchServiceImpl.java:644-654`, message
   "startup bundles are already immutable and durable").
2. **Sealed dynamic workspace**: `immutable=true` in the descriptor, refuses new documents, but
   **can be deleted**, checkpoint and all.

OPINION (P2). This is a doc and terminology bug before it is a code bug. Whatever the seal button
ends up being called, the descriptor field and the delete error message should not both say
"immutable" for these two different things. Suggest: startup bundles are "built-in" or "operator
indexes"; sealed workspaces are "read-only".
