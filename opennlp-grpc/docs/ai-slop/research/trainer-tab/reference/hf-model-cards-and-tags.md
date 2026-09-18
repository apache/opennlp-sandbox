# Hugging Face Model Card Metadata and Tags
Source: https://huggingface.co/docs/hub/model-cards
Fetched: 2026-08-28

## What a model card is

"Model cards are files that accompany the models and provide handy
information. Under the hood, model cards are simple Markdown files with
additional metadata. Model cards are essential for discoverability,
reproducibility, and sharing." A model card is the `README.md` file in a
model repo, with a YAML metadata block at the top.

A model card is described as having "two key parts, with overlapping
information": Metadata, and Text descriptions.

## `library_name`

"Specifying library_name in the model card (recommended if your model is
not a transformers model). This information can be added via the metadata
UI or directly in the model card YAML section":

```yaml
library_name: flair
```

Priority order for library detection: explicit `library_name` field first,
then a matching entry in `tags`, then (discouraged) automatic detection
from repo file presence, e.g. "`*.nemo` or `*.mlmodel`" files. Note also:
"For model repos created after August 2024, [assuming transformers by
default when a config.json is present] is not the case anymore, so you
need to set library_name: transformers explicitly." This is a precedent
for being explicit about which library/family a model belongs to rather
than relying on inference.

## `tags`

Free-form list field. "You can add custom tags to your model by adding
them to the tags field in the model card metadata. The metadata UI will
suggest some popular tags, but you can add any tag you want." Example
given: a `finance` tag to indicate domain focus. Tags also gate special UI
behavior, e.g. a `not-for-all-audiences` tag triggers a content warning
message on the model page. Library detection can also fall back to a tag
matching a supported library name if `library_name` is absent.

## `pipeline_tag`

"You can specify the pipeline_tag in the model card metadata. The
pipeline_tag indicates the type of task the model is intended for. This
tag will be displayed on the model page and users can filter models on the
Hub by task. This tag is also used to determine which widget to use for
the model and which APIs to use under the hood." For `transformers` models
it is normally auto-inferred from `config.json` but can be overridden.

## `base_model`

"If your model is a fine-tune, an adapter, or a quantized version of a
base model, you can specify the base model in the model card metadata
section. This information can also be used to indicate if your model is a
merge of multiple existing models." Example:

```yaml
base_model: HuggingFaceH4/zephyr-7b-beta
```

`base_model` accepts either a single model ID or a list (for merges). The
Hub infers a relation type automatically ("adapter", "merge", "quantized",
"finetune") but it can be set explicitly via a separate
`base_model_relation` field, e.g. `base_model_relation: quantized`. This is
a strong precedent for a field that names both "what family a model
descends from" and, via a companion relation field, "what kind of
derivation it is."

## `license`

"You can specify the license in the model card metadata section. The
license will be displayed on the model page and users will be able to
filter models by license." The metadata UI offers a dropdown of common
licenses; a custom license uses `license: other` plus `license_name` and
`license_link` fields.

## `datasets`

"You can specify the datasets used to train your model in the model card
metadata section. The datasets will be displayed on the model page and
users will be able to filter models by dataset." Example:

```yaml
datasets:
- stanfordnlp/imdb
- HuggingFaceFW/fineweb
```

Adding this field also causes the model page to render a "Datasets used to
train:" message linking the referenced datasets.

## `language`

Shown in the general YAML metadata example as a list of ISO 639-1 codes:

```yaml
language:
  - "List of ISO 639-1 code for your language"
  - lang1
  - lang2
```

Dataset and language identifiers are drawn from the Hub's own canonical
Datasets and Languages listing pages, i.e. these are not free text but
values expected to match a controlled list where possible.

## How tags surface on the model page

"Each model page lists all the model's tags in the page header, below the
model name. These are primarily computed from the model card metadata,
although some are added automatically." This confirms the general pattern:
a small set of structured YAML fields (`library_name`, `pipeline_tag`,
`base_model`, `license`, `datasets`, `language`) plus a free-form `tags`
list together drive both the model's discoverability (search/filter on the
Hub) and what capabilities/behaviors get unlocked (widget selection, API
selection, base-model lineage display, dataset provenance display).

## Summary of naming precedent

- `library_name` - which library/framework a model belongs to; explicit
  over inferred.
- `tags` - free-form list for arbitrary discoverability labels and feature
  flags (e.g. `not-for-all-audiences`).
- `pipeline_tag` - the specific task/capability a model is intended for;
  drives which UI widget and API path apply.
- `base_model` (+ `base_model_relation`) - lineage: what model(s) this one
  derives from, and how (finetune, adapter, quantized, merge).
- `license`, `datasets`, `language` - provenance and legal/data metadata,
  each independently filterable on the Hub.

This is the direct precedent for the general pattern of "tags that describe
what a trained model unlocks (task/capability) plus tags that describe its
family/lineage (base model, library)" as two distinct, named metadata
concerns rather than one blended concept.
