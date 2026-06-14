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
import org.apache.opennlp.grpc.testing.TinyDoccatModel;
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
 * Unit tests for {@link DocCategorizerRegistry}. The topic model is trained in-memory from a
 * fixture corpus (see {@link TinyDoccatModel}), so these tests are fully offline.
 */
class DocCategorizerRegistryTest {

  @TempDir
  static Path modelDir;

  private static Path topicModelPath;

  @BeforeAll
  static void trainTopicModel() throws IOException {
    topicModelPath = TinyDoccatModel.trainTopicModel(modelDir.resolve("topic-doccat.bin"));
  }

  private static String pathKey(String id) {
    return ClassicDocCategorizerBackendFactory.KEY_PREFIX + id
        + ClassicDocCategorizerBackendFactory.KEY_SUFFIX;
  }

  @Test
  void emptyConfigurationProducesUnavailableRegistry() {
    final DocCategorizerRegistry registry = DocCategorizerRegistry.create(Map.of());
    assertFalse(registry.isAvailable());
    assertTrue(registry.modelIds().isEmpty());
    assertNull(registry.resolveDefaultModelId());
  }

  @Test
  void loadsConfiguredClassicModel() {
    final DocCategorizerRegistry registry =
        DocCategorizerRegistry.create(Map.of(pathKey("topic"), topicModelPath.toString()));

    assertTrue(registry.isAvailable());
    assertEquals(List.of("topic"), registry.modelIds());
    assertTrue(registry.supportsModel("TOPIC"));
    assertEquals("topic", registry.resolveDefaultModelId());
  }

  @Test
  void classifiesFixtureDocumentByDominantVocabulary() {
    final DocCategorizerRegistry registry =
        DocCategorizerRegistry.create(Map.of(pathKey("topic"), topicModelPath.toString()));
    final DocCategorizerModel model = registry.get("topic");

    final DocumentClassification weather = model.classify(null,
        "rain storm clouds thunder forecast wind temperature".split(" "));
    assertEquals("weather", weather.getBestCategory());

    final DocumentClassification finance = model.classify(null,
        "stocks market shares dividend investor earnings profit".split(" "));
    assertEquals("finance", finance.getBestCategory());
    assertEquals(2, finance.getCategoryScoresCount());
    assertEquals("opennlp-me", model.backendId());
  }

  @Test
  void soleModelIsTheDefaultWithoutAnExplicitSelector() {
    final DocCategorizerRegistry registry =
        DocCategorizerRegistry.create(Map.of(pathKey("topic"), topicModelPath.toString()));
    assertEquals("topic", registry.resolveDefaultModelId());
  }

  @Test
  void multipleModelsWithoutDefaultIsAmbiguous() {
    final DocCategorizerRegistry registry = DocCategorizerRegistry.create(Map.of(
        pathKey("topic"), topicModelPath.toString(),
        pathKey("topic2"), topicModelPath.toString()));
    assertTrue(registry.isAvailable());
    // No default_id and several models -> ambiguous; the analyzer turns this into an error.
    assertNull(registry.resolveDefaultModelId());
  }

  @Test
  void defaultIdSelectsAmongMultipleModels() {
    final DocCategorizerRegistry registry = DocCategorizerRegistry.create(Map.of(
        pathKey("topic"), topicModelPath.toString(),
        pathKey("topic2"), topicModelPath.toString(),
        DocCategorizerRegistry.KEY_DEFAULT_ID, "topic2"));
    assertEquals("topic2", registry.resolveDefaultModelId());
  }

  @Test
  void rejectsUnknownDefaultId() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        DocCategorizerRegistry.create(Map.of(
            pathKey("topic"), topicModelPath.toString(),
            DocCategorizerRegistry.KEY_DEFAULT_ID, "nope")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("nope"));
  }

  @Test
  void rejectsBlankIdInKey() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        DocCategorizerRegistry.create(Map.of("model.doccat..path", "/tmp/model.bin")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void rejectsMissingModelFileWithNotFound() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        DocCategorizerRegistry.create(Map.of(pathKey("topic"), "/no/such/path/topic.bin")));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
    assertTrue(error.getMessage().contains("/no/such/path/topic.bin"));
  }

  @Test
  void rejectsDuplicateId() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        DocCategorizerRegistry.create(Map.of(
            "model.doccat.topic.path", topicModelPath.toString(),
            "model.doccat.TOPIC.path", topicModelPath.toString())));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void rejectsDlConfigMissingRequiredAttribute() {
    // path present but vocab/categories missing.
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        DocCategorizerRegistry.create(Map.of(
            OnnxDocCategorizerBackendFactory.KEY_DL_PREFIX + "topic.path", "/tmp/model.onnx")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void discoversExternalBackendThroughServiceLoader() {
    // StubDocCategorizerBackendFactory is registered only via test META-INF/services, like a
    // third-party jar. Its model joins the built-in backends' models in the same registry.
    final DocCategorizerRegistry registry = DocCategorizerRegistry.create(Map.of(
        pathKey("topic"), topicModelPath.toString(),
        StubDocCategorizerBackendFactory.KEY_CATEGORY, "spam"));

    assertTrue(registry.supportsModel("topic"));
    assertTrue(registry.supportsModel("stub:spam"));
    assertEquals(StubDocCategorizerBackendFactory.FACTORY_ID,
        registry.get("stub:spam").backendId());
  }

  @Test
  void externalBackendStaysInertWithoutItsConfiguration() {
    final DocCategorizerRegistry registry =
        DocCategorizerRegistry.create(Map.of(pathKey("topic"), topicModelPath.toString()));
    assertEquals(List.of("topic"), registry.modelIds());
  }
}
