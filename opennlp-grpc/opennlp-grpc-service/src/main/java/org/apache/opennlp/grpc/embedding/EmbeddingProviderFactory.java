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

import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.opennlp.grpc.processor.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the configured {@link EmbeddingProvider} for the gRPC server.
 *
 * <p>Backends are discovered through the {@link EmbeddingBackendFactory} service provider
 * interface via {@link ServiceLoader} and selected with the {@code model.embedder.backend}
 * configuration key. The server ships {@code onnx} (the default, ONNX Runtime on CPU) and
 * {@code cuda} (ONNX Runtime with the CUDA execution provider; requires a server built
 * with the {@code gpu} Maven flavor). Additional backends become available by placing a
 * jar with an {@link EmbeddingBackendFactory} registration on the classpath. Unknown
 * backend values are rejected with the list of discovered backends.</p>
 */
public final class EmbeddingProviderFactory {

  private static final Logger logger = LoggerFactory.getLogger(EmbeddingProviderFactory.class);

  static final String KEY_BACKEND = "model.embedder.backend";
  static final String DEFAULT_BACKEND = OnnxEmbeddingBackendFactory.BACKEND_ID;

  private EmbeddingProviderFactory() {
  }

  /**
   * Creates the embedding provider declared by the server configuration.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return The configured provider. Never {@code null}.
   *
   * @throws AnalysisException If the configured backend is not registered, two factories
   *                           declare the same backend id, or the provider's model
   *                           configuration is invalid.
   */
  public static EmbeddingProvider create(Map<String, String> configuration) {
    final String backend =
        configuration.getOrDefault(KEY_BACKEND, DEFAULT_BACKEND).trim().toLowerCase(Locale.ROOT);
    final SortedMap<String, EmbeddingBackendFactory> factories = discoverFactories();

    final EmbeddingBackendFactory factory = factories.get(backend);
    if (factory == null) {
      throw AnalysisException.invalidArgument(
          KEY_BACKEND + " '" + backend + "' is not supported; registered backends: "
              + String.join(", ", factories.keySet()));
    }
    logger.info("Selected embedding backend '{}' ({})", backend, factory.getClass().getName());
    return factory.create(configuration);
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
      final String id = factory.backendId();
      if (id == null || id.isBlank() || !id.equals(id.toLowerCase(Locale.ROOT))) {
        throw AnalysisException.invalidArgument(
            factory.getClass().getName() + " declares an invalid backend id '" + id
                + "'; backend ids must be non-blank and lower-case");
      }
      final EmbeddingBackendFactory duplicate = factories.putIfAbsent(id, factory);
      if (duplicate != null) {
        throw AnalysisException.invalidArgument(
            "Embedding backend id '" + id + "' is declared by both "
                + duplicate.getClass().getName() + " and " + factory.getClass().getName());
      }
    }
    return factories;
  }
}
