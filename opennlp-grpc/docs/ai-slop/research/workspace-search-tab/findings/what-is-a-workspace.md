# What a "dynamic workspace" actually is, and what to call it

The owner's question: "no one would know what a 'dynamic workspace' means". This file
answers it in three parts: what the thing is in this codebase (FACT), what the industry
calls the same thing (FACT, with sources in `reference/`), and what to name it and how to
explain it (OPINION, prioritised).

## 1. FACT: what a dynamic workspace is

A dynamic workspace is **one vector search index that this server built in its own memory
from documents you analyzed in this browser session, and that you can still add to**.

That is the whole concept. Everything below is the evidence.

### 1.1 The server has exactly two kinds of index

`opennlp_search.proto:29-31` states the split at the top of the service:

> Search over immutable indexes loaded by the operator and bounded dynamic flat indexes
> created from analyzed document shapes. Dynamic indexes live only for the server process
> and can be disabled by the operator.

The wire form of the distinction is a single boolean on the descriptor,
`opennlp_search.proto:472-473`:

> `// True for operator-loaded indexes and false for process-local dynamic indexes.`
> `bool immutable = 8;`

There is no `kind` enum. "Dynamic" is simply `immutable == false`. The front end reads
that one field (search-adapter.ts:195, 215) and uses it as the sole filter that decides
which of the two search tabs an index appears on (semantic-workbench.ts:183-185).

### 1.2 Where the data lives

`DynamicSearchIndexRegistry` (opennlp-grpc-service/.../search/DynamicSearchIndexRegistry.java)
is an in-process `LinkedHashMap<String, DynamicIndex>` (line 77) with hard ceilings
(lines 65-72): 32 indexes, 256 documents per index, 10 000 chunks per index, 16 MiB of
retained source per index. The whole registry is created by the server and dies with it,
unless a checkpoint was written.

The persistence class is literally named for the concept:
`WorkspaceCheckpointStore`, keyed by the config property `search.persist.root`
(WorkspaceCheckpointStore.java:57). So "workspace" is already the server's own word for
this; the UI did not invent it.

### 1.3 The three states a workspace can be in

FACT, from the persist and seal paths:

| State | How you get there | `immutable` | `persisted` | Survives restart? | Accepts documents? | Which tab |
|---|---|---|---|---|---|---|
| Live | first `IndexDocuments` call | false | false | no | yes | Workspace search |
| Saved (checkpointed) | Lifecycle `Save checkpoint` (index.html:1007) -> `PersistIndex` | false | true | yes | yes | Workspace search |
| Sealed | Lifecycle `Seal as read-only` (index.html:1009) -> `SealIndex` | **true** | true | yes | **no** | Corpus search |

Evidence: `persist(index, index.sealed())` keeps the flag as it was
(DynamicSearchIndexRegistry.java:466-473); `seal()` calls `persist(index, true)` then
`index.markSealed()` (lines 483-490); the descriptor sets
`.setImmutable(sealed)` (line 1352). A further `IndexDocuments` against a sealed index
raises `Sealed search index '<id>' is immutable` (lines 266-268).

This three-state table is the single most useful thing that is missing from the UI. The
tab today presents only a binary, and states it wrongly (see
findings/gating-and-links.md section 4.4).

### 1.4 The vocabulary the UI never surfaces

The proto has a capability enum that already carries the right words
(opennlp_search.proto:429-442):

- `SEARCH_PROVIDER_CAPABILITY_LIVE`: "Serves mutable live indexes published as atomic snapshots."
- `SEARCH_PROVIDER_CAPABILITY_BUNDLE`: "Loads immutable startup index bundles."
- `SEARCH_PROVIDER_CAPABILITY_PERSISTENT`: "Persists index data beyond the server process."

**Live** and **bundle** are exactly the two things the two search tabs are for, and the
words are already in the API. The UI says "dynamic" and "on-the-fly" instead.

## 2. FACT: what the industry calls this

Excerpts and source URLs are in `reference/vocab-lucene.md`,
`reference/vocab-elasticsearch.md`, `reference/vocab-solr.md`,
`reference/vocab-vector-databases.md`, and `reference/vocab-ui-container-words.md`
(all fetched 2026-08-28).

