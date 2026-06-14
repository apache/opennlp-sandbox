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

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.opennlp.grpc.chunk.ChunkEmbedProcessor;
import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.model.DocCategorizerModel;
import org.apache.opennlp.grpc.model.DocCategorizerRegistry;
import org.apache.opennlp.grpc.model.NameFinderRegistry;
import org.apache.opennlp.grpc.model.SentimentRegistry;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.processor.PipelineStepPolicy;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ModelBundleRef;
import org.apache.opennlp.grpc.v1.POSTagFormat;
import org.apache.opennlp.grpc.v1.ParseFormat;
import org.apache.opennlp.grpc.v1.PipelineStep;

/**
 * Validates an {@code AnalyzeDocument} request against the capabilities of this server
 * before any pipeline step runs, so invalid requests fail fast with a precise status
 * instead of failing halfway through processing.
 */
final class AnalysisRequestValidator {

  private final EmbeddingProvider embeddingProvider;
  private final NameFinderRegistry nameFinderRegistry;
  private final DocCategorizerRegistry docCategorizerRegistry;
  private final SentimentRegistry sentimentRegistry;
  private final boolean parserAvailable;

  AnalysisRequestValidator(
      EmbeddingProvider embeddingProvider,
      NameFinderRegistry nameFinderRegistry,
      DocCategorizerRegistry docCategorizerRegistry,
      SentimentRegistry sentimentRegistry,
      boolean parserAvailable) {
    this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
    this.nameFinderRegistry = Objects.requireNonNull(nameFinderRegistry, "nameFinderRegistry");
    this.docCategorizerRegistry =
        Objects.requireNonNull(docCategorizerRegistry, "docCategorizerRegistry");
    this.sentimentRegistry = Objects.requireNonNull(sentimentRegistry, "sentimentRegistry");
    this.parserAvailable = parserAvailable;
  }

  /**
   * Runs all request-level checks: every requested step is implemented, options are
   * consistent, the model bundle exists, the embedding configuration is satisfiable,
   * and all chunk+embed config entries are well-formed.
   *
   * @throws AnalysisException If any check fails.
   */
  void validate(AnalyzeDocumentRequest request, AnalysisProfile profile, String rawText) {
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
    validateNerRequest(profile);
    validateDocCategorizeRequest(profile);
    validateSentimentRequest(profile);
    validateParseRequest(profile);
    validatePosTagFormat(profile);
    validateEmbeddingRequest(request, profile);
    validateChunkEmbedConfigs(request);
  }

