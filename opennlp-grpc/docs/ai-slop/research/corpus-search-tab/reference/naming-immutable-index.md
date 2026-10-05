# Cross-product survey: naming an index that can no longer be written to

Sources fetched 2026-08-28:

- https://www.elastic.co/docs/reference/elasticsearch/index-settings/index-block
- https://www.elastic.co/docs/api/doc/elasticsearch/operation/operation-indices-close
- https://www.elastic.co/docs/manage-data/lifecycle/data-tiers
- https://www.elastic.co/docs/deploy-manage/tools/snapshot-and-restore/searchable-snapshots
- https://solr.apache.org/guide/solr/latest/deployment-guide/collection-management.html
- https://docs.opensearch.org/latest/im-plugin/ism/policies/
- https://docs.pinecone.io/docs/collections
- https://qdrant.tech/documentation/snapshots/
- https://lucene.apache.org/core/7_5_0/core/org/apache/lucene/index/package-summary.html
- https://solr.apache.org/guide/solr/latest/configuration-guide/index-segments-merging.html

## Candidate terms, with the product that actually uses each

### "read-only" / "read only"

The clear plurality term.

- Elasticsearch: `index.blocks.read_only` makes "the index and index metadata read only". Client
  error string: `"FORBIDDEN/5/index read-only (api)"`.
- Elasticsearch searchable snapshots: "Fully mounted indices are read-only." Also, searchable
  snapshots exist to "search infrequently accessed and read-only data".
- Solr: "Setting the `readOnly` attribute to `true` puts the collection in read-only mode, in which
  any index update requests are rejected."
- OpenSearch ISM action `read_only`: "Sets a managed index to be read only." Its inverse action is
  `read_write`: "Sets a managed index to be writeable."

What it means: writes rejected, reads and searches still work. This is exactly the property a
finished corpus has.

Recognition: highest of any term surveyed. It is ordinary English, it is used by Elastic, Solr and
OpenSearch for this precise state, and a non-expert reads it correctly on first sight.

### "closed"

- Elasticsearch: "A closed index is blocked for read or write operations and does not allow all
  operations that opened indices allow." Search, get and index operations fail with
  `index_closed_exception`. API `POST /<index>/_close`.
- OpenSearch ISM action `close`: "Closes the managed index. Closed indexes remain on disk, but
  consume no CPU or memory. You can't read from, write to, or search closed indexes."

What it means: stronger than read-only, since you cannot search it either. Wrong word for a corpus
you still intend to query.

Recognition: familiar word, wrong meaning. Actively misleading for a searchable corpus.

### "frozen"

- Elasticsearch data tier names: "content tier", "hot tier", "warm tier", "cold tier", "frozen
  tier". Frozen: "Once data is no longer being queried, or being queried rarely, it may move from
  the cold tier to the frozen tier where it stays for the rest of its life."

What it means: a storage cost and access-frequency tier, backed by "partially mounted indices". It
is about how rarely you query, not about whether you can write.

Recognition: intuitively suggests "cannot change", which is close to the right idea, but it is a
tier name in Elastic's vocabulary and carries a strong "rarely searched, slow" connotation. That
connotation is wrong for a corpus you want people to search interactively.

### "snapshot"

- Qdrant: "Snapshots are tar archive files that contain data and configuration of a specific
  collection on a specific node at a specific time." A collection snapshot includes "the collection
  configuration, all points and payloads".
- Elasticsearch: "searchable snapshot", with "fully mounted index" and "partially mounted index" as
  the two mount modes.
- OpenSearch ISM has a `snapshot` action.

What it means: a point-in-time copy, usually stored elsewhere, usually restored before use. Elastic
is the exception in making a snapshot directly searchable.

Recognition: widely understood, but it names an artifact you restore from, not a live index you
search. Using it for a queryable corpus would set the wrong expectation unless paired with
"searchable".

### "immutable" / "write-once"

Lucene's internal vocabulary:

- "Segments are immutable; updates and deletions may only create new segments and do not modify
  existing ones."
- "Lucene indexes are 'write-once' files: once a segment has been written to permanent storage (to
  disk), it is never altered."
- Lucene 4.0: "the segments are fully immutable (write-once), and any changes are expressed either
  as new segments or new lists of deletions."

What it means: a property of segment files. Every Lucene index has immutable segments, including one
you are actively writing to, because writing adds new segments rather than editing old ones.

Recognition: precise for engine implementers, misleading for everyone else. "Immutable index" is not
a term any of the surveyed products use for a user-visible index state.

### "commit point"

- Lucene: "The commit point is a list of segments (and deletions) comprising the whole index at the
  point in time when the commit operation was successfully completed."

What it means: a durable index version marker. Internals vocabulary. Not a user-facing state name.

### "collection"

- Pinecone: "A collection is a static copy of a pod-based index that only consumes storage. It is a
  non-queryable representation of a set of records."

What it means: in Pinecone specifically, a sealed non-searchable copy. Everywhere else in the
industry "collection" means a live, writable, searchable index (Solr collection, Weaviate
collection, Qdrant collection). Pinecone's newer serverless docs use "backup" instead: "Create
backups of serverless indexes to protect data, copy indexes, or experiment with configurations".

Recognition: collides badly with the mainstream meaning. Do not use it for a sealed index.

### "sealed", "archived"

No product surveyed uses either as a UI or API term for this state.

- "sealed" appears in no Elastic, Solr, OpenSearch, Qdrant, Weaviate, Pinecone, Typesense,
  Meilisearch or Vespa vocabulary found in this survey.
- "archive" appears only in prose about what snapshots are for (Qdrant: snapshots can be used "to
  archive data").

Recognition: "sealed" is evocative but has no industry precedent and no established meaning.
"Archived" strongly implies "moved away and no longer immediately available", which is the wrong
implication for a corpus that is still searched.

## Ranking for a general audience

1. "read-only" - used by Elasticsearch, Solr and OpenSearch for exactly this state, and understood
   by everyone. Safest choice.
2. "snapshot" - well understood, but implies a stored copy rather than a live searchable index.
3. "frozen" - understandable, but in Elastic it names a cost tier and implies rarely queried.
4. "closed" - understood, but in both Elasticsearch and OpenSearch it means not searchable either.
5. "immutable" / "write-once" - accurate Lucene internals term, opaque to non-experts.
6. "sealed" - no precedent anywhere in the surveyed products.
7. "collection" - means the opposite (a live index) in every product except Pinecone.
8. "archived" - implies removed from active use.
