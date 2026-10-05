# Raw user-visible vocabulary inventory

FACT. Every string below is user-visible today: a heading, label, button, option,
`aria-label`, `placeholder`, help paragraph, or a status/error message rendered at
runtime. Cited as `path:line`. Extracted 2026-08-28 from
`opennlp-grpc-webapp-default/index.html`, `opennlp-grpc-webapp-default/src/*.ts`, and
the protos under `opennlp-grpc-api/src/main/proto/org/apache/opennlp/grpc/v1/`.

This file is the input for `findings/glossary.md`. It records what is said, not
whether it is right.

## 0. Global chrome

| String | Kind | Where |
| --- | --- | --- |
| `gRPC Workbench` | product subtitle | opennlp-grpc-webapp-default/index.html:38 |
| `Workbench navigation` | aria-label on the tab strip | opennlp-grpc-webapp-default/index.html:41 |
| `Analyze` | tab | opennlp-grpc-webapp-default/index.html:43 |
| `Workflows` | tab | opennlp-grpc-webapp-default/index.html:45 |
| `Corpus search` | tab | opennlp-grpc-webapp-default/index.html:47 |
| `Workspace search` | tab (panel id is `session-search`) | opennlp-grpc-webapp-default/index.html:49 |
| `Models & data` | tab | opennlp-grpc-webapp-default/index.html:51 |
| `Trainer` | tab | opennlp-grpc-webapp-default/index.html:53 |
| `Lifecycle` | tab | opennlp-grpc-webapp-default/index.html:55 |
| `Skip to playground` | skip link | opennlp-grpc-webapp-default/index.html:32 |
| `OpenNLP workbench` | aria-label on the whole playground | opennlp-grpc-webapp-default/index.html:66 |
| `OpenNLP tools` / `Default extension` | extension switcher | opennlp-grpc-webapp-default/index.html:67, :70 |
| `Discovering UI extensions.` | status | opennlp-grpc-webapp-default/index.html:74 |
| `Connecting` | service status | opennlp-grpc-webapp-default/index.html:61 |
| `Offline` / `The web interface is running, but the analysis service could not be reached.` | status | opennlp-grpc-webapp-default/src/main.ts:423, :424 |

## 1. Analyze tab (panel `analysis-workbench`)

### Headings and facts
| String | Where |
| --- | --- |
| `Document analysis workbench` (h1) | index.html:82 |
| `Service` / `Profiles` / `Bundles` / `Languages` (dt) | index.html:88, :89, :90, :91 |
| `How to use the analyzer` (summary) | index.html:96 |
| `Document shape` (response label) | index.html:248 |
| `Analysis result` (h3) | index.html:249 |
| `Layers` / `Annotations` / `Offsets` (dt) | index.html:279, :280, :281 |
| `Typed annotation` / `Selection details` (drawer) | index.html:374, :375 |

### Form vocabulary
| String | Where |
| --- | --- |
| `Document text` (label), placeholder `Paste a paragraph, article, contract, or other document here.` | index.html:118, :122 |
| `Feature preset` (label) with options `All available features`, `Choose features`, `Server automatic` | index.html:128, :130-132 |
| `Embedding model` (label); `No embedding model configured` | index.html:139, :141 |
| `Language pipeline` (label); `Automatic (route by detected language)` | index.html:146, :148 |
| `Installed language model sets` (help) | index.html:150 |
| `Part-of-speech tag set`; `Model native`, `Universal Dependencies (UD)`, `Penn Treebank` | index.html:154, :156-158 |
| `Chunk output` (legend); `Return either strategy or both in the same response.` | index.html:166, :167 |
| `Sentence chunks` / `One chunk per detected sentence` | index.html:170 |
| `Token windows` / `Overlapping windows across the document` | index.html:174 |
| `Window (tokens)` / `Overlap (tokens)` | index.html:177, :179 |
| `Enabled features` / `Loaded model bundles` / `Available model bundles` | index.html:186, :192, :193 |
| `Analysis features` (legend); `Required backbone steps are added automatically.` | index.html:201, :202 |
| `Analyze text` (submit) | index.html:210 |
| `Use short sample` / `Load Alice novel` / `Load Pride and Prejudice` | index.html:214-216 |
| `Normalization X-ray` (checkbox label) | index.html:220 |
| `Batch analyze (streaming)` (summary); `Batch documents` | index.html:229, :235 |
| `Add to server workspace` (button) | index.html:252 |
| `Copy JSON` / `Download JSON` / `Download .pb` / `Open saved response` | index.html:253-256 |

