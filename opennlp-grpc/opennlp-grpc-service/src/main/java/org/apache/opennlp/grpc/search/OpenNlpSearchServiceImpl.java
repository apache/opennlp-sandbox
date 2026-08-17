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
package org.apache.opennlp.grpc.search;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.embedding.EmbeddingBatchResult;
import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.apache.opennlp.grpc.v1.ListSearchIndexesRequest;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpSearchServiceGrpc;
import org.apache.opennlp.grpc.v1.SearchHit;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;
import org.apache.opennlp.grpc.v1.server.GrpcStatusMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** gRPC adapter for bounded read-only search over configured immutable indexes. */
public final class OpenNlpSearchServiceImpl
    extends OpenNlpSearchServiceGrpc.OpenNlpSearchServiceImplBase {

  private static final Logger logger = LoggerFactory.getLogger(OpenNlpSearchServiceImpl.class);
  private static final Comparator<SearchResult> STABLE_ORDER =
      Comparator.comparingDouble(SearchResult::score).reversed()
          .thenComparing(result -> result.record().chunkId())
          .thenComparing(result -> result.record().documentId());

  private final SearchIndexRegistry registry;
  private final EmbeddingProvider embeddingProvider;
  private final Map<String, String> queryBackendByIndex;

  /**
   * Creates a service and verifies every index against the available embedding vector spaces.
   *
   * @param registry Startup-loaded search registry.
   * @param embeddingProvider Query embedding provider.
   * @throws IllegalArgumentException If an index model, dimension, or vector space is unavailable.
   */
  public OpenNlpSearchServiceImpl(
      SearchIndexRegistry registry, EmbeddingProvider embeddingProvider) {
    if (registry == null) {
      throw new IllegalArgumentException("registry must not be null");
    }
    if (embeddingProvider == null) {
      throw new IllegalArgumentException("embeddingProvider must not be null");
    }
    this.registry = registry;
    this.embeddingProvider = embeddingProvider;
    final Map<String, String> selectedBackends = new TreeMap<>();
    for (SearchIndexDescriptor descriptor : registry.descriptors()) {
      final EmbeddingRoute selectedRoute = selectConfiguredRoute(descriptor, embeddingProvider);
      final int minimumResponseSize = SearchIndexResponse.newBuilder()
          .setIndex(descriptor)
          .setQueryEmbeddingRoute(selectedRoute)
          .setTruncated(true)
          .build()
          .getSerializedSize();
      if (minimumResponseSize > descriptor.getMaxResponseBytes()) {
        throw new IllegalArgumentException("Search index '" + descriptor.getIndexId()
            + "' max_response_bytes " + descriptor.getMaxResponseBytes()
            + " is smaller than its minimum response size " + minimumResponseSize);
      }
      selectedBackends.put(descriptor.getIndexId(), selectedRoute.getBackendId());
    }
    this.queryBackendByIndex = Map.copyOf(selectedBackends);
  }

  @Override
  public void listSearchIndexes(
      ListSearchIndexesRequest request,
      StreamObserver<ListSearchIndexesResponse> responseObserver) {
    responseObserver.onNext(ListSearchIndexesResponse.newBuilder()
        .addAllIndexes(registry.descriptors())
        .build());
    responseObserver.onCompleted();
  }

  @Override
  public void searchIndex(
      SearchIndexRequest request, StreamObserver<SearchIndexResponse> responseObserver) {
    try {
      final SearchIndexProvider provider = validateRequest(request);
      final SearchIndexDescriptor descriptor = provider.descriptor();
      final EmbeddingRoute configuredRoute = descriptor.getEmbeddingRoute();
      final EmbeddingBatchResult embedding = embeddingProvider.embedBatchResolved(
          configuredRoute.getModelId(), queryBackendByIndex.get(descriptor.getIndexId()),
          List.of(request.getQuery().getRawText()));
      validateResolvedRoute(descriptor, embedding);
      final float[] queryVector = embedding.vectors().getFirst();
      final SearchIndexResponse.Builder response = SearchIndexResponse.newBuilder()
          .setIndex(descriptor)
          .setQueryEmbeddingRoute(embedding.route());
      if (response.build().getSerializedSize() > descriptor.getMaxResponseBytes()) {
        throw AnalysisException.failedPrecondition("Search index '" + descriptor.getIndexId()
            + "' max_response_bytes " + descriptor.getMaxResponseBytes()
            + " cannot contain the resolved query embedding route");
      }
      final List<SearchResult> results = provider.search(queryVector, request.getTopK());
      if (results == null || results.stream().anyMatch(java.util.Objects::isNull)) {
        throw new IllegalStateException("Search provider returned null results");
      }
      if (results.size() > request.getTopK()) {
        throw new IllegalStateException("Search provider returned " + results.size()
            + " results for top_k " + request.getTopK());
      }
      final List<SearchHit> rankedHits = results.stream()
          .sorted(STABLE_ORDER)
          .limit(request.getTopK())
          .map(OpenNlpSearchServiceImpl::toHit)
          .toList();
      for (SearchHit hit : rankedHits) {
        response.addHits(hit);
        if (response.build().getSerializedSize() > descriptor.getMaxResponseBytes()) {
          response.removeHits(response.getHitsCount() - 1);
          response.setTruncated(true);
          while (response.build().getSerializedSize() > descriptor.getMaxResponseBytes()
              && response.getHitsCount() > 0) {
            response.removeHits(response.getHitsCount() - 1);
          }
          if (response.build().getSerializedSize() > descriptor.getMaxResponseBytes()) {
            throw AnalysisException.failedPrecondition("Search index '"
                + descriptor.getIndexId() + "' max_response_bytes "
                + descriptor.getMaxResponseBytes()
                + " cannot contain truncation metadata for the resolved route");
          }
          break;
        }
      }
      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      final Status status = GrpcStatusMapper.toStatus(e);
      if (status.getCode() == Status.Code.INTERNAL
          || status.getCode() == Status.Code.UNAVAILABLE) {
        logger.error("SearchIndex failed", e);
      }
      responseObserver.onError(status.withDescription(e.getMessage()).withCause(e.getCause())
          .asRuntimeException());
    } catch (RuntimeException e) {
      logger.error("Unexpected error handling SearchIndex", e);
      responseObserver.onError(Status.INTERNAL.withDescription("Internal server error")
          .withCause(e).asRuntimeException());
    }
  }

  private SearchIndexProvider validateRequest(SearchIndexRequest request) {
    if (request == null) {
      throw AnalysisException.invalidArgument("SearchIndex request must not be null");
    }
    if (request.getIndexId().isBlank()) {
      throw AnalysisException.invalidArgument("SearchIndex index_id must not be blank");
    }
    final SearchIndexProvider provider = registry.require(request.getIndexId());
    if (!request.hasQuery() || request.getQuery().getRawText().isBlank()) {
      throw AnalysisException.invalidArgument("SearchIndex query.raw_text must not be blank");
    }
    final SearchIndexDescriptor descriptor = provider.descriptor();
    if (request.getTopK() < 1 || request.getTopK() > descriptor.getMaxTopK()) {
      throw AnalysisException.invalidArgument("SearchIndex top_k must be between 1 and "
          + descriptor.getMaxTopK() + ", was " + request.getTopK());
    }
    final int queryBytes = request.getQuery().getRawText().getBytes(StandardCharsets.UTF_8).length;
    if (queryBytes > descriptor.getMaxQueryBytes()) {
      throw AnalysisException.invalidArgument("SearchIndex query.raw_text uses " + queryBytes
          + " UTF-8 bytes, exceeding maximum " + descriptor.getMaxQueryBytes());
    }
    return provider;
  }

  private static EmbeddingRoute selectConfiguredRoute(
      SearchIndexDescriptor descriptor, EmbeddingProvider embeddingProvider) {
    final EmbeddingRoute route = descriptor.getEmbeddingRoute();
    if (!embeddingProvider.supportsModel(route.getModelId())) {
      throw new IllegalArgumentException("Search index '" + descriptor.getIndexId()
          + "' requires unavailable embedding model '" + route.getModelId() + "'");
    }
    final int dimension = embeddingProvider.embeddingDimension(route.getModelId());
    if (dimension != descriptor.getDimension()) {
      throw new IllegalArgumentException("Search index '" + descriptor.getIndexId()
          + "' dimension " + descriptor.getDimension() + " does not match embedding model '"
          + route.getModelId() + "' dimension " + dimension);
    }
    for (EmbeddingRoute available : embeddingProvider.routesForModel(route.getModelId())) {
      if (route.getModelId().equals(available.getModelId())
          && route.getVectorSpaceId().equals(available.getVectorSpaceId())
          && !available.getBackendId().isBlank()) {
        return available;
      }
    }
    throw new IllegalArgumentException("Search index '" + descriptor.getIndexId()
        + "' requires unavailable vector space '" + route.getVectorSpaceId() + "'");
  }

  private static void validateResolvedRoute(
      SearchIndexDescriptor descriptor, EmbeddingBatchResult embedding) {
    if (embedding == null || embedding.route() == null || embedding.vectors() == null
        || embedding.vectors().size() != 1 || embedding.vectors().getFirst() == null) {
      throw new IllegalStateException("Embedding provider returned an invalid query result");
    }
    final EmbeddingRoute expected = descriptor.getEmbeddingRoute();
    final EmbeddingRoute actual = embedding.route();
    if (!expected.getModelId().equals(actual.getModelId())
        || actual.getVectorSpaceId().isBlank()
        || !expected.getVectorSpaceId().equals(actual.getVectorSpaceId())) {
      throw AnalysisException.failedPrecondition("Query embedding route for index '"
          + descriptor.getIndexId() + "' resolved to model '" + actual.getModelId()
          + "' vector space '" + actual.getVectorSpaceId() + "', expected model '"
          + expected.getModelId() + "' vector space '" + expected.getVectorSpaceId() + "'");
    }
    if (embedding.vectors().getFirst().length != descriptor.getDimension()) {
      throw AnalysisException.failedPrecondition("Query embedding dimension "
          + embedding.vectors().getFirst().length + " does not match index '"
          + descriptor.getIndexId() + "' dimension " + descriptor.getDimension());
    }
  }

  private static SearchHit toHit(SearchResult result) {
    final SearchRecord record = result.record();
    return SearchHit.newBuilder()
        .setDocumentId(record.documentId())
        .setChunkId(record.chunkId())
        .setScore(result.score())
        .setSourceDocument(record.sourceDocument())
        .setSourceSpan(record.sourceSpan())
        .setEmittedText(record.emittedText())
        .build();
  }
}
