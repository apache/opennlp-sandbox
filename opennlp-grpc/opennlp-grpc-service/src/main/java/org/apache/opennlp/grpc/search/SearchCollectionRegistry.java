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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.apache.opennlp.grpc.search.query.QueryTermAnalyzer;
import org.apache.opennlp.grpc.v1.AnalysisChainDescriptor;
import org.apache.opennlp.grpc.v1.CollectionDescriptor;
import org.apache.opennlp.grpc.v1.CollectionDriftStats;
import org.apache.opennlp.grpc.v1.CollectionEvent;
import org.apache.opennlp.grpc.v1.CollectionEventKind;
import org.apache.opennlp.grpc.v1.PersistedCollection;
import org.apache.opennlp.grpc.v1.SetCollectionRequest;
import org.apache.opennlp.grpc.v1.TermLedgerEntry;

/**
 * Bounded registry of collections: the scope vocabulary accretion is measured over.
 *
 * <p>A collection stores configured state only: its member index ids, artifact lineage,
 * and drift threshold. The term ledger and drift statistics are recomputed on every read
 * from the live emitted text of member index chunks, analyzed with the same chain as the
 * keyword legs, so replaced or deleted documents never leave stale counts. Terms of the
 * current vocabulary artifact count as one unit by greedy longest match, mirroring how
 * vocabularies are learned.</p>
 *
 * <p>When constructed over a directory, each collection is rewritten atomically as one
 * {@value #COLLECTION_FILE} file on mutation, with an integrity hash inside and the last
 * write winning. Watchers subscribe per collection and receive a snapshot event first;
 * every event carries a complete descriptor snapshot.</p>
 */
public final class SearchCollectionRegistry {

  /** Reserved subdirectory of the persistence root holding collection files. */
  public static final String COLLECTIONS_DIR = "collections";

  /** Collection file name within one collection's directory. */
  static final String COLLECTION_FILE = "collection.pb";

  /** Fixed safety ceiling for the collection count. */
  static final int MAX_COLLECTIONS = 256;

  /** Fixed safety ceiling for one collection's member index count. */
  static final int MAX_MEMBER_INDEXES = 64;

  /** Largest term ledger carried by one descriptor; drift always covers everything. */
  static final int MAX_LEDGER_TERMS = 32_768;

  private static final int FORMAT_VERSION = 1;
  private static final long MAX_FILE_BYTES = 16L * 1024 * 1024;

  /**
   * Resolves the term rows of one vocabulary artifact for drift measurement.
   *
   * <p>Thread safety is implementation specific.</p>
   */
  @FunctionalInterface
  public interface VocabularyTermsSource {

    /**
     * Returns the term rows of one vocabulary artifact.
     *
     * @param vocabularyArtifactId Server-owned vocabulary artifact id.
     * @return Term texts; multiword terms are joined by single spaces.
     * @throws IOException If reading the artifact fails.
     * @throws IllegalArgumentException If the artifact id is unknown.
     */
    List<String> terms(String vocabularyArtifactId) throws IOException;
  }

  /**
   * One active watch subscription. Closing it stops event delivery without
   * completing the subscriber.
   */
  public interface Watch extends AutoCloseable {

    /** Unregisters the subscriber. Safe to call more than once. */
    @Override
    void close();
  }

  /** One subscriber and the completion callback fired when its collection is deleted. */
  private record Watcher(Consumer<CollectionEvent> events, Runnable completed) {
  }

  /** One collection's configured state and its live bookkeeping. */
  private static final class StoredCollection {
    private CollectionDescriptor configured;
    private String integrityHash = "";
    private long lastNewTerms;
    private final List<Watcher> watchers = new ArrayList<>();
  }

  private final DynamicSearchIndexRegistry indexes;
  private final VocabularyTermsSource vocabularyTerms;
  private final Path directory;
  private final SortedMap<String, StoredCollection> collections = new TreeMap<>();

