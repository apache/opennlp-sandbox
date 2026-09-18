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
package org.apache.opennlp.grpc.vocabulary;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.v1.DictionaryArtifactDescriptor;
import org.apache.opennlp.grpc.v1.DownloadVocabularyRequest;
import org.apache.opennlp.grpc.v1.ImportDictionaryRequest;
import org.apache.opennlp.grpc.v1.ImportDictionaryStart;
import org.apache.opennlp.grpc.v1.LearnVocabularyRequest;
import org.apache.opennlp.grpc.v1.LearnVocabularyStart;
import org.apache.opennlp.grpc.v1.ListDictionaryFormatsRequest;
import org.apache.opennlp.grpc.v1.ListDictionaryFormatsResponse;
import org.apache.opennlp.grpc.v1.ListDictionariesRequest;
import org.apache.opennlp.grpc.v1.ListDictionariesResponse;
import org.apache.opennlp.grpc.v1.ListVocabulariesRequest;
import org.apache.opennlp.grpc.v1.ListVocabulariesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.OpenNlpVocabularyServiceGrpc;
import org.apache.opennlp.grpc.v1.VocabularyArtifactChunk;
import org.apache.opennlp.grpc.v1.VocabularyArtifactDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** gRPC adapter for dictionary format discovery and bounded vocabulary artifact creation. */
public final class OpenNlpVocabularyServiceImpl
    extends OpenNlpVocabularyServiceGrpc.OpenNlpVocabularyServiceImplBase {

  private static final Logger logger = LoggerFactory.getLogger(OpenNlpVocabularyServiceImpl.class);
  private static final int DOWNLOAD_CHUNK_BYTES = 64 * 1024;
  private static final String WRITES_DISABLED_DESCRIPTION =
      VocabularyArtifactStore.ARTIFACT_ROOT_KEY
          + " is not configured; vocabulary writes are disabled";

  private final DictionaryFormatRegistry formats;
  private final VocabularyArtifactStore store;
  private final Semaphore writePermits;

  /**
   * Creates the service over immutable format and artifact registries.
   *
   * @param formats Available dictionary format providers.
   * @param store Bounded artifact store, possibly write-disabled.
   * @throws IllegalArgumentException If either argument is {@code null}.
   */
  public OpenNlpVocabularyServiceImpl(
      DictionaryFormatRegistry formats, VocabularyArtifactStore store) {
    if (formats == null) {
      throw new IllegalArgumentException("formats must not be null");
    }
    if (store == null) {
      throw new IllegalArgumentException("store must not be null");
    }
    this.formats = formats;
    this.store = store;
    this.writePermits = new Semaphore(store.maxConcurrentWrites());
  }

  /** {@inheritDoc} */
  @Override
  public void listDictionaryFormats(
      ListDictionaryFormatsRequest request,
      StreamObserver<ListDictionaryFormatsResponse> responseObserver) {
    responseObserver.onNext(ListDictionaryFormatsResponse.newBuilder()
        .addAllFormats(formats.descriptors())
        .setWritesEnabled(store.writesEnabled())
        .setMaxDictionaryBytes(store.maxDictionaryBytes())
        .setMaxDictionaryEntries(store.maxDictionaryEntries())
        .setMaxCorpusDocuments(store.maxCorpusDocuments())
        .setMaxCorpusBytes(store.maxCorpusBytes())
        .setMaxVocabularyTerms(store.maxVocabularyTerms())
        .setMaxConcurrentWrites(store.maxConcurrentWrites())
        .build());
    responseObserver.onCompleted();
  }

  /** {@inheritDoc} */
  @Override
  public void listDictionaries(
      ListDictionariesRequest request,
      StreamObserver<ListDictionariesResponse> responseObserver) {
    responseObserver.onNext(ListDictionariesResponse.newBuilder()
        .addAllDictionaries(store.listDictionaries())
        .build());
    responseObserver.onCompleted();
  }

  /** {@inheritDoc} */
  @Override
  public void listVocabularies(
      ListVocabulariesRequest request,
      StreamObserver<ListVocabulariesResponse> responseObserver) {
    responseObserver.onNext(ListVocabulariesResponse.newBuilder()
        .addAllVocabularies(store.listVocabularies())
        .build());
    responseObserver.onCompleted();
  }

  /** {@inheritDoc} */
  @Override
  public StreamObserver<ImportDictionaryRequest> importDictionary(
      StreamObserver<DictionaryArtifactDescriptor> responseObserver) {
    return new StreamObserver<>() {
      private final WriteAdmission admission = new WriteAdmission();
      private final ByteArrayOutputStream data = new ByteArrayOutputStream();
      private ImportDictionaryStart start;

      @Override
      public void onNext(ImportDictionaryRequest request) {
        if (admission.isTerminated()) {
          return;
        }
        if (request == null) {
          fail(responseObserver, admission,
              Status.INVALID_ARGUMENT.withDescription(
                  "ImportDictionary frame must not be null"));
          return;
        }
        switch (request.getFrameCase()) {
          case START -> acceptStart(request.getStart());
          case DATA -> acceptData(request.getData());
          case FRAME_NOT_SET -> fail(responseObserver, admission,
              Status.INVALID_ARGUMENT.withDescription(
                  "ImportDictionary frame kind must be set"));
        }
      }

      /** Accepts and validates the first import frame. */
      private void acceptStart(ImportDictionaryStart candidate) {
        if (start != null) {
          fail(responseObserver, admission, Status.INVALID_ARGUMENT.withDescription(
              "ImportDictionary start must appear exactly once as the first frame"));
          return;
        }
        if (!store.writesEnabled()) {
          fail(responseObserver, admission, Status.FAILED_PRECONDITION.withDescription(
              WRITES_DISABLED_DESCRIPTION));
          return;
        }
        try {
          formats.require(candidate.getFormat());
          if (!admission.acquire()) {
            fail(responseObserver, admission, Status.RESOURCE_EXHAUSTED.withDescription(
                "concurrent vocabulary writes exceed configured maximum "
                    + store.maxConcurrentWrites()));
            return;
          }
          start = candidate;
        } catch (IllegalArgumentException e) {
          fail(responseObserver, admission,
              Status.INVALID_ARGUMENT.withDescription(e.getMessage()));
        }
      }

      /** Retains one bounded import data frame. */
      private void acceptData(ByteString chunk) {
        if (start == null) {
          fail(responseObserver, admission, Status.INVALID_ARGUMENT.withDescription(
              "ImportDictionary start must be the first frame"));
          return;
        }
        if (chunk.isEmpty()) {
          fail(responseObserver, admission, Status.INVALID_ARGUMENT.withDescription(
              "ImportDictionary data frame must not be empty"));
          return;
        }
        if ((long) data.size() + chunk.size() > store.maxDictionaryBytes()) {
          fail(responseObserver, admission, Status.INVALID_ARGUMENT.withDescription(
              "dictionary bytes exceed configured maximum " + store.maxDictionaryBytes()));
          return;
        }
        try {
          chunk.writeTo(data);
        } catch (IOException impossible) {
          failUnexpected(responseObserver, admission, "ImportDictionary", impossible);
        }
      }

      @Override
      public void onError(Throwable throwable) {
        admission.terminate();
      }

      @Override
      public void onCompleted() {
        if (admission.isTerminated()) {
          return;
        }
        if (start == null) {
          fail(responseObserver, admission, Status.INVALID_ARGUMENT.withDescription(
              "ImportDictionary requires a start frame"));
          return;
        }
        final DictionaryArtifactDescriptor descriptor;
        try {
          descriptor = store.importDictionary(start, data.toByteArray());
        } catch (IllegalArgumentException e) {
          fail(responseObserver, admission,
              Status.INVALID_ARGUMENT.withDescription(e.getMessage()));
          return;
        } catch (IOException | RuntimeException e) {
          failUnexpected(responseObserver, admission, "ImportDictionary", e);
          return;
        }
        if (admission.terminate()) {
          responseObserver.onNext(descriptor);
          responseObserver.onCompleted();
        }
      }
    };
  }

  /** Sends one terminal gRPC error and releases any held write admission. */
  private <T> void fail(
      StreamObserver<T> responseObserver, WriteAdmission admission, Status status) {
    if (admission.terminate()) {
      responseObserver.onError(status.asRuntimeException());
    }
  }

  /** Logs one internal failure without exposing filesystem or parser details to the caller. */
  private <T> void failUnexpected(
      StreamObserver<T> responseObserver,
      WriteAdmission admission,
      String operation,
      Throwable failure) {
    logger.error("{} failed", operation, failure);
    fail(responseObserver, admission, Status.INTERNAL.withDescription(operation + " failed"));
  }

  /** Tracks terminal state and one optional permit for a client-streamed write operation. */
  private final class WriteAdmission {
    private final AtomicBoolean terminated = new AtomicBoolean();
    private boolean permitHeld;

    /** @return Whether this stream has already emitted or received a terminal event. */
    private boolean isTerminated() {
      return terminated.get();
    }

    /** @return Whether a write permit was acquired immediately. */
    private boolean acquire() {
      permitHeld = writePermits.tryAcquire();
      return permitHeld;
    }

    /** @return Whether this call won terminal delivery. */
    private boolean terminate() {
      if (!terminated.compareAndSet(false, true)) {
        return false;
      }
      if (permitHeld) {
        permitHeld = false;
        writePermits.release();
      }
      return true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public StreamObserver<LearnVocabularyRequest> learnVocabulary(
      StreamObserver<VocabularyArtifactDescriptor> responseObserver) {
    return new StreamObserver<>() {
      private final WriteAdmission admission = new WriteAdmission();
      private final List<OpenNlpDocument> documents = new ArrayList<>();
      private LearnVocabularyStart start;
      private long corpusBytes;

      @Override
      public void onNext(LearnVocabularyRequest request) {
        if (admission.isTerminated()) {
          return;
        }
        if (request == null) {
          fail(responseObserver, admission, Status.INVALID_ARGUMENT.withDescription(
              "LearnVocabulary frame must not be null"));
          return;
        }
        switch (request.getFrameCase()) {
          case START -> acceptStart(request.getStart());
          case DOCUMENT -> acceptDocument(request.getDocument());
          case FRAME_NOT_SET -> fail(responseObserver, admission,
              Status.INVALID_ARGUMENT.withDescription(
                  "LearnVocabulary frame kind must be set"));
        }
      }

      /** Accepts and validates the first vocabulary-build frame. */
      private void acceptStart(LearnVocabularyStart candidate) {
        if (start != null) {
          fail(responseObserver, admission, Status.INVALID_ARGUMENT.withDescription(
              "LearnVocabulary start must appear exactly once as the first frame"));
          return;
        }
        if (!store.writesEnabled()) {
          fail(responseObserver, admission, Status.FAILED_PRECONDITION.withDescription(
              WRITES_DISABLED_DESCRIPTION));
          return;
        }
        try {
          if (!candidate.getDictionaryArtifactId().isEmpty()) {
            store.requireDictionary(candidate.getDictionaryArtifactId());
          }
          if (!admission.acquire()) {
            fail(responseObserver, admission, Status.RESOURCE_EXHAUSTED.withDescription(
                "concurrent vocabulary writes exceed configured maximum "
                    + store.maxConcurrentWrites()));
            return;
          }
          start = candidate;
        } catch (UnknownVocabularyArtifactException e) {
          fail(responseObserver, admission, Status.NOT_FOUND.withDescription(e.getMessage()));
        } catch (IllegalArgumentException e) {
          fail(responseObserver, admission,
              Status.INVALID_ARGUMENT.withDescription(e.getMessage()));
        }
      }

      /** Retains one bounded document-shaped corpus frame. */
      private void acceptDocument(OpenNlpDocument document) {
        if (start == null) {
          fail(responseObserver, admission, Status.INVALID_ARGUMENT.withDescription(
              "LearnVocabulary start must be the first frame"));
          return;
        }
        if (document.getRawText().isBlank()) {
          fail(responseObserver, admission, Status.INVALID_ARGUMENT.withDescription(
              "LearnVocabulary document raw_text must not be blank"));
          return;
        }
        if (documents.size() >= store.maxCorpusDocuments()) {
          fail(responseObserver, admission, Status.INVALID_ARGUMENT.withDescription(
              "corpus documents exceed configured maximum " + store.maxCorpusDocuments()));
          return;
        }
        corpusBytes += document.getRawText().getBytes(StandardCharsets.UTF_8).length;
        if (corpusBytes > store.maxCorpusBytes()) {
          fail(responseObserver, admission, Status.INVALID_ARGUMENT.withDescription(
              "corpus bytes exceed configured maximum " + store.maxCorpusBytes()));
          return;
        }
        documents.add(document);
      }

      @Override
      public void onError(Throwable throwable) {
        admission.terminate();
      }

      @Override
      public void onCompleted() {
        if (admission.isTerminated()) {
          return;
        }
        if (start == null) {
          fail(responseObserver, admission, Status.INVALID_ARGUMENT.withDescription(
              "LearnVocabulary requires a start frame"));
          return;
        }
        final VocabularyArtifactDescriptor descriptor;
        try {
          descriptor = store.learnVocabulary(start, List.copyOf(documents));
        } catch (UnknownVocabularyArtifactException e) {
          fail(responseObserver, admission, Status.NOT_FOUND.withDescription(e.getMessage()));
          return;
        } catch (IllegalArgumentException e) {
          fail(responseObserver, admission,
              Status.INVALID_ARGUMENT.withDescription(e.getMessage()));
          return;
        } catch (IOException | RuntimeException e) {
          failUnexpected(responseObserver, admission, "LearnVocabulary", e);
          return;
        }
        if (admission.terminate()) {
          responseObserver.onNext(descriptor);
          responseObserver.onCompleted();
        }
      }
    };
  }

  /** {@inheritDoc} */
  @Override
  public void downloadVocabulary(
      DownloadVocabularyRequest request,
      StreamObserver<VocabularyArtifactChunk> responseObserver) {
    if (!store.writesEnabled()) {
      responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(
          "vocabulary.artifact_root is not configured; vocabulary downloads are disabled")
          .asRuntimeException());
      return;
    }
    try {
      final VocabularyArtifactDescriptor descriptor =
          store.requireVocabulary(request.getArtifactId());
      try (InputStream input = store.openVocabulary(descriptor.getArtifactId())) {
        final byte[] buffer = new byte[DOWNLOAD_CHUNK_BYTES];
        int sequence = 0;
        int length;
        while ((length = input.read(buffer)) >= 0) {
          if (length > 0) {
            responseObserver.onNext(VocabularyArtifactChunk.newBuilder()
                .setArtifactId(descriptor.getArtifactId())
                .setSequence(sequence++)
                .setData(ByteString.copyFrom(buffer, 0, length))
                .build());
          }
        }
      }
      responseObserver.onCompleted();
    } catch (UnknownVocabularyArtifactException e) {
      responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (IllegalArgumentException e) {
      responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (IOException e) {
      logger.error("DownloadVocabulary failed", e);
      responseObserver.onError(Status.INTERNAL.withDescription("DownloadVocabulary failed")
          .asRuntimeException());
    }
  }
}
