# ONNX Model Zoo layout and ONNX_HUB_MANIFEST.json

Sources fetched 2026-08-28:

- https://raw.githubusercontent.com/onnx/models/main/ONNX_HUB_MANIFEST.json
- https://raw.githubusercontent.com/onnx/models/main/README.md
- docs/Hub.md from the onnx/onnx repository at tag v1.16.0 (the file is no longer present
  on onnx/onnx main, and https://onnx.ai/onnx/repo-docs/Hub.html returns 404 as of the
  fetch date, so the tagged copy was used)

## 1. Repository layout

```
<repo root>/
  ONNX_HUB_MANIFEST.json
  validated/
    vision/classification/resnet/...
    text/machine_comprehension/bert-squad/model/bertsquad-10.onnx
  ...
```

Models that have passed accuracy verification live under `validated/`. Each model folder
holds the serialized `model.onnx` protobuf plus test data as either `test_data_set_*`
directories of `.pb` tensors or `test_data_*.npz` NumPy archives. Model binaries are
tracked in Git LFS. The README notes that LFS download was discontinued on 1 July 2025 and
points at https://huggingface.co/onnxmodelzoo for the binaries.

## 2. Manifest entry schema

One entry per model plus opset combination. First entry of the live manifest:

```json
{
    "model": "BERT-Squad",
    "model_path": "validated/text/machine_comprehension/bert-squad/model/bertsquad-10.onnx",
    "onnx_version": "1.5",
    "opset_version": 10,
    "metadata": {
        "model_sha": "5945dee6478abdab2d5e4ce3868b4d969741e3dad2134cc669da65a4f092755b",
        "model_bytes": 435852734,
        "tags": ["text", "machine comprehension", "bert-squad"],
        "io_ports": {
            "inputs": [
                {"name": "segment_ids:0", "shape": ["unk__493", 256], "type": "tensor(int64)"},
                {"name": "input_mask:0", "shape": ["unk__494", 256], "type": "tensor(int64)"},
                {"name": "input_ids:0", "shape": ["unk__495", 256], "type": "tensor(int64)"}
            ],
            "outputs": [
                {"name": "unstack:1", "shape": ["unk__496", 256], "type": "tensor(float)"},
                {"name": "unique_ids:0", "shape": ["unk__498"], "type": "tensor(int64)"}
            ]
        },
        "model_with_data_path": "validated/text/machine_comprehension/bert-squad/model/bertsquad-10.tar.gz",
        "model_with_data_sha": "0ff18af268a891e7de390c5476191084e95eafba2763a69091c83ced7030c8b2",
        "model_with_data_bytes": 403398451
    }
}
```

Field meaning, quoted from docs/Hub.md:

- `model`: "The name of the model used for querying"
- `model_path`: "The relative path of the model stored in Git LFS."
- `onnx_version`: "The ONNX version of the model"
- `opset_version`: "The version of the opset. The client downloads the latest opset if left unspecified."
- `metadata/model_sha`: "Optional model sha specification for increased download security"
- `metadata/tags`: "Optional high level tags to help users find models by a given type"
- "All other fields in the `metadata` field are optional for the client but provide
  important details for users."

Additional metadata keys observed in the live manifest: `model_bytes`, `io_ports`
(with `inputs` / `outputs`, each entry `name` + `shape` + `type` where `type` is an ONNX
tensor type string such as `tensor(float)` and unknown dims appear as `"unk__NNN"`),
`model_with_data_path`, `model_with_data_sha`, `model_with_data_bytes`.

## 3. How onnx.hub resolves an entry

```python
from onnx import hub

model = hub.load("resnet50")
model = hub.load("resnet50", repo="onnx/models:771185265efbdc049fb223bd68ab1aeb1aecde76")

all_models    = hub.list_models()
mnist_models  = hub.list_models(model="mnist")
vision_models = hub.list_models(tags=["vision"])

print(hub.get_model_info(model="mnist", opset=8))
```

Signatures:

```
onnx.hub.load(model, repo="onnx/models:main", opset=None, force_reload=False, silent=False)
onnx.hub.get_model_info(model, repo="onnx/models:main", opset=None)
onnx.hub.list_models(repo="onnx/models:main", model=None, tags=None)
onnx.hub.download_model_with_test_data(...)
onnx.hub.set_dir(path) / onnx.hub.get_dir()
```

`repo` is `"user/repo[:branch]"` and may name a branch or a commit sha. Resolution:
the client fetches `ONNX_HUB_MANIFEST.json` from the top level of that GitHub repository,
matches on `model` name and `opset` (largest opset when `opset=None`), then downloads
`model_path` relative to the repo root and verifies the bytes against `model_sha`.

`get_model_info` prints a `ModelInfo` whose repr exposes `model`, `opset`, `path` and the
whole `metadata` dict:

```
ModelInfo(
    model=MNIST,
    opset=8,
    path=vision/classification/mnist/model/mnist-8.onnx,
    metadata={'model_sha': '2f06e72d...', 'model_bytes': 26454,
              'tags': ['vision', 'classification', 'mnist'],
              'io_ports': {...},
              'model_with_data_path': '...mnist-8.tar.gz',
              'model_with_data_sha': '1dd098b0...',
              'model_with_data_bytes': 25962}
)
```

## 4. Cache resolution

Cache directory lookup order:

1. `$ONNX_HOME/hub` if `ONNX_HOME` is set
2. `$XDG_CACHE_HOME/hub` if `XDG_CACHE_HOME` is set
3. `~/.cache/onnx/hub`

"the model cache directory structure will mirror the directory structure specified by the
`model_path` field of the manifest, but with file names disambiguated with model SHA256
Hashes." Because entries are disambiguated by hash, `force_reload=True` is not needed for
normal use, and cached models can be reused across hubs when name and hash agree.

## 5. Hosting your own hub

"To host your own model hub, add an ONNX_HUB_MANIFEST.json to the top level of your github
repository." Official contributions require a markdown table in the model's `README.md`;
the manifest generator pulls the metadata out of those tables.
