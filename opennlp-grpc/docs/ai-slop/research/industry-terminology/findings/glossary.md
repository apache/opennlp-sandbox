# Industry glossary audit of the OpenNLP gRPC Workbench UI

What this is: every distinct concept the UI names, with the word we use, the word the
industry uses, and a verdict. Raw inventory with `path:line` for every string is in
`reference/ui-vocabulary-inventory.md`. External evidence with URLs and fetch dates is in
`reference/nlp-toolkits.md`, `reference/search-engines.md`,
`reference/vector-databases.md`, `reference/mlops.md`, and
`reference/text-standards.md`. All external pages fetched 2026-08-28.

Verdicts:

- **STANDARD**: at least two independent ecosystems use this exact word for this exact
  thing. Keep it.
- **VARIANT**: the concept is standard but we named it something else, or we picked the
  minority spelling. A better word exists.
- **INVENTED**: no precedent found. Either rename or define it in a glossary.
- **PRODUCT**: a legitimate product-specific name that no standard covers. Keep it, but
  it must be defined somewhere the user can reach.

Priorities on recommendations: **P1** confusing or misleading for a first-time user,
**P2** worth doing, **P3** polish.

Counts: 127 terms audited. 70 STANDARD, 30 VARIANT, 19 INVENTED, 8 PRODUCT. Roughly one
user-visible word in four is either invented here or is the minority spelling of a settled
industry term.

Every product named in the tables below was fetched at one of these URLs on 2026-08-28. Cell
text quotes them; this key keeps the tables readable.

| Product or standard | URL |
| --- | --- |
| Apache Lucene | https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/index/package-summary.html and the `IndexWriter`, `BooleanQuery`, `PhraseQuery`, `TopDocs`, `analysis`, `ByteBuffersDirectory`, `TFIDFSimilarity` javadocs under the same tree |
| Elasticsearch | https://www.elastic.co/guide/en/elasticsearch/reference/current/glossary.html plus `index-modules-blocks`, `indices-forcemerge`, `aliases`, `docs-reindex`, `indices-flush`, `indices-refresh`, `index-lifecycle-management`, `data-streams`, `query-dsl-bool-query`, `compound-queries`, `rrf`, `knn-search`, `highlighting`, `analysis`, `indices-analyze` under the same tree |
| OpenSearch | https://docs.opensearch.org/1.3/im-plugin/index-alias/ and https://docs.opensearch.org/latest/query-dsl/compound/index/ |
| Apache Solr | https://solr.apache.org/guide/solr/latest/getting-started/solr-glossary.html plus `deployment-guide/aliases`, `configuration-guide/commits-transaction-logs`, `indexing-guide/analyzers`, `indexing-guide/analysis-screen` |
| Vespa | https://docs.vespa.ai/en/schemas.html , /overview.html , /ranking.html |
| Weaviate | https://docs.weaviate.io/weaviate/manage-collections |
| Qdrant | https://qdrant.tech/documentation/concepts/collections/ , /guides/quantization/ , /concepts/hybrid-queries/ |
| Milvus | https://milvus.io/docs/glossary.md , /manage-collections.md , /index-explained.md , /metric.md |
| Pinecone | https://docs.pinecone.io/guides/index-data/indexing-overview , /guides/organizations/understanding-organizations , https://www.pinecone.io/learn/chunking-strategies/ |
| Chroma | https://cookbook.chromadb.dev/core/collections/ |
| pgvector | https://github.com/pgvector/pgvector |
| FAISS | https://github.com/facebookresearch/faiss/wiki/Faiss-indexes |
| LangChain | https://docs.langchain.com/oss/python/langchain/knowledge-base , /retrieval |
| LlamaIndex | https://developers.llamaindex.ai/python/framework/module_guides/loading/node_parsers/ |
| MLflow | https://mlflow.org/docs/latest/ml/tracking/ , /ml/model-registry/ |
| Hugging Face | https://huggingface.co/tasks , /docs/transformers/main_classes/pipelines , /docs/transformers/en/tokenizer_summary , /docs/transformers/en/models , /docs/hub/en/model-cards , /docs/transformers/en/tasks/knowledge_distillation_for_image_classification , /blog/static-embeddings , /blog/matryoshka |
| Model2Vec | https://github.com/MinishLab/model2vec |
| ONNX | https://onnx.ai/onnx/intro/concepts.html , https://github.com/onnx/models |
| TensorFlow | https://www.tensorflow.org/guide/saved_model , https://www.tensorflow.org/tensorboard/tensorboard_projector_plugin |
| DVC | https://doc.dvc.org/user-guide/pipelines/defining-pipelines |
| Evidently | https://www.evidentlyai.com/ml-in-production/data-drift |
| UMAP | https://umap-learn.readthedocs.io/en/latest/basic_usage.html |
| Microsoft Foundry model catalog | https://learn.microsoft.com/en-us/azure/ai-foundry/concepts/foundry-models-overview |
| spaCy | https://spacy.io/usage/processing-pipelines , /usage/linguistic-features , /api/span |
| NLTK | https://www.nltk.org/book/ch07.html , /book/ch02.html |
| Stanford CoreNLP | https://stanfordnlp.github.io/CoreNLP/annotators.html |
| Apache OpenNLP manual | https://opennlp.apache.org/docs/2.5.4/manual/opennlp.html |
| Apache UIMA | https://uima.apache.org/d/uimaj-current/oas.html , https://uima.apache.org/ruta.html |
| GATE | https://gate.ac.uk/family/developer.html |
| INCEpTION / WebAnno | https://inception-project.github.io/releases/32.1/docs/user-guide.html |
| TEI P5 | https://tei-c.org/release/doc/tei-p5-doc/en/html/CC.html , /CO.html , and the Analysis and Interpretation chapter |
| CoNLL-U | https://universaldependencies.org/format.html |
| CoNLL-2003 | https://huggingface.co/datasets/eriktks/conll2003 |
| BILOU | https://cogcomp.seas.upenn.edu/papers/RatinovRo09.pdf |
| ISO 24617 SemAF | https://standards.clarin.eu/sis/views/view-spec.xq?id=SpecSemAF |
| Weka | https://machinelearningmastery.com/tour-weka-machine-learning-workbench/ (secondary source, used because it names the shipped GUIs) |

Fetches that failed on 2026-08-28 and are recorded as failures rather than paraphrased in
`reference/`: the UIMA references page, the INCEpTION `releases/latest` path, both
clips.uantwerpen.be CoNLL task pages, iso.org (HTTP 403), the Qdrant points and snapshots
pages, the Chroma persistent-client page, and one third-party drift page. No quotation in
these findings comes from a page that could not be fetched.

