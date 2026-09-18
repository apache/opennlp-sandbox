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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.opennlp.grpc.embedding.EmbeddingBackendSelections;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.EmbeddingSelector;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.Token;

/**
 * RAG-style segmentation chunking over an analyzed document backbone.
 *
 * <p>Supported algorithms are {@code sentence} (one chunk per sentence), {@code token}
 * (overlapping token windows) and {@code semantic} (topic boundaries from sentence
 * embedding similarity, delegated to {@link SemanticChunker}).</p>
 */
public final class SegmentationChunker {

  /**
   * Exclusive-end document character offsets plus the sentences touched by the chunk.
   *
   * @param start           The inclusive start character offset of the chunk in the
   *                        document text.
   * @param end             The exclusive end character offset of the chunk in the
   *                        document text.
   * @param sentenceIndices The indices of the sentences covered by the chunk, in
   *                        document order.
   */
  public record ChunkSegment(int start, int end, List<Integer> sentenceIndices) {
  }

  private SegmentationChunker() {
  }

  /**
   * Segments an analyzed document according to the given chunking spec.
   *
   * @param rawText           The document text the annotation offsets refer to.
   * @param document          The analyzed document. Sentence spans are required; token
   *                          spans are additionally required for the {@code token} algorithm.
   * @param spec              The chunking spec. A strategy must be selected.
   * @param embeddingProvider The provider used for semantic chunking. Must not be
   *                          {@code null}; it is not consulted for other algorithms.
   *
   * @return The chunk segments in document order. Never {@code null}.
   *
   * @throws AnalysisException If the spec is invalid or names an unknown algorithm.
   */
  public static List<ChunkSegment> segment(
      String rawText,
      OpenNlpDocument document,
      ChunkingSpec spec,
      EmbeddingProvider embeddingProvider) {
    return segment(rawText, document, spec, embeddingProvider, null);
  }

  /**
   * Segments with an optional entry-level embedding selector for semantic fallback.
   *
   * @param rawText The document text the annotation offsets refer to.
   * @param document The analyzed document.
   * @param spec The chunking specification.
   * @param embeddingProvider The provider used for semantic chunking.
   * @param fallbackSelector The entry-level selector to use when semantic configuration omits one.
   * @return The chunk segments in document order.
   * @throws AnalysisException If the specification or selector is invalid.
   */
  public static List<ChunkSegment> segment(
      String rawText,
      OpenNlpDocument document,
      ChunkingSpec spec,
      EmbeddingProvider embeddingProvider,
      EmbeddingSelector fallbackSelector) {
    final String strategy = ChunkingStrategies.selectedId(spec);
    if (ChunkingStrategies.SEMANTIC.equals(strategy)) {
      final SemanticSelection selection =
          requireSemanticSelection(spec, embeddingProvider, fallbackSelector);
      return SemanticChunker.chunk(
          rawText, document, spec.getSemanticConfig(), embeddingProvider,
          selection.modelId(), selection.backendId());
    }
    return switch (strategy) {
      case ChunkingStrategies.SENTENCE -> sentenceChunks(document);
      case ChunkingStrategies.TOKEN -> tokenWindowChunks(document, spec);
      default -> throw AnalysisException.unimplemented(
          "chunking strategy '" + strategy + "' is not implemented");
    };
  }

