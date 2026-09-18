# The lifecycle state machine, and what to call each state

Owner's words: *"Persist checkpoint vs. seal immutable?"* and *"'collections' too, 'what is a
collection' would help."*

---

## 1. The state machine as it actually is

FACT. Reconstructed from
`opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/DynamicSearchIndexRegistry.java`,
`opennlp-grpc-api/src/main/proto/org/apache/opennlp/grpc/v1/opennlp_search.proto:48-111`, and
`opennlp-grpc-webapp-default/src/lifecycle-workbench.ts`.

```
                        Workflows tab              Workspace search tab
                    (corpus-workflow.ts:220)   (semantic-workbench.ts:333)
                                 \                    /
                                  \                  /
                                   v                v
                              IndexDocuments  (creates the index)
                                        |
                                        v
             .--------------------------------------------------------.
             |  DYNAMIC                                               |
             |  in server memory, accepts documents, searchable       |
             |  not on disk, lost on restart                          |
             |  descriptor: immutable=false, persisted=false          |
             '--------------------------------------------------------'
                 |                    |                          |
   PersistIndex  |                    | SealIndex                | DeleteSearchIndex
   ("Save        |                    | ("Seal as read-only")    |
    checkpoint") |                    |                          v
                 v                    |                       (gone)
   .------------------------------.   |
   |  DYNAMIC + CHECKPOINTED      |   |
   |  same as above, plus a copy  |   |
   |  on disk under               |---'  SealIndex is also reachable from here
   |  search.persist.root         |      (it just re-persists with sealed=true)
   |  STILL accepts documents     |
   |  survives restart, mutable   |
   |  immutable=false             |
   '------------------------------'
                 |                                     |
                 | PersistIndex again, or the          |
                 | auto-checkpoint timer               v
                 | (search.persist.checkpoint_seconds) |
                 '-------------------------------------'
                                        |
                                     SealIndex
                                        |
                                        v
             .--------------------------------------------------------.
             |  SEALED                                                |
             |  on disk, still searchable                             |
             |  IndexDocuments -> FAILED_PRECONDITION                 |
             |  "Sealed search index '<id>' is immutable"             |
             |  restored as immutable after restart                   |
             |  descriptor: immutable=true                            |
             |  *** DeleteSearchIndex STILL WORKS ***                 |
             |  *** disappears from the Lifecycle tab entirely ***    |
             '--------------------------------------------------------'

  Side channels, available from any dynamic state:

  ReindexIndex ("Rebuild index")
      source index --(replay retained chunks through a new embedding model)--> NEW index id
      source keeps serving, untouched. Optional alias is repointed only after the build succeeds.
      This is the only way to "unseal": you rebuild a fresh editable copy.

  Aliases (SetIndexAlias / DeleteIndexAlias)
      alias name -----> index id
      accepted anywhere an index id is accepted; resolved before every lifecycle call.
      Deleting an alias does not touch the index.

  Collections (SetCollection / WatchCollection)
      collection id ---> { set of member index ids }
                         + dictionary artifact id
                         + vocabulary artifact id
                         + model artifact id
                         + drift threshold
      NOT searchable. Contributes no state to any index. Read-only observation plus a watch stream.
```

Two facts worth pulling out of the diagram:

FACT. **Persist and seal are the same write.** Both call the private
`persist(DynamicIndex, boolean sealed)` at `DynamicSearchIndexRegistry.java:519-546`. Seal differs
by exactly one thing: it passes `sealed=true` and then calls `index.markSealed()`
(`DynamicSearchIndexRegistry.java:467-490`, flag at lines 1116, 1169-1171). There is no separate
"more durable" write, no compaction, no merge, no different file format. So the honest framing is
**one durability action with an optional "and stop accepting writes" flag**, not two ladder rungs.

FACT. **There is no unseal.** `markSealed()` only sets true, and a checkpoint header with
`sealed` restores it as immutable on restart (`DynamicSearchIndexRegistry.java:633-635`).

---

## 2. Mapping each state and transition to industry vocabulary

FACT for the industry column; see `../reference/` for the quoted sources.