### Result view names
| String | Where |
| --- | --- |
| `Document`, `Chunks`, `Heatmap`, `Graph`, `Protobuf JSON` (result tabs) | index.html:268, :270, :272, :274, :276 |
| `Filter annotation layers` / `Filter layers` / `Annotation layers` | index.html:288, :289, :291 |
| `Analyze text to discover layers` | index.html:287 |
| `Complete document` (window slider label) | index.html:299 |
| `Chunk projections` / `Compare every requested strategy over the same document` | index.html:310 |
| `Each column preserves its typed group identity, source span, and attached embedding count.` | index.html:311 |
| `Document heatmap` / `Shade the same document by query similarity or by sentiment` | index.html:319 |
| `Query similarity` / `Sentiment` (heatmap mode buttons) | index.html:324, :325 |
| `Chunk projection` (select label); `All projections, separate lanes` | index.html:328, :330 |
| `Find related chunks in this document` / `Score chunks` | index.html:332, :336 |
| `Document graph` / `Document, layer, and annotation relationships` | index.html:351 |
| `Document shape graph` (aria-label); `Show complete graph` | index.html:360, :355 |
| `Show balanced overview` / `Complete graph limited for large documents` | src/semantic-workbench.ts:579, :580 |
| `Document root` (graph node) | src/semantic-workbench.ts:596 |

### Feature (pipeline step) display names
`opennlp-grpc-webapp-default/src/analysis-config.ts:43-61`:
`Language detection`, `Offset-aware normalization`, `Sentence detection`,
`Tokenization`, `Subword tokenization`, `Named entities`, `Entity geocoding`,
`Part-of-speech tags`, `Lemmas`, `Stems`, `Term vectors`, `Lexical expansion`,
`Document categories`, `Sentence sentiment`, `Constituency parses`,
`Syntactic chunks`, `Document embeddings`.

### Chunking strategy display names
`opennlp-grpc-webapp-default/src/chunk-projection.ts:44-47`:
`Sentence`, `Token window`, `Semantic`, `Category`.

### Offset encoding display names
`opennlp-grpc-webapp-default/src/document-shape.ts:326-339`:
`UTF-16`, `Unicode code points`, `UTF-8 bytes`, `Not reported`.

### Normalization X-ray strings
| String | Where |
| --- | --- |
| `Normalization X-ray` (panel title) | src/normalization-xray.ts:91 |
| `N alignment run(s), N changed` | src/normalization-xray.ts:95 |
| `Applied normalizers` / `No normalizers reported` | src/normalization-xray.ts:100, :104 |
| `Run N, <state>` (segment title) | src/normalization-xray.ts:158 |

## 2. Workflows tab (panel `workflows-workbench`)

