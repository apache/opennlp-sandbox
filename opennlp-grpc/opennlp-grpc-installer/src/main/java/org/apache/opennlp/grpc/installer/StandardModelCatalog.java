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
package org.apache.opennlp.grpc.installer;

import org.apache.opennlp.grpc.spi.catalog.CatalogFile;
import org.apache.opennlp.grpc.spi.catalog.CatalogModel;
import org.apache.opennlp.grpc.spi.catalog.ModelCatalogProvider;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.opennlp.grpc.v1.ModelArtifactRole;
import org.apache.opennlp.grpc.v1.ModelCatalogDescriptor;

/**
 * The built-in immutable model catalog, shipped as metadata, never as model bytes.
 * Registered via ServiceLoader so the server discovers it when this add-on jar is on
 * the classpath.
 */
public final class StandardModelCatalog implements ModelCatalogProvider {

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
  private static final String UD_MODELS_ROOT =
      "https://downloads.apache.org/opennlp/models/ud-models-1.3";
  private static final String UD_MODELS_SOURCE = "https://opennlp.apache.org/models.html";
  private static final String UD_MODELS_REVISION = "ud-models-1.3-2.5.4";
  private static final String NER_15_ROOT = "https://opennlp.sourceforge.net/models-1.5";
  private static final String NER_15_SOURCE = "https://opennlp.apache.org/models.html";
  private static final String NER_15_REVISION = "models-1.5";

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public StandardModelCatalog() {
  }

  /**
   * Lists the standard model entries.
   *
   * @return All entries in stable catalog-id order.
   */
  @Override
  public List<CatalogModel> models() {
    final StandardModelCatalog catalog = this;
    final List<CatalogModel> models = new ArrayList<>();
    models.add(catalog.teacher());
    models.add(catalog.multilingualTeacher());
    models.add(catalog.potionBase());
    models.add(catalog.potionMultilingual());
    models.add(catalog.potionRetrieval());
    models.add(catalog.gumChunker());
    models.add(catalog.gumParser());
    models.add(catalog.classicNameFinder("person", "person names", 5_207_953,
        "687a9263d96b37fced707c9f2ac0560f9edaf54658856395555901924f64dbe4"));
    models.add(catalog.classicNameFinder("location", "locations", 5_110_658,
        "8fe39e48633f4a86c4132d9c54b396a2d8e0460c1d71e3562dacf976984f447b"));
    models.add(catalog.classicNameFinder("organization", "organizations", 5_297_172,
        "0136c12afe1ac357142260c39bb879b7c9d121e41024114db5a6455b4fd5ba00"));
    models.add(catalog.classicNameFinder("date", "dates", 5_030_307,
        "1207030923852e1c244919d8f15d9e78c217323728fcf909029abd1703967855"));
    models.add(catalog.classicNameFinder("money", "money amounts", 4_806_234,
        "b80d577d7d319038457e19f814438965aee9ef5cd1f4f175418d4aece8e504b8"));
    models.add(catalog.classicNameFinder("percentage", "percentages", 4_728_645,
        "dbc57162ba9784ae7a851393584aa7193aa2eee6ce2ec962fa937c9fa5e08137"));
    models.add(catalog.classicNameFinder("time", "time expressions", 4_724_357,
        "8a815e6e6d353ee4c478f85dc19b201361e955a9820487f2cf3a2f43c9c78274"));
    models.addAll(catalog.udPipeline("de", "German", "gsd",
        15_043, "b5553223d30a0422e80a28e2ae766a92dd7181e229f0d6c73087951e84142c43",
        524_098, "32d0a7ff84fdd50f9e454340bed782d7269845bfa656e0c988cbb823dd628d6e",
        1_269_178, "073fefbfaff2ed403bf474fccf793752d01c22e69f487cd68b43832d3123742f",
        854_900, "8850986f02dfbe293046bfce93bc0c3f3fa010e89177250abca23d860033d4f6"));
    models.addAll(catalog.udPipeline("fr", "French", "gsd",
        12_760, "ee1f8109cbca3c5a2799b873ee08202671920d526352edfc56462c2d80ab673c",
        543_614, "a06dbc9b72600ba0886f64a632636e136e5fc225955913c4c1a926a9333bc14a",
        1_558_836, "8ae017da8f93fd4219004681cdadbe4296e1c9dd66651eeca12bd053f5884172",
        924_647, "8e8f137265044ed2389c293a333715a4e18da58514197da2e8e1c950a59e8769"));
    models.addAll(catalog.udPipeline("es", "Spanish", "gsd",
        13_977, "043fed7bbae8281dc559d04ad0ed9271e90697ffdfc5be3a1852e703eea8b30a",
        539_431, "309de611ff7a8c4227c57086838d4783bcede8520026d7ad416c85a513567ce7",
        1_754_275, "c1d7e327acae631bd25936f2efc51c0110c9338fd6e52cfa3e3b234963e55cd5",
        1_039_483, "d9970825f87e95fbc4c54e7c35cb2af141a29fd65f21908d86e29ef9af2da914"));
    models.add(catalog.sentencePieceModel());
    models.add(catalog.openEnglishWordNet());
    models.sort(Comparator.comparing(model -> model.descriptor().getCatalogId()));
    return List.copyOf(models);
  }

