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

/**
 * Search service RPC behavior that needs the persistent TurboQuant provider from the
 * opennlp-grpc-search-turboquant add-on: workspace persist/seal and watch lifecycle.
 */
class OpenNlpSearchServiceTurboQuantTest {

  @Test
  void persistsAndSealsAWorkspaceThroughGrpcMethods(@TempDir Path root) {
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
        new OpenNlpSearchServiceImplTest.StubEmbeddingProvider(workspaceRoute, 2, List.of(workspaceRoute),
            new float[] {1, 0}));
    final OpenNlpSearchServiceImplTest.CapturingObserver<IndexDocumentsResponse> indexed = new OpenNlpSearchServiceImplTest.CapturingObserver<>();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0).toBuilder()
            .setProvider(SearchProviderSelector.newBuilder()
                .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
            .build(),
        indexed);
    final String indexId = indexed.value.getIndex().getIndexId();

    final OpenNlpSearchServiceImplTest.CapturingObserver<PersistIndexResponse> persisted = new OpenNlpSearchServiceImplTest.CapturingObserver<>();
    service.persistIndex(PersistIndexRequest.newBuilder().setIndexId(indexId).build(),
        persisted);
    assertNull(persisted.error);
    assertTrue(persisted.value.getIndex().getPersisted());
    assertFalse(persisted.value.getIndex().getImmutable());

    final OpenNlpSearchServiceImplTest.CapturingObserver<SealIndexResponse> sealed = new OpenNlpSearchServiceImplTest.CapturingObserver<>();
    service.sealIndex(SealIndexRequest.newBuilder().setIndexId(indexId).build(), sealed);
    assertNull(sealed.error);
    assertTrue(sealed.value.getIndex().getImmutable());

    final OpenNlpSearchServiceImplTest.CapturingObserver<IndexDocumentsResponse> mutation = new OpenNlpSearchServiceImplTest.CapturingObserver<>();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(indexId, "doc-2", "beta", 0, 1), mutation);
    assertEquals(Status.Code.FAILED_PRECONDITION,
        Status.fromThrowable(mutation.error).getCode());
  }
  @Test
  void watchStreamsASnapshotFirstAndLifecycleEventsAfterwards(@TempDir Path root) {
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
        new OpenNlpSearchServiceImplTest.StubEmbeddingProvider(workspaceRoute, 2, List.of(workspaceRoute),
            new float[] {1, 0}),
        IndexAliasRegistry.inMemory(),
        SearchCollectionRegistry.inMemory(dynamicRegistry,
            artifactId -> List.of("alpha")));
    final OpenNlpSearchServiceImplTest.CapturingObserver<IndexDocumentsResponse> indexed = new OpenNlpSearchServiceImplTest.CapturingObserver<>();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0).toBuilder()
            .setProvider(SearchProviderSelector.newBuilder()
                .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
            .build(),
        indexed);
    final String indexId = indexed.value.getIndex().getIndexId();
    final OpenNlpSearchServiceImplTest.CapturingObserver<SetCollectionResponse> set = new OpenNlpSearchServiceImplTest.CapturingObserver<>();
    service.setCollection(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds(indexId)
        .setVocabularyArtifactId("vocabulary-1")
        .setDriftNewTermThreshold(1)
        .build(), set);
    assertNull(set.error);

    final OpenNlpSearchServiceImplTest.CollectingObserver<CollectionEvent> events = new OpenNlpSearchServiceImplTest.CollectingObserver<>();
    service.watchCollection(WatchCollectionRequest.newBuilder()
        .setCollectionId("legal").build(), events);
    assertEquals(1, events.values.size());
    assertEquals(CollectionEventKind.COLLECTION_EVENT_KIND_SNAPSHOT,
        events.values.get(0).getKind());

    final OpenNlpSearchServiceImplTest.CapturingObserver<PersistIndexResponse> persisted = new OpenNlpSearchServiceImplTest.CapturingObserver<>();
    service.persistIndex(PersistIndexRequest.newBuilder().setIndexId(indexId).build(),
        persisted);
    assertNull(persisted.error);
    assertEquals(2, events.values.size());
    assertEquals(CollectionEventKind.COLLECTION_EVENT_KIND_INDEX_PERSISTED,
        events.values.get(1).getKind());
    assertEquals(indexId, events.values.get(1).getIndexId());

    final OpenNlpSearchServiceImplTest.CapturingObserver<IndexDocumentsResponse> extended = new OpenNlpSearchServiceImplTest.CapturingObserver<>();
    service.indexDocuments(
        DynamicSearchIndexRegistryTest.request(indexId, "doc-2", "beta", 0, 1), extended);
    assertNull(extended.error);
    assertEquals(3, events.values.size());
    assertEquals(CollectionEventKind.COLLECTION_EVENT_KIND_DRIFT_THRESHOLD_CROSSED,
        events.values.get(2).getKind());
    assertEquals(1, events.values.get(2).getCollection().getDrift().getNewTerms());

    final OpenNlpSearchServiceImplTest.CapturingObserver<DeleteCollectionResponse> deleted = new OpenNlpSearchServiceImplTest.CapturingObserver<>();
    service.deleteCollection(DeleteCollectionRequest.newBuilder()
        .setCollectionId("legal").build(), deleted);
    assertNull(deleted.error);
    assertTrue(events.completed);

    final OpenNlpSearchServiceImplTest.CollectingObserver<CollectionEvent> unknown = new OpenNlpSearchServiceImplTest.CollectingObserver<>();
    service.watchCollection(WatchCollectionRequest.newBuilder()
        .setCollectionId("legal").build(), unknown);
    assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(unknown.error).getCode());
  }
}
