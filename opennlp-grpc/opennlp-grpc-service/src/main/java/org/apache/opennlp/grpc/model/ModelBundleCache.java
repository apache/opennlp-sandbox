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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
 * <p>Models resolve from optional explicit paths in configuration, otherwise from the
 * classpath via {@link DefaultClassPathModelProvider} and {@code opennlp-models-*} runtime deps.
 */
public final class ModelBundleCache {

  private static final Logger logger = LoggerFactory.getLogger(ModelBundleCache.class);

  private static final String DEFAULT_LANGUAGE = "en";
  private static final String KEY_SENTDETECT_PATH = "model.sentence_detector.path";
  private static final String KEY_TOKENIZER_PATH = "model.tokenizer.path";

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
        model = modelProvider.load(DEFAULT_LANGUAGE, ModelType.SENTENCE_DETECTOR, SentenceModel.class);
        if (model == null) {
          throw AnalysisException.notFound(
              "No sentence detector model available for language '" + DEFAULT_LANGUAGE + "'");
        }
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
        model = modelProvider.load(DEFAULT_LANGUAGE, ModelType.TOKENIZER, TokenizerModel.class);
        if (model == null) {
          throw AnalysisException.notFound(
              "No tokenizer model available for language '" + DEFAULT_LANGUAGE + "'");
        }
      }
      return new TokenizerME(model);
    } catch (IOException e) {
      throw AnalysisException.internal("Failed to load tokenizer model", e);
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
            .build())
        .addModels(ModelDescriptor.newBuilder()
            .setName("opennlp-models-tokenizer-" + DEFAULT_LANGUAGE)
            .setLocale(DEFAULT_LANGUAGE)
            .setComponentType(ComponentType.COMPONENT_TYPE_TOKENIZER)
            .addLanguages(DEFAULT_LANGUAGE)
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
            .build());
      }
    }
    final Map<String, ModelBundleInfo> catalog = new HashMap<>();
    catalog.put(ProfileRegistry.DEFAULT_BUNDLE_ID, bundle.build());
    return catalog;
  }
}
