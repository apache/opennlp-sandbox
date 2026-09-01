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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.google.protobuf.CodedOutputStream;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingBatchResult;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.search.query.CelQueryEvaluator;
import org.apache.opennlp.grpc.search.query.CompoundQueryExecutor;
import org.apache.opennlp.grpc.search.query.CompoundQueryValidator;
import org.apache.opennlp.grpc.spi.search.QueryCandidate;
import org.apache.opennlp.grpc.v1.CollectionDescriptor;
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
import org.apache.opennlp.grpc.v1.IndexAlias;
import org.apache.opennlp.grpc.v1.ListIndexAliasesRequest;
import org.apache.opennlp.grpc.v1.ListIndexAliasesResponse;
import org.apache.opennlp.grpc.v1.PersistIndexRequest;
import org.apache.opennlp.grpc.v1.PersistIndexResponse;
import org.apache.opennlp.grpc.v1.ReindexIndexRequest;
import org.apache.opennlp.grpc.v1.ReindexIndexResponse;
import org.apache.opennlp.grpc.v1.SealIndexRequest;
import org.apache.opennlp.grpc.v1.SealIndexResponse;
import org.apache.opennlp.grpc.v1.SetIndexAliasRequest;
import org.apache.opennlp.grpc.v1.SetIndexAliasResponse;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.apache.opennlp.grpc.v1.EmbeddingSelector;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.ListSearchIndexesRequest;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.ListSearchProvidersRequest;
import org.apache.opennlp.grpc.v1.ListSearchProvidersResponse;
import org.apache.opennlp.grpc.v1.OpenNlpSearchServiceGrpc;
import org.apache.opennlp.grpc.v1.QueryNode;
import org.apache.opennlp.grpc.v1.SearchHit;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;
import org.apache.opennlp.grpc.v1.server.GrpcStatusMapper;
import org.apache.opennlp.grpc.vocabulary.UnknownVocabularyArtifactException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.opennlp.grpc.spi.search.SearchResult;
import org.apache.opennlp.grpc.spi.search.SearchIndexProvider;
import org.apache.opennlp.grpc.spi.search.SearchRecord;
import org.apache.opennlp.grpc.spi.search.SearchIndexBundleConfiguration;
import org.apache.opennlp.grpc.spi.search.KeywordQueryIndex;

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
  private final IndexAliasRegistry aliasRegistry;
  private final SearchCollectionRegistry collectionRegistry;
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
    this(registry, dynamicRegistry, embeddingProvider, IndexAliasRegistry.inMemory());
  }

  /**
   * Creates a service over immutable and dynamic index registries with a configured
   * alias registry.
   *
   * @param registry Startup-loaded immutable indexes.
   * @param dynamicRegistry Bounded in-memory indexes owned by the server lifecycle.
   * @param embeddingProvider Query embedding provider.
   * @param aliasRegistry Logical alias registry.
   * @throws IllegalArgumentException If an argument or index route is invalid.
   */
  public OpenNlpSearchServiceImpl(
      SearchIndexRegistry registry,
      DynamicSearchIndexRegistry dynamicRegistry,
      EmbeddingProvider embeddingProvider,
      IndexAliasRegistry aliasRegistry) {
    this(registry, dynamicRegistry, embeddingProvider, aliasRegistry,
        dynamicRegistry == null ? null
            : SearchCollectionRegistry.inMemory(dynamicRegistry, artifactId -> {
              throw new IllegalArgumentException(
                  "Unknown vocabulary artifact '" + artifactId + "'");
            }));
  }

  /**
   * Creates a service over immutable and dynamic index registries with configured
   * alias and collection registries.
   *
   * @param registry Startup-loaded immutable indexes.
   * @param dynamicRegistry Bounded in-memory indexes owned by the server lifecycle.
   * @param embeddingProvider Query embedding provider.
   * @param aliasRegistry Logical alias registry.
   * @param collectionRegistry Collection registry over the dynamic indexes.
   * @throws IllegalArgumentException If an argument or index route is invalid.
   */
  public OpenNlpSearchServiceImpl(
      SearchIndexRegistry registry,
      DynamicSearchIndexRegistry dynamicRegistry,
      EmbeddingProvider embeddingProvider,
      IndexAliasRegistry aliasRegistry,
      SearchCollectionRegistry collectionRegistry) {
    if (aliasRegistry == null) {
      throw new IllegalArgumentException("aliasRegistry must not be null");
    }
    if (registry == null) {
      throw new IllegalArgumentException("registry must not be null");
    }
    if (embeddingProvider == null) {
      throw new IllegalArgumentException("embeddingProvider must not be null");
    }
    if (dynamicRegistry == null) {
      throw new IllegalArgumentException("dynamicRegistry must not be null");
    }
    if (collectionRegistry == null) {
      throw new IllegalArgumentException("collectionRegistry must not be null");
    }
    this.registry = registry;
    this.dynamicRegistry = dynamicRegistry;
    this.embeddingProvider = embeddingProvider;
    this.aliasRegistry = aliasRegistry;
    this.collectionRegistry = collectionRegistry;
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
  public void listSearchProviders(
      ListSearchProvidersRequest request,
      StreamObserver<ListSearchProvidersResponse> responseObserver) {
    responseObserver.onNext(ListSearchProvidersResponse.newBuilder()
        .addAllProviders(dynamicRegistry.catalog().instances())
        .setDynamicIndexingEnabled(dynamicRegistry.isEnabled())
        .setPersistenceConfigured(dynamicRegistry.isPersistenceConfigured())
        .build());
    responseObserver.onCompleted();
  }

  /** {@inheritDoc} */
  @Override
  public void indexDocuments(
      IndexDocumentsRequest request, StreamObserver<IndexDocumentsResponse> responseObserver) {
    try {
      final IndexDocumentsResponse response = dynamicRegistry.index(request);
      collectionRegistry.notifyIndexed(response.getIndex().getIndexId());
      responseObserver.onNext(response);
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
      if (deleted) {
        // An alias to a deleted index would resolve to NOT_FOUND, and a collection listing it
        // could not be saved again as reported, so both references go with the index.
        aliasRegistry.deleteByIndex(request.getIndexId());
        if (collectionRegistry != null) {
          collectionRegistry.removeMember(request.getIndexId());
        }
      }
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
  public void persistIndex(
      PersistIndexRequest request, StreamObserver<PersistIndexResponse> responseObserver) {
    try {
      final String indexId = requireLifecycleIndexId("PersistIndex",
          request == null ? null : request.getIndexId());
      final SearchIndexDescriptor persisted = dynamicRegistry.persist(indexId);
      collectionRegistry.notifyIndexPersisted(indexId);
      responseObserver.onNext(PersistIndexResponse.newBuilder()
          .setIndex(persisted)
          .build());
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      respondAnalysisFailure("PersistIndex", e, responseObserver);
    } catch (RuntimeException e) {
      respondUnexpectedFailure("PersistIndex", e, responseObserver);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void sealIndex(
      SealIndexRequest request, StreamObserver<SealIndexResponse> responseObserver) {
    try {
      final String indexId = requireLifecycleIndexId("SealIndex",
          request == null ? null : request.getIndexId());
      final SearchIndexDescriptor sealed = dynamicRegistry.seal(indexId);
      collectionRegistry.notifyIndexPersisted(indexId);
      responseObserver.onNext(SealIndexResponse.newBuilder()
          .setIndex(sealed)
          .build());
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      respondAnalysisFailure("SealIndex", e, responseObserver);
    } catch (RuntimeException e) {
      respondUnexpectedFailure("SealIndex", e, responseObserver);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void reindexIndex(
      ReindexIndexRequest request, StreamObserver<ReindexIndexResponse> responseObserver) {
    try {
      final String sourceId = requireLifecycleIndexId("ReindexIndex",
          request == null ? null : request.getIndexId());
      DynamicSearchIndexRegistry.validateSelector(request.getEmbedding());
      if (request.hasAlias()) {
        try {
          SearchIndexRegistry.requireStableId(request.getAlias(), "ReindexIndex alias");
        } catch (IllegalArgumentException e) {
          throw AnalysisException.invalidArgument(e.getMessage());
        }
        if (indexExists(request.getAlias())) {
          throw AnalysisException.invalidArgument("ReindexIndex alias '" + request.getAlias()
              + "' collides with an existing index id");
        }
      }
      final List<DynamicSearchIndexRegistry.RetainedChunk> sourceChunks =
          dynamicRegistry.retainedChunks(sourceId);
      final List<DynamicSearchIndexRegistry.IndexedChunk> replayed =
          replay(sourceChunks, request.getEmbedding());
      final SearchIndexDescriptor built = dynamicRegistry.reindexInto(
          sourceId, request.hasProvider() ? request.getProvider() : null, replayed);
      if (request.hasAlias()) {
        aliasRegistry.set(request.getAlias(), built.getIndexId());
      }
      responseObserver.onNext(ReindexIndexResponse.newBuilder()
          .setIndex(built)
          .setSourceIndexId(sourceId)
          .setReindexedDocuments(Math.toIntExact(replayed.stream()
              .map(chunk -> chunk.record().documentId()).distinct().count()))
          .setReindexedChunks(replayed.size())
          .build());
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      respondAnalysisFailure("ReindexIndex", e, responseObserver);
    } catch (RuntimeException e) {
      respondUnexpectedFailure("ReindexIndex", e, responseObserver);
    }
  }

  /**
   * Re-embeds retained chunk texts through the newly selected route in bounded batches.
   *
   * @param sourceChunks Retained source chunks.
   * @param embedding Requested embedding selection.
   * @return Chunks carrying the new vectors and the resolved route.
   * @throws AnalysisException If an embedding call fails.
   */
  private List<DynamicSearchIndexRegistry.IndexedChunk> replay(
      List<DynamicSearchIndexRegistry.RetainedChunk> sourceChunks,
      EmbeddingSelector embedding) {
    final int batchSize = 16;
    final List<DynamicSearchIndexRegistry.IndexedChunk> replayed =
        new ArrayList<>(sourceChunks.size());
    EmbeddingRoute resolvedRoute = null;
    for (int start = 0; start < sourceChunks.size(); start += batchSize) {
      final List<DynamicSearchIndexRegistry.RetainedChunk> batch =
          sourceChunks.subList(start, Math.min(start + batchSize, sourceChunks.size()));
      final List<String> texts = batch.stream()
          .map(chunk -> chunk.record().indexedText()).toList();
      final String backend = resolvedRoute == null
          ? DynamicSearchIndexRegistry.selectedBackend(embedding)
          : resolvedRoute.getBackendId();
      final EmbeddingBatchResult embedded = embeddingProvider.embedBatchResolved(
          embedding.getModelId(), backend, texts);
      if (embedded == null || embedded.vectors() == null
          || embedded.vectors().size() != texts.size()) {
        throw new IllegalStateException(
            "Embedding backend returned a mismatched reindex batch");
      }
      if (resolvedRoute == null) {
        resolvedRoute = embedded.route();
        if (resolvedRoute.getModelId().isBlank() || resolvedRoute.getBackendId().isBlank()
            || resolvedRoute.getVectorSpaceId().isBlank()) {
          throw new IllegalStateException(
              "Embedding backend resolved an incomplete reindex route");
        }
      } else if (!resolvedRoute.getModelId().equals(embedded.route().getModelId())
          || !resolvedRoute.getVectorSpaceId().equals(embedded.route().getVectorSpaceId())) {
        throw new IllegalStateException(
            "Embedding backend changed vector spaces during a reindex");
      }
      for (int index = 0; index < batch.size(); index++) {
        replayed.add(new DynamicSearchIndexRegistry.IndexedChunk(
            batch.get(index).record(), embedded.vectors().get(index), resolvedRoute));
      }
    }
    return List.copyOf(replayed);
  }

  /** {@inheritDoc} */
  @Override
  public void setIndexAlias(
      SetIndexAliasRequest request, StreamObserver<SetIndexAliasResponse> responseObserver) {
    try {
      if (request == null || request.getAlias().isBlank()) {
        throw AnalysisException.invalidArgument("SetIndexAlias alias must not be blank");
      }
      if (request.getIndexId().isBlank()) {
        throw AnalysisException.invalidArgument("SetIndexAlias index_id must not be blank");
      }
      if (indexExists(request.getAlias())) {
        throw AnalysisException.invalidArgument("SetIndexAlias alias '" + request.getAlias()
            + "' collides with an existing index id");
      }
      if (!indexExists(request.getIndexId())) {
        throw AnalysisException.notFound("SetIndexAlias index_id names unknown index '"
            + request.getIndexId() + "'");
      }
      final IndexAlias alias;
      try {
        alias = aliasRegistry.set(request.getAlias(), request.getIndexId());
      } catch (IllegalArgumentException e) {
        throw AnalysisException.invalidArgument("SetIndexAlias " + e.getMessage());
      }
      responseObserver.onNext(SetIndexAliasResponse.newBuilder().setAlias(alias).build());
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      respondAnalysisFailure("SetIndexAlias", e, responseObserver);
    } catch (RuntimeException e) {
      respondUnexpectedFailure("SetIndexAlias", e, responseObserver);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void deleteIndexAlias(
      DeleteIndexAliasRequest request,
      StreamObserver<DeleteIndexAliasResponse> responseObserver) {
    try {
      if (request == null || request.getAlias().isBlank()) {
        throw AnalysisException.invalidArgument("DeleteIndexAlias alias must not be blank");
      }
      responseObserver.onNext(DeleteIndexAliasResponse.newBuilder()
          .setAlias(request.getAlias())
          .setDeleted(aliasRegistry.delete(request.getAlias()))
          .build());
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      respondAnalysisFailure("DeleteIndexAlias", e, responseObserver);
    } catch (RuntimeException e) {
      respondUnexpectedFailure("DeleteIndexAlias", e, responseObserver);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void listIndexAliases(
      ListIndexAliasesRequest request,
      StreamObserver<ListIndexAliasesResponse> responseObserver) {
    responseObserver.onNext(ListIndexAliasesResponse.newBuilder()
        .addAllAliases(aliasRegistry.aliases())
        .build());
    responseObserver.onCompleted();
  }

  /** {@inheritDoc} */
  @Override
  public void setCollection(
      SetCollectionRequest request, StreamObserver<SetCollectionResponse> responseObserver) {
    try {
      if (request == null || request.getCollectionId().isBlank()) {
        throw AnalysisException.invalidArgument("SetCollection collection_id must not be blank");
      }
      final SetCollectionRequest.Builder resolved = request.toBuilder().clearMemberIndexIds();
      for (String member : request.getMemberIndexIdsList()) {
        if (member.isBlank()) {
          throw AnalysisException.invalidArgument(
              "SetCollection member index id must not be blank");
        }
        final String indexId = aliasRegistry.resolve(member);
        if (registry.find(indexId) != null) {
          throw AnalysisException.failedPrecondition("SetCollection member '" + indexId
              + "' is a startup bundle; members must be dynamic indexes");
        }
        if (dynamicRegistry.find(indexId) == null) {
          throw AnalysisException.notFound(
              "SetCollection member names unknown index '" + indexId + "'");
        }
        resolved.addMemberIndexIds(indexId);
      }
      final CollectionDescriptor collection;
      try {
        collection = collectionRegistry.set(resolved.build());
      } catch (UnknownVocabularyArtifactException e) {
        throw AnalysisException.notFound("SetCollection " + e.getMessage());
      } catch (IllegalArgumentException e) {
        throw AnalysisException.invalidArgument("SetCollection " + e.getMessage());
      }
      responseObserver.onNext(SetCollectionResponse.newBuilder()
          .setCollection(collection)
          .build());
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      respondAnalysisFailure("SetCollection", e, responseObserver);
    } catch (RuntimeException e) {
      respondUnexpectedFailure("SetCollection", e, responseObserver);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void getCollection(
      GetCollectionRequest request, StreamObserver<GetCollectionResponse> responseObserver) {
    try {
      if (request == null || request.getCollectionId().isBlank()) {
        throw AnalysisException.invalidArgument("GetCollection collection_id must not be blank");
      }
      final CollectionDescriptor collection =
          collectionRegistry.find(request.getCollectionId());
      if (collection == null) {
        throw AnalysisException.notFound("GetCollection names unknown collection '"
            + request.getCollectionId() + "'");
      }
      responseObserver.onNext(GetCollectionResponse.newBuilder()
          .setCollection(collection)
          .build());
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      respondAnalysisFailure("GetCollection", e, responseObserver);
    } catch (RuntimeException e) {
      respondUnexpectedFailure("GetCollection", e, responseObserver);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void listCollections(
      ListCollectionsRequest request,
      StreamObserver<ListCollectionsResponse> responseObserver) {
    try {
      responseObserver.onNext(ListCollectionsResponse.newBuilder()
          .addAllCollections(collectionRegistry.list())
          .build());
      responseObserver.onCompleted();
    } catch (RuntimeException e) {
      respondUnexpectedFailure("ListCollections", e, responseObserver);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void deleteCollection(
      DeleteCollectionRequest request,
      StreamObserver<DeleteCollectionResponse> responseObserver) {
    try {
      if (request == null || request.getCollectionId().isBlank()) {
        throw AnalysisException.invalidArgument(
            "DeleteCollection collection_id must not be blank");
      }
      responseObserver.onNext(DeleteCollectionResponse.newBuilder()
          .setCollectionId(request.getCollectionId())
          .setDeleted(collectionRegistry.delete(request.getCollectionId()))
          .build());
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      respondAnalysisFailure("DeleteCollection", e, responseObserver);
    } catch (RuntimeException e) {
      respondUnexpectedFailure("DeleteCollection", e, responseObserver);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void watchCollection(
      WatchCollectionRequest request, StreamObserver<CollectionEvent> responseObserver) {
    try {
      if (request == null || request.getCollectionId().isBlank()) {
        throw AnalysisException.invalidArgument(
            "WatchCollection collection_id must not be blank");
      }
      final AtomicReference<SearchCollectionRegistry.Watch> subscription =
          new AtomicReference<>();
      final AtomicBoolean cancelled = new AtomicBoolean();
      if (responseObserver instanceof ServerCallStreamObserver<CollectionEvent> serverCall) {
        final ServerCallStreamObserver<CollectionEvent> observedCall = serverCall;
        serverCall.setOnCancelHandler(() -> {
          cancelled.set(true);
          final SearchCollectionRegistry.Watch watch = subscription.getAndSet(null);
          if (watch != null) {
            watch.close();
          }
        });
        if (observedCall.isCancelled()) {
          return;
        }
      }
      try {
        final SearchCollectionRegistry.Watch watch = collectionRegistry.watch(
            request.getCollectionId(), responseObserver::onNext, responseObserver::onCompleted);
        if (cancelled.get()) {
          watch.close();
          return;
        }
        subscription.set(watch);
        if (cancelled.get() && subscription.compareAndSet(watch, null)) {
          watch.close();
        }
      } catch (IllegalArgumentException e) {
        throw AnalysisException.notFound("WatchCollection " + e.getMessage());
      }
    } catch (AnalysisException e) {
      respondAnalysisFailure("WatchCollection", e, responseObserver);
    } catch (RuntimeException e) {
      respondUnexpectedFailure("WatchCollection", e, responseObserver);
    }
  }

  /**
   * Validates and alias-resolves one lifecycle target, rejecting immutable startup
   * bundles, which are already durable.
   *
   * @param operation RPC operation name.
   * @param idOrAlias Requested index id or alias.
   * @return The resolved dynamic index id.
   * @throws AnalysisException If the id is blank or names a startup bundle.
   */
  private String requireLifecycleIndexId(String operation, String idOrAlias) {
    if (idOrAlias == null || idOrAlias.isBlank()) {
      throw AnalysisException.invalidArgument(operation + " index_id must not be blank");
    }
    final String indexId = aliasRegistry.resolve(idOrAlias);
    if (registry.find(indexId) != null) {
      throw AnalysisException.failedPrecondition(operation + " cannot target index '"
          + indexId + "': startup bundles are already immutable and durable");
    }
    return indexId;
  }

  /** {@inheritDoc} */
  @Override
  public void searchIndex(
      SearchIndexRequest request, StreamObserver<SearchIndexResponse> responseObserver) {
    try {
      final SearchIndexProvider provider = validateRequest(request);
      final SearchIndexDescriptor descriptor = provider.descriptor();
      final int resultLimit = resultLimit(request, descriptor);
      if (request.getQueryKindCase() == SearchIndexRequest.QueryKindCase.COMPOUND_QUERY) {
        responseObserver.onNext(searchCompound(request, provider, descriptor, resultLimit));
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
      final List<SearchResult> results = provider.search(queryVector, resultLimit);
      if (results == null || results.stream().anyMatch(java.util.Objects::isNull)) {
        throw new IllegalStateException("Search provider returned null results");
      }
      if (results.size() > resultLimit) {
        throw new IllegalStateException("Search provider returned " + results.size()
            + " results for limit " + resultLimit);
      }
      final List<ResponseHit> rankedHits = results.stream()
          .sorted(STABLE_ORDER)
          .limit(resultLimit)
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
   * @param resultLimit Validated maximum number of results.
   * @return Complete search response within the index response budget.
   * @throws AnalysisException If the index does not execute compound queries or a
   *     clause fails.
   */
  private SearchIndexResponse searchCompound(
      SearchIndexRequest request, SearchIndexProvider provider,
      SearchIndexDescriptor descriptor, int resultLimit) {
    final List<QueryCandidate> candidates = provider.queryCandidates();
    if (candidates == null) {
      throw AnalysisException.unimplemented("Search index '" + descriptor.getIndexId()
          + "' does not execute compound queries");
    }
    final org.apache.opennlp.grpc.spi.search.KeywordQueryIndex keywordIndex =
        provider.keywordQueryIndex();
    if (keywordIndex == null
        && org.apache.opennlp.grpc.search.query.CompoundQueryValidator
            .containsKeywordClause(request.getCompoundQuery())) {
      throw AnalysisException.unimplemented("Search index '" + descriptor.getIndexId()
          + "' has no configured keyword query provider");
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
        request.getCompoundQuery(), candidates, embedder, provider::search,
        keywordIndex, resultLimit);
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
      List<ResponseHit> rankedHits) {
    final Map<String, org.apache.opennlp.grpc.v1.OpenNlpDocument> sources =
        new LinkedHashMap<>();
    int responseBytes = response.build().getSerializedSize();
    final int truncatedBytes = CodedOutputStream.computeBoolSize(
        SearchIndexResponse.TRUNCATED_FIELD_NUMBER, true);
    for (int resultIndex = 0; resultIndex < rankedHits.size(); resultIndex++) {
      final ResponseHit result = rankedHits.get(resultIndex);
      final SearchHit hit = result.hit();
      final var existingSource = sources.get(hit.getDocumentId());
      final boolean addedSource = existingSource == null;
      if (!addedSource && !existingSource.equals(result.sourceDocument())) {
        throw new IllegalStateException("Search provider returned conflicting source documents for '"
            + hit.getDocumentId() + "'");
      }
      final int addedBytes = CodedOutputStream.computeMessageSize(
          SearchIndexResponse.HITS_FIELD_NUMBER, hit)
          + (addedSource ? CodedOutputStream.computeMessageSize(
              SearchIndexResponse.SOURCE_DOCUMENTS_FIELD_NUMBER, result.sourceDocument()) : 0);
      final boolean finalResult = resultIndex + 1 == rankedHits.size();
      final int byteLimit = descriptor.getMaxResponseBytes() - (finalResult ? 0 : truncatedBytes);
      if (responseBytes + addedBytes > byteLimit) {
        response.setTruncated(true);
        if (responseBytes + truncatedBytes > descriptor.getMaxResponseBytes()) {
          throw AnalysisException.failedPrecondition("Search index '"
              + descriptor.getIndexId() + "' max_response_bytes "
              + descriptor.getMaxResponseBytes()
              + " cannot contain truncation metadata for the resolved route");
        }
        break;
      }
      if (addedSource) {
        sources.put(hit.getDocumentId(), result.sourceDocument());
        response.addSourceDocuments(result.sourceDocument());
      }
      response.addHits(hit);
      responseBytes += addedBytes;
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
    return provider;
  }

  /**
   * Resolves the request's typed result limit.
   *
   * @param request Search request.
   * @param descriptor Target index descriptor.
   * @return Validated provider result limit.
   * @throws AnalysisException If the selected limit violates the descriptor.
   * @throws IllegalStateException If the descriptor advertises an unsafe exhaustive result.
   */
  private int resultLimit(
      SearchIndexRequest request, SearchIndexDescriptor descriptor) {
    return switch (request.getResultLimitCase()) {
      case TOP_K -> {
        if (request.getTopK() < 1 || request.getTopK() > descriptor.getMaxTopK()) {
          throw AnalysisException.invalidArgument("SearchIndex top_k must be between 1 and "
              + descriptor.getMaxTopK() + ", was " + request.getTopK());
        }
        yield request.getTopK();
      }
      case ALL_HITS -> {
        if (!request.getAllHits()) {
          throw AnalysisException.invalidArgument("SearchIndex all_hits must be true");
        }
        if (!descriptor.getSupportsAllHits()) {
          throw AnalysisException.failedPrecondition("Search index '"
              + descriptor.getIndexId() + "' does not support exhaustive results");
        }
        if (descriptor.getSize() > SearchIndexBundleConfiguration.MAX_ALL_HITS_LIMIT) {
          throw new IllegalStateException("Search index '" + descriptor.getIndexId()
              + "' advertises exhaustive results above the fixed safety ceiling");
        }
        yield Math.max(1, descriptor.getSize());
      }
      case RESULTLIMIT_NOT_SET -> throw AnalysisException.invalidArgument(
          "SearchIndex requires exactly one of top_k and all_hits");
    };
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
   * Resolves a provider from either registry after alias resolution.
   *
   * @param idOrAlias Opaque index identifier or alias.
   * @return Matching provider.
   * @throws AnalysisException If no provider has the resolved identifier.
   */
  private SearchIndexProvider findProvider(String idOrAlias) {
    final String indexId = aliasRegistry.resolve(idOrAlias);
    final SearchIndexProvider immutable = registry.find(indexId);
    if (immutable != null) {
      return immutable;
    }
    try {
      return dynamicRegistry.require(indexId);
    } catch (AnalysisException e) {
      if (e.getFailureType() != AnalysisException.FailureType.NOT_FOUND) {
        throw e;
      }
      // Neither registry knows the id; say so once rather than blaming the live one.
      throw AnalysisException.notFound("Unknown search index '" + idOrAlias
          + "': no read-only bundle or live index has that id or alias");
    }
  }

  /**
   * Tests whether an identifier names a configured or dynamic index.
   *
   * @param indexId Opaque index identifier.
   * @return {@code true} when an index owns the identifier.
   */
  private boolean indexExists(String indexId) {
    return registry.find(indexId) != null || dynamicRegistry.find(indexId) != null;
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
  private static ResponseHit toHit(SearchResult result) {
    final SearchRecord record = result.record();
    final SearchHit hit = SearchHit.newBuilder()
        .setDocumentId(record.documentId())
        .setChunkId(record.chunkId())
        .setChunkGroupId(record.chunkGroupId())
        .setScore(result.score())
        .setSourceSpan(record.sourceSpan())
        .setIndexedText(record.indexedText())
        .build();
    return new ResponseHit(hit, record.sourceDocument());
  }

  /**
   * Converts one compound query result to its wire representation.
   *
   * @param hit Executor result.
   * @return Search hit carrying the root algebra score and matched spans.
   */
  private static ResponseHit toHit(CompoundQueryExecutor.QueryHit hit) {
    final SearchRecord record = hit.candidate().record();
    final SearchHit wireHit = SearchHit.newBuilder()
        .setDocumentId(record.documentId())
        .setChunkId(record.chunkId())
        .setChunkGroupId(record.chunkGroupId())
        .setScore(hit.score())
        .setSourceSpan(record.sourceSpan())
        .setIndexedText(record.indexedText())
        .addAllMatchedSpans(hit.matchedSpans())
        .build();
    return new ResponseHit(wireHit, record.sourceDocument());
  }

  /**
   * One compact wire hit paired with its response-level source document.
   *
   * @param hit Compact ranked hit.
   * @param sourceDocument Referenced source document.
   */
  private record ResponseHit(
      SearchHit hit, org.apache.opennlp.grpc.v1.OpenNlpDocument sourceDocument) {
  }
}