| String | Where |
| --- | --- |
| `Text to searchable knowledge` (kicker) | index.html:388 |
| `Build and explore in one flow` (h3) | index.html:389 |
| `analyze it, learn its vocabulary, distill a static embedding model, build a live index, and search it` | index.html:391-392 |
| `Automatic defaults` (badge) | index.html:395 |
| `Your text collection` (h4) | index.html:402 |
| `Documents (blank line separates documents)` | index.html:404 |
| `Workflow name`, default value `My text workflow` | index.html:412, :413 |
| `First search` / `Search again` | index.html:416, :422 |
| `Configure the workflow` / `Dictionary, teacher, vector storage, and limits` | index.html:429 |
| `Vocabulary source`; `Corpus terms only (default)` | index.html:434, :436 |
| `Pair the pasted corpus with a larger imported dictionary to guarantee its headwords.` | index.html:438 |
| `Embedding teacher`; `Discovering teachers` | index.html:441, :443 |
| `The selected teacher supplies semantic vectors for the learned vocabulary.` | index.html:445 |
| `Vector storage`; `Exact flat float` | index.html:448, :450 |
| `Min term frequency` / `Max corpus terms` / `PCA dimensions` | index.html:455, :458, :461 |
| `Build workflow and search` | index.html:469 |
| `Pipeline stages` (h4) | index.html:479 |
| Stage names: `Analyze text`, `Learn vocabulary`, `Distill embeddings`, `Embed documents`, `Build live index`, `Search and visualize` | index.html:483, :487, :491, :495, :499, :503 |
| `What "train" means here` and its explanation | index.html:508-510 |
| `Analysis and search` (h4); `No artifacts built yet.` | index.html:517, :518 |
| `Analysis` / `Search heatmap` (result tabs) | index.html:522, :524 |
| `Similarity score color scale` | index.html:532 |
| Runtime stage captions: `Learning terms from the pasted corpus`, `Starting teacher distillation`, `Embedding analyzed sentence chunks`, `Publishing a live workspace index`, `Embedding and searching the new query` | src/corpus-workflow.ts:188, :202, :213, :217, :250 |
| `Learned from text pasted into the Workflows workbench` (provenance) | src/corpus-workflow.ts:196 |
| `Distilled through the Workflows workbench` (provenance) | src/corpus-workflow.ts:208 |
| `Training is unavailable because this server has no writable artifact root or teacher model.` | src/corpus-workflow.ts:156 |
| `Embedded analysis returned no indexable chunk groups.` | src/corpus-workflow.ts:520 |
| `TurboQuant (quantized)` / `Exact flat float` | src/corpus-workflow.ts:569-570 |

## 3. Corpus search tab (panel `server-search`, tab id `corpus-search`)

| String | Where |
| --- | --- |
| `Server-backed semantic search` (kicker) | index.html:549 |
| `Explore an immutable index` (h3) | index.html:550 |
| `Search a configured corpus, compare transformed chunks with their authoritative source spans` | index.html:551 |
| `This tab searches read-only indexes an operator configured or persisted.` | index.html:553 |
| `less similar` / `cosine score` / `more similar` (legend) | index.html:561 |
| `How to use corpus search` (summary) | index.html:566 |
| `its size, dimensions, and provenance appear beside it` | index.html:568 |
| `ranks every candidate chunk by cosine similarity` | index.html:569-570 |
| `Open a hit to compare the indexed (normalized) chunk with its original source span` | index.html:573-574 |
| `Search index` (label); `Discovering configured indexes` | index.html:587, :589 |
| `build your own workspace index` (jump link) | index.html:592 |
| `Natural-language query`; placeholder `What should this corpus help you find?` | index.html:595, :597 |
| `Results` (top-k label, input `server-search-top-k`) | index.html:600 |
| `Search index` (submit button) | index.html:603 |
| `Compound query builder` (summary) | index.html:606 |
| `Compose semantic, term, and phrase clauses under one join.` | index.html:608 |
| `Clause kind`; options `Semantic`, `Term`, `Phrase` | index.html:613, :615-617 |
| `Clause text` / `Term match mode` / `Match any term` / `Match all terms` | index.html:619-624 |
| `Phrase slop` | index.html:626 |
| `Add clause` / `Clear clauses` | index.html:628, :638 |
| `Join`; `All clauses (AND, mean score)`, `Any clause (OR, maximum score)`, `Any clause, reciprocal-rank fusion` | index.html:632, :634-636 |
| `Ranked evidence` / `Search results` | index.html:649 |
| `List` / `Heatmap` (view toggle); `0 hits` | index.html:652, :653, :655 |
| `Selected evidence` / `Result inspector` | index.html:670 |
| `similarity` (score caption) | index.html:671 |
| `Sentences` / `Tokens` / `Entities` / `Chunks` / `Terms` (dt) | index.html:675-679 |
| `Authoritative source`; `Selected span is painted with its fixed score color.` | index.html:684, :685 |
| `Original source span` / `Indexed chunk` | index.html:694, :698 |
| `Identity and provenance` (h5) | index.html:706 |
| `Annotations covering this chunk` (h5) | index.html:712 |
| `An operator must configure an immutable index bundle at startup.` | src/server-search-workbench.ts:146 |
| Inspector fact names `Source offsets`, `Search provider`, `Configured embedding route`, `Query embedding route`, `Model artifact`, `Query model artifact`, `Corpus artifact`, `Bundle format`, `Index builder`, `Preparation config`, `Corpus license` | src/server-search-workbench.ts:473-506 |
| `None (keyword-only query)` | src/server-search-workbench.ts:625 |
| `Only the requested top results are shaded.` | src/server-search-workbench.ts:362 |
| `The compound query is invalid.` | src/server-search-workbench.ts:242 |

