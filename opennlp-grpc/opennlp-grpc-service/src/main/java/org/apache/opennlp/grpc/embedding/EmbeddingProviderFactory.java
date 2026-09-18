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
package org.apache.opennlp.grpc.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.SortedMap;
import java.util.TreeMap;

import opennlp.tools.util.StringUtil;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingBackendFactory;

/**
 * Creates the aggregate {@link EmbeddingProvider} for the gRPC server.
 *
 * <p>Backends are discovered through the {@link EmbeddingBackendFactory} service provider
 * interface via {@link ServiceLoader}; the {@code onnx} (ONNX Runtime on CPU) and {@code cuda}
 * (ONNX Runtime with the CUDA execution provider) engines register from the
 * {@code opennlp-grpc-dl} add-on jar, and additional backends (remote TEI, OpenVINO, ...)
 * register from their own jars. <b>Every</b> configured backend is loaded and the providers are aggregated into
 * a {@link CompositeEmbeddingProvider}, so several engines serve embeddings at once and a model id
 * can be served by more than one engine with priority/fallback (see that class).</p>
 */
public final class EmbeddingProviderFactory {

  private static final Logger logger = LoggerFactory.getLogger(EmbeddingProviderFactory.class);

  /** Namespace shared by all embedding engines: {@code model.embedder.<id>.<engine suffix>}. */
  private static final String KEY_PREFIX = "model.embedder.";

  /** Engine-specific model path suffixes served only by the {@code opennlp-grpc-dl} add-on. */
  private static final Map<String, String> DL_PATH_SUFFIXES =
      Map.of(".onnx.path", "onnx", ".cuda.path", "cuda");

  private EmbeddingProviderFactory() {
  }

  /**
   * Loads every configured embedding backend and aggregates them into one provider.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return A {@link CompositeEmbeddingProvider} over all backends that have models configured
   *     (possibly serving none, when no embedding model is configured). Never {@code null}.
   *
   * @throws AnalysisException If two factories declare the same backend id, a backend's model
   *     configuration is invalid, or engines serving one logical id disagree on dimension.
   */
  public static EmbeddingProvider create(Map<String, String> configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    final SortedMap<String, EmbeddingBackendFactory> factories = discoverFactories();
    requireDlBackendsWhenConfigured(configuration, factories);
    final List<EmbeddingProvider> available = new ArrayList<>();
    for (EmbeddingBackendFactory factory : factories.values()) {
      EmbeddingProvider provider = null;
      final boolean providerAvailable;
      try {
        provider = factory.create(configuration);
        if (provider == null) {
          throw new IllegalStateException(
              factory.getClass().getName() + " returned a null provider");
        }
        providerAvailable = provider.isAvailable();
      } catch (RuntimeException e) {
        closeProvider(provider);
        closeProviders(available);
        throw e;
      }
      if (providerAvailable) {
        logger.info("Embedding backend '{}' serving {}",
            provider.backendId(), provider.registeredModelIds());
        available.add(provider);
      } else {
        closeProvider(provider);
      }
    }
    return new CompositeEmbeddingProvider(available, configuration);
  }

  /**
   * Fails loud when an ONNX-family model path is configured but the engine that serves it was
   * not discovered, instead of silently ignoring the model the operator asked for.
   *
   * @param configuration The server configuration.
   * @param factories The discovered backend factories, keyed by backend id.
   *
   * @throws AnalysisException {@code FAILED_PRECONDITION} if an {@code .onnx.path} or
   *     {@code .cuda.path} entry exists and the matching engine is missing.
   */
  private static void requireDlBackendsWhenConfigured(
      Map<String, String> configuration, SortedMap<String, EmbeddingBackendFactory> factories) {
    for (String key : configuration.keySet()) {
      if (!key.startsWith(KEY_PREFIX)) {
        continue;
      }
      for (Map.Entry<String, String> engine : DL_PATH_SUFFIXES.entrySet()) {
        if (key.endsWith(engine.getKey()) && !factories.containsKey(engine.getValue())) {
          throw AnalysisException.failedPrecondition(
              key + " is configured but the '" + engine.getValue() + "' embedding backend is "
                  + "not on the classpath; add the opennlp-grpc-dl jar (cpu or gpu flavor)");
        }
      }
    }
  }

  /** Closes every provider while preserving later cleanup. */
  private static void closeProviders(List<EmbeddingProvider> providers) {
    for (EmbeddingProvider provider : providers) {
      closeProvider(provider);
    }
  }

  /** Closes one provider when it owns resources. */
  private static void closeProvider(EmbeddingProvider provider) {
    if (provider instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception e) {
        logger.warn("Failed to close embedding provider '{}' during startup cleanup",
            provider.backendId(), e);
      }
    }
  }

  /**
   * Discovers all registered backend factories, keyed by backend id.
   *
   * @throws AnalysisException If a factory declares an invalid id or two factories declare
   *                           the same id.
   */
  private static SortedMap<String, EmbeddingBackendFactory> discoverFactories() {
    final SortedMap<String, EmbeddingBackendFactory> factories = new TreeMap<>();
    for (EmbeddingBackendFactory factory : ServiceLoader.load(EmbeddingBackendFactory.class)) {
      final String id = validId(factory.backendId(), factory.getClass().getName());
      final EmbeddingBackendFactory duplicate = factories.putIfAbsent(id, factory);
      if (duplicate != null) {
        throw AnalysisException.invalidArgument(
            "Embedding backend id '" + id + "' is declared by both "
                + duplicate.getClass().getName() + " and " + factory.getClass().getName());
      }
    }
    return factories;
  }

  /** Returns a validated backend id. Package-private for tests. */
  static String validId(String id, String owner) {
    if (id == null || id.isBlank() || !id.equals(StringUtil.toLowerCase(id))
        || id.chars().anyMatch(Character::isWhitespace)) {
      throw AnalysisException.invalidArgument(
          owner + " declares an invalid backend id '" + id
              + "'; backend ids must be non-blank, lower-case, and contain no whitespace");
    }
    return id;
  }
}
