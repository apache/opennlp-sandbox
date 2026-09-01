# Reference: what text-annotation standards call a span, an annotation, and a layer

Evidence for the Workbench terminology audit: tagging schemes, CoNLL formats, TEI, the ISO/TC 37
annotation standards, and UIMA CAS. Per source: URL, fetch date, verbatim excerpts, then what it calls
X. Failed fetches are stated as failures, not paraphrased into quotes. All fetches 2026-08-28.

## IOB / BIO tagging
https://www.nltk.org/book/ch07.html (2026-08-28)
> "In this scheme, each token is tagged with one of three special chunk tags, I (inside), O (outside), or B (begin). A token is tagged as B if it marks the beginning of a chunk. Subsequent tokens within the chunk are tagged I. All other tokens are tagged O."

https://spacy.io/usage/linguistic-features (2026-08-28)
> "`I` - Token is **inside** an entity. `O` - Token is **outside** an entity. `B` - Token is the **beginning** of an entity."

- the scheme: **IOB**, also written **BIO**. Both spellings are in live use for the same thing.
- the thing being tagged: a **token**. The tag is a per-token **chunk tag** or entity tag, and the span
  is implied by the prefix run, never stored as a pair of offsets.

## BILOU / BILUO / BIOES
Ratinov and Roth, "Design Challenges and Misconceptions in Named Entity Recognition", CoNLL-2009,
https://cogcomp.seas.upenn.edu/papers/RatinovRo09.pdf (2026-08-28, text extracted from the PDF)
> "We focus instead on two most popular schemes- BIO and BILOU. The BIO scheme suggests to learn classifiers that identify the Beginning, the Inside and the Outside of the text segments. The BILOU scheme suggests to learn classifiers that identify the Beginning, the Inside and the Last tokens of multi-token chunks as well as Unit-length chunks."
> "We find that BILOU representation of text chunks significantly outperforms the widely adopted BIO."
> "the BILOU encoding of four NE types, each token can take 21 states (O, B-PER, I-PER , U-PER, etc.)."

https://spacy.io/usage/linguistic-features (2026-08-28) uses the letter order **BILUO**:
> "`B` - Token is the **beginning** of a multi-token entity. `I` - Token is **inside** a multi-token entity."

- the scheme: **BILOU** in the source paper, **BILUO** in spaCy, **BIOES** elsewhere. Same letter set,
  different ordering per ecosystem, so UI text should spell out what the letters mean.
- the paper's own words for the axis are "**representation scheme**" and "**encoding scheme**" of
  "**text chunks**". That is the nearest standard phrase to an "offset encoding" style setting, and
  note it is about tag encoding, not character offsets.

## CoNLL-2000 (chunking shared task)
Fetch note: https://www.clips.uantwerpen.be/conll2000/chunking/ returned HTTP 404 on 2026-08-28 and
https://aclanthology.org/W00-0726/ carries no abstract text, so no verbatim quote from the task page
is available. The paper is "Introduction to the CoNLL-2000 Shared Task Chunking", Erik F. Tjong Kim
Sang and Sabine Buchholz, 2000. Quotes below are from NLTK, which distributes the corpus.
https://www.nltk.org/book/ch07.html (2026-08-28)
> "The CoNLL 2000 corpus contains 270k words of Wall Street Journal text, divided into 'train' and 'test' portions, annotated with part-of-speech tags and chunk tags in the IOB format."
> "The basic technique we will use for entity detection is chunking, which segments and labels multi-token sequences."

- the task: **chunking**; output units are **chunks** with syntactic types (NP, VP, PP). This is the
  origin of the OpenNLP `ChunkerME` sense of "chunk": a syntactic phrase, not an arbitrary passage.

