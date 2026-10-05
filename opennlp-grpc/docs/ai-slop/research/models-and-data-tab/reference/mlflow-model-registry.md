# MLflow MLmodel format and Model Registry vocabulary

Sources fetched 2026-08-28:

- https://mlflow.org/docs/latest/ml/model/
- https://mlflow.org/docs/latest/ml/model-registry/
- https://mlflow.org/docs/latest/api_reference/rest-api.html

## 1. The MLmodel file

An MLflow model is a directory whose root contains an `MLmodel` YAML descriptor plus the
serialized artifacts it references.

Keys documented for `MLmodel`:

| Key | Meaning |
| --- | --- |
| `time_created` / `utc_time_created` | UTC ISO 8601 creation timestamp |
| `artifact_path` | path the model was logged under inside the run |
| `run_id` | id of the tracking run that produced the model |
| `model_uuid` | unique identifier assigned to the saved model |
| `mlflow_version` | MLflow version that logged the model |
| `flavors` | mapping of flavor name to flavor-specific config |
| `signature` | input and output schema, stored as JSON |
| `input_example` / `saved_input_example_info` | reference to the input example artifact |
| `metadata` | free-form user key/value block |
| `databricks_runtime` | runtime string when trained on Databricks |

Documented example:

```yaml
time_created: 2018-05-25T17:28:53.35
flavors:
  sklearn:
    sklearn_version: 0.19.1
    pickled_model: model.pkl
  python_function:
    loader_module: mlflow.sklearn
```

## 2. Flavors

A flavor is "a convention that deployment tools can use to understand the model". One
model directory can declare several flavors at once: a framework-native flavor plus the
universal `python_function` flavor, so a deployment tool only has to understand one of
them.

Built-in flavor names include:

```
python_function, sklearn, pytorch, tensorflow, keras, h2o, spark, xgboost, lightgbm,
catboost, onnx, statsmodels, prophet, pmdarima, transformers, sentence_transformers,
johnsnowlabs, spacy, crate
```

The `python_function` flavor carries `loader_module`, and framework flavors carry their
own keys such as `sklearn_version` and `pickled_model`.

## 3. Registry vocabulary

Definitions quoted from the model registry page:

- Registered Model: "An MLflow Model can be registered with the Model Registry. A
  registered model has a unique name, contains versions, aliases, tags, and other
  metadata."
- Model Version: "Each registered model can have one or many versions. When a new model is
  added to the Model Registry, it is added as version 1."
- Model Alias: "Model aliases allow you to assign a mutable, named reference to a
  particular version of a registered model."
- Tags: "Key-value pairs that you associate with registered models and model versions,
  allowing you to label and categorize them by function or status."

URI forms used to load a model out of the registry:

```
models:/<model-name>/<model-version>
models:/<model-name>@<alias-name>
```

Stages (`current_stage`) are the older mechanism, superseded by aliases and tags; the
stage vocabulary is `None`, `Staging`, `Production`, `Archived`, and a version moves
between them with the transition-stage call.

## 4. REST data structures

`RegisteredModel`

```
name                    STRING
creation_timestamp      INT64   (milliseconds)
last_updated_timestamp  INT64   (milliseconds)
user_id                 STRING
description             STRING
latest_versions         ModelVersion[]
tags                    RegisteredModelTag[]
```

`ModelVersion`

```
name                    STRING
version                 INT32
creation_timestamp      INT64
last_updated_timestamp  INT64
user_id                 STRING
current_stage           STRING
description             STRING
source                  STRING   (artifact URI the version was created from)
run_id                  STRING
status                  ModelVersionStatus
status_message          STRING
tags                    ModelVersionTag[]
```

`RegisteredModelAlias` is `{ alias: STRING, version: INT32 }`.
`ModelVersionTag` and `RegisteredModelTag` are both `{ key: STRING, value: STRING }`.

`ModelVersionStatus` enum: `PENDING_REGISTRATION`, `READY`, `FAILED_REGISTRATION`.

## 5. REST endpoints

```
POST   /api/2.0/mlflow/registered-models/create
GET    /api/2.0/mlflow/registered-models/get
POST   /api/2.0/mlflow/registered-models/rename
POST   /api/2.0/mlflow/registered-models/update
DELETE /api/2.0/mlflow/registered-models/delete
POST   /api/2.0/mlflow/registered-models/search
GET    /api/2.0/mlflow/registered-models/get-latest-versions
POST   /api/2.0/mlflow/registered-models/set-tag
DELETE /api/2.0/mlflow/registered-models/delete-tag
POST   /api/2.0/mlflow/registered-models/alias
DELETE /api/2.0/mlflow/registered-models/alias
POST   /api/2.0/mlflow/model-versions/create
GET    /api/2.0/mlflow/model-versions/get
POST   /api/2.0/mlflow/model-versions/update
DELETE /api/2.0/mlflow/model-versions/delete
POST   /api/2.0/mlflow/model-versions/search
GET    /api/2.0/mlflow/model-versions/get-download-uri
POST   /api/2.0/mlflow/model-versions/transition-stage
POST   /api/2.0/mlflow/model-versions/set-tag
DELETE /api/2.0/mlflow/model-versions/delete-tag
GET    /api/2.0/mlflow/model-versions/get-by-alias
```
