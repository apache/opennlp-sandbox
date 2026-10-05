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
package org.apache.opennlp.grpc.v1.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalysisSession;
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.processor.basic.BasicDocumentAnalyzer;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;
import org.apache.opennlp.grpc.v1.AnalyzeStreamDocument;
import org.apache.opennlp.grpc.v1.AnalyzeStreamRequest;
import org.apache.opennlp.grpc.v1.AnalyzeStreamResponse;
import org.apache.opennlp.grpc.v1.GrpcStatusCode;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the concurrent, options-first full document analysis stream. */
class AnalyzeStreamTest {

  private static OpenNlpAnalysisServiceImpl serviceWith(DocumentAnalyzer analyzer) {
    return new OpenNlpAnalysisServiceImpl(
        analyzer, ProfileRegistry.createDefault(), new ModelBundleCache(Map.of()), "test");
  }

  private static AnalyzeStreamRequest configuration() {
    return AnalyzeStreamRequest.newBuilder()
        .setConfiguration(AnalyzeStreamConfiguration.newBuilder().build())
        .build();
  }

  /** Returns a stream configuration with an explicit {@code clear_adaptive_data} value. */
  private static AnalyzeStreamRequest configurationWithClearAdaptiveData(boolean clear) {
    return AnalyzeStreamRequest.newBuilder()
        .setConfiguration(AnalyzeStreamConfiguration.newBuilder()
            .setOptions(AnalysisOptions.newBuilder().setClearAdaptiveData(clear)))
        .build();
  }

  /** Analyzer whose session records the worker thread and order of every analyzed document. */
  private static DocumentAnalyzer recordingAnalyzer(
      List<Thread> threads, List<String> analyzed, AtomicReference<Thread> firstWorker) {
    return new DocumentAnalyzer() {
      @Override
      public AnalyzeDocumentResponse analyze(
          org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest request) {
        throw new IllegalStateException("stream bypassed its prepared analyzer session");
      }

      @Override
      public DocumentAnalysisSession openSession(AnalyzeStreamConfiguration configuration) {
        return document -> {
          final Thread thread = Thread.currentThread();
          threads.add(thread);
          firstWorker.compareAndSet(null, thread);
          analyzed.add(document.getDocId());
          return AnalyzeDocumentResponse.newBuilder().setDocument(document).build();
        };
      }
    };
  }

  private static AnalyzeStreamRequest document(long sequence, String text) {
    return AnalyzeStreamRequest.newBuilder()
        .setDocument(AnalyzeStreamDocument.newBuilder()
            .setSequence(sequence)
            .setDocument(OpenNlpDocument.newBuilder()
                .setDocId("doc-" + sequence)
                .setRawText(text)))
        .build();
  }

  private static AnalyzeDocumentResponse echo(org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest r) {
    return AnalyzeDocumentResponse.newBuilder().setDocument(r.getDocument()).build();
  }

  @Test
  void defaultAnalyzerSessionValidatesItsPublicArguments() {
    final DocumentAnalyzer analyzer = AnalyzeStreamTest::echo;
    final IllegalArgumentException configurationError = assertThrows(
        IllegalArgumentException.class, () -> analyzer.openSession(null));
    assertEquals("configuration must not be null", configurationError.getMessage());

    final DocumentAnalysisSession session = analyzer.openSession(
        AnalyzeStreamConfiguration.getDefaultInstance());
    final IllegalArgumentException documentError = assertThrows(
        IllegalArgumentException.class, () -> session.analyze(null));
    assertEquals("document must not be null", documentError.getMessage());
  }

  @Test
  void delegatesEveryDocumentThroughTheGenericAnalyzerAndCompletesAfterHalfClose()
      throws Exception {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(AnalyzeStreamTest::echo)
        .analyzeStream(responses);

    requests.onNext(configuration());
    requests.onNext(document(7, "first"));
    requests.onNext(document(8, "second"));
    requests.onCompleted();

    assertTrue(responses.awaitTerminal());
    assertNull(responses.error);
    assertTrue(responses.completed);
    assertEquals("first", responses.bySequence(7).getOk().getDocument().getRawText());
    assertEquals("second", responses.bySequence(8).getOk().getDocument().getRawText());
  }

