# External vocabulary precedents

Excerpts gathered on 2026-08-28 for the Workflows tab terminology review. Each block gives the
source URL, then the quoted definition. Quotes are trimmed to the defining sentence.

## Kibana: "data view"

Source: <https://www.elastic.co/guide/en/kibana/current/data-views.html> (fetched 2026-08-28)

> A data view can point to one or more indices, data streams, or index aliases.

Takeaway: Elastic keeps "index" as the storage noun and introduces a separate, purely
user-facing noun ("data view") for the thing a person picks in a search box. The two nouns are
never used for each other.

## Kibana: "space"

Source: <https://www.elastic.co/docs/deploy-manage/manage-spaces> (fetched 2026-08-28)

> Spaces let you organize your content and users according to your needs. Each space has its own
> saved objects.

> Users can access only the spaces that they have been granted access to.

Takeaway: a "space" is a permission and organisation boundary over saved objects, not a data
container. It carries no storage semantics at all.

## Apache Solr: "collection"

Source: <https://solr.apache.org/guide/solr/latest/deployment-guide/cluster-types.html>
(fetched 2026-08-28)

> A collection is the entire group of cores that represent an index: the logical shards and the
> physical replicas for each shard.

> Collections all share the same configurations (schema, `solrconfig.xml`, etc.).

Takeaway: in Solr, a collection is one logical searchable index spread over physical pieces. It is
the thing a client queries by name. Note the direction: collection wraps index, one to one.

## Pinecone: "index" and "namespace"

Source: <https://docs.pinecone.io/guides/index-data/indexing-overview> (fetched 2026-08-28)

> In Pinecone, you store data in indexes. A serverless index holds your data as documents or
> records, depending on how the index was created.

> Within an index, records are partitioned into namespaces, and all upserts, queries, and other
> data read and write operations always target one namespace.

Takeaway: "index" is the top-level user-visible container, and the sub-container has its own
distinct word. Vector databases have not adopted "workspace" for either level.

## Weaviate: "collection"

Source: <https://docs.weaviate.io/weaviate/concepts/data> (fetched 2026-08-28)

> Collections are groups of objects that share a schema definition.

> Every collection has its own vector space. This means that different collections can have
> different embeddings of the same object.

Takeaway: Weaviate binds "collection" to one vector space, which is exactly the binding this
project's index descriptors carry (`vectorSpaceId` in `search-adapter.ts:38`).

## Milvus: "collection"

Source: <https://milvus.io/docs/manage-collections.md> (fetched 2026-08-28)

> A collection is a two-dimensional table with fixed columns and variant rows. Each column
> represents a field, and each row represents an entity.

> Collection and entity are similar to tables and records in relational databases.

Takeaway: another vector store where "collection" is the queryable container and "index" is a
structure built over a field inside it. This is the opposite nesting from Solr, which is why
using both words for the same product is risky.

## Apache Lucene: commit, segment, point in time

Source: <https://lucene.apache.org/core/10_0_0/core/org/apache/lucene/index/IndexWriter.html>
(fetched 2026-08-28)

> Commits all pending changes (added and deleted documents, segment merges, added indexes, etc.)
> to the index, and syncs all referenced index files, such that a reader will see the changes and
> the index updates will survive an OS or machine crash or power loss.

> The old readers will continue to search the "point in time" snapshot they had opened, and won't
> see the newly created index until they re-open.

Takeaway: Lucene's durability verb is **commit**, and its immutability noun is **segment** (or
"point in time" snapshot). Lucene does not use "checkpoint" or "seal" for either idea.

## MLflow: run, experiment, artifact, model

Source: <https://mlflow.org/docs/latest/ml/tracking/> (fetched 2026-08-28)

> **Run**: executions of some piece of data science code, for example, a single `python train.py`
> execution. Each run records metadata ... and artifacts

> **Experiment**: groups together runs and models for a specific task.

> **Artifact**: output files from the run such as model weights, images, etc.

> **Model**: trained machine learning artifacts that are produced during your runs. Logged Models
> contain their own metadata and artifacts similar to runs.

Takeaway: MLflow already owns "run" and "artifact" for exactly the concepts this tab produces.
"Artifact" as used at `index.html:518` ("No artifacts built yet.") matches MLflow usage; "run" is
the standard word for one execution, which this tab currently calls a "workflow".

## Notion: "workspace"

Source: <https://www.notion.com/help/create-delete-and-switch-workspaces> (fetched 2026-08-28)

> as you start using Notion for more things, and with more groups of people, you can keep
> different kinds of content separate by using multiple workspaces

> Workspaces are completely separate silos, so you won't be able to link any content between them.

## Slack: "workspace"

Source: <https://slack.com/help/articles/212675257-Join-a-Slack-workspace> (fetched 2026-08-28)

> A Slack workspace is made up of channels, where team members can communicate and work together.

Takeaway from Notion and Slack together: in mainstream products "workspace" means *a container of
people and their content, scoped by membership*. It is an account-level, social, long-lived
boundary. It is never a single searchable data structure, and it is never something you create by
pressing a button in the middle of a task. That is the opposite of how this front end uses it.
