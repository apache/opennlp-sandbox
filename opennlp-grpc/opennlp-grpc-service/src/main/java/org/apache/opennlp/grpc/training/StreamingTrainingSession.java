/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalysisSession;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamDocument;
import org.apache.opennlp.grpc.v1.AnalyzeStreamError;
import org.apache.opennlp.grpc.v1.AnalyzeStreamResponse;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.StaticModelDescriptor;
import org.apache.opennlp.grpc.v1.StreamingTrainingAccepted;
import org.apache.opennlp.grpc.v1.StreamingTrainingCompleted;
import org.apache.opennlp.grpc.v1.StreamingTrainingDocumentUpdate;
import org.apache.opennlp.grpc.v1.StreamingTrainingIndexDurability;
import org.apache.opennlp.grpc.v1.StreamingTrainingProgress;
import org.apache.opennlp.grpc.v1.StreamingTrainingRequest;
import org.apache.opennlp.grpc.v1.StreamingTrainingStage;
import org.apache.opennlp.grpc.v1.StreamingTrainingStart;
import org.apache.opennlp.grpc.v1.StreamingTrainingUpdate;
import org.apache.opennlp.grpc.v1.VocabularyArtifactDescriptor;
import org.apache.opennlp.grpc.v1.server.GrpcStatusMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One bounded document-to-index bidirectional training session. */
final class StreamingTrainingSession implements StreamObserver<StreamingTrainingRequest> {

  private static final Logger logger = LoggerFactory.getLogger(StreamingTrainingSession.class);
  private static final long READY_TIMEOUT_MILLIS = 30_000;

  private final StreamingTrainingPipeline pipeline;
  private final StreamObserver<StreamingTrainingUpdate> responseObserver;
  private final ServerCallStreamObserver<StreamingTrainingUpdate> serverCallObserver;
  private final StreamingTrainingPipeline.Limits limits;
  private final List<OpenNlpDocument> documents = new ArrayList<>();
  private final AtomicBoolean terminated = new AtomicBoolean();
  private final AtomicBoolean terminalCallbackRun = new AtomicBoolean();
  private final Object readyLock = new Object();
  private final Runnable terminalCallback;

  private StreamingTrainingStart start;
  private DocumentAnalysisSession analysis;
  private int corpusBytes;
  private int retainedBytes;

  /** Creates one stream and grants only its first inbound frame. */
  StreamingTrainingSession(
      StreamingTrainingPipeline pipeline,
      StreamObserver<StreamingTrainingUpdate> responseObserver) {
    this(pipeline, responseObserver, () -> { });
  }

