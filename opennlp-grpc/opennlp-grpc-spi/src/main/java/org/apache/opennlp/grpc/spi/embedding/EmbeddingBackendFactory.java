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

import java.util.Map;

/**
 * Service provider interface for embedding backends discovered through
 * {@link java.util.ServiceLoader}. Each backend contributes its configured models to
 * the aggregate provider under one stable backend id.
 *
 * <p>Thread safety is implementation specific.</p>
 */
public interface EmbeddingBackendFactory {

  /**
   * Returns the backend id this factory serves.
   *
   * @return The unique backend id this factory serves. Must be lower-case, non-blank, and
   *         stable across releases because it is part of the discovery and selector contract.
   */
  String backendId();

  /**
   * Creates the embedding provider for this backend.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return The provider with all configured models loaded. Never {@code null}.
   *
   * @throws org.apache.opennlp.grpc.spi.AnalysisException If the model configuration
   *         is invalid or a model fails to load.
   * @throws IllegalArgumentException If {@code configuration} is {@code null}.
   */
  EmbeddingProvider create(Map<String, String> configuration);
}
