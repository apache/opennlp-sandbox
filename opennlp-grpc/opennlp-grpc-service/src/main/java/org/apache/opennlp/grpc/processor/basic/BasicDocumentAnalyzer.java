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

import opennlp.tools.sentdetect.NewlineSentenceDetector;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import org.apache.opennlp.grpc.chunk.ChunkingStrategies;
import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.model.NameFinderRegistry;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalysisSession;
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.processor.PipelineStepPolicy;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.profile.ProfileResolver;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.DocumentAnalytics;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.ParseFormat;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.ProcessingDiagnostic;
import org.apache.opennlp.grpc.v1.StandardSentenceDetectorEngine;
import org.apache.opennlp.grpc.v1.StandardTokenizerEngine;

/**
 * v1 pipeline orchestrator: resolves the analysis profile, validates the request, and
 * executes the requested steps in order, delegating the actual work to focused
 * helpers: {@link AnalysisRequestValidator} for request checks,
 * {@link ClassicStepRunner} for the classic annotation steps,
 * {@link EmbedChunkStepRunner} for embeddings and chunk groups, and
 * {@link DocumentOffsetEncoder} for the final span conversion.
 *
 * <p>Internally all offsets are computed in Java UTF-16 indices; the final pass
 * converts every span to the client-requested {@link OffsetEncoding} (default UTF-8
 * bytes).
 */
public class BasicDocumentAnalyzer implements DocumentAnalyzer {

  private static final NewlineSentenceDetector NEWLINE_SENTENCE_DETECTOR =
      new NewlineSentenceDetector();

  private final ProfileResolver profileResolver;
  private final AnalysisRequestValidator validator;
  private final ClassicStepRunner classicSteps;
  private final EmbedChunkStepRunner embedChunkSteps;
  private final NameFinderRegistry nameFinderRegistry;
  private final EmbeddingProvider embeddingProvider;

  /**
   * Creates an analyzer backed by a fresh {@link ModelBundleCache} built from the given
   * configuration. The default profile registry is derived from the model capabilities
   * the cache discovers.
   *
   * @param configuration The model-loading configuration passed through to
   *                      {@link ModelBundleCache}. Must not be {@code null}.
   */
  public BasicDocumentAnalyzer(Map<String, String> configuration) {
    this(new ModelBundleCache(configuration));
  }

  private BasicDocumentAnalyzer(ModelBundleCache modelBundleCache) {
    this(modelBundleCache.createProfileRegistry(), modelBundleCache);
  }

  /**
   * Creates an analyzer with an explicit profile registry and model cache, using the
   * embedding provider exposed by the cache.
   *
   * @param profileRegistry  The profile registry resolving requested profiles. Must not
   *                        be {@code null}.
   * @param modelBundleCache The cache supplying loaded models and registries. Must not be
   *                        {@code null}.
   */
  public BasicDocumentAnalyzer(ProfileRegistry profileRegistry, ModelBundleCache modelBundleCache) {
    this(profileRegistry, modelBundleCache, modelBundleCache.getEmbeddingProvider());
  }

  /**
   * Creates an analyzer with an explicit profile registry, model cache, and embedding
   * provider. This is the canonical constructor the other constructors delegate to.
   *
   * @param profileRegistry   The profile registry resolving requested profiles. Must not
   *                         be {@code null}.
   * @param modelBundleCache  The cache supplying loaded models and registries. Must not be
   *                         {@code null}.
   * @param embeddingProvider The provider used for embedding and semantic-chunk steps.
   *                         Must not be {@code null}.
   */
  public BasicDocumentAnalyzer(
      ProfileRegistry profileRegistry,
      ModelBundleCache modelBundleCache,
      EmbeddingProvider embeddingProvider) {
    Objects.requireNonNull(profileRegistry, "profileRegistry");
    Objects.requireNonNull(modelBundleCache, "modelBundleCache");
    Objects.requireNonNull(embeddingProvider, "embeddingProvider");
    this.profileResolver = new ProfileResolver(profileRegistry);
    this.nameFinderRegistry = modelBundleCache.getNameFinderRegistry();
    this.embeddingProvider = embeddingProvider;
    this.validator = new AnalysisRequestValidator(embeddingProvider, nameFinderRegistry,
        modelBundleCache.getDocCategorizerRegistry(), modelBundleCache.getSentimentRegistry(),
        modelBundleCache.getParserRegistry(), modelBundleCache.getChunkerRegistry(),
        modelBundleCache.getArtifactRegistry(), modelBundleCache.getSubwordRegistry(),
        modelBundleCache.getHunspellRegistry(), modelBundleCache.getWordNetRegistry(),
        modelBundleCache.getLatticeRegistry(), modelBundleCache.getTokenizerRegistry(),
        modelBundleCache.getSentenceDetectorRegistry());
    this.classicSteps = new ClassicStepRunner(modelBundleCache);
    this.embedChunkSteps = new EmbedChunkStepRunner(embeddingProvider, classicSteps);
  }

