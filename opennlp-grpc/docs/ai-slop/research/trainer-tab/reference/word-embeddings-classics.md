# Word Embeddings Naming Precedent: word2vec, GloVe, fastText
Fetched: 2026-08-28

This single file covers three classic sources, each with its own Source
line below.

## word2vec

Source: https://arxiv.org/abs/1301.3781

Mikolov, Chen, Corrado, Dean. "Efficient Estimation of Word Representations
in Vector Space."

Abstract opening: "We propose two novel model architectures for computing
continuous vector representations of words from very large data sets."

The paper reports achieving "high quality word vectors from a 1.6 billion
words data set" in under a day of training, evaluated via a word similarity
task, and demonstrating both syntactic and semantic word similarity
performance.

Terminology: the paper's own phrase is "continuous vector representations
of words" and "word vectors." "Word embeddings" is the term the broader
field later settled on for the same objects; word2vec's own abstract
prefers "vector(s)" and "representations" over "embeddings," though
"embeddings" is used elsewhere in the paper body and is now the standard
retrospective label for this entire family of techniques.

## GloVe

Source: https://nlp.stanford.edu/projects/glove/

GloVe is described as "an unsupervised learning algorithm for obtaining
vector representations for words," trained "on aggregated global
word-word co-occurrence statistics from a corpus."

Output framing: the resulting representations "showcase interesting linear
substructures of the word vector space," where "vector differences capture
as much as possible the meaning specified by the juxtaposition of two
words."

Technical framing: "essentially a log-bilinear model with a weighted
least-squares objective," built on the idea that "ratios of word-word
co-occurrence probabilities have the potential for encoding some form of
meaning."

Vocabulary and scale: pre-trained GloVe models are described by their
vocabulary size (400K to 2.2M words) and vector dimensionality (50d to
300d), depending on the training corpus. This is a direct precedent for
describing a model by "vocabulary size" and "dimensionality" as its two
headline scale numbers.

## fastText

Source: https://fasttext.cc/

fastText is described as "an open-source, free, lightweight library that
allows users to learn text representations and text classifiers."

It explicitly supports two things: "Learning text representations (word
embeddings)" and "Training text classifiers," and can run "on standard
hardware" with models compressible for mobile deployment.

Pre-trained resources are named "word vectors": "English word vectors
trained on webcrawl and Wikipedia data" and "Multi-lingual word vectors
covering 157 languages."

fastText's technical foundation (per its own reference papers) is subword
information - representing words as bags of character n-grams so that
rare or unseen words can still get a vector composed from their known
subword pieces. This is the standard origin of "subword" as a term in word
embedding literature, and it is also the mechanism that lets fastText
handle out-of-vocabulary (OOV) words: an OOV word can still be embedded by
summing/averaging the vectors of its known subword n-grams, rather than
having no representation at all.

## Summary of naming precedent (all three)

- "Word vectors" and "word embeddings" are used near-interchangeably across
  this literature; word2vec and fastText both prefer "word vectors" as the
  concrete deliverable, with "embeddings" as the parenthetical/field-level
  synonym.
- "Vocabulary" is the standard term for the fixed set of known words/tokens
  a model has vectors for (see GloVe's "vocabulary size" framing).
- "Subword" is fastText's term for sub-token units (character n-grams) used
  to build robustness to unseen words.
- "Out-of-vocabulary (OOV)" is the standard term for a word not present in
  a model's vocabulary; fastText's subword mechanism is the classic
  technique for mitigating OOV failure, though the abbreviation "OOV"
  itself is standard field vocabulary rather than a fastText-coined term.