---

## A. Document and annotation vocabulary

| Our term | Where | Industry term and who uses it | Verdict | Recommendation |
| --- | --- | --- | --- | --- |
| `Document text` | index.html:118 | spaCy `Doc`, TEI `<text>`, Lucene `Document`, Chroma "document" | STANDARD | Keep. |
| `Annotations` | index.html:280 | UIMA "The association of a metadata, such as a label, with a region of text"; TEI `<interp>` "interpretative annotation"; GATE; ISO 24617 SemAF | STANDARD | Keep. The single safest word in the whole UI. |
| `Layers`, `Annotation layers` | index.html:279, :291; proto `StandardLayer` | INCEpTION and WebAnno say **annotation layer**; UIMA says **type** in a **type system**; GATE says **annotation set**; CoNLL says **column**; TEI and ISO 24617 have no equivalent word | STANDARD (weak) | P3. Keep `layer`, but define it once: "a named set of annotations of one kind". The word is only standard in the annotation-editor family, so it needs a definition for a Lucene or spaCy reader. |
| `Layer scope`, `LAYER_SCOPE_POSITIONAL` / `_DOCUMENT` | opennlp_document.proto:830-838 | UIMA models the same split as `uima.cas.AnnotationBase` (no offsets) versus `uima.tcas.Annotation` (adds begin/end). No ecosystem names the axis | INVENTED | P3. Keep the enum. In the UI say **span-anchored** and **document-level**, never "positional". |
| `Typed annotation` (drawer header) | index.html:374 | UIMA type system is the source of "typed"; nobody says "typed annotation" as a noun phrase | VARIANT | P3. `Annotation details` reads better and loses nothing. |
| Span (`Original source span`, `source_span`) | index.html:694; opennlp_document.proto:260 | TEI `<span>` "associates an interpretative annotation directly with a span of text"; spaCy `Span` "A slice from a Doc object"; UIMA uses begin/end | STANDARD | Keep. TEI-backed. |
| `Offsets` (result summary) | index.html:281 | spaCy `start_char` / `end_char` and `start` / `end`; UIMA begin/end | STANDARD | Keep. |
| `Offset encoding`, `OFFSET_ENCODING_UTF8_BYTE` | index.html:281; opennlp_document.proto:292 | No ecosystem carries an offset-unit flag. spaCy encodes the unit in the field name instead. The nearest standards phrase is the CoNLL "representation scheme" / "encoding scheme", and that is about tag encoding, not character offsets | INVENTED | P2. Rename the user-facing label to **`Offset unit`** and show `UTF-8 bytes` / `UTF-16 code units` / `Unicode code points` (`src/document-shape.ts:326-339` already renders exactly these). "Encoding" makes readers think of character encoding of the text, not of the index unit. |
| `CoordinateSpace` | opennlp_document.proto:279 | Nothing. spaCy separates token offsets from character offsets by field name | INVENTED | P3. Not user-visible today except through raw JSON. Leave the enum; do not surface it. |
| `Tokens`, `Tokenization` | index.html:676; src/analysis-config.ts:47 | Universal. spaCy "splitting a text into meaningful segments, called tokens"; OpenNLP "segment an input character sequence into tokens" | STANDARD | Keep. |
| `Sentences`, `Sentence detection` | index.html:675; src/analysis-config.ts:46 | OpenNLP "Sentence Detector"; CoreNLP `ssplit`; spaCy "sentencizer" | STANDARD | Keep. OpenNLP's own word wins here. |
| `Lemmas` | src/analysis-config.ts:52 | Universal: OpenNLP, spaCy, CoreNLP `lemma`, CoNLL-U `LEMMA` | STANDARD | Keep. |
| `Stems` | src/analysis-config.ts:53 | NLTK stemmers, Lucene `PorterStemFilter`, Snowball | STANDARD | Keep. |
| `Part-of-speech tags`, `Part-of-speech tag set` | src/analysis-config.ts:51; index.html:154 | Universal | STANDARD | Keep. |
| `Universal Dependencies (UD)` / `Penn Treebank` | index.html:157, :158 | CoNLL-U distinguishes **UPOS** ("Universal part-of-speech tag") from **XPOS** ("Optional language-specific ... tag") | VARIANT | P3. Label the options **`Universal (UPOS)`** and **`Treebank (XPOS: Penn)`**. That is exactly the axis CoNLL-U defines, and the current labels do not say which is which. |
| `Named entities` | src/analysis-config.ts:49 | OpenNLP "Name Finder"; spaCy "named entity"; Hugging Face's canonical task name is **token classification** with `ner` as an alias | STANDARD | Keep. |
| `Syntactic chunks`, `Syntactic chunker` | src/analysis-config.ts:59; src/model-data-workbench.ts:655 | OpenNLP manual: "Text chunking consists of dividing a text in syntactically correlated parts of words"; NLTK: "chunking, which segments and labels multi-token sequences"; CoNLL-2000 shared task is named "Chunking" | VARIANT | **P1**. The bare word collides head-on with segmentation chunks (see D). Rename to **`Shallow parse (phrase chunks)`** and **`Shallow parser`**. See `findings/code-vs-ui-consistency.md` M4. |
| `Constituency parses` | src/analysis-config.ts:58 | CoreNLP `parse` "using both the constituent and dependency representations"; OpenNLP "A parser returns a parse tree ... according to a phrase structure grammar" | STANDARD | Keep. |
| `Document categories` | src/analysis-config.ts:56 | OpenNLP "Document Categorizer ... can classify text into pre-defined categories"; Hugging Face canonical name is **text classification** | STANDARD | Keep. OpenNLP's own word. |
| `Sentence sentiment` | src/analysis-config.ts:57 | Hugging Face `sentiment-analysis` is an alias of `text-classification` | STANDARD | Keep. |
| `Language detection` | src/analysis-config.ts:44 | OpenNLP "Language Detector classifies a document in ISO-639-3 languages" | STANDARD | Keep. |
| `Subword tokenization` | src/analysis-config.ts:48 | Hugging Face: "Transformers support three subword tokenization algorithms: Byte pair encoding (BPE), Unigram, and WordPiece" | STANDARD | Keep. |
| `Stopwords` (`STANDARD_LAYER_STOPWORDS`) | opennlp_document.proto:734 | NLTK "a corpus of stopwords"; Lucene `StopFilter` | STANDARD | Keep, one word, no hyphen, as both use it. |
| `Term vectors` | src/analysis-config.ts:54 | Lucene **term vectors** are exactly this: per-document term frequencies with optional positions | STANDARD | Keep. Lucene is the precedent, and it is the right one for a search product. |
| `Lexical expansion` | src/analysis-config.ts:55; proto `LexicalExpansionKind` | Nobody says this. NLTK says **WordNet** and **synsets**; search engines say **synonym expansion** and **query expansion**; the SPI concept is a lexical knowledge base | INVENTED | P2. Rename to **`Synonym expansion`** in the UI, since that is what a user selecting it expects, and keep `LexicalExpansion` on the wire where the broader relation set (hypernym, meronym) justifies it. |
| `Entity geocoding` | src/analysis-config.ts:50 | **Geocoding** is standard GIS vocabulary; the NLP-side name for entity to place resolution is **toponym resolution** or **geoparsing**. None of spaCy, NLTK, CoreNLP or OpenNLP ships it | VARIANT | P3. `Entity geocoding` is clear enough. Mention "toponym resolution" once in help text for readers who know that literature. |
| `Word types` (UAX 29) | opennlp_document.proto:731, :947 | UAX 29 is a real Unicode standard ("Unicode Text Segmentation"), and it defines word break property values | STANDARD | Keep, and cite UAX 29 in the help text. |
| `Offset-aware normalization`, `Applied normalizers` | src/analysis-config.ts:45; src/normalization-xray.ts:100 | **Text normalization** is standard; Elasticsearch has a `normalizer` (a keyword-field analyzer); Lucene has `CharFilter` and `TokenFilter`. "Offset-aware" is our own qualifier and it is the whole point of the feature | VARIANT | P3. Keep. "Offset-aware" is doing real work here: it distinguishes this from destructive normalization. |
| `Document shape` | index.html:248, :360; README.md:26; opennlp_document.proto:641 | Nothing. spaCy says `Doc`, UIMA says CAS, CoreNLP says `Annotation` | INVENTED | P2. It is used consistently in the UI, the proto and the README, so it is a real product term, but a first-time reader cannot guess it. Either rename to **`Analyzed document`** in the UI (keeping "document shape" in the proto comments), or define it in the first help callout. Preference: rename in the UI. |
| `Document analytics` (`Sentences`/`Tokens`/`Entities` counts) | index.html:674-679; opennlp_document.proto:307 | Standard phrasing is **document statistics** or **corpus statistics** | VARIANT | P3. `Document statistics` is plainer. Low value. |

