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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.google.protobuf.ByteString;
import org.apache.opennlp.grpc.format.OutputFormatterRegistry;
import org.apache.opennlp.grpc.spi.format.OutputFormatter;
import org.apache.opennlp.grpc.v1.FormatDocumentRequest;
import org.apache.opennlp.grpc.v1.FormatDocumentResponse;
import org.apache.opennlp.grpc.v1.ListOutputFormatsRequest;
import org.apache.opennlp.grpc.v1.ListOutputFormatsResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingBatchResult;
import org.apache.opennlp.grpc.embedding.EmbeddingBackendSelections;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalysisSession;
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.processor.PipelineStepPolicy;
import org.apache.opennlp.grpc.processor.ProgressiveAnalysisListener;
import org.apache.opennlp.grpc.processor.ProgressiveDocumentAnalyzer;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentEvent;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalysisLayerBatch;
import org.apache.opennlp.grpc.v1.AnalysisStarted;
import org.apache.opennlp.grpc.v1.AnalysisStepFailure;
import org.apache.opennlp.grpc.v1.AnalyzeStreamRequest;
import org.apache.opennlp.grpc.v1.AnalyzeStreamResponse;
import org.apache.opennlp.grpc.v1.EmbedTextRequest;
import org.apache.opennlp.grpc.v1.EmbedTextResponse;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.StandardLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC adapter for the v1 document-centric API.
 */
public class OpenNlpAnalysisServiceImpl extends OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceImplBase {

  /** Default operator limit for text-bearing requests, in UTF-8 encoded bytes. */
  public static final int DEFAULT_MAX_TEXT_BYTES = 1_048_576;

  private static final Logger logger = LoggerFactory.getLogger(OpenNlpAnalysisServiceImpl.class);

  /**
   * Deployed document output formatters, discovered once. The built-in binary
   * protobuf, protobuf JSON, and TSV formatters ship with the server; add-on jars
   * contribute further formats through the format SPI.
   */
  private static final OutputFormatterRegistry<OpenNlpDocument> outputFormatters =
      OutputFormatterRegistry.discover(OpenNlpDocument.class);

  private static final String API_VERSION = "v1";
  private static final String INTERNAL_SERVER_ERROR = "Internal server error";
  private static final String SERVICE_VERSION = serviceVersion();
  private static final int DEFAULT_ANALYSIS_STREAM_WINDOW =
      Math.max(2, Runtime.getRuntime().availableProcessors());
  private static final List<StandardLayer> STANDARD_LAYERS = Arrays.stream(StandardLayer.values())
      .filter(layer -> layer != StandardLayer.STANDARD_LAYER_UNSPECIFIED
          && layer != StandardLayer.UNRECOGNIZED)
      .toList();

  private final DocumentAnalyzer documentAnalyzer;
  private final ProgressiveDocumentAnalyzer progressiveDocumentAnalyzer;
  private final ProfileRegistry profileRegistry;
  private final ModelBundleCache modelBundleCache;
  private final String opennlpVersion;
  private final Executor analysisExecutor;
  private final int analysisStreamWindow;
  private final int maxTextBytes;

  /**
   * Creates the gRPC service adapter delegating analysis to the given orchestrator and
   * answering capability queries from the profile registry and model cache.
   *
   * @param documentAnalyzer The orchestrator handling {@code analyzeDocument}. Must not be
   *     {@code null}.
   * @param profileRegistry  The registry exposing the available analysis profiles. Must not
   *     be {@code null}.
   * @param modelBundleCache The cache exposing loaded models for capability reporting. Must
   *     not be {@code null}.
   * @param opennlpVersion   The OpenNLP version string reported to clients; {@code "unknown"}
   *     is substituted when {@code null}.
   */
  public OpenNlpAnalysisServiceImpl(
      DocumentAnalyzer documentAnalyzer,
      ProfileRegistry profileRegistry,
      ModelBundleCache modelBundleCache,
      String opennlpVersion) {
    this(documentAnalyzer, profileRegistry, modelBundleCache, opennlpVersion,
        ForkJoinPool.commonPool(), DEFAULT_ANALYSIS_STREAM_WINDOW, DEFAULT_MAX_TEXT_BYTES);
  }

