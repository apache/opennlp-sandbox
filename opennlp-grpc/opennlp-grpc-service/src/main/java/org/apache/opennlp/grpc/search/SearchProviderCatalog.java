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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;
import org.apache.opennlp.grpc.v1.SearchProviderInstance;
import org.apache.opennlp.grpc.v1.SearchProviderSelector;
import org.apache.opennlp.grpc.v1.StandardSearchProvider;
import org.apache.opennlp.grpc.spi.search.SearchIndexProviderFactory;

/**
 * Immutable catalog of configured search provider instances.
 *
 * <p>Every discovered {@link SearchIndexProviderFactory} registers one default instance whose
 * id equals its provider id; the configuration adds named instances through
 * {@code search.provider.<instance-id>.type=<provider-id>}. Instance ids are the values
 * accepted by {@code SearchProviderSelector.custom}, and the standard enum values are
 * shorthand for the default instance of each built-in provider.</p>
 */
public final class SearchProviderCatalog {

  /**
   * Provider id of the TurboQuant factory, which ships in the
   * {@code opennlp-grpc-search-turboquant} add-on; mirrored here so the catalog can map the
   * id to its standard shorthand without a compile-time dependency on the add-on.
   */
  static final String TURBO_QUANT_PROVIDER_ID = "turbo_quant";

  private static final String PREFIX = "search.provider.";
  private static final String TYPE_SUFFIX = ".type";
  private static final String OPTION_SEGMENT = ".option.";

  private final SortedMap<String, Instance> instances;

  private SearchProviderCatalog(SortedMap<String, Instance> instances) {
    this.instances = java.util.Collections.unmodifiableSortedMap(instances);
  }

  /**
   * One configured provider instance.
   *
   * @param instanceId Stable configured instance identifier, unique on this server.
   * @param factory Provider implementation behind this instance.
   * @param configured Typed provider configuration.
   * @param capabilities Immutable capabilities captured once during discovery.
   * @param standard Standard shorthand selecting this instance, or {@code null} when the
   *     instance is only selectable by id.
   */
  public record Instance(
      String instanceId, SearchIndexProviderFactory factory,
      SearchIndexProviderFactory.ConfiguredProvider configured,
      Set<SearchProviderCapability> capabilities, StandardSearchProvider standard) {

    /** Captures an immutable capability snapshot for this configured instance. */
    public Instance {
      capabilities = Set.copyOf(capabilities);
      if (configured == null) {
        throw new IllegalArgumentException("configured provider must not be null");
      }
    }

    /**
     * Returns the selector clients use to name this instance. Default instances of a
     * standard provider answer with the standard shorthand.
     *
     * @return The client-visible selector.
     */
    public SearchProviderSelector selector() {
      final SearchProviderSelector.Builder selector = SearchProviderSelector.newBuilder();
      if (standard != null) {
        selector.setStandard(standard);
      } else {
        selector.setCustom(instanceId);
      }
      return selector.build();
    }

    /**
     * Tests one declared capability.
     *
     * @param capability Capability to test.
     * @return {@code true} when the factory declares it.
     */
    public boolean has(SearchProviderCapability capability) {
      return capabilities.contains(capability);
    }
  }

  /**
   * Builds a catalog of the discovered providers' default instances.
   *
   * @return Catalog without configured named instances.
   */
  public static SearchProviderCatalog discover() {
    return fromConfiguration(Map.of());
  }

  /**
   * Builds a catalog from configuration and {@link ServiceLoader} discovery.
   *
   * @param configuration Server configuration.
   * @return Catalog of default and configured instances.
   * @throws IllegalArgumentException If an instance declaration is invalid.
   */
  public static SearchProviderCatalog fromConfiguration(Map<String, String> configuration) {
    return fromConfiguration(configuration,
        ServiceLoader.load(SearchIndexProviderFactory.class));
  }

