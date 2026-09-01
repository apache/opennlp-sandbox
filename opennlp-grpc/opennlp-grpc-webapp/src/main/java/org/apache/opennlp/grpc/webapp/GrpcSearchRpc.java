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
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import io.grpc.Channel;
import org.apache.opennlp.grpc.v1.CollectionEvent;
import org.apache.opennlp.grpc.v1.DeleteCollectionRequest;
import org.apache.opennlp.grpc.v1.DeleteCollectionResponse;
import org.apache.opennlp.grpc.v1.DeleteIndexAliasRequest;
import org.apache.opennlp.grpc.v1.DeleteIndexAliasResponse;
import org.apache.opennlp.grpc.v1.GetCollectionRequest;
import org.apache.opennlp.grpc.v1.GetCollectionResponse;
import org.apache.opennlp.grpc.v1.ListCollectionsRequest;
import org.apache.opennlp.grpc.v1.ListCollectionsResponse;
import org.apache.opennlp.grpc.v1.SetCollectionRequest;
import org.apache.opennlp.grpc.v1.SetCollectionResponse;
import org.apache.opennlp.grpc.v1.WatchCollectionRequest;
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
  private final long ceilingNanos;
  private final long timeoutPerMebibyteNanos;

  /**
   * Creates a blocking gRPC search adapter whose deadlines never scale with input size.
   *
   * @param channel The channel to the OpenNLP service.
   * @param timeout The deadline applied to every call.
   * @throws IllegalArgumentException If an argument is {@code null} or the timeout is not positive.
   */
  GrpcSearchRpc(Channel channel, Duration timeout) {
    this(channel, timeout, timeout, Duration.ZERO);
  }

  /**
   * Creates a blocking gRPC search adapter.
   *
   * <p>Indexing carries a deadline of {@code timeout} plus {@code timeoutPerMebibyte} for
   * every mebibyte of documents it submits, never exceeding {@code ceiling}: indexing a
   * novel's chunks with embeddings costs at least as much as analyzing it, so it gets the
   * same allowance analysis does. Every other call keeps the base deadline.</p>
   *
   * @param channel The channel to the OpenNLP service.
   * @param timeout The base deadline applied to every call.
   * @param ceiling The largest deadline an indexing call is ever granted.
   * @param timeoutPerMebibyte The extra indexing deadline per mebibyte of submitted documents;
   *     zero disables scaling.
   * @throws IllegalArgumentException If an argument is {@code null}, a timeout is not
   *     positive, or the per-mebibyte allowance is negative.
   */
  GrpcSearchRpc(Channel channel, Duration timeout, Duration ceiling, Duration timeoutPerMebibyte) {
    if (channel == null) {
      throw new IllegalArgumentException("channel must not be null");
    }
    if (timeout == null) {
      throw new IllegalArgumentException("timeout must not be null");
    }
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    if (ceiling == null) {
      throw new IllegalArgumentException("ceiling must not be null");
    }
    if (ceiling.isZero() || ceiling.isNegative()) {
      throw new IllegalArgumentException("ceiling must be positive");
    }
    if (timeoutPerMebibyte == null) {
      throw new IllegalArgumentException("timeoutPerMebibyte must not be null");
    }
    if (timeoutPerMebibyte.isNegative()) {
      throw new IllegalArgumentException("timeoutPerMebibyte must not be negative");
    }
    this.stub = OpenNlpSearchServiceGrpc.newBlockingStub(channel);
    this.timeoutNanos = timeout.toNanos();
    this.ceilingNanos = ceiling.toNanos();
    this.timeoutPerMebibyteNanos = timeoutPerMebibyte.toNanos();
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
    return stub.withDeadlineAfter(indexDeadlineNanos(request.getSerializedSize()),
        TimeUnit.NANOSECONDS).indexDocuments(request);
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

  /** {@inheritDoc} */
  @Override
  public SetCollectionResponse setCollection(SetCollectionRequest request) {
    return deadlineStub().setCollection(request);
  }

  /** {@inheritDoc} */
  @Override
  public GetCollectionResponse getCollection(GetCollectionRequest request) {
    return deadlineStub().getCollection(request);
  }

  /** {@inheritDoc} */
  @Override
  public ListCollectionsResponse listCollections() {
    return deadlineStub().listCollections(ListCollectionsRequest.getDefaultInstance());
  }

  /** {@inheritDoc} */
  @Override
  public DeleteCollectionResponse deleteCollection(DeleteCollectionRequest request) {
    return deadlineStub().deleteCollection(request);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The configured deadline bounds the gateway watch lifetime; the stream ends
   * with DEADLINE_EXCEEDED and a reconnect receives a fresh snapshot first.</p>
   */
  @Override
  public Iterator<CollectionEvent> watchCollection(WatchCollectionRequest request) {
    return deadlineStub().watchCollection(request);
  }

  /**
   * Computes the deadline an indexing call of the given size receives.
   *
   * @param inputBytes The serialized size of the indexing request.
   * @return The base deadline plus the per-mebibyte allowance, capped at the ceiling.
   */
  long indexDeadlineNanos(long inputBytes) {
    return GrpcAnalysisRpc.scaledDeadlineNanos(
        timeoutNanos, timeoutPerMebibyteNanos, ceilingNanos, inputBytes);
  }

  /** @return A stub carrying the configured deadline. */
  private OpenNlpSearchServiceGrpc.OpenNlpSearchServiceBlockingStub deadlineStub() {
    return stub.withDeadlineAfter(timeoutNanos, TimeUnit.NANOSECONDS);
  }
}
