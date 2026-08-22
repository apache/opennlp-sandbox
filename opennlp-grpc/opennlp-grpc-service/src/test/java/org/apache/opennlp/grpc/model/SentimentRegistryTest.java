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
import org.apache.opennlp.grpc.testing.TinySentimentModel;
import org.apache.opennlp.grpc.v1.DocumentClassification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SentimentRegistry}. The polarity model is trained in-memory from a
 * fixture corpus (see {@link TinySentimentModel}), so these tests are fully offline. They also
 * pin down that the {@code model.sentiment.*} namespace is isolated from {@code model.doccat.*}
 * even though both are served by the same {@link DocCategorizerBackendFactory} backends.
 */
class SentimentRegistryTest {

  @TempDir
  static Path modelDir;

  private static Path polarityModelPath;

  @BeforeAll
  static void trainPolarityModel() throws IOException {
    polarityModelPath = TinySentimentModel.trainPolarityModel(modelDir.resolve("polarity.bin"));
  }

  private static String pathKey(String id) {
    return SentimentRegistry.KEY_PREFIX + id + SentimentRegistry.KEY_SUFFIX;
  }

  @Test
  void emptyConfigurationProducesUnavailableRegistry() {
    final SentimentRegistry registry = SentimentRegistry.create(Map.of());
    assertFalse(registry.isAvailable());
    assertTrue(registry.modelIds().isEmpty());
    assertNull(registry.resolveDefaultModelId());
  }

  @Test
  void loadsConfiguredClassicModel() {
    final SentimentRegistry registry =
        SentimentRegistry.create(Map.of(pathKey("polarity"), polarityModelPath.toString()));

    assertTrue(registry.isAvailable());
    assertEquals(List.of("polarity"), registry.modelIds());
    assertTrue(registry.supportsModel("POLARITY"));
    assertEquals("polarity", registry.resolveDefaultModelId());
    assertEquals("opennlp-me", registry.get("polarity").backendId());
  }

  @Test
  void classifiesFixtureSentenceByPolarity() {
    final SentimentRegistry registry =
        SentimentRegistry.create(Map.of(pathKey("polarity"), polarityModelPath.toString()));
    final DocCategorizerModel model = registry.get("polarity");

    final DocumentClassification positive = model.classify(null,
        "wonderful excellent amazing delightful lovely".split(" "));
    assertEquals("positive", positive.getBestCategory());

    final DocumentClassification negative = model.classify(null,
        "terrible awful horrible disappointing dreadful".split(" "));
    assertEquals("negative", negative.getBestCategory());
    assertEquals(2, negative.getCategoryScoresCount());
  }

  @Test
  void defaultIdSelectsAmongMultipleModels() {
    final SentimentRegistry registry = SentimentRegistry.create(Map.of(
        pathKey("polarity"), polarityModelPath.toString(),
        pathKey("polarity2"), polarityModelPath.toString(),
        SentimentRegistry.KEY_DEFAULT_ID, "polarity2"));
    assertEquals("polarity2", registry.resolveDefaultModelId());
  }

  @Test
  void multipleModelsWithoutDefaultIsAmbiguous() {
    final SentimentRegistry registry = SentimentRegistry.create(Map.of(
        pathKey("polarity"), polarityModelPath.toString(),
        pathKey("polarity2"), polarityModelPath.toString()));
    assertTrue(registry.isAvailable());
    assertNull(registry.resolveDefaultModelId());
  }

  @Test
  void rejectsUnknownDefaultId() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        SentimentRegistry.create(Map.of(
            pathKey("polarity"), polarityModelPath.toString(),
            SentimentRegistry.KEY_DEFAULT_ID, "nope")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("nope"));
    assertTrue(error.getMessage().contains(SentimentRegistry.KEY_DEFAULT_ID));
  }

  @Test
  void rejectsMissingModelFileWithNotFound() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        SentimentRegistry.create(Map.of(pathKey("polarity"), "/no/such/path/polarity.bin")));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
    assertTrue(error.getMessage().contains("/no/such/path/polarity.bin"));
  }

  @Test
  void rejectsDlConfigMissingRequiredAttribute() {
    // path present but vocab/categories missing.
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        SentimentRegistry.create(Map.of(
            SentimentRegistry.KEY_DL_PREFIX + "polarity.path", "/tmp/model.onnx")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void discoversExternalBackendThroughServiceLoader() {
    // The same StubDocCategorizerBackendFactory that serves doccat also serves sentiment: its
    // sentiment-namespace key is canonicalized onto the doccat key the stub reads, so a
    // third-party backend is written once and contributes to both capabilities.
    final SentimentRegistry registry = SentimentRegistry.create(Map.of(
        pathKey("polarity"), polarityModelPath.toString(),
        "model.sentiment_stub.category", "mixed"));

    assertTrue(registry.supportsModel("polarity"));
    assertTrue(registry.supportsModel("stub:mixed"));
    assertEquals(StubDocCategorizerBackendFactory.FACTORY_ID,
        registry.get("stub:mixed").backendId());
  }

  @Test
  void ignoresDoccatNamespaceConfiguration() {
    // A model configured under the doccat namespace must not appear in the sentiment registry.
    final SentimentRegistry registry = SentimentRegistry.create(Map.of(
        "model.doccat.topic.path", polarityModelPath.toString()));
    assertFalse(registry.isAvailable());
    assertTrue(registry.modelIds().isEmpty());
  }

  @Test
  void doccatRegistryIgnoresSentimentNamespaceConfiguration() {
    // The reverse isolation: a sentiment-namespace model must not leak into the doccat registry.
    final DocCategorizerRegistry registry = DocCategorizerRegistry.create(Map.of(
        pathKey("polarity"), polarityModelPath.toString()));
    assertFalse(registry.isAvailable());
  }
}