  @Test
  void copiesTheFixedConfigurationOntoEveryAnalyzerRequest() throws Exception {
    final BlockingQueue<org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest> analyzed =
        new LinkedBlockingQueue<>();
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(request -> {
      analyzed.add(request);
      return echo(request);
    }).analyzeStream(responses);
    final AnalysisProfile profile = AnalysisProfile.newBuilder()
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .build();
    final AnalysisOptions options = AnalysisOptions.newBuilder()
        .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
        .build();

    requests.onNext(AnalyzeStreamRequest.newBuilder()
        .setConfiguration(AnalyzeStreamConfiguration.newBuilder()
            .setProfile(profile)
            .setOptions(options)
            .setProfileId("bulk"))
        .build());
    requests.onNext(document(1, "configured"));
    requests.onCompleted();

    assertTrue(responses.awaitTerminal());
    final var request = analyzed.poll(5, TimeUnit.SECONDS);
    assertNotNull(request);
    assertEquals(profile, request.getProfile());
    assertEquals(options, request.getOptions());
    assertEquals("bulk", request.getProfileId());
  }

  @Test
  void returnsDocumentFailuresWithoutTerminatingTheStream() throws Exception {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(request -> {
      if (request.getDocument().getRawText().equals("bad")) {
        throw AnalysisException.invalidArgument("bad document");
      }
      return echo(request);
    }).analyzeStream(responses);

    requests.onNext(configuration());
    requests.onNext(document(1, "good"));
    requests.onNext(document(2, "bad"));
    requests.onNext(document(3, "also good"));
    requests.onCompleted();

    assertTrue(responses.awaitTerminal());
    assertTrue(responses.completed);
    assertNull(responses.error);
    assertTrue(responses.bySequence(1).hasOk());
    assertEquals(GrpcStatusCode.GRPC_STATUS_CODE_INVALID_ARGUMENT,
        responses.bySequence(2).getError().getCode());
    assertTrue(responses.bySequence(2).getError().getMessage().contains("bad document"));
    assertTrue(responses.bySequence(3).hasOk());
  }

  @Test
  void returnsUnexpectedDocumentFailuresWithoutLeakingTheirDetails() throws Exception {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(request -> {
      throw new IllegalStateException("secret implementation detail");
    }).analyzeStream(responses);

    requests.onNext(configuration());
    requests.onNext(document(4, "boom"));
    requests.onCompleted();

    assertTrue(responses.awaitTerminal());
    final var error = responses.bySequence(4).getError();
    assertEquals(GrpcStatusCode.GRPC_STATUS_CODE_INTERNAL, error.getCode());
    assertEquals("Internal server error", error.getMessage());
    assertFalse(error.getMessage().contains("secret"));
  }

