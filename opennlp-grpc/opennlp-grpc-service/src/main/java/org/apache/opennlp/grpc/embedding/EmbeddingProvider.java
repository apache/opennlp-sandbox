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
import java.util.Objects;
import java.util.Set;

/**
 * Embedding backend for the {@code PIPELINE_STEP_EMBED} pipeline step.
 *
 * <p>Implementations own their model lifecycle: models are registered at construction
 * time and identified by a stable model id. Implementations that hold native resources
 * or remote connections should also implement {@link AutoCloseable}; the server closes
 * such providers on shutdown.</p>
 *
 * <p>Inference may run in-process (ONNX Runtime, CUDA, DJL, OpenVINO, ...) or be
 * delegated to a remote service; the server is agnostic. Batch-capable backends should
 * override {@link #embedBatch(String, List)}, which the server prefers whenever it has
 * more than one text to embed.</p>
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
   */
  default List<float[]> embedBatch(String modelId, List<String> texts) {
    Objects.requireNonNull(texts, "texts must not be null");
    final List<float[]> vectors = new ArrayList<>(texts.size());
    for (String text : texts) {
      vectors.add(embed(modelId, text));
    }
    return vectors;
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
}
