# scikit-learn CountVectorizer: Vocabulary and Frequency Parameters
Source: https://scikit-learn.org/stable/modules/generated/sklearn.feature_extraction.text.CountVectorizer.html
Fetched: 2026-08-28

This is the standard precedent in machine learning tooling for the phrases
"learn a vocabulary," "min frequency," and "max terms" style parameters.
TfidfVectorizer inherits the same vocabulary-building behavior and parameter
names as CountVectorizer.

## `vocabulary` parameter (exact docstring wording)

> vocabulary : Mapping or iterable, default=None
>
> Either a Mapping (e.g., a dict) where keys are terms and values are
> indices in the feature matrix, or an iterable over terms. If not given, a
> vocabulary is determined from the input documents. Indices in the mapping
> should not be repeated and should not have any gap between 0 and the
> largest index.

Key point: if you do not supply your own `vocabulary`, scikit-learn's own
wording is "a vocabulary is determined from the input documents" - i.e. the
vocabulary is learned/derived from data, not assumed to be a fixed external
list.

## `vocabulary_` fitted attribute (exact docstring wording)

> vocabulary_ : dict
>
> A mapping of terms to feature indices.

This is the standard naming convention in scikit-learn: a trailing
underscore marks an attribute that only exists after `fit()` has been
called, and `vocabulary_` is the canonical example of a "vocabulary learned
during fitting" attribute name.

## `min_df` parameter (exact docstring wording)

> min_df : float in range [0.0, 1.0] or int, default=1
>
> When building the vocabulary ignore terms that have a document frequency
> strictly lower than the given threshold. This value is also called
> cut-off in the literature. If float, the parameter represents a
> proportion of documents, integer absolute counts. This parameter is
> ignored if vocabulary is not None.

Note scikit-learn's own aside that "cut-off" is a synonym used "in the
literature" for a minimum document frequency threshold.

## `max_df` parameter (exact docstring wording)

> max_df : float in range [0.0, 1.0] or int, default=1.0
>
> When building the vocabulary ignore terms that have a document frequency
> strictly higher than the given threshold (corpus-specific stop words). If
> float, the parameter represents a proportion of documents, integer
> absolute counts. This parameter is ignored if vocabulary is not None.

Note the parenthetical: overly frequent terms filtered by `max_df` are
explicitly characterized as "corpus-specific stop words."

## `max_features` parameter (exact docstring wording)

> max_features : int, default=None
>
> If not None, build a vocabulary that only consider the top max_features
> ordered by term frequency across the corpus. Otherwise, all features are
> used.
>
> This parameter is ignored if vocabulary is not None.

This is the direct precedent for a "max terms" style cap: keep only the top
N most frequent terms when building the vocabulary.

## "Learn the vocabulary" / fit wording

- `fit()` method description: "Learn a vocabulary dictionary of all tokens
  in the raw documents."
- `fit_transform()` method description: "Learn the vocabulary dictionary
  and return document-term matrix."

Both confirm scikit-learn's own verb for building a vocabulary from data is
"learn" ("learn a vocabulary," "learn the vocabulary dictionary"), and this
is explicitly framed as part of `fit` (i.e. training), not a separate
preprocessing step outside the model lifecycle.

## Summary of naming precedent

- Building a vocabulary from data is called "building the vocabulary" in
  parameter docs and "learn a/the vocabulary" in method docs - both are
  scikit-learn's own words, not paraphrase.
- `min_df` = minimum document frequency threshold, aka "cut-off" in the
  literature per sklearn's own docstring.
- `max_df` = maximum document frequency threshold; terms above it are
  treated as "corpus-specific stop words."
- `max_features` = cap on vocabulary size, keeping the top-N terms by
  frequency across the corpus - the direct precedent for "max terms."
- `vocabulary_` (trailing underscore) = the fitted attribute exposing the
  final learned term-to-index mapping.
