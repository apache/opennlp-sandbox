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
import java.util.List;
import java.util.Set;

import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.Chunk;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.ChunkGroupStats;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.DiagnosticSeverity;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.ProcessingDiagnostic;

/**
 * Builds {@link ChunkEmbeddingGroup} results from {@link ChunkEmbedConfigEntry} requests.
 */
public final class ChunkEmbedProcessor {

  private ChunkEmbedProcessor() {
  }

  /**
   * Validates a chunk+embed config entry against the server's capabilities before any
   * processing starts, so invalid requests fail without partial results.
   *
   * @param entry             The config entry to validate.
   * @param embeddingProvider The provider whose registered models are checked.
   *
   * @throws AnalysisException If the entry is incomplete, references unknown embedding
   *                           models, or requires features this server does not provide.
   */
  public static void validateEntry(ChunkEmbedConfigEntry entry, EmbeddingProvider embeddingProvider) {
    if (entry.getConfigId().isBlank()) {
      throw AnalysisException.invalidArgument("chunk_embed_configs.config_id is required");
    }
    if (entry.hasProfile()) {
      throw AnalysisException.unimplemented(
          "per-entry analysis profiles in chunk_embed_configs are not implemented");
    }
    if (!entry.hasChunking()) {
      throw AnalysisException.invalidArgument(
          "chunk_embed_configs.chunking is required for config '" + entry.getConfigId() + "'");
    }
    final ChunkingSpec chunking = entry.getChunking();
    if (isSemantic(chunking)) {
      validateSemanticChunking(entry);
      if (!embeddingProvider.isAvailable()) {
        throw AnalysisException.notFound(
            "semantic chunking for config '" + entry.getConfigId()
                + "' requires configured embedding models on this server");
      }
    }
    if (entry.getEmbeddingModelIdsCount() > 0 && !embeddingProvider.isAvailable()) {
      throw AnalysisException.notFound(
          "embedding models requested for config '" + entry.getConfigId()
              + "' but no embedding models are configured on this server");
    }
    for (String modelId : entry.getEmbeddingModelIdsList()) {
      if (!embeddingProvider.supportsModel(modelId)) {
        throw AnalysisException.notFound("Unknown embedding model '" + modelId + "'");
      }
    }
  }

  /**
   * Chunks the document according to the entry's chunking spec and embeds every chunk
   * with each requested embedding model.
   *
   * @param rawText           The document text the annotation offsets refer to.
   * @param document          The analyzed document backbone.
   * @param entry             A previously validated config entry.
   * @param embeddingProvider The provider used for chunk embeddings and semantic chunking.
   *
   * @return The resulting chunk group including per-group statistics.
   */
  public static ChunkEmbeddingGroup buildGroup(
      String rawText,
      OpenNlpDocument document,
      ChunkEmbedConfigEntry entry,
      EmbeddingProvider embeddingProvider) {
    final long started = System.currentTimeMillis();
    final List<SegmentationChunker.ChunkSegment> segments =
        SegmentationChunker.segment(rawText, document, entry.getChunking(), embeddingProvider);

    final ChunkEmbeddingGroup.Builder group = ChunkEmbeddingGroup.newBuilder()
        .setGroupId(entry.getConfigId())
        .setChunkConfigId(entry.getConfigId())
        .addAllEmbeddingModelIds(entry.getEmbeddingModelIdsList())
        .setGranularity(EmbeddingGranularity.EMBEDDING_GRANULARITY_CHUNK_LEVEL);
    if (entry.hasResultSetName()) {
      group.setResultSetName(entry.getResultSetName());
    }

    int totalTokens = 0;
    for (SegmentationChunker.ChunkSegment segment : segments) {
      final String chunkText = rawText.substring(segment.start(), segment.end());
      final Chunk.Builder chunk = Chunk.newBuilder()
          .setAnnotationSpan(toSpan(segment.start(), segment.end()))
          .setTextContent(chunkText)
          .addAllContainedSentenceIndices(segment.sentenceIndices());
      totalTokens += countTokens(document, segment);
      for (String modelId : entry.getEmbeddingModelIdsList()) {
        final float[] vector = embeddingProvider.embed(modelId, chunkText);
        chunk.addEmbeddings(EmbeddingResult.newBuilder()
            .setModelId(modelId)
            .addAllVector(toFloatList(vector))
            .setSourceSpan(toSpan(segment.start(), segment.end()))
            .setGranularity(EmbeddingGranularity.EMBEDDING_GRANULARITY_CHUNK_LEVEL)
            .build());
      }
      group.addChunks(chunk.build());
    }

    group.setStats(ChunkGroupStats.newBuilder()
        .setChunkCount(segments.size())
        .setTotalTokens(totalTokens)
        .setProcessingTimeMs(System.currentTimeMillis() - started)
        .build());
    return group.build();
  }

