# Exporting models, and whether a model-zoo index standard is worth adopting

The owner is considering the S3 store add-on (`opennlp-grpc-store-s3`) for an
"export model" feature and asks whether there is a model-zoo index standard
worth following. Primary-source excerpts for every standard named here are in
`reference/`.

## 1. FACT: what already exists

### The publish side is nearly there

`StaticModelArtifactStore.publish`
(`opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/training/StaticModelArtifactStore.java:410`)
already writes, for every trained static model, an artifact containing:

- the distilled model files,
- `manifest.tsv` (`:89`), one line per file: `name\tbyteSize\tsha256`,
- `model.pb` (`:90`), the serialized `StaticModelDescriptor`,
- and it computes `artifact_hash` as the SHA-256 **of the manifest bytes**
  (`:434`), so a single 64-character string covers every file.

`StaticModelDescriptor` (`opennlp_training.proto:338`) already carries
`artifact_id`, `display_name`, `vocabulary_artifact_id`, `teacher_id`, `family`
(the tokenizer family, "wordpiece"), `dimension`, `vocabulary_size`,
`term_count`, `explained_variance_ratio`, `artifact_hash`, `byte_size`,
`provenance_summary`, and `created_at`.

That is a model manifest in all but name. It is missing a license, a source, a
language, and a role, and it is protobuf rather than a readable file.

### S3 is already a supported backend, for one kind of thing

`opennlp-grpc-store-s3` implements only `VocabularyStore`
(`S3VocabularyStore.java:55`). Its key layout is:

```
<prefix>artifacts/<kind>/<artifactId>/<entryName>     the bytes
<prefix>published/<kind>/<artifactId>                 the publication marker
```

(`S3VocabularyStore.java:57,58,189,194`). The marker key is what makes
publication atomic: `list(kind)` enumerates markers, not objects
(`:88`), and `commit()` writes the marker last (`:254`). Training uses the
single kind `"models"` (`StaticModelArtifactStore.java:88`).

**So a trained model is already exportable to S3 today** by configuring the S3
`VocabularyStoreProvider`. What does not exist is any way to *read one back* as
a catalog entry on another server.

### The import side has an SPI but no file format

A catalog entry is `CatalogModel` = one `ModelCatalogDescriptor` plus a
`List<CatalogFile>` of `(relativePath, sourceUri, byteSize, sha256)`
(`opennlp-grpc-spi/src/main/java/org/apache/opennlp/grpc/spi/catalog/CatalogFile.java`,
which validates sha256 as 64 lowercase hex characters at `:50`). Catalogs are
discovered through `ModelCatalogProvider` (ServiceLoader).

The only implementation, `StandardModelCatalog`, is **hard-coded Java**: 26
entries built by constructor calls with literal sizes and digests
(`opennlp-grpc-installer/.../StandardModelCatalog.java:73-114`). There is no
serialization format for a catalog, so there is nothing to push to S3 and
nothing to read back.

**That is the whole gap.** The SPI shape is already right; only a file format is
missing.

## 2. FACT: how the eight candidate standards compare

