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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ServiceLoader;

import org.apache.opennlp.grpc.processor.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Catalog of {@link DocCategorizerModel} document classifiers, keyed by model id.
 *
 * <p>Models are produced by {@link DocCategorizerBackendFactory} backends discovered via
 * {@link ServiceLoader}: the built-in classic ({@code model.doccat.<id>.path}) and ONNX
 * ({@code model.doccat_dl.<id>.*}) backends, plus any third-party backend whose jar registers a
 * {@code DocCategorizerBackendFactory}. Several backends may contribute models at once.</p>
 *
 * <p>Because the response carries a single document {@code classification}, one model runs per
 * request: the {@code model.doccat.default_id} entry selects it, or the sole configured model is
 * used when only one exists. Each model is loaded once and shared across requests.</p>
 */
public final class DocCategorizerRegistry implements AutoCloseable {

  /** Configuration key selecting the default document categorizer when several are configured. */
  public static final String KEY_DEFAULT_ID = "model.doccat.default_id";

  private static final Logger logger = LoggerFactory.getLogger(DocCategorizerRegistry.class);

  private final Map<String, DocCategorizerModel> modelsById;
  private final String configuredDefaultId;

  private DocCategorizerRegistry(
      Map<String, DocCategorizerModel> modelsById, String configuredDefaultId) {
    this.modelsById = Map.copyOf(modelsById);
    this.configuredDefaultId = configuredDefaultId;
  }

  /**
   * Canonical form of a model id: trimmed and lower-cased, so configuration keys and the
   * {@code default_id} selector match case-insensitively.
   *
   * @return The normalized id, or {@code null} if {@code id} is {@code null}.
   */
  public static String normalize(String id) {
    return id == null ? null : id.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Loads all document categorizers by discovering {@link DocCategorizerBackendFactory} backends
   * via {@link ServiceLoader} and aggregating the models each contributes.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return A registry, possibly empty when no document categorizer is configured.
   *
   * @throws AnalysisException If a backend's configuration is invalid, a model fails to load, or
   *     {@code model.doccat.default_id} names an unknown model.
   */
  public static DocCategorizerRegistry create(Map<String, String> configuration) {
    if (configuration == null) {
      throw new NullPointerException("configuration");
    }
    final Map<String, DocCategorizerModel> modelsById = new LinkedHashMap<>();
    final Set<String> seenFactories = new HashSet<>();
    for (DocCategorizerBackendFactory factory : ServiceLoader.load(
        DocCategorizerBackendFactory.class, DocCategorizerRegistry.class.getClassLoader())) {
      if (!seenFactories.add(factory.factoryId())) {
        logger.warn("Ignoring duplicate doc categorizer backend factory '{}' ({})",
            factory.factoryId(), factory.getClass().getName());
        continue;
      }
      for (DocCategorizerModel model : factory.create(configuration)) {
        if (modelsById.putIfAbsent(model.id(), model) != null) {
          throw AnalysisException.invalidArgument(
              "Duplicate document categorizer model id: " + model.id());
        }
      }
    }

    final String defaultId = normalize(configuration.get(KEY_DEFAULT_ID));
    if (defaultId != null && !defaultId.isEmpty() && !modelsById.containsKey(defaultId)) {
      throw AnalysisException.invalidArgument(KEY_DEFAULT_ID + " names unknown document "
          + "categorizer '" + defaultId + "'; configured ids: " + modelsById.keySet());
    }
    return new DocCategorizerRegistry(
        modelsById, defaultId == null || defaultId.isEmpty() ? null : defaultId);
  }

  public boolean isAvailable() {
    return !modelsById.isEmpty();
  }

  /** @return All configured categorizer ids, in registration order. */
  public List<String> modelIds() {
    return List.copyOf(modelsById.keySet());
  }

  public boolean supportsModel(String modelId) {
    return modelId != null && modelsById.containsKey(normalize(modelId));
  }

  /** @return All configured categorizers, in registration order, for catalog reporting. */
  public List<DocCategorizerModel> allModels() {
    return List.copyOf(modelsById.values());
  }

  public DocCategorizerModel get(String modelId) {
    return modelId == null ? null : modelsById.get(normalize(modelId));
  }

  /**
   * Resolves which categorizer to run: the configured {@code default_id}, or the sole model when
   * only one is configured.
   *
   * @return The resolved model id, or {@code null} when none is configured or the choice is
   *     ambiguous (several models, no {@code default_id}).
   */
  public String resolveDefaultModelId() {
    if (configuredDefaultId != null) {
      return configuredDefaultId;
    }
    if (modelsById.size() == 1) {
      return modelsById.keySet().iterator().next();
    }
    return null;
  }

  /** Closes any categorizer that holds native resources (e.g. ONNX sessions). */
  @Override
  public void close() {
    for (DocCategorizerModel model : modelsById.values()) {
      if (model instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception e) {
          logger.warn("Failed to close document categorizer '{}'", model.id(), e);
        }
      }
    }
  }
}
