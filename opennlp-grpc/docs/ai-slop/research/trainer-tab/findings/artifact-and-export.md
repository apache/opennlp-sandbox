# The trained artifact, and what an export would need

This file covers only the trainer-side artifact: what a trained static
embedding model is on disk, how it is identified, what the existing S3 add-on
actually does, and the minimal metadata an export or a zoo entry would need from
this side. Zoo standards themselves are somebody else's file.

## 1. FACT: the artifact on disk

A trained model is published under the `models` kind of the configured artifact
root, at `models/static-model-<uuid>/`
(StaticModelArtifactStore.java:88, :91, :335). It holds:

| Entry | Written by | What it is |
| --- | --- | --- |
| `model.safetensors` | distiller | one 2-D F32 matrix, optionally a 1-D `weights` tensor |
| `tokenizer.json` | distiller | the cleaned teacher tokenizer |
| `config.json` | distiller | `{"normalize": true}` |
| `terms.txt` | distiller | the learned terms, one per line, in matrix row order |
| `vocab.txt` | assembler (WordPiece only) | one token per line, line number is the row |
| `tokenizer_config.json` | assembler (WordPiece only) | carries `do_lower_case` |
| a SentencePiece `.model` | distiller (SentencePiece teachers only) | copied from the teacher |
| `manifest.tsv` | this repo | `name<TAB>size<TAB>sha256` for every file above |
| `model.pb` | this repo | the serialized `StaticModelDescriptor` |

The first seven are the Model2Vec release layout, which is why
`StaticEmbeddingModel.load` can open a Potion model from Hugging Face and a
locally distilled model with the same code path
(../reference/opennlp-embeddings-javadoc.md;
../reference/model2vec.md). `manifest.tsv` and `model.pb` are this project's
additions and are explicitly excluded from the distilled file list
(StaticModelArtifactStore.java:471).

## 2. FACT: identity and integrity

- Artifact id: `static-model-` plus a random UUID
  (StaticModelArtifactStore.java:91, :334). It is not derived from content, so
  the same inputs distilled twice give two different ids.
- `artifact_hash` is the lowercase SHA-256 **of `manifest.tsv`**, not of the
  weights (StaticModelArtifactStore.java:418-435; opennlp_training.proto:357).
  Because the manifest lists every file's name, size and SHA-256, that one hash
  transitively pins the whole directory. It is a Merkle-style root, and it is a
  perfectly good export identity.
- `byte_size` is the sum of the distilled files, excluding the manifest and the
  descriptor (StaticModelArtifactStore.java:430, :459).
- On every read back, the manifest hash is checked against the descriptor and
  each file is re-hashed against the manifest before the model is served
  (StaticModelArtifactStore.java:505-540). A tampered file fails loud at startup;
  `StaticModelArtifactStoreTest.rejectsTamperedModelsBeforeReloading` covers it.

## 3. FACT: the S3 add-on is a storage backend, not an export feature

This is the single most important correction to the owner's framing. The
S3 module is not "export a model to a bucket". It is the durable store itself.

- `vocabulary.artifact_root` accepts a plain path, a `file` URI, or any scheme a
  `VocabularyStoreProvider` claims
  (opennlp-grpc-service/src/main/java/org/apache/opennlp/grpc/vocabulary/store/VocabularyStores.java:49).
- `opennlp-grpc-store-s3` registers the `s3` scheme
  (opennlp-grpc-store-s3/src/main/java/org/apache/opennlp/grpc/store/s3/S3VocabularyStoreProvider.java).
  Set `vocabulary.artifact_root=s3://bucket/prefix`, drop the `-all` jar on the
  classpath, and dictionaries, vocabularies **and trained models** all live in
  the bucket already (README.md:527, :576).
- The S3 layout: entries at
  `<prefix>artifacts/<kind>/<artifactId>/<entryName>`, made visible by a marker
  object at `<prefix>published/<kind>/<artifactId>` written last, because S3 has
  no atomic multi-object rename
  (S3VocabularyStore.java class javadoc).
- The filesystem store gets the same atomicity from a `.staging-<id>` directory
  and one `ATOMIC_MOVE`
  (FileSystemVocabularyStore.java:47, :106, :201).

So a server already configured with an `s3` root has, today, an off-box copy of
every trained model, at a stable key, with a hash. What it does not have is any
way for a **second** server to consume it, or any metadata a human could use to
decide whether to.

OPINION P1. Do not build "export to S3" as a new button. The bucket is already
the store. What is missing is:

1. **Import**: a way to point a second server at a model artifact produced
   elsewhere. Today `loadExistingModels` only reads the models the same
   configured root already holds (StaticModelArtifactStore.java:483). Two servers
   sharing one `s3://` root would both load the same models, which is either a
   feature or a surprise depending on whether anyone documented it.
2. **A portable descriptor**: `model.pb` is a protobuf serialization of an
   internal message. Nothing outside this codebase can read it.
3. **License and provenance fields**, which are missing entirely; see below.

## 4. FACT: what the descriptor records, and the gaps

`StaticModelDescriptor` (opennlp_training.proto:338) carries: `artifact_id`,
`display_name`, `vocabulary_artifact_id`, `teacher_id`, `family`, `dimension`,
`vocabulary_size`, `term_count`, `explained_variance_ratio`, `artifact_hash`,
`byte_size`, `provenance_summary`, `created_at`.

`provenance_summary` is a free-text string that the trainer hard-codes to
`"Distilled through the trainer workbench"` (vocabulary-trainer.ts:254). The user
is never shown the field and cannot set it.

