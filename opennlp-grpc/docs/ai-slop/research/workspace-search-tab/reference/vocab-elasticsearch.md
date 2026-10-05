# Vocabulary: Elasticsearch

Sources fetched 2026-08-28:

- https://www.elastic.co/docs/manage-data/data-store/index-basics
- https://www.elastic.co/guide/en/elasticsearch/reference/current/near-real-time.html
- https://www.elastic.co/docs/reference/elasticsearch/index-settings/index-block
- https://www.elastic.co/guide/en/elasticsearch/reference/current/searchable-snapshots.html
- https://www.elastic.co/docs/manage-data/data-store/data-streams

## The container word: index

> "An _index_ is the fundamental unit of storage in Elasticsearch, and the level at
> which you interact with your data."
> (index-basics)

> "To store a document, you add it to a specific index. To search, you target one or
> more indices."
> (index-basics)

Elasticsearch deliberately uses one noun for the whole life of the data. There is no
separate word for "the index I am still filling up". Mutability is expressed as
*settings on* the index, never as a different kind of object. This is the opposite of
Lucene's two-handle design and worth weighing: a single noun plus a state badge.

## Writing is not instantly visible: refresh

> "When a document is stored in Elasticsearch, it is indexed and fully searchable in
> near real-time--within 1 second."
> (near-real-time)

> "In Elasticsearch, this process of writing and opening a new segment is called a
> refresh. A refresh makes all operations performed on an index since the last refresh
> available for search."
> (near-real-time)

> "Documents in the in-memory indexing buffer are written to a new segment."
> (near-real-time)

> "By default, Elasticsearch periodically refreshes indices every second, but only on
> indices that have received one search request or more in the last 30 seconds."
> (near-real-time)

Note that Elasticsearch inherits Lucene's segment vocabulary and says so plainly:

> "A segment is similar to an inverted index, but the word index in Lucene means 'a
> collection of segments plus a commit point'."
> (near-real-time)

Useful UI words harvested here: **in-memory indexing buffer**, **refresh**,
**near real-time**.

## Making an index read-only: index blocks

This is Elasticsearch's answer to "can later be made read-only".

> "Index blocks limit the kind of operations that are available on a certain index.
> The blocks come in different flavours, allowing to block write, read, or metadata
> operations."
> (index-block)

The four settings, verbatim:

> `index.blocks.read_only`: "Set to `true` to make the index and index metadata read
> only, `false` to allow writes and metadata changes."

> `index.blocks.read_only_allow_delete`: "Similar to `index.blocks.write`, except that
> you can delete the index when this block is in place."

> `index.blocks.read`: "Set to `true` to disable read operations against the index."

> `index.blocks.write`: "Set to `true` to disable data write operations against the
> index. Unlike `read_only`, this setting does not affect metadata."
> (all four: index-block)

Terminology takeaway: the state is called **read only** (two words in the docs, one
token `read_only` in the API), and the *action* of entering it is called
**adding a block**. There is an "add index block API". Elasticsearch does not say
"freeze" or "seal" for this.

## Searchable snapshots and the frozen tier

> "Searchable snapshots let you use snapshots to search infrequently accessed and
> read-only data in a very cost-effective fashion."
> (searchable-snapshots)

> "The cold and frozen data tiers use searchable snapshots to reduce your storage and
> operating costs."
> (searchable-snapshots)

Two mount flavours:

> Fully mounted index: "Fully caches the snapshotted index's shards in the
> Elasticsearch cluster. ILM uses this option in the `hot` and `cold` phases."

> Partially mounted index: "Uses a local cache containing only recently searched parts
> of the snapshotted index's data. This cache has a fixed size and is shared across
> shards of partially mounted indices allocated on the same data node. ILM uses this
> option in the `frozen` phase."
> (both: searchable-snapshots)

So Elasticsearch has *three* separate words that all touch our concept:

- **read-only**, an access state applied by a block,
- **snapshot**, a durable point-in-time copy in a repository,
- **frozen**, a *storage tier* name, not an access state.

The older standalone "frozen indices" feature (a throttled, memory-cheap index state)
is no longer the meaning of "frozen"; current docs use frozen for the tier. Anyone
borrowing "frozen" should know it now reads as a cost tier to Elasticsearch users,
not as "sealed".

## Data streams: one write target, many read-only members

This is the closest published analogue to a "live one plus finished ones" UI.

> "A data stream acts as a layer of abstraction over a set of indices that are
> optimized for storing append-only time series data."

> "A data stream consists of one or more hidden, auto-generated backing indices."

> "The most recently created backing index is the data stream's write index. The
> stream adds new documents to this index only."

> "You cannot add new documents to other backing indices, even by sending requests
> directly to the index."

> "A rollover creates a new backing index that becomes the stream's new write index."
> (all: data-streams)

Vocabulary worth stealing wholesale: **write index** for the one currently accepting
documents, **backing index** for the sealed members, **rollover** for the act of
retiring the live one and starting a new one.

## Vocabulary summary for Elasticsearch

| Idea | Elasticsearch's word |
| --- | --- |
| The container | index |
| Group of containers, one live | data stream |
| The one accepting writes | write index |
| A retired, no longer writable member | backing index |
| Act of retiring the live one | rollover |
| Written but not yet searchable | in-memory indexing buffer |
| Make recent writes searchable | refresh |
| Search latency property | near real-time |
| Locked against writes | read only (index block) |
| Durable point-in-time copy | snapshot |
| Cheap cold storage tier | frozen |

## What this suggests for naming

1. Elasticsearch proves a single noun ("index") can carry both states if the UI shows
   the state as an attribute. That argues against inventing a second noun.
2. **write index** is a remarkably plain-language phrase for exactly "the one being
   written to", and it is already industry-standard.
3. **rollover** is the verb for the transition. "Seal", "finalize", "publish" are all
   inventions by comparison.
