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

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.Chunk;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.apache.opennlp.grpc.v1.EmbeddingSelector;
import org.apache.opennlp.grpc.v1.EmbeddingBackendSelector;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexComponent;
import org.apache.opennlp.grpc.v1.SearchComponentKind;
import org.apache.opennlp.grpc.v1.SearchProviderSelector;
import org.apache.opennlp.grpc.v1.StandardSearchProvider;
import org.apache.opennlp.grpc.v1.StandardEmbeddingBackend;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.apache.opennlp.grpc.spi.search.SearchResult;

/**
 * TurboQuant behavior of the server dynamic search registry, exercised with the
 * opennlp-grpc-search-turboquant add-on discovered from the classpath exactly as a
 * deployment discovers it. Shares the server module test fixtures via its test jar.
 */
class DynamicSearchIndexRegistryTurboQuantTest {

  @Test
  void buildsAndSearchesATurboQuantDynamicIndex() {
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry();
    final IndexDocumentsRequest turbo = DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
        .build();
    final var created = registry.index(turbo);

    assertEquals(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT,
        created.getIndex().getProvider().getStandard());
    final String indexId = created.getIndex().getIndexId();
    final var extended = registry.index(DynamicSearchIndexRegistryTest.request(indexId, "doc-2", "beta", 0, 1));
    assertEquals(2, extended.getIndex().getSize());
    assertEquals(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT,
        extended.getIndex().getProvider().getStandard());

    final List<SearchResult> hits = registry.require(indexId)
        .search(new float[] {1, 0}, 2);
    assertEquals(2, hits.size());
    assertEquals("doc-1", hits.getFirst().record().documentId());
    assertTrue(hits.getFirst().score() > hits.getLast().score());
    assertTrue(hits.getFirst().score() > 0.5);
    assertEquals("sentence", hits.getFirst().record().chunkGroupId());
    assertEquals(0, registry.retainedRawVectorValues(indexId));
  }
  @Test
  void turboQuantAdvertisesEveryBoundedChunkBeyondTheOldThousandHitCeiling() {
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry();
    final IndexDocumentsRequest base = DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0);
    final Chunk chunk = base.getDocuments(0).getChunkEmbeddingGroups(0).getChunks(0);
    final ChunkEmbeddingGroup.Builder group = base.getDocuments(0)
        .getChunkEmbeddingGroups(0).toBuilder().clearChunks();
    for (int index = 0; index < 1_001; index++) {
      group.addChunks(chunk);
    }
    final OpenNlpDocument document = base.getDocuments(0).toBuilder()
        .setChunkEmbeddingGroups(0, group)
        .build();
    final IndexDocumentsRequest turbo = base.toBuilder()
        .setDocuments(0, document)
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
        .build();

    final SearchIndexDescriptor descriptor = registry.index(turbo).getIndex();

