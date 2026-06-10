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
import java.util.Arrays;
import java.util.List;

import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.SemanticChunkingConfig;

/**
 * Topic-boundary chunking using consecutive sentence embedding similarity.
 *
 * <p>Every sentence is embedded individually and a chunk boundary is placed wherever the
 * cosine similarity of two consecutive sentences falls below the threshold. The threshold
 * is, in order of precedence, the {@code percentile_threshold} over the observed
 * similarities, the explicit {@code similarity_threshold}, or
 * {@value #DEFAULT_SIMILARITY_THRESHOLD}.</p>
 *
 * <p>Size constraints are applied after boundary detection: chunks smaller than
 * {@code min_chunk_sentences} are merged first, then chunks larger than
 * {@code max_chunk_sentences} are split. The maximum therefore always holds, while the
 * minimum may be violated by a split remainder.</p>
 */
public final class SemanticChunker {

  static final float DEFAULT_SIMILARITY_THRESHOLD = 0.5f;

  private SemanticChunker() {
  }

  /**
   * Chunks the analyzed document at semantic topic boundaries.
   *
   * @param rawText           The document text the sentence spans refer to.
   * @param document          The analyzed document. Sentence spans are required.
   * @param config            The semantic chunking configuration.
   * @param embeddingProvider The provider used to embed each sentence.
   * @param modelId           The id of a registered embedding model.
   *
   * @return The chunk segments in document order. Never {@code null}.
   *
   * @throws AnalysisException If the configuration is invalid or embedding fails.
   */
  public static List<SegmentationChunker.ChunkSegment> chunk(
      String rawText,
      OpenNlpDocument document,
      SemanticChunkingConfig config,
      EmbeddingProvider embeddingProvider,
      String modelId) {
    if (document.getSentencesCount() == 0) {
      return List.of();
    }
    if (document.getSentencesCount() == 1) {
      final AnnotationSpan span = document.getSentences(0).getSentenceSpan();
      return List.of(new SegmentationChunker.ChunkSegment(span.getStart(), span.getEnd(), List.of(0)));
    }

    final int sentenceCount = document.getSentencesCount();
    final float[][] embeddings = new float[sentenceCount][];
    for (int i = 0; i < sentenceCount; i++) {
      final AnnotationSpan span = document.getSentences(i).getSentenceSpan();
      final String sentenceText = rawText.substring(span.getStart(), span.getEnd());
      embeddings[i] = embeddingProvider.embed(modelId, sentenceText);
    }

    final float[] similarities = new float[sentenceCount - 1];
    for (int i = 0; i < similarities.length; i++) {
      similarities[i] = cosineSimilarity(embeddings[i], embeddings[i + 1]);
    }

    final float threshold = resolveThreshold(config, similarities);
    final int minSentences = config.getMinChunkSentences() > 0 ? config.getMinChunkSentences() : 1;
    final int maxSentences =
        config.getMaxChunkSentences() > 0 ? config.getMaxChunkSentences() : Integer.MAX_VALUE;

    final List<Integer> starts = new ArrayList<>();
    starts.add(0);
    for (int i = 0; i < similarities.length; i++) {
      if (similarities[i] < threshold) {
        starts.add(i + 1);
      }
    }

    mergeSmallChunks(starts, minSentences, sentenceCount);
    splitLargeChunks(starts, maxSentences, sentenceCount);

    final List<SegmentationChunker.ChunkSegment> chunks = new ArrayList<>();
    for (int i = 0; i < starts.size(); i++) {
      final int startSentence = starts.get(i);
      final int endSentence = i + 1 < starts.size() ? starts.get(i + 1) - 1 : sentenceCount - 1;
      chunks.add(toSegment(rawText, document, startSentence, endSentence));
    }
    return chunks;
  }

  private static float resolveThreshold(SemanticChunkingConfig config, float[] similarities) {
    if (config.getPercentileThreshold() > 0) {
      if (config.getPercentileThreshold() >= 100) {
        throw AnalysisException.invalidArgument("semantic_config.percentile_threshold must be < 100");
      }
      return percentile(similarities, config.getPercentileThreshold());
    }
    if (config.getSimilarityThreshold() > 0f) {
      return config.getSimilarityThreshold();
    }
    return DEFAULT_SIMILARITY_THRESHOLD;
  }

  private static float percentile(float[] values, int percentile) {
    final float[] sorted = values.clone();
    Arrays.sort(sorted);
    final int index = Math.max(0, Math.min(sorted.length - 1,
        (int) Math.ceil(percentile / 100.0 * sorted.length) - 1));
    return sorted[index];
  }

  /**
   * Merges chunks smaller than {@code minSentences} into a neighbour. An undersized chunk
   * absorbs the following chunk; an undersized final chunk is absorbed by the preceding
   * one. A single chunk covering the whole document is never merged away, so documents
   * with fewer than {@code minSentences} sentences yield one chunk.
   */
  private static void mergeSmallChunks(List<Integer> starts, int minSentences, int sentenceCount) {
    if (minSentences <= 1) {
      return;
    }
    int index = 0;
    while (index < starts.size()) {
      final int chunkStart = starts.get(index);
      final int chunkEnd = index + 1 < starts.size() ? starts.get(index + 1) - 1 : sentenceCount - 1;
      if (chunkEnd - chunkStart + 1 >= minSentences) {
        index++;
      } else if (index + 1 < starts.size()) {
        // Absorb the following chunk, then re-check the grown chunk at the same index.
        starts.remove(index + 1);
      } else if (index > 0) {
        // Undersized final chunk: absorb it into the preceding chunk.
        starts.remove(index);
      } else {
        break;
      }
    }
  }

  /**
   * Splits chunks larger than {@code maxSentences} into consecutive windows of at most
   * {@code maxSentences} sentences.
   */
  private static void splitLargeChunks(List<Integer> starts, int maxSentences, int sentenceCount) {
    int index = 0;
    while (index < starts.size()) {
      final int chunkStart = starts.get(index);
      final int chunkEnd = index + 1 < starts.size() ? starts.get(index + 1) - 1 : sentenceCount - 1;
      final int size = chunkEnd - chunkStart + 1;
      if (size <= maxSentences) {
        index++;
        continue;
      }
      int splitAt = chunkStart + maxSentences;
      starts.add(index + 1, splitAt);
      index++;
    }
  }

  private static SegmentationChunker.ChunkSegment toSegment(
      String rawText,
      OpenNlpDocument document,
      int startSentence,
      int endSentence) {
    final AnnotatedSentence first = document.getSentences(startSentence);
    final AnnotatedSentence last = document.getSentences(endSentence);
    final int start = first.getSentenceSpan().getStart();
    final int end = last.getSentenceSpan().getEnd();
    final List<Integer> sentenceIndices = new ArrayList<>();
    for (int i = startSentence; i <= endSentence; i++) {
      sentenceIndices.add(i);
    }
    return new SegmentationChunker.ChunkSegment(start, end, List.copyOf(sentenceIndices));
  }

  static float cosineSimilarity(float[] left, float[] right) {
    if (left.length != right.length) {
      throw AnalysisException.invalidArgument("Embedding dimension mismatch during semantic chunking");
    }
    double dot = 0;
    double leftNorm = 0;
    double rightNorm = 0;
    for (int i = 0; i < left.length; i++) {
      dot += left[i] * right[i];
      leftNorm += left[i] * left[i];
      rightNorm += right[i] * right[i];
    }
    if (leftNorm == 0 || rightNorm == 0) {
      return 0f;
    }
    return (float) (dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)));
  }
}
