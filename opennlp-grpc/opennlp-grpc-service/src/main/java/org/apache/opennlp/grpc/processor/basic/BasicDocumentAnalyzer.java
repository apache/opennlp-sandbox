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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.model.NameFinderRegistry;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.processor.PipelineStepPolicy;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.profile.ProfileResolver;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.ProcessingDiagnostic;

/**
 * v1 pipeline orchestrator: resolves the analysis profile, validates the request, and
 * executes the requested steps in order, delegating the actual work to focused
 * helpers — {@link AnalysisRequestValidator} for request checks,
 * {@link ClassicStepRunner} for the classic annotation steps,
 * {@link EmbedChunkStepRunner} for embeddings and chunk groups, and
 * {@link DocumentOffsetEncoder} for the final span conversion.
 *
 * <p>Internally all offsets are computed in Java UTF-16 indices; the final pass
 * converts every span to the client-requested {@link OffsetEncoding} (default UTF-8
 * bytes).
 */
public class BasicDocumentAnalyzer implements DocumentAnalyzer {

  private final ProfileResolver profileResolver;
  private final AnalysisRequestValidator validator;
  private final ClassicStepRunner classicSteps;
  private final EmbedChunkStepRunner embedChunkSteps;
  private final NameFinderRegistry nameFinderRegistry;

  public BasicDocumentAnalyzer(Map<String, String> configuration) {
    this(new ModelBundleCache(configuration));
  }

