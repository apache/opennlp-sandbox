# opennlp-embeddings javadoc excerpts (ModelDistiller, StaticEmbeddingModel, VocabularyLearner, TermCount)

Source: opennlp-embeddings-3.0.0-OPENNLP-1833-SNAPSHOT-javadoc.jar, resolved from the
local Maven repository as the `org.apache.opennlp:opennlp-embeddings` dependency of
opennlp-grpc-service (opennlp-grpc-service/pom.xml:68)
Fetched: 2026-08-28

This is the library the Trainer tab actually drives. It is not part of this repository,
so its wording is the closest thing to an upstream naming authority for the tab.

## opennlp.embeddings.ModelDistiller (class javadoc, verbatim)

> Distills a sentence-transformer teacher into a static embedding table in the layout
> `StaticEmbeddingModel.load(Path)` opens, reproducing Model2Vec's distillation in Java
> so no Python environment is needed. The pipeline is Model2Vec's:
>
> - The teacher's vocabulary is cleaned (unused tokens and special added tokens other than
>   the unknown and pad tokens are dropped, the rest keeps its id order) and every surviving
>   token is run through the teacher's ONNX graph as `[bos, token, eos]`; the token's
>   embedding is the mean of the last hidden states.
> - The matrix is projected onto its top principal components (a randomized SVD standing in
>   for scikit-learn's dense one; see `RandomizedPca`).
> - Each row is scaled by its Zipf weight `sif / (sif + p)`, where `p` is the row's share of
>   a Zipf distribution over the vocabulary and `sif` is `1.0E-4`, Model2Vec's default.
> - The result is written as `model.safetensors` (F32), the cleaned `tokenizer.json`, and a
>   `config.json` with `"normalize": true`; a SentencePiece teacher's `.model` file is copied
>   alongside. The directory is then completed and verified by `ModelAssembler`.
>
> The teacher directory must hold `tokenizer.json` and `onnx/model.onnx` (the ONNX export
> every sentence-transformer ships on the Hugging Face hub); a local `tokenizer_config.json`
> supplies the pad token when present.

Marked `@Experimental`.

## ModelDistiller.distill(Path, Path, int, List, ProgressListener), on terms

> Distills a teacher into a model directory with additional term rows: whole words and
> multi-word phrases (a learned corpus vocabulary) that are segmented by the teacher's own
> tokenizer, run through the teacher as full sequences, and appended to the table after the
> subword rows. The loaded model then matches text against these terms greedily
> longest-first before falling back to subword pieces.
>
> Each term is normalized to lower-cased words joined by single spaces before use; terms
> that normalize to the same form are distilled once, and a term equal to a surviving
> vocabulary token is dropped, because its row would duplicate that token's. The terms are
> written to the model directory as `terms.txt`, one per line in row order, and should
> arrive sorted by descending corpus frequency: the Zipf weighting spans the subword rows
> and the term rows as one ranking.

On `pcaDims`:

> The number of principal components to keep; clamped to the teacher's hidden dimension,
> and skipped entirely when it would not reduce a tiny vocabulary. Model2Vec's default
> (and the recommended value) is 256.

On the teacher reference form:

> a local directory is used as-is, a Hugging Face model id (`org/model`, or
> `org/model@revision` to pin a revision) is downloaded into a local cache on first use.

## ModelDistiller.Result (record components, verbatim)

> - `family` - "WordPiece" or "SentencePiece".
> - `vocabularySize` - The number of subword rows in the distilled table.
> - `termCount` - The number of term rows appended after the subword rows.
> - `teacherDimension` - The teacher's hidden dimension.
> - `dimension` - The distilled table's dimension (after PCA).
> - `explainedVarianceRatio` - The share of the embedding variance the PCA kept.

## opennlp.embeddings.StaticEmbeddingModel (class javadoc, excerpts)

> A static (non-contextual) sentence embedding model: a per-token vector table plus subword
> tokenization. Embedding a sentence is tokenize, gather each piece's row, optionally weight,
> mean-pool, and optionally L2-normalize; there is no model forward pass.
>
> It loads distilled tables in the Model2Vec release layout for both tokenizer families:
> WordPiece models carry a `vocab.txt` whose line number is the matrix row, and Unigram
> models carry a `tokenizer.json` whose `model.vocab` list order is the row order.
> ... the `model.safetensors` holds one 2-D float matrix, with an optional per-token
> `weights` tensor.
>
> A model directory may additionally carry a `terms.txt`: whole words and multi-word phrases
> distilled through the teacher as units, owning the matrix rows after the subword rows.
> ... Term matching is case-insensitive regardless of the subword tokenizer's casing.

Accessors: `dimension()`, `vocabularySize()` ("the number of subword tokens in this model's
vocabulary, without term rows"), `termCount()` ("the number of term rows appended after the
subword vocabulary, 0 for a model without a term table"), plus `similarity`, `mostSimilar`
and `analogy` (the last documented as "The classic word2vec analogy").

## opennlp.embeddings.corpus.VocabularyLearner (class javadoc, verbatim)

> Learns a vocabulary from corpus texts and dictionary headwords: every dictionary term with
> its corpus frequency, plus the corpus words frequent enough to keep.
>
> Texts fold to lower case and split into words (maximal runs of letters and digits).
> Dictionary headwords fold and split the same way, so multi-word headwords become word
> sequences, and the scan counts them by greedy longest match: at each word, the longest
> dictionary sequence starting there wins and consumes its words, so "habeas corpus" counts
> as one term and neither "habeas" nor "corpus" is counted for it. Words not consumed by a
> dictionary term count individually.
>
> The result lists every dictionary term first (highest count first, zero-count terms
> included), then the remaining corpus words with at least the configured minimum frequency
> (highest count first, ties in first-seen order), truncated to the configured maximum size.
> Dictionary terms are never truncated, even when they alone exceed the maximum.

Constructor parameters: `minFrequency` ("The smallest corpus frequency that keeps a
non-dictionary word. Must be at least one.") and `maxTerms` ("The largest result size,
dictionary terms exempt.").

## opennlp.embeddings.corpus.TermCount (record javadoc, verbatim)

> One learned vocabulary term with its corpus frequency.
>
> The on-disk interchange form is a TSV file with one term per line,
> `term<TAB>count<TAB>source`, where source is `dictionary` for terms kept because a law
> dictionary lists them and `corpus` for terms kept by frequency. Terms are case-folded;
> multi-word terms join their words with single spaces.
