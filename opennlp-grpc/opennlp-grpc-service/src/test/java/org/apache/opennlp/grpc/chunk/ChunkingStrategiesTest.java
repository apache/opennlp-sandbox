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
package org.apache.opennlp.grpc.chunk;

import java.util.List;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.ChunkingStrategySelector;
import org.apache.opennlp.grpc.v1.SemanticChunkingConfig;
import org.apache.opennlp.grpc.v1.StandardChunkingStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies typed chunking strategy resolution and legacy string compatibility. */
class ChunkingStrategiesTest {

  @Test
  void resolvesEveryStandardStrategy() {
    assertEquals(List.of("sentence", "token", "semantic"), List.of(
        selected(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_SENTENCE),
        selected(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_TOKEN),
        selected(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_SEMANTIC)));
  }

  @Test
  void canonicalizesLegacyAndOpenCustomStrategies() {
    final ChunkingSpec legacy = ChunkingSpec.newBuilder().setAlgorithm("  sentence  ").build();
    final ChunkingSpec custom = ChunkingSpec.newBuilder()
        .setStrategy(ChunkingStrategySelector.newBuilder().setCustom("  extension  "))
        .build();

    assertEquals("sentence", ChunkingStrategies.selectedId(legacy));
    assertEquals(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_SENTENCE,
        ChunkingStrategies.selectedStrategy(legacy).getStandard());
    assertEquals("extension", ChunkingStrategies.selectedId(custom));
    assertEquals("extension", ChunkingStrategies.selectedStrategy(custom).getCustom());
  }

  @Test
  void semanticConfigSelectsSemanticWhenNoExplicitStrategyExists() {
    final ChunkingSpec spec = ChunkingSpec.newBuilder()
        .setSemanticConfig(SemanticChunkingConfig.getDefaultInstance())
        .build();

    assertTrue(ChunkingStrategies.isSemantic(spec));
    assertEquals(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_SEMANTIC,
        ChunkingStrategies.selectedStrategy(spec).getStandard());
  }

  @Test
  void rejectsMixedEmptyAndContradictorySelections() {
    final ChunkingSpec mixed = ChunkingSpec.newBuilder()
        .setAlgorithm("sentence")
        .setStrategy(standard(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_SENTENCE))
        .build();
    final ChunkingSpec unspecified = ChunkingSpec.newBuilder()
        .setStrategy(standard(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_UNSPECIFIED))
        .build();
    final ChunkingSpec blankCustom = ChunkingSpec.newBuilder()
        .setStrategy(ChunkingStrategySelector.newBuilder().setCustom("  "))
        .build();
    final ChunkingSpec missingKind = ChunkingSpec.newBuilder()
        .setStrategy(ChunkingStrategySelector.getDefaultInstance())
        .build();
    final ChunkingSpec contradictory = ChunkingSpec.newBuilder()
        .setStrategy(standard(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_SENTENCE))
        .setSemanticConfig(SemanticChunkingConfig.getDefaultInstance())
        .build();
    final ChunkingSpec resultOnlyCategory = ChunkingSpec.newBuilder()
        .setStrategy(standard(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_CATEGORY))
        .build();

    assertInvalid(mixed);
    assertInvalid(unspecified);
    assertInvalid(blankCustom);
    assertInvalid(missingKind);
    assertInvalid(contradictory);
    assertInvalid(resultOnlyCategory);
    assertInvalid(ChunkingSpec.getDefaultInstance());
  }

  private static String selected(StandardChunkingStrategy strategy) {
    return ChunkingStrategies.selectedId(
        ChunkingSpec.newBuilder().setStrategy(standard(strategy)).build());
  }

  private static ChunkingStrategySelector standard(StandardChunkingStrategy strategy) {
    return ChunkingStrategySelector.newBuilder().setStandard(strategy).build();
  }

  private static void assertInvalid(ChunkingSpec spec) {
    final AnalysisException error = assertThrows(
        AnalysisException.class, () -> ChunkingStrategies.selectedId(spec));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }
}
