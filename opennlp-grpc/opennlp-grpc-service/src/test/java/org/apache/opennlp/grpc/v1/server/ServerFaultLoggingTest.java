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
package org.apache.opennlp.grpc.v1.server;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.opennlp.grpc.embedding.StubEmbeddingBackendFactory;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;
import org.apache.opennlp.grpc.v1.AnalyzeStreamDocument;
import org.apache.opennlp.grpc.v1.AnalyzeStreamRequest;
import org.apache.opennlp.grpc.v1.AnalyzeStreamResponse;
import org.apache.opennlp.grpc.v1.EmbedTextRequest;
import org.apache.opennlp.grpc.v1.EmbedTextResponse;
import org.apache.opennlp.grpc.v1.GrpcStatusCode;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the server-side diagnostics contract for step failures: an {@link AnalysisException}
 * that maps to INTERNAL (or another code the client did not cause) reports only the status to
 * the client, so the detail must be logged server-side, or a model throwing on pathological
 * input leaves no trace anywhere.
 */
class ServerFaultLoggingTest {

  private static OpenNlpAnalysisServiceImpl serviceWith(DocumentAnalyzer analyzer) {
    return new OpenNlpAnalysisServiceImpl(
        analyzer, ProfileRegistry.createDefault(), new ModelBundleCache(Map.of()), "test");
  }

  private static AnalysisException internalFailure() {
    return AnalysisException.internal(
        "PIPELINE_STEP_NER failed", new IllegalStateException("pathological input"));
  }

  @Test
  void analyzeDocumentLogsInternalStepFailures() {
    final CapturingObserver<AnalyzeDocumentResponse> observer = new CapturingObserver<>();

    try (CapturedLogs logs = CapturedLogs.on(OpenNlpAnalysisServiceImpl.class.getName())) {
      serviceWith(request -> {
        throw internalFailure();
      }).analyzeDocument(AnalyzeDocumentRequest.newBuilder()
          .setDocument(OpenNlpDocument.newBuilder().setRawText("hello").build())
          .build(), observer);

      assertEquals(Status.Code.INTERNAL, Status.fromThrowable(observer.error).getCode());
      assertFalse(logs.errorEvents().isEmpty(),
          "an INTERNAL-mapped step failure was reported to the client without any "
              + "server-side log entry");
    }
  }

  @Test
  void embedTextLogsInternalStepFailures() {
    final OpenNlpAnalysisServiceImpl service = new OpenNlpAnalysisServiceImpl(
        request -> {
          throw new UnsupportedOperationException("not under test");
        },
        ProfileRegistry.createDefault(),
        new ModelBundleCache(Map.of(
            StubEmbeddingBackendFactory.KEY_MODEL_ID, "mini",
            StubEmbeddingBackendFactory.KEY_FAIL_TEXT, "kaboom")),
        "test");
    final CapturingObserver<EmbedTextResponse> observer = new CapturingObserver<>();
    final StreamObserver<EmbedTextRequest> requests = service.embedText(observer);

    try (CapturedLogs logs = CapturedLogs.on(OpenNlpAnalysisServiceImpl.class.getName())) {
      requests.onNext(EmbedTextRequest.newBuilder()
          .setSequence(1).setModelId("mini").setText("kaboom").build());

      assertNotNull(observer.error);
      assertEquals(Status.Code.INTERNAL, Status.fromThrowable(observer.error).getCode());
      assertFalse(logs.errorEvents().isEmpty(),
          "an INTERNAL-mapped embedding failure was reported to the client without any "
              + "server-side log entry");
    }
  }

  @Test
  void analyzeStreamLogsInternalStepFailures() {
    final CapturingObserver<AnalyzeStreamResponse> observer = new CapturingObserver<>();
    final StreamObserver<AnalyzeStreamRequest> requests = new AnalyzeDocumentStream(
        request -> {
          throw internalFailure();
        }, Runnable::run, 2, observer);

    try (CapturedLogs logs = CapturedLogs.on(AnalyzeDocumentStream.class.getName())) {
      requests.onNext(AnalyzeStreamRequest.newBuilder()
          .setConfiguration(AnalyzeStreamConfiguration.newBuilder().build())
          .build());
      requests.onNext(AnalyzeStreamRequest.newBuilder()
          .setDocument(AnalyzeStreamDocument.newBuilder()
              .setSequence(1)
              .setDocument(OpenNlpDocument.newBuilder().setRawText("hello").build())
              .build())
          .build());

      final AnalyzeStreamResponse response = observer.value;
      assertNotNull(response);
      assertTrue(response.hasError());
      assertEquals(GrpcStatusCode.GRPC_STATUS_CODE_INTERNAL, response.getError().getCode());
      assertFalse(logs.errorEvents().isEmpty(),
          "an INTERNAL-mapped step failure was reported to the client without any "
              + "server-side log entry");
    }
  }

  /** Captures the terminal callbacks a service makes on a response stream. */
  private static final class CapturingObserver<T> implements StreamObserver<T> {
    private T value;
    private Throwable error;

    @Override
    public void onNext(T value) {
      this.value = value;
    }

    @Override
    public void onError(Throwable error) {
      this.error = error;
    }

    @Override
    public void onCompleted() {
    }
  }

  /** An attached log4j2 appender plus the detach action. */
  private record CapturedLogs(CapturingAppender appender, Runnable detach)
      implements AutoCloseable {

    static CapturedLogs on(String loggerName) {
      final LoggerContext context = LoggerContext.getContext(false);
      final Configuration configuration = context.getConfiguration();
      final CapturingAppender appender = new CapturingAppender(loggerName);
      appender.start();
      final LoggerConfig loggerConfig = configuration.getLoggerConfig(loggerName);
      loggerConfig.addAppender(appender, Level.ERROR, null);
      context.updateLoggers();
      return new CapturedLogs(appender, () -> {
        loggerConfig.removeAppender(appender.getName());
        context.updateLoggers();
        appender.stop();
      });
    }

    List<LogEvent> errorEvents() {
      return appender.events.stream()
          .filter(event -> event.getLoggerName().equals(appender.loggerName))
          .filter(event -> event.getLevel() == Level.ERROR)
          .toList();
    }

    @Override
    public void close() {
      detach.run();
    }
  }

  /** Minimal appender recording the events of one logger. */
  private static final class CapturingAppender extends AbstractAppender {

    private final String loggerName;
    private final List<LogEvent> events = new CopyOnWriteArrayList<>();

    CapturingAppender(String loggerName) {
      super("capture-" + loggerName, null, null, true, Property.EMPTY_ARRAY);
      this.loggerName = loggerName;
    }

    @Override
    public void append(LogEvent event) {
      events.add(event.toImmutable());
    }
  }
}
