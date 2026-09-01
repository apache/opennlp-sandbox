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
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import opennlp.tools.sentdetect.NewlineSentenceDetector;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import org.apache.opennlp.grpc.chunk.ChunkingStrategies;
import org.apache.opennlp.grpc.model.ClassicLanguagePipeline;
import org.apache.opennlp.grpc.model.DependencyParserRegistry;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.model.NameFinderRegistry;
import org.apache.opennlp.grpc.processor.DocumentAnalysisSession;
import org.apache.opennlp.grpc.processor.PipelineStepPolicy;
import org.apache.opennlp.grpc.processor.ProgressiveAnalysisListener;
import org.apache.opennlp.grpc.processor.ProgressiveDocumentAnalyzer;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.profile.ProfileResolver;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
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
public class BasicDocumentAnalyzer implements ProgressiveDocumentAnalyzer {

  private static final NewlineSentenceDetector NEWLINE_SENTENCE_DETECTOR =
      new NewlineSentenceDetector();

  private final ProfileResolver profileResolver;
  private final AnalysisRequestValidator validator;
  private final ClassicStepRunner classicSteps;
  private final EmbedChunkStepRunner embedChunkSteps;
  private final NameFinderRegistry nameFinderRegistry;
  private final EmbeddingProvider embeddingProvider;
  private final DependencyParserRegistry dependencyParserRegistry;
  private final ModelBundleCache modelBundleCache;
  private final boolean ownsModelBundleCache;
  private final AtomicBoolean closed = new AtomicBoolean();

  /**
   * Creates an analyzer backed by a fresh {@link ModelBundleCache} built from the given
   * configuration. The default profile registry is derived from the model capabilities
   * the cache discovers. Closing the analyzer closes this cache.
   *
   * @param configuration The model-loading configuration passed through to
   *                      {@link ModelBundleCache}. Must not be {@code null}.
   * @throws IllegalArgumentException If {@code configuration} is {@code null}.
   */
  public BasicDocumentAnalyzer(Map<String, String> configuration) {
    this(createOwnedModelBundleCache(configuration), true);
  }

  /** Creates an analyzer and records whether it owns the supplied cache. */
  private BasicDocumentAnalyzer(ModelBundleCache modelBundleCache, boolean ownsModelBundleCache) {
    this(modelBundleCache.createProfileRegistry(), modelBundleCache,
        modelBundleCache.getEmbeddingProvider(), ownsModelBundleCache);
  }

  /**
   * Creates an analyzer with an explicit profile registry and model cache, using the
   * embedding provider exposed by the cache. The caller retains ownership of the cache.
   *
   * @param profileRegistry  The profile registry resolving requested profiles. Must not
   *                        be {@code null}.
   * @param modelBundleCache The cache supplying loaded models and registries. Must not be
   *                        {@code null}.
   * @throws IllegalArgumentException If either argument is {@code null}.
   */
  public BasicDocumentAnalyzer(ProfileRegistry profileRegistry, ModelBundleCache modelBundleCache) {
    this(profileRegistry, modelBundleCache, embeddingProvider(modelBundleCache), false);
  }

  /**
   * Creates an analyzer with an explicit profile registry, model cache, and embedding
   * provider. The caller retains ownership of both injected resources.
   *
   * @param profileRegistry   The profile registry resolving requested profiles. Must not
   *                         be {@code null}.
   * @param modelBundleCache  The cache supplying loaded models and registries. Must not be
   *                         {@code null}.
   * @param embeddingProvider The provider used for embedding and semantic-chunk steps.
   *                         Must not be {@code null}.
   * @throws IllegalArgumentException If any argument is {@code null}.
   */
  public BasicDocumentAnalyzer(
      ProfileRegistry profileRegistry,
      ModelBundleCache modelBundleCache,
      EmbeddingProvider embeddingProvider) {
    this(profileRegistry, modelBundleCache, embeddingProvider, false);
  }

