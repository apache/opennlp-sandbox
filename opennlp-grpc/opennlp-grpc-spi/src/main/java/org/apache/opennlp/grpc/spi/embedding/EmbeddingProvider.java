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
package org.apache.opennlp.grpc.spi.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;

/**
 * Supplies registered embedding models and reports the route used for each result.
 *
 * <p>Thread safety is implementation specific.</p>
 */
public interface EmbeddingProvider {

  /**
   * Returns the open identifier of the backend serving this provider's models.
   *
   * @return The open identifier of the backend serving this provider's models,
   *         e.g. {@code "onnx"} or {@code "cuda"}. Matches the id of the
   *         {@link EmbeddingBackendFactory} that created the provider and is reported
   *         to clients in {@code ModelDescriptor.backend_id}. Never {@code null}.
   */
  String backendId();

  /**
   * Returns the backend id of the engine that actually serves the given model. For a
   * single-engine provider this is just {@link #backendId()}; an aggregating provider that routes
   * a model id across several engines overrides this to report the engine a specific model resolves
   * to.
   *
   * @param modelId The id of a registered embedding model.
   *
   * @return The backend id serving {@code modelId}.
   */
  default String backendId(String modelId) {
    return backendId();
  }

  /**
   * Reports whether this provider can serve any embedding requests.
   *
   * @return {@code true} when at least one embedding model is registered.
   */
  boolean isAvailable();

  /**
   * Returns the ids of every embedding model this provider can serve.
   *
   * @return The ids of all registered embedding models. Never {@code null}.
   */
  Set<String> registeredModelIds();

  /**
   * Reports whether the given model id refers to a model this provider serves.
   *
   * @param modelId The model id to check. May be {@code null} or blank.
   *
   * @return {@code true} when the given id refers to a registered embedding model.
   */
  boolean supportsModel(String modelId);

  /**
   * Reports whether one concrete backend route serves the logical model.
   *
   * @param modelId The logical model id.
   * @param backendId The concrete backend id.
   * @return {@code true} when the backend serves the model.
   */
  default boolean supportsModel(String modelId, String backendId) {
    return supportsModel(modelId) && backendId != null && backendId.equals(backendId(modelId));
  }

  /**
   * Returns the dimension of the vectors produced by the given model.
   *
   * @param modelId The id of a registered embedding model.
   *
   * @return The dimension of the vectors produced by the model.
   */
  int embeddingDimension(String modelId);

  /**
   * Embeds the given text.
   *
   * @param modelId The id of a registered embedding model.
   * @param text    The text to embed. Must not be {@code null}.
   *
   * @return The embedding vector of length {@link #embeddingDimension(String)}.
   *
   * @throws IllegalArgumentException If {@code text} is {@code null}.
   */
  float[] embed(String modelId, String text);

  /**
   * Embeds the given texts in one call. The default implementation embeds each text
   * individually; backends with native batch support (GPU inference, remote services)
   * should override this to avoid per-text dispatch overhead.
   *
   * @param modelId The id of a registered embedding model.
   * @param texts   The texts to embed. Must not be {@code null} and must not contain
   *                {@code null} elements.
   *
   * @return One embedding vector per input text, in input order.
   *
   * @throws IllegalArgumentException If {@code texts} is {@code null} or contains a
   *         {@code null} element.
   */
  default List<float[]> embedBatch(String modelId, List<String> texts) {
    if (texts == null) {
      throw new IllegalArgumentException("texts must not be null");
    }
    final List<float[]> vectors = new ArrayList<>(texts.size());
    for (String text : texts) {
      vectors.add(embed(modelId, text));
    }
    return vectors;
  }

  /**
   * Embeds a batch on the selected route and reports the route that actually produced it.
   * A blank backend selects this provider's default route.
   *
   * @param modelId The logical model id.
   * @param backendId The concrete backend id, or blank to use the default route.
   * @param texts The texts to embed.
   * @return The vectors and the concrete route that produced them.
   */
  default EmbeddingBatchResult embedBatchResolved(
      String modelId, String backendId, List<String> texts) {
    final String actualBackend = backendId(modelId);
    if (backendId != null && !backendId.isBlank() && !supportsModel(modelId, backendId)) {
      throw AnalysisException.notFound(
          "Engine '" + backendId + "' does not serve embedding model '" + modelId + "'");
    }
    final EmbeddingRoute.Builder route = EmbeddingRoute.newBuilder()
        .setModelId(modelId)
        .setBackendId(actualBackend)
        .setPrimary(true);
    final String hash = modelArtifactHash(modelId);
    if (hash != null && !hash.isBlank()) {
      route.setArtifactHash(hash);
    }
    return new EmbeddingBatchResult(embedBatch(modelId, texts), route.build());
  }

  /**
   * Lists the concrete backend routes available for one logical model.
   *
   * @param modelId The logical model id.
   * @return Routes in selection order, with the default route first.
   */
  default List<EmbeddingRoute> routesForModel(String modelId) {
    if (!supportsModel(modelId)) {
      return List.of();
    }
    final EmbeddingRoute.Builder route = EmbeddingRoute.newBuilder()
        .setModelId(modelId)
        .setBackendId(backendId(modelId))
        .setPrimary(true);
    final String hash = modelArtifactHash(modelId);
    if (hash != null && !hash.isBlank()) {
      route.setArtifactHash(hash);
    }
    return List.of(route.build());
  }

  /**
   * Resolves the effective model id from an optional client override.
   *
   * @param requestedModelId The model id requested by the client. May be {@code null}
   *                         or blank when the client wants the server default.
   *
   * @return The model id to use, or {@code null} when no default can be determined.
   */
  default String resolveModelId(String requestedModelId) {
    if (requestedModelId != null && !requestedModelId.isBlank()) {
      return requestedModelId;
    }
    if (registeredModelIds().size() == 1) {
      return registeredModelIds().iterator().next();
    }
    return null;
  }

  /**
   * Returns the SHA-256 hash of the primary artifact backing {@code modelId}, when known.
   *
   * @param modelId The id of a registered embedding model.
   * @return The lowercase hex digest, or an empty string when unavailable.
   */
  default String modelArtifactHash(String modelId) {
    return "";
  }
}
