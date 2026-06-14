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
import java.util.function.BiFunction;

import org.apache.opennlp.grpc.processor.AnalysisException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link CompositeEmbeddingProvider}'s multi-engine routing — the same logical model served
 * by several engines with priority, fallback, and {@code id@engine} pinning — using stub providers
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
        "model.embedder.minilm.slow.priority", "50"));
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
          throw new IllegalStateException("fast engine down");
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
          throw new IllegalStateException("fast engine down");
        });
    final CompositeEmbeddingProvider composite = twoEngines(fast, engine("slow", SLOW_VECTOR));
    assertThrows(IllegalStateException.class,
        () -> composite.embedBatchOnEngine("minilm", "fast", List.of("x")));
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
}
