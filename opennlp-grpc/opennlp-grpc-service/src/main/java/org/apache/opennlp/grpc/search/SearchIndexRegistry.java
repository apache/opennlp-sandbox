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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.spi.ModelArtifactHasher;
import org.apache.opennlp.grpc.v1.SearchIndexBuildDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.SearchMetric;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;
import org.apache.opennlp.grpc.v1.SearchProviderSelector;
import org.apache.opennlp.grpc.v1.StandardSearchProvider;
import org.apache.opennlp.grpc.spi.search.SearchIndexProviderFactory;
import org.apache.opennlp.grpc.spi.search.SearchIndexProvider;
import org.apache.opennlp.grpc.spi.search.SearchIndexBundleConfiguration;

/** Immutable registry of every bounded search index loaded before server startup. */
public final class SearchIndexRegistry implements AutoCloseable {

  /** Default maximum number of configured indexes. */
  public static final int DEFAULT_MAX_INDEXES = 32;
  /** Absolute safety ceiling for the operator-configured index count. */
  public static final int MAX_INDEXES_LIMIT = 256;

  private static final String INDEXES_KEY = "search.indexes";
  private static final String MAX_INDEXES_KEY = "search.max_indexes";
  private static final String PREFIX = "search.index.";

  private final SortedMap<String, SearchIndexProvider> providers;
  private final List<SearchIndexProvider> closeOrder;
  private final AtomicBoolean closed = new AtomicBoolean();

  /**
   * Creates a registry and validates every provider descriptor.
   *
   * @param providers Providers to register, limited to {@link #DEFAULT_MAX_INDEXES}.
   */
  public SearchIndexRegistry(List<SearchIndexProvider> providers) {
    this(providers, DEFAULT_MAX_INDEXES);
  }

  private SearchIndexRegistry(List<SearchIndexProvider> providers, int maxIndexes) {
    if (providers == null) {
      throw new IllegalArgumentException("search index providers must not be null");
    }
    if (maxIndexes < 1 || maxIndexes > MAX_INDEXES_LIMIT) {
      throw new IllegalArgumentException("maximum search index count must be between 1 and "
          + MAX_INDEXES_LIMIT + ", was " + maxIndexes);
    }
    if (providers.size() > maxIndexes) {
      throw new IllegalArgumentException("configured search index count " + providers.size()
          + " exceeds maximum " + maxIndexes);
    }
    final SortedMap<String, SearchIndexProvider> registered = new TreeMap<>();
    for (SearchIndexProvider provider : providers) {
      if (provider == null) {
        throw new IllegalArgumentException("search index providers must not contain null");
      }
      final SearchIndexDescriptor descriptor = provider.descriptor();
      validateDescriptor(descriptor);
      if (registered.putIfAbsent(descriptor.getIndexId(), provider) != null) {
        throw new IllegalArgumentException("search index id '" + descriptor.getIndexId()
            + "' is configured more than once");
      }
    }
    this.providers = java.util.Collections.unmodifiableSortedMap(registered);
    this.closeOrder = List.copyOf(providers);
  }

  /**
   * Loads configured providers discovered through {@link ServiceLoader}.
   *
   * @param configuration Server configuration.
   * @return Fully loaded registry.
   * @throws IOException If a configured bundle cannot be loaded.
   */
  public static SearchIndexRegistry fromConfiguration(Map<String, String> configuration)
      throws IOException {
    return fromConfiguration(configuration, ServiceLoader.load(SearchIndexProviderFactory.class));
  }

