# Reference: vector database terminology, gathered from vendor documentation

Evidence file for the Workbench terminology audit. Each section names a product, its source
URLs, short verbatim excerpts, and what that product calls the concepts we care about.
Excerpts are quoted exactly. Failed fetches are recorded as failures, not filled in.
All pages fetched 2026-08-28.

## Weaviate

Sources: https://docs.weaviate.io/weaviate/manage-collections and
https://docs.weaviate.io/weaviate/concepts/data

> "A Weaviate collection is defined by several core components and settings that enable data storage and vector search."
> "Each data object in Weaviate belongs to a `collection` and has one or more `properties`."
> "Collections are groups of objects that share a schema definition."
> "Weaviate stores `data objects` in class-based collections"

- Top-level container: **collection**. The older name **class** survives only in prose about
  the schema; the current page title is "manage collections".
- One stored unit: **object** or "data object". Not chunk, not record, not point.
- Fields on an object: **properties**.

## Qdrant

Sources: https://qdrant.tech/documentation/concepts/collections/ ,
https://qdrant.tech/documentation/guides/quantization/ ,
https://qdrant.tech/documentation/concepts/hybrid-queries/ .
Direct fetch of https://qdrant.tech/documentation/concepts/points/ and
https://qdrant.tech/documentation/snapshots/ failed (empty body returned, twice); the point and
snapshot wording below is what the search index returned for those pages, so it is weaker
evidence than the rest of this section.

> "A collection is a named set of points (vectors with a payload) among which you can search."
> "Qdrant supports these most popular types of metrics: Dot product: `Dot`, Cosine similarity: `Cosine`, Euclidean distance: `Euclid`, Manhattan distance: `Manhattan`"
> "Scalar quantization ... is a compression technique that compresses vectors by reducing the number of bits used to represent each vector component."
> "Binary quantization is an extreme case of scalar quantization."
> "Product quantization is a method of compressing vectors to minimize their memory usage by dividing them into chunks and quantizing each segment individually."
> "dense and sparse vectors to get the best of both worlds: semantic understanding from dense vectors and precise word matching from sparse vectors"

Via search index, points page: "A point is a record consisting of a vector and an optional
payload." Its three elements are the ID, the vector or vectors, and the payload.
Via search index, snapshots page: snapshots are tar archive files containing the data and
configuration of a collection on a node at a point in time, used to back up and restore.

- Top-level container: **collection**. One stored unit: **point** (id, vector, payload).
- Metadata on a point is called **payload**, never "metadata".
- Category word for cosine/dot/euclid: **distance metrics**; cosine itself is "cosine similarity".
- Durability: **snapshot**. Compression: **scalar / binary / product quantization**.
- Sparse plus dense combining: **hybrid queries**, merged by **fusion** (RRF, DBSF).

## Milvus

Sources: manage-collections.md, glossary.md, manage_databases.md, index-explained.md,
metric.md, multi-vector-search.md under https://milvus.io/docs/ , plus
https://milvus.io/blog/deep-dive-4-data-insertion-and-data-persistence.md (via search index).

> "A collection is a two-dimensional table with fixed columns and variant rows. Each column represents a field, and each row represents an entity."
> "Entities are data records that share the same set of fields in a collection."
> "Partitions are subsets of a collection, which share the same field set with its parent collection, each containing a subset of entities."
> "In Milvus, a database serves as a logical unit for organizing and managing data."
> "An automatically created data file that stores inserted data. A collection may contain multiple segments, and each segment can hold numerous entities."
> "Once sealed, a segment no longer accepts new data and is transferred to object storage."
> "A growing segment continues to collect new data until it hits a specific threshold or time limit, after which it becomes sealed."
> "For a search with a high filter ratio (>95%), use Brute-Force (FLAT) for the most accurate search results."
> "IVF-series index types enable Milvus to cluster vectors into buckets using centroid-based partitioning."
> "In Milvus, similarity metrics are used to measure similarities among vectors."
> "Dense Vector are excellent for capturing semantic relationships, while Sparse Vector are highly effective for precise keyword matching."

Via search index, persistence blog: the transition of a growing segment into a sealed segment
is called a flush; `flush()` persists all data in a collection; a sealed segment is immutable.

- Hierarchy: **database** > **collection** > **partition** > **segment**.
- One stored unit: **entity**, glossed as a data record.
- On-disk unit: **segment**, with states **growing**, **sealed**, **flushed**. "Sealed" is the
  exact word for closed to writes, and the sealed segment is **immutable**.