    assertEquals(1_001, descriptor.getSize());
    assertEquals(1_001, descriptor.getMaxTopK());
  }
  @Test
  void turboQuantReplacementFiltersSupersededSegmentRows() {
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry();
    final IndexDocumentsRequest turbo = DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
        .build();
    final String indexId = registry.index(turbo).getIndex().getIndexId();

    registry.index(DynamicSearchIndexRegistryTest.request(indexId, "doc-1", "replacement", 0, 1));

    final List<SearchResult> hits = registry.require(indexId)
        .search(new float[] {1, 0}, 10);
    assertEquals(1, hits.size());
    assertEquals("replacement", hits.getFirst().record().indexedText());
    assertEquals(0, registry.retainedRawVectorValues(indexId));
  }
  @Test
  void persistsTurboQuantWithoutWritingRawFloatVectors(@TempDir Path root) throws Exception {
    final WorkspaceCheckpointStore store = new WorkspaceCheckpointStore(root);
    final DynamicSearchIndexRegistry registry =
        new DynamicSearchIndexRegistry(SearchProviderCatalog.discover(), store);
    final IndexDocumentsRequest turbo = DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
        .build();
    final String indexId = registry.index(turbo).getIndex().getIndexId();

    registry.persist(indexId);

    try (var input = Files.newInputStream(root.resolve(indexId)
        .resolve(WorkspaceCheckpointStore.CHUNKS_FILE))) {
      final org.apache.opennlp.grpc.v1.PersistedSearchChunk chunk =
          org.apache.opennlp.grpc.v1.PersistedSearchChunk.parseDelimitedFrom(input);
      assertNotNull(chunk);
      assertEquals(0, chunk.getVectorCount());
      assertFalse(chunk.getVectorId().isBlank());
      assertEquals(32, chunk.getVectorSha256().size());
    }
  }
  @Test
  void keepsTheProviderFixedAfterIndexCreation() {
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry();
    final var created = registry.index(DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0));

    final IndexDocumentsRequest mismatch =
        DynamicSearchIndexRegistryTest.request(created.getIndex().getIndexId(), "doc-2", "beta", 0, 1).toBuilder()
            .setProvider(SearchProviderSelector.newBuilder()
                .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
            .build();

    final AnalysisException failure =
        assertThrows(AnalysisException.class, () -> registry.index(mismatch));
    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, failure.getFailureType());
    assertEquals(1, registry.require(created.getIndex().getIndexId()).descriptor().getSize());
  }
  @Test
  void acceptsConfiguredCustomProviderInstances() {
    final SearchProviderCatalog catalog = SearchProviderCatalog.fromConfiguration(Map.of(
        "search.provider.fast-workspace.type", "turbo_quant"));
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry(catalog);

    final IndexDocumentsRequest custom = DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setProvider(SearchProviderSelector.newBuilder().setCustom("fast-workspace"))
        .build();
    final var created = registry.index(custom);

    assertEquals("fast-workspace", created.getIndex().getProvider().getCustom());
    final List<SearchResult> hits = registry.require(created.getIndex().getIndexId())
        .search(new float[] {1, 0}, 1);
    assertEquals("doc-1", hits.getFirst().record().documentId());

    final IndexDocumentsRequest mismatch =
        DynamicSearchIndexRegistryTest.request(created.getIndex().getIndexId(), "doc-2", "beta", 0, 1).toBuilder()
            .setProvider(SearchProviderSelector.newBuilder()
                .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
            .build();
    final AnalysisException failure =
        assertThrows(AnalysisException.class, () -> registry.index(mismatch));
    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, failure.getFailureType());
  }
  @Test
  void persistsAndRestoresATurboQuantWorkspaceAcrossRegistries(@TempDir Path root) {
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();
    final WorkspaceCheckpointStore store = new WorkspaceCheckpointStore(root);
    final DynamicSearchIndexRegistry registry =
        new DynamicSearchIndexRegistry(catalog, store);
    final IndexDocumentsRequest turbo = DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
        .build();
    final String indexId = registry.index(turbo).getIndex().getIndexId();

    final SearchIndexDescriptor persisted = registry.persist(indexId);
    assertTrue(persisted.getPersisted());
    registry.close();

    final DynamicSearchIndexRegistry restored =
        new DynamicSearchIndexRegistry(catalog, new WorkspaceCheckpointStore(root));
    final SearchIndexDescriptor descriptor = restored.descriptors().getFirst();
    assertEquals(indexId, descriptor.getIndexId());
    assertTrue(descriptor.getPersisted());
    assertFalse(descriptor.getImmutable());
    assertEquals(0, restored.retainedRawVectorValues(indexId));
    assertEquals("doc-1", restored.require(indexId)
        .search(new float[] {1, 0}, 1).getFirst().record().documentId());

    final var extended = restored.index(DynamicSearchIndexRegistryTest.request(indexId, "doc-2", "beta", 0, 1));
    assertEquals(2, extended.getIndex().getSize());
    assertEquals(0, restored.retainedRawVectorValues(indexId));
    assertEquals(2, restored.require(indexId).search(new float[] {1, 0}, 2).size());
  }
  @Test
  void sealedIndexesRejectMutationAndRestoreImmutable(@TempDir Path root) {
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();
    final DynamicSearchIndexRegistry registry =
        new DynamicSearchIndexRegistry(catalog, new WorkspaceCheckpointStore(root));
    final IndexDocumentsRequest turbo = DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
        .build();
    final String indexId = registry.index(turbo).getIndex().getIndexId();

    final SearchIndexDescriptor sealed = registry.seal(indexId);
    assertTrue(sealed.getImmutable());
    assertTrue(sealed.getPersisted());
    final AnalysisException mutation = assertThrows(AnalysisException.class,
        () -> registry.index(DynamicSearchIndexRegistryTest.request(indexId, "doc-2", "beta", 0, 1)));
    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, mutation.getFailureType());
    registry.close();

    final DynamicSearchIndexRegistry restored =
        new DynamicSearchIndexRegistry(catalog, new WorkspaceCheckpointStore(root));
    assertTrue(restored.descriptors().getFirst().getImmutable());
    assertThrows(AnalysisException.class,
        () -> restored.index(DynamicSearchIndexRegistryTest.request(indexId, "doc-2", "beta", 0, 1)));
    assertEquals("doc-1", restored.require(indexId)
        .search(new float[] {1, 0}, 1).getFirst().record().documentId());
  }
  @Test
  void deletingAPersistedIndexRemovesItsCheckpoint(@TempDir Path root) {
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();
    final DynamicSearchIndexRegistry registry =
        new DynamicSearchIndexRegistry(catalog, new WorkspaceCheckpointStore(root));
    final IndexDocumentsRequest turbo = DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
        .build();
    final String indexId = registry.index(turbo).getIndex().getIndexId();
    registry.persist(indexId);

    assertTrue(registry.delete(indexId));
    registry.close();

    final DynamicSearchIndexRegistry restored =
        new DynamicSearchIndexRegistry(catalog, new WorkspaceCheckpointStore(root));
    assertTrue(restored.descriptors().isEmpty());
  }
  @Test
  void failedCheckpointDeletionKeepsTheLiveIndexRegistered(@TempDir Path root)
      throws Exception {
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();
    final DynamicSearchIndexRegistry registry =
        new DynamicSearchIndexRegistry(catalog, new WorkspaceCheckpointStore(root));
    final IndexDocumentsRequest turbo = DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
        .build();
    final String indexId = registry.index(turbo).getIndex().getIndexId();
    registry.persist(indexId);
    final Path checkpoint = root.resolve(indexId);
    assumeTrue(Files.getFileStore(checkpoint).supportsFileAttributeView("posix"));
    final Set<PosixFilePermission> original = Files.getPosixFilePermissions(checkpoint);
    Files.setPosixFilePermissions(checkpoint, Set.of(
        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
    try {
      assertThrows(UncheckedIOException.class, () -> registry.delete(indexId));
      assertNotNull(registry.find(indexId));
    } finally {
      Files.setPosixFilePermissions(checkpoint, original);
    }
  }
  @Test
  void checkpointsRewritePersistedIndexesOnlyWhenContentChanged(@TempDir Path root) {
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();
    final DynamicSearchIndexRegistry registry =
        new DynamicSearchIndexRegistry(catalog, new WorkspaceCheckpointStore(root));
    final IndexDocumentsRequest turbo = DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
        .build();
    final String indexId = registry.index(turbo).getIndex().getIndexId();
    registry.persist(indexId);

    registry.index(DynamicSearchIndexRegistryTest.request(indexId, "doc-2", "beta", 0, 1));
    assertEquals(List.of(indexId), registry.checkpointPersistedIndexes());
    assertEquals(List.of(), registry.checkpointPersistedIndexes());
    registry.close();

    final DynamicSearchIndexRegistry restored =
        new DynamicSearchIndexRegistry(catalog, new WorkspaceCheckpointStore(root));
    assertEquals(2, restored.descriptors().getFirst().getSize());
  }
  @Test
  void restoreRecoversTheLastCompleteCheckpointFromAnInterruptedSwap(@TempDir Path root)
      throws Exception {
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry(
        catalog, new WorkspaceCheckpointStore(root));
    final IndexDocumentsRequest turbo = DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
        .build();
    final String indexId = registry.index(turbo).getIndex().getIndexId();
    registry.persist(indexId);
    registry.close();
    Files.move(root.resolve(indexId), root.resolve("." + indexId
        + "-old-00000000-0000-0000-0000-000000000000"));

    final DynamicSearchIndexRegistry restored = new DynamicSearchIndexRegistry(
        catalog, new WorkspaceCheckpointStore(root));

    assertEquals(List.of(indexId), restored.descriptors().stream()
        .map(SearchIndexDescriptor::getIndexId).toList());
  }
  @Test
  void restoreRejectsAProviderWithoutPersistentVectorCapabilities(@TempDir Path root)
      throws Exception {
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry(
        catalog, new WorkspaceCheckpointStore(root));
    final IndexDocumentsRequest turbo = DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
        .build();
    final String indexId = registry.index(turbo).getIndex().getIndexId();
    registry.persist(indexId);
    registry.close();
    final Path descriptor = root.resolve(indexId)
        .resolve(WorkspaceCheckpointStore.DESCRIPTOR_FILE);
    final Properties properties = new Properties();
    try (var input = Files.newInputStream(descriptor)) {
      properties.load(input);
    }
    // The keyword-only provider declares neither vector nor persistent capabilities.
    properties.setProperty("provider.instance", TermsSearchIndexProviderFactory.PROVIDER_ID);
    try (var output = Files.newOutputStream(descriptor)) {
      properties.store(output, "tampered provider capability");
    }

    final IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> new DynamicSearchIndexRegistry(catalog, new WorkspaceCheckpointStore(root)));

    assertTrue(failure.getMessage().contains("persistent live vector"));
  }
  @Test
  void restoreRejectsAnUnboundedProviderVectorSegmentCount(@TempDir Path root)
      throws Exception {
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry(
        catalog, new WorkspaceCheckpointStore(root));
    final IndexDocumentsRequest turbo = DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
        .build();
    final String indexId = registry.index(turbo).getIndex().getIndexId();
    registry.persist(indexId);
    registry.close();
    final Path descriptor = root.resolve(indexId)
        .resolve(WorkspaceCheckpointStore.DESCRIPTOR_FILE);
    final Properties properties = new Properties();
    try (var input = Files.newInputStream(descriptor)) {
      properties.load(input);
    }
    properties.setProperty("vector.segment.count", "10001");
    try (var output = Files.newOutputStream(descriptor)) {
      properties.store(output, "unbounded vector segments");
    }

    final IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> new DynamicSearchIndexRegistry(catalog, new WorkspaceCheckpointStore(root)));

    assertTrue(failure.getCause().getMessage().contains("vector.segment.count"));
    assertTrue(failure.getCause().getMessage().contains("10000"));
  }
}
