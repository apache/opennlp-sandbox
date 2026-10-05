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
package org.apache.opennlp.grpc.model;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import opennlp.tools.lemmatizer.Lemmatizer;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagFormat;
import opennlp.tools.postag.POSTagFormatMapper;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.model.BaseModel;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.embedding.EmbeddingProviderFactory;
import org.apache.opennlp.grpc.training.TrainedModelEmbeddingProvider;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.ComponentType;
import org.apache.opennlp.grpc.v1.ConfiguredResource;
import org.apache.opennlp.grpc.v1.ModelBundleInfo;
import org.apache.opennlp.grpc.v1.ModelDescriptor;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.ResourceIdentity;
import org.apache.opennlp.grpc.v1.StandardResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.opennlp.grpc.spi.model.NerModel;
import org.apache.opennlp.grpc.spi.ModelArtifactHasher;
import org.apache.opennlp.grpc.spi.model.DocCategorizerModel;

/**
 * Loads shared thread-safe {@code *ME} singletons once at startup.
 *
 * <p>Models resolve in three steps: optional explicit paths in configuration, then
 * {@code model.properties} descriptors on the classpath from {@code opennlp-models-*}
 * runtime deps, and finally the model binaries bundled inside the shaded server jar itself.
 *
 * <p>The bundled fallback exists because classpath discovery matches model <em>jar file
 * names</em> ({@code opennlp-models-*.jar}) and therefore cannot see models that have been
 * merged into a single executable jar, which is how the server is distributed and run
 * ({@code java -jar opennlp-grpc-server-*.jar}).
 */
