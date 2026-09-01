/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.model;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.StringList;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.opennlp.grpc.spi.model.NerModel;

/**
 * Unit tests for the model-free dictionary NER backend configured through
 * {@code model.name_finder_dictionary.<entity_type>.path}. The dictionary file is either a
 * plain wordlist (one entry per line) or a serialized OpenNLP dictionary; both are fully
 * offline fixtures.
 */
class DictionaryNerBackendFactoryTest {

  @TempDir
  Path dir;

  // "She moved to Kansas City yesterday"
  private static AnnotatedSentence sentence() {
    return AnnotatedSentence.newBuilder()
        .addTokens(NerTestFixtures.token("She", 0, 3))
        .addTokens(NerTestFixtures.token("moved", 4, 9))
        .addTokens(NerTestFixtures.token("to", 10, 12))
        .addTokens(NerTestFixtures.token("Kansas", 13, 19))
        .addTokens(NerTestFixtures.token("City", 20, 24))
        .addTokens(NerTestFixtures.token("yesterday", 25, 34))
        .build();
  }

  private Path wordlist(String... entries) throws IOException {
    return Files.write(dir.resolve("cities.txt"), List.of(entries));
  }

  @Test
  void recognizesMultiTokenWordlistEntriesAsEntities() throws IOException {
    final Path list = wordlist("Kansas City", "Springfield");
    final NameFinderRegistry registry = NameFinderRegistry.create(
        Map.of("model.name_finder_dictionary.city.path", list.toString()));

    assertTrue(registry.isAvailable());
    assertEquals(List.of("city"), registry.entityTypes());

    final List<NamedEntity> entities =
        registry.allModels().get(0).recognize(sentence(), false);
    assertEquals(1, entities.size());
    final NamedEntity entity = entities.get(0);
    assertEquals("city", entity.getEntityType());
    assertEquals(13, entity.getAnnotationSpan().getStart());
    assertEquals(24, entity.getAnnotationSpan().getEnd());
  }

  @Test
  void wordlistMatchingIsCaseInsensitiveByDefault() throws IOException {
    final Path list = wordlist("kansas city");
    final NameFinderRegistry registry = NameFinderRegistry.create(
        Map.of("model.name_finder_dictionary.city.path", list.toString()));

    assertEquals(1, registry.allModels().get(0).recognize(sentence(), false).size());
  }

  @Test
  void loadsSerializedOpenNlpDictionaries() throws IOException {
    final Dictionary dictionary = new Dictionary(true);
    dictionary.put(new StringList("Kansas", "City"));
    final Path serialized = dir.resolve("cities.dict");
    try (OutputStream out = Files.newOutputStream(serialized)) {
      dictionary.serialize(out);
    }
    final NameFinderRegistry registry = NameFinderRegistry.create(
        Map.of("model.name_finder_dictionary.city.path", serialized.toString()));

    final List<NamedEntity> entities =
        registry.allModels().get(0).recognize(sentence(), false);
    assertEquals(1, entities.size());
    assertEquals("city", entities.get(0).getEntityType());
  }

  @Test
  void dictionaryModelIsStatelessAndReportsItsBackend() throws IOException {
    final Path list = wordlist("Kansas City");
    final NerModel model = NameFinderRegistry.create(
        Map.of("model.name_finder_dictionary.city.path", list.toString())).allModels().get(0);

    assertFalse(model.isStateful());
    assertEquals("dictionary", model.backendId());
    assertFalse(model.artifactHash().isEmpty());
  }

  @Test
  void exactMatchesCarryFullConfidenceWhenProbabilitiesAreRequested() throws IOException {
    final Path list = wordlist("Kansas City");
    final NerModel model = NameFinderRegistry.create(
        Map.of("model.name_finder_dictionary.city.path", list.toString())).allModels().get(0);

    // A dictionary match is deterministic, so the reported confidence is exactly 1.
    final NamedEntity withProbability = model.recognize(sentence(), true).get(0);
    assertTrue(withProbability.hasProbability());
    assertEquals(1.0, withProbability.getProbability());
    assertFalse(model.recognize(sentence(), false).get(0).hasProbability());
  }

  @Test
  void blankPathFailsLoud() {
    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> NameFinderRegistry.create(Map.of("model.name_finder_dictionary.city.path", " ")));
    assertTrue(e.getMessage().contains("city"));
  }

  @Test
  void missingFileFailsLoud() {
    assertThrows(AnalysisException.class, () -> NameFinderRegistry.create(
        Map.of("model.name_finder_dictionary.city.path", dir.resolve("absent.txt").toString())));
  }

  @Test
  void blankEntityTypeFailsLoud() {
    assertThrows(AnalysisException.class, () -> NameFinderRegistry.create(
        Map.of("model.name_finder_dictionary..path", "cities.txt")));
  }
}
