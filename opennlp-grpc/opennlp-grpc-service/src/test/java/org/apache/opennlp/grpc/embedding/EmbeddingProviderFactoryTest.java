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

import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;

/**
 * Tests {@link EmbeddingProviderFactory}: it discovers every embedding backend via
 * {@link java.util.ServiceLoader} and aggregates the ones with configured models into a
 * {@link CompositeEmbeddingProvider}. (Multi-engine routing/fallback is covered by
 * {@link CompositeEmbeddingProviderTest}; this verifies discovery + aggregation.) The
 * ServiceLoader-registered {@link StubEmbeddingBackendFactory} stands in for a third-party engine.
 */
class EmbeddingProviderFactoryTest {

  @Test
  void onnxPathWithoutTheAddOnFailsLoud() {
    // The onnx/cuda engines ship in the opennlp-grpc-dl add-on, absent from this module's
    // classpath; configured ONNX-family models must fail startup, never silently vanish.
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        EmbeddingProviderFactory.create(Map.of(
            "model.embedder.m1.onnx.path", "/tmp/model.onnx",
            "model.embedder.m1.vocab.path", "/tmp/vocab.txt")));
    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, error.getFailureType());
    assertTrue(error.getMessage().contains("opennlp-grpc-dl"));
  }

  @Test
  void cudaPathWithoutTheAddOnFailsLoud() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        EmbeddingProviderFactory.create(Map.of(
            "model.embedder.m1.cuda.path", "/tmp/model.onnx",
            "model.embedder.m1.vocab.path", "/tmp/vocab.txt")));
    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, error.getFailureType());
    assertTrue(error.getMessage().contains("opennlp-grpc-dl"));
  }

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

  @Test
  void closesEarlierProvidersWhenALaterFactoryFails() {
    TrackingEmbeddingBackendFactory.reset();

    assertThrows(RuntimeException.class, () -> EmbeddingProviderFactory.create(Map.of(
        TrackingEmbeddingBackendFactory.KEY_MODEL_ID, "tracking-model",
        FailingEmbeddingBackendFactory.KEY_FAIL, "true")));

    assertTrue(TrackingEmbeddingBackendFactory.wasClosed(),
        "a provider created before a later factory failed was leaked");
  }

  @Test
  void rejectsWhitespaceInBackendId() {
    // A backend id with leading, trailing, or inner whitespace would be advertised verbatim.
    for (String id : List.of("onnx ", " onnx", "on nx", "onnx\t")) {
      final AnalysisException error = assertThrows(AnalysisException.class,
          () -> EmbeddingProviderFactory.validId(id, "test"), "id '" + id + "'");
      assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    }
  }

  @Test
  void acceptsPlainLowercaseBackendId() {
    assertEquals("onnx", EmbeddingProviderFactory.validId("onnx", "test"));
  }
}