| This repo | Lucene | Elasticsearch / OpenSearch | Solr | Qdrant / Weaviate | Git |
| --- | --- | --- | --- | --- | --- |
| Dynamic (in-memory index) | an open `IndexWriter` with uncommitted segments | an index whose translog is unflushed; "refresh" makes it searchable | an open core | a collection in memory | an uncommitted working tree |
| `PersistIndex` "Save checkpoint" | **`commit()`**: "the index updates will survive an OS or machine crash or power loss" | **flush**: "any data that is currently only stored in the transaction log is also permanently stored in the Lucene index" | `commit` | **snapshot** | **commit** |
| The artifact persist produces | a **commit point** | a flushed generation | a commit point | a snapshot | a commit object |
| `SealIndex` "Seal as read-only" | `commit()` then **`close()`**; Lucene has no "seal" | **`index.blocks.write=true`**, or the ILM **read-only** action; **freeze** existed and was deprecated in 7.14 | no direct equivalent | no direct equivalent | **tag** (a name pinned to an immutable point) |
| Sealed state | a closed index directory | a read-only index; an ILM **cold** or **frozen** phase index | read-only core | immutable snapshot | a tag |
| `ReindexIndex` "Rebuild index" | reindex into a new directory (Lucene offers no reindex API) | **`_reindex`** plus an **alias swap**, the standard zero-downtime pattern | reindex into a new collection then `CREATEALIAS` | recreate collection then move alias | `git filter-repo` then move the branch |
| `SetIndexAlias` | none | **alias**: "An alias points to one or more indices or data streams" | **collection alias** (`CREATEALIAS`) | **alias**: "additional names for existing collections" | branch / ref |
| `DeleteSearchIndex` | delete the directory | `DELETE /index`, or the ILM **delete** phase | `DELETE` collection | delete collection | `git branch -D` |
| Collection | none | none. The nearest thing is a **data stream** or an **index pattern** | **collection** = "a single logical index" (searchable) | **collection** = "a named set of points among which you can search" (searchable) | none |
| The tab overall | none | **"index lifecycle management"** | none | none | none |
| Auto-checkpoint (`search.persist.checkpoint_seconds`) | periodic `commit()` | scheduled flush | autoCommit | none | none |
| Not present here | `forceMerge()` | rollover, shrink, ILM hot/warm/cold/frozen phases, snapshot repositories | none | snapshot restore | none |

Two mappings that do **not** hold, and matter:

- **Vespa** has no equivalent state machine to map onto. Vespa content clusters have no user-facing
  commit, seal, or alias operation; documents are durable on write. It is not a useful precedent
  here and I would leave it out of any doc.
- **"Frozen"** in Elastic never meant what "sealed" means here. It meant read-only *and* evicted
  from heap, a memory-tiering trick, and it was deprecated in 7.14 with the note "Frozen indices are
  no longer useful due to recent improvements in heap memory usage"
  (`../reference/elasticsearch-ilm-and-blocks.md`). Renaming toward "freeze" would adopt a
  deprecated term for a different concept.

---

## 3. Proposed renames

OPINION throughout. Current strings are quoted from `index.html` and `lifecycle-workbench.ts`.

### 3.1 "Save checkpoint"

Current: `Save checkpoint` (`index.html:1007`), helper "**Save checkpoint** writes the workspace to
disk, and it keeps accepting new documents." (`index.html:1012-1013`).

| Candidate | Pros | Cons |
| --- | --- | --- |
| Keep **Save checkpoint** | Matches the proto and the config key `search.persist.checkpoint_seconds`. "Checkpoint" is widely understood as "a resumable saved point" from training loops and databases. Correctly implies "more will follow". | Not a search-industry word. Elastic/Solr say flush/commit. |
| **Save snapshot** | Qdrant, OpenSearch, and Weaviate all ship "snapshot". Familiar to vector-DB users. | **Wrong meaning here.** A snapshot in those systems is a separate, restorable, point-in-time *copy* you can keep several of. This writes one checkpoint that is overwritten in place, and there is no restore-to-an-earlier-one API. Adopting "snapshot" would promise version history the server does not have. |
| **Commit** | Lucene's own word, exact structural match, and this is Apache OpenNLP so Lucene vocabulary is defensible. | Collides with Git for the general audience, and "commit" alone does not tell a first-time user that the data is now on disk. |
| **Save to disk** | Zero jargon. Answers "what happens" directly. | Loses the "and you can keep going" nuance and the auto-checkpoint tie-in. |

