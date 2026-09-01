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
package org.apache.opennlp.grpc.embedding;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;

/**
 * Tests {@link CompositeEmbeddingProvider}'s multi-engine routing: the same logical model served
 * by several engines with priority, fallback, and {@code id@engine} pinning, using stub providers
 * with distinct backend ids, so no real engine is needed.
 */
class CompositeEmbeddingProviderTest {

  private static final float[] FAST_VECTOR = {1f, 1f, 1f};
  private static final float[] SLOW_VECTOR = {2f, 2f, 2f};

  private static StubEmbeddingProvider engine(String backendId, float[] vector) {
    final BiFunction<String, String, float[]> embed = (modelId, text) -> vector;
    return new StubEmbeddingProvider(backendId, Map.of("minilm", 3), embed);
  }

  // "minilm" on engine "fast" (priority 100) and engine "slow" (priority 50).
  private static CompositeEmbeddingProvider twoEngines(
      StubEmbeddingProvider fast, StubEmbeddingProvider slow) {
    return new CompositeEmbeddingProvider(List.of(fast, slow), Map.of(
        "model.embedder.minilm.fast.priority", "100",
        "model.embedder.minilm.slow.priority", "50",
        "model.embedder.minilm.fast.vector_space_id", "minilm-v1-mean-normalized",
        "model.embedder.minilm.slow.vector_space_id", "minilm-v1-mean-normalized"));
  }

  @Test
  void routesToHighestPriorityEngineByDefault() {
    final CompositeEmbeddingProvider composite =
        twoEngines(engine("fast", FAST_VECTOR), engine("slow", SLOW_VECTOR));
    assertEquals("fast", composite.backendId("minilm"));
    assertArrayEquals(FAST_VECTOR, composite.embed("minilm", "hello"));
    assertEquals(3, composite.embeddingDimension("minilm"));
  }

  @Test
  void pinsExplicitEngineViaTypedArgument() {
    final CompositeEmbeddingProvider composite =
        twoEngines(engine("fast", FAST_VECTOR), engine("slow", SLOW_VECTOR));
    // Engine pinning is a separate typed argument, not a parsed model-id string.
    assertArrayEquals(SLOW_VECTOR, composite.embedBatchOnEngine("minilm", "slow", List.of("x")).get(0));
    // The id catalog lists the logical model only; engines are a separate dimension.
    assertEquals(java.util.Set.of("minilm"), composite.registeredModelIds());
    assertEquals("fast", composite.backendId("minilm"));   // default = highest priority
  }

  @Test
  void fallsBackToNextEngineWhenPrimaryFails() {
    final StubEmbeddingProvider fast =
        new StubEmbeddingProvider("fast", Map.of("minilm", 3), (modelId, text) -> {
          throw AnalysisException.unavailable(
              "fast engine down", new IllegalStateException("connection refused"));
        });
    final CompositeEmbeddingProvider composite = twoEngines(fast, engine("slow", SLOW_VECTOR));
    // Bare id falls back from the failing primary to the secondary engine.
    assertArrayEquals(SLOW_VECTOR, composite.embed("minilm", "hello"));
    // Batch falls back whole.
    assertArrayEquals(SLOW_VECTOR, composite.embedBatch("minilm", List.of("a")).get(0));
  }

  @Test
  void pinnedEngineDoesNotFallBack() {
    final StubEmbeddingProvider fast =
        new StubEmbeddingProvider("fast", Map.of("minilm", 3), (modelId, text) -> {
          throw AnalysisException.unavailable(
              "fast engine down", new IllegalStateException("connection refused"));
        });
    final CompositeEmbeddingProvider composite = twoEngines(fast, engine("slow", SLOW_VECTOR));
    assertThrows(AnalysisException.class,
        () -> composite.embedBatchOnEngine("minilm", "fast", List.of("x")));
  }

