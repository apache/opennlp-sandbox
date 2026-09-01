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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalysisSession;
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;
import org.apache.opennlp.grpc.v1.AnalyzeStreamDocument;
import org.apache.opennlp.grpc.v1.AnalyzeStreamError;
import org.apache.opennlp.grpc.v1.AnalyzeStreamRequest;
import org.apache.opennlp.grpc.v1.AnalyzeStreamResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One full-document analysis stream. Configuration is fixed by the first frame,
 * while documents execute concurrently through the transport-neutral
 * {@link DocumentAnalyzer}. Results are serialized onto the response observer in
 * completion order.
 *
 * <p>A stream that opts out of clearing adaptive NER state
 * ({@code clear_adaptive_data=false}) is the exception to concurrent execution: that
 * state is per-thread in OpenNLP 3.x, so the stream's documents run serially on one
 * dedicated worker, giving deterministic cross-document continuity, confining the state
 * away from shared pool threads, and letting it die with the stream.</p>
 */
final class AnalyzeDocumentStream implements StreamObserver<AnalyzeStreamRequest> {

  private static final Logger logger = LoggerFactory.getLogger(AnalyzeDocumentStream.class);
  private static final long READY_TIMEOUT_MILLIS = 30_000;

  private final DocumentAnalyzer documentAnalyzer;
  private final Executor executor;
  private final StreamObserver<AnalyzeStreamResponse> responseObserver;
  private final ServerCallStreamObserver<AnalyzeStreamResponse> serverCallObserver;
  private final int streamWindow;
  private final Object outputLock = new Object();
  private final Object readyLock = new Object();
  private final AtomicInteger inFlight = new AtomicInteger();
  private final AtomicBoolean terminated = new AtomicBoolean();
  private final Set<FutureTask<Void>> tasks = ConcurrentHashMap.newKeySet();

  private volatile AnalyzeStreamConfiguration configuration;
  private volatile DocumentAnalysisSession analysisSession;
  private volatile boolean clientCompleted;
  /** Dedicated serial worker for adaptive-continuity streams; {@code null} otherwise. */
  private volatile ExecutorService adaptiveWorker;

