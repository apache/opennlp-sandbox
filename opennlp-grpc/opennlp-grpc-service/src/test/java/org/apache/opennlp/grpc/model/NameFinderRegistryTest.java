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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    return ClassicNerBackendFactory.KEY_PREFIX + "person" + ClassicNerBackendFactory.KEY_SUFFIX;
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
  void closeReleasesCloseableModels() {
    // A DL name finder holds a native ONNX session and must be released on shutdown. The closeable
    // stub stands in for one (no ONNX model needed) and records its release.
    StubNerBackendFactory.resetCloseCount();
    final NameFinderRegistry registry = NameFinderRegistry.create(
        Map.of(StubNerBackendFactory.KEY_CLOSEABLE_TYPE, "person"));
    assertTrue(registry.supportsEntityType("person"));
    assertEquals(0, StubNerBackendFactory.closeCount());

    registry.close();
    assertEquals(1, StubNerBackendFactory.closeCount());
  }

  @Test
  void closeIsHarmlessWhenNoModelHoldsResources() {
    // Classic NameFinderME models hold no native resources; closing must not throw.
    StubNerBackendFactory.resetCloseCount();
    final NameFinderRegistry registry =
        NameFinderRegistry.create(Map.of(personKey(), personModelPath.toString()));
    assertDoesNotThrow(registry::close);
    assertEquals(0, StubNerBackendFactory.closeCount());
  }

  @Test
  void modelBundleCacheCloseReleasesNerModels() {
    // Regression guard: ModelBundleCache.close() must release the name-finder registry, not only
    // the embedding/doccat/sentiment registries, or DL NER sessions leak at server shutdown.
    StubNerBackendFactory.resetCloseCount();
    final ModelBundleCache cache =
        new ModelBundleCache(Map.of(StubNerBackendFactory.KEY_CLOSEABLE_TYPE, "person"));
    assertTrue(cache.getNameFinderRegistry().supportsEntityType("person"));
    assertEquals(0, StubNerBackendFactory.closeCount());

    cache.close();
    assertEquals(1, StubNerBackendFactory.closeCount());
  }

  @Test
  void modelBundleCacheReleasesModelsWhenConstructionFails() {
    // A closeable NER model is created, then the parser load fails (bad path). The half-built
    // cache can never be close()d by the caller, so construction itself must release what it
    // already created rather than leaking the native session.
    StubNerBackendFactory.resetCloseCount();
    assertThrows(AnalysisException.class, () -> new ModelBundleCache(Map.of(
        StubNerBackendFactory.KEY_CLOSEABLE_TYPE, "person",
        "model.parser.path", "/no/such/parser-model.bin")));
    assertEquals(1, StubNerBackendFactory.closeCount());
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
    // The same underlying model is selected regardless of the requested type's case.
    assertEquals(registry.modelsForTypes(List.of("person")),
        registry.modelsForTypes(List.of("PERSON")));
    assertEquals(1, registry.modelsForTypes(List.of("PERSON")).size());
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
  void rejectsDlConfigMissingRequiredAttribute() {
    // path present but vocab/labels missing.
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        NameFinderRegistry.create(Map.of(
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.path", "/tmp/model.onnx")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void rejectsDlConfigUnsupportedBackend() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        NameFinderRegistry.create(Map.of(
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.path", "/tmp/model.onnx",
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.vocab", "/tmp/vocab.txt",
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.labels", "/tmp/labels.txt",
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.backend", "tpu")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void rejectsDlConfigWithoutSentenceDetector() {
    // A complete ONNX config still needs a sentence detector; create(config) supplies none.
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        NameFinderRegistry.create(Map.of(
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.path", "/tmp/model.onnx",
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.vocab", "/tmp/vocab.txt",
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.labels", "/tmp/labels.txt")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("sentence detector"));
  }

  @Test
  void resolveEntityTypesReturnsAllConfiguredWhenFilterUnset() {
    final NameFinderRegistry registry =
        NameFinderRegistry.create(Map.of(personKey(), personModelPath.toString()));
    assertEquals(registry.entityTypes(), registry.resolveEntityTypes(List.of()));
  }

  @Test
  void discoversExternalBackendThroughServiceLoader() {
    // StubNerBackendFactory is registered only via test META-INF/services, like a third-party
    // jar. Its model joins the built-in backends' models in the same registry.
    final NameFinderRegistry registry = NameFinderRegistry.create(Map.of(
        personKey(), personModelPath.toString(),
        StubNerBackendFactory.KEY_TYPE, "gadget"));

    assertTrue(registry.supportsEntityType("gadget"));
    assertTrue(registry.supportsEntityType("person"));
    assertEquals(1, registry.modelsForTypes(List.of("gadget")).size());
    assertEquals(StubNerBackendFactory.FACTORY_ID,
        registry.modelsForTypes(List.of("gadget")).get(0).backendId());
  }

  @Test
  void externalBackendStaysInertWithoutItsConfiguration() {
    // Without the stub's activation key, only the built-in classic model is present.
    final NameFinderRegistry registry =
        NameFinderRegistry.create(Map.of(personKey(), personModelPath.toString()));
    assertEquals(List.of("person"), registry.entityTypes());
  }
}