  private SearchCollectionRegistry(DynamicSearchIndexRegistry indexes,
      VocabularyTermsSource vocabularyTerms, Path directory) {
    if (indexes == null) {
      throw new IllegalArgumentException("indexes must not be null");
    }
    if (vocabularyTerms == null) {
      throw new IllegalArgumentException("vocabularyTerms must not be null");
    }
    this.indexes = indexes;
    this.vocabularyTerms = vocabularyTerms;
    this.directory = directory;
  }

  /**
   * Creates a registry that keeps collections only for the server process lifetime.
   *
   * @param indexes Dynamic index registry serving member contents.
   * @param vocabularyTerms Vocabulary term source for drift measurement.
   * @return Empty in-memory registry.
   * @throws IllegalArgumentException If an argument is {@code null}.
   */
  public static SearchCollectionRegistry inMemory(
      DynamicSearchIndexRegistry indexes, VocabularyTermsSource vocabularyTerms) {
    return new SearchCollectionRegistry(indexes, vocabularyTerms, null);
  }

  /**
   * Creates a registry persisted beneath one directory, one file per collection.
   *
   * @param directory Directory holding one subdirectory per collection.
   * @param indexes Dynamic index registry serving member contents.
   * @param vocabularyTerms Vocabulary term source for drift measurement.
   * @return Registry holding the directory's collections.
   * @throws IllegalArgumentException If an argument is {@code null}.
   * @throws IllegalStateException If a collection file is unreadable, malformed, or
   *     fails integrity verification.
   */
  public static SearchCollectionRegistry at(Path directory,
      DynamicSearchIndexRegistry indexes, VocabularyTermsSource vocabularyTerms) {
    if (directory == null) {
      throw new IllegalArgumentException("directory must not be null");
    }
    final SearchCollectionRegistry registry =
        new SearchCollectionRegistry(indexes, vocabularyTerms, directory);
    registry.load();
    return registry;
  }