| | File format | Per-file hash | License field | Version axis | Task/format metadata | Self-hostable offline | Runtime coupling |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Hugging Face Hub | README.md YAML front matter; index is a REST service | LFS `oid` sha256 in `siblings` | `license` (SPDX id), `license_name`, `license_link` | git commit sha as `revision` | `pipeline_tag`, `library_name`, `tags` | no, the index is the service | none |
| ONNX Model Zoo | one `ONNX_HUB_MANIFEST.json` at the repo root | `model_sha`, `model_bytes`, `model_with_data_sha` | per-model LICENSE file only | `onnx_version` plus `opset_version` | `tags`, `io_ports` | yes, `hub.load(..., repo=...)` | ONNX only |
| MLflow | `MLmodel` YAML per model | none | tag only | integer model version plus aliases | `flavors` map | yes | Python-flavor centric |
| spaCy | `meta.json` per pipeline | none | `license` | `version`, `spacy_version` | `pipeline`, `components`, `vectors` | yes | spaCy only |
| Triton | `config.pbtxt` per model directory | none | none | numbered version subdirectories | `platform` / `backend`, tensor `input`/`output` | yes | server-specific |
| TorchServe | `MANIFEST.json` inside a `.mar` zip | none | none | `modelVersion` | `handler`, `runtime` | yes | Python handler required |
| OCI artifacts / ORAS, CNCF model-spec | OCI image manifest plus a JSON config blob | every layer has `digest` and `size` | `descriptor.licenses[]`, SPDX expressions | `descriptor.version`, `descriptor.revision`, registry tags | `config.format` (`onnx`, `safetensors`, `gguf`, `pt`), `config.architecture`, `descriptor.family` | yes, needs a registry | none |
| Kaggle Models / TF Hub | handle `owner/model/framework/variation/version` plus a metadata JSON | none | license slug | integer version | framework in the handle | partly | none |

Two of these are directly relevant.

- **ONNX Model Zoo** is the closest match to what is needed: one JSON index file
  at a known path, resolved by an offline-capable client, with an explicit
  SHA-256 and byte size per artifact. Its schema is however unversioned and
  ONNX-only, and its own README now points at Hugging Face for the binaries
  (`reference/onnx-model-zoo.md`).
- **The CNCF model-spec config object** is the best-designed field set:
  `descriptor` (`createdAt`, `authors[]`, `vendor`, `family`, `name`,
  `version`, `title`, `description`, `docURL`, `sourceURL`, `revision`,
  `licenses[]` as SPDX expressions) and `config` (`architecture`, `format`,
  `paramSize`, `precision`, `quantization`) (`reference/serving-repos.md`).
  Its `config.format` enum (`"onnx"`, `"safetensors"`, `"gguf"`, `"pt"`) is
  exactly the "family (onnx, etc)" tag the owner asked for, already named and
  already enumerated by somebody else.

## 3. OPINION: recommendation

**P2. Adopt no standard wholesale. Publish one JSON manifest per model plus one
JSON index, with field names taken from the CNCF model-spec where they overlap
and from Hugging Face where a client is likely to read both.**

Reasons not to adopt each candidate directly:

- OCI/ORAS pulls in a container registry as a hard dependency for what is
  currently a plain HTTPS `GET` with a pinned digest. Keep it as a later
  transport: the manifest below maps field-for-field onto the CNCF config
  object, so an ORAS push is an add-on rather than a rewrite.
- TorchServe `.mar` requires a Python handler, and Triton `config.pbtxt`
  describes tensor I/O that an OpenNLP `.bin` does not have.
- MLflow `MLmodel` is YAML and its `flavors` are Python entry points.
- The Hugging Face index is a hosted service, not a file, so it cannot be the
  format. Its *keys* are still worth matching, because an exported model that
  also carries a Hugging Face README front matter block can be uploaded there
  unchanged.

### The proposed per-model manifest

Written as `opennlp-model.json` beside the model files.

```json
{
  "schemaVersion": 1,
  "catalogId": "potion-base-8m",
  "displayName": "Potion Base 8M",
  "description": "General-purpose English Model2Vec static embeddings",
  "role": "MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING",
  "modelId": "potion-base-8m",
  "format": "safetensors",
  "runtime": "opennlp-static",
  "unlocks": ["PIPELINE_STEP_EMBED", "PIPELINE_STEP_CHUNK"],
  "requiresRestart": false,
  "language": ["en"],
  "dimension": 256,
  "sourceUri": "https://huggingface.co/minishlab/potion-base-8M",
  "revision": "bf8b056651a2c21b8d2565580b8569da283cab23",
  "license": "MIT",
  "licenseUri": "https://opensource.org/license/mit",
  "byteSize": 30458083,
  "artifactHash": "sha256:<digest of the ordered file list>",
  "files": [
    { "path": "config.json", "byteSize": 202, "sha256": "sha256:2a6a..." },
    { "path": "model.safetensors", "byteSize": 30236760, "sha256": "sha256:f65d..." },
    { "path": "tokenizer_config.json", "byteSize": 1431, "sha256": "sha256:6725..." },
    { "path": "vocab.txt", "byteSize": 219690, "sha256": "sha256:1394..." }
  ],
  "provenance": {
    "producedBy": "opennlp-grpc 3.0.0",
    "createdAt": "2026-08-28T00:00:00Z",
    "teacherId": "all-minilm-l6-v2",
    "vocabularyArtifactId": "vocab-legal-2026-08",
    "explainedVarianceRatio": 0.93,
    "summary": "distilled from the legal corpus vocabulary"
  }
}
```