| System | Container noun | Writable vs read-only distinction |
|---|---|---|
| Apache Lucene | **index** | `IndexWriter` "creates and maintains an index" vs `IndexReader`, "a point-in-time view of an index". Searching a writer's live state is **near real-time (NRT)**, via `DirectoryReader.open(IndexWriter)`. Sealing verb: **commit**. |
| Elasticsearch | **index**, "the fundamental unit of storage" | `index.blocks.read_only` "make the index and index metadata read only". In a data stream the writable member is called the **write index** and the sealed ones are **backing indices**; the transition is a **rollover**. New writes become visible on **refresh** (near real-time). |
| Apache Solr | **collection**, "one or more Documents grouped together in a single logical index" (a **core** is one instance of one) | No read-only collection state. Has **soft commit**, which "only makes index changes visible and does not fsync", vs hard commit. Serving object is a **searcher**. |
| Vespa | no single noun: **content cluster**, **schema**, **document type** | No read-only state. Writes are visible "after a few milliseconds". |
| Weaviate | **collection** (renamed from **class**) | Tenants are `ACTIVE` / `INACTIVE` / `OFFLOADED`, but inactive tenants are not searchable, so not an analogue. Weaviate renamed `HOT`/`COLD` to `ACTIVE`/`INACTIVE` in v1.26 precisely because the old words did not explain themselves. |
| Qdrant | **collection**, "a named set of points (vectors with a payload) among which you can search" | No read-only state; readiness is a status: ready, optimizing, pending, error. |
| Pinecone | **index**, partitioned into **namespaces** | The sealed form is a **backup**, "a non-queryable representation of a set of records", so it is not a searchable read-only state. |
| Chroma | **collection**, "the fundamental unit of storage and querying in Chroma" | No read-only state. |

Two conclusions follow directly.

**First: nobody in search calls this a "workspace".** Every search and vector system uses
either **index** or **collection**. The word "dynamic" appears in none of their user
documentation as a name for a mutable index; the words they use are *live*, *write*,
*near real-time*, or *uncommitted*.

**Second: "workspace" in general software means something much bigger.** VS Code
(a set of folders in one window), Postman (a shared collaboration scope), Databricks (a
whole deployment), and Slack (an organisation) all use it for a long-lived,
account-or-team-level container. Nobody uses it for a short-lived per-job artifact. A
developer who has used any of those four will read "workspace" and expect a container
that holds *many* indexes, not one index.

Words with the opposite problem, from `reference/vocab-ui-container-words.md`:
"sandbox" (AWS) promises disposability and explicitly forbids promotion to production;
"session" (Jupyter, Colab) promises state loss; "draft" (Figma, GitHub) promises "not
usable yet"; VS Code's word for the unsaved case is "untitled". All three over-promise
transience for something that can be checkpointed, sealed, aliased, and reindexed here.

## 3. FACT: the name "collection" is already taken in this codebase

