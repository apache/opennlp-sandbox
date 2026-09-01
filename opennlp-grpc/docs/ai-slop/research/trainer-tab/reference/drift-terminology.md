# Drift Terminology: Data Drift, Concept Drift, and "Vocabulary Drift"
Source: https://www.evidentlyai.com/ml-in-production/data-drift
Fetched: 2026-08-28

Additional sources fetched in the same pass are listed inline below, each
with its own Source line, plus one FETCH NOTE for a source that could not
be reached directly.

## Evidently: data drift vs. concept drift

Data drift definition: "A change in the statistical properties and
characteristics of the input data" occurring when production data deviates
from training data, potentially causing model performance decline.

Concept drift definition: changes in the relationship between inputs and
target - "whatever your model is predicting - it is changing." Evidently
explicitly distinguishes this from data drift: distributions may shift
without concept drift occurring, or vice versa.

Important honesty note from Evidently's own docs: "none of these terms is
strictly defined! Examining them helps grasp various factors that impact a
machine learning model in production. However, in the real world, these
semantic distinctions are rarely important." So even the vendor whose
business is drift monitoring says the field does not have crisp, universally
agreed definitions for "data drift" vs. "concept drift" vs. "covariate
shift" - treat all of these as loosely-used umbrella terms, not
rigorously standardized ones.

Working summary distinction from Evidently: data drift = input changes;
concept drift = input-output relationship changes. "Covariate shift" was
not explicitly defined in the fetched content (it is generally used in the
wider ML literature as a near-synonym for data/feature drift restricted to
the input distribution, but that synonym relationship was not confirmed in
this specific source).

## Evidently: text-specific drift terminology

Source: https://www.evidentlyai.com/blog/tutorial-detecting-drift-in-text-data
Fetched: 2026-08-28

Key terms used for monitoring drift specifically in text data:

- "Text Descriptors" - "descriptive statistics of the text data (such as
  length of text, the share of out-of-vocabulary words, and the share of
  non-letter symbols)," tracked via a "Text Descriptors Drift" metric.
- "Out-of-vocabulary rate" - phrased as "share of out-of-vocabulary words";
  the tutorial's own example flags "over 30% of out-of-vocabulary words" as
  evidence of distribution shift.
- "Embeddings drift" - named as a separate, complementary technique:
  "you might need to monitor drift in embeddings instead of raw text data."
- "Domain classifier" method - "a background model trained to distinguish
  between the reference and the current dataset," scored via ROC AUC.

Notably, Evidently's own text-drift material does NOT use the phrase
"vocabulary drift" as a named metric. Their term for the vocabulary-related
signal is "share of out-of-vocabulary words" / "out-of-vocabulary rate,"
tracked as one descriptive statistic among several, not a standalone named
"drift type."

## Alibi Detect

FETCH NOTE: direct fetch of https://docs.seldon.io/projects/alibi-detect/en/stable/cd/methods.html
failed with a DNS resolution error (getaddrinfo ENOTFOUND). The
information below is a search-result snippet, not a fetched page, and
should be treated as lower-confidence than the other entries in this file.

Search-result snippet (from a web search, not a direct fetch): Alibi
Detect is described as "a source-available Python library focused on
outlier, adversarial and drift detection that covers both online and
offline detectors for tabular data, text, images and time series." For
text specifically, the snippet states that "detecting input data drift
(covariate shift) for text data requires a custom preprocessing step, and
changes in semantics can be picked up by extracting contextual embeddings
and detecting drift on those." This snippet treats "covariate shift" as a
parenthetical synonym for "input data drift," consistent with the general
ML usage note above, though this could not be verified against the
original page text.

## Is "vocabulary drift" a real term in the literature? Yes, with a precise definition

Source: https://arxiv.org/abs/2305.17127 ("Characterizing and Measuring
Linguistic Dataset Drift")
Fetched: 2026-08-28

This paper's abstract states directly: "we propose three dimensions of
linguistic dataset drift: vocabulary, structural, and semantic drift."

Their own precise definitions of the three dimensions, quoted from the
abstract: "These dimensions correspond to content word frequency
divergences, syntactic divergences, and meaning changes not captured by
word frequencies (e.g. lexical semantic change)."

