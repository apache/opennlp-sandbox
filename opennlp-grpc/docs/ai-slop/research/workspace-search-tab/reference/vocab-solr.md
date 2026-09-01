# Vocabulary: Apache Solr

Sources fetched 2026-08-28:

- https://solr.apache.org/guide/solr/latest/getting-started/solr-glossary.html
- https://solr.apache.org/guide/solr/latest/configuration-guide/commits-transaction-logs.html
- https://solr.apache.org/guide/solr/latest/deployment-guide/solrcloud-shards-indexing.html

## The container words: collection and core

Solr is the one project in this survey with two competing container nouns, and the
glossary defines both in one line each.

> Collection: "One or more Documents grouped together in a single logical index using
> a single configuration and Schema."
> (solr-glossary)

> Core: "An individual Solr instance (represents a logical index). Multiple cores can
> run on a single node."
> (solr-glossary)

> SolrCloud: "Umbrella term for a suite of functionality in Solr which allows managing
> a Cluster of Solr Nodes for scalability, fault tolerance, and high availability."
> (solr-glossary)

The practical split: **core** is the single-node, on-disk thing; **collection** is the
distributed, user-facing thing. Users in SolrCloud talk about collections and almost
never about cores. That is a useful precedent: the word exposed in the UI does not
have to be the word used by the storage layer.

Sub-structure:

> "A Shard is a logical partition of the collection, containing a subset of documents
> from the collection, such that every document in a collection is contained in
> exactly one shard."
> (solrcloud-shards-indexing)

> "Every shard consists of at least one physical replica, exactly one of which is a
> leader."
> (solrcloud-shards-indexing)

## Visibility: commit, hard versus soft

Solr's glossary defines commit in terms the user can act on:

> Commit: "To make document changes permanent in the index. In the case of added
> documents, they would be searchable after a commit."
> (solr-glossary)

The two flavours:

> "A hard commit calls `fsync` on the index files to ensure they have been flushed to
> stable storage."
> (commits-transaction-logs)

> "A soft commit is faster since it only makes index changes visible and does not
> `fsync` index files, start a new segment, nor start a new transaction log."
> (commits-transaction-logs)

> "A softCommit may be 'less expensive' than a hard commit (`openSearcher=true`), but
> it is not free."
> (commits-transaction-logs)

> "a soft commit gives you faster visibility because it's not waiting for background
> merges to finish."
> (commits-transaction-logs)

The `openSearcher` knob separates durability from visibility explicitly:

> `openSearcher`: "Whether to open a new searcher when performing a commit. If this is
> `false`, the commit will flush recent index changes to stable storage, but does not
> cause a new searcher to be opened to make those changes visible."
> (commits-transaction-logs)

## Near real time

> "If an additional parameter `softCommit=true` is specified, then Solr performs a soft
> commit. This is an implementation of Near Real Time storage, a feature that boosts
> document visibility."
> (commits-transaction-logs)

Solr, like Lucene and Elasticsearch, treats **near real time** as a property of *when
writes become visible*, never as a name for a container.

## Read-only in Solr

Solr has no widely used "read-only collection" state comparable to an Elasticsearch
index block. The nearest concepts are a collection that simply stops receiving
updates, and backup/restore of a collection. Solr's vocabulary for the frozen thing is
therefore weak; it is not a good source for the read-only half of the naming problem.

## Vocabulary summary for Solr

| Idea | Solr's word |
| --- | --- |
| The distributed container | collection |
| The single-node container | core |
| Logical partition | shard |
| Physical copy | replica |
| Make changes durable | hard commit |
| Make changes visible only | soft commit |
| The object serving queries | searcher |
| Search latency property | Near Real Time (NRT) |

## What this suggests for naming

1. **searcher** is Solr's noun for "the read-only view currently answering queries".
   It is a plainer word than "reader" or "snapshot" and it is already domain-standard.
2. Solr separates *durable* from *visible* with two commit types. If a UI has both a
   "save" and a "make searchable" action, this precedent supports two distinct verbs.
3. The collection/core split shows a project can carry an internal name and a
   user-facing name without confusion, provided only one of them appears in the UI.
