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
import org.apache.opennlp.grpc.profile.ProfileMerger;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
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
  private final ClassicStepRunner classicSteps;

  /**
   * Creates a runner backed by the given embedding provider and classic step delegate.
   *
   * @param embeddingProvider The embedding provider. Must not be {@code null}.
   * @param classicSteps      The classic step runner used for per-entry backbones. Must not be
   *                          {@code null}.
   */
  EmbedChunkStepRunner(EmbeddingProvider embeddingProvider, ClassicStepRunner classicSteps) {
    this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
    this.classicSteps = Objects.requireNonNull(classicSteps, "classicSteps");
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

  /**
   * Builds one chunk+embedding group per requested config entry, running any per-entry backbone
   * steps first.
   *
   * @param rawText              The document text.
   * @param document             The mutable document builder.
   * @param request              The analyze request carrying chunk configs.
   * @param requestProfile       The effective top-level analysis profile.
   * @param includeProbabilities Whether to attach model probabilities to backbone steps.
   * @param diagnostics          The diagnostic list to append to.
   */
  void runChunkEmbedConfigs(
      String rawText,
      OpenNlpDocument.Builder document,
      AnalyzeDocumentRequest request,
      AnalysisProfile requestProfile,
      boolean includeProbabilities,
      List<ProcessingDiagnostic> diagnostics) {
    if (document.getSentencesCount() == 0 && request.getChunkEmbedConfigsList().stream()
        .noneMatch(entry -> entry.hasProfile())) {
      throw AnalysisException.failedPrecondition(
          "chunk_embed_configs requires sentence detection backbone");
    }
    for (ChunkEmbedConfigEntry entry : request.getChunkEmbedConfigsList()) {
      ensureEntryBackbone(rawText, document, requestProfile, entry, includeProbabilities,
          diagnostics);
      final ChunkEmbeddingGroup group =
          ChunkEmbedProcessor.buildGroup(rawText, document.build(), entry, embeddingProvider);
      document.addChunkEmbeddingGroups(group);
      diagnostics.add(ChunkEmbedProcessor.successDiagnostic(
          entry.getConfigId(), group.getChunksCount()));
    }
  }

  /**
   * Ensures the document backbone required by one chunk config entry is present, running classic
   * steps on demand when the entry supplies its own profile.
   */
  private void ensureEntryBackbone(
      String rawText,
      OpenNlpDocument.Builder document,
      AnalysisProfile requestProfile,
      ChunkEmbedConfigEntry entry,
      boolean includeProbabilities,
      List<ProcessingDiagnostic> diagnostics) {
    final AnalysisProfile effectiveProfile = entry.hasProfile()
        ? ProfileMerger.merge(requestProfile, entry.getProfile())
        : requestProfile;
    final String algorithm = entry.hasChunking()
        ? entry.getChunking().getAlgorithm()
        : "sentence";

    if (document.getSentencesCount() == 0) {
      classicSteps.detectSentences(rawText, document, includeProbabilities, diagnostics);
    }
    if ("token".equals(algorithm) && !isTokenized(document)) {
      classicSteps.tokenize(rawText, document, includeProbabilities, diagnostics);
    }
    if (entry.hasProfile()
        && effectiveProfile.getStepsList().contains(PipelineStep.PIPELINE_STEP_TOKENIZE)
        && !isTokenized(document)) {
      classicSteps.tokenize(rawText, document, includeProbabilities, diagnostics);
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

  /** Reports whether every sentence in {@code document} already carries tokens. */
  private static boolean isTokenized(OpenNlpDocument.Builder document) {
    if (document.getSentencesCount() == 0) {
      return false;
    }
    for (AnnotatedSentence sentence : document.getSentencesList()) {
      if (sentence.getTokensCount() == 0) {
        return false;
      }
    }
    return true;
  }

  /** Converts a primitive embedding vector to the boxed list used in protobuf builders. */
  private static List<Float> toFloatList(float[] vector) {
    final List<Float> values = new ArrayList<>(vector.length);
    for (float value : vector) {
      values.add(value);
    }
    return values;
  }
}