  /** Initializes the analyzer and its cache-ownership policy. */
  private BasicDocumentAnalyzer(
      ProfileRegistry profileRegistry,
      ModelBundleCache modelBundleCache,
      EmbeddingProvider embeddingProvider,
      boolean ownsModelBundleCache) {
    if (profileRegistry == null) {
      throw new IllegalArgumentException("profileRegistry must not be null");
    }
    if (modelBundleCache == null) {
      throw new IllegalArgumentException("modelBundleCache must not be null");
    }
    if (embeddingProvider == null) {
      throw new IllegalArgumentException("embeddingProvider must not be null");
    }
    this.profileResolver = new ProfileResolver(profileRegistry);
    this.modelBundleCache = modelBundleCache;
    this.ownsModelBundleCache = ownsModelBundleCache;
    this.nameFinderRegistry = modelBundleCache.getNameFinderRegistry();
    this.embeddingProvider = embeddingProvider;
    this.dependencyParserRegistry = modelBundleCache.getDependencyParserRegistry();
    this.validator = new AnalysisRequestValidator(embeddingProvider, nameFinderRegistry,
        modelBundleCache.getDocCategorizerRegistry(), modelBundleCache.getSentimentRegistry(),
        modelBundleCache.getParserRegistry(), modelBundleCache.getChunkerRegistry(),
        modelBundleCache.getArtifactRegistry(), modelBundleCache.getSubwordRegistry(),
        modelBundleCache.getDependencyParserRegistry(),
        modelBundleCache.getHunspellRegistry(), modelBundleCache.getWordNetRegistry(),
        modelBundleCache.getLatticeRegistry(), modelBundleCache.getTokenizerRegistry(),
        modelBundleCache.getSentenceDetectorRegistry());
    this.classicSteps = new ClassicStepRunner(modelBundleCache);
    this.embedChunkSteps = new EmbedChunkStepRunner(embeddingProvider, classicSteps);
  }

  /** Creates the analyzer-owned cache after validating the public constructor input. */
  private static ModelBundleCache createOwnedModelBundleCache(Map<String, String> configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    return new ModelBundleCache(configuration);
  }

  /** Returns the cache's embedding provider after validating the public constructor input. */
  private static EmbeddingProvider embeddingProvider(ModelBundleCache modelBundleCache) {
    if (modelBundleCache == null) {
      throw new IllegalArgumentException("modelBundleCache must not be null");
    }
    return modelBundleCache.getEmbeddingProvider();
  }

  /** {@inheritDoc} */
  @Override
  public AnalyzeDocumentResponse analyze(AnalyzeDocumentRequest request) {
    ensureOpen();
    if (request == null) {
      throw new IllegalArgumentException("request must not be null");
    }
    final String rawText = requiredRawText(request);
    final PreparedAnalysis prepared = prepare(request);
    validator.validateDocument(request, rawText);
    return analyzeWithCleanup(request, prepared, rawText);
  }

  /** {@inheritDoc} */
  @Override
  public void analyzeProgressively(
      AnalyzeDocumentRequest request,
      Executor branchExecutor,
      ProgressiveAnalysisListener listener) {
    ensureOpen();
    if (request == null) {
      throw new IllegalArgumentException("request must not be null");
    }
    if (branchExecutor == null) {
      throw new IllegalArgumentException("branchExecutor must not be null");
    }
    if (listener == null) {
      throw new IllegalArgumentException("listener must not be null");
    }
    final String rawText = requiredRawText(request);
    final PreparedAnalysis prepared = prepare(request);
    validator.validateDocument(request, rawText);
    ProgressiveAnalysisCoordinator.start(
        request,
        prepared.effectiveSteps(),
        branchExecutor,
        embeddingProvider,
        listener,
        (branchRequest, steps, backbone) -> analyzeWithCleanup(
            branchRequest,
            new PreparedAnalysis(prepared.profile(), Set.copyOf(steps)),
            rawText,
            backbone));
  }