## 4. Workspace search tab (panel `session-search`, tab label `Workspace search`)

| String | Where |
| --- | --- |
| `Dynamic gRPC search` (kicker) | index.html:725 |
| `Server memory` (badge) | index.html:726 |
| `On-the-fly workspace index` (h3) | index.html:727 |
| `let the gRPC service build and search a bounded in-memory index, stored exact (flat float) or quantized (TurboQuant)` | index.html:729-731 |
| `This tab searches dynamic workspaces held in server memory.` | index.html:733 |
| `Indexed chunks` / `Similarity` (`Cosine`) / `Storage` (`gRPC server memory`) | index.html:738-740 |
| `Workspace to search`; `New workspace (created on first add)` | index.html:742, :744 |
| `Vector storage for the next new index`; `Flat float (exact)`, `TurboQuant (quantized)` | index.html:746, :748, :749 |
| `How to use workspace search` (summary) | index.html:752 |
| `The first add creates a new workspace index in server memory.` | index.html:756-757 |
| `To keep a workspace across restarts, or to seal, alias, or rebuild it, use the Lifecycle tab.` | index.html:760-761 |
| `Semantic query` | index.html:773 |
| `Search workspace` / `Clear workspace index` | index.html:777, :778 |
| `No workspace search results yet.` | index.html:785 |
| `Detached. The next add creates a new workspace index.` | src/semantic-workbench.ts:211 |
| `The selected workspace no longer exists on the server.` | src/semantic-workbench.ts:219 |
| `This result has no indexed chunk embeddings. Select an embedding model and chunk strategy.` | src/semantic-workbench.ts:246 |
| `Sending the analyzed document shape to the gRPC workspace index.` | src/semantic-workbench.ts:250 |
| `The gRPC server deleted the workspace index.` | src/semantic-workbench.ts:277 |
| `No server workspace is available for this query.` | src/semantic-workbench.ts:305 |
| `Workspace query failed.` | src/semantic-workbench.ts:317 |
| `No compatible vectors were found in the server workspace.` | src/semantic-workbench.ts:408 |
| `Workbench index` (display name sent for the heatmap index) | src/semantic-workbench.ts:331 |
| `Current document heatmap:` (index name prefix) | src/semantic-workbench.ts:88 |

## 5. Models & data tab (panel `model-data-workbench`)

| String | Where |
| --- | --- |
| `Server capability inventory` (kicker) | index.html:794 |
| `Models & data` (h3) | index.html:795 |
| `what still needs an operator-provided resource` | index.html:796 |
| `Pipeline readiness` (h4); `ready means it runs now` | index.html:818, :803 |
| `Loaded bundles` (h4); `Discovering model bundles.` | index.html:824, :826 |
| `Pinned model catalog` (kicker) | index.html:832 |
| `Download verified models to this server node` (h4) | index.html:833 |
| `Static tables and teacher models become available immediately.` | index.html:835 |
| `Downloaded on this node` (h5) | index.html:844 |
| `Verified resource installer` / `Download before server startup` | index.html:852, :853 |
| `N of M features ready` | src/model-data-workbench.ts:266 |
| Role labels `Ready-to-serve static table`, `Training teacher`, `Constituency parser`, `Syntactic chunker`, `Sentence detector`, `Tokenizer`, `POS tagger`, `Lemmatizer` | src/model-data-workbench.ts:646-662 |
| Install labels `Download and activate`, `Download teacher`, `Download parser`, ... | src/model-data-workbench.ts:664-672 |
| `Model card` (catalog link text) | src/model-data-workbench.ts:305, :400 |
| `<language> language pack` / `Classic pipeline` | src/model-data-workbench.ts:366, :369 |
| `Artifact hash unavailable` | src/model-data-workbench.ts:220 |
| `No catalog models have been downloaded to this node.` | src/model-data-workbench.ts:198 |
| `This build does not publish a standard model catalog.` | src/model-data-workbench.ts:347 |
| `I reviewed <license> and approve this node download.` | src/model-data-workbench.ts:330 |

