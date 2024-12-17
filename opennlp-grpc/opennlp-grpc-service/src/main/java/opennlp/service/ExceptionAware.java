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

package opennlp.service;

import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;

/**
 * The {@code ExceptionAware} interface provides a mechanism for handling exceptions
 * and returning appropriate gRPC error responses to clients.
 *
 * <p>This interface defines a default method, {@code handleException}, that standardizes
 * the handling of exceptions by converting them into a gRPC-compatible {@link io.grpc.StatusRuntimeException}.
 * This ensures that clients receive a structured error response with an appropriate status code
 * and message.</p>
 *
 * <p><b>Purpose:</b> By implementing this interface, services can uniformly handle errors
 * and communicate meaningful error information to clients using gRPC's error model.</p>
 *
 * @see io.grpc.stub.StreamObserver
 * @see com.google.rpc.Status
 * @see io.grpc.protobuf.StatusProto
 */
public interface ExceptionAware {

  /**
   * Handles exceptions and sends a structured error response to the client using gRPC.
   *
   * <p>This method converts the provided exception into a {@link com.google.rpc.Status} object
   * with the following properties:</p>
   * <ul>
   *   <li>{@link Code#INTERNAL} - The status code representing an internal server error.</li>
   *   <li>The exception's localized message as the error description.</li>
   * </ul>
   *
   * @param e                The exception to be handled. Its message will be included in the error response.
   * @param responseObserver The gRPC {@link StreamObserver} used to send the error response back to the client.
   */
  default void handleException(Exception e, StreamObserver<?> responseObserver) {
    final Status status = Status.newBuilder()
        .setCode(Code.INTERNAL.getNumber())
        .setMessage(e.getLocalizedMessage() == null ? "" : e.getLocalizedMessage())
        .build();
    responseObserver.onError(StatusProto.toStatusRuntimeException(status));
  }
}
