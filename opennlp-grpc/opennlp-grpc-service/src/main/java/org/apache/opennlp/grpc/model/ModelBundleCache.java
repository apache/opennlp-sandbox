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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.parser.Parser;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.models.ClassPathModelProvider;
import opennlp.tools.models.DefaultClassPathModelProvider;
import opennlp.tools.models.ModelType;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.model.BaseModel;
import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.embedding.EmbeddingProviderFactory;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.ComponentType;
import org.apache.opennlp.grpc.v1.ModelBundleInfo;
import org.apache.opennlp.grpc.v1.ModelDescriptor;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads shared thread-safe {@code *ME} singletons once at startup.
 *
 * <p>Models resolve in three steps: optional explicit paths in configuration, then the
 * classpath via {@link DefaultClassPathModelProvider} and {@code opennlp-models-*} runtime
 * deps, and finally the model binaries bundled inside the shaded server jar itself.
 *
 * <p>The bundled fallback exists because classpath discovery matches model <em>jar file
 * names</em> ({@code opennlp-models-*.jar}) and therefore cannot see models that have been
 * merged into a single executable jar, which is how the server is distributed and run
 * ({@code java -jar opennlp-grpc-server-*.jar}).
 */
public final class ModelBundleCache {

  private static final Logger logger = LoggerFactory.getLogger(ModelBundleCache.class);

  private static final String DEFAULT_LANGUAGE = "en";
  private static final String KEY_SENTDETECT_PATH = "model.sentence_detector.path";
  private static final String KEY_TOKENIZER_PATH = "model.tokenizer.path";
  private static final String KEY_POS_TAGGER_PATH = "model.pos_tagger.path";
  private static final String KEY_LEMMATIZER_PATH = "model.lemmatizer.path";
  private static final String KEY_LANGDETECT_PATH = "model.language_detector.path";
  private static final String KEY_PARSER_PATH = "model.parser.path";
  private static final String KEY_CHUNKER_PATH = "model.chunker.path";

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

