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
package org.apache.opennlp.grpc.embedding.tei;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.embedding.EmbeddingProviderFactory;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.tei.v1.EmbedGrpc;
import org.apache.opennlp.grpc.tei.v1.EmbedRequest;
import org.apache.opennlp.grpc.tei.v1.EmbedResponse;
import org.apache.opennlp.grpc.tei.v1.InfoGrpc;
import org.apache.opennlp.grpc.tei.v1.InfoRequest;
import org.apache.opennlp.grpc.tei.v1.InfoResponse;
import org.apache.opennlp.grpc.tei.v1.ModelType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link TeiEmbeddingProvider} against an embedded gRPC server implementing
 * the {@code tei.v1} surface. The stub returns deterministic vectors whose first
 * component is the input length, which makes ordering verifiable.
 */
class TeiEmbeddingProviderTest {

  private static final int DIMENSION = 3;

  private static Server embeddingServer;
  private static Server classifierServer;

  @BeforeAll
  static void startServers() throws IOException {
    embeddingServer = ServerBuilder.forPort(0)
        .addService(new StubInfoService(ModelType.MODEL_TYPE_EMBEDDING))
        .addService(new StubEmbedService())
        .build()
        .start();
    classifierServer = ServerBuilder.forPort(0)
        .addService(new StubInfoService(ModelType.MODEL_TYPE_CLASSIFIER))
        .addService(new StubEmbedService())
        .build()
        .start();
  }

  @AfterAll
  static void stopServers() throws InterruptedException {
    embeddingServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    classifierServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }

  @Test
  void connectsAndDiscoversDimension() {
    final TeiEmbeddingProvider provider = new TeiEmbeddingProvider(config("minilm"));
    try {
      assertTrue(provider.isAvailable());
      assertEquals(Set.of("minilm"), provider.registeredModelIds());
      assertEquals(DIMENSION, provider.embeddingDimension("minilm"));
      assertEquals("minilm", provider.resolveModelId(null));
      assertEquals(TeiEmbeddingBackendFactory.BACKEND_ID, provider.backendId());
    } finally {
      provider.close();
    }
  }

  @Test
  void embedReturnsServerVector() {
    final TeiEmbeddingProvider provider = new TeiEmbeddingProvider(config("minilm"));
    try {
      final float[] vector = provider.embed("minilm", "hello");
      assertEquals(DIMENSION, vector.length);
      assertEquals(5f, vector[0]);
    } finally {
      provider.close();
    }
  }

  @Test
  void embedBatchPreservesInputOrder() {
    final TeiEmbeddingProvider provider = new TeiEmbeddingProvider(config("minilm"));
    try {
      final List<float[]> vectors =
          provider.embedBatch("minilm", List.of("a", "bb", "ccc", "dddd"));
      assertEquals(4, vectors.size());
      for (int i = 0; i < vectors.size(); i++) {
        assertEquals(i + 1f, vectors.get(i)[0], "vector " + i + " out of order");
      }
    } finally {
      provider.close();
    }
  }

  @Test
  void factoryAggregatesTeiThroughServiceLoader() throws Exception {
    // The factory discovers the TEI backend via ServiceLoader and aggregates it into the composite
    // provider; the TEI-configured model resolves to the TEI engine.
    final EmbeddingProvider provider = EmbeddingProviderFactory.create(config("minilm"));
    try {
      assertTrue(provider.isAvailable());
      assertTrue(provider.supportsModel("minilm"));
      assertEquals(TeiEmbeddingBackendFactory.BACKEND_ID, provider.backendId("minilm"));
    } finally {
      if (provider instanceof AutoCloseable closeable) {
        closeable.close();
      }
    }
  }

  @Test
  void isInertWhenNoTargetConfigured() {
    // No .tei.target keys: the provider must construct cleanly and report itself unavailable so
    // the composite can aggregate it alongside other engines in a deploy that does not use TEI.
    final TeiEmbeddingProvider provider = new TeiEmbeddingProvider(Map.of());
    try {
      assertFalse(provider.isAvailable());
      assertTrue(provider.registeredModelIds().isEmpty());
    } finally {
      provider.close();
    }
  }

  @Test
  void rejectsNonEmbeddingModel() {
    final Map<String, String> configuration = new HashMap<>();
    configuration.put("model.embedder.minilm.tei.target",
        "localhost:" + classifierServer.getPort());
    configuration.put("model.embedder.tei.deadline_ms", "5000");
    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> new TeiEmbeddingProvider(configuration));
    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, e.getFailureType());
    assertTrue(e.getMessage().contains("CLASSIFIER"), e.getMessage());
  }

  @Test
  void failsFastForUnreachableTarget() {
    final Map<String, String> configuration = new HashMap<>();
    configuration.put("model.embedder.minilm.tei.target", "localhost:1");
    configuration.put("model.embedder.tei.deadline_ms", "2000");
    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> new TeiEmbeddingProvider(configuration));
    assertEquals(AnalysisException.FailureType.INTERNAL, e.getFailureType());
  }

  @Test
  void rejectsUnknownModelId() {
    final TeiEmbeddingProvider provider = new TeiEmbeddingProvider(config("minilm"));
    try {
      final AnalysisException e = assertThrows(AnalysisException.class,
          () -> provider.embed("other", "text"));
      assertEquals(AnalysisException.FailureType.NOT_FOUND, e.getFailureType());
    } finally {
      provider.close();
    }
  }

  @Test
  void rejectsInvalidDeadline() {
    final Map<String, String> configuration = config("minilm");
    configuration.put("model.embedder.tei.deadline_ms", "soon");
    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> new TeiEmbeddingProvider(configuration));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, e.getFailureType());
  }

  private static Map<String, String> config(String modelId) {
    final Map<String, String> configuration = new HashMap<>();
    configuration.put("model.embedder." + modelId + ".tei.target",
        "localhost:" + embeddingServer.getPort());
    configuration.put("model.embedder.tei.deadline_ms", "5000");
    return configuration;
  }

  /** Info stub reporting a fixed model type. */
  private static final class StubInfoService extends InfoGrpc.InfoImplBase {

    private final ModelType modelType;

    private StubInfoService(ModelType modelType) {
      this.modelType = modelType;
    }

    @Override
    public void info(InfoRequest request, StreamObserver<InfoResponse> observer) {
      observer.onNext(InfoResponse.newBuilder()
          .setVersion("test")
          .setModelId("stub/test-model")
          .setModelDtype("float32")
          .setModelType(modelType)
          .build());
      observer.onCompleted();
    }
  }

  /** Embed stub returning {@code [length(inputs), 1, 1]} for every request. */
  private static final class StubEmbedService extends EmbedGrpc.EmbedImplBase {

    @Override
    public void embed(EmbedRequest request, StreamObserver<EmbedResponse> observer) {
      observer.onNext(EmbedResponse.newBuilder()
          .addEmbeddings(request.getInputs().length())
          .addEmbeddings(1f)
          .addEmbeddings(1f)
          .build());
      observer.onCompleted();
    }
  }
}
