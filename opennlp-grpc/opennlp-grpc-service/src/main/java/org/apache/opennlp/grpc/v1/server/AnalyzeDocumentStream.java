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

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.processor.AnalysisException;
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

  private volatile AnalyzeStreamConfiguration configuration;
  private volatile DocumentAnalysisSession analysisSession;
  private volatile boolean clientCompleted;

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
    this.documentAnalyzer = Objects.requireNonNull(documentAnalyzer, "documentAnalyzer");
    this.executor = Objects.requireNonNull(executor, "executor");
    if (streamWindow < 1) {
      throw new IllegalArgumentException("streamWindow must be positive");
    }
    this.streamWindow = streamWindow;
    this.responseObserver = Objects.requireNonNull(responseObserver, "responseObserver");
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

  @Override
  public void onError(Throwable error) {
    // The caller or transport has already ended the call, so workers only need
    // the termination flag to suppress late writes.
    terminated.set(true);
    signalReady();
    logger.debug("AnalyzeStream closed by client or transport", error);
  }

  @Override
  public void onCompleted() {
    clientCompleted = true;
    maybeComplete();
  }

  private void acceptConfiguration(AnalyzeStreamConfiguration requestedConfiguration) {
    if (configuration != null) {
      failProtocol("configuration may only be the first message of the stream");
      return;
    }
    configuration = requestedConfiguration;
    try {
      analysisSession = Objects.requireNonNull(
          documentAnalyzer.openSession(requestedConfiguration),
          "DocumentAnalyzer.openSession returned null");
    } catch (AnalysisException e) {
      failStream(GrpcStatusMapper.toStatus(e).withDescription(e.getMessage()));
      return;
    } catch (RuntimeException e) {
      logger.error("Unexpected error preparing AnalyzeStream configuration", e);
      failStream(Status.INTERNAL.withDescription("Internal server error"));
      return;
    }
    request(streamWindow);
  }

  private void acceptDocument(AnalyzeStreamDocument document) {
    if (configuration == null) {
      failProtocol("the first message of the stream must carry configuration");
      return;
    }
    inFlight.incrementAndGet();
    try {
      executor.execute(() -> analyze(document));
    } catch (RejectedExecutionException e) {
      inFlight.decrementAndGet();
      sendFailure(document.getSequence(), Status.RESOURCE_EXHAUSTED
          .withDescription("analysis capacity is exhausted"));
      request(1);
      maybeComplete();
    }
  }

  private void analyze(AnalyzeStreamDocument document) {
    try {
      send(AnalyzeStreamResponse.newBuilder()
          .setSequence(document.getSequence())
          .setOk(analysisSession.analyze(document.getDocument()))
          .build());
    } catch (AnalysisException e) {
      sendFailure(document.getSequence(), GrpcStatusMapper.toStatus(e)
          .withDescription(e.getMessage()));
    } catch (RuntimeException e) {
      logger.error("Unexpected error handling AnalyzeStream document", e);
      sendFailure(document.getSequence(), Status.INTERNAL
          .withDescription("Internal server error"));
    } finally {
      inFlight.decrementAndGet();
      request(1);
      maybeComplete();
    }
  }

  private void sendFailure(long sequence, Status status) {
    final String description = status.getDescription() == null
        ? status.getCode().name() : status.getDescription();
    send(AnalyzeStreamResponse.newBuilder()
        .setSequence(sequence)
        .setError(AnalyzeStreamError.newBuilder()
            .setCode(status.getCode().value())
            .setMessage(description))
        .build());
  }

  private void send(AnalyzeStreamResponse response) {
    synchronized (outputLock) {
      if (!terminated.get() && awaitReady()) {
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

  private void failProtocol(String description) {
    failStream(Status.INVALID_ARGUMENT.withDescription(description));
  }

  private void failStream(Status status) {
    if (terminated.compareAndSet(false, true)) {
      signalReady();
      synchronized (outputLock) {
        responseObserver.onError(status.asRuntimeException());
      }
    }
  }

  private void request(int count) {
    if (serverCallObserver != null && !terminated.get()) {
      serverCallObserver.request(count);
    }
  }

  private void maybeComplete() {
    if (clientCompleted && inFlight.get() == 0 && terminated.compareAndSet(false, true)) {
      synchronized (outputLock) {
        responseObserver.onCompleted();
      }
    }
  }

  private void cancel() {
    terminated.set(true);
    signalReady();
  }

  private void failOutput(Status status) {
    if (terminated.compareAndSet(false, true)) {
      signalReady();
      responseObserver.onError(status.asRuntimeException());
    }
  }

  private void signalReady() {
    synchronized (readyLock) {
      readyLock.notifyAll();
    }
  }
}
