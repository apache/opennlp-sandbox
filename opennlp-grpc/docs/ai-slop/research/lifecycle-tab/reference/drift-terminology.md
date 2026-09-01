# Drift terminology in the literature

Excerpts. Fetched 2026-08-28.

## Concept drift
Source: https://en.wikipedia.org/wiki/Concept_drift

> "Concept drift or drift is an evolution of data that invalidates the data model. It happens when
> the statistical properties of the target variable, which the model is trying to predict, change
> over time in unforeseen ways."

The article uses "data drift" loosely, for records failing to match the real world over time, and
separately for divergence between database replicas. It does not define "covariate shift",
"virtual concept drift", or "distribution shift".

## The distinction that matters here

Standard usage in the ML literature:
- **Concept drift**: the relationship between input and target changes. Requires labels and a
  prediction target to even be observable.
- **Data drift / covariate shift**: the input distribution changes while the target relationship
  does not.
- **Out-of-vocabulary (OOV) rate**: the share of tokens in new text that are absent from a fixed
  vocabulary. This is a plain coverage statistic from language modelling and speech recognition. It
  needs no labels, no target, and no notion of time.

The panel this repo labels "Vocabulary drift" computes the third of these. See
findings/vocabulary-drift.md for the code and the exact arithmetic.
