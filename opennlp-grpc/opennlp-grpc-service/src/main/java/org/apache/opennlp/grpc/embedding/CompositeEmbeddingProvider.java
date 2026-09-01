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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.opennlp.grpc.backend.RankedBackends;
import org.apache.opennlp.grpc.backend.RankedBackends.Invocation;
import org.apache.opennlp.grpc.backend.RankedBackends.Registration;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingBatchResult;

/**
 * Aggregates every configured embedding backend into one provider, so several engines (in-process
 * ONNX/CUDA, remote TEI/OpenVINO, ...) serve embeddings concurrently. The first consumer of the
 * generic {@link RankedBackends} multi-backend pattern.
 *
 * <p>A logical model id may be served by more than one engine; each carries a priority from
 * {@code model.embedder.<id>.<engine>.priority} (higher first, default 0). {@link #embed} resolves
 * to the highest-priority engine and <b>falls back</b> on failure; {@link #embedBatchOnEngine} pins
 * one engine by a strongly-typed argument (no parsed id string). Because fallback only makes sense
 * for interchangeable vectors, all engines serving one logical id must report the same
 * {@link #embeddingDimension(String) dimension} (enforced at startup).</p>
 */
public final class CompositeEmbeddingProvider implements EmbeddingProvider, AutoCloseable {

  /** Backend id of the aggregate itself; the per-model engine is reported by {@link #backendId(String)}. */
  static final String BACKEND_ID = "composite";

  /** Artifact-hash prefix length in a derived vector space id. */
  private static final int DERIVED_SPACE_HASH_CHARS = 16;

  private static final String KEY_PREFIX = "model.embedder.";
  private static final String KEY_DEFAULT_ID = KEY_PREFIX + "default_id";

  private static final Logger logger = LoggerFactory.getLogger(CompositeEmbeddingProvider.class);

  private final List<EmbeddingProvider> providers;
  private final RankedBackends<EmbeddingProvider> backends;
  private final String configuredDefaultId;
  private final Map<String, String> configuration;

  /**
   * Aggregates the given per-engine providers, indexing each engine's models by logical id with
   * its configured priority.
   *
   * @param providers The per-engine providers to aggregate (only available ones should be passed).
   * @param configuration The server configuration, read for per-model priorities and the default
   *     id. Must not be {@code null}.
   *
   * @throws AnalysisException If a {@code .priority} value is not an integer, or if one logical id
   *     is served by engines reporting different embedding dimensions.
   */
  public CompositeEmbeddingProvider(
      List<EmbeddingProvider> providers, Map<String, String> configuration) {
    if (providers == null) {
      throw new IllegalArgumentException("providers must not be null");
    }
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    this.providers = List.copyOf(providers);
    this.configuration = Map.copyOf(configuration);
    try {
      final RankedBackends.Builder<EmbeddingProvider> builder = RankedBackends.builder();
      for (EmbeddingProvider provider : this.providers) {
        final String engine = provider.backendId();
        for (String modelId : provider.registeredModelIds()) {
          builder.add(modelId, engine, priority(configuration, modelId, engine), provider);
        }
      }
      this.backends = builder.build();
      verifyDimensionAgreement();
      verifyVectorSpaceAgreement(configuration);
      final String defaultId = configuration.get(KEY_DEFAULT_ID);
      this.configuredDefaultId = defaultId == null || defaultId.isBlank() ? null : defaultId.trim();
      if (configuredDefaultId != null && !supportsModel(configuredDefaultId)) {
        throw AnalysisException.invalidArgument(KEY_DEFAULT_ID + " names unknown embedding model '"
            + configuredDefaultId + "'; configured: " + backends.ids());
      }
    } catch (RuntimeException e) {
      closeProviders(this.providers);
      throw e;
    }
  }