  /**
   * Creates the service with an explicit shared executor and per-stream admission
   * window for full document analysis.
   *
   * @param documentAnalyzer Analyzer handling unary and streaming documents.
   * @param profileRegistry Registry exposing available profiles.
   * @param modelBundleCache Cache exposing models and embedding providers.
   * @param opennlpVersion OpenNLP version reported to clients.
   * @param analysisExecutor Shared executor for streamed document work.
   * @param analysisStreamWindow Maximum documents admitted concurrently per stream.
   */
  public OpenNlpAnalysisServiceImpl(
      DocumentAnalyzer documentAnalyzer,
      ProfileRegistry profileRegistry,
      ModelBundleCache modelBundleCache,
      String opennlpVersion,
      Executor analysisExecutor,
      int analysisStreamWindow) {
    this(documentAnalyzer, profileRegistry, modelBundleCache, opennlpVersion,
        analysisExecutor, analysisStreamWindow, DEFAULT_MAX_TEXT_BYTES);
  }

  /**
   * Creates the service with explicit stream concurrency and operator text limits.
   *
   * @param documentAnalyzer Analyzer handling unary and streaming documents.
   * @param profileRegistry Registry exposing available profiles.
   * @param modelBundleCache Cache exposing models and embedding providers.
   * @param opennlpVersion OpenNLP version reported to clients.
   * @param analysisExecutor Shared executor for streamed document work.
   * @param analysisStreamWindow Maximum documents admitted concurrently per stream.
   * @param maxTextBytes Maximum UTF-8 encoded text bytes accepted on analysis and embedding
   *     messages.
   */
  public OpenNlpAnalysisServiceImpl(
      DocumentAnalyzer documentAnalyzer,
      ProfileRegistry profileRegistry,
      ModelBundleCache modelBundleCache,
      String opennlpVersion,
      Executor analysisExecutor,
      int analysisStreamWindow,
      int maxTextBytes) {
    if (documentAnalyzer == null) {
      throw new IllegalArgumentException("documentAnalyzer must not be null");
    }
    final DocumentAnalyzer delegate = documentAnalyzer;
    if (profileRegistry == null) {
      throw new IllegalArgumentException("profileRegistry must not be null");
    }
    this.profileRegistry = profileRegistry;
    if (modelBundleCache == null) {
      throw new IllegalArgumentException("modelBundleCache must not be null");
    }
    this.modelBundleCache = modelBundleCache;
    this.opennlpVersion = opennlpVersion == null ? "unknown" : opennlpVersion;
    if (analysisExecutor == null) {
      throw new IllegalArgumentException("analysisExecutor must not be null");
    }
    this.analysisExecutor = analysisExecutor;
    if (analysisStreamWindow < 1) {
      throw new IllegalArgumentException("analysisStreamWindow must be positive");
    }
    if (maxTextBytes < 1) {
      throw new IllegalArgumentException("maxTextBytes must be positive");
    }
    this.analysisStreamWindow = analysisStreamWindow;
    this.maxTextBytes = maxTextBytes;
    this.progressiveDocumentAnalyzer = delegate instanceof ProgressiveDocumentAnalyzer progressive
        ? progressive : null;
    this.documentAnalyzer = limitText(delegate, maxTextBytes);
  }

  /** {@inheritDoc} */
  @Override
  public StreamObserver<AnalyzeStreamRequest> analyzeStream(
      StreamObserver<AnalyzeStreamResponse> responseObserver) {
    return new AnalyzeDocumentStream(
        documentAnalyzer,
        analysisExecutor,
        analysisStreamWindow,
        responseObserver);
  }