  /** {@inheritDoc} */
  @Override
  public DocumentAnalysisSession openSession(AnalyzeStreamConfiguration configuration) {
    ensureOpen();
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
      ensureOpen();
      if (document == null) {
        throw new IllegalArgumentException("document must not be null");
      }
      final AnalyzeDocumentRequest request = template.toBuilder().setDocument(document).build();
      final String rawText = requiredRawText(request);
      validator.validateDocument(request, rawText);
      return analyzeWithCleanup(request, prepared, rawText);
    };
  }

  /** Releases resources created by the configuration constructor. */
  @Override
  public void close() {
    if (closed.compareAndSet(false, true) && ownsModelBundleCache) {
      modelBundleCache.close();
    }
  }

  /** Rejects work after the analyzer has been closed. */
  private void ensureOpen() {
    if (closed.get()) {
      throw new IllegalStateException(
          "BasicDocumentAnalyzer is closed and cannot analyze documents");
    }
  }

  /** Runs prepared analysis and releases decoder state owned by the calling worker. */
  private AnalyzeDocumentResponse analyzeWithCleanup(
      AnalyzeDocumentRequest request, PreparedAnalysis prepared, String rawText) {
    return analyzeWithCleanup(request, prepared, rawText, null);
  }

  /** Runs prepared analysis from an optional backbone and releases worker decoder state. */
  private AnalyzeDocumentResponse analyzeWithCleanup(
      AnalyzeDocumentRequest request,
      PreparedAnalysis prepared,
      String rawText,
      OpenNlpDocument backbone) {
    try {
      return analyzePrepared(request, prepared, rawText, backbone);
    } finally {
      modelBundleCache.clearThreadLocalState();
    }
  }

  /** Runs a validated, prepared analysis. */
  private AnalyzeDocumentResponse analyzePrepared(
      AnalyzeDocumentRequest request,
      PreparedAnalysis prepared,
      String rawText,
      OpenNlpDocument backbone) {
    final OpenNlpDocument input = request.getDocument();
    final AnalysisProfile profile = prepared.profile();
    final Set<PipelineStep> effectiveSteps = prepared.effectiveSteps();

    final boolean includeProbabilities =
        request.hasOptions() && request.getOptions().getIncludeProbabilities();

    final List<ProcessingDiagnostic> diagnostics = new ArrayList<>();
    // Layers produced directly by steps whose results live only in the document shape
    // (no classic response field), appended by the shape assembler after the built-ins.
    final List<AnnotationLayer> extraLayers = new ArrayList<>();
    final OpenNlpDocument.Builder document;
    if (backbone == null) {
      document = OpenNlpDocument.newBuilder()
          .setDocId(input.getDocId())
          .setRawText(rawText);
      if (input.hasMetadata()) {
        document.setMetadata(input.getMetadata());
      }
    } else {
      document = backbone.toBuilder()
          .clearLayers()
          .clearAnalytics();
    }

    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT)) {
      final int rankedLanguageCount =
          request.hasOptions() ? request.getOptions().getRankedLanguageCount() : 0;
      runStep(
          PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT,
          () -> classicSteps.detectLanguage(rawText, document, rankedLanguageCount, diagnostics));
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

    // The classic pipeline is fixed once per document: an explicit pipeline_language
    // wins, then a configured pipeline matching the detected language, then the default.
    final ClassicLanguagePipeline classicPipeline =
        resolveClassicPipeline(profile, document, diagnostics);

    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)) {
      final var sentenceDetector = validator.resolveSentenceDetector(profile);
      runStep(
          PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
          () -> {
            if (sentenceDetector.custom() != null) {
              classicSteps.detectSentences(rawText, document, sentenceDetector.custom(),
                  "custom:" + sentenceDetector.customId(), diagnostics);
            } else if (sentenceDetector.standard()
                == StandardSentenceDetectorEngine.STANDARD_SENTENCE_DETECTOR_ENGINE_NEWLINE) {
              classicSteps.detectSentences(rawText, document, NEWLINE_SENTENCE_DETECTOR,
                  "newline", diagnostics);
            } else {
              classicSteps.detectSentences(
                  classicPipeline, rawText, document, includeProbabilities, diagnostics);
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
              classicSteps.tokenize(rawText, document, tokenizer.custom(),
                  "custom:" + tokenizer.customId(), diagnostics);
            } else if (tokenizer.standard()
                == StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_UAX29) {
              ClassicStepRunner.tokenizeUax29(rawText, document, diagnostics);
            } else if (tokenizer.standard()
                == StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_WHITESPACE) {
              classicSteps.tokenize(rawText, document, WhitespaceTokenizer.INSTANCE,
                  "whitespace", diagnostics);
            } else if (tokenizer.standard()
                == StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_SIMPLE) {
              classicSteps.tokenize(rawText, document, SimpleTokenizer.INSTANCE,
                  "simple", diagnostics);
            } else if (tokenizer.standard()
                == StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_LATTICE) {
              classicSteps.tokenizeLattice(rawText, document, latticeDictionaryId, diagnostics);
            } else {
              classicSteps.tokenize(
                  classicPipeline, rawText, document, includeProbabilities, diagnostics);
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
            if (profile.getTermLayersCount() > 0) {
              classicSteps.computeConfiguredTermLayers(
                  document, profile.getTermLayersList(), diagnostics);
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
      try {
        runStep(
            PipelineStep.PIPELINE_STEP_NER,
            () -> classicSteps.findNamedEntities(
                document, nerEntityTypes, profile.getNerEnginePolicy(), includeProbabilities,
                diagnostics));
      } finally {
        // Clear adaptive data on failure too, or a finder that accumulated context before
        // throwing leaks it into the next document on this thread.
        if (shouldClearAdaptiveData(request)) {
          nameFinderRegistry.clearAdaptiveData();
        }
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
              classicPipeline, document, profile.getPosTagFormat(), includeProbabilities,
              diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_POS_TAG));
    }

    final String dependencyParserId = validator.resolveDependencyParserId(profile);
    LinguisticGraphRenderer.DependencyResult dependencyResult = null;
    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE)) {
      requireTokens(document, PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE);
      dependencyResult = runStep(
          PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE,
          () -> {
            final LinguisticGraphRenderer.DependencyResult result =
                LinguisticGraphRenderer.parse(
                    document.build(), dependencyParserRegistry.get(dependencyParserId),
                    dependencyParserId);
            extraLayers.add(result.layer());
            diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE,
                "Parsed " + result.layer().getDependencyValues().getAnnotationsCount()
                    + " dependency arc(s)"));
            return result;
          });
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE));
    }

    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_RELATION_EXTRACT)) {
      final LinguisticGraphRenderer.DependencyResult parsed = dependencyResult;
      if (parsed == null) {
        throw AnalysisException.failedPrecondition(
            PipelineStep.PIPELINE_STEP_RELATION_EXTRACT.name() + " requires "
                + PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE.name());
      }
      runStep(
          PipelineStep.PIPELINE_STEP_RELATION_EXTRACT,
          () -> {
            final AnnotationLayer layer = LinguisticGraphRenderer.relations(
                parsed.document(), profile.getRelationPatternsList());
            extraLayers.add(layer);
            diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_RELATION_EXTRACT,
                "Extracted " + layer.getRelationValues().getAnnotationsCount()
                    + " relation(s)"));
          });
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_RELATION_EXTRACT));
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
          () -> classicSteps.lemmatize(
              classicPipeline, document, profile.getPosTagFormat(), diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_LEMMATIZE));
    }

    if (shouldRunStep(effectiveSteps, PipelineStep.PIPELINE_STEP_STEM)) {
      // The profile-level dependency on TOKENIZE is validated once per request; here the
      // tokens may come from a progressive backbone, so only their presence is checked.
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
              request.getOptions().getIncludeDocumentCentroid(),
              request.getOptions().getDocumentCentroidNormalization(), diagnostics));
    } else {
      diagnostics.add(StepDiagnostics.skipped(PipelineStep.PIPELINE_STEP_EMBED));
    }

    if (request.getChunkEmbedConfigsCount() > 0) {
      runStep(
          PipelineStep.PIPELINE_STEP_CHUNK,
          () -> embedChunkSteps.runChunkEmbedConfigs(
              classicPipeline,
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

  /** Returns the required document text. */
  private static String requiredRawText(AnalyzeDocumentRequest request) {
    if (!request.hasDocument()) {
      throw AnalysisException.invalidArgument("document is required");
    }
    final String rawText = request.getDocument().getRawText();
    if (rawText.isEmpty()) {
      throw AnalysisException.invalidArgument("document.raw_text is required");
    }
    if (rawText.isBlank()) {
      throw AnalysisException.invalidArgument(
          "document.raw_text must contain non-whitespace characters");
    }
    return rawText;
  }

  /** Resolves and validates the fixed analysis configuration. */
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

  /** Returns whether the effective profile includes a step. */
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

  /** Wraps unexpected step failures and returns the step result. */
  private static <T> T runStep(PipelineStep step, StepSupplier<T> action) {
    try {
      return action.run();
    } catch (AnalysisException e) {
      throw e;
    } catch (RuntimeException e) {
      throw AnalysisException.internal(step.name() + " failed", e);
    }
  }

  /** Returns detected sentences or raises the requesting step's failed precondition. */
  private static void requireSentences(OpenNlpDocument.Builder document, PipelineStep step) {
    if (document.getSentencesCount() == 0) {
      throw AnalysisException.failedPrecondition(
          step.name() + " requires " + PipelineStep.PIPELINE_STEP_SENTENCE_DETECT.name());
    }
  }

  /** Returns tokenized sentences or raises the requesting step's failed precondition. */
  private static void requireTokens(OpenNlpDocument.Builder document, PipelineStep step) {
    boolean tokenized = document.getSentencesCount() > 0;
    for (AnnotatedSentence sentence : document.getSentencesList()) {
      tokenized = tokenized && sentence.getTokensCount() > 0;
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

  @FunctionalInterface
  private interface StepSupplier<T> {
    T run();
  }

  /**
   * Resolves the classic pipeline serving this document: the profile's explicit
   * {@code pipeline_language} when set, otherwise the configured pipeline matching the
   * detected language, otherwise the default models.
   *
   * @throws AnalysisException {@code NOT_FOUND} when an explicit language names no
   *     configured pipeline.
   */
  private ClassicLanguagePipeline resolveClassicPipeline(
      AnalysisProfile profile,
      OpenNlpDocument.Builder document,
      List<ProcessingDiagnostic> diagnostics) {
    if (profile.hasPipelineLanguage()) {
      final ClassicLanguagePipeline pipeline =
          modelBundleCache.pipelineFor(profile.getPipelineLanguage());
      if (pipeline == null) {
        final List<String> configured = modelBundleCache.pipelineLanguages();
        throw AnalysisException.notFound("pipeline_language '"
            + profile.getPipelineLanguage() + "' has no configured classic pipeline; "
            + (configured.isEmpty() ? "no model.pipeline.<lang> sets are configured"
                : "configured: " + String.join(", ", configured)));
      }
      diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
          "Classic pipeline '" + pipeline.language() + "' selected by pipeline_language"));
      return pipeline;
    }
    if (document.hasDetectedLanguage()) {
      final ClassicLanguagePipeline pipeline =
          modelBundleCache.pipelineFor(document.getDetectedLanguage());
      if (pipeline != null) {
        diagnostics.add(StepDiagnostics.info(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
            "Classic pipeline '" + pipeline.language() + "' routed by detected language '"
                + document.getDetectedLanguage() + "'"));
        return pipeline;
      }
    }
    return modelBundleCache.defaultPipeline();
  }
}
