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
package org.apache.opennlp.grpc.webapp;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.Channel;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentEvent;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamRequest;
import org.apache.opennlp.grpc.v1.AnalyzeStreamResponse;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.FormatDocumentRequest;
import org.apache.opennlp.grpc.v1.FormatDocumentResponse;
import org.apache.opennlp.grpc.v1.ListOutputFormatsRequest;
import org.apache.opennlp.grpc.v1.ListOutputFormatsResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;

final class GrpcAnalysisRpc implements AnalysisRpc {

  /** Marks the ordered end of a response stream in the transfer queue. */
  private static final Object STREAM_COMPLETE = new Object();

  private final OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub stub;
  private final OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceStub asyncStub;
  private static final long MEBIBYTE = 1024L * 1024L;

  private final long timeoutNanos;
  private final long streamTimeoutNanos;
  private final long timeoutPerMebibyteNanos;

  /**
   * Creates a blocking gRPC adapter whose unary deadlines do not scale with input size.
   *
   * @param channel The channel to the OpenNLP service.
   * @param timeout The deadline applied to every unary call.
   * @param streamTimeout The deadline applied to a whole batch AnalyzeStream call.
   * @throws IllegalArgumentException If an argument is {@code null} or a timeout is not positive.
   */
  GrpcAnalysisRpc(Channel channel, Duration timeout, Duration streamTimeout) {
    this(channel, timeout, streamTimeout, Duration.ZERO);
  }

  /**
   * Creates a blocking gRPC adapter.
   *
   * <p>Document-sized calls (analysis and formatting) carry a deadline of {@code timeout}
   * plus {@code timeoutPerMebibyte} for every mebibyte of document text they submit, never
   * exceeding {@code streamTimeout}, so a novel gets proportionally more time than a
   * sentence without loosening the bound on small requests.</p>
   *
   * @param channel The channel to the OpenNLP service.
   * @param timeout The base deadline applied to every unary call.
   * @param streamTimeout The deadline applied to a whole batch AnalyzeStream call, and the
   *     ceiling of every size-scaled unary deadline.
   * @param timeoutPerMebibyte The extra deadline per mebibyte of submitted document text;
   *     zero disables scaling.
   * @throws IllegalArgumentException If an argument is {@code null}, a timeout is not
   *     positive, or the per-mebibyte allowance is negative.
   */
  GrpcAnalysisRpc(Channel channel, Duration timeout, Duration streamTimeout,
      Duration timeoutPerMebibyte) {
    if (channel == null) {
      throw new IllegalArgumentException("channel must not be null");
    }
    if (timeout == null) {
      throw new IllegalArgumentException("timeout must not be null");
    }
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    if (streamTimeout == null) {
      throw new IllegalArgumentException("streamTimeout must not be null");
    }
    if (streamTimeout.isZero() || streamTimeout.isNegative()) {
      throw new IllegalArgumentException("streamTimeout must be positive");
    }
    if (timeoutPerMebibyte == null) {
      throw new IllegalArgumentException("timeoutPerMebibyte must not be null");
    }
    if (timeoutPerMebibyte.isNegative()) {
      throw new IllegalArgumentException("timeoutPerMebibyte must not be negative");
    }
    this.stub = OpenNlpAnalysisServiceGrpc.newBlockingStub(channel);
    this.asyncStub = OpenNlpAnalysisServiceGrpc.newStub(channel);
    this.timeoutNanos = timeout.toNanos();
    this.streamTimeoutNanos = streamTimeout.toNanos();
    this.timeoutPerMebibyteNanos = timeoutPerMebibyte.toNanos();
  }

  /** {@inheritDoc} */
  @Override
  public GetServiceInfoResponse getServiceInfo() {
    return deadlineStub().getServiceInfo(GetServiceInfoRequest.getDefaultInstance());
  }

  /** {@inheritDoc} */
  @Override
  public ListModelBundlesResponse listModelBundles() {
    return deadlineStub().listModelBundles(ListModelBundlesRequest.getDefaultInstance());
  }

  /** {@inheritDoc} */
  @Override
  public ListOutputFormatsResponse listOutputFormats() {
    return deadlineStub().listOutputFormats(ListOutputFormatsRequest.getDefaultInstance());
  }

  /** {@inheritDoc} */
  @Override
  public FormatDocumentResponse formatDocument(FormatDocumentRequest request) {
    return sizedDeadlineStub(request.getDocument().getSerializedSize())
        .formatDocument(request);
  }

  /** {@inheritDoc} */
  @Override
  public AnalyzeDocumentResponse analyze(AnalyzeDocumentRequest request) {
    return sizedDeadlineStub(request.getDocument().getRawTextBytes().size())
        .analyzeDocument(request);
  }

