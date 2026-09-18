# Analyze tab: terminology audit

Scope: the panel `#analysis-workbench` in
`opennlp-grpc-webapp-default/index.html:77-367`, the annotation drawer
`#annotation-details` at `index.html:369-382`, and every label those elements
receive at runtime from `src/analysis-controls.ts`, `src/analysis-config.ts`,
`src/document-shape.ts`, `src/annotation-drawer.ts`,
`src/chunk-projection.ts`, `src/chunk-projection-view.ts`,
`src/term-vector-stack.ts`, `src/normalization-xray.ts`,
`src/document-heatmap-view.ts`, `src/semantic-workbench.ts`, `src/main.ts`.

Verdicts: **Standard** means a named product or specification uses the same
word for the same thing (source cited, excerpt saved under `../reference/`).
**Local** means the word is correct English but is this project's own coinage
for the concept. **Invented** means the reader has no way to guess what it
means from prior art.

FACT statements cite `path:line`. Recommendations are marked OPINION with a
priority.

---

## 1. Tab chrome and page heading

| Current string | Where | Verdict | Precedent or replacement |
| --- | --- | --- | --- |
| `Analyze` | `index.html:43` | Standard | spaCy, CoreNLP and OpenNLP all use "analyze"/"analysis" for running a pipeline over text. Keep. |
| `Document analysis workbench` | `index.html:82` | Standard | "workbench" is the standard word for a multi-tool inspection surface (Eclipse Workbench, Apache UIMA "Annotation Viewer" is the closer NLP analogue). Keep. |
| `Apache OpenNLP gRPC` (kicker) | `index.html:81` | Standard | Keep. |
| `Service` / `Profiles` / `Bundles` / `Languages` | `index.html:88-91` | Mixed | "Profiles" and "Languages" are fine. **"Bundles"** is local; see 1.1. |
| `Discovering` / `Offline` (service name) | `index.html:88`, `main.ts:425` | Standard | Keep. |

### 1.1 FACT: "Bundles" and "Profiles" are two different things that share names

`GET /api/v1/service-info` returns `availableProfileIds = [en-sentiment,
en-embed, en-basic]` and `GET /api/v1/model-bundles` returns bundles
`en-sentiment` and `en-basic` (`../reference/demo-state.md`). The tab shows the
count of the first as **Profiles** (`index.html:89`, filled at `main.ts:444`)
and the count of the second as **Bundles** (`index.html:90`, `main.ts:445`),
and lists the bundle ids under the disclosure **Loaded model bundles**
(`index.html:192`). The dropdown then offers `Profile: en-basic`
(`analysis-controls.ts:198`) for the profile of that name.

Measured on the demo (`../reference/demo-errors.md`): the `en-basic` *bundle*
advertises `LANGUAGE_DETECT, SENTENCE_DETECT, TOKENIZE, POS_TAG, LEMMATIZE,
EMBED`, while the `en-basic` *profile* runs only `SENTENCE_DETECT` and
`TOKENIZE`. Two entities, one name, different behaviour, both surfaced on the
same screen.

