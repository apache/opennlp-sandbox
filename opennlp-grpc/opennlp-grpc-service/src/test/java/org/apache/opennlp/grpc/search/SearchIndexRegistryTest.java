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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.apache.opennlp.grpc.v1.SearchCorpusDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexBuildDescriptor;
import org.apache.opennlp.grpc.v1.SearchMetric;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;
import org.apache.opennlp.grpc.v1.SearchProviderSelector;
import org.apache.opennlp.grpc.v1.StandardSearchProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.opennlp.grpc.spi.search.SearchIndexProvider;
import org.apache.opennlp.grpc.spi.search.SearchResult;
import org.apache.opennlp.grpc.spi.search.SearchIndexProviderFactory;
import org.apache.opennlp.grpc.spi.search.SearchIndexBundleConfiguration;

class SearchIndexRegistryTest {

  private static final Set<SearchProviderCapability> BUNDLE_CAPABILITIES = Set.of(
      SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_VECTOR,
      SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_BUNDLE);

  @Test
  void bundleDefaultTopKMatchesTheDynamicWorkspaceDefault() {
    // Startup bundles and dynamic workspaces must not disagree on the default result
    // depth; the per-index max_top_k setting raises it up to the fixed ceiling.
    assertEquals(1_000, SearchIndexBundleConfiguration.DEFAULT_MAX_TOP_K);
  }

  @Test
  void allowsFiftyThousandResultsWithinTheFixedSearchCeilings() {
    assertEquals(50_000, SearchIndexBundleConfiguration.MAX_TOP_K_LIMIT);
    assertEquals(50_000, SearchIndexBundleConfiguration.MAX_ALL_HITS_LIMIT);
  }

  @Test
  void closesProvidersInReverseLoadOrder() {
    final List<String> closed = new ArrayList<>();
    final SearchIndexProvider first = closeableProvider(descriptor("first"), closed);
    final SearchIndexProvider second = closeableProvider(descriptor("second"), closed);
    final SearchIndexRegistry registry = new SearchIndexRegistry(List.of(first, second));

    registry.close();

    assertEquals(List.of("second", "first"), closed);
  }

  @Test
  void closesPartialLoadsWhenALaterFactoryFails() {
    final List<String> closed = new ArrayList<>();
    final SearchIndexProviderFactory factory = new SearchIndexProviderFactory() {
      @Override
      public String providerId() {
        return "test";
      }

      @Override
      public Set<SearchProviderCapability> capabilities() {
        return BUNDLE_CAPABILITIES;
      }

      @Override
      public SearchIndexProvider load(SearchIndexBundleConfiguration configuration)
          throws IOException {
        if (configuration.indexId().equals("second")) {
          throw new IOException("later bundle failed");
        }
        return closeableProvider(descriptor(configuration.indexId()), closed);
      }
    };
    final Map<String, String> configuration = Map.of(
        "search.indexes", "first,second",
        "search.index.first.provider", "test",
        "search.index.first.directory", "/tmp/first-index",
        "search.index.first.passages", "/tmp/first-passages.jsonl",
        "search.index.second.provider", "test",
        "search.index.second.directory", "/tmp/second-index",
        "search.index.second.passages", "/tmp/second-passages.jsonl");

    assertThrows(IOException.class,
        () -> SearchIndexRegistry.fromConfiguration(configuration, List.of(factory)));
    assertEquals(List.of("first"), closed);
  }

  @Test
  void storesProvidersAndListsDescriptorsInStableIdOrder() {
    final SearchIndexProvider second = provider(descriptor("zeta"));
    final SearchIndexProvider first = provider(descriptor("alpha"));
    final SearchIndexRegistry registry = new SearchIndexRegistry(List.of(second, first));

    assertEquals(List.of("alpha", "zeta"), registry.descriptors().stream()
        .map(SearchIndexDescriptor::getIndexId).toList());
    assertSame(first, registry.require("alpha"));
  }

