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
import org.apache.opennlp.grpc.model.ChunkerRegistry;
import org.apache.opennlp.grpc.model.DocCategorizerModel;
import org.apache.opennlp.grpc.model.DocCategorizerRegistry;
import org.apache.opennlp.grpc.model.ModelArtifactRegistry;
import org.apache.opennlp.grpc.model.NameFinderRegistry;
import org.apache.opennlp.grpc.model.ParserRegistry;
import org.apache.opennlp.grpc.model.SentimentRegistry;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.processor.PipelineStepPolicy;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.CategoryChunkConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ComponentModelRef;
import org.apache.opennlp.grpc.v1.ComponentType;
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
  private final ParserRegistry parserRegistry;
  private final ChunkerRegistry chunkerRegistry;
  private final ModelArtifactRegistry artifactRegistry;

  AnalysisRequestValidator(
      EmbeddingProvider embeddingProvider,
      NameFinderRegistry nameFinderRegistry,
      DocCategorizerRegistry docCategorizerRegistry,
      SentimentRegistry sentimentRegistry,
      ParserRegistry parserRegistry,
      ChunkerRegistry chunkerRegistry,
      ModelArtifactRegistry artifactRegistry) {
    this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "embeddingProvider");
    this.nameFinderRegistry = Objects.requireNonNull(nameFinderRegistry, "nameFinderRegistry");
    this.docCategorizerRegistry =
        Objects.requireNonNull(docCategorizerRegistry, "docCategorizerRegistry");
    this.sentimentRegistry = Objects.requireNonNull(sentimentRegistry, "sentimentRegistry");
    this.parserRegistry = Objects.requireNonNull(parserRegistry, "parserRegistry");
    this.chunkerRegistry = Objects.requireNonNull(chunkerRegistry, "chunkerRegistry");
    this.artifactRegistry = Objects.requireNonNull(artifactRegistry, "artifactRegistry");
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
    validateSyntacticChunkRequest(profile);
    validatePosTagFormat(profile);
    validateEmbeddingRequest(request, profile);
    validateChunkEmbedConfigs(request);
    validateCategoryChunkConfigs(request, profile);
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
    final String pinnedEmbedder = pinnedEmbedderModelId(profile);
    if (pinnedEmbedder != null) {
      return pinnedEmbedder;
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
    if (profile.getPosTagFormat() == POSTagFormat.POS_TAG_FORMAT_CUSTOM) {
      throw AnalysisException.unimplemented(
          "pos_tag_format CUSTOM requires a client-supplied tag mapping; not supported");
    }
  }

  private void validateParseRequest(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_PARSE)) {
      return;
    }
    if (!parserRegistry.isAvailable()) {
      throw AnalysisException.notFound(
          "PIPELINE_STEP_PARSE requested but no parser model is configured on this server; "
              + "set model.parser.<id>.path");
    }
    for (String engine : profile.getParseEnginePolicy().getEnginesList()) {
      if (engine == null || engine.isBlank()) {
        throw AnalysisException.invalidArgument(
            "parse_engine_policy.engines must not contain blank values");
      }
      if (!parserRegistry.knowsEngine(engine)) {
        throw AnalysisException.notFound(
            "Unknown parser engine '" + engine + "' in parse_engine_policy");
      }
    }
  }

  private void validateSyntacticChunkRequest(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK)) {
      return;
    }
    if (!chunkerRegistry.isAvailable()) {
      throw AnalysisException.notFound(
          "PIPELINE_STEP_SYNTACTIC_CHUNK requested but no chunker model is configured on this "
              + "server; set model.chunker.<id>.path");
    }
    // The chunker classifies the token+POS-tag sequence, so POS tagging must also run.
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_POS_TAG)) {
      throw AnalysisException.failedPrecondition(
          PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK.name() + " requires "
              + PipelineStep.PIPELINE_STEP_POS_TAG.name());
    }
    for (String engine : profile.getChunkEnginePolicy().getEnginesList()) {
      if (engine == null || engine.isBlank()) {
        throw AnalysisException.invalidArgument(
            "chunk_engine_policy.engines must not contain blank values");
      }
      if (!chunkerRegistry.knowsEngine(engine)) {
        throw AnalysisException.notFound(
            "Unknown chunker engine '" + engine + "' in chunk_engine_policy");
      }
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

  private void validateCategoryChunkConfigs(
      AnalyzeDocumentRequest request, AnalysisProfile profile) {
    if (request.getCategoryChunkConfigsCount() == 0) {
      return;
    }
    // Category grouping keys on the per-sentence sentiment label, so SENTIMENT must run.
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_SENTIMENT)) {
      throw AnalysisException.failedPrecondition(
          "category_chunk_configs requires " + PipelineStep.PIPELINE_STEP_SENTIMENT.name()
              + " in the profile so sentences carry category labels");
    }
    for (CategoryChunkConfigEntry entry : request.getCategoryChunkConfigsList()) {
      ChunkEmbedProcessor.validateCategoryEntry(entry, embeddingProvider);
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
    for (String engine : profile.getNerEnginePolicy().getEnginesList()) {
      if (engine == null || engine.isBlank()) {
        throw AnalysisException.invalidArgument(
            "ner_engine_policy.engines must not contain blank values");
      }
      if (!nameFinderRegistry.knowsEngine(engine)) {
        throw AnalysisException.notFound(
            "Unknown NER engine '" + engine + "' in ner_engine_policy");
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
        && !bundleId.equals(ProfileRegistry.PARSE_BUNDLE_ID)
        && !bundleId.equals(ProfileRegistry.CHUNK_BUNDLE_ID)) {
      throw AnalysisException.notFound(
          "Unknown model bundle '" + bundleId + "'; available bundles: "
              + ProfileRegistry.DEFAULT_BUNDLE_ID
              + (nameFinderRegistry.isAvailable() ? ", " + ProfileRegistry.NER_BUNDLE_ID : "")
              + (docCategorizerRegistry.isAvailable()
                  ? ", " + ProfileRegistry.DOCCAT_BUNDLE_ID : "")
              + (sentimentRegistry.isAvailable()
                  ? ", " + ProfileRegistry.SENTIMENT_BUNDLE_ID : "")
              + (parserRegistry.isAvailable() ? ", " + ProfileRegistry.PARSE_BUNDLE_ID : "")
              + (chunkerRegistry.isAvailable() ? ", " + ProfileRegistry.CHUNK_BUNDLE_ID : ""));
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
    if (bundleId.equals(ProfileRegistry.PARSE_BUNDLE_ID) && !parserRegistry.isAvailable()) {
      throw AnalysisException.notFound(
          "Model bundle '" + ProfileRegistry.PARSE_BUNDLE_ID
              + "' requires a parser model; configure model.parser.<id>.path");
    }
    if (bundleId.equals(ProfileRegistry.CHUNK_BUNDLE_ID) && !chunkerRegistry.isAvailable()) {
      throw AnalysisException.notFound(
          "Model bundle '" + ProfileRegistry.CHUNK_BUNDLE_ID
              + "' requires a chunker model; configure model.chunker.<id>.path");
    }
    if (bundle.getComponentModelsCount() > 0) {
      artifactRegistry.validateComponentModels(bundle.getComponentModelsList());
    }
  }

  /**
   * Returns the embedding model id pinned by {@code component_models}, when present.
   *
   * @param profile The effective analysis profile.
   *
   * @return The pinned model id, or {@code null} when no embedder pin is set.
   */
  private String pinnedEmbedderModelId(AnalysisProfile profile) {
    if (!profile.hasModelBundle()) {
      return null;
    }
    for (ComponentModelRef ref : profile.getModelBundle().getComponentModelsList()) {
      if (ref.getComponentType() == ComponentType.COMPONENT_TYPE_EMBEDDER) {
        return artifactRegistry.embedderModelIdForHash(ref.getModelHash())
            .orElseThrow(() -> AnalysisException.notFound(
                "component_models embedder hash '" + ref.getModelHash() + "' is not loaded"));
      }
    }
    return null;
  }
}
