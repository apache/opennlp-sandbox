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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.stub.ServerCallStreamObserver;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentEvent;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalysisStarted;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrpcAnalysisRpcTest {

  private static final String SAMPLE_TEXT = "Hello";
  private static final String RPC_DOCUMENT_ID = "rpc";
  private static final String PROGRESSIVE_DOCUMENT_ID = "progressive";
  private static final String BIG_DOCUMENT_ID = "big";

  @Test
  void fallbackPublishesItsStartEventBeforeRunningUnaryAnalysis() {
    final EmptyAnalysisRpc rpc = new EmptyAnalysisRpc();
    final Iterator<AnalyzeDocumentEvent> events = rpc.analyzeProgressively(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(SAMPLE_TEXT))
            .build());

    assertEquals(0, rpc.analysisCalls());
    assertEquals(AnalyzeDocumentEvent.UpdateCase.STARTED, events.next().getUpdateCase());
    assertEquals(0, rpc.analysisCalls());
    assertEquals(AnalyzeDocumentEvent.UpdateCase.COMPLETE, events.next().getUpdateCase());
    assertEquals(1, rpc.analysisCalls());
  }

  @Test
  void closingProgressiveEventsCancelsTheGrpcCall() throws Exception {
    final CountDownLatch cancelled = new CountDownLatch(1);
    final String name = InProcessServerBuilder.generateName();
    final Server server = InProcessServerBuilder.forName(name)
        .directExecutor()
        .addService(new OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceImplBase() {
          @Override
          public void analyzeDocumentProgressive(
              AnalyzeDocumentRequest request, StreamObserver<AnalyzeDocumentEvent> observer) {
            ((ServerCallStreamObserver<AnalyzeDocumentEvent>) observer)
                .setOnCancelHandler(cancelled::countDown);
            observer.onNext(AnalyzeDocumentEvent.newBuilder()
                .setSequence(1)
                .setStarted(AnalysisStarted.newBuilder().setDocument(request.getDocument()))
                .build());
          }
        })
        .build()
        .start();
    final ManagedChannel channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    try {
      final GrpcAnalysisRpc rpc = new GrpcAnalysisRpc(
          channel, Duration.ofSeconds(2), Duration.ofSeconds(30));
      final Iterator<AnalyzeDocumentEvent> events = rpc.analyzeProgressively(
          AnalyzeDocumentRequest.newBuilder()
              .setDocument(OpenNlpDocument.newBuilder().setRawText(SAMPLE_TEXT))
              .build());

      assertEquals(AnalyzeDocumentEvent.UpdateCase.STARTED, events.next().getUpdateCase());
      assertTrue(events instanceof AutoCloseable);
      ((AutoCloseable) events).close();
      assertTrue(cancelled.await(5, TimeUnit.SECONDS));
    } finally {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void deadlinesScaleWithInputSizeUnderTheLongRunningCeiling() {
    final long base = Duration.ofSeconds(30).toNanos();
    final long perMebibyte = Duration.ofSeconds(120).toNanos();
    final long ceiling = Duration.ofSeconds(1800).toNanos();

    // An empty input keeps the base deadline; negative sizes count as empty.
    assertEquals(base, GrpcAnalysisRpc.scaledDeadlineNanos(base, perMebibyte, ceiling, 0));
    assertEquals(base, GrpcAnalysisRpc.scaledDeadlineNanos(base, perMebibyte, ceiling, -5));
    // Pride and Prejudice (694 478 bytes) earns about 79 extra seconds.
    final long novel = GrpcAnalysisRpc.scaledDeadlineNanos(base, perMebibyte, ceiling, 694_478);
    assertEquals(109, Duration.ofNanos(novel).toSeconds());
    // Scaling never exceeds the long-running ceiling.
    assertEquals(ceiling, GrpcAnalysisRpc.scaledDeadlineNanos(
        base, perMebibyte, ceiling, 100L * 1024 * 1024));
    // Zero allowance disables scaling entirely.
    assertEquals(base, GrpcAnalysisRpc.scaledDeadlineNanos(base, 0, ceiling, 694_478));
  }

  @Test
  void rejectsANegativePerMebibyteAllowance() {
    final ManagedChannel channel = InProcessChannelBuilder.forName("unused").build();
    try {
      assertThrows(IllegalArgumentException.class, () -> new GrpcAnalysisRpc(
          channel, Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(-1)));
    } finally {
      channel.shutdownNow();
    }
  }

  @Test
  void delegatesAllUnaryGatewayCalls() throws Exception {
    String name = InProcessServerBuilder.generateName();
    Server server = InProcessServerBuilder.forName(name)
        .directExecutor()
        .addService(new TestAnalysisService())
        .build()
        .start();
    ManagedChannel channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    try {
      GrpcAnalysisRpc rpc = new GrpcAnalysisRpc(channel, Duration.ofSeconds(2), Duration.ofSeconds(30));

      assertEquals("v1", rpc.getServiceInfo().getApiVersion());
      assertEquals(0, rpc.listModelBundles().getBundlesCount());
      assertEquals(RPC_DOCUMENT_ID, rpc.analyze(AnalyzeDocumentRequest.newBuilder()
          .setDocument(org.apache.opennlp.grpc.v1.OpenNlpDocument.newBuilder()
              .setDocId(RPC_DOCUMENT_ID)
              .setRawText(SAMPLE_TEXT))
          .build()).getDocument().getDocId());
      final Iterator<AnalyzeDocumentEvent> progressive = rpc.analyzeProgressively(
          AnalyzeDocumentRequest.newBuilder()
              .setDocument(OpenNlpDocument.newBuilder()
                  .setDocId(PROGRESSIVE_DOCUMENT_ID)
                  .setRawText(SAMPLE_TEXT))
              .build());
      assertEquals(AnalyzeDocumentEvent.UpdateCase.STARTED,
          progressive.next().getUpdateCase());
      assertEquals(PROGRESSIVE_DOCUMENT_ID,
          progressive.next().getComplete().getDocument().getDocId());
    } finally {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  /** Six MiB of response text, past the 4 MiB gRPC default inbound limit. */
  private static final String LARGE_RESPONSE_TEXT = "x".repeat(6 * 1024 * 1024);

  @Test
  void acceptsResponsesBeyondTheGrpcDefaultMessageLimit() throws Exception {
    // Whole-document analysis with embeddings can exceed the 4 MiB gRPC default;
    // the channel the application builds must accept such responses.
    Server server = ServerBuilder.forPort(0)
        .directExecutor()
        .addService(new TestAnalysisService())
        .build()
        .start();
    ManagedChannel channel = OpenNlpGrpcWebApp.newChannel(
        "127.0.0.1:" + server.getPort(), true,
        OpenNlpGrpcWebApp.DEFAULT_GRPC_MAX_INBOUND_MESSAGE_BYTES);
    try {
      GrpcAnalysisRpc rpc = new GrpcAnalysisRpc(channel, Duration.ofSeconds(30), Duration.ofSeconds(60));

      AnalyzeDocumentResponse response = rpc.analyze(AnalyzeDocumentRequest.newBuilder()
          .setDocument(OpenNlpDocument.newBuilder()
              .setDocId(BIG_DOCUMENT_ID)
              .setRawText("pad"))
          .build());

      assertEquals(LARGE_RESPONSE_TEXT.length(), response.getDocument().getRawText().length());
    } finally {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private static final class TestAnalysisService
      extends OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceImplBase {

    @Override
    public void getServiceInfo(
        GetServiceInfoRequest request, StreamObserver<GetServiceInfoResponse> observer) {
      observer.onNext(GetServiceInfoResponse.newBuilder().setApiVersion("v1").build());
      observer.onCompleted();
    }

    @Override
    public void listModelBundles(
        ListModelBundlesRequest request, StreamObserver<ListModelBundlesResponse> observer) {
      observer.onNext(ListModelBundlesResponse.getDefaultInstance());
      observer.onCompleted();
    }

    @Override
    public void analyzeDocument(
        AnalyzeDocumentRequest request, StreamObserver<AnalyzeDocumentResponse> observer) {
      OpenNlpDocument document = request.getDocument();
      if (BIG_DOCUMENT_ID.equals(document.getDocId())) {
        document = document.toBuilder().setRawText(LARGE_RESPONSE_TEXT).build();
      }
      observer.onNext(AnalyzeDocumentResponse.newBuilder()
          .setDocument(document)
          .build());
      observer.onCompleted();
    }

    @Override
    public void analyzeDocumentProgressive(
        AnalyzeDocumentRequest request, StreamObserver<AnalyzeDocumentEvent> observer) {
      observer.onNext(AnalyzeDocumentEvent.newBuilder()
          .setSequence(1)
          .setStarted(AnalysisStarted.newBuilder().setDocument(request.getDocument()))
          .build());
      observer.onNext(AnalyzeDocumentEvent.newBuilder()
          .setSequence(2)
          .setComplete(AnalyzeDocumentResponse.newBuilder().setDocument(request.getDocument()))
          .build());
      observer.onCompleted();
    }
  }
}
