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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import opennlp.tools.models.ClassPathModelProvider;
import opennlp.tools.models.DefaultClassPathModelProvider;
import opennlp.tools.models.ModelType;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
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

  /** Backend id reported for models served by the classic OpenNLP maxent runtime. */
  private static final String OPENNLP_ME_BACKEND_ID = "opennlp-me";

  /** Name fragments identifying the bundled UD model binaries at the jar root. */
  private static final String BUNDLED_SENTENCE_MODEL_FRAGMENT = "-sentence-";
  private static final String BUNDLED_TOKENIZER_MODEL_FRAGMENT = "-tokens-";
  private static final String MODEL_FILE_SUFFIX = ".bin";

  private final ClassPathModelProvider modelProvider;
  private final Map<String, ModelBundleInfo> bundles;
  private final SentenceDetectorME sentenceDetector;
  private final TokenizerME tokenizer;
  private final EmbeddingProvider embeddingProvider;

  public ModelBundleCache(Map<String, String> configuration) {
    Objects.requireNonNull(configuration, "configuration");
    this.modelProvider = new DefaultClassPathModelProvider();
    this.sentenceDetector = loadSentenceDetector(configuration);
    this.tokenizer = loadTokenizer(configuration);
    this.embeddingProvider = EmbeddingProviderFactory.create(configuration);
    this.bundles = buildBundleCatalog();
  }

  public SentenceDetectorME getSentenceDetector() {
    return sentenceDetector;
  }

  public TokenizerME getTokenizer() {
    return tokenizer;
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

  private SentenceDetectorME loadSentenceDetector(Map<String, String> configuration) {
    try {
      final String configuredPath = configuration.get(KEY_SENTDETECT_PATH);
      final SentenceModel model;
      if (configuredPath != null && !configuredPath.isBlank()) {
        try (InputStream input = new FileInputStream(configuredPath)) {
          model = new SentenceModel(input);
        }
      } else {
        SentenceModel resolved =
            modelProvider.load(DEFAULT_LANGUAGE, ModelType.SENTENCE_DETECTOR, SentenceModel.class);
        if (resolved == null) {
          final InputStream bundled = openBundledModel(BUNDLED_SENTENCE_MODEL_FRAGMENT);
          if (bundled == null) {
            throw AnalysisException.notFound(
                "No sentence detector model available for language '" + DEFAULT_LANGUAGE
                    + "'. Configure '" + KEY_SENTDETECT_PATH + "' or add an opennlp-models-sentdetect"
                    + " jar to the classpath.");
          }
          logger.info("Loaded sentence detector model bundled in the server jar");
          try (InputStream input = bundled) {
            resolved = new SentenceModel(input);
          }
        }
        model = resolved;
      }
      return new SentenceDetectorME(model);
    } catch (IOException e) {
      throw AnalysisException.internal("Failed to load sentence detector model", e);
    }
  }

  private TokenizerME loadTokenizer(Map<String, String> configuration) {
    try {
      final String configuredPath = configuration.get(KEY_TOKENIZER_PATH);
      final TokenizerModel model;
      if (configuredPath != null && !configuredPath.isBlank()) {
        try (InputStream input = new FileInputStream(configuredPath)) {
          model = new TokenizerModel(input);
        }
      } else {
        TokenizerModel resolved =
            modelProvider.load(DEFAULT_LANGUAGE, ModelType.TOKENIZER, TokenizerModel.class);
        if (resolved == null) {
          final InputStream bundled = openBundledModel(BUNDLED_TOKENIZER_MODEL_FRAGMENT);
          if (bundled == null) {
            throw AnalysisException.notFound(
                "No tokenizer model available for language '" + DEFAULT_LANGUAGE
                    + "'. Configure '" + KEY_TOKENIZER_PATH + "' or add an opennlp-models-tokenizer"
                    + " jar to the classpath.");
          }
          logger.info("Loaded tokenizer model bundled in the server jar");
          try (InputStream input = bundled) {
            resolved = new TokenizerModel(input);
          }
        }
        model = resolved;
      }
      return new TokenizerME(model);
    } catch (IOException e) {
      throw AnalysisException.internal("Failed to load tokenizer model", e);
    }
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
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSupportedSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addModels(ModelDescriptor.newBuilder()
            .setName("opennlp-models-sentdetect-" + DEFAULT_LANGUAGE)
            .setLocale(DEFAULT_LANGUAGE)
            .setComponentType(ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR)
            .addLanguages(DEFAULT_LANGUAGE)
            .setBackendId(OPENNLP_ME_BACKEND_ID)
            .build())
        .addModels(ModelDescriptor.newBuilder()
            .setName("opennlp-models-tokenizer-" + DEFAULT_LANGUAGE)
            .setLocale(DEFAULT_LANGUAGE)
            .setComponentType(ComponentType.COMPONENT_TYPE_TOKENIZER)
            .addLanguages(DEFAULT_LANGUAGE)
            .setBackendId(OPENNLP_ME_BACKEND_ID)
            .build());
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
}
