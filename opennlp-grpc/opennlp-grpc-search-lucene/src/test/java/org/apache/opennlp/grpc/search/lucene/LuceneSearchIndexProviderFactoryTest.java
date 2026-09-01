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
package org.apache.opennlp.grpc.search.lucene;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.opennlp.grpc.search.SearchProviderCatalog;
import org.apache.opennlp.grpc.spi.search.SearchIndexProviderFactory;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;
import org.apache.opennlp.grpc.v1.SearchProviderSelector;
import org.apache.opennlp.grpc.v1.SearchProviderInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the provider registration: the server's provider catalog discovers the Lucene
 * keyword provider through ServiceLoader exactly as a deployment discovers a
 * dropped-in jar.
 */
class LuceneSearchIndexProviderFactoryTest {

  @Test
  void declaresTheKeywordLiveCapabilitySet() {
    final LuceneSearchIndexProviderFactory factory = new LuceneSearchIndexProviderFactory();

    assertEquals("lucene", factory.providerId());
    assertEquals(Set.of(
            SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_KEYWORD,
            SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_LIVE),
        factory.capabilities());
    assertEquals("lucene-standard", factory.analysisChain().getChainId());
  }

  @Test
  void rejectsUnsupportedProviderOptions() {
    final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> new LuceneSearchIndexProviderFactory()
            .configureInstance("bm25", Map.of("analyzer", "whitespace")));
    assertTrue(failure.getMessage().contains("analyzer"));
  }

  @Test
  void registersThroughTheSearchSpi() {
    assertTrue(ServiceLoader.load(SearchIndexProviderFactory.class).stream()
        .anyMatch(provider -> provider.type() == LuceneSearchIndexProviderFactory.class));
  }

  @Test
  void theServerCatalogDiscoversTheLuceneDefaultInstance() {
    assertEquals(List.of("flat_float", "lucene", "terms"),
        SearchProviderCatalog.discover().instances().stream()
            .map(SearchProviderInstance::getInstanceId).toList());
  }

  @Test
  void configuredInstancesResolveToTypedLuceneConfigurations() {
    final SearchProviderCatalog catalog = SearchProviderCatalog.fromConfiguration(Map.of(
        "search.provider.bm25.type", "lucene"));

    assertEquals(List.of("bm25", "flat_float", "lucene", "terms"),
        catalog.instances().stream()
            .map(SearchProviderInstance::getInstanceId).toList());
    assertInstanceOf(LuceneSearchIndexProviderFactory.Configuration.class,
        catalog.resolve(SearchProviderSelector.newBuilder().setCustom("bm25").build())
            .configured());
  }
}
