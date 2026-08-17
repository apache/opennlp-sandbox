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
package org.apache.opennlp.grpc.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.embedding.BlockingEmbeddingBackendFactory;
import org.apache.opennlp.grpc.model.ClassicDocCategorizerBackendFactory;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.testing.TinyDoccatModel;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;
import org.apache.opennlp.grpc.v1.AnalyzeStreamDocument;
import org.apache.opennlp.grpc.v1.AnalyzeStreamRequest;
import org.apache.opennlp.grpc.v1.AnalyzeStreamResponse;
import org.apache.opennlp.grpc.v1.GrpcStatusCode;
import org.apache.opennlp.grpc.v1.EmbedTextRequest;
import org.apache.opennlp.grpc.v1.EmbedTextResponse;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.ListSearchIndexesRequest;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.OpenNlpSearchServiceGrpc;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenNlpGrpcServerIT {

  private static final String SENTENCE_MODEL_PREFIX = "opennlp-en-ud-ewt-sentence-";
  private static final String TOKENIZER_MODEL_PREFIX = "opennlp-en-ud-ewt-tokens-";

  @TempDir
  static Path modelDir;

  private static OpenNlpGrpcServer server;
  private static ManagedChannel channel;

  @BeforeAll
  static void init() throws Exception {
    server = new OpenNlpGrpcServer();
    server.port = 0;
    server.config = writeIntegrationConfig().toString();
    server.start();

    channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
        .usePlaintext()
        .build();
  }

  @AfterAll
  static void tearDown() {
    if (server != null) {
      server.stop();
    }
    if (channel != null) {
      channel.shutdown();
    }
  }

  /**
   * Writes a server config with explicit model paths. Integration tests must not rely on
   * {@code DefaultClassPathModelProvider} classpath scanning, which is environment-dependent
   * across operating systems and Maven classloader layouts.
   */
  private static Path writeIntegrationConfig() throws IOException, URISyntaxException {
    return writeIntegrationConfig(new Properties());
  }

  private static Path writeIntegrationConfig(Properties overrides)
      throws IOException, URISyntaxException {
    final Path modelsDir = Paths.get(
        Objects.requireNonNull(OpenNlpGrpcServerIT.class.getResource("/models/")).toURI());
    final Path sentenceModel = requireModelFile(modelsDir, SENTENCE_MODEL_PREFIX);
    final Path tokenizerModel = requireModelFile(modelsDir, TOKENIZER_MODEL_PREFIX);

    final Properties properties = new Properties();
    properties.setProperty("server.enable_reflection", "false");
    properties.setProperty("server.max_inbound_message_size", "64");
    properties.setProperty("server.max_text_bytes", "128");
    properties.setProperty("server.analysis_stream_workers", "2");
    properties.setProperty("model.sentence_detector.path", sentenceModel.toAbsolutePath().toString());
    properties.setProperty("model.tokenizer.path", tokenizerModel.toAbsolutePath().toString());
    final Path doccatModel = TinyDoccatModel.trainTopicModel(modelDir.resolve("topic.bin"));
    properties.setProperty(
        ClassicDocCategorizerBackendFactory.KEY_PREFIX + "topic"
            + ClassicDocCategorizerBackendFactory.KEY_SUFFIX,
        doccatModel.toAbsolutePath().toString());
    properties.putAll(overrides);

    final Path config = Files.createTempFile("opennlp-grpc-it-", ".ini");
    config.toFile().deleteOnExit();
    // The server parses the config with Properties.load, so the file must be written with
    // Properties.store: it escapes backslashes, which would otherwise corrupt Windows paths.
    try (OutputStream out = Files.newOutputStream(config)) {
      properties.store(out, null);
    }
    return config;
  }

  private static Path requireModelFile(Path modelsDir, String prefix) throws IOException {
    try (Stream<Path> files = Files.list(modelsDir)) {
      return files.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().startsWith(prefix))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(
              "Expected a model file with prefix '" + prefix + "' under " + modelsDir));
    }
  }

  @Test
  void analyzeDocumentOverGrpc() {
    assertFalse(channel.isTerminated());

    final String text =
        "The driver got badly injured by the accident. He was taken to the hospital!";

    final OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub v1 =
        OpenNlpAnalysisServiceGrpc.newBlockingStub(channel);

    final var serviceInfo = v1.getServiceInfo(GetServiceInfoRequest.getDefaultInstance());
    assertEquals("v1", serviceInfo.getApiVersion());
    assertEquals(128, serviceInfo.getMaxTextBytes());
    assertTrue(serviceInfo.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT));
    assertTrue(serviceInfo.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_TOKENIZE));
    assertTrue(serviceInfo.getAvailableProfileIdsList().contains(ProfileRegistry.DOCCAT_PROFILE_ID));

    final var response = v1.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder()
            .setDocId("it-doc-1")
            .setRawText(text)
            .build())
        .build());

    assertEquals("it-doc-1", response.getDocument().getDocId());
    assertEquals(2, response.getDocument().getSentencesCount());
    assertFalse(response.getDocument().getSentences(0).getTokensList().isEmpty());
    assertTrue(response.getDiagnosticsList().stream()
        .anyMatch(d -> d.getStep() == PipelineStep.PIPELINE_STEP_SENTENCE_DETECT));
  }

  @Test
  void acceptsTextWithinAdvertisedLimitWhenConfiguredTransportLimitIsSmaller() {
    final String text = "word ".repeat(20).trim();
    final var response = OpenNlpAnalysisServiceGrpc.newBlockingStub(channel)
        .analyzeDocument(AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder()
                .setDocId("transport-floor")
                .setRawText(text))
            .build());

    assertEquals(text, response.getDocument().getRawText());
  }

  @Test
  void enforcesTheOperatorTextLimitAcrossUnaryAndStreamingAnalysis() throws Exception {
    final OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub blocking =
        OpenNlpAnalysisServiceGrpc.newBlockingStub(channel);
    final StatusRuntimeException unaryError = assertThrows(StatusRuntimeException.class,
        () -> blocking.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText("x".repeat(129)))
            .build()));
    assertEquals(Status.Code.INVALID_ARGUMENT, unaryError.getStatus().getCode());

    final var responses = new CopyOnWriteArrayList<AnalyzeStreamResponse>();
    final AtomicReference<Throwable> error = new AtomicReference<>();
    final CountDownLatch done = new CountDownLatch(1);
    final StreamObserver<AnalyzeStreamRequest> requests =
        OpenNlpAnalysisServiceGrpc.newStub(channel).analyzeStream(new StreamObserver<>() {
          @Override
          public void onNext(AnalyzeStreamResponse response) {
            responses.add(response);
          }

          @Override
          public void onError(Throwable failure) {
            error.set(failure);
            done.countDown();
          }

          @Override
          public void onCompleted() {
            done.countDown();
          }
        });

    requests.onNext(AnalyzeStreamRequest.newBuilder()
        .setConfiguration(AnalyzeStreamConfiguration.getDefaultInstance())
        .build());
    requests.onNext(streamDocument(21, "x".repeat(129)));
    requests.onNext(streamDocument(22, "within limit"));
    requests.onCompleted();

    assertTrue(done.await(10, TimeUnit.SECONDS));
    assertNull(error.get());
    assertEquals(2, responses.size());
    assertEquals(GrpcStatusCode.GRPC_STATUS_CODE_INVALID_ARGUMENT, responses.stream()
        .filter(response -> response.getSequence() == 21)
        .findFirst().orElseThrow().getError().getCode());
    assertTrue(responses.stream()
        .filter(response -> response.getSequence() == 22)
        .findFirst().orElseThrow().hasOk());
  }

  @Test
  void reportsTheAnalysisServiceAsServingThroughStandardGrpcHealth() {
    final HealthCheckResponse response = HealthGrpc.newBlockingStub(channel)
        .check(HealthCheckRequest.newBuilder()
            .setService("org.apache.opennlp.grpc.v1.OpenNlpAnalysisService")
            .build());
    final HealthCheckResponse wholeServer = HealthGrpc.newBlockingStub(channel)
        .check(HealthCheckRequest.newBuilder().setService("").build());

    assertEquals(HealthCheckResponse.ServingStatus.SERVING, response.getStatus());
    assertEquals(HealthCheckResponse.ServingStatus.SERVING, wholeServer.getStatus());
  }

  @Test
  void exposesAnEmptySearchCatalogAndAdvertisesItAsServingWhenSearchIsDisabled() {
    final var catalog = OpenNlpSearchServiceGrpc.newBlockingStub(channel)
        .listSearchIndexes(ListSearchIndexesRequest.getDefaultInstance());
    final HealthCheckResponse health = HealthGrpc.newBlockingStub(channel)
        .check(HealthCheckRequest.newBuilder()
            .setService(OpenNlpSearchServiceGrpc.SERVICE_NAME)
            .build());

    assertEquals(0, catalog.getIndexesCount());
    assertEquals(HealthCheckResponse.ServingStatus.SERVING, health.getStatus());
  }

  @Test
  void reflectionIsDisabledByDefaultAndEnumeratesServicesWhenEnabled() throws Exception {
    final ReflectionResult disabled = listServices(channel);
    assertEquals(Status.Code.UNIMPLEMENTED, Status.fromThrowable(disabled.error()).getCode());

    final Properties overrides = new Properties();
    overrides.setProperty("server.enable_reflection", "true");
    final OpenNlpGrpcServer reflectionServer = new OpenNlpGrpcServer();
    reflectionServer.port = 0;
    reflectionServer.config = writeIntegrationConfig(overrides).toString();
    ManagedChannel reflectionChannel = null;
    try {
      reflectionServer.start();
      reflectionChannel = ManagedChannelBuilder
          .forAddress("localhost", reflectionServer.getPort())
          .usePlaintext()
          .build();

      final ReflectionResult enabled = listServices(reflectionChannel);
      assertNull(enabled.error());
      assertTrue(enabled.response().getListServicesResponse().getServiceList().stream()
          .anyMatch(service -> OpenNlpAnalysisServiceGrpc.SERVICE_NAME.equals(service.getName())));
      assertTrue(enabled.response().getListServicesResponse().getServiceList().stream()
          .anyMatch(service -> OpenNlpSearchServiceGrpc.SERVICE_NAME.equals(service.getName())));
    } finally {
      reflectionServer.stop();
      if (reflectionChannel != null) {
        reflectionChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void failedPublicStartClosesAlreadyConstructedModelResources() throws Exception {
    BlockingEmbeddingBackendFactory.reset();
    final Properties overrides = new Properties();
    overrides.setProperty(BlockingEmbeddingBackendFactory.KEY_MODEL_ID, "cleanup");
    overrides.setProperty("search.indexes", "broken");
    overrides.setProperty("search.index.broken.provider", "missing-provider");
    overrides.setProperty("search.index.broken.directory", modelDir.toString());
    overrides.setProperty("search.index.broken.passages", modelDir.resolve("missing.jsonl").toString());
    final OpenNlpGrpcServer failedServer = new OpenNlpGrpcServer();
    failedServer.port = 0;
    failedServer.config = writeIntegrationConfig(overrides).toString();

    assertThrows(IllegalArgumentException.class, failedServer::start);

    assertTrue(BlockingEmbeddingBackendFactory.wasClosed());
  }

  @Test
  void stopDrainsAnAcceptedRpcBeforeClosingItsProvider() throws Exception {
    BlockingEmbeddingBackendFactory.reset();
    final Properties overrides = new Properties();
    overrides.setProperty(BlockingEmbeddingBackendFactory.KEY_MODEL_ID, "slow");
    overrides.setProperty("server.shutdown_grace_seconds", "3");
    final OpenNlpGrpcServer drainingServer = new OpenNlpGrpcServer();
    drainingServer.port = 0;
    drainingServer.config = writeIntegrationConfig(overrides).toString();
    final ExecutorService stopExecutor = Executors.newSingleThreadExecutor();
    ManagedChannel drainingChannel = null;
    try {
      drainingServer.start();
      drainingChannel = ManagedChannelBuilder
          .forAddress("localhost", drainingServer.getPort())
          .usePlaintext()
          .build();
      final CountDownLatch terminal = new CountDownLatch(1);
      final AtomicReference<EmbedTextResponse> response = new AtomicReference<>();
      final AtomicReference<Throwable> error = new AtomicReference<>();
      final StreamObserver<EmbedTextRequest> requests =
          OpenNlpAnalysisServiceGrpc.newStub(drainingChannel)
              .embedText(new StreamObserver<>() {
                @Override
                public void onNext(EmbedTextResponse value) {
                  response.set(value);
                }

                @Override
                public void onError(Throwable failure) {
                  error.set(failure);
                  terminal.countDown();
                }

                @Override
                public void onCompleted() {
                  terminal.countDown();
                }
              });

      requests.onNext(EmbedTextRequest.newBuilder()
          .setSequence(17)
          .setModelId("slow")
          .setText("accepted before shutdown")
          .build());
      requests.onCompleted();
      assertTrue(BlockingEmbeddingBackendFactory.awaitStarted(5, TimeUnit.SECONDS));

      final var stopping = stopExecutor.submit(drainingServer::stop);
      assertThrows(TimeoutException.class, () -> stopping.get(200, TimeUnit.MILLISECONDS));
      assertFalse(BlockingEmbeddingBackendFactory.wasClosed());

      BlockingEmbeddingBackendFactory.release();
      assertTrue(terminal.await(5, TimeUnit.SECONDS));
      stopping.get(5, TimeUnit.SECONDS);
      assertNull(error.get());
      assertEquals(17, response.get().getSequence());
      assertTrue(BlockingEmbeddingBackendFactory.wasClosed());
    } finally {
      BlockingEmbeddingBackendFactory.release();
      drainingServer.stop();
      if (drainingChannel != null) {
        drainingChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      }
      stopExecutor.shutdownNow();
    }
  }

  @Test
  void stopForcesAnExpiredGracePeriodBeforeClosingItsProvider() throws Exception {
    BlockingEmbeddingBackendFactory.reset();
    final Properties overrides = new Properties();
    overrides.setProperty(BlockingEmbeddingBackendFactory.KEY_MODEL_ID, "forced");
    overrides.setProperty("server.shutdown_grace_seconds", "0");
    final OpenNlpGrpcServer forcedServer = new OpenNlpGrpcServer();
    forcedServer.port = 0;
    forcedServer.config = writeIntegrationConfig(overrides).toString();
    final ExecutorService stopExecutor = Executors.newSingleThreadExecutor();
    ManagedChannel forcedChannel = null;
    try {
      forcedServer.start();
      forcedChannel = ManagedChannelBuilder
          .forAddress("localhost", forcedServer.getPort())
          .usePlaintext()
          .build();
      final StreamObserver<EmbedTextRequest> requests =
          OpenNlpAnalysisServiceGrpc.newStub(forcedChannel)
              .embedText(new StreamObserver<>() {
                @Override
                public void onNext(EmbedTextResponse value) {
                }

                @Override
                public void onError(Throwable failure) {
                }

                @Override
                public void onCompleted() {
                }
              });

      requests.onNext(EmbedTextRequest.newBuilder()
          .setSequence(23)
          .setModelId("forced")
          .setText("held beyond grace")
          .build());
      requests.onCompleted();
      assertTrue(BlockingEmbeddingBackendFactory.awaitStarted(5, TimeUnit.SECONDS));

      final var stopping = stopExecutor.submit(forcedServer::stop);
      stopping.get(2, TimeUnit.SECONDS);
      assertTrue(BlockingEmbeddingBackendFactory.wasClosed());
    } finally {
      BlockingEmbeddingBackendFactory.release();
      forcedServer.stop();
      if (forcedChannel != null) {
        forcedChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      }
      stopExecutor.shutdownNow();
    }
  }

  @Test
  void analyzesMultipleDocumentsOverOneGrpcStream() throws Exception {
    final var responses = new CopyOnWriteArrayList<AnalyzeStreamResponse>();
    final AtomicReference<Throwable> error = new AtomicReference<>();
    final CountDownLatch done = new CountDownLatch(1);
    final StreamObserver<AnalyzeStreamRequest> requests =
        OpenNlpAnalysisServiceGrpc.newStub(channel).analyzeStream(new StreamObserver<>() {
          @Override
          public void onNext(AnalyzeStreamResponse response) {
            responses.add(response);
          }

          @Override
          public void onError(Throwable failure) {
            error.set(failure);
            done.countDown();
          }

          @Override
          public void onCompleted() {
            done.countDown();
          }
        });

    requests.onNext(AnalyzeStreamRequest.newBuilder()
        .setConfiguration(AnalyzeStreamConfiguration.getDefaultInstance())
        .build());
    requests.onNext(streamDocument(11, "A first sentence."));
    requests.onNext(streamDocument(12, "Another sentence follows."));
    requests.onCompleted();

    assertTrue(done.await(10, TimeUnit.SECONDS));
    assertNull(error.get());
    assertEquals(2, responses.size());
    assertTrue(responses.stream().allMatch(AnalyzeStreamResponse::hasOk));
    assertTrue(responses.stream().anyMatch(response -> response.getSequence() == 11));
    assertTrue(responses.stream().anyMatch(response -> response.getSequence() == 12));
    assertTrue(responses.stream().allMatch(
        response -> response.getOk().getDocument().getSentencesCount() == 1));
  }

  private static AnalyzeStreamRequest streamDocument(long sequence, String text) {
    return AnalyzeStreamRequest.newBuilder()
        .setDocument(AnalyzeStreamDocument.newBuilder()
            .setSequence(sequence)
            .setDocument(OpenNlpDocument.newBuilder()
                .setDocId("stream-" + sequence)
                .setRawText(text)))
        .build();
  }

  private static ReflectionResult listServices(ManagedChannel targetChannel)
      throws InterruptedException {
    final CountDownLatch terminal = new CountDownLatch(1);
    final AtomicReference<ServerReflectionResponse> response = new AtomicReference<>();
    final AtomicReference<Throwable> error = new AtomicReference<>();
    final StreamObserver<ServerReflectionRequest> requests = ServerReflectionGrpc
        .newStub(targetChannel)
        .serverReflectionInfo(new StreamObserver<>() {
          @Override
          public void onNext(ServerReflectionResponse value) {
            response.set(value);
          }

          @Override
          public void onError(Throwable failure) {
            error.set(failure);
            terminal.countDown();
          }

          @Override
          public void onCompleted() {
            terminal.countDown();
          }
        });
    requests.onNext(ServerReflectionRequest.newBuilder().setListServices("").build());
    requests.onCompleted();
    assertTrue(terminal.await(5, TimeUnit.SECONDS), "reflection call did not terminate");
    return new ReflectionResult(response.get(), error.get());
  }

  private record ReflectionResult(ServerReflectionResponse response, Throwable error) {
  }
}
