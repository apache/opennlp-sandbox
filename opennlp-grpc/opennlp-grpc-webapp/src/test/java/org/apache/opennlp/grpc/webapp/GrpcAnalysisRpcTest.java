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
      observer.onNext(AnalyzeDocumentResponse.newBuilder()
          .setDocument(request.getDocument())
          .build());
      observer.onCompleted();
    }
  }
}