OPINION (P1): rename the disclosure and the fact to make the distinction
visible. "Bundle" has no NLP precedent in this sense; the closest standard term
for "a set of model files loaded together for a language" is **model pack**
(spaCy calls a downloadable set a "trained pipeline"/"model package", see
https://spacy.io/usage/models). Propose:

- `Bundles` -> `Model packs`
- `Loaded model bundles` -> `Loaded model packs`
- `Profile: en-basic` -> `Preset: en-basic (server profile)`

and, separately, ask the service owners whether the profile ids and bundle ids
should be allowed to collide at all (see Questions).

---

## 2. Help callout

`index.html:95-115`.

| Current string | Verdict | Note |
| --- | --- | --- |
| `How to use the analyzer` | Standard | Keep. |
| `Load Alice novel` | Local | Fine, but see `error-states-and-links.md` section 4 for what pressing it costs. |
| `pick a feature preset` | Standard | matches the control label. Keep. |
| `The complete typed response renders as the Document, Chunks, Heatmap, Graph, and Protobuf JSON projections` | **Invented** ("projections") | See 8.1. |
| `select any annotation to inspect its exact span and payload` | Standard | "span" is spaCy/CoreNLP vocabulary ("assigns labels to contiguous spans of tokens", `../reference/spacy-linguistic-features.md`). Keep. |
| `Add to server workspace` ... `then query it from the Workspace search tab` | Standard | but there is no link; see `error-states-and-links.md` section 1. |
| `Copy JSON and Download .pb export exactly what your client receives` | Standard | Keep. |

---

## 3. Composer form

`index.html:117-198`.

| Current string | Where | Verdict | Precedent / proposal |
| --- | --- | --- | --- |
| `Document text` | `:118` | Standard | Keep. |
| `0 characters` | `:119` | Standard | Keep. |
| `Feature preset` | `:128` | Local, good | scikit-learn and Hugging Face both use "preset"/"config"; "preset" reads correctly. Keep. |
| `All available features` | `:130` | Local, good | Keep. |
| `Choose features` | `:131` | Standard | Keep. |
| `Server automatic` | `:132` | Local | Slightly opaque: it means "send no profile and let the server choose". Propose `Server default profile`. P3. |
| `Combines every safely configured feature reported by this server.` | `:134` | Local | "safely configured" is unexplained. Propose `Every feature this server has a model for.` P2. |
| `Embedding model` | `:139` | Standard | Hugging Face / Elastic `inference` API both say "embedding model". Keep. |
| `No embedding model configured` | `:141` | Standard | Keep, but add a route out; see `model-gating.md`. |
| `When available, vectors are attached to document and chunk output.` | `:143` | Standard | Keep. |
| `Language pipeline` | `:146` | Standard | spaCy calls the object a "pipeline"; "language pipeline" reads naturally. Keep. |
| `Automatic (route by detected language)` | `:148` | Standard | "language detection" and "routing" are both standard. Keep. |
| `Installed language model sets` | `:150` | Local | "model sets" is a third name for what is elsewhere "bundles" and "packs". Propose `Installed language model packs` for consistency with 1.1. P2. |
| `Part-of-speech tag set` | `:154` | Standard | OpenNLP manual "Chapter 7. Part-of-Speech Tagger" (`../reference/opennlp-manual-chapters.md`); "tag set" is standard (Penn Treebank tagset, UD POS tags). Keep. |
| `Model native` | `:156` | Standard | Keep. |
| `Universal Dependencies (UD)` | `:157` | Standard | https://universaldependencies.org/u/pos/. Keep. |
| `Penn Treebank` | `:158` | Standard | https://catalog.ldc.upenn.edu/LDC99T42. Keep. |

---

## 4. Chunk output fieldset

`index.html:165-182`.

| Current string | Verdict | Precedent / proposal |
| --- | --- | --- |
| `Chunk output` (legend) | **Ambiguous, P1** | See 4.1. |
| `Return either strategy or both in the same response.` | Standard | "strategy" matches Elastic's `chunking_settings.strategy` (`../reference/elastic-chunking-settings.md`). Keep. |
| `Sentence chunks` / `One chunk per detected sentence` | Standard | Elastic strategy `"sentence"`: "splits the input text at sentence boundaries". Keep. |
| `Token windows` / `Overlapping windows across the document` | Local | Elastic's equivalent strategy is named `"word"` with an `overlap` parameter. "Token window" is defensible because the split is on OpenNLP tokens, not whitespace words, and "window" is standard in IR. Keep, but see 4.1. |
| `Window (tokens)` | Local | Elastic calls it `max_chunk_size`, LangChain `chunk_size`. Propose `Chunk size (tokens)`. P3. |
| `Overlap (tokens)` | Standard | Elastic `overlap`, LangChain `chunk_overlap`. Keep. |

### 4.1 FACT: "chunk" carries two unrelated meanings on this one screen

The tab offers a feature named **`Syntactic chunks`** (`analysis-config.ts:59`,
for `PIPELINE_STEP_SYNTACTIC_CHUNK`) and a chunk-output control named
**`Sentence chunks`** (`index.html:170`). The first is OpenNLP's shallow parser:
"Chapter 9. Chunker" in the manual, noun-phrase and verb-phrase chunks
(`../reference/opennlp-manual-chapters.md`). The second is retrieval passage
splitting: "Chunking is the process of splitting the input text into pieces"
(`../reference/elastic-chunking-settings.md`). The result panel then has a tab
literally called `Chunks` (`index.html:270`) which shows only the second kind,
while a layer named `Syntactic Chunks` would appear in the Layers browser of the
`Document` tab.

OPINION (P1): keep "chunk" for the retrieval sense (that is now the dominant
industry meaning) and rename the syntactic one to match OpenNLP's own manual:

- `Syntactic chunks` -> `Phrase chunks (shallow parse)` in `FEATURE_NAMES`
  (`analysis-config.ts:59`), and correspondingly `Syntactic chunker` ->
  `Phrase chunker` in `model-data-workbench.ts` `roleLabel`.
- Legend `Chunk output` -> `Passage chunking`, so the fieldset says what it
  splits and for what.

---

## 5. Capability summary and feature checklist

`index.html:184-205`.

| Current string | Where | Verdict | Note |
| --- | --- | --- | --- |
| `Enabled features` | `:186` | Standard | Keep. |
| `Loaded model bundles` | `:192` | Local | See 1.1. |
| `None reported` | `analysis-controls.ts:219` | Standard | Keep. |
| `Analysis features` (legend) | `index.html:202` | Standard | Keep. |
| `Select any configured feature. Required backbone steps are added automatically.` | `:202` | Local ("backbone") | "backbone" means something else in ML (a backbone network). The standard word for what is meant is **dependency** or **prerequisite step**. Propose `Prerequisite steps are added automatically.` P2. |
| `Ready` | `analysis-controls.ts:288` | Standard | Keep. |
| `Needs model or data` | `analysis-controls.ts:289` | Local, good | Accurate. But it is a dead end; see `model-gating.md`. |
| `Not in this server build` | `analysis-controls.ts:289` | Standard | Keep. |
| `Discovering server features.` | `index.html:204` | Standard | Keep. |
| `Server automatic profile` | `analysis-controls.ts:245` | Local | see 3. |
| `Named <id> profile` | `analysis-controls.ts:247` | Local | Propose `Server profile '<id>'`. P3. |

### 5.1 Feature names (`FEATURE_NAMES`, `analysis-config.ts:42-60`)

| Current | Verdict | Precedent | Proposal |
| --- | --- | --- | --- |
| `Language detection` | Standard | OpenNLP "Chapter 2. Language Detector" | keep |
| `Offset-aware normalization` | Local | "text normalization" is standard (ICU, spaCy); "offset-aware" is this project's own but is exactly right and has a direct analogue in Hugging Face tokenizers' *offset mapping* | keep |
| `Sentence detection` | Standard | OpenNLP "Chapter 3. Sentence Detector"; spaCy "Sentence Segmentation" | keep |
| `Tokenization` | Standard | OpenNLP "Chapter 4. Tokenizer"; spaCy "Tokenization" | keep |
| `Subword tokenization` | Standard | Hugging Face "subword tokenization" (`../reference/huggingface-tokenizer-summary.md`) | keep |
| `Named entities` | Standard | spaCy "Named Entity Recognition"; OpenNLP "Chapter 5. Name Finder" | keep |
| `Entity geocoding` | Standard | "geocoding" is the standard GIS term (Nominatim, GeoNames) | keep |
| `Part-of-speech tags` | Standard | OpenNLP "Chapter 7. Part-of-Speech Tagger" | keep |
| `Lemmas` | Standard | OpenNLP "Chapter 8. Lemmatizer"; spaCy "Lemmatization" | keep |
| `Stems` | Standard | Lucene `PorterStemFilter`, Snowball | keep |
| `Term vectors` | Standard | Elasticsearch Term Vectors API (`../reference/elasticsearch-term-vectors.md`) | keep |
| `Lexical expansion` | Local | the standard IR term is **query expansion** / **synonym expansion** (Lucene `SynonymGraphFilter`); this is document-side, so `Synonym expansion (WordNet)` is clearer | P2 |
| `Document categories` | Standard | OpenNLP "Chapter 6. Document Categorizer" | keep |
| `Sentence sentiment` | Standard | standard sentiment-analysis vocabulary | keep |
| `Constituency parses` | Standard | OpenNLP "Chapter 10. Parser"; CoreNLP "constituency parse" | keep |
| `Syntactic chunks` | Standard but colliding | see 4.1 | rename, P1 |
| `Document embeddings` | Standard | Hugging Face, Elastic | keep |

---

## 6. Actions and status

`index.html:207-225`.

| Current string | Verdict | Note |
| --- | --- | --- |
| `Analyze text` / `Analyzing` | Standard | `main.ts:1153`. Keep. |
| `Use short sample` | Standard | Keep. |
| `Load Alice novel` / `Load Pride and Prejudice` | Local | Keep; they name real public-domain texts. |
| `Normalization X-ray` | **Invented, P1** | See 6.1. |
| `Connect to the service and enter text to begin.` | Standard | Keep. |
| `Batch analyze (streaming)` | Standard | Keep. |
| `Paste several documents separated by blank lines.` | Standard | Keep. |
| `results arrive in completion order` | Standard | Keep. |
| `Analyze batch` | Standard | Keep. |
| `Document N: 5 sentences, 12 tokens` | Standard | `batch-analysis.ts:96-99`. Keep. |

### 6.1 FACT: "Normalization X-ray" has no prior art

The checkbox is `index.html:218-220`; the rendered heading string
`"Normalization X-ray"` is set in `normalization-xray.ts:91`. The panel shows
two synchronised panes, `Raw text` and `Normalized text`
(`normalization-xray.ts:110-113`), with per-run highlighting and a caption
`"N alignment runs, M changed"` (`normalization-xray.ts:97-98`).

That is a **text alignment view** or a **diff view**. Precedents for the exact
concept:

- Hugging Face tokenizers call the raw-to-normalized mapping the
  **offset mapping** / **alignment**
  (https://huggingface.co/docs/tokenizers/en/api/encoding, field
  `offsets`, and `NormalizedString` "alignments").
- ICU calls the transformation **normalization** and the recorded relation
  between the two strings a **mapping**
  (https://unicode-org.github.io/icu/userguide/transforms/normalization/).
- Every code review tool calls the two-pane display a **side-by-side diff**.

OPINION (P1): rename to `Normalization alignment` (checkbox label
`Show normalization alignment`), keep the two panes and their labels, and keep
`alignment runs` in the caption, which is already the right word and already
matches the wire field `normalization.alignment`
(`normalization-xray.ts:62`). "X-ray" survives nowhere else in the product and
gives a first-time reader no idea that the feature is about offsets.

Sub-labels in that panel:

| Current | Verdict | Note |
| --- | --- | --- |
| `Raw text` / `Normalized text` | Standard | keep |
| `N alignment runs, M changed` | Standard | keep |
| `No normalizers reported` | Standard | keep |
| `strip invisible`, `whitespace`, `quotes`, `dashes`, `digits`, `ellipsis`, `bullets` | Standard | derived from `NORMALIZER_*` at `normalization-xray.ts:224-226`. keep |
| `Run 3, replaced` / `Run 3, unchanged` | Standard | keep |

---

## 7. Result panel header and summary

`index.html:245-282`.

| Current string | Where | Verdict | Proposal |
| --- | --- | --- | --- |
| `Document shape` | `:248` | **Invented, P1** | see 7.1 |
| `Analysis result` | `:249` | Standard | keep |
| `Add to server workspace` | `:252` | Local | see `error-states-and-links.md` 1 |
| `Copy JSON` / `Download JSON` / `Download .pb` | `:253-255` | Standard | keep |
| `Open saved response` | `:280` | Standard | keep |
| `Ranked language predictions` | `:264` | Standard | keep; matches `rankedLanguages` on the wire |
| `Default models` (pipeline badge) | `main.ts:1115` | Local | propose `Default pipeline`; the badge names a routing outcome, not a model list. P3 |
| `Layers` | `:279` | Standard | INCEpTION uses "layers" for exactly this (`../reference/inception-annotation-layers.md`). keep |
| `Annotations` | `:280` | Standard | keep |
| `Offsets` | `:281` | Standard | keep |
| `UTF-16` / `Unicode code points` / `UTF-8 bytes` / `Not reported` | `document-shape.ts:328-341` | Standard | LSP `PositionEncodingKind` uses `utf-16`, `utf-32` ("the same as Unicode code points"), `utf-8` (`../reference/lsp-position-encoding.md`). keep |

### 7.1 FACT: "Document shape" is the label above every result

`index.html:248` renders the eyebrow `Document shape` above the heading
`Analysis result`. The same phrase appears in the graph canvas aria-label
(`index.html:360` `Document shape graph`), in status text
(`index.html:320` `Sentiment reads the current document shape.`), and in
`semantic-workbench.ts:250`
(`"Sending the analyzed document shape to the gRPC workspace index."`).

Searching NLP prior art, "document shape" is not used anywhere for "the typed
layers and annotations a pipeline produced". The concept has three established
names:

- **Annotation structure** or **annotation set** in Apache UIMA and INCEpTION
  (`../reference/inception-annotation-layers.md`).
- **Doc** / **document object** in spaCy
  (`../reference/spacy-linguistic-features.md` "linguistic annotations").
- **Analysis result** in CoreNLP (`Annotation` object).

OPINION (P1): replace the eyebrow with `Typed annotations` or delete it (the
heading `Analysis result` already carries the meaning). Replace the internal
uses with concrete wording:

- `index.html:320` -> `Sentiment reads the layers in the current result.`
- `semantic-workbench.ts:250` -> `Sending the analyzed document to the gRPC workspace index.`
- `index.html:360` aria-label -> `Annotation graph`.

The TypeScript type name `DocumentShapeView` can stay; it is not user-visible.

---

## 8. Result tabs and their panels

| Current string | Where | Verdict | Proposal |
| --- | --- | --- | --- |
| `Document` | `index.html:268` | Standard | keep |
| `Chunks` | `:270` | Standard | keep, once 4.1 lands |
| `Heatmap` | `:272` | Standard | keep; standard dataviz term |
| `Graph` | `:274` | Standard | keep |
| `Protobuf JSON` | `:276` | Standard | protobuf's own name for the format is "ProtoJSON"/"JSON Mapping" (https://protobuf.dev/programming-guides/json/); `Protobuf JSON` is close enough and clearer. keep |

### 8.1 FACT: "projection" is used for four different things

- `Chunk projections` heading, `index.html:310`.
- `Chunk projection` select label in the heatmap, `index.html:328`.
- `All projections, separate lanes` option, `index.html:330` and
  `semantic-workbench.ts:504`.
- `Projection` fact in the drawer for a search hit, `annotation-drawer.ts:228`,
  whose value is a `chunkGroupId`.
- `choose one layer to isolate that projection`, `index.html:296`, where it
  means "one annotation layer overlaid on the text".
- `Balanced overview` versus complete graph, `semantic-workbench.ts:581-586`,
  where the truncation is also described as a projection in
  `document-window.ts:41` ("buffered projection page").

In mathematics and in data science "projection" means a dimensionality-reducing
map (PCA projection, UMAP projection, t-SNE projection). A reader who knows
scikit-learn (https://scikit-learn.org/stable/modules/decomposition.html) will
expect a 2-D scatter of vectors when they click a tab called "Chunk
projections", and will get a column of text cards instead.

OPINION (P1): drop "projection" from the user-visible surface entirely.

| Current | Proposed | Why |
| --- | --- | --- |
| `Chunk projections` (`index.html:310`) | `Chunk groups` | matches the wire field `chunkEmbeddingGroups` and the layer `opennlp:chunk-groups` |
| `Compare every requested strategy over the same document` | keep | already correct |
| `Each column preserves its typed group identity, source span, and attached embedding count.` (`:311`) | `Each column keeps its group id, source span, and attached vector count.` | "typed group identity" is jargon |
| `Chunk projection` select (`:328`) | `Chunk group` | same |
| `All projections, separate lanes` | `All groups, separate lanes` | same |
| `Projection` drawer fact (`annotation-drawer.ts:228`) | `Chunk group` | the value already is a group id |
| `isolate that projection` (`:296`) | `isolate that layer` | |

### 8.2 Chunks view strings

| Current | Verdict | Note |
| --- | --- | --- |
| `Sentence chunks` / `Token windows` (group titles) | Local | from `resultSetName` at `analysis-config.ts:391,403`. keep |
| `Sentence` / `Token window` / `Semantic` / `Category` (strategy names) | Standard | `chunk-projection.ts:44-49`; matches Elastic's `sentence`/`word`/`recursive` family. keep |
| `#3 · 120..184` | Standard | keep |
| `No attached vector` / `2 attached vectors` | Standard | keep |
| `This strategy returned no chunks.` | Standard | keep |
| `No chunk groups were returned for this analysis.` | Standard | `chunk-projection-view.ts:41`. keep |

### 8.3 Heatmap view strings

| Current | Where | Verdict | Proposal |
| --- | --- | --- | --- |
| `Document heatmap` | `index.html:319` | Standard | keep |
| `Shade the same document by query similarity or by sentiment` | `:319` | Standard | keep |
| `Query similarity` / `Sentiment` mode buttons | `:324-325` | Standard | keep |
| `Find related chunks in this document` | `:332` | Standard | keep |
| `Score chunks` | `:336` | Standard | keep |
| `Cosine 0.8123` / `cosine 0.8123` | `document-heatmap-view.ts:104,128` | Standard | "cosine similarity" is standard; keep, but capitalise consistently. P3 |
| `Not returned` | `document-heatmap-view.ts:128` | Local | propose `Not scored`. P3 |
| `4 of 12 chunks scored. Coverage is partial; unreturned chunks remain gray.` | `document-heatmap-view.ts:63-66` | Standard | keep |
| `Sentence sentiment` lane title | `semantic-workbench.ts:727` | Standard | keep |
| `Select a colored segment to inspect its text and score.` | `index.html:346` | Standard | keep |
| `Similarity 0.8123 · characters 120 to 184 · ...` | `semantic-workbench.ts:479-482` | Standard | keep |

### 8.4 Graph view strings

| Current | Where | Verdict | Proposal |
| --- | --- | --- | --- |
| `Document graph` | `index.html:351` | Standard | keep |
| `Document, layer, and annotation relationships` | `:351` | Standard | keep |
| `Pan, zoom, or select an annotation node to return to its typed layer.` | `:353` | Local ("typed layer") | "typed" adds nothing for a reader; propose `... to jump back to its layer.` P3 |
| `Show complete graph` / `Show balanced overview` | `semantic-workbench.ts:579-580` | Local | "balanced overview" is a coinage but is self-explanatory. keep |
| `Complete graph limited for large documents` | `semantic-workbench.ts:579` | Standard | keep |
| `Balanced overview of 120 of 286938 annotations across every layer.` | `semantic-workbench.ts:583` | Standard | keep |
| `Document root` | `semantic-workbench.ts:596` | Standard | keep |
| `Layer: Tokens` / `Annotation: Paris` | `semantic-workbench.ts:597` | Standard | keep |

---

## 9. Document view and the layer browser

| Current | Where | Verdict | Note |
| --- | --- | --- | --- |
| `Filter layers` | `index.html:289` | Standard | keep |
| `Analyze text to discover layers` | `:287` | Standard | keep |
| `Highlights` | `main.ts:652` | Local, good | keep |
| `Entities and sentences only; select All annotations for every layer` | `main.ts:657` | Standard | keep |
| `All annotations` | `main.ts:670` | Standard | keep |
| `Combined projection of every returned annotation layer` | `main.ts:674` | Local ("projection") | -> `Every returned annotation layer, combined` (see 8.1). P2 |
| `Document-wide results` | `main.ts:796` | Local, good | the standard name is "document-level annotations" (INCEpTION "Document metadata"). Either works. keep |
| `Complete document, 144,569 characters` | `main.ts:897` | Standard | keep |
| `Characters 16,001 to 32,000 of 144,569, window 2 of 10` | `main.ts:898-899` | Standard | keep |
| `This layer has no selectable text spans in the current document window.` | `main.ts:775` | Standard | keep |
| `This document-scoped layer has no selectable text spans.` | `main.ts:776` | Standard | "document-scoped" matches `LAYER_SCOPE_DOCUMENT`. keep |
| `This analysis returned no document-shape layers.` | `main.ts:635` | Invented (see 7.1) | -> `This analysis returned no annotation layers.` P1 |
| `The response did not contain document text.` | `main.ts:630` | Standard | keep |

### 9.1 FACT: layer titles are derived and one of them is wrong

`document-shape.ts:318-326` turns a layer id into a title by splitting on `-`
and `_`, keeping all-caps parts of three characters or fewer, and title-casing
the rest. Measured against the demo's real layer ids
(`../reference/demo-state.md`):

| Layer id | Rendered title | Verdict |
| --- | --- | --- |
| `opennlp:sentences` | `Sentences` | fine |
| `opennlp:tokens` | `Tokens` | fine |
| `opennlp:pos` | **`Pos`** | wrong |
| `opennlp:lemmas` | `Lemmas` | fine |
| `opennlp:stopwords` | `Stopwords` | fine |
| `opennlp:terms:ACCENT_FOLD` | `Accent Fold` | opaque |
| `opennlp:terms:CASE_FOLD` | `Case Fold` | opaque |
| `opennlp:terms:NFC` | `NFC` | correct, acronym rule fires |
| `opennlp:terms:STEM` | `Stem` | collides with `Stems` |
| `opennlp:sentiment` | `Sentiment` | fine |
| `opennlp:language` | `Language` | fine |
| `opennlp:embeddings` | `Embeddings` | fine |
| `opennlp:analytics` | `Analytics` | fine |
| `opennlp:normalization` | `Normalization` | fine |
| `opennlp:chunk-groups` | `Chunk Groups` | fine |
| `opennlp:stems` | `Stems` | fine |
| `opennlp:term-vectors` | `Term Vectors` | fine |

Three problems, all visible on a default run:

1. **`Pos`**. The acronym rule at `document-shape.ts:321-323` only preserves a
   part that is *already* upper case in the id. `pos` is lower case, so it
   title-cases to `Pos`. Every other surface in the product says
   "Part-of-speech" or "POS" (`index.html:154`, `analysis-config.ts:51`,
   `model-data-workbench.ts` `roleLabel` `POS tagger`).
   OPINION (P1): map `STANDARD_LAYER_*` identities to display names instead of
   deriving them from the id. The response already carries
   `identity.standard = STANDARD_LAYER_POS_TAGS` for this layer, so the title
   can be `POS tags` with no guessing. The full mapping is already half-written
   in `FEATURE_NAMES`.

2. **`Stem` vs `Stems`**. `opennlp:terms:STEM` is one dimension of the term
   profile; `opennlp:stems` is the output of `PIPELINE_STEP_STEM`. Two adjacent
   buttons reading `Stem` and `Stems` with 16 annotations each is a coin flip
   for the reader.
   OPINION (P1): title term-profile layers as `Terms (stem)`,
   `Terms (case fold)`, `Terms (accent fold)`, `Terms (NFC)`. The qualifier is
   already parsed into `AnnotationLayerView.qualifier`
   (`document-shape.ts:263`) and is simply not used in the title.

3. **`Accent Fold` / `Case Fold`**. Both are real Lucene vocabulary
   (`ASCIIFoldingFilter`, `LowerCaseFilter`, and Lucene/ICU docs speak of "case
   folding" and "accent folding", see
   https://lucene.apache.org/core/10_1_0/analysis/common/org/apache/lucene/analysis/miscellaneous/ASCIIFoldingFilter.html
   and https://unicode.org/reports/tr15/ for NFC). The words are standard; what
   is missing is any hint that these four layers are alternative *term
   identities* for the same tokens.
   OPINION (P2): give the four term layers a shared group heading or a
   tooltip: `Normalized term identity used for matching`.

### 9.2 Value-type labels (`document-shape.ts:63-79`)

Shown in the drawer as `Value type` (`annotation-drawer.ts:85`) and used for
layer button titles (`main.ts:684`).

| Current | Verdict | Note |
| --- | --- | --- |
| `String`, `Category`, `Embedding`, `Parse tree`, `Subword`, `Word type`, `Named entity`, `Syntactic chunk`, `Stem`, `Normalization`, `Analytics`, `Chunk group`, `Term vector` | Standard | all map to established vocabulary |
| `Geographic result` | Local | propose `Geocoding result`; "geocoding" is the standard verb and already used in `FEATURE_NAMES`. P3 |
| `Lexical expansion` | Local | see 5.1; propose `Synonym expansion`. P2 |
| `Unknown` | Standard | keep |

---

## 10. Annotation drawer

`index.html:369-382`, filled by `annotation-drawer.ts`.

| Current | Where | Verdict | Proposal |
| --- | --- | --- | --- |
| `Typed annotation` (eyebrow) | `index.html:374` | Local | "annotation" alone is the standard word; "typed" is implementation detail. Propose `Annotation`. P3 |
| `Selection details` | `:375` | Standard | keep |
| `Choose a highlighted span to inspect its annotation.` | `:380` | Standard | keep |
| `Layer` / `Value type` / `Browser span` / `Probability` / `Score` | `annotation-drawer.ts:85-95` | Mixed | `Browser span` is local; it means "offsets converted to JavaScript string indices". Propose `Span (UTF-16)`, which names the encoding the way LSP does. P2 |
| `Recognized by` | `annotation-drawer.ts:97` | Standard | keep |
| `Value` + `Copy JSON` | `annotation-drawer.ts:310-318` | Standard | keep |
| `Granularity`: `Document` / `Sentence` / `Chunk` / `Group centroid` | `annotation-drawer.ts:499-507` | Standard | "centroid" is standard (scikit-learn `NearestCentroid`, https://scikit-learn.org/stable/modules/generated/sklearn.neighbors.NearestCentroid.html). keep |
| `Dimensions` / `First 3 values` / `Copy vector` | `annotation-drawer.ts:468-479` | Standard | keep |
| `Unidentified model` | `annotation-drawer.ts:459` | Local | propose `Model not reported`, matching `Not reported` elsewhere. P3 |
| `Cosine score` | `annotation-drawer.ts:227` | Standard | keep |
| `Search provider` / `Index` / `Model` / `Serving backend` | `annotation-drawer.ts:227-234` | Standard | keep |
| `Vector space` | `annotation-drawer.ts:234` | Standard | the vector space model is classic IR (Salton 1975); Elastic/Vespa both speak of a vector space. keep |
| `Corpus` / `Provenance` / `License` / `Model artifact` | `annotation-drawer.ts:235-242` | Standard | keep |
| `Preparation` (value is `preparationConfigHash`) | `annotation-drawer.ts:243` | Local | propose `Preparation config`. P3 |
| `3 category predictions, ranked by confidence.` | `annotation-drawer.ts:139-141` | Standard | keep |
| `15 distinct terms, 42 occurrences, ranked by frequency.` | `term-vector-stack.ts:157-160` | Standard | matches Elasticsearch's "term frequency". keep |
| `No positional annotations intersect this chunk.` | `annotation-drawer.ts:247` | Standard | "positional" reads as INCEpTION's "span layer"; acceptable. keep |

### 10.1 FACT: "Term vector stack"

`term-vector-stack.ts` renders a single stacked bar per term-vector layer,
segments sized by frequency, with a `+N` remainder
(`term-vector-stack.ts:126-132`). The user-visible string is only
`"<Layer title>: 15 distinct terms, 42 occurrences"`
(`term-vector-stack.ts:112`); the phrase "term vector stack" never reaches the
screen. It exists as a CSS class and a module name.

OPINION (P3): nothing user-visible to fix. If the phrase ever surfaces, the
standard name for the graphic is a **stacked bar chart**, and the standard name
for the data is a **term frequency distribution**.

---

## 11. Terms the brief asked about that do not appear on this tab

FACT: grepping `index.html` and `src/` for the following found no user-visible
string on the Analyze tab.

| Term asked about | Result |
| --- | --- |
| `resolved route` | Not present. The wire field `embedding.route` exists (`chunk-projection.ts:73`) but is never labelled "route" in the UI. |
| `document centroid` | Not present as a label. `includeDocumentCentroid` is set in the request (`analysis-config.ts:247`) and `documentCentroids` comes back on the wire, but no view renders it. See Questions. |
| `vector space` | Present only in the drawer for a *search hit* (`annotation-drawer.ts:234`) and on the Lifecycle tab (`lifecycle-workbench.ts:178`). Standard term, no change. |
| `positional layer` | Not present verbatim; "document-scoped layer" and "positional annotations" are (`main.ts:776`, `annotation-drawer.ts:247`). |
| `typed layer` | Present at `index.html:353` and `charts.ts:105`. See 8.4. |

---

## 12. Recommendation summary

| Priority | Change | Files |
| --- | --- | --- |
| P1 | `Document shape` -> `Typed annotations` (or delete the eyebrow); fix the three internal reuses | `index.html:248,320,360`, `main.ts:635`, `semantic-workbench.ts:250` |
| P1 | `Normalization X-ray` -> `Normalization alignment` | `index.html:220`, `normalization-xray.ts:91` |
| P1 | Drop "projection" from all six user-visible uses | `index.html:296,310,311,328,330`, `semantic-workbench.ts:504`, `annotation-drawer.ts:228` |
| P1 | Layer titles from `identity.standard`, not from the id, so `Pos` becomes `POS tags` | `document-shape.ts:318-326` |
| P1 | Title term-profile layers with their qualifier: `Terms (stem)` etc, so `Stem` stops colliding with `Stems` | `document-shape.ts:318-326` |
| P1 | Split the two meanings of "chunk": `Syntactic chunks` -> `Phrase chunks (shallow parse)`, legend `Chunk output` -> `Passage chunking` | `analysis-config.ts:59`, `index.html:166`, `model-data-workbench.ts` `roleLabel` |
| P1 | `Bundles` / `Loaded model bundles` -> `Model packs` / `Loaded model packs`, and `Profile: x` -> `Preset: x (server profile)` | `index.html:90,192`, `analysis-controls.ts:198` |
| P2 | `Lexical expansion` -> `Synonym expansion (WordNet)` | `analysis-config.ts:55`, `document-shape.ts:78` |
| P2 | `Browser span` -> `Span (UTF-16)` | `annotation-drawer.ts:87` |
| P2 | `Required backbone steps` -> `Prerequisite steps` | `index.html:202` |
| P2 | `Installed language model sets` -> `Installed language model packs` | `index.html:150` |
| P2 | Group heading or tooltip for the four `opennlp:terms:*` layers | `document-shape.ts` / `main.ts:676-686` |
| P3 | `Server automatic` -> `Server default profile`; `Named x profile` -> `Server profile 'x'` | `index.html:132`, `analysis-controls.ts:245-247` |
| P3 | `Window (tokens)` -> `Chunk size (tokens)` | `index.html:177` |
| P3 | `Geographic result` -> `Geocoding result`; `Unidentified model` -> `Model not reported`; `Not returned` -> `Not scored`; `Preparation` -> `Preparation config`; `Typed annotation` -> `Annotation` | `document-shape.ts:73`, `annotation-drawer.ts:243,471`, `document-heatmap-view.ts:128`, `index.html:374` |

---

## Questions for the lead

1. Should service profile ids and model bundle ids be allowed to collide
   (`en-basic` is both, with different behaviour)? If the service can namespace
   them, the front end problem in 1.1 disappears without a rename.
2. `en-basic` as a *profile* runs only sentence detection and tokenization,
   although its bundle carries POS, lemma, language detection and embedding
   models. Is that intended, or is the profile under-specified on the demo
   image? It affects whether `Profile: en-basic` is worth offering at all.
3. The request always asks for `includeDocumentCentroid: true`
   (`analysis-config.ts:247`) and the response carries `documentCentroids`, but
   no view shows it and no label names it. Should the centroid get a surface,
   or should the request stop asking for it? Dropping it would shrink the
   response measurably on the novel-sized samples.
4. Do you want "chunk" reserved for the retrieval sense product-wide (my
   proposal in 4.1), or reserved for OpenNLP's shallow parser sense, with the
   retrieval one renamed to "passage"? Either is defensible; the current state
   of using both is not.
