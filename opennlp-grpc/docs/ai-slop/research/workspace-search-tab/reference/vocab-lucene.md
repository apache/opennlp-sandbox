# Vocabulary: Apache Lucene

Sources fetched 2026-08-28:

- https://lucene.apache.org/core/9_9_0/core/org/apache/lucene/index/IndexWriter.html
- https://lucene.apache.org/core/9_9_0/core/org/apache/lucene/index/IndexReader.html
- https://lucene.apache.org/core/9_9_0/core/org/apache/lucene/index/DirectoryReader.html

## The container word

Lucene has no user-facing "workspace" concept. The container is simply the **index**,
and it is named indirectly through the two objects that act on it: `IndexWriter` and
`IndexReader`. The writable thing and the readable thing are *different Java types*,
which is the cleanest statement in the field that "being written to" and "being
searched" are two distinct states of the same data.

## IndexWriter: the writable side

> "An `IndexWriter` creates and maintains an index."
> (IndexWriter.html)

> "The `IndexWriterConfig.OpenMode` option ... determines whether a new index is
> created, or whether an existing index is opened."
> (IndexWriter.html)

Note the vocabulary pair Lucene uses for the lifecycle of the container: **create**
versus **open**. Not "new workspace" versus "load workspace".

Buffered, not yet visible:

> "These changes are buffered in memory and periodically flushed to the `Directory`
> ... but these changes are not visible to IndexReader until either `commit()` or
> `close()` is called."
> (IndexWriter.html)

The word for making writes durable and visible is **commit**:

> "Commits all pending changes (added and deleted documents, segment merges, added
> indexes, etc.) to the index, and syncs all referenced index files, such that a
> reader will see the changes."
> (IndexWriter.html)

Terms in play here that a UI could borrow: *pending changes*, *buffered in memory*,
*flush*, *commit*.

## IndexReader: the read-only side

> "IndexReader is an abstract class, providing an interface for accessing a
> point-in-time view of an index."
> (IndexReader.html)

> "Any changes made to the index via IndexWriter will not be visible until a new
> IndexReader is opened."
> (IndexReader.html)

> "IndexReader instances are completely thread safe, meaning multiple threads can
> call any of its methods, concurrently."
> (IndexReader.html)

The load-bearing phrase is **point-in-time view**. Elsewhere in the same Javadoc the
older, blunter phrase **"point in time" snapshot** appears:

> "You can open an index with `IndexWriterConfig.OpenMode.CREATE` even while readers
> are using the index. The old readers will continue to search the 'point in time'
> snapshot they had opened."
> (IndexWriter.html)

So Lucene's own word for a frozen searchable view is **snapshot**, qualified as
*point in time*. It does not say "read-only index"; readers are read-only by
construction, so the docs never need the adjective.

## Near real-time (NRT): searching a live writer

This is the exact feature the "dynamic workspace" idea is describing, and Lucene
already has a name for it.

> "Opens a near real time IndexReader from the `IndexWriter`."
> (DirectoryReader.html, `open(IndexWriter)`)

The DirectoryReader Javadoc frames the benefit as searching uncommitted changes
without closing the writer or calling `commit()`. IndexWriter refers back to the same
state as a *mode* of the writer:

> "If `DirectoryReader.open(IndexWriter)` has been called (ie, this writer is in near
> real-time mode), then after a merge completes, this class can be invoked to warm the
> reader on the newly merged segment."
> (IndexWriter.html)

Note the exact phrasing: **"this writer is in near real-time mode"**. The adjective
attaches to the writer, not to the index or to a workspace.

## Expert variant

> "Expert: opens a near real time IndexReader from the `IndexWriter`, controlling
> whether past deletions should be applied."
> (DirectoryReader.html, `open(IndexWriter, boolean, boolean)`)

## Vocabulary summary for Lucene

| Idea | Lucene's word |
| --- | --- |
| The container | index |
| The writable handle | IndexWriter |
| The searchable handle | IndexReader / DirectoryReader |
| Written but not yet visible | pending changes, buffered in memory |
| Make visible and durable | commit |
| A frozen searchable view | point-in-time view, "point in time" snapshot |
| Searching a live writer | near real-time (NRT), near real-time mode |
| Unit of on-disk immutability | segment |

## What this suggests for naming

1. Lucene never needs an adjective like "dynamic" because the *handle type* carries
   the mutability. If a UI shows one object, it needs the adjective; if it shows two
   states, it can name the states instead.
2. The strongest ready-made pair from Lucene is **open / committed**, or
   **live / snapshot**.
3. "Near real-time" is a precise term of art but describes *search latency*, not the
   container. It is the wrong word for a noun in a tab bar.
