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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.grpc.Channel;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;

final class GrpcAnalysisRpc implements AnalysisRpc {

  private final OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub stub;
  private final long timeoutNanos;

  /**
   * Creates a blocking gRPC adapter.
   *
   * @param channel The channel to the OpenNLP service.
   * @param timeout The deadline applied to every call.
   * @throws IllegalArgumentException If an argument is {@code null} or the timeout is not positive.
   */
  GrpcAnalysisRpc(Channel channel, Duration timeout) {
    if (channel == null) {
      throw new IllegalArgumentException("channel must not be null");
    }
    if (timeout == null) {
      throw new IllegalArgumentException("timeout must not be null");
    }
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    this.stub = OpenNlpAnalysisServiceGrpc.newBlockingStub(channel);
    this.timeoutNanos = timeout.toNanos();
  }

  /** {@inheritDoc} */
  @Override
  public GetServiceInfoResponse getServiceInfo() {
    return deadlineStub().getServiceInfo(GetServiceInfoRequest.getDefaultInstance());
  }

  /** {@inheritDoc} */
  @Override
  public ListModelBundlesResponse listModelBundles() {
    return deadlineStub().listModelBundles(ListModelBundlesRequest.getDefaultInstance());
  }

  /** {@inheritDoc} */
  @Override
  public AnalyzeDocumentResponse analyze(AnalyzeDocumentRequest request) {
    return deadlineStub().analyzeDocument(request);
  }

  /** @return A stub carrying the configured deadline. */
  private OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub deadlineStub() {
    return stub.withDeadlineAfter(timeoutNanos, TimeUnit.NANOSECONDS);
  }
}
