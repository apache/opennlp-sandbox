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
package org.apache.opennlp.grpc.embedding.openvino;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.embedding.EmbeddingProviderFactory;
import org.apache.opennlp.grpc.kserve.v2.GRPCInferenceServiceGrpc;
import org.apache.opennlp.grpc.kserve.v2.InferTensorContents;
import org.apache.opennlp.grpc.kserve.v2.ModelInferRequest;
import org.apache.opennlp.grpc.kserve.v2.ModelInferResponse;
import org.apache.opennlp.grpc.kserve.v2.ModelMetadataRequest;
import org.apache.opennlp.grpc.kserve.v2.ModelMetadataResponse;
import org.apache.opennlp.grpc.kserve.v2.ModelReadyRequest;
import org.apache.opennlp.grpc.kserve.v2.ModelReadyResponse;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link OpenVinoEmbeddingProvider} against an embedded gRPC server
 * implementing the KServe v2 surface. The stub returns deterministic vectors whose
 * first component is the input length, which makes ordering verifiable. Both KServe
 * output representations (typed contents and little-endian raw contents) are covered.
 */
class OpenVinoEmbeddingProviderTest {

  private static final int DIMENSION = 3;
  private static final String MODEL_NAME = "all-MiniLM-L6-v2";

  private static Server typedServer;
  private static Server rawServer;
  private static Server notReadyServer;
  private static Server tokenInputServer;
  private static StubInferenceService typedService;

  @BeforeAll
  static void startServers() throws IOException {
    typedService = new StubInferenceService(true, false, "BYTES");
    typedServer = ServerBuilder.forPort(0).addService(typedService).build().start();
    rawServer = ServerBuilder.forPort(0)
        .addService(new StubInferenceService(true, true, "BYTES")).build().start();
    notReadyServer = ServerBuilder.forPort(0)
        .addService(new StubInferenceService(false, false, "BYTES")).build().start();
    tokenInputServer = ServerBuilder.forPort(0)
        .addService(new StubInferenceService(true, false, "INT64")).build().start();
  }

  @AfterAll
  static void stopServers() throws InterruptedException {
    for (Server server : List.of(typedServer, rawServer, notReadyServer, tokenInputServer)) {
      server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void connectsAndDiscoversDimension() {
    final OpenVinoEmbeddingProvider provider =
        new OpenVinoEmbeddingProvider(config(typedServer, "minilm"));
    try {
      assertTrue(provider.isAvailable());
      assertEquals(Set.of("minilm"), provider.registeredModelIds());
      assertEquals(DIMENSION, provider.embeddingDimension("minilm"));
      assertEquals("minilm", provider.resolveModelId(null));
      assertEquals(OpenVinoEmbeddingBackendFactory.BACKEND_ID, provider.backendId());
    } finally {
      provider.close();
    }
  }

  @Test
  void embedReturnsServerVectorFromTypedContents() {
    final OpenVinoEmbeddingProvider provider =
        new OpenVinoEmbeddingProvider(config(typedServer, "minilm"));
    try {
      final float[] vector = provider.embed("minilm", "hello");
      assertEquals(DIMENSION, vector.length);
      assertEquals(5f, vector[0]);
    } finally {
      provider.close();
    }
  }

  @Test
  void embedParsesRawOutputContents() {
    final OpenVinoEmbeddingProvider provider =
        new OpenVinoEmbeddingProvider(config(rawServer, "minilm"));
    try {
      final float[] vector = provider.embed("minilm", "hello");
      assertEquals(DIMENSION, vector.length);
      assertEquals(5f, vector[0]);
    } finally {
      provider.close();
    }
  }

  @Test
  void embedBatchIsOneInferCallAndPreservesOrder() {
    final OpenVinoEmbeddingProvider provider =
        new OpenVinoEmbeddingProvider(config(typedServer, "minilm"));
    try {
      final int callsBefore = typedService.inferCalls.get();
      final List<float[]> vectors =
          provider.embedBatch("minilm", List.of("a", "bb", "ccc", "dddd"));
      assertEquals(1, typedService.inferCalls.get() - callsBefore,
          "batch must be a single ModelInfer call");
      assertEquals(4, vectors.size());
      for (int i = 0; i < vectors.size(); i++) {
        assertEquals(i + 1f, vectors.get(i)[0], "vector " + i + " out of order");
      }
    } finally {
      provider.close();
    }
  }

  @Test
  void factoryAggregatesOpenVinoThroughServiceLoader() throws Exception {
    // The factory discovers the OpenVINO backend via ServiceLoader and aggregates it into the
    // composite provider; the OpenVINO-configured model resolves to the OpenVINO engine.
    final EmbeddingProvider provider =
        EmbeddingProviderFactory.create(config(typedServer, "minilm"));
    try {
      assertTrue(provider.isAvailable());
      assertTrue(provider.supportsModel("minilm"));
      assertEquals(OpenVinoEmbeddingBackendFactory.BACKEND_ID, provider.backendId("minilm"));
    } finally {
      if (provider instanceof AutoCloseable closeable) {
        closeable.close();
      }
    }
  }

  @Test
  void isInertWhenNoTargetConfigured() {
    // No .openvino.target keys: the provider must construct cleanly and report itself unavailable
    // so the composite can aggregate it alongside other engines in a deploy that does not use it.
    final OpenVinoEmbeddingProvider provider = new OpenVinoEmbeddingProvider(Map.of());
    try {
      assertFalse(provider.isAvailable());
      assertTrue(provider.registeredModelIds().isEmpty());
    } finally {
      provider.close();
    }
  }

  @Test
  void rejectsMissingModelName() {
    final Map<String, String> configuration = new HashMap<>();
    configuration.put("model.embedder.minilm.openvino.target",
        "localhost:" + typedServer.getPort());
    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> new OpenVinoEmbeddingProvider(configuration));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, e.getFailureType());
  }

