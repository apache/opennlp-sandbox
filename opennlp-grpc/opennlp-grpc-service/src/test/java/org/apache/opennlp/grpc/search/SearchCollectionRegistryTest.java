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
import org.apache.opennlp.grpc.v1.TermLedgerEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SearchCollectionRegistryTest {

  private static final SearchCollectionRegistry.VocabularyTermsSource NO_VOCABULARIES =
      artifactId -> {
        throw new IllegalArgumentException("Unknown vocabulary artifact '" + artifactId + "'");
      };

  @Test
  void recomputesTheTermLedgerFromLiveMemberContents() {
    final DynamicSearchIndexRegistry indexes = new DynamicSearchIndexRegistry();
    final String indexId = indexes
        .index(DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha beta alpha", 1, 0))
        .getIndex().getIndexId();
    final SearchCollectionRegistry registry =
        SearchCollectionRegistry.inMemory(indexes, NO_VOCABULARIES);

    final CollectionDescriptor created = registry.set(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds(indexId)
        .build());
    assertEquals("legal", created.getCollectionId());
    assertEquals(List.of(indexId), created.getMemberIndexIdsList());
    assertEquals(TermsSearchIndexProviderFactory.CHAIN_ID,
        created.getAnalysisChain().getChainId());

    final CollectionDescriptor descriptor = registry.find("legal");
    assertEquals(List.of("alpha", "beta"), descriptor.getTermLedgerList().stream()
        .map(TermLedgerEntry::getTerm).toList());
    assertEquals(2, descriptor.getTermLedger(0).getOccurrences());
    assertEquals(1, descriptor.getTermLedger(1).getOccurrences());
    assertFalse(descriptor.getTermLedger(0).getInVocabulary());
    assertEquals(2, descriptor.getDrift().getDistinctTerms());
    assertEquals(3, descriptor.getDrift().getTermOccurrences());
    assertEquals(2, descriptor.getDrift().getNewTerms());
    assertEquals(3, descriptor.getDrift().getNewTermOccurrences());
    assertEquals(0.0, descriptor.getDrift().getVocabularyCoverage());

    indexes.index(DynamicSearchIndexRegistryTest.request(indexId, "doc-2", "beta gamma", 0, 1));
    assertEquals(3, registry.find("legal").getDrift().getDistinctTerms());

    assertNull(registry.find("unknown"));
  }

  @Test
  void countsMultiwordVocabularyTermsAsOneUnit() throws IOException {
    final DynamicSearchIndexRegistry indexes = new DynamicSearchIndexRegistry();
    final String indexId = indexes
        .index(DynamicSearchIndexRegistryTest.request(
            null, "doc-1", "The writ of Habeas Corpus", 1, 0))
        .getIndex().getIndexId();
    final SearchCollectionRegistry registry = SearchCollectionRegistry.inMemory(
        indexes, artifactId -> List.of("habeas corpus", "writ"));

    registry.set(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds(indexId)
        .setVocabularyArtifactId("vocabulary-1")
        .build());

    final CollectionDescriptor descriptor = registry.find("legal");
    final Map<String, TermLedgerEntry> ledger = descriptor.getTermLedgerList().stream()
        .collect(java.util.stream.Collectors.toMap(TermLedgerEntry::getTerm, entry -> entry));
    assertEquals(4, ledger.size());
    assertTrue(ledger.get("habeas corpus").getInVocabulary());
    assertTrue(ledger.get("writ").getInVocabulary());
    assertFalse(ledger.get("the").getInVocabulary());
    assertFalse(ledger.get("of").getInVocabulary());
    assertEquals(4, descriptor.getDrift().getDistinctTerms());
    assertEquals(4, descriptor.getDrift().getTermOccurrences());
    assertEquals(2, descriptor.getDrift().getNewTerms());
    assertEquals(2, descriptor.getDrift().getNewTermOccurrences());
    assertEquals(0.5, descriptor.getDrift().getVocabularyCoverage());
  }

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

  @Test
  void failedPersistentMutationsLeaveThePublishedCollectionUnchanged(@TempDir Path root)
      throws Exception {
    final DynamicSearchIndexRegistry indexes = new DynamicSearchIndexRegistry();
    final String indexId = indexes
        .index(DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0))
        .getIndex().getIndexId();
    final Path collectionsRoot = root.resolve(SearchCollectionRegistry.COLLECTIONS_DIR);
    final SearchCollectionRegistry registry = SearchCollectionRegistry.at(
        collectionsRoot, indexes, NO_VOCABULARIES);
    registry.set(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Original name")
        .addMemberIndexIds(indexId)
        .build());
    final Path collectionDirectory = collectionsRoot.resolve("legal");
    assumeTrue(Files.getFileStore(collectionDirectory).supportsFileAttributeView("posix"));
    final Set<PosixFilePermission> original =
        Files.getPosixFilePermissions(collectionDirectory);
    Files.setPosixFilePermissions(collectionDirectory, Set.of(
        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
    try {
      assertThrows(UncheckedIOException.class, () -> registry.set(
          SetCollectionRequest.newBuilder()
              .setCollectionId("legal")
              .setDisplayName("Unpublished replacement")
              .addMemberIndexIds(indexId)
              .build()));
      assertEquals("Original name", registry.find("legal").getDisplayName());

      assertThrows(UncheckedIOException.class, () -> registry.delete("legal"));
      assertEquals("Original name", registry.find("legal").getDisplayName());
    } finally {
      Files.setPosixFilePermissions(collectionDirectory, original);
    }
  }

  @Test
  void validatesIdsMembersAndTheVocabularyArtifact() {
    final DynamicSearchIndexRegistry indexes = new DynamicSearchIndexRegistry();
    final SearchCollectionRegistry registry =
        SearchCollectionRegistry.inMemory(indexes, NO_VOCABULARIES);

    assertThrows(IllegalArgumentException.class, () -> registry.set(
        SetCollectionRequest.newBuilder()
            .setCollectionId("bad id")
            .setDisplayName("Broken")
            .build()));
    assertThrows(IllegalArgumentException.class, () -> registry.set(
        SetCollectionRequest.newBuilder()
            .setCollectionId("legal")
            .build()));
    assertThrows(IllegalArgumentException.class, () -> registry.set(
        SetCollectionRequest.newBuilder()
            .setCollectionId("legal")
            .setDisplayName("Legal corpus")
            .addMemberIndexIds("missing-index")
            .build()));
    assertThrows(IllegalArgumentException.class, () -> registry.set(
        SetCollectionRequest.newBuilder()
            .setCollectionId("legal")
            .setDisplayName("Legal corpus")
            .setVocabularyArtifactId("vocabulary-missing")
            .build()));

    final String indexId = indexes
        .index(DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0))
        .getIndex().getIndexId();
    assertThrows(IllegalArgumentException.class, () -> registry.set(
        SetCollectionRequest.newBuilder()
            .setCollectionId("legal")
            .setDisplayName("Legal corpus")
            .addMemberIndexIds(indexId)
            .addMemberIndexIds(indexId)
            .build()));
    assertThrows(IllegalArgumentException.class, () -> registry.set(null));
    registry.set(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds(indexId)
        .build());
    assertThrows(IllegalArgumentException.class,
        () -> registry.watch("legal", null, () -> { }));
    assertThrows(IllegalArgumentException.class,
        () -> registry.watch("legal", event -> { }, null));
  }

  @Test
  void watchesReceiveASnapshotFirstAndLifecycleEventsAfterwards() {
    final DynamicSearchIndexRegistry indexes = new DynamicSearchIndexRegistry();
    final String indexId = indexes
        .index(DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0))
        .getIndex().getIndexId();
    final SearchCollectionRegistry registry = SearchCollectionRegistry.inMemory(
        indexes, artifactId -> List.of("alpha"));
    registry.set(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds(indexId)
        .setVocabularyArtifactId("vocabulary-1")
        .build());

    final List<CollectionEvent> events = new ArrayList<>();
    final List<String> completions = new ArrayList<>();
    final SearchCollectionRegistry.Watch watch =
        registry.watch("legal", events::add, () -> completions.add("done"));

    assertEquals(1, events.size());
    assertEquals(CollectionEventKind.COLLECTION_EVENT_KIND_SNAPSHOT, events.get(0).getKind());
    assertEquals("legal", events.get(0).getCollection().getCollectionId());
    assertEquals(1, events.get(0).getCollection().getTermLedgerCount());

    registry.notifyIndexPersisted(indexId);
    assertEquals(2, events.size());
    assertEquals(CollectionEventKind.COLLECTION_EVENT_KIND_INDEX_PERSISTED,
        events.get(1).getKind());
    assertEquals(indexId, events.get(1).getIndexId());

    registry.notifyIndexPersisted("unrelated-index");
    assertEquals(2, events.size());

    registry.notifyModelPublished("model-1", "vocabulary-1");
    assertEquals(3, events.size());
    assertEquals(CollectionEventKind.COLLECTION_EVENT_KIND_MODEL_PUBLISHED,
        events.get(2).getKind());
    assertEquals("model-1", events.get(2).getModelArtifactId());

    registry.notifyModelPublished("model-2", "vocabulary-other");
    assertEquals(3, events.size());

    watch.close();
    registry.notifyIndexPersisted(indexId);
    assertEquals(3, events.size());
    assertTrue(completions.isEmpty());

    assertThrows(IllegalArgumentException.class,
        () -> registry.watch("unknown", events::add, () -> { }));
  }

  @Test
  void driftThresholdCrossingEmitsExactlyOncePerCrossing() {
    final DynamicSearchIndexRegistry indexes = new DynamicSearchIndexRegistry();
    final String indexId = indexes
        .index(DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0))
        .getIndex().getIndexId();
    final SearchCollectionRegistry registry = SearchCollectionRegistry.inMemory(
        indexes, artifactId -> List.of("alpha"));
    registry.set(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds(indexId)
        .setVocabularyArtifactId("vocabulary-1")
        .setDriftNewTermThreshold(2)
        .build());

    final List<CollectionEvent> events = new ArrayList<>();
    registry.watch("legal", events::add, () -> { });
    assertEquals(1, events.size());

    indexes.index(DynamicSearchIndexRegistryTest.request(indexId, "doc-2", "beta", 0, 1));
    registry.notifyIndexed(indexId);
    assertEquals(1, events.size());

    indexes.index(DynamicSearchIndexRegistryTest.request(indexId, "doc-3", "gamma", 0, 1));
    registry.notifyIndexed(indexId);
    assertEquals(2, events.size());
    assertEquals(CollectionEventKind.COLLECTION_EVENT_KIND_DRIFT_THRESHOLD_CROSSED,
        events.get(1).getKind());
    assertEquals(2, events.get(1).getCollection().getDrift().getNewTerms());

    indexes.index(DynamicSearchIndexRegistryTest.request(indexId, "doc-4", "delta", 0, 1));
    registry.notifyIndexed(indexId);
    assertEquals(2, events.size());
  }

  @Test
  void listsWithoutLedgersAndDeleteCompletesWatchers() {
    final DynamicSearchIndexRegistry indexes = new DynamicSearchIndexRegistry();
    final String indexId = indexes
        .index(DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha beta", 1, 0))
        .getIndex().getIndexId();
    final SearchCollectionRegistry registry =
        SearchCollectionRegistry.inMemory(indexes, NO_VOCABULARIES);
    registry.set(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds(indexId)
        .build());

    final List<CollectionDescriptor> listed = registry.list();
    assertEquals(1, listed.size());
    assertEquals(0, listed.get(0).getTermLedgerCount());
    assertEquals(2, listed.get(0).getOmittedLedgerTerms());
    assertEquals(2, listed.get(0).getDrift().getDistinctTerms());

    final List<String> completions = new ArrayList<>();
    registry.watch("legal", event -> { }, () -> completions.add("done"));

    assertTrue(registry.delete("legal"));
    assertEquals(List.of("done"), completions);
    assertNull(registry.find("legal"));
    assertFalse(registry.delete("legal"));
  }

  @Test
  void failedInitialWatchDeliveryDoesNotLeaveARegisteredWatcher() {
    final DynamicSearchIndexRegistry indexes = new DynamicSearchIndexRegistry();
    final String indexId = indexes
        .index(DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0))
        .getIndex().getIndexId();
    final SearchCollectionRegistry registry =
        SearchCollectionRegistry.inMemory(indexes, NO_VOCABULARIES);
    registry.set(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds(indexId)
        .build());
    final List<String> completions = new ArrayList<>();

    assertThrows(IllegalStateException.class,
        () -> registry.watch("legal", event -> {
          throw new IllegalStateException("transport closed");
        }, () -> completions.add("done")));
    registry.delete("legal");

    assertTrue(completions.isEmpty());
  }

  @Test
  void watcherCallbacksNeverRunWhileHoldingTheRegistryMonitor() {
    final DynamicSearchIndexRegistry indexes = new DynamicSearchIndexRegistry();
    final String indexId = indexes
        .index(DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0))
        .getIndex().getIndexId();
    final SearchCollectionRegistry registry =
        SearchCollectionRegistry.inMemory(indexes, NO_VOCABULARIES);
    registry.set(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds(indexId)
        .build());
    final List<Boolean> lockStates = new ArrayList<>();

    registry.watch("legal",
        event -> lockStates.add(Thread.holdsLock(registry)),
        () -> lockStates.add(Thread.holdsLock(registry)));
    registry.notifyIndexPersisted(indexId);
    registry.delete("legal");

    assertEquals(List.of(false, false, false), lockStates);
  }

  @Test
  void driftRejectsMoreDistinctTermsThanItsConfiguredBound() {
    final DynamicSearchIndexRegistry indexes = new DynamicSearchIndexRegistry();
    final String indexId = indexes
        .index(DynamicSearchIndexRegistryTest.request(
            null, "doc-1", "alpha beta gamma", 1, 0))
        .getIndex().getIndexId();
    final SearchCollectionRegistry registry =
        SearchCollectionRegistry.inMemory(indexes, NO_VOCABULARIES, 2);

    final org.apache.opennlp.grpc.processor.AnalysisException failure = assertThrows(
        org.apache.opennlp.grpc.processor.AnalysisException.class,
        () -> registry.set(SetCollectionRequest.newBuilder()
            .setCollectionId("legal")
            .setDisplayName("Legal corpus")
            .addMemberIndexIds(indexId)
            .build()));

    assertEquals(org.apache.opennlp.grpc.processor.AnalysisException.FailureType
        .RESOURCE_EXHAUSTED, failure.getFailureType());
  }

  @Test
  void vocabularyAndDriftRebuildsRunOutsideTheRegistryMonitor() {
    final DynamicSearchIndexRegistry indexes = new DynamicSearchIndexRegistry();
    final String indexId = indexes
        .index(DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0))
        .getIndex().getIndexId();
    final List<Boolean> lockStates = new ArrayList<>();
    final SearchCollectionRegistry[] holder = new SearchCollectionRegistry[1];
    final SearchCollectionRegistry registry = SearchCollectionRegistry.inMemory(
        indexes, artifactId -> {
          lockStates.add(Thread.holdsLock(holder[0]));
          return List.of("alpha");
        });
    holder[0] = registry;
    registry.set(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds(indexId)
        .setVocabularyArtifactId("vocabulary-1")
        .setDriftNewTermThreshold(1)
        .build());
    lockStates.clear();

    registry.find("legal");
    registry.notifyIndexed(indexId);

    assertEquals(List.of(false, false), lockStates);
  }

  @Test
  void everyCollectionDescriptorRebuildRunsOutsideTheRegistryMonitor() {
    final DynamicSearchIndexRegistry indexes = new DynamicSearchIndexRegistry();
    final String indexId = indexes
        .index(DynamicSearchIndexRegistryTest.request(null, "doc-1", "alpha", 1, 0))
        .getIndex().getIndexId();
    final List<Boolean> lockStates = new ArrayList<>();
    final SearchCollectionRegistry[] holder = new SearchCollectionRegistry[1];
    final SearchCollectionRegistry registry = SearchCollectionRegistry.inMemory(
        indexes, artifactId -> {
          lockStates.add(Thread.holdsLock(holder[0]));
          return List.of("alpha");
        });
    holder[0] = registry;

    registry.set(SetCollectionRequest.newBuilder()
        .setCollectionId("legal")
        .setDisplayName("Legal corpus")
        .addMemberIndexIds(indexId)
        .setVocabularyArtifactId("vocabulary-1")
        .build());
    registry.watch("legal", event -> { }, () -> { });
    registry.notifyIndexPersisted(indexId);
    registry.notifyModelPublished("model-1", "vocabulary-1");

    assertEquals(List.of(false, false, false, false), lockStates);
  }
}