## B. Pipeline and analysis vocabulary

| Our term | Where | Industry term and who uses it | Verdict | Recommendation |
| --- | --- | --- | --- | --- |
| `Pipeline` | index.html:146, :479, :818 | spaCy "processing pipeline"; DVC "Pipelines represent data workflows"; CoreNLP pipeline | STANDARD | Keep. |
| `PipelineStep` / the UI's `feature` | opennlp_pipeline.proto:34; index.html:186, :201 | spaCy says **component** (`add_pipe`); CoreNLP says **annotator**; DVC says **stage**; GATE says **processing resource**. Nobody says "feature" | VARIANT | **P1**. Say **step** in the UI, matching our own proto. `Enabled features` becomes `Enabled steps`. See M3 in `findings/code-vs-ui-consistency.md`. |
| `Pipeline stages` (Workflows) | index.html:479 | DVC uses **stage** for exactly this: a node of a reproducible pipeline with dependencies and outputs | VARIANT | **P1**. The word is fine, "pipeline" is not: these are not `PipelineStep`s. Rename the heading to `Workflow steps`. |
| `Feature preset` / `Profiles` / `Profile: ...` | index.html:128, :89; src/analysis-controls.ts:198 | No standard. "Preset" and "profile" are both ordinary product words | PRODUCT | **P1**. Three names, one widget. Pick `Analysis profile`, which matches `profileId` on the wire. |
| `Required backbone steps are added automatically.` | index.html:202 | spaCy documents component **dependencies**; Airflow and DVC say **upstream** | INVENTED | P2. Say **`Prerequisite steps are added automatically.`** "Backbone" means something else in modelling (a backbone network). |
| `Language pipeline`, `Installed language model sets`, `<lang> language pack` | index.html:146, :150; src/model-data-workbench.ts:366 | spaCy calls a downloadable per-language bundle a **pipeline package** or **trained pipeline**; Tesseract says **language pack** | VARIANT | P3. Standardise on **`Language pack`** across all three places. spaCy's "pipeline package" is more correct but "language pack" is what a first-time user reads faster, and it is already used at src/model-data-workbench.ts:366. |
| `Batch analyze (streaming)` | index.html:229 | Universal | STANDARD | Keep. |
| `Analysis chain` (drift stats fact) | src/lifecycle-workbench.ts:492; opennlp_search.proto:520 | Lucene, Elasticsearch and Solr all say **analyzer**, made of a **tokenizer** and **token filters**. The exact phrase "analysis chain" appears on none of their pages; Solr's closest wording is "a sequence of more specialized classes are wired together" | VARIANT | P2. In the UI say **`Analyzer`**. Keep `AnalysisChainDescriptor` on the wire; the descriptor genuinely identifies a chain including a learned vocabulary, which no single Lucene analyzer does. |
| index-time versus query-time analysis (`query-time analysis provably matches index-time analysis`) | opennlp_search.proto:521-523 | Solr: index time is "when a field is being created", query time "the values being searched for are analyzed" | STANDARD | Keep. This phrasing is exactly right. |

## C. Chunking, embedding and vector vocabulary

