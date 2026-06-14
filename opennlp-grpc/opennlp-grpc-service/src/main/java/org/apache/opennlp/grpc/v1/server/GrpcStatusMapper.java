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
import org.apache.opennlp.grpc.processor.AnalysisException;

/**
 * Maps domain failures to canonical gRPC {@link Status} codes.
 *
 * @see <a href="https://grpc.io/docs/guides/status-codes/">gRPC status codes</a>
 */
public final class GrpcStatusMapper {

  private GrpcStatusMapper() {
  }

  public static Status toStatus(AnalysisException exception) {
    return switch (exception.getFailureType()) {
      case INVALID_ARGUMENT -> Status.INVALID_ARGUMENT;
      case NOT_FOUND -> Status.NOT_FOUND;
      case FAILED_PRECONDITION -> Status.FAILED_PRECONDITION;
      case UNIMPLEMENTED -> Status.UNIMPLEMENTED;
      case UNAVAILABLE -> Status.UNAVAILABLE;
      case INTERNAL -> Status.INTERNAL;
    };
  }
}
