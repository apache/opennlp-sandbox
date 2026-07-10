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

import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagFormat;
import opennlp.tools.postag.POSTagFormatMapper;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
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
 * <p>Models resolve in three steps: optional explicit paths in configuration, then
 * {@code model.properties} descriptors on the classpath from {@code opennlp-models-*}
 * runtime deps, and finally the model binaries bundled inside the shaded server jar itself.
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
  // The *Model artifacts are immutable and shared; the *ME decoders keep per-call state
  // (probabilities of the last run, beam search buffers) and are NOT safe for concurrent
  // use, so every server thread decodes with its own instance over the shared model.
  private final POSModel posModel;
  private final ThreadLocal<SentenceDetectorME> sentenceDetector;
  private final ThreadLocal<TokenizerME> tokenizer;
  private final ThreadLocal<POSTaggerME> posTagger;
  private final ThreadLocal<LemmatizerME> lemmatizer;
  private final ThreadLocal<LanguageDetectorME> languageDetector;
  private final EmbeddingProvider embeddingProvider;
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
    final LoadedArtifact<SentenceModel> loadedSentence = loadModel(configuration,
        KEY_SENTDETECT_PATH, BUNDLED_SENTENCE_MODEL_FRAGMENT, "sentence detector",
        SentenceModel::new);
    final LoadedArtifact<TokenizerModel> loadedTokenizer = loadModel(configuration,
        KEY_TOKENIZER_PATH, BUNDLED_TOKENIZER_MODEL_FRAGMENT, "tokenizer", TokenizerModel::new);
    final LoadedArtifact<POSModel> loadedPos = loadModel(configuration,
        KEY_POS_TAGGER_PATH, BUNDLED_POS_MODEL_FRAGMENT, "POS tagger", POSModel::new);
    final LoadedArtifact<LemmatizerModel> loadedLemma = loadModel(configuration,
        KEY_LEMMATIZER_PATH, BUNDLED_LEMMATIZER_MODEL_FRAGMENT, "lemmatizer", LemmatizerModel::new);
    final LoadedArtifact<LanguageDetectorModel> loadedLangDetect =
        loadLanguageDetectorModel(configuration);
    final SentenceModel sentenceModel = loadedSentence.model();
    this.sentenceDetector = ThreadLocal.withInitial(() -> new SentenceDetectorME(sentenceModel));
    final TokenizerModel tokenizerModel = loadedTokenizer.model();
    this.tokenizer = ThreadLocal.withInitial(() -> new TokenizerME(tokenizerModel));
    this.posModel = loadedPos.model();
    this.posTagger = ThreadLocal.withInitial(() -> new POSTaggerME(posModel));
    final LemmatizerModel lemmatizerModel = loadedLemma.model();
    this.lemmatizer = ThreadLocal.withInitial(() -> new LemmatizerME(lemmatizerModel));
    final LanguageDetectorModel languageDetectorModel = loadedLangDetect.model();
    this.languageDetector =
        ThreadLocal.withInitial(() -> new LanguageDetectorME(languageDetectorModel));
    // The embedding provider and the three registries may hold native resources (ONNX sessions,
    // remote connections). If a later load fails, release the ones already created so a failed
    // startup does not leak native sessions.
    EmbeddingProvider embeddingProvider = null;
    NameFinderRegistry nameFinderRegistry = null;
    DocCategorizerRegistry docCategorizerRegistry = null;
    SentimentRegistry sentimentRegistry = null;
    ChunkerRegistry chunkerRegistry = null;
    boolean constructed = false;
    try {
      embeddingProvider = EmbeddingProviderFactory.create(configuration);
      // The registry may call the detector from any of its threads; hand it a view that
      // resolves to the calling thread's own instance rather than one shared decoder.
      nameFinderRegistry = NameFinderRegistry.create(configuration, new SentenceDetector() {
        @Override
        public String[] sentDetect(CharSequence text) {
          return sentenceDetector.get().sentDetect(text);
        }

        @Override
        public Span[] sentPosDetect(CharSequence text) {
          return sentenceDetector.get().sentPosDetect(text);
        }
      });
      docCategorizerRegistry = DocCategorizerRegistry.create(configuration);
      sentimentRegistry = SentimentRegistry.create(configuration);
      chunkerRegistry = ChunkerRegistry.create(configuration);
      this.embeddingProvider = embeddingProvider;
      this.nameFinderRegistry = nameFinderRegistry;
      this.docCategorizerRegistry = docCategorizerRegistry;
      this.sentimentRegistry = sentimentRegistry;
      this.chunkerRegistry = chunkerRegistry;
      this.parserRegistry = ParserRegistry.create(configuration);
      this.bundles = buildBundleCatalog(
          loadedLangDetect.hash(), loadedSentence.hash(), loadedTokenizer.hash(),
          loadedPos.hash(), loadedLemma.hash());
      this.artifactRegistry = buildArtifactRegistry(
          loadedLangDetect.hash(), loadedSentence.hash(), loadedTokenizer.hash(),
          loadedPos.hash(), loadedLemma.hash());
      constructed = true;
    } finally {
      if (!constructed) {
        closeQuietly(chunkerRegistry);
        closeQuietly(sentimentRegistry);
        closeQuietly(docCategorizerRegistry);
        closeQuietly(nameFinderRegistry);
        closeQuietly(embeddingProvider);
      }
    }
  }

  /**
   * Returns the calling thread's sentence detector over the shared model. Always available
   * (bundled default when unconfigured). Per-thread because {@link SentenceDetectorME} keeps
   * per-call state (e.g. the probabilities of the last run) and is not safe for concurrent use.
   *
   * @return The calling thread's sentence detector. Never {@code null}.
   */
  public SentenceDetectorME getSentenceDetector() {
    return sentenceDetector.get();
  }

  /**
   * Returns the calling thread's tokenizer over the shared model. Always available (bundled
   * default when unconfigured). Per-thread because {@link TokenizerME} keeps per-call state
   * and is not safe for concurrent use.
   *
   * @return The calling thread's tokenizer. Never {@code null}.
   */
  public TokenizerME getTokenizer() {
    return tokenizer.get();
  }

  /**
   * Returns the calling thread's POS tagger over the shared model. Always available (bundled
   * default when unconfigured). Per-thread because {@link POSTaggerME} keeps per-call state
   * and is not safe for concurrent use.
   *
   * @return The calling thread's POS tagger. Never {@code null}.
   */
  public POSTaggerME getPosTagger() {
    return posTagger.get();
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
    if (requestedFormat == org.apache.opennlp.grpc.v1.POSTagFormat.POS_TAG_FORMAT_CUSTOM) {
      throw AnalysisException.unimplemented(
          "pos_tag_format CUSTOM requires a client-supplied tag mapping; not supported");
    }
    final POSTagFormat outputFormat = switch (requestedFormat) {
      case POS_TAG_FORMAT_UD -> POSTagFormat.UD;
      case POS_TAG_FORMAT_PENN -> POSTagFormat.PENN;
      case POS_TAG_FORMAT_UNSPECIFIED, UNRECOGNIZED ->
          POSTagFormatMapper.guessFormat(posModel);
      default -> POSTagFormatMapper.guessFormat(posModel);
    };
    return new POSTaggerME(posModel, outputFormat);
  }

  /**
   * Returns the calling thread's lemmatizer over the shared model. Always available (bundled
   * default when unconfigured). Per-thread because {@link LemmatizerME} keeps per-call state
   * and is not safe for concurrent use.
   *
   * @return The calling thread's lemmatizer. Never {@code null}.
   */
  public LemmatizerME getLemmatizer() {
    return lemmatizer.get();
  }

  /**
   * Returns the calling thread's language detector over the shared model. Always available
   * (bundled default when unconfigured). Per-thread because {@link LanguageDetectorME} keeps
   * per-call state and is not safe for concurrent use.
   *
   * @return The calling thread's language detector. Never {@code null}.
   */
  public LanguageDetectorME getLanguageDetector() {
    return languageDetector.get();
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
   * Classpath-resolved artifact bytes and hash.
   *
   * @param bytes The model artifact bytes. Never {@code null}.
   * @param hash  The lowercase hex SHA-256 digest. Never {@code null}.
   */
  private record ClasspathArtifact(byte[] bytes, String hash) {
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
  private LoadedArtifact<LanguageDetectorModel> loadLanguageDetectorModel(
      Map<String, String> configuration) {
    try {
      final String configuredPath = configuration.get(KEY_LANGDETECT_PATH);
      if (configuredPath != null && !configuredPath.isBlank()) {
        final byte[] bytes = Files.readAllBytes(Path.of(configuredPath));
        return new LoadedArtifact<>(new LanguageDetectorModel(new ByteArrayInputStream(bytes)),
            ModelArtifactHasher.sha256Hex(bytes));
      }
      byte[] classpathBytes = findClasspathLanguageDetectorBytes();
      String classpathHash = null;
      if (classpathBytes == null) {
        final InputStream bundled = openBundledModel(BUNDLED_LANGDETECT_MODEL_FRAGMENT);
        if (bundled != null) {
          try (InputStream input = bundled) {
            classpathBytes = input.readAllBytes();
          }
        }
      } else {
        classpathHash = findClasspathLanguageDetectorHash();
      }
      if (classpathBytes == null) {
        throw AnalysisException.notFound(
            "No language detector model available. Configure '" + KEY_LANGDETECT_PATH
                + "' or add the opennlp-models-langdetect jar to the classpath.");
      }
      final String hash = classpathHash != null
          ? classpathHash
          : ModelArtifactHasher.sha256Hex(classpathBytes);
      return new LoadedArtifact<>(
          new LanguageDetectorModel(new ByteArrayInputStream(classpathBytes)),
          hash);
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
   * Locates a classic model binary through {@code model.properties} descriptors on the classpath.
   *
   * @param language     The model language tag to match, e.g. {@code "en"}.
   * @param nameFragment A substring that must appear in the {@code model.name} entry.
   *
   * @return The artifact bytes and hash, or {@code null} when no matching descriptor is found.
   *
   * @throws IOException If a descriptor or model stream cannot be read.
   */
  private static ClasspathArtifact findClasspathArtifact(String language, String nameFragment)
      throws IOException {
    final ClassLoader classLoader = ModelBundleCache.class.getClassLoader();
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
        final String declaredHash = properties.getProperty("model.sha256", "").trim().toLowerCase();
        final String hash = declaredHash.isBlank()
            ? ModelArtifactHasher.sha256Hex(bytes)
            : declaredHash;
        return new ClasspathArtifact(bytes, hash);
      }
    }
    return null;
  }

  /**
   * Locates the language-detector binary bytes on the classpath.
   *
   * @return The model bytes, or {@code null} when no matching descriptor is found.
   *
   * @throws IOException If a descriptor or model stream cannot be read.
   */
  private static byte[] findClasspathLanguageDetectorBytes() throws IOException {
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
        try (InputStream model = classLoader.getResourceAsStream(modelName)) {
          if (model != null) {
            return model.readAllBytes();
          }
        }
      }
    }
    return null;
  }

  /**
   * Reads the declared {@code model.sha256} for the classpath language-detector artifact.
   *
   * @return The lowercase hex digest, or {@code null} when no descriptor declares one.
   *
   * @throws IOException If a descriptor stream cannot be read.
   */
  private static String findClasspathLanguageDetectorHash() throws IOException {
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
        final String declaredHash = properties.getProperty("model.sha256", "").trim().toLowerCase();
        if (!declaredHash.isBlank()) {
          return declaredHash;
        }
      }
    }
    return null;
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
      String lemmaHash) {
    final ModelArtifactRegistry.Builder builder = ModelArtifactRegistry.builder()
        .register(ComponentType.COMPONENT_TYPE_LANGUAGE_DETECTOR, langDetectHash,
            "opennlp-models-langdetect")
        .register(ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR, sentenceHash,
            "opennlp-models-sentdetect-" + DEFAULT_LANGUAGE)
        .register(ComponentType.COMPONENT_TYPE_TOKENIZER, tokenizerHash,
            "opennlp-models-tokenizer-" + DEFAULT_LANGUAGE)
        .register(ComponentType.COMPONENT_TYPE_POS_TAGGER, posHash,
            "opennlp-models-pos-" + DEFAULT_LANGUAGE)
        .register(ComponentType.COMPONENT_TYPE_LEMMATIZER, lemmaHash,
            "opennlp-models-lemmatizer-" + DEFAULT_LANGUAGE);
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

  private Map<String, ModelBundleInfo> buildBundleCatalog(
      String langDetectHash,
      String sentenceHash,
      String tokenizerHash,
      String posHash,
      String lemmaHash) {
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
            "opennlp-models-sentdetect-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR,
            DEFAULT_LANGUAGE,
            sentenceHash))
        .addModels(classicModelDescriptor(
            "opennlp-models-tokenizer-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_TOKENIZER,
            DEFAULT_LANGUAGE,
            tokenizerHash))
        .addModels(classicModelDescriptor(
            "opennlp-models-pos-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_POS_TAGGER,
            DEFAULT_LANGUAGE,
            posHash))
        .addModels(classicModelDescriptor(
            "opennlp-models-lemmatizer-" + DEFAULT_LANGUAGE,
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
            .setBackendId(embeddingProvider.backendId(modelId));
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
    if (chunkerRegistry.isAvailable()) {
      catalog.put(ProfileRegistry.CHUNK_BUNDLE_ID,
          buildChunkBundleCatalog(sentenceHash, tokenizerHash, posHash));
    }
    return catalog;
  }

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
            "opennlp-models-sentdetect-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR,
            DEFAULT_LANGUAGE,
            sentenceHash))
        .addModels(classicModelDescriptor(
            "opennlp-models-tokenizer-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_TOKENIZER,
            DEFAULT_LANGUAGE,
            tokenizerHash))
        .addModels(classicModelDescriptor(
            "opennlp-models-pos-" + DEFAULT_LANGUAGE,
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
            "opennlp-models-sentdetect-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR,
            DEFAULT_LANGUAGE,
            sentenceHash))
        .addModels(classicModelDescriptor(
            "opennlp-models-tokenizer-" + DEFAULT_LANGUAGE,
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
            "opennlp-models-sentdetect-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR,
            DEFAULT_LANGUAGE,
            sentenceHash))
        .addModels(classicModelDescriptor(
            "opennlp-models-tokenizer-" + DEFAULT_LANGUAGE,
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
            "opennlp-models-sentdetect-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR,
            DEFAULT_LANGUAGE,
            sentenceHash))
        .addModels(classicModelDescriptor(
            "opennlp-models-tokenizer-" + DEFAULT_LANGUAGE,
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
            "opennlp-models-sentdetect-" + DEFAULT_LANGUAGE,
            ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR,
            DEFAULT_LANGUAGE,
            sentenceHash))
        .addModels(classicModelDescriptor(
            "opennlp-models-tokenizer-" + DEFAULT_LANGUAGE,
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