  @Test
  void rejectsDuplicateIdsBeforeServing() {
    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new SearchIndexRegistry(List.of(
            provider(descriptor("legal")), provider(descriptor("legal")))));

    assertTrue(exception.getMessage().contains("legal"));
    assertTrue(exception.getMessage().contains("more than once"));
  }

  @Test
  void reportsUnknownIndexAsNotFound() {
    final SearchIndexRegistry registry = new SearchIndexRegistry(List.of());

    final AnalysisException exception = assertThrows(AnalysisException.class,
        () -> registry.require("missing"));

    assertEquals(AnalysisException.FailureType.NOT_FOUND, exception.getFailureType());
    assertTrue(exception.getMessage().contains("missing"));
  }

  @Test
  void rejectsUnboundedIndexCountsAndInvalidTypedFields() {
    final List<SearchIndexProvider> tooMany = new ArrayList<>();
    for (int i = 0; i <= SearchIndexRegistry.DEFAULT_MAX_INDEXES; i++) {
      tooMany.add(provider(descriptor("index" + i)));
    }
    assertThrows(IllegalArgumentException.class, () -> new SearchIndexRegistry(tooMany));

    final SearchIndexDescriptor noProvider = descriptor("no-provider").toBuilder()
        .clearProvider().build();
    final SearchIndexDescriptor noMetric = descriptor("no-metric").toBuilder()
        .setMetric(SearchMetric.SEARCH_METRIC_UNSPECIFIED).build();
    final SearchIndexDescriptor noVectorSpace = descriptor("no-space").toBuilder()
        .setEmbeddingRoute(descriptor("source").getEmbeddingRoute().toBuilder()
            .clearVectorSpaceId()).build();
    final SearchIndexDescriptor tooManyRecords = descriptor("too-many-records").toBuilder()
        .setSize(SearchIndexBundleConfiguration.MAX_RECORDS_LIMIT + 1).build();

    assertThrows(IllegalArgumentException.class,
        () -> new SearchIndexRegistry(List.of(provider(noProvider))));
    assertThrows(IllegalArgumentException.class,
        () -> new SearchIndexRegistry(List.of(provider(noMetric))));
    assertThrows(IllegalArgumentException.class,
        () -> new SearchIndexRegistry(List.of(provider(noVectorSpace))));
    assertThrows(IllegalArgumentException.class,
        () -> new SearchIndexRegistry(List.of(provider(tooManyRecords))));
  }

  @Test
  void validatesTheExhaustiveCapabilityIndependentlyOfMaxTopK() {
    final SearchIndexDescriptor exhaustive = descriptor("exhaustive").toBuilder()
        .setSize(2).setMaxTopK(1).setSupportsAllHits(true).build();
    final SearchIndexRegistry registry = new SearchIndexRegistry(List.of(provider(exhaustive)));
    assertTrue(registry.require("exhaustive").descriptor().getSupportsAllHits());

    final SearchIndexDescriptor wrongProvider = exhaustive.toBuilder()
        .setIndexId("flat")
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_FLAT_FLOAT))
        .build();
    final SearchIndexDescriptor tooLarge = exhaustive.toBuilder()
        .setIndexId("too-large")
        .setSize(SearchIndexBundleConfiguration.MAX_ALL_HITS_LIMIT + 1)
        .build();
    assertThrows(IllegalArgumentException.class,
        () -> new SearchIndexRegistry(List.of(provider(wrongProvider))));
    assertThrows(IllegalArgumentException.class,
        () -> new SearchIndexRegistry(List.of(provider(tooLarge))));
  }

  @Test
  void rejectsUntrimmedDescriptorStringsAndCustomProviders() {
    final SearchIndexDescriptor untrimmedId = descriptor("legal").toBuilder()
        .setIndexId(" legal ").build();
    final SearchIndexDescriptor untrimmedProvider = descriptor("custom").toBuilder()
        .setProvider(SearchProviderSelector.newBuilder().setCustom(" vendor ")).build();
    final SearchIndexDescriptor untrimmedDisplayName = descriptor("display").toBuilder()
        .setDisplayName(" Legal corpus ").build();
    final SearchIndexDescriptor untrimmedVectorSpace = descriptor("space").toBuilder()
        .setEmbeddingRoute(descriptor("source").getEmbeddingRoute().toBuilder()
            .setVectorSpaceId(" mini-v1 ")).build();
    final SearchIndexDescriptor untrimmedBackend = descriptor("backend").toBuilder()
        .setEmbeddingRoute(descriptor("source").getEmbeddingRoute().toBuilder()
            .setBackendId(" static ")).build();
    final SearchIndexDescriptor untrimmedModel = descriptor("model").toBuilder()
        .setEmbeddingRoute(descriptor("source").getEmbeddingRoute().toBuilder()
            .setModelId(" mini ")).build();
    final SearchIndexDescriptor untrimmedCorpus = descriptor("corpus").toBuilder()
        .setCorpus(descriptor("source").getCorpus().toBuilder()
            .setProvenanceSummary(" Test fixture ")).build();
    final SearchIndexDescriptor untrimmedBuilder = descriptor("builder").toBuilder()
        .setBuild(descriptor("source").getBuild().toBuilder()
            .setBuilderId(" test-builder ")).build();

    for (SearchIndexDescriptor invalid : List.of(
        untrimmedId, untrimmedProvider, untrimmedDisplayName, untrimmedVectorSpace,
        untrimmedBackend, untrimmedModel, untrimmedCorpus, untrimmedBuilder)) {
      assertThrows(IllegalArgumentException.class,
          () -> new SearchIndexRegistry(List.of(provider(invalid))));
    }
  }

  @Test
  void enforcesConfiguredIndexCountBeforeLoadingBundles() {
    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> SearchIndexRegistry.fromConfiguration(Map.of(
            "search.max_indexes", "1",
            "search.indexes", "one,two"), List.of()));

    assertTrue(exception.getMessage().contains("count"));
    assertTrue(exception.getMessage().contains("1"));
  }

  @Test
  void passesOnlyImmutableNamespacedProviderOptionsToTheFactory() throws Exception {
    final AtomicReference<Map<String, String>> received = new AtomicReference<>();
    final SearchIndexProviderFactory factory = new SearchIndexProviderFactory() {
      @Override
      public String providerId() {
        return "test";
      }

      @Override
      public Set<SearchProviderCapability> capabilities() {
        return BUNDLE_CAPABILITIES;
      }

      @Override
      public SearchIndexProvider load(SearchIndexBundleConfiguration configuration) {
        received.set(configuration.providerOptions());
        return provider(descriptor(configuration.indexId()));
      }
    };
    final Map<String, String> configuration = Map.of(
        "search.indexes", "legal",
        "search.index.legal.provider", "test",
        "search.index.legal.directory", "/tmp/legal-index",
        "search.index.legal.passages", "/tmp/legal-passages.jsonl",
        "search.index.legal.max_top_k", "7",
        "search.index.legal.provider_option.beam_size", "16",
        "search.index.legal.provider_option.segment.mode", "exact");

    final SearchIndexRegistry registry = SearchIndexRegistry.fromConfiguration(
        configuration, List.of(factory));

    assertEquals(Map.of("beam_size", "16", "segment.mode", "exact"), received.get());
    assertThrows(UnsupportedOperationException.class,
        () -> received.get().put("later", "mutation"));
    registry.close();
  }

  @Test
  void rejectsInvalidProviderOptionNamesAndUntrimmedValues() {
    final SearchIndexProviderFactory factory = new SearchIndexProviderFactory() {
      @Override
      public String providerId() {
        return "test";
      }

      @Override
      public Set<SearchProviderCapability> capabilities() {
        return BUNDLE_CAPABILITIES;
      }

      @Override
      public SearchIndexProvider load(SearchIndexBundleConfiguration configuration) {
        return provider(descriptor(configuration.indexId()));
      }
    };
    final Map<String, String> configuration = new HashMap<>(Map.of(
        "search.indexes", "legal",
        "search.index.legal.provider", "test",
        "search.index.legal.directory", "/tmp/legal-index",
        "search.index.legal.passages", "/tmp/legal-passages.jsonl"));
    configuration.put("search.index.legal.provider_option.Bad", "value");
    assertThrows(IllegalArgumentException.class,
        () -> SearchIndexRegistry.fromConfiguration(configuration, List.of(factory)));

    configuration.remove("search.index.legal.provider_option.Bad");
    configuration.put("search.index.legal.provider_option.beam_size", " padded ");
    assertThrows(IllegalArgumentException.class,
        () -> SearchIndexRegistry.fromConfiguration(configuration, List.of(factory)));

    configuration.remove("search.index.legal.provider_option.beam_size");
    configuration.put("search.index.legal.provider", " test ");
    assertThrows(IllegalArgumentException.class,
        () -> SearchIndexRegistry.fromConfiguration(configuration, List.of(factory)));
  }

  @Test
  void rejectsBundleConfigurationNamingANonBundleProvider() {
    final SearchIndexProviderFactory factory = new SearchIndexProviderFactory() {
      @Override
      public String providerId() {
        return "test";
      }

      @Override
      public Set<SearchProviderCapability> capabilities() {
        return Set.of(
            SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_VECTOR,
            SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_LIVE);
      }
    };
    final Map<String, String> configuration = Map.of(
        "search.indexes", "legal",
        "search.index.legal.provider", "test",
        "search.index.legal.directory", "/tmp/legal-index",
        "search.index.legal.passages", "/tmp/legal-passages.jsonl");

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> SearchIndexRegistry.fromConfiguration(configuration, List.of(factory)));

    assertTrue(exception.getMessage().contains("does not load immutable bundles"));
  }

  @Test
  void rejectsNullProviderFactoryAtTheRegistryBoundary() {
    final List<SearchIndexProviderFactory> factories = new ArrayList<>();
    factories.add(null);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> SearchIndexRegistry.fromConfiguration(Map.of(), factories));

    assertEquals("search provider factories must not contain null", exception.getMessage());
  }

  private static SearchIndexProvider provider(SearchIndexDescriptor descriptor) {
    return new SearchIndexProvider() {
      @Override
      public SearchIndexDescriptor descriptor() {
        return descriptor;
      }

      @Override
      public List<SearchResult> search(float[] queryVector, int topK) {
        return List.of();
      }
    };
  }

  private static SearchIndexProvider closeableProvider(
      SearchIndexDescriptor descriptor, List<String> closed) {
    return new SearchIndexProvider() {
      @Override
      public SearchIndexDescriptor descriptor() {
        return descriptor;
      }

      @Override
      public List<SearchResult> search(float[] queryVector, int topK) {
        return List.of();
      }

      @Override
      public void close() {
        closed.add(descriptor.getIndexId());
      }
    };
  }

  static SearchIndexDescriptor descriptor(String id) {
    return SearchIndexDescriptor.newBuilder()
        .setIndexId(id)
        .setDisplayName(id)
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
        .setEmbeddingRoute(EmbeddingRoute.newBuilder()
            .setModelId("mini")
            .setBackendId("static")
            .setVectorSpaceId("mini-v1")
            .setArtifactHash("a".repeat(64)))
        .setDimension(4)
        .setMetric(SearchMetric.SEARCH_METRIC_COSINE)
        .setSize(1)
        .setImmutable(true)
        .setCorpus(SearchCorpusDescriptor.newBuilder()
            .setTitle("Legal corpus")
            .setProvenanceSummary("Test fixture"))
        .setMaxTopK(50)
        .setMaxQueryBytes(1024)
        .setMaxResponseBytes(1_048_576)
        .setBuild(SearchIndexBuildDescriptor.newBuilder()
            .setBundleFormatVersion(1)
            .setBundleArtifactHash("b".repeat(64))
            .setBuilderId("test-builder")
            .setBuilderVersion("1")
            .setPreparationConfigHash("c".repeat(64)))
        .build();
  }
}