Missing, and needed by any export or catalog entry:

| Field | Why it is needed | Where the value already exists |
| --- | --- | --- |
| `license_name` / `license_uri` | the model is a derivative of the teacher; MiniLM is Apache-2.0, Potion is MIT | `ModelCatalogDescriptor.license_name` / `license_uri`, opennlp_training.proto:109 |
| `languages` | a model distilled from the multilingual teacher is not the English one | `ModelCatalogDescriptor.languages`, opennlp_training.proto:114 |
| `teacher_reference` and pinned revision | `teacher_id` is a local operator alias like `local-mini`; it means nothing on another machine | `TeacherDescriptor.reference` (opennlp_training.proto:293), catalog `revision` |
| `pca_dims` as requested vs applied | PCA is skipped for a tiny vocabulary, so `dimension` alone does not say what was asked | request field, discarded after use |
| `min_frequency` / `max_terms` of the source vocabulary | reproducibility | `VocabularyArtifactDescriptor.min_frequency`, `max_terms` (opennlp_vocabulary.proto:166) |

The dictionary descriptor already has `source_uri`, `license_name` and
`license_uri` (opennlp_vocabulary.proto:133-135). The model descriptor, which is
the artifact most likely to be shared, has none of them. That is the asymmetry
to fix.

## 5. OPINION: the minimal manifest an export could emit

Precedent: `CatalogFile` in this repo is already a checksum-pinned file record
(relative path, https source, byte size, lowercase SHA-256)
(opennlp-grpc-spi/src/main/java/org/apache/opennlp/grpc/spi/catalog/CatalogFile.java),
and `CatalogModel` validates that the descriptor's `byte_size` equals the sum of
its files (CatalogModel.java:49). A trained model already produces exactly this
information in `manifest.tsv`. Externally, Hugging Face model cards structure the
same thing as `library_name`, `pipeline_tag`, `base_model` plus
`base_model_relation`, `license`, `datasets`, `language` and a free `tags` list
(../reference/hf-model-cards-and-tags.md).

P2. Emit one `model-card.json` beside `manifest.tsv` at publication time. Every
field below is already known at that moment except the two marked NEW:

```
{
  "artifact_id":   "static-model-<uuid>",
  "artifact_hash": "<sha256 of manifest.tsv>",
  "display_name":  "Legal static embedding model",
  "created_at":    "2026-08-28T12:00:00Z",
  "role":          "static-embedding",
  "runtime":       "static",
  "dimension":     256,
  "vocabulary_size": 29184,
  "term_count":      4812,
  "family":          "WordPiece",
  "explained_variance_ratio": 0.784,
  "byte_size":       31245012,
  "base_model": {
    "teacher_id":  "all-minilm-l6-v2-teacher",
    "reference":   "sentence-transformers/all-MiniLM-L6-v2",   // NEW on the descriptor
    "revision":    "1110a243fdf4706b3f48f1d95db1a4f5529b4d41", // NEW on the descriptor
    "relation":    "distilled"
  },
  "vocabulary": {
    "artifact_id":   "vocabulary-<uuid>",
    "display_name":  "Legal vocabulary",
    "term_count":    4812,
    "min_frequency": 2,
    "max_terms":     10000,
    "artifact_hash": "<sha256>"
  },
  "license":   { "name": "Apache-2.0", "uri": "https://www.apache.org/licenses/LICENSE-2.0" },
  "languages": ["en"],
  "provenance_summary": "Distilled through the trainer workbench",
  "files": [ { "name": "model.safetensors", "size": 30123456, "sha256": "..." }, ... ]
}
```

`role`, `runtime`, `base_model.relation`, `license` and `languages` are the
five fields that turn a private artifact into something another operator can
evaluate. The `files` array is `manifest.tsv` restated as JSON, so a consumer
does not need to parse a bespoke TSV.

P2. The license must be **inherited and recorded at distillation time**, not
looked up later. The teacher can be a Hugging Face id that gets re-pinned or
withdrawn; the artifact is immutable and must carry the answer with it.

P3. Consider making the artifact id content-addressed, or at least recording the
inputs' hashes, so that two distillations of the same vocabulary against the
same pinned teacher are recognisably the same model. Today a rebuild produces a
new UUID and nothing links them.

## 6. FACT: cross-references for this section

- Installing a teacher, and therefore unblocking the whole tab, happens on
  **Models and data**, not here. There is no link from the Trainer tab to it;
  see findings/states-links-and-tests.md.
- The catalog entries a teacher comes from live in
  opennlp-grpc-installer (StandardModelCatalog.java:165, :185) and are shipped
  as metadata only, never as bytes (StandardModelCatalog.java:33 class javadoc).
- Three pre-distilled Model2Vec models (`potion-base-8m`,
  `potion-retrieval-32m`, `potion-multilingual-128m`) are already installable
  with role `MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING`
  (StandardModelCatalog.java:204, :218, :232). They are the natural comparison
  point for a locally trained model, and the trainer never mentions them.

## Questions for the lead

1. Is the intended story "share a bucket between servers" or "export a file the
   user downloads"? They need different work: the first needs an import path and
   a documented shared-root mode, the second needs a packaged archive endpoint.
   The bucket already works for the first, halfway.
2. Adding `license`, `languages`, `teacher_reference` and `teacher_revision` to
   `StaticModelDescriptor` is a proto change to a published message. Is that in
   scope now, or does the model card start life as a separate side file?
3. Should the trainer offer the user a `provenance_summary` field? It is stored
   on every artifact and is currently hard-coded to a string the user never sees.