## CoNLL-2003 (NER shared task)
https://huggingface.co/datasets/eriktks/conll2003 (2026-08-28). The original task page
https://www.clips.uantwerpen.be/conll2003/ner/ returned HTTP 404 on this date; the dataset card is
used instead. The paper is "Introduction to the CoNLL-2003 Shared Task: Language-Independent Named
Entity Recognition", Erik F. Tjong Kim Sang and Fien De Meulder, 2003, https://aclanthology.org/W03-0419/
(the landing page carries no abstract text).
> "The chunk tags and the named entity tags have the format I-TYPE which means that the word is inside a phrase of type TYPE. Only if two phrases of the same type immediately follow each other, the first word of the second phrase will have tag B-TYPE to show that it starts a new phrase. A word with tag O is not part of a phrase."
> "Note the dataset uses IOB2 tagging scheme, whereas the original dataset uses IOB1."
Data fields: `tokens`, `pos_tags`, `chunk_tags`, `ner_tags`.

- one file, one row per token, and **four parallel tag columns** over the same token sequence. This is
  the classic layered representation, and note the standard does **not** call the columns "layers".
- **IOB1** and **IOB2** are distinct, named variants. A UI that says only "IOB" is underspecified.
- CoNLL-2003 keeps `chunk_tags` and `ner_tags` as separate columns, which is the sharpest available
  proof that "chunk" and "named entity" are different things in this tradition.

## CoNLL-U (Universal Dependencies)
https://universaldependencies.org/format.html (2026-08-28)
> Word lines contain "the annotation of a word/token/node in 10 fields separated by single tab characters".
Fields verbatim: ID "Word index, integer starting at 1 for each new sentence; may be a range for
multiword tokens"; FORM "Word form or punctuation symbol"; LEMMA "Lemma or stem of word form";
UPOS "Universal part-of-speech tag"; XPOS "Optional language-specific (or treebank-specific)
part-of-speech / morphological tag"; FEATS "List of morphological features from the universal feature
inventory"; HEAD, DEPREL "Universal dependency relation to the HEAD"; DEPS; MISC "Any other annotation".
> "(multiword) tokens are indexed with integer ranges like 1-2 or 3-5."
Sentences are delimited by blank lines.

- **UPOS** versus **XPOS** is the standard way to distinguish a universal tag set from a
  treebank-specific one such as Penn Treebank. That is the vocabulary to reuse for a tag-set selector.
- LEMMA is defined as "Lemma or stem", the one place in these standards where the two are treated as
  interchangeable. CoNLL-U carries **no character offsets**; position is a token index in a sentence.

## TEI (Text Encoding Initiative)
https://tei-c.org/release/doc/tei-p5-doc/en/html/AI.html (2026-08-28)
> `<span>` "associates an interpretative annotation directly with a span of text."
> `<interp>` "summarizes a specific interpretative annotation which can be linked to a span of text."
`<spanGrp>` groups related spans sharing attributes such as responsibility or type; `<interpGrp>`
groups interpretations. Stand-off representation uses `<linkGrp>` and `<link>` elements pointing at
`xml:id` values on text segments.

https://tei-c.org/release/doc/tei-p5-doc/en/html/CC.html (2026-08-28)
> "[teiCorpus] (TEI corpus) contains the whole of a TEI encoded corpus, comprising a single corpus header and one or more [TEI] elements, each containing a single text header and a text."
> "Language corpora are regarded by these Guidelines as composite texts rather than unitary texts."

https://tei-c.org/release/doc/tei-p5-doc/en/html/CO.html (2026-08-28)
> "the `w` element represents a grammatical (not necessarily orthographic) word."
> "If a consistent internal subdivision of paragraphs is desired, the s or seg ('segment') elements may be used"

- span: **span** is TEI's literal element name, and it is defined as annotation attached to a span of
  text. This is the single strongest standards precedent for the word "span".
- annotation: **annotation**, specifically "interpretative annotation". The grouping construct is a
  **spanGrp**, not a "layer".
- corpus: **corpus** / `teiCorpus`, and a corpus is "composite texts", each with its own header.
- token and sentence: **w** (word) and **s** / **seg** (segment).