  /** {@inheritDoc} */
  @Override
  public void analyzeDocument(
      AnalyzeDocumentRequest request,
      StreamObserver<AnalyzeDocumentResponse> responseObserver) {
    try {
      responseObserver.onNext(documentAnalyzer.analyze(request));
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      final Status status = GrpcStatusMapper.toStatus(e);
      // Server-caused failures reach the client as a bare status; without logging here
      // a model throwing on pathological input leaves no server-side trace.
      if (status.getCode() == Status.Code.INTERNAL
          || status.getCode() == Status.Code.UNAVAILABLE) {
        logger.error("AnalyzeDocument failed", e);
      }
      responseObserver.onError(
          status.withDescription(e.getMessage()).withCause(e.getCause()).asRuntimeException());
    } catch (RuntimeException e) {
      // Any non-AnalysisException is an unexpected server fault. Without this it would escape the
      // handler and gRPC would close the call with an opaque UNKNOWN (and risk leaking the raw
      // exception); map it to a clean INTERNAL, logging the detail server-side only.
      logger.error("Unexpected error handling AnalyzeDocument", e);
      responseObserver.onError(Status.INTERNAL
          .withDescription(INTERNAL_SERVER_ERROR).withCause(e).asRuntimeException());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void analyzeDocumentProgressive(
      AnalyzeDocumentRequest request,
      StreamObserver<AnalyzeDocumentEvent> responseObserver) {
    if (responseObserver == null) {
      throw new IllegalArgumentException("responseObserver must not be null");
    }
    final ProgressiveResponseWriter writer = new ProgressiveResponseWriter(responseObserver);
    final ProgressiveAnalysisListener listener = new ProgressiveAnalysisListener() {
      private final AtomicLong sequence = new AtomicLong();

      /** {@inheritDoc} */
      @Override
      public void onStarted(AnalysisStarted started) {
        writer.next(AnalyzeDocumentEvent.newBuilder()
            .setSequence(sequence.incrementAndGet())
            .setStarted(started)
            .build());
      }

      /** {@inheritDoc} */
      @Override
      public void onLayersReady(AnalysisLayerBatch layers) {
        writer.next(AnalyzeDocumentEvent.newBuilder()
            .setSequence(sequence.incrementAndGet())
            .setLayersReady(layers)
            .build());
      }

      /** {@inheritDoc} */
      @Override
      public void onStepFailed(PipelineStep step, RuntimeException failure) {
        final Status status = statusFor(failure, "Progressive analysis branch failed");
        writer.next(AnalyzeDocumentEvent.newBuilder()
            .setSequence(sequence.incrementAndGet())
            .setStepFailed(AnalysisStepFailure.newBuilder()
                .setStep(step)
                .setCode(GrpcStatusMapper.toWireCode(status))
                .setMessage(clientMessage(failure))
                .build())
            .build());
      }

      /** {@inheritDoc} */
      @Override
      public void onComplete(AnalyzeDocumentResponse response) {
        writer.next(AnalyzeDocumentEvent.newBuilder()
            .setSequence(sequence.incrementAndGet())
            .setComplete(response)
            .build());
        writer.complete();
      }

      /** {@inheritDoc} */
      @Override
      public void onError(RuntimeException failure) {
        writer.error(statusFor(failure, "Progressive analysis failed")
            .withDescription(clientMessage(failure))
            .withCause(failure)
            .asRuntimeException());
      }

      /** {@inheritDoc} */
      @Override
      public boolean isCancelled() {
        return writer.isCancelled();
      }
    };

    try {
      if (request != null && request.hasDocument()) {
        validateText(request.getDocument().getRawText(), maxTextBytes);
      }
      if (progressiveDocumentAnalyzer != null) {
        progressiveDocumentAnalyzer.analyzeProgressively(request, analysisExecutor, listener);
      } else {
        analysisExecutor.execute(() -> analyzeProgressiveFallback(request, listener));
      }
    } catch (RuntimeException e) {
      listener.onError(e);
    }
  }

  /** Runs a unary analyzer behind the progressive contract when it has no branch support. */
  private void analyzeProgressiveFallback(
      AnalyzeDocumentRequest request, ProgressiveAnalysisListener listener) {
    try {
      listener.onStarted(AnalysisStarted.newBuilder()
          .setDocument(request.getDocument())
          .build());
      final AnalyzeDocumentResponse response = documentAnalyzer.analyze(request);
      if (response.getDocument().hasLayers()) {
        listener.onLayersReady(AnalysisLayerBatch.newBuilder()
            .setStep(PipelineStep.PIPELINE_STEP_UNSPECIFIED)
            .addAllLayers(response.getDocument().getLayers().getLayersList())
            .addAllDiagnostics(response.getDiagnosticsList())
            .build());
      }
      listener.onComplete(response);
    } catch (RuntimeException e) {
      listener.onError(e);
    }
  }

  /** Maps a progressive failure and keeps unexpected details server-side. */
  private Status statusFor(RuntimeException failure, String operation) {
    if (failure instanceof AnalysisException analysisFailure) {
      final Status status = GrpcStatusMapper.toStatus(analysisFailure);
      if (status.getCode() == Status.Code.INTERNAL
          || status.getCode() == Status.Code.UNAVAILABLE) {
        logger.error(operation, failure);
      }
      return status;
    }
    logger.error(operation, failure);
    return Status.INTERNAL;
  }

  /** Returns the client-visible failure text without exposing unexpected internals. */
  private String clientMessage(RuntimeException failure) {
    return failure instanceof AnalysisException ? failure.getMessage() : INTERNAL_SERVER_ERROR;
  }

  /** Serializes progressive writes and waits for outbound transport capacity. */
  private static final class ProgressiveResponseWriter {

    private static final long READY_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(30);

    private final StreamObserver<AnalyzeDocumentEvent> responseObserver;
    private final ServerCallStreamObserver<AnalyzeDocumentEvent> serverObserver;
    private final Object readyLock = new Object();
    private boolean terminal;

    /**
     * Creates a writer for one server stream.
     *
     * @param responseObserver The stream receiving progressive events.
     */
    private ProgressiveResponseWriter(StreamObserver<AnalyzeDocumentEvent> responseObserver) {
      this.responseObserver = responseObserver;
      if (responseObserver instanceof ServerCallStreamObserver<AnalyzeDocumentEvent> observer) {
        serverObserver = observer;
        observer.setOnReadyHandler(() -> {
          synchronized (readyLock) {
            readyLock.notifyAll();
          }
        });
        observer.setOnCancelHandler(() -> {
          synchronized (readyLock) {
            readyLock.notifyAll();
          }
        });
      } else {
        serverObserver = null;
      }
    }

    /** Writes one event after transport readiness is available. */
    private synchronized void next(AnalyzeDocumentEvent event) {
      if (terminal || isCancelled()) {
        return;
      }
      awaitReady();
      if (!terminal && !isCancelled()) {
        responseObserver.onNext(event);
      }
    }

    /** Completes the response once. */
    private synchronized void complete() {
      if (!terminal && !isCancelled()) {
        terminal = true;
        responseObserver.onCompleted();
      }
    }

    /** Fails the response once. */
    private synchronized void error(RuntimeException failure) {
      if (!terminal && !isCancelled()) {
        terminal = true;
        responseObserver.onError(failure);
      }
    }

    /** Returns whether the transport has been cancelled. */
    private boolean isCancelled() {
      return serverObserver != null && serverObserver.isCancelled();
    }

    /** Blocks the virtual coordinator while the client is not draining events. */
    private void awaitReady() {
      if (serverObserver == null || serverObserver.isReady()) {
        return;
      }
      final long deadline = System.nanoTime() + READY_TIMEOUT_NANOS;
      synchronized (readyLock) {
        while (!serverObserver.isReady() && !serverObserver.isCancelled()) {
          final long remaining = deadline - System.nanoTime();
          if (remaining <= 0) {
            throw AnalysisException.resourceExhausted(
                "Progressive analysis client did not drain responses");
          }
          try {
            TimeUnit.NANOSECONDS.timedWait(readyLock, remaining);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw AnalysisException.unavailable(
                "Interrupted while waiting for progressive response capacity", e);
          }
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public StreamObserver<EmbedTextRequest> embedText(
      StreamObserver<EmbedTextResponse> responseObserver) {
    return new EmbedTextStream(
        modelBundleCache.getEmbeddingProvider(), maxTextBytes, responseObserver);
  }

  /**
   * One EmbedText stream: texts in, vectors out, in order. gRPC delivers a stream's
   * messages serially and (with automatic flow control) requests the next message only
   * after {@link #onNext} returns, so embedding synchronously here gives 1:1 request/response
   * coupling and inbound backpressure without any buffering of our own. The outbound side
   * needs its own gate: a client that pumps texts but does not read vectors would otherwise
   * queue responses on the server heap without bound, so before each write the stream waits
   * for transport readiness and fails loud if the client stays unready. The stream's model
   * is fixed by the first message; a later message naming a different model, a blank text,
   * or an unresolvable model id terminates the stream with a status, matching the service's
   * fail-loud error model (no per-message error payloads).
   */
  private static final class EmbedTextStream implements StreamObserver<EmbedTextRequest> {

    // A client that has not drained any responses for this long is not a slow reader, it
    // is a stalled or hostile one; the stream fails rather than buffering further.
    private static final long READY_TIMEOUT_MILLIS = 30_000;

    // How many responses may be written past the transport's not-ready signal before the
    // stream blocks for readiness. Not-ready only means the low (32 KB) write-buffer
    // threshold is crossed, and blocking on every such blip serializes the pipeline against
    // the drain cadence, costing about a third of streaming throughput in benchmarks. This
    // window restores elasticity while keeping per-stream buffering bounded (about 1 MB for
    // a 256-dimension model) instead of letting a non-reading client grow the heap without
    // limit.
    private static final int UNREADY_WRITE_WINDOW = 1_024;

    private final EmbeddingProvider embeddingProvider;
    private final int maxTextBytes;
    private final StreamObserver<EmbedTextResponse> responseObserver;
    private final io.grpc.stub.ServerCallStreamObserver<EmbedTextResponse> serverCallObserver;
    private final Object readyLock = new Object();
    private String modelId;
    private String backendId;
    private boolean failed;
    private boolean cancelled;
    private int writesSinceReady;

    private EmbedTextStream(
        EmbeddingProvider embeddingProvider,
        int maxTextBytes,
        StreamObserver<EmbedTextResponse> responseObserver) {
      this.embeddingProvider = embeddingProvider;
      this.maxTextBytes = maxTextBytes;
      this.responseObserver = responseObserver;
      // Direct unit tests pass a plain observer; the gate then degrades to always-ready,
      // which is fine because there is no transport to back up in that case.
      if (responseObserver instanceof io.grpc.stub.ServerCallStreamObserver<EmbedTextResponse> o) {
        this.serverCallObserver = o;
        o.setOnReadyHandler(() -> {
          synchronized (readyLock) {
            readyLock.notifyAll();
          }
        });
      } else {
        this.serverCallObserver = null;
      }
    }

    /** {@inheritDoc} */
    @Override
    public void onNext(EmbedTextRequest request) {
      if (failed || cancelled) {
        return;
      }
      try {
        final String text = validText(request, maxTextBytes);
        resolveRoute(request);
        final EmbeddingBatchResult result = embeddingProvider.embedBatchResolved(
            modelId, backendId, List.of(text));
        final float[] vector = result.vectors().getFirst();
        final EmbedTextResponse.Builder response = EmbedTextResponse.newBuilder()
            .setSequence(request.getSequence())
            .setRoute(result.route());
        for (final float value : vector) {
          response.addVector(value);
        }
        if (!awaitReady()) {
          // The client cancelled while the stream waited for transport capacity; the call
          // is dead, so stop quietly instead of writing or reporting a failure.
          cancelled = true;
          return;
        }
        responseObserver.onNext(response.build());
      } catch (AnalysisException e) {
        final Status status = GrpcStatusMapper.toStatus(e);
        // Server-caused failures reach the client as a bare status; without logging here
        // a model throwing on pathological input leaves no server-side trace.
        if (status.getCode() == Status.Code.INTERNAL
            || status.getCode() == Status.Code.UNAVAILABLE) {
          logger.error("EmbedText failed", e);
        }
        fail(status.withDescription(e.getMessage()).withCause(e.getCause())
            .asRuntimeException());
      } catch (RuntimeException e) {
        logger.error("Unexpected error handling EmbedText", e);
        fail(Status.INTERNAL
            .withDescription(INTERNAL_SERVER_ERROR).withCause(e).asRuntimeException());
      }
    }

    /** {@inheritDoc} */
    @Override
    public void onError(Throwable t) {
      // The client cancelled or the transport failed; nothing to send back on a dead call.
      logger.debug("EmbedText stream closed by client/transport", t);
    }

    /** {@inheritDoc} */
    @Override
    public void onCompleted() {
      if (!failed && !cancelled) {
        responseObserver.onCompleted();
      }
    }

    /** Resolves and fixes the embedding route for this stream. */
    private void resolveRoute(EmbedTextRequest request) {
      if (request.hasModelId() && request.hasEmbeddingSelector()) {
        throw AnalysisException.invalidArgument(
            "EmbedText model_id and embedding_selector are mutually exclusive");
      }
      final String requested;
      final String requestedBackend;
      if (request.hasEmbeddingSelector()) {
        requested = request.getEmbeddingSelector().getModelId().trim();
        final String selectedBackend = EmbeddingBackendSelections.selectedId(
            request.getEmbeddingSelector());
        requestedBackend = selectedBackend == null ? "" : selectedBackend;
        if (requested.isEmpty()) {
          throw AnalysisException.invalidArgument(
              "EmbedText embedding_selector.model_id must not be blank");
        }
      } else {
        requested = request.hasModelId() ? request.getModelId().trim() : "";
        requestedBackend = "";
      }
      if (modelId == null) {
        final String resolved =
            embeddingProvider.resolveModelId(requested.isEmpty() ? null : requested);
        if (resolved == null) {
          throw AnalysisException.notFound("EmbedText requires model_id on the first message "
              + "when no single default embedding model can be determined; configured: "
              + embeddingProvider.registeredModelIds());
        }
        final String selectedBackend = requestedBackend.isEmpty() ? null : requestedBackend;
        if (!embeddingProvider.supportsModel(resolved, selectedBackend)
            && (selectedBackend != null || !embeddingProvider.supportsModel(resolved))) {
          throw AnalysisException.notFound(selectedBackend == null
              ? "Unknown embedding model '" + resolved + "'"
              : "Engine '" + selectedBackend + "' does not serve embedding model '"
                  + resolved + "'");
        }
        modelId = resolved;
        backendId = selectedBackend;
        return;
      }
      if (!requested.isEmpty() && !requested.equals(modelId)) {
        throw AnalysisException.invalidArgument("EmbedText streams use one model: the stream "
            + "started with '" + modelId + "' but a later message names '" + requested
            + "'; open a separate stream per model");
      }
      if (request.hasEmbeddingSelector()
          && !Objects.equals(requestedBackend.isEmpty() ? null : requestedBackend, backendId)) {
        throw AnalysisException.invalidArgument("EmbedText streams use one backend route: the "
            + "stream started with '" + displayBackend(backendId)
            + "' but a later message names '"
            + displayBackend(requestedBackend.isEmpty() ? null : requestedBackend)
            + "'; open a separate stream per backend route");
      }
    }

    /** Returns a client-facing backend label. */
    private static String displayBackend(String backendId) {
      return backendId == null ? "default" : backendId;
    }

    /** Returns validated request text. */
    private static String validText(EmbedTextRequest request, int maxTextBytes) {
      final String text = request.getText();
      if (text.isBlank()) {
        throw AnalysisException.invalidArgument(
            "EmbedText message with sequence " + request.getSequence() + " has a blank text");
      }
      if (exceedsUtf8Bytes(text, maxTextBytes)) {
        throw AnalysisException.invalidArgument(
            "EmbedText message with sequence " + request.getSequence()
                + " exceeds server max_text_bytes (" + maxTextBytes + ")");
      }
      return text;
    }

    // Bounds outbound buffering. Writes proceed freely while the transport reports ready
    // (or within the elastic window past a not-ready blip); beyond the window, this blocks
    // the (serialized, per-stream) inbound thread until the transport drains. Because gRPC
    // requests the next inbound message only after onNext returns, waiting here also stops
    // granting the client send window: backpressure propagates end to end instead of
    // accumulating on this heap.
    /** Waits until the outbound transport can accept a response; false when the call is dead. */
    private boolean awaitReady() {
      if (serverCallObserver == null) {
        return true;
      }
      if (serverCallObserver.isCancelled()) {
        return false;
      }
      if (serverCallObserver.isReady()) {
        writesSinceReady = 0;
        return true;
      }
      if (++writesSinceReady <= UNREADY_WRITE_WINDOW) {
        return true;
      }
      final long deadline = System.currentTimeMillis() + READY_TIMEOUT_MILLIS;
      synchronized (readyLock) {
        while (!serverCallObserver.isReady() && !serverCallObserver.isCancelled()) {
          final long remaining = deadline - System.currentTimeMillis();
          if (remaining <= 0) {
            throw AnalysisException.resourceExhausted(
                "EmbedText client stopped reading responses for " + READY_TIMEOUT_MILLIS
                    + " ms; closing the stream instead of buffering further");
          }
          try {
            readyLock.wait(Math.min(remaining, 1_000));
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw AnalysisException.internal("Interrupted while waiting for stream readiness", e);
          }
        }
      }
      writesSinceReady = 0;
      return !serverCallObserver.isCancelled();
    }

    /** Terminates the embedding stream once with the given status. */
    private void fail(RuntimeException statusException) {
      failed = true;
      responseObserver.onError(statusException);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void getServiceInfo(
      GetServiceInfoRequest request,
      StreamObserver<GetServiceInfoResponse> responseObserver) {
    responseObserver.onNext(GetServiceInfoResponse.newBuilder()
        .setOpennlpVersion(opennlpVersion)
        .setApiVersion(API_VERSION)
        .addAllAvailableProfileIds(profileRegistry.getProfiles().keySet())
        .addAllSupportedSteps(PipelineStepPolicy.implementedSteps())
        .addAllSupportedLayers(STANDARD_LAYERS)
        .addAllCustomTokenizerIds(modelBundleCache.getTokenizerRegistry().ids())
        .addAllCustomSentenceDetectorIds(
            modelBundleCache.getSentenceDetectorRegistry().ids())
        .addAllConfiguredResources(modelBundleCache.listConfiguredResources())
        .setMaxTextBytes(maxTextBytes)
        .setServiceVersion(SERVICE_VERSION)
        .build());
    responseObserver.onCompleted();
  }

  /** {@inheritDoc} */
  @Override
  public void listModelBundles(
      ListModelBundlesRequest request,
      StreamObserver<ListModelBundlesResponse> responseObserver) {
    responseObserver.onNext(ListModelBundlesResponse.newBuilder()
        .addAllBundles(modelBundleCache.listBundles())
        .build());
    responseObserver.onCompleted();
  }

  /** {@inheritDoc} */
  @Override
  public void listOutputFormats(
      ListOutputFormatsRequest request,
      StreamObserver<ListOutputFormatsResponse> responseObserver) {
    responseObserver.onNext(ListOutputFormatsResponse.newBuilder()
        .addAllFormats(outputFormatters.descriptors())
        .build());
    responseObserver.onCompleted();
  }

  /** {@inheritDoc} */
  @Override
  public void formatDocument(
      FormatDocumentRequest request,
      StreamObserver<FormatDocumentResponse> responseObserver) {
    try {
      if (!request.hasDocument()) {
        throw AnalysisException.invalidArgument("FormatDocumentRequest.document is required");
      }
      final OutputFormatter<OpenNlpDocument> formatter =
          outputFormatters.require(request.getFormatId());
      final ByteArrayOutputStream rendered = new ByteArrayOutputStream();
      try {
        formatter.format(request.getDocument(), rendered);
      } catch (IOException | RuntimeException e) {
        // The formatter is add-on code; its failure detail stays server-side.
        logger.error("Output formatter '{}' failed", formatter.formatId(), e);
        responseObserver.onError(Status.INTERNAL
            .withDescription("Output formatting failed").withCause(e).asRuntimeException());
        return;
      }
      responseObserver.onNext(FormatDocumentResponse.newBuilder()
          .setContent(ByteString.copyFrom(rendered.toByteArray()))
          .setMediaType(formatter.mediaType())
          .setFileExtension(formatter.fileExtension())
          .build());
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      responseObserver.onError(GrpcStatusMapper.toStatus(e)
          .withDescription(e.getMessage()).withCause(e.getCause()).asRuntimeException());
    } catch (RuntimeException e) {
      logger.error("Unexpected error handling FormatDocument", e);
      responseObserver.onError(Status.INTERNAL
          .withDescription(INTERNAL_SERVER_ERROR).withCause(e).asRuntimeException());
    }
  }

  /** Returns the packaged service version or a development fallback. */
  private static String serviceVersion() {
    final String implementationVersion = OpenNlpAnalysisServiceImpl.class
        .getPackage().getImplementationVersion();
    return implementationVersion == null || implementationVersion.isBlank()
        ? "dev" : implementationVersion;
  }

  /** Wraps an analyzer with the operator text-size limit. */
  private static DocumentAnalyzer limitText(DocumentAnalyzer delegate, int maxTextBytes) {
    return new DocumentAnalyzer() {
      /** {@inheritDoc} */
      @Override
      public AnalyzeDocumentResponse analyze(AnalyzeDocumentRequest request) {
        if (request != null && request.hasDocument()) {
          validateText(request.getDocument().getRawText(), maxTextBytes);
        }
        return delegate.analyze(request);
      }

      /** {@inheritDoc} */
      @Override
      public DocumentAnalysisSession openSession(
          org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration configuration) {
        final DocumentAnalysisSession session = delegate.openSession(configuration);
        return document -> {
          if (document != null) {
            validateText(document.getRawText(), maxTextBytes);
          }
          return session.analyze(document);
        };
      }
    };
  }

  /** Validates text. */
  private static void validateText(String text, int maxTextBytes) {
    if (exceedsUtf8Bytes(text, maxTextBytes)) {
      throw AnalysisException.invalidArgument(
          "document.raw_text exceeds server max_text_bytes (" + maxTextBytes + ")");
    }
  }

  /** Returns whether UTF-8 encoding exceeds the configured byte limit. */
  private static boolean exceedsUtf8Bytes(String text, int maxTextBytes) {
    int remaining = maxTextBytes;
    for (int index = 0; index < text.length(); index++) {
      final char value = text.charAt(index);
      final int width;
      if (value <= 0x7f) {
        width = 1;
      } else if (value <= 0x7ff) {
        width = 2;
      } else if (Character.isHighSurrogate(value)
          && index + 1 < text.length()
          && Character.isLowSurrogate(text.charAt(index + 1))) {
        width = 4;
        index++;
      } else {
        width = 3;
      }
      remaining -= width;
      if (remaining < 0) {
        return true;
      }
    }
    return false;
  }
}
