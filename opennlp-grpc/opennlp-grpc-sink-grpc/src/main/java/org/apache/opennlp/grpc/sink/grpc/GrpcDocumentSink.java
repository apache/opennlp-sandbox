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
package org.apache.opennlp.grpc.sink.grpc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.ByteString;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.spi.format.OutputFormatter;
import org.apache.opennlp.grpc.spi.sink.DocumentSink;
import org.apache.opennlp.grpc.v1.DocumentSinkItem;
import org.apache.opennlp.grpc.v1.DocumentSinkSummary;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.OpenNlpDocumentSinkServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Streams analyzed documents to one downstream {@code OpenNlpDocumentSinkService}
 * receiver over a single client-streaming call, optionally attaching a rendering from
 * a deployed output formatter to every item. Delivery is sequential per sink: accept
 * calls synchronize on the request stream, and close half-closes the stream and waits
 * briefly for the receiver's summary.
 */
final class GrpcDocumentSink implements DocumentSink {

  private static final Logger logger = LoggerFactory.getLogger(GrpcDocumentSink.class);

  private static final long SUMMARY_WAIT_SECONDS = 10;

  private final String instanceId;
  private final ManagedChannel ownedChannel;
  private final OutputFormatter<OpenNlpDocument> formatter;
  private final StreamObserver<DocumentSinkItem> requests;
  private final CountDownLatch completed = new CountDownLatch(1);
  private volatile DocumentSinkSummary summary;
  private volatile Throwable failure;
  private boolean closed;

  /**
   * Opens the client stream to the receiver.
   *
   * @param instanceId The configured instance id, for diagnostics.
   * @param channel The channel to the receiver. Must not be {@code null}.
   * @param ownedChannel The channel to shut down on close, or {@code null} when the
   *     caller owns the channel's lifecycle.
   * @param formatter The rendering to attach to every item, or {@code null} for none.
   */
  GrpcDocumentSink(String instanceId, Channel channel, ManagedChannel ownedChannel,
      OutputFormatter<OpenNlpDocument> formatter) {
    if (instanceId == null || instanceId.isBlank()) {
      throw new IllegalArgumentException("instanceId must not be blank");
    }
    this.instanceId = instanceId;
    if (channel == null) {
      throw new IllegalArgumentException("channel must not be null");
    }
    this.ownedChannel = ownedChannel;
    this.formatter = formatter;
    this.requests = OpenNlpDocumentSinkServiceGrpc.newStub(channel)
        .streamDocuments(new StreamObserver<>() {
          @Override
          public void onNext(DocumentSinkSummary value) {
            summary = value;
          }

          @Override
          public void onError(Throwable t) {
            failure = t;
            completed.countDown();
          }

          @Override
          public void onCompleted() {
            completed.countDown();
          }
        });
  }

  /** {@inheritDoc} */
  @Override
  public synchronized void accept(OpenNlpDocument document) throws IOException {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    if (closed) {
      throw new IOException("Document sink '" + instanceId + "' is closed");
    }
    if (failure != null) {
      throw new IOException("Document sink '" + instanceId
          + "' stream failed; dropping document '" + document.getDocId() + "'", failure);
    }
    final DocumentSinkItem.Builder item = DocumentSinkItem.newBuilder().setDocument(document);
    if (formatter != null) {
      final ByteArrayOutputStream rendered = new ByteArrayOutputStream();
      formatter.format(document, rendered);
      item.setFormatId(formatter.formatId())
          .setMediaType(formatter.mediaType())
          .setRendered(ByteString.copyFrom(rendered.toByteArray()));
    }
    requests.onNext(item.build());
  }

  /** {@inheritDoc} */
  @Override
  public synchronized void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    try {
      if (failure == null) {
        requests.onCompleted();
        try {
          if (completed.await(SUMMARY_WAIT_SECONDS, TimeUnit.SECONDS)) {
            final DocumentSinkSummary acknowledged = summary;
            if (acknowledged != null) {
              logger.info("Document sink '{}' receiver acknowledged {} document(s)",
                  instanceId, acknowledged.getAcceptedDocuments());
            }
          } else {
            logger.warn("Document sink '{}' receiver sent no summary within {} seconds",
                instanceId, SUMMARY_WAIT_SECONDS);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      if (failure != null) {
        throw new IOException("Document sink '" + instanceId + "' stream failed", failure);
      }
    } finally {
      if (ownedChannel != null) {
        ownedChannel.shutdownNow();
      }
    }
  }
}
