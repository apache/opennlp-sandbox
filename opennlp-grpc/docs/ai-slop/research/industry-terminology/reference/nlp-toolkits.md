# Reference: what NLP toolkits actually call things

Evidence for the Workbench terminology audit. One section per upstream source: URL, fetch date, short
verbatim excerpts proving the wording, then what that source calls X. Excerpts only, not dumps.
Failed fetches are stated as failures rather than paraphrased into quotes. All fetches 2026-08-28.

## spaCy: processing pipelines
https://spacy.io/usage/processing-pipelines (2026-08-28)
> "When you call `nlp` on a text, spaCy first tokenizes the text to produce a `Doc` object. The `Doc` is then processed in several different steps - this is also referred to as the **processing pipeline**."
> "Each pipeline component returns the processed `Doc`, which is then passed on to the next component."
> "Custom components can be added to the pipeline using the `add_pipe` method."

- pipeline step: **component**. The API verb is `add_pipe`. "Step" appears only as loose prose.
- document object: **Doc**. Pipeline: **processing pipeline**.

## spaCy: linguistic features
https://spacy.io/usage/linguistic-features (2026-08-28)
> "Tokenization is the task of splitting a text into meaningful segments, called _tokens_."
> "A named entity is a 'real-world object' that's assigned a name - for example, a person, a country, a product or a book title."
> "Noun chunks are 'base noun phrases' - flat phrases that have a noun as their head..."
> BILUO: "`B` - Token is the **beginning** of a multi-token entity. `I` - Token is **inside** a multi-token entity." IOB: "`I` - Token is **inside** an entity. `O` - Token is **outside** an entity. `B` - Token is the **beginning** of an entity."

- shallow parsing: **noun chunks** only (`doc.noun_chunks`). spaCy ships no general chunker.
- lemma vs stem: **lemma** only. spaCy lemmatizes and does not stem.
- tag sets: **UPOS** (Universal Dependencies) for `token.pos_`, treebank tag for `token.tag_`.
- sentence splitting: **sentencizer** / sentence segmentation. Tag encodings: **BILUO** and **IOB**.

## spaCy: Span API (offsets)
https://spacy.io/api/span (2026-08-28)
> Span is "A slice from a `Doc` object." `start`: "The token offset for the start of the span." `end`: "The token offset for the end of the span." `start_char`: "The character offset for the start of the span." `end_char`: "The character offset for the end of the span."

- span: **Span**. Offsets: **offset**, with **token offset** and **character offset** named separately.
- This is the nearest thing in the toolkits to "offset encoding": spaCy encodes the unit in the field
  name rather than carrying a separate encoding flag.

## NLTK
https://www.nltk.org/book/ch07.html, https://www.nltk.org/book/ch02.html,
https://www.nltk.org/api/nltk.corpus.html (2026-08-28)
> "The basic technique we will use for entity detection is chunking, which segments and labels multi-token sequences."
> "We will begin by considering the task of noun phrase chunking, or NP-chunking, where we search for chunks corresponding to individual noun phrases."
> "In this scheme, each token is tagged with one of three special chunk tags, I (inside), O (outside), or B (begin)."
> "The CoNLL 2000 corpus contains 270k words of Wall Street Journal text, divided into 'train' and 'test' portions, annotated with part-of-speech tags and chunk tags in the IOB format."
> "A text corpus is a large, structured collection of texts."
> "There is also a corpus of stopwords, that is, high-frequency words like the, to and also that we sometimes want to filter out of a document before further processing."
> "WordNet is a semantically-oriented dictionary of English, similar to a traditional thesaurus but with a richer structure."

- shallow parsing: **chunking**; a chunk is a labelled multi-token sequence with a linguistic type.
- corpus: **corpus**, read through a **corpus reader**. Stopwords: **stopwords**, a named corpus.
- lexical expansion: **WordNet**, described as a dictionary/thesaurus, units called **synsets**.

## Stanford CoreNLP
https://stanfordnlp.github.io/CoreNLP/annotators.html (2026-08-28)
Annotator names listed: tokenize, cleanxml, docdate, ssplit, pos, lemma, ner, entitymentions, regexner,
tokensregex, parse, depparse, coref, dcoref, relation, natlog, openie, entitylink, kbp, quote,
sentiment, truecase, udfeats.
> tokenize: "Tokenizes the text. This splits the text into roughly 'words'". ssplit: "Splits a sequence of tokens into sentences". pos: "Labels tokens with their POS tag". lemma: "Generates the word lemmas for all tokens in the corpus". ner: "Recognizes named (PERSON, LOCATION, ORGANIZATION, MISC), numerical, and temporal entities". parse: "Provides full syntactic analysis, using both the constituent and dependency representations".

- pipeline step: **annotator**. A pipeline is a comma-separated list of annotator names.
- result container: **Annotation**, with typed keys (TokensAnnotation, SentencesAnnotation,
  PartOfSpeechAnnotation, LemmaAnnotation).
