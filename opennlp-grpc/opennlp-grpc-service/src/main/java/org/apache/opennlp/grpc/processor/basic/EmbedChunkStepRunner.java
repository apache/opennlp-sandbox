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
package org.apache.opennlp.grpc.processor.basic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.opennlp.grpc.chunk.Centroids;
import org.apache.opennlp.grpc.chunk.ChunkEmbedProcessor;
import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CategoryChunkConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.ProcessingDiagnostic;

/**
 * Executes the embedding and chunking steps: sentence-level embeddings through the
 * configured {@link EmbeddingProvider}, and chunk+embed groups via
 * {@link ChunkEmbedProcessor}.
 */
final class EmbedChunkStepRunner {

  private final EmbeddingProvider embeddingProvider;

  EmbedChunkStepRunner(EmbeddingProvider embeddingProvider) {
    this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
  }

  /** Embeds every sentence in one batch and attaches the vectors to the document. */
  void embedSentences(
      String rawText,
      OpenNlpDocument.Builder document,
      String modelId,
      List<ProcessingDiagnostic> diagnostics) {
    final List<AnnotationSpan> sentenceSpans = new ArrayList<>(document.getSentencesCount());
    final List<String> sentenceTexts = new ArrayList<>(document.getSentencesCount());
    for (AnnotatedSentence sentence : document.getSentencesList()) {
      final AnnotationSpan sentenceSpan = sentence.getSentenceSpan();
      sentenceSpans.add(sentenceSpan);
      sentenceTexts.add(rawText.substring(sentenceSpan.getStart(), sentenceSpan.getEnd()));
    }
    final List<float[]> vectors = embeddingProvider.embedBatch(modelId, sentenceTexts);
    for (int i = 0; i < vectors.size(); i++) {
      document.addEmbeddings(EmbeddingResult.newBuilder()
          .setModelId(modelId)
          .addAllVector(toFloatList(vectors.get(i)))
          .setSourceSpan(sentenceSpans.get(i))
          .setGranularity(EmbeddingGranularity.EMBEDDING_GRANULARITY_SENTENCE)
          .build());
    }
    // One document centroid per model: the mean of its sentence vectors over the whole text.
    final EmbeddingResult documentCentroid = Centroids.centroid(modelId, vectors,
        AnnotationSpan.newBuilder()
            .setStart(0)
            .setEnd(rawText.length())
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
            .build(),
        EmbeddingGranularity.EMBEDDING_GRANULARITY_DOCUMENT);
    if (documentCentroid != null) {
      document.addDocumentCentroids(documentCentroid);
    }
    diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_EMBED,
        "Generated " + vectors.size() + " sentence embedding(s) with model '" + modelId + "'"));
  }

  /** Builds one chunk+embedding group per requested config entry. */
  void runChunkEmbedConfigs(
      String rawText,
      OpenNlpDocument.Builder document,
      AnalyzeDocumentRequest request,
      List<ProcessingDiagnostic> diagnostics) {
    if (document.getSentencesCount() == 0) {
      throw AnalysisException.failedPrecondition(
          "chunk_embed_configs requires sentence detection backbone");
    }
    for (ChunkEmbedConfigEntry entry : request.getChunkEmbedConfigsList()) {
      if ("token".equals(entry.getChunking().getAlgorithm())) {
        ensureTokenized(document);
      }
      final ChunkEmbeddingGroup group =
          ChunkEmbedProcessor.buildGroup(rawText, document.build(), entry, embeddingProvider);
      document.addChunkEmbeddingGroups(group);
      diagnostics.add(ChunkEmbedProcessor.successDiagnostic(
          entry.getConfigId(), group.getChunksCount()));
    }
  }

  /** Builds one category-grouped chunk+embedding group per requested category config entry. */
  void runCategoryChunkConfigs(
      String rawText,
      OpenNlpDocument.Builder document,
      AnalyzeDocumentRequest request,
      List<ProcessingDiagnostic> diagnostics) {
    if (document.getSentencesCount() == 0) {
      throw AnalysisException.failedPrecondition(
          "category_chunk_configs requires sentence detection backbone");
    }
    for (CategoryChunkConfigEntry entry : request.getCategoryChunkConfigsList()) {
      final ChunkEmbeddingGroup group =
          ChunkEmbedProcessor.buildCategoryGroup(rawText, document.build(), entry,
              embeddingProvider);
      document.addChunkEmbeddingGroups(group);
      diagnostics.add(ChunkEmbedProcessor.successDiagnostic(
          entry.getConfigId(), group.getChunksCount()));
    }
  }

  /** Builds the default sentence-chunk group for a profile-requested CHUNK step. */
  void runProfileChunking(
      String rawText,
      OpenNlpDocument.Builder document,
      List<ProcessingDiagnostic> diagnostics) {
    if (document.getSentencesCount() == 0) {
      throw AnalysisException.failedPrecondition(
          PipelineStep.PIPELINE_STEP_CHUNK.name()
              + " requires "
              + PipelineStep.PIPELINE_STEP_SENTENCE_DETECT.name());
    }
    final ChunkEmbeddingGroup group =
        ChunkEmbedProcessor.buildSentenceGroup(rawText, document.build(), "profile-chunk");
    document.addChunkEmbeddingGroups(group);
    diagnostics.add(ChunkEmbedProcessor.successDiagnostic("profile-chunk", group.getChunksCount()));
  }

  private static void ensureTokenized(OpenNlpDocument.Builder document) {
    for (AnnotatedSentence sentence : document.getSentencesList()) {
      if (sentence.getTokensCount() == 0) {
        throw AnalysisException.failedPrecondition(
            "token chunking requires " + PipelineStep.PIPELINE_STEP_TOKENIZE.name());
      }
    }
  }

  private static List<Float> toFloatList(float[] vector) {
    final List<Float> values = new ArrayList<>(vector.length);
    for (float value : vector) {
      values.add(value);
    }
    return values;
  }
}
