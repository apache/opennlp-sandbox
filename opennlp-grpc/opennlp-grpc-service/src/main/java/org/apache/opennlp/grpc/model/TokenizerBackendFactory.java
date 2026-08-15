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

import java.util.Map;
import java.util.Optional;

import opennlp.tools.tokenize.Tokenizer;

/**
 * Service provider interface for custom word-tokenizer engines. The server discovers
 * factories through {@link java.util.ServiceLoader}; clients select an available engine
 * with {@code AnalysisProfile.tokenizer.custom}.
 *
 * <p>The returned tokenizer must be safe for concurrent calls. A backend wrapping a
 * stateful implementation can satisfy that contract with a thread-local delegate.</p>
 */
public interface TokenizerBackendFactory {

  /**
   * Returns the stable custom engine id exposed to clients.
   *
   * @return A lower-case, non-blank engine id. Never {@code null}.
   */
  String engineId();

  /**
   * Creates this engine from the server configuration.
   *
   * @param configuration The complete server configuration. Must not be {@code null}.
   *
   * @return The configured tokenizer, or empty when this backend is not configured.
   */
  Optional<Tokenizer> create(Map<String, String> configuration);
}