  /**
   * Creates a registry from configuration: persisted under the checkpoint root when
   * {@value WorkspaceCheckpointStore#ROOT_KEY} is set, in memory otherwise.
   *
   * @param configuration Server configuration.
   * @param indexes Dynamic index registry serving member contents.
   * @param vocabularyTerms Vocabulary term source for drift measurement.
   * @return The configured registry.
   * @throws IllegalArgumentException If an argument is {@code null}.
   */
  public static SearchCollectionRegistry fromConfiguration(Map<String, String> configuration,
      DynamicSearchIndexRegistry indexes, VocabularyTermsSource vocabularyTerms) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    final String root = configuration.get(WorkspaceCheckpointStore.ROOT_KEY);
    if (root == null || root.isBlank()) {
      return inMemory(indexes, vocabularyTerms);
    }
    return at(Path.of(root.trim()).resolve(COLLECTIONS_DIR), indexes, vocabularyTerms);
  }

  /**
   * Creates or replaces one collection's complete configured state.
   *
   * @param request Configured state with member index ids already resolved.
   * @return Descriptor after the write, with its recomputed ledger and drift.
   * @throws IllegalArgumentException If an identifier is invalid, a member index or the
   *     vocabulary artifact is unknown, or a bound would be exceeded.
   * @throws UncheckedIOException If reading the vocabulary or writing the file fails.
   */
  public synchronized CollectionDescriptor set(SetCollectionRequest request) {
    SearchIndexRegistry.requireStableId(request.getCollectionId(), "collection id");
    if (request.getDisplayName().isBlank()) {
      throw new IllegalArgumentException("display_name must not be blank");
    }
    if (!collections.containsKey(request.getCollectionId())
        && collections.size() >= MAX_COLLECTIONS) {
      throw new IllegalArgumentException("collection count reached " + MAX_COLLECTIONS);
    }
    if (request.getMemberIndexIdsCount() > MAX_MEMBER_INDEXES) {
      throw new IllegalArgumentException("member index count " + request.getMemberIndexIdsCount()
          + " exceeds " + MAX_MEMBER_INDEXES);
    }
    final Set<String> members = new HashSet<>();
    for (String memberId : request.getMemberIndexIdsList()) {
      SearchIndexRegistry.requireStableId(memberId, "member index id");
      if (!members.add(memberId)) {
        throw new IllegalArgumentException("member index '" + memberId + "' is listed twice");
      }
      if (indexes.find(memberId) == null) {
        throw new IllegalArgumentException("Unknown dynamic index '" + memberId + "'");
      }
    }
    validateArtifactId(request.hasDictionaryArtifactId(),
        request.getDictionaryArtifactId(), "dictionary_artifact_id");
    validateArtifactId(request.hasVocabularyArtifactId(),
        request.getVocabularyArtifactId(), "vocabulary_artifact_id");
    validateArtifactId(request.hasModelArtifactId(),
        request.getModelArtifactId(), "model_artifact_id");

    final CollectionDescriptor.Builder configured = CollectionDescriptor.newBuilder()
        .setCollectionId(request.getCollectionId())
        .setDisplayName(request.getDisplayName())
        .addAllMemberIndexIds(request.getMemberIndexIdsList())
        .setDriftNewTermThreshold(request.getDriftNewTermThreshold());
    if (request.hasDictionaryArtifactId()) {
      configured.setDictionaryArtifactId(request.getDictionaryArtifactId());
    }
    if (request.hasVocabularyArtifactId()) {
      configured.setVocabularyArtifactId(request.getVocabularyArtifactId());
    }
    if (request.hasModelArtifactId()) {
      configured.setModelArtifactId(request.getModelArtifactId());
    }

    final CollectionDescriptor next = configured.build();
    // Resolving the vocabulary now validates the artifact before any state mutates.
    final CollectionDescriptor computed = describe(next, "", true);
    final StoredCollection stored =
        collections.computeIfAbsent(request.getCollectionId(), id -> new StoredCollection());
    stored.configured = next;
    stored.lastNewTerms = computed.getDrift().getNewTerms();
    write(stored, computed);
    return computed.toBuilder().setIntegrityHash(stored.integrityHash).build();
  }

  /**
   * Returns one collection with its recomputed ledger and drift.
   *
   * @param collectionId Stable collection id.
   * @return The descriptor, or {@code null} when the id is unknown.
   * @throws UncheckedIOException If reading the vocabulary artifact fails.
   */
  public synchronized CollectionDescriptor find(String collectionId) {
    final StoredCollection stored =
        collectionId == null ? null : collections.get(collectionId);
    return stored == null ? null : describe(stored.configured, stored.integrityHash, true);
  }

  /**
   * Returns every collection with recomputed drift and its ledger omitted.
   *
   * @return Immutable descriptors in stable collection-id order.
   * @throws UncheckedIOException If reading a vocabulary artifact fails.
   */
  public synchronized List<CollectionDescriptor> list() {
    final List<CollectionDescriptor> result = new ArrayList<>(collections.size());
    for (StoredCollection stored : collections.values()) {
      result.add(describe(stored.configured, stored.integrityHash, false));
    }
    return List.copyOf(result);
  }

  /**
   * Deletes one collection, removes its file, and completes its watchers.
   *
   * @param collectionId Stable collection id.
   * @return {@code true} when the collection existed and was removed.
   * @throws UncheckedIOException If removing the collection file fails.
   */
  public synchronized boolean delete(String collectionId) {
    final StoredCollection stored = collections.remove(collectionId);
    if (stored == null) {
      return false;
    }
    if (directory != null) {
      try {
        WorkspaceCheckpointStore.deleteRecursively(directory.resolve(collectionId));
      } catch (IOException e) {
        throw new UncheckedIOException(
            "Failed to delete collection '" + collectionId + "'", e);
      }
    }
    for (Watcher watcher : List.copyOf(stored.watchers)) {
      watcher.completed().run();
    }
    stored.watchers.clear();
    return true;
  }

  /**
   * Subscribes to one collection and delivers its snapshot event immediately.
   *
   * @param collectionId Stable collection id.
   * @param events Receiver of every event, called under the registry lock.
   * @param completed Called once when the collection is deleted.
   * @return Handle that unregisters the subscription.
   * @throws IllegalArgumentException If the collection is unknown.
   */
  public synchronized Watch watch(String collectionId,
      Consumer<CollectionEvent> events, Runnable completed) {
    final StoredCollection stored = collections.get(collectionId);
    if (stored == null) {
      throw new IllegalArgumentException("Unknown collection '" + collectionId + "'");
    }
    final Watcher watcher = new Watcher(events, completed);
    stored.watchers.add(watcher);
    events.accept(event(stored, CollectionEventKind.COLLECTION_EVENT_KIND_SNAPSHOT,
        null, null));
    return () -> {
      synchronized (this) {
        stored.watchers.remove(watcher);
      }
    };
  }

  /**
   * Recomputes drift for collections holding one just-extended member index and emits a
   * drift event on the crossing from below the configured threshold to at or above it.
   *
   * @param indexId Dynamic index that accepted new documents.
   */
  public synchronized void notifyIndexed(String indexId) {
    for (StoredCollection stored : collections.values()) {
      if (stored.configured.getDriftNewTermThreshold() == 0
          || !stored.configured.getMemberIndexIdsList().contains(indexId)) {
        continue;
      }
      final long threshold = stored.configured.getDriftNewTermThreshold();
      final long newTerms = describe(stored.configured, "", false).getDrift().getNewTerms();
      final boolean crossed = stored.lastNewTerms < threshold && newTerms >= threshold;
      stored.lastNewTerms = newTerms;
      if (crossed) {
        emit(stored, CollectionEventKind.COLLECTION_EVENT_KIND_DRIFT_THRESHOLD_CROSSED,
            null, null);
      }
    }
  }

  /**
   * Emits an index-persisted event to collections holding one member index.
   *
   * @param indexId Dynamic index that was persisted or sealed.
   */
  public synchronized void notifyIndexPersisted(String indexId) {
    for (StoredCollection stored : collections.values()) {
      if (stored.configured.getMemberIndexIdsList().contains(indexId)) {
        emit(stored, CollectionEventKind.COLLECTION_EVENT_KIND_INDEX_PERSISTED,
            indexId, null);
      }
    }
  }

  /**
   * Emits a model-published event to collections configured with the published model's
   * parent vocabulary. Adopting the model remains an explicit SetCollection call.
   *
   * @param modelArtifactId Published model artifact id.
   * @param vocabularyArtifactId The published model's parent vocabulary artifact id.
   */
  public synchronized void notifyModelPublished(
      String modelArtifactId, String vocabularyArtifactId) {
    for (StoredCollection stored : collections.values()) {
      if (stored.configured.hasVocabularyArtifactId()
          && stored.configured.getVocabularyArtifactId().equals(vocabularyArtifactId)) {
        emit(stored, CollectionEventKind.COLLECTION_EVENT_KIND_MODEL_PUBLISHED,
            null, modelArtifactId);
      }
    }
  }

  /**
   * Delivers one event to every watcher of a collection.
   *
   * @param stored Collection whose watchers receive the event.
   * @param kind Event kind.
   * @param indexId Member index context, or {@code null}.
   * @param modelArtifactId Model artifact context, or {@code null}.
   */
  private void emit(StoredCollection stored, CollectionEventKind kind,
      String indexId, String modelArtifactId) {
    if (stored.watchers.isEmpty()) {
      return;
    }
    final CollectionEvent event = event(stored, kind, indexId, modelArtifactId);
    for (Watcher watcher : List.copyOf(stored.watchers)) {
      try {
        watcher.events().accept(event);
      } catch (RuntimeException e) {
        // A subscriber that cancelled between events stops receiving them.
        stored.watchers.remove(watcher);
      }
    }
  }

  /**
   * Builds one self-contained event with a complete descriptor snapshot.
   *
   * @param stored Collection to snapshot.
   * @param kind Event kind.
   * @param indexId Member index context, or {@code null}.
   * @param modelArtifactId Model artifact context, or {@code null}.
   * @return The event.
   */
  private CollectionEvent event(StoredCollection stored, CollectionEventKind kind,
      String indexId, String modelArtifactId) {
    final CollectionEvent.Builder event = CollectionEvent.newBuilder()
        .setKind(kind)
        .setCollection(describe(stored.configured, stored.integrityHash, true));
    if (indexId != null) {
      event.setIndexId(indexId);
    }
    if (modelArtifactId != null) {
      event.setModelArtifactId(modelArtifactId);
    }
    return event.build();
  }

  /**
   * Builds one descriptor with its ledger and drift recomputed from live members.
   *
   * @param configured Collection configuration to describe.
   * @param integrityHash Integrity hash of the last persisted form, or empty.
   * @param includeLedger Whether the bounded ledger is carried; drift always covers
   *     the complete ledger.
   * @return The descriptor.
   * @throws UncheckedIOException If reading the vocabulary artifact fails.
   */
  private CollectionDescriptor describe(
      CollectionDescriptor configured, String integrityHash, boolean includeLedger) {
    final Set<String> vocabulary = vocabularyOf(configured);
    final Map<String, Long> counts = countUnits(configured, vocabulary);

    long occurrences = 0;
    long newTerms = 0;
    long newOccurrences = 0;
    for (Map.Entry<String, Long> entry : counts.entrySet()) {
      occurrences += entry.getValue();
      if (!vocabulary.contains(entry.getKey())) {
        newTerms++;
        newOccurrences += entry.getValue();
      }
    }
    final CollectionDriftStats drift = CollectionDriftStats.newBuilder()
        .setDistinctTerms(counts.size())
        .setTermOccurrences(occurrences)
        .setNewTerms(newTerms)
        .setNewTermOccurrences(newOccurrences)
        .setVocabularyCoverage(occurrences == 0
            ? 0 : (occurrences - newOccurrences) / (double) occurrences)
        .build();

    final CollectionDescriptor.Builder descriptor = configured.toBuilder()
        .setAnalysisChain(AnalysisChainDescriptor.newBuilder()
            .setChainId(TermsSearchIndexProviderFactory.CHAIN_ID)
            .setChainVersion(TermsSearchIndexProviderFactory.CHAIN_VERSION))
        .setDrift(drift)
        .setIntegrityHash(integrityHash);
    if (includeLedger) {
      final List<Map.Entry<String, Long>> ordered = new ArrayList<>(counts.entrySet());
      ordered.sort(Map.Entry.<String, Long>comparingByValue().reversed()
          .thenComparing(Map.Entry.comparingByKey()));
      final int carried = Math.min(ordered.size(), MAX_LEDGER_TERMS);
      for (Map.Entry<String, Long> entry : ordered.subList(0, carried)) {
        descriptor.addTermLedger(TermLedgerEntry.newBuilder()
            .setTerm(entry.getKey())
            .setOccurrences(entry.getValue())
            .setInVocabulary(vocabulary.contains(entry.getKey())));
      }
      descriptor.setOmittedLedgerTerms(counts.size() - carried);
    } else {
      descriptor.setOmittedLedgerTerms(counts.size());
    }
    return descriptor.build();
  }

  /**
   * Resolves the configured vocabulary artifact's term rows.
   *
   * @param configured Collection configuration.
   * @return Term set; empty when no vocabulary artifact is configured.
   * @throws UncheckedIOException If reading the artifact fails.
   * @throws IllegalArgumentException If the artifact id is unknown.
   */
  private Set<String> vocabularyOf(CollectionDescriptor configured) {
    if (!configured.hasVocabularyArtifactId()) {
      return Set.of();
    }
    try {
      return Set.copyOf(vocabularyTerms.terms(configured.getVocabularyArtifactId()));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read vocabulary artifact '"
          + configured.getVocabularyArtifactId() + "'", e);
    }
  }

  /**
   * Counts term units across the live emitted text of every member index chunk. A
   * multiword vocabulary term consumes its words as one unit by greedy longest match,
   * mirroring how vocabularies are learned.
   *
   * @param configured Collection configuration naming the member indexes.
   * @param vocabulary Current vocabulary term rows.
   * @return Occurrence counts per term unit.
   */
  private Map<String, Long> countUnits(
      CollectionDescriptor configured, Set<String> vocabulary) {
    final Map<String, List<List<String>>> multiwordByFirst = new HashMap<>();
    for (String term : vocabulary) {
      final List<String> words = spaceJoinedWords(term);
      if (words.size() > 1) {
        final List<List<String>> candidates =
            multiwordByFirst.computeIfAbsent(words.get(0), first -> new ArrayList<>());
        candidates.add(words);
        candidates.sort((a, b) -> Integer.compare(b.size(), a.size()));
      }
    }

    final Map<String, Long> counts = new LinkedHashMap<>();
    for (String memberId : configured.getMemberIndexIdsList()) {
      if (indexes.find(memberId) == null) {
        continue;
      }
      for (DynamicSearchIndexRegistry.IndexedChunk chunk : indexes.retainedChunks(memberId)) {
        final List<String> words = new ArrayList<>();
        for (QueryTermAnalyzer.Term term
            : QueryTermAnalyzer.analyze(chunk.record().emittedText())) {
          words.add(term.text());
        }
        int i = 0;
        while (i < words.size()) {
          final int consumed = countMultiwordMatch(words, i, multiwordByFirst, counts);
          if (consumed == 0) {
            counts.merge(words.get(i), 1L, Long::sum);
            i++;
          } else {
            i += consumed;
          }
        }
      }
    }
    return counts;
  }

  /**
   * Counts the longest multiword vocabulary term starting at a position, if any.
   *
   * @param words Analyzed words of one chunk.
   * @param position Scan position.
   * @param multiwordByFirst Multiword terms indexed by first word, longest first.
   * @param counts Occurrence counts to update.
   * @return The number of words consumed, zero when no term matches.
   */
  private static int countMultiwordMatch(List<String> words, int position,
      Map<String, List<List<String>>> multiwordByFirst, Map<String, Long> counts) {
    final List<List<String>> candidates = multiwordByFirst.get(words.get(position));
    if (candidates == null) {
      return 0;
    }
    for (List<String> candidate : candidates) {
      if (position + candidate.size() <= words.size()
          && words.subList(position, position + candidate.size()).equals(candidate)) {
        counts.merge(String.join(" ", candidate), 1L, Long::sum);
        return candidate.size();
      }
    }
    return 0;
  }

  /**
   * Splits one vocabulary term on the single spaces its words were joined with.
   *
   * @param term Vocabulary term text.
   * @return The term's words in order, without empty entries.
   */
  private static List<String> spaceJoinedWords(String term) {
    final List<String> words = new ArrayList<>();
    int start = 0;
    while (start <= term.length()) {
      int end = term.indexOf(' ', start);
      if (end < 0) {
        end = term.length();
      }
      if (end > start) {
        words.add(term.substring(start, end));
      }
      start = end + 1;
    }
    return words;
  }

  /**
   * Validates one optional artifact reference.
   *
   * @param present Whether the field is set.
   * @param value Field value when present.
   * @param name Field name for error messages.
   * @throws IllegalArgumentException If a present value is not a stable identifier.
   */
  private static void validateArtifactId(boolean present, String value, String name) {
    if (present) {
      SearchIndexRegistry.requireStableId(value, name);
    }
  }

  /**
   * Loads every collection file beneath the directory.
   *
   * @throws IllegalStateException If a file is unreadable, malformed, oversized, or
   *     fails integrity verification.
   */
  private void load() {
    if (!Files.isDirectory(directory)) {
      return;
    }
    final List<Path> entries = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
      for (Path entry : stream) {
        if (Files.isDirectory(entry) && !entry.getFileName().toString().startsWith(".")) {
          entries.add(entry);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to list collections in " + directory, e);
    }
    for (Path entry : entries) {
      final Path file = entry.resolve(COLLECTION_FILE);
      try {
        if (!Files.isRegularFile(file)) {
          throw new IllegalStateException(entry + " lacks " + COLLECTION_FILE);
        }
        if (Files.size(file) > MAX_FILE_BYTES) {
          throw new IllegalStateException(file + " exceeds " + MAX_FILE_BYTES + " bytes");
        }
        final PersistedCollection persisted;
        try (InputStream input = Files.newInputStream(file)) {
          persisted = PersistedCollection.parseFrom(input);
        }
        if (persisted.getFormatVersion() != FORMAT_VERSION) {
          throw new IllegalStateException("Unsupported collection format_version "
              + persisted.getFormatVersion() + " in " + file
              + "; expected " + FORMAT_VERSION);
        }
        final CollectionDescriptor descriptor = persisted.getCollection();
        SearchIndexRegistry.requireStableId(descriptor.getCollectionId(),
            "persisted collection id");
        if (!entry.getFileName().toString().equals(descriptor.getCollectionId())) {
          throw new IllegalStateException("collection directory " + entry
              + " does not match its declared collection_id '"
              + descriptor.getCollectionId() + "'");
        }
        final String declared = descriptor.getIntegrityHash();
        final String computed = sha256Hex(persisted.toBuilder()
            .setCollection(descriptor.toBuilder().clearIntegrityHash())
            .build().toByteArray());
        if (!computed.equals(declared)) {
          throw new IllegalStateException(file + " fails integrity verification");
        }
        final StoredCollection stored = new StoredCollection();
        stored.configured = descriptor.toBuilder()
            .clearAnalysisChain()
            .clearTermLedger()
            .clearOmittedLedgerTerms()
            .clearDrift()
            .clearIntegrityHash()
            .build();
        stored.integrityHash = declared;
        stored.lastNewTerms = descriptor.getDrift().getNewTerms();
        collections.put(descriptor.getCollectionId(), stored);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to load collection from " + file, e);
      }
    }
  }

  /**
   * Rewrites one collection file atomically when this registry is persisted.
   *
   * @param stored Collection whose integrity hash is updated after the write.
   * @param computed Freshly described state, including its ledger snapshot.
   * @throws UncheckedIOException If the write fails.
   */
  private void write(StoredCollection stored, CollectionDescriptor computed) {
    if (directory == null) {
      return;
    }
    final CollectionDescriptor snapshot = computed.toBuilder()
        .clearIntegrityHash()
        .build();
    final PersistedCollection cleared = PersistedCollection.newBuilder()
        .setFormatVersion(FORMAT_VERSION)
        .setCollection(snapshot)
        .build();
    final String hash = sha256Hex(cleared.toByteArray());
    final PersistedCollection persisted = cleared.toBuilder()
        .setCollection(snapshot.toBuilder().setIntegrityHash(hash))
        .build();
    final Path target = directory.resolve(snapshot.getCollectionId());
    try {
      Files.createDirectories(target);
      final Path staging = Files.createTempFile(target, ".opennlp-collection-", ".tmp");
      try (OutputStream output = Files.newOutputStream(staging)) {
        persisted.writeTo(output);
      }
      Files.move(staging, target.resolve(COLLECTION_FILE),
          StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to persist collection '"
          + snapshot.getCollectionId() + "'", e);
    }
    stored.integrityHash = hash;
  }

  /**
   * Computes one lowercase hex SHA-256 digest.
   *
   * @param bytes Bytes to digest.
   * @return The digest.
   */
  private static String sha256Hex(byte[] bytes) {
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
    final byte[] hashed = digest.digest(bytes);
    final StringBuilder hex = new StringBuilder(hashed.length * 2);
    for (byte b : hashed) {
      hex.append(Character.forDigit((b >> 4) & 0xF, 16));
      hex.append(Character.forDigit(b & 0xF, 16));
    }
    return hex.toString();
  }
}
