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
import java.util.Set;

import opennlp.embeddings.index.VectorIndex;
import org.apache.opennlp.grpc.v1.AnalysisChainDescriptor;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;

/**
 * Service-provider interface for search engines behind index legs and bundles.
 *
 * <p>Implementations are discovered once at server startup through {@link java.util.ServiceLoader}
 * and must be declared in
 * {@code META-INF/services/org.apache.opennlp.grpc.search.SearchIndexProviderFactory}. Each
 * factory declares its capabilities; the host calls only the operations those capabilities
 * announce. Bundle loads receive validated common bundle fields and options from the
 * index-specific {@code provider_option} namespace, never the full server configuration.</p>
 */
public interface SearchIndexProviderFactory {

  /**
   * Returns the stable lower-case provider identifier used in configuration.
   *
   * @return The provider id, never {@code null} or blank.
   */
  String providerId();

  /**
   * Returns what this provider executes and how it stores index data.
   *
   * @return The declared capabilities, never {@code null} or empty.
   */
  Set<SearchProviderCapability> capabilities();

  /**
   * Loads and fully validates one bundle before the server begins listening. The host calls
   * this only on providers declaring
   * {@link SearchProviderCapability#SEARCH_PROVIDER_CAPABILITY_BUNDLE}.
   *
   * @param configuration Validated bundle paths and operator limits.
   * @return The loaded immutable provider.
   * @throws IOException If the bundle is unreadable or invalid, or this provider does not
   *     load bundles.
   */
  default SearchIndexProvider load(SearchIndexBundleConfiguration configuration)
      throws IOException {
    throw new IOException("search provider '" + providerId()
        + "' does not load immutable bundles");
  }

  /**
   * Creates one unfrozen vector index for a live snapshot. The host calls this only on
   * providers declaring both {@link SearchProviderCapability#SEARCH_PROVIDER_CAPABILITY_VECTOR}
   * and {@link SearchProviderCapability#SEARCH_PROVIDER_CAPABILITY_LIVE}, then adds every
   * snapshot vector and freezes the index before serving it.
   *
   * @param dimension Number of float components in every added and queried vector, at least one.
   * @return A new empty build-phase vector index.
   * @throws UnsupportedOperationException If this provider does not build live vector legs.
   */
  default VectorIndex createLiveVectorIndex(int dimension) {
    throw new UnsupportedOperationException("search provider '" + providerId()
        + "' does not build live vector legs");
  }

  /**
   * Returns the provider parameters that change stored vector data, for descriptor
   * provenance hashing.
   *
   * @return A stable parameter identity, or an empty string when no provider parameter
   *     changes stored vectors.
   */
  default String preparationIdentity() {
    return "";
  }

  /**
   * Returns the analysis chain identity recorded on keyword legs served by this provider.
   *
   * @return The chain identity, or {@code null} for providers without a keyword leg.
   */
  default AnalysisChainDescriptor analysisChain() {
    return null;
  }
}