  /**
   * Creates the four pinned Apache UD 1.3 pipeline models of one language: sentence
   * detector, tokenizer, POS tagger, and lemmatizer, all released by the Apache OpenNLP
   * project and activated on the next server start after installation.
   */
  private List<CatalogModel> udPipeline(String language, String languageName, String treebank,
      long sentenceSize, String sentenceSha256, long tokensSize, String tokensSha256,
      long posSize, String posSha256, long lemmasSize, String lemmasSha256) {
    final String modelId = language + "-ud-" + treebank;
    return List.of(
        udModel(modelId, languageName, language, treebank, "sentence",
            ModelArtifactRole.MODEL_ARTIFACT_ROLE_SENTENCE_DETECTOR,
            "sentence detector", sentenceSize, sentenceSha256),
        udModel(modelId, languageName, language, treebank, "tokens",
            ModelArtifactRole.MODEL_ARTIFACT_ROLE_TOKENIZER,
            "tokenizer", tokensSize, tokensSha256),
        udModel(modelId, languageName, language, treebank, "pos",
            ModelArtifactRole.MODEL_ARTIFACT_ROLE_POS_TAGGER,
            "POS tagger", posSize, posSha256),
        udModel(modelId, languageName, language, treebank, "lemmas",
            ModelArtifactRole.MODEL_ARTIFACT_ROLE_LEMMATIZER,
            "lemmatizer", lemmasSize, lemmasSha256));
  }

  /** Creates one pinned Apache UD 1.3 model entry. */
  private CatalogModel udModel(String modelId, String languageName, String language,
      String treebank, String fileKind, ModelArtifactRole role, String roleName,
      long byteSize, String sha256) {
    final String fileName =
        "opennlp-" + language + "-ud-" + treebank + "-" + fileKind + "-1.3-2.5.4.bin";
    final ModelCatalogDescriptor descriptor = ModelCatalogDescriptor.newBuilder()
        .setCatalogId(modelId + "-" + fileKind)
        .setDisplayName(languageName + " UD " + roleName)
        .setRole(role)
        .setModelId(modelId)
        .setSourceUri(UD_MODELS_SOURCE)
        .setRevision(UD_MODELS_REVISION)
        .setLicenseName(APACHE_2)
        .setLicenseUri(APACHE_2_URI)
        .setByteSize(byteSize)
        .addLanguages(language)
        .setDescription("Apache OpenNLP " + languageName + " " + roleName
            + " trained from the UD " + treebank.toUpperCase(Locale.ROOT)
            + " treebank")
        .build();
    return new CatalogModel(descriptor, List.of(new CatalogFile(Path.of(fileName),
        URI.create(UD_MODELS_ROOT + "/" + fileName), byteSize, sha256)));
  }

  /**
   * Creates the pinned SentencePiece entry: the T5 small unigram model, a compact general
   * English subword vocabulary that serves subword tokenization after a restart.
   */
  private CatalogModel sentencePieceModel() {
    final String revision = "df1b051c49625cf57a3d0d8d3863ed4d13564fe4";
    final String repository = "google-t5/t5-small";
    return model("t5-small-sentencepiece", "T5 small SentencePiece model",
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_SUBWORD_MODEL, "t5-small", repository, revision,
        APACHE_2, APACHE_2_URI, 0, List.of("en"),
        "32k-piece unigram SentencePiece model from T5 small, for subword tokenization",
        List.of(file(repository, revision, "spiece.model", 791_656,
            "d60acb128cf7b7f2536e8f38a5b18a05535c9e14c7a355904270e15b0945ea86")));
  }

