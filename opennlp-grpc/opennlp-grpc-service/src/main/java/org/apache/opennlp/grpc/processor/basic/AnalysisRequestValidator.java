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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.opennlp.grpc.chunk.ChunkEmbedProcessor;
import org.apache.opennlp.grpc.embedding.EmbeddingBackendSelections;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.model.ChunkerRegistry;
import org.apache.opennlp.grpc.model.HunspellRegistry;
import org.apache.opennlp.grpc.model.LatticeRegistry;
import org.apache.opennlp.grpc.spi.model.DocCategorizerModel;
import org.apache.opennlp.grpc.model.DocCategorizerRegistry;
import org.apache.opennlp.grpc.model.ModelArtifactRegistry;
import org.apache.opennlp.grpc.model.NameFinderRegistry;
import org.apache.opennlp.grpc.model.ParserRegistry;
import org.apache.opennlp.grpc.model.SentenceDetectorRegistry;
import org.apache.opennlp.grpc.model.SentimentRegistry;
import org.apache.opennlp.grpc.model.SubwordRegistry;
import org.apache.opennlp.grpc.model.TokenizerRegistry;
import org.apache.opennlp.grpc.model.WordNetRegistry;
import org.apache.opennlp.grpc.spi.AnalysisException;
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
import opennlp.tools.util.normalizer.Dimension;
import org.apache.opennlp.grpc.v1.Normalizer;
import org.apache.opennlp.grpc.v1.POSTagFormat;
import org.apache.opennlp.grpc.v1.ParseFormat;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.SentenceDetectorSelector;
import org.apache.opennlp.grpc.v1.StandardSentenceDetectorEngine;
import org.apache.opennlp.grpc.v1.StandardTokenizerEngine;
import org.apache.opennlp.grpc.v1.TermLayerSpec;
import org.apache.opennlp.grpc.v1.TokenizerSelector;
import org.apache.opennlp.grpc.v1.VectorNormalization;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.Tokenizer;

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
  private final SubwordRegistry subwordRegistry;
  private final HunspellRegistry hunspellRegistry;
  private final WordNetRegistry wordNetRegistry;
  private final LatticeRegistry latticeRegistry;
  private final TokenizerRegistry tokenizerRegistry;
  private final SentenceDetectorRegistry sentenceDetectorRegistry;

  /**
   * Creates a validator over the capabilities available to the analyzer.
   *
   * @param embeddingProvider The configured embedding provider.
   * @param nameFinderRegistry The configured named-entity models.
   * @param docCategorizerRegistry The configured document categorizers.
   * @param sentimentRegistry The configured sentiment models.
   * @param parserRegistry The configured parsers.
   * @param chunkerRegistry The configured syntactic chunkers.
   * @param artifactRegistry The model-artifact registry.
   * @param subwordRegistry The configured subword models.
   * @param hunspellRegistry The configured Hunspell dictionaries.
   * @param wordNetRegistry The configured WordNet lexicons.
   * @param latticeRegistry The configured lattice tokenizers.
   * @param tokenizerRegistry The configured word-tokenizer engines.
   * @param sentenceDetectorRegistry The configured sentence-detector engines.
   */
  AnalysisRequestValidator(
      EmbeddingProvider embeddingProvider,
      NameFinderRegistry nameFinderRegistry,
      DocCategorizerRegistry docCategorizerRegistry,
      SentimentRegistry sentimentRegistry,
      ParserRegistry parserRegistry,
      ChunkerRegistry chunkerRegistry,
      ModelArtifactRegistry artifactRegistry,
      SubwordRegistry subwordRegistry,
      HunspellRegistry hunspellRegistry,
      WordNetRegistry wordNetRegistry,
      LatticeRegistry latticeRegistry,
      TokenizerRegistry tokenizerRegistry,
      SentenceDetectorRegistry sentenceDetectorRegistry) {
    if (embeddingProvider == null) {
      throw new IllegalArgumentException("embeddingProvider must not be null");
    }
    this.embeddingProvider = embeddingProvider;
    if (nameFinderRegistry == null) {
      throw new IllegalArgumentException("nameFinderRegistry must not be null");
    }
    this.nameFinderRegistry = nameFinderRegistry;
    if (docCategorizerRegistry == null) {
      throw new IllegalArgumentException("docCategorizerRegistry must not be null");
    }
    this.docCategorizerRegistry = docCategorizerRegistry;
    if (sentimentRegistry == null) {
      throw new IllegalArgumentException("sentimentRegistry must not be null");
    }
    this.sentimentRegistry = sentimentRegistry;
    if (parserRegistry == null) {
      throw new IllegalArgumentException("parserRegistry must not be null");
    }
    this.parserRegistry = parserRegistry;
    if (chunkerRegistry == null) {
      throw new IllegalArgumentException("chunkerRegistry must not be null");
    }
    this.chunkerRegistry = chunkerRegistry;
    if (artifactRegistry == null) {
      throw new IllegalArgumentException("artifactRegistry must not be null");
    }
    this.artifactRegistry = artifactRegistry;
    if (subwordRegistry == null) {
      throw new IllegalArgumentException("subwordRegistry must not be null");
    }
    this.subwordRegistry = subwordRegistry;
    if (hunspellRegistry == null) {
      throw new IllegalArgumentException("hunspellRegistry must not be null");
    }
    this.hunspellRegistry = hunspellRegistry;
    if (wordNetRegistry == null) {
      throw new IllegalArgumentException("wordNetRegistry must not be null");
    }
    this.wordNetRegistry = wordNetRegistry;
    if (latticeRegistry == null) {
      throw new IllegalArgumentException("latticeRegistry must not be null");
    }
    this.latticeRegistry = latticeRegistry;
    if (tokenizerRegistry == null) {
      throw new IllegalArgumentException("tokenizerRegistry must not be null");
    }
    this.tokenizerRegistry = tokenizerRegistry;
    if (sentenceDetectorRegistry == null) {
      throw new IllegalArgumentException("sentenceDetectorRegistry must not be null");
    }
    this.sentenceDetectorRegistry = sentenceDetectorRegistry;
  }

  /**
   * Runs all request-level checks: every requested step is implemented, options are
   * consistent, the model bundle exists, the embedding configuration is satisfiable,
   * and all chunk+embed config entries are well-formed.
   *
   * @throws AnalysisException If any check fails.
   */
  void validateConfiguration(AnalyzeDocumentRequest request, AnalysisProfile profile) {
    for (PipelineStep step : profile.getStepsList()) {
      if (step == PipelineStep.PIPELINE_STEP_UNSPECIFIED) {
        continue;
      }
      if (!PipelineStepPolicy.isImplemented(step)) {
        throw AnalysisException.unimplemented(step.name() + " is not implemented on this server");
      }
    }
    validateOptions(request, profile);
    validateModelBundle(profile);
    validateStepDependencies(profile);
    validateNerRequest(profile);
    validateDocCategorizeRequest(profile);
    validateSentimentRequest(profile);
    validateParseRequest(profile);
    validateSyntacticChunkRequest(profile);
    validatePosTagFormat(profile);
    validateNormalizeRequest(profile);
    validateTokenizerEngine(profile);
    resolveSentenceDetector(profile);
    validateTermDimensions(profile);
    validateTermProfile(profile);
    validateTermLayers(profile);
    validateTermVectorRequest(profile);
    validateStopwordLanguage(profile);
    validateSubwordRequest(profile);
    validateStemRequest(profile);
    validateExpandRequest(profile);
    validateEmbeddingRequest(request, profile);
    validateChunkEmbedConfigs(request);
    validateCategoryChunkConfigs(request, profile);
  }

  /**
   * Rejects requested steps whose pipeline dependency the profile does not run, so the
   * request fails fast here with FAILED_PRECONDITION (the runtime guards in
   * {@code BasicDocumentAnalyzer} stay as defense in depth).
   */
  private static void validateStepDependencies(AnalysisProfile profile) {
    requireStep(profile, PipelineStep.PIPELINE_STEP_TOKENIZE,
        PipelineStep.PIPELINE_STEP_SENTENCE_DETECT);
    requireStep(profile, PipelineStep.PIPELINE_STEP_LEMMATIZE,
        PipelineStep.PIPELINE_STEP_POS_TAG);
    requireStep(profile, PipelineStep.PIPELINE_STEP_GEOCODE,
        PipelineStep.PIPELINE_STEP_NER);
    requireStep(profile, PipelineStep.PIPELINE_STEP_STEM,
        PipelineStep.PIPELINE_STEP_TOKENIZE);
    requireStep(profile, PipelineStep.PIPELINE_STEP_EXPAND,
        PipelineStep.PIPELINE_STEP_TOKENIZE);
  }

  /** Rejects a requested step whose dependency the profile does not run. */
  private static void requireStep(
      AnalysisProfile profile, PipelineStep step, PipelineStep dependency) {
    if (PipelineStepPolicy.shouldRun(profile, step)
        && !PipelineStepPolicy.shouldRun(profile, dependency)) {
      throw AnalysisException.failedPrecondition(
          step.name() + " requires " + dependency.name());
    }
  }

  /** Validates constraints that depend on one document rather than the fixed pipeline. */
  void validateDocument(AnalyzeDocumentRequest request, String rawText) {
    if (request.hasOptions()
        && request.getOptions().hasMaxTextLength()
        && request.getOptions().getMaxTextLength() > 0
        && rawText.length() > request.getOptions().getMaxTextLength()) {
      throw AnalysisException.invalidArgument(
          "document.raw_text exceeds max_text_length ("
              + request.getOptions().getMaxTextLength() + ")");
    }
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
   * Resolves the subword model to run for this request: the profile's explicit id, or
   * the configured default (or sole configured model). Returns {@code null} when the
   * profile does not run the subword step.
   */
  String resolveSubwordModelId(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_SUBWORD_TOKENIZE)) {
      return null;
    }
    return subwordRegistry.resolveModelId(
        profile.hasSubwordModelId() ? profile.getSubwordModelId() : null);
  }

  /** Rejects a subword request that no configured model can serve. */
  private void validateSubwordRequest(AnalysisProfile profile) {
    resolveSubwordModelId(profile);
  }

  /** Rejects a stem request whose spec is incomplete or unservable. */
  private void validateStemRequest(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_STEM)) {
      return;
    }
    StemmerSelector.validate(profile.getStemmer(), hunspellRegistry);
  }

  /**
   * Resolves the WordNet lexicon to run for this request: the profile's explicit id,
   * or the configured default (or sole configured lexicon). Returns {@code null} when
   * the profile does not run the expand step.
   */
  String resolveWordNetLexiconId(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_EXPAND)) {
      return null;
    }
    return wordNetRegistry.resolveLexiconId(
        profile.hasWordnetLexiconId() ? profile.getWordnetLexiconId() : null);
  }

  /** Rejects an expand request that no configured lexicon can serve. */
  private void validateExpandRequest(AnalysisProfile profile) {
    resolveWordNetLexiconId(profile);
  }

  /** Resolves the requested, profiled, or server-default embedding model id. */
  String resolveEmbeddingModelId(AnalyzeDocumentRequest request, AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_EMBED)) {
      return null;
    }
    final String pinnedEmbedder = pinnedEmbedderModelId(profile);
    if (pinnedEmbedder != null) {
      return pinnedEmbedder;
    }
    String requested = null;
    if (request.hasOptions()) {
      if (request.getOptions().hasEmbeddingSelector()) {
        requested = request.getOptions().getEmbeddingSelector().getModelId();
      } else if (request.getOptions().hasEmbeddingModelId()) {
        requested = request.getOptions().getEmbeddingModelId();
      }
    }
    return embeddingProvider.resolveModelId(requested);
  }

  /** Resolves the concrete embedding backend id, or blank for default routing. */
  String resolveEmbeddingBackendId(AnalyzeDocumentRequest request) {
    if (!request.hasOptions() || !request.getOptions().hasEmbeddingSelector()) {
      return null;
    }
    return EmbeddingBackendSelections.selectedId(
        request.getOptions().getEmbeddingSelector());
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

  /** Validates doc categorize request. */
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

  /** Validates sentiment request. */
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

  /** The rule-based UAX #29 engine id for AnalysisProfile.tokenizer_engine. */
  static final String UAX29_TOKENIZER_ENGINE = "uax29";
  static final String LATTICE_TOKENIZER_ENGINE = "lattice";
  private static final String MODEL_TOKENIZER_ENGINE = "model";

  /** Validates normalize request. */
  /**
   * Rejects a chain selecting more than one whitespace-collapsing normalizer, since each
   * variant defines the complete whitespace treatment.
   *
   * @param normalizers Recognized normalizers in canonical order.
   * @throws AnalysisException If two whitespace variants are combined.
   */
  private void requireOneWhitespaceVariant(List<Normalizer> normalizers) {
    int variants = 0;
    for (final Normalizer normalizer : normalizers) {
      if (normalizer == Normalizer.NORMALIZER_WHITESPACE
          || normalizer == Normalizer.NORMALIZER_WHITESPACE_PRESERVE_LINE_BREAKS
          || normalizer == Normalizer.NORMALIZER_WHITESPACE_PRESERVE_PARAGRAPHS) {
        variants++;
      }
    }
    if (variants > 1) {
      throw AnalysisException.invalidArgument(
          "WHITESPACE, WHITESPACE_PRESERVE_LINE_BREAKS, and WHITESPACE_PRESERVE_PARAGRAPHS "
              + "are mutually exclusive normalizers");
    }
  }

  private void validateNormalizeRequest(AnalysisProfile profile) {
    final boolean requested =
        PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_NORMALIZE);
    if (!requested) {
      if (profile.hasNormalization()) {
        throw AnalysisException.invalidArgument(
            "AnalysisProfile.normalization requires PIPELINE_STEP_NORMALIZE in the profile steps");
      }
      return;
    }
    if (!profile.hasNormalization() || profile.getNormalization().getNormalizersCount() == 0) {
      throw AnalysisException.invalidArgument(
          "PIPELINE_STEP_NORMALIZE requires AnalysisProfile.normalization with at least one normalizer");
    }
    final var spec = profile.getNormalization();
    final var normalizers = Normalizers.canonicalOrder(spec.getNormalizersList());
    if (normalizers.isEmpty()) {
      throw AnalysisException.invalidArgument(
          "AnalysisProfile.normalization.normalizers contains no recognized normalizer");
    }
    requireOneWhitespaceVariant(normalizers);
    final boolean requireAlignment = !spec.hasRequireAlignment() || spec.getRequireAlignment();
    if (requireAlignment && !Normalizers.allOffsetAware(normalizers)) {
      throw AnalysisException.invalidArgument(
          "The requested normalizers include offset-opaque one(s) (NFC, NFKC, CASE_FOLD, ACCENT_FOLD, "
              + "CONFUSABLE_FOLD), which cannot report an alignment; drop them or set "
              + "normalization.require_alignment = false to accept normalized text without one");
    }
  }

  /** Validates tokenizer engine. */
  private void validateTokenizerEngine(AnalysisProfile profile) {
    resolveTokenizer(profile);
    resolveLatticeDictionaryId(profile);
  }

  /** Resolves the typed or compatibility tokenizer choice for one profile. */
  TokenizerSelection resolveTokenizer(AnalysisProfile profile) {
    if (profile.hasTokenizerEngine() && profile.hasTokenizer()) {
      throw AnalysisException.invalidArgument(
          "AnalysisProfile.tokenizer_engine and tokenizer are mutually exclusive");
    }
    if (!profile.hasTokenizer()) {
      final String engine = profile.getTokenizerEngine();
      return switch (engine) {
        case "", MODEL_TOKENIZER_ENGINE -> TokenizerSelection.standard(
            StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_MODEL);
        case UAX29_TOKENIZER_ENGINE -> TokenizerSelection.standard(
            StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_UAX29);
        case LATTICE_TOKENIZER_ENGINE -> TokenizerSelection.standard(
            StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_LATTICE);
        default -> throw AnalysisException.invalidArgument(
            "Unknown tokenizer_engine '" + engine
                + "'; supported: \"model\", \"uax29\", \"lattice\"");
      };
    }

    final TokenizerSelector selector = profile.getTokenizer();
    return switch (selector.getKindCase()) {
      case STANDARD -> switch (selector.getStandard()) {
        case STANDARD_TOKENIZER_ENGINE_UNSPECIFIED, STANDARD_TOKENIZER_ENGINE_MODEL ->
            TokenizerSelection.standard(StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_MODEL);
        case STANDARD_TOKENIZER_ENGINE_UAX29, STANDARD_TOKENIZER_ENGINE_WHITESPACE,
            STANDARD_TOKENIZER_ENGINE_SIMPLE, STANDARD_TOKENIZER_ENGINE_LATTICE ->
            TokenizerSelection.standard(selector.getStandard());
        case UNRECOGNIZED -> throw AnalysisException.invalidArgument(
            "AnalysisProfile.tokenizer.standard is not recognized by this server");
      };
      case CUSTOM -> {
        final String id = selector.getCustom();
        if (id.isBlank()) {
          throw AnalysisException.invalidArgument(
              "AnalysisProfile.tokenizer.custom must not be blank");
        }
        yield TokenizerSelection.custom(id, tokenizerRegistry.get(id));
      }
      case KIND_NOT_SET -> TokenizerSelection.standard(
          StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_MODEL);
    };
  }

  /** Resolves the typed sentence-detector choice for one profile. */
  SentenceDetectorSelection resolveSentenceDetector(AnalysisProfile profile) {
    if (!profile.hasSentenceDetector()) {
      return SentenceDetectorSelection.standard(
          StandardSentenceDetectorEngine.STANDARD_SENTENCE_DETECTOR_ENGINE_MODEL);
    }
    final SentenceDetectorSelector selector = profile.getSentenceDetector();
    return switch (selector.getKindCase()) {
      case STANDARD -> switch (selector.getStandard()) {
        case STANDARD_SENTENCE_DETECTOR_ENGINE_UNSPECIFIED,
            STANDARD_SENTENCE_DETECTOR_ENGINE_MODEL -> SentenceDetectorSelection.standard(
                StandardSentenceDetectorEngine.STANDARD_SENTENCE_DETECTOR_ENGINE_MODEL);
        case STANDARD_SENTENCE_DETECTOR_ENGINE_NEWLINE ->
            SentenceDetectorSelection.standard(selector.getStandard());
        case UNRECOGNIZED -> throw AnalysisException.invalidArgument(
            "AnalysisProfile.sentence_detector.standard is not recognized by this server");
      };
      case CUSTOM -> {
        final String id = selector.getCustom();
        if (id.isBlank()) {
          throw AnalysisException.invalidArgument(
              "AnalysisProfile.sentence_detector.custom must not be blank");
        }
        yield SentenceDetectorSelection.custom(id, sentenceDetectorRegistry.get(id));
      }
      case KIND_NOT_SET -> SentenceDetectorSelection.standard(
          StandardSentenceDetectorEngine.STANDARD_SENTENCE_DETECTOR_ENGINE_MODEL);
    };
  }

  /**
   * Resolves the lattice dictionary to segment with: the profile's explicit id, or the
   * configured default (or sole configured dictionary). Returns {@code null} when the
   * profile does not use the lattice tokenizer engine.
   */
  String resolveLatticeDictionaryId(AnalysisProfile profile) {
    if (resolveTokenizer(profile).standard()
        != StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_LATTICE) {
      return null;
    }
    return latticeRegistry.resolveDictionaryId(
        profile.hasLatticeDictionaryId() ? profile.getLatticeDictionaryId() : null);
  }

  /** One resolved word-tokenizer route. Exactly one of standard and custom is set. */
  record TokenizerSelection(
      StandardTokenizerEngine standard, String customId, Tokenizer custom) {

    /** Creates a standard tokenizer selection. */
    private static TokenizerSelection standard(StandardTokenizerEngine engine) {
      return new TokenizerSelection(engine, null, null);
    }

    /** Creates a custom tokenizer selection. */
    private static TokenizerSelection custom(String id, Tokenizer tokenizer) {
      return new TokenizerSelection(null, id, tokenizer);
    }
  }

  /** One resolved sentence-detector route. Exactly one of standard and custom is set. */
  record SentenceDetectorSelection(
      StandardSentenceDetectorEngine standard, String customId, SentenceDetector custom) {

    /** Creates a standard sentence-detector selection. */
    private static SentenceDetectorSelection standard(StandardSentenceDetectorEngine engine) {
      return new SentenceDetectorSelection(engine, null, null);
    }

    /** Creates a custom sentence-detector selection. */
    private static SentenceDetectorSelection custom(String id, SentenceDetector detector) {
      return new SentenceDetectorSelection(null, id, detector);
    }
  }

  /** Validates term dimensions. */
  private void validateTermDimensions(AnalysisProfile profile) {
    if (profile.getTermDimensionsCount() == 0) {
      return;
    }
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_TOKENIZE)) {
      throw AnalysisException.failedPrecondition(
          "AnalysisProfile.term_dimensions requires PIPELINE_STEP_TOKENIZE in the profile steps");
    }
    for (final String name : profile.getTermDimensionsList()) {
      final Dimension dimension;
      try {
        dimension = Dimension.valueOf(name);
      } catch (IllegalArgumentException e) {
        throw AnalysisException.invalidArgument(
            "Unknown term dimension '" + name + "'; use the library's character-level "
                + "Dimension names (e.g. NFC, CASE_FOLD, FULL_CASE_FOLD, EMOJI_FOLD)");
      }
      if (dimension == Dimension.ORIGINAL || dimension == Dimension.STEM
          || dimension == Dimension.LEMMA) {
        throw AnalysisException.invalidArgument(
            "Term dimension '" + name + "' is not a character-level dimension; "
                + "PIPELINE_STEP_LEMMATIZE owns lemmas");
      }
    }
  }

  /** Validates stopword language. */
  private void validateStopwordLanguage(AnalysisProfile profile) {
    if (!profile.hasStopwordLanguage()) {
      return;
    }
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_TOKENIZE)) {
      throw AnalysisException.failedPrecondition(
          "AnalysisProfile.stopword_language requires PIPELINE_STEP_TOKENIZE in the profile steps");
    }
    if (!opennlp.tools.stopword.StopwordLists.supportedLanguages()
        .contains(profile.getStopwordLanguage())) {
      throw AnalysisException.notFound(
          "No bundled stopword list for language '" + profile.getStopwordLanguage() + "'");
    }
  }

  /** Validates term profile. */
  private void validateTermProfile(AnalysisProfile profile) {
    if (!profile.hasTermProfile()) {
      return;
    }
    if (profile.getTermDimensionsCount() > 0) {
      throw AnalysisException.invalidArgument(
          "term_profile and term_dimensions are mutually exclusive; the profile already "
              + "defines its term dimensions");
    }
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_TOKENIZE)) {
      throw AnalysisException.failedPrecondition(
          "AnalysisProfile.term_profile requires PIPELINE_STEP_TOKENIZE in the profile steps");
    }
    if (opennlp.tools.util.normalizer.NormalizationProfiles
        .forLanguage(profile.getTermProfile()).isEmpty()) {
      throw AnalysisException.notFound(
          "No normalization profile registered for language '" + profile.getTermProfile() + "'");
    }
  }

  /** Validates term layers. */
  private void validateTermLayers(AnalysisProfile profile) {
    if (profile.getTermLayersCount() == 0) {
      return;
    }
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_TOKENIZE)) {
      throw AnalysisException.failedPrecondition(
          "AnalysisProfile.term_layers requires PIPELINE_STEP_TOKENIZE in the profile steps");
    }
    final Set<String> qualifiers = new HashSet<>(profile.getTermDimensionsList());
    if (profile.hasTermProfile()) {
      opennlp.tools.util.normalizer.NormalizationProfiles
          .forLanguage(profile.getTermProfile()).orElseThrow().matchingAnalyzer().dimensions()
          .forEach(dimension -> qualifiers.add(dimension.name()));
    }
    for (final TermLayerSpec spec : profile.getTermLayersList()) {
      if (spec.getQualifier().isBlank()) {
        throw AnalysisException.invalidArgument("term_layers.qualifier must not be blank");
      }
      if (!qualifiers.add(spec.getQualifier())) {
        throw AnalysisException.invalidArgument(
            "term layer qualifier '" + spec.getQualifier() + "' is produced more than once");
      }
      if (spec.getNormalizersCount() == 0 && !spec.hasStemmer()) {
        throw AnalysisException.invalidArgument(
            "term layer '" + spec.getQualifier()
                + "' requires at least one normalizer or a stemmer");
      }
      final List<Normalizer> normalizers =
          Normalizers.canonicalOrder(spec.getNormalizersList());
      if (spec.getNormalizersCount() > 0 && normalizers.isEmpty()) {
        throw AnalysisException.invalidArgument(
            "term layer '" + spec.getQualifier() + "' contains no recognized normalizer");
      }
      requireOneWhitespaceVariant(normalizers);
      if (normalizers.contains(Normalizer.NORMALIZER_CASE_FOLD)
          && normalizers.contains(Normalizer.NORMALIZER_FULL_CASE_FOLD)) {
        throw AnalysisException.invalidArgument(
            "CASE_FOLD and FULL_CASE_FOLD are mutually exclusive normalizers");
      }
      if (spec.hasStemmer()) {
        StemmerSelector.validate(spec.getStemmer(), hunspellRegistry);
      }
    }
  }

  /** Validates term vector request. */
  private void validateTermVectorRequest(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_TERM_VECTOR)) {
      return;
    }
    if (!profile.hasTermVector()) {
      throw AnalysisException.invalidArgument(
          "PIPELINE_STEP_TERM_VECTOR requires AnalysisProfile.term_vector");
    }
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_TOKENIZE)) {
      throw AnalysisException.failedPrecondition(
          "PIPELINE_STEP_TERM_VECTOR requires PIPELINE_STEP_TOKENIZE");
    }
    final var source = TermVectorStepRunner.sourceIdentity(profile.getTermVector());
    switch (source.getStandard()) {
      case STANDARD_LAYER_LEMMAS -> requireTermVectorStep(
          profile, PipelineStep.PIPELINE_STEP_LEMMATIZE, "lemma");
      case STANDARD_LAYER_STEMS -> requireTermVectorStep(
          profile, PipelineStep.PIPELINE_STEP_STEM, "stem");
      case STANDARD_LAYER_TERMS -> validateTermVectorDimension(profile, source.getQualifier());
      default -> {
        // The source resolver already restricted the remaining standard value to TOKENS.
      }
    }
    TermVectorStepRunner.resolvedMode(profile.getTermVector());
  }

  /** Returns the required term-vector step configuration. */
  private static void requireTermVectorStep(
      AnalysisProfile profile, PipelineStep required, String source) {
    if (!PipelineStepPolicy.shouldRun(profile, required)) {
      throw AnalysisException.failedPrecondition(
          "term-vector " + source + " source requires " + required.name());
    }
  }

  /** Validates term vector dimension. */
  private static void validateTermVectorDimension(AnalysisProfile profile, String qualifier) {
    if (profile.getTermDimensionsList().contains(qualifier)) {
      return;
    }
    if (profile.getTermLayersList().stream()
        .anyMatch(spec -> spec.getQualifier().equals(qualifier))) {
      return;
    }
    if (profile.hasTermProfile()) {
      final var normalizationProfile = opennlp.tools.util.normalizer.NormalizationProfiles
          .forLanguage(profile.getTermProfile()).orElseThrow();
      if (normalizationProfile.matchingAnalyzer().dimensions().stream()
          .anyMatch(dimension -> dimension.name().equals(qualifier))) {
        return;
      }
    }
    throw AnalysisException.invalidArgument(
        "term-vector TERMS source requires term_dimensions or term_profile to produce '"
            + qualifier + "'");
  }

  /** Validates pos tag format. */
  private void validatePosTagFormat(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_POS_TAG)) {
      return;
    }
    if (profile.getPosTagFormat() == POSTagFormat.POS_TAG_FORMAT_CUSTOM) {
      throw AnalysisException.unimplemented(
          "pos_tag_format CUSTOM requires a client-supplied tag mapping; not supported");
    }
  }

  /** Validates parse request. */
  private void validateParseRequest(AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_PARSE)) {
      return;
    }
    if (!parserRegistry.isAvailable()) {
      throw AnalysisException.notFound(
          "PIPELINE_STEP_PARSE requested but no parser model is configured on this server; "
              + "set model.parser.<id>.path");
    }
    for (String engine : EngineSelections.ids(profile.getParseEnginePolicy())) {
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

  /** Validates syntactic chunk request. */
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
    for (String engine : EngineSelections.ids(profile.getChunkEnginePolicy())) {
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

  /** Validates options. */
  private void validateOptions(AnalyzeDocumentRequest request, AnalysisProfile profile) {
    if (!request.hasOptions()) {
      return;
    }
    final AnalysisOptions options = request.getOptions();
    final boolean embedRequested =
        PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_EMBED);
    if (options.hasEmbeddingModelId() && options.hasEmbeddingSelector()) {
      throw AnalysisException.invalidArgument(
          "embedding_model_id and embedding_selector are mutually exclusive");
    }
    if (options.hasEmbeddingModelId() && !options.getEmbeddingModelId().isBlank()) {
      if (!embedRequested) {
        throw AnalysisException.failedPrecondition(
            "embedding_model_id requires PIPELINE_STEP_EMBED in the analysis profile");
      }
    }
    if (options.getDocumentCentroidNormalization() == VectorNormalization.UNRECOGNIZED) {
      throw AnalysisException.invalidArgument(
          "document_centroid_normalization must be recognized");
    }
    if (options.getDocumentCentroidNormalization()
            != VectorNormalization.VECTOR_NORMALIZATION_UNSPECIFIED
        && !options.getIncludeDocumentCentroid()) {
      throw AnalysisException.invalidArgument(
          "document_centroid_normalization requires include_document_centroid");
    }
  }

  /** Validates embedding request. */
  private void validateEmbeddingRequest(AnalyzeDocumentRequest request, AnalysisProfile profile) {
    if (!PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_EMBED)) {
      return;
    }
    if (!embeddingProvider.isAvailable()) {
      throw AnalysisException.notFound(
          "PIPELINE_STEP_EMBED requested but no embedding models are configured on this server; "
              + "configure a model.embedder.<id>.<backend> entry");
    }
    final String modelId = resolveEmbeddingModelId(request, profile);
    if (modelId == null || modelId.isBlank()) {
      throw AnalysisException.invalidArgument(
          "embedding_model_id is required when multiple embedding models are configured");
    }
    if (!embeddingProvider.supportsModel(modelId)) {
      throw AnalysisException.notFound("Unknown embedding model '" + modelId + "'");
    }
    final String backendId = resolveEmbeddingBackendId(request);
    if (backendId != null && !embeddingProvider.supportsModel(modelId, backendId)) {
      throw AnalysisException.notFound(
          "Engine '" + backendId + "' does not serve embedding model '" + modelId + "'");
    }
  }

  /** Validates chunk embed configs. */
  private void validateChunkEmbedConfigs(AnalyzeDocumentRequest request) {
    if (request.getChunkEmbedConfigsCount() == 0) {
      return;
    }
    for (ChunkEmbedConfigEntry entry : request.getChunkEmbedConfigsList()) {
      ChunkEmbedProcessor.validateEntry(entry, embeddingProvider);
    }
  }

  /** Validates category chunk configs. */
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

  /** Validates ner request. */
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
    for (String engine : EngineSelections.ids(profile.getNerEnginePolicy())) {
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

  /** Validates model bundle. */
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