  /**
   * Loads configured providers through an explicit factory set.
   *
   * @param configuration Server configuration.
   * @param factories Available provider factories.
   * @return Fully loaded registry.
   * @throws IOException If a configured bundle cannot be loaded.
   */
  public static SearchIndexRegistry fromConfiguration(
      Map<String, String> configuration, Iterable<SearchIndexProviderFactory> factories)
      throws IOException {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    if (factories == null) {
      throw new IllegalArgumentException("factories must not be null");
    }
    final Map<String, SearchIndexProviderFactory> byId = new TreeMap<>();
    for (SearchIndexProviderFactory factory : factories) {
      if (factory == null) {
        throw new IllegalArgumentException("search provider factories must not contain null");
      }
      final String id = factory.providerId();
      requireStableId(id, factory.getClass().getName() + " search provider id");
      if (byId.putIfAbsent(id, factory) != null) {
        throw new IllegalArgumentException("search provider id '" + id
            + "' is declared more than once");
      }
    }
    final String configured = configuration.get(INDEXES_KEY);
    final int maxIndexes = positiveInt(
        configuration, MAX_INDEXES_KEY, DEFAULT_MAX_INDEXES);
    if (maxIndexes > MAX_INDEXES_LIMIT) {
      throw new IllegalArgumentException(MAX_INDEXES_KEY + " must not exceed "
          + MAX_INDEXES_LIMIT + ", was " + maxIndexes);
    }
    if (configured == null || configured.isBlank()) {
      return new SearchIndexRegistry(List.of(), maxIndexes);
    }
    final List<String> ids = commaSeparatedIds(configured);
    if (ids.size() > maxIndexes) {
      throw new IllegalArgumentException("configured search index count " + ids.size()
          + " exceeds maximum " + maxIndexes);
    }
    final List<SearchIndexProvider> loaded = new ArrayList<>(ids.size());
    try {
      for (String id : ids) {
        requireStableId(id, "configured search index id");
        final String prefix = PREFIX + id + ".";
        final String providerId = requireTrimmed(configuration, prefix + "provider");
        requireStableId(providerId, "configured search provider id");
        final SearchIndexProviderFactory factory = byId.get(providerId);
        if (factory == null) {
          throw new IllegalArgumentException("search index '" + id + "' names unknown provider '"
              + providerId + "'; available providers: " + byId.keySet());
        }
        if (!factory.capabilities().contains(
            SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_BUNDLE)) {
          throw new IllegalArgumentException("search index '" + id + "' names provider '"
              + providerId + "', which does not load immutable bundles");
        }
        final SearchIndexBundleConfiguration bundle = new SearchIndexBundleConfiguration(
            id,
            Path.of(require(configuration, prefix + "directory")),
            Path.of(require(configuration, prefix + "passages")),
            positiveInt(configuration, prefix + "max_top_k",
                SearchIndexBundleConfiguration.DEFAULT_MAX_TOP_K),
            positiveInt(configuration, prefix + "max_query_bytes",
                SearchIndexBundleConfiguration.DEFAULT_MAX_QUERY_BYTES),
            positiveInt(configuration, prefix + "max_response_bytes",
                SearchIndexBundleConfiguration.DEFAULT_MAX_RESPONSE_BYTES),
            positiveInt(configuration, prefix + "max_records",
                SearchIndexBundleConfiguration.DEFAULT_MAX_RECORDS),
            positiveInt(configuration, prefix + "max_source_document_bytes",
                SearchIndexBundleConfiguration.DEFAULT_MAX_SOURCE_DOCUMENT_BYTES),
            positiveInt(configuration, prefix + "max_indexed_text_bytes",
                SearchIndexBundleConfiguration.DEFAULT_MAX_INDEXED_TEXT_BYTES),
            positiveInt(configuration, prefix + "max_bundle_bytes",
                SearchIndexBundleConfiguration.DEFAULT_MAX_BUNDLE_BYTES),
            providerOptions(configuration, prefix));
        loaded.add(factory.load(bundle));
      }
      return new SearchIndexRegistry(loaded, maxIndexes);
    } catch (IOException | RuntimeException | Error failure) {
      closeReverse(loaded, failure);
      throw failure;
    }
  }

  /**
   * Returns descriptors in stable index-id order.
   *
   * @return Immutable descriptor list.
   */
  public List<SearchIndexDescriptor> descriptors() {
    return providers.values().stream().map(SearchIndexProvider::descriptor).toList();
  }

  /**
   * Resolves one configured index.
   *
   * @param indexId Requested index id.
   * @return The configured provider.
   * @throws AnalysisException If the id is unknown.
   */
  public SearchIndexProvider require(String indexId) {
    final SearchIndexProvider provider = providers.get(indexId);
    if (provider == null) {
      throw AnalysisException.notFound("Unknown search index '" + indexId
          + "'; configured indexes: " + providers.keySet());
    }
    return provider;
  }

  /**
   * Returns a configured immutable provider.
   *
   * @param indexId Opaque index identifier.
   * @return Matching provider, or {@code null} when the id is unknown.
   */
  SearchIndexProvider find(String indexId) {
    return providers.get(indexId);
  }

