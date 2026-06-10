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

import java.util.Set;

import org.apache.opennlp.grpc.v1.InferenceBackend;

/**
 * Local embedding backend for the {@code PIPELINE_STEP_EMBED} pipeline step.
 *
 * <p>Implementations own their model lifecycle: models are registered at construction
 * time and identified by a stable model id. Implementations that hold native resources
 * should also implement {@link AutoCloseable}; the server closes such providers on
 * shutdown.</p>
 */
public interface EmbeddingProvider {

  /**
   * @return {@code true} when at least one embedding model is registered.
   */
  boolean isAvailable();

  /**
   * @return The ids of all registered embedding models. Never {@code null}.
   */
  Set<String> registeredModelIds();

  /**
   * @param modelId The model id to check. May be {@code null} or blank.
   *
   * @return {@code true} when the given id refers to a registered embedding model.
   */
  boolean supportsModel(String modelId);

  /**
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
   * @param backend The inference backend requested by the client.
   *
   * @return {@code true} when the provider can serve the requested inference backend.
   *         {@code UNSPECIFIED} and {@code OPENNLP_ME} are always accepted because they
   *         do not constrain the embedding backend.
   */
  default boolean supportsInferenceBackend(InferenceBackend backend) {
    return backend == InferenceBackend.INFERENCE_BACKEND_UNSPECIFIED
        || backend == InferenceBackend.INFERENCE_BACKEND_OPENNLP_ME
        || backend == InferenceBackend.INFERENCE_BACKEND_ONNX_RUNTIME;
  }
}
