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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.embedding.EmbeddingBatchResult;
import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.search.query.CelQueryEvaluator;
import org.apache.opennlp.grpc.search.query.CompoundQueryExecutor;
import org.apache.opennlp.grpc.search.query.CompoundQueryValidator;
import org.apache.opennlp.grpc.search.query.QueryCandidate;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexRequest;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexResponse;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.ListSearchIndexesRequest;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpSearchServiceGrpc;
import org.apache.opennlp.grpc.v1.QueryNode;
import org.apache.opennlp.grpc.v1.SearchHit;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;
import org.apache.opennlp.grpc.v1.server.GrpcStatusMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** gRPC adapter for bounded search over static and process-local dynamic indexes. */
public final class OpenNlpSearchServiceImpl
    extends OpenNlpSearchServiceGrpc.OpenNlpSearchServiceImplBase {

  private static final Logger logger = LoggerFactory.getLogger(OpenNlpSearchServiceImpl.class);
  private static final Comparator<SearchResult> STABLE_ORDER =
      Comparator.comparingDouble(SearchResult::score).reversed()
          .thenComparing(result -> result.record().chunkId())
          .thenComparing(result -> result.record().documentId());

  private final SearchIndexRegistry registry;
  private final DynamicSearchIndexRegistry dynamicRegistry;
  private final EmbeddingProvider embeddingProvider;
  private final Map<String, String> queryBackendByIndex;
  private final CompoundQueryExecutor compoundQueryExecutor =
      new CompoundQueryExecutor(CelQueryEvaluator.discover());

  /**
   * Creates a service and verifies every index against the available embedding vector spaces.
   *
   * @param registry Startup-loaded search registry.
   * @param embeddingProvider Query embedding provider.
   * @throws IllegalArgumentException If an index model, dimension, or vector space is unavailable.
   */
  public OpenNlpSearchServiceImpl(
      SearchIndexRegistry registry, EmbeddingProvider embeddingProvider) {
    this(registry, new DynamicSearchIndexRegistry(), embeddingProvider);
  }

  /**
   * Creates a service over immutable and server-owned dynamic index registries.
   *
   * @param registry Startup-loaded immutable indexes.
   * @param dynamicRegistry Bounded in-memory indexes owned by the server lifecycle.
   * @param embeddingProvider Query embedding provider.
   * @throws IllegalArgumentException If an argument or index route is invalid.
   */
  public OpenNlpSearchServiceImpl(
      SearchIndexRegistry registry,
      DynamicSearchIndexRegistry dynamicRegistry,
      EmbeddingProvider embeddingProvider) {
    if (registry == null) {
      throw new IllegalArgumentException("registry must not be null");
    }
    if (embeddingProvider == null) {
      throw new IllegalArgumentException("embeddingProvider must not be null");
    }
    if (dynamicRegistry == null) {
      throw new IllegalArgumentException("dynamicRegistry must not be null");
    }
    this.registry = registry;
    this.dynamicRegistry = dynamicRegistry;
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

  /** {@inheritDoc} */
  @Override
  public void listSearchIndexes(
      ListSearchIndexesRequest request,
      StreamObserver<ListSearchIndexesResponse> responseObserver) {
    final List<SearchIndexDescriptor> descriptors = new ArrayList<>(registry.descriptors());
    descriptors.addAll(dynamicRegistry.descriptors());
    descriptors.sort(Comparator.comparing(SearchIndexDescriptor::getIndexId));
    responseObserver.onNext(ListSearchIndexesResponse.newBuilder()
        .addAllIndexes(descriptors)
        .build());
    responseObserver.onCompleted();
  }

  /** {@inheritDoc} */
  @Override
  public void indexDocuments(
      IndexDocumentsRequest request, StreamObserver<IndexDocumentsResponse> responseObserver) {
    try {
      responseObserver.onNext(dynamicRegistry.index(request));
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      respondAnalysisFailure("IndexDocuments", e, responseObserver);
    } catch (RuntimeException e) {
      respondUnexpectedFailure("IndexDocuments", e, responseObserver);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void deleteSearchIndex(
      DeleteSearchIndexRequest request,
      StreamObserver<DeleteSearchIndexResponse> responseObserver) {
    try {
      if (request == null || request.getIndexId().isBlank()) {
        throw AnalysisException.invalidArgument("DeleteSearchIndex index_id must not be blank");
      }
      if (registry.find(request.getIndexId()) != null) {
        throw AnalysisException.failedPrecondition(
            "DeleteSearchIndex cannot delete immutable index '" + request.getIndexId() + "'");
      }
      final boolean deleted = dynamicRegistry.delete(request.getIndexId());
      responseObserver.onNext(DeleteSearchIndexResponse.newBuilder()
          .setIndexId(request.getIndexId())
          .setDeleted(deleted)
          .build());
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      respondAnalysisFailure("DeleteSearchIndex", e, responseObserver);
    } catch (RuntimeException e) {
      respondUnexpectedFailure("DeleteSearchIndex", e, responseObserver);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void searchIndex(
      SearchIndexRequest request, StreamObserver<SearchIndexResponse> responseObserver) {
    try {
      final SearchIndexProvider provider = validateRequest(request);
      final SearchIndexDescriptor descriptor = provider.descriptor();
      if (request.getQueryKindCase() == SearchIndexRequest.QueryKindCase.COMPOUND_QUERY) {
        responseObserver.onNext(searchCompound(request, provider, descriptor));
        responseObserver.onCompleted();
        return;
      }
      final EmbeddingRoute configuredRoute = descriptor.getEmbeddingRoute();
      final EmbeddingBatchResult embedding = embeddingProvider.embedBatchResolved(
          configuredRoute.getModelId(), queryBackend(descriptor),
          List.of(request.getQuery().getRawText()));
      validateResolvedRoute(descriptor, embedding);
      final float[] queryVector = embedding.vectors().getFirst();
      final SearchIndexResponse.Builder response = SearchIndexResponse.newBuilder()
          .setIndex(descriptor)
          .setQueryEmbeddingRoute(embedding.route());
      requireRouteBudget(response, descriptor);
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
      responseObserver.onNext(addHitsWithinBudget(response, descriptor, rankedHits));
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      respondAnalysisFailure("SearchIndex", e, responseObserver);
    } catch (RuntimeException e) {
      respondUnexpectedFailure("SearchIndex", e, responseObserver);
    }
  }

  /**
   * Executes one compound query against a provider exposing its retained candidates.
   *
   * @param request Validated search request carrying a compound query.
   * @param provider Resolved index provider.
   * @param descriptor Provider descriptor.
   * @return Complete search response within the index response budget.
   * @throws AnalysisException If the index does not execute compound queries or a
   *     clause fails.
   */
  private SearchIndexResponse searchCompound(
      SearchIndexRequest request, SearchIndexProvider provider,
      SearchIndexDescriptor descriptor) {
    final List<QueryCandidate> candidates = provider.queryCandidates();
    if (candidates == null) {
      throw AnalysisException.unimplemented("Search index '" + descriptor.getIndexId()
          + "' does not execute compound queries");
    }
    final AtomicReference<EmbeddingRoute> resolvedRoute = new AtomicReference<>();
    final CompoundQueryExecutor.QueryEmbedder embedder = queryDocument -> {
      final EmbeddingBatchResult embedding = embeddingProvider.embedBatchResolved(
          descriptor.getEmbeddingRoute().getModelId(), queryBackend(descriptor),
          List.of(queryDocument.getRawText()));
      validateResolvedRoute(descriptor, embedding);
      resolvedRoute.compareAndSet(null, embedding.route());
      return embedding.vectors().getFirst();
    };
    final List<CompoundQueryExecutor.QueryHit> hits = compoundQueryExecutor.execute(
        request.getCompoundQuery(), candidates, embedder, request.getTopK());
    final SearchIndexResponse.Builder response = SearchIndexResponse.newBuilder()
        .setIndex(descriptor);
    if (resolvedRoute.get() != null) {
      response.setQueryEmbeddingRoute(resolvedRoute.get());
    }
    requireRouteBudget(response, descriptor);
    return addHitsWithinBudget(response, descriptor,
        hits.stream().map(OpenNlpSearchServiceImpl::toHit).toList());
  }

  /**
   * Verifies a response's fixed metadata fits the index response budget.
   *
   * @param response Response carrying only descriptor and route metadata.
   * @param descriptor Index descriptor.
   * @throws AnalysisException If the budget cannot contain the metadata.
   */
  private static void requireRouteBudget(
      SearchIndexResponse.Builder response, SearchIndexDescriptor descriptor) {
    if (response.build().getSerializedSize() > descriptor.getMaxResponseBytes()) {
      throw AnalysisException.failedPrecondition("Search index '" + descriptor.getIndexId()
          + "' max_response_bytes " + descriptor.getMaxResponseBytes()
          + " cannot contain the resolved query embedding route");
    }
  }

  /**
   * Adds ranked hits until the index response budget is reached, marking truncation.
   *
   * @param response Response carrying descriptor and route metadata.
   * @param descriptor Index descriptor.
   * @param rankedHits Hits in final rank order.
   * @return The complete response.
   * @throws AnalysisException If the budget cannot even contain truncation metadata.
   */
  private static SearchIndexResponse addHitsWithinBudget(
      SearchIndexResponse.Builder response, SearchIndexDescriptor descriptor,
      List<SearchHit> rankedHits) {
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
    return response.build();
  }

  /**
   * Validates a search request and resolves its provider.
   *
   * @param request Search request.
   * @return Matching static or dynamic provider.
   * @throws AnalysisException If the request violates its index contract.
   */
  private SearchIndexProvider validateRequest(SearchIndexRequest request) {
    if (request == null) {
      throw AnalysisException.invalidArgument("SearchIndex request must not be null");
    }
    if (request.getIndexId().isBlank()) {
      throw AnalysisException.invalidArgument("SearchIndex index_id must not be blank");
    }
    final SearchIndexProvider provider = findProvider(request.getIndexId());
    final SearchIndexDescriptor descriptor = provider.descriptor();
    switch (request.getQueryKindCase()) {
      case QUERY -> {
        if (request.getQuery().getRawText().isBlank()) {
          throw AnalysisException.invalidArgument("SearchIndex query.raw_text must not be blank");
        }
        requireQueryBytes(request.getQuery().getRawText(), descriptor);
      }
      case COMPOUND_QUERY -> {
        CompoundQueryValidator.validate(request.getCompoundQuery());
        requireSemanticQueryBytes(request.getCompoundQuery(), descriptor);
      }
      case QUERYKIND_NOT_SET -> throw AnalysisException.invalidArgument(
          "SearchIndex requires exactly one of query and compound_query");
    }
    if (request.getTopK() < 1 || request.getTopK() > descriptor.getMaxTopK()) {
      throw AnalysisException.invalidArgument("SearchIndex top_k must be between 1 and "
          + descriptor.getMaxTopK() + ", was " + request.getTopK());
    }
    return provider;
  }

  /**
   * Enforces the index query byte bound on one query text.
   *
   * @param rawText Query text.
   * @param descriptor Index descriptor.
   * @throws AnalysisException If the text exceeds the bound.
   */
  private static void requireQueryBytes(String rawText, SearchIndexDescriptor descriptor) {
    final int queryBytes = rawText.getBytes(StandardCharsets.UTF_8).length;
    if (queryBytes > descriptor.getMaxQueryBytes()) {
      throw AnalysisException.invalidArgument("SearchIndex query.raw_text uses " + queryBytes
          + " UTF-8 bytes, exceeding maximum " + descriptor.getMaxQueryBytes());
    }
  }

  /**
   * Enforces the index query byte bound on every semantic clause of a compound query.
   *
   * @param node Query node.
   * @param descriptor Index descriptor.
   * @throws AnalysisException If any semantic clause exceeds the bound.
   */
  private static void requireSemanticQueryBytes(
      QueryNode node, SearchIndexDescriptor descriptor) {
    switch (node.getKindCase()) {
      case SEMANTIC -> requireQueryBytes(node.getSemantic().getDocument().getRawText(),
          descriptor);
      case JOIN -> {
        for (QueryNode operand : node.getJoin().getOperandsList()) {
          requireSemanticQueryBytes(operand, descriptor);
        }
        for (QueryNode exclusion : node.getJoin().getExclusionsList()) {
          requireSemanticQueryBytes(exclusion, descriptor);
        }
      }
      case BOOST -> requireSemanticQueryBytes(node.getBoost().getOperand(), descriptor);
      case TERM, PHRASE, CEL_FILTER, CEL_CALCULATOR, KIND_NOT_SET -> {
        // No embedded query text to bound.
      }
    }
  }

  /**
   * Resolves a provider from either registry.
   *
   * @param indexId Opaque index identifier.
   * @return Matching provider.
   * @throws AnalysisException If no provider has the identifier.
   */
  private SearchIndexProvider findProvider(String indexId) {
    final SearchIndexProvider immutable = registry.find(indexId);
    return immutable != null ? immutable : dynamicRegistry.require(indexId);
  }

  /**
   * Selects a compatible backend for a static or dynamic index.
   *
   * @param descriptor Index descriptor.
   * @return Backend identifier.
   * @throws IllegalArgumentException If no compatible backend exists.
   */
  private String queryBackend(SearchIndexDescriptor descriptor) {
    final String configured = queryBackendByIndex.get(descriptor.getIndexId());
    return configured != null
        ? configured : selectConfiguredRoute(descriptor, embeddingProvider).getBackendId();
  }

  /**
   * Maps an expected analysis failure to gRPC status.
   *
   * @param operation RPC operation name.
   * @param failure Failure to map.
   * @param observer Response observer.
   */
  private static void respondAnalysisFailure(
      String operation, AnalysisException failure, StreamObserver<?> observer) {
    final Status status = GrpcStatusMapper.toStatus(failure);
    if (status.getCode() == Status.Code.INTERNAL
        || status.getCode() == Status.Code.UNAVAILABLE) {
      logger.error("{} failed", operation, failure);
    }
    observer.onError(status.withDescription(failure.getMessage()).withCause(failure.getCause())
        .asRuntimeException());
  }

  /**
   * Maps an unexpected runtime failure to a sanitized gRPC status.
   *
   * @param operation RPC operation name.
   * @param failure Failure to log.
   * @param observer Response observer.
   */
  private static void respondUnexpectedFailure(
      String operation, RuntimeException failure, StreamObserver<?> observer) {
    logger.error("Unexpected error handling {}", operation, failure);
    observer.onError(Status.INTERNAL.withDescription("Internal server error")
        .withCause(failure).asRuntimeException());
  }

  /**
   * Selects an available backend in the index vector space.
   *
   * @param descriptor Index descriptor.
   * @param embeddingProvider Available embedding provider.
   * @return Compatible resolved route.
   * @throws IllegalArgumentException If the required vector space is unavailable.
   */
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

  /**
   * Validates a provider's resolved query embedding.
   *
   * @param descriptor Target index descriptor.
   * @param embedding Provider result.
   * @throws AnalysisException If the route or dimension is incompatible.
   * @throws IllegalStateException If the provider response is malformed.
   */
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
    double norm = 0;
    for (float value : embedding.vectors().getFirst()) {
      if (!Float.isFinite(value)) {
        throw new IllegalStateException("Embedding provider returned a non-finite query vector");
      }
      norm += (double) value * value;
    }
    if (norm == 0) {
      throw new IllegalStateException("Embedding provider returned a zero query vector");
    }
  }

  /**
   * Converts one provider result to its wire representation.
   *
   * @param result Provider result.
   * @return Search hit.
   */
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

  /**
   * Converts one compound query result to its wire representation.
   *
   * @param hit Executor result.
   * @return Search hit carrying the root algebra score and matched spans.
   */
  private static SearchHit toHit(CompoundQueryExecutor.QueryHit hit) {
    final SearchRecord record = hit.candidate().record();
    return SearchHit.newBuilder()
        .setDocumentId(record.documentId())
        .setChunkId(record.chunkId())
        .setScore(hit.score())
        .setSourceDocument(record.sourceDocument())
        .setSourceSpan(record.sourceSpan())
        .setEmittedText(record.emittedText())
        .addAllMatchedSpans(hit.matchedSpans())
        .build();
  }
}
