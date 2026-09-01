# Sentence Transformers: Static Embeddings and Distillation
Source: https://sbert.net/examples/sentence_transformer/training/distillation/README.html
Fetched: 2026-08-28

Secondary source fetched in the same pass:
Source: https://huggingface.co/blog/static-embeddings
Fetched: 2026-08-28

## Teacher / student terminology (sbert.net distillation docs)

This page uses "teacher" and "student" explicitly and defines them:

"a slow (but well performing) teacher model and a fast student model. The
fast student model imitates the teacher model and achieves by this a high
performance."

"Knowledge distillation describes the process to transfer knowledge from a
teacher model to a student model."

This is the clean, canonical teacher/student pairing (unlike Model2Vec's own
README, which prefers "base model"). Sentence Transformers' docs are a
better precedent than Model2Vec's own README if the goal is to justify
"teacher model" / "student model" as the input/output naming pair.

## Distillation methods named on this page

1. Transformer-based approach: lightweight architectures like TinyBERT,
   trained with `EmbedDistillLoss`, described as including "an optional
   learnable projection so the student and teacher don't have to share an
   embedding dimension."
2. Layer reduction approach: "Take the teacher model and keep only certain
   layers, for example, only 4 layers. Trained with `MSELoss`."

Trade-off noted: "Smaller models are faster, but show a (slightly) worse
performance when evaluated on down stream tasks."

Note: this specific sbert.net distillation page, as fetched, is oriented
around transformer-to-smaller-transformer distillation (TinyBERT-style,
layer reduction) rather than the Model2Vec static-vector style of
distillation. It does not itself use the phrase "static embedding model" or
name "Model2Vec distillation" as a technique - see the Hugging Face blog
post below for that vocabulary.

## Static Embedding Models (Hugging Face blog: "static-embeddings")

"Static Embeddings refers to a group of Encoder models that don't use large
and slow attention-based models, but instead rely on pre-computed token
embeddings."

Mechanism described: these models use dictionary/token lookups instead of
transformer attention at inference time, enabling "speedups of several
orders of magnitude."

## Model2Vec cross-reference

"Recently, Model2Vec has been used to convert pre-trained embedding models
into Static Embedding models."

The Sentence Transformers `StaticEmbedding` module supports loading
Model2Vec models directly: `StaticEmbedding.from_model2vec`.

It also supports training new static embeddings via distillation:
`StaticEmbedding.from_distillation` "to perform Model2Vec-style
distillation" - this is the closest the source material comes to naming
"Model2Vec distillation" as a technique name, phrased as an adjective
("Model2Vec-style") rather than a fixed noun phrase.

## Teacher/student framing in the static-embeddings context

The blog frames future work in classic teacher/student terms even though it
does not always use those exact words on first mention: "we can also feed
unsupervised data through a larger embedding model and distil those
embeddings into the static embedding-based student model." Here "a larger
embedding model" is the (unnamed but implied) teacher, and "the static
embedding-based student model" is named explicitly as "student model."

## Summary of naming findings

- Canonical pairing across Sentence Transformers docs: "teacher model" and
  "student model," each spelled out in full definitions on the distillation
  page.
- "Static embedding model" (or "Static Embeddings" as a plural group noun)
  is the Sentence Transformers term for a Model2Vec-style, lookup-based
  model, always paired with "embedding," matching Model2Vec's own usage.
- "Model2Vec-style distillation" is used as a descriptor for this specific
  distillation approach, not a single fixed proper-noun term.