So mapped out:
- Vocabulary drift = "content word frequency divergences."
- Structural drift = "syntactic divergences."
- Semantic drift = "meaning changes not captured by word frequencies,"
  explicitly tied to the older "lexical semantic change" literature.

A companion web-search snippet on this same paper elaborates the definition
further: "Vocabulary drift is defined as the divergence between content
word frequencies in two text samples. Content words are open class words
that generally contain substantial semantic content (e.g. nouns, verbs,
adjectives, and adverbs), contrasted with function words that primarily
convey grammatical relationships (e.g. prepositions, conjunctions, and
pronouns)." It adds a clarifying example: "'The dog was happy' and 'The
beagle was ecstatic' would have high vocabulary drift due to differing word
choice, despite their high semantic similarity."

Conclusion: "vocabulary drift" IS a real, defined term in NLP dataset-shift
literature (2023), but it is a fairly specific academic dimension (content
word frequency divergence) rather than a widely-adopted industry monitoring
term. Mainstream drift-monitoring tooling (Evidently, and per the search
snippet, Alibi Detect) does not appear to use "vocabulary drift" as a named
metric; they use "out-of-vocabulary rate" or "covariate/data shift" on
embeddings instead.

## Semantic drift / semantic change / diachronic word embeddings

Source: https://arxiv.org/abs/1605.09096 ("Diachronic Word Embeddings
Reveal Statistical Laws of Semantic Change")
Fetched: 2026-08-28

Abstract (fetched verbatim): "Understanding how words change their
meanings over time is key to models of language and cultural evolution,
but historical data on meaning is scarce, making theories hard to develop
and test. Word embeddings show promise as a diachronic tool, but have not
been carefully evaluated. We develop a robust methodology for quantifying
semantic change by evaluating word embeddings (PPMI, SVD, word2vec)
against known historical changes. We then use this methodology to reveal
statistical laws of semantic evolution. Using six historical corpora
spanning four languages and two centuries, we propose two quantitative
laws of semantic change: (i) the law of conformity - the rate of semantic
change scales with an inverse power-law of word frequency; (ii) the law of
innovation - independent of frequency, words that are more polysemous have
higher rates of semantic change."

This paper's preferred terms are "semantic change," "diachronic," and
"semantic evolution" - not "drift" and not "vocabulary drift." This
confirms that the corpus-linguistics / diachronic-semantics tradition
names this phenomenon "semantic change" (or "lexical semantic change"),
while "drift" as a word is more of an ML-monitoring-community term. The
overlap point is the 2023 "Characterizing and Measuring Linguistic Dataset
Drift" paper above, which explicitly cites "lexical semantic change" as
the definition of its "semantic drift" dimension - i.e. it is the bridge
between the two vocabularies, not evidence that the diachronic-semantics
field itself says "drift."

A related web-search snippet (not independently fetched as a full paper)
also surfaced "lexical drift," defined as "how much the vocabulary
distribution has shifted, computed by tokenizing both baseline and current
responses and computing token frequency distributions," sometimes measured
via Jensen-Shannon divergence. Treat this as a secondary, less-established
term compared to "vocabulary drift" (which has the peer-reviewed 2023
definition above) and "semantic change" (which has the peer-reviewed 2016
definition above).

## Bottom line for terminology choices

- Standard, well-established, loosely-defined-by-their-own-vendors' -
  admission terms: "data drift," "concept drift." Evidently itself says
  these lack strict definitions in practice.
- "Covariate shift" is used in the wider literature as roughly a synonym
  for input/data drift, but this could not be pinned to an exact
  definition in the primary sources checked here.
- "Out-of-vocabulary rate" / "share of out-of-vocabulary words" is the
  actual, concretely-used industry term (Evidently) for vocabulary-related
  drift signals in text.
- "Vocabulary drift" DOES exist as a defined academic term (content word
  frequency divergence, per arXiv:2305.17127, 2023) but is not the
  mainstream industry-tooling name for the same underlying signal.
- "Semantic drift" / "semantic change" / "lexical semantic change" is the
  correct family of terms for meaning-shift-over-time, with roots in
  diachronic linguistics (arXiv:1605.09096, 2016) predating the ML drift-
  monitoring vocabulary.
