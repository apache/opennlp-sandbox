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
package org.apache.opennlp.grpc.spi.search;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.embeddings.index.VectorIndex;
import org.apache.opennlp.grpc.v1.AnalysisChainDescriptor;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;

/**
 * Service-provider interface for search engines behind index components and bundles.
 *
 * <p>Implementations are discovered once at server startup through {@link java.util.ServiceLoader}
 * and must be declared in
 * {@code META-INF/services/org.apache.opennlp.grpc.spi.search.SearchIndexProviderFactory}. Each
 * factory declares its capabilities; the host calls only the operations those capabilities
 * announce. Bundle loads receive validated common bundle fields and options from the
 * index-specific {@code provider_option} namespace, never the full server configuration.</p>
 */
public interface SearchIndexProviderFactory {

  /**
   * One configured provider instance. Implementations normally use an immutable record
   * here, so provider-specific string options are parsed once and all runtime calls use
   * typed values.
   */
  interface ConfiguredProvider {

    /**
     * One restored provider-owned vector component and its stable row identifiers.
     *
     * @param index Restored frozen index.
     * @param ids Row identifiers in index order.
     */
    record RestoredVectorIndex(VectorIndex index, List<String> ids) {

      /** Validates the restored provider result. */
      public RestoredVectorIndex {
        if (index == null || ids == null || ids.size() != index.size()) {
          throw new IllegalArgumentException(
              "restored vector index and ids must be nonnull and have equal sizes");
        }
        ids = List.copyOf(ids);
      }
    }

    /**
     * Creates one live vector component for this configured instance.
     *
     * @param dimension Vector dimension.
     * @return New build-phase vector index.
     */
    default VectorIndex createLiveVectorIndex(int dimension) {
      throw new UnsupportedOperationException(
          "configured search provider does not build live vector components");
    }

    /**
     * Reports whether immutable snapshots must retain original float arrays.
     *
     * @return {@code true} when snapshot rebuilds need the original arrays.
     */
    default boolean retainRawVectors() {
      return true;
    }

    /**
     * Writes one frozen provider-owned vector segment.
     *
     * @param index Frozen index created by this configured provider.
     * @param directory Empty destination directory.
     * @throws IOException If the segment cannot be written or has the wrong provider type.
     */
    default void writeLiveVectorIndex(VectorIndex index, Path directory) throws IOException {
      throw new IOException("configured search provider does not persist live vector components");
    }

    /**
     * Restores one provider-owned vector segment.
     *
     * @param directory Segment directory written by this configured provider.
     * @return Restored index and row identifiers.
     * @throws IOException If the segment is unreadable or invalid.
     */
    default RestoredVectorIndex readLiveVectorIndex(Path directory) throws IOException {
      throw new IOException("configured search provider does not restore live vector components");
    }

    /**
     * Returns the typed parameters that affect stored vector preparation.
     *
     * @return Stable preparation identity.
     */
    default String preparationIdentity() {
      return "";
    }

    /**
     * Returns the configured keyword analysis-chain identity, when applicable.
     *
     * @return Analysis-chain descriptor, or {@code null}.
     */
    default AnalysisChainDescriptor analysisChain() {
      return null;
    }

    /**
     * Creates the provider-owned keyword component for one immutable candidate snapshot.
     *
     * @param candidates Candidate records.
     * @return Immutable keyword index.
     */
    default KeywordQueryIndex createKeywordQueryIndex(List<QueryCandidate> candidates) {
      throw new UnsupportedOperationException(
          "configured search provider does not execute keyword query leaves");
    }
  }

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
   * Parses one named instance's provider-specific options into an immutable typed object.
   * The default accepts no options and delegates to the factory's stateless methods.
   *
   * @param instanceId Stable configured instance id.
   * @param options Provider-specific option values without the configuration prefix.
   * @return Configured provider instance.
   * @throws IllegalArgumentException If an option is unsupported or invalid.
   */
  default ConfiguredProvider configureInstance(
      String instanceId, Map<String, String> options) {
    if (options == null) {
      throw new IllegalArgumentException("provider options must not be null");
    }
    if (!options.isEmpty()) {
      throw new IllegalArgumentException("search provider instance '" + instanceId
          + "' does not support options " + options.keySet());
    }
    final SearchIndexProviderFactory factory = this;
    return new ConfiguredProvider() {
      @Override
      public VectorIndex createLiveVectorIndex(int dimension) {
        return factory.createLiveVectorIndex(dimension);
      }

      @Override
      public String preparationIdentity() {
        return factory.preparationIdentity();
      }

      @Override
      public AnalysisChainDescriptor analysisChain() {
        return factory.analysisChain();
      }
    };
  }

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
   * @throws UnsupportedOperationException If this provider does not build live vector components.
   */
  default VectorIndex createLiveVectorIndex(int dimension) {
    throw new UnsupportedOperationException("search provider '" + providerId()
        + "' does not build live vector components");
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
   * Returns the analysis chain identity recorded on keyword components served by this provider.
   *
   * @return The chain identity, or {@code null} for providers without a keyword component.
   */
  default AnalysisChainDescriptor analysisChain() {
    return null;
  }
}