  /**
   * Resolves the entity types to run for NER: an explicit profile filter, or all
   * configured types when {@code ner_entity_types} is empty.
   */
  List<String> resolveNerEntityTypes(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_NER)) {
      return List.of();
    }
    return nameFinderRegistry.resolveEntityTypes(profile.getNerEntityTypesList());
  }

  /**
   * Resolves the embedding model id for this request: the explicitly requested id, or
   * the provider default. Returns {@code null} when the profile does not embed.
   */
  String resolveEmbeddingModelId(AnalyzeDocumentRequest request, AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_EMBED)) {
      return null;
    }
    String requested = null;
    if (request.hasOptions() && request.getOptions().hasEmbeddingModelId()) {
      requested = request.getOptions().getEmbeddingModelId();
    }
    return embeddingProvider.resolveModelId(requested);
  }

  /**
   * Resolves the document categorizer to run for this request: the configured default (or the
   * sole configured model). Returns {@code null} when the profile does not categorize.
   */
  String resolveDocCategorizerModelId(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE)) {
      return null;
    }
    return docCategorizerRegistry.resolveDefaultModelId();
  }

  /**
   * Whether the selected document categorizer needs tokens (classic maxent) rather than only the
   * raw text (transformer). A raw-text model can run without {@code TOKENIZE}. Defaults to
   * {@code true} for an unknown id so the conservative token prerequisite still applies.
   */
  boolean docCategorizerRequiresTokens(String modelId) {
    final DocCategorizerModel model = docCategorizerRegistry.get(modelId);
    return model == null || model.requiresTokens();
  }

  private void validateDocCategorizeRequest(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE)) {
      return;
    }
    if (!docCategorizerRegistry.isAvailable()) {
      throw AnalysisException.notFound(
          "PIPELINE_STEP_DOC_CATEGORIZE requested but no document categorizer models are "
              + "configured on this server; set model.doccat.<id>.path entries");
    }
    if (docCategorizerRegistry.resolveDefaultModelId() == null) {
      throw AnalysisException.invalidArgument(
          "Multiple document categorizer models are configured; set " + DocCategorizerRegistry
              .KEY_DEFAULT_ID + " to select one. Configured ids: "
              + docCategorizerRegistry.modelIds());
    }
  }

  /**
   * Resolves the sentiment model to run for this request: the configured default (or the sole
   * configured model). Returns {@code null} when the profile does not score sentiment.
   */
  String resolveSentimentModelId(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_SENTIMENT)) {
      return null;
    }
    return sentimentRegistry.resolveDefaultModelId();
  }

  /**
   * Whether the selected sentiment model needs each sentence's tokens (classic maxent) rather
   * than only the sentence text (transformer). A raw-text model still needs sentences, but no
   * {@code TOKENIZE}. Defaults to {@code true} for an unknown id.
   */
  boolean sentimentRequiresTokens(String modelId) {
    final DocCategorizerModel model = sentimentRegistry.get(modelId);
    return model == null || model.requiresTokens();
  }

  private void validateSentimentRequest(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_SENTIMENT)) {
      return;
    }
    if (!sentimentRegistry.isAvailable()) {
      throw AnalysisException.notFound(
          "PIPELINE_STEP_SENTIMENT requested but no sentiment models are configured on this "
              + "server; set model.sentiment.<id>.path entries");
    }
    if (sentimentRegistry.resolveDefaultModelId() == null) {
      throw AnalysisException.invalidArgument(
          "Multiple sentiment models are configured; set " + SentimentRegistry.KEY_DEFAULT_ID
              + " to select one. Configured ids: " + sentimentRegistry.modelIds());
    }
  }

  private void validatePosTagFormat(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_POS_TAG)) {
      return;
    }
    final POSTagFormat format = profile.getPosTagFormat();
    if (format != POSTagFormat.POS_TAG_FORMAT_UNSPECIFIED
        && format != POSTagFormat.UNRECOGNIZED) {
      // The tagger emits its model's native tagset; we do not convert/select tagsets, so reject
      // rather than silently returning a different tagset than the client asked for.
      throw AnalysisException.unimplemented(
          "pos_tag_format selection is not implemented; the POS tagger emits its model's native "
              + "tagset (requested " + format + ")");
    }
  }

  private void validateParseRequest(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_PARSE)) {
      return;
    }
    if (!parserAvailable) {
      throw AnalysisException.notFound(
          "PIPELINE_STEP_PARSE requested but no parser model is configured on this server; "
              + "set model.parser.path");
    }
  }

  /**
   * Resolves which parse representations to populate for this request: the formats listed in
   * options, or the default {@code STRUCTURED + BRACKETED} set when none (or only UNSPECIFIED)
   * is given. Returns an empty set when the profile does not parse.
   */
  Set<ParseFormat> resolveParseFormats(AnalyzeDocumentRequest request, AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_PARSE)) {
      return EnumSet.noneOf(ParseFormat.class);
    }
    final Set<ParseFormat> formats = EnumSet.noneOf(ParseFormat.class);
    if (request.hasOptions()) {
      for (ParseFormat format : request.getOptions().getParseFormatsList()) {
        if (format != ParseFormat.PARSE_FORMAT_UNSPECIFIED
            && format != ParseFormat.UNRECOGNIZED) {
          formats.add(format);
        }
      }
    }
    if (formats.isEmpty()) {
      formats.add(ParseFormat.PARSE_FORMAT_STRUCTURED);
      formats.add(ParseFormat.PARSE_FORMAT_BRACKETED);
    }
    return formats;
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

  private void validateChunkEmbedConfigs(AnalyzeDocumentRequest request) {
    if (request.getChunkEmbedConfigsCount() == 0) {
      return;
    }
    for (ChunkEmbedConfigEntry entry : request.getChunkEmbedConfigsList()) {
      ChunkEmbedProcessor.validateEntry(entry, embeddingProvider);
    }
  }

  private void validateNerRequest(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_NER)) {
      return;
    }
    if (!nameFinderRegistry.isAvailable()) {
      throw AnalysisException.notFound(
          "PIPELINE_STEP_NER requested but no name finder models are configured on this server; "
              + "set model.name_finder.<entity_type>.path entries");
    }
    for (String entityType : profile.getNerEntityTypesList()) {
      if (entityType == null || entityType.isBlank()) {
        throw AnalysisException.invalidArgument("ner_entity_types must not contain blank values");
      }
      if (!nameFinderRegistry.supportsEntityType(entityType)) {
        throw AnalysisException.notFound(
            "Unknown ner_entity_type '" + entityType + "'; configured types: "
                + nameFinderRegistry.entityTypes());
      }
    }
  }

  private void validateModelBundle(AnalysisProfile profile) {
    if (!profile.hasModelBundle()) {
      return;
    }
    final ModelBundleRef bundle = profile.getModelBundle();
    final String bundleId = bundle.getBundleId();
    if (!bundleId.isBlank()
        && !bundleId.equals(ProfileRegistry.DEFAULT_BUNDLE_ID)
        && !bundleId.equals(ProfileRegistry.NER_BUNDLE_ID)
        && !bundleId.equals(ProfileRegistry.DOCCAT_BUNDLE_ID)
        && !bundleId.equals(ProfileRegistry.SENTIMENT_BUNDLE_ID)
        && !bundleId.equals(ProfileRegistry.PARSE_BUNDLE_ID)) {
      throw AnalysisException.notFound(
          "Unknown model bundle '" + bundleId + "'; available bundles: "
              + ProfileRegistry.DEFAULT_BUNDLE_ID
              + (nameFinderRegistry.isAvailable() ? ", " + ProfileRegistry.NER_BUNDLE_ID : "")
              + (docCategorizerRegistry.isAvailable()
                  ? ", " + ProfileRegistry.DOCCAT_BUNDLE_ID : "")
              + (sentimentRegistry.isAvailable()
                  ? ", " + ProfileRegistry.SENTIMENT_BUNDLE_ID : "")
              + (parserAvailable ? ", " + ProfileRegistry.PARSE_BUNDLE_ID : ""));
    }
    if (bundleId.equals(ProfileRegistry.NER_BUNDLE_ID) && !nameFinderRegistry.isAvailable()) {
      throw AnalysisException.notFound(
          "Model bundle '" + ProfileRegistry.NER_BUNDLE_ID
              + "' requires name finder models; configure model.name_finder.<entity_type>.path");
    }
    if (bundleId.equals(ProfileRegistry.DOCCAT_BUNDLE_ID)
        && !docCategorizerRegistry.isAvailable()) {
      throw AnalysisException.notFound(
          "Model bundle '" + ProfileRegistry.DOCCAT_BUNDLE_ID
              + "' requires document categorizer models; configure model.doccat.<id>.path");
    }
    if (bundleId.equals(ProfileRegistry.SENTIMENT_BUNDLE_ID)
        && !sentimentRegistry.isAvailable()) {
      throw AnalysisException.notFound(
          "Model bundle '" + ProfileRegistry.SENTIMENT_BUNDLE_ID
              + "' requires sentiment models; configure model.sentiment.<id>.path");
    }
    if (bundleId.equals(ProfileRegistry.PARSE_BUNDLE_ID) && !parserAvailable) {
      throw AnalysisException.notFound(
          "Model bundle '" + ProfileRegistry.PARSE_BUNDLE_ID
              + "' requires a parser model; configure model.parser.path");
    }
    if (bundle.getComponentModelsCount() > 0) {
      throw AnalysisException.unimplemented(
          "per-component model selection (component_models) is not implemented");
    }
  }
}
