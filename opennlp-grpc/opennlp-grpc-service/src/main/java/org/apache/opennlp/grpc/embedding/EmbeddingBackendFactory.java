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

import java.util.Map;

/**
 * Service provider interface for embedding backends, discovered via
 * {@link java.util.ServiceLoader}.
 *
 * <p>A backend module registers its implementation in
 * {@code META-INF/services/org.apache.opennlp.grpc.embedding.EmbeddingBackendFactory}
 * and is then selectable through the {@code model.embedder.backend} server configuration
 * key without any change to the gRPC server. This is the extension point for additional
 * inference runtimes shipped as separate jars: in-process engines (OpenVINO, DJL, ...)
 * as well as remote backends whose provider is a client to an external inference
 * service. A remote provider implements the same surface ({@code embed},
 * {@code embedBatch}, model registry) over a connection it owns, which keeps the
 * actual inference free to live in another process or language entirely.</p>
 *
 * <p>Implementations must be stateless and provide a public no-argument constructor.
 * Providers that hold connections or native resources should implement
 * {@link AutoCloseable}; the server closes them on shutdown.</p>
 */
public interface EmbeddingBackendFactory {

  /**
   * @return The unique backend id this factory serves, matched case-insensitively against
   *         the {@code model.embedder.backend} configuration value. Must be lower-case,
   *         non-blank, and stable across releases (it is part of the configuration contract).
   */
  String backendId();

  /**
   * Creates the embedding provider for this backend.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return The provider with all configured models loaded. Never {@code null}.
   *
   * @throws org.apache.opennlp.grpc.processor.AnalysisException If the model configuration
   *         is invalid or a model fails to load.
   */
  EmbeddingProvider create(Map<String, String> configuration);
}
