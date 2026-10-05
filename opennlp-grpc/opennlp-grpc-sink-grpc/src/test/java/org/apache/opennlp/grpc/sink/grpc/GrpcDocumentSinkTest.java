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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.sink.DocumentSinkRegistry;
import org.apache.opennlp.grpc.spi.sink.DocumentSink;
import org.apache.opennlp.grpc.spi.sink.DocumentSinkProvider;
import org.apache.opennlp.grpc.v1.DocumentSinkItem;
import org.apache.opennlp.grpc.v1.DocumentSinkSummary;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.OpenNlpDocumentSinkServiceGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the gRPC sink against an in-process fake receiver implementing the
 * OpenNlpDocumentSinkService contract exactly as a Python or Go receiver would.
 */
class GrpcDocumentSinkTest {

  /** Fake downstream receiver collecting every streamed item. */
  private static final class FakeReceiver
      extends OpenNlpDocumentSinkServiceGrpc.OpenNlpDocumentSinkServiceImplBase {

    private final List<DocumentSinkItem> items = new ArrayList<>();

    @Override
    public StreamObserver<DocumentSinkItem> streamDocuments(
        StreamObserver<DocumentSinkSummary> responseObserver) {
      return new StreamObserver<>() {
        @Override
        public void onNext(DocumentSinkItem value) {
          synchronized (items) {
            items.add(value);
          }
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onCompleted() {
          responseObserver.onNext(DocumentSinkSummary.newBuilder()
              .setAcceptedDocuments(items.size())
              .setReceiver("fake-receiver")
              .build());
          responseObserver.onCompleted();
        }
      };
    }
  }

  private Server server;
  private ManagedChannel channel;

  @AfterEach
  void stopReceiver() throws InterruptedException {
    if (channel != null) {
      channel.shutdownNow();
      channel.awaitTermination(5, TimeUnit.SECONDS);
    }
    if (server != null) {
      server.shutdownNow();
      server.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  /** Starts an in-process receiver and returns a channel to it. */
  private FakeReceiver startReceiver() throws IOException {
    final FakeReceiver receiver = new FakeReceiver();
    final String name = InProcessServerBuilder.generateName();
    server = InProcessServerBuilder.forName(name)
        .directExecutor().addService(receiver).build().start();
    channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    return receiver;
  }

  private static OpenNlpDocument document(String docId) {
    return OpenNlpDocument.newBuilder().setDocId(docId).setRawText("Alpha.").build();
  }

  @Test
  void streamsEveryAcceptedDocumentAndClosesWithTheSummary() throws IOException {
    final FakeReceiver receiver = startReceiver();
    final DocumentSink sink = new GrpcDocumentSink("down", channel, null, null);

    sink.accept(document("doc-1"));
    sink.accept(document("doc-2"));
    sink.close();

    assertEquals(2, receiver.items.size());
    assertEquals("doc-1", receiver.items.getFirst().getDocument().getDocId());
    assertTrue(receiver.items.getFirst().getFormatId().isEmpty());
  }

  @Test
  void attachesTheConfiguredFormattersRenderingToEveryItem() throws IOException {
    final FakeReceiver receiver = startReceiver();
    final DocumentSink sink = new GrpcDocumentSink("down", channel, null,
        new org.apache.opennlp.grpc.format.TsvDocumentFormatter());

    sink.accept(document("doc-1"));
    sink.close();

    final DocumentSinkItem item = receiver.items.getFirst();
    assertEquals("tsv", item.getFormatId());
    assertEquals("text/tab-separated-values", item.getMediaType());
    assertTrue(item.getRendered().toStringUtf8().startsWith("sentence\ttoken"));
  }

  @Test
  void acceptAfterCloseFailsLoud() throws IOException {
    startReceiver();
    final DocumentSink sink = new GrpcDocumentSink("down", channel, null, null);
    sink.close();

    assertThrows(IOException.class, () -> sink.accept(document("late")));
  }

  @Test
  void providerRejectsUnknownOptionsMissingTargetsAndUnknownFormats() {
    final GrpcDocumentSinkProvider provider = new GrpcDocumentSinkProvider();

    assertThrows(IllegalArgumentException.class,
        () -> provider.open("down", Map.of("tls", "true", "target", "localhost:9")));
    assertThrows(IllegalArgumentException.class, () -> provider.open("down", Map.of()));
    final IllegalArgumentException unknownFormat = assertThrows(IllegalArgumentException.class,
        () -> provider.open("down", Map.of("target", "localhost:9", "format", "yaml")));
    assertTrue(unknownFormat.getMessage().contains("tsv"),
        "the error must list deployed formats: " + unknownFormat.getMessage());
  }

  @Test
  void registersThroughTheSinkSpiAndTheServerRegistry() throws IOException {
    assertTrue(ServiceLoader.load(DocumentSinkProvider.class).stream()
        .anyMatch(provider -> provider.type() == GrpcDocumentSinkProvider.class));
    try (DocumentSinkRegistry registry = DocumentSinkRegistry.fromConfiguration(
        Map.of("sink.down.provider", "grpc", "sink.down.target", "localhost:1"))) {
      assertTrue(!registry.isEmpty());
    }
  }
}
