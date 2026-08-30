# Distilling the Knowledge in a Neural Network
Source: https://arxiv.org/abs/1503.02531
Fetched: 2026-08-28

## Bibliographic note

Hinton, Vinyals, Dean. "Distilling the Knowledge in a Neural Network." arXiv:1503.02531.
This is the paper that establishes "distillation" as the standard term for compressing
an ensemble or large model into a single smaller model.

## Abstract (fetched verbatim)

"A very simple way to improve the performance of almost any machine learning
algorithm is to train many different models on the same data and then to
average their predictions. Unfortunately, making predictions using a whole
ensemble of models is cumbersome and may be too computationally expensive to
allow deployment to a large number of users, especially if the individual
models are large neural nets. Caruana and his collaborators have shown that
it is possible to compress the knowledge in an ensemble into a single model
which is much easier to deploy and we develop this approach further using a
different compression technique. We achieve some surprising results on MNIST
and we show that we can significantly improve the acoustic model of a heavily
used commercial system by distilling the knowledge in an ensemble of models
into a single model. We also introduce a new type of ensemble composed of one
or more full models and many specialist models which learn to distinguish
fine-grained classes that the full models confuse. Unlike a mixture of
experts, these specialist models can be trained rapidly and in parallel."

## Exact terminology found

- "distillation" / "distilling the knowledge" - the compression technique itself.
  Exact phrase: "distilling the knowledge in an ensemble of models into a
  single model."
- "cumbersome" - describes the large source model or ensemble, not "teacher."
  Exact phrase: "making predictions using a whole ensemble of models is
  cumbersome."
- "ensemble" - the source of the knowledge being compressed. Used throughout
  as the thing that gets distilled, e.g. "compress the knowledge in an
  ensemble into a single model."
- "compress" / "compression technique" - used interchangeably with
  distillation in the abstract ("we develop this approach further using a
  different compression technique").

## Notable absence

The abstract itself does NOT use the words "teacher" or "student." The
paper's body (per secondary literature and later citations) is the origin of
"soft targets" and later works popularized "teacher model" / "student model"
as the standard pair of terms, but the original abstract's own vocabulary is
"cumbersome model" (or "ensemble") versus "the [single/small] model," plus
"distillation" for the transfer process. Later papers and virtually all
modern usage (including Model2Vec and Sentence Transformers, see the other
files in this directory) standardized on "teacher" for the source model and
"student" for the compressed target model, crediting this paper as the
origin of the technique even though the exact word "teacher" is a
downstream convention, not verbatim Hinton et al. abstract text.

## Summary of naming precedent

- Compression process: "distillation" / "distilling the knowledge."
- Source model: "cumbersome model" / "ensemble" (abstract wording); "teacher"
  is the term the field converged on afterward.
- Target model: "a single model"; "student" is the term the field converged
  on afterward.