| Our term | Where | Industry term and who uses it | Verdict | Recommendation |
| --- | --- | --- | --- | --- |
| `Chunk` (segmentation sense) | index.html:270, :678, :738 | Pinecone: "breaking down large text into smaller segments called chunks"; LangChain `chunk_size` / `chunk_overlap`; LlamaIndex "a specific chunk of the parent document" | STANDARD | Keep, but never leave it bare next to the shallow-parsing sense. See A `Syntactic chunks`. |
| `Chunk strategy`, `Chunk output` | index.html:166; proto `StandardChunkingStrategy` | Pinecone's guide is titled "Chunking Strategies" | STANDARD | Keep. |
| `Sentence chunks` | index.html:170 | LlamaIndex `SentenceSplitter` | STANDARD | Keep. |
| `Token windows`, `Window (tokens)`, `Overlap (tokens)` | index.html:174, :177, :179 | LangChain `chunk_size` and `chunk_overlap`; "sliding window" is universal | STANDARD | Keep. Consider `Chunk size (tokens)` to match the parameter names everyone else uses. P3. |
| `Semantic` chunking | src/chunk-projection.ts:46 | LangChain ships `SemanticChunker` | STANDARD | Keep. |
| `Category` chunking | src/chunk-projection.ts:47 | No precedent found in any source consulted | INVENTED | P3. Define it in help text: "one chunk per document-category span". |
| `Chunk projection`, `Chunk projections`, `All projections, separate lanes` | index.html:310, :328, :330; src/chunk-projection.ts | **Projection** in this field means the low-dimensional view of high-dimensional vectors: UMAP's tutorial says "UMAP projection of the Penguin dataset"; TensorBoard ships the **Embedding Projector** | INVENTED and colliding | **P1**. A user who knows embeddings will expect a 2D scatter and get a list of chunks. Rename to **`Chunk set`** (one per strategy) and the picker option to `All chunk sets, separate lanes`. |
| `Embedding model`, `Document embeddings` | index.html:139; src/analysis-config.ts:60 | Universal. Hugging Face's task name is **feature extraction**, but "embedding model" is what every vector database says | STANDARD | Keep. |
| `Embedding teacher` / `Teacher` / `Training teacher` | index.html:441, :943; src/model-data-workbench.ts:651 | Hugging Face knowledge-distillation guide: "transfer knowledge from a larger, more complex model (teacher) to a smaller, simpler model (student)". Model2Vec's own README says "Sentence Transformer model", not "teacher" | STANDARD | P3. Standardise all three on **`Teacher model`**. |
| `Embedding route`, `Configured embedding route`, `Query embedding route` | src/server-search-workbench.ts:477, :478; opennlp_document.proto:540 | "Routing" is standard in model serving. No ecosystem has this exact descriptor | PRODUCT | P3. Keep. It is precise and there is nothing better. Define it in help text: "which model on which backend produced these vectors". |
| resolved route (`Actual route used to embed the query after route resolution`) | opennlp_search.proto:625 | ES uses "resolved" for aliases; the concept is a fallback | PRODUCT | P3. In the UI, say **`Route actually used`** rather than "resolved". Users read "resolved" as "fixed a problem". |
| `Vector space` (fact label) | src/annotation-drawer.ts:234; src/lifecycle-workbench.ts:178; opennlp_document.proto:546 | The **vector space model** is classic IR (Salton). But our `vector_space_id` is a compatibility identity covering weights, tokenization, pooling and normalization, which is not what "vector space" means to a reader | VARIANT | P2. Label it **`Embedding space id`** or, plainer, **`Compatibility id`**. Keep `vector_space_id` on the wire. |
| `Vector storage` | index.html:448, :746, :1043 | Vector databases say **index type** (Milvus FLAT / IVF / HNSW), **distance metric**, and **quantization**. "Storage" is not the axis they name | VARIANT | P2. Rename to **`Vector index type`**. What the control actually picks is exact versus quantized, which is an index-type choice, not a storage location. |
| `Flat float (exact)` / `Exact flat float` | index.html:748, :450 | FAISS `IndexFlat` is documented as "Exact Search for L2" and is brute force; Milvus: "use Brute-Force (FLAT) for the most accurate search results"; pgvector: "exact and approximate nearest neighbor search" | STANDARD | Keep. Both halves of the label are real vocabulary. |
| `TurboQuant (quantized)` | index.html:749 | Quantization is standard: Qdrant documents scalar, binary and product quantization. TurboQuant is our own implementation name | PRODUCT | Keep. |
| `PCA dimensions` | index.html:461, :947 | **Dimensionality reduction** is the general term; PCA is one instance | STANDARD | Keep. P3: the tooltip should say what 0 means without making the user read `(0 = server default)` twice (index.html:461 lacks it, :947 has it). |
| `Similarity`, `cosine score`, `Cosine` | index.html:561, :739 | Elasticsearch kNN: "using a similarity metric such as cosine or L2 norm"; Milvus says **metric type**; Qdrant says **distance** | STANDARD | Keep. |
| `Embedding granularity` (`EmbeddingGranularity`) | opennlp_document.proto:587 | No precedent | INVENTED | P3. Not user-visible except in raw JSON. If it ever surfaces, say **`Embedding level`** with values document, sentence, chunk. |
| `Centroid` (`ChunkGroupStats`) | opennlp_document.proto:439 | Milvus: "cluster vectors into buckets using centroid-based partitioning" | STANDARD | Keep. |
| `Dimensions` | index.html:568; src/annotation-drawer.ts:392 | Universal | STANDARD | Keep. |

## D. Search and index vocabulary