- constituency parse: **constituent representation**, under the `parse` annotator.
- CoreNLP ships no chunker. Chunking is absent from the annotator list entirely.

## Hugging Face
https://huggingface.co/tasks, https://huggingface.co/docs/transformers/main_classes/pipelines,
https://huggingface.co/docs/transformers/tokenizer_summary (2026-08-28)
Task taxonomy, NLP subset, verbatim: "Feature Extraction", "Fill-Mask", "Question Answering",
"Sentence Similarity", "Summarization", "Table Question Answering", "Text Classification",
"Text Generation", "Text Ranking", "Token Classification", "Translation", "Zero-Shot Classification".
Pipeline task strings with aliases: `"token-classification"` (alias `"ner"`), `"text-classification"`
(alias `"sentiment-analysis"`), `"feature-extraction"`, `"fill-mask"`, `"zero-shot-classification"`.
> "Transformers support three subword tokenization algorithms: Byte pair encoding (BPE), Unigram, and WordPiece. They split text into units between words and characters, keeping the vocabulary compact while still capturing meaningful pieces."
> "SentencePiece is a tokenization library that applies BPE or Unigram directly on raw text."
> "Words not in the vocabulary map to an `\"<unk>\"` token, so the model can't handle new words."

- NER: canonical name **token classification**; `ner` is only an alias.
- sentiment: canonical name **text classification**; `sentiment-analysis` is only an alias.
- embeddings: the task is **feature extraction**, not "embedding".
- subword: **subword tokenization**; **BPE**, **Unigram**, **WordPiece** are algorithms and
  **SentencePiece** is described as a library, not an algorithm.
- OOV: the page says "not in the vocabulary" and names the `<unk>` **unknown token**. It does not use
  the literal phrase "out-of-vocabulary".

## Apache OpenNLP (own manual)
https://opennlp.apache.org/docs/2.5.4/manual/opennlp.html (2026-08-28, verified against raw HTML)
> Chunker: "Text chunking consists of dividing a text in syntactically correlated parts of words, like noun groups, verb groups, but does not specify their internal structure, nor their role in the main sentence."
> Parser: "A parser returns a parse tree from a sentence according to a phrase structure grammar. A parse tree specifies the internal structure of a sentence."
> "The OpenNLP Tokenizers segment an input character sequence into tokens. Tokens are usually words, punctuation, numbers, etc."
> "The OpenNLP Sentence Detector can detect that a punctuation character marks the end of a sentence or not."
> "The Name Finder can detect named entities and numbers in text."
> "The OpenNLP Document Categorizer can classify text into pre-defined categories."
> "The lemmatizer returns, for a given word form (token) and Part of Speech tag, the dictionary form of a word, which is usually referred to as its lemma."
> "The OpenNLP Language Detector classifies a document in ISO-639-3 languages according to the model capabilities."

Class names in the manual: `TokenizerME`, `SentenceDetectorME`, `NameFinderME`, `DocumentCategorizerME`,
`POSTaggerME`, `LemmatizerME`, `ChunkerME`, `LanguageDetectorME`, `Parser`.

- shallow parsing: **text chunking**, component **Chunker** / `ChunkerME`. The manual never writes
  "shallow parsing".
- document categorization: **Document Categorizer**. "doccat" is CLI and model-file naming, not prose.
- sentence splitting: **Sentence Detector**, not "sentence splitter", not "ssplit".
- NER: **Name Finder**. The manual has a chapter titled **Corpora** (CONLL 2000/2002/2003, OntoNotes).

## Apache UIMA
https://uima.apache.org/d/uimaj-current/oas.html (2026-08-28).
Fetch note: https://uima.apache.org/d/uimaj-current/references.html returned HTTP 404 on this date, so
the glossary excerpts below come from the overview page only.
> Annotator: "A software component that implements the UIMA annotator interface."
> Analysis Engine: "A program that analyzes artifacts (e.g. documents) and infers information about them, and which implements the UIMA interface Specification."
> CAS: "The UIMA Common Analysis Structure is the primary data structure which UIMA analysis components use to represent and share analysis results."
> Sofa: "Sofa stands for **Subject of Analysis**."
> Type System: "Think of a type system as a schema or class model for the CAS. It defines the types of objects and their properties (or features) that may be instantiated in a CAS."
> Annotation: "The association of a metadata, such as a label, with a region of text (or other type of artifact)."
> View: "A CAS can have multiple views; each view has a unique representation of the artifact, and has its own index repository."
> Aggregate Analysis Engine: "An Analysis Engine made up of multiple subcomponents arranged in a flow."

- pipeline step: **annotator**, formally an **Analysis Engine**; a composed pipeline is an
  **Aggregate Analysis Engine** and its ordering is a **flow**.
- annotation layer: UIMA has **no "layer"**. The equivalent axis is the **type** in the **type system**;
  the parallel-text axis is the **view** / **Sofa**.
- span: an annotation is a feature structure with **begin** and **end** over a Sofa.
- document-level vs positional: `uima.cas.AnnotationBase` (bound to a Sofa, no offsets) vs
  `uima.tcas.Annotation` (adds begin/end). That is UIMA's version of "layer scope".