public final class ModelBundleCache implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(ModelBundleCache.class);

  private static final String DEFAULT_LANGUAGE = "en";
  private static final String SENTENCE_MODEL_NAME =
      "opennlp-models-sentdetect-" + DEFAULT_LANGUAGE;
  private static final String TOKENIZER_MODEL_NAME =
      "opennlp-models-tokenizer-" + DEFAULT_LANGUAGE;
  private static final String POS_MODEL_NAME = "opennlp-models-pos-" + DEFAULT_LANGUAGE;
  private static final String KEY_SENTDETECT_PATH = "model.sentence_detector.path";
  private static final String KEY_TOKENIZER_PATH = "model.tokenizer.path";
  private static final String KEY_POS_TAGGER_PATH = "model.pos_tagger.path";
  private static final String KEY_LEMMATIZER_PATH = "model.lemmatizer.path";
  private static final String KEY_LEMMATIZER_DICTIONARY = "model.lemmatizer.dictionary";
  private static final String DICTIONARY_LEMMATIZER_NAME = "lemmatizer-dictionary";
  private static final String KEY_LANGDETECT_PATH = "model.language_detector.path";
  /** Prefix of per-language classic pipeline model sets: {@code model.pipeline.<lang>.*}. */
  public static final String KEY_PIPELINE_PREFIX = "model.pipeline.";
  private static final String PIPELINE_SENTDETECT_SUFFIX = ".sentence_detector.path";
  private static final String PIPELINE_TOKENIZER_SUFFIX = ".tokenizer.path";
  private static final String PIPELINE_POS_SUFFIX = ".pos_tagger.path";
  private static final String PIPELINE_LEMMATIZER_SUFFIX = ".lemmatizer.path";
  private static final String PIPELINE_BUNDLE_PREFIX = "pipeline-";

  /** Backend id reported for models served by the classic OpenNLP maxent runtime. */
  private static final String OPENNLP_ME_BACKEND_ID = "opennlp-me";

  /** Name fragments identifying the bundled UD model binaries at the jar root. */
  private static final String BUNDLED_SENTENCE_MODEL_FRAGMENT = "-sentence-";
  private static final String BUNDLED_TOKENIZER_MODEL_FRAGMENT = "-tokens-";
  private static final String BUNDLED_POS_MODEL_FRAGMENT = "-pos-";
  private static final String BUNDLED_LEMMATIZER_MODEL_FRAGMENT = "-lemmas-";
  private static final String BUNDLED_LANGDETECT_MODEL_FRAGMENT = "langdetect";
  private static final String MODEL_DESCRIPTOR_RESOURCE = "model.properties";
  private static final String MODEL_FILE_SUFFIX = ".bin";

  private final Map<String, ModelBundleInfo> bundles;
  private final ModelArtifactRegistry artifactRegistry;
  // The classic pipelines share their *Model artifacts and thread-safe *ME decoders.
  // Each decoder keeps caller-specific result state internally and releases it after
  // each document.
  private final ClassicLanguagePipeline defaultPipeline;
  // Configured model.pipeline.<lang> sets keyed by normalized language, plus an index
  // from each language's ISO 639-3 code (the language detector's alphabet) to that key.
  private final Map<String, ClassicLanguagePipeline> languagePipelines;
  private final Map<String, String> pipelineLanguageAliases;
  private final LanguageDetectorME languageDetector;
  private final EmbeddingProvider embeddingProvider;
  private final TrainedModelEmbeddingProvider trainedModelRegistry;
  private final NameFinderRegistry nameFinderRegistry;
  private final DocCategorizerRegistry docCategorizerRegistry;
  private final SentimentRegistry sentimentRegistry;
  // Optional constituency-parsing registry (operator-supplied via model.parser.<id>.path, not
  // bundled). Groups parsers by id into a RankedBackends so a parser can be served by several
  // engines; each classic parser holds its own per-thread Parser (OpenNLP's parser is not
  // thread-safe). Empty when no parser model is configured.
  private final ParserRegistry parserRegistry;
  // Optional shallow-chunking registry (operator-supplied via model.chunker.<id>.path, not bundled).
  // Groups chunkers by id into a RankedBackends so a chunker can be served by several engines;
  // empty when no chunker model is configured.
  private final ChunkerRegistry chunkerRegistry;
  // Optional subword tokenizers (operator-supplied via model.subword.<id>.path, not bundled).
  // The loaded SentencePiece tokenizers are thread-safe and shared; empty when none is configured.
  private final SubwordRegistry subwordRegistry;
  private final DependencyParserRegistry dependencyParserRegistry;
  // Optional hunspell affix dictionaries for the STEM step (operator-supplied via
  // model.hunspell.<id>.affix_path/.dictionary_path, not bundled). The loaded stemmers are
  // thread-safe and shared; empty when none is configured.
  private final HunspellRegistry hunspellRegistry;
  // Optional WordNet-style knowledge bases for the EXPAND step (operator-supplied via
  // model.wordnet.<id>.path, not bundled). The built expanders are thread-safe and shared;
  // empty when none is configured.
  private final WordNetRegistry wordNetRegistry;
  // Optional MeCab-format dictionaries for the "lattice" tokenizer engine (operator-supplied
  // via model.lattice.<id>.dir, not bundled). The built tokenizers read only immutable
  // dictionary state and are shared; empty when none is configured.
  private final LatticeRegistry latticeRegistry;
  // Custom tokenizer and sentence-detector engines contributed by ServiceLoader modules.
  private final TokenizerRegistry tokenizerRegistry;
  private final SentenceDetectorRegistry sentenceDetectorRegistry;

  /**
   * Eagerly loads every model and registry described by the given configuration. The classic
   * {@code *ME} components fall back to bundled defaults when no path is configured; the
   * embedding provider, name finder, document categorizer, and sentiment registries are built
   * from their configured backends and the optional parser model is loaded last. If any load
   * after the classic components fails, the resources already created are released so a failed
   * startup does not leak native sessions.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @throws AnalysisException If a configured model path is invalid or a model fails to load.
   */
  public ModelBundleCache(Map<String, String> configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    // Loaded first: these registries hold no native resources, so they can never leak.
    this.subwordRegistry = SubwordRegistry.create(configuration);
    this.dependencyParserRegistry = DependencyParserRegistry.create(configuration);
    this.hunspellRegistry = HunspellRegistry.create(configuration);
    this.wordNetRegistry = WordNetRegistry.create(configuration);
    this.latticeRegistry = LatticeRegistry.create(configuration);
    final LoadedArtifact<SentenceModel> loadedSentence = loadModel(configuration,
        KEY_SENTDETECT_PATH, BUNDLED_SENTENCE_MODEL_FRAGMENT, "sentence detector",
        SentenceModel::new);
    final LoadedArtifact<TokenizerModel> loadedTokenizer = loadModel(configuration,
        KEY_TOKENIZER_PATH, BUNDLED_TOKENIZER_MODEL_FRAGMENT, "tokenizer", TokenizerModel::new);
    final LoadedArtifact<POSModel> loadedPos = loadModel(configuration,
        KEY_POS_TAGGER_PATH, BUNDLED_POS_MODEL_FRAGMENT, "POS tagger", POSModel::new);
    final LoadedLemmatizer loadedLemmatizer = loadLemmatizer(configuration);
    final LoadedArtifact<LanguageDetectorModel> loadedLangDetect =
        loadLanguageDetectorModel(configuration);
    final POSModel defaultPosModel = loadedPos.model();
    this.defaultPipeline = new ClassicLanguagePipeline("",
        new SentenceDetectorME(loadedSentence.model()),
        new TokenizerME(loadedTokenizer.model()),
        defaultPosModel,
        new POSTaggerME(defaultPosModel),
        loadedLemmatizer.lemmatizer(),
        loadedLemmatizer.statistical());
    final Map<String, LoadedPipeline> loadedPipelines = loadLanguagePipelines(configuration);
    final Map<String, ClassicLanguagePipeline> pipelines = new TreeMap<>();
    final Map<String, String> aliases = new TreeMap<>();
    for (Map.Entry<String, LoadedPipeline> entry : loadedPipelines.entrySet()) {
      pipelines.put(entry.getKey(), entry.getValue().pipeline());
      aliases.put(entry.getKey(), entry.getKey());
      final String iso3 = iso3Code(entry.getKey());
      if (iso3 != null) {
        aliases.putIfAbsent(iso3, entry.getKey());
      }
    }
    this.languagePipelines = Map.copyOf(pipelines);
    this.pipelineLanguageAliases = Map.copyOf(aliases);
    this.languageDetector = new LanguageDetectorME(loadedLangDetect.model());
    // The embedding provider and the three registries may hold native resources (ONNX sessions,
    // remote connections). If a later load fails, release the ones already created so a failed
    // startup does not leak native sessions.
    EmbeddingProvider embeddingProvider = null;
    NameFinderRegistry nameFinderRegistry = null;
    DocCategorizerRegistry docCategorizerRegistry = null;
    SentimentRegistry sentimentRegistry = null;
    ChunkerRegistry chunkerRegistry = null;
    TokenizerRegistry tokenizerRegistry = null;
    SentenceDetectorRegistry sentenceDetectorRegistry = null;
    boolean constructed = false;
    try {
      tokenizerRegistry = TokenizerRegistry.create(configuration);
      sentenceDetectorRegistry = SentenceDetectorRegistry.create(configuration);
      // Trained models register into this wrapper at runtime, so every consumer of
      // getEmbeddingProvider() resolves them without a restart.
      embeddingProvider =
          new TrainedModelEmbeddingProvider(EmbeddingProviderFactory.create(configuration));
      nameFinderRegistry =
          NameFinderRegistry.create(configuration, defaultPipeline.sentenceDetector());
      docCategorizerRegistry = DocCategorizerRegistry.create(configuration);
      sentimentRegistry = SentimentRegistry.create(configuration);
      chunkerRegistry = ChunkerRegistry.create(configuration);
      this.embeddingProvider = embeddingProvider;
      this.trainedModelRegistry = (TrainedModelEmbeddingProvider) embeddingProvider;
      this.nameFinderRegistry = nameFinderRegistry;
      this.docCategorizerRegistry = docCategorizerRegistry;
      this.sentimentRegistry = sentimentRegistry;
      this.chunkerRegistry = chunkerRegistry;
      this.tokenizerRegistry = tokenizerRegistry;
      this.sentenceDetectorRegistry = sentenceDetectorRegistry;
      this.parserRegistry = ParserRegistry.create(configuration);
      this.bundles = withPipelineBundles(buildBundleCatalog(
          loadedLangDetect.hash(), loadedSentence.hash(), loadedTokenizer.hash(),
          loadedPos.hash(), loadedLemmatizer.hash(), loadedLemmatizer.name()),
          loadedPipelines);
      this.artifactRegistry = buildArtifactRegistry(
          loadedLangDetect.hash(), loadedSentence.hash(), loadedTokenizer.hash(),
          loadedPos.hash(), loadedLemmatizer.hash(), loadedLemmatizer.name());
      constructed = true;
    } finally {
      if (!constructed) {
        closeQuietly(sentenceDetectorRegistry);
        closeQuietly(tokenizerRegistry);
        closeQuietly(chunkerRegistry);
        closeQuietly(sentimentRegistry);
        closeQuietly(docCategorizerRegistry);
        closeQuietly(nameFinderRegistry);
        closeQuietly(embeddingProvider);
      }
    }
  }

  /**
   * Returns the shared sentence detector. Always available through the bundled default when
   * unconfigured.
   *
   * @return The sentence detector. Never {@code null}.
   */
  public SentenceDetectorME getSentenceDetector() {
    return defaultPipeline.sentenceDetector();
  }

  /**
   * Returns the shared tokenizer. Always available through the bundled default when unconfigured.
   *
   * @return The tokenizer. Never {@code null}.
   */
  public TokenizerME getTokenizer() {
    return defaultPipeline.tokenizer();
  }

  /**
   * Returns the shared POS tagger. Always available through the bundled default when unconfigured.
   *
   * @return The POS tagger. Never {@code null}.
   */
  public POSTaggerME getPosTagger() {
    return defaultPipeline.posTagger();
  }

  /**
   * Returns a POS tagger emitting tags in the requested output format.
   *
   * @param requestedFormat The client-requested tagset.
   *
   * @return A tagger configured for {@code requestedFormat}. Never {@code null}.
   *
   * @throws AnalysisException If {@code requestedFormat} is {@code CUSTOM}.
   */
  public POSTaggerME createPosTagger(org.apache.opennlp.grpc.v1.POSTagFormat requestedFormat) {
    return defaultPipeline.createPosTagger(requestedFormat);
  }

  /**
   * Reports whether tagging with the requested output format rewrites the model's native tags.
   * When it does, consumers trained on the native tagset (the lemmatizer) must not be fed the
   * converted {@code Token.pos_tag} values.
   *
   * @param requestedFormat The client-requested tagset.
   *
   * @return {@code true} when {@link #createPosTagger} with {@code requestedFormat} converts tags.
   */
  public boolean convertsPosTagFormat(org.apache.opennlp.grpc.v1.POSTagFormat requestedFormat) {
    return defaultPipeline.convertsPosTagFormat(requestedFormat);
  }

  /**
   * Returns the shared lemmatizer: the dictionary lemmatizer when
   * {@code model.lemmatizer.dictionary} is configured, otherwise the statistical decoder,
   * always available through the bundled default when unconfigured.
   *
   * @return The lemmatizer. Never {@code null}.
   */
  public Lemmatizer getLemmatizer() {
    return defaultPipeline.lemmatizer();
  }

  /**
   * Returns the shared language detector. Always available through the bundled default when
   * unconfigured.
   *
   * @return The language detector. Never {@code null}.
   */
  public LanguageDetectorME getLanguageDetector() {
    return languageDetector;
  }

  /**
   * Releases caller-specific decoder state after one document finishes on the current thread.
   */
  public void clearThreadLocalState() {
    defaultPipeline.clearThreadLocalState();
    for (ClassicLanguagePipeline pipeline : languagePipelines.values()) {
      pipeline.clearThreadLocalState();
    }
    nameFinderRegistry.clearThreadLocalState();
    chunkerRegistry.clearThreadLocalState();
    parserRegistry.clearThreadLocalState();
  }

  /**
   * Returns the catalog of loaded model bundles for capability reporting.
   *
   * @return A new list of the loaded bundle descriptors. Never {@code null}.
   */
  public List<ModelBundleInfo> listBundles() {
    return new ArrayList<>(bundles.values());
  }

  /**
   * Returns the loaded non-model resources that profiles can select by id.
   *
   * <p>Model artifacts and embedding routes remain in {@link #listBundles()}; this
   * catalog covers resource families whose runtime objects are not OpenNLP models.</p>
   *
   * @return A stable immutable list, grouped by standard resource type and then id.
   */
  public List<ConfiguredResource> listConfiguredResources() {
    final List<ConfiguredResource> resources = new ArrayList<>();
    addResources(resources, StandardResource.STANDARD_RESOURCE_SUBWORD_MODEL,
        subwordRegistry.ids(), subwordRegistry::isDefault);
    addResources(resources, StandardResource.STANDARD_RESOURCE_HUNSPELL_DICTIONARY,
        hunspellRegistry.ids(), hunspellRegistry::isDefault);
    addResources(resources, StandardResource.STANDARD_RESOURCE_WORDNET_LEXICON,
        wordNetRegistry.ids(), wordNetRegistry::isDefault);
    addResources(resources, StandardResource.STANDARD_RESOURCE_LATTICE_DICTIONARY,
        latticeRegistry.ids(), latticeRegistry::isDefault);
    return List.copyOf(resources);
  }

  /** Adds configured resources to the capability catalog. */
  private static void addResources(
      List<ConfiguredResource> resources,
      StandardResource type,
      List<String> ids,
      java.util.function.Predicate<String> isDefault) {
    for (String id : ids) {
      resources.add(ConfiguredResource.newBuilder()
          .setIdentity(ResourceIdentity.newBuilder().setStandard(type))
          .setResourceId(id)
          .setIsDefault(isDefault.test(id))
          .build());
    }
  }

  /**
   * Returns the registry of SHA-256 hashes for loaded model artifacts.
   *
   * @return The artifact registry. Never {@code null}.
   */
  public ModelArtifactRegistry getArtifactRegistry() {
    return artifactRegistry;
  }

  /**
   * Returns the configured embedding provider.
   *
   * @return The embedding provider; never {@code null}, though it may report no registered
   *     models when none is configured.
   */
  public EmbeddingProvider getEmbeddingProvider() {
    return embeddingProvider;
  }

  /**
   * Returns the registry that serves models trained at runtime in front of the
   * configured embedding provider.
   *
   * @return The trained model registry. Never {@code null}.
   */
  public TrainedModelEmbeddingProvider getTrainedModelRegistry() {
    return trainedModelRegistry;
  }

  /**
   * Creates the default analysis profile registry from the capabilities loaded in this cache.
   * Keeping this derivation beside the registries prevents embedded and executable-server
   * construction paths from advertising different profile catalogs for the same models.
   *
   * @return A new profile registry matching every loaded optional model family.
   */
  public ProfileRegistry createProfileRegistry() {
    return ProfileRegistry.createDefault(
        nameFinderRegistry.isAvailable(),
        docCategorizerRegistry.isAvailable(),
        sentimentRegistry.isAvailable(),
        isParserAvailable(),
        isChunkerAvailable(),
        embeddingProvider.isAvailable());
  }

  /**
   * Returns the registry of loaded name finders.
   *
   * @return The name finder registry; never {@code null}, possibly empty.
   */
  public NameFinderRegistry getNameFinderRegistry() {
    return nameFinderRegistry;
  }

  /**
   * Returns the registry of loaded document categorizers.
   *
   * @return The document categorizer registry; never {@code null}, possibly empty.
   */
  public DocCategorizerRegistry getDocCategorizerRegistry() {
    return docCategorizerRegistry;
  }

  /**
   * Returns the registry of loaded sentiment models.
   *
   * @return The sentiment registry; never {@code null}, possibly empty.
   */
  public SentimentRegistry getSentimentRegistry() {
    return sentimentRegistry;
  }

  /**
   * Returns the registry of configured subword tokenizers.
   *
   * @return The registry of configured subword tokenizers, possibly empty. Never
   *         {@code null}.
   */
  public SubwordRegistry getSubwordRegistry() {
    return subwordRegistry;
  }

  /**
   * Returns the registry of configured dependency parsers.
   *
   * @return The dependency parser registry, possibly empty. Never {@code null}.
   */
  public DependencyParserRegistry getDependencyParserRegistry() {
    return dependencyParserRegistry;
  }

  /**
   * Returns the registry of configured hunspell dictionaries.
   *
   * @return The registry of configured hunspell dictionaries, possibly empty. Never
   *         {@code null}.
   */
  public HunspellRegistry getHunspellRegistry() {
    return hunspellRegistry;
  }

  /**
   * Returns the registry of configured WordNet lexicons.
   *
   * @return The registry of configured WordNet lexicons, possibly empty. Never
   *         {@code null}.
   */
  public WordNetRegistry getWordNetRegistry() {
    return wordNetRegistry;
  }

  /**
   * Returns the registry of configured lattice dictionaries.
   *
   * @return The registry of configured lattice dictionaries, possibly empty. Never
   *         {@code null}.
   */
  public LatticeRegistry getLatticeRegistry() {
    return latticeRegistry;
  }

  /**
   * Returns the custom tokenizer registry.
   *
   * @return The configured custom tokenizer registry, possibly empty. Never {@code null}.
   */
  public TokenizerRegistry getTokenizerRegistry() {
    return tokenizerRegistry;
  }

  /**
   * Returns the custom sentence-detector registry.
   *
   * @return The configured custom detector registry, possibly empty. Never {@code null}.
   */
  public SentenceDetectorRegistry getSentenceDetectorRegistry() {
    return sentenceDetectorRegistry;
  }

  /**
   * Reports whether a constituency parser model is configured on this server.
   *
   * @return Whether a constituency parser model is configured on this server.
   */
  public boolean isParserAvailable() {
    return parserRegistry.isAvailable();
  }

  /**
   * Returns the parser registry (parsers grouped by id, with their engines).
   *
   * @return The parser registry. Never {@code null}; may be empty.
   */
  public ParserRegistry getParserRegistry() {
    return parserRegistry;
  }

  /**
   * Reports whether any shallow-chunking model is configured on this server.
   *
   * @return Whether a chunker is configured.
   */
  public boolean isChunkerAvailable() {
    return chunkerRegistry.isAvailable();
  }

  /**
   * Returns the chunker registry (chunkers grouped by id, with their engines).
   *
   * @return The chunker registry. Never {@code null}; may be empty.
   */
  public ChunkerRegistry getChunkerRegistry() {
    return chunkerRegistry;
  }

  /**
   * Releases native resources held by the embedding provider and the name-finder, document
   * categorizer and sentiment registries (e.g. ONNX sessions in DL models). Each failure is
   * logged so the remaining shutdown is not interrupted. The classic {@code *ME} backbone models
   * and the parser hold no native resources and need no release.
   */
  @Override
  public void close() {
    if (embeddingProvider instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception e) {
        logger.warn("Failed to close embedding provider", e);
      }
    }
    nameFinderRegistry.close();
    docCategorizerRegistry.close();
    sentimentRegistry.close();
    chunkerRegistry.close();
    closeQuietly(sentenceDetectorRegistry);
    closeQuietly(tokenizerRegistry);
  }

  /** Closes a resource if it is {@link AutoCloseable}, logging rather than propagating failures. */
  private static void closeQuietly(Object resource) {
    if (resource instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception e) {
        logger.warn("Failed to release a model resource during failed startup", e);
      }
    }
  }

  /**
   * Loads one component model following the three-step resolution order: an explicitly
   * configured file path, classpath discovery via the model provider, and finally the
   * model binary bundled inside the shaded server jar.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   * @param pathKey The configuration key for an explicit model file path.
   * @param type The {@link ModelType} used for classpath discovery.
   * @param modelClass The model class used for classpath discovery.
   * @param bundledFragment The name fragment identifying the bundled binary.
   * @param description Human-readable component name for log and error messages.
   * @param reader Deserializes the model from a stream, e.g. {@code SentenceModel::new}.
   *
   * @return The loaded model and its artifact hash, never {@code null}.
   * @throws AnalysisException If no model can be resolved or loading fails.
   */
  private <M extends BaseModel> LoadedArtifact<M> loadModel(
      Map<String, String> configuration, String pathKey, String bundledFragment,
      String description, ModelReader<M> reader) {
    try {
      final String configuredPath = configuration.get(pathKey);
      if (configuredPath != null && !configuredPath.isBlank()) {
        final byte[] bytes = Files.readAllBytes(Path.of(configuredPath));
        return new LoadedArtifact<>(reader.read(new ByteArrayInputStream(bytes)),
            ModelArtifactHasher.sha256Hex(bytes));
      }
      final ClasspathArtifact classpathArtifact =
          findClasspathArtifact(DEFAULT_LANGUAGE, bundledFragment);
      if (classpathArtifact != null) {
        return new LoadedArtifact<>(reader.read(new ByteArrayInputStream(classpathArtifact.bytes())),
            classpathArtifact.hash());
      }
      final InputStream bundled = openBundledModel(bundledFragment);
      if (bundled == null) {
        throw AnalysisException.notFound(
            "No " + description + " model available for language '" + DEFAULT_LANGUAGE
                + "'. Configure '" + pathKey + "' or add the corresponding opennlp-models"
                + " jar to the classpath.");
      }
      logger.info("Loaded {} model bundled in the server jar", description);
      try (InputStream input = bundled) {
        final byte[] bytes = input.readAllBytes();
        return new LoadedArtifact<>(reader.read(new ByteArrayInputStream(bytes)),
            ModelArtifactHasher.sha256Hex(bytes));
      }
    } catch (FileNotFoundException e) {
      // A configured path that does not exist is an operator error, not an internal fault.
      throw AnalysisException.notFound(
          "Configured " + description + " model file not found: " + configuration.get(pathKey));
    } catch (IOException e) {
      throw AnalysisException.internal("Failed to load " + description + " model", e);
    }
  }

  /**
   * One loaded classic model and the SHA-256 hash of its artifact bytes.
   *
   * @param model The deserialized OpenNLP model. Never {@code null}.
   * @param hash  The lowercase hex SHA-256 digest of the artifact bytes. Never {@code null}.
   */
  private record LoadedArtifact<M extends BaseModel>(M model, String hash) {
  }

  /**
   * Classpath-resolved artifact bytes and hash. Package-private for tests.
   *
   * @param bytes The model artifact bytes. Never {@code null}.
   * @param hash  The lowercase hex SHA-256 digest. Never {@code null}.
   */
  record ClasspathArtifact(byte[] bytes, String hash) {
  }

  /** Deserializes a model from a stream; all OpenNLP model constructors fit this shape. */
  @FunctionalInterface
  private interface ModelReader<M extends BaseModel> {
    M read(InputStream input) throws IOException;
  }

  /**
   * Returns the default classic pipeline, serving every request no configured language
   * pipeline matches.
   *
   * @return The default pipeline. Never {@code null}.
   */
  public ClassicLanguagePipeline defaultPipeline() {
    return defaultPipeline;
  }

  /**
   * Returns the configured classic pipeline languages.
   *
   * @return The normalized language codes in sorted order, possibly empty. Never
   *     {@code null}.
   */
  public List<String> pipelineLanguages() {
    return List.copyOf(languagePipelines.keySet());
  }

  /**
   * Resolves a configured classic pipeline by language code: the configured code itself
   * or its ISO 639-3 form, which is what the language detector reports.
   *
   * @param language The language code to resolve. May be {@code null}.
   *
   * @return The matching pipeline, or {@code null} when none is configured for
   *     {@code language}.
   */
  public ClassicLanguagePipeline pipelineFor(String language) {
    if (language == null || language.isBlank()) {
      return null;
    }
    final String key = pipelineLanguageAliases.get(StringUtil.toLowerCase(language.trim()));
    return key == null ? null : languagePipelines.get(key);
  }

  /** One loaded language pipeline with the artifact identities its bundle reports. */
  private record LoadedPipeline(ClassicLanguagePipeline pipeline,
      String sentenceName, String sentenceHash, String tokenizerName, String tokenizerHash,
      String posName, String posHash, String lemmatizerName, String lemmatizerHash) {
  }

  /**
   * Loads every configured {@code model.pipeline.<lang>.*} model set. A language that
   * configures some but not all four pipeline models fails loud.
   */
  private Map<String, LoadedPipeline> loadLanguagePipelines(Map<String, String> configuration) {
    final Map<String, Map<String, String>> byLanguage = new TreeMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_PIPELINE_PREFIX)) {
        continue;
      }
      final int languageEnd = key.indexOf('.', KEY_PIPELINE_PREFIX.length());
      if (languageEnd < 0) {
        throw AnalysisException.invalidArgument(
            "Invalid pipeline configuration key '" + key + "'");
      }
      final String language = StringUtil.toLowerCase(
          key.substring(KEY_PIPELINE_PREFIX.length(), languageEnd).trim());
      if (language.isEmpty()) {
        throw AnalysisException.invalidArgument(
            "Invalid pipeline configuration key '" + key + "'; language must not be blank");
      }
      byLanguage.computeIfAbsent(language, ignored -> new TreeMap<>())
          .put(key.substring(languageEnd + 1), entry.getValue());
    }
    final Map<String, LoadedPipeline> pipelines = new TreeMap<>();
    for (Map.Entry<String, Map<String, String>> entry : byLanguage.entrySet()) {
      pipelines.put(entry.getKey(), loadLanguagePipeline(entry.getKey(), entry.getValue()));
    }
    return pipelines;
  }

  /** Loads the four models of one language pipeline. */
  private LoadedPipeline loadLanguagePipeline(String language, Map<String, String> slots) {
    final String sentencePath = requiredPipelinePath(
        language, slots, PIPELINE_SENTDETECT_SUFFIX.substring(1));
    final String tokenizerPath = requiredPipelinePath(
        language, slots, PIPELINE_TOKENIZER_SUFFIX.substring(1));
    final String posPath = requiredPipelinePath(
        language, slots, PIPELINE_POS_SUFFIX.substring(1));
    final String lemmatizerPath = requiredPipelinePath(
        language, slots, PIPELINE_LEMMATIZER_SUFFIX.substring(1));
    final LoadedArtifact<SentenceModel> sentence = loadPipelineModel(
        language, "sentence detector", sentencePath, SentenceModel::new);
    final LoadedArtifact<TokenizerModel> tokenizer = loadPipelineModel(
        language, "tokenizer", tokenizerPath, TokenizerModel::new);
    final LoadedArtifact<POSModel> pos = loadPipelineModel(
        language, "POS tagger", posPath, POSModel::new);
    final LoadedArtifact<LemmatizerModel> lemma = loadPipelineModel(
        language, "lemmatizer", lemmatizerPath, LemmatizerModel::new);
    final LemmatizerME statistical = new LemmatizerME(lemma.model());
    final ClassicLanguagePipeline pipeline = new ClassicLanguagePipeline(language,
        new SentenceDetectorME(sentence.model()),
        new TokenizerME(tokenizer.model()),
        pos.model(), new POSTaggerME(pos.model()),
        statistical, statistical);
    logger.info("Loaded classic pipeline for language '{}'", language);
    return new LoadedPipeline(pipeline,
        fileName(sentencePath), sentence.hash(),
        fileName(tokenizerPath), tokenizer.hash(),
        fileName(posPath), pos.hash(),
        fileName(lemmatizerPath), lemma.hash());
  }

  /** Returns one required pipeline slot path, failing loud when it is absent or blank. */
  private static String requiredPipelinePath(
      String language, Map<String, String> slots, String slotKey) {
    final String path = slots.get(slotKey);
    if (path == null || path.isBlank()) {
      throw AnalysisException.invalidArgument("Pipeline '" + language
          + "' is missing " + KEY_PIPELINE_PREFIX + language + "." + slotKey
          + "; a pipeline configures all four classic models");
    }
    return path.trim();
  }

  /** Loads one pipeline model from its configured file. */
  private <M extends BaseModel> LoadedArtifact<M> loadPipelineModel(
      String language, String description, String path, ModelReader<M> reader) {
    try {
      final byte[] bytes = Files.readAllBytes(Path.of(path));
      return new LoadedArtifact<>(reader.read(new ByteArrayInputStream(bytes)),
          ModelArtifactHasher.sha256Hex(bytes));
    } catch (NoSuchFileException | FileNotFoundException e) {
      throw AnalysisException.notFound("Pipeline '" + language + "' " + description
          + " model file not found: " + path);
    } catch (IOException e) {
      throw AnalysisException.internal("Failed to load pipeline '" + language + "' "
          + description + " model from " + path, e);
    }
  }

  /** Returns the file name of a configured path, for bundle reporting. */
  private static String fileName(String path) {
    return Path.of(path).getFileName().toString();
  }

  /** Returns the ISO 639-3 form of a language code, or {@code null} when unknown. */
  private static String iso3Code(String language) {
    try {
      final String iso3 = Locale.of(language).getISO3Language();
      return iso3.isEmpty() ? null : StringUtil.toLowerCase(iso3);
    } catch (MissingResourceException e) {
      return null;
    }
  }

  /** Adds one advertised bundle per configured language pipeline. */
  private Map<String, ModelBundleInfo> withPipelineBundles(
      Map<String, ModelBundleInfo> catalog, Map<String, LoadedPipeline> loadedPipelines) {
    if (loadedPipelines.isEmpty()) {
      return catalog;
    }
    final Map<String, ModelBundleInfo> extended = new TreeMap<>(catalog);
    for (Map.Entry<String, LoadedPipeline> entry : loadedPipelines.entrySet()) {
      final String language = entry.getKey();
      final LoadedPipeline loaded = entry.getValue();
      extended.put(PIPELINE_BUNDLE_PREFIX + language, ModelBundleInfo.newBuilder()
          .setBundleId(PIPELINE_BUNDLE_PREFIX + language)
          .addSupportedLanguages(language)
          .addSupportedSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
          .addSupportedSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
          .addSupportedSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
          .addSupportedSteps(PipelineStep.PIPELINE_STEP_LEMMATIZE)
          .addModels(classicModelDescriptor(loaded.sentenceName(),
              ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR, language, loaded.sentenceHash()))
          .addModels(classicModelDescriptor(loaded.tokenizerName(),
              ComponentType.COMPONENT_TYPE_TOKENIZER, language, loaded.tokenizerHash()))
          .addModels(classicModelDescriptor(loaded.posName(),
              ComponentType.COMPONENT_TYPE_POS_TAGGER, language, loaded.posHash()))
          .addModels(classicModelDescriptor(loaded.lemmatizerName(),
              ComponentType.COMPONENT_TYPE_LEMMATIZER, language, loaded.lemmatizerHash()))
          .build());
    }
    return Map.copyOf(extended);
  }

  /**
   * The lemmatizer serving PIPELINE_STEP_LEMMATIZE with its reporting identity.
   *
   * @param lemmatizer The lemmatizer to serve. Never {@code null}.
   * @param statistical The statistical decoder when it backs {@code lemmatizer}, or
   *     {@code null} for a dictionary lemmatizer.
   * @param hash The lowercase hex SHA-256 digest of the source artifact.
   * @param name The artifact name reported in the bundle catalog.
   */
  private record LoadedLemmatizer(
      Lemmatizer lemmatizer, LemmatizerME statistical, String hash, String name) {
  }

  /**
   * Loads the configured lemmatizer: the tab-separated dictionary named by
   * {@code model.lemmatizer.dictionary}, or the statistical model named by
   * {@code model.lemmatizer.path} (falling back to the bundled English model).
   * Configuring both sources fails loud.
   */
  private LoadedLemmatizer loadLemmatizer(Map<String, String> configuration) {
    final String dictionaryPath = configuration.get(KEY_LEMMATIZER_DICTIONARY);
    final String modelPath = configuration.get(KEY_LEMMATIZER_PATH);
    final boolean dictionaryConfigured = dictionaryPath != null && !dictionaryPath.isBlank();
    if (dictionaryConfigured && modelPath != null && !modelPath.isBlank()) {
      throw AnalysisException.invalidArgument(KEY_LEMMATIZER_PATH + " and "
          + KEY_LEMMATIZER_DICTIONARY + " are mutually exclusive; configure one lemmatizer "
          + "source");
    }
    if (!dictionaryConfigured) {
      final LoadedArtifact<LemmatizerModel> loaded = loadModel(configuration,
          KEY_LEMMATIZER_PATH, BUNDLED_LEMMATIZER_MODEL_FRAGMENT, "lemmatizer",
          LemmatizerModel::new);
      final LemmatizerME statistical = new LemmatizerME(loaded.model());
      return new LoadedLemmatizer(statistical, statistical, loaded.hash(),
          "opennlp-models-lemmatizer-" + DEFAULT_LANGUAGE);
    }
    final byte[] bytes;
    try {
      bytes = Files.readAllBytes(Path.of(dictionaryPath));
    } catch (NoSuchFileException | FileNotFoundException e) {
      throw AnalysisException.notFound(
          "Lemmatizer dictionary file not found: " + dictionaryPath);
    } catch (IOException e) {
      throw AnalysisException.internal(
          "Failed to read lemmatizer dictionary " + dictionaryPath, e);
    }
    try {
      final DictionaryLemmatizer dictionary =
          new DictionaryLemmatizer(new ByteArrayInputStream(bytes));
      logger.info("Loaded dictionary lemmatizer from {} ({} entries)",
          dictionaryPath, dictionary.getDictMap().size());
      return new LoadedLemmatizer(dictionary, null,
          ModelArtifactHasher.sha256Hex(bytes), DICTIONARY_LEMMATIZER_NAME);
    } catch (IOException | RuntimeException e) {
      throw AnalysisException.invalidArgument("Lemmatizer dictionary " + dictionaryPath
          + " is not a valid word<TAB>postag<TAB>lemma file: " + e.getMessage());
    }
  }

  /**
   * Loads the language detector model. It needs custom resolution because the generic
   * classpath provider is keyed by language, while this model is language-independent
   * ({@code model.language=root} in its descriptor): explicit path first, then the
   * {@code model.properties} descriptors of the model jars on the classpath, then the
   * binary bundled inside the shaded server jar.
   */
  private LoadedArtifact<LanguageDetectorModel> loadLanguageDetectorModel(
      Map<String, String> configuration) {
    try {
      final String configuredPath = configuration.get(KEY_LANGDETECT_PATH);
      if (configuredPath != null && !configuredPath.isBlank()) {
        final byte[] bytes = Files.readAllBytes(Path.of(configuredPath));
        return new LoadedArtifact<>(new LanguageDetectorModel(new ByteArrayInputStream(bytes)),
            ModelArtifactHasher.sha256Hex(bytes));
      }
      final ClasspathArtifact classpathArtifact = findClasspathLanguageDetectorArtifact();
      final byte[] bytes;
      final String hash;
      if (classpathArtifact != null) {
        bytes = classpathArtifact.bytes();
        hash = classpathArtifact.hash();
      } else {
        final InputStream bundled = openBundledModel(BUNDLED_LANGDETECT_MODEL_FRAGMENT);
        if (bundled == null) {
          throw AnalysisException.notFound(
              "No language detector model available. Configure '" + KEY_LANGDETECT_PATH
                  + "' or add the opennlp-models-langdetect jar to the classpath.");
        }
        try (InputStream input = bundled) {
          bytes = input.readAllBytes();
        }
        hash = ModelArtifactHasher.sha256Hex(bytes);
      }
      return new LoadedArtifact<>(
          new LanguageDetectorModel(new ByteArrayInputStream(bytes)), hash);
    } catch (FileNotFoundException e) {
      // A configured path that does not exist is an operator error, not an internal fault.
      throw AnalysisException.notFound(
          "Configured language detector model file not found: "
              + configuration.get(KEY_LANGDETECT_PATH));
    } catch (IOException e) {
      throw AnalysisException.internal("Failed to load language detector model", e);
    }
  }



  /**
   * Locates a classic model binary through the {@code model.properties} descriptors on this
   * class's own classpath; see {@link #findClasspathArtifact(ClassLoader, String, String)}.
   */
  private static ClasspathArtifact findClasspathArtifact(String language, String nameFragment)
      throws IOException {
    return findClasspathArtifact(ModelBundleCache.class.getClassLoader(), language, nameFragment);
  }

  /**
   * Locates a classic model binary through {@code model.properties} descriptors visible to the
   * given classloader. Package-private so tests can supply an isolated descriptor classpath.
   *
   * @param classLoader  The classloader to scan for descriptors and model binaries.
   * @param language     The model language tag to match, e.g. {@code "en"}.
   * @param nameFragment A substring that must appear in the {@code model.name} entry.
   *
   * @return The artifact bytes and hash, or {@code null} when no matching descriptor is found.
   *
   * @throws IOException If a descriptor or model stream cannot be read.
   */
  static ClasspathArtifact findClasspathArtifact(
      ClassLoader classLoader, String language, String nameFragment) throws IOException {
    final Enumeration<URL> descriptors = classLoader.getResources(MODEL_DESCRIPTOR_RESOURCE);
    while (descriptors.hasMoreElements()) {
      final Properties properties = new Properties();
      try (InputStream input = descriptors.nextElement().openStream()) {
        properties.load(input);
      }
      if (!language.equals(properties.getProperty("model.language"))) {
        continue;
      }
      final String modelName = properties.getProperty("model.name", "");
      if (!modelName.endsWith(MODEL_FILE_SUFFIX) || !modelName.contains(nameFragment)) {
        continue;
      }
      try (InputStream model = classLoader.getResourceAsStream(modelName)) {
        if (model == null) {
          continue;
        }
        final byte[] bytes = model.readAllBytes();
        final String declaredHash =
            StringUtil.toLowerCase(properties.getProperty("model.sha256", "").trim());
        return new ClasspathArtifact(bytes, verifyDeclaredHash(modelName, bytes, declaredHash));
      }
    }
    return null;
  }

  /**
   * Locates the language-detector artifact through the {@code model.properties} descriptors on
   * this class's own classpath; see {@link #findClasspathLanguageDetectorArtifact(ClassLoader)}.
   */
  private static ClasspathArtifact findClasspathLanguageDetectorArtifact() throws IOException {
    return findClasspathLanguageDetectorArtifact(ModelBundleCache.class.getClassLoader());
  }

  /**
   * Locates the language-detector binary through the {@code model.properties} descriptors
   * visible to the given classloader, returning its bytes together with the declared (or
   * computed) SHA-256 hash. Package-private so tests can supply an isolated descriptor
   * classpath.
   *
   * @param classLoader The classloader to scan for descriptors and model binaries.
   *
   * @return The artifact bytes and hash, or {@code null} when no matching descriptor is found.
   *
   * @throws IOException If a descriptor or model stream cannot be read.
   */
  static ClasspathArtifact findClasspathLanguageDetectorArtifact(ClassLoader classLoader)
      throws IOException {
    final Enumeration<URL> descriptors = classLoader.getResources(MODEL_DESCRIPTOR_RESOURCE);
    while (descriptors.hasMoreElements()) {
      final Properties properties = new Properties();
      try (InputStream input = descriptors.nextElement().openStream()) {
        properties.load(input);
      }
      final String modelName = properties.getProperty("model.name", "");
      if (modelName.contains(BUNDLED_LANGDETECT_MODEL_FRAGMENT)
          && modelName.endsWith(MODEL_FILE_SUFFIX)) {
        try (InputStream model = classLoader.getResourceAsStream(modelName)) {
          if (model != null) {
            final byte[] bytes = model.readAllBytes();
            final String declaredHash =
                StringUtil.toLowerCase(properties.getProperty("model.sha256", "").trim());
            return new ClasspathArtifact(bytes, verifyDeclaredHash(modelName, bytes, declaredHash));
          }
        }
      }
    }
    return null;
  }

  /**
   * Verifies a descriptor-declared {@code model.sha256} against the artifact bytes actually
   * read: a declared hash that does not match the bytes fails startup with an integrity error
   * rather than pinning a hash the artifact does not have. When no hash is declared, the
   * computed digest is used.
   *
   * @param modelName The artifact name from the descriptor, for the error message.
   * @param bytes The artifact bytes read from the classpath.
   * @param declaredHash The descriptor's declared digest, lower-cased; may be blank.
   *
   * @return The verified lowercase hex SHA-256 digest of {@code bytes}.
   */
  private static String verifyDeclaredHash(String modelName, byte[] bytes, String declaredHash) {
    final String computed = ModelArtifactHasher.sha256Hex(bytes);
    if (!declaredHash.isBlank() && !declaredHash.equals(computed)) {
      throw AnalysisException.internal(
          "Integrity check failed for classpath model '" + modelName + "': the descriptor"
              + " declares sha256 " + declaredHash + " but the artifact bytes hash to "
              + computed, null);
    }
    return computed;
  }

  /**
   * Builds the artifact registry used for {@code component_models} validation and catalog hashes.
   *
   * @param langDetectHash SHA-256 digest of the language detector artifact.
   * @param sentenceHash   SHA-256 digest of the sentence detector artifact.
   * @param tokenizerHash  SHA-256 digest of the tokenizer artifact.
   * @param posHash        SHA-256 digest of the POS tagger artifact.
   * @param lemmaHash      SHA-256 digest of the lemmatizer artifact.
   *
   * @return The completed registry. Never {@code null}.
   */
  private ModelArtifactRegistry buildArtifactRegistry(
      String langDetectHash,
      String sentenceHash,
      String tokenizerHash,
      String posHash,
      String lemmaHash,
      String lemmaName) {
    final ModelArtifactRegistry.Builder builder = ModelArtifactRegistry.builder()
        .register(ComponentType.COMPONENT_TYPE_LANGUAGE_DETECTOR, langDetectHash,
            "opennlp-models-langdetect")
        .register(ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR, sentenceHash,
            SENTENCE_MODEL_NAME)
        .register(ComponentType.COMPONENT_TYPE_TOKENIZER, tokenizerHash,
            TOKENIZER_MODEL_NAME)
        .register(ComponentType.COMPONENT_TYPE_POS_TAGGER, posHash,
            POS_MODEL_NAME)
        .register(ComponentType.COMPONENT_TYPE_LEMMATIZER, lemmaHash, lemmaName);
    for (String modelId : embeddingProvider.registeredModelIds()) {
      final String hash = embeddingProvider.modelArtifactHash(modelId);
      if (hash != null && !hash.isBlank()) {
        builder.register(ComponentType.COMPONENT_TYPE_EMBEDDER, hash, modelId);
      }
    }
    for (NerModel model : nameFinderRegistry.allModels()) {
      if (!model.artifactHash().isBlank()) {
        builder.register(ComponentType.COMPONENT_TYPE_NAME_FINDER, model.artifactHash(),
            model.id());
      }
    }
    return builder.build();
  }

  /**
   * Locates the language detector binary through the {@code model.properties}
   * descriptors of the model jars on the classpath. The binary sits at the jar root, so
   * once the descriptor names it, the model loads as a plain classpath resource.
   *
   * @return An input stream of the model bytes, or {@code null} if no descriptor on the
   *     classpath names a language detector binary.
   */
  private static InputStream findClassPathLanguageDetectorModel() throws IOException {
    final ClassLoader classLoader = ModelBundleCache.class.getClassLoader();
    final Enumeration<URL> descriptors = classLoader.getResources(MODEL_DESCRIPTOR_RESOURCE);
    while (descriptors.hasMoreElements()) {
      final Properties properties = new Properties();
      try (InputStream input = descriptors.nextElement().openStream()) {
        properties.load(input);
      }
      final String modelName = properties.getProperty("model.name", "");
      if (modelName.contains(BUNDLED_LANGDETECT_MODEL_FRAGMENT)
          && modelName.endsWith(MODEL_FILE_SUFFIX)) {
        final InputStream model = classLoader.getResourceAsStream(modelName);
        if (model != null) {
          return model;
        }
      }
    }
    return null;
  }

  /**
   * Opens a model binary bundled in the jar this class was loaded from, e.g. the shaded
   * server jar which merges the {@code opennlp-models-*} artifacts. Returns {@code null}
   * when not running from a jar (tests, exploded classpath) or when no matching entry
   * exists; classpath discovery is expected to handle those cases.
   *
   * @param nameFragment The fragment identifying the model binary, e.g. {@code "-sentence-"}.
   * @return An input stream of the model bytes, or {@code null} if not found.
   * @throws IOException Thrown if the jar exists but cannot be read.
   */
  private static InputStream openBundledModel(String nameFragment) throws IOException {
    final Path jarPath = codeSourceJar();
    if (jarPath == null) {
      return null;
    }
    return findBundledModel(jarPath, nameFragment);
  }

  /**
   * Scans the root entries of {@code jarFile} for a model binary whose name contains
   * {@code nameFragment} and ends with {@code .bin}.
   *
   * @param jarFile The jar file to scan. Must not be {@code null}.
   * @param nameFragment The fragment identifying the model binary. Must not be {@code null}.
   * @return An input stream of the model bytes, or {@code null} if no entry matches.
   * @throws IOException Thrown if the jar cannot be read.
   */
  static InputStream findBundledModel(Path jarFile, String nameFragment) throws IOException {
    try (JarFile jar = new JarFile(jarFile.toFile())) {
      final Enumeration<JarEntry> entries = jar.entries();
      while (entries.hasMoreElements()) {
        final JarEntry entry = entries.nextElement();
        final String name = entry.getName();
        // Model artifacts place their binaries at the jar root; nested entries belong
        // to other dependencies and are not considered.
        if (!entry.isDirectory() && !name.contains("/")
            && name.endsWith(MODEL_FILE_SUFFIX) && name.contains(nameFragment)) {
          try (InputStream input = jar.getInputStream(entry)) {
            return new ByteArrayInputStream(input.readAllBytes());
          }
        }
      }
    }
    return null;
  }

  /**
   * @return The jar file this class was loaded from, or {@code null} when running from
   *     an exploded classpath (e.g. during tests or {@code mvn exec}).
   */
  private static Path codeSourceJar() {
    final CodeSource codeSource = ModelBundleCache.class.getProtectionDomain().getCodeSource();
    if (codeSource == null || codeSource.getLocation() == null) {
      return null;
    }
    try {
      final Path path = Path.of(codeSource.getLocation().toURI());
      if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
        return path;
      }
      return null;
    } catch (URISyntaxException e) {
      logger.warn("Could not resolve code source location: {}", codeSource.getLocation(), e);
      return null;
    }
  }

  /** Builds the complete model-bundle capability catalog. */
  private Map<String, ModelBundleInfo> buildBundleCatalog(
      String langDetectHash,
      String sentenceHash,
      String tokenizerHash,
      String posHash,
      String lemmaHash,
      String lemmaName) {
    final ModelBundleInfo.Builder bundle = ModelBundleInfo.newBuilder()
        .setBundleId(ProfileRegistry.DEFAULT_BUNDLE_ID)
        .addSupportedLanguages(DEFAULT_LANGUAGE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_LEMMATIZE)
        .addModels(classicModelDescriptor(
            "opennlp-models-langdetect",
            ComponentType.COMPONENT_TYPE_LANGUAGE_DETECTOR,
            "root",
            langDetectHash))
        .addModels(classicModelDescriptor(
            SENTENCE_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR,
            DEFAULT_LANGUAGE,
            sentenceHash))
        .addModels(classicModelDescriptor(
            TOKENIZER_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_TOKENIZER,
            DEFAULT_LANGUAGE,
            tokenizerHash))
        .addModels(classicModelDescriptor(
            POS_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_POS_TAGGER,
            DEFAULT_LANGUAGE,
            posHash))
        .addModels(classicModelDescriptor(
            lemmaName,
            ComponentType.COMPONENT_TYPE_LEMMATIZER,
            DEFAULT_LANGUAGE,
            lemmaHash));
    if (embeddingProvider.isAvailable()) {
      bundle.addSupportedSteps(PipelineStep.PIPELINE_STEP_EMBED);
      for (String modelId : embeddingProvider.registeredModelIds()) {
        // Each logical model is tagged with the engine it resolves to by default (highest priority).
        final ModelDescriptor.Builder descriptor = ModelDescriptor.newBuilder()
            .setName(modelId)
            .setLocale(DEFAULT_LANGUAGE)
            .setComponentType(ComponentType.COMPONENT_TYPE_EMBEDDER)
            .addLanguages(DEFAULT_LANGUAGE)
            .setEmbeddingDimension(embeddingProvider.embeddingDimension(modelId))
            .setBackendId(embeddingProvider.backendId(modelId))
            .addAllEmbeddingRoutes(embeddingProvider.routesForModel(modelId));
        final String hash = embeddingProvider.modelArtifactHash(modelId);
        if (hash != null && !hash.isBlank()) {
          descriptor.setHash(hash);
        }
        bundle.addModels(descriptor.build());
      }
    }
    final Map<String, ModelBundleInfo> catalog = new HashMap<>();
    catalog.put(ProfileRegistry.DEFAULT_BUNDLE_ID, bundle.build());
    if (nameFinderRegistry.isAvailable()) {
      catalog.put(ProfileRegistry.NER_BUNDLE_ID,
          buildNerBundleCatalog(sentenceHash, tokenizerHash));
    }
    if (docCategorizerRegistry.isAvailable()) {
      catalog.put(ProfileRegistry.DOCCAT_BUNDLE_ID,
          buildDoccatBundleCatalog(sentenceHash, tokenizerHash));
    }
    if (sentimentRegistry.isAvailable()) {
      catalog.put(ProfileRegistry.SENTIMENT_BUNDLE_ID,
          buildSentimentBundleCatalog(sentenceHash, tokenizerHash));
    }
    if (parserRegistry.isAvailable()) {
      catalog.put(ProfileRegistry.PARSE_BUNDLE_ID,
          buildParseBundleCatalog(sentenceHash, tokenizerHash));
    }
    if (dependencyParserRegistry.isAvailable()) {
      catalog.put(ProfileRegistry.DEPENDENCY_BUNDLE_ID,
          buildDependencyBundleCatalog(sentenceHash, tokenizerHash, posHash));
    }
    if (chunkerRegistry.isAvailable()) {
      catalog.put(ProfileRegistry.CHUNK_BUNDLE_ID,
          buildChunkBundleCatalog(sentenceHash, tokenizerHash, posHash));
    }
    return catalog;
  }

  /** Builds the syntactic-chunk model bundle. */
  private ModelBundleInfo buildChunkBundleCatalog(
      String sentenceHash, String tokenizerHash, String posHash) {
    // Shallow chunking consumes POS-tagged English tokens, so the bundle constrains input to
    // English; the chunker model is operator-supplied and its language is unknown to the server.
    return ModelBundleInfo.newBuilder()
        .setBundleId(ProfileRegistry.CHUNK_BUNDLE_ID)
        .addSupportedLanguages(DEFAULT_LANGUAGE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK)
        .addModels(classicModelDescriptor(
            SENTENCE_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR,
            DEFAULT_LANGUAGE,
            sentenceHash))
        .addModels(classicModelDescriptor(
            TOKENIZER_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_TOKENIZER,
            DEFAULT_LANGUAGE,
            tokenizerHash))
        .addModels(classicModelDescriptor(
            POS_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_POS_TAGGER,
            DEFAULT_LANGUAGE,
            posHash))
        .addModels(ModelDescriptor.newBuilder()
            .setName("chunker")
            .setComponentType(ComponentType.COMPONENT_TYPE_CHUNKER)
            .addSupportedSteps(PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK)
            .setBackendId(OPENNLP_ME_BACKEND_ID)
            .build())
        .build();
  }

  /** Builds the parser model bundle. */
  private ModelBundleInfo buildParseBundleCatalog(String sentenceHash, String tokenizerHash) {
    // Parsing consumes the English tokenizer's output, so the bundle constrains input to English;
    // the parser model is operator-supplied and its language is unknown to the server.
    return ModelBundleInfo.newBuilder()
        .setBundleId(ProfileRegistry.PARSE_BUNDLE_ID)
        .addSupportedLanguages(DEFAULT_LANGUAGE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_PARSE)
        .addModels(classicModelDescriptor(
            SENTENCE_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR,
            DEFAULT_LANGUAGE,
            sentenceHash))
        .addModels(classicModelDescriptor(
            TOKENIZER_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_TOKENIZER,
            DEFAULT_LANGUAGE,
            tokenizerHash))
        .addModels(ModelDescriptor.newBuilder()
            .setName("parser")
            .setComponentType(ComponentType.COMPONENT_TYPE_PARSER)
            .addSupportedSteps(PipelineStep.PIPELINE_STEP_PARSE)
            .setBackendId(OPENNLP_ME_BACKEND_ID)
            .build())
        .build();
  }

  /** Builds the dependency parser model bundle. */
  private ModelBundleInfo buildDependencyBundleCatalog(
      String sentenceHash, String tokenizerHash, String posHash) {
    final ModelBundleInfo.Builder bundle = ModelBundleInfo.newBuilder()
        .setBundleId(ProfileRegistry.DEPENDENCY_BUNDLE_ID)
        .addSupportedLanguages(DEFAULT_LANGUAGE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE)
        .addModels(classicModelDescriptor(
            SENTENCE_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR,
            DEFAULT_LANGUAGE,
            sentenceHash))
        .addModels(classicModelDescriptor(
            TOKENIZER_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_TOKENIZER,
            DEFAULT_LANGUAGE,
            tokenizerHash))
        .addModels(classicModelDescriptor(
            POS_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_POS_TAGGER,
            DEFAULT_LANGUAGE,
            posHash));
    for (String parserId : dependencyParserRegistry.ids()) {
      bundle.addModels(ModelDescriptor.newBuilder()
          .setName(parserId)
          .setComponentType(ComponentType.COMPONENT_TYPE_DEPENDENCY_PARSER)
          .addSupportedSteps(PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE)
          .setBackendId(OPENNLP_ME_BACKEND_ID));
    }
    return bundle.build();
  }

  /** Builds the name-finder model bundle. */
  private ModelBundleInfo buildNerBundleCatalog(String sentenceHash, String tokenizerHash) {
    // The sentence-detector and tokenizer backbone is English, so the bundle constrains
    // input to English; the name finder models themselves are operator-supplied and their
    // language is unknown to the server, so their descriptors claim no locale/language.
    final ModelBundleInfo.Builder bundle = ModelBundleInfo.newBuilder()
        .setBundleId(ProfileRegistry.NER_BUNDLE_ID)
        .addSupportedLanguages(DEFAULT_LANGUAGE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_NER)
        .addModels(classicModelDescriptor(
            SENTENCE_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR,
            DEFAULT_LANGUAGE,
            sentenceHash))
        .addModels(classicModelDescriptor(
            TOKENIZER_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_TOKENIZER,
            DEFAULT_LANGUAGE,
            tokenizerHash));
    for (NerModel model : nameFinderRegistry.allModels()) {
      for (String entityType : model.entityTypes()) {
        final ModelDescriptor.Builder descriptor = ModelDescriptor.newBuilder()
            .setName(entityType)
            .setComponentType(ComponentType.COMPONENT_TYPE_NAME_FINDER)
            .addSupportedSteps(PipelineStep.PIPELINE_STEP_NER)
            .setBackendId(model.backendId());
        if (!model.artifactHash().isBlank()) {
          descriptor.setHash(model.artifactHash());
        }
        bundle.addModels(descriptor.build());
      }
    }
    return bundle.build();
  }

  /** Builds the document-categorizer model bundle. */
  private ModelBundleInfo buildDoccatBundleCatalog(String sentenceHash, String tokenizerHash) {
    // The classic categorizer consumes the English tokenizer's output, so the bundle constrains
    // input to English; the categorizer models themselves are operator-supplied and their
    // language is unknown to the server, so their descriptors claim no locale/language.
    final ModelBundleInfo.Builder bundle = ModelBundleInfo.newBuilder()
        .setBundleId(ProfileRegistry.DOCCAT_BUNDLE_ID)
        .addSupportedLanguages(DEFAULT_LANGUAGE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE)
        .addModels(classicModelDescriptor(
            SENTENCE_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR,
            DEFAULT_LANGUAGE,
            sentenceHash))
        .addModels(classicModelDescriptor(
            TOKENIZER_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_TOKENIZER,
            DEFAULT_LANGUAGE,
            tokenizerHash));
    for (DocCategorizerModel model : docCategorizerRegistry.allModels()) {
      bundle.addModels(ModelDescriptor.newBuilder()
          .setName(model.id())
          .setComponentType(ComponentType.COMPONENT_TYPE_DOC_CATEGORIZER)
          .addSupportedSteps(PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE)
          .setBackendId(model.backendId())
          .build());
    }
    return bundle.build();
  }

  /** Builds the sentiment model bundle. */
  private ModelBundleInfo buildSentimentBundleCatalog(String sentenceHash, String tokenizerHash) {
    // Sentiment runs per sentence, so it needs the English sentence-detector and tokenizer
    // backbone; the sentiment models themselves are operator-supplied and their language is
    // unknown to the server, so their descriptors claim no locale/language.
    final ModelBundleInfo.Builder bundle = ModelBundleInfo.newBuilder()
        .setBundleId(ProfileRegistry.SENTIMENT_BUNDLE_ID)
        .addSupportedLanguages(DEFAULT_LANGUAGE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_SENTIMENT)
        .addModels(classicModelDescriptor(
            SENTENCE_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR,
            DEFAULT_LANGUAGE,
            sentenceHash))
        .addModels(classicModelDescriptor(
            TOKENIZER_MODEL_NAME,
            ComponentType.COMPONENT_TYPE_TOKENIZER,
            DEFAULT_LANGUAGE,
            tokenizerHash));
    for (DocCategorizerModel model : sentimentRegistry.allModels()) {
      bundle.addModels(ModelDescriptor.newBuilder()
          .setName(model.id())
          .setComponentType(ComponentType.COMPONENT_TYPE_SENTIMENT)
          .addSupportedSteps(PipelineStep.PIPELINE_STEP_SENTIMENT)
          .setBackendId(model.backendId())
          .build());
    }
    return bundle.build();
  }

  /** Descriptor for a model served by the classic OpenNLP maxent runtime. */
  private static ModelDescriptor classicModelDescriptor(
      String name, ComponentType type, String locale, String hash) {
    return ModelDescriptor.newBuilder()
        .setHash(hash)
        .setName(name)
        .setLocale(locale)
        .setComponentType(type)
        .addLanguages(locale)
        .setBackendId(OPENNLP_ME_BACKEND_ID)
        .build();
  }
}
