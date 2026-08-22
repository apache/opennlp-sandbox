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

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * Sends response headers as soon as a call is accepted. This prevents a
 * bidirectional stream deadlock when a client waits for response headers before
 * submitting its first request frame.
 */
final class EagerHeadersInterceptor implements ServerInterceptor {

  /** {@inheritDoc} */
  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call,
      Metadata headers,
      ServerCallHandler<ReqT, RespT> next) {
    final ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> wrapped =
        new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
          private boolean sent;

          /** {@inheritDoc} */
          @Override
          public void sendHeaders(Metadata responseHeaders) {
            if (!sent) {
              sent = true;
              super.sendHeaders(responseHeaders);
            }
          }
        };
    final ServerCall.Listener<ReqT> listener = next.startCall(wrapped, headers);
    wrapped.sendHeaders(new Metadata());
    return listener;
  }
}
