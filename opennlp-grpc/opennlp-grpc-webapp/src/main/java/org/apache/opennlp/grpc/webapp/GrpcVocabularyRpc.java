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
package org.apache.opennlp.grpc.webapp;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.protobuf.ByteString;
import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.v1.DictionaryArtifactDescriptor;
import org.apache.opennlp.grpc.v1.DownloadVocabularyRequest;
import org.apache.opennlp.grpc.v1.ImportDictionaryRequest;
import org.apache.opennlp.grpc.v1.ImportDictionaryUpload;
import org.apache.opennlp.grpc.v1.LearnVocabularyRequest;
import org.apache.opennlp.grpc.v1.LearnVocabularyUpload;
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

final class GrpcVocabularyRpc implements VocabularyRpc {

  private static final int UPLOAD_FRAME_BYTES = 64 * 1024;

  private final OpenNlpVocabularyServiceGrpc.OpenNlpVocabularyServiceBlockingStub blockingStub;
  private final OpenNlpVocabularyServiceGrpc.OpenNlpVocabularyServiceStub asyncStub;
  private final long timeoutNanos;

  /**
   * Creates a gRPC vocabulary adapter.
   *
   * @param channel The channel to the OpenNLP service.
   * @param timeout The deadline applied to every call.
   * @throws IllegalArgumentException If an argument is {@code null} or the timeout is not positive.
   */
  GrpcVocabularyRpc(Channel channel, Duration timeout) {
    if (channel == null) {
      throw new IllegalArgumentException("channel must not be null");
    }
    if (timeout == null) {
      throw new IllegalArgumentException("timeout must not be null");
    }
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    this.blockingStub = OpenNlpVocabularyServiceGrpc.newBlockingStub(channel);
    this.asyncStub = OpenNlpVocabularyServiceGrpc.newStub(channel);
    this.timeoutNanos = timeout.toNanos();
  }

  /** {@inheritDoc} */
  @Override
  public ListDictionaryFormatsResponse listDictionaryFormats() {
    return blockingStub.withDeadlineAfter(timeoutNanos, TimeUnit.NANOSECONDS)
        .listDictionaryFormats(ListDictionaryFormatsRequest.getDefaultInstance());
  }

  /** {@inheritDoc} */
  @Override
  public ListDictionariesResponse listDictionaries() {
    return blockingStub.withDeadlineAfter(timeoutNanos, TimeUnit.NANOSECONDS)
        .listDictionaries(ListDictionariesRequest.getDefaultInstance());
  }

  /** {@inheritDoc} */
  @Override
  public ListVocabulariesResponse listVocabularies() {
    return blockingStub.withDeadlineAfter(timeoutNanos, TimeUnit.NANOSECONDS)
        .listVocabularies(ListVocabulariesRequest.getDefaultInstance());
  }

  /** {@inheritDoc} */
  @Override
  public DictionaryArtifactDescriptor importDictionary(ImportDictionaryUpload upload) {
    final CompletableFuture<DictionaryArtifactDescriptor> published = new CompletableFuture<>();
    final StreamObserver<ImportDictionaryRequest> requests =
        asyncStub.withDeadlineAfter(timeoutNanos, TimeUnit.NANOSECONDS)
            .importDictionary(singleResponse(published));
    requests.onNext(ImportDictionaryRequest.newBuilder().setStart(upload.getStart()).build());
    final ByteString data = upload.getData();
    for (int offset = 0; offset < data.size(); offset += UPLOAD_FRAME_BYTES) {
      requests.onNext(ImportDictionaryRequest.newBuilder()
          .setData(data.substring(offset, Math.min(offset + UPLOAD_FRAME_BYTES, data.size())))
          .build());
    }
    requests.onCompleted();
    return await(published);
  }

  /** {@inheritDoc} */
  @Override
  public VocabularyArtifactDescriptor learnVocabulary(LearnVocabularyUpload upload) {
    final CompletableFuture<VocabularyArtifactDescriptor> published = new CompletableFuture<>();
    final StreamObserver<LearnVocabularyRequest> requests =
        asyncStub.withDeadlineAfter(timeoutNanos, TimeUnit.NANOSECONDS)
            .learnVocabulary(singleResponse(published));
    requests.onNext(LearnVocabularyRequest.newBuilder().setStart(upload.getStart()).build());
    for (OpenNlpDocument document : upload.getDocumentsList()) {
      requests.onNext(LearnVocabularyRequest.newBuilder().setDocument(document).build());
    }
    requests.onCompleted();
    return await(published);
  }

  /** {@inheritDoc} */
  @Override
  public byte[] downloadVocabulary(DownloadVocabularyRequest request) {
    final Iterator<VocabularyArtifactChunk> chunks =
        blockingStub.withDeadlineAfter(timeoutNanos, TimeUnit.NANOSECONDS)
            .downloadVocabulary(request);
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    while (chunks.hasNext()) {
      bytes.writeBytes(chunks.next().getData().toByteArray());
    }
    return bytes.toByteArray();
  }

  /** Completes one future from a single-response observer. */
  private static <T> StreamObserver<T> singleResponse(CompletableFuture<T> future) {
    return new StreamObserver<>() {
      @Override
      public void onNext(T value) {
        future.complete(value);
      }

      @Override
      public void onError(Throwable throwable) {
        future.completeExceptionally(throwable);
      }

      @Override
      public void onCompleted() {
        if (!future.isDone()) {
          future.completeExceptionally(Status.INTERNAL
              .withDescription("The stream completed without a response")
              .asRuntimeException());
        }
      }
    };
  }

  /** Awaits one composed client stream, unwrapping its gRPC status. */
  private <T> T await(CompletableFuture<T> future) {
    try {
      return future.get(timeoutNanos, TimeUnit.NANOSECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw Status.CANCELLED.withDescription("Interrupted while waiting for the response")
          .asRuntimeException();
    } catch (TimeoutException timeout) {
      throw Status.DEADLINE_EXCEEDED.withDescription("The upstream call timed out")
          .asRuntimeException();
    } catch (ExecutionException failure) {
      if (failure.getCause() instanceof StatusRuntimeException status) {
        throw status;
      }
      throw Status.INTERNAL.withDescription("The upstream call failed")
          .withCause(failure.getCause()).asRuntimeException();
    }
  }
}