- Durability verb: **flush**. Exact index: **FLAT**, described as brute force.
- Metric vocabulary: **metric type** and **similarity metrics** headline; "distance metric"
  also appears. Names are `COSINE`, `L2`, `IP`.
- **hybrid search**, **reranking**, result count is `limit` / topK.

## Pinecone

Sources: https://docs.pinecone.io/guides/index-data/indexing-overview ,
https://docs.pinecone.io/guides/organizations/understanding-organizations ,
https://www.pinecone.io/learn/chunking-strategies/

> "In Pinecone, you store data in indexes."
> "Within an index, records are partitioned into namespaces, and all upserts, queries, and other data read and write operations always target one namespace."
> "Dense vectors and sparse vectors are the basic units of data in Pinecone"
> "A Pinecone organization is a set of projects that use the same billing."
> "the process of breaking down large text into smaller segments called chunks"
> "chunking is an essential preprocessing technique that helps optimize the relevance of the content ultimately stored in a vector database"
> "There's no one-size-fits-all solution to chunking, so what works for one use case may not work for another."

- Top-level container: **index**. Pinecone is the outlier that does not say "collection".
- Subdivision inside an index: **namespace**. One stored unit: **record** (id, vector, metadata).
- Account structure: **organization** then **project**. The word "workspace" does not appear on
  the organizations page.
- Splitting text: **chunking**, output **chunks**, and the guide is titled "Chunking Strategies".

## Chroma

Sources: https://cookbook.chromadb.dev/core/collections/ ,
https://docs.trychroma.com/docs/overview/introduction ,
https://docs.trychroma.com/docs/run-chroma/clients (via search index; the persistent-client
page returned HTTP 404 on direct fetch).

> "Collections are the grouping mechanism for embeddings, documents, and metadata."
> "store documents and metadata"

Via search index, clients page: "In this mode, Chroma will persist data between sessions", and
the client is constructed as `chromadb.PersistentClient(path=...)`.

- Top-level container: **collection**.
- One stored unit: an aligned tuple of **id**, **document**, **embedding**, **metadata**; the
  text itself is the **document**.
- Durability: **persist** / **PersistentClient**. Not snapshot, not checkpoint, not flush.

## pgvector

Source: https://github.com/pgvector/pgvector

> "vector similarity search for Postgres"
> "exact and approximate nearest neighbor search"

- Top-level container: an ordinary Postgres **table** with a **vector column**.
- Index types: **HNSW** and **IVFFlat**. The exact path is a sequential scan with no index, so
  "flat" appears only inside the name IVFFlat here.
- The pair of search modes is named exactly **exact and approximate nearest neighbor search**.
- Distance is expressed as operators: `<->` L2, `<#>` inner product, `<=>` cosine. It also
  documents **binary quantization**.

## FAISS

Source: https://github.com/facebookresearch/faiss/wiki/Faiss-indexes

> "Exact Search for L2"
> "all the indexed vectors are decoded sequentially and compared to the query vectors"

- The exact index class is `IndexFlatL2` / `IndexFlat`, factory string `"Flat"`, documented as
  "Exact Search for L2" and characterized as brute force and exhaustive. **Flat is a real,
  standard name for an exact index.**
- Alternatives are **IVF** (inverted file, cell probe) and **HNSW** (graph).

## Chunking vocabulary outside the databases

Sources: https://docs.langchain.com/oss/python/langchain/retrieval and
https://developers.llamaindex.ai/python/framework/module_guides/loading/node_parsers/

> "Break large docs into smaller chunks that will be retrievable individually and fit within a model's context window."
> "Node parsers are a simple abstraction that take a list of documents, and chunk them into `Node` objects, such that each node is a specific chunk of the parent document."

- LangChain: component is a **text splitter**, verb **split**, output unit a **chunk** carried
  in a **Document**.
- LlamaIndex: component is a **node parser** (classes named `SentenceSplitter`,
  `TokenTextSplitter`), output unit a **Node**, still defined as "a specific chunk of the
  parent document".
- **chunk** is the shared noun across all sources even where the component name differs.
  "Segmentation" and "passage" were not the headline word in any source fetched.

## Does any vector database say "workspace"?

Checked: Pinecone organizations page, no occurrence of "workspace". Weaviate Cloud docs, the
console panel is **Clusters** and you "create a cluster". Qdrant Cloud organizes around
**clusters**; the cloud-intro direct fetch failed, so that one rests on the search index only.

Answer: **no vector database in this family uses "workspace" as a product noun.** The account
level nouns in use are organization, project, cluster, and namespace.
