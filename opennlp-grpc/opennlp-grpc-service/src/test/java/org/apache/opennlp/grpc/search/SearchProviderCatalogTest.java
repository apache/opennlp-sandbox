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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;
import org.apache.opennlp.grpc.v1.SearchProviderInstance;
import org.apache.opennlp.grpc.v1.SearchProviderSelector;
import org.apache.opennlp.grpc.v1.StandardSearchProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchProviderCatalogTest {

  @Test
  void discoversDefaultInstancesForEveryBuiltInProvider() {
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();

    final List<SearchProviderInstance> instances = catalog.instances();
    assertEquals(List.of("flat_float", "terms", "turbo_quant"), instances.stream()
        .map(SearchProviderInstance::getInstanceId).toList());

    final SearchProviderInstance flat = instances.getFirst();
    assertEquals("flat_float", flat.getProviderId());
    assertTrue(flat.getCapabilitiesList().contains(
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_VECTOR));
    assertTrue(flat.getCapabilitiesList().contains(
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_LIVE));
    assertTrue(flat.hasStandard());
    assertEquals(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_FLAT_FLOAT,
        flat.getStandard());

    final SearchProviderInstance terms = instances.get(1);
    assertEquals("terms", terms.getProviderId());
    assertTrue(terms.getCapabilitiesList().contains(
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_KEYWORD));
    assertTrue(!terms.hasStandard());

    final SearchProviderInstance turbo = instances.get(2);
    assertTrue(turbo.getCapabilitiesList().containsAll(List.of(
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_VECTOR,
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_LIVE,
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_BUNDLE,
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_PERSISTENT)));
    assertEquals(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT,
        turbo.getStandard());
  }

  @Test
  void configuredInstancesJoinTheDefaultsUnderTheirOwnIds() {
    final SearchProviderCatalog catalog = SearchProviderCatalog.fromConfiguration(Map.of(
        "search.provider.fast-workspace.type", "turbo_quant"));

    assertEquals(List.of("fast-workspace", "flat_float", "terms", "turbo_quant"),
        catalog.instances().stream().map(SearchProviderInstance::getInstanceId).toList());
    final SearchProviderInstance configured = catalog.instances().getFirst();
    assertEquals("turbo_quant", configured.getProviderId());
    assertTrue(!configured.hasStandard());
  }

  @Test
  void resolvesStandardSelectorsToTheDefaultInstance() {
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();

    final SearchProviderCatalog.Instance flat = catalog.resolve(
        SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_FLAT_FLOAT)
            .build());

    assertEquals("flat_float", flat.instanceId());
    assertEquals(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_FLAT_FLOAT, flat.standard());
    assertSame(flat, catalog.resolve(SearchProviderSelector.newBuilder()
        .setCustom("flat_float").build()));
  }

  @Test
  void resolvesCustomSelectorsToConfiguredInstances() {
    final SearchProviderCatalog catalog = SearchProviderCatalog.fromConfiguration(Map.of(
        "search.provider.fast-workspace.type", "turbo_quant"));

    final SearchProviderCatalog.Instance configured = catalog.resolve(
        SearchProviderSelector.newBuilder().setCustom("fast-workspace").build());

    assertEquals("fast-workspace", configured.instanceId());
    assertEquals("turbo_quant", configured.factory().providerId());
    assertNull(configured.standard());
  }

  @Test
  void unknownCustomInstancesListTheAvailableIds() {
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();

    final AnalysisException exception = assertThrows(AnalysisException.class,
        () -> catalog.resolve(SearchProviderSelector.newBuilder()
            .setCustom("missing").build()));

    assertTrue(exception.getMessage().contains("missing"));
    assertTrue(exception.getMessage().contains("flat_float"));
    assertTrue(exception.getMessage().contains("turbo_quant"));
  }

  @Test
  void rejectsUnspecifiedSelectors() {
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();

    assertThrows(AnalysisException.class,
        () -> catalog.resolve(SearchProviderSelector.getDefaultInstance()));
    assertThrows(AnalysisException.class,
        () -> catalog.resolve(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_UNSPECIFIED)
            .build()));
  }

  @Test
  void rejectsInstancesNamingUnknownProviders() {
    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> SearchProviderCatalog.fromConfiguration(Map.of(
            "search.provider.fast.type", "lucene")));

    assertTrue(exception.getMessage().contains("lucene"));
    assertTrue(exception.getMessage().contains("flat_float"));
  }

  @Test
  void rejectsInstancesShadowingADifferentProvidersDefaultId() {
    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> SearchProviderCatalog.fromConfiguration(Map.of(
            "search.provider.flat_float.type", "turbo_quant")));

    assertTrue(exception.getMessage().contains("flat_float"));
  }

  @Test
  void rejectsUnknownInstanceConfigurationKeys() {
    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> SearchProviderCatalog.fromConfiguration(Map.of(
            "search.provider.fast.type", "turbo_quant",
            "search.provider.fast.bits", "8")));

    assertTrue(exception.getMessage().contains("search.provider.fast.bits"));
  }

  @Test
  void termsInstanceRecordsItsAnalysisChainIdentity() {
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();

    final SearchProviderCatalog.Instance terms = catalog.resolve(
        SearchProviderSelector.newBuilder().setCustom("terms").build());

    final var chain = terms.factory().analysisChain();
    assertEquals(TermsSearchIndexProviderFactory.CHAIN_ID, chain.getChainId());
    assertEquals(TermsSearchIndexProviderFactory.CHAIN_VERSION, chain.getChainVersion());
    assertTrue(!chain.hasConfigurationHash());
  }

  @Test
  void snapshotsFactoryCapabilitiesExactlyOnceAtDiscovery() {
    final AtomicInteger calls = new AtomicInteger();
    final SearchIndexProviderFactory factory = new SearchIndexProviderFactory() {
      @Override
      public String providerId() {
        return "stateful-test";
      }

      @Override
      public Set<SearchProviderCapability> capabilities() {
        calls.incrementAndGet();
        return Set.of(SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_VECTOR);
      }
    };

    final SearchProviderCatalog catalog = SearchProviderCatalog.fromConfiguration(
        Map.of(), List.of(factory));
    catalog.instances();
    catalog.find("stateful-test").has(
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_VECTOR);

    assertEquals(1, calls.get());
  }
}
