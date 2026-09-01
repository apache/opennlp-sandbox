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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.opennlp.grpc.v1.CollectionDescriptor;
import org.apache.opennlp.grpc.v1.CollectionEvent;
import org.apache.opennlp.grpc.v1.CollectionEventKind;
import org.apache.opennlp.grpc.v1.SetCollectionRequest;
import org.apache.opennlp.grpc.v1.TermStatistic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Collection persistence over the TurboQuant provider from the
 * opennlp-grpc-search-turboquant add-on, discovered from the classpath exactly as a
 * deployment discovers it.
 */
class SearchCollectionRegistryTurboQuantTest {

  /** Mirrors the server module test fixture: no vocabulary artifacts are resolvable. */
  private static final SearchCollectionRegistry.VocabularyTermsSource NO_VOCABULARIES =
      artifactId -> {
        throw new IllegalArgumentException("Unknown vocabulary artifact '" + artifactId + "'");
      };

  @Test
  void persistsCollectionsBesideIndexCheckpointsAndRestoresThem(@TempDir Path root)
      throws IOException {
    final WorkspaceCheckpointStore checkpoints = new WorkspaceCheckpointStore(root);
    final SearchProviderCatalog catalog = SearchProviderCatalog.discover();
    final DynamicSearchIndexRegistry indexes =
        new DynamicSearchIndexRegistry(catalog, checkpoints);
    final String indexId = indexes
        .index(DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha beta", 1, 0)
            .toBuilder()
            .setProvider(org.apache.opennlp.grpc.v1.SearchProviderSelector.newBuilder()
                .setStandard(org.apache.opennlp.grpc.v1.StandardSearchProvider
                    .STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
            .build())
        .getIndex().getIndexId();
    indexes.persist(indexId);

    final SearchCollectionRegistry registry = SearchCollectionRegistry.fromConfiguration(
        Map.of(WorkspaceCheckpointStore.ROOT_KEY, root.toString()), indexes, NO_VOCABULARIES);
    final CollectionDescriptor written = registry.set(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds(indexId)
        .setDriftNewTermThreshold(5)
        .build());
    assertFalse(written.getIntegrityHash().isEmpty());
    assertTrue(Files.isRegularFile(root
        .resolve(SearchCollectionRegistry.COLLECTIONS_DIR)
        .resolve("legal")
        .resolve(SearchCollectionRegistry.COLLECTION_FILE)));

    // The reserved collections directory is not an index checkpoint.
    final DynamicSearchIndexRegistry restoredIndexes =
        new DynamicSearchIndexRegistry(catalog, new WorkspaceCheckpointStore(root));
    assertEquals(1, restoredIndexes.descriptors().size());

    final SearchCollectionRegistry restored = SearchCollectionRegistry.fromConfiguration(
        Map.of(WorkspaceCheckpointStore.ROOT_KEY, root.toString()),
        restoredIndexes, NO_VOCABULARIES);
    final CollectionDescriptor descriptor = restored.find("legal");
    assertEquals("Legal corpus", descriptor.getDisplayName());
    assertEquals(List.of(indexId), descriptor.getMemberIndexIdsList());
    assertEquals(5, descriptor.getDriftNewTermThreshold());
    assertEquals(written.getIntegrityHash(), descriptor.getIntegrityHash());
    assertEquals(2, descriptor.getDrift().getDistinctTerms());

    // A tampered collection file fails integrity verification at load.
    final Path file = root.resolve(SearchCollectionRegistry.COLLECTIONS_DIR)
        .resolve("legal").resolve(SearchCollectionRegistry.COLLECTION_FILE);
    Files.write(file, org.apache.opennlp.grpc.v1.PersistedCollection.parseFrom(
            Files.readAllBytes(file)).toBuilder()
        .setCollection(descriptor.toBuilder().setDisplayName("Tampered"))
        .build().toByteArray());
    assertThrows(IllegalStateException.class, () -> SearchCollectionRegistry.fromConfiguration(
        Map.of(WorkspaceCheckpointStore.ROOT_KEY, root.toString()),
        restoredIndexes, NO_VOCABULARIES));
  }
}
