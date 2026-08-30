# Elasticsearch: index lifecycle management, index blocks, aliases, flush

Excerpts. Fetched 2026-08-28.

## Index Lifecycle Management (ILM)
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/index-lifecycle-management.html

> "Index lifecycle management (ILM) automates the management of time-based indices, such as logs and metrics."

Phases, named verbatim in the doc: **hot, warm, cold, frozen, delete**.

Rollover:
> "Creates a new write index when the current one reaches a certain size, number of docs, or age."

Read-only appears as a lifecycle *action*, not a state name:
> "Move the old index into the warm phase, mark it read only, and shrink it down to a single shard"

Relevance: the industry word for the whole tab is "index lifecycle management". "Read only" is the
industry phrase for what this repo calls "sealed" / "immutable". Note it is an *action applied to*
an index, not a noun for the index.

## Index blocks (the read-only mechanism)
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules-blocks.html

> `index.blocks.read_only`: "Set to `true` to make the index and index metadata read only, `false`
> to allow writes and metadata changes."

> `index.blocks.write`: "Set to `true` to disable data write operations against the index. Unlike
> `read_only`, this setting does not affect metadata."

Relevance: Elastic distinguishes "read only" (data + metadata frozen) from "write block" (data
only). This repo's seal is closest to a *write block plus durability*: `DeleteSearchIndex` still
works on a sealed index, so it is not fully read-only in the Elastic sense.

## Freeze (deprecated, important precedent against the word)
Source: https://www.elastic.co/guide/en/elasticsearch/reference/7.17/freeze-index-api.html

The freeze index API converts an index into a read-only state with minimal cluster overhead.
It was **deprecated in 7.14**:
> "Frozen indices are no longer useful due to recent improvements in heap memory usage."

Relevance: "Freeze" is a poisoned term. In Elastic it meant "read-only *and* unloaded from heap",
a memory-tiering concept, and it was deprecated. Do not rename "Seal as read-only" to "Freeze".

## Aliases
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/aliases.html

> "An alias points to one or more indices or data streams. Most Elasticsearch APIs accept an alias
> in place of a data stream or index name."

> "During this swap, the `logs` alias has no downtime and never points to both streams at the same
> time."

Relevance: this repo's alias semantics match exactly, and "alias" is the correct, unchanged word.

## Flush (the durability word)
Source: https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-flush.html

> Flushing is "the process of making sure that any data that is currently only stored in the
> transaction log is also permanently stored in the Lucene index."

Refresh, by contrast, makes indexed data searchable but does not guarantee durability.

Relevance: "flush" is the durability verb one layer above Lucene's commit. It is a poor UI label
because it reads as "discard" to non-search users.
