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
package org.apache.opennlp.grpc.webapp;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.grpc.Channel;
import org.apache.opennlp.grpc.v1.ListSearchIndexesRequest;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpSearchServiceGrpc;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;

final class GrpcSearchRpc implements SearchRpc {

  private final OpenNlpSearchServiceGrpc.OpenNlpSearchServiceBlockingStub stub;
  private final long timeoutNanos;

  /**
   * Creates a blocking gRPC search adapter.
   *
   * @param channel The channel to the OpenNLP service.
   * @param timeout The deadline applied to every call.
   * @throws IllegalArgumentException If an argument is {@code null} or the timeout is not positive.
   */
  GrpcSearchRpc(Channel channel, Duration timeout) {
    if (channel == null) {
      throw new IllegalArgumentException("channel must not be null");
    }
    if (timeout == null) {
      throw new IllegalArgumentException("timeout must not be null");
    }
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    this.stub = OpenNlpSearchServiceGrpc.newBlockingStub(channel);
    this.timeoutNanos = timeout.toNanos();
  }

  /** {@inheritDoc} */
  @Override
  public ListSearchIndexesResponse listSearchIndexes() {
    return deadlineStub().listSearchIndexes(ListSearchIndexesRequest.getDefaultInstance());
  }

  /** {@inheritDoc} */
  @Override
  public SearchIndexResponse search(SearchIndexRequest request) {
    return deadlineStub().searchIndex(request);
  }

  /** @return A stub carrying the configured deadline. */
  private OpenNlpSearchServiceGrpc.OpenNlpSearchServiceBlockingStub deadlineStub() {
    return stub.withDeadlineAfter(timeoutNanos, TimeUnit.NANOSECONDS);
  }
}
