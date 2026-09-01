# The owner's flagged terms: verdict and strongest precedent

One entry per term the owner called out. Each gives the exact user-visible string with
`path:line`, the verdict, the single strongest precedent with its quote and link, and one
recommendation with a priority. Full evidence with fetch dates is in `reference/`.
All external pages fetched 2026-08-28.

Verdict key is the same as `findings/glossary.md`: STANDARD, VARIANT, INVENTED, PRODUCT.

At a glance:

| Term | Verdict | Recommended word | Priority |
| --- | --- | --- | --- |
| immutable index | VARIANT | read-only index | P1 |
| dynamic workspace | INVENTED | live index | P1 |
| workspace | INVENTED | live index | P1 |
| collection | VARIANT | index group, or keep and define | P1 |
| checkpoint / persist checkpoint | VARIANT, collides | Save to disk / snapshot | P1 |
| seal / seal immutable | VARIANT | Make read-only (keep `SealIndex` on the wire) | P2 |
| vocabulary | STANDARD | keep | keep |
| vocabulary drift | INVENTED | vocabulary coverage, out-of-vocabulary rate | P1 |
| trained vocabulary | VARIANT | built vocabulary | P2 |
| teacher | STANDARD | teacher model | P3 |
| static model | VARIANT | static embedding model | P2 |
| compound query | STANDARD | keep, consider "hybrid query" | keep |
| corpus search | STANDARD | keep | keep |
| workflow | STANDARD | keep | keep |
| lifecycle | STANDARD | keep | keep |
| workbench | STANDARD | keep | keep |
| model bundle | INVENTED | model set, or loaded models | P2 |
| catalog | STANDARD | keep for the model catalog only | P3 |
| role | VARIANT | model kind, or "used for" | P3 |
| layer | STANDARD (weak) | keep, define once | P3 |
| resolved route | PRODUCT | route actually used | P3 |
| vector space | VARIANT | embedding space id | P2 |
| chunk | STANDARD | keep, but never bare next to shallow parsing | P1 |
| projection | INVENTED, collides | chunk set | P1 |
| heatmap | STANDARD | keep | keep |
| X-ray | INVENTED | normalization trace | P2 |
| shape | INVENTED | analyzed document | P2 |

---

## 1. `immutable index`

FACT. Shown as the heading `Explore an immutable index` (index.html:550) and in the empty
state `An operator must configure an immutable index bundle at startup.`
(src/server-search-workbench.ts:146). The wire field is
`SearchIndexDescriptor.immutable` (opennlp_search.proto:472).

VERDICT: **VARIANT**. The concept is standard, the phrase is not.

Strongest precedent. Elasticsearch names the state **read-only**, both as a setting
(`index.blocks.write`) and in prose: "We recommend force merging only a read-only index
(meaning the index is no longer receiving writes)."
(https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-forcemerge.html). Lucene does
use "immutable", but about **segments**, not indexes: "Segments are immutable; updates and
deletions may only create new segments and do not modify existing ones."
(https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/index/package-summary.html). So "immutable index" reads to a
Lucene person as a category error and to an Elasticsearch person as an unfamiliar phrase.

OPINION (P1). Change the heading at index.html:550 to
**`Explore a read-only index`** and the empty state at src/server-search-workbench.ts:146
to "An operator must configure a read-only index bundle at startup." Leave the wire field
`immutable` alone; renaming it costs compatibility and buys nothing.

## 2. `dynamic workspace`

FACT. `Dynamic workspace` is the select label on the Lifecycle tab (index.html:1003), and
`No dynamic workspaces` is its empty option (src/lifecycle-workbench.ts:148).

VERDICT: **INVENTED**. Two invented words joined.

