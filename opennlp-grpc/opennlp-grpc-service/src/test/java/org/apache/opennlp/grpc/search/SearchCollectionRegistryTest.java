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

class SearchCollectionRegistryTest {

  private static final SearchCollectionRegistry.VocabularyTermsSource NO_VOCABULARIES =
      artifactId -> {
        throw new IllegalArgumentException("Unknown vocabulary artifact '" + artifactId + "'");
      };

  @Test
  void recomputesTheTermStatisticsFromLiveMemberContents() {
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
    assertEquals(List.of("alpha", "beta"), descriptor.getTermStatisticsList().stream()
        .map(TermStatistic::getTerm).toList());
    assertEquals(2, descriptor.getTermStatistics(0).getOccurrences());
    assertEquals(1, descriptor.getTermStatistics(1).getOccurrences());
    assertFalse(descriptor.getTermStatistics(0).getInVocabulary());
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
    final Map<String, TermStatistic> termStatistics = descriptor.getTermStatisticsList().stream()
        .collect(java.util.stream.Collectors.toMap(TermStatistic::getTerm, entry -> entry));
    assertEquals(4, termStatistics.size());
    assertTrue(termStatistics.get("habeas corpus").getInVocabulary());
    assertTrue(termStatistics.get("writ").getInVocabulary());
    assertFalse(termStatistics.get("the").getInVocabulary());
    assertFalse(termStatistics.get("of").getInVocabulary());
    assertEquals(4, descriptor.getDrift().getDistinctTerms());
    assertEquals(4, descriptor.getDrift().getTermOccurrences());
    assertEquals(2, descriptor.getDrift().getNewTerms());
    assertEquals(2, descriptor.getDrift().getNewTermOccurrences());
    assertEquals(0.5, descriptor.getDrift().getVocabularyCoverage());
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
    assertThrows(IllegalArgumentException.class, () -> registry.removeMember(null));
    assertThrows(IllegalArgumentException.class, () -> registry.removeMember("bad index"));
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
    assertEquals(1, events.get(0).getCollection().getTermStatisticsCount());

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
  void listsWithoutTermStatisticsAndDeleteCompletesWatchers() {
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
    assertEquals(0, listed.get(0).getTermStatisticsCount());
    assertEquals(2, listed.get(0).getOmittedTermCount());
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

    final org.apache.opennlp.grpc.spi.AnalysisException failure = assertThrows(
        org.apache.opennlp.grpc.spi.AnalysisException.class,
        () -> registry.set(SetCollectionRequest.newBuilder()
            .setCollectionId("legal")
            .setDisplayName("Legal corpus")
            .addMemberIndexIds(indexId)
            .build()));

    assertEquals(org.apache.opennlp.grpc.spi.AnalysisException.FailureType
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
