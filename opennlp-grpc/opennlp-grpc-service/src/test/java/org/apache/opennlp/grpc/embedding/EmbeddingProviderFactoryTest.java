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
package org.apache.opennlp.grpc.embedding;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link EmbeddingProviderFactory}: it discovers every embedding backend via
 * {@link java.util.ServiceLoader} and aggregates the ones with configured models into a
 * {@link CompositeEmbeddingProvider}. (Multi-engine routing/fallback is covered by
 * {@link CompositeEmbeddingProviderTest}; this verifies discovery + aggregation.) The
 * ServiceLoader-registered {@link StubEmbeddingBackendFactory} stands in for a third-party engine.
 */
class EmbeddingProviderFactoryTest {

  @Test
  void aggregatesBackendsIntoComposite() {
    final EmbeddingProvider provider = EmbeddingProviderFactory.create(Map.of());
    assertInstanceOf(CompositeEmbeddingProvider.class, provider);
  }

  @Test
  void emptyWhenNoEmbeddingModelsConfigured() {
    // No backend has models configured, so the aggregate serves nothing (rather than failing).
    final EmbeddingProvider provider = EmbeddingProviderFactory.create(Map.of());
    assertFalse(provider.isAvailable());
  }

  @Test
  void discoversExternalBackendThroughServiceLoader() {
    // The stub engine, registered only via test META-INF/services, contributes a model and is
    // aggregated like any built-in backend; its model resolves to the stub engine.
    final EmbeddingProvider provider = EmbeddingProviderFactory.create(
        Map.of(StubEmbeddingBackendFactory.KEY_MODEL_ID, "demo"));
    assertTrue(provider.isAvailable());
    assertTrue(provider.supportsModel("demo"));
    assertEquals(StubEmbeddingProvider.BACKEND_ID, provider.backendId("demo"));
    assertEquals(3, provider.embeddingDimension("demo"));
  }
}