## 6. Trainer tab (panel `vocabulary-trainer`)

| String | Where |
| --- | --- |
| `Vocabulary to model` (kicker) | index.html:873 |
| `Trainer` (h3) | index.html:874 |
| `distill a configured teacher into a static embedding model` | index.html:877 |
| `its headwords become guaranteed terms of every vocabulary learned afterwards` | index.html:884-885 |
| `the minimum frequency controls which corpus terms survive` | index.html:886-887 |
| `Distill the vocabulary against an operator-approved teacher model.` | index.html:888 |
| `1 · Import a dictionary` (h4) | index.html:904 |
| `Format` / `Display name` / `Dictionary file` / `Import dictionary` / `Imported dictionaries` | index.html:905-913 |
| `2 · Learn a vocabulary` (h4) | index.html:916 |
| `Corpus documents (blank line separates documents)` | index.html:917 |
| `Min frequency` / `Max terms` / `Learn vocabulary` / `Learned vocabularies` / `Download TSV` | index.html:927-939 |
| `3 · Train a static model` (h4) | index.html:942 |
| `Teacher` (label) | index.html:943 |
| `PCA dimensions (0 = server default)` | index.html:947 |
| `Train model` | index.html:949 |
| `Trained models on this server` (h4) | index.html:954 |
| `A trained model serves under its artifact id.` | index.html:956 |
| `No teachers are configured; add training.teacher entries.` | src/vocabulary-trainer.ts:165 |
| `Training is disabled: the server has no vocabulary artifact root or no teachers.` | src/vocabulary-trainer.ts:161 |
| `The server is learning the vocabulary.` | src/vocabulary-trainer.ts:208 |
| `The server is distilling the static model.` | src/vocabulary-trainer.ts:248 |
| `Learned through the trainer workbench` (provenance) | src/vocabulary-trainer.ts:215 |
| `Distilled through the trainer workbench` (provenance) | src/vocabulary-trainer.ts:254 |
| `Use in Analyze` | src/vocabulary-trainer.ts:298 |
| Defaults `Trainer vocabulary`, `Trainer static model` | src/vocabulary-trainer.ts:207, :246 |

## 7. Lifecycle tab (panel `lifecycle-workbench`)

