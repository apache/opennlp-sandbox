/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.opennlp.grpc.v1.ModelArtifactRole;
import org.apache.opennlp.grpc.v1.ModelCatalogDescriptor;

/** The immutable model catalog shipped as metadata, never as model bytes. */
final class StandardModelCatalog {

  private static final String APACHE_2 = "Apache-2.0";
  private static final String APACHE_2_URI =
      "https://www.apache.org/licenses/LICENSE-2.0";
  private static final String MIT = "MIT";
  private static final String MIT_URI = "https://opensource.org/license/mit";
  private static final String CC_BY_4_0 = "CC-BY-4.0";
  private static final String GUM_REVISION = "22fdf87f9c71c96bcc771461d06e689b1f90020d";
  private static final String GUM_RELEASE = "opennlp-grpc-gum-models-v1";
  private static final String GUM_RELEASE_ROOT =
      "https://github.com/ai-pipestream/opennlp-sandbox/releases/download/" + GUM_RELEASE;
  private static final String GUM_SOURCE =
      "https://github.com/ai-pipestream/opennlp-sandbox/releases/tag/" + GUM_RELEASE;
  private static final String GUM_LICENSE =
      "https://github.com/amir-zeldes/gum/blob/" + GUM_REVISION + "/LICENSE.md";

  private StandardModelCatalog() {
  }

  /**
   * Lists the standard model entries.
   *
   * @return All entries in stable catalog-id order.
   */
  static List<CatalogModel> models() {
    final StandardModelCatalog catalog = new StandardModelCatalog();
    final List<CatalogModel> models = new ArrayList<>();
    models.add(catalog.teacher());
    models.add(catalog.potionBase());
    models.add(catalog.potionMultilingual());
    models.add(catalog.potionRetrieval());
    models.add(catalog.gumChunker());
    models.add(catalog.gumParser());
    models.sort(Comparator.comparing(model -> model.descriptor().getCatalogId()));
    return List.copyOf(models);
  }

  /** Creates the pinned English distillation teacher entry. */
  private CatalogModel teacher() {
    final String revision = "1110a243fdf4706b3f48f1d95db1a4f5529b4d41";
    final String repository = "sentence-transformers/all-MiniLM-L6-v2";
    return model("all-minilm-l6-v2-teacher", "all-MiniLM-L6-v2 teacher",
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER,
        "all-minilm-l6-v2", repository, revision, APACHE_2, APACHE_2_URI,
        0, List.of("en"),
        "Compact ONNX sentence-transformer teacher for Model2Vec-style distillation",
        List.of(
            file(repository, revision, "tokenizer.json", 466_247,
                "be50c3628f2bf5bb5e3a7f17b1f74611b2561a3a27eeab05e5aa30f411572037"),
            file(repository, revision, "tokenizer_config.json", 350,
                "acb92769e8195aabd29b7b2137a9e6d6e25c476a4f15aa4355c233426c61576b"),
            file(repository, revision, "onnx/model.onnx", 90_405_214,
                "6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452")));
  }

  /** Creates the pinned general-purpose English static model entry. */
  private CatalogModel potionBase() {
    final String revision = "bf8b056651a2c21b8d2565580b8569da283cab23";
    final String repository = "minishlab/potion-base-8M";
    return model("potion-base-8m", "Potion Base 8M",
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING,
        "potion-base-8m", repository, revision, MIT, MIT_URI, 256, List.of("en"),
        "General-purpose English Model2Vec static embeddings",
        wordPieceFiles(repository, revision, 30_236_760, 219_690,
            "f65d0f325faadc1e121c319e2faa41170d3fa07d8c89abd48ca5358d9a223de2",
            "1394523a67ddd404a825428018c0582a6998bcfa044ecbcbf1f4d71adb94c61c",
            "2a6ac0e9aaa356a68a5688070db78fc3a464fefe85d2f06a1905ce3718687553"));
  }

  /** Creates the pinned English retrieval model entry. */
  private CatalogModel potionRetrieval() {
    final String revision = "6fc8051fab2a1e0ee76689cf08c853792ac285e7";
    final String repository = "minishlab/potion-retrieval-32M";
    return model("potion-retrieval-32m", "Potion Retrieval 32M",
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING,
        "potion-retrieval-32m", repository, revision, MIT, MIT_URI, 512, List.of("en"),
        "English Model2Vec static embeddings tuned for retrieval",
        wordPieceFiles(repository, revision, 129_210_456, 492_156,
            "07609e5bd33aad37900b3fd62f4ec96f6daec88ca4d46b9d8b928bfababf6ea0",
            "4b3452e69455f96c6cfc1cdb212d3b7b1a3e9d2505ab6f61a50022f61467a6a3",
            "63c00d90824c832c04ec1d02b6a983fb90489bf049f29fbff15ba481b8a432ee"));
  }