  /**
   * Creates a stream and, when a real server transport is present, grants the one
   * inbound frame needed to receive its configuration.
   *
   * @param documentAnalyzer Analyzer used for every document.
   * @param executor Executor on which document analyses run.
   * @param streamWindow Maximum documents this stream admits concurrently.
   * @param responseObserver Observer receiving completion-ordered results.
   */
  AnalyzeDocumentStream(
      DocumentAnalyzer documentAnalyzer,
      Executor executor,
      int streamWindow,
      StreamObserver<AnalyzeStreamResponse> responseObserver) {
    if (documentAnalyzer == null) {
      throw new IllegalArgumentException("documentAnalyzer must not be null");
    }
    this.documentAnalyzer = documentAnalyzer;
    if (executor == null) {
      throw new IllegalArgumentException("executor must not be null");
    }
    this.executor = executor;
    if (streamWindow < 1) {
      throw new IllegalArgumentException("streamWindow must be positive");
    }
    this.streamWindow = streamWindow;
    if (responseObserver == null) {
      throw new IllegalArgumentException("responseObserver must not be null");
    }
    this.responseObserver = responseObserver;
    if (responseObserver instanceof ServerCallStreamObserver<AnalyzeStreamResponse> observer) {
      serverCallObserver = observer;
      observer.disableAutoInboundFlowControl();
      observer.setOnCancelHandler(this::cancel);
      observer.setOnReadyHandler(this::signalReady);
      observer.request(1);
    } else {
      serverCallObserver = null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void onNext(AnalyzeStreamRequest request) {
    if (terminated.get()) {
      return;
    }
    switch (request.getMessageCase()) {
      case CONFIGURATION -> acceptConfiguration(request.getConfiguration());
      case DOCUMENT -> acceptDocument(request.getDocument());
      case MESSAGE_NOT_SET -> failProtocol("message carries neither configuration nor document");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void onError(Throwable error) {
    terminateWorkers();
    logger.debug("AnalyzeStream closed by client or transport", error);
  }

  /** {@inheritDoc} */
  @Override
  public void onCompleted() {
    clientCompleted = true;
    maybeComplete();
  }

  /** Fixes and validates the reusable analysis session from the first stream frame. */
  private void acceptConfiguration(AnalyzeStreamConfiguration requestedConfiguration) {
    if (configuration != null) {
      failProtocol("configuration may only be the first message of the stream");
      return;
    }
    configuration = requestedConfiguration;
    try {
      analysisSession = documentAnalyzer.openSession(requestedConfiguration);
      if (analysisSession == null) {
        throw new IllegalStateException("DocumentAnalyzer.openSession returned null");
      }
    } catch (AnalysisException e) {
      failStream(GrpcStatusMapper.toStatus(e).withDescription(e.getMessage()));
      return;
    } catch (RuntimeException e) {
      logger.error("Unexpected error preparing AnalyzeStream configuration", e);
      failStream(Status.INTERNAL.withDescription("Internal server error"));
      return;
    }
    if (keepsAdaptiveData(requestedConfiguration)) {
      adaptiveWorker = Executors.newSingleThreadExecutor(
          Thread.ofVirtual().name("opennlp-stream-adaptive-", 0).factory());
    }
    request(streamWindow);
  }

  /** Returns whether the stream opts out of clearing adaptive NER data between documents. */
  private static boolean keepsAdaptiveData(AnalyzeStreamConfiguration configuration) {
    return configuration.hasOptions()
        && configuration.getOptions().hasClearAdaptiveData()
        && !configuration.getOptions().getClearAdaptiveData();
  }

  /** Admits one document to the bounded worker set or reports capacity exhaustion. */
  private void acceptDocument(AnalyzeStreamDocument document) {
    if (configuration == null) {
      failProtocol("the first message of the stream must carry configuration");
      return;
    }
    inFlight.incrementAndGet();
    final FutureTask<Void> task = new FutureTask<>(() -> {
      analyze(document);
      return null;
    }) {
      /** Releases stream accounting whether the task ran or was cancelled while queued. */
      @Override
      protected void done() {
        completeTask(this);
      }
    };
    tasks.add(task);
    if (terminated.get()) {
      task.cancel(true);
      return;
    }
    try {
      documentWorker().execute(task);
    } catch (RejectedExecutionException e) {
      if (!terminated.get()) {
        sendFailure(document.getSequence(), Status.RESOURCE_EXHAUSTED
            .withDescription("analysis capacity is exhausted"));
      }
      task.cancel(false);
    }
  }

  /** Releases one completed task and replenishes inbound demand. */
  private void completeTask(FutureTask<Void> task) {
    tasks.remove(task);
    inFlight.decrementAndGet();
    request(1);
    maybeComplete();
  }

  /** Returns the number of accepted documents that have not reached a terminal task state. */
  int inFlightCount() {
    return inFlight.get();
  }

  /**
   * Returns where document work runs: the dedicated serial worker when this stream keeps
   * adaptive state between documents, otherwise the shared analysis executor.
   */
  private Executor documentWorker() {
    final ExecutorService dedicated = adaptiveWorker;
    return dedicated != null ? dedicated : executor;
  }

  /** Stops the dedicated adaptive-continuity worker when this stream created one. */
  private void shutdownAdaptiveWorker() {
    final ExecutorService dedicated = adaptiveWorker;
    if (dedicated != null) {
      dedicated.shutdown();
    }
  }

  /** Returns the number of tasks retained for cancellation. */
  int trackedTaskCount() {
    return tasks.size();
  }

  /** Analyzes one document and sends a document-local result. */
  private void analyze(AnalyzeStreamDocument document) {
    try {
      send(AnalyzeStreamResponse.newBuilder()
          .setSequence(document.getSequence())
          .setOk(analysisSession.analyze(document.getDocument()))
          .build());
    } catch (AnalysisException e) {
      final Status status = GrpcStatusMapper.toStatus(e);
      // Server-caused failures reach the client as a bare status; without logging here
      // a model throwing on pathological input leaves no server-side trace.
      if (status.getCode() == Status.Code.INTERNAL
          || status.getCode() == Status.Code.UNAVAILABLE) {
        logger.error("AnalyzeStream document analysis failed", e);
      }
      sendFailure(document.getSequence(), status.withDescription(e.getMessage()));
    } catch (RuntimeException e) {
      logger.error("Unexpected error handling AnalyzeStream document", e);
      sendFailure(document.getSequence(), Status.INTERNAL
          .withDescription("Internal server error"));
    }
  }

  /** Sends one document-local failure response. */
  private void sendFailure(long sequence, Status status) {
    final String description = status.getDescription() == null
        ? status.getCode().name() : status.getDescription();
    send(AnalyzeStreamResponse.newBuilder()
        .setSequence(sequence)
        .setError(AnalyzeStreamError.newBuilder()
            .setCode(GrpcStatusMapper.toWireCode(status))
            .setMessage(description))
        .build());
  }

  /** Sends one response when the transport is ready. */
  private void send(AnalyzeStreamResponse response) {
    // Wait for readiness outside outputLock: the lock only serializes the write itself.
    // Holding it across the wait would park sibling workers on an uninterruptible monitor
    // where they can neither observe termination nor be cancelled while the writer is stuck.
    if (terminated.get() || !awaitReady()) {
      return;
    }
    synchronized (outputLock) {
      // Re-check under the lock: the stream may have terminated while this worker waited.
      if (!terminated.get()) {
        try {
          responseObserver.onNext(response);
        } catch (RuntimeException e) {
          terminated.set(true);
          signalReady();
          logger.debug("AnalyzeStream response transport closed during write", e);
        }
      }
    }
  }

  /** Waits until the outbound transport can accept a response. */
  private boolean awaitReady() {
    if (serverCallObserver == null) {
      return true;
    }
    final long deadline = System.nanoTime()
        + TimeUnit.MILLISECONDS.toNanos(READY_TIMEOUT_MILLIS);
    while (!terminated.get() && !serverCallObserver.isReady()) {
      final long remaining = deadline - System.nanoTime();
      if (remaining <= 0) {
        failOutput(Status.RESOURCE_EXHAUSTED
            .withDescription("client did not drain AnalyzeStream responses"));
        return false;
      }
      try {
        synchronized (readyLock) {
          if (!serverCallObserver.isReady() && !terminated.get()) {
            TimeUnit.NANOSECONDS.timedWait(readyLock, remaining);
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        failOutput(Status.CANCELLED.withDescription("analysis response write interrupted"));
        return false;
      }
    }
    return !terminated.get();
  }

  /** Terminates the stream after a protocol violation. */
  private void failProtocol(String description) {
    failStream(Status.INVALID_ARGUMENT.withDescription(description));
  }

  /** Terminates the stream with the given status. */
  private void failStream(Status status) {
    if (terminated.compareAndSet(false, true)) {
      cancelTasks();
      shutdownAdaptiveWorker();
      synchronized (outputLock) {
        responseObserver.onError(status.asRuntimeException());
      }
    }
  }

  /** Requests more inbound frames when the stream remains active. */
  private void request(int count) {
    if (serverCallObserver != null && !terminated.get()) {
      serverCallObserver.request(count);
    }
  }

  /** Completes the response after the client closes and all work finishes. */
  private void maybeComplete() {
    if (clientCompleted && inFlight.get() == 0 && terminated.compareAndSet(false, true)) {
      shutdownAdaptiveWorker();
      synchronized (outputLock) {
        responseObserver.onCompleted();
      }
    }
  }

  /** Cancels active and queued document work. */
  private void cancel() {
    terminateWorkers();
  }

  /** Terminates output after a transport failure. */
  private void failOutput(Status status) {
    if (terminated.compareAndSet(false, true)) {
      cancelTasks();
      shutdownAdaptiveWorker();
      responseObserver.onError(status.asRuntimeException());
    }
  }

  /** Stops accepting output and cancels active work. */
  private void terminateWorkers() {
    if (terminated.compareAndSet(false, true)) {
      cancelTasks();
      shutdownAdaptiveWorker();
    }
  }

  /** Cancels every active or queued document task without waiting for termination. */
  private void cancelTasks() {
    signalReady();
    tasks.forEach(task -> task.cancel(true));
  }

  /** Wakes workers waiting for outbound transport capacity. */
  private void signalReady() {
    synchronized (readyLock) {
      readyLock.notifyAll();
    }
  }
}
