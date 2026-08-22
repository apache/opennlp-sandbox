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

import io.grpc.Status;

final class GrpcHttpStatusMapper {

  /** Prevents instantiation. */
  private GrpcHttpStatusMapper() {
  }

  /**
   * Maps a gRPC status code to an HTTP status.
   *
   * @param code The gRPC status code.
   * @return The corresponding HTTP status.
   */
  static int toHttpStatus(Status.Code code) {
    return switch (code) {
      case OK -> 200;
      case INVALID_ARGUMENT, OUT_OF_RANGE -> 400;
      case UNAUTHENTICATED -> 401;
      case PERMISSION_DENIED -> 403;
      case NOT_FOUND -> 404;
      case ALREADY_EXISTS, ABORTED -> 409;
      case FAILED_PRECONDITION -> 412;
      case RESOURCE_EXHAUSTED -> 429;
      case CANCELLED -> 499;
      case UNIMPLEMENTED -> 501;
      case UNAVAILABLE -> 503;
      case DEADLINE_EXCEEDED -> 504;
      case UNKNOWN, INTERNAL, DATA_LOSS -> 500;
    };
  }
}
