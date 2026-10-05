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
import java.util.ServiceLoader;

import org.apache.opennlp.grpc.spi.catalog.CatalogModel;
import org.apache.opennlp.grpc.spi.catalog.ModelCatalogProvider;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.opennlp.grpc.v1.ModelArtifactRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardModelCatalogTest {

  @Test
  void catalogsPinnedEmbeddingParserAndChunkerModels() {
    final List<CatalogModel> models = new StandardModelCatalog().models();

    assertEquals(List.of(
        "all-minilm-l6-v2-teacher",
        "de-ud-gsd-lemmas",
        "de-ud-gsd-pos",
        "de-ud-gsd-sentence",
        "de-ud-gsd-tokens", "en-ner-15-date", "en-ner-15-location",
            "en-ner-15-money", "en-ner-15-organization", "en-ner-15-percentage",
            "en-ner-15-person", "en-ner-15-time", "es-ud-gsd-lemmas",
        "es-ud-gsd-pos",
        "es-ud-gsd-sentence",
        "es-ud-gsd-tokens",
        "fr-ud-gsd-lemmas",
        "fr-ud-gsd-pos",
        "fr-ud-gsd-sentence",
        "fr-ud-gsd-tokens",
        "gum-cc-by-4-chunker",
        "gum-cc-by-4-parser",
        "open-english-wordnet-2024",
        "paraphrase-multilingual-minilm-l12-v2-teacher",
        "potion-base-8m",
        "potion-multilingual-128m",
        "potion-retrieval-32m",
        "t5-small-sentencepiece"),
        models.stream().map(model -> model.descriptor().getCatalogId()).toList());
    final Map<String, ModelArtifactRole> roles = models.stream().collect(
        Collectors.toMap(
            model -> model.descriptor().getCatalogId(),
            model -> model.descriptor().getRole()));
    assertEquals(ModelArtifactRole.MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER,
        roles.get("all-minilm-l6-v2-teacher"));
    assertEquals(ModelArtifactRole.MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER,
        roles.get("paraphrase-multilingual-minilm-l12-v2-teacher"));
    assertEquals(ModelArtifactRole.MODEL_ARTIFACT_ROLE_CHUNKER,
        roles.get("gum-cc-by-4-chunker"));
    assertEquals(ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER,
        roles.get("gum-cc-by-4-parser"));
    assertEquals(ModelArtifactRole.MODEL_ARTIFACT_ROLE_SENTENCE_DETECTOR,
        roles.get("de-ud-gsd-sentence"));
    assertEquals(ModelArtifactRole.MODEL_ARTIFACT_ROLE_TOKENIZER,
        roles.get("fr-ud-gsd-tokens"));
    assertEquals(ModelArtifactRole.MODEL_ARTIFACT_ROLE_POS_TAGGER,
        roles.get("es-ud-gsd-pos"));
    assertEquals(ModelArtifactRole.MODEL_ARTIFACT_ROLE_LEMMATIZER,
        roles.get("de-ud-gsd-lemmas"));
    assertTrue(models.stream()
        .filter(model -> model.descriptor().getCatalogId().startsWith("potion-"))
        .allMatch(model -> model.descriptor().getRole()
            == ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING));
    assertEquals(256, dimensionOf(models, "potion-base-8m"));
    assertEquals(256, dimensionOf(models, "potion-multilingual-128m"));
    assertEquals(512, dimensionOf(models, "potion-retrieval-32m"));
  }

  /** Returns the declared dimension of one catalog entry. */
  private static int dimensionOf(List<CatalogModel> models, String catalogId) {
    return models.stream()
        .filter(model -> catalogId.equals(model.descriptor().getCatalogId()))
        .findFirst().orElseThrow().descriptor().getDimension();
  }

  @Test
  void offersASentencePieceModelAndAWordNetLexicon() {
    final Map<String, CatalogModel> models = new StandardModelCatalog().models().stream()
        .collect(Collectors.toMap(model -> model.descriptor().getCatalogId(), model -> model));
    final CatalogModel subword = models.get("t5-small-sentencepiece");
    assertEquals(ModelArtifactRole.MODEL_ARTIFACT_ROLE_SUBWORD_MODEL,
        subword.descriptor().getRole());
    assertEquals("t5-small", subword.descriptor().getModelId());
    assertEquals("spiece.model", subword.files().getFirst().relativePath().toString());
    assertEquals(791_656, subword.descriptor().getByteSize());
    final CatalogModel wordnet = models.get("open-english-wordnet-2024");
    assertEquals(ModelArtifactRole.MODEL_ARTIFACT_ROLE_WORDNET_LEXICON,
        wordnet.descriptor().getRole());
    assertEquals("oewn-2024", wordnet.descriptor().getModelId());
    assertEquals("CC-BY-4.0", wordnet.descriptor().getLicenseName());
    assertEquals("english-wordnet-2024.xml.gz",
        wordnet.files().getFirst().relativePath().toString());
    assertEquals(12_912_118, wordnet.descriptor().getByteSize());
  }

  @Test
  void everyCatalogFileHasAnExactSizeAndSha256() {
    for (CatalogModel model : new StandardModelCatalog().models()) {
      long total = 0;
      for (CatalogFile file : model.files()) {
        assertTrue(file.relativePath().getNameCount() <= 2);
        assertTrue(file.byteSize() > 0);
        assertEquals(64, file.sha256().length());
        total += file.byteSize();
      }
      assertEquals(total, model.descriptor().getByteSize());
    }
  }

  @Test
  void offersTheSevenClassicEnglishNameFinders() {
    final List<CatalogModel> finders = new StandardModelCatalog().models().stream()
        .filter(model -> model.descriptor().getRole()
            == org.apache.opennlp.grpc.v1.ModelArtifactRole.MODEL_ARTIFACT_ROLE_NAME_FINDER)
        .toList();

    // Catalog entries list in catalog-id order.
    assertEquals(List.of("date", "location", "money", "organization", "percentage",
            "person", "time"),
        finders.stream().map(model -> model.descriptor().getModelId()).toList());
    for (CatalogModel finder : finders) {
      assertEquals(1, finder.files().size());
      assertTrue(finder.files().getFirst().source().toString()
          .startsWith("https://opennlp.sourceforge.net/models-1.5/en-ner-"));
      assertEquals("Apache-2.0", finder.descriptor().getLicenseName());
    }
  }

  @Test
  void everyCatalogIdUsesOnlyLowercaseLettersDigitsAndHyphens() {
    // The server's catalog store enforces this alphabet at startup; a violating entry
    // would take the whole server down, so the catalog pins it here.
    for (CatalogModel model : new StandardModelCatalog().models()) {
      final String id = model.descriptor().getCatalogId();
      assertTrue(!id.isBlank() && id.equals(id.trim()), "blank catalog id");
      for (int i = 0; i < id.length(); i++) {
        final char character = id.charAt(i);
        assertTrue((character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9') || character == '-',
            "catalog id '" + id + "' has an unsupported character '" + character + "'");
      }
    }
  }

  @Test
  void registersThroughTheCatalogSpi() {
    // The server discovers this catalog via ServiceLoader when the jar is on the classpath.
    assertTrue(ServiceLoader.load(ModelCatalogProvider.class).stream()
        .anyMatch(provider -> provider.type() == StandardModelCatalog.class));
  }
}
