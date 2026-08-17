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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.junit.jupiter.api.Test;

class GrpcAnalysisRpcTest {

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
      GrpcAnalysisRpc rpc = new GrpcAnalysisRpc(channel, Duration.ofSeconds(2));

      assertEquals("v1", rpc.getServiceInfo().getApiVersion());
      assertEquals(0, rpc.listModelBundles().getBundlesCount());
      assertEquals("rpc", rpc.analyze(AnalyzeDocumentRequest.newBuilder()
          .setDocument(org.apache.opennlp.grpc.v1.OpenNlpDocument.newBuilder()
              .setDocId("rpc")
              .setRawText("Hello"))
          .build()).getDocument().getDocId());
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
      GrpcAnalysisRpc rpc = new GrpcAnalysisRpc(channel, Duration.ofSeconds(30));

      AnalyzeDocumentResponse response = rpc.analyze(AnalyzeDocumentRequest.newBuilder()
          .setDocument(OpenNlpDocument.newBuilder()
              .setDocId("big")
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
      if ("big".equals(document.getDocId())) {
        document = document.toBuilder().setRawText(LARGE_RESPONSE_TEXT).build();
      }
      observer.onNext(AnalyzeDocumentResponse.newBuilder()
          .setDocument(document)
          .build());
      observer.onCompleted();
    }
  }
}
