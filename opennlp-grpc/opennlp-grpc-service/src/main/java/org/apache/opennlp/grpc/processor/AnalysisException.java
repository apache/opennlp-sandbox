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

  /**
   * The category of failure, used by {@link org.apache.opennlp.grpc.v1.server.GrpcStatusMapper}
   * to select the gRPC status code reported to the client.
   */
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
    /** The caller exhausted a server-side limit (e.g. a streaming client that stops reading
     * responses, forcing the server to either buffer without bound or close the call). */
    RESOURCE_EXHAUSTED,
    /** Unexpected server-side failure while executing a required step. */
    INTERNAL
  }

  /** The failure category this exception carries. */
  private final FailureType failureType;

  private AnalysisException(FailureType failureType, String message) {
    super(message);
    this.failureType = failureType;
  }

  private AnalysisException(FailureType failureType, String message, Throwable cause) {
    super(message, cause);
    this.failureType = failureType;
  }

  /**
   * Returns the failure category this exception carries.
   *
   * @return The failure type, never {@code null}.
   */
  public FailureType getFailureType() {
    return failureType;
  }

  /**
   * Creates an exception for a client request that is malformed or semantically invalid.
   *
   * @param message The human-readable failure detail.
   *
   * @return A new exception with failure type {@link FailureType#INVALID_ARGUMENT}.
   */
  public static AnalysisException invalidArgument(String message) {
    return new AnalysisException(FailureType.INVALID_ARGUMENT, message);
  }

  /**
   * Creates an exception for a referenced profile, bundle, or model handle that does not exist.
   *
   * @param message The human-readable failure detail.
   *
   * @return A new exception with failure type {@link FailureType#NOT_FOUND}.
   */
  public static AnalysisException notFound(String message) {
    return new AnalysisException(FailureType.NOT_FOUND, message);
  }

  /**
   * Creates an exception for a request that cannot run in the current document or profile state.
   *
   * @param message The human-readable failure detail.
   *
   * @return A new exception with failure type {@link FailureType#FAILED_PRECONDITION}.
   */
  public static AnalysisException failedPrecondition(String message) {
    return new AnalysisException(FailureType.FAILED_PRECONDITION, message);
  }

  /**
   * Creates an exception for a capability that is not implemented on this server.
   *
   * @param message The human-readable failure detail.
   *
   * @return A new exception with failure type {@link FailureType#UNIMPLEMENTED}.
   */
  public static AnalysisException unimplemented(String message) {
    return new AnalysisException(FailureType.UNIMPLEMENTED, message);
  }

  /**
   * Creates an exception for an unreachable or timed-out upstream dependency. The request
   * may succeed on retry.
   *
   * @param message The human-readable failure detail.
   * @param cause   The underlying connectivity or timeout failure.
   *
   * @return A new exception with failure type {@link FailureType#UNAVAILABLE}.
   */
  public static AnalysisException unavailable(String message, Throwable cause) {
    return new AnalysisException(FailureType.UNAVAILABLE, message, cause);
  }

  /**
   * Creates an exception for a caller that exhausted a server-side limit, such as a streaming
   * client that stops draining responses.
   *
   * @param message The human-readable failure detail.
   *
   * @return A new exception with failure type {@link FailureType#RESOURCE_EXHAUSTED}.
   */
  public static AnalysisException resourceExhausted(String message) {
    return new AnalysisException(FailureType.RESOURCE_EXHAUSTED, message);
  }

  /**
   * Creates an exception for an unexpected server-side failure while executing a step.
   *
   * @param message The human-readable failure detail.
   * @param cause   The underlying failure.
   *
   * @return A new exception with failure type {@link FailureType#INTERNAL}.
   */
  public static AnalysisException internal(String message, Throwable cause) {
    return new AnalysisException(FailureType.INTERNAL, message, cause);
  }
}
