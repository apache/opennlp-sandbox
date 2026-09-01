Source: https://huggingface.co/docs/transformers/en/tokenizer_summary
Fetched: 2026-08-28

Quoted terminology: "subword tokenization", "Byte pair encoding (BPE)",
"WordPiece", "SentencePiece", "Unigram", "vocabulary", "pre-tokenizer".

Definition, quoted: subword tokenization algorithms "split text into units
between words and characters, keeping the vocabulary compact while still
capturing meaningful pieces. Common words stay intact as single tokens, and rare
or unknown words decompose into subwords."

Example given: "`annoyingly` might be split into `["annoying", "ly"]` or
`["annoy", "ing", "ly"]` depending on the vocabulary."

Relevance: "subword" and "subword tokenization" are standard vocabulary; the
workbench's "Subword tokenization" feature name needs no change. The word
"piece" used by the workbench's annotation label reader
(`document-shape.ts` label key `piece`) also matches WordPiece/SentencePiece
usage.
