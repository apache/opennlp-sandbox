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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.v1.ListSearchIndexesRequest;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.OpenNlpSearchServiceGrpc;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;
import org.junit.jupiter.api.Test;

class GrpcSearchRpcTest {

  private static final String INDEX_ID = "legal-demo";
  private static final String QUERY_ID = "query-1";

  @Test
  void delegatesBothUnarySearchCalls() throws Exception {
    String name = InProcessServerBuilder.generateName();
    Server server = InProcessServerBuilder.forName(name)
        .directExecutor()
        .addService(new TestSearchService())
        .build()
        .start();
    ManagedChannel channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    try {
      GrpcSearchRpc rpc = new GrpcSearchRpc(channel, Duration.ofSeconds(2));

      assertEquals(INDEX_ID, rpc.listSearchIndexes().getIndexes(0).getIndexId());
      assertEquals(QUERY_ID, rpc.search(SearchIndexRequest.newBuilder()
          .setIndexId(INDEX_ID)
          .setQuery(OpenNlpDocument.newBuilder().setDocId(QUERY_ID).setRawText("writ"))
          .setTopK(3)
          .build()).getHits(0).getDocumentId());
    } finally {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private static final class TestSearchService
      extends OpenNlpSearchServiceGrpc.OpenNlpSearchServiceImplBase {

    @Override
    public void listSearchIndexes(
        ListSearchIndexesRequest request,
        StreamObserver<ListSearchIndexesResponse> observer) {
      observer.onNext(ListSearchIndexesResponse.newBuilder()
          .addIndexes(SearchIndexDescriptor.newBuilder().setIndexId(INDEX_ID))
          .build());
      observer.onCompleted();
    }

    @Override
    public void searchIndex(
        SearchIndexRequest request,
        StreamObserver<SearchIndexResponse> observer) {
      observer.onNext(SearchIndexResponse.newBuilder()
          .addHits(org.apache.opennlp.grpc.v1.SearchHit.newBuilder()
              .setDocumentId(request.getQuery().getDocId()))
          .build());
      observer.onCompleted();
    }
  }
}