  /** {@inheritDoc} */
  @Override
  public AnalyzeDocumentResponse analyze(AnalyzeDocumentRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request must not be null");
    }
    final String rawText = requiredRawText(request);
    final PreparedAnalysis prepared = prepare(request);
    validator.validateDocument(request, rawText);
    return analyzePrepared(request, prepared, rawText);
  }

  /** {@inheritDoc} */
  @Override
  public DocumentAnalysisSession openSession(AnalyzeStreamConfiguration configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    final AnalyzeDocumentRequest.Builder fixed = AnalyzeDocumentRequest.newBuilder();
    if (configuration.hasProfile()) {
      fixed.setProfile(configuration.getProfile());
    }
    if (configuration.hasOptions()) {
      fixed.setOptions(configuration.getOptions());
    }
    if (configuration.hasProfileId()) {
      fixed.setProfileId(configuration.getProfileId());
    }
    fixed.addAllChunkEmbedConfigs(configuration.getChunkEmbedConfigsList());
    fixed.addAllCategoryChunkConfigs(configuration.getCategoryChunkConfigsList());
    final AnalyzeDocumentRequest template = fixed.build();
    final PreparedAnalysis prepared = prepare(template);
    return document -> {
      if (document == null) {
        throw new IllegalArgumentException("document must not be null");
      }
      final AnalyzeDocumentRequest request = template.toBuilder().setDocument(document).build();
      final String rawText = requiredRawText(request);
      validator.validateDocument(request, rawText);
      return analyzePrepared(request, prepared, rawText);
    };
  }

  private AnalyzeDocumentResponse analyzePrepared(
      AnalyzeDocumentRequest request, PreparedAnalysis prepared, String rawText) {
    final OpenNlpDocument input = request.getDocument();
    final AnalysisProfile profile = prepared.profile();
    final Set<PipelineStep> effectiveSteps = prepared.effectiveSteps();

    final boolean includeProbabilities =
        request.hasOptions() && request.getOptions().getIncludeProbabilities();

    final List<ProcessingDiagnostic> diagnostics = new ArrayList<>();
    // Layers produced directly by steps whose results live only in the document shape
    // (no classic response field), appended by the shape assembler after the built-ins.
    final List<AnnotationLayer> extraLayers = new ArrayList<>();
    final OpenNlpDocument.Builder document = OpenNlpDocument.newBuilder()
        .setDocId(input.getDocId())
        .setRawText(rawText);
    if (input.hasMetadata()) {
      document.setMetadata(input.getMetadata());
    }

    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT)) {
      runStep(
          PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT,
          () -> classicSteps.detectLanguage(rawText, document, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT));
    }

    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_NORMALIZE)) {
      runStep(
          PipelineStep.PIPELINE_STEP_NORMALIZE,
          () -> ClassicStepRunner.normalize(
              rawText, profile.getNormalization(), document, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_NORMALIZE));
    }

    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)) {
      final var sentenceDetector = validator.resolveSentenceDetector(profile);
      runStep(
          PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
          () -> {
            if (sentenceDetector.custom() != null) {
              ClassicStepRunner.detectSentences(rawText, document, sentenceDetector.custom(),
                  "custom:" + sentenceDetector.customId(), diagnostics);
            } else if (sentenceDetector.standard()
                == StandardSentenceDetectorEngine.STANDARD_SENTENCE_DETECTOR_ENGINE_NEWLINE) {
              ClassicStepRunner.detectSentences(rawText, document, NEWLINE_SENTENCE_DETECTOR,
                  "newline", diagnostics);
            } else {
              classicSteps.detectSentences(
                  rawText, document, includeProbabilities, diagnostics);
            }
          });
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT));
    }

    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_TOKENIZE)) {
      requireSentences(document, PipelineStep.PIPELINE_STEP_TOKENIZE);
      final var tokenizer = validator.resolveTokenizer(profile);
      final String latticeDictionaryId = validator.resolveLatticeDictionaryId(profile);
      runStep(
          PipelineStep.PIPELINE_STEP_TOKENIZE,
          () -> {
            if (tokenizer.custom() != null) {
              ClassicStepRunner.tokenize(rawText, document, tokenizer.custom(),
                  "custom:" + tokenizer.customId(), diagnostics);
            } else if (tokenizer.standard()
                == StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_UAX29) {
              ClassicStepRunner.tokenizeUax29(rawText, document, diagnostics);
            } else if (tokenizer.standard()
                == StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_WHITESPACE) {
              ClassicStepRunner.tokenize(rawText, document, WhitespaceTokenizer.INSTANCE,
                  "whitespace", diagnostics);
            } else if (tokenizer.standard()
                == StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_SIMPLE) {
              ClassicStepRunner.tokenize(rawText, document, SimpleTokenizer.INSTANCE,
                  "simple", diagnostics);
            } else if (tokenizer.standard()
                == StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_LATTICE) {
              classicSteps.tokenizeLattice(rawText, document, latticeDictionaryId, diagnostics);
            } else {
              classicSteps.tokenize(rawText, document, includeProbabilities, diagnostics);
            }
            if (!profile.getTermDimensionsList().isEmpty()) {
              ClassicStepRunner.computeTermLayers(
                  document, profile.getTermDimensionsList(), diagnostics);
            }
            if (profile.hasStopwordLanguage()) {
              ClassicStepRunner.markStopwords(
                  document, profile.getStopwordLanguage(), diagnostics);
            }
            if (profile.hasTermProfile()) {
              ClassicStepRunner.computeProfileTermLayers(
                  document, profile.getTermProfile(), diagnostics);
            }
          });
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_TOKENIZE));
    }

    final String subwordModelId = validator.resolveSubwordModelId(profile);
    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_SUBWORD_TOKENIZE)) {
      runStep(
          PipelineStep.PIPELINE_STEP_SUBWORD_TOKENIZE,
          () -> classicSteps.subwordTokenize(rawText, subwordModelId, extraLayers, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_SUBWORD_TOKENIZE));
    }

    final List<String> nerEntityTypes = validator.resolveNerEntityTypes(profile);
    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_NER)) {
      requireTokens(document, PipelineStep.PIPELINE_STEP_NER);
      runStep(
          PipelineStep.PIPELINE_STEP_NER,
          () -> classicSteps.findNamedEntities(
              document, nerEntityTypes, profile.getNerEnginePolicy(), includeProbabilities,
              diagnostics));
      if (shouldClearAdaptiveData(request)) {
        nameFinderRegistry.clearAdaptiveData();
      }
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_NER));
    }

    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_GEOCODE)) {
      if (!shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_NER)) {
        throw AnalysisException.failedPrecondition(
            PipelineStep.PIPELINE_STEP_GEOCODE.name()
                + " requires "
                + PipelineStep.PIPELINE_STEP_NER.name());
      }
      runStep(
          PipelineStep.PIPELINE_STEP_GEOCODE,
          () -> classicSteps.geocode(document, extraLayers, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_GEOCODE));
    }

    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_POS_TAG)) {
      requireTokens(document, PipelineStep.PIPELINE_STEP_POS_TAG);
      runStep(
          PipelineStep.PIPELINE_STEP_POS_TAG,
          () -> classicSteps.tagPartsOfSpeech(
              document, profile.getPosTagFormat(), includeProbabilities, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_POS_TAG));
    }

    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_LEMMATIZE)) {
      requireTokens(document, PipelineStep.PIPELINE_STEP_LEMMATIZE);
      if (!shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_POS_TAG)) {
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

    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_STEM)) {
      if (!shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_TOKENIZE)) {
        throw AnalysisException.failedPrecondition(
            PipelineStep.PIPELINE_STEP_STEM.name()
                + " requires "
                + PipelineStep.PIPELINE_STEP_TOKENIZE.name());
      }
      requireTokens(document, PipelineStep.PIPELINE_STEP_STEM);
      runStep(
          PipelineStep.PIPELINE_STEP_STEM,
          () -> classicSteps.stem(document, profile.getStemmer(), extraLayers, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_STEM));
    }

    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_TERM_VECTOR)) {
      requireTokens(document, PipelineStep.PIPELINE_STEP_TERM_VECTOR);
      runStep(
          PipelineStep.PIPELINE_STEP_TERM_VECTOR,
          () -> {
            final AnnotationLayer layer = TermVectorStepRunner.aggregate(
                rawText, document, extraLayers, profile.getTermVector());
            extraLayers.add(layer);
            diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_TERM_VECTOR,
                "Aggregated " + layer.getTermVectorValues().getAnnotationsCount()
                    + " distinct term(s)"));
          });
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_TERM_VECTOR));
    }

    final String wordNetLexiconId = validator.resolveWordNetLexiconId(profile);
    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_EXPAND)) {
      if (!shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_TOKENIZE)) {
        throw AnalysisException.failedPrecondition(
            PipelineStep.PIPELINE_STEP_EXPAND.name()
                + " requires "
                + PipelineStep.PIPELINE_STEP_TOKENIZE.name());
      }
      requireTokens(document, PipelineStep.PIPELINE_STEP_EXPAND);
      runStep(
          PipelineStep.PIPELINE_STEP_EXPAND,
          () -> classicSteps.expand(document, wordNetLexiconId, extraLayers, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_EXPAND));
    }

    final String docCategorizerModelId = validator.resolveDocCategorizerModelId(profile);
    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE)) {
      // Only classic (token-based) categorizers need tokenization; raw-text models (ONNX) can
      // classify the document text directly, so a DOC_CATEGORIZE-only profile is valid for them.
      if (validator.docCategorizerRequiresTokens(docCategorizerModelId)) {
        requireTokens(document, PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE);
      }
      runStep(
          PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE,
          () -> classicSteps.categorizeDocument(
              rawText, document, docCategorizerModelId, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE));
    }

    final String sentimentModelId = validator.resolveSentimentModelId(profile);
    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_SENTIMENT)) {
      // Sentiment is per sentence, so it always needs sentences; only classic (token-based)
      // models additionally need tokenization, while raw-text models score the sentence text.
      requireSentences(document, PipelineStep.PIPELINE_STEP_SENTIMENT);
      if (validator.sentimentRequiresTokens(sentimentModelId)) {
        requireTokens(document, PipelineStep.PIPELINE_STEP_SENTIMENT);
      }
      runStep(
          PipelineStep.PIPELINE_STEP_SENTIMENT,
          () -> classicSteps.analyzeSentiment(
              rawText, document, sentimentModelId, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_SENTIMENT));
    }

    final Set<ParseFormat> parseFormats = validator.resolveParseFormats(request, profile);
    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_PARSE)) {
      requireTokens(document, PipelineStep.PIPELINE_STEP_PARSE);
      runStep(
          PipelineStep.PIPELINE_STEP_PARSE,
          () -> classicSteps.parse(document, parseFormats, profile.getParseEnginePolicy(),
              includeProbabilities, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_PARSE));
    }

    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK)) {
      // The validator already requires POS_TAG in the profile; tokens (and thus POS tags) are
      // present by the time this runs.
      requireTokens(document, PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK);
      runStep(
          PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK,
          () -> classicSteps.chunkSyntactic(
              document, profile.getChunkEnginePolicy(), diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK));
    }

    final String embeddingModelId = validator.resolveEmbeddingModelId(request, profile);
    final String embeddingBackendId = validator.resolveEmbeddingBackendId(request);
    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_EMBED)) {
      requireSentences(document, PipelineStep.PIPELINE_STEP_EMBED);
      runStep(
          PipelineStep.PIPELINE_STEP_EMBED,
          () -> embedChunkSteps.embedSentences(
              rawText, document, embeddingModelId, embeddingBackendId,
              request.getOptions().getIncludeDocumentCentroid(), diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_EMBED));
    }

    if (request.getChunkEmbedConfigsCount() > 0) {
      runStep(
          PipelineStep.PIPELINE_STEP_CHUNK,
          () -> embedChunkSteps.runChunkEmbedConfigs(
              rawText, document, request, profile, includeProbabilities, diagnostics));
    } else if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_CHUNK)) {
      runStep(
          PipelineStep.PIPELINE_STEP_CHUNK,
          () -> embedChunkSteps.runProfileChunking(rawText, document, diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_CHUNK));
    }

    // Category-driven chunking is an independent request-side output (it groups by the sentiment
    // labels the SENTIMENT step already attached), so it runs after the strategy chunking above.
    if (request.getCategoryChunkConfigsCount() > 0) {
      runStep(
          PipelineStep.PIPELINE_STEP_CHUNK,
          () -> embedChunkSteps.runCategoryChunkConfigs(rawText, document, request, diagnostics));
    }

    final OffsetEncoding requestedEncoding = request.hasOptions()
        ? request.getOptions().getOffsetEncoding()
        : OffsetEncoding.OFFSET_ENCODING_UNSPECIFIED;

    final DocumentAnalytics analytics = DocumentAnalyticsComputer.compute(document.build());
    if (analytics != null) {
      document.setAnalytics(analytics);
    }

    // The document-shape rendering runs over UTF-16 spans; the offset encoder below
    // remaps layer spans together with every other span.
    DocumentShapeAssembler.apply(document, rawText, extraLayers);
    DocumentLayersValidator.validate(document.build(), embeddingProvider);

    DocumentOffsetEncoder.apply(document, rawText, requestedEncoding);

    return AnalyzeDocumentResponse.newBuilder()
        .setDocument(document.build())
        .addAllDiagnostics(diagnostics)
        .build();
  }

  private static String requiredRawText(AnalyzeDocumentRequest request) {
    if (!request.hasDocument()) {
      throw AnalysisException.invalidArgument("document is required");
    }
    final String rawText = request.getDocument().getRawText();
    if (rawText == null || rawText.isBlank()) {
      throw AnalysisException.invalidArgument("document.raw_text is required");
    }
    return rawText;
  }

  private PreparedAnalysis prepare(AnalyzeDocumentRequest request) {
    final AnalysisProfile profile = profileResolver.resolve(request);
    validator.validateConfiguration(request, profile);
    return new PreparedAnalysis(profile, Set.copyOf(resolveEffectiveSteps(request, profile)));
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
        if (entry.hasChunking()
            && ChunkingStrategies.TOKEN.equals(
                ChunkingStrategies.selectedId(entry.getChunking()))) {
          steps.add(PipelineStep.PIPELINE_STEP_TOKENIZE);
        }
      }
    }
    if (PipelineStepPolicy.shouldRun(profile, PipelineStep.PIPELINE_STEP_CHUNK)
        && request.getChunkEmbedConfigsCount() == 0) {
      steps.add(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT);
    }
    if (request.getCategoryChunkConfigsCount() > 0) {
      // Category grouping needs sentences and their sentiment labels (validated to be in profile).
      steps.add(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT);
    }
    return steps;
  }

  private static boolean shouldRunStep(Set<PipelineStep> effectiveSteps, PipelineStep step) {
    return effectiveSteps.contains(step);
  }

  private record PreparedAnalysis(
      AnalysisProfile profile, Set<PipelineStep> effectiveSteps) {
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