**Recommendation (P2): keep "Save checkpoint".** It is already the least confusing of the two
buttons, and the owner's complaint was aimed at the other one. Do not move to "snapshot": it would
be an actively wrong promise. Do strengthen the tooltip.

Tooltip: *Writes this workspace to disk so it survives a server restart. It stays editable, so you
can keep adding documents and save again. Like a Lucene commit.*

### 3.2 "Seal as read-only" (the owner's "seal immutable")

Current: `Seal as read-only` (`index.html:1008-1009`), helper "**Seal as read-only** writes it to
disk too, and also makes it permanently read-only: it accepts no further documents."
(`index.html:1014-1015`).

| Candidate | Pros | Cons |
| --- | --- | --- |
| Keep **Seal as read-only** | Already says the consequence in the label. "Seal" carries the right one-way feel. | "Seal" is invented; no search system uses it. Three words compete with "immutable" in the fact row and "persisted" in the proto. |
| **Freeze (make read-only)** | "Freeze" is memorable and appears in Elastic and in ILM's frozen phase. | Elastic's freeze API meant something else and was **deprecated in 7.14**. ILM's "frozen" is a storage tier, not a write block. Borrowing it imports the wrong model. **Recommend against.** |
| **Make read-only** | Exactly Elastic's own wording for `index.blocks.read_only`: "make the index and index metadata read only". Plain English, no new noun, and a user who has used any search engine recognises it. | Loses the "this is permanent" signal, since read-only is reversible in Elastic and is not here. |
| **Finish and lock** | Most legible to a non-expert; pairs naturally with checkpoint. | No industry precedent at all. |
| **Close (stop accepting documents)** | Lucene's actual verb for the operation, and Elastic has a close-index API. | Elastic's "close" means unloaded and *unsearchable*, which is the opposite of what happens here. Would mislead anyone with Elastic experience. **Recommend against.** |

**Recommendation (P1): "Make read-only" as the button, with "permanent" moved into the confirming
text rather than the label.** It matches Elastic's own phrasing for the same mechanism, it drops the
invented "seal", and it removes the three-way split with "immutable". Then make the fact row at
`lifecycle-workbench.ts:180` say `Read-only: yes/no` instead of `Sealed`, and the proto's
`immutable` field stays untouched on the wire.

If the lead prefers to keep "seal" for continuity with the RPC name, then the minimum P1 fix is to
make the three words agree: button "Seal (make read-only)", fact row "Sealed", helper text using
"sealed" and never "immutable".

Tooltip: *Saves this workspace to disk and stops it accepting new documents. It stays searchable.
This cannot be undone; to get an editable copy, rebuild it below.*

### 3.3 "Collection"

Current: panel heading `Collections` (`index.html:1063`), select `Collection` (`index.html:1064`),
option `New collection` (`lifecycle-workbench.ts:320`). There is no definition of the word anywhere
on the tab; the section blurb only says "group workspaces into collections whose vocabulary drift
the server streams live" (`index.html:969-972`).

FACT. In Qdrant, Solr, and Weaviate a collection **is the searchable thing** you index into and
query (`../reference/vector-db-collections-and-snapshots.md`). Here it is not searchable at all: no
API accepts a collection id where an index id is accepted, and its entire observable behaviour is
recomputed term statistics plus a watch stream
(`opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/SearchCollectionRegistry.java`,
proto `opennlp_search.proto:200-239`).