  @Test
  void rejectsADocumentBeforeTheConfiguration() throws Exception {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(AnalyzeStreamTest::echo)
        .analyzeStream(responses);

    requests.onNext(document(1, "too early"));

    assertTrue(responses.awaitTerminal());
    assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(responses.error).getCode());
    assertFalse(responses.completed);
  }

  @Test
  void rejectsASecondConfiguration() throws Exception {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(AnalyzeStreamTest::echo)
        .analyzeStream(responses);

    requests.onNext(configuration());
    requests.onNext(configuration());

    assertTrue(responses.awaitTerminal());
    assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(responses.error).getCode());
    assertFalse(responses.completed);
  }

  @Test
  void emitsResponsesInCompletionOrderWithoutHeadOfLineBlocking() throws Exception {
    final CountDownLatch slowStarted = new CountDownLatch(1);
    final CountDownLatch releaseSlow = new CountDownLatch(1);
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(request -> {
      if (request.getDocument().getRawText().equals("slow")) {
        slowStarted.countDown();
        await(releaseSlow);
      } else {
        await(slowStarted);
      }
      return echo(request);
    }).analyzeStream(responses);

    requests.onNext(configuration());
    requests.onNext(document(1, "slow"));
    requests.onNext(document(2, "fast"));

    final AnalyzeStreamResponse first = responses.next();
    assertNotNull(first);
    assertEquals(2, first.getSequence());

    releaseSlow.countDown();
    requests.onCompleted();
    assertTrue(responses.awaitTerminal());
    assertEquals(1, responses.next().getSequence());
  }

  @Test
  void couplesInboundDemandAndOutboundWritesToTransportCapacity() throws Exception {
    final TrackingServerObserver responses = new TrackingServerObserver();
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      final StreamObserver<AnalyzeStreamRequest> requests = new AnalyzeDocumentStream(
          AnalyzeStreamTest::echo, executor, 2, responses);

      assertEquals(1, responses.requested.poll(5, TimeUnit.SECONDS));
      requests.onNext(configuration());
      assertEquals(2, responses.requested.poll(5, TimeUnit.SECONDS));

      responses.ready = false;
      requests.onNext(document(1, "wait for the reader"));
      assertNull(responses.values.poll(250, TimeUnit.MILLISECONDS));

      responses.ready = true;
      responses.onReady.run();
      assertEquals(1, responses.values.poll(5, TimeUnit.SECONDS).getSequence());
      assertEquals(1, responses.requested.poll(5, TimeUnit.SECONDS));

      requests.onCompleted();
      assertTrue(responses.terminal.await(5, TimeUnit.SECONDS));
      assertTrue(responses.completed);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void clientCancellationInterruptsActiveAnalysisAndDropsQueuedWork() throws Exception {
    final CountDownLatch activeStarted = new CountDownLatch(1);
    final CountDownLatch activeInterrupted = new CountDownLatch(1);
    final CountDownLatch queuedStarted = new CountDownLatch(1);
    final TrackingServerObserver responses = new TrackingServerObserver();
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      final DocumentAnalyzer analyzer = request -> {
        if (request.getDocument().getRawText().equals("active")) {
          activeStarted.countDown();
          try {
            new CountDownLatch(1).await();
          } catch (InterruptedException e) {
            activeInterrupted.countDown();
            Thread.currentThread().interrupt();
            throw AnalysisException.internal("active analysis interrupted", e);
          }
        } else {
          queuedStarted.countDown();
        }
        return echo(request);
      };
      final StreamObserver<AnalyzeStreamRequest> requests = new AnalyzeDocumentStream(
          analyzer, executor, 2, responses);

      assertEquals(1, responses.requested.poll(5, TimeUnit.SECONDS));
      requests.onNext(configuration());
      assertEquals(2, responses.requested.poll(5, TimeUnit.SECONDS));
      requests.onNext(document(1, "active"));
      requests.onNext(document(2, "queued"));
      assertTrue(activeStarted.await(5, TimeUnit.SECONDS));

      responses.cancelled = true;
      responses.onCancel.run();

      assertTrue(activeInterrupted.await(5, TimeUnit.SECONDS),
          "client cancellation did not interrupt the active analysis");
      assertFalse(queuedStarted.await(250, TimeUnit.MILLISECONDS),
          "client cancellation allowed queued analysis to start");
      assertTrue(responses.values.isEmpty());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void clientCancellationReleasesTasksThatNeverStarted() throws Exception {
    final QueuedExecutorService executor = new QueuedExecutorService();
    final TrackingServerObserver responses = new TrackingServerObserver();
    try {
      final AnalyzeDocumentStream requests = new AnalyzeDocumentStream(
          AnalyzeStreamTest::echo, executor, 2, responses);
      requests.onNext(configuration());
      requests.onNext(document(1, "never starts"));

      assertEquals(1, requests.trackedTaskCount());
      assertEquals(1, requests.inFlightCount());

      responses.cancelled = true;
      responses.onCancel.run();

      assertEquals(0, requests.trackedTaskCount());
      assertEquals(0, requests.inFlightCount());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void opensOneGenericAnalyzerSessionForTheStreamsFixedConfiguration() throws Exception {
    final AtomicInteger opened = new AtomicInteger();
    final AtomicReference<AnalyzeStreamConfiguration> prepared = new AtomicReference<>();
    final DocumentAnalyzer analyzer = new DocumentAnalyzer() {
      @Override
      public AnalyzeDocumentResponse analyze(
          org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest request) {
        throw new IllegalStateException("stream bypassed its prepared analyzer session");
      }

      @Override
      public DocumentAnalysisSession openSession(AnalyzeStreamConfiguration configuration) {
        opened.incrementAndGet();
        prepared.set(configuration);
        return document -> AnalyzeDocumentResponse.newBuilder().setDocument(document).build();
      }
    };
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(analyzer)
        .analyzeStream(responses);
    final AnalyzeStreamConfiguration configuration = AnalyzeStreamConfiguration.newBuilder()
        .setProfileId("prepared-profile")
        .build();

    requests.onNext(AnalyzeStreamRequest.newBuilder().setConfiguration(configuration).build());
    requests.onNext(document(1, "first"));
    requests.onNext(document(2, "second"));
    requests.onCompleted();

    assertTrue(responses.awaitTerminal());
    assertEquals(1, opened.get());
    assertEquals(configuration, prepared.get());
    assertTrue(responses.bySequence(1).hasOk());
    assertTrue(responses.bySequence(2).hasOk());
  }

  @Test
  void productionAnalyzerRejectsInvalidFixedConfigurationBeforeAnyDocument() throws Exception {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(
        new BasicDocumentAnalyzer(Map.of())).analyzeStream(responses);
    final AnalysisProfile invalid = AnalysisProfile.newBuilder()
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .setTokenizerEngine("not-an-engine")
        .build();

    requests.onNext(AnalyzeStreamRequest.newBuilder()
        .setConfiguration(AnalyzeStreamConfiguration.newBuilder().setProfile(invalid))
        .build());

    assertTrue(responses.awaitTerminal(), "invalid configuration did not terminate the stream");
    assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(responses.error).getCode());
    assertTrue(Status.fromThrowable(responses.error).getDescription().contains("tokenizer_engine"));
    assertFalse(responses.completed);
    assertTrue(responses.values.isEmpty());
  }

  @Test
  void productionAnalyzerKeepsTextLimitsAsPerDocumentErrors() throws Exception {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(
        new BasicDocumentAnalyzer(Map.of())).analyzeStream(responses);

    requests.onNext(AnalyzeStreamRequest.newBuilder()
        .setConfiguration(AnalyzeStreamConfiguration.newBuilder()
            .setOptions(AnalysisOptions.newBuilder().setMaxTextLength(4)))
        .build());
    requests.onNext(document(1, "too long"));
    requests.onNext(document(2, "fine"));
    requests.onCompleted();

    assertTrue(responses.awaitTerminal());
    assertNull(responses.error);
    assertTrue(responses.completed);
    assertEquals(GrpcStatusCode.GRPC_STATUS_CODE_INVALID_ARGUMENT,
        responses.bySequence(1).getError().getCode());
    assertTrue(responses.bySequence(2).hasOk());
  }

  @Test
  void adaptiveContinuityStreamRunsSeriallyOnOneDedicatedThreadInSubmissionOrder()
      throws Exception {
    // clear_adaptive_data=false promises cross-document adaptive NER continuity, but adaptive
    // state is per-thread in OpenNLP 3.x: only a single confined worker per stream can deliver
    // deterministic continuity. On the shared pool every document would land on an arbitrary
    // thread (a fixed pool starts one worker per task until its core size is reached).
    final ExecutorService pool = Executors.newFixedThreadPool(4);
    final List<Thread> threads = Collections.synchronizedList(new ArrayList<>());
    final List<String> analyzed = Collections.synchronizedList(new ArrayList<>());
    final AtomicReference<Thread> firstWorker = new AtomicReference<>();
    try {
      final CapturingObserver responses = new CapturingObserver();
      final StreamObserver<AnalyzeStreamRequest> requests = new AnalyzeDocumentStream(
          recordingAnalyzer(threads, analyzed, firstWorker), pool, 4, responses);

      requests.onNext(configurationWithClearAdaptiveData(false));
      for (int sequence = 1; sequence <= 4; sequence++) {
        requests.onNext(document(sequence, "text " + sequence));
      }
      requests.onCompleted();

      assertTrue(responses.awaitTerminal());
      assertNull(responses.error);
      assertTrue(responses.completed);
      assertEquals(1, threads.stream().distinct().count(),
          "an adaptive-continuity stream must confine every document to one worker thread");
      assertEquals(List.of("doc-1", "doc-2", "doc-3", "doc-4"), analyzed,
          "an adaptive-continuity stream must analyze documents in submission order");
      assertTrue(firstWorker.get().getName().startsWith("opennlp-stream-adaptive-"),
          "continuity must run on a dedicated worker, not a shared pool thread: "
              + firstWorker.get());
      for (int sequence = 1; sequence <= 4; sequence++) {
        assertEquals(sequence, responses.next().getSequence());
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void defaultStreamStillRunsDocumentsConcurrently() throws Exception {
    // Guard: the serial confinement above must apply only to streams that opt out of clearing
    // adaptive data. A default stream whose documents rendezvous mid-analysis only completes
    // when two documents run at once.
    final ExecutorService pool = Executors.newFixedThreadPool(2);
    final CountDownLatch bothStarted = new CountDownLatch(2);
    final DocumentAnalyzer analyzer = new DocumentAnalyzer() {
      @Override
      public AnalyzeDocumentResponse analyze(
          org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest request) {
        throw new IllegalStateException("stream bypassed its prepared analyzer session");
      }

      @Override
      public DocumentAnalysisSession openSession(AnalyzeStreamConfiguration configuration) {
        return document -> {
          bothStarted.countDown();
          await(bothStarted);
          return AnalyzeDocumentResponse.newBuilder().setDocument(document).build();
        };
      }
    };
    try {
      final CapturingObserver responses = new CapturingObserver();
      final StreamObserver<AnalyzeStreamRequest> requests = new AnalyzeDocumentStream(
          analyzer, pool, 2, responses);

      requests.onNext(configuration());
      requests.onNext(document(1, "first"));
      requests.onNext(document(2, "second"));
      requests.onCompleted();

      assertTrue(responses.awaitTerminal());
      assertNull(responses.error);
      assertTrue(responses.completed);
      assertTrue(responses.bySequence(1).hasOk());
      assertTrue(responses.bySequence(2).hasOk());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void dedicatedAdaptiveWorkerDiesWithItsStream() throws Exception {
    // The per-stream continuity worker holds the accumulated adaptive state on its thread; it
    // must be released when the stream terminates, or many streams pile up live workers (and
    // their retained state) for the lifetime of the pool.
    final ExecutorService pool = Executors.newFixedThreadPool(2);
    final List<Thread> workers = new ArrayList<>();
    try {
      for (int stream = 0; stream < 4; stream++) {
        final List<Thread> threads = Collections.synchronizedList(new ArrayList<>());
        final List<String> analyzed = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<Thread> firstWorker = new AtomicReference<>();
        final CapturingObserver responses = new CapturingObserver();
        final StreamObserver<AnalyzeStreamRequest> requests = new AnalyzeDocumentStream(
            recordingAnalyzer(threads, analyzed, firstWorker), pool, 2, responses);

        requests.onNext(configurationWithClearAdaptiveData(false));
        requests.onNext(document(1, "one"));
        requests.onCompleted();

        assertTrue(responses.awaitTerminal());
        assertTrue(responses.completed);
        assertNotNull(firstWorker.get());
        workers.add(firstWorker.get());
      }
      assertEquals(4, workers.stream().distinct().count(),
          "each adaptive-continuity stream must get its own confined worker");
      for (Thread worker : workers) {
        worker.join(TimeUnit.SECONDS.toMillis(2));
        assertFalse(worker.isAlive(),
            "adaptive-continuity worker outlived its stream: " + worker);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void aStuckWriteDoesNotTrapSiblingWorkersBehindTheOutputLock() throws Exception {
    // Worker A blocks inside a stuck transport write. Worker B, waiting for readiness behind
    // it, must observe a cancellation and finish: queueing on the outputLock monitor is
    // uninterruptible and would trap B (and its pool thread) for as long as A is stuck. A
    // probe task on the shared pool proves a thread actually came back.
    final ExecutorService executor = Executors.newFixedThreadPool(2);
    final TrackingServerObserver responses = new TrackingServerObserver();
    responses.onNextGate = new CountDownLatch(1);
    responses.onNextEntered = new CountDownLatch(1);
    final CountDownLatch secondAnalyzed = new CountDownLatch(1);
    final DocumentAnalyzer analyzer = new DocumentAnalyzer() {
      @Override
      public AnalyzeDocumentResponse analyze(
          org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest request) {
        throw new IllegalStateException("stream bypassed its prepared analyzer session");
      }

      @Override
      public DocumentAnalysisSession openSession(AnalyzeStreamConfiguration configuration) {
        return document -> {
          if ("doc-2".equals(document.getDocId())) {
            secondAnalyzed.countDown();
          }
          return AnalyzeDocumentResponse.newBuilder().setDocument(document).build();
        };
      }
    };
    try {
      final AnalyzeDocumentStream requests = new AnalyzeDocumentStream(
          analyzer, executor, 2, responses);
      assertEquals(1, responses.requested.poll(5, TimeUnit.SECONDS));
      requests.onNext(configuration());
      assertEquals(2, responses.requested.poll(5, TimeUnit.SECONDS));
      requests.onNext(document(1, "first"));
      assertTrue(responses.onNextEntered.await(5, TimeUnit.SECONDS),
          "the first write never reached the transport");
      responses.ready = false;
      requests.onNext(document(2, "second"));
      assertTrue(secondAnalyzed.await(5, TimeUnit.SECONDS),
          "the second document never finished analysis");

      responses.cancelled = true;
      responses.onCancel.run();

      final CountDownLatch probeRan = new CountDownLatch(1);
      executor.execute(probeRan::countDown);
      assertTrue(probeRan.await(5, TimeUnit.SECONDS),
          "no pool thread came back: a sibling worker stayed trapped behind the stuck "
              + "writer after cancellation");
      assertTrue(responses.values.isEmpty(),
          "a cancelled stream must not emit further responses");
    } finally {
      responses.onNextGate.countDown();
      executor.shutdownNow();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("test synchronization timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("test synchronization interrupted", e);
    }
  }

  /** Executor that retains submitted work without starting a worker thread. */
  private static final class QueuedExecutorService extends AbstractExecutorService {
    private final BlockingQueue<Runnable> queued = new LinkedBlockingQueue<>();
    private volatile boolean shutdown;

    @Override
    public void shutdown() {
      shutdown = true;
    }

    @Override
    public java.util.List<Runnable> shutdownNow() {
      shutdown = true;
      final java.util.List<Runnable> remaining = new java.util.ArrayList<>();
      queued.drainTo(remaining);
      return remaining;
    }

    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    @Override
    public boolean isTerminated() {
      return shutdown && queued.isEmpty();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return isTerminated();
    }

    @Override
    public void execute(Runnable command) {
      queued.add(command);
    }
  }

  /** Captures stream responses and terminal callbacks across worker threads. */
  private static final class CapturingObserver implements StreamObserver<AnalyzeStreamResponse> {
    private final BlockingQueue<AnalyzeStreamResponse> values = new LinkedBlockingQueue<>();
    private final CountDownLatch terminal = new CountDownLatch(1);
    private volatile Throwable error;
    private volatile boolean completed;

    @Override
    public void onNext(AnalyzeStreamResponse value) {
      values.add(value);
    }

    @Override
    public void onError(Throwable error) {
      this.error = error;
      terminal.countDown();
    }

    @Override
    public void onCompleted() {
      completed = true;
      terminal.countDown();
    }

    private boolean awaitTerminal() throws InterruptedException {
      return terminal.await(5, TimeUnit.SECONDS);
    }

    private AnalyzeStreamResponse next() throws InterruptedException {
      return values.poll(5, TimeUnit.SECONDS);
    }

    private AnalyzeStreamResponse bySequence(long sequence) {
      return values.stream()
          .filter(value -> value.getSequence() == sequence)
          .findFirst()
          .orElseThrow(() -> new AssertionError("Missing response for sequence " + sequence));
    }
  }

  /** Server-side observer exposing readiness and manual inbound demand to the test. */
  private static final class TrackingServerObserver
      extends ServerCallStreamObserver<AnalyzeStreamResponse> {
    private final BlockingQueue<AnalyzeStreamResponse> values = new LinkedBlockingQueue<>();
    private final BlockingQueue<Integer> requested = new LinkedBlockingQueue<>();
    private final CountDownLatch terminal = new CountDownLatch(1);
    private volatile Runnable onCancel = () -> { };
    private volatile Runnable onReady = () -> { };
    private volatile boolean ready = true;
    private volatile boolean cancelled;
    private volatile boolean completed;
    /** When non-null, {@link #onNext} blocks until this gate opens, simulating a stuck write. */
    private volatile CountDownLatch onNextGate;
    /** Counted down when a gated {@link #onNext} is entered. */
    private volatile CountDownLatch onNextEntered;

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void setOnCancelHandler(Runnable handler) {
      onCancel = handler;
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
      onReady = handler;
    }

    @Override
    public void disableAutoInboundFlowControl() {
      // The request counts below prove that manual flow control is active.
    }

    @Override
    public void request(int count) {
      requested.add(count);
    }

    @Override
    public void setMessageCompression(boolean enable) {
      // Message compression is outside this test's scope.
    }

    @Override
    public void onNext(AnalyzeStreamResponse value) {
      final CountDownLatch gate = onNextGate;
      if (gate != null) {
        final CountDownLatch entered = onNextEntered;
        if (entered != null) {
          entered.countDown();
        }
        // A stuck transport write ignores interrupts, like a blocked native socket write.
        boolean interrupted = false;
        while (true) {
          try {
            gate.await();
            break;
          } catch (InterruptedException e) {
            interrupted = true;
          }
        }
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
      values.add(value);
    }

    @Override
    public void onError(Throwable error) {
      terminal.countDown();
    }

    @Override
    public void onCompleted() {
      completed = true;
      terminal.countDown();
    }
  }
}
