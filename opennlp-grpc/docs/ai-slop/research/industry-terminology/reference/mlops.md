# Reference: ML ops terminology, gathered from vendor and project documentation

Evidence file for the Workbench terminology audit. Each section names a product or source, its
URL, short verbatim excerpts, and what that source calls the concepts we care about. Excerpts
are quoted exactly. Failed fetches are recorded as failures, not filled in.
All pages fetched 2026-08-28.

## MLflow

Sources: https://mlflow.org/docs/latest/ml/tracking/ and
https://mlflow.org/docs/latest/ml/model-registry/ . Checkpoint wording is from the autolog and
pytorch API pages under mlflow.org, reached through the search index rather than a direct fetch.

> "executions of some piece of data science code, for example, a single `python train.py` execution. Each run records metadata...and artifacts"
> "An experiment groups together runs and models for a specific task."
> "output files from the run such as model weights, images, etc"
> "a centralized model store, set of APIs and a UI designed to collaboratively manage the full lifecycle of a machine learning model"
> "An MLflow Model can be registered with the Model Registry. A registered model has a unique name, contains versions, aliases, tags, and other metadata."
> "Each registered model can have one or many versions. When a new model is added to the Model Registry, it is added as version 1."
> "Model aliases allow you to assign a mutable, named reference to a particular version of a registered model."
> "Tags are key-value pairs that you associate with registered models and model versions"

- **experiment** groups **runs**; a run emits **metrics**, **params**, and **artifacts**.
- **artifact** is genuinely standard MLflow vocabulary, and it means an output file of a run
  (weights, images, plots), stored in the **artifact store**.
- Registry nouns: **Model Registry**, **registered model**, **model version**, **alias**, **tag**.
  Current docs lead with **aliases**; the older **stage** vocabulary is not used on that page.
- **checkpoint** in MLflow means a saved model state captured partway through training (per
  epoch or per N batches, optionally weights only), logged as an artifact. It never means
  "write an index to disk".

## Hugging Face Hub

Sources: https://huggingface.co/docs/hub/en/model-cards and
https://huggingface.co/docs/transformers/en/glossary . The checkpoint definition below is from
https://huggingface.co/docs/transformers/en/models via the search index; the glossary page,
which was fetched directly, has no entry for "checkpoint".

> "Model cards are files that accompany the models and provide handy information. Under the hood, model cards are simple Markdown files with additional metadata."
> "You can find a model card as the `README.md` file in any model repo."
> "A model repo will render its `README.md` as a model card."

Via search index, transformers "Loading models" page: "A checkpoint refers to the model's
weights for a given architecture. For example, BERT is an architecture while
google-bert/bert-base-uncased is a checkpoint."

- Storage unit: **model repository** (a git repo). Metadata document: **model card**, which is
  the repo `README.md` with a YAML header. Neighbouring repo types: **dataset**, **Space**.
- Version selectors are git-shaped: **revision**, **branch**, **tag**, plus metadata keys
  `base_model` and `new_version`.
- **checkpoint on the Hub means a set of saved weights for an architecture.** This directly
  collides with any use of "checkpoint" to mean "flush state to disk". Anyone reading
  "checkpoint" next to model names will read it as weights.

## Knowledge distillation vocabulary

Sources: https://huggingface.co/docs/transformers/en/tasks/knowledge_distillation_for_image_classification
and https://arxiv.org/abs/1503.02531

> "Knowledge distillation is a technique used to transfer knowledge from a larger, more complex model (teacher) to a smaller, simpler model (student)."
> "we take a pre-trained teacher model trained on a certain task ... and randomly initialize a student model"
> "This guide demonstrates how you can distill a fine-tuned ViT model (teacher model) to a MobileNet (student model)"

Honest note on the Hinton citation: the paper is titled "Distilling the Knowledge in a Neural
Network" (Hinton, Vinyals, Dean, 2015, arXiv:1503.02531), and its abstract uses the phrase
"cumbersome model", for example "making predictions using a whole ensemble of models is
cumbersome and may be too computationally expensive". The words "teacher" and "student" do not
appear in the abstract, so cite the Hugging Face task guide, not the abstract, for those two.

- **teacher model** and **student model** are the standard pair, and the Hugging Face task
  guide uses both words in exactly that sense.

## Model2Vec and static embeddings

Sources: https://github.com/MinishLab/model2vec and https://huggingface.co/blog/static-embeddings

> "Model2Vec is a technique to turn any sentence transformer into a small, fast static embedding model"
> "you can distill your own Model2Vec model from a Sentence Transformer model"
> "Distill a Sentence Transformer model, in this case the BAAI/bge-base-en-v1.5 model."
> "don't use large and slow attention-based models, but instead rely on pre-computed token embeddings"
> "feed unsupervised data through a larger embedding model and distil those embeddings into the static embedding-based student model"

- Model2Vec calls its output a **static embedding model**, and calls the distillation source a
  **Sentence Transformer model**, not a "teacher model". "Teacher" is the general field word;
  Model2Vec itself does not use it in its README.
- **static embeddings** is the standard contrast to **contextual** embeddings. The Hugging Face
  blog frames it as a lookup of pre-computed token embeddings versus attention layers whose
  output for a token differs between "river bank" and the financial institution.
- So "static" here means non-contextual, not frozen or not-updated. The frozen sense is
  expressed with "freezing its weights" in the Hugging Face glossary entry on finetuning.

## "Bundle" as a word for a packaged model