  @Test
  void rejectsModelThatIsNotReady() {
    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> new OpenVinoEmbeddingProvider(config(notReadyServer, "minilm")));
    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, e.getFailureType());
  }

  @Test
  void rejectsModelWithoutStringInput() {
    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> new OpenVinoEmbeddingProvider(config(tokenInputServer, "minilm")));
    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, e.getFailureType());
    assertTrue(e.getMessage().contains("INT64"), e.getMessage());
  }

  @Test
  void rejectsUnknownModelId() {
    final OpenVinoEmbeddingProvider provider =
        new OpenVinoEmbeddingProvider(config(typedServer, "minilm"));
    try {
      final AnalysisException e = assertThrows(AnalysisException.class,
          () -> provider.embed("other", "text"));
      assertEquals(AnalysisException.FailureType.NOT_FOUND, e.getFailureType());
    } finally {
      provider.close();
    }
  }

  private static Map<String, String> config(Server server, String modelId) {
    final Map<String, String> configuration = new HashMap<>();
    configuration.put("model.embedder." + modelId + ".openvino.target",
        "localhost:" + server.getPort());
    configuration.put("model.embedder." + modelId + ".openvino.model_name", MODEL_NAME);
    configuration.put("model.embedder.openvino.deadline_ms", "5000");
    return configuration;
  }

  /**
   * KServe v2 stub serving one model with a single string input and a single FP32
   * output of dimension 3 where {@code vector[0] == length(text)}.
   */
  private static final class StubInferenceService
      extends GRPCInferenceServiceGrpc.GRPCInferenceServiceImplBase {

    private final boolean ready;
    private final boolean rawOutput;
    private final String inputDatatype;
    private final AtomicInteger inferCalls = new AtomicInteger();

    private StubInferenceService(boolean ready, boolean rawOutput, String inputDatatype) {
      this.ready = ready;
      this.rawOutput = rawOutput;
      this.inputDatatype = inputDatatype;
    }

    @Override
    public void modelReady(ModelReadyRequest request, StreamObserver<ModelReadyResponse> observer) {
      observer.onNext(ModelReadyResponse.newBuilder().setReady(ready).build());
      observer.onCompleted();
    }

    @Override
    public void modelMetadata(ModelMetadataRequest request,
        StreamObserver<ModelMetadataResponse> observer) {
      observer.onNext(ModelMetadataResponse.newBuilder()
          .setName(MODEL_NAME)
          .addInputs(ModelMetadataResponse.TensorMetadata.newBuilder()
              .setName("texts")
              .setDatatype(inputDatatype)
              .addShape(-1))
          .addOutputs(ModelMetadataResponse.TensorMetadata.newBuilder()
              .setName("embeddings")
              .setDatatype("FP32")
              .addShape(-1)
              .addShape(DIMENSION))
          .build());
      observer.onCompleted();
    }

    @Override
    public void modelInfer(ModelInferRequest request, StreamObserver<ModelInferResponse> observer) {
      inferCalls.incrementAndGet();
      final List<ByteString> texts = request.getInputs(0).getContents().getBytesContentsList();
      final ModelInferResponse.Builder response = ModelInferResponse.newBuilder()
          .setModelName(request.getModelName());
      final ModelInferResponse.InferOutputTensor.Builder output =
          ModelInferResponse.InferOutputTensor.newBuilder()
              .setName("embeddings")
              .setDatatype("FP32")
              .addShape(texts.size())
              .addShape(DIMENSION);
      if (rawOutput) {
        final ByteBuffer buffer = ByteBuffer.allocate(texts.size() * DIMENSION * Float.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (ByteString text : texts) {
          buffer.putFloat(text.toString(StandardCharsets.UTF_8).length());
          buffer.putFloat(1f);
          buffer.putFloat(1f);
        }
        buffer.flip();
        response.addOutputs(output).addRawOutputContents(ByteString.copyFrom(buffer));
      } else {
        final InferTensorContents.Builder contents = InferTensorContents.newBuilder();
        for (ByteString text : texts) {
          contents.addFp32Contents(text.toString(StandardCharsets.UTF_8).length());
          contents.addFp32Contents(1f);
          contents.addFp32Contents(1f);
        }
        response.addOutputs(output.setContents(contents));
      }
      observer.onNext(response.build());
      observer.onCompleted();
    }
  }
}