`SetCollection` / `GetCollection` / `ListCollections` / `WatchCollection`
(opennlp_search.proto:87-111) define a **collection** as a *group of dynamic indexes* over
which vocabulary drift is measured: "Member index ids may be aliases and are stored
resolved; every member must be a dynamic index." The Lifecycle tab exposes it
(index.html:971-972: "group workspaces into collections whose vocabulary drift the server
streams live").

This rules out the single most common industry word. Renaming the per-index concept to
"collection" would collide head-on with an existing API and UI concept that means the
level *above* it. Any proposal that uses "collection" for one index must also rename the
group concept, which is a much larger change.

## 4. OPINION: six naming options

Constraint recap: the word must not collide with "collection" (the group) or "index"
used generically for both kinds; it should pair naturally with a read-only state; and it
should survive being said by an operator reading server logs, where
`WorkspaceCheckpointStore` and `search.persist.root` already exist.

### Option A: keep **workspace**, delete **dynamic**, add a state word

- Current: `Workspace search`, `dynamic workspaces held in server memory`
- Proposed: `Workspace search`, and every workspace carries a visible state chip:
  `Live`, `Saved`, or `Sealed`.
- Pros: zero server churn (`WorkspaceCheckpointStore`, `search.persist.root`, the
  Lifecycle tab copy, README.md:752 all keep working). Kills the actually meaningless
  word, "dynamic", which is the owner's complaint. The state chip is what a user needs
  anyway (section 1.3). Precedent for state-over-kind naming: Weaviate's
  `ACTIVE`/`INACTIVE` rename, and Elasticsearch's "write index" naming the *role*, not the
  type.
- Cons: keeps a word that four major products use for something ten times bigger. Still
  needs an inline definition on first use, so it does not remove the explaining work.

### Option B: **live index** (recommended runner-up)

- Current: `On-the-fly workspace index`
- Proposed: `Live index`, paired with `Sealed index` on the Corpus search side.
- Pros: it is already the API's own word,
  `SEARCH_PROVIDER_CAPABILITY_LIVE` = "Serves mutable live indexes"
  (opennlp_search.proto:436-437). Directly precedented by Elasticsearch's **write index**
  and Lucene's **NRT** framing: the index you are still writing to and can already search.
  Self-explanatory to someone who has never read the docs. Pairs cleanly with sealed.
- Cons: contradicts the existing server class name `WorkspaceCheckpointStore` and the
  Lifecycle tab, so it is a broader rename. "Live" can also read as "production", the
  opposite of what this is.

### Option C: **session index**

- Proposed: `Session search`, `session index`. Note the tab's HTML id is *already*
  `session-search` (index.html:722), so this is what the code once called it.
- Pros: honest about the default lifetime; the id already agrees.
- Cons: factually wrong once checkpointed. A checkpointed index outlives the browser
  session *and* the server process (DynamicSearchIndexRegistry.java:104-115 restores on
  startup). "Session" in Jupyter and Colab promises state loss, so it actively
  mis-sets expectations for the persist and seal features this very tab points at.

### Option D: **scratch index** / **sandbox index**

- Pros: unambiguously temporary; nobody would confuse it with an operator's corpus.
- Cons: same problem as C, worse. AWS's sandbox guidance explicitly says a sandbox is
  disposable and must not be promoted. This one *can* be sealed and served in production.
  It also devalues the Lifecycle tab, whose whole point is promotion.

### Option E: **draft index**

- Pros: a familiar promotion metaphor (Figma drafts, GitHub draft pull requests), and
  "publish a draft" maps onto "seal" nicely.
- Cons: "draft" implies not yet usable. This index is fully searchable from the first
  document. That is the headline feature of the tab and "draft" argues against it.

### Option F: **my indexes** / **your indexes**

- Proposed: tab label `My indexes`, versus `Corpus search` for the operator's.
- Pros: needs no glossary at all. The distinction a first-time user actually cares about
  is ownership, not mutability: "the ones I made" versus "the ones that came with the
  server". Precedent: GitHub's "Your repositories", Google Colab's "My notebooks",
  Grafana's "My dashboards".
- Cons: false in a multi-user deployment. The registry is server-global, not per-browser
  and not per-user (`DynamicSearchIndexRegistry` holds one map for the whole process), so
  two people using the same server see each other's "my" indexes. That is disqualifying
  for anything beyond a single-operator demo.

### Recommendation

**Option A, with the Option B vocabulary used for the state chip.** Concretely:

- Keep the tab label `Workspace search` and the noun "workspace". It matches the server
  class names and the Lifecycle tab, and changing it ripples into Java, config keys, and
  README.md:752 for no user-visible gain over a good one-sentence definition.
- Delete the word **dynamic** from every user-visible string. It carries no meaning to a
  reader and is pure API leakage.
  - `Dynamic gRPC search` (index.html:725) -> `Search what you just analyzed`
  - `dynamic workspaces held in server memory` (index.html:733) -> see section 5
  - `Dynamic workspace` (index.html:1003, the Lifecycle picker label) -> `Workspace`
- Show the state, not the kind: a chip reading `Live`, `Saved`, or `Sealed` next to each
  workspace in both pickers, sourced from the descriptor's `immutable` and `persisted`
  fields. `persisted` is on the wire already (opennlp_search.proto:487-489) and the front
  end simply does not read it (search-adapter.ts:200-229 maps every other field).
- Define the word once, inline, at the top of the tab (section 5).

Priority: P1 for deleting "dynamic" and adding the definition; P2 for the state chip.

## 5. OPINION: the explainer text

### 5.1 Replace the tab-bridge paragraph

Current, index.html:733-736:

> This tab searches dynamic workspaces held in server memory. Read-only corpus and
> persisted indexes are searched on the Corpus search tab.

This is both jargon and, as shown in findings/gating-and-links.md section 4.4, untrue.

Proposed, using the `.tab-bridge` class that already exists (style.css:476) and the
`data-workbench-jump` mechanism that already works (workbench-navigation.ts:40-42):

> A **workspace** is a search index this server builds from documents you analyze here.
> It lives in the server's memory and disappears when the server restarts, unless you
> save it on the **Lifecycle** tab. Indexes that shipped with the server are on the
> **Corpus search** tab.

with `Lifecycle` and `Corpus search` as `.link-button` jumps. Three sentences, one new
term, defined in the first six words.

### 5.2 Add a "What is a workspace?" disclosure

The page already uses `<details class="help-callout">` six times, so this costs no new
CSS and no JavaScript. Put it directly under the heading, above the existing
`How to use workspace search` callout:

```
<details class="help-callout">
  <summary>What is a workspace?</summary>
  <p>A workspace is one search index that this server builds in its own memory from
     documents you analyze on the Analyze tab. You can search it as soon as the first
     document is added, and you can keep adding documents to it.</p>
  <p>A workspace has three states:</p>
  <ul>
    <li><strong>Live</strong>: in server memory only. It is gone when the server
        restarts.</li>
    <li><strong>Saved</strong>: a copy is on the server's disk, and it still accepts new
        documents. Use <em>Save checkpoint</em> on the Lifecycle tab.</li>
    <li><strong>Sealed</strong>: on disk and permanently read only. Sealed workspaces
        move to the Corpus search tab.</li>
  </ul>
  <p>Elsewhere you may see this called a live index (Elasticsearch calls the writable
     member of a data stream the write index) or a collection (Qdrant, Weaviate, Solr).
     In this workbench a <em>collection</em> means something else: a group of workspaces
     whose vocabulary drift the server watches.</p>
</details>
```

That last paragraph matters. A user arriving from any vector database will map
"collection" onto this concept and be wrong, because this product uses the word one level
up. Saying so once prevents the confusion permanently. Precedent for a definition
disclosure in a developer product: IBM Carbon's **definition tooltip**, whose stated
purpose is exactly "to define a word or phrase" the user may not know
(`reference/onboarding-design-systems.md`).

### 5.3 Fix the heading

- Current: `On-the-fly workspace index` (index.html:727)
- Proposed: `Search the documents you analyze`
- Rationale: the heading is the one line a scanning user reads. "On-the-fly" describes
  the implementation; "search the documents you analyze" describes the outcome and needs
  no glossary. Precedent: Polaris's title rule, verbatim, "Be action-oriented: encourage
  merchants to take the step required to activate the product or feature", and Grafana's
  shipped heading "Start your new dashboard by adding a visualization", both quoted in
  `reference/onboarding-empty-states.md`.

Priority: 5.1 and 5.3 are P1, 5.2 is P1 as well since it is the direct answer to the
owner's question and costs one HTML block.

## Questions for the lead

1. Option A keeps "workspace". If you would rather take the bigger rename to "live
   index" (Option B), it needs to reach `WorkspaceCheckpointStore`, `search.persist.root`
   in docs, the Lifecycle tab, and README.md:752. Is that churn acceptable?
2. Does the `persisted` descriptor field get plumbed into `SearchIndex`
   (search-adapter.ts:32-55) so the Live / Saved / Sealed chip can exist? It is a
   four-line change and unblocks several other findings.
3. The tab's HTML id is `session-search` while its label is "Workspace search"
   (index.html:48-49, 722). Worth aligning, or is the id load-bearing for the e2e specs
   (e2e/workbench.spec.ts:39)?
