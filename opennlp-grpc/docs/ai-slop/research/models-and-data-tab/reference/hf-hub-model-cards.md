# Hugging Face Hub model cards and Hub API

Sources fetched 2026-08-28:

- https://huggingface.co/docs/hub/model-cards
- https://huggingface.co/docs/hub/model-card-annotated
- https://huggingface.co/docs/hub/api (now redirects the field detail to https://huggingface.co/.well-known/openapi.md)
- https://huggingface.co/tasks
- https://raw.githubusercontent.com/huggingface/hub-docs/main/modelcard.md
- https://raw.githubusercontent.com/huggingface/huggingface.js/main/packages/tasks/src/pipelines.ts (pipeline_tag literal values)

## 1. Where the metadata lives

A model repo renders `README.md` as its model card. The card is Markdown with a YAML
front matter block delimited by `---` at the top of the file.

## 2. Recognised YAML front matter keys

From `modelcard.md` (the Hub validation spec) and the model-cards doc:

| Key | Type |
| --- | --- |
| `language` | list of ISO 639-1 codes |
| `license` | string, one license identifier, or `other` |
| `license_name` | string, only with `license: other` |
| `license_link` | string URL, or a path to a LICENSE file in the repo |
| `library_name` | string, e.g. `flair`, `transformers` |
| `tags` | list of free-form strings |
| `datasets` | list of Hub dataset repo ids |
| `buckets` | list of Hub storage bucket repo ids |
| `metrics` | list |
| `base_model` | string repo id, or list of repo ids for a merge |
| `base_model_relation` | one of `adapter`, `merge`, `quantized`, `finetune` |
| `new_version` | repo id of the successor model |
| `pipeline_tag` | one task string, see section 4 |
| `thumbnail` | URL used in social sharing |
| `model-index` | list, see section 3 |
| `inference` | widget / inference toggle (documented under widgets) |

Minimal example straight from the docs:

```yaml
---
language:
  - "List of ISO 639-1 code for your language"
  - lang1
  - lang2
thumbnail: "url to a thumbnail used in social sharing"
tags:
- tag1
- tag2
license: "any valid license identifier"
datasets:
- dataset1
- dataset2
base_model: "base model Hub identifier"
---
```

Custom license form:

```yaml
license: other
license_name: coqui-public-model-license
license_link: https://coqui.ai/cpml
```

Notes captured verbatim from the docs:

- Library resolution order: explicit `library_name` first, then a `tags` entry naming a
  supported library, then file sniffing (`*.nemo`, `*.mlmodel`). Since August 2024 the
  presence of `config.json` alone no longer implies `transformers`.
- A link to an arXiv abstract or PDF in the card body causes the Hub to add an
  `arxiv:<PAPER ID>` tag automatically.
- `not-for-all-audiences` in `tags` triggers an interstitial on the model page.

## 3. model-index structure

```yaml
model-index:
- name: {model_id}
  results:
  - task:
      type: {task_type}
      name: {task_name}
    dataset:
      type: {dataset_type}
      name: {dataset_name}
      config: {dataset_config}
      split: {dataset_split}
      revision: {dataset_revision}
      args:
        {arg_0}: {value_0}
    metrics:
      - type: {metric_type}
        value: {metric_value}
        name: {metric_name}
        config: {metric_config}
        args:
          {arg_0}: {value_0}
        verifyToken: {verify_token}
    source:
      name: {source_name}
      url: {source_url}
```

The spec was derived from the Papers with Code `model-index` specification.

## 4. pipeline_tag values

Literal machine values (from `packages/tasks/src/pipelines.ts`):

```
text-classification, token-classification, table-question-answering, question-answering,
zero-shot-classification, feature-extraction, text-generation, fill-mask,
sentence-similarity, text-to-speech, text-to-audio, automatic-speech-recognition,
audio-to-audio, audio-classification, audio-text-to-text, voice-activity-detection,
depth-estimation, image-classification, object-detection, image-segmentation,
text-to-image, image-to-text, image-to-image, image-to-video,
unconditional-image-generation, video-classification, reinforcement-learning,
tabular-classification, tabular-regression, tabular-to-text, table-to-text,
multiple-choice, text-ranking, text-retrieval, time-series-forecasting, text-to-video,
image-text-to-text, image-text-to-image, image-text-to-video, visual-question-answering,
document-question-answering, zero-shot-image-classification, graph-ml, mask-generation,
zero-shot-object-detection, text-to-3d, image-to-3d, image-feature-extraction,
video-text-to-text, keypoint-detection, visual-document-retrieval, any-to-any,
video-to-video
```

huggingface.co/tasks groups the browsable subset by modality: Multimodal, Natural Language
Processing, Computer Vision, Audio, Tabular, Reinforcement Learning.

## 5. Hub REST API

The prose API page was replaced by an OpenAPI document. Endpoints relevant to indexing:

```
GET /api/models
    query params: search, author, filter, pipeline_tag, library, sort, direction,
                  limit, full, config, expand
GET /api/models/{namespace}/{repo}
GET /api/models/{namespace}/{repo}/revision/{rev}
GET /api/models/{namespace}/{repo}/tree/{rev}/{path}
```

Response fields named in the OpenAPI document and mirrored by the `ModelInfo` dataclass in
`huggingface_hub`:

```
id, _id, modelId, author, sha, createdAt / created_at, lastModified / last_modified,
private, disabled, gated, downloads, downloads_all_time, likes, trendingScore,
usedStorage, library_name, pipeline_tag, tags, cardData, config, safetensors,
siblings, spaces, securityStatus
```

Revision semantics: `sha` is the repo commit hash at the revision returned; the
`{revision}` path segment accepts a branch name or the OID/SHA of a commit as a
hexadecimal string.

File listing: `siblings` is a list of `RepoSibling` entries whose `rfilename` is the file
path relative to the repo root. Large files carry a `BlobLfsInfo` under `lfs` with:

```json
"lfs": { "oid": "<sha256 hex>", "size": 1234, "pointerSize": 134 }
```

The LFS `oid` is the SHA-256 digest of the file content, which is what makes per-file
content addressing possible without downloading the blob.

Download counting (models-download-stats) uses per-library query files, defaulting to
`config.json`, `config.yaml`, `hyperparams.yaml`, `params.json`, `meta.yaml`.
