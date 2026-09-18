# What "collection" means in other search and vector systems

Excerpts. Fetched 2026-08-28.

## Qdrant
Source: https://qdrant.tech/documentation/concepts/collections/

> "A collection is a named set of points (vectors with a payload) among which you can search."

On aliases:
> "Aliases are additional names for existing collections."
> "All queries to the collection can also be done identically, using an alias instead of the
> collection name."

## Apache Solr
Source: https://solr.apache.org/guide/solr/latest/deployment-guide/collection-management.html

> "A collection is a single logical index that uses a single Solr configuration file
> (`solrconfig.xml`) and a single index schema."

Solr collection aliases are alternative names for collections, letting callers keep one query
endpoint while the underlying collection is replaced.

## Weaviate
Source: https://docs.weaviate.io/weaviate/manage-data/collections

> "A Weaviate collection is defined by several core components and settings that enable data storage
> and vector search."

Collections are containers for objects (the fundamental stored unit) plus their vector embeddings
and their model/indexing configuration.

## The conflict this creates for this repo

In Qdrant, Solr, and Weaviate a **collection IS the searchable thing**: you index into it and you
query it. It is the noun that replaces "index".

In this repo a collection is **not searchable at all**. It is a named grouping of already-existing
dynamic indexes, carrying artifact lineage (dictionary, vocabulary, model) and a drift threshold,
whose only observable behaviour is recomputed term statistics and a watch stream
(`opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/search/SearchCollectionRegistry.java`).
Nothing accepts a collection id where an index id is accepted.

So "collection" here is a false friend: it borrows the most loaded noun in the vector-database
vocabulary and gives it an incompatible meaning. That is the terminology risk to flag to the lead.
