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

import org.apache.opennlp.grpc.spi.AnalysisException;
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
import org.apache.opennlp.grpc.spi.model.DocCategorizerModel;
import org.apache.opennlp.grpc.spi.model.DocCategorizerBackendFactory;

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
  void dlKeysWithoutTheAddOnFailLoud() {
    // The ONNX backend ships in the opennlp-grpc-dl add-on, absent from this module's
    // classpath; configured DL models must fail startup, never silently vanish.
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        DocCategorizerRegistry.create(Map.of(
            DocCategorizerRegistry.KEY_DL_PREFIX + "topic.path", "/tmp/model.onnx")));
    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, error.getFailureType());
    assertTrue(error.getMessage().contains("opennlp-grpc-dl"));
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
  void findsModelWhoseBackendReturnsAMixedCaseId() {
    // A third-party backend may return an id with uppercase letters; the registry normalizes ids
    // at registration so get()/supportsModel() (which normalize the lookup) still find it.
    final DocCategorizerRegistry registry = DocCategorizerRegistry.create(
        Map.of(StubDocCategorizerBackendFactory.KEY_RAW_ID, "MixedCase"));
    assertTrue(registry.supportsModel("MixedCase"));
    assertTrue(registry.supportsModel("mixedcase"));
    assertEquals(List.of("mixedcase"), registry.modelIds());
    assertEquals("stub", registry.get("MIXEDCASE").backendId());
  }

  @Test
  void externalBackendStaysInertWithoutItsConfiguration() {
    final DocCategorizerRegistry registry =
        DocCategorizerRegistry.create(Map.of(pathKey("topic"), topicModelPath.toString()));
    assertEquals(List.of("topic"), registry.modelIds());
  }

  @Test
  void duplicateFactoryIdFailsStartup() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        DocCategorizerRegistry.createForNamespace("doccat", Map.of(),
            List.of(stubFactory("dup"), stubFactory("dup"))));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("dup"),
        "the error must name the duplicate factory id: " + error.getMessage());
  }

  @Test
  void factoryFailureClosesModelsFromEarlierFactories() {
    // A factory that throws after an earlier factory loaded a model holding a native session
    // must not leak that session: the half-built registry can never be close()d by the caller.
    final CloseTrackingDocCategorizerModel model = new CloseTrackingDocCategorizerModel();
    assertThrows(AnalysisException.class, () ->
        DocCategorizerRegistry.createForNamespace("doccat", Map.of(),
            List.of(stubFactory("tracking", List.of(model)), throwingFactory("zz-failing"))));
    assertTrue(model.closed, "a model loaded before a later factory failed was leaked");
  }

  private static DocCategorizerBackendFactory stubFactory(String factoryId) {
    return stubFactory(factoryId, List.of());
  }

  private static DocCategorizerBackendFactory stubFactory(
      String factoryId, List<DocCategorizerModel> models) {
    return new DocCategorizerBackendFactory() {
      @Override
      public String factoryId() {
        return factoryId;
      }

      @Override
      public List<DocCategorizerModel> create(Map<String, String> configuration) {
        return models;
      }
    };
  }

  private static DocCategorizerBackendFactory throwingFactory(String factoryId) {
    return new DocCategorizerBackendFactory() {
      @Override
      public String factoryId() {
        return factoryId;
      }

      @Override
      public List<DocCategorizerModel> create(Map<String, String> configuration) {
        throw AnalysisException.internal("deliberate test factory failure", null);
      }
    };
  }

  /** A categorizer holding a pretend native resource, recording its release. */
  private static final class CloseTrackingDocCategorizerModel
      implements DocCategorizerModel, AutoCloseable {

    private boolean closed;

    @Override
    public String id() {
      return "tracking";
    }

    @Override
    public String backendId() {
      return "tracking";
    }

    @Override
    public List<String> categories() {
      return List.of("x");
    }

    @Override
    public DocumentClassification classify(String documentText, String[] documentTokens) {
      return DocumentClassification.getDefaultInstance();
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
