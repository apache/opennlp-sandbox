# Model2Vec (MinishLab)
Source: https://github.com/MinishLab/model2vec
Fetched: 2026-08-28

Secondary source fetched in the same pass:
Source: https://minish.ai/packages/model2vec/introduction
Fetched: 2026-08-28

## What Model2Vec calls itself

"Model2Vec is a technique to turn any sentence transformer into a small,
fast static embedding model."

Also phrased as: "turn any Sentence Transformer model into a lightweight
embedding model."

This confirms the project's own term is "static embedding model," not
"static model" standing alone. "Static" is always paired with
"embedding(s)" or "embedding model" in their usage, e.g. "the most
performant static embedding model in the world" and "outperform any other
static embeddings (such as GLoVe and BPEmb)."

## Distillation and the source model

"Fast, Dataset-free Distillation: distill your own model in 30 seconds on a
CPU, without a dataset."

"Distillation doesn't need any data, just a vocabulary and a model."

Notably, the README does not use the word "teacher model" explicitly. It
refers to the input as a "Sentence Transformer model" or "the base model."
The introduction page corroborates this: "The original Sentence Transformer
serving as the base, referenced as 'the base model' throughout the
documentation." So Model2Vec's own preferred noun for the input model is
"(Sentence Transformer) base model," with "teacher" being the more generic
distillation-literature term rather than Model2Vec's house style.

## Vocabulary

"just a vocabulary and a model" (the two required inputs to distillation).

"forward pass a vocabulary through a sentence transformer model" - describes
the mechanism: every token in the vocabulary gets one embedding by a single
forward pass, rather than the model being queried at inference time.

Vocabulary expansion is also named directly: "we can expand the base
model's vocabulary with new tokens to improve performance on specific
domains."

"Output vocabulary" is not a separately defined term in the README; the
vocabulary that goes in is the same set of tokens that ends up with fixed
vectors out. Quantization/compression of that output set is described via
"clustering embeddings using k-means and merging them" rather than a named
"output vocabulary" concept.

## PCA and dimensionality

"applying PCA improves performance even if we don't reduce dimensionality"
- i.e. Model2Vec applies PCA to the per-token embedding matrix both as a
denoising step and, optionally, as a dimensionality-reduction step: "we
apply PCA on the embedding matrix, reducing its dimensionality."

They do not use a distinct phrase like "output dimensionality" as a
formal parameter name in the prose; dimensionality is discussed as a
property of the embedding matrix after PCA.

## Zipf / SIF weighting

Model2Vec explicitly names Smooth Inverse Frequency (SIF) weighting:
"reweighting tokens using Smooth Inverse Frequency (SIF)" where "word
frequency in natural language roughly follows a power-law distribution."
This power-law observation is the "Zipf" connection - Model2Vec's own docs
reference the power-law/Zipfian frequency distribution as the justification
for SIF-style down-weighting of frequent tokens, but the parameter/feature
itself is named "SIF weighting" in their material, not "Zipf weighting."
Treat "Zipf weighting" as a paraphrase of the underlying frequency law, and
"SIF weighting" as the project's actual named technique.

## Potion models

The "potion" family (e.g. potion-base-32M, potion-base-8M, potion-base-4M,
potion-base-2M, potion-multilingual-128M, potion-retrieval-32M) are
Model2Vec's own pretrained static embedding models. Per the introduction
page: "after distillation, we can optionally pre-train the model" using a
companion tool called Tokenlearn - the potion models are the distilled
models that have gone through this additional pretraining step, not just
raw distillation output.

## Summary of naming findings

- Input model: "Sentence Transformer" / "base model" (not consistently
  called "teacher" in Model2Vec's own docs, though the wider distillation
  literature would call it that).
- Output model: "static embedding model" (never bare "static model").
- Required distillation inputs: "a vocabulary and a model."
- Weighting technique: "SIF weighting" (Smooth Inverse Frequency), justified
  by reference to Zipfian/power-law word frequency, but "Zipf weighting" is
  not the literal name Model2Vec gives the feature.
- Dimensionality reduction: "PCA" applied to "the embedding matrix."
- Pretrained model family name: "potion" models.
