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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.embedding.StubEmbeddingBackendFactory;
import org.apache.opennlp.grpc.embedding.TrackingEmbeddingBackendFactory;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.EmbeddingBackendSelector;
import org.apache.opennlp.grpc.v1.EmbeddingSelector;
import org.apache.opennlp.grpc.v1.EmbedTextRequest;
import org.apache.opennlp.grpc.v1.EmbedTextResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the EmbedText streaming boundary: vectors come back in request order with echoed
 * sequences, the stream's model is fixed by the first message, and every failure mode
 * (unknown model, mid-stream model switch, blank text, unresolvable default) terminates the
 * stream with its status instead of a per-message error payload.
 */
class EmbedTextStreamTest {

  // The stub embedding backend (registered via test META-INF/services) contributes one
  // 3-dimensional model named "mini" when activated through this configuration.
  private static OpenNlpAnalysisServiceImpl serviceWithStubModel() {
    return new OpenNlpAnalysisServiceImpl(
        req -> {
          throw new UnsupportedOperationException("not under test");
        },
        ProfileRegistry.createDefault(),
        new ModelBundleCache(Map.of("model.embedder.stub.model_id", "mini")),
        "test");
  }

  private static OpenNlpAnalysisServiceImpl serviceWithNoModels() {
    return new OpenNlpAnalysisServiceImpl(
        req -> {
          throw new UnsupportedOperationException("not under test");
        },
        ProfileRegistry.createDefault(),
        new ModelBundleCache(Map.of()),
        "test");
  }

  private static OpenNlpAnalysisServiceImpl serviceWithTwoRoutes() {
    return new OpenNlpAnalysisServiceImpl(
        req -> {
          throw new UnsupportedOperationException("not under test");
        },
        ProfileRegistry.createDefault(),
        new ModelBundleCache(Map.of(
            StubEmbeddingBackendFactory.KEY_MODEL_ID, "mini",
            TrackingEmbeddingBackendFactory.KEY_MODEL_ID, "mini",
            "model.embedder.mini.stub.priority", "100",
            "model.embedder.mini.tracking.priority", "50",
            "model.embedder.mini.stub.vector_space_id", "mini-v1",
            "model.embedder.mini.tracking.vector_space_id", "mini-v1")),
        "test");
  }

  private static EmbedTextRequest text(long sequence, String text) {
    return EmbedTextRequest.newBuilder().setSequence(sequence).setText(text).build();
  }

  private static EmbedTextRequest text(long sequence, String text, String modelId) {
    return EmbedTextRequest.newBuilder()
        .setSequence(sequence).setText(text).setModelId(modelId).build();
  }

  @Test
  void streamsVectorsInOrderWithEchoedSequences() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithStubModel().embedText(responses);

    requests.onNext(text(7, "one sentence"));
    requests.onNext(text(8, "another sentence"));
    requests.onNext(text(9, "a third"));
    requests.onCompleted();

