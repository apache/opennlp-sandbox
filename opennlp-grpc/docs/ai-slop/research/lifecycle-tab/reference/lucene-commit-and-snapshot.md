# Apache Lucene: commit, close, force-merge, commit points

Excerpts. Fetched 2026-08-28.
Source: https://lucene.apache.org/core/9_11_0/core/org/apache/lucene/index/IndexWriter.html

## commit()
> "Commits all pending changes (added and deleted documents, segment merges, added indexes, etc.)
> to the index, and syncs all referenced index files, such that a reader will see the changes and
> the index updates will survive an OS or machine crash or power loss."

## prepareCommit()
> "Prepare for commit. This does the first phase of 2-phase commit. This method does all steps
> necessary to commit changes since this writer was opened: flushes pending added and deleted docs,
> syncs the index files, writes most of next segments_N file."

## close()
> "Closes all open resources and releases the write lock"

Closing attempts a graceful shutdown: writing changes, waiting for merges, and committing.

## forceMerge()
> "Forces merge policy to merge segments until there are <= maxNumSegments. The actual merges to be
> executed are determined by the MergePolicy."

## Commit points
Lucene calls a durable committed state a **"commit point"**. Readers search a "point in time"
snapshot they had opened, staying consistent against that index version even when newer commits
land. `IndexDeletionPolicy` controls when prior commits are deleted;
`KeepOnlyLastCommitDeletionPolicy` is the default.

Relevance to this repo:
- "Save checkpoint" (`PersistIndex`) is, structurally, exactly a Lucene **commit**: durable, index
  stays open for writes. Lucene's noun for the artifact it produces is a **commit point**.
- "Seal as read-only" (`SealIndex`) is a commit **plus** an irreversible refusal of further writes.
  Lucene's nearest analogue is `commit()` followed by `close()`: closing a writer is what makes an
  index stop accepting documents. Lucene has no "seal".
- Lucene has no operation named "seal", "freeze", or "immutable". The word "immutable" in this
  repo's proto is a descriptor field, not a Lucene concept.
