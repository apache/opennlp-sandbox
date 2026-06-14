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

import java.util.List;
import java.util.Map;

/**
 * Service provider interface for syntactic-chunking backends. Each factory parses its own slice of
 * the server configuration and contributes the chunkers it finds; {@link ChunkerRegistry} discovers
 * factories through {@link java.util.ServiceLoader} and groups their chunkers by id, so several
 * engines can serve the same chunker id.
 */
public interface ChunkerBackendFactory {

  /**
   * Returns a stable identifier for this factory, used to deduplicate factories discovered more
   * than once on the classpath.
   *
   * @return The factory id. Never {@code null}.
   */
  String factoryId();

  /**
   * Creates the chunkers this backend is configured for.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return The chunkers found, possibly empty; never {@code null}.
   */
  List<ChunkerModel> create(Map<String, String> configuration);
}
