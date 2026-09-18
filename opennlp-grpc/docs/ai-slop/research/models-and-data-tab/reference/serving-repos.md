# Serving-side model packaging: TorchServe, Triton, OCI model artifacts

Sources fetched 2026-08-28:

- https://raw.githubusercontent.com/pytorch/serve/master/model-archiver/README.md and the
  archiver sources `model_archiver/manifest_components/manifest.py` and `model.py`
- https://docs.pytorch.org/serve/getting_started.html
- https://raw.githubusercontent.com/triton-inference-server/server/main/docs/user_guide/model_repository.md
- https://raw.githubusercontent.com/triton-inference-server/server/main/docs/user_guide/model_configuration.md
- https://oras.land/docs/concepts/artifact
- CloudNativeAI/model-spec `docs/spec.md`, `docs/config.md`, `docs/annotations.md`

## A. TorchServe .mar archive

A `.mar` is a zip whose reserved folder `MAR-INF` holds `MANIFEST.json`. Built by
`torch-model-archiver` with these arguments:

```
--model-name        exported name, output file is <model-name>.mar
--version           model version string
--model-file        python file with the model architecture (eager mode)
--serialized-file   .pt or .pth state_dict, or an executable ScriptModule
--handler           built-in handler name or path to a custom handler file
--extra-files       comma separated dependency files
--requirements-file model specific requirements.txt
--config-file       model config YAML
--export-path       output directory
--archive-format    tgz | no-archive | zip-store | default
-f, --force         overwrite an existing .mar
```

Manifest keys, taken from the archiver source rather than prose. Top level
(`Manifest.__to_dict__`):

```
createdOn          "%d/%m/%Y %H:%M:%S" of archive creation
runtime            RuntimeType enum: "python" | "python3" | "LSP"
model              nested object, below
archiverVersion    model_archiver package version
```

Nested `model` object (`Model.__to_dict__`, keys emitted only when set):

```
modelName          required
serializedFile     basename of the weights file
handler            basename of the handler
modelFile          basename of the architecture .py
modelVersion
extensions
requirementsFile
configFile
```

## B. Triton Inference Server model repository

Directory layout, verbatim shape from `model_repository.md`:

```
<model-repository-path>/
  <model-name>/
    config.pbtxt
    [<output-labels-file> ...]
    [configs]/
      [<custom-config-file> ...]
    <version>/
      <model-definition-file>
    <version>/
      <model-definition-file>
```

Version subdirectories are numeric. Default model file name per backend:

| Backend | Default filename |
| --- | --- |
| TensorRT | `model.plan` |
| ONNX | `model.onnx` |
| TorchScript | `model.pt` |
| TensorFlow SavedModel | `model.savedmodel` |
| TensorFlow GraphDef | `model.graphdef` |
| OpenVINO | `model.xml` plus `model.bin` |
| Python | `model.py` |
| DALI | `model.dali` |

Minimal `config.pbtxt`:

```
platform: "tensorrt_plan"
max_batch_size: 8
input [
  {
    name: "input0"
    data_type: TYPE_FP32
    dims: [ 16 ]
  },
  {
    name: "input1"
    data_type: TYPE_FP32
    dims: [ 16 ]
  }
]
output [
  {
    name: "output0"
    data_type: TYPE_FP32
    dims: [ 16 ]
  }
]
```

Config fields: `name` (optional, defaults to the repository directory name), `backend` or
`platform`, `max_batch_size` (0 disables batching), `input` / `output` entries carrying
`name`, `data_type`, `dims`, optional `format` and `reshape`, plus `version_policy`,
`instance_group`, `dynamic_batching`, `model_warmup`, `default_model_filename`.

`version_policy` options:

```
version_policy: { all: {} }
version_policy: { latest: { num_versions: 2 } }
version_policy: { specific: { versions: [1,3] } }
```

`data_type` enum: `TYPE_BOOL`, `TYPE_UINT8`, `TYPE_UINT16`, `TYPE_UINT32`, `TYPE_UINT64`,
`TYPE_INT8`, `TYPE_INT16`, `TYPE_INT32`, `TYPE_INT64`, `TYPE_FP16`, `TYPE_FP32`,
`TYPE_FP64`, `TYPE_STRING`, `TYPE_BF16`.

## C. OCI artifacts: ORAS and the CNCF model-spec

ORAS pushes any blob set into an OCI registry. The artifact kind is declared by
`artifactType` on the image manifest, the payload lives in `layers` with per-layer
`mediaType`, and metadata rides in `annotations`.

```bash
oras push registry.example.com/myartifact:v1.0 \
  --artifact-type application/vnd.example+type \
  --annotation org.opencontainers.image.created=2023-08-03T00:21:51Z \
  myfile.tar
```

The generic manifest carries `schemaVersion: 2`, `mediaType`
`application/vnd.oci.image.manifest.v1+json`, the declared `artifactType`, a `config`
descriptor (`application/vnd.oci.empty.v1+json` with digest
`sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a` when unused),
the `layers` array, and an `annotations` map.

CNCF model-spec (modelpack) fixes those values for models:

- manifest `mediaType` MUST be `application/vnd.oci.image.manifest.v1+json`
- manifest `artifactType` MUST be `application/vnd.cncf.model.manifest.v1+json`
- config `mediaType` MUST be `application/vnd.cncf.model.config.v1+json`
- layer media types, each in `.raw`, `.tar`, `.tar+gzip`, `.tar+zstd` variants:
  `application/vnd.cncf.model.weight.v1`, `...model.weight.config.v1`,
  `...model.doc.v1`, `...model.code.v1`, `...model.dataset.v1`

Predefined annotation keys (docs/annotations.md):

```
org.cncf.model.filepath                  file path of the layer
org.cncf.model.file.metadata+json        JSON string of file metadata
org.cncf.model.file.mediatype.untested   media type classification is untested
```

Config object schema (`application/vnd.cncf.model.config.v1+json`), REQUIRED top level
`descriptor` and `config`:

```
descriptor: createdAt, authors[], vendor, family, name, version, title, description,
            docURL, sourceURL, datasetsURL[], revision, licenses[] (SPDX expressions)
config:     architecture, format ("onnx", "safetensors", "gguf", "pt"), paramSize,
            precision, quantization
```

Example model manifest:

```json
{
    "schemaVersion": 2,
    "mediaType": "application/vnd.oci.image.manifest.v1+json",
    "artifactType": "application/vnd.cncf.model.manifest.v1+json",
    "config": {
        "mediaType": "application/vnd.cncf.model.config.v1+json",
        "digest": "sha256:d5815835051dd97d800a03f641ed8162877920e734d3d705b698912602b8c763",
        "size": 301
    },
    "layers": [
        {
            "mediaType": "application/vnd.cncf.model.weight.v1.tar",
            "digest": "sha256:3f907c1a03bf20f20355fe449e18ff3f9de2e49570ffb536f1a32f20c7179808",
            "size": 30327160
        }
    ]
}
```