  @Test
  void doesNotFallBackAfterNonRetryableFailure() {
    final AtomicInteger secondaryCalls = new AtomicInteger();
    final StubEmbeddingProvider primary =
        new StubEmbeddingProvider("fast", Map.of("minilm", 3), (modelId, text) -> {
          throw AnalysisException.invalidArgument("text is not valid for this model");
        });
    final StubEmbeddingProvider secondary =
        new StubEmbeddingProvider("slow", Map.of("minilm", 3), (modelId, text) -> {
          secondaryCalls.incrementAndGet();
          return SLOW_VECTOR;
        });
    final CompositeEmbeddingProvider composite = twoEngines(primary, secondary);

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> composite.embed("minilm", "hello"));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertEquals(0, secondaryCalls.get(),
        "a client or model-contract failure must not be retried on another engine");
  }

  @Test
  void rejectsDimensionMismatchAcrossEnginesForOneModel() {
    final StubEmbeddingProvider fast =
        new StubEmbeddingProvider("fast", Map.of("minilm", 3), (m, t) -> FAST_VECTOR);
    final StubEmbeddingProvider slow =
        new StubEmbeddingProvider("slow", Map.of("minilm", 768), (m, t) -> SLOW_VECTOR);
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        new CompositeEmbeddingProvider(List.of(fast, slow), Map.of()));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void rejectsDifferentVectorSpacesEvenWhenDimensionsMatch() {
    final StubEmbeddingProvider first = engine("fast", FAST_VECTOR);
    final StubEmbeddingProvider second = engine("slow", SLOW_VECTOR);

    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        new CompositeEmbeddingProvider(List.of(first, second), Map.of(
            "model.embedder.minilm.fast.vector_space_id", "minilm-mean-normalized",
            "model.embedder.minilm.slow.vector_space_id", "minilm-cls-unnormalized")));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("vector space"));
  }

  @Test
  void requiresVectorSpaceIdentityBeforeEnablingFallback() {
    final StubEmbeddingProvider first = engine("fast", FAST_VECTOR);
    final StubEmbeddingProvider second = engine("slow", SLOW_VECTOR);

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> new CompositeEmbeddingProvider(List.of(first, second), Map.of()));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("vector_space_id"));
  }

  @Test
  void artifactHashComesFromThePrimaryRoute() {
    final EmbeddingProvider slow = providerWithHash("slow", "slow-hash", SLOW_VECTOR);
    final EmbeddingProvider fast = providerWithHash("fast", "fast-hash", FAST_VECTOR);
    final CompositeEmbeddingProvider composite = new CompositeEmbeddingProvider(
        List.of(slow, fast), Map.of(
            "model.embedder.minilm.fast.priority", "100",
            "model.embedder.minilm.slow.priority", "50",
            "model.embedder.minilm.fast.vector_space_id", "minilm-v1",
            "model.embedder.minilm.slow.vector_space_id", "minilm-v1"));

    assertEquals("fast", composite.backendId("minilm"));
    assertEquals("fast-hash", composite.modelArtifactHash("minilm"));
  }

  @Test
  void undeclaredVectorSpaceDerivesFromTheArtifact() {
    // A single-engine model without a declared space still advertises a complete route,
    // narrow to its own artifact so it never claims compatibility it cannot prove.
    final CompositeEmbeddingProvider composite = new CompositeEmbeddingProvider(
        List.of(providerWithHash("fast", "0123456789abcdef0123456789abcdef", FAST_VECTOR)),
        Map.of());

    assertEquals("minilm@0123456789abcdef",
        composite.routesForModel("minilm").getFirst().getVectorSpaceId());
    assertEquals("minilm@fast",
        CompositeEmbeddingProvider.derivedVectorSpaceId("minilm", "fast", null));
  }

  @Test
  void declaredVectorSpaceWinsOverTheDerivedOne() {
    final CompositeEmbeddingProvider composite = new CompositeEmbeddingProvider(
        List.of(providerWithHash("fast", "fast-hash", FAST_VECTOR)),
        Map.of("model.embedder.minilm.fast.vector_space_id", "minilm-v1"));

    assertEquals("minilm-v1",
        composite.routesForModel("minilm").getFirst().getVectorSpaceId());
  }

  @Test
  void closesOwnedProvidersWhenConstructionFails() {
    final ClosingProvider first = new ClosingProvider("fast", 3);
    final ClosingProvider second = new ClosingProvider("slow", 4);

    assertThrows(AnalysisException.class,
        () -> new CompositeEmbeddingProvider(List.of(first, second), Map.of()));

    assertTrue(first.closed.get(), "the first provider leaked after aggregate validation failed");
    assertTrue(second.closed.get(), "the second provider leaked after aggregate validation failed");
  }

  @Test
  void resolvesDefaultModelId() {
    final CompositeEmbeddingProvider withDefault = new CompositeEmbeddingProvider(
        List.of(engine("fast", FAST_VECTOR),
            new StubEmbeddingProvider("slow", Map.of("bge", 3), (m, t) -> SLOW_VECTOR)),
        Map.of("model.embedder.default_id", "bge"));
    assertEquals("bge", withDefault.resolveModelId(null));
    assertEquals("minilm", withDefault.resolveModelId("minilm"));

    // Two logical models, no default -> ambiguous.
    final CompositeEmbeddingProvider noDefault = new CompositeEmbeddingProvider(
        List.of(engine("fast", FAST_VECTOR),
            new StubEmbeddingProvider("slow", Map.of("bge", 3), (m, t) -> SLOW_VECTOR)),
        Map.of());
    assertNull(noDefault.resolveModelId(null));
  }

  @Test
  void emptyWhenNoEnginesContribute() {
    final CompositeEmbeddingProvider empty =
        new CompositeEmbeddingProvider(List.of(), Map.of());
    assertEquals(false, empty.isAvailable());
    assertTrue(empty.registeredModelIds().isEmpty());
  }

  private static EmbeddingProvider providerWithHash(
      String backendId, String artifactHash, float[] vector) {
    return new EmbeddingProvider() {
      @Override
      public String backendId() {
        return backendId;
      }

      @Override
      public boolean isAvailable() {
        return true;
      }

      @Override
      public Set<String> registeredModelIds() {
        return Set.of("minilm");
      }

      @Override
      public boolean supportsModel(String modelId) {
        return "minilm".equals(modelId);
      }

      @Override
      public int embeddingDimension(String modelId) {
        return 3;
      }

      @Override
      public float[] embed(String modelId, String text) {
        return vector;
      }

      @Override
      public String modelArtifactHash(String modelId) {
        return artifactHash;
      }
    };
  }

  private static final class ClosingProvider implements EmbeddingProvider, AutoCloseable {

    private final String backendId;
    private final int dimension;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ClosingProvider(String backendId, int dimension) {
      this.backendId = backendId;
      this.dimension = dimension;
    }

    @Override
    public String backendId() {
      return backendId;
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public Set<String> registeredModelIds() {
      return Set.of("minilm");
    }

    @Override
    public boolean supportsModel(String modelId) {
      return "minilm".equals(modelId);
    }

    @Override
    public int embeddingDimension(String modelId) {
      return dimension;
    }

    @Override
    public float[] embed(String modelId, String text) {
      return new float[dimension];
    }

    @Override
    public void close() {
      closed.set(true);
    }
  }
}
