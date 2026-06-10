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

import org.apache.opennlp.grpc.processor.AnalysisException;

/**
 * Creates the configured {@link EmbeddingProvider} for the gRPC server.
 *
 * <p>The backend is selected with the {@code model.embedder.backend} configuration key.
 * Supported values are {@value #BACKEND_ONNX} (the default, ONNX Runtime on CPU) and
 * {@value #BACKEND_CUDA} (ONNX Runtime with the CUDA execution provider; requires a
 * server built with the {@code gpu} Maven profile). Any other value is rejected.</p>
 */
public final class EmbeddingProviderFactory {

  static final String KEY_BACKEND = "model.embedder.backend";
  static final String BACKEND_ONNX = "onnx";
  static final String BACKEND_CUDA = "cuda";

  private EmbeddingProviderFactory() {
  }

  /**
   * Creates the embedding provider declared by the server configuration.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return The configured provider. Never {@code null}.
   *
   * @throws AnalysisException If the configured backend is unknown or the provider's
   *                           model configuration is invalid.
   */
  public static EmbeddingProvider create(Map<String, String> configuration) {
    final String backend =
        configuration.getOrDefault(KEY_BACKEND, BACKEND_ONNX).trim().toLowerCase(Locale.ROOT);
    return switch (backend) {
      case BACKEND_ONNX -> new OnnxRuntimeEmbeddingProvider(configuration);
      case BACKEND_CUDA -> new CudaEmbeddingProvider(configuration);
      default -> throw AnalysisException.invalidArgument(
          KEY_BACKEND + " '" + backend + "' is not supported; expected one of: "
              + BACKEND_ONNX + ", " + BACKEND_CUDA);
    };
  }
}
