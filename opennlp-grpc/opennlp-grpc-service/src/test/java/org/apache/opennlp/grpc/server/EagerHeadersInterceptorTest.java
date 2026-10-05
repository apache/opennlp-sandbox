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
package org.apache.opennlp.grpc.server;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.Status;
import org.apache.opennlp.grpc.v1.AnalyzeStreamRequest;
import org.apache.opennlp.grpc.v1.AnalyzeStreamResponse;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests immediate response headers for bidirectional stream compatibility. */
class EagerHeadersInterceptorTest {

  @Test
  void sendsHeadersAsSoonAsTheHandlerAcceptsTheCall() {
    final TrackingCall call = new TrackingCall();

    new EagerHeadersInterceptor().interceptCall(
        call,
        new Metadata(),
        (acceptedCall, headers) -> new ServerCall.Listener<>() { });

    assertEquals(1, call.headersSent);
  }

  @Test
  void suppressesTheHandlersLaterDuplicateHeaders() {
    final TrackingCall call = new TrackingCall();

    new EagerHeadersInterceptor().interceptCall(
        call,
        new Metadata(),
        (acceptedCall, headers) -> {
          acceptedCall.sendHeaders(new Metadata());
          return new ServerCall.Listener<>() { };
        });

    assertEquals(1, call.headersSent);
  }

  /** Minimal server call that counts header writes. */
  private static final class TrackingCall
      extends ServerCall<AnalyzeStreamRequest, AnalyzeStreamResponse> {
    private int headersSent;

    @Override
    public void request(int numMessages) {
      // Inbound demand is outside this interceptor test's scope.
    }

    @Override
    public void sendHeaders(Metadata headers) {
      headersSent++;
    }

    @Override
    public void sendMessage(AnalyzeStreamResponse message) {
      // Response messages are outside this interceptor test's scope.
    }

    @Override
    public void close(Status status, Metadata trailers) {
      // Call closure is outside this interceptor test's scope.
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public MethodDescriptor<AnalyzeStreamRequest, AnalyzeStreamResponse> getMethodDescriptor() {
      return OpenNlpAnalysisServiceGrpc.getAnalyzeStreamMethod();
    }
  }
}
