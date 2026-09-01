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
package org.apache.opennlp.grpc.v1.server;

import io.grpc.Status;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.GrpcStatusCode;

/**
 * Maps domain failures to canonical gRPC {@link Status} codes.
 *
 * @see <a href="https://grpc.io/docs/guides/status-codes/">gRPC status codes</a>
 */
public final class GrpcStatusMapper {

  private GrpcStatusMapper() {
  }

  /**
   * Maps an {@link AnalysisException} to the gRPC {@link Status} its failure type denotes.
   *
   * @param exception The analysis failure to map. Must not be {@code null}.
   *
   * @return The gRPC status corresponding to the exception's
   *     {@link AnalysisException.FailureType}.
   * @throws IllegalArgumentException If {@code exception} is {@code null}.
   */
  public static Status toStatus(AnalysisException exception) {
    if (exception == null) {
      throw new IllegalArgumentException("exception must not be null");
    }
    return switch (exception.getFailureType()) {
      case INVALID_ARGUMENT -> Status.INVALID_ARGUMENT;
      case NOT_FOUND -> Status.NOT_FOUND;
      case FAILED_PRECONDITION -> Status.FAILED_PRECONDITION;
      case UNIMPLEMENTED -> Status.UNIMPLEMENTED;
      case UNAVAILABLE -> Status.UNAVAILABLE;
      case RESOURCE_EXHAUSTED -> Status.RESOURCE_EXHAUSTED;
      case INTERNAL -> Status.INTERNAL;
    };
  }

  /**
   * Converts a non-OK transport status to its typed document-error representation.
   *
   * @param status The gRPC status to convert. Must not be {@code null} or {@code OK}.
   *
   * @return The corresponding typed wire status.
   * @throws IllegalArgumentException If {@code status} is {@code null} or has code {@code OK}.
   */
  public static GrpcStatusCode toWireCode(Status status) {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    return switch (status.getCode()) {
      case OK -> throw new IllegalArgumentException(
          "gRPC status code OK cannot represent an AnalyzeStreamError");
      case CANCELLED -> GrpcStatusCode.GRPC_STATUS_CODE_CANCELLED;
      case UNKNOWN -> GrpcStatusCode.GRPC_STATUS_CODE_UNKNOWN;
      case INVALID_ARGUMENT -> GrpcStatusCode.GRPC_STATUS_CODE_INVALID_ARGUMENT;
      case DEADLINE_EXCEEDED -> GrpcStatusCode.GRPC_STATUS_CODE_DEADLINE_EXCEEDED;
      case NOT_FOUND -> GrpcStatusCode.GRPC_STATUS_CODE_NOT_FOUND;
      case ALREADY_EXISTS -> GrpcStatusCode.GRPC_STATUS_CODE_ALREADY_EXISTS;
      case PERMISSION_DENIED -> GrpcStatusCode.GRPC_STATUS_CODE_PERMISSION_DENIED;
      case RESOURCE_EXHAUSTED -> GrpcStatusCode.GRPC_STATUS_CODE_RESOURCE_EXHAUSTED;
      case FAILED_PRECONDITION -> GrpcStatusCode.GRPC_STATUS_CODE_FAILED_PRECONDITION;
      case ABORTED -> GrpcStatusCode.GRPC_STATUS_CODE_ABORTED;
      case OUT_OF_RANGE -> GrpcStatusCode.GRPC_STATUS_CODE_OUT_OF_RANGE;
      case UNIMPLEMENTED -> GrpcStatusCode.GRPC_STATUS_CODE_UNIMPLEMENTED;
      case INTERNAL -> GrpcStatusCode.GRPC_STATUS_CODE_INTERNAL;
      case UNAVAILABLE -> GrpcStatusCode.GRPC_STATUS_CODE_UNAVAILABLE;
      case DATA_LOSS -> GrpcStatusCode.GRPC_STATUS_CODE_DATA_LOSS;
      case UNAUTHENTICATED -> GrpcStatusCode.GRPC_STATUS_CODE_UNAUTHENTICATED;
    };
  }
}