  /**
   * Builds a sentence-per-chunk group without embeddings, used when the {@code CHUNK}
   * pipeline step runs without chunk+embed configs.
   *
   * @param rawText  The document text the annotation offsets refer to.
   * @param document The analyzed document backbone.
   * @param groupId  The id assigned to the resulting group.
   *
   * @return The resulting chunk group.
   */
  public static ChunkEmbeddingGroup buildSentenceGroup(
      String rawText, OpenNlpDocument document, String groupId) {
    final ChunkingSpec spec = ChunkingSpec.newBuilder().setAlgorithm("sentence").build();
    final ChunkEmbedConfigEntry entry = ChunkEmbedConfigEntry.newBuilder()
        .setConfigId(groupId)
        .setChunking(spec)
        .build();
    return buildGroup(rawText, document, entry, new NoOpEmbeddingProvider());
  }

  /**
   * @param configId   The config id the diagnostic refers to.
   * @param chunkCount The number of chunks produced for the config.
   *
   * @return An INFO diagnostic for a successfully processed chunk config.
   */
  public static ProcessingDiagnostic successDiagnostic(String configId, int chunkCount) {
    return ProcessingDiagnostic.newBuilder()
        .setStep(PipelineStep.PIPELINE_STEP_CHUNK)
        .setSeverity(DiagnosticSeverity.DIAGNOSTIC_SEVERITY_INFO)
        .setMessage("Produced " + chunkCount + " chunk(s) for config '" + configId + "'")
        .build();
  }

  private static void validateSemanticChunking(ChunkEmbedConfigEntry entry) {
    final var semantic = entry.getChunking().getSemanticConfig();
    if (semantic.hasSemanticEmbeddingModelId() && !semantic.getSemanticEmbeddingModelId().isBlank()) {
      return;
    }
    if (entry.getEmbeddingModelIdsCount() == 1) {
      return;
    }
    throw AnalysisException.invalidArgument(
        "semantic chunking requires semantic_embedding_model_id or exactly one embedding_model_id");
  }

  private static boolean isSemantic(ChunkingSpec chunking) {
    return "semantic".equals(chunking.getAlgorithm()) || chunking.hasSemanticConfig();
  }

  private static int countTokens(OpenNlpDocument document, SegmentationChunker.ChunkSegment segment) {
    int count = 0;
    for (int sentenceIndex : segment.sentenceIndices()) {
      final AnnotatedSentence sentence = document.getSentences(sentenceIndex);
      for (var token : sentence.getTokensList()) {
        final AnnotationSpan span = token.getAnnotationSpan();
        if (span.getStart() < segment.end() && span.getEnd() > segment.start()) {
          count++;
        }
      }
    }
    return count;
  }

  private static AnnotationSpan toSpan(int start, int end) {
    return AnnotationSpan.newBuilder()
        .setStart(start)
        .setEnd(end)
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }

  private static List<Float> toFloatList(float[] vector) {
    final List<Float> values = new ArrayList<>(vector.length);
    for (float value : vector) {
      values.add(value);
    }
    return values;
  }

  /** Embedding provider that rejects embed calls; used for chunk-only groups. */
  private static final class NoOpEmbeddingProvider implements EmbeddingProvider {
    @Override
    public boolean isAvailable() {
      return false;
    }

    @Override
    public Set<String> registeredModelIds() {
      return Set.of();
    }

    @Override
    public boolean supportsModel(String modelId) {
      return false;
    }

    @Override
    public int embeddingDimension(String modelId) {
      return 0;
    }

    @Override
    public float[] embed(String modelId, String text) {
      throw AnalysisException.failedPrecondition("embeddings were not requested for this group");
    }
  }
}