Sources: https://onnx.ai/onnx/intro/concepts.html , https://www.tensorflow.org/guide/saved_model ,
https://opennlp.apache.org/docs/2.5.4/manual/opennlp.html

> "A machine-learning model implemented with ONNX is often referenced as an ONNX graph."
> "A SavedModel contains a complete TensorFlow program, including trained parameters (i.e, tf.Variables) and computation."
> "The loaded version of SavedModel is referred to as SavedModelBundle and contains the MetaGraphDef and the session within which it is loaded."

- ONNX: the artifact is an **ONNX model** or **ONNX graph**, serialized to a `.onnx` file. The
  word "bundle" does not appear on the concepts page.
- TensorFlow: the on-disk directory is a **SavedModel**. "Bundle" appears only as the C++
  in-memory type `SavedModelBundle`, not as a name for the saved directory.
- MLflow calls it a **model artifact**; Hugging Face calls it a **repository**.
- Apache OpenNLP's own manual says **model file** and the `.bin` extension, and does use
  "bundle" as a verb in the section heading "Bundling a custom trained OpenNLP model for the
  classpath". That is packaging for the classpath, not a name for the file.
- Verdict: **there is no standard noun "model bundle".** The safe words are model file,
  model artifact, or SavedModel, depending on the ecosystem.

## Catalog vs registry vs hub vs zoo

Sources: https://mlflow.org/docs/latest/ml/model-registry/ , https://github.com/onnx/models ,
https://huggingface.co/docs/hub/en/model-cards ,
https://learn.microsoft.com/en-us/azure/ai-foundry/concepts/foundry-models-overview

> "a centralized model store, set of APIs and a UI designed to collaboratively manage the full lifecycle of a machine learning model"
> "A collection of pre-trained, state-of-the-art models in the ONNX format"
> "The model catalog is organized into two main categories"
> "Request that Microsoft add a model to the model catalog right from the model catalog page"

- All four words are live and they are not synonyms.
  **registry** is the versioned, governed store of your own models (MLflow).
  **hub** is the shared public site of many people's models (Hugging Face Hub).
  **zoo** is a curated collection of pretrained models (the repository titles itself
  "ONNX Model Zoo").
  **catalog** is the browse-and-pick surface over models you did not train (Microsoft Foundry
  model catalog).
- If the thing is versioned and owned, "registry" is the more precise word than "catalog".

## Drift

Sources: https://www.evidentlyai.com/ml-in-production/data-drift and
https://www.evidentlyai.com/blog/tutorial-detecting-drift-in-text-data plus
https://learn.evidentlyai.com/ml-observability-course/module-3-ml-monitoring-for-unstructured-data/monitoring-data-drift-with-descriptors
(the two blog and course pages via search index; the ml-in-production page fetched directly).

> "Data drift is a change in the statistical properties and characteristics of the input data."
> "Concept drift relates to changes in the relationships between input and target variables."

Via search index, text drift material: a **text descriptor** is any feature you can derive from
raw text, and example descriptors given include "the share of out-of-vocabulary words".

- **data drift** and **concept drift** are standard and precisely distinguished.
  **embedding drift** is also used: the directly fetched page links "Drift detection methods for
  embeddings" and covers drift "for unstructured data, including raw text data and embeddings".
- **"vocabulary drift" is not standard.** It does not appear anywhere on the Evidently data
  drift page. A search surfaced the phrase only in one third-party course page
  (apxml.com), which returned HTTP 403 on fetch, so there is no verifiable quote for it.
- The standard measurable is the **out-of-vocabulary (OOV) rate** or **share of
  out-of-vocabulary words**, sometimes framed as **vocabulary coverage**. Prefer "OOV rate".

## Dimensionality and projection

Sources: https://huggingface.co/blog/matryoshka ,
https://umap-learn.readthedocs.io/en/latest/basic_usage.html ,
https://www.tensorflow.org/tensorboard/tensorboard_projector_plugin

> "Matryoshka embedding models can produce useful embeddings of various dimensions."
> "After receiving the embeddings, we can optionally truncate them to a smaller dimensionality."
> "graphical representation of high dimensional embeddings"

UMAP's own tutorial labels its plots "UMAP projection of the Penguin dataset" and stores the
result on the `embedding_` attribute.

- **Matryoshka** models support **truncation** to fewer **dimensions**, and this is a training
  property, not a post-hoc transform. The blog does not mention PCA at all, so do not present
  Matryoshka truncation as a kind of PCA.
- **dimensionality reduction** is the general term; PCA, t-SNE, and UMAP are its instances.
- **projection** in this field means the low-dimensional view of high-dimensional vectors
  produced by such a method, as in "UMAP projection of ..." and TensorBoard's **Embedding
  Projector** tool. Honest note: the directly fetched TensorBoard projector page describes a
  "graphical representation of high dimensional embeddings" and a "low-dimensional space" but
  does not itself use the noun "projection", so UMAP is the better citation.
- Because of that settled meaning, **"projection" is a poor name for one chunking strategy's
  output view.** Readers will expect a 2D or 3D scatter of reduced vectors.

## DVC

Source: https://doc.dvc.org/user-guide/pipelines/defining-pipelines

> "Pipelines represent data workflows that you want to reproduce reliably"
> "A simple dependency is a file or directory used as input by the stage command."
> "Stage outputs are files (or directories) written by pipelines, for example machine learning models and intermediate artifacts."

- DVC vocabulary: **pipeline** made of **stages**, each with **dependencies** and **outputs**,
  arranged as a DAG. **artifact** is used for the produced files, matching MLflow's sense.