  /** Creates one stream with a callback that releases its admission permit. */
  StreamingTrainingSession(
      StreamingTrainingPipeline pipeline,
      StreamObserver<StreamingTrainingUpdate> responseObserver,
      Runnable terminalCallback) {
    if (pipeline == null) {
      throw new IllegalArgumentException("pipeline must not be null");
    }
    if (responseObserver == null) {
      throw new IllegalArgumentException("responseObserver must not be null");
    }
    if (terminalCallback == null) {
      throw new IllegalArgumentException("terminalCallback must not be null");
    }
    this.pipeline = pipeline;
    this.responseObserver = responseObserver;
    this.terminalCallback = terminalCallback;
    this.limits = pipeline.limits();
    if (responseObserver instanceof ServerCallStreamObserver<StreamingTrainingUpdate> observer) {
      serverCallObserver = observer;
      observer.disableAutoRequest();
      observer.setOnCancelHandler(this::cancel);
      observer.setOnReadyHandler(this::signalReady);
      observer.request(1);
    } else {
      serverCallObserver = null;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void onNext(StreamingTrainingRequest request) {
    if (terminated.get()) {
      return;
    }
    try {
      if (request == null) {
        throw new IllegalArgumentException("StreamingTraining frame must not be null");
      }
      switch (request.getFrameCase()) {
        case START -> acceptStart(request.getStart());
        case DOCUMENT -> acceptDocument(request.getDocument());
        case FRAME_NOT_SET -> throw new IllegalArgumentException(
            "StreamingTraining frame kind must be set");
      }
    } catch (IllegalArgumentException e) {
      fail(Status.INVALID_ARGUMENT.withDescription(e.getMessage()));
    } catch (IllegalStateException e) {
      fail(Status.FAILED_PRECONDITION.withDescription(e.getMessage()));
    } catch (AnalysisException e) {
      fail(GrpcStatusMapper.toStatus(e).withDescription(e.getMessage()));
    } finally {
      requestNext();
    }
  }

  /** Validates and fixes the session configuration. */
  private void acceptStart(StreamingTrainingStart candidate) {
    if (start != null) {
      throw new IllegalArgumentException(
          "StreamingTraining start must appear exactly once as the first frame");
    }
    validateStart(candidate);
    try {
      pipeline.validateStart(candidate);
      analysis = pipeline.openAnalysis(candidate.getAnalysis());
    } catch (IllegalArgumentException | IllegalStateException | AnalysisException e) {
      throw e;
    } catch (RuntimeException analysisProviderFailure) {
      throw AnalysisException.internal(
          "StreamingTraining analysis provider could not open a session",
          analysisProviderFailure);
    }
    start = candidate;
    send(StreamingTrainingUpdate.newBuilder()
        .setAccepted(StreamingTrainingAccepted.newBuilder()
            .setMaxDocuments(limits.maxDocuments())
            .setMaxCorpusBytes(limits.maxCorpusBytes())
            .setModelTrainingEnabled(limits.modelTrainingEnabled())
            .setIndexingEnabled(limits.indexingEnabled()))
        .build());
  }

  /** Analyzes and retains one successful, bounded document. */
  private void acceptDocument(AnalyzeStreamDocument frame) {
    if (start == null) {
      throw new IllegalArgumentException("StreamingTraining start must be the first frame");
    }
    final OpenNlpDocument input = frame.getDocument();
    final int textBytes = input.getRawText().getBytes(StandardCharsets.UTF_8).length;
    if (documents.size() >= limits.maxDocuments()) {
      fail(Status.RESOURCE_EXHAUSTED.withDescription(
          "StreamingTraining corpus documents exceed configured maximum "
              + limits.maxDocuments()));
      return;
    }
    if ((long) corpusBytes + textBytes > limits.maxCorpusBytes()) {
      fail(Status.RESOURCE_EXHAUSTED.withDescription(
          "StreamingTraining corpus bytes exceed configured maximum "
              + limits.maxCorpusBytes()));
      return;
    }
    AnalyzeStreamResponse result;
    try {
      final AnalyzeDocumentResponse analyzed = analysis.analyze(input);
      final OpenNlpDocument retained = retain(input);
      if ((long) retainedBytes + retained.getSerializedSize() > limits.maxCorpusBytes()) {
        fail(Status.RESOURCE_EXHAUSTED.withDescription(
            "StreamingTraining retained document bytes exceed configured maximum "
                + limits.maxCorpusBytes()));
        return;
      }
      documents.add(retained);
      corpusBytes += textBytes;
      retainedBytes += retained.getSerializedSize();
      result = AnalyzeStreamResponse.newBuilder()
          .setSequence(frame.getSequence())
          .setOk(analyzed)
          .build();
    } catch (AnalysisException e) {
      final Status status = GrpcStatusMapper.toStatus(e).withDescription(e.getMessage());
      result = documentFailure(frame.getSequence(), status);
    } catch (RuntimeException analysisProviderFailure) {
      final AnalysisException failure = AnalysisException.internal(
          "StreamingTraining analysis provider failed", analysisProviderFailure);
      logger.error(failure.getMessage(), failure);
      result = documentFailure(frame.getSequence(),
          Status.INTERNAL.withDescription("Internal server error"));
    }
    send(StreamingTrainingUpdate.newBuilder()
        .setDocument(StreamingTrainingDocumentUpdate.newBuilder()
            .setResult(result)
            .setAcceptedDocuments(documents.size())
            .setAcceptedCorpusBytes(corpusBytes))
        .build());
  }

  /** {@inheritDoc} */
  @Override
  public void onError(Throwable throwable) {
    cancel();
  }

  /** Publishes every configured terminal stage after client half-close. */
  @Override
  public void onCompleted() {
    if (terminated.get()) {
      return;
    }
    if (start == null) {
      fail(Status.INVALID_ARGUMENT.withDescription(
          "StreamingTraining requires a start frame"));
      return;
    }
    if (documents.isEmpty()) {
      fail(Status.INVALID_ARGUMENT.withDescription(
          "StreamingTraining requires at least one successfully analyzed document"));
      return;
    }
    VocabularyArtifactDescriptor vocabulary = null;
    StaticModelDescriptor model = null;
    StreamingTrainingPipeline.IndexPublication index = null;
    try {
      requireActive();
      progress(StreamingTrainingStage.STREAMING_TRAINING_STAGE_VOCABULARY,
          "Learning vocabulary");
      vocabulary = publishVocabulary();
      requireActive();
      if (start.hasModel()) {
        final String vocabularyId = vocabulary.getArtifactId();
        model = publishModel(vocabularyId);
        requireActive();
      }
      if (start.hasIndex()) {
        progress(StreamingTrainingStage.STREAMING_TRAINING_STAGE_INDEX,
            "Creating search index");
        index = publishIndex(model);
        requireActive();
      }
      final StreamingTrainingCompleted.Builder completed = StreamingTrainingCompleted.newBuilder()
          .setVocabulary(vocabulary)
          .setAcceptedDocuments(documents.size())
          .setAcceptedCorpusBytes(corpusBytes);
      if (model != null) {
        completed.setModel(model);
      }
      if (index != null) {
        completed.setIndex(index.response());
      }
      if (!send(StreamingTrainingUpdate.newBuilder().setCompleted(completed).build())) {
        throw new CancellationException("StreamingTraining completion was not delivered");
      }
      if (terminated.compareAndSet(false, true)) {
        try {
          responseObserver.onCompleted();
        } finally {
          runTerminalCallback();
        }
      }
    } catch (CancellationException e) {
      rollback(vocabulary, model, index, e);
      cancel();
    } catch (AnalysisException e) {
      rollback(vocabulary, model, index, e);
      fail(GrpcStatusMapper.toStatus(e).withDescription(e.getMessage()));
    } catch (IllegalArgumentException e) {
      rollback(vocabulary, model, index, e);
      fail(Status.INVALID_ARGUMENT.withDescription(e.getMessage()));
    } catch (IllegalStateException e) {
      rollback(vocabulary, model, index, e);
      fail(Status.FAILED_PRECONDITION.withDescription(e.getMessage()));
    } catch (IOException | StreamingTrainingPublicationException e) {
      rollback(vocabulary, model, index, e);
      logger.error("StreamingTraining publication failed", e);
      fail(Status.INTERNAL.withDescription("StreamingTraining publication failed"));
    }
  }

  /** Publishes the vocabulary while translating the vocabulary SPI boundary. */
  private VocabularyArtifactDescriptor publishVocabulary() throws IOException {
    try {
      return pipeline.learnVocabulary(start.getVocabulary(), List.copyOf(documents));
    } catch (IllegalArgumentException | IllegalStateException | AnalysisException
        | StreamingTrainingPublicationException e) {
      throw e;
    } catch (RuntimeException vocabularyProviderFailure) {
      throw new StreamingTrainingPublicationException(
          "Unexpected vocabulary publication provider failure", vocabularyProviderFailure);
    }
  }

  /** Publishes the model while translating the training-provider boundary. */
  private StaticModelDescriptor publishModel(String vocabularyId) throws IOException {
    try {
      return pipeline.trainModel(start.getModel(), vocabularyId,
          message -> progress(StreamingTrainingStage.STREAMING_TRAINING_STAGE_MODEL, message),
          this::isCancelled);
    } catch (IllegalArgumentException | IllegalStateException | AnalysisException
        | StreamingTrainingPublicationException e) {
      throw e;
    } catch (RuntimeException modelProviderFailure) {
      throw new StreamingTrainingPublicationException(
          "Unexpected model publication provider failure", modelProviderFailure);
    }
  }

  /** Publishes the index while translating the search-provider boundary. */
  private StreamingTrainingPipeline.IndexPublication publishIndex(StaticModelDescriptor model)
      throws IOException {
    try {
      return pipeline.createIndex(start, model, List.copyOf(documents), this::isCancelled);
    } catch (IllegalArgumentException | IllegalStateException | AnalysisException
        | StreamingTrainingPublicationException e) {
      throw e;
    } catch (RuntimeException searchProviderFailure) {
      throw new StreamingTrainingPublicationException(
          "Unexpected index publication provider failure", searchProviderFailure);
    }
  }

  /** Applies structural validation before any session resource is retained. */
  private void validateStart(StreamingTrainingStart candidate) {
    if (!candidate.hasVocabulary()) {
      throw new IllegalArgumentException("StreamingTraining vocabulary configuration is required");
    }
    if (candidate.hasModel() && !limits.modelTrainingEnabled()) {
      throw new IllegalStateException("StreamingTraining model training is disabled");
    }
    if (!candidate.hasIndex()) {
      return;
    }
    if (!candidate.hasModel()) {
      throw new IllegalArgumentException(
          "StreamingTraining index configuration requires a model plan");
    }
    if (!limits.indexingEnabled()) {
      throw new IllegalStateException("StreamingTraining dynamic indexing is disabled");
    }
    if (candidate.getIndex().getDurability()
        == StreamingTrainingIndexDurability.STREAMING_TRAINING_INDEX_DURABILITY_UNSPECIFIED
        || candidate.getIndex().getDurability() == StreamingTrainingIndexDurability.UNRECOGNIZED) {
      throw new IllegalArgumentException(
          "StreamingTraining index durability must be specified");
    }
    if (candidate.getIndex().getChunkEmbedConfigsCount() == 0
        && candidate.getIndex().getCategoryChunkConfigsCount() == 0) {
      throw new IllegalArgumentException(
          "StreamingTraining index requires at least one chunk configuration");
    }
    candidate.getIndex().getChunkEmbedConfigsList().forEach(config -> {
      if (config.getEmbeddingModelIdsCount() != 0
          || config.getEmbeddingSelectorsCount() != 0) {
        throw new IllegalArgumentException(
            "StreamingTraining chunk configurations must omit embedding selections");
      }
    });
    candidate.getIndex().getCategoryChunkConfigsList().forEach(config -> {
      if (config.getEmbeddingModelIdsCount() != 0
          || config.getEmbeddingSelectorsCount() != 0) {
        throw new IllegalArgumentException(
            "StreamingTraining category chunk configurations must omit embedding selections");
      }
    });
    if (candidate.getIndex().hasAlias()
        && (candidate.getIndex().getAlias().isBlank()
            || !candidate.getIndex().getAlias().equals(candidate.getIndex().getAlias().trim()))) {
      throw new IllegalArgumentException(
          "StreamingTraining index alias must be nonblank and trimmed");
    }
  }

  /** Keeps only source identity, text, and caller metadata for terminal stages. */
  private static OpenNlpDocument retain(OpenNlpDocument input) {
    return OpenNlpDocument.newBuilder()
        .setDocId(input.getDocId())
        .setRawText(input.getRawText())
        .setMetadata(input.getMetadata())
        .build();
  }

  /** Builds one document-local failure using the established stream error shape. */
  private static AnalyzeStreamResponse documentFailure(long sequence, Status status) {
    final String description = status.getDescription() == null
        ? status.getCode().name() : status.getDescription();
    return AnalyzeStreamResponse.newBuilder()
        .setSequence(sequence)
        .setError(AnalyzeStreamError.newBuilder()
            .setCode(GrpcStatusMapper.toWireCode(status))
            .setMessage(description))
        .build();
  }

  /** Sends one typed progress update. */
  private void progress(StreamingTrainingStage stage, String message) {
    requireActive();
    send(StreamingTrainingUpdate.newBuilder()
        .setProgress(StreamingTrainingProgress.newBuilder()
            .setStage(stage)
            .setMessage(message == null ? "" : message))
        .build());
    requireActive();
  }

  /** Rolls back published session stages in reverse order. */
  private void rollback(
      VocabularyArtifactDescriptor vocabulary,
      StaticModelDescriptor model,
      StreamingTrainingPipeline.IndexPublication index,
      Throwable failure) {
    if (index != null) {
      try {
        index.rollback().run();
      } catch (IOException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      } catch (RuntimeException cleanupDependencyFailure) {
        failure.addSuppressed(new StreamingTrainingPublicationException(
            "Unexpected index rollback dependency failure", cleanupDependencyFailure));
      }
    }
    if (model != null) {
      try {
        pipeline.deleteModel(model.getArtifactId());
      } catch (IOException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      } catch (RuntimeException cleanupDependencyFailure) {
        failure.addSuppressed(new StreamingTrainingPublicationException(
            "Unexpected model rollback dependency failure", cleanupDependencyFailure));
      }
    }
    if (vocabulary != null) {
      try {
        pipeline.deleteVocabulary(vocabulary.getArtifactId());
      } catch (IOException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      } catch (RuntimeException cleanupDependencyFailure) {
        failure.addSuppressed(new StreamingTrainingPublicationException(
            "Unexpected vocabulary rollback dependency failure", cleanupDependencyFailure));
      }
    }
  }

  /** Sends one output after honoring transport readiness. */
  private boolean send(StreamingTrainingUpdate update) {
    if (terminated.get() || !awaitReady()) {
      return false;
    }
    try {
      responseObserver.onNext(update);
      return !isCancelled();
    } catch (RuntimeException transportFailure) {
      // StreamObserver implementations may signal a closed transport only by throwing.
      cancel();
      logger.debug("StreamingTraining response transport closed during write", transportFailure);
      return false;
    }
  }

  /** Waits until the outbound transport can accept another response. */
  private boolean awaitReady() {
    if (serverCallObserver == null) {
      return true;
    }
    final long deadline = System.nanoTime()
        + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(READY_TIMEOUT_MILLIS);
    synchronized (readyLock) {
      while (!terminated.get() && !serverCallObserver.isCancelled()
          && !serverCallObserver.isReady()) {
        final long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          fail(Status.DEADLINE_EXCEEDED.withDescription(
              "StreamingTraining response transport remained unready"));
          return false;
        }
        try {
          java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(readyLock, remaining);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          cancel();
          return false;
        }
      }
    }
    return !isCancelled();
  }

  /** Wakes a producer waiting for response readiness. */
  private void signalReady() {
    synchronized (readyLock) {
      readyLock.notifyAll();
    }
  }

  /** Grants the next input frame only after the current one is fully processed. */
  private void requestNext() {
    if (serverCallObserver != null && !terminated.get() && !serverCallObserver.isCancelled()) {
      serverCallObserver.request(1);
    }
  }

  /** Terminates this call with one sanitized status. */
  private void fail(Status status) {
    if (terminated.compareAndSet(false, true)) {
      signalReady();
      try {
        responseObserver.onError(status.asRuntimeException());
      } finally {
        runTerminalCallback();
      }
    }
  }

  /** Marks cancellation without writing after the transport closed. */
  private void cancel() {
    terminated.set(true);
    signalReady();
    runTerminalCallback();
  }

  /** Releases service-level admission exactly once on every terminal path. */
  private void runTerminalCallback() {
    if (terminalCallbackRun.compareAndSet(false, true)) {
      terminalCallback.run();
    }
  }

  /** @return Whether the client or session has terminated. */
  private boolean isCancelled() {
    return terminated.get()
        || (serverCallObserver != null && serverCallObserver.isCancelled());
  }

  /** Throws when work must stop before another publication boundary. */
  private void requireActive() {
    if (isCancelled()) {
      throw new CancellationException("StreamingTraining call is cancelled");
    }
  }
}