  /**
   * Creates the pinned Open English WordNet 2024 entry, served gzipped as published, for
   * lexical expansion after a restart.
   */
  private CatalogModel openEnglishWordNet() {
    final String fileName = "english-wordnet-2024.xml.gz";
    final long byteSize = 12_912_118;
    final ModelCatalogDescriptor descriptor = ModelCatalogDescriptor.newBuilder()
        .setCatalogId("open-english-wordnet-2024")
        .setDisplayName("Open English WordNet 2024")
        .setRole(ModelArtifactRole.MODEL_ARTIFACT_ROLE_WORDNET_LEXICON)
        .setModelId("oewn-2024")
        .setSourceUri("https://en-word.net/")
        .setRevision("2024")
        .setLicenseName(CC_BY_4_0)
        .setLicenseUri("https://creativecommons.org/licenses/by/4.0/")
        .setByteSize(byteSize)
        .addLanguages("en")
        .setDescription("Open English WordNet 2024 in WN-LMF, for synonym and hypernym "
            + "expansion")
        .build();
    return new CatalogModel(descriptor, List.of(new CatalogFile(Path.of(fileName),
        URI.create("https://en-word.net/static/" + fileName), byteSize,
        "e1f633b0a93758cae34ea27c44c4dad310a8af2467b155f99dd6673af697e875")));
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

  /** Creates the pinned multilingual distillation teacher entry. */
  private CatalogModel multilingualTeacher() {
    final String revision = "e8f8c211226b894fcb81acc59f3b34ba3efd5f42";
    final String repository = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2";
    return model("paraphrase-multilingual-minilm-l12-v2-teacher",
        "paraphrase-multilingual-MiniLM-L12-v2 teacher",
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER,
        "paraphrase-multilingual-minilm-l12-v2", repository, revision, APACHE_2, APACHE_2_URI,
        0, List.of("multilingual"),
        "Multilingual ONNX sentence-transformer teacher (50+ languages) for "
            + "Model2Vec-style distillation",
        List.of(
            file(repository, revision, "tokenizer.json", 9_081_518,
                "2c3387be76557bd40970cec13153b3bbf80407865484b209e655e5e4729076b8"),
            file(repository, revision, "tokenizer_config.json", 526,
                "5036ea374ffedd706e3bef33e2e0d6953cb868ef8a490e76e32ba0faa37a6b9b"),
            file(repository, revision, "onnx/model.onnx", 470_301_610,
                "10f7a088420252b26caf819236ca2c9d2987afd0fc06fec7553b542a5655a05a")));
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

  /**
   * Creates one classic OpenNLP 1.5 English name finder entry. The model id is the entity
   * type it recognizes, so installation publishes {@code model.name_finder.<type>.path}.
   */
  private CatalogModel classicNameFinder(
      String entityType, String recognizes, long byteSize, String sha256) {
    final String fileName = "en-ner-" + entityType + ".bin";
    final CatalogFile file = new CatalogFile(Path.of(fileName),
        URI.create(NER_15_ROOT + "/" + fileName), byteSize, sha256);
    final ModelCatalogDescriptor descriptor = ModelCatalogDescriptor.newBuilder()
        .setCatalogId("en-ner-15-" + entityType)
        .setDisplayName("OpenNLP 1.5 English " + recognizes)
        .setRole(ModelArtifactRole.MODEL_ARTIFACT_ROLE_NAME_FINDER)
        .setModelId(entityType)
        .setSourceUri(NER_15_SOURCE)
        .setRevision(NER_15_REVISION)
        .setLicenseName(APACHE_2)
        .setLicenseUri(APACHE_2_URI)
        .setByteSize(byteSize)
        .addLanguages("en")
        .setDescription("Classic maxent name finder for English " + recognizes
            + " from the OpenNLP 1.5 model release; expects Penn-style tokenization")
        .build();
    return new CatalogModel(descriptor, List.of(file));
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
