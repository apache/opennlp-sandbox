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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingBatchResult;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.spi.search.QueryCandidate;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexRequest;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexResponse;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.apache.opennlp.grpc.v1.IndexAlias;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.ListSearchIndexesRequest;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.DeleteIndexAliasRequest;
import org.apache.opennlp.grpc.v1.DeleteIndexAliasResponse;
import org.apache.opennlp.grpc.v1.ListIndexAliasesRequest;
import org.apache.opennlp.grpc.v1.ListIndexAliasesResponse;
import org.apache.opennlp.grpc.v1.ListSearchProvidersRequest;
import org.apache.opennlp.grpc.v1.ListSearchProvidersResponse;
import org.apache.opennlp.grpc.v1.EmbeddingSelector;
import org.apache.opennlp.grpc.v1.PersistIndexRequest;
import org.apache.opennlp.grpc.v1.PersistIndexResponse;
import org.apache.opennlp.grpc.v1.ReindexIndexRequest;
import org.apache.opennlp.grpc.v1.ReindexIndexResponse;
import org.apache.opennlp.grpc.v1.SealIndexRequest;
import org.apache.opennlp.grpc.v1.SealIndexResponse;
import org.apache.opennlp.grpc.v1.SetIndexAliasRequest;
import org.apache.opennlp.grpc.v1.SetIndexAliasResponse;
import org.apache.opennlp.grpc.v1.SearchProviderSelector;
import org.apache.opennlp.grpc.v1.StandardSearchProvider;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;
import org.apache.opennlp.grpc.v1.SearchProviderInstance;
import org.apache.opennlp.grpc.v1.CollectionEvent;
import org.apache.opennlp.grpc.v1.CollectionEventKind;
import org.apache.opennlp.grpc.v1.DeleteCollectionRequest;
import org.apache.opennlp.grpc.v1.DeleteCollectionResponse;
import org.apache.opennlp.grpc.v1.GetCollectionRequest;
import org.apache.opennlp.grpc.v1.GetCollectionResponse;
import org.apache.opennlp.grpc.v1.ListCollectionsRequest;
import org.apache.opennlp.grpc.v1.ListCollectionsResponse;
import org.apache.opennlp.grpc.v1.SetCollectionRequest;
import org.apache.opennlp.grpc.v1.SetCollectionResponse;
import org.apache.opennlp.grpc.v1.WatchCollectionRequest;
import org.apache.opennlp.grpc.v1.CelFilterClause;
import org.apache.opennlp.grpc.v1.JoinClause;
import org.apache.opennlp.grpc.v1.JoinOperator;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.QueryNode;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;
import org.apache.opennlp.grpc.v1.SemanticClause;
import org.apache.opennlp.grpc.v1.TermClause;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.opennlp.grpc.spi.search.SearchResult;
import org.apache.opennlp.grpc.spi.search.SearchIndexProvider;
import org.apache.opennlp.grpc.spi.search.SearchRecord;
import org.apache.opennlp.grpc.spi.search.SearchIndexBundleConfiguration;

class OpenNlpSearchServiceImplTest {

  @Test
  void listsConfiguredIndexesInStableOrder() {
    final OpenNlpSearchServiceImpl service = service(
        provider(SearchIndexRegistryTest.descriptor("zeta"), List.of()),
        provider(SearchIndexRegistryTest.descriptor("alpha"), List.of()));
    final CapturingObserver<ListSearchIndexesResponse> observer = new CapturingObserver<>();

    service.listSearchIndexes(ListSearchIndexesRequest.getDefaultInstance(), observer);

    assertEquals(List.of("alpha", "zeta"), observer.value.getIndexesList().stream()
        .map(SearchIndexDescriptor::getIndexId).toList());
    assertTrue(observer.completed);
    assertNull(observer.error);
  }

  @Test
  void listsConfiguredProviderInstancesWithCapabilities() {
    final OpenNlpSearchServiceImpl service = service();
    final CapturingObserver<ListSearchProvidersResponse> observer = new CapturingObserver<>();

    service.listSearchProviders(ListSearchProvidersRequest.getDefaultInstance(), observer);

    assertNull(observer.error);
    assertTrue(observer.completed);
    // The TurboQuant provider ships in the opennlp-grpc-search-turboquant add-on, absent
    // from this module's classpath; the add-on module asserts the full provider set.
    assertEquals(List.of("flat_float", "terms"),
        observer.value.getProvidersList().stream()
            .map(SearchProviderInstance::getInstanceId).toList());
    assertTrue(observer.value.getProviders(0).getCapabilitiesList().contains(
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_VECTOR));
    // The default service has live indexing on and no persistence root.
    assertTrue(observer.value.getDynamicIndexingEnabled());
    assertFalse(observer.value.getPersistenceConfigured());
  }