  /**
   * Builds a catalog from configuration and an explicit factory set.
   *
   * @param configuration Server configuration.
   * @param factories Available provider factories.
   * @return Catalog of default and configured instances.
   * @throws IllegalArgumentException If a factory or instance declaration is invalid.
   */
  public static SearchProviderCatalog fromConfiguration(
      Map<String, String> configuration, Iterable<SearchIndexProviderFactory> factories) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    if (factories == null) {
      throw new IllegalArgumentException("factories must not be null");
    }
    final SortedMap<String, SearchIndexProviderFactory> byProviderId = new TreeMap<>();
    final SortedMap<String, Set<SearchProviderCapability>> capabilitiesByProviderId =
        new TreeMap<>();
    for (SearchIndexProviderFactory factory : factories) {
      if (factory == null) {
        throw new IllegalArgumentException("search provider factories must not contain null");
      }
      SearchIndexRegistry.requireStableId(factory.providerId(),
          factory.getClass().getName() + " search provider id");
      final Set<SearchProviderCapability> declared = factory.capabilities();
      if (declared == null || declared.isEmpty()
          || declared.contains(
              SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_UNSPECIFIED)) {
        throw new IllegalArgumentException("search provider '" + factory.providerId()
            + "' must declare at least one specified capability");
      }
      if (byProviderId.putIfAbsent(factory.providerId(), factory) != null) {
        throw new IllegalArgumentException("search provider id '" + factory.providerId()
            + "' is declared more than once");
      }
      capabilitiesByProviderId.put(factory.providerId(), Set.copyOf(declared));
    }
    final SortedMap<String, Instance> instances = new TreeMap<>();
    for (SearchIndexProviderFactory factory : byProviderId.values()) {
      instances.put(factory.providerId(),
          new Instance(factory.providerId(), factory,
              factory.configureInstance(factory.providerId(), Map.of()),
              capabilitiesByProviderId.get(factory.providerId()),
              standardFor(factory.providerId())));
    }
    final SortedMap<String, String> configuredTypes = new TreeMap<>();
    final SortedMap<String, SortedMap<String, String>> configuredOptions = new TreeMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(PREFIX)) {
        continue;
      }
      final String remainder = key.substring(PREFIX.length());
      if (remainder.endsWith(TYPE_SUFFIX)
          && remainder.length() > TYPE_SUFFIX.length()) {
        final String instanceId = remainder.substring(0,
            remainder.length() - TYPE_SUFFIX.length());
        SearchIndexRegistry.requireStableId(
            instanceId, "configured search provider instance id");
        if (configuredTypes.putIfAbsent(instanceId, entry.getValue()) != null) {
          throw new IllegalArgumentException(
              "search provider instance '" + instanceId + "' declares type more than once");
        }
        continue;
      }
      final int optionAt = remainder.lastIndexOf(OPTION_SEGMENT);
      if (optionAt > 0 && optionAt + OPTION_SEGMENT.length() < remainder.length()) {
        final String instanceId = remainder.substring(0, optionAt);
        final String option = remainder.substring(optionAt + OPTION_SEGMENT.length());
        SearchIndexRegistry.requireStableId(
            instanceId, "configured search provider instance id");
        SearchIndexRegistry.requireStableId(option, "search provider option name");
        configuredOptions.computeIfAbsent(instanceId, ignored -> new TreeMap<>())
            .put(option, entry.getValue());
        continue;
      }
      {
        throw new IllegalArgumentException("unsupported search provider configuration key '"
            + key + "'; use " + PREFIX + "<instance-id>" + TYPE_SUFFIX + " or "
            + PREFIX + "<instance-id>" + OPTION_SEGMENT + "<option>");
      }
    }
    for (Map.Entry<String, SortedMap<String, String>> options : configuredOptions.entrySet()) {
      if (!configuredTypes.containsKey(options.getKey())) {
        throw new IllegalArgumentException("search provider options for instance '"
            + options.getKey() + "' require a matching type declaration");
      }
    }
    for (Map.Entry<String, String> declaration : configuredTypes.entrySet()) {
      final String instanceId = declaration.getKey();
      final String providerId = declaration.getValue();
      final String key = PREFIX + instanceId + TYPE_SUFFIX;
      if (providerId == null || providerId.isBlank()
          || !providerId.equals(providerId.trim())) {
        throw new IllegalArgumentException(key + " must be a nonblank trimmed provider id");
      }
      final SearchIndexProviderFactory factory = byProviderId.get(providerId);
      if (factory == null) {
        throw new IllegalArgumentException("search provider instance '" + instanceId
            + "' names unknown provider '" + providerId + "'; available providers: "
            + byProviderId.keySet());
      }
      final Instance existing = instances.get(instanceId);
      if (existing != null && existing.factory() != factory) {
        throw new IllegalArgumentException("search provider instance '" + instanceId
            + "' shadows the default instance of provider '"
            + existing.factory().providerId() + "'");
      }
      final StandardSearchProvider standard = existing == null ? null : existing.standard();
      instances.put(instanceId, new Instance(instanceId, factory,
          factory.configureInstance(instanceId,
              configuredOptions.getOrDefault(instanceId, new TreeMap<>())),
          capabilitiesByProviderId.get(factory.providerId()), standard));
    }
    return new SearchProviderCatalog(instances);
  }

  /**
   * Returns wire descriptors for every instance in stable instance-id order.
   *
   * @return Immutable instance descriptors.
   */
  public List<SearchProviderInstance> instances() {
    final List<SearchProviderInstance> result = new ArrayList<>(instances.size());
    for (Instance instance : instances.values()) {
      final SearchProviderInstance.Builder builder = SearchProviderInstance.newBuilder()
          .setInstanceId(instance.instanceId())
          .setProviderId(instance.factory().providerId());
      instance.capabilities().stream().sorted().forEach(builder::addCapabilities);
      if (instance.standard() != null) {
        builder.setStandard(instance.standard());
      }
      result.add(builder.build());
    }
    return List.copyOf(result);
  }

  /**
   * Resolves one client-supplied selector.
   *
   * @param selector Selector naming a standard shorthand or a configured instance id.
   * @return The matching instance.
   * @throws AnalysisException If the selector is unspecified or names no configured instance.
   */
  public Instance resolve(SearchProviderSelector selector) {
    if (selector == null
        || selector.getKindCase() == SearchProviderSelector.KindCase.KIND_NOT_SET) {
      throw AnalysisException.invalidArgument(
          "Search provider selector must name a standard provider or a configured instance");
    }
    if (selector.hasStandard()) {
      final Instance instance = defaultInstance(selector.getStandard());
      if (instance == null) {
        throw AnalysisException.invalidArgument(
            "Search provider selector names an unavailable standard provider "
                + selector.getStandard());
      }
      return instance;
    }
    return find(selector.getCustom());
  }

  /**
   * Resolves one configured instance id.
   *
   * @param instanceId Configured instance id.
   * @return The matching instance.
   * @throws AnalysisException If the id names no configured instance.
   */
  public Instance find(String instanceId) {
    final Instance instance = findOrNull(instanceId);
    if (instance == null) {
      throw AnalysisException.invalidArgument("Unknown search provider instance '"
          + instanceId + "'; configured instances: " + instances.keySet());
    }
    return instance;
  }

  /**
   * Returns one configured instance without failing.
   *
   * @param instanceId Configured instance id.
   * @return The matching instance, or {@code null} when the id is unknown.
   */
  Instance findOrNull(String instanceId) {
    return instanceId == null ? null : instances.get(instanceId);
  }

  /**
   * Returns the default instance selected by a standard shorthand.
   *
   * @param standard Standard provider value.
   * @return The default instance, or {@code null} when the provider is not on the classpath
   *     or the value is unspecified.
   */
  public Instance defaultInstance(StandardSearchProvider standard) {
    for (Instance instance : instances.values()) {
      if (instance.standard() == standard) {
        return instance;
      }
    }
    return null;
  }

  /**
   * Maps a built-in provider id to its standard shorthand.
   *
   * @param providerId Discovered provider id.
   * @return The standard value, or {@code null} for extension providers.
   */
  private static StandardSearchProvider standardFor(String providerId) {
    return switch (providerId) {
      case FlatFloatSearchIndexProviderFactory.PROVIDER_ID ->
          StandardSearchProvider.STANDARD_SEARCH_PROVIDER_FLAT_FLOAT;
      case TURBO_QUANT_PROVIDER_ID ->
          StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT;
      default -> null;
    };
  }
}