  /**
   * {@inheritDoc} The call runs under the stream ceiling rather than the size-scaled unary
   * deadline: a server-streaming reply lasts as long as the browser takes to consume it, and
   * a document the server finishes in seconds would otherwise be cut off while the page is
   * still drawing earlier events.
   */
  @Override
  public ProgressiveEvents analyzeProgressively(AnalyzeDocumentRequest request) {
    final Context.CancellableContext context = Context.current().withCancellation();
    final AtomicReference<Iterator<AnalyzeDocumentEvent>> events = new AtomicReference<>();
    try {
      context.run(() -> events.set(
          stub.withDeadlineAfter(streamTimeoutNanos, TimeUnit.NANOSECONDS)
              .analyzeDocumentProgressive(request)));
      return new CancellableEventIterator(events.get(), context);
    } catch (RuntimeException failure) {
      context.cancel(failure);
      throw failure;
    }
  }

  /** Cancels the active gRPC call when its consumer stops reading. */
  private static final class CancellableEventIterator implements ProgressiveEvents {

    private final Iterator<AnalyzeDocumentEvent> delegate;
    private final Context.CancellableContext context;
    private boolean closed;

    /**
     * Creates a cancellable wrapper around one active response stream.
     *
     * @param delegate The blocking response iterator.
     * @param context The context that owns the gRPC call.
     */
    private CancellableEventIterator(
        Iterator<AnalyzeDocumentEvent> delegate, Context.CancellableContext context) {
      this.delegate = delegate;
      this.context = context;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasNext() {
      if (closed) {
        return false;
      }
      final boolean available = delegate.hasNext();
      if (!available) {
        close();
      }
      return available;
    }

    /** {@inheritDoc} */
    @Override
    public AnalyzeDocumentEvent next() {
      if (closed) {
        throw new NoSuchElementException();
      }
      return delegate.next();
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
      if (!closed) {
        closed = true;
        context.cancel(null);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public Iterator<AnalyzeStreamResponse> analyzeStream(List<AnalyzeStreamRequest> frames) {
    if (frames == null || frames.isEmpty()) {
      throw new IllegalArgumentException("frames must not be null or empty");
    }
    final BlockingQueue<Object> transfer = new LinkedBlockingQueue<>();
    final StreamObserver<AnalyzeStreamRequest> requests = asyncStub
        .withDeadlineAfter(streamTimeoutNanos, TimeUnit.NANOSECONDS)
        .analyzeStream(new StreamObserver<>() {
          @Override
          public void onNext(AnalyzeStreamResponse response) {
            transfer.add(response);
          }

          @Override
          public void onError(Throwable failure) {
            transfer.add(failure);
          }

          @Override
          public void onCompleted() {
            transfer.add(STREAM_COMPLETE);
          }
        });
    for (AnalyzeStreamRequest frame : frames) {
      requests.onNext(frame);
    }
    requests.onCompleted();
    return new Iterator<>() {
      private Object pending;

      @Override
      public boolean hasNext() {
        if (pending == null) {
          try {
            pending = transfer.take();
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw Status.CANCELLED.withDescription("interrupted while streaming")
                .asRuntimeException();
          }
        }
        if (pending instanceof Throwable failure) {
          throw Status.fromThrowable(failure).asRuntimeException();
        }
        return pending != STREAM_COMPLETE;
      }

      @Override
      public AnalyzeStreamResponse next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        final AnalyzeStreamResponse response = (AnalyzeStreamResponse) pending;
        pending = null;
        return response;
      }
    };
  }

  /** @return A stub carrying the configured deadline. */
  private OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub deadlineStub() {
    return stub.withDeadlineAfter(timeoutNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Returns a stub whose deadline grows with the submitted input size.
   *
   * @param inputBytes The bytes of document input the call submits.
   * @return A stub carrying the size-scaled deadline.
   */
  private OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub sizedDeadlineStub(
      long inputBytes) {
    return stub.withDeadlineAfter(
        scaledDeadlineNanos(timeoutNanos, timeoutPerMebibyteNanos, streamTimeoutNanos,
            inputBytes),
        TimeUnit.NANOSECONDS);
  }

  /**
   * Computes a deadline that grows linearly with input size within a fixed ceiling.
   *
   * @param baseNanos The deadline for an empty input.
   * @param perMebibyteNanos The extra deadline per mebibyte of input.
   * @param ceilingNanos The largest deadline ever granted.
   * @param inputBytes The input size; negative values count as empty.
   * @return {@code min(ceiling, base + perMebibyte * inputBytes / 1 MiB)}, never below
   *     the base unless the ceiling is smaller.
   */
  static long scaledDeadlineNanos(
      long baseNanos, long perMebibyteNanos, long ceilingNanos, long inputBytes) {
    final double allowance = perMebibyteNanos * (Math.max(0, inputBytes) / (double) MEBIBYTE);
    final double scaled = baseNanos + allowance;
    return (long) Math.min(ceilingNanos, scaled);
  }
}