    assertNull(responses.error);
    assertTrue(responses.completed);
    assertEquals(3, responses.values.size());
    assertEquals(7, responses.values.get(0).getSequence());
    assertEquals(8, responses.values.get(1).getSequence());
    assertEquals(9, responses.values.get(2).getSequence());
    assertEquals(3, responses.values.get(0).getVectorCount());
  }

  @Test
  void acceptsAConsistentExplicitModelIdOnLaterMessages() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithStubModel().embedText(responses);

    requests.onNext(text(1, "first", "mini"));
    requests.onNext(text(2, "second", "mini"));
    requests.onCompleted();

    assertNull(responses.error);
    assertTrue(responses.completed);
    assertEquals(2, responses.values.size());
  }

  @Test
  void pinsAStreamToOneBackendAndReportsTheActualRoute() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithTwoRoutes().embedText(responses);
    final EmbeddingSelector selector = EmbeddingSelector.newBuilder()
        .setModelId("mini")
        .setBackend(EmbeddingBackendSelector.newBuilder().setCustom("tracking"))
        .build();

    requests.onNext(EmbedTextRequest.newBuilder()
        .setSequence(1).setText("first").setEmbeddingSelector(selector).build());
    requests.onNext(EmbedTextRequest.newBuilder()
        .setSequence(2).setText("second").setEmbeddingSelector(selector).build());
    requests.onCompleted();

    assertNull(responses.error);
    assertTrue(responses.completed);
    assertEquals(2, responses.values.size());
    assertEquals(9.0f, responses.values.get(0).getVector(0));
    assertEquals("mini", responses.values.get(0).getRoute().getModelId());
    assertEquals("tracking", responses.values.get(0).getRoute().getBackendId());
    assertEquals("mini-v1", responses.values.get(0).getRoute().getVectorSpaceId());
  }

  @Test
  void rejectsABackendSwitchMidStream() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithTwoRoutes().embedText(responses);

    requests.onNext(EmbedTextRequest.newBuilder().setSequence(1).setText("first")
        .setEmbeddingSelector(EmbeddingSelector.newBuilder()
            .setModelId("mini").setBackendId("tracking"))
        .build());
    requests.onNext(EmbedTextRequest.newBuilder().setSequence(2).setText("second")
        .setEmbeddingSelector(EmbeddingSelector.newBuilder()
            .setModelId("mini").setBackendId("stub"))
        .build());

    assertNotNull(responses.error);
    assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(responses.error).getCode());
    assertEquals(1, responses.values.size());
    assertFalse(responses.completed);
  }

  @Test
  void rejectsLegacyModelIdTogetherWithASelector() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithTwoRoutes().embedText(responses);

    requests.onNext(EmbedTextRequest.newBuilder().setSequence(1).setText("first")
        .setModelId("mini")
        .setEmbeddingSelector(EmbeddingSelector.newBuilder()
            .setModelId("mini").setBackendId("tracking"))
        .build());

    assertNotNull(responses.error);
    assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(responses.error).getCode());
    assertTrue(responses.values.isEmpty());
  }

  @Test
  void rejectsAnUnknownModelId() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithStubModel().embedText(responses);

    requests.onNext(text(1, "first", "no-such-model"));

    assertNotNull(responses.error);
    assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(responses.error).getCode());
    assertFalse(responses.completed);
    assertTrue(responses.values.isEmpty());
  }

  @Test
  void rejectsAModelSwitchMidStreamAndIgnoresLaterMessages() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithStubModel().embedText(responses);

    requests.onNext(text(1, "first", "mini"));
    requests.onNext(text(2, "second", "other-model"));
    requests.onNext(text(3, "after the failure"));
    requests.onCompleted();

    assertNotNull(responses.error);
    final Status status = Status.fromThrowable(responses.error);
    assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
    assertTrue(status.getDescription().contains("one model"));
    // The first message succeeded; nothing after the failure produced output or completion.
    assertEquals(1, responses.values.size());
    assertFalse(responses.completed);
  }

  @Test
  void rejectsBlankTextNamingTheSequence() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithStubModel().embedText(responses);

    requests.onNext(text(42, "   "));

    assertNotNull(responses.error);
    final Status status = Status.fromThrowable(responses.error);
    assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
    assertTrue(status.getDescription().contains("42"));
  }

  @Test
  void rejectsTextBeyondTheOperatorLimit() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithStubModel().embedText(responses);

    requests.onNext(text(43, "é".repeat(524_289)));

    assertNotNull(responses.error);
    final Status status = Status.fromThrowable(responses.error);
    assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
    assertTrue(status.getDescription().contains("43"));
    assertTrue(status.getDescription().contains("1048576"));
    assertTrue(responses.values.isEmpty());
  }

  @Test
  void rejectsAStreamWithNoResolvableModel() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithNoModels().embedText(responses);

    requests.onNext(text(1, "first"));

    assertNotNull(responses.error);
    assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(responses.error).getCode());
  }

  @Test
  void cancellationWhileWaitingForReadinessStopsQuietly() throws Exception {
    // The writer exhausts the elastic unready-write window (1024 responses) and then blocks on
    // transport readiness. Cancelling mid-wait must stop the stream quietly: a write to the
    // cancelled call throws at the transport, and mapping that to INTERNAL misreports a dead
    // call as a server fault.
    final TrackingEmbedServerObserver responses = new TrackingEmbedServerObserver();
    responses.ready = false;
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithStubModel().embedText(responses);
    final CountDownLatch pumpDone = new CountDownLatch(1);
    final Thread pump = new Thread(() -> {
      try {
        for (int sequence = 0; sequence < 1100; sequence++) {
          requests.onNext(text(sequence, "text " + sequence));
        }
      } finally {
        pumpDone.countDown();
      }
    }, "embed-pump");
    pump.start();
    try {
      awaitParked(pump);
      responses.cancelled = true;

      assertTrue(pumpDone.await(5, TimeUnit.SECONDS),
          "the writer kept working after cancellation");
      assertNull(responses.error,
          "a cancelled call must not see a failure status: " + responses.error);
      assertEquals(1024, responses.values.size(),
          "no response may be written after cancellation");
      assertFalse(responses.completed);
    } finally {
      pump.interrupt();
    }
  }

  /** Waits until the writer thread parks on the readiness gate (its only blocking point). */
  private static void awaitParked(Thread writer) {
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (writer.getState() != Thread.State.TIMED_WAITING) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("the writer never parked on the readiness gate");
      }
      java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
    }
  }

  /** Server-side observer with controllable readiness and cancellation. */
  private static final class TrackingEmbedServerObserver
      extends ServerCallStreamObserver<EmbedTextResponse> {
    private final List<EmbedTextResponse> values =
        Collections.synchronizedList(new ArrayList<>());
    private volatile boolean ready = true;
    private volatile boolean cancelled;
    private volatile Throwable error;
    private volatile boolean completed;

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void setOnCancelHandler(Runnable handler) {
      // The embed stream observes cancellation through isCancelled, not a handler.
    }

    @Override
    public void setCompression(String compression) {
      // Compression selection is outside this test's scope.
    }

    @Override
    public boolean isReady() {
      return ready;
    }

    @Override
    public void setOnReadyHandler(Runnable handler) {
      // The test flips readiness directly; the readiness wait polls at most 1s.
    }

    @Override
    public void disableAutoInboundFlowControl() {
      // Automatic inbound flow control stays on for this stream.
    }

    @Override
    public void request(int count) {
      // Inbound demand is outside this test's scope.
    }

    @Override
    public void setMessageCompression(boolean enable) {
      // Message compression is outside this test's scope.
    }

    @Override
    public void onNext(EmbedTextResponse value) {
      if (cancelled) {
        // The real transport rejects writes to a cancelled call.
        throw Status.CANCELLED.withDescription("call is cancelled").asRuntimeException();
      }
      values.add(value);
    }

    @Override
    public void onError(Throwable error) {
      this.error = error;
    }

    @Override
    public void onCompleted() {
      completed = true;
    }
  }

  /** Captures everything the service emits on the response stream. */
  private static final class CapturingObserver implements StreamObserver<EmbedTextResponse> {
    private final List<EmbedTextResponse> values = new ArrayList<>();
    private Throwable error;
    private boolean completed;

    @Override
    public void onNext(EmbedTextResponse value) {
      values.add(value);
    }

    @Override
    public void onError(Throwable error) {
      this.error = error;
    }

    @Override
    public void onCompleted() {
      this.completed = true;
    }
  }
}
