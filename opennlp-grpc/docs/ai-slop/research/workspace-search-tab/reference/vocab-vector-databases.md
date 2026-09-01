# Vocabulary: vector and hybrid search engines

Covers Vespa, Weaviate, Qdrant, Pinecone, Chroma.

Sources fetched 2026-08-28:

- https://docs.vespa.ai/en/overview.html
- https://docs.vespa.ai/en/schemas.html
- https://docs.weaviate.io/weaviate/concepts/data
- https://qdrant.tech/documentation/concepts/collections/
- https://docs.pinecone.io/guides/index-data/indexing-overview
- https://docs.pinecone.io/guides/manage-data/backups-overview
- https://docs.trychroma.com/docs/collections/manage-collections

## Headline finding

Four of the five use exactly one of two nouns: **collection** or **index**. None of
them uses "workspace", "session", "sandbox", or "draft" for the searchable container.
None of them puts an adjective like "dynamic" or "live" in front of the container
noun in normal documentation prose.

## Vespa

Vespa is the outlier: it has no single container noun, because it splits *where data
lives* from *what shape the data has*.

> "Content clusters in Vespa are responsible for storing data and execute queries and
> inferences over the data."
> (overview)

> "The stateless container clusters host components which process incoming data and/or
> queries and their responses."
> (overview)

> "A schema defines a type of data and what we want to compute over it."
> (schemas)

> "A schema contains a document type, which is a named collection of fields."
> (schemas)

> "When a schema is defined and added to a content cluster, you can write data
> according to it, and query using the attributes and indexed fields in it. Indexing
> always happens automatically in real time."
> (schemas)

> "Each content cluster specified in services.xml refers to the schemas that should be
> stored and indexed in that cluster."
> (schemas)

The whole deployable unit is:

> "A Vespa application is completely specified by an _application package_, which is a
> directory structure containing a declaration of the clusters to run as part of the
> application, the content schemas, any machine-learned models and Java components, and
> other configuration or data files needed by various features."
> (overview)

Vespa also gives the strongest statement in this whole survey that write visibility can
be immediate rather than near real time:

> "Writes are persistent and visible in all queries after receiving an ack on the write
> message, after a few milliseconds."
> (overview)

Vespa has no read-only or frozen container state. Note the term **application package**
as a candidate for "the bundle of config plus data that a user assembles", though it
reads as deployment jargon rather than plain language.

## Weaviate

> "Collections are groups of objects that share a schema definition."
> (concepts/data)

> "Each data object in Weaviate belongs to a `collection` and has one or more
> `properties`."
> (concepts/data)

"Collection" is the current term; the older API used **class**, and the raw JSON in the
docs still shows `"class": "Author"`. This rename is itself a data point: a vendor
moved off an implementation-flavoured word (class) to a plain-language one
(collection) once the concept became user-facing.

Weaviate's per-tenant lifecycle states are the most explicit "warm versus cold"
vocabulary in the vector database space:

- `ACTIVE`: loaded, available for read and write
- `INACTIVE`: on local disk, no access
- `OFFLOADED`: on cloud storage, no access
- `OFFLOADING` and `ONLOADING`: transient states

With a documented rename note: tenant status was renamed in v1.26, where `HOT` became
`ACTIVE` and `COLD` became `INACTIVE`. (concepts/data)

That rename is directly relevant: Weaviate concluded that temperature words (hot/cold)
read worse than availability words (active/inactive). A UI choosing between
"live/frozen" and "active/inactive" has a vendor precedent for the second.

Note that none of Weaviate's inactive states are searchable. Weaviate does not have a
"read-only but still searchable" state.

## Qdrant

> "A collection is a named set of points (vectors with a payload) among which you can
> search."
> (concepts/collections)

That is the tidiest one-line container definition found anywhere in this research: a
*named set* of things *among which you can search*.

Qdrant exposes readiness as a status colour rather than as a mutability state:

- green: "collection is ready"
- yellow: "collection is optimizing"
- grey: "collection is pending optimization"
- red: "an error occurred which the engine could not recover from"
  (concepts/collections)

Qdrant has no read-only collection state in the collections concept page. Its
vocabulary for "still settling" is **optimizing**, and for "usable" is **ready**.

## Pinecone

> "In Pinecone, you store data in indexes. A serverless index holds your data as
> documents or records."
> (indexing-overview)

> "Within an index, records are partitioned into namespaces, and all upserts, queries,
> and other data read and write operations always target one namespace."
> (indexing-overview)

Pinecone therefore has a two-level container: **index** (the searchable unit) and
**namespace** (a partition inside it, used for multitenancy and for faster queries).
Namespace is worth noting as the industry word for "a slice of one index that belongs
to one user or one job".

Pinecone's frozen artifact is explicitly *not* searchable:

> "A backup is a static copy of a serverless index that only consumes storage. It is a
> non-queryable representation of a set of records."
> (backups-overview)

> "You can create a new serverless index from a backup. This allows you to restore the
> index with the same or different configurations."
> (backups-overview)

So Pinecone's word for the sealed thing is **backup**, and sealing it means losing
query ability. That is the wrong shape for a "made read-only but still searchable"
concept and should not be borrowed.

## Chroma

> "Collections are the fundamental unit of storage and querying in Chroma."
> (manage-collections)

Chroma follows Weaviate and Qdrant on "collection", with the addition that its docs
frame the collection as the unit of *both* storage and querying. Collection names are
constrained to 3 to 512 characters, must start and end with a lowercase letter or
digit, and may contain dots, dashes, and underscores. Chroma has no read-only
collection state.

## Cross-vendor summary

| Vendor | Container noun | Sub-unit | Writable vs read-only distinction | Term used |
| --- | --- | --- | --- | --- |
| Vespa | content cluster + schema | document type, field | no | none |
| Weaviate | collection (was: class) | tenant, object, property | yes, but not searchable when cold | ACTIVE / INACTIVE / OFFLOADED |
| Qdrant | collection | point, payload | no | status: ready / optimizing |
| Pinecone | index | namespace, record | yes, but sealed copy is not queryable | backup ("non-queryable") |
| Chroma | collection | document, embedding, metadata | no | none |

## What this suggests for naming

1. **collection** is the modal noun of the modern search and vector space: three of
   five vendors, plus Solr, use it. It is plain language, it implies a bag of things
   you gathered, and it carries no mutability claim either way.
2. **namespace** is the standard word for "a per-user or per-job slice of one index".
   If the concept is really "my scoped view", namespace beats workspace on precedent.
3. No vendor in this group markets a "writable versus sealed but still searchable"
   pair. Elasticsearch's read-only index block and Lucene's writer/reader split remain
   the only real precedents for that, which means a UI naming both states is
   effectively free to choose plain words.
4. Weaviate's HOT/COLD to ACTIVE/INACTIVE rename is evidence that temperature
   metaphors age badly compared with availability adjectives.
