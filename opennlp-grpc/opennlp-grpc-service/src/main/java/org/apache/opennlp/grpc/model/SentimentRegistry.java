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

import java.util.List;
import java.util.Map;

/**
 * Catalog of sentence-level sentiment classifiers, keyed by model id.
 *
 * <p>Sentiment is document categorization applied per sentence, so it reuses the
 * {@link DocCategorizerModel} abstraction and the {@link DocCategorizerBackendFactory} SPI.
 * Models are configured under a dedicated {@code model.sentiment.*} / {@code model.sentiment_dl.*}
 * namespace and held in their own registry, independent of the document-level
 * {@link DocCategorizerRegistry}; a third-party backend therefore serves both capabilities
 * without being written twice. The selected model classifies each sentence, and its best
 * category and that category's score become {@code sentiment_label} and
 * {@code sentiment_confidence}.</p>
 */
public final class SentimentRegistry implements AutoCloseable {

  /** Configuration namespace token for sentiment models. */
  static final String NAMESPACE = "sentiment";

  /** Prefix for classic sentiment path entries: {@code model.sentiment.<id>.path}. */
  public static final String KEY_PREFIX = "model." + NAMESPACE + ".";

  /** Suffix completing a classic path key. */
  public static final String KEY_SUFFIX = ".path";

  /** Prefix for ONNX sentiment entries: {@code model.sentiment_dl.<id>.*}. */
  public static final String KEY_DL_PREFIX = "model." + NAMESPACE + "_dl.";

  /** Configuration key selecting the default sentiment model when several are configured. */
  public static final String KEY_DEFAULT_ID = "model." + NAMESPACE + ".default_id";

  private final DocCategorizerRegistry delegate;

  private SentimentRegistry(DocCategorizerRegistry delegate) {
    this.delegate = delegate;
  }

  /**
   * Loads all sentiment models configured under the {@code model.sentiment.*} namespace by
   * delegating to the shared document-categorizer backends.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return A registry, possibly empty when no sentiment model is configured.
   *
   * @throws org.apache.opennlp.grpc.processor.AnalysisException If a backend's configuration is
   *     invalid, a model fails to load, or {@code model.sentiment.default_id} names an unknown
   *     model.
   */
  public static SentimentRegistry create(Map<String, String> configuration) {
    return new SentimentRegistry(
        DocCategorizerRegistry.createForNamespace(NAMESPACE, configuration));
  }

  public boolean isAvailable() {
    return delegate.isAvailable();
  }

  /** @return All configured sentiment model ids, in registration order. */
  public List<String> modelIds() {
    return delegate.modelIds();
  }

  public boolean supportsModel(String modelId) {
    return delegate.supportsModel(modelId);
  }

  /** @return All configured sentiment models, in registration order, for catalog reporting. */
  public List<DocCategorizerModel> allModels() {
    return delegate.allModels();
  }

  public DocCategorizerModel get(String modelId) {
    return delegate.get(modelId);
  }

  /**
   * Resolves which sentiment model to run: the configured {@code default_id}, or the sole model
   * when only one is configured.
   *
   * @return The resolved model id, or {@code null} when none is configured or the choice is
   *     ambiguous (several models, no {@code default_id}).
   */
  public String resolveDefaultModelId() {
    return delegate.resolveDefaultModelId();
  }

  /** Closes any sentiment model that holds native resources (e.g. ONNX sessions). */
  @Override
  public void close() {
    delegate.close();
  }
}