| Candidate | Pros | Cons |
| --- | --- | --- |
| Keep **Collection** | Already on the wire in `CollectionDescriptor` and five RPCs; renaming the UI diverges from the API. Solr, the closest Apache sibling, uses it. | It is a **false friend**. It is the single most loaded noun in vector search and it means something incompatible here. A Qdrant or Solr user will expect to search it. |
| **Drift group** | Says precisely what it does: the scope drift is measured over. Matches the proto's own comment, "the scope vocabulary drift is measured over" (`opennlp_search.proto:87-88`). | Invented. Diverges from the RPC names. |
| **Corpus** | An OpenNLP-native word; a named body of text you train and measure against. Fits the lineage fields (dictionary, vocabulary, model). | Already used elsewhere in this UI for a different thing: the "Corpus search" tab searches server-side static indexes. Would collide. |
| **Watch group** / **Monitored set** | Honest about the watch-stream purpose. | Invented, and undersells the artifact-lineage role. |

**Recommendation (P1 for the help text, P3 for the rename): keep the word, define it on screen.**
Renaming away from the RPC name buys less than it costs, and the real defect is that the tab never
says what a collection is. Add a definition directly under the "Collections" heading and a flyout on
the term.

Proposed on-screen definition, to sit under `index.html:1063`:

> **A collection is a named group of workspaces you want to watch together.** It is not something
> you search. It records which workspaces belong together, which dictionary, vocabulary, and model
> they were built from, and how many unfamiliar terms should trigger an alert. The server then
> reports, live, how much of that group's text the current vocabulary still covers.

If the lead does want a rename, my second choice is **"Drift group"** in the UI while leaving
`collection_id` on the wire, and the help text should then say "called a collection in the API".

### 3.4 Smaller strings

| Current | Where | Proposed | Precedent |
| --- | --- | --- | --- |
| `Dynamic workspace` | `index.html:1003` | `Workspace` in the label, with "dynamic" explained in help: "in server memory until you save it" | "dynamic" is a server-side implementation word; nothing user-facing needs it |
| `Point alias at workspace` | `index.html:1024` | `Point alias here` | Elastic and Solr both frame this as pointing an alias at an index |
| `Rebuild with a new model` | `index.html:1031` | keep; it is clear | Elastic's `_reindex` plus alias swap is the same pattern |
| `Provider instances` | `index.html:1058` | `Vector storage available on this server` | "provider instance" is SPI vocabulary leaking into the UI |
| `Vector space` fact row | `lifecycle-workbench.ts:178` | keep, add tooltip "the coordinate system a model's vectors live in; two indexes are only comparable inside one" | |

---

## 4. Suggested one-line help text for every lifecycle control

OPINION. To be attached as tooltips or flyouts.

- **Workspace**: a search index you built in this session, held in server memory until you save it.
- **Save checkpoint**: writes it to disk so it survives a restart; it stays editable.
- **Make read-only** (today "Seal as read-only"): saves it to disk and stops it accepting new
  documents. Cannot be undone.
- **Alias**: a stable nickname for an index. Point it at a new index and every client follows
  without changing anything. Deleting the alias leaves the index alone.
- **Rebuild index**: builds a second index from the same documents using a different embedding
  model, while the first one keeps answering searches. Then the alias switches over.
- **Vector storage**: how vectors are held. Exact flat float keeps full precision in memory and
  **cannot be checkpointed or sealed**. TurboQuant compresses them and can be saved to disk.
- **Collection**: a named group of workspaces watched together for vocabulary drift. Not searchable.
- **Dictionary artifact**: the word list a vocabulary was cut against.
- **Vocabulary artifact**: the term list the current model was trained on; drift is measured against
  it.
- **Serving model artifact**: the model whose vector space these workspaces were indexed in.

---

## Questions for the lead

1. "Seal" is an invented word with no precedent in Lucene, Elastic, Solr, or any vector database.
   Is keeping it worth the cost, given the RPC name `SealIndex` is already public API?
2. Is "collection" here permanent API vocabulary, or is there appetite to rename it before it
   ships? A collection that cannot be searched will surprise every user arriving from Solr or
   Qdrant.
3. The docs promise more of the ladder than the code has: `docs/rfc/opennlp-search-query-model.md:146`
   says an explicit seal "turns one into an immutable bundle", but sealing sets a flag on a
   checkpoint, it does not produce a startup bundle. Should the RFC be corrected, or the code
   extended?