  /** Returns the configured route priority. */
  private static int priority(Map<String, String> configuration, String modelId, String engine) {
    final String raw = configuration.get(KEY_PREFIX + modelId + "." + engine + ".priority");
    if (raw == null || raw.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw AnalysisException.invalidArgument(KEY_PREFIX + modelId + "." + engine
          + ".priority must be an integer, was '" + raw + "'");
    }
  }

  /** Engines serving one logical id must agree on dimension so fallback yields interchangeable vectors. */
  private void verifyDimensionAgreement() {
    for (String logicalId : backends.ids()) {
      int expected = -1;
      for (Registration<EmbeddingProvider> registration : backends.resolve(logicalId)) {
        final int dimension = registration.value().embeddingDimension(logicalId);
        if (expected == -1) {
          expected = dimension;
        } else if (dimension != expected) {
          throw AnalysisException.invalidArgument("Embedding model '" + logicalId + "' is served by "
              + "engines with differing dimensions (" + expected + " vs " + dimension
              + "); engines serving one logical id must agree so fallback is safe");
        }
      }
    }
  }

  /** Engines used as fallbacks must explicitly declare the same vector-space identity. */
  private void verifyVectorSpaceAgreement(Map<String, String> configuration) {
    for (String logicalId : backends.ids()) {
      final List<Registration<EmbeddingProvider>> registrations = backends.resolve(logicalId);
      if (registrations.size() < 2) {
        continue;
      }
      String expected = null;
      for (Registration<EmbeddingProvider> registration : registrations) {
        final String key = KEY_PREFIX + logicalId + "." + registration.engineId()
            + ".vector_space_id";
        final String configured = configuration.get(key);
        if (configured == null || configured.isBlank()) {
          throw AnalysisException.invalidArgument(key + " is required when embedding model '"
              + logicalId + "' is served by more than one engine");
        }
        final String vectorSpaceId = configured.trim();
        if (expected == null) {
          expected = vectorSpaceId;
        } else if (!expected.equals(vectorSpaceId)) {
          throw AnalysisException.invalidArgument("Embedding model '" + logicalId
              + "' is served by engines in different vector spaces ('" + expected + "' vs '"
              + vectorSpaceId + "'); fallback requires one shared vector space");
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public String backendId() {
    return BACKEND_ID;
  }

  /** {@inheritDoc} */
  @Override
  public String backendId(String modelId) {
    return backends.primary(modelId).engineId();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAvailable() {
    return !backends.isEmpty();
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> registeredModelIds() {
    return backends.ids();
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsModel(String modelId) {
    return backends.supports(modelId);
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsModel(String modelId, String backendId) {
    return backends.supports(modelId, backendId);
  }

  /** {@inheritDoc} */
  @Override
  public int embeddingDimension(String modelId) {
    return backends.primary(modelId).value().embeddingDimension(modelId);
  }

  /** {@inheritDoc} */
  @Override
  public float[] embed(String modelId, String text) {
    return backends.invoke(modelId, registration -> registration.value().embed(modelId, text));
  }

  /** {@inheritDoc} */
  @Override
  public List<float[]> embedBatch(String modelId, List<String> texts) {
    if (texts == null) {
      throw new IllegalArgumentException("texts must not be null");
    }
    return embedBatchResolved(modelId, null, texts).vectors();
  }

  /** {@inheritDoc} */
  @Override
  public EmbeddingBatchResult embedBatchResolved(
      String modelId, String backendId, List<String> texts) {
    if (texts == null) {
      throw new IllegalArgumentException("texts must not be null");
    }
    final Invocation<EmbeddingProvider, List<float[]>> invocation;
    if (backendId == null || backendId.isBlank()) {
      invocation = backends.invokeResolved(modelId,
          registration -> registration.value().embedBatch(modelId, texts));
    } else {
      final Registration<EmbeddingProvider> registration = backends.resolve(modelId, backendId);
      invocation = new Invocation<>(registration,
          registration.value().embedBatch(modelId, texts));
    }
    return new EmbeddingBatchResult(invocation.result(), route(invocation.registration()));
  }

  /** {@inheritDoc} */
  @Override
  public List<EmbeddingRoute> routesForModel(String modelId) {
    if (!supportsModel(modelId)) {
      return List.of();
    }
    return backends.resolve(modelId).stream().map(this::route).toList();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates to the provider that serves {@code modelId}.</p>
   */
  @Override
  public String modelArtifactHash(String modelId) {
    if (modelId == null || modelId.isBlank()) {
      return "";
    }
    final String hash = backends.primary(modelId).value().modelArtifactHash(modelId);
    return hash == null ? "" : hash;
  }

  /**
   * Embeds on a specific engine with no fallback. The model id and
   * engine are separate arguments, never a parsed composite string.
   *
   * @param modelId The logical model id.
   * @param engine The backend id of the engine to use.
   * @param texts The texts to embed.
   *
   * @return One vector per text.
   * @throws AnalysisException {@code NOT_FOUND} if {@code engine} does not serve {@code modelId}.
   */
  public List<float[]> embedBatchOnEngine(String modelId, String engine, List<String> texts) {
    return embedBatchResolved(modelId, engine, texts).vectors();
  }

  /** Builds the advertised route for a backend registration. */
  private EmbeddingRoute route(Registration<EmbeddingProvider> registration) {
    final String modelId = registration.logicalId();
    final String engineId = registration.engineId();
    final EmbeddingRoute.Builder route = EmbeddingRoute.newBuilder()
        .setModelId(modelId)
        .setBackendId(engineId)
        .setPriority(registration.priority())
        .setPrimary(backends.primary(modelId).engineId().equals(engineId));
    final String hash = registration.value().modelArtifactHash(modelId);
    if (hash != null && !hash.isBlank()) {
      route.setArtifactHash(hash);
    }
    final String vectorSpaceId = configuration.get(
        KEY_PREFIX + modelId + "." + engineId + ".vector_space_id");
    route.setVectorSpaceId(vectorSpaceId != null && !vectorSpaceId.isBlank()
        ? vectorSpaceId.trim() : derivedVectorSpaceId(modelId, engineId, hash));
    return route.build();
  }

  /**
   * Derives the vector space of a route whose operator declared none, so every route is
   * complete enough to index against. The derived space is deliberately narrow: it names
   * one model artifact (or, without an artifact hash, one engine), so it never claims
   * compatibility with vectors from a different artifact; operators declare a shared
   * {@code vector_space_id} explicitly when several engines serve one space.
   *
   * @param modelId The logical model id.
   * @param engineId The serving engine id.
   * @param hash The model artifact hash, or {@code null} or blank when unknown.
   * @return The derived vector space id.
   */
  static String derivedVectorSpaceId(String modelId, String engineId, String hash) {
    if (hash != null && !hash.isBlank()) {
      return modelId + "@" + hash.substring(0, Math.min(DERIVED_SPACE_HASH_CHARS, hash.length()));
    }
    return modelId + "@" + engineId;
  }

  /** {@inheritDoc} */
  @Override
  public String resolveModelId(String requestedModelId) {
    if (requestedModelId != null && !requestedModelId.isBlank()) {
      return requestedModelId;
    }
    if (configuredDefaultId != null) {
      return configuredDefaultId;
    }
    final Set<String> logicalIds = backends.ids();
    return logicalIds.size() == 1 ? logicalIds.iterator().next() : null;
  }

  /** Closes every aggregated provider that holds resources, continuing past individual failures. */
  @Override
  public void close() {
    closeProviders(providers);
  }

  /** Closes every provider while preserving later cleanup. */
  private static void closeProviders(List<EmbeddingProvider> providers) {
    for (EmbeddingProvider provider : providers) {
      if (provider instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception e) {
          logger.warn("Failed to close embedding provider '{}'", provider.backendId(), e);
        }
      }
    }
  }
}