## ISO/TC 37 annotation standards (SemAF, LAF)
https://standards.clarin.eu/sis/views/view-spec.xq?id=SpecSemAF (2026-08-28)
> "Semantic Annotaton Framework (SemAF) is a standard of multiple-parts for semantic annotation. It is developed by the ISO within the ISO/TC 37/SC 4/ WG 2."
Scope given on that page: "Semantic corpus annotation."

Fetch note: https://www.iso.org/standard/60581.html (ISO 24617-6:2016, "Principles of semantic
annotation") returned HTTP 403 on this date, and a sample PDF of ISO 24617-11:2021 could not be
text-extracted. No verbatim text from the ISO documents themselves is included here. What can be
stated from the catalogue titles alone, which were retrieved:
- ISO 24617 is titled "Language resource management - Semantic annotation framework (SemAF)" and is
  split into numbered parts: Part 1 Time and events (ISO-TimeML), Part 2 Dialogue acts,
  Part 4 Semantic roles, Part 6 Principles of semantic annotation, Part 7 Spatial information
  (ISOspace), Part 8 Semantic relations in discourse, Part 11, Part 12 Quantification.
- ISO 24612 is the "Linguistic annotation framework" (LAF).

- the family word is **annotation**; the umbrella noun in every ISO title is **language resource
  management**. "Layer" is not part of any of these titles.
- SemAF's structuring device is **parts**, one per semantic phenomenon. This family scopes kinds of
  annotation by naming the phenomenon, not by numbering layers.
- LAF's well-known distinction is between an **annotation** (the information) and its
  **representation** (the serialised format): the difference between "a PERSON spans characters 10 to
  16" and "it is stored as B-PER I-PER". Described in secondary literature about SemAF; not quoted
  here from the standard itself, whose text could not be fetched.

## UIMA CAS
https://uima.apache.org/d/uimaj-current/oas.html (2026-08-28). Fetch note:
https://uima.apache.org/d/uimaj-current/references.html returned HTTP 404 on this date.
> Annotation: "The association of a metadata, such as a label, with a region of text (or other type of artifact)."
> CAS: "The UIMA Common Analysis Structure is the primary data structure which UIMA analysis components use to represent and share analysis results."
> Sofa: "Sofa stands for **Subject of Analysis**."
> View: "A CAS can have multiple views; each view has a unique representation of the artifact, and has its own index repository."
> Type System: "Think of a type system as a schema or class model for the CAS. It defines the types of objects and their properties (or features) that may be instantiated in a CAS."

- span: UIMA does not use the word. `uima.tcas.Annotation` "adds two features, a begin and an end
  feature, which are suitable for identifying a span in a text string that the annotation applies to"
  (https://uima.apache.org/d/uima-as-2.9.0/apidocs/org/apache/uima/jcas/cas/AnnotationBase.html,
  2026-08-28). So the field names are **begin** and **end**, and "span" appears only as prose.
- annotation layer: UIMA has none. The kind axis is the **type** in the **type system**; the parallel
  text axis is the **view** / **Sofa**.
- positional versus document-level scope: modelled as `uima.cas.AnnotationBase` (bound to a Sofa, no
  offsets) versus `uima.tcas.Annotation` (adds begin/end). That is the standards-side answer to
  "layer scope".

## Summary across the standards
- **span**: TEI says it outright. UIMA and spaCy say begin/end or start/end. CoNLL formats have no span
  at all, only per-token prefix tags. So "span" is safe and TEI-backed, but a UI must be explicit about
  whether it means characters or tokens.
- **annotation**: universal. TEI, ISO 24617, ISO 24612, UIMA and GATE all use exactly this word.
- **layer**: absent from TEI, the CoNLL formats and UIMA. Standard only in the annotation-editor family
  (INCEpTION, WebAnno). Elsewhere the axis is a **type**, an **annotation set**, or a named **column**.