  /** Releases provider-owned resources in reverse load order. This method is idempotent. */
  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    closeReverse(closeOrder);
  }

  private static void closeReverse(List<SearchIndexProvider> loaded) {
    RuntimeException closeFailure = null;
    for (int index = loaded.size() - 1; index >= 0; index--) {
      try {
        loaded.get(index).close();
      } catch (RuntimeException failure) {
        if (closeFailure == null) {
          closeFailure = failure;
        } else {
          closeFailure.addSuppressed(failure);
        }
      }
    }
    if (closeFailure != null) {
      throw closeFailure;
    }
  }

  private static void closeReverse(List<SearchIndexProvider> loaded, Throwable originalFailure) {
    for (int index = loaded.size() - 1; index >= 0; index--) {
      try {
        loaded.get(index).close();
      } catch (RuntimeException failure) {
        originalFailure.addSuppressed(failure);
      }
    }
  }

  private static void validateDescriptor(SearchIndexDescriptor descriptor) {
    if (descriptor == null) {
      throw new IllegalArgumentException("search index descriptor must not be null");
    }
    requireText(descriptor.getIndexId(), "index_id");
    requireStableId(descriptor.getIndexId(), "search index descriptor index_id");
    requireTrimmedText(descriptor.getDisplayName(), "display_name");
    final SearchProviderSelector selector = descriptor.getProvider();
    if (selector.getKindCase() == SearchProviderSelector.KindCase.KIND_NOT_SET
        || (selector.hasStandard()
            && (selector.getStandard() == StandardSearchProvider.STANDARD_SEARCH_PROVIDER_UNSPECIFIED
                || selector.getStandard() == StandardSearchProvider.UNRECOGNIZED))
        || (selector.hasCustom() && !isStableId(selector.getCustom()))) {
      throw new IllegalArgumentException("search index '" + descriptor.getIndexId()
          + "' declares an invalid provider selector");
    }
    if (descriptor.getMetric() != SearchMetric.SEARCH_METRIC_COSINE) {
      throw new IllegalArgumentException("search index '" + descriptor.getIndexId()
          + "' must declare SEARCH_METRIC_COSINE");
    }
    if (!descriptor.getImmutable()) {
      throw new IllegalArgumentException("search index '" + descriptor.getIndexId()
          + "' must be immutable");
    }
    if (descriptor.getSize() < 0) {
      throw new IllegalArgumentException("search index '" + descriptor.getIndexId()
          + "' size exceeds the Java int-addressed index limit");
    }
    if (descriptor.getSize() > SearchIndexBundleConfiguration.MAX_RECORDS_LIMIT) {
      throw new IllegalArgumentException("search index '" + descriptor.getIndexId()
          + "' size exceeds fixed safety ceiling "
          + SearchIndexBundleConfiguration.MAX_RECORDS_LIMIT);
    }
    if (descriptor.getDimension() < 1 || descriptor.getMaxTopK() < 1
        || descriptor.getMaxQueryBytes() < 1 || descriptor.getMaxResponseBytes() < 1) {
      throw new IllegalArgumentException("search index '" + descriptor.getIndexId()
          + "' has non-positive dimension or operator limit");
    }
    if (descriptor.getMaxTopK() > SearchIndexBundleConfiguration.MAX_TOP_K_LIMIT) {
      throw new IllegalArgumentException("search index '" + descriptor.getIndexId()
          + "' max_top_k exceeds fixed safety ceiling "
          + SearchIndexBundleConfiguration.MAX_TOP_K_LIMIT);
    }
    if (descriptor.getSupportsAllHits()
        && descriptor.getProvider().getStandard()
            != StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT) {
      throw new IllegalArgumentException("search index '" + descriptor.getIndexId()
          + "' advertises exhaustive results without the TurboQuant provider");
    }
    if (descriptor.getSupportsAllHits()
        && descriptor.getSize() > SearchIndexBundleConfiguration.MAX_ALL_HITS_LIMIT) {
      throw new IllegalArgumentException("search index '" + descriptor.getIndexId()
          + "' exhaustive size exceeds the fixed safety ceiling");
    }
    requireTrimmedText(descriptor.getEmbeddingRoute().getModelId(),
        "embedding_route.model_id");
    requireTrimmedText(descriptor.getEmbeddingRoute().getBackendId(),
        "embedding_route.backend_id");
    requireTrimmedText(descriptor.getEmbeddingRoute().getVectorSpaceId(),
        "embedding_route.vector_space_id");
    requireTrimmedText(descriptor.getCorpus().getTitle(), "corpus.title");
    requireTrimmedText(descriptor.getCorpus().getProvenanceSummary(),
        "corpus.provenance_summary");
    if (descriptor.getCorpus().hasSourceUri()) {
      requireAbsoluteUri(descriptor.getCorpus().getSourceUri(), "corpus.source_uri");
    }
    if (descriptor.getCorpus().hasLicenseName()) {
      requireTrimmedText(descriptor.getCorpus().getLicenseName(), "corpus.license_name");
    }
    if (descriptor.getCorpus().hasLicenseUri()) {
      requireAbsoluteUri(descriptor.getCorpus().getLicenseUri(), "corpus.license_uri");
    }
    if (descriptor.getCorpus().hasArtifactHash()) {
      requireSha256(descriptor.getCorpus().getArtifactHash(), "corpus.artifact_hash");
    }
    final SearchIndexBuildDescriptor build = descriptor.getBuild();
    if (build.getBundleFormatVersion() < 1) {
      throw new IllegalArgumentException("search index build bundle_format_version must be positive");
    }
    requireSha256(build.getBundleArtifactHash(), "build.bundle_artifact_hash");
    requireTrimmedText(build.getBuilderId(), "build.builder_id");
    requireTrimmedText(build.getBuilderVersion(), "build.builder_version");
    requireSha256(build.getPreparationConfigHash(), "build.preparation_config_hash");
  }

  static void requireSha256(String value, String name) {
    ModelArtifactHasher.requireSha256Hex(value, name);
  }

  private static String require(Map<String, String> configuration, String key) {
    final String value = configuration.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(key + " must not be blank");
    }
    return value.trim();
  }

  private static String requireTrimmed(Map<String, String> configuration, String key) {
    final String value = configuration.get(key);
    if (value == null || value.isBlank() || !value.equals(value.trim())) {
      throw new IllegalArgumentException(key + " must be a nonblank trimmed value");
    }
    return value;
  }

  private static int positiveInt(
      Map<String, String> configuration, String key, int defaultValue) {
    final String value = configuration.get(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      final int parsed = Integer.parseInt(value.trim());
      if (parsed < 1) {
        throw new NumberFormatException();
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(key + " must be a positive integer, was '" + value + "'");
    }
  }

  private static Map<String, String> providerOptions(
      Map<String, String> configuration, String indexPrefix) {
    final String optionPrefix = indexPrefix + "provider_option.";
    final Map<String, String> options = new TreeMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      if (!entry.getKey().startsWith(optionPrefix)) {
        continue;
      }
      final String name = entry.getKey().substring(optionPrefix.length());
      requireStableId(name, "search provider option name");
      final String value = entry.getValue();
      if (value == null || value.isBlank() || !value.equals(value.trim())) {
        throw new IllegalArgumentException(entry.getKey()
            + " must be a nonblank trimmed value");
      }
      options.put(name, value);
    }
    return Map.copyOf(options);
  }

  private static List<String> commaSeparatedIds(String value) {
    final List<String> ids = new ArrayList<>();
    int start = 0;
    for (int index = 0; index <= value.length(); index++) {
      if (index == value.length() || value.charAt(index) == ',') {
        final String id = value.substring(start, index);
        if (id.isEmpty()) {
          throw new IllegalArgumentException(INDEXES_KEY + " must not contain blank ids");
        }
        ids.add(id);
        start = index + 1;
      }
    }
    return ids;
  }

  static void requireStableId(String value, String name) {
    if (!isStableId(value)) {
      throw new IllegalArgumentException(name + " must be a trimmed lower-case ASCII identifier "
          + "using letters, digits, dots, hyphens, or underscores, was '" + value + "'");
    }
  }

  private static boolean isStableId(String value) {
    if (value == null || value.isEmpty() || !value.equals(value.trim())) {
      return false;
    }
    boolean previousSeparator = true;
    for (int index = 0; index < value.length(); index++) {
      final char character = value.charAt(index);
      final boolean alphanumeric = (character >= 'a' && character <= 'z')
          || (character >= '0' && character <= '9');
      final boolean separator = character == '-' || character == '_' || character == '.';
      if ((!alphanumeric && !separator) || (separator && previousSeparator)) {
        return false;
      }
      previousSeparator = separator;
    }
    return !previousSeparator;
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("search index descriptor " + name + " must not be blank");
    }
  }

  private static void requireTrimmedText(String value, String name) {
    requireText(value, name);
    if (!value.equals(value.trim())) {
      throw new IllegalArgumentException("search index descriptor " + name
          + " must be trimmed");
    }
  }

  private static void requireAbsoluteUri(String value, String name) {
    requireTrimmedText(value, name);
    try {
      if (!new URI(value).isAbsolute()) {
        throw new IllegalArgumentException("search index descriptor " + name
            + " must be an absolute URI, was '" + value + "'");
      }
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("search index descriptor " + name
          + " must be an absolute URI, was '" + value + "'", e);
    }
  }
}
