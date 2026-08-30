Source: https://spacy.io/usage/linguistic-features
Fetched: 2026-08-28

Excerpt of the terminology spaCy uses for its pipeline features. Section headings,
verbatim:

- Tokenization
- Part-of-speech tagging
- Morphology
- Lemmatization
- Dependency Parsing
- Named Entity Recognition
- Entity Linking
- Sentence Segmentation
- Merging and splitting
- Noun chunks

Definitions quoted from the page:

- Tokenization: "splitting a text into meaningful segments, called *tokens*".
- Named Entity Recognition: "assigns labels to contiguous spans of tokens".
- Lemmatization: the lemma is "the base form of the word".
- Noun chunks: "base noun phrases", phrases with a noun as head.

Relevant negative result: the page does not use "layer" or "annotation layer" for
the attributes hanging off a `Doc`. It calls them "linguistic annotations" and
"token-level attributes". So "layer" is not spaCy vocabulary; see
`inception-annotation-layers.md` for where "layer" is standard.