  /** Returns semantic selection, rejecting invalid input. */
  private static SemanticSelection requireSemanticSelection(
      ChunkingSpec spec, EmbeddingProvider provider, EmbeddingSelector fallbackSelector) {
    if (!spec.hasSemanticConfig()) {
      throw AnalysisException.invalidArgument("chunking.semantic_config is required for semantic chunking");
    }
    final var semantic = spec.getSemanticConfig();
    if (semantic.hasSemanticEmbeddingModelId() && semantic.hasSemanticEmbeddingSelector()) {
      throw AnalysisException.invalidArgument("semantic_embedding_model_id and "
          + "semantic_embedding_selector are mutually exclusive");
    }
    final EmbeddingSelector selector = semantic.hasSemanticEmbeddingSelector()
        ? semantic.getSemanticEmbeddingSelector() : fallbackSelector;
    final String requested = selector != null
        ? selector.getModelId()
        : semantic.hasSemanticEmbeddingModelId() ? semantic.getSemanticEmbeddingModelId() : null;
    final String backendId = selector == null
        ? null : EmbeddingBackendSelections.selectedId(selector);
    final String modelId = provider.resolveModelId(requested);
    if (modelId == null || modelId.isBlank()) {
      throw AnalysisException.invalidArgument(
          "semantic chunking requires semantic_embedding_model_id or exactly one registered embedding model");
    }
    if (backendId == null && !provider.supportsModel(modelId)) {
      throw AnalysisException.notFound("Unknown semantic embedding model '" + modelId + "'");
    }
    if (backendId != null && !provider.supportsModel(modelId, backendId)) {
      throw AnalysisException.notFound(
          "Engine '" + backendId + "' does not serve semantic embedding model '"
              + modelId + "'");
    }
    return new SemanticSelection(modelId, backendId);
  }

  private record SemanticSelection(String modelId, String backendId) {
  }

  /** Creates one chunk per sentence. */
  private static List<ChunkSegment> sentenceChunks(OpenNlpDocument document) {
    final List<ChunkSegment> chunks = new ArrayList<>();
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotationSpan span = document.getSentences(i).getSentenceSpan();
      chunks.add(new ChunkSegment(span.getStart(), span.getEnd(), List.of(i)));
    }
    return chunks;
  }

  /** Creates chunks from fixed-size token windows. */
  private static List<ChunkSegment> tokenWindowChunks(OpenNlpDocument document, ChunkingSpec spec) {
    final int chunkSize = spec.getChunkSize();
    final int chunkOverlap = spec.getChunkOverlap();
    if (chunkSize <= 0) {
      throw AnalysisException.invalidArgument("chunking.chunk_size must be positive for token windows");
    }
    if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
      throw AnalysisException.invalidArgument(
          "chunking.chunk_overlap must be >= 0 and < chunk_size");
    }

    final List<FlatToken> flatTokens = flattenTokens(document);
    if (flatTokens.isEmpty()) {
      return List.of();
    }

    final int step = Math.max(1, chunkSize - chunkOverlap);
    final List<ChunkSegment> chunks = new ArrayList<>();
    for (int startToken = 0; startToken < flatTokens.size(); startToken += step) {
      final int endToken = Math.min(startToken + chunkSize, flatTokens.size()) - 1;
      final FlatToken first = flatTokens.get(startToken);
      final FlatToken last = flatTokens.get(endToken);
      chunks.add(new ChunkSegment(
          first.start(),
          last.end(),
          sentenceIndices(flatTokens, startToken, endToken)));
      if (endToken == flatTokens.size() - 1) {
        break;
      }
    }
    return chunks;
  }

  /** Flattens sentence tokens while retaining sentence indices. */
  private static List<FlatToken> flattenTokens(OpenNlpDocument document) {
    final List<FlatToken> tokens = new ArrayList<>();
    for (int sentenceIndex = 0; sentenceIndex < document.getSentencesCount(); sentenceIndex++) {
      for (Token token : document.getSentences(sentenceIndex).getTokensList()) {
        final AnnotationSpan span = token.getAnnotationSpan();
        tokens.add(new FlatToken(span.getStart(), span.getEnd(), sentenceIndex));
      }
    }
    return tokens;
  }

  /** Returns the sentence indices covered by a token window. */
  private static List<Integer> sentenceIndices(
      List<FlatToken> flatTokens, int startToken, int endToken) {
    final Set<Integer> indices = new LinkedHashSet<>();
    for (int i = startToken; i <= endToken; i++) {
      indices.add(flatTokens.get(i).sentenceIndex());
    }
    return List.copyOf(indices);
  }

  private record FlatToken(int start, int end, int sentenceIndex) {
  }
}