| Our term | Where | Industry term and who uses it | Verdict | Recommendation |
| --- | --- | --- | --- | --- |
| `Search index`, `index` | index.html:587; opennlp_search.proto:452 | Lucene, Elasticsearch, Pinecone ("In Pinecone, you store data in indexes"), Solr | STANDARD | Keep. |
| `immutable index` | index.html:550; src/server-search-workbench.ts:146; opennlp_search.proto:472 | Lucene applies immutability to **segments**: "Segments are immutable; updates and deletions may only create new segments". Elasticsearch says **read-only**: `index.blocks.write`, and "We recommend force merging only a read-only index (meaning the index is no longer receiving writes)" | VARIANT | **P1**. Say **`read-only index`**. Nobody outside Lucene internals says "immutable index". Full argument in `findings/flagged-terms.md`. |
| `dynamic workspace`, `workspace index`, `On-the-fly workspace index` | index.html:727, :1003, :592 | No search engine and no vector database uses "workspace" for an index. Elasticsearch's only "workspace" is the Kibana UI area: "The main area of the active app in Kibana". Vector database account nouns are organization, project, cluster, namespace | INVENTED | **P1**. Say **`live index`**. Our own provider capability is already `SEARCH_PROVIDER_CAPABILITY_LIVE` (opennlp_search.proto:437) and the Workflows tab already says `Build live index` (index.html:499). |
| `Collection` (group of live indexes) | index.html:1064; opennlp_search.proto:204 | Solr: "one or more Documents grouped together in a single logical index"; Weaviate, Qdrant, Milvus and Chroma all use **collection** for their **top-level container**; Elasticsearch has no "collection" and uses **data stream** for a group of indices | VARIANT | **P1**. Everyone else's "collection" is one index, not a group of them. Either rename ours to **`Index group`**, or keep `collection` and define it prominently. See `findings/flagged-terms.md`. |
| `Corpus`, `Corpus search`, `Corpus artifact`, `Corpus license` | index.html:47, :551; src/server-search-workbench.ts:488, :504 | Deeply standard in NLP: NLTK "A text corpus is a large, structured collection of texts"; TEI `teiCorpus`; OpenNLP manual has a **Corpora** chapter. Not search-engine vocabulary: neither the Solr nor the Elasticsearch glossary has an entry | STANDARD | Keep. This is an NLP product and the NLP word is right. |
| `Alias`, `Point alias at workspace`, `logical alias` | index.html:1020, :1024, :981 | Elasticsearch: "An alias points to one or more indices or data streams ... has no downtime and never points to both streams at the same time"; Solr: "These alternative names for collections are known as aliases", `CREATEALIAS`; OpenSearch: "a virtual index name"; MLflow also has model aliases | STANDARD | Keep. The strongest-precedent term in the whole Lifecycle tab. |
| `blue/green` | index.html:982, :1035 | Standard deployment vocabulary, and Solr documents the same move as "Atomically switch to using a newly (re)indexed collection with zero down time" | STANDARD | Keep. |
| `Rebuild index`, `Rebuild with a new model` | index.html:1053, :1031 | Elasticsearch names the API **Reindex**: "Copy documents from a source to a destination"; Solr writes "(re)indexed". Our own route is `/api/v1/reindex-index` (GrpcJsonApi.java:156) | VARIANT | P2. Say **`Reindex`** in the UI so it matches the route, the RPC and the industry. Keep "with a new model" as the subtitle. |
| `hits`, `0 hits` | index.html:655; opennlp_search.proto:637 | Lucene `TopDocs` "Represents hits returned by IndexSearcher.search"; Elasticsearch `hits` | STANDARD | Keep, and use it consistently: `Search results` at index.html:649 should also say hits. |
| `Search results` / `Results` (top-k input) | index.html:649, :600 | `top_k` and `k` are universal; Milvus says `limit` | VARIANT | P2. Label the input **`Max results (top k)`** so it stops looking like a results heading. |
| `Score`, `Similarity score color scale` | index.html:558, :671 | Universal | STANDARD | Keep. |
| `Compound query builder`, `compound query` | index.html:606 | Elasticsearch documents a section called **Compound queries**: "Compound queries wrap other compound or leaf queries, either to combine their results and scores"; OpenSearch mirrors it; MongoDB Atlas Search uses `compound`. Lucene itself says `BooleanQuery` | STANDARD | Keep. This one is genuinely industry-standard, and the Elasticsearch page is the citation. |
| `Clause`, `Add clause`, `Clear clauses` | index.html:613, :628, :638 | Lucene `BooleanQuery`: "Return a list of the clauses of this BooleanQuery"; Elasticsearch "one or more boolean clauses" | STANDARD | Keep. |
| `Semantic` / `Term` / `Phrase` clause kinds | index.html:615-617 | Elasticsearch `term` query, `match_phrase` query; "semantic" is Elasticsearch's own `semantic_text` wording | STANDARD | Keep. |
| `Phrase slop` | index.html:626 | Lucene `PhraseQuery`: "The slop is an edit distance between respective positions of terms" | STANDARD | Keep. It is jargon, so add the one-line tooltip Lucene's own doc gives. P3. |
| `Join`, `All clauses (AND, mean score)` | index.html:632, :634 | Elasticsearch expresses this as a `bool` query with `must` / `should`; nobody calls the control a "join", and in databases "join" means something else entirely | VARIANT | P2. Rename the label to **`Combine clauses`** and the options to `All clauses must match (mean score)` / `Any clause may match (max score)` / `Any clause, reciprocal rank fusion`. "Join" will be misread as a SQL join. |
| `reciprocal-rank fusion` | index.html:636 | Elasticsearch RRF: "a method for combining multiple result sets with different relevance indicators into a single result set"; Qdrant lists RRF among its **fusion** methods | STANDARD | Keep. Spell it `Reciprocal rank fusion (RRF)`. |
| `Match any term` / `Match all terms` | index.html:623, :624 | Elasticsearch expresses it as `operator: and|or` or `minimum_should_match` | VARIANT | P3. Fine as user-facing wording; it is clearer than the Elasticsearch parameter names. |
| `highlighted spans`, `MatchedSpan` | index.html:610; opennlp_search.proto:664 | Elasticsearch **highlighting** returns "the best-matching highlighted snippets"; Lucene highlighter produces **fragments** | VARIANT | P3. Say **`highlights`** in the user text. `MatchedSpan` on the wire is fine and more precise. |
| `Search provider`, `Provider instances`, `provider instance id` | index.html:1058; opennlp_search.proto:416 | Nothing standard. Elasticsearch and Solr have no pluggable per-index engine concept of this shape | PRODUCT | P3. Keep, but define: "the search engine implementation backing one component of an index". |
| `vector` / `keyword` / `live` / `bundle` / `persistent` capabilities | src/lifecycle-workbench.ts:196 | Milvus and Qdrant say **dense** and **sparse**; Elasticsearch says **kNN** and **text**. Our raw tails are not words | VARIANT | **P1**. Map them to sentences. See M7 in `findings/code-vs-ui-consistency.md`. |
| hybrid (vector plus keyword components) | opennlp_search.proto:508-517 | Milvus: "Dense Vector are excellent for capturing semantic relationships, while Sparse Vector are highly effective for precise keyword matching"; Qdrant **hybrid queries**; OpenSearch **hybrid query** | STANDARD | P2. The UI never says "hybrid" even though the compound builder is exactly a hybrid search. Say so: `Hybrid query builder` would tell a search engineer what this is in one word. |
| `Indexed chunk` versus `Original source span` | index.html:698, :694 | Lucene distinguishes **indexed** (analyzed) from **stored** field values; Solr documents the same split | STANDARD | Keep. This is one of the clearest pairs of labels in the UI. |
| `Provenance`, `Identity and provenance`, `provenance summary` | index.html:706; opennlp_search.proto:548 | W3C PROV; standard in data and ML governance | STANDARD | Keep. |
| `index bundle`, `Bundle format` | src/server-search-workbench.ts:146, :494 | No standard noun. See F `model bundle` | PRODUCT | P2. Always qualify as **index bundle**, never bare `bundle`. |
| `Ranked evidence` / `Selected evidence` | index.html:649, :670 | Search UIs say **results**, **hits**, **matches**. "Evidence" is legal and scientific vocabulary | INVENTED | P2. Rename to `Ranked hits` and `Selected hit`. "Evidence" promises provenance the panel does not always have. |
| `Result inspector` | index.html:670 | Elasticsearch has the **Explain API**; Kibana has **Inspect** | VARIANT | P3. Fine. |
| `all_hits` / exhaustive | opennlp_search.proto:492, :615 | FAISS and Milvus say **exhaustive** and **brute force** | VARIANT | P3. If ever surfaced, say `Return every hit (exhaustive)`. |

