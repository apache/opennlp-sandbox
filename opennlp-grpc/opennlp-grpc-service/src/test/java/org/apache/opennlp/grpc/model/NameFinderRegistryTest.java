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
import java.util.Set;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.testing.TinyNerModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.opennlp.grpc.spi.model.NerModel;
import org.apache.opennlp.grpc.spi.model.NerBackendFactory;
import org.apache.opennlp.grpc.spi.model.NerBackendContext;

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
        "model.parser.default.path", "/no/such/parser-model.bin")));
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
    // The same recognizer is selected regardless of the requested type's case.
    assertEquals(registry.recognizerIdsForTypes(List.of("person")),
        registry.recognizerIdsForTypes(List.of("PERSON")));
    assertEquals(List.of("person"), registry.recognizerIdsForTypes(List.of("PERSON")));
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
  void dlKeysWithoutTheAddOnFailLoud() {
    // The ONNX backend ships in the opennlp-grpc-dl add-on, absent from this module's
    // classpath; configured DL models must fail startup, never silently vanish.
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        NameFinderRegistry.create(Map.of(
            NameFinderRegistry.KEY_DL_PREFIX + "person.path", "/tmp/model.onnx")));
    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, error.getFailureType());
    assertTrue(error.getMessage().contains("opennlp-grpc-dl"));
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
    // The stub recognizer's id is "stub:gadget" (its own naming); it emits the "gadget" type.
    final List<String> gadgetIds = registry.recognizerIdsForTypes(List.of("gadget"));
    assertEquals(List.of(StubNerBackendFactory.FACTORY_ID + ":gadget"), gadgetIds);
    assertEquals(StubNerBackendFactory.FACTORY_ID,
        registry.recognizers().primary(gadgetIds.get(0)).value().backendId());
  }

  @Test
  void externalBackendStaysInertWithoutItsConfiguration() {
    // Without the stub's activation key, only the built-in classic model is present.
    final NameFinderRegistry registry =
        NameFinderRegistry.create(Map.of(personKey(), personModelPath.toString()));
    assertEquals(List.of("person"), registry.entityTypes());
  }

  @Test
  void duplicateFactoryIdFailsStartup() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        NameFinderRegistry.create(Map.of(), null,
            List.of(stubFactory("dup"), stubFactory("dup"))));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("dup"),
        "the error must name the duplicate factory id: " + error.getMessage());
  }

  @Test
  void factoryFailureClosesModelsFromEarlierFactories() {
    // A factory that throws after an earlier factory loaded a model holding a native session
    // must not leak that session: the half-built registry can never be close()d by the caller.
    final CloseTrackingNerModel model = new CloseTrackingNerModel();
    assertThrows(AnalysisException.class, () ->
        NameFinderRegistry.create(Map.of(), null,
            List.of(stubFactory("tracking", List.of(model)), throwingFactory("zz-failing"))));
    assertTrue(model.closed, "a model loaded before a later factory failed was leaked");
  }

  private static NerBackendFactory stubFactory(String factoryId) {
    return stubFactory(factoryId, List.of());
  }

  private static NerBackendFactory stubFactory(String factoryId, List<NerModel> models) {
    return new NerBackendFactory() {
      @Override
      public String factoryId() {
        return factoryId;
      }

      @Override
      public List<NerModel> create(Map<String, String> configuration, NerBackendContext context) {
        return models;
      }
    };
  }

  private static NerBackendFactory throwingFactory(String factoryId) {
    return new NerBackendFactory() {
      @Override
      public String factoryId() {
        return factoryId;
      }

      @Override
      public List<NerModel> create(Map<String, String> configuration, NerBackendContext context) {
        throw AnalysisException.internal("deliberate test factory failure", null);
      }
    };
  }

  /** A recognizer holding a pretend native resource, recording its release. */
  private static final class CloseTrackingNerModel implements NerModel, AutoCloseable {

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
    public Set<String> entityTypes() {
      return Set.of("gadget");
    }

    @Override
    public boolean isStateful() {
      return false;
    }

    @Override
    public void clearAdaptiveData() {
      // Stateless.
    }

    @Override
    public List<org.apache.opennlp.grpc.v1.NamedEntity> recognize(
        org.apache.opennlp.grpc.v1.AnnotatedSentence sentence, boolean includeProbabilities) {
      return List.of();
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
