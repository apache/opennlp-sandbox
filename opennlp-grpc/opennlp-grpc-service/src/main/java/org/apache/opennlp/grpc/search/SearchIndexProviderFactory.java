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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.search;

import java.io.IOException;

/**
 * Service-provider interface for loading configured immutable index bundles.
 *
 * <p>Implementations are discovered once at server startup through {@link java.util.ServiceLoader}
 * and must be declared in
 * {@code META-INF/services/org.apache.opennlp.grpc.search.SearchIndexProviderFactory}. The host
 * passes only validated common bundle fields and options from the index-specific
 * {@code provider_option} namespace. Factories do not receive the full server configuration.</p>
 */
public interface SearchIndexProviderFactory {

  /**
   * Returns the stable lower-case provider identifier used in configuration.
   *
   * @return The provider id, never {@code null} or blank.
   */
  String providerId();

  /**
   * Loads and fully validates one bundle before the server begins listening.
   *
   * @param configuration Validated bundle paths and operator limits.
   * @return The loaded immutable provider.
   * @throws IOException If the bundle is unreadable or invalid.
   */
  SearchIndexProvider load(SearchIndexBundleConfiguration configuration) throws IOException;
}