| String | Where |
| --- | --- |
| `Save, watch drift, retrain, rebuild` (kicker) | index.html:966 |
| `Lifecycle` (h3) | index.html:967 |
| `Checkpoint and seal dynamic workspaces, point logical aliases at the current index` | index.html:969-970 |
| `group workspaces into collections whose vocabulary drift the server streams live` | index.html:971-972 |
| `Save a checkpoint to keep a dynamic workspace across server restarts, or seal it to make it read-only.` | index.html:979-980 |
| `blue/green style` | index.html:982 |
| `the stream reports new terms that fall outside the current vocabulary artifact` | index.html:986 |
| `Workspaces` (h4) | index.html:1000 |
| `Checkpoint and seal` (h5) | index.html:1002 |
| `Dynamic workspace` (label) | index.html:1003 |
| `Save checkpoint` (button) | index.html:1007 |
| `Seal as read-only` (button) | index.html:1008-1009 |
| `Save checkpoint writes the workspace to disk, and it keeps accepting new documents.` | index.html:1012-1013 |
| `Seal as read-only ... makes it permanently read-only` | index.html:1014-1015 |
| `Aliases` (h5); `Alias name`; `Point alias at workspace` | index.html:1020, :1022, :1024 |
| `Rebuild with a new model` (h5) | index.html:1031 |
| `a blue/green swap` | index.html:1035 |
| `1 · Pick the new embedding model`, `2 · Pick vector storage`, `3 · Optional alias to switch` | index.html:1037, :1042, :1049 |
| `Rebuild index` (button) | index.html:1053 |
| `Provider instances` (h5) | index.html:1058 |
| `Collections` (h4); `Collection`; `Collection id`; `Member workspaces` | index.html:1063, :1064, :1068, :1076 |
| `Dictionary artifact id` / `Vocabulary artifact id` / `Serving model artifact` | index.html:1080, :1084, :1088 |
| `Report vocabulary drift after this many new terms`; `Leave this at 0 to never report drift.` | index.html:1091, :1094 |
| `Vocabulary drift` (h5); `Vocabulary coverage` (aria-label) | index.html:1100, :1101 |
| `Out-of-vocabulary terms` (h5) | index.html:1106 |
| `Watch stream` (h5); `Not watching a collection.` | index.html:1108, :1109 |
| `No dynamic workspaces` | src/lifecycle-workbench.ts:148 |
| `Index documents in Workspace search to create a dynamic workspace first.` | src/lifecycle-workbench.ts:136 |
| `Keep the current vector storage` | src/lifecycle-workbench.ts:189 |
| `No trained model yet: distill one on the Trainer tab` | src/lifecycle-workbench.ts:238 |
| Drift fact names `Distinct terms`, `New occurrences`, `Analysis chain` | src/lifecycle-workbench.ts:488-492 |
| `No vocabulary artifact is configured; every indexed term counts as new.` | src/lifecycle-workbench.ts:497 |
| `In the current vocabulary` / `Out of the current vocabulary` (term chip titles) | src/lifecycle-workbench.ts:507 |
| `Watching '<id>'. Snapshot received.` | src/lifecycle-workbench.ts:465 |
| Provider capability names rendered raw and lowercased: `vector`, `keyword`, `live`, `bundle`, `persistent` | src/lifecycle-workbench.ts:196 joined from src/search-adapter.ts:161-167 |

## 8. Proto vocabulary a user meets through the Protobuf JSON view

The Analyze tab's `Protobuf JSON` tab (index.html:276) prints the raw response, so
every enum constant below is user-visible text.

- `PipelineStep`, opennlp_pipeline.proto:34-111: `PIPELINE_STEP_LANGUAGE_DETECT`,
  `_SENTENCE_DETECT`, `_TOKENIZE`, `_POS_TAG`, `_NER`, `_CHUNK`, `_PARSE`,
  `_LEMMATIZE`, `_DOC_CATEGORIZE`, `_SENTIMENT`, `_EMBED`, `_SYNTACTIC_CHUNK`,
  `_NORMALIZE`, `_SUBWORD_TOKENIZE`, `_STEM`, `_EXPAND`, `_GEOCODE`, `_TERM_VECTOR`.
- `StandardLayer`, opennlp_document.proto:707-756: 22 values from
  `STANDARD_LAYER_SENTENCES` through `STANDARD_LAYER_TERM_VECTORS`.
- `LayerScope`, opennlp_document.proto:830-838: `LAYER_SCOPE_POSITIONAL`,
  `LAYER_SCOPE_DOCUMENT`.
- `OffsetEncoding`, opennlp_document.proto:292-305: `OFFSET_ENCODING_UTF8_BYTE`,
  `_UTF16_CODE_UNIT`, `_UNICODE_CODE_POINT`.
- `CoordinateSpace`, opennlp_document.proto:279-286: `COORDINATE_SPACE_CHAR_DOCUMENT`,
  `COORDINATE_SPACE_TOKEN_SENTENCE`.
- `StandardChunkingStrategy`, opennlp_document.proto:386-397: `SENTENCE`, `TOKEN`,
  `SEMANTIC`, `CATEGORY`.
