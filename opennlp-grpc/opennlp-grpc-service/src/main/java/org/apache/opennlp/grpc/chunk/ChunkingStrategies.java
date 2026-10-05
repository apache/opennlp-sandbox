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


import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.ChunkingStrategySelector;
import org.apache.opennlp.grpc.v1.StandardChunkingStrategy;

/** Resolves typed and compatibility chunking strategy selectors. */
public final class ChunkingStrategies {

  /** Canonical id of sentence chunking. */
  public static final String SENTENCE = "sentence";
  /** Canonical id of token-window chunking. */
  public static final String TOKEN = "token";
  /** Canonical id of embedding-driven semantic chunking. */
  public static final String SEMANTIC = "semantic";
  /** Canonical id of category grouping. */
  public static final String CATEGORY = "category";

  private ChunkingStrategies() {
  }

  /**
   * Returns the canonical strategy id selected by the specification.
   *
   * @param spec The chunking specification to resolve.
   * @return The canonical built-in id or trimmed custom id.
   * @throws AnalysisException If the selection is absent, contradictory, or incomplete.
   */
  public static String selectedId(ChunkingSpec spec) {
    return resolve(spec).id();
  }

  /**
   * Returns the canonical typed strategy identity for result provenance.
   *
   * @param spec The chunking specification to resolve.
   * @return A standard enum case for a built-in strategy or the open custom case.
   * @throws AnalysisException If the selection is absent, contradictory, or incomplete.
   */
  public static ChunkingStrategySelector selectedStrategy(ChunkingSpec spec) {
    return resolve(spec).selector();
  }

  /**
   * Reports whether the specification selects semantic chunking.
   *
   * @param spec The chunking specification to inspect.
   * @return {@code true} for the semantic strategy.
   * @throws AnalysisException If the selection is absent, contradictory, or incomplete.
   */
  public static boolean isSemantic(ChunkingSpec spec) {
    return SEMANTIC.equals(selectedId(spec));
  }

  /**
   * Returns a typed selector for one standard strategy.
   *
   * @param strategy The standard strategy to select.
   * @return A selector carrying the standard enum case.
   */
  public static ChunkingStrategySelector standard(StandardChunkingStrategy strategy) {
    if (strategy == null) {
      throw new IllegalArgumentException("strategy must not be null");
    }
    return ChunkingStrategySelector.newBuilder().setStandard(strategy).build();
  }

  /** Resolves a strategy from the compatibility and typed fields. */
  private static Selection resolve(ChunkingSpec spec) {
    if (spec == null) {
      throw new IllegalArgumentException("spec must not be null");
    }
    final String legacy = spec.getAlgorithm().trim();
    if (!legacy.isEmpty() && spec.hasStrategy()) {
      throw AnalysisException.invalidArgument(
          "chunking.algorithm and chunking.strategy are mutually exclusive");
    }

    final Selection selection;
    if (spec.hasStrategy()) {
      selection = resolve(spec.getStrategy());
    } else if (!legacy.isEmpty()) {
      selection = fromId(legacy);
    } else if (spec.hasSemanticConfig()) {
      selection = standardSelection(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_SEMANTIC);
    } else {
      throw AnalysisException.invalidArgument("chunking strategy is required");
    }

    if (CATEGORY.equals(selection.id())) {
      throw AnalysisException.invalidArgument(
          "category grouping must be configured with CategoryChunkConfigEntry");
    }
    if (spec.hasSemanticConfig() && !SEMANTIC.equals(selection.id())) {
      throw AnalysisException.invalidArgument(
          "chunking.semantic_config requires the semantic strategy");
    }
    return selection;
  }

  /** Resolves a typed strategy selector. */
  private static Selection resolve(ChunkingStrategySelector selector) {
    return switch (selector.getKindCase()) {
      case STANDARD -> standardSelection(selector.getStandard());
      case CUSTOM -> {
        final String custom = selector.getCustom().trim();
        if (custom.isEmpty()) {
          throw AnalysisException.invalidArgument(
              "chunking.strategy custom id must not be blank");
        }
        yield fromId(custom);
      }
      case KIND_NOT_SET -> throw AnalysisException.invalidArgument(
          "chunking.strategy must select a standard or custom strategy");
    };
  }

  /** Builds a resolved standard strategy selection. */
  private static Selection standardSelection(StandardChunkingStrategy strategy) {
    final String id = switch (strategy) {
      case STANDARD_CHUNKING_STRATEGY_SENTENCE -> SENTENCE;
      case STANDARD_CHUNKING_STRATEGY_TOKEN -> TOKEN;
      case STANDARD_CHUNKING_STRATEGY_SEMANTIC -> SEMANTIC;
      case STANDARD_CHUNKING_STRATEGY_CATEGORY -> CATEGORY;
      case STANDARD_CHUNKING_STRATEGY_UNSPECIFIED, UNRECOGNIZED ->
          throw AnalysisException.invalidArgument(
              "chunking.strategy standard value must not be unspecified or unrecognized");
    };
    return new Selection(id, standard(strategy));
  }

  /** Resolves a strategy from its open id. */
  private static Selection fromId(String id) {
    return switch (id) {
      case SENTENCE -> standardSelection(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_SENTENCE);
      case TOKEN -> standardSelection(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_TOKEN);
      case SEMANTIC -> standardSelection(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_SEMANTIC);
      case CATEGORY -> standardSelection(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_CATEGORY);
      default -> new Selection(id, ChunkingStrategySelector.newBuilder().setCustom(id).build());
    };
  }

  private record Selection(String id, ChunkingStrategySelector selector) {
  }
}
