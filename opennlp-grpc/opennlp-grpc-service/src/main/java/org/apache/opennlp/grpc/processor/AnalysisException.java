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
package org.apache.opennlp.grpc.processor;

/**
 * Fatal analysis failure mapped to a gRPC status by
 * {@link org.apache.opennlp.grpc.v1.server.GrpcStatusMapper}.
 */
public final class AnalysisException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public enum FailureType {
    /** Client supplied an invalid request. */
    INVALID_ARGUMENT,
    /** Referenced profile, bundle, or model handle does not exist. */
    NOT_FOUND,
    /** Request cannot be executed in the current document/profile state. */
    FAILED_PRECONDITION,
    /** Requested capability is not implemented on this server. */
    UNIMPLEMENTED,
    /** A required upstream/remote dependency (e.g. a remote embedding backend) is unreachable
     * or timed out; the request may succeed on retry. */
    UNAVAILABLE,
    /** Unexpected server-side failure while executing a required step. */
    INTERNAL
  }

  private final FailureType failureType;

  private AnalysisException(FailureType failureType, String message) {
    super(message);
    this.failureType = failureType;
  }

  private AnalysisException(FailureType failureType, String message, Throwable cause) {
    super(message, cause);
    this.failureType = failureType;
  }

  public FailureType getFailureType() {
    return failureType;
  }

  public static AnalysisException invalidArgument(String message) {
    return new AnalysisException(FailureType.INVALID_ARGUMENT, message);
  }

  public static AnalysisException notFound(String message) {
    return new AnalysisException(FailureType.NOT_FOUND, message);
  }

  public static AnalysisException failedPrecondition(String message) {
    return new AnalysisException(FailureType.FAILED_PRECONDITION, message);
  }

  public static AnalysisException unimplemented(String message) {
    return new AnalysisException(FailureType.UNIMPLEMENTED, message);
  }

  public static AnalysisException unavailable(String message, Throwable cause) {
    return new AnalysisException(FailureType.UNAVAILABLE, message, cause);
  }

  public static AnalysisException internal(String message, Throwable cause) {
    return new AnalysisException(FailureType.INTERNAL, message, cause);
  }
}
