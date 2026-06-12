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
    this.embeddingProvider = EmbeddingProviderFactory.create(configuration);
    this.bundles = buildBundleCatalog();
  }

  public SentenceDetectorME getSentenceDetector() {
    return sentenceDetector;
  }

  public TokenizerME getTokenizer() {
    return tokenizer;
  }

  public POSTaggerME getPosTagger() {
    return posTagger;
  }

  public LemmatizerME getLemmatizer() {
    return lemmatizer;
  }

  public LanguageDetectorME getLanguageDetector() {
    return languageDetector;
  }

  public List<ModelBundleInfo> listBundles() {
    return new ArrayList<>(bundles.values());
  }

  public EmbeddingProvider getEmbeddingProvider() {
    return embeddingProvider;
  }

  /**
   * Releases resources held by the embedding provider. Failures are logged so that the
   * remaining server shutdown is not interrupted.
   */
  public void close() {
    if (embeddingProvider instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception e) {
        logger.warn("Failed to close embedding provider", e);
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
    } catch (IOException e) {
      throw AnalysisException.internal("Failed to load language detector model", e);
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
        bundle.addModels(ModelDescriptor.newBuilder()
            .setName(modelId)
            .setLocale(DEFAULT_LANGUAGE)
            .setComponentType(ComponentType.COMPONENT_TYPE_EMBEDDER)
            .addLanguages(DEFAULT_LANGUAGE)
            .setEmbeddingDimension(embeddingProvider.embeddingDimension(modelId))
            .setBackendId(embeddingProvider.backendId())
            .build());
      }
    }
    final Map<String, ModelBundleInfo> catalog = new HashMap<>();
    catalog.put(ProfileRegistry.DEFAULT_BUNDLE_ID, bundle.build());
    return catalog;
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
