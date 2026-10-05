# spaCy trained pipelines and Kaggle Models handles

Sources fetched 2026-08-28:

- https://spacy.io/models
- https://spacy.io/api/data-formats (meta.json section)
- https://www.kaggle.com/docs/models (page returned only its title, no body text, so the
  Kaggle field detail below comes from the official Kaggle tooling repositories instead)
- https://raw.githubusercontent.com/Kaggle/kaggle-api/main/docs/models.md
- https://raw.githubusercontent.com/Kaggle/kaggle-api/main/docs/models_metadata.md
- https://raw.githubusercontent.com/Kaggle/kagglehub/main/README.md

## A. spaCy

### Naming convention

Package names are `[lang]_[name]`, where the name half is composed of three parts:

```
[lang]_[type]_[genre]_[size]
en_core_web_sm
```

- `type`: capability set, `core` for a general purpose pipeline with tagger, parser,
  lemmatizer and NER; `dep` for tagging and parsing only.
- `genre`: the kind of text trained on, for example `web` (blogs, news, comments) or
  `news`.
- `size`: `sm`, `md`, `lg`, `trf`.

Size semantics as documented:

| Suffix | Vectors |
| --- | --- |
| `sm` | no static word vectors |
| `md` | 20k vectors for roughly 500k words, or 50k floret entries |
| `lg` | roughly 500k entries, or 200k floret entries |
| `trf` | no static word vectors, transformer based |

Version string `a.b.c`: `a` is the spaCy major version, `b` the spaCy minor version, `c`
the model version, which "reflects different training configurations".

### meta.json fields

Exported automatically when an `nlp` object is saved.

| Key | Type | Notes |
| --- | --- | --- |
| `lang` | str | pipeline language ISO code, default `"en"` |
| `name` | str | pipeline identifier, for example `"core_web_sm"`, default `"pipeline"` |
| `version` | str | pipeline version, default `"0.0.0"` |
| `spacy_version` | str | spaCy version range the package is compatible with |
| `parent_package` | str | usually `"spacy"` or `"spacy_nightly"` |
| `description` | str | |
| `author` | str | |
| `email` | str | |
| `url` | str | |
| `license` | str | |
| `sources` | Optional[List[Dict]] | each entry has `name`, `url`, `author`, `license` |
| `vectors` | Dict[str, Any] | vector metadata: `width`, `vectors`, `keys`, `name` |
| `pipeline` | List[str] | component names, informational only |
| `components` | List[str] | components present in the pipeline |
| `labels` | Dict[str, Dict] | "Label schemes of the trained pipeline components, keyed by component name" |
| `performance` | Dict | accuracy scores, added by `spacy train` |
| `speed` | Dict | inference speed, keys `cpu`, `gpu`, `nwords` |
| `spacy_git_version` | str | git commit of the spaCy build used |
| `requirements` | List[str] | python package dependencies |
| other | Any | "Any other custom meta information you want to add" |

The vectors block is the one used for indexing embedding capability, shaped as
`{"width": 96, "vectors": 20000, "keys": 500000, "name": "en_vectors"}`.

## B. Kaggle Models

### Handle format

Model instances are addressed positionally:

```
owner/model/framework/variation/version
google/bert/tensorFlow2/answer-equivalence-bem/1
```

The trailing version is optional and defaults to the latest:

```python
import kagglehub
kagglehub.model_download('google/bert/tensorFlow2/answer-equivalence-bem')
kagglehub.model_download('google/bert/tensorFlow2/answer-equivalence-bem/1')
kagglehub.model_download('google/bert/tensorFlow2/answer-equivalence-bem',
                         path='variables/variables.index')
```

Upload uses the four-segment form `<KAGGLE_USERNAME>/<MODEL>/<FRAMEWORK>/<VARIATION>`
with `version_notes` and `license_name` as optional keyword arguments. Kaggle calls the
third segment the framework and the fourth the variation; a version is created implicitly
on each upload.

### model-metadata.json

```json
{
  "ownerSlug": "INSERT_OWNER_SLUG_HERE",
  "title": "INSERT_TITLE_HERE",
  "slug": "INSERT_SLUG_HERE",
  "subtitle": "",
  "isPrivate": true,
  "description": "Model Card Markdown, see below",
  "publishTime": "",
  "provenanceSources": ""
}
```

Supported keys: `ownerSlug`, `title`, `slug`, `licenseName`, `subtitle`, `isPrivate`,
`description`, `publishTime`, `provenanceSources`.

### model-instance-metadata.json

```json
{
  "ownerSlug": "INSERT_OWNER_SLUG_HERE",
  "modelSlug": "INSERT_EXISTING_MODEL_SLUG_HERE",
  "instanceSlug": "INSERT_INSTANCE_SLUG_HERE",
  "framework": "INSERT_FRAMEWORK_HERE",
  "overview": "",
  "usage": "Usage Markdown, see below",
  "licenseName": "Apache 2.0",
  "fineTunable": false,
  "trainingData": [],
  "modelInstanceType": "Unspecified",
  "baseModelInstance": "",
  "externalBaseModelUrl": ""
}
```

Enumerations:

- `framework`: `tensorFlow1`, `tensorFlow2`, `tfLite`, `tfJs`, `pyTorch`, `jax`, `coral`
- `modelInstanceType`: `base model`, `external variant`, `internal variant`, `unspecified`
- `fineTunable`: boolean
- `trainingData`: array of strings, dataset URLs or Kaggle dataset references
- `licenseName`: one of a fixed list of roughly 25 license names

CLI surface: `kaggle models list | init | create | get | update | delete`, plus
`kaggle models instances ...` and `kaggle models topics list | show`.
