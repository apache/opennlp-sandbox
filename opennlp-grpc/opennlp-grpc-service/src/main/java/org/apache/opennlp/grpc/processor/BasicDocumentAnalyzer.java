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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.profile.ProfileResolver;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.DiagnosticSeverity;
import org.apache.opennlp.grpc.v1.InferenceBackend;
import org.apache.opennlp.grpc.v1.ModelBundleRef;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.ProcessingDiagnostic;
import org.apache.opennlp.grpc.v1.SemanticChunkingConfig;
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

  public BasicDocumentAnalyzer(Map<String, String> configuration) {
    this(ProfileRegistry.createDefault(), new ModelBundleCache(configuration));
  }

  public BasicDocumentAnalyzer(ProfileRegistry profileRegistry, ModelBundleCache modelBundleCache) {
    Objects.requireNonNull(profileRegistry, "profileRegistry");
    Objects.requireNonNull(modelBundleCache, "modelBundleCache");
    this.profileResolver = new ProfileResolver(profileRegistry);
    this.modelBundleCache = modelBundleCache;
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

    if (PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)) {
      runStep(
          PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
          diagnostics,
          () -> runSentenceDetection(rawText, document, includeProbabilities, diagnostics));
    } else {
      addSkippedDiagnostic(diagnostics, PipelineStep.PIPELINE_STEP_SENTENCE_DETECT);
    }

    if (PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_TOKENIZE)) {
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

    final OffsetEncoding requestedEncoding = request.hasOptions()
        ? request.getOptions().getOffsetEncoding()
        : OffsetEncoding.OFFSET_ENCODING_UNSPECIFIED;
    applyOffsetEncoding(document, rawText, requestedEncoding);

    return AnalyzeDocumentResponse.newBuilder()
        .setDocument(document.build())
        .addAllDiagnostics(diagnostics)
        .build();
  }

  private static void validateSupportedRequest(
      AnalyzeDocumentRequest request, AnalysisProfile profile, String rawText) {
    for (PipelineStep step : profile.getStepsList()) {
      if (step == PipelineStep.PIPELINE_STEP_UNSPECIFIED) {
        continue;
      }
      if (!PipelineStepPolicy.isImplemented(step)) {
        throw AnalysisException.unimplemented(step.name() + " is not implemented on this server");
      }
    }

    validateOptions(request, rawText);
    validateModelBundle(profile);

    if (request.getChunkEmbedConfigsCount() == 0) {
      return;
    }
    for (ChunkEmbedConfigEntry entry : request.getChunkEmbedConfigsList()) {
      validateSemanticChunking(entry);
    }
    throw AnalysisException.unimplemented("chunk_embed_configs are not implemented on this server");
  }

  private static void validateOptions(AnalyzeDocumentRequest request, String rawText) {
    if (!request.hasOptions()) {
      return;
    }
    final AnalysisOptions options = request.getOptions();
    final InferenceBackend backend = options.getInferenceBackend();
    if (backend != InferenceBackend.INFERENCE_BACKEND_UNSPECIFIED
        && backend != InferenceBackend.INFERENCE_BACKEND_OPENNLP_ME) {
      throw AnalysisException.unimplemented(
          "inference_backend " + backend.name() + " is not implemented; only OPENNLP_ME is supported");
    }
    if (options.hasOnnxEmbeddingModelId() && !options.getOnnxEmbeddingModelId().isBlank()) {
      throw AnalysisException.unimplemented(
          "onnx_embedding_model_id is not implemented (no EMBED step on this server)");
    }
    if (options.hasMaxTextLength()
        && options.getMaxTextLength() > 0
        && rawText.length() > options.getMaxTextLength()) {
      throw AnalysisException.invalidArgument(
          "document.raw_text exceeds max_text_length (" + options.getMaxTextLength() + ")");
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

  private static void validateSemanticChunking(ChunkEmbedConfigEntry entry) {
    if (!entry.hasChunking() || !entry.getChunking().hasSemanticConfig()) {
      return;
    }
    final SemanticChunkingConfig semantic = entry.getChunking().getSemanticConfig();
    if (semantic.hasSemanticEmbeddingModelId() && !semantic.getSemanticEmbeddingModelId().isBlank()) {
      return;
    }
    if (entry.getEmbeddingModelIdsCount() == 1) {
      return;
    }
    throw AnalysisException.invalidArgument(
        "semantic chunking requires semantic_embedding_model_id or exactly one embedding_model_id");
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
