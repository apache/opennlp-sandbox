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
import org.apache.opennlp.grpc.v1.DeleteIndexAliasRequest;
import org.apache.opennlp.grpc.v1.DeleteIndexAliasResponse;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexRequest;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexResponse;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.ListIndexAliasesRequest;
import org.apache.opennlp.grpc.v1.ListIndexAliasesResponse;
import org.apache.opennlp.grpc.v1.ListSearchIndexesRequest;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.ListSearchProvidersRequest;
import org.apache.opennlp.grpc.v1.ListSearchProvidersResponse;
import org.apache.opennlp.grpc.v1.OpenNlpSearchServiceGrpc;
import org.apache.opennlp.grpc.v1.PersistIndexRequest;
import org.apache.opennlp.grpc.v1.PersistIndexResponse;
import org.apache.opennlp.grpc.v1.ReindexIndexRequest;
import org.apache.opennlp.grpc.v1.ReindexIndexResponse;
import org.apache.opennlp.grpc.v1.SealIndexRequest;
import org.apache.opennlp.grpc.v1.SealIndexResponse;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;
import org.apache.opennlp.grpc.v1.SetIndexAliasRequest;
import org.apache.opennlp.grpc.v1.SetIndexAliasResponse;

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
  public ListSearchProvidersResponse listSearchProviders() {
    return deadlineStub().listSearchProviders(ListSearchProvidersRequest.getDefaultInstance());
  }

  /** {@inheritDoc} */
  @Override
  public SearchIndexResponse search(SearchIndexRequest request) {
    return deadlineStub().searchIndex(request);
  }

  /** {@inheritDoc} */
  @Override
  public IndexDocumentsResponse index(IndexDocumentsRequest request) {
    return deadlineStub().indexDocuments(request);
  }

  /** {@inheritDoc} */
  @Override
  public DeleteSearchIndexResponse delete(DeleteSearchIndexRequest request) {
    return deadlineStub().deleteSearchIndex(request);
  }

  /** {@inheritDoc} */
  @Override
  public PersistIndexResponse persist(PersistIndexRequest request) {
    return deadlineStub().persistIndex(request);
  }

  /** {@inheritDoc} */
  @Override
  public SealIndexResponse seal(SealIndexRequest request) {
    return deadlineStub().sealIndex(request);
  }

  /** {@inheritDoc} */
  @Override
  public ReindexIndexResponse reindex(ReindexIndexRequest request) {
    return deadlineStub().reindexIndex(request);
  }

  /** {@inheritDoc} */
  @Override
  public SetIndexAliasResponse setAlias(SetIndexAliasRequest request) {
    return deadlineStub().setIndexAlias(request);
  }

  /** {@inheritDoc} */
  @Override
  public DeleteIndexAliasResponse deleteAlias(DeleteIndexAliasRequest request) {
    return deadlineStub().deleteIndexAlias(request);
  }

  /** {@inheritDoc} */
  @Override
  public ListIndexAliasesResponse listAliases() {
    return deadlineStub().listIndexAliases(ListIndexAliasesRequest.getDefaultInstance());
  }

  /** @return A stub carrying the configured deadline. */
  private OpenNlpSearchServiceGrpc.OpenNlpSearchServiceBlockingStub deadlineStub() {
    return stub.withDeadlineAfter(timeoutNanos, TimeUnit.NANOSECONDS);
  }
}
