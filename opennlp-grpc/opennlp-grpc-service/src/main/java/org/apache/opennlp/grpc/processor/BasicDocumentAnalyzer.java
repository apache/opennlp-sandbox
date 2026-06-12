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
package org.apache.opennlp.grpc.processor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.chunk.ChunkEmbedProcessor;
import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.profile.ProfileResolver;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.Chunk;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.DiagnosticSeverity;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.ModelBundleRef;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.ProcessingDiagnostic;
import org.apache.opennlp.grpc.v1.Token;

/**
 * Initial v1 processor: shared sentence detection and tokenization backbone.
 *
 * <p>Internally all offsets are computed in Java UTF-16 indices; a final pass converts
 * every span to the client-requested {@link OffsetEncoding} (default UTF-8 bytes).
 */
public class BasicDocumentAnalyzer implements DocumentAnalyzer {

  private final ProfileResolver profileResolver;
  private final ModelBundleCache modelBundleCache;
  private final EmbeddingProvider embeddingProvider;

  public BasicDocumentAnalyzer(Map<String, String> configuration) {
    this(ProfileRegistry.createDefault(), new ModelBundleCache(configuration));
  }

  public BasicDocumentAnalyzer(ProfileRegistry profileRegistry, ModelBundleCache modelBundleCache) {
    this(profileRegistry, modelBundleCache, modelBundleCache.getEmbeddingProvider());
  }

  public BasicDocumentAnalyzer(
      ProfileRegistry profileRegistry,
      ModelBundleCache modelBundleCache,
      EmbeddingProvider embeddingProvider) {
    Objects.requireNonNull(profileRegistry, "profileRegistry");
    Objects.requireNonNull(modelBundleCache, "modelBundleCache");
    Objects.requireNonNull(embeddingProvider, "embeddingProvider");
    this.profileResolver = new ProfileResolver(profileRegistry);
    this.modelBundleCache = modelBundleCache;
    this.embeddingProvider = embeddingProvider;
  }

  @Override
  public AnalyzeDocumentResponse analyze(AnalyzeDocumentRequest request) {
    Objects.requireNonNull(request, "request");
    if (!request.hasDocument()) {
      throw AnalysisException.invalidArgument("document is required");
    }

    final OpenNlpDocument input = request.getDocument();
    final String rawText = input.getRawText();
    if (rawText == null || rawText.isBlank()) {
      throw AnalysisException.invalidArgument("document.raw_text is required");
    }

    final AnalysisProfile profile = profileResolver.resolve(request);
    validateSupportedRequest(request, profile, rawText);

    final boolean includeProbabilities =
        request.hasOptions() && request.getOptions().getIncludeProbabilities();

    final List<ProcessingDiagnostic> diagnostics = new ArrayList<>();
    final OpenNlpDocument.Builder document = OpenNlpDocument.newBuilder()
        .setDocId(input.getDocId())
        .setRawText(rawText);
    if (input.hasMetadata()) {
      document.setMetadata(input.getMetadata());
    }

    if (shouldRunStep(request, profile, PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)) {
      runStep(
          PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
          diagnostics,
          () -> runSentenceDetection(rawText, document, includeProbabilities, diagnostics));
    } else {
      addSkippedDiagnostic(diagnostics, PipelineStep.PIPELINE_STEP_SENTENCE_DETECT);
    }

    if (shouldRunStep(request, profile, PipelineStep.PIPELINE_STEP_TOKENIZE)) {
      if (document.getSentencesCount() == 0) {
        throw AnalysisException.failedPrecondition(
            PipelineStep.PIPELINE_STEP_TOKENIZE.name()
                + " requires "
                + PipelineStep.PIPELINE_STEP_SENTENCE_DETECT.name());
      }
      runStep(
          PipelineStep.PIPELINE_STEP_TOKENIZE,
          diagnostics,
          () -> runTokenization(rawText, document, includeProbabilities, diagnostics));
    } else {
      addSkippedDiagnostic(diagnostics, PipelineStep.PIPELINE_STEP_TOKENIZE);
    }

    final String embeddingModelId = resolveEmbeddingModelId(request, profile);
    if (PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_EMBED)) {
      if (document.getSentencesCount() == 0) {
        throw AnalysisException.failedPrecondition(
            PipelineStep.PIPELINE_STEP_EMBED.name()
                + " requires "
                + PipelineStep.PIPELINE_STEP_SENTENCE_DETECT.name());
      }
      runStep(
          PipelineStep.PIPELINE_STEP_EMBED,
          diagnostics,
          () -> runEmbedding(rawText, document, embeddingModelId, diagnostics));
    } else {
      addSkippedDiagnostic(diagnostics, PipelineStep.PIPELINE_STEP_EMBED);
    }

