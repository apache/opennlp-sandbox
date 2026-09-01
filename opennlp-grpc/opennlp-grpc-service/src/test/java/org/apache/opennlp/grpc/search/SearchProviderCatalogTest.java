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

import org.apache.opennlp.grpc.spi.AnalysisException;
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
import org.apache.opennlp.grpc.spi.search.SearchIndexProviderFactory;

class SearchProviderCatalogTest {

  @Test
  void discoversTheBuiltInProvidersWithoutTheTurboQuantAddOn() {
    // The TurboQuant provider ships in the opennlp-grpc-search-turboquant add-on, absent
    // from this module's classpath; the add-on module asserts the full provider set.
    assertEquals(List.of("flat_float", "terms"),
        SearchProviderCatalog.discover().instances().stream()
            .map(SearchProviderInstance::getInstanceId).toList());
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
  void termsInstanceRecordsItsAnalysisChainIdentity() {
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();

    final SearchProviderCatalog.Instance terms = catalog.resolve(
        SearchProviderSelector.newBuilder().setCustom("terms").build());

    final var chain = terms.configured().analysisChain();
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
