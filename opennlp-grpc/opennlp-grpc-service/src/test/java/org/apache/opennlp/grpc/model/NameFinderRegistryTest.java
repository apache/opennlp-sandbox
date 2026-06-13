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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.testing.TinyNerModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link NameFinderRegistry}. The person model is trained in-memory from a
 * fixture corpus (see {@link TinyNerModel}), so these tests are fully offline.
 */
class NameFinderRegistryTest {

  @TempDir
  static Path modelDir;

  private static Path personModelPath;

  @BeforeAll
  static void trainPersonModel() throws IOException {
    personModelPath = TinyNerModel.trainPersonModel(modelDir.resolve("person-ner.bin"));
  }

  private static String personKey() {
    return NameFinderRegistry.KEY_PREFIX + "person" + NameFinderRegistry.KEY_SUFFIX;
  }

  @Test
  void emptyConfigurationProducesUnavailableRegistry() {
    final NameFinderRegistry registry = NameFinderRegistry.create(Map.of());
    assertFalse(registry.isAvailable());
    assertTrue(registry.entityTypes().isEmpty());
  }

  @Test
  void loadsConfiguredPerTypeModels() {
    final NameFinderRegistry registry =
        NameFinderRegistry.create(Map.of(personKey(), personModelPath.toString()));

    assertTrue(registry.isAvailable());
    assertEquals(List.of("person"), registry.entityTypes());
    assertTrue(registry.supportsEntityType("person"));
  }

  @Test
  void entityTypeLookupIsCaseInsensitive() {
    final NameFinderRegistry registry =
        NameFinderRegistry.create(Map.of(personKey(), personModelPath.toString()));

    // Config key is stored normalized ("person"); client-supplied types match regardless
    // of case so a request for "PERSON" or " Person " resolves the same finder.
    assertTrue(registry.supportsEntityType("PERSON"));
    assertTrue(registry.supportsEntityType(" Person "));
    assertEquals(List.of("person"), registry.resolveEntityTypes(List.of("PERSON")));
    assertEquals(registry.get("person"), registry.get("PERSON"));
  }

  @Test
  void rejectsBlankEntityTypeInKey() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        NameFinderRegistry.create(Map.of("model.name_finder..path", "/tmp/model.bin")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void rejectsMissingModelFileWithNotFound() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        NameFinderRegistry.create(Map.of(personKey(), "/no/such/path/en-ner-person.bin")));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
    assertTrue(error.getMessage().contains("/no/such/path/en-ner-person.bin"));
  }

  @Test
  void rejectsDuplicateEntityType() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        NameFinderRegistry.create(Map.of(
            "model.name_finder.person.path", "/tmp/a.bin",
            "model.name_finder.PERSON.path", "/tmp/b.bin")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void resolveEntityTypesReturnsAllConfiguredWhenFilterUnset() {
    final NameFinderRegistry registry =
        NameFinderRegistry.create(Map.of(personKey(), personModelPath.toString()));
    assertEquals(registry.entityTypes(), registry.resolveEntityTypes(List.of()));
  }
}