  private BasicDocumentAnalyzer(ModelBundleCache modelBundleCache) {
    this(ProfileRegistry.createDefault(modelBundleCache.getNameFinderRegistry().isAvailable()),
        modelBundleCache);
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
    this.nameFinderRegistry = modelBundleCache.getNameFinderRegistry();
    this.validator = new AnalysisRequestValidator(embeddingProvider, nameFinderRegistry);
    this.classicSteps = new ClassicStepRunner(modelBundleCache);
    this.embedChunkSteps = new EmbedChunkStepRunner(embeddingProvider);
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
    validator.validate(request, profile, rawText);

    final boolean includeProbabilities =
        request.hasOptions() && request.getOptions().getIncludeProbabilities();

    final List<ProcessingDiagnostic> diagnostics = new ArrayList<>();
    final OpenNlpDocument.Builder document = OpenNlpDocument.newBuilder()
        .setDocId(input.getDocId())
        .setRawText(rawText);
    if (input.hasMetadata()) {
      document.setMetadata(input.getMetadata());
    }

    if (shouldRunStep(request, profile, PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT)) {
      runStep(
          PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT,
          () -> classicSteps.detectLanguage(rawText, document, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT));
    }

    if (shouldRunStep(request, profile, PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)) {
      runStep(
          PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
          () -> classicSteps.detectSentences(rawText, document, includeProbabilities, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT));
    }

    if (shouldRunStep(request, profile, PipelineStep.PIPELINE_STEP_TOKENIZE)) {
      requireSentences(document, PipelineStep.PIPELINE_STEP_TOKENIZE);
      runStep(
          PipelineStep.PIPELINE_STEP_TOKENIZE,
          () -> classicSteps.tokenize(rawText, document, includeProbabilities, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_TOKENIZE));
    }

    final List<String> nerEntityTypes = validator.resolveNerEntityTypes(profile);
    if (shouldRunStep(request, profile, PipelineStep.PIPELINE_STEP_NER)) {
      requireTokens(document, PipelineStep.PIPELINE_STEP_NER);
      runStep(
          PipelineStep.PIPELINE_STEP_NER,
          () -> classicSteps.findNamedEntities(
              document, nerEntityTypes, includeProbabilities, diagnostics));
      if (shouldClearAdaptiveData(request)) {
        nameFinderRegistry.clearAdaptiveData();
      }
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_NER));
    }

    if (shouldRunStep(request, profile, PipelineStep.PIPELINE_STEP_POS_TAG)) {
      requireTokens(document, PipelineStep.PIPELINE_STEP_POS_TAG);
      runStep(
          PipelineStep.PIPELINE_STEP_POS_TAG,
          () -> classicSteps.tagPartsOfSpeech(document, includeProbabilities, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_POS_TAG));
    }

    if (shouldRunStep(request, profile, PipelineStep.PIPELINE_STEP_LEMMATIZE)) {
      requireTokens(document, PipelineStep.PIPELINE_STEP_LEMMATIZE);
      if (!shouldRunStep(request, profile, PipelineStep.PIPELINE_STEP_POS_TAG)) {
        throw AnalysisException.failedPrecondition(
            PipelineStep.PIPELINE_STEP_LEMMATIZE.name()
                + " requires "
                + PipelineStep.PIPELINE_STEP_POS_TAG.name());
      }
      runStep(
          PipelineStep.PIPELINE_STEP_LEMMATIZE,
          () -> classicSteps.lemmatize(document, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_LEMMATIZE));
    }

    final String embeddingModelId = validator.resolveEmbeddingModelId(request, profile);
    if (PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_EMBED)) {
      requireSentences(document, PipelineStep.PIPELINE_STEP_EMBED);
      runStep(
          PipelineStep.PIPELINE_STEP_EMBED,
          () -> embedChunkSteps.embedSentences(rawText, document, embeddingModelId, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_EMBED));
    }

    if (request.getChunkEmbedConfigsCount() > 0) {
      runStep(
          PipelineStep.PIPELINE_STEP_CHUNK,
          () -> embedChunkSteps.runChunkEmbedConfigs(rawText, document, request, diagnostics));
    } else if (shouldRunStep(request, profile, PipelineStep.PIPELINE_STEP_CHUNK)) {
      runStep(
          PipelineStep.PIPELINE_STEP_CHUNK,
          () -> embedChunkSteps.runProfileChunking(rawText, document, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_CHUNK));
    }

    final OffsetEncoding requestedEncoding = request.hasOptions()
        ? request.getOptions().getOffsetEncoding()
        : OffsetEncoding.OFFSET_ENCODING_UNSPECIFIED;
    DocumentOffsetEncoder.apply(document, rawText, requestedEncoding);

    return AnalyzeDocumentResponse.newBuilder()
        .setDocument(document.build())
        .addAllDiagnostics(diagnostics)
        .build();
  }

  /**
   * Computes the steps that effectively run for this request: the profile steps plus
   * the backbone steps implied by embedding and chunking requests.
   */
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

  /**
   * Defaults to {@code true} per the v1 contract when {@code clear_adaptive_data} is unset.
   */
  private static boolean shouldClearAdaptiveData(AnalyzeDocumentRequest request) {
    if (!request.hasOptions() || !request.getOptions().hasClearAdaptiveData()) {
      return true;
    }
    return request.getOptions().getClearAdaptiveData();
  }

  /** Wraps unexpected step failures in an INTERNAL status carrying the step name. */
  private static void runStep(PipelineStep step, StepAction action) {
    try {
      action.run();
    } catch (AnalysisException e) {
      throw e;
    } catch (RuntimeException e) {
      throw AnalysisException.internal(step.name() + " failed", e);
    }
  }

  private static void requireSentences(OpenNlpDocument.Builder document, PipelineStep step) {
    if (document.getSentencesCount() == 0) {
      throw AnalysisException.failedPrecondition(
          step.name() + " requires " + PipelineStep.PIPELINE_STEP_SENTENCE_DETECT.name());
    }
  }

  private static void requireTokens(OpenNlpDocument.Builder document, PipelineStep step) {
    boolean tokenized = document.getSentencesCount() > 0;
    for (AnnotatedSentence sentence : document.getSentencesList()) {
      tokenized &= sentence.getTokensCount() > 0;
    }
    if (!tokenized) {
      throw AnalysisException.failedPrecondition(
          step.name() + " requires " + PipelineStep.PIPELINE_STEP_TOKENIZE.name());
    }
  }

  @FunctionalInterface
  private interface StepAction {
    void run();
  }
}