Field-by-field justification:

| Field | Why, and precedent |
| --- | --- |
| `schemaVersion` | OCI manifests carry `schemaVersion: 2`; the ONNX hub manifest carries none and cannot be evolved safely. Start at 1. |
| `files[].sha256`, `files[].byteSize` | Already the exact content of `manifest.tsv` (`StaticModelArtifactStore.java:427`) and of `CatalogFile` (`CatalogFile.java`). Matches ONNX Zoo `model_sha`/`model_bytes` and OCI layer `digest`/`size`. |
| `artifactHash` | Already computed as the SHA-256 over the manifest (`StaticModelArtifactStore.java:434`); one string verifies the whole set. This is what OCI achieves with the config descriptor digest. |
| `sha256:` prefix | The repo stores bare lowercase hex (`CatalogFile.java:50`). Prefixing matches OCI digest syntax and leaves room for a second algorithm. Apache OpenNLP itself publishes `sha512`, `sha1`, `md5`, and `asc` beside each model (`reference/opennlp-models-index.md`), so a single hard-coded algorithm is already out of step with the project. |
| `license`, `licenseUri` | `license` as an SPDX expression, matching CNCF `descriptor.licenses[]` and Hugging Face `license`; `licenseUri` matching HF `license_link`. The three values in use (`Apache-2.0`, `MIT`, `CC-BY-4.0`) are already SPDX ids (`StandardModelCatalog.java:41-46`). |
| `revision` | Already present, already a HF commit sha for HF-sourced entries. Same meaning as HF `revision`. |
| `format` | CNCF `config.format`, values `"onnx"`, `"safetensors"`, `"gguf"`, `"pt"`, extended with `"opennlp-bin"`. Do not call it `family`: `family` is taken (`StaticModelDescriptor.family` means the tokenizer family). |
| `runtime` | Triton's `backend`. The repo already emits the same idea as `backendId` (`opennlp-me`, `cuda`) in `/api/v1/model-bundles`. |
| `unlocks` | Hugging Face `pipeline_tag`, generalised to a list because one language pack member unlocks one step and a name finder unlocks two. |
| `requiresRestart` | No external precedent; it is the local activation rule (`CatalogModelStore.java:588`) and today it is duplicated in the front end (`src/model-data-workbench.ts:640`). |
| `provenance` | `StaticModelDescriptor.provenance_summary` plus the training inputs already recorded. Matches CNCF `descriptor.authors`/`sourceURL` and spaCy `meta.json` `sources`. |

### The index

One `opennlp-catalog.json` at a known path, holding `schemaVersion`,
`generatedAt`, and a `models` array of the same objects with an added
`path` prefix per entry. Precedent: ONNX Model Zoo's single
`ONNX_HUB_MANIFEST.json` at the repository root, which `onnx.hub.load(model,
repo=...)` resolves against any repository, including a private one
(`reference/onnx-model-zoo.md`). A new `ModelCatalogProvider` that reads this
file drops straight into the existing SPI, and `CatalogModelStore` needs no
change, because `CatalogModel` is already descriptor plus files.

### The S3 layout

Reuse the existing two-key convention so the export inherits its atomicity:

```
<prefix>artifacts/catalog/<catalogId>/opennlp-model.json
<prefix>artifacts/catalog/<catalogId>/<model files>
<prefix>published/catalog/<catalogId>            publication marker
<prefix>artifacts/catalog/_index/opennlp-catalog.json
```

`S3VocabularyStore` writes the marker last (`:254`) and `list(kind)` reads only
markers (`:88`), so a half-uploaded model is invisible. Using `kind = "catalog"`
means no new store code at all.

### Versioning

Two independent axes, both already half-present:

1. `revision`, the upstream identity. Keep as is.
2. A catalog-entry version for republished exports. Recommend an integer
   `catalogVersion` incremented on every push, with the index resolving the
   highest by default. Precedent: MLflow integer model versions plus aliases
   (`reference/mlflow-model-registry.md`) and Triton numbered version
   subdirectories (`reference/serving-repos.md`). Avoid a mutable `"latest"`
   string inside the manifest itself; let the index resolve it.

## 4. OPINION: the security question this opens

`ModelCatalogDescriptor`'s comment is explicit: "Source_uri is a human-facing
model page, not a caller-controlled download location"
(`opennlp_training.proto:99`). Today the actual download URIs are compiled into
`StandardModelCatalog` and can never be influenced by a request.

A JSON catalog read from S3 inverts that: the download URIs become data. The
existing controls (exact byte size plus SHA-256 per file, symlink rejection at
`CatalogModelStore.java:479`, layout verification at `:489`, atomic publication
at `:311`) all still apply to the *bytes*, but nothing constrains *where the
bytes come from*.

Minimum conditions before shipping an importable catalog, in priority order:

1. **P1.** The index location is an operator configuration key, never a request
   field, exactly as `model.catalog_root` is today
   (`CatalogModelStore.java:70`).
2. **P1.** The index itself is pinned: `model.catalog_index.uri` plus
   `model.catalog_index.sha256`. Without that, one mutable S3 object controls
   every download URI on every node.
3. **P2.** A detached signature beside the index. Apache OpenNLP already ships
   `.asc` files with a documented `gpg --import KEYS` step for its own models
   (`reference/opennlp-models-index.md`); an OpenNLP-branded catalog that does
   not is a step backwards.
4. **P2.** Keep the consent gate. `InstallModelRequest.license_acknowledged` is
   validated against the catalog's own `license_name`
   (`CatalogModelStore.java:340,346`), and an imported catalog with a missing or
   non-SPDX license should be refused rather than shown with a blank chip.

## 5. OPINION: sequencing

| Step | What | Priority |
| --- | --- | --- |
| 1 | Define `opennlp-model.json` v1 and write it beside every trained static model, alongside the existing `manifest.tsv` and `model.pb`. Purely additive. | P2 |
| 2 | Add `format`, `unlocks`, `requires_restart`, and a `files` list to `ModelCatalogDescriptor` so the workbench can render the tags asked for in `findings/unlocks-and-tags.md`. Generate them from the same source as the JSON. | P1 for the tags, P2 for the export |
| 3 | Emit `opennlp-catalog.json` from `StandardModelCatalog` as a build artifact and diff it in a test. This makes the hard-coded Java catalog and the file format provably identical before either is trusted. | P2 |
| 4 | Add a `FileModelCatalogProvider` reading a pinned index URI. This is the "re-import as a catalog" feature. | P3 |
| 5 | Export to S3 under `kind = "catalog"`. | P3 |
| 6 | ORAS/OCI transport, if a registry is ever in the picture. | P3 |

## Questions for the lead

1. Is "export model" meant to move a **trained static model** between servers,
   or to publish a curated catalog for a fleet? The manifest is the same; only
   steps 4 and 5 above differ.
2. Should the exported manifest also carry a Hugging Face README front matter
   block, so an exported static model can be pushed to the Hub unchanged? The
   overlapping keys (`license`, `language`, `pipeline_tag`, `base_model`) are
   listed in `reference/hf-hub-model-cards.md`.
3. Signing: is a detached `.asc` in scope, or is a pinned index digest in the
   server configuration considered sufficient?
