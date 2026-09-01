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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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
 * Unit tests for the model-free regex NER backend configured through
 * {@code model.name_finder_regex.<entity_type>.path}: a text file with one Java regular
 * expression per line, where blank lines and {@code #} comment lines are ignored.
 */
class RegexNerBackendFactoryTest {

  @TempDir
  Path dir;

  // "Pay INV-42 by Friday"
  private static AnnotatedSentence sentence() {
    return AnnotatedSentence.newBuilder()
        .addTokens(NerTestFixtures.token("Pay", 0, 3))
        .addTokens(NerTestFixtures.token("INV-42", 4, 10))
        .addTokens(NerTestFixtures.token("by", 11, 13))
        .addTokens(NerTestFixtures.token("Friday", 14, 20))
        .build();
  }

  private Path patterns(String... lines) throws IOException {
    return Files.write(dir.resolve("invoice.regex"), List.of(lines));
  }

  @Test
  void recognizesPatternMatchesAsEntities() throws IOException {
    final Path file = patterns("# invoice ids", "", "INV-[0-9]+");
    final NameFinderRegistry registry = NameFinderRegistry.create(
        Map.of("model.name_finder_regex.invoice.path", file.toString()));

    assertTrue(registry.isAvailable());
    assertEquals(List.of("invoice"), registry.entityTypes());

    final List<NamedEntity> entities =
        registry.allModels().get(0).recognize(sentence(), false);
    assertEquals(1, entities.size());
    final NamedEntity entity = entities.get(0);
    assertEquals("invoice", entity.getEntityType());
    assertEquals(4, entity.getAnnotationSpan().getStart());
    assertEquals(10, entity.getAnnotationSpan().getEnd());
  }

  @Test
  void regexModelIsStatelessAndReportsItsBackend() throws IOException {
    final Path file = patterns("INV-[0-9]+");
    final NerModel model = NameFinderRegistry.create(
        Map.of("model.name_finder_regex.invoice.path", file.toString())).allModels().get(0);

    assertFalse(model.isStateful());
    assertEquals("regex", model.backendId());
    assertFalse(model.artifactHash().isEmpty());
  }

  @Test
  void exactMatchesCarryFullConfidenceWhenProbabilitiesAreRequested() throws IOException {
    final Path file = patterns("INV-[0-9]+");
    final NerModel model = NameFinderRegistry.create(
        Map.of("model.name_finder_regex.invoice.path", file.toString())).allModels().get(0);

    // A pattern match is deterministic, so the reported confidence is exactly 1.
    final NamedEntity withProbability = model.recognize(sentence(), true).get(0);
    assertTrue(withProbability.hasProbability());
    assertEquals(1.0, withProbability.getProbability());
    assertFalse(model.recognize(sentence(), false).get(0).hasProbability());
  }

  @Test
  void invalidPatternFailsLoudWithItsLineNumber() throws IOException {
    final Path file = patterns("INV-[0-9]+", "([broken");
    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> NameFinderRegistry.create(
            Map.of("model.name_finder_regex.invoice.path", file.toString())));
    assertTrue(e.getMessage().contains("line 2"));
  }

  @Test
  void patternFileWithoutPatternsFailsLoud() throws IOException {
    final Path file = patterns("# nothing but comments");
    assertThrows(AnalysisException.class, () -> NameFinderRegistry.create(
        Map.of("model.name_finder_regex.invoice.path", file.toString())));
  }

  @Test
  void missingFileFailsLoud() {
    assertThrows(AnalysisException.class, () -> NameFinderRegistry.create(
        Map.of("model.name_finder_regex.invoice.path", dir.resolve("absent.regex").toString())));
  }
}
