Source: https://www.elastic.co/docs/explore-analyze/elastic-inference/inference-api
Fetched: 2026-08-28

Chunking settings, quoted:

  "Chunking is the process of splitting the input text into pieces that remain
  within these limits."

Parameters, verbatim:

- `strategy`
  - `"sentence"`: "splits the input text at sentence boundaries"
  - `"word"`: "splits the input text on individual words"
  - `"recursive"`: "splits the input text based on a configurable list of
    separator patterns"
  - `"none"`: "disables chunking and processes the entire input text as a single
    block"
- `max_chunk_size`
- `sentence_overlap`: "defines the number of sentences from the previous chunk to
  include in the current chunk which is either `0` or `1`"
- `overlap` (word strategy): "the number of words from the previous chunk to
  include in the current chunk"

Relevance: "chunk", "chunking strategy", "chunk size", and "overlap" are all
standard retrieval vocabulary in this sense. "Sentence" and "word" are the
standard strategy names; the workbench calls the second one "Token windows".