## GATE
https://gate.ac.uk/family/developer.html (2026-08-28)
GATE names its UI **GATE Developer**, "a development environment that provides a rich set of graphical
interactive tools for the creation, measurement and maintenance of software components for processing
human language", and "a specialist tool similar in purpose and character to a programmer's integrated
development environment". Its data model uses **annotation sets** and **annotation types** over
documents with stand-off markup.

- UI name: **Developer**, not Workbench. **Teamware** is the collaborative annotation server.
- annotation layer: **annotation set**, a named set of annotations on a document.
- pipeline step: **processing resource**; data are **language resources**.

## INCEpTION / WebAnno
https://inception-project.github.io/releases/32.1/docs/user-guide.html (2026-08-28)
Fetch note: the `releases/latest/` path returned HTTP 404 on this date, so the pinned 32.1 guide was
used and the summary below is paraphrase, not quotation. INCEpTION and WebAnno model annotation as
**annotation layers** with three layer types: **span layers** (one or more adjacent tokens, used for
part-of-speech and named entity tagging), **relation layers** (arcs between span annotations), and
**chain layers** (span plus relation in one structural layer).

- annotation layer: **layer**. This is the strongest precedent for the word "layer".
- layer scope: expressed as the **layer type** (span / relation / chain), not as a separate
  positional versus document-level flag.
- the human doing the work: **annotator**. Machine assistance is a **recommender**.

## Weka
https://machinelearningmastery.com/tour-weka-machine-learning-workbench/ (2026-08-28, secondary
source, used because it names the shipped GUIs). Weka's GUIs are the **Explorer**, the
**Experimenter**, the **KnowledgeFlow** and the **Workbench**, where the Workbench is the integrated
environment combining all the graphical interfaces into a single interface, and the KnowledgeFlow is
the graphical pipeline designer.

- UI name: **Workbench** is a real, shipped, top-level GUI name in a mainstream open source ML toolkit,
  and it means specifically "everything in one window".

## Retrieval-side "chunk" (LangChain)
https://docs.langchain.com/oss/python/langchain/knowledge-base (2026-08-28)
> "A page is often too coarse for retrieval. Split pages further so relevant passages are not diluted by surrounding text."
> "`RecursiveCharacterTextSplitter` recursively splits on common separators (such as newlines) until each chunk is the target size. This is the recommended text splitter for generic text use cases."
Parameters are named `chunk_size` and `chunk_overlap`.

- operation: **splitting**, class is a **text splitter**, not a chunker.
- output unit: **chunk**, sized in characters or tokens, with **overlap**.

The collision: in OpenNLP, NLTK and the CoNLL literature a **chunk** is a syntactic phrase found by a
**chunker**, carries a linguistic label (NP, VP), and is measured in tokens. In retrieval tooling a
**chunk** is an arbitrary passage produced by a **splitter**, carries no linguistic label, and is sized
by a `chunk_size` budget. The retrieval ecosystem's own class names say *split* and *splitter*, so
"split", "passage" and "segment" are available words that do not collide with the parsing sense.

## Model2Vec (static embeddings, distillation)
https://github.com/MinishLab/model2vec (2026-08-28)
> "Fast State-of-the-Art Static Embeddings"
> The method: "forward pass a vocabulary through a sentence transformer model, creating static embeddings for the individual tokens", then post-processing.
The README uses **static embedding model**, **vocabulary** and **distill** ("distill your own Model2Vec
model from a Sentence Transformer model"). It does **not** use "teacher" or "student"; it calls the
source a "Sentence Transformer model".

- source model: **Sentence Transformer model**, not "teacher model". Operation: **distill**.
- output: **static embedding model**, the same family as GloVe, word2vec, fastText.
- Wider literature does use **teacher model** / **student model** as the standard distillation framing,
  for example the survey title "Teacher-Student Architecture for Knowledge Learning: A Survey"
  (https://arxiv.org/pdf/2210.17332, 2026-08-28). It is standard in the literature but not in
  Model2Vec's own documentation.

## Terms with no evidence found in these sources
Searched but not found as standard vocabulary in any toolkit consulted: "annotation layer" outside
INCEpTION/WebAnno, "layer scope", "offset encoding", "embedding granularity", "lexical expansion"
(the ecosystems say WordNet, synonyms, or query expansion), "term vector" (Lucene's word, not an NLP
toolkit's), "workspace" as an NLP unit, and "geocoding"/"toponym resolution" (a GIS and geoparsing
term; none of the six toolkits above ship it).

## Apache UIMA Ruta (added 2026-08-28, follow-up fetch)
https://uima.apache.org/ruta.html (2026-08-28)
> "The UIMA Ruta Workbench was created to facilitate all steps in creating Analysis Engines based on the UIMA Ruta language."

- UI name: **Workbench** is used as a top-level GUI name inside Apache itself, not only by Weka.
  Together with Weka's Workbench this gives "workbench" two independent precedents.
