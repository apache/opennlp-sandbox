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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.opennlp.grpc.spi.model.DocCategorizerModel;
import org.apache.opennlp.grpc.spi.model.ModelConfigSupport;
import org.apache.opennlp.grpc.spi.model.DocCategorizerBackendFactory;

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

  /**
   * Canonical configuration namespace. Built-in backends read {@code model.doccat.*} /
   * {@code model.doccat_dl.*} keys; other namespaces (e.g. {@code sentiment}) are canonicalized
   * onto this one before reaching the factories, so one backend serves every namespace.
   */
  static final String DEFAULT_NAMESPACE = "doccat";

  /** Configuration key selecting the default document categorizer when several are configured. */
  public static final String KEY_DEFAULT_ID = "model." + DEFAULT_NAMESPACE + ".default_id";

  /**
   * Prefix for ONNX document categorizer entries in the canonical namespace
   * ({@code model.doccat_dl.<id>.<attr>}). The keys are parsed by the ONNX backend shipped in
   * the {@code opennlp-grpc-dl} add-on; the registry itself only verifies the namespace is
   * claimed by a discovered backend.
   */
  public static final String KEY_DL_PREFIX = "model." + DEFAULT_NAMESPACE + "_dl.";

  /** Factory id under which the ONNX add-on registers its backends. */
  static final String DL_FACTORY_ID = "onnx";

  private static final Logger logger = LoggerFactory.getLogger(DocCategorizerRegistry.class);

  private final String namespace;
  private final Map<String, DocCategorizerModel> modelsById;
  private final String configuredDefaultId;

  private DocCategorizerRegistry(
      String namespace, Map<String, DocCategorizerModel> modelsById, String configuredDefaultId) {
    this.namespace = namespace;
    this.modelsById = Map.copyOf(modelsById);
    this.configuredDefaultId = configuredDefaultId;
  }

  /**
   * Canonical form of a model id: trimmed and lower-cased, so configuration keys and the
   * {@code default_id} selector match case-insensitively.
   *
   * @param id The raw model id to normalize. May be {@code null}.
   *
   * @return The normalized id, or {@code null} if {@code id} is {@code null}.
   */
  public static String normalize(String id) {
    return ModelConfigSupport.normalize(id);
  }

  /**
   * Loads all document categorizers by discovering {@link DocCategorizerBackendFactory} backends
   * via {@link ServiceLoader} and aggregating the models each contributes.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return A registry, possibly empty when no document categorizer is configured.
   *
   * @throws AnalysisException If two factories declare the same factory id, a backend's
   *     configuration is invalid, a model fails to load, or
   *     {@code model.doccat.default_id} names an unknown model.
   */
  public static DocCategorizerRegistry create(Map<String, String> configuration) {
    return createForNamespace(DEFAULT_NAMESPACE, configuration);
  }

  /**
   * Loads classifiers configured under {@code model.<namespace>.*} / {@code model.<namespace>_dl.*}.
   * Those keys are canonicalized onto the built-in {@code model.doccat*} namespace before the
   * {@link DocCategorizerBackendFactory} backends run, so the same backends (built-in and
   * third-party) serve every namespace without modification. Keys belonging to a different
   * namespace are ignored, keeping the namespaces' catalogs isolated.
   *
   * @param namespace The configuration namespace token, e.g. {@code "doccat"} or {@code "sentiment"}.
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return A registry, possibly empty when no model is configured under the namespace.
   *
   * @throws AnalysisException If two factories declare the same factory id, a backend's
   *     configuration is invalid, a model fails to load, or
   *     {@code model.<namespace>.default_id} names an unknown model.
   */
  static DocCategorizerRegistry createForNamespace(
      String namespace, Map<String, String> configuration) {
    return createForNamespace(namespace, configuration,
        ServiceLoader.load(DocCategorizerBackendFactory.class));
  }

  /**
   * Loads from the given factories instead of {@link ServiceLoader} discovery; package-private
   * so tests can drive the factory set directly.
   *
   * @param namespace The configuration namespace token, e.g. {@code "doccat"} or
   *     {@code "sentiment"}.
   * @param configuration The server configuration. Must not be {@code null}.
   * @param factories The backend factories to load models from.
   *
   * @return A registry, possibly empty when no model is configured under the namespace.
   *
   * @throws AnalysisException If two factories declare the same factory id, a backend's
   *     configuration is invalid, a model fails to load, or
   *     {@code model.<namespace>.default_id} names an unknown model.
   */
  static DocCategorizerRegistry createForNamespace(
      String namespace, Map<String, String> configuration,
      Iterable<DocCategorizerBackendFactory> factories) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    final Map<String, String> canonical = canonicalize(namespace, configuration);
    final Map<String, DocCategorizerModel> modelsById = new LinkedHashMap<>();
    final Map<String, String> seenFactories = new LinkedHashMap<>();
    try {
      for (DocCategorizerBackendFactory factory : factories) {
        final String previous =
            seenFactories.putIfAbsent(factory.factoryId(), factory.getClass().getName());
        if (previous != null) {
          throw AnalysisException.invalidArgument(
              "Doc categorizer backend factory id '" + factory.factoryId()
                  + "' is declared by both " + previous + " and " + factory.getClass().getName());
        }
        for (DocCategorizerModel model : factory.create(canonical)) {
          // Register under the normalized id so a backend that returns a mixed-case id is still
          // found by get()/supportsModel(), which look up by the normalized form.
          if (modelsById.putIfAbsent(normalize(model.id()), model) != null) {
            throw AnalysisException.invalidArgument(
                "Duplicate " + namespace + " model id: " + model.id());
          }
        }
      }
      requireDlBackendWhenConfigured(namespace, canonical, seenFactories.keySet());
    } catch (RuntimeException e) {
      // A factory that throws after earlier factories loaded models must not leak the native
      // sessions those models hold; the half-built registry can never be closed by the caller.
      try {
        closeModels(modelsById.values());
      } catch (RuntimeException closeFailure) {
        e.addSuppressed(closeFailure);
      }
      throw e;
    }

    final String defaultIdKey = "model." + namespace + ".default_id";
    final String defaultId = normalize(configuration.get(defaultIdKey));
    if (defaultId != null && !defaultId.isEmpty() && !modelsById.containsKey(defaultId)) {
      throw AnalysisException.invalidArgument(defaultIdKey + " names unknown " + namespace
          + " model '" + defaultId + "'; configured ids: " + modelsById.keySet());
    }
    return new DocCategorizerRegistry(
        namespace, modelsById, defaultId == null || defaultId.isEmpty() ? null : defaultId);
  }

  /**
   * Fails loud when {@code model.<namespace>_dl.*} keys are configured but no discovered backend
   * claims the ONNX namespace, instead of silently ignoring the models the operator asked for.
   *
   * @param namespace The original configuration namespace token, for the error message.
   * @param canonical The canonicalized configuration.
   * @param factoryIds The ids of the discovered backend factories.
   *
   * @throws AnalysisException {@code FAILED_PRECONDITION} if ONNX keys exist with no backend.
   */
  private static void requireDlBackendWhenConfigured(
      String namespace, Map<String, String> canonical, Set<String> factoryIds) {
    if (factoryIds.contains(DL_FACTORY_ID)) {
      return;
    }
    for (String key : canonical.keySet()) {
      if (key.startsWith(KEY_DL_PREFIX)) {
        throw AnalysisException.failedPrecondition(
            "model." + namespace + "_dl.* is configured but no ONNX document categorizer "
                + "backend is on the classpath; add the opennlp-grpc-dl jar (cpu or gpu flavor)");
      }
    }
  }

  /**
   * Rewrites {@code model.<namespace>*} configuration keys onto the canonical {@code model.doccat*}
   * namespace the built-in backends read, dropping every key that belongs to another namespace.
   * For the canonical namespace this keeps only its own keys; for an aliased namespace (e.g.
   * {@code sentiment}) it both renames the keys and isolates them from the canonical models.
   */
  private static Map<String, String> canonicalize(
      String namespace, Map<String, String> configuration) {
    final String token = "model." + namespace;
    final String canonicalPrefix = "model." + DEFAULT_NAMESPACE;
    final Map<String, String> canonical = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      // A '.' suffix scopes the classic/default keys; a '_' suffix scopes a backend sub-namespace
      // (e.g. model.<ns>_dl.* or a third-party model.<ns>_remote.*). Both are canonicalized.
      if (key.startsWith(token + ".") || key.startsWith(token + "_")) {
        canonical.put(canonicalPrefix + key.substring(token.length()), entry.getValue());
      }
    }
    return canonical;
  }

  /**
   * Reports whether any categorizer is configured.
   *
   * @return {@code true} when at least one model is registered.
   */
  public boolean isAvailable() {
    return !modelsById.isEmpty();
  }

  /**
   * Returns all configured categorizer ids, in registration order.
   *
   * @return An immutable copy of the registered, normalized model ids.
   */
  public List<String> modelIds() {
    return List.copyOf(modelsById.keySet());
  }

  /**
   * Reports whether a categorizer is registered under the given id.
   *
   * @param modelId The model id to check. May be {@code null}; matched after normalization.
   *
   * @return {@code true} when a model is registered under the normalized id.
   */
  public boolean supportsModel(String modelId) {
    return modelId != null && modelsById.containsKey(normalize(modelId));
  }

  /**
   * Returns all configured categorizers, in registration order, for catalog reporting.
   *
   * @return An immutable copy of the registered models.
   */
  public List<DocCategorizerModel> allModels() {
    return List.copyOf(modelsById.values());
  }

  /**
   * Looks up the categorizer registered under the given id.
   *
   * @param modelId The model id to look up. May be {@code null}; matched after normalization.
   *
   * @return The matching model, or {@code null} when {@code modelId} is {@code null} or no
   *     model is registered under the normalized id.
   */
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

  /** Closes every closeable model, aggregating failures; used when startup fails partway. */
  private static void closeModels(Iterable<DocCategorizerModel> models) {
    final List<Exception> failures = new ArrayList<>();
    for (DocCategorizerModel model : models) {
      if (model instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception e) {
          failures.add(e);
        }
      }
    }
    if (!failures.isEmpty()) {
      final IllegalStateException error = new IllegalStateException(
          "Failed to close " + failures.size() + " document categorizer(s)");
      failures.forEach(error::addSuppressed);
      throw error;
    }
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