    if (request.getChunkEmbedConfigsCount() > 0) {
      runStep(
          PipelineStep.PIPELINE_STEP_CHUNK,
          diagnostics,
          () -> runChunkEmbedConfigs(rawText, document, request, diagnostics));
    } else if (shouldRunStep(request, profile, PipelineStep.PIPELINE_STEP_CHUNK)) {
      runStep(
          PipelineStep.PIPELINE_STEP_CHUNK,
          diagnostics,
          () -> runProfileChunking(rawText, document, diagnostics));
    } else {
      addSkippedDiagnostic(diagnostics, PipelineStep.PIPELINE_STEP_CHUNK);
    }

    final OffsetEncoding requestedEncoding = request.hasOptions()
        ? request.getOptions().getOffsetEncoding()
        : OffsetEncoding.OFFSET_ENCODING_UNSPECIFIED;
    applyOffsetEncoding(document, rawText, requestedEncoding);

    return AnalyzeDocumentResponse.newBuilder()
        .setDocument(document.build())
        .addAllDiagnostics(diagnostics)
        .build();
  }

  private void validateSupportedRequest(
      AnalyzeDocumentRequest request, AnalysisProfile profile, String rawText) {
    for (PipelineStep step : profile.getStepsList()) {
      if (step == PipelineStep.PIPELINE_STEP_UNSPECIFIED) {
        continue;
      }
      if (!PipelineStepPolicy.isImplemented(step)) {
        throw AnalysisException.unimplemented(step.name() + " is not implemented on this server");
      }
    }

    validateOptions(request, profile, rawText);
    validateModelBundle(profile);
    validateEmbeddingRequest(request, profile);
    validateChunkEmbedConfigs(request);
  }

  private void validateChunkEmbedConfigs(AnalyzeDocumentRequest request) {
    if (request.getChunkEmbedConfigsCount() == 0) {
      return;
    }
    for (ChunkEmbedConfigEntry entry : request.getChunkEmbedConfigsList()) {
      ChunkEmbedProcessor.validateEntry(entry, embeddingProvider);
    }
  }

  private Set<PipelineStep> resolveEffectiveSteps(
      AnalyzeDocumentRequest request, AnalysisProfile profile) {
    final LinkedHashSet<PipelineStep> steps = new LinkedHashSet<>(profile.getStepsList());
    if (PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_EMBED)) {
      steps.add(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT);
    }
    if (request.getChunkEmbedConfigsCount() > 0) {
      steps.add(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT);
      for (ChunkEmbedConfigEntry entry : request.getChunkEmbedConfigsList()) {
        if (entry.hasChunking() && "token".equals(entry.getChunking().getAlgorithm())) {
          steps.add(PipelineStep.PIPELINE_STEP_TOKENIZE);
        }
      }
    }
    if (PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_CHUNK)
        && request.getChunkEmbedConfigsCount() == 0) {
      steps.add(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT);
    }
    return steps;
  }

  private boolean shouldRunStep(
      AnalyzeDocumentRequest request, AnalysisProfile profile, PipelineStep step) {
    return resolveEffectiveSteps(request, profile).contains(step);
  }

  private void validateOptions(
      AnalyzeDocumentRequest request, AnalysisProfile profile, String rawText) {
    if (!request.hasOptions()) {
      return;
    }
    final AnalysisOptions options = request.getOptions();
    final boolean embedRequested =
        PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_EMBED);
    if (options.hasEmbeddingModelId() && !options.getEmbeddingModelId().isBlank()) {
      if (!embedRequested) {
        throw AnalysisException.invalidArgument(
            "embedding_model_id requires PIPELINE_STEP_EMBED in the analysis profile");
      }
    }
    if (options.hasMaxTextLength()
        && options.getMaxTextLength() > 0
        && rawText.length() > options.getMaxTextLength()) {
      throw AnalysisException.invalidArgument(
          "document.raw_text exceeds max_text_length (" + options.getMaxTextLength() + ")");
    }
  }

  private void validateEmbeddingRequest(AnalyzeDocumentRequest request, AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_EMBED)) {
      return;
    }
    if (!embeddingProvider.isAvailable()) {
      throw AnalysisException.notFound(
          "PIPELINE_STEP_EMBED requested but no embedding models are configured on this server");
    }
    final String modelId = resolveEmbeddingModelId(request, profile);
    if (modelId == null || modelId.isBlank()) {
      throw AnalysisException.invalidArgument(
          "embedding_model_id is required when multiple embedding models are configured");
    }
    if (!embeddingProvider.supportsModel(modelId)) {
      throw AnalysisException.notFound("Unknown embedding model '" + modelId + "'");
    }
  }

  private String resolveEmbeddingModelId(AnalyzeDocumentRequest request, AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_EMBED)) {
      return null;
    }
    String requested = null;
    if (request.hasOptions() && request.getOptions().hasEmbeddingModelId()) {
      requested = request.getOptions().getEmbeddingModelId();
    }
    return embeddingProvider.resolveModelId(requested);
  }

  private void runChunkEmbedConfigs(
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

  private void runProfileChunking(
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

  private static void validateModelBundle(AnalysisProfile profile) {
    if (!profile.hasModelBundle()) {
      return;
    }
    final ModelBundleRef bundle = profile.getModelBundle();
    final String bundleId = bundle.getBundleId();
    if (!bundleId.isBlank() && !bundleId.equals(ProfileRegistry.DEFAULT_BUNDLE_ID)) {
      throw AnalysisException.notFound(
          "Unknown model bundle '" + bundleId + "'; only '"
              + ProfileRegistry.DEFAULT_BUNDLE_ID + "' is available");
    }
    if (bundle.getComponentModelsCount() > 0) {
      throw AnalysisException.unimplemented(
          "per-component model selection (component_models) is not implemented");
    }
  }

  private void runStep(
      PipelineStep step,
      List<ProcessingDiagnostic> diagnostics,
      StepAction action) {
    try {
      action.run();
    } catch (AnalysisException e) {
      throw e;
    } catch (RuntimeException e) {
      throw AnalysisException.internal(step.name() + " failed", e);
    }
  }

  private void runSentenceDetection(
      String rawText,
      OpenNlpDocument.Builder document,
      boolean includeProbabilities,
      List<ProcessingDiagnostic> diagnostics) {
    final SentenceDetectorME detector = modelBundleCache.getSentenceDetector();
    final Span[] spans = detector.sentPosDetect(rawText);
    final double[] probabilities = includeProbabilities ? detector.probs() : null;
    for (int i = 0; i < spans.length; i++) {
      final AnnotationSpan.Builder span = toAnnotationSpan(spans[i]).toBuilder();
      if (probabilities != null && i < probabilities.length) {
        span.setProbability(probabilities[i]);
      }
      document.addSentences(AnnotatedSentence.newBuilder().setSentenceSpan(span).build());
    }
    diagnostics.add(ProcessingDiagnostic.newBuilder()
        .setStep(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .setSeverity(DiagnosticSeverity.DIAGNOSTIC_SEVERITY_INFO)
        .setMessage("Detected " + spans.length + " sentence(s)")
        .build());
  }

  private void runTokenization(
      String rawText,
      OpenNlpDocument.Builder document,
      boolean includeProbabilities,
      List<ProcessingDiagnostic> diagnostics) {
    final TokenizerME tokenizer = modelBundleCache.getTokenizer();
    int tokenCount = 0;
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence sentence = document.getSentences(i);
      final AnnotationSpan sentenceSpan = sentence.getSentenceSpan();
      final String sentenceText = rawText.substring(sentenceSpan.getStart(), sentenceSpan.getEnd());
      final Span[] tokenSpans = tokenizer.tokenizePos(sentenceText);
      final double[] probabilities = includeProbabilities ? tokenizer.probs() : null;
      final AnnotatedSentence.Builder sentenceBuilder = sentence.toBuilder();
      for (int t = 0; t < tokenSpans.length; t++) {
        final Span tokenSpan = tokenSpans[t];
        final AnnotationSpan.Builder span = AnnotationSpan.newBuilder()
            .setStart(sentenceSpan.getStart() + tokenSpan.getStart())
            .setEnd(sentenceSpan.getStart() + tokenSpan.getEnd())
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT);
        if (probabilities != null && t < probabilities.length) {
          span.setProbability(probabilities[t]);
        }
        sentenceBuilder.addTokens(Token.newBuilder()
            .setText(sentenceText.substring(tokenSpan.getStart(), tokenSpan.getEnd()))
            .setAnnotationSpan(span)
            .build());
        tokenCount++;
      }
      document.setSentences(i, sentenceBuilder.build());
    }
    diagnostics.add(ProcessingDiagnostic.newBuilder()
        .setStep(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .setSeverity(DiagnosticSeverity.DIAGNOSTIC_SEVERITY_INFO)
        .setMessage("Tokenized " + tokenCount + " token(s)")
        .build());
  }

  private void runEmbedding(
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
    diagnostics.add(ProcessingDiagnostic.newBuilder()
        .setStep(PipelineStep.PIPELINE_STEP_EMBED)
        .setSeverity(DiagnosticSeverity.DIAGNOSTIC_SEVERITY_INFO)
        .setMessage("Generated " + vectors.size() + " sentence embedding(s) with model '"
            + modelId + "'")
        .build());
  }

  private static List<Float> toFloatList(float[] vector) {
    final List<Float> values = new ArrayList<>(vector.length);
    for (float value : vector) {
      values.add(value);
    }
    return values;
  }

  /**
   * Converts every span in the document from Java UTF-16 indices to the requested
   * {@link OffsetEncoding} and records the chosen encoding on the document.
   */
  private static void applyOffsetEncoding(
      OpenNlpDocument.Builder document, String rawText, OffsetEncoding requested) {
    final OffsetMapper mapper = OffsetMapper.forText(rawText, requested);
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence.Builder sentence = document.getSentences(i).toBuilder();
      sentence.setSentenceSpan(remap(sentence.getSentenceSpan(), mapper));
      for (int t = 0; t < sentence.getTokensCount(); t++) {
        final Token.Builder token = sentence.getTokens(t).toBuilder();
        token.setAnnotationSpan(remap(token.getAnnotationSpan(), mapper));
        sentence.setTokens(t, token.build());
      }
      document.setSentences(i, sentence.build());
    }
    for (int e = 0; e < document.getEmbeddingsCount(); e++) {
      final EmbeddingResult embedding = document.getEmbeddings(e);
      document.setEmbeddings(e, embedding.toBuilder()
          .setSourceSpan(remap(embedding.getSourceSpan(), mapper))
          .build());
    }
    for (int g = 0; g < document.getChunkEmbeddingGroupsCount(); g++) {
      final ChunkEmbeddingGroup.Builder group = document.getChunkEmbeddingGroups(g).toBuilder();
      for (int c = 0; c < group.getChunksCount(); c++) {
        final Chunk.Builder chunk = group.getChunks(c).toBuilder();
        chunk.setAnnotationSpan(remap(chunk.getAnnotationSpan(), mapper));
        for (int e = 0; e < chunk.getEmbeddingsCount(); e++) {
          final EmbeddingResult embedding = chunk.getEmbeddings(e);
          chunk.setEmbeddings(e, embedding.toBuilder()
              .setSourceSpan(remap(embedding.getSourceSpan(), mapper))
              .build());
        }
        group.setChunks(c, chunk.build());
      }
      document.setChunkEmbeddingGroups(g, group.build());
    }
    document.setOffsetEncoding(mapper.encoding());
  }

  private static AnnotationSpan remap(AnnotationSpan span, OffsetMapper mapper) {
    return span.toBuilder()
        .setStart(mapper.toTarget(span.getStart()))
        .setEnd(mapper.toTarget(span.getEnd()))
        .build();
  }

  private static void addSkippedDiagnostic(List<ProcessingDiagnostic> diagnostics, PipelineStep step) {
    diagnostics.add(ProcessingDiagnostic.newBuilder()
        .setStep(step)
        .setSeverity(DiagnosticSeverity.DIAGNOSTIC_SEVERITY_INFO)
        .setMessage(step.name() + " skipped (not requested by profile)")
        .build());
  }

  private static AnnotationSpan toAnnotationSpan(Span span) {
    return AnnotationSpan.newBuilder()
        .setStart(span.getStart())
        .setEnd(span.getEnd())
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }

  @FunctionalInterface
  private interface StepAction {
    void run();
  }
}
