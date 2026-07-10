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

import java.util.Objects;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.processor.PipelineStepPolicy;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.EmbedTextRequest;
import org.apache.opennlp.grpc.v1.EmbedTextResponse;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC adapter for the v1 document-centric API.
 */
public class OpenNlpAnalysisServiceImpl extends OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceImplBase {

  private static final Logger logger = LoggerFactory.getLogger(OpenNlpAnalysisServiceImpl.class);

  private static final String API_VERSION = "v1";

  private final DocumentAnalyzer documentAnalyzer;
  private final ProfileRegistry profileRegistry;
  private final ModelBundleCache modelBundleCache;
  private final String opennlpVersion;

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
    this.documentAnalyzer = Objects.requireNonNull(documentAnalyzer, "documentAnalyzer");
    this.profileRegistry = Objects.requireNonNull(profileRegistry, "profileRegistry");
    this.modelBundleCache = Objects.requireNonNull(modelBundleCache, "modelBundleCache");
    this.opennlpVersion = opennlpVersion == null ? "unknown" : opennlpVersion;
  }

  @Override
  public void analyzeDocument(
      AnalyzeDocumentRequest request,
      StreamObserver<AnalyzeDocumentResponse> responseObserver) {
    try {
      responseObserver.onNext(documentAnalyzer.analyze(request));
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      final Status status = GrpcStatusMapper.toStatus(e);
      responseObserver.onError(
          status.withDescription(e.getMessage()).withCause(e.getCause()).asRuntimeException());
    } catch (RuntimeException e) {
      // Any non-AnalysisException is an unexpected server fault. Without this it would escape the
      // handler and gRPC would close the call with an opaque UNKNOWN (and risk leaking the raw
      // exception); map it to a clean INTERNAL, logging the detail server-side only.
      logger.error("Unexpected error handling AnalyzeDocument", e);
      responseObserver.onError(Status.INTERNAL
          .withDescription("Internal server error").withCause(e).asRuntimeException());
    }
  }

  @Override
  public StreamObserver<EmbedTextRequest> embedText(
      StreamObserver<EmbedTextResponse> responseObserver) {
    return new EmbedTextStream(modelBundleCache.getEmbeddingProvider(), responseObserver);
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
    private final StreamObserver<EmbedTextResponse> responseObserver;
    private final io.grpc.stub.ServerCallStreamObserver<EmbedTextResponse> serverCallObserver;
    private final Object readyLock = new Object();
    private String modelId;
    private boolean failed;
    private int writesSinceReady;

    private EmbedTextStream(
        EmbeddingProvider embeddingProvider, StreamObserver<EmbedTextResponse> responseObserver) {
      this.embeddingProvider = embeddingProvider;
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

    @Override
    public void onNext(EmbedTextRequest request) {
      if (failed) {
        return;
      }
      try {
        final float[] vector = embeddingProvider.embed(resolveModel(request), validText(request));
        final EmbedTextResponse.Builder response = EmbedTextResponse.newBuilder()
            .setSequence(request.getSequence());
        for (final float value : vector) {
          response.addVector(value);
        }
        awaitReady();
        responseObserver.onNext(response.build());
      } catch (AnalysisException e) {
        fail(GrpcStatusMapper.toStatus(e)
            .withDescription(e.getMessage()).withCause(e.getCause()).asRuntimeException());
      } catch (RuntimeException e) {
        logger.error("Unexpected error handling EmbedText", e);
        fail(Status.INTERNAL
            .withDescription("Internal server error").withCause(e).asRuntimeException());
      }
    }

    @Override
    public void onError(Throwable t) {
      // The client cancelled or the transport failed; nothing to send back on a dead call.
      logger.debug("EmbedText stream closed by client/transport", t);
    }

    @Override
    public void onCompleted() {
      if (!failed) {
        responseObserver.onCompleted();
      }
    }

    private String resolveModel(EmbedTextRequest request) {
      final String requested = request.hasModelId() ? request.getModelId().trim() : "";
      if (modelId == null) {
        final String resolved =
            embeddingProvider.resolveModelId(requested.isEmpty() ? null : requested);
        if (resolved == null) {
          throw AnalysisException.notFound("EmbedText requires model_id on the first message "
              + "when no single default embedding model can be determined; configured: "
              + embeddingProvider.registeredModelIds());
        }
        if (!embeddingProvider.supportsModel(resolved)) {
          throw AnalysisException.notFound("Unknown embedding model '" + resolved + "'");
        }
        modelId = resolved;
        return modelId;
      }
      if (!requested.isEmpty() && !requested.equals(modelId)) {
        throw AnalysisException.invalidArgument("EmbedText streams use one model: the stream "
            + "started with '" + modelId + "' but a later message names '" + requested
            + "'; open a separate stream per model");
      }
      return modelId;
    }

    private static String validText(EmbedTextRequest request) {
      final String text = request.getText();
      if (text.isBlank()) {
        throw AnalysisException.invalidArgument(
            "EmbedText message with sequence " + request.getSequence() + " has a blank text");
      }
      return text;
    }

    // Bounds outbound buffering. Writes proceed freely while the transport reports ready
    // (or within the elastic window past a not-ready blip); beyond the window, this blocks
    // the (serialized, per-stream) inbound thread until the transport drains. Because gRPC
    // requests the next inbound message only after onNext returns, waiting here also stops
    // granting the client send window: backpressure propagates end to end instead of
    // accumulating on this heap.
    private void awaitReady() {
      if (serverCallObserver == null) {
        return;
      }
      if (serverCallObserver.isReady()) {
        writesSinceReady = 0;
        return;
      }
      if (++writesSinceReady <= UNREADY_WRITE_WINDOW) {
        return;
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
    }

    private void fail(RuntimeException statusException) {
      failed = true;
      responseObserver.onError(statusException);
    }
  }

  @Override
  public void getServiceInfo(
      GetServiceInfoRequest request,
      StreamObserver<GetServiceInfoResponse> responseObserver) {
    responseObserver.onNext(GetServiceInfoResponse.newBuilder()
        .setOpennlpVersion(opennlpVersion)
        .setApiVersion(API_VERSION)
        .addAllAvailableProfileIds(profileRegistry.getProfiles().keySet())
        .addAllSupportedSteps(PipelineStepPolicy.implementedSteps())
        .build());
    responseObserver.onCompleted();
  }

  @Override
  public void listModelBundles(
      ListModelBundlesRequest request,
      StreamObserver<ListModelBundlesResponse> responseObserver) {
    responseObserver.onNext(ListModelBundlesResponse.newBuilder()
        .addAllBundles(modelBundleCache.listBundles())
        .build());
    responseObserver.onCompleted();
  }
}