  /** Creates the pinned multilingual static model entry. */
  private CatalogModel potionMultilingual() {
    final String revision = "73908c3438cf03b6a01bcb9611d62b23d0726f08";
    final String repository = "minishlab/potion-multilingual-128M";
    return model("potion-multilingual-128m", "Potion Multilingual 128M",
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING,
        "potion-multilingual-128m", repository, revision, MIT, MIT_URI, 256,
        List.of("multilingual"),
        "Multilingual Model2Vec static embeddings with a self-contained Unigram tokenizer",
        List.of(
            file(repository, revision, "config.json", 271,
                "595e4cab2093732efd5dbe084fd5c1826b5eea693b73b4c1fd971672867d2e54"),
            file(repository, revision, "model.safetensors", 512_361_560,
                "14b5eb39cb4ce5666da8ad1f3dc6be4346e9b2d601c073302fa0a31bf7943397"),
            file(repository, revision, "tokenizer.json", 18_616_131,
                "19f1909063da3cfe3bd83a782381f040dccea475f4816de11116444a73e1b6a1")));
  }

  /** Creates the current OpenNLP chunker trained from the CC BY 4.0 GUM subset. */
  private CatalogModel gumChunker() {
    return releaseModel("gum-cc-by-4-chunker", "GUM CC BY 4.0 English chunker",
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_CHUNKER, "gum-cc-by-4", 196_936,
        "76ea0ba20807fafc9ae76113236da470707ab032a13cfa6626af40095caa5d16",
        "en-gum-cc-by-4-chunker.bin",
        "OpenNLP syntactic chunker trained from GUM academic and court trees; "
            + "held-out phrase F1 0.9051");
  }

  /** Creates the current OpenNLP parser trained from the CC BY 4.0 GUM subset. */
  private CatalogModel gumParser() {
    return releaseModel("gum-cc-by-4-parser", "GUM CC BY 4.0 English parser",
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER, "gum-cc-by-4", 1_097_289,
        "a28e1dc122eeb67a58a41a16c446dbbfc72cfce1fc05ddfdc93512be8c14e77d",
        "en-gum-cc-by-4-parser.bin",
        "OpenNLP constituency parser trained from GUM academic and court trees; "
            + "held-out constituent F1 0.6750");
  }

  /** Creates one release-hosted OpenNLP model entry. */
  private CatalogModel releaseModel(
      String catalogId, String displayName, ModelArtifactRole role, String modelId,
      long byteSize, String sha256, String fileName, String description) {
    final CatalogFile file = new CatalogFile(Path.of(fileName),
        URI.create(GUM_RELEASE_ROOT + "/" + fileName), byteSize, sha256);
    final ModelCatalogDescriptor descriptor = ModelCatalogDescriptor.newBuilder()
        .setCatalogId(catalogId)
        .setDisplayName(displayName)
        .setRole(role)
        .setModelId(modelId)
        .setSourceUri(GUM_SOURCE)
        .setRevision(GUM_RELEASE + "+gum-" + GUM_REVISION)
        .setLicenseName(CC_BY_4_0)
        .setLicenseUri(GUM_LICENSE)
        .setByteSize(byteSize)
        .addLanguages("en")
        .setDescription(description)
        .build();
    return new CatalogModel(descriptor, List.of(file));
  }

  /** Creates the common file list used by the WordPiece static models. */
  private List<CatalogFile> wordPieceFiles(
      String repository, String revision, long modelBytes, long vocabularyBytes,
      String modelHash, String vocabularyHash, String configHash) {
    return List.of(
        file(repository, revision, "config.json", 202, configHash),
        file(repository, revision, "model.safetensors", modelBytes, modelHash),
        file(repository, revision, "tokenizer_config.json", 1_431,
            "6725995e3ab3039857ff5bd99178a7cdf42863abb04449e7bb31feb1f55fe567"),
        file(repository, revision, "vocab.txt", vocabularyBytes, vocabularyHash));
  }

  /** Creates one public descriptor paired with its immutable files. */
  private CatalogModel model(
      String catalogId, String displayName, ModelArtifactRole role, String modelId,
      String repository, String revision, String licenseName, String licenseUri,
      int dimension, List<String> languages, String description, List<CatalogFile> files) {
    long bytes = 0;
    for (CatalogFile file : files) {
      bytes = Math.addExact(bytes, file.byteSize());
    }
    final ModelCatalogDescriptor descriptor = ModelCatalogDescriptor.newBuilder()
        .setCatalogId(catalogId)
        .setDisplayName(displayName)
        .setRole(role)
        .setModelId(modelId)
        .setSourceUri("https://huggingface.co/" + repository)
        .setRevision(revision)
        .setLicenseName(licenseName)
        .setLicenseUri(licenseUri)
        .setByteSize(bytes)
        .setDimension(dimension)
        .addAllLanguages(languages)
        .setDescription(description)
        .build();
    return new CatalogModel(descriptor, files);
  }

  /** Creates one pinned file entry in a Hugging Face repository. */
  private CatalogFile file(
      String repository, String revision, String relativePath, long byteSize, String sha256) {
    return new CatalogFile(Path.of(relativePath), URI.create(
        "https://huggingface.co/" + repository + "/resolve/" + revision + "/" + relativePath),
        byteSize, sha256);
  }
}