Strongest precedent. There is none for the pair. The closest thing anyone ships is
Milvus's **growing segment**: "A growing segment continues to collect new data until it
hits a specific threshold or time limit, after which it becomes sealed."
(https://milvus.io/docs/glossary.md). Our own proto already has the right adjective in
`SEARCH_PROVIDER_CAPABILITY_LIVE`, described as "Serves mutable live indexes published as
atomic snapshots" (opennlp_search.proto:436-437).

OPINION (P1). **`Live index`**. It is our own word, it is one syllable shorter, and the
Workflows tab already uses it (`Build live index`, index.html:499).

## 3. `workspace`

FACT. The tab label `Workspace search` (index.html:49), the heading
`On-the-fly workspace index` (index.html:727), the button
`Add to server workspace` (index.html:252), `Search workspace` and
`Clear workspace index` (index.html:777, :778), `Workspace to search` (index.html:742),
`New workspace (created on first add)` (index.html:744), and seven runtime status strings
in src/semantic-workbench.ts (:211, :219, :250, :277, :305, :317, :408).

VERDICT: **INVENTED** for an index.

Strongest precedent, and it points the other way. No search engine and no vector database
uses "workspace" for an index. Elasticsearch's glossary uses the word exactly once, for a
UI region: workspace is "The main area of the active app in Kibana."
(https://www.elastic.co/guide/en/elasticsearch/reference/current/glossary.html). Pinecone's account hierarchy is
**organization** then **project**, and "workspace" does not appear on that page
(https://docs.pinecone.io/guides/organizations/understanding-organizations). Weaviate
Cloud and Qdrant Cloud organize around **clusters**. Lucene's in-memory index is a
`ByteBuffersDirectory`, "A ByteBuffer-based Directory implementation that can be used to
store index files on the heap."
(https://lucene.apache.org/core/9_11_1/core/org/apache/lucene/store/ByteBuffersDirectory.html).

OPINION (P1). Replace with **`live index`** everywhere. Concretely:
`Workspace search` becomes `Live index search`; `Add to server workspace` becomes
`Add to live index`; `Clear workspace index` becomes `Delete this live index` (which is
what `/api/v1/delete-search-index` actually does, GrpcJsonApi.java:150). This also lets
the DOM id `session-search` be renamed to match its label for the first time
(index.html:48-49; src/workbench-navigation.ts:23, :30, :105).

## 4. `collection`

FACT. On the Lifecycle tab, `Collection`, `Collection id`, `Member workspaces`,
`Save collection` (index.html:1064, :1068, :1076, :1096), backed by
`CollectionDescriptor` (opennlp_search.proto:204). On the Workflows tab, the unrelated
heading `Your text collection` over a plain textarea (index.html:402).

VERDICT: **VARIANT**. The word is standard, but everyone else's "collection" is the
opposite level of the hierarchy from ours.

Strongest precedent, and it is against us. Four of the six vector databases use
**collection** as the **top-level container of stored units**: Weaviate "Collections are
groups of objects that share a schema definition"
(https://docs.weaviate.io/weaviate/manage-collections); Qdrant "A collection is a named
set of points (vectors with a payload) among which you can search"
(https://qdrant.tech/documentation/concepts/collections/); Milvus "A collection is a
two-dimensional table with fixed columns and variant rows"
(https://milvus.io/docs/manage-collections.md); Chroma "Collections are the grouping
mechanism for embeddings, documents, and metadata"
(https://cookbook.chromadb.dev/core/collections/). Solr agrees at the same level: "one or
more Documents grouped together in a single logical index"
(https://solr.apache.org/guide/solr/latest/getting-started/solr-glossary.html). Ours is a
**group of indexes**, which in the Elasticsearch world is a **data stream**: "A data
stream acts as a layer of abstraction over a set of indices"
(https://www.elastic.co/guide/en/elasticsearch/reference/current/data-streams.html).

OPINION (P1). Two acceptable answers, and one unacceptable one.

- Preferred: rename ours to **`Index group`**. It says what it is and it collides with
  nothing.
- Acceptable: keep `Collection` and put a one-line definition directly under the heading:
  "A collection groups several live indexes so their vocabulary coverage is tracked
  together." A user coming from Qdrant or Weaviate will otherwise assume a collection
  holds vectors.
- Not acceptable: keeping both meanings. Rename the Workflows heading
  `Your text collection` (index.html:402) to **`Your documents`** regardless of which
  answer you pick.

## 5. `checkpoint` and `persist checkpoint`

FACT. Button `Save checkpoint` (index.html:1007), subcard heading `Checkpoint and seal`
(index.html:1002), help text `Save a checkpoint to keep a dynamic workspace across server
restarts` (index.html:979). The wire is `PersistIndex` / `/api/v1/persist-index`
(opennlp_search.proto:115; GrpcJsonApi.java:152), and the same tab's help callout names
the RPC as "the persist ... RPCs" (index.html:989).

VERDICT: **VARIANT**, and it actively collides on a page full of models.

Strongest precedent, and it is a collision. Hugging Face defines checkpoint as weights:
"A checkpoint refers to the model's weights for a given architecture. For example, BERT is
an architecture while google-bert/bert-base-uncased is a checkpoint."
(https://huggingface.co/docs/transformers/en/models). MLflow's checkpoints are mid-training
model states logged as artifacts. Apache Flink's checkpoints are stream state. **No search
engine and no vector database uses "checkpoint" for writing an index to disk.** They say:
Lucene **commit** ("Commits all pending changes ... to the index") versus **flush**
("Moves all in-memory segments to the Directory, but does not commit (fsync) them");
Solr **hard commit**, which "calls `fsync` on the index files"; Elasticsearch **flush** and
**snapshot**; Qdrant **snapshot**; Milvus **flush**; Chroma **persist**.

Note that our own Lifecycle tab lists model artifacts one panel away
(`Serving model artifact`, index.html:1088). A reader who knows Hugging Face will read
`Save checkpoint` as "save model weights".

OPINION (P1). Button becomes **`Save to disk`**, with the existing helper text kept
verbatim ("writes the workspace to disk, and it keeps accepting new documents",
index.html:1012-1013, with "workspace" replaced per item 3). Subcard heading becomes
**`Durability`** or `Save and make read-only`. Where a noun is needed for the on-disk
artifact, use **snapshot**, which Qdrant and Elasticsearch both use and which nobody
confuses with weights. Keep `PersistIndex` on the wire; "persist" has Chroma precedent and
is unambiguous.

## 6. `seal` and `seal immutable`

FACT. Button `Seal as read-only` (index.html:1008-1009), help text "or seal it to make it
read-only" (index.html:979-980), wire `SealIndex` (opennlp_search.proto:126) and
`/api/v1/seal-index` (GrpcJsonApi.java:154).

VERDICT: **VARIANT**. Real precedent, but in exactly one product.

Strongest precedent. Milvus, and only Milvus: "Once sealed, a segment no longer accepts new
data and is transferred to object storage." "A growing segment continues to collect new
data until it hits a specific threshold or time limit, after which it becomes sealed."
(https://milvus.io/docs/glossary.md). The word appears nowhere in Lucene, Elasticsearch,
OpenSearch, Solr or Vespa.

OPINION (P2). The button already glosses itself (`Seal as read-only`), which is the right
instinct and should be preserved. Simplify to **`Make read-only`**: it needs no gloss at
all and it matches item 1's `read-only index`. Keep `SealIndex` on the wire, where the
Milvus precedent makes it legible to anyone coming from a vector database.

## 7. `vocabulary`

FACT. The Trainer tab: `2 - Learn a vocabulary`, `Learned vocabularies`,
`Vocabulary artifact id`, `Vocabulary source` (index.html:916, :936, :1084, :434).

VERDICT: **STANDARD**.

Strongest precedent. Hugging Face's tokenizer summary uses it throughout: subword
algorithms "split text into units between words and characters, keeping the vocabulary
compact"; "Words not in the vocabulary map to an `<unk>` token"
(https://huggingface.co/docs/transformers/en/tokenizer_summary). Model2Vec's method is
described as "forward pass a vocabulary through a sentence transformer model"
(https://github.com/MinishLab/model2vec).

OPINION. Keep, unchanged. See item 9 for the verb.

## 8. `vocabulary drift`

FACT. `Vocabulary drift` is a section heading (index.html:1100), a threshold label
`Report vocabulary drift after this many new terms` (index.html:1091), a tab kicker
`Save, watch drift, retrain, rebuild` (index.html:966), and the wire message
`CollectionDriftStats` (opennlp_search.proto:253).

VERDICT: **INVENTED**.

Strongest precedent, and it is against us. The MLOps drift vocabulary is precisely
partitioned and "vocabulary drift" is not in it. Evidently: "Data drift is a change in the
statistical properties and characteristics of the input data" and "Concept drift relates to
changes in the relationships between input and target variables"
(https://www.evidentlyai.com/ml-in-production/data-drift). **Embedding drift** is also
standard. The measurable our feature actually computes has a standard name: the
**out-of-vocabulary rate**, listed by Evidently as a text descriptor, "the share of
out-of-vocabulary words". A search surfaced "vocabulary drift" only on one third-party
course page that returned HTTP 403, so there is no citable use of it anywhere.

Note that the UI already knows the right words. The meter's aria-label is
`Vocabulary coverage` (index.html:1101) and the heading one line below is
`Out-of-vocabulary terms` (index.html:1106).

OPINION (P1). Rename the section heading at index.html:1100 to
**`Vocabulary coverage`**, and the threshold label at index.html:1091 to
**`Warn after this many out-of-vocabulary terms`**. Change the kicker at index.html:966 to
`Save, watch coverage, retrain, rebuild`. Keep `CollectionDriftStats` on the wire if you
like, but the user-facing word should be the one Evidently and every tokenizer doc uses.

## 9. `trained vocabulary`

FACT. The verb form: `2 - Learn a vocabulary` (index.html:916), the button
`Learn vocabulary` (index.html:935), `Learned vocabularies` (index.html:936),
`The server is learning the vocabulary.` (src/vocabulary-trainer.ts:208), the RPC
`LearnVocabulary` (opennlp_vocabulary.proto:140), and the route `/api/v1/learn-vocabulary`
(GrpcJsonApi.java:178). The Trainer's help text also says "learn a vocabulary from your
documents" (index.html:876).

VERDICT: **VARIANT**. Nobody trains or learns a vocabulary; they build one.

Strongest precedent. scikit-learn's `CountVectorizer` **fits** and exposes a
`vocabulary_` attribute; a Hugging Face tokenizer is **trained** but the vocabulary is its
output, not the thing trained. Model2Vec takes an existing vocabulary as **input**:
"forward pass a vocabulary through a sentence transformer model, creating static embeddings
for the individual tokens" (https://github.com/MinishLab/model2vec). What our step does is
frequency-filtered term extraction, controlled by `Min frequency` and `Max terms`
(index.html:927, :931), which is exactly scikit-learn's `min_df` and `max_features`.

OPINION (P2). UI verb becomes **`Build`**: `2 - Build a vocabulary`, button
`Build vocabulary`, select `Built vocabularies`, status "The server is building the
vocabulary." (index.html:916, :935, :936; src/vocabulary-trainer.ts:208). Leave
`LearnVocabulary` on the wire. "Learn" oversells a frequency filter, and on a tab that also
does real distillation that overselling matters.

## 10. `teacher`

FACT. Three different labels for one object: `Teacher` (index.html:943),
`Embedding teacher` (index.html:441), `Training teacher`
(src/model-data-workbench.ts:651). Wire:
`MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER` (opennlp_training.proto:79) and
`TeacherDescriptor` (opennlp_training.proto:287).

VERDICT: **STANDARD**, with one caveat.

Strongest precedent. Hugging Face's knowledge-distillation task guide: "Knowledge
distillation is a technique used to transfer knowledge from a larger, more complex model
(teacher) to a smaller, simpler model (student)."
(https://huggingface.co/docs/transformers/en/tasks/knowledge_distillation_for_image_classification).
Caveat: **Model2Vec itself does not use the word.** Its README says "distill your own
Model2Vec model from a Sentence Transformer model"
(https://github.com/MinishLab/model2vec). Honest note on the usual citation: the Hinton,
Vinyals and Dean 2015 paper (arXiv:1503.02531) is titled "Distilling the Knowledge in a
Neural Network" and its abstract says "cumbersome model", not "teacher", so cite the
Hugging Face guide rather than the abstract.

OPINION (P3). Keep `teacher`, standardise all three UI labels on **`Teacher model`**
(index.html:441, :943; src/model-data-workbench.ts:651). The proto names are already
correct and need no change. If you ever want to be maximally native to Model2Vec, the
alternative is "Source model", but "teacher model" is the wider-field word and is the
better choice for a general product.

## 11. `static model`

FACT. `3 - Train a static model` (index.html:942), placeholder
`Legal static model` (index.html:946), role label
`Ready-to-serve static table` (src/model-data-workbench.ts:648), route
`/api/v1/static-models` (GrpcJsonApi.java:188), wire `StaticModelDescriptor`
(opennlp_training.proto:338) and `MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING`
(opennlp_training.proto:81).

VERDICT: **VARIANT**. The full phrase is standard, the truncated one is ambiguous.

Strongest precedent. Model2Vec's own tagline is "Fast State-of-the-Art Static Embeddings"
and it calls its output a **static embedding model**
(https://github.com/MinishLab/model2vec). Hugging Face's static-embeddings blog frames the
contrast: static embeddings are a lookup of pre-computed token embeddings, as opposed to
contextual, attention-based embeddings whose output for one token differs between "river
bank" and the financial institution
(https://huggingface.co/blog/static-embeddings). So "static" here means **non-contextual**,
not "frozen" and not "unchanging". Bare "static model" invites the frozen reading, and it
also collides with the proto's use of "static" to mean "not dynamic" for indexes
(opennlp_search.proto:444, :636).

OPINION (P2). Always write **`static embedding model`** in the UI: index.html:942 becomes
`3 - Distill a static embedding model` (see also item 11's verb problem), the role label at
src/model-data-workbench.ts:648 becomes `Static embedding model`. The proto enum
`MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING` is already exactly right. Separately, fix the two
proto comments that use "static" for indexes.

Bonus finding on the same button. `Train model` (index.html:949) is the wrong verb.
Model2Vec's verb is **distill**, and our own UI already says "distill" in five other places
(index.html:491, :877, :888; src/vocabulary-trainer.ts:248, :254). The Workflows tab even
carries a footnote apologising for the word: `What "train" means here` ... "so a small
pasted corpus does not pretend to train language meaning from scratch"
(index.html:508-510). That footnote exists because the button is misleading. Rename the
button to **`Distill model`** (P1) and the footnote becomes unnecessary.

## 12. `compound query`

FACT. `Compound query builder` (index.html:606), `Compose semantic, term, and phrase
clauses under one join.` (index.html:608), `The compound query is invalid.`
(src/server-search-workbench.ts:242), and the `QueryNode` tree in opennlp_query.proto:63.

VERDICT: **STANDARD**.

Strongest precedent. Elasticsearch documents an entire section under this exact name:
"Compound queries wrap other compound or leaf queries, either to combine their results and
scores, to change their behaviour, or to switch from query to filter context."
(https://www.elastic.co/guide/en/elasticsearch/reference/current/compound-queries.html). OpenSearch
mirrors the same section name. MongoDB Atlas Search has a `compound` operator: "The
`compound` operator combines two or more operators into a single query. Each element of a
`compound` query is called a clause." Lucene itself says `BooleanQuery` rather than
"compound", but our `clause` vocabulary matches it exactly: "Return a list of the clauses of
this BooleanQuery."

OPINION. Keep it, unchanged. This is one of the best-chosen names in the UI.

One P2 addition: the builder is also a **hybrid search** builder, since it fuses vector and
keyword components (`SearchComponentKind`, opennlp_search.proto:508-517). Milvus and Qdrant
both use "hybrid" for exactly this. Saying so in the hint at index.html:608 would tell a
search engineer in one word what the panel is for.

## 13. `corpus search`

FACT. The tab label `Corpus search` (index.html:47), `How to use corpus search`
(index.html:566), `What should this corpus help you find?` (index.html:597), and inspector
facts `Corpus artifact` and `Corpus license` (src/server-search-workbench.ts:488, :504).
Wire: `SearchCorpusDescriptor` (opennlp_search.proto:546).

VERDICT: **STANDARD**, in the right ecosystem.

Strongest precedent. NLTK: "A text corpus is a large, structured collection of texts."
(https://www.nltk.org/book/ch02.html). TEI has a `teiCorpus` element: "contains the whole of
a TEI encoded corpus, comprising a single corpus header and one or more TEI elements"
(https://tei-c.org/release/doc/tei-p5-doc/en/html/CC.html). Apache OpenNLP's own manual has
a chapter titled **Corpora**. Honest note: this is **not** search-engine vocabulary. Neither
the Solr glossary nor the Elasticsearch glossary has an entry for "corpus"; Lucene's
`TFIDFSimilarity` javadoc says "collection-level", not "corpus-level".

OPINION. Keep. This is an NLP product built on OpenNLP, and the NLP word carries the right
meaning: a curated body of text with provenance and a license, which is exactly what
`SearchCorpusDescriptor` models (title, provenance summary, source URI, license, artifact
hash, opennlp_search.proto:546-561).

## 14. `workflow`

FACT. Tab `Workflows` (index.html:45), `Workflow name` (index.html:412),
`Build workflow and search` (index.html:469), `Configure the workflow` (index.html:429),
`The workflow did not complete.` (src/corpus-workflow.ts:235).

VERDICT: **STANDARD**.

Strongest precedent. DVC: "Pipelines represent data workflows that you want to reproduce
reliably" (https://doc.dvc.org/user-guide/pipelines/defining-pipelines), with the same
structure we have: a workflow made of **stages**, each with dependencies and outputs.
Argo Workflows and Apache Airflow use the word the same way.

OPINION. Keep. One adjustment: DVC calls the units **stages**, and our own UI calls them
`Pipeline stages` (index.html:479), which is right in the second word and wrong in the
first. Rename to **`Workflow steps`** or `Workflow stages` so the word "pipeline" is
reserved for the analysis pipeline. See `findings/code-vs-ui-consistency.md` M3.

## 15. `lifecycle`

FACT. Tab `Lifecycle` (index.html:55, :967).

VERDICT: **STANDARD**.

Strongest precedent. Elasticsearch has a feature by this name: "Index lifecycle management
(ILM) automates the management of time-based indices, such as logs and metrics."
(https://www.elastic.co/guide/en/elasticsearch/reference/current/index-lifecycle-management.html). MLflow's
Model Registry is "designed to collaboratively manage the full lifecycle of a machine
learning model" (https://mlflow.org/docs/latest/ml/model-registry/). Both senses, index
lifecycle and model lifecycle, are on this tab, so the name is doubly earned.

OPINION. Keep, unchanged. Best-named tab in the app.

## 16. `workbench`

FACT. Product subtitle `gRPC Workbench` (index.html:38), `Workbench navigation`
(index.html:41), `Document analysis workbench` (index.html:82), `OpenNLP workbench`
(index.html:66), and the module names `*-workbench.ts` throughout.

VERDICT: **STANDARD**, with real Apache precedent.

Strongest precedent. Weka, one of the best-known open source ML toolkits, ships a top-level
GUI literally named the **Workbench**, the integrated environment that combines all its
graphical interfaces into a single window, alongside the Explorer, the Experimenter and the
KnowledgeFlow. Apache UIMA ships one too: "The UIMA Ruta Workbench was created to facilitate
all steps in creating Analysis Engines based on the UIMA Ruta language."
(https://uima.apache.org/ruta.html). The counterexample is GATE, which
calls its equivalent **GATE Developer**, "a specialist tool similar in purpose and character
to a programmer's integrated development environment"
(https://gate.ac.uk/family/developer.html).

OPINION. Keep. The name is defensible, in-family for Apache, and it promises exactly what
the product delivers: everything in one window.

One P3 inconsistency: the skip link says `Skip to playground` (index.html:32) and the
section `aria-label` says `OpenNLP workbench` (index.html:66) while the section id is
`playground` (index.html:66) and the h1 id is `playground-heading` (index.html:82).
"Playground" and "workbench" promise different things. Change the skip link to
`Skip to workbench`.

## 17. `model bundle`

FACT. The service fact `Bundles` (index.html:90, element id `model-count`),
`Loaded model bundles` (index.html:192), `Available model bundles` (index.html:193),
`Loaded bundles` (index.html:824), `Discovering model bundles.` (index.html:826). Wire:
`ListModelBundles` (opennlp_service.proto:359), `/api/v1/model-bundles`
(GrpcJsonApi.java:130).

VERDICT: **INVENTED**. There is no standard noun "model bundle".

Strongest precedent, and it is an absence. ONNX: "A machine-learning model implemented with
ONNX is often referenced as an ONNX graph" (https://onnx.ai/onnx/intro/concepts.html).
TensorFlow: "A SavedModel contains a complete TensorFlow program"
(https://www.tensorflow.org/guide/saved_model); "bundle" appears there only as the C++
in-memory type `SavedModelBundle`, never as a name for the saved directory. MLflow says
**model artifact**. Hugging Face says **repository**. Apache OpenNLP's own manual says
**model file** and `.bin`, and uses "bundle" only as a verb, in the heading "Bundling a
custom trained OpenNLP model for the classpath".

Worse, "bundle" already means something else in this same UI: the immutable index bundle
(src/server-search-workbench.ts:146, :494; `SEARCH_PROVIDER_CAPABILITY_BUNDLE`,
opennlp_search.proto:434-435).

OPINION (P2). For the NLP model set, say **`Model set`** or simply **`Loaded models`**. The
bare counter `Bundles` at index.html:90 is the worst offender: it is a number with no noun a
newcomer can parse. Make it `Models`. Keep `ListModelBundles` on the wire. Wherever the
search-side meaning appears, always write **`index bundle`**, never bare `bundle`.

## 18. `catalog`

FACT. Three uses, one correct and two not. Correct: `Pinned model catalog`
(index.html:832), `/api/v1/model-catalog` (GrpcJsonApi.java:184), `ListModelCatalog`
(opennlp_training.proto:118), `Loading the standard model catalog.` (index.html:841).
Incorrect: `Loading the trainer catalog.` (index.html:900; src/vocabulary-trainer.ts:173)
and `Loading the lifecycle catalog.` (index.html:996; src/lifecycle-workbench.ts:139),
neither of which names a catalog of anything.

VERDICT: **STANDARD** for the model catalog, **INVENTED** for the other two.

Strongest precedent. Four live words in this space and they are not synonyms.
**Registry** is your own versioned, governed store: MLflow's is "a centralized model store,
set of APIs and a UI designed to collaboratively manage the full lifecycle of a machine
learning model" (https://mlflow.org/docs/latest/ml/model-registry/). **Hub** is the shared
public site: Hugging Face Hub. **Zoo** is a curated collection of pretrained models: the
repository titles itself the "ONNX Model Zoo", "A collection of pre-trained,
state-of-the-art models in the ONNX format" (https://github.com/onnx/models).
**Catalog** is the browse-and-pick surface over models you did not train: "The model catalog
is organized into two main categories"
(https://learn.microsoft.com/en-us/azure/ai-foundry/concepts/foundry-models-overview).

Ours is a curated, pinned, checksum-verified list of third-party models you download but do
not own. That is a **catalog**, exactly.

OPINION (P3). Keep `Pinned model catalog`. Fix the two misuses: index.html:900 becomes
`Loading dictionaries, vocabularies, and teacher models.` and index.html:996 becomes
`Loading indexes, aliases, and collections.` (matching source at
src/vocabulary-trainer.ts:173 and src/lifecycle-workbench.ts:139). Note for later: if the
Trainer ever versions its own distilled models, the word for that store is **registry**, not
catalog.

## 19. `role`

FACT. `ModelArtifactRole` (opennlp_training.proto:76), rendered by `roleLabel`
(src/model-data-workbench.ts:646-662) into `Ready-to-serve static table`,
`Training teacher`, `Constituency parser`, `Syntactic chunker`, `Sentence detector`,
`Tokenizer`, `POS tagger`, `Lemmatizer`, and shown as a small badge beside each catalog
entry (src/model-data-workbench.ts:291).

VERDICT: **VARIANT**.

Strongest precedent. Hugging Face and spaCy classify a model by its **task**
(huggingface.co/tasks lists "Token Classification", "Text Classification", "Feature
Extraction"). MLflow uses **flavor** for the serving format. spaCy uses **component** for
the pipeline slot a model fills. "Role" is used in NLP for something else entirely:
**semantic role** labelling, which ISO 24617 Part 4 is devoted to.

OPINION (P3). The wire name `ModelArtifactRole` is fine. The user-facing badge should say
what the model is **used for**, so label the column **`Used for`** or **`Model kind`**
(src/model-data-workbench.ts:291). Avoid "role" in UI copy on a product that also does
semantic annotation.

## 20. `layer`

FACT. Result summary `Layers` (index.html:279), `Annotation layers` (index.html:291),
`Filter annotation layers` (index.html:288), `Analyze text to discover layers`
(index.html:287), `Select a hit to inspect typed layers.` (index.html:714). Wire:
`StandardLayer` with 22 values (opennlp_document.proto:707-756), `AnnotationLayer`
(opennlp_document.proto:784), `DocumentLayers` (opennlp_document.proto:699).

VERDICT: **STANDARD (weak)**. Real precedent, but only in one product family.

Strongest precedent. INCEpTION and WebAnno both model annotation as **annotation layers**,
with span layers, relation layers and chain layers
(https://inception-project.github.io/releases/32.1/docs/user-guide.html). Against that:
**UIMA has no layer at all.** Its kind axis is the **type** in the **type system** ("a schema
or class model for the CAS ... It defines the types of objects and their properties")
and its parallel-text axis is the **view** / **Sofa** ("Sofa stands for Subject of Analysis")
(https://uima.apache.org/d/uimaj-current/oas.html). GATE says **annotation set**. TEI groups
with `<spanGrp>`. CoNLL formats just have named **columns** (CoNLL-2003 keeps `pos_tags`,
`chunk_tags` and `ner_tags` as separate columns over the same tokens).

OPINION (P3). Keep `layer`. It is the clearest available word for the thing, it maps
cleanly onto CoNLL's parallel columns, and it has a shipped precedent. But it needs one
definition, once, in the Analyze tab help: "A layer is a named set of annotations of one
kind, for example tokens or named entities." Without it, a reader arriving from UIMA or
Lucene has no anchor.

## 21. `resolved route`

FACT. The user-facing labels are `Configured embedding route` and `Query embedding route`
(src/server-search-workbench.ts:477, :478), rendered as
`<backend> / <model> - <vector space>` (src/server-search-workbench.ts:618, :624). The word
"resolved" appears in the proto: "Actual route used to embed the query after route
resolution" (opennlp_search.proto:625) and "The response descriptor always carries the
resolved index id" (opennlp_search.proto:595).

VERDICT: **PRODUCT**.

Strongest precedent. "Routing" is standard in model serving and in Elasticsearch (which uses
`routing` for shard selection and "resolve" for alias resolution). `EmbeddingRoute` itself,
carrying model id, backend id, vector space id, artifact hash, priority and a primary flag
(opennlp_document.proto:540-554), has no equivalent in any product surveyed. It is a
genuinely new and useful thing.

OPINION (P3). Keep the concept and the wire name. In UI copy, say **`Route actually used`**
rather than "resolved route": a user reads "resolved" as "a problem was fixed", where the
intent is "after fallback, this is what ran". The existing pair
`Configured embedding route` / `Query embedding route` is already good; add a one-line
definition in the inspector: "which model, on which backend, produced these vectors".

## 22. `vector space`

FACT. Fact label `Vector space` in two inspectors (src/annotation-drawer.ts:234;
src/lifecycle-workbench.ts:178), and rendered inline after the model id
(src/server-search-workbench.ts:618). Wire: `EmbeddingRoute.vector_space_id`, documented as
"Stable identity of the vector space, including weights, tokenization, pooling, and
normalization semantics" (opennlp_document.proto:545-547).

VERDICT: **VARIANT**.

Strongest precedent, and it is a near-miss. The **vector space model** is classic IR
vocabulary (Salton), and every vector database talks about vectors, dimensions and distance
metrics. But none of them has an identity token for "these two sets of vectors are
comparable". What we call a vector space is a **compatibility fingerprint**, and the closest
real analogue is Milvus's requirement that query and index vectors share a metric type and
dimension, or Elasticsearch's `dense_vector` `dims` plus `similarity`. Showing a user a long
opaque id next to the words "Vector space" invites them to read it as a mathematical object.

OPINION (P2). Label it **`Embedding space id`**, or plainer, **`Compatibility id`**, in the
three UI places (src/annotation-drawer.ts:234; src/lifecycle-workbench.ts:178;
src/server-search-workbench.ts:618). Keep `vector_space_id` on the wire, where the doc
comment already explains it correctly.

## 23. `chunk`

FACT. Segmentation sense: result tab `Chunks` (index.html:270), fieldset `Chunk output`
(index.html:166), `Sentence chunks` and `Token windows` (index.html:170, :174),
`Indexed chunks` (index.html:738), `Score chunks` (index.html:336), `Indexed chunk`
(index.html:698), inspector `<dt>` `Chunks` (index.html:678). Shallow-parsing sense:
`Syntactic chunks` (src/analysis-config.ts:59), `Syntactic chunker`
(src/model-data-workbench.ts:655), `PIPELINE_STEP_SYNTACTIC_CHUNK`
(opennlp_pipeline.proto:70).

VERDICT: **STANDARD** in the segmentation sense, but the collision is the single biggest
vocabulary hazard in this product.

Strongest precedent for keeping it. Pinecone's guide is titled "Chunking Strategies" and
defines it as "the process of breaking down large text into smaller segments called chunks"
(https://www.pinecone.io/learn/chunking-strategies/). LangChain uses `chunk_size` and
`chunk_overlap`. LlamaIndex defines a Node as "a specific chunk of the parent document"
(https://developers.llamaindex.ai/python/framework/module_guides/loading/node_parsers/).

Strongest precedent for the collision. OpenNLP's own manual: "Text chunking consists of
dividing a text in syntactically correlated parts of words, like noun groups, verb groups,
but does not specify their internal structure"
(https://opennlp.apache.org/docs/2.5.4/manual/opennlp.html). NLTK: "chunking, which segments
and labels multi-token sequences" (https://www.nltk.org/book/ch07.html). The CoNLL-2000
shared task is named **Chunking**, and CoNLL-2003 keeps `chunk_tags` and `ner_tags` as
separate columns, which is the sharpest proof that a chunk in this tradition is a syntactic
phrase.

Note the escape hatch the retrieval ecosystem gives us: its own class names say **split** and
**splitter** (`RecursiveCharacterTextSplitter`, `SentenceSplitter`), never "chunker". So the
verb side is free even though the noun side is taken.

OPINION (P1). Keep `chunk` for segmentation, because that battle is lost and won in our
favour. Never let the bare word stand for shallow parsing. Rename
`Syntactic chunks` to **`Shallow parse (phrase chunks)`** (src/analysis-config.ts:59) and
`Syntactic chunker` to **`Shallow parser`** (src/model-data-workbench.ts:655). Qualify the
inspector `<dt>` at index.html:678 as `Indexed chunks`. Add one sentence to the Analyze help:
"Chunks here are passages for embedding, not the phrase chunks a syntactic chunker produces."

## 24. `projection`

FACT. `Chunk projections` (index.html:310), the select label `Chunk projection`
(index.html:328), the option `All projections, separate lanes` (index.html:330, repeated at
src/semantic-workbench.ts:504), the canvas hint "choose one layer to isolate that
projection" (index.html:295-296), and the module names `chunk-projection.ts` /
`chunk-projection-view.ts`.

VERDICT: **INVENTED**, and it collides with a settled meaning.

Strongest precedent, and it is against us. In this field, a projection is the
low-dimensional view of high-dimensional vectors produced by PCA, t-SNE or UMAP. UMAP's own
tutorial labels its plots "UMAP projection of the Penguin dataset"
(https://umap-learn.readthedocs.io/en/latest/basic_usage.html), and TensorBoard ships a tool
called the **Embedding Projector**. A user who knows embeddings will click `Chunk
projections` expecting a scatter plot and get a list of chunk columns.

The collision is worse here than elsewhere because this product genuinely has PCA
(`PCA dimensions`, index.html:461, :947), so a real projection is one feature away.

OPINION (P1). Rename to **`Chunk set`**. `Chunk projections` (index.html:310) becomes
`Chunk sets`, the picker label (index.html:328) becomes `Chunk set`, and the option
(index.html:330; src/semantic-workbench.ts:504) becomes
`All chunk sets, separate lanes`. Also fix the canvas hint at index.html:295-296, which uses
"projection" for a third, unrelated thing (a single annotation layer's overlay): say "choose
one layer to isolate it".

## 25. `heatmap`

FACT. Result tab `Heatmap` (index.html:272), `Document heatmap` (index.html:319),
`Search heatmap` (index.html:524), `Document score heatmap` (index.html:344), the mode
buttons `Query similarity` / `Sentiment` (index.html:324, :325), and the status strings in
src/semantic-workbench.ts:452-539.

VERDICT: **STANDARD**.

Strongest precedent. Heatmap is universal data-visualization vocabulary and needs no
citation from a specific vendor. The specific use here, shading text by a per-segment score,
is the same idea as attention and saliency heatmaps over text, which is a well-established
presentation in the interpretability literature.

OPINION. Keep, unchanged. The accompanying `Similarity score color scale` legend
(index.html:558-561) with its fixed minus-one-to-plus-one scale is exactly what a heatmap
needs and is one of the strongest pieces of the UI.

## 26. `X-ray`

FACT. Checkbox label `Normalization X-ray` (index.html:220), the section `aria-label`
`Normalization X-ray` (index.html:304), and the panel title
(src/normalization-xray.ts:91).

VERDICT: **INVENTED**.

Strongest precedent, and it points to a better name. Both major search engines ship this exact
feature and neither uses a metaphor. Elasticsearch: "The analyze API performs analysis on a
text string and returns the resulting tokens", at the `_analyze` endpoint, with an `explain`
flag that "will output all token attributes for each token"
(https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-analyze.html), plus a
task page titled "Test an analyzer". Solr's Admin UI has an **Analysis screen**: "you should
test it out to make sure that it behaves the way you expect it to", displaying "a simple
output of only the tokens produced by each step of analysis"
(https://solr.apache.org/guide/solr/latest/indexing-guide/analysis-screen.html). So the family
words are **analyze**, **test**, and **explain**, and a user searching our docs for "X-ray"
will find nothing that matches their mental model.

OPINION (P2). Rename to **`Normalization trace`** (index.html:220, :304;
src/normalization-xray.ts:91), or `Show each normalizer` if you want a verb. If the lead
prefers to keep "X-ray" for memorability, then the help text must contain the sentence "this
is the equivalent of an analyze or explain view" so the feature is findable by the words
people actually search for.

## 27. `shape`

FACT. Response label `Document shape` (index.html:248), graph `aria-label`
`Document shape graph` (index.html:360), help text "Sentiment reads the current document
shape" (index.html:320), "with a document-shaped query" (index.html:580), the module
`src/document-shape.ts`, the proto section banner
"============================ The document shape ============================"
(opennlp_document.proto:641), and README.md:26 "## The document shape".

VERDICT: **INVENTED**, but consistently so.

Strongest precedent, and it is an absence. spaCy calls the object a **Doc**. UIMA calls it a
**CAS**, the Common Analysis Structure. CoreNLP calls it an **Annotation**. GATE calls it a
**document** with **annotation sets**. Nobody says "shape". In data engineering, "shape"
means the dimensions of an array (`numpy.shape`), which is an active competing meaning in a
product that also deals in vectors and dimensions.

The mitigating fact: the term is used identically in the UI, the proto and the README, so it
is a real, coherent product term rather than a slip.

OPINION (P2). Rename in the UI only, to **`Analyzed document`**: the response label at
index.html:248, the graph aria-label at index.html:360, and the help text at index.html:320.
Keep "document shape" in the proto section banner and the README, where it names a schema
rather than a thing on screen, and where renaming would churn documentation for no user
benefit. If the lead prefers one word everywhere, the honest alternative is to keep "document
shape" and define it in the first sentence of the Analyze help callout (index.html:96-105),
which currently never explains it.

---

## What to change first

If only five changes are made, make these:

1. `workspace` becomes `live index`, everywhere (item 3). Eight names collapse to one.
2. `Save checkpoint` becomes `Save to disk` (item 5). Removes a direct collision with model
   weights on a page that lists model artifacts.
3. `Vocabulary drift` becomes `Vocabulary coverage` (item 8). The UI already uses the right
   words two lines below.
4. `Chunk projections` becomes `Chunk sets` (item 24), and `Syntactic chunks` becomes
   `Shallow parse (phrase chunks)` (item 23). Two renames, one collision resolved each.
5. `Train model` becomes `Distill model` (item 11). Deletes the footnote that currently
   apologises for the word.