## E. Lifecycle and durability vocabulary

| Our term | Where | Industry term and who uses it | Verdict | Recommendation |
| --- | --- | --- | --- | --- |
| `Lifecycle` (tab) | index.html:55, :967 | Elasticsearch **ILM**: "Index lifecycle management (ILM) automates the management of time-based indices"; MLflow: "manage the full lifecycle of a machine learning model" | STANDARD | Keep. |
| `Save checkpoint`, `Checkpoint and seal` | index.html:1007, :1002 | Hugging Face: "A checkpoint refers to the model's weights for a given architecture ... google-bert/bert-base-uncased is a checkpoint". MLflow checkpoints are saved training states. Flink checkpoints are stream state snapshots. No search engine uses the word for writing an index to disk; Lucene says **commit**, Solr says **hard commit** which "calls fsync", Elasticsearch says **flush** and **snapshot** | VARIANT and colliding | **P1**. On a page that also lists model artifacts, "checkpoint" will be read as model weights. Say **`Save to disk`** on the button and **`snapshot`** for the artifact. |
| `persist` (`/api/v1/persist-index`, `PersistIndex`) | GrpcJsonApi.java:152; opennlp_search.proto:115 | Chroma uses it (`PersistentClient`, "Chroma will persist data between sessions"); Lucene, Solr and Elasticsearch do not | VARIANT | P3. Keep on the wire. It is unambiguous, which is more than "checkpoint" manages. |
| `Seal as read-only`, `seal`, `SealIndex` | index.html:1008; opennlp_search.proto:126 | Only Milvus: "Once sealed, a segment no longer accepts new data and is transferred to object storage"; "A growing segment ... becomes sealed". Zero occurrences in Lucene, Elasticsearch, OpenSearch, Solr or Vespa | VARIANT | P2. The button already spells out the meaning (`Seal as read-only`), which is the right instinct. Prefer **`Make read-only`** as the button and keep `SealIndex` on the wire, where the Milvus precedent makes it legible to a vector-database reader. |
| `Snapshot received.` (watch stream) | src/lifecycle-workbench.ts:465 | Qdrant snapshots; Kubernetes watch streams open with a list then watch | STANDARD | Keep. |
| `Watch stream`, `Watching '<id>'.` | index.html:1108; src/lifecycle-workbench.ts:434 | Kubernetes `watch`; etcd watch | STANDARD | Keep. |
| `Vocabulary drift` | index.html:1091, :1100; opennlp_search.proto:253 | **Data drift** and **concept drift** are standard (Evidently: "Data drift is a change in the statistical properties and characteristics of the input data"; "Concept drift relates to changes in the relationships between input and target variables"). **Embedding drift** is used. "Vocabulary drift" appears in no vendor documentation that could be fetched | INVENTED | **P1**. What the feature actually measures is the **out-of-vocabulary rate**, and the UI already says so one heading below (`Out-of-vocabulary terms`, index.html:1106) and shows a `Vocabulary coverage` meter (index.html:1101). Rename the panel to **`Vocabulary coverage`** and the threshold to `Warn after this many out-of-vocabulary terms`. |
| `Out-of-vocabulary terms`, `In the current vocabulary` | index.html:1106; src/lifecycle-workbench.ts:507 | **OOV** is universal; Evidently's own text-drift descriptors include "the share of out-of-vocabulary words"; Hugging Face names the `<unk>` **unknown token** for words "not in the vocabulary" | STANDARD | Keep. Add the abbreviation once: `Out-of-vocabulary (OOV) terms`. |
| `Distinct terms`, `New occurrences` | src/lifecycle-workbench.ts:488, :491 | Lucene says **unique terms** and **term frequency** | VARIANT | P3. `Unique terms` matches Lucene. |

## F. Model, artifact and training vocabulary

