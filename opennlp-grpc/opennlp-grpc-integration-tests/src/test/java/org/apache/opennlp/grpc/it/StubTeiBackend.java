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
package org.apache.opennlp.grpc.it;

import java.io.IOException;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.tei.v1.EmbedGrpc;
import org.apache.opennlp.grpc.tei.v1.EmbedRequest;
import org.apache.opennlp.grpc.tei.v1.EmbedResponse;
import org.apache.opennlp.grpc.tei.v1.InfoGrpc;
import org.apache.opennlp.grpc.tei.v1.InfoRequest;
import org.apache.opennlp.grpc.tei.v1.InfoResponse;
import org.apache.opennlp.grpc.tei.v1.ModelType;

/**
 * In-process stub TEI embedding backend shared by the live tests. Every input
 * embeds deterministically to {@code [length(inputs), 1, 1]}.
 */
final class StubTeiBackend {

  /** Vector components every stub embedding carries. */
  static final int EMBEDDING_DIMENSION = 3;

  private StubTeiBackend() {
  }

  /**
   * Starts a stub TEI gRPC server on an ephemeral port.
   *
   * @return The started server. Callers own it and must shut it down.
   * @throws IOException If binding fails.
   */
  static Server start() throws IOException {
    return ServerBuilder.forPort(0)
        .addService(new StubTeiInfoService())
        .addService(new StubTeiEmbedService())
        .build()
        .start();
  }

  /** TEI Info stub reporting an embedding model. */
  private static final class StubTeiInfoService extends InfoGrpc.InfoImplBase {
    @Override
    public void info(InfoRequest request, StreamObserver<InfoResponse> observer) {
      observer.onNext(InfoResponse.newBuilder()
          .setVersion("live-it")
          .setModelId("stub/live-model")
          .setModelDtype("float32")
          .setModelType(ModelType.MODEL_TYPE_EMBEDDING)
          .build());
      observer.onCompleted();
    }
  }

  /** TEI Embed stub returning {@code [length(inputs), 1, 1]} for every request. */
  private static final class StubTeiEmbedService extends EmbedGrpc.EmbedImplBase {
    private static EmbedResponse embedding(EmbedRequest request) {
      return EmbedResponse.newBuilder()
          .addEmbeddings(request.getInputs().length())
          .addEmbeddings(1f)
          .addEmbeddings(1f)
          .build();
    }

    @Override
    public void embed(EmbedRequest request, StreamObserver<EmbedResponse> observer) {
      observer.onNext(embedding(request));
      observer.onCompleted();
    }

    @Override
    public StreamObserver<EmbedRequest> embedStream(StreamObserver<EmbedResponse> observer) {
      // The provider batches via the bidi EmbedStream RPC; echo one response per request.
      return new StreamObserver<>() {
        @Override
        public void onNext(EmbedRequest request) {
          observer.onNext(embedding(request));
        }

        @Override
        public void onError(Throwable t) {
          observer.onError(t);
        }

        @Override
        public void onCompleted() {
          observer.onCompleted();
        }
      };
    }
  }
}