  @Test
  void providerListingReportsPersistenceWhenARootIsConfigured(@TempDir Path root) {
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of()),
        new DynamicSearchIndexRegistry(
            SearchProviderCatalog.discover(), new WorkspaceCheckpointStore(root)),
        new StubEmbeddingProvider(EmbeddingRoute.getDefaultInstance(), 2, List.of(),
            new float[] {1, 0}));
    final CapturingObserver<ListSearchProvidersResponse> observer = new CapturingObserver<>();

    service.listSearchProviders(ListSearchProvidersRequest.getDefaultInstance(), observer);

    assertNull(observer.error);
    assertTrue(observer.value.getDynamicIndexingEnabled());
    assertTrue(observer.value.getPersistenceConfigured());
  }

  @Test
  void indexesSearchesAndDeletesAWorkspaceThroughGrpcMethods() {
    final DynamicSearchIndexRegistry dynamicRegistry = new DynamicSearchIndexRegistry();
    final EmbeddingRoute workspaceRoute = EmbeddingRoute.newBuilder()
        .setModelId("demo")
        .setBackendId("static")
        .setVectorSpaceId("demo-space")
        .build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of()),
        dynamicRegistry,
        new StubEmbeddingProvider(workspaceRoute, 2, List.of(workspaceRoute),
            new float[] {1, 0}));
    final CapturingObserver<IndexDocumentsResponse> indexed = new CapturingObserver<>();

    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0), indexed);

    assertNull(indexed.error);
    final String indexId = indexed.value.getIndex().getIndexId();
    final CapturingObserver<SearchIndexResponse> searched = new CapturingObserver<>();
    service.searchIndex(request(indexId, "alpha", 1), searched);
    assertNull(searched.error);
    assertEquals("doc-1", searched.value.getHits(0).getDocumentId());

    final CapturingObserver<DeleteSearchIndexResponse> deleted = new CapturingObserver<>();
    service.deleteSearchIndex(DeleteSearchIndexRequest.newBuilder()
        .setIndexId(indexId).build(), deleted);
    assertTrue(deleted.value.getDeleted());
    assertTrue(dynamicRegistry.descriptors().isEmpty());
  }


  @Test
  void persistingAFlatWorkspaceSucceedsThroughGrpcMethods(@TempDir Path root) {
    final DynamicSearchIndexRegistry dynamicRegistry = new DynamicSearchIndexRegistry(
        SearchProviderCatalog.discover(), new WorkspaceCheckpointStore(root));
    final EmbeddingRoute workspaceRoute = EmbeddingRoute.newBuilder()
        .setModelId("demo")
        .setBackendId("static")
        .setVectorSpaceId("demo-space")
        .build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of()),
        dynamicRegistry,
        new StubEmbeddingProvider(workspaceRoute, 2, List.of(workspaceRoute),
            new float[] {1, 0}));
    final CapturingObserver<IndexDocumentsResponse> indexed = new CapturingObserver<>();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0), indexed);

    final CapturingObserver<PersistIndexResponse> persisted = new CapturingObserver<>();
    service.persistIndex(PersistIndexRequest.newBuilder()
        .setIndexId(indexed.value.getIndex().getIndexId()).build(), persisted);

    assertNull(persisted.error);
    assertTrue(persisted.value.getIndex().getPersisted());
    assertFalse(persisted.value.getIndex().getImmutable());
    assertTrue(Files.isDirectory(root));
  }

  @Test
  void aliasesResolveOnSearchAndSupportUpsertListAndDelete() {
    final DynamicSearchIndexRegistry dynamicRegistry = new DynamicSearchIndexRegistry();
    final EmbeddingRoute workspaceRoute = EmbeddingRoute.newBuilder()
        .setModelId("demo")
        .setBackendId("static")
        .setVectorSpaceId("demo-space")
        .build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of()),
        dynamicRegistry,
        new StubEmbeddingProvider(workspaceRoute, 2, List.of(workspaceRoute),
            new float[] {1, 0}),
        IndexAliasRegistry.inMemory());
    final CapturingObserver<IndexDocumentsResponse> indexed = new CapturingObserver<>();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0), indexed);
    final String indexId = indexed.value.getIndex().getIndexId();

    final CapturingObserver<SetIndexAliasResponse> set = new CapturingObserver<>();
    service.setIndexAlias(SetIndexAliasRequest.newBuilder()
        .setAlias("legal-current").setIndexId(indexId).build(), set);
    assertNull(set.error);
    assertEquals(indexId, set.value.getAlias().getIndexId());

    final CapturingObserver<SearchIndexResponse> searched = new CapturingObserver<>();
    service.searchIndex(request("legal-current", "alpha", 1), searched);
    assertNull(searched.error);
    assertEquals(indexId, searched.value.getIndex().getIndexId());
    assertEquals("doc-1", searched.value.getHits(0).getDocumentId());

    final CapturingObserver<ListIndexAliasesResponse> listed = new CapturingObserver<>();
    service.listIndexAliases(ListIndexAliasesRequest.getDefaultInstance(), listed);
    assertEquals(1, listed.value.getAliasesCount());

    final CapturingObserver<DeleteIndexAliasResponse> deleted = new CapturingObserver<>();
    service.deleteIndexAlias(DeleteIndexAliasRequest.newBuilder()
        .setAlias("legal-current").build(), deleted);
    assertTrue(deleted.value.getDeleted());

    final CapturingObserver<SearchIndexResponse> missing = new CapturingObserver<>();
    service.searchIndex(request("legal-current", "alpha", 1), missing);
    assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(missing.error).getCode());
  }

  @Test
  void rejectsAliasCollisionsAndUnknownAliasTargets() {
    final DynamicSearchIndexRegistry dynamicRegistry = new DynamicSearchIndexRegistry();
    final EmbeddingRoute workspaceRoute = EmbeddingRoute.newBuilder()
        .setModelId("demo")
        .setBackendId("static")
        .setVectorSpaceId("demo-space")
        .build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of()),
        dynamicRegistry,
        new StubEmbeddingProvider(workspaceRoute, 2, List.of(workspaceRoute),
            new float[] {1, 0}));
    final CapturingObserver<IndexDocumentsResponse> indexed = new CapturingObserver<>();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0), indexed);
    final String indexId = indexed.value.getIndex().getIndexId();

    final CapturingObserver<SetIndexAliasResponse> collision = new CapturingObserver<>();
    service.setIndexAlias(SetIndexAliasRequest.newBuilder()
        .setAlias(indexId).setIndexId(indexId).build(), collision);
    assertEquals(Status.Code.INVALID_ARGUMENT,
        Status.fromThrowable(collision.error).getCode());

    final CapturingObserver<SetIndexAliasResponse> unknown = new CapturingObserver<>();
    service.setIndexAlias(SetIndexAliasRequest.newBuilder()
        .setAlias("legal-current").setIndexId("missing-index").build(), unknown);
    assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(unknown.error).getCode());
  }

  @Test
  void reindexesAWorkspaceIntoANewVectorSpaceAndSwapsTheAlias() {
    final DynamicSearchIndexRegistry dynamicRegistry = new DynamicSearchIndexRegistry();
    final EmbeddingRoute newSpaceRoute = EmbeddingRoute.newBuilder()
        .setModelId("demo")
        .setBackendId("static")
        .setVectorSpaceId("demo-space-v2")
        .build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of()),
        dynamicRegistry,
        new StubEmbeddingProvider(newSpaceRoute, 2, List.of(newSpaceRoute),
            new float[] {0, 1}),
        IndexAliasRegistry.inMemory());
    final CapturingObserver<IndexDocumentsResponse> indexed = new CapturingObserver<>();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0), indexed);
    final String sourceId = indexed.value.getIndex().getIndexId();
    final CapturingObserver<SetIndexAliasResponse> aliased = new CapturingObserver<>();
    service.setIndexAlias(SetIndexAliasRequest.newBuilder()
        .setAlias("legal-current").setIndexId(sourceId).build(), aliased);

    final CapturingObserver<ReindexIndexResponse> reindexed = new CapturingObserver<>();
    service.reindexIndex(ReindexIndexRequest.newBuilder()
        .setIndexId("legal-current")
        .setEmbedding(EmbeddingSelector.newBuilder().setModelId("demo"))
        .setAlias("legal-current")
        .build(), reindexed);

    assertNull(reindexed.error);
    assertEquals(sourceId, reindexed.value.getSourceIndexId());
    final SearchIndexDescriptor built = reindexed.value.getIndex();
    assertFalse(built.getIndexId().equals(sourceId));
    assertEquals("demo-space-v2", built.getEmbeddingRoute().getVectorSpaceId());
    assertEquals(1, reindexed.value.getReindexedDocuments());
    assertEquals(1, reindexed.value.getReindexedChunks());

    final CapturingObserver<SearchIndexResponse> searched = new CapturingObserver<>();
    service.searchIndex(request("legal-current", "alpha", 1), searched);
    assertNull(searched.error);
    assertEquals(built.getIndexId(), searched.value.getIndex().getIndexId());
    assertEquals("doc-1", searched.value.getHits(0).getDocumentId());

    final CapturingObserver<ListSearchIndexesResponse> listed = new CapturingObserver<>();
    service.listSearchIndexes(ListSearchIndexesRequest.getDefaultInstance(), listed);
    assertEquals(2, listed.value.getIndexesCount());
    assertTrue(listed.value.getIndexesList().stream()
        .anyMatch(descriptor -> descriptor.getIndexId().equals(sourceId)));
  }

  @Test
  void reindexValidatesItsSelectorAndSource() {
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of()),
        new DynamicSearchIndexRegistry(),
        new StubEmbeddingProvider(route(), 2));

    final CapturingObserver<ReindexIndexResponse> missingSelector = new CapturingObserver<>();
    service.reindexIndex(ReindexIndexRequest.newBuilder()
        .setIndexId("workspace-unknown").build(), missingSelector);
    assertEquals(Status.Code.INVALID_ARGUMENT,
        Status.fromThrowable(missingSelector.error).getCode());

    final CapturingObserver<ReindexIndexResponse> unknownSource = new CapturingObserver<>();
    service.reindexIndex(ReindexIndexRequest.newBuilder()
        .setIndexId("workspace-unknown")
        .setEmbedding(EmbeddingSelector.newBuilder().setModelId("demo"))
        .build(), unknownSource);
    assertEquals(Status.Code.NOT_FOUND,
        Status.fromThrowable(unknownSource.error).getCode());
  }

  @Test
  void executesCompoundQueriesOverADynamicWorkspaceWithMatchedSpans() {
    final DynamicSearchIndexRegistry dynamicRegistry = new DynamicSearchIndexRegistry();
    final EmbeddingRoute workspaceRoute = EmbeddingRoute.newBuilder()
        .setModelId("demo")
        .setBackendId("static")
        .setVectorSpaceId("demo-space")
        .build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of()),
        dynamicRegistry,
        new StubEmbeddingProvider(workspaceRoute, 2, List.of(workspaceRoute),
            new float[] {1, 0}));
    final CapturingObserver<IndexDocumentsResponse> indexed = new CapturingObserver<>();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha bravo", 1, 0), indexed);
    assertNull(indexed.error);
    final String indexId = indexed.value.getIndex().getIndexId();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(indexId, "doc-2", "charlie delta", 0, 1),
        new CapturingObserver<>());

    final QueryNode compound = QueryNode.newBuilder()
        .setJoin(JoinClause.newBuilder()
            .setOperator(JoinOperator.JOIN_OPERATOR_OR)
            .addOperands(QueryNode.newBuilder()
                .setSemantic(SemanticClause.newBuilder()
                    .setDocument(OpenNlpDocument.newBuilder().setRawText("alpha"))))
            .addOperands(QueryNode.newBuilder()
                .setTerm(TermClause.newBuilder().setText("alpha"))))
        .build();
    final CapturingObserver<SearchIndexResponse> searched = new CapturingObserver<>();
    service.searchIndex(compoundRequest(indexId, compound, 2), searched);

    assertNull(searched.error);
    assertEquals(2, searched.value.getHitsCount());
    final var best = searched.value.getHits(0);
    assertEquals("doc-1", best.getDocumentId());
    assertEquals(1.0, best.getScore(), 1e-9);
    assertEquals(1, best.getMatchedSpansCount());
    assertEquals("alpha", best.getMatchedSpans(0).getTerm());
    assertEquals(0, best.getMatchedSpans(0).getStart());
    assertEquals(5, best.getMatchedSpans(0).getEnd());
    assertTrue(searched.value.hasQueryEmbeddingRoute());
    assertTrue(searched.value.getHits(1).getScore() >= 0
        && searched.value.getHits(1).getScore() <= 1);
  }

  @Test
  void compoundKeywordQueriesNeedNoEmbeddingRoute() {
    final DynamicSearchIndexRegistry dynamicRegistry = new DynamicSearchIndexRegistry();
    final EmbeddingRoute workspaceRoute = EmbeddingRoute.newBuilder()
        .setModelId("demo")
        .setBackendId("static")
        .setVectorSpaceId("demo-space")
        .build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of()),
        dynamicRegistry,
        new StubEmbeddingProvider(workspaceRoute, 2, List.of(workspaceRoute),
            new float[] {1, 0}));
    final CapturingObserver<IndexDocumentsResponse> indexed = new CapturingObserver<>();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha bravo", 1, 0), indexed);
    final String indexId = indexed.value.getIndex().getIndexId();

    final QueryNode compound = QueryNode.newBuilder()
        .setTerm(TermClause.newBuilder().setText("bravo"))
        .build();
    final CapturingObserver<SearchIndexResponse> searched = new CapturingObserver<>();
    service.searchIndex(compoundRequest(indexId, compound, 1), searched);

    assertNull(searched.error);
    assertFalse(searched.value.hasQueryEmbeddingRoute());
    assertEquals("bravo", searched.value.getHits(0).getMatchedSpans(0).getTerm());
  }

  @Test
  void compoundSemanticClausesUseTheSelectedProvidersVectorSearch() {
    final SearchIndexDescriptor descriptor = SearchIndexRegistryTest.descriptor("legal");
    final SearchResult providerFirst = result("provider-first", "chunk-b", 0.8);
    final SearchResult providerSecond = result("raw-vector-first", "chunk-a", -0.8);
    final AtomicInteger searches = new AtomicInteger();
    final SearchIndexProvider provider = new SearchIndexProvider() {
      @Override
      public SearchIndexDescriptor descriptor() {
        return descriptor;
      }

      @Override
      public List<SearchResult> search(float[] queryVector, int topK) {
        searches.incrementAndGet();
        return List.of(providerFirst, providerSecond);
      }

      @Override
      public List<QueryCandidate> queryCandidates() {
        return List.of(
            new QueryCandidate(providerSecond.record(), new float[] {1, 0, 0, 0}),
            new QueryCandidate(providerFirst.record(), new float[] {-1, 0, 0, 0}));
      }
    };
    final OpenNlpSearchServiceImpl service = service(provider);
    final CapturingObserver<SearchIndexResponse> searched = new CapturingObserver<>();

    service.searchIndex(compoundRequest("legal", QueryNode.newBuilder()
        .setSemantic(SemanticClause.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText("query")))
        .build(), 2), searched);

    assertNull(searched.error);
    assertEquals(1, searches.get());
    assertEquals(List.of("provider-first", "raw-vector-first"),
        searched.value.getHitsList().stream().map(hit -> hit.getDocumentId()).toList());
  }

  @Test
  void rejectsRequestsWithoutExactlyOneQueryForm() {
    final OpenNlpSearchServiceImpl service = service(
        provider(SearchIndexRegistryTest.descriptor("legal"), List.of()));

    assertStatus(service, SearchIndexRequest.newBuilder()
        .setIndexId("legal").setTopK(1).build(), Status.Code.INVALID_ARGUMENT);
  }

  @Test
  void compoundQueriesOnIndexesWithoutCandidatesReportUnimplemented() {
    final OpenNlpSearchServiceImpl service = service(
        provider(SearchIndexRegistryTest.descriptor("legal"), List.of()));

    final QueryNode compound = QueryNode.newBuilder()
        .setTerm(TermClause.newBuilder().setText("alpha"))
        .build();
    assertStatus(service, compoundRequest("legal", compound, 1), Status.Code.UNIMPLEMENTED);
  }

  @Test
  void celClausesWithoutAnInstalledEvaluatorReportUnimplemented() {
    final DynamicSearchIndexRegistry dynamicRegistry = new DynamicSearchIndexRegistry();
    final EmbeddingRoute workspaceRoute = EmbeddingRoute.newBuilder()
        .setModelId("demo")
        .setBackendId("static")
        .setVectorSpaceId("demo-space")
        .build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of()),
        dynamicRegistry,
        new StubEmbeddingProvider(workspaceRoute, 2, List.of(workspaceRoute),
            new float[] {1, 0}));
    final CapturingObserver<IndexDocumentsResponse> indexed = new CapturingObserver<>();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0), indexed);
    final String indexId = indexed.value.getIndex().getIndexId();

    final QueryNode compound = QueryNode.newBuilder()
        .setJoin(JoinClause.newBuilder()
            .setOperator(JoinOperator.JOIN_OPERATOR_AND)
            .addOperands(QueryNode.newBuilder()
                .setTerm(TermClause.newBuilder().setText("alpha")))
            .addOperands(QueryNode.newBuilder()
                .setCelFilter(CelFilterClause.newBuilder()
                    .setExpression("metadata.published == true"))))
        .build();
    assertStatus(service, compoundRequest(indexId, compound, 1), Status.Code.UNIMPLEMENTED);
  }

  @Test
  void rejectsUnknownIndexBlankQueryAndInvalidTopK() {
    final OpenNlpSearchServiceImpl service = service(
        provider(SearchIndexRegistryTest.descriptor("legal"), List.of()));

    assertStatus(service, request("missing", "query", 1), Status.Code.NOT_FOUND);
    assertStatus(service, request("legal", " ", 1), Status.Code.INVALID_ARGUMENT);
    assertStatus(service, request("legal", "query", 0), Status.Code.INVALID_ARGUMENT);
    assertStatus(service, request("legal", "query", 51), Status.Code.INVALID_ARGUMENT);
    assertStatus(service, request("legal", "x".repeat(1025), 1), Status.Code.INVALID_ARGUMENT);
  }

  @Test
  void embedsOnDeclaredRouteAndReturnsStableNegativeScoresWithProvenance() {
    final SearchIndexDescriptor descriptor = SearchIndexRegistryTest.descriptor("legal");
    final SearchIndexProvider provider = provider(descriptor, List.of(
        result("doc-b", "chunk-b", -0.5),
        result("doc-a", "chunk-a", 0.75),
        result("doc-c", "chunk-c", -0.5)));
    final OpenNlpSearchServiceImpl service = service(provider);
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service.searchIndex(request("legal", "constitutional liberty", 3), observer);

    assertNull(observer.error);
    assertTrue(observer.completed);
    assertEquals(List.of("chunk-a", "chunk-b", "chunk-c"), observer.value.getHitsList().stream()
        .map(hit -> hit.getChunkId()).toList());
    assertEquals(-0.5, observer.value.getHits(1).getScore());
    assertEquals("mini-v1", observer.value.getQueryEmbeddingRoute().getVectorSpaceId());
    assertEquals(observer.value.getHits(0).getDocumentId(),
        observer.value.getSourceDocuments(0).getDocId());
    assertEquals(descriptor, observer.value.getIndex());
  }

  @Test
  void returnsEveryHitForAnExhaustiveTurboQuantIndex() {
    final SearchIndexDescriptor descriptor = SearchIndexRegistryTest.descriptor("legal")
        .toBuilder().setSize(3).setMaxTopK(3).setSupportsAllHits(true).build();
    final SearchIndexProvider provider = provider(descriptor, List.of(
        result("doc-a", "chunk-a", 0.9),
        result("doc-b", "chunk-b", 0.8),
        result("doc-c", "chunk-c", 0.7)));
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service(provider).searchIndex(SearchIndexRequest.newBuilder()
        .setIndexId("legal")
        .setQuery(OpenNlpDocument.newBuilder().setRawText("query"))
        .setAllHits(true)
        .build(), observer);

    assertNull(observer.error);
    assertEquals(3, observer.value.getHitsCount());
    assertFalse(observer.value.getTruncated());
  }

  @Test
  void returnsTheFiftyThousandHitExhaustiveSafetyCeiling() {
    final int resultCount = SearchIndexBundleConfiguration.MAX_ALL_HITS_LIMIT;
    final SearchIndexDescriptor descriptor = SearchIndexRegistryTest.descriptor("legal")
        .toBuilder()
        .setSize(resultCount)
        .setMaxTopK(1)
        .setMaxResponseBytes(32 * 1024 * 1024)
        .setSupportsAllHits(true)
        .build();
    final List<SearchResult> results = new ArrayList<>(resultCount);
    for (int index = 0; index < resultCount; index++) {
      results.add(result("doc", "chunk-" + index, 0.5, "Shared source"));
    }
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service(provider(descriptor, results)).searchIndex(SearchIndexRequest.newBuilder()
        .setIndexId("legal")
        .setQuery(OpenNlpDocument.newBuilder().setRawText("query"))
        .setAllHits(true)
        .build(), observer);

    assertNull(observer.error);
    assertEquals(resultCount, observer.value.getHitsCount());
    assertEquals(1, observer.value.getSourceDocumentsCount());
    assertFalse(observer.value.getTruncated());
  }

  @Test
  void deduplicatesSourceDocumentsAcrossChunkHits() {
    final String source = "One source document with two independently ranked chunks.";
    final SearchIndexDescriptor descriptor = SearchIndexRegistryTest.descriptor("legal")
        .toBuilder().setSize(2).setMaxTopK(2).build();
    final SearchIndexProvider provider = provider(descriptor, List.of(
        result("doc", "chunk-a", 0.9, source),
        result("doc", "chunk-b", 0.8, source)));
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service(provider).searchIndex(request("legal", "query", 2), observer);

    assertNull(observer.error);
    assertEquals(2, observer.value.getHitsCount());
    assertEquals(1, observer.value.getSourceDocumentsCount());
    assertEquals("doc", observer.value.getSourceDocuments(0).getDocId());
    assertEquals("default", observer.value.getHits(0).getChunkGroupId());
  }

  @Test
  void rejectsExhaustiveSearchWhenTheIndexDoesNotAdvertiseIt() {
    final OpenNlpSearchServiceImpl service = service(
        provider(SearchIndexRegistryTest.descriptor("legal"), List.of()));
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service.searchIndex(SearchIndexRequest.newBuilder()
        .setIndexId("legal")
        .setQuery(OpenNlpDocument.newBuilder().setRawText("query"))
        .setAllHits(true)
        .build(), observer);

    assertEquals(Status.Code.FAILED_PRECONDITION,
        Status.fromThrowable(observer.error).getCode());
  }

  @Test
  void acceptsDifferentBackendServingTheSameVectorSpace() {
    final SearchIndexProvider provider = provider(
        SearchIndexRegistryTest.descriptor("legal"), List.of(result("doc", "chunk", 1)));
    final EmbeddingRoute alternate = route().toBuilder().setBackendId("onnx").build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of(provider)), new StubEmbeddingProvider(alternate, 4));
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service.searchIndex(request("legal", "query", 1), observer);

    assertNull(observer.error);
    assertEquals("onnx", observer.value.getQueryEmbeddingRoute().getBackendId());
  }

  @Test
  void selectsACompatibleSecondaryRouteWhenTheDefaultVectorSpaceIsIncompatible() {
    final SearchIndexProvider provider = provider(
        SearchIndexRegistryTest.descriptor("legal"), List.of(result("doc", "chunk", 1)));
    final EmbeddingRoute incompatibleDefault = route().toBuilder()
        .setBackendId("default")
        .setVectorSpaceId("other-space")
        .build();
    final EmbeddingRoute compatibleSecondary = route().toBuilder()
        .setBackendId("secondary")
        .setPrimary(false)
        .build();
    final StubEmbeddingProvider embeddings = new StubEmbeddingProvider(
        compatibleSecondary, 4, List.of(incompatibleDefault, compatibleSecondary));
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of(provider)), embeddings);
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service.searchIndex(request("legal", "query", 1), observer);

    assertNull(observer.error);
    assertEquals("secondary", embeddings.requestedBackend.get());
    assertEquals("secondary", observer.value.getQueryEmbeddingRoute().getBackendId());
  }

  @Test
  void rejectsEmbeddingRouteDriftBeforeSearching() {
    final SearchIndexProvider provider = provider(
        SearchIndexRegistryTest.descriptor("legal"), List.of(result("doc", "chunk", 1)));
    final EmbeddingRoute drifted = route().toBuilder().setVectorSpaceId("other-space").build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of(provider)), new StubEmbeddingProvider(drifted, 4));
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service.searchIndex(request("legal", "query", 1), observer);

    assertEquals(Status.Code.FAILED_PRECONDITION,
        Status.fromThrowable(observer.error).getCode());
  }

  @Test
  void truncatesDeterministicallyBeforeExceedingTheResponseByteLimit() {
    final SearchIndexDescriptor descriptor = SearchIndexRegistryTest.descriptor("bounded")
        .toBuilder().setMaxResponseBytes(1_000).build();
    final String largeText = "bounded response text ".repeat(100);
    final SearchIndexProvider provider = provider(descriptor, List.of(
        result("doc-a", "chunk-a", 0.9, largeText),
        result("doc-b", "chunk-b", 0.8, largeText)));
    final OpenNlpSearchServiceImpl service = service(provider);
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service.searchIndex(request("bounded", "query", 2), observer);

    assertNull(observer.error);
    assertTrue(observer.value.getTruncated());
    assertTrue(observer.value.getSerializedSize() <= descriptor.getMaxResponseBytes());
    assertEquals(0, observer.value.getHitsCount());
  }

  @Test
  void rejectsAnActualEmbeddingRouteThatMakesEvenAnEmptyResponseOversized() {
    final SearchIndexDescriptor descriptor = SearchIndexRegistryTest.descriptor("bounded")
        .toBuilder().setMaxResponseBytes(700).build();
    final SearchIndexProvider provider = provider(descriptor, List.of());
    final EmbeddingRoute oversizedActualRoute = route().toBuilder()
        .setBackendId("x".repeat(1_000))
        .build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of(provider)),
        new StubEmbeddingProvider(oversizedActualRoute, 4));
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service.searchIndex(request("bounded", "query", 1), observer);

    assertEquals(Status.Code.FAILED_PRECONDITION,
        Status.fromThrowable(observer.error).getCode());
    assertTrue(Status.fromThrowable(observer.error).getDescription()
        .contains("max_response_bytes"));
  }

  @Test
  void rejectsConfiguredDimensionMismatchAtStartup() {
    final SearchIndexProvider provider = provider(
        SearchIndexRegistryTest.descriptor("legal"), List.of());

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new OpenNlpSearchServiceImpl(new SearchIndexRegistry(List.of(provider)),
            new StubEmbeddingProvider(route(), 3)));
    assertTrue(exception.getMessage().contains("dimension"));
  }

  @Test
  void mapsUnexpectedProviderFailureToInternalWithoutLeakingDetail() {
    final SearchIndexDescriptor descriptor = SearchIndexRegistryTest.descriptor("legal");
    final SearchIndexProvider provider = new SearchIndexProvider() {
      @Override
      public SearchIndexDescriptor descriptor() {
        return descriptor;
      }

      @Override
      public List<SearchResult> search(float[] queryVector, int topK) {
        throw new IllegalStateException("secret provider detail");
      }
    };
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service(provider).searchIndex(request("legal", "query", 1), observer);

    final Status status = Status.fromThrowable(observer.error);
    assertEquals(Status.Code.INTERNAL, status.getCode());
    assertEquals("Internal server error", status.getDescription());
    assertFalse(observer.completed);
  }

  @Test
  void rejectsAProviderThatReturnsMoreThanTopK() {
    final SearchIndexDescriptor descriptor = SearchIndexRegistryTest.descriptor("legal");
    final SearchIndexProvider provider = new SearchIndexProvider() {
      @Override
      public SearchIndexDescriptor descriptor() {
        return descriptor;
      }

      @Override
      public List<SearchResult> search(float[] queryVector, int topK) {
        return List.of(
            result("doc-a", "chunk-a", 1),
            result("doc-b", "chunk-b", 0.5));
      }
    };
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service(provider).searchIndex(request("legal", "query", 1), observer);

    final Status status = Status.fromThrowable(observer.error);
    assertEquals(Status.Code.INTERNAL, status.getCode());
    assertEquals("Internal server error", status.getDescription());
  }

  @Test
  void rejectsANonFiniteQueryVector() {
    final SearchIndexProvider provider = provider(
        SearchIndexRegistryTest.descriptor("legal"), List.of());
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of(provider)),
        new StubEmbeddingProvider(route(), 4, List.of(route()),
            new float[] {Float.NaN, 0, 0, 0}));
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service.searchIndex(request("legal", "query", 1), observer);

    assertEquals(Status.Code.INTERNAL, Status.fromThrowable(observer.error).getCode());
  }

  @Test
  void deleteSearchIndexDropsItsAliasesAndCollectionMembership() {
    final DynamicSearchIndexRegistry dynamicRegistry = new DynamicSearchIndexRegistry();
    final EmbeddingRoute workspaceRoute = EmbeddingRoute.newBuilder()
        .setModelId("demo")
        .setBackendId("static")
        .setVectorSpaceId("demo-space")
        .build();
    final IndexAliasRegistry aliases = IndexAliasRegistry.inMemory();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of()),
        dynamicRegistry,
        new StubEmbeddingProvider(workspaceRoute, 2, List.of(workspaceRoute),
            new float[] {1, 0}),
        aliases,
        SearchCollectionRegistry.inMemory(dynamicRegistry, artifactId -> List.of("alpha")));
    final CapturingObserver<IndexDocumentsResponse> first = new CapturingObserver<>();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha beta", 1, 0), first);
    final CapturingObserver<IndexDocumentsResponse> second = new CapturingObserver<>();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(null, "doc-2", "gamma delta", 1, 0), second);
    final String doomed = first.value.getIndex().getIndexId();
    final String kept = second.value.getIndex().getIndexId();
    service.setIndexAlias(SetIndexAliasRequest.newBuilder()
        .setAlias("doomed-current").setIndexId(doomed).build(), new CapturingObserver<>());
    service.setIndexAlias(SetIndexAliasRequest.newBuilder()
        .setAlias("kept-current").setIndexId(kept).build(), new CapturingObserver<>());
    final CapturingObserver<SetCollectionResponse> set = new CapturingObserver<>();
    service.setCollection(SetCollectionRequest.newBuilder()
        .setCollectionId("mixed")
        .setDisplayName("Mixed")
        .addMemberIndexIds(doomed)
        .addMemberIndexIds(kept)
        .build(), set);
    assertNull(set.error);

    final CapturingObserver<DeleteSearchIndexResponse> deleted = new CapturingObserver<>();
    service.deleteSearchIndex(DeleteSearchIndexRequest.newBuilder()
        .setIndexId(doomed).build(), deleted);
    assertNull(deleted.error);
    assertTrue(deleted.value.getDeleted());

    // The alias that pointed at the deleted index is gone; the other one stays.
    assertEquals(List.of("kept-current"),
        aliases.aliases().stream().map(IndexAlias::getAlias).toList());
    // The collection keeps its other member and can be saved again as reported.
    final CapturingObserver<GetCollectionResponse> got = new CapturingObserver<>();
    service.getCollection(GetCollectionRequest.newBuilder()
        .setCollectionId("mixed").build(), got);
    assertNull(got.error);
    assertEquals(List.of(kept), got.value.getCollection().getMemberIndexIdsList());
    final CapturingObserver<SetCollectionResponse> resaved = new CapturingObserver<>();
    service.setCollection(SetCollectionRequest.newBuilder()
        .setCollectionId("mixed")
        .setDisplayName("Mixed")
        .addAllMemberIndexIds(got.value.getCollection().getMemberIndexIdsList())
        .build(), resaved);
    assertNull(resaved.error);
  }

  @Test
  void collectionsResolveMemberAliasesAndAnswerCrudCalls() {
    final DynamicSearchIndexRegistry dynamicRegistry = new DynamicSearchIndexRegistry();
    final EmbeddingRoute workspaceRoute = EmbeddingRoute.newBuilder()
        .setModelId("demo")
        .setBackendId("static")
        .setVectorSpaceId("demo-space")
        .build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of()),
        dynamicRegistry,
        new StubEmbeddingProvider(workspaceRoute, 2, List.of(workspaceRoute),
            new float[] {1, 0}),
        IndexAliasRegistry.inMemory(),
        SearchCollectionRegistry.inMemory(dynamicRegistry,
            artifactId -> List.of("alpha")));
    final CapturingObserver<IndexDocumentsResponse> indexed = new CapturingObserver<>();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha beta", 1, 0), indexed);
    final String indexId = indexed.value.getIndex().getIndexId();
    final CapturingObserver<SetIndexAliasResponse> aliased = new CapturingObserver<>();
    service.setIndexAlias(SetIndexAliasRequest.newBuilder()
        .setAlias("legal-current").setIndexId(indexId).build(), aliased);
    assertNull(aliased.error);

    final CapturingObserver<SetCollectionResponse> set = new CapturingObserver<>();
    service.setCollection(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds("legal-current")
        .setVocabularyArtifactId("vocabulary-1")
        .build(), set);
    assertNull(set.error);
    assertEquals(List.of(indexId), set.value.getCollection().getMemberIndexIdsList());

    final CapturingObserver<GetCollectionResponse> got = new CapturingObserver<>();
    service.getCollection(GetCollectionRequest.newBuilder()
        .setCollectionId("legal").build(), got);
    assertNull(got.error);
    assertEquals(2, got.value.getCollection().getTermStatisticsCount());
    assertEquals(1, got.value.getCollection().getDrift().getNewTerms());
    assertEquals(0.5, got.value.getCollection().getDrift().getVocabularyCoverage());

    final CapturingObserver<ListCollectionsResponse> listed = new CapturingObserver<>();
    service.listCollections(ListCollectionsRequest.getDefaultInstance(), listed);
    assertNull(listed.error);
    assertEquals(1, listed.value.getCollectionsCount());
    assertEquals(0, listed.value.getCollections(0).getTermStatisticsCount());

    final CapturingObserver<GetCollectionResponse> missing = new CapturingObserver<>();
    service.getCollection(GetCollectionRequest.newBuilder()
        .setCollectionId("unknown").build(), missing);
    assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(missing.error).getCode());

    final CapturingObserver<SetCollectionResponse> unknownMember = new CapturingObserver<>();
    service.setCollection(SetCollectionRequest.newBuilder()
        .setCollectionId("broken")
        .setDisplayName("Broken")
        .addMemberIndexIds("missing-index")
        .build(), unknownMember);
    assertEquals(Status.Code.NOT_FOUND,
        Status.fromThrowable(unknownMember.error).getCode());

    final CapturingObserver<SetCollectionResponse> blank = new CapturingObserver<>();
    service.setCollection(SetCollectionRequest.newBuilder()
        .setDisplayName("Blank id").build(), blank);
    assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(blank.error).getCode());

    final CapturingObserver<DeleteCollectionResponse> deleted = new CapturingObserver<>();
    service.deleteCollection(DeleteCollectionRequest.newBuilder()
        .setCollectionId("legal").build(), deleted);
    assertNull(deleted.error);
    assertTrue(deleted.value.getDeleted());
    final CapturingObserver<DeleteCollectionResponse> gone = new CapturingObserver<>();
    service.deleteCollection(DeleteCollectionRequest.newBuilder()
        .setCollectionId("legal").build(), gone);
    assertFalse(gone.value.getDeleted());
  }

  @Test
  void collectionsRejectStartupBundleMembers() {
    final OpenNlpSearchServiceImpl service = service(
        provider(SearchIndexRegistryTest.descriptor("static-1"), List.of()));

    final CapturingObserver<SetCollectionResponse> rejected = new CapturingObserver<>();
    service.setCollection(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds("static-1")
        .build(), rejected);

    assertEquals(Status.Code.FAILED_PRECONDITION,
        Status.fromThrowable(rejected.error).getCode());
  }


  @Test
  void watchDoesNotRegisterAfterImmediateTransportCancellation() {
    final DynamicSearchIndexRegistry indexes = new DynamicSearchIndexRegistry();
    final String indexId = indexes
        .index(DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0))
        .getIndex().getIndexId();
    final SearchCollectionRegistry collections = SearchCollectionRegistry.inMemory(
        indexes, artifactId -> List.of("alpha"));
    collections.set(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds(indexId)
        .build());
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of()), indexes,
        new StubEmbeddingProvider(route(), 4), IndexAliasRegistry.inMemory(), collections);
    final ImmediatelyCancelledObserver events = new ImmediatelyCancelledObserver();

    service.watchCollection(WatchCollectionRequest.newBuilder()
        .setCollectionId("legal").build(), events);
    collections.notifyIndexPersisted(indexId);

    assertTrue(events.values.isEmpty());
    assertNull(events.error);
    assertFalse(events.completed);
  }

  private static OpenNlpSearchServiceImpl service(SearchIndexProvider... providers) {
    return new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of(providers)), new StubEmbeddingProvider(route(), 4));
  }

  private static SearchIndexProvider provider(
      SearchIndexDescriptor descriptor, List<SearchResult> results) {
    return new SearchIndexProvider() {
      @Override
      public SearchIndexDescriptor descriptor() {
        return descriptor;
      }

      @Override
      public List<SearchResult> search(float[] queryVector, int topK) {
        return results.subList(0, Math.min(topK, results.size()));
      }
    };
  }

  private static SearchResult result(String documentId, String chunkId, double score) {
    return result(documentId, chunkId, score, "Retained source for " + chunkId);
  }

  private static SearchResult result(
      String documentId, String chunkId, double score, String text) {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setDocId(documentId)
        .setRawText(text)
        .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
        .build();
    return new SearchResult(new SearchRecord(documentId, chunkId, document,
        AnnotationSpan.newBuilder()
            .setStart(0)
            .setEnd(text.length())
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
            .build(), text), score);
  }

  private static SearchIndexRequest request(String indexId, String query, int topK) {
    return SearchIndexRequest.newBuilder()
        .setIndexId(indexId)
        .setQuery(OpenNlpDocument.newBuilder().setRawText(query))
        .setTopK(topK)
        .build();
  }

  private static SearchIndexRequest compoundRequest(
      String indexId, QueryNode compound, int topK) {
    return SearchIndexRequest.newBuilder()
        .setIndexId(indexId)
        .setCompoundQuery(compound)
        .setTopK(topK)
        .build();
  }

  private static EmbeddingRoute route() {
    return EmbeddingRoute.newBuilder()
        .setModelId("mini")
        .setBackendId("static")
        .setVectorSpaceId("mini-v1")
        .setArtifactHash("a".repeat(64))
        .setPrimary(true)
        .build();
  }

  private static void assertStatus(
      OpenNlpSearchServiceImpl service, SearchIndexRequest request, Status.Code expected) {
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();
    service.searchIndex(request, observer);
    assertNotNull(observer.error);
    assertEquals(expected, Status.fromThrowable(observer.error).getCode());
  }

  static final class StubEmbeddingProvider implements EmbeddingProvider {

    final EmbeddingRoute resultRoute;
    final int dimension;
    final List<EmbeddingRoute> routes;
    final float[] resultVector;
    final AtomicReference<String> requestedBackend = new AtomicReference<>();

    StubEmbeddingProvider(EmbeddingRoute resultRoute, int dimension) {
      this(resultRoute, dimension, List.of(route()));
    }

    StubEmbeddingProvider(
        EmbeddingRoute resultRoute, int dimension, List<EmbeddingRoute> routes) {
      this(resultRoute, dimension, routes, unitVector(dimension));
    }

    StubEmbeddingProvider(
        EmbeddingRoute resultRoute,
        int dimension,
        List<EmbeddingRoute> routes,
        float[] resultVector) {
      this.resultRoute = resultRoute;
      this.dimension = dimension;
      this.routes = routes;
      this.resultVector = resultVector.clone();
    }

    @Override
    public String backendId() {
      return "static";
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public Set<String> registeredModelIds() {
      return Set.of(resultRoute.getModelId());
    }

    @Override
    public boolean supportsModel(String modelId) {
      return modelId.equals(resultRoute.getModelId());
    }

    @Override
    public int embeddingDimension(String modelId) {
      return dimension;
    }

    @Override
    public float[] embed(String modelId, String text) {
      return new float[dimension];
    }

    @Override
    public EmbeddingBatchResult embedBatchResolved(
        String modelId, String backendId, List<String> texts) {
      requestedBackend.set(backendId);
      final List<float[]> vectors = new java.util.ArrayList<>(texts.size());
      for (int index = 0; index < texts.size(); index++) {
        vectors.add(resultVector.clone());
      }
      return new EmbeddingBatchResult(vectors, resultRoute);
    }

    @Override
    public List<EmbeddingRoute> routesForModel(String modelId) {
      return routes;
    }

    @Override
    public String modelArtifactHash(String modelId) {
      return "a".repeat(64);
    }

    static float[] unitVector(int dimension) {
      final float[] vector = new float[dimension];
      if (dimension > 0) {
        vector[0] = 1;
      }
      return vector;
    }
  }

  private static final class ImmediatelyCancelledObserver
      extends ServerCallStreamObserver<CollectionEvent> {

    private final List<CollectionEvent> values = new ArrayList<>();
    private Throwable error;
    private boolean completed;

    @Override
    public boolean isCancelled() {
      return true;
    }

    @Override
    public void setOnCancelHandler(Runnable handler) {
      handler.run();
    }

    @Override
    public void setCompression(String compression) {
    }

    @Override
    public boolean isReady() {
      return false;
    }

    @Override
    public void setOnReadyHandler(Runnable handler) {
    }

    @Override
    public void disableAutoInboundFlowControl() {
    }

    @Override
    public void request(int count) {
    }

    @Override
    public void setMessageCompression(boolean enable) {
    }

    @Override
    public void onNext(CollectionEvent value) {
      values.add(value);
    }

    @Override
    public void onError(Throwable throwable) {
      error = throwable;
    }

    @Override
    public void onCompleted() {
      completed = true;
    }
  }

  static final class CollectingObserver<T> implements StreamObserver<T> {

    final List<T> values = new ArrayList<>();
    Throwable error;
    boolean completed;

    @Override
    public void onNext(T value) {
      values.add(value);
    }

    @Override
    public void onError(Throwable error) {
      this.error = error;
    }

    @Override
    public void onCompleted() {
      completed = true;
    }
  }

  static final class CapturingObserver<T> implements StreamObserver<T> {

    T value;
    Throwable error;
    boolean completed;

    @Override
    public void onNext(T value) {
      this.value = value;
    }

    @Override
    public void onError(Throwable error) {
      this.error = error;
    }

    @Override
    public void onCompleted() {
      completed = true;
    }
  }
}