  private final ClassPathModelProvider modelProvider;
  private final Map<String, ModelBundleInfo> bundles;
  private final SentenceDetectorME sentenceDetector;
  private final TokenizerME tokenizer;
  private final POSTaggerME posTagger;
  private final LemmatizerME lemmatizer;
  private final LanguageDetectorME languageDetector;
  private final EmbeddingProvider embeddingProvider;
  private final NameFinderRegistry nameFinderRegistry;
  private final DocCategorizerRegistry docCategorizerRegistry;
  private final SentimentRegistry sentimentRegistry;
  // The parser is optional (the model is large and operator-supplied, not bundled). Unlike the
  // other classic *ME components, OpenNLP's parser is NOT thread-safe — its beam search mutates
  // per-instance state — so each request thread gets its own Parser built from the shared,
  // immutable ParserModel. {@code parser} is null when no parser model is configured.
  private final boolean parserAvailable;
  private final ThreadLocal<Parser> parser;
  // Optional shallow-chunking model (operator-supplied via model.chunker.path, not bundled).
  // ChunkerME is @ThreadSafe (per-thread state), so a single instance is shared. Null when
  // no chunker model is configured.
  private final ChunkerME chunker;

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
    Objects.requireNonNull(configuration, "configuration");
    this.modelProvider = new DefaultClassPathModelProvider();
    this.sentenceDetector = new SentenceDetectorME(loadModel(configuration,
        KEY_SENTDETECT_PATH, ModelType.SENTENCE_DETECTOR, SentenceModel.class,
        BUNDLED_SENTENCE_MODEL_FRAGMENT, "sentence detector", SentenceModel::new));
    this.tokenizer = new TokenizerME(loadModel(configuration,
        KEY_TOKENIZER_PATH, ModelType.TOKENIZER, TokenizerModel.class,
        BUNDLED_TOKENIZER_MODEL_FRAGMENT, "tokenizer", TokenizerModel::new));
    this.posTagger = new POSTaggerME(loadModel(configuration,
        KEY_POS_TAGGER_PATH, ModelType.POS_GENERIC, POSModel.class,
        BUNDLED_POS_MODEL_FRAGMENT, "POS tagger", POSModel::new));
    this.lemmatizer = new LemmatizerME(loadModel(configuration,
        KEY_LEMMATIZER_PATH, ModelType.LEMMATIZER, LemmatizerModel.class,
        BUNDLED_LEMMATIZER_MODEL_FRAGMENT, "lemmatizer", LemmatizerModel::new));
    this.languageDetector = new LanguageDetectorME(loadLanguageDetectorModel(configuration));
    // The embedding provider and the three registries may hold native resources (ONNX sessions,
    // remote connections). If a later load fails, release the ones already created so a failed
    // startup does not leak native sessions.
    EmbeddingProvider embeddingProvider = null;
    NameFinderRegistry nameFinderRegistry = null;
    DocCategorizerRegistry docCategorizerRegistry = null;
    SentimentRegistry sentimentRegistry = null;
    boolean constructed = false;
    try {
      embeddingProvider = EmbeddingProviderFactory.create(configuration);
      nameFinderRegistry = NameFinderRegistry.create(configuration, sentenceDetector);
      docCategorizerRegistry = DocCategorizerRegistry.create(configuration);
      sentimentRegistry = SentimentRegistry.create(configuration);
      final ParserModel parserModel = loadParserModel(configuration);
      this.embeddingProvider = embeddingProvider;
      this.nameFinderRegistry = nameFinderRegistry;
      this.docCategorizerRegistry = docCategorizerRegistry;
      this.sentimentRegistry = sentimentRegistry;
      this.parserAvailable = parserModel != null;
      this.parser = parserModel == null ? null
          : ThreadLocal.withInitial(() -> ParserFactory.create(parserModel));
      this.chunker = loadChunker(configuration);
      this.bundles = buildBundleCatalog();
      constructed = true;
    } finally {
      if (!constructed) {
        closeQuietly(sentimentRegistry);
        closeQuietly(docCategorizerRegistry);
        closeQuietly(nameFinderRegistry);
        closeQuietly(embeddingProvider);
      }
    }
  }

  /**
   * Returns the shared sentence detector. Always available (bundled default when unconfigured).
   *
   * @return The shared sentence detector. Never {@code null}.
   */
  public SentenceDetectorME getSentenceDetector() {
    return sentenceDetector;
  }

  /**
   * Returns the shared tokenizer. Always available (bundled default when unconfigured).
   *
   * @return The shared tokenizer. Never {@code null}.
   */
  public TokenizerME getTokenizer() {
    return tokenizer;
  }

  /**
   * Returns the shared POS tagger. Always available (bundled default when unconfigured).
   *
   * @return The shared POS tagger. Never {@code null}.
   */
  public POSTaggerME getPosTagger() {
    return posTagger;
  }

  /**
   * Returns the shared lemmatizer. Always available (bundled default when unconfigured).
   *
   * @return The shared lemmatizer. Never {@code null}.
   */
  public LemmatizerME getLemmatizer() {
    return lemmatizer;
  }

  /**
   * Returns the shared language detector. Always available (bundled default when unconfigured).
   *
   * @return The shared language detector. Never {@code null}.
   */
  public LanguageDetectorME getLanguageDetector() {
    return languageDetector;
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
   * Returns the configured embedding provider.
   *
   * @return The embedding provider; never {@code null}, though it may report no registered
   *     models when none is configured.
   */
  public EmbeddingProvider getEmbeddingProvider() {
    return embeddingProvider;
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
   * Reports whether a constituency parser model is configured on this server.
   *
   * @return Whether a constituency parser model is configured on this server.
   */
  public boolean isParserAvailable() {
    return parserAvailable;
  }

  /**
   * Returns the calling thread's parser instance, building it lazily from the shared model.
   *
   * @return A parser for the calling thread (lazily built from the shared immutable model), or
   *     {@code null} when no parser is configured. The instance must not be shared across threads
   *     — OpenNLP's parser is not thread-safe — so callers use the returned parser only on the
   *     thread that requested it.
   */
  public Parser getParser() {
    return parser == null ? null : parser.get();
  }

  /**
   * Reports whether a shallow-chunking (ChunkerME) model is configured on this server.
   *
   * @return Whether a chunker model is configured.
   */
  public boolean isChunkerAvailable() {
    return chunker != null;
  }

  /**
   * Returns the shared shallow chunker. {@code ChunkerME} is thread-safe, so the one instance is
   * shared across requests.
   *
   * @return The chunker, or {@code null} when none is configured.
   */
  public ChunkerME getChunker() {
    return chunker;
  }

  /**
   * Releases native resources held by the embedding provider and the name-finder, document
   * categorizer and sentiment registries (e.g. ONNX sessions in DL models). Each failure is
   * logged so the remaining shutdown is not interrupted. The classic {@code *ME} backbone models
   * and the parser hold no native resources and need no release.
   */
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
   * @return The loaded model, never {@code null}.
   * @throws AnalysisException If no model can be resolved or loading fails.
   */
  private <M extends BaseModel> M loadModel(
      Map<String, String> configuration, String pathKey, ModelType type, Class<M> modelClass,
      String bundledFragment, String description, ModelReader<M> reader) {
    try {
      final String configuredPath = configuration.get(pathKey);
      if (configuredPath != null && !configuredPath.isBlank()) {
        try (InputStream input = new FileInputStream(configuredPath)) {
          return reader.read(input);
        }
      }
      M resolved = modelProvider.load(DEFAULT_LANGUAGE, type, modelClass);
      if (resolved == null) {
        final InputStream bundled = openBundledModel(bundledFragment);
        if (bundled == null) {
          throw AnalysisException.notFound(
              "No " + description + " model available for language '" + DEFAULT_LANGUAGE
                  + "'. Configure '" + pathKey + "' or add the corresponding opennlp-models"
                  + " jar to the classpath.");
        }
        logger.info("Loaded {} model bundled in the server jar", description);
        try (InputStream input = bundled) {
          resolved = reader.read(input);
        }
      }
      return resolved;
    } catch (FileNotFoundException e) {
      // A configured path that does not exist is an operator error, not an internal fault.
      throw AnalysisException.notFound(
          "Configured " + description + " model file not found: " + configuration.get(pathKey));
    } catch (IOException e) {
      throw AnalysisException.internal("Failed to load " + description + " model", e);
    }
  }

  /** Deserializes a model from a stream; all OpenNLP model constructors fit this shape. */
  @FunctionalInterface
  private interface ModelReader<M extends BaseModel> {
    M read(InputStream input) throws IOException;
  }

  /**
   * Loads the language detector model. It needs custom resolution because the generic
   * classpath provider is keyed by language, while this model is language-independent
   * ({@code model.language=root} in its descriptor): explicit path first, then the
   * {@code model.properties} descriptors of the model jars on the classpath, then the
   * binary bundled inside the shaded server jar.
   */
  private LanguageDetectorModel loadLanguageDetectorModel(Map<String, String> configuration) {
    try {
      final String configuredPath = configuration.get(KEY_LANGDETECT_PATH);
      if (configuredPath != null && !configuredPath.isBlank()) {
        try (InputStream input = new FileInputStream(configuredPath)) {
          return new LanguageDetectorModel(input);
        }
      }
      InputStream resolved = findClassPathLanguageDetectorModel();
      if (resolved == null) {
        resolved = openBundledModel(BUNDLED_LANGDETECT_MODEL_FRAGMENT);
      }
      if (resolved == null) {
        throw AnalysisException.notFound(
            "No language detector model available. Configure '" + KEY_LANGDETECT_PATH
                + "' or add the opennlp-models-langdetect jar to the classpath.");
      }
      try (InputStream input = resolved) {
        return new LanguageDetectorModel(input);
      }
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
   * Loads the optional constituency parser. Unlike the always-present backbone components, the
   * parser model is large and not bundled, so it is loaded only when an explicit
   * {@code model.parser.path} is configured; otherwise the server simply does not offer
   * {@code PIPELINE_STEP_PARSE}.
   *
   * @return The parser model, or {@code null} when none is configured. The model is immutable and
   *     shared; per-thread {@code Parser} instances are created from it on demand.
   */
  private ParserModel loadParserModel(Map<String, String> configuration) {
    final String configuredPath = configuration.get(KEY_PARSER_PATH);
    if (configuredPath == null || configuredPath.isBlank()) {
      return null;
    }
    try (InputStream input = new FileInputStream(configuredPath)) {
      final ParserModel model = new ParserModel(input);
      logger.info("Loaded parser model from {}", configuredPath);
      return model;
    } catch (FileNotFoundException e) {
      // A configured path that does not exist is an operator error, not an internal fault.
      throw AnalysisException.notFound("Configured parser model file not found: " + configuredPath);
    } catch (IOException e) {
      throw AnalysisException.internal("Failed to load parser model from " + configuredPath, e);
    }
  }

  /**
   * Loads the optional shallow chunker. Like the parser, the chunker model is operator-supplied
   * (not bundled), so it is loaded only when {@code model.chunker.path} is configured; otherwise
   * the server does not offer {@code PIPELINE_STEP_SYNTACTIC_CHUNK}.
   *
   * @return A shared thread-safe chunker, or {@code null} when none is configured.
   */
  private ChunkerME loadChunker(Map<String, String> configuration) {
    final String configuredPath = configuration.get(KEY_CHUNKER_PATH);
    if (configuredPath == null || configuredPath.isBlank()) {
      return null;
    }
    try (InputStream input = new FileInputStream(configuredPath)) {
      final ChunkerME loaded = new ChunkerME(new ChunkerModel(input));
      logger.info("Loaded chunker model from {}", configuredPath);
      return loaded;
    } catch (FileNotFoundException e) {
      // A configured path that does not exist is an operator error, not an internal fault.
      throw AnalysisException.notFound("Configured chunker model file not found: " + configuredPath);
    } catch (IOException e) {
      throw AnalysisException.internal("Failed to load chunker model from " + configuredPath, e);
    }
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

  private Map<String, ModelBundleInfo> buildBundleCatalog() {
    final ModelBundleInfo.Builder bundle = ModelBundleInfo.newBuilder()
        .setBundleId(ProfileRegistry.DEFAULT_BUNDLE_ID)
        .addSupportedLanguages(DEFAULT_LANGUAGE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_LEMMATIZE)
        .addModels(ModelDescriptor.newBuilder()
            .setName("opennlp-models-langdetect")
            // The language detector is language-independent; its descriptor declares
            // locale "root" and it predicts ISO 639-3 codes for 103 languages.
            .setLocale("root")
            .setComponentType(ComponentType.COMPONENT_TYPE_LANGUAGE_DETECTOR)
            .setBackendId(OPENNLP_ME_BACKEND_ID)
            .build())
        .addModels(classicModelDescriptor(
            "opennlp-models-sentdetect-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR))
        .addModels(classicModelDescriptor(
            "opennlp-models-tokenizer-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_TOKENIZER))
        .addModels(classicModelDescriptor(
            "opennlp-models-pos-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_POS_TAGGER))
        .addModels(classicModelDescriptor(
            "opennlp-models-lemmatizer-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_LEMMATIZER));
    if (embeddingProvider.isAvailable()) {
      bundle.addSupportedSteps(PipelineStep.PIPELINE_STEP_EMBED);
      for (String modelId : embeddingProvider.registeredModelIds()) {
        // Each logical model is tagged with the engine it resolves to by default (highest priority).
        bundle.addModels(ModelDescriptor.newBuilder()
            .setName(modelId)
            .setLocale(DEFAULT_LANGUAGE)
            .setComponentType(ComponentType.COMPONENT_TYPE_EMBEDDER)
            .addLanguages(DEFAULT_LANGUAGE)
            .setEmbeddingDimension(embeddingProvider.embeddingDimension(modelId))
            .setBackendId(embeddingProvider.backendId(modelId))
            .build());
      }
    }
    final Map<String, ModelBundleInfo> catalog = new HashMap<>();
    catalog.put(ProfileRegistry.DEFAULT_BUNDLE_ID, bundle.build());
    if (nameFinderRegistry.isAvailable()) {
      catalog.put(ProfileRegistry.NER_BUNDLE_ID, buildNerBundleCatalog());
    }
    if (docCategorizerRegistry.isAvailable()) {
      catalog.put(ProfileRegistry.DOCCAT_BUNDLE_ID, buildDoccatBundleCatalog());
    }
    if (sentimentRegistry.isAvailable()) {
      catalog.put(ProfileRegistry.SENTIMENT_BUNDLE_ID, buildSentimentBundleCatalog());
    }
    if (parserAvailable) {
      catalog.put(ProfileRegistry.PARSE_BUNDLE_ID, buildParseBundleCatalog());
    }
    if (chunker != null) {
      catalog.put(ProfileRegistry.CHUNK_BUNDLE_ID, buildChunkBundleCatalog());
    }
    return catalog;
  }

  private ModelBundleInfo buildChunkBundleCatalog() {
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
            "opennlp-models-sentdetect-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR))
        .addModels(classicModelDescriptor(
            "opennlp-models-tokenizer-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_TOKENIZER))
        .addModels(classicModelDescriptor(
            "opennlp-models-pos-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_POS_TAGGER))
        .addModels(ModelDescriptor.newBuilder()
            .setName("chunker")
            .setComponentType(ComponentType.COMPONENT_TYPE_CHUNKER)
            .addSupportedSteps(PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK)
            .setBackendId(OPENNLP_ME_BACKEND_ID)
            .build())
        .build();
  }

  private ModelBundleInfo buildParseBundleCatalog() {
    // Parsing consumes the English tokenizer's output, so the bundle constrains input to English;
    // the parser model is operator-supplied and its language is unknown to the server.
    return ModelBundleInfo.newBuilder()
        .setBundleId(ProfileRegistry.PARSE_BUNDLE_ID)
        .addSupportedLanguages(DEFAULT_LANGUAGE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_PARSE)
        .addModels(classicModelDescriptor(
            "opennlp-models-sentdetect-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR))
        .addModels(classicModelDescriptor(
            "opennlp-models-tokenizer-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_TOKENIZER))
        .addModels(ModelDescriptor.newBuilder()
            .setName("parser")
            .setComponentType(ComponentType.COMPONENT_TYPE_PARSER)
            .addSupportedSteps(PipelineStep.PIPELINE_STEP_PARSE)
            .setBackendId(OPENNLP_ME_BACKEND_ID)
            .build())
        .build();
  }

  private ModelBundleInfo buildNerBundleCatalog() {
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
            "opennlp-models-sentdetect-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR))
        .addModels(classicModelDescriptor(
            "opennlp-models-tokenizer-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_TOKENIZER));
    for (NerModel model : nameFinderRegistry.allModels()) {
      for (String entityType : model.entityTypes()) {
        bundle.addModels(ModelDescriptor.newBuilder()
            .setName(entityType)
            .setComponentType(ComponentType.COMPONENT_TYPE_NAME_FINDER)
            .addSupportedSteps(PipelineStep.PIPELINE_STEP_NER)
            .setBackendId(model.backendId())
            .build());
      }
    }
    return bundle.build();
  }

  private ModelBundleInfo buildDoccatBundleCatalog() {
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
            "opennlp-models-sentdetect-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR))
        .addModels(classicModelDescriptor(
            "opennlp-models-tokenizer-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_TOKENIZER));
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

  private ModelBundleInfo buildSentimentBundleCatalog() {
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
            "opennlp-models-sentdetect-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR))
        .addModels(classicModelDescriptor(
            "opennlp-models-tokenizer-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_TOKENIZER));
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
  private static ModelDescriptor classicModelDescriptor(String name, ComponentType type) {
    return ModelDescriptor.newBuilder()
        .setName(name)
        .setLocale(DEFAULT_LANGUAGE)
        .setComponentType(type)
        .addLanguages(DEFAULT_LANGUAGE)
        .setBackendId(OPENNLP_ME_BACKEND_ID)
        .build();
  }
}