- `TermVectorMode`, opennlp_document.proto:757-765: `FULL`, `SCORING_ONLY`.
- `DocumentWordType`, opennlp_document.proto:947.
- `LexicalExpansionKind`, opennlp_document.proto:1015.
- `VectorNormalization` / `EmbeddingGranularity`, opennlp_document.proto:576, :587.
- `StandardEmbeddingBackend`, opennlp_document.proto:498.
- `ParseNodeKind`, opennlp_document.proto:487. `StemmerAlgorithm`, :33.
- `SearchComponentKind`, opennlp_search.proto:508-517: `VECTOR`, `KEYWORD`.
- `SearchMetric`, opennlp_search.proto:563-569: `SEARCH_METRIC_COSINE`.
- `StandardSearchProvider`, opennlp_search.proto:571-578: `FLAT_FLOAT`, `TURBO_QUANT`.
- `SearchProviderCapability`, opennlp_search.proto:429-442: `VECTOR`, `KEYWORD`,
  `LIVE`, `BUNDLE`, `PERSISTENT`.
- `CollectionEventKind`, opennlp_search.proto:344.
- `ModelArtifactRole`, opennlp_training.proto:76-98:
  `MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER`, `_STATIC_EMBEDDING`, `_PARSER`,
  `_CHUNKER`, `_SENTENCE_DETECTOR`, `_TOKENIZER`, `_POS_TAGGER`, `_LEMMATIZER`,
  `_NAME_FINDER`.
- `InstallModelStage`, opennlp_training.proto:152. `StreamingTrainingStage`, :263.
  `StreamingTrainingIndexDurability`, :226.
- `StandardDictionaryFormat`, opennlp_vocabulary.proto:59.
- `StandardResource`, opennlp_service.proto:334. `DiagnosticSeverity`, :273.
- Key message names that surface as JSON keys: `OpenNlpDocument`, `DocumentLayers`,
  `AnnotationLayer`, `AnnotationSpan`, `LayerIdentity`, `ChunkResult`, `ChunkSpan`,
  `ChunkEmbeddingGroup`, `EmbeddingRoute`, `NormalizationResult`, `AlignmentRun`,
  `DocumentAnalytics`, `TermVectorAnnotation`, `SearchIndexDescriptor`,
  `SearchCorpusDescriptor`, `SearchIndexBuildDescriptor`, `AnalysisChainDescriptor`,
  `SearchHit`, `MatchedSpan`, `CollectionDescriptor`, `CollectionDriftStats`,
  `TermStatistic`, `TeacherDescriptor`, `StaticModelDescriptor`,
  `VocabularyArtifactDescriptor`, `DictionaryArtifactDescriptor`,
  `ModelCatalogDescriptor`, `InstalledModelDescriptor`, `IndexAlias`.

## 9. Gateway route vocabulary (visible in the help callouts and in DevTools)

`opennlp-grpc-webapp/src/main/java/org/apache/opennlp/grpc/webapp/GrpcJsonApi.java:128-190`:
`/api/v1/service-info`, `/model-bundles`, `/analyze`, `/output-formats`,
`/format-document`, `/response/encode`, `/response/decode`, `/search-indexes`,
`/search-providers`, `/search`, `/index-documents`, `/delete-search-index`,
`/persist-index`, `/seal-index`, `/reindex-index`, `/set-index-alias`,
`/delete-index-alias`, `/index-aliases`, `/set-collection`, `/get-collection`,
`/collections`, `/delete-collection`, `/dictionary-formats`, `/dictionaries`,
`/import-dictionary`, `/learn-vocabulary`, `/download-vocabulary`, `/teachers`,
`/model-catalog`, `/installed-models`, `/static-models`, `/delete-static-model`.

Route names quoted in the UI help text: index.html:108 (`POST /api/v1/analyze`),
:581 (`GET /api/v1/search-indexes`), :765 (`POST /api/v1/index-documents`),
:812-813 (`GET /api/v1/model-bundles`, `GET /api/v1/model-catalog`,
`POST /api/v1/install-model`), :895 (`POST /api/v1/import-dictionary`,
`POST /api/v1/learn-vocabulary`), :991-992 (`/api/v1/persist-index`,
`/api/v1/seal-index`, `/api/v1/reindex-index`).