| Our term | Where | Industry term and who uses it | Verdict | Recommendation |
| --- | --- | --- | --- | --- |
| `Model bundle`, `Bundles`, `Loaded bundles` | index.html:90, :192, :824 | No standard noun exists. ONNX says **ONNX model**; TensorFlow says **SavedModel** (with `SavedModelBundle` only as a C++ in-memory type); MLflow says **model artifact**; Hugging Face says **repository**; OpenNLP's own manual says **model file** and uses "bundle" only as the verb in "Bundling a custom trained OpenNLP model for the classpath" | INVENTED | P2. Rename the user-facing noun to **`Model set`** or **`Loaded models`**. `Bundles` as a bare counter at index.html:90 tells a new user nothing. Keep `ListModelBundles` on the wire. |
| `Pinned model catalog`, `/api/v1/model-catalog` | index.html:832 | Four live words that are not synonyms: **registry** (MLflow, versioned and governed, yours), **hub** (Hugging Face, shared and public), **zoo** (ONNX Model Zoo, curated pretrained), **catalog** (Microsoft Foundry, browse-and-pick over models you did not train) | STANDARD | Keep. "Catalog" is exactly right for a curated list of third-party models you download. |
| `Artifact id`, `Model artifact`, `Vocabulary artifact id` | index.html:956, :1084; src/server-search-workbench.ts:482 | MLflow: artifacts are "output files from the run such as model weights, images"; DVC: "Stage outputs are files ... for example machine learning models and intermediate artifacts" | STANDARD | Keep. |
| `Model card` | src/model-data-workbench.ts:305 | Hugging Face: "Model cards are files that accompany the models ... you can find a model card as the README.md file in any model repo" | STANDARD | Keep. |
| `Role` / `ModelArtifactRole` | opennlp_training.proto:76; src/model-data-workbench.ts:646 | Hugging Face and spaCy classify models by **task** or by **component**; MLflow uses **flavor** for the serving format | VARIANT | P3. In the UI say **`Model kind`** or **`Used for`**. "Role" is a fine wire name and a vague UI label. |
| `Ready-to-serve static table` | src/model-data-workbench.ts:648 | The standard phrase is **static embedding model**, as opposed to contextual: Model2Vec is "Fast State-of-the-Art Static Embeddings"; the Hugging Face static-embeddings blog contrasts pre-computed token embeddings with attention | VARIANT | P2. Say **`Static embedding model`**. "Table" is an implementation detail. |
| `Train a static model`, `Train model`, `Trained models` | index.html:942, :949, :954 | Model2Vec's own verb is **distill**: "distill your own Model2Vec model from a Sentence Transformer model". The UI already says "distill" in five other places (index.html:877, :888, :491; src/vocabulary-trainer.ts:248, :254) | VARIANT | **P1**. The UI itself warns that "train" is misleading (index.html:508-510, `What "train" means here`). That footnote exists because the button lies. Rename step 3 to **`Distill a static model`** and the button to **`Distill model`**, and the footnote can go. |
| `Teacher` (see C) | index.html:943 | Hugging Face distillation guide uses teacher and student | STANDARD | P3. Say `Teacher model`. |
| `Distill`, `Distill embeddings` | index.html:491, :888 | Model2Vec and Hugging Face both use it | STANDARD | Keep. |
| `Vocabulary`, `Learned vocabularies` | index.html:916, :936 | Hugging Face: subword algorithms keep "the vocabulary compact"; Model2Vec: "forward pass a vocabulary through a sentence transformer model" | STANDARD | Keep the noun. |
| `Learn a vocabulary`, `Learn vocabulary`, `learned vocabulary` | index.html:916, :935; opennlp_vocabulary.proto:140 | Nobody "learns" a vocabulary. scikit-learn `CountVectorizer` **fits** and exposes `vocabulary_`; Hugging Face tokenizer training **builds** a vocabulary; Model2Vec takes a vocabulary as input | VARIANT | P2. Say **`Build a vocabulary`** and `Built vocabularies`. "Learn" implies a model was fitted; this is a frequency-filtered term extraction. |
| `Min frequency`, `Min term frequency`, `Max terms`, `Max corpus terms` | index.html:927, :455, :931, :458 | scikit-learn `min_df` and `max_features`; Lucene min term frequency | STANDARD | P3. Use one pair of labels on both tabs; right now the Trainer and Workflows tabs word them differently. |
| `Dictionary`, `Imported dictionaries`, `headwords` | index.html:904, :912, :438 | NLTK: "WordNet is a semantically-oriented dictionary of English"; **headword** is standard lexicography | STANDARD | Keep. |
| `Vocabulary source` (Workflows) | index.html:434 | Not standard, but it is a clear compound of two standard words | PRODUCT | P3. Keep. |
| `Installed models`, `Downloaded on this node` | index.html:844; src/model-data-workbench.ts:198 | Standard operational phrasing | STANDARD | Keep. |
| `Pipeline readiness`, `ready` | index.html:818, :803 | Kubernetes **readiness**; standard | STANDARD | Keep. This is one of the best-named things in the UI. |
| `Verified resource installer`, checksum, digest | index.html:852, :861 | SHA-256 **digest** and **checksum** are standard | STANDARD | Keep. |
| `Trainer` (tab) | index.html:53, :874 | Hugging Face ships a `Trainer` class | STANDARD | Keep, though what the tab does is import, extract and distill. See `Train a static model` above. |
| `Loading the trainer catalog.` / `Loading the lifecycle catalog.` | index.html:900, :996 | There is no catalog behind either message | INVENTED | P3. Replace with what is actually loading. See M11. |

## G. UI structure and visualization vocabulary

| Our term | Where | Industry term and who uses it | Verdict | Recommendation |
| --- | --- | --- | --- | --- |
| `Workbench` (product subtitle and every tab panel) | index.html:38, :41, :82 | Weka ships a top-level GUI literally called the **Workbench**, the integrated environment combining all its graphical interfaces. Apache UIMA ships the **Ruta Workbench**. GATE went the other way and calls its equivalent **GATE Developer**, "a specialist tool similar in purpose and character to a programmer's integrated development environment" | STANDARD | Keep. Real precedent in a mainstream open source ML toolkit and inside Apache itself, and it carries the right promise: everything in one window. |
| `playground` (skip link, section aria-label) | index.html:32, :66 | Common for hosted API try-it pages | VARIANT | P3. It contradicts "workbench" in the same document. Change the skip link to `Skip to workbench` and the aria-label to `OpenNLP workbench` (index.html:32, :66). |
| `Workflow`, `Workflow name`, `Build workflow and search` | index.html:412, :469 | DVC: "Pipelines represent data workflows that you want to reproduce reliably"; Airflow and Argo Workflows | STANDARD | Keep. |
| `Heatmap`, `Document heatmap`, `Search heatmap` | index.html:272, :319, :524 | Universal data-visualization term | STANDARD | Keep. |
| `Graph`, `Document graph` | index.html:274, :351 | Universal | STANDARD | Keep. |
| `Normalization X-ray` | index.html:220; src/normalization-xray.ts:91 | No precedent. The equivalents are Elasticsearch's `_analyze` API and Solr's Admin UI **Analysis screen**, both of which show the token stream step by step | INVENTED | P2. Rename to **`Normalization trace`** or `Show each normalizer`. "X-ray" is memorable but a user searching the docs for it will find nothing, and the feature is exactly what other engines call analysis explanation. |
| `Document shape graph` (aria-label) | index.html:360 | See A `Document shape` | INVENTED | P2. `Analyzed document graph`. |
| `lens` (`server-search-lens`, `search-lens-heading`) | index.html:545, :547 | Nothing | INVENTED | P3. Not user-visible. Rename the class for readability only. |
| `Protobuf JSON` (result tab) | index.html:276 | The canonical name is **protobuf JSON mapping** (google.protobuf.util.JsonFormat) | STANDARD | Keep. |
| `Score legend`, `less similar / cosine score / more similar` | index.html:558, :561 | Standard dataviz legend wording | STANDARD | Keep. One of the clearest affordances in the UI. |
| `Server automatic`, `Automatic (route by detected language)` | index.html:132, :148 | Ordinary product wording | PRODUCT | P3. Keep. |
| `Add to server workspace` | index.html:252 | See D `workspace` | INVENTED | **P1**. Becomes `Add to live index`. |
| `Clear workspace index` | index.html:778 | Same | INVENTED | **P1**. Becomes `Delete this live index`, which is what the route does (`/api/v1/delete-search-index`, GrpcJsonApi.java:150). "Clear" suggests emptying, not deleting. |

---

## Appendix 1. Cross-tab references, and whether a jump exists

FACT. Only three cross-tab jumps exist as clickable controls, all via
`data-workbench-jump` (handled at `src/workbench-navigation.ts:40-42`), plus one
programmatic jump:

| From | To | Control | Exists |
| --- | --- | --- | --- |
| Corpus search intro | Workspace search | index.html:555 | yes |
| Corpus search index picker | Workflows | index.html:592 | yes |
| Workspace search intro | Corpus search | index.html:735 | yes |
| Trainer model row `Use in Analyze` | Analyze, with the model preselected | src/vocabulary-trainer.ts:298 wired at src/main.ts:244-250 | yes, and it also sets the embedding model, which is the best cross-tab affordance in the app |

FACT. These places name another tab in prose but offer no jump:

| Where | Text | Missing jump |
| --- | --- | --- |
| index.html:103-104 | `Press Add to server workspace to index the analyzed document, then query it from the Workspace search tab.` | to Workspace search |
| index.html:754 | `On the Analyze tab, analyze a document with an embedding model selected.` | to Analyze |
| index.html:760-761 | `use the Lifecycle tab` | to Lifecycle |
| index.html:889-890 | `select it on the Analyze tab, index with it, and search with it` | to Analyze |
| index.html:956-957 | `Select it as the embedding model in Analyze, index the analyzed documents in Workspace search` | to Analyze and to Workspace search |
| src/lifecycle-workbench.ts:136 | `Index documents in Workspace search to create a dynamic workspace first.` | to Workspace search |
| src/lifecycle-workbench.ts:238 | `No trained model yet: distill one on the Trainer tab` | to Trainer |

OPINION (P2). The last two matter most, because they are dead ends: the Lifecycle tab is
entirely unusable until the user has done something on another tab, and it tells them so
in a `<select>` option and a status line that neither of which they can click. Wrap both
in the existing `link-button` plus `data-workbench-jump` pattern already used at
index.html:555.

## Appendix 2. Feature gating, and what the user sees today

FACT, gathered from the running demo (2026-08-28) and the source. For each feature that
fails when a model or backend is missing:

| Feature | Requires | Exact text today | Where |
| --- | --- | --- | --- |
| Embedding model picker (Analyze) | at least one configured embedding backend | `No embedding model configured` (select is disabled) | index.html:141 |
| Add to server workspace | an embedding model plus a chunk strategy | `This result has no indexed chunk embeddings. Select an embedding model and chunk strategy.` | src/semantic-workbench.ts:246 |
| Corpus search | an operator-configured immutable index | `No server indexes configured` and `An operator must configure an immutable index bundle at startup.` | src/server-search-workbench.ts:144, :146 |
| Trainer, all three steps | `vocabulary.artifact_root` and at least one `training.teacher.<id>.ref` | `Training is disabled: the server has no vocabulary artifact root or no teachers.` and `No teachers are configured; add training.teacher entries.` | src/vocabulary-trainer.ts:161, :165 |
| Workflows | same as Trainer | `Training is unavailable because this server has no writable artifact root or teacher model.` | src/corpus-workflow.ts:156 |
| Lifecycle, save and seal | `search.persist.root` | server message `Index persistence is not configured; set search.persist.root` | DynamicSearchIndexRegistry.java:522 |
| Lifecycle, rebuild | a trained static model | `No trained model yet: distill one on the Trainer tab` | src/lifecycle-workbench.ts:238 |
| Model catalog | a build that publishes one | `This build does not publish a standard model catalog.` | src/model-data-workbench.ts:347 |
| Sentiment heatmap | a sentiment model | `No typed sentiment layer was returned. Enable Sentiment and install its model data first.` | src/semantic-workbench.ts:535 |

OPINION (P2). The vocabulary of these messages is already better than the vocabulary of
the labels above them: they say what is missing and which config key supplies it. Two
gaps. First, three of them name a config key (`training.teacher`,
`vocabulary artifact root`, `search.persist.root`) while three others do not, so the fix
path is inconsistent. Second, none of them offers a jump. A browned-out state should read
as one sentence with one action, for example: "Corpus search needs a prebuilt index
bundle configured at startup. You can build a searchable index yourself on the Workflows
tab." with the tab name as a `data-workbench-jump` button.

## Appendix 3. Tests that touch this vocabulary

FACT. 32 unit test files under `opennlp-grpc-webapp-default/test/*.test.ts` and three
Playwright specs under `opennlp-grpc-webapp-default/e2e/`.

FACT. Tests that assert on a label map or an enum-to-label conversion, that is, the
things this audit would change:

- `test/analysis-config.test.ts` covers `FEATURE_NAMES` and profile assembly
  (`src/analysis-config.ts:43-61`).
- `test/chunk-projection.test.ts` covers the strategy label map
  (`src/chunk-projection.ts:44-47`).
- `test/document-shape.test.ts` covers `layerTitle` and `offsetEncodingLabel`
  (`src/document-shape.ts:318`, :326).
- `test/model-data-workbench.test.ts` covers `roleLabel` and `installLabel`
  (`src/model-data-workbench.ts:646`, :664).
- `test/search-adapter.test.ts` covers capability lowercasing
  (`src/search-adapter.ts:161-167`).

FACT. Vocabulary with **no** test found anywhere:

- The provider capability rendering at `src/lifecycle-workbench.ts:196`. The lowercasing
  is tested in `search-adapter`, but nothing asserts what the user finally reads.
- The three `data-workbench-jump` targets (index.html:555, :592, :735).
  `test/workbench-navigation.test.ts` tests tab selection but no test asserts that a jump
  button exists for a named tab.
- Every literal string in `index.html`. Nothing reads the HTML and checks a label.
- The status strings on the Lifecycle tab (`src/lifecycle-workbench.ts:136`, :238, :497),
  which are the ones that tell a user what to do next.

OPINION (P2). One new test file, `test/vocabulary.test.ts`, that parses `index.html` and
asserts the agreed glossary: no banned word appears in a user-visible position, and every
`data-workbench-jump` value resolves to a real tab id. Cheap, and it is the only thing
that will stop the vocabulary drifting back after a rename.

## Questions for the lead

1. Are you willing to rename `workspace` to `live index` across the UI, or is `workspace`
   already committed to in a talk, a blog post, or a README that ships?
2. `collection` is the hardest call. Every vector database uses it for their **top-level
   container**, and ours is a **group of indexes**. Is a rename to `index group`
   acceptable, or do you prefer to keep `collection` and define it loudly?
3. `Train a static model` versus `Distill a static model`: the UI already carries a
   footnote apologising for "train" (index.html:508-510). Do you want the button renamed,
   or the footnote kept?
4. Is `Normalization X-ray` a name you want to keep for its memorability, accepting that
   it is undiscoverable, or should it become `Normalization trace`?
