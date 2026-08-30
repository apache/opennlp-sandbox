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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.protobuf.ByteString;
import opennlp.embeddings.index.VectorIndex;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.apache.opennlp.grpc.v1.EmbeddingSelector;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PersistedSearchChunk;
import org.apache.opennlp.grpc.v1.SearchCorpusDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexBuildDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexComponent;
import org.apache.opennlp.grpc.v1.SearchComponentKind;
import org.apache.opennlp.grpc.v1.SearchMetric;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;
import org.apache.opennlp.grpc.v1.SearchProviderSelector;
import org.apache.opennlp.grpc.v1.StandardEmbeddingBackend;
import org.apache.opennlp.grpc.v1.StandardSearchProvider;
import org.apache.opennlp.grpc.spi.search.KeywordQueryIndex;
import org.apache.opennlp.grpc.spi.search.SearchResult;
import org.apache.opennlp.grpc.spi.search.SearchRecord;
import org.apache.opennlp.grpc.spi.search.SearchIndexProvider;
import org.apache.opennlp.grpc.spi.search.SearchIndexProviderFactory;
import org.apache.opennlp.grpc.spi.search.QueryCandidate;

/** Bounded registry of server-owned indexes created from analyzed document shapes. */
public final class DynamicSearchIndexRegistry implements AutoCloseable {

  static final int MAX_INDEXES = 32;
  static final int MAX_DOCUMENTS_PER_REQUEST = 16;
  static final int MAX_DOCUMENTS_PER_INDEX = 256;
  static final int MAX_CHUNKS_PER_INDEX = 10_000;
  static final int MAX_SOURCE_DOCUMENT_BYTES_PER_INDEX = 16 * 1024 * 1024;
  static final int MAX_VECTOR_DIMENSION = 65_536;
  static final long MAX_VECTOR_VALUES = 16_000_000;
  static final long MAX_SOURCE_BYTES = 128L * 1024 * 1024;
  private static final int DEFAULT_MAX_TOP_K = 1_000;
  private static final int DEFAULT_MAX_QUERY_BYTES = 65_536;
  private static final int DEFAULT_MAX_RESPONSE_BYTES = 32 * 1024 * 1024;

  private final Map<String, DynamicIndex> indexes = new LinkedHashMap<>();
  private final SearchProviderCatalog catalog;
  private final SearchProviderCatalog.Instance keywordInstance;
  private final WorkspaceCheckpointStore checkpointStore;
  private final int maxIndexes;
  private final long maxVectorValues;
  private final long maxSourceBytes;
  private final boolean enabled;
  private boolean closed;

  /** Creates an empty bounded dynamic registry over the discovered provider instances. */
  public DynamicSearchIndexRegistry() {
    this(SearchProviderCatalog.discover(), null, true, MAX_INDEXES, MAX_VECTOR_VALUES,
        MAX_SOURCE_BYTES);
  }

  /**
   * Creates an empty bounded dynamic registry over a configured provider catalog.
   *
   * @param catalog Configured search provider instances.
   * @throws IllegalArgumentException If the catalog is {@code null}.
   */
  public DynamicSearchIndexRegistry(SearchProviderCatalog catalog) {
    this(catalog, null, true, MAX_INDEXES, MAX_VECTOR_VALUES, MAX_SOURCE_BYTES);
  }

  /**
   * Creates a bounded dynamic registry that restores and persists checkpoints.
   *
   * @param catalog Configured search provider instances.
   * @param checkpointStore Checkpoint store, or {@code null} when persistence is not
   *     configured.
   * @throws IllegalArgumentException If the catalog is {@code null}.
   * @throws IllegalStateException If a stored checkpoint cannot be restored.
   */
  public DynamicSearchIndexRegistry(
      SearchProviderCatalog catalog, WorkspaceCheckpointStore checkpointStore) {
    this(catalog, checkpointStore, true, MAX_INDEXES, MAX_VECTOR_VALUES, MAX_SOURCE_BYTES);
  }

  /**
   * Creates a registry with testable limits below the fixed safety ceilings.
   *
   * @param maxIndexes Maximum retained indexes.
   * @param maxVectorValues Maximum retained float values.
   * @param maxSourceBytes Maximum retained serialized document bytes.
   * @throws IllegalArgumentException If a limit is outside its fixed safety ceiling.
   */
  DynamicSearchIndexRegistry(int maxIndexes, long maxVectorValues, long maxSourceBytes) {
    this(SearchProviderCatalog.discover(), null, true, maxIndexes, maxVectorValues,
        maxSourceBytes);
  }

  /**
   * Creates a registry with explicit safety limits and restores stored checkpoints.
   *
   * @param catalog Configured search provider instances.
   * @param checkpointStore Checkpoint store, or {@code null} when persistence is not
   *     configured.
   * @param enabled Whether mutation is enabled.
   * @param maxIndexes Maximum retained indexes.
   * @param maxVectorValues Maximum retained float values.
   * @param maxSourceBytes Maximum retained serialized document bytes.
   * @throws IllegalArgumentException If the catalog is {@code null} or a limit is outside
   *     its fixed safety ceiling.
   * @throws IllegalStateException If a stored checkpoint cannot be restored.
   */
  private DynamicSearchIndexRegistry(SearchProviderCatalog catalog,
      WorkspaceCheckpointStore checkpointStore,
      boolean enabled, int maxIndexes, long maxVectorValues, long maxSourceBytes) {
    if (catalog == null) {
      throw new IllegalArgumentException("catalog must not be null");
    }
    if (maxIndexes < 1 || maxIndexes > MAX_INDEXES || maxVectorValues < 1
        || maxVectorValues > MAX_VECTOR_VALUES || maxSourceBytes < 1
        || maxSourceBytes > MAX_SOURCE_BYTES) {
      throw new IllegalArgumentException("Dynamic search limits are outside their safety bounds");
    }
    this.catalog = catalog;
    this.keywordInstance = catalog.findOrNull(TermsSearchIndexProviderFactory.PROVIDER_ID);
    this.checkpointStore = checkpointStore;
    this.maxIndexes = maxIndexes;
    this.maxVectorValues = maxVectorValues;
    this.maxSourceBytes = maxSourceBytes;
    this.enabled = enabled;
    if (checkpointStore != null) {
      restore();
    }
  }

  /**
   * Returns a registry that exposes no dynamic indexes and rejects mutation RPCs.
   *
   * @return Disabled registry.
   */
  public static DynamicSearchIndexRegistry disabled() {
    return new DynamicSearchIndexRegistry(
        SearchProviderCatalog.discover(), null, false, 1, 1, 1);
  }

  /**
   * Reports whether callers may create and mutate dynamic indexes.
   *
   * @return Whether dynamic indexing is enabled.
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Reports whether live indexes can be saved to disk.
   *
   * @return {@code true} when a checkpoint root is configured.
   */
  public boolean isPersistenceConfigured() {
    return checkpointStore != null;
  }

  /**
   * Validates a provider selection for a new live vector index.
   *
   * @param selector Provider selection, or the unset default instance.
   * @throws AnalysisException If the selection is unknown or lacks live vector support.
   */
  public void validateProvider(SearchProviderSelector selector) {
    requireOpen();
    requireEnabled();
    if (selector == null || selector.getKindCase()
        == SearchProviderSelector.KindCase.KIND_NOT_SET) {
      defaultVectorInstance();
      return;
    }
    final SearchProviderCatalog.Instance instance = catalog.resolve(selector);
    if (!instance.has(SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_VECTOR)
        || !instance.has(SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_LIVE)) {
      throw AnalysisException.failedPrecondition("Search provider instance '"
          + instance.instanceId() + "' does not serve live vector components");
    }
  }

  /**
   * Returns the maximum documents retained by one dynamic index.
   *
   * @return Per-index document limit.
   */
  public int maxDocumentsPerIndex() {
    return MAX_DOCUMENTS_PER_INDEX;
  }

  /**
   * Returns the maximum documents accepted by one indexing request.
   *
   * @return Per-request document limit.
   */
  public int maxDocumentsPerRequest() {
    return MAX_DOCUMENTS_PER_REQUEST;
  }

  /**
   * Returns the maximum serialized source-document bytes retained by one dynamic index.
   *
   * @return Per-index source-document byte limit.
   */
  public int maxSourceDocumentBytesPerIndex() {
    return MAX_SOURCE_DOCUMENT_BYTES_PER_INDEX;
  }

  /**
   * Returns the provider catalog behind this registry.
   *
   * @return The configured catalog, never {@code null}.
   */
  SearchProviderCatalog catalog() {
    return catalog;
  }

  /**
   * Creates or extends one index after validating the complete request.
   *
   * @param request Documents and embedding selection to index.
   * @return The published index snapshot summary.
   * @throws AnalysisException If the request is invalid or exceeds a bound.
   */
  public synchronized IndexDocumentsResponse index(IndexDocumentsRequest request) {
    requireOpen();
    requireEnabled();
    if (request == null) {
      throw AnalysisException.invalidArgument("IndexDocuments request must not be null");
    }
    if (request.getDocumentsCount() < 1
        || request.getDocumentsCount() > MAX_DOCUMENTS_PER_REQUEST) {
      throw AnalysisException.invalidArgument("IndexDocuments documents must contain between 1 and "
          + MAX_DOCUMENTS_PER_REQUEST + " entries");
    }
    validateSelector(request.getEmbedding());
    final SearchProviderCatalog.Instance requestedInstance = resolveVectorInstance(request);
    final DynamicIndex existing = request.hasIndexId()
        ? requireDynamic(request.getIndexId()) : null;
    if (existing != null && existing.sealed()) {
      throw AnalysisException.failedPrecondition("Sealed search index '"
          + existing.descriptor().getIndexId() + "' is immutable");
    }
    if (existing != null && requestedInstance != null
        && !requestedInstance.instanceId().equals(existing.instance().instanceId())) {
      throw AnalysisException.failedPrecondition(
          "IndexDocuments provider must match the existing dynamic index provider instance '"
              + existing.instance().instanceId() + "'");
    }
    if (existing == null && indexes.size() >= maxIndexes) {
      throw AnalysisException.resourceExhausted("Dynamic search index count reached " + maxIndexes);
    }
    final String indexId = existing == null ? newIndexId() : existing.descriptor().getIndexId();
    final String displayName = existing == null
        ? requireDisplayName(request.getDisplayName()) : existing.descriptor().getDisplayName();
    final List<IndexedChunk> additions = extract(request, existing);
    final DynamicIndex target = existing == null
        ? new DynamicIndex(indexId, displayName, additions.getFirst().route(),
            requestedInstance == null ? defaultVectorInstance() : requestedInstance,
            keywordInstance)
        : existing;
    final Snapshot candidate = target.prepare(request.getDocumentsList(), additions);
    validateGlobalBudget(existing, candidate);
    target.publish(candidate);
    if (existing == null) {
      indexes.put(indexId, target);
    }
    return IndexDocumentsResponse.newBuilder()
        .setIndex(target.descriptor())
        .setIndexedDocuments(request.getDocumentsCount())
        .setIndexedChunks(additions.size())
        .build();
  }

  /**
   * Returns current dynamic index descriptors in creation order.
   *
   * @return Immutable descriptor snapshots.
   */
  synchronized List<SearchIndexDescriptor> descriptors() {
    requireOpen();
    return indexes.values().stream().map(DynamicIndex::descriptor).toList();
  }

  /**
   * Resolves one dynamic index.
   *
   * @param indexId Opaque dynamic index identifier.
   * @return The matching provider.
   * @throws AnalysisException If the identifier is blank or unknown.
   */
  synchronized SearchIndexProvider require(String indexId) {
    requireOpen();
    return requireDynamic(indexId);
  }

  /**
   * Returns one dynamic index without failing.
   *
   * @param indexId Opaque dynamic index identifier.
   * @return The matching provider, or {@code null} when the id is unknown.
   */
  synchronized SearchIndexProvider find(String indexId) {
    requireOpen();
    return indexId == null ? null : indexes.get(indexId);
  }

  /**
   * Returns one dynamic index's retained chunks for server-side replay.
   *
   * @param indexId Opaque dynamic index identifier.
   * @return Immutable chunks in snapshot order.
   * @throws AnalysisException If the identifier is blank or unknown.
   */
  synchronized List<RetainedChunk> retainedChunks(String indexId) {
    requireOpen();
    return requireDynamic(indexId).snapshot().chunks().stream()
        .map(chunk -> new RetainedChunk(chunk.record(), chunk.route()))
        .toList();
  }

  /**
   * Returns the number of raw float values retained by one published snapshot.
   *
   * @param indexId Opaque dynamic index identifier.
   * @return Retained raw float count.
   * @throws AnalysisException If the index is unknown.
   */
  synchronized long retainedRawVectorValues(String indexId) {
    requireOpen();
    return requireDynamic(indexId).snapshot().chunks().stream()
        .filter(chunk -> chunk.rawVector() != null)
        .mapToLong(chunk -> chunk.rawVector().length)
        .sum();
  }

  /**
   * Publishes a new index beside its source from replayed chunks, blue/green.
   *
   * @param sourceIndexId Source index whose display name and provider are inherited.
   * @param selector Vector storage for the new index, or {@code null} to keep the
   *     source index's instance.
   * @param chunks Replayed chunks sharing one route and dimension, at least one.
   * @return Descriptor of the newly published index.
   * @throws AnalysisException If the source is unknown, the selector is invalid, a chunk
   *     vector is invalid, or a bound would be exceeded.
   */
  synchronized SearchIndexDescriptor reindexInto(
      String sourceIndexId, SearchProviderSelector selector, List<IndexedChunk> chunks) {
    requireOpen();
    requireEnabled();
    final DynamicIndex source = requireDynamic(sourceIndexId);
    if (chunks.isEmpty()) {
      throw AnalysisException.failedPrecondition(
          "ReindexIndex source '" + sourceIndexId + "' has no retained chunks");
    }
    SearchProviderCatalog.Instance instance = source.instance();
    if (selector != null) {
      instance = catalog.resolve(selector);
      if (!instance.has(SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_VECTOR)
          || !instance.has(SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_LIVE)) {
        throw AnalysisException.failedPrecondition("Search provider instance '"
            + instance.instanceId() + "' does not serve live vector components");
      }
    }
    if (indexes.size() >= maxIndexes) {
      throw AnalysisException.resourceExhausted("Dynamic search index count reached " + maxIndexes);
    }
    final EmbeddingRoute route = chunks.getFirst().route();
    final int dimension = chunks.getFirst().vector().length;
    if (dimension < 1 || dimension > MAX_VECTOR_DIMENSION) {
      throw AnalysisException.failedPrecondition(
          "Replayed vectors must have a dimension between 1 and " + MAX_VECTOR_DIMENSION);
    }
    for (IndexedChunk chunk : chunks) {
      if (chunk.vector().length != dimension || !compatible(route, chunk.route())) {
        throw AnalysisException.failedPrecondition(
            "Replayed chunks do not share one vector space and dimension");
      }
      double norm = 0;
      for (float value : chunk.vector()) {
        if (!Float.isFinite(value)) {
          throw AnalysisException.failedPrecondition(
              "Replayed chunk '" + chunk.record().chunkId()
                  + "' contains a non-finite vector value");
        }
        norm += (double) value * value;
      }
      if (norm == 0) {
        throw AnalysisException.failedPrecondition(
            "Replayed chunk '" + chunk.record().chunkId() + "' has a zero vector");
      }
    }
    final DynamicIndex target = new DynamicIndex(newIndexId(),
        source.descriptor().getDisplayName(), route, instance, keywordInstance);
    final Snapshot snapshot = target.prepare(List.of(), List.copyOf(chunks));
    validateGlobalBudget(null, snapshot);
    target.publish(snapshot);
    indexes.put(target.descriptor().getIndexId(), target);
    return target.descriptor();
  }

  /**
   * Deletes one dynamic index.
   *
   * @param indexId Opaque dynamic index identifier.
   * @return {@code true} when an index was removed.
   * @throws AnalysisException If mutation is disabled or the identifier is blank.
   */
  public synchronized boolean delete(String indexId) {
    requireOpen();
    requireEnabled();
    if (indexId == null || indexId.isBlank()) {
      throw AnalysisException.invalidArgument("DeleteSearchIndex index_id must not be blank");
    }
    final DynamicIndex existing = indexes.get(indexId);
    if (existing != null) {
      if (checkpointStore != null && existing.persisted()) {
        try {
          checkpointStore.delete(indexId);
        } catch (IOException e) {
          throw new UncheckedIOException(
              "Failed to delete the checkpoint of index '" + indexId + "'", e);
        }
      }
      indexes.remove(indexId);
      existing.close();
      return true;
    }
    return false;
  }

  /**
   * Persists one dynamic index as a checkpoint, replacing any previous checkpoint.
   *
   * @param indexId Opaque dynamic index identifier.
   * @return The descriptor with {@code persisted} set.
   * @throws AnalysisException If persistence is not configured, the index is unknown, or
   *     its provider instance is not persistent.
   */
  public synchronized SearchIndexDescriptor persist(String indexId) {
    requireOpen();
    requireEnabled();
    final DynamicIndex index = requireDynamic(indexId);
    persist(index, index.sealed());
    return index.descriptor();
  }

  /**
   * Persists one dynamic index and marks it immutable.
   *
   * @param indexId Opaque dynamic index identifier.
   * @return The descriptor, immutable and persisted.
   * @throws AnalysisException If persistence is not configured, the index is unknown, or
   *     its provider instance is not persistent.
   */
  public synchronized SearchIndexDescriptor seal(String indexId) {
    requireOpen();
    requireEnabled();
    final DynamicIndex index = requireDynamic(indexId);
    persist(index, true);
    index.markSealed();
    return index.descriptor();
  }

  /**
   * Rewrites the checkpoint of every persisted index whose content changed since its
   * last checkpoint. Sealed indexes never change and are skipped.
   *
   * @return Ids of the indexes whose checkpoints were rewritten.
   */
  public synchronized List<String> checkpointPersistedIndexes() {
    requireOpen();
    final List<String> rewritten = new ArrayList<>();
    for (DynamicIndex index : indexes.values()) {
      if (index.persisted() && !index.sealed()
          && !index.snapshot().contentHash().equals(index.lastPersistedContentHash())) {
        persist(index, false);
        rewritten.add(index.descriptor().getIndexId());
      }
    }
    return List.copyOf(rewritten);
  }

  /**
   * Writes one index checkpoint and records the persisted content hash.
   *
   * @param index Index to persist.
   * @param sealed Whether the checkpoint is written as sealed.
   * @throws AnalysisException If persistence is not configured or the instance is not
   *     persistent.
   */
  private void persist(DynamicIndex index, boolean sealed) {
    if (checkpointStore == null) {
      throw AnalysisException.failedPrecondition(
          "Index persistence is not configured; set " + WorkspaceCheckpointStore.ROOT_KEY);
    }
    if (!index.instance().has(SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_PERSISTENT)) {
      throw AnalysisException.failedPrecondition("Search provider instance '"
          + index.instance().instanceId() + "' is not persistent");
    }
    final Snapshot snapshot = index.snapshot();
    final List<PersistedSearchChunk> chunks = new ArrayList<>(snapshot.chunks().size());
    for (StoredChunk chunk : snapshot.chunks()) {
      chunks.add(toChunkProto(chunk));
    }
    final String indexId = index.descriptor().getIndexId();
    try {
      checkpointStore.write(new WorkspaceCheckpointStore.CheckpointHeader(
          indexId, index.descriptor().getDisplayName(), index.instance().instanceId(),
          index.route(), index.dimension(), sealed, snapshot.contentHash()), chunks,
          snapshot.vectorSegments().size(),
          (segment, directory) -> index.instance().configured()
              .writeLiveVectorIndex(snapshot.vectorSegments().get(segment).index(), directory));
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to persist the checkpoint of index '" + indexId + "'", e);
    }
    index.markPersisted(snapshot.contentHash());
  }

  /**
   * Restores every stored checkpoint into this registry.
   *
   * @throws IllegalStateException If a checkpoint is corrupt or names an unavailable
   *     provider instance.
   */
  private void restore() {
    final List<WorkspaceCheckpointStore.RestoredCheckpoint> checkpoints;
    try {
      checkpoints = checkpointStore.restoreAll();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to restore search index checkpoints", e);
    }
    if (checkpoints.size() > maxIndexes) {
      throw new IllegalStateException("Stored checkpoint count " + checkpoints.size()
          + " exceeds the configured dynamic index limit of " + maxIndexes);
    }
    for (WorkspaceCheckpointStore.RestoredCheckpoint checkpoint : checkpoints) {
      final WorkspaceCheckpointStore.CheckpointHeader header = checkpoint.header();
      final SearchProviderCatalog.Instance instance =
          catalog.findOrNull(header.providerInstanceId());
      if (instance == null) {
        throw new IllegalStateException("Checkpoint of index '" + header.indexId()
            + "' names unavailable provider instance '" + header.providerInstanceId() + "'");
      }
      if (!instance.has(SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_VECTOR)
          || !instance.has(SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_LIVE)
          || !instance.has(SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_PERSISTENT)) {
        throw new IllegalStateException("Checkpoint of index '" + header.indexId()
            + "' requires a persistent live vector provider instance, but '"
            + header.providerInstanceId() + "' does not declare those capabilities");
      }
      final List<VectorSegment> vectorSegments = new ArrayList<>(
          checkpoint.vectorSegments().size());
      final Set<String> restoredVectorIds = new HashSet<>();
      for (var segmentDirectory : checkpoint.vectorSegments()) {
        final SearchIndexProviderFactory.ConfiguredProvider.RestoredVectorIndex restored;
        try {
          restored = instance.configured().readLiveVectorIndex(segmentDirectory);
        } catch (IOException e) {
          throw new IllegalStateException("Checkpoint of index '" + header.indexId()
              + "' has an unreadable provider vector segment", e);
        }
        if (restored.index().dimension() != header.dimension()) {
          throw new IllegalStateException("Checkpoint of index '" + header.indexId()
              + "' has a provider vector segment with the wrong dimension");
        }
        final Set<String> ids = Set.copyOf(restored.ids());
        if (ids.size() != restored.ids().size()
            || ids.stream().anyMatch(id -> !restoredVectorIds.add(id))) {
          throw new IllegalStateException("Checkpoint of index '" + header.indexId()
              + "' has duplicate provider vector ids");
        }
        vectorSegments.add(new VectorSegment(restored.index(), ids));
      }
      final List<StoredChunk> chunks = new ArrayList<>(checkpoint.chunks().size());
      for (PersistedSearchChunk chunk : checkpoint.chunks()) {
        if (!chunk.hasVectorId() || chunk.getVectorId().isBlank()
            || !chunk.hasVectorSegment()
            || chunk.getVectorSegment() >= vectorSegments.size()
            || !vectorSegments.get(chunk.getVectorSegment()).ids().contains(chunk.getVectorId())) {
          throw new IllegalStateException("Checkpoint of index '" + header.indexId()
              + "' contains an invalid provider vector reference");
        }
        if (!chunk.hasVectorSha256() || chunk.getVectorSha256().size() != 32) {
          throw new IllegalStateException("Checkpoint of index '" + header.indexId()
              + "' contains an invalid vector digest");
        }
        final StoredChunk restored = storedFromChunkProto(chunk);
        if (!compatible(header.route(), restored.route())) {
          throw new IllegalStateException("Checkpoint of index '" + header.indexId()
              + "' contains a chunk whose embedding route is incompatible with its header");
        }
        chunks.add(restored);
      }
      if (!contentHash(chunks).equals(header.contentHash())) {
        throw new IllegalStateException("Checkpoint of index '" + header.indexId()
            + "' does not match its declared content hash");
      }
      final DynamicIndex index = new DynamicIndex(header.indexId(), header.displayName(),
          header.route(), instance, keywordInstance);
      final Snapshot snapshot = index.restoreSnapshot(chunks, vectorSegments);
      validateGlobalBudget(null, snapshot);
      index.publish(snapshot);
      index.markPersisted(header.contentHash());
      if (header.sealed()) {
        index.markSealed();
      }
      indexes.put(header.indexId(), index);
    }
  }

  /**
   * Converts one in-memory chunk to its persisted record.
   *
   * @param chunk Snapshot chunk.
   * @return Persisted chunk record.
   */
  private static PersistedSearchChunk toChunkProto(StoredChunk chunk) {
    final PersistedSearchChunk.Builder builder = PersistedSearchChunk.newBuilder()
        .setDocumentId(chunk.record().documentId())
        .setChunkId(chunk.record().chunkId())
        .setChunkGroupId(chunk.record().chunkGroupId())
        .setSourceDocument(chunk.record().sourceDocument())
        .setSourceSpan(chunk.record().sourceSpan())
        .setIndexedText(chunk.record().indexedText())
        .setRoute(chunk.route())
        .setVectorId(chunk.vectorId())
        .setVectorSegment(chunk.vectorSegment())
        .setVectorSha256(chunk.vectorSha256());
    if (chunk.rawVector() != null) {
      for (float value : chunk.rawVector()) {
        builder.addVector(value);
      }
    }
    return builder.build();
  }

  /**
   * Converts one persisted record back to an in-memory chunk.
   *
   * <p>A provider that retains raw vectors writes them into the record, and they come back
   * here so the index can keep rebuilding its single exact segment after a restart. Their
   * digest must match the one the record carries.</p>
   *
   * @param chunk Persisted chunk record.
   * @return Snapshot chunk.
   * @throws IllegalStateException If the record shape is invalid or a retained vector does
   *     not match its digest.
   */
  private static StoredChunk storedFromChunkProto(PersistedSearchChunk chunk) {
    float[] rawVector = null;
    if (chunk.getVectorCount() > 0) {
      rawVector = new float[chunk.getVectorCount()];
      for (int i = 0; i < rawVector.length; i++) {
        rawVector[i] = chunk.getVector(i);
      }
      if (!ByteString.copyFrom(vectorSha256(rawVector)).equals(chunk.getVectorSha256())) {
        throw new IllegalStateException("Checkpoint chunk '" + chunk.getChunkId()
            + "' carries a retained vector that does not match its digest");
      }
    }
    try {
      return new StoredChunk(
          new SearchRecord(chunk.getDocumentId(), chunk.getChunkId(),
              chunk.getChunkGroupId().isBlank() ? "default" : chunk.getChunkGroupId(),
              chunk.getSourceDocument(), chunk.getSourceSpan(), chunk.getIndexedText()),
          rawVector, chunk.getRoute(), chunk.getVectorId(), chunk.getVectorSegment(),
          chunk.getVectorSha256());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Checkpoint chunk '" + chunk.getChunkId()
          + "' is invalid: " + e.getMessage(), e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    indexes.values().forEach(DynamicIndex::close);
    indexes.clear();
  }

  /**
   * Resolves one dynamic index without repeating lifecycle validation.
   *
   * @param indexId Opaque dynamic index identifier.
   * @return The matching index.
   * @throws AnalysisException If the identifier is blank or unknown.
   */
  private DynamicIndex requireDynamic(String indexId) {
    if (indexId == null || indexId.isBlank()) {
      throw AnalysisException.invalidArgument("Dynamic search index_id must not be blank");
    }
    final DynamicIndex index = indexes.get(indexId);
    if (index == null) {
      throw AnalysisException.notFound("Unknown dynamic search index '" + indexId + "'");
    }
    return index;
  }

  /**
   * Validates a candidate snapshot against server-wide memory ceilings.
   *
   * @param replaced Existing index replaced by the candidate, or {@code null}.
   * @param candidate Candidate snapshot.
   * @throws AnalysisException If a global ceiling would be exceeded.
   */
  private void validateGlobalBudget(DynamicIndex replaced, Snapshot candidate) {
    long vectorValues = candidate.vectorValues();
    long sourceBytes = candidate.sourceDocumentBytes();
    for (DynamicIndex index : indexes.values()) {
      if (index != replaced) {
        vectorValues += index.snapshot().vectorValues();
        sourceBytes += index.snapshot().sourceDocumentBytes();
      }
    }
    if (vectorValues > maxVectorValues) {
      throw AnalysisException.resourceExhausted(
          "Dynamic search vector memory exceeds the server-wide value limit of "
              + maxVectorValues);
    }
    if (sourceBytes > maxSourceBytes) {
      throw AnalysisException.resourceExhausted(
          "Dynamic search source documents exceed the server-wide byte limit of "
              + maxSourceBytes);
    }
  }

  /**
   * Extracts matching embedded chunks from an indexing request.
   *
   * @param request Indexing request.
   * @param existing Existing target index, or {@code null} for a new index.
   * @return Validated chunks ready for publication.
   * @throws AnalysisException If documents or embeddings violate the contract.
   */
  private static List<IndexedChunk> extract(
      IndexDocumentsRequest request, DynamicIndex existing) {
    final List<IndexedChunk> additions = new ArrayList<>();
    final Set<String> requestedGroups = new HashSet<>(request.getChunkGroupIdsList());
    if (requestedGroups.size() != request.getChunkGroupIdsCount()
        || requestedGroups.stream().anyMatch(String::isBlank)) {
      throw AnalysisException.invalidArgument(
          "IndexDocuments chunk_group_ids must be distinct and nonblank");
    }
    final Set<String> documentIds = new HashSet<>();
    for (OpenNlpDocument document : request.getDocumentsList()) {
      if (document.getDocId().isBlank() || !documentIds.add(document.getDocId())) {
        throw AnalysisException.invalidArgument(
            "IndexDocuments documents require distinct nonblank doc_id values");
      }
      int selectedInDocument = 0;
      final Set<String> documentGroupIds = new HashSet<>();
      for (int groupIndex = 0;
          groupIndex < document.getChunkEmbeddingGroupsCount(); groupIndex++) {
        final ChunkEmbeddingGroup group = document.getChunkEmbeddingGroups(groupIndex);
        if (group.getGroupId().isBlank()
            || !group.getGroupId().equals(group.getGroupId().trim())
            || !documentGroupIds.add(group.getGroupId())) {
          throw AnalysisException.failedPrecondition("Document '" + document.getDocId()
              + "' chunk groups require distinct nonblank trimmed group_id values");
        }
        if (!requestedGroups.isEmpty() && !requestedGroups.contains(group.getGroupId())) {
          continue;
        }
        for (int chunkIndex = 0; chunkIndex < group.getChunksCount(); chunkIndex++) {
          final var chunk = group.getChunks(chunkIndex);
          for (EmbeddingResult embedding : chunk.getEmbeddingsList()) {
            if (!matches(request.getEmbedding(), embedding)) {
              continue;
            }
            validateEmbedding(embedding, existing);
            final String chunkId = document.getDocId() + ":" + groupIndex
                + ":" + chunkIndex;
            final String indexedText = chunk.hasTextContent()
                ? chunk.getTextContent() : coveredText(document, chunk.getAnnotationSpan());
            additions.add(new IndexedChunk(
                searchRecord(document, chunkId, group.getGroupId(),
                    chunk.getAnnotationSpan(), indexedText),
                toArray(embedding), embedding.getRoute()));
            selectedInDocument++;
            break;
          }
        }
      }
      if (selectedInDocument == 0) {
        throw AnalysisException.failedPrecondition("Document '" + document.getDocId()
            + "' has no chunk embedding for model '" + request.getEmbedding().getModelId() + "'");
      }
    }
    final EmbeddingRoute first = additions.getFirst().route();
    for (IndexedChunk addition : additions) {
      if (addition.vector().length != additions.getFirst().vector().length
          || !compatible(first, addition.route())) {
        throw AnalysisException.failedPrecondition(
            "Selected chunk embeddings do not share one vector space and dimension");
      }
    }
    return List.copyOf(additions);
  }

  /**
   * Resolves the requested vector storage instance for a dynamic index.
   *
   * @param request Indexing request.
   * @return The selected configured instance, or {@code null} when unset.
   * @throws AnalysisException If the selector names no configured instance or the
   *     instance does not serve live vector components.
   */
  private SearchProviderCatalog.Instance resolveVectorInstance(IndexDocumentsRequest request) {
    if (!request.hasProvider()) {
      return null;
    }
    final SearchProviderCatalog.Instance instance = catalog.resolve(request.getProvider());
    if (!instance.has(SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_VECTOR)
        || !instance.has(SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_LIVE)) {
      throw AnalysisException.failedPrecondition("Search provider instance '"
          + instance.instanceId() + "' does not serve live vector components");
    }
    return instance;
  }

  /**
   * Returns the default vector instance used when a request sets no provider.
   *
   * @return The flat float default instance.
   * @throws IllegalStateException If the flat float provider is not on the classpath.
   */
  private SearchProviderCatalog.Instance defaultVectorInstance() {
    final SearchProviderCatalog.Instance instance = catalog.defaultInstance(
        StandardSearchProvider.STANDARD_SEARCH_PROVIDER_FLAT_FLOAT);
    if (instance == null) {
      throw new IllegalStateException(
          "The default flat float search provider is not on the classpath");
    }
    return instance;
  }

  /**
   * Tests whether one embedding matches the requested model and backend.
   *
   * @param selector Requested embedding selector.
   * @param embedding Candidate embedding.
   * @return {@code true} when the candidate matches.
   */
  private static boolean matches(EmbeddingSelector selector, EmbeddingResult embedding) {
    if (!selector.getModelId().equals(embedding.getModelId()) || !embedding.hasRoute()) {
      return false;
    }
    final String backend = selectedBackend(selector);
    return backend == null || backend.equals(embedding.getRoute().getBackendId());
  }

  /**
   * Creates a validated search record and maps shape errors to a client failure.
   *
   * @param document Source document.
   * @param chunkId Stable chunk identifier.
   * @param chunkGroupId Stable projection identifier.
   * @param span Chunk span in the source document.
   * @param indexedText Exact text represented by the embedding.
   * @return Validated search record.
   * @throws AnalysisException If the document shape is inconsistent.
   */
  private static SearchRecord searchRecord(
      OpenNlpDocument document,
      String chunkId,
      String chunkGroupId,
      org.apache.opennlp.grpc.v1.AnnotationSpan span,
      String indexedText) {
    try {
      return new SearchRecord(document.getDocId(), chunkId, chunkGroupId,
          searchSource(document), span, indexedText);
    } catch (IllegalArgumentException e) {
      throw AnalysisException.failedPrecondition(
          "Document '" + document.getDocId() + "' contains an invalid indexed chunk: "
              + e.getMessage());
    }
  }

  /**
   * Retains only fields required to identify and map a search hit. Chunk vectors and
   * analysis layers are indexing input, not hit source data, and retaining them would
   * repeat an entire analyzed document in every returned hit.
   *
   * @param document An analyzed document supplied for indexing.
   * @return A bounded source document for search results.
   */
  private static OpenNlpDocument searchSource(OpenNlpDocument document) {
    final OpenNlpDocument.Builder source = OpenNlpDocument.newBuilder()
        .setDocId(document.getDocId())
        .setRawText(document.getRawText())
        .setOffsetEncoding(document.getOffsetEncoding());
    if (document.hasMetadata()) {
      source.setMetadata(document.getMetadata());
    }
    return source.build();
  }

  /**
   * Validates the requested embedding selector.
   *
   * @param selector Selector to validate.
   * @throws AnalysisException If the selector is ambiguous or incomplete.
   */
  static void validateSelector(EmbeddingSelector selector) {
    if (selector == null || selector.getModelId().isBlank()
        || !selector.getModelId().equals(selector.getModelId().trim())) {
      throw AnalysisException.invalidArgument(
          "IndexDocuments embedding.model_id must be nonblank and trimmed");
    }
    if (selector.hasBackendId() && selector.getBackend().getKindCase()
        != org.apache.opennlp.grpc.v1.EmbeddingBackendSelector.KindCase.KIND_NOT_SET) {
      throw AnalysisException.invalidArgument(
          "IndexDocuments embedding cannot set both backend_id and backend");
    }
    selectedBackend(selector);
  }

  /**
   * Resolves a selector's optional backend identifier.
   *
   * @param selector Validated embedding selector.
   * @return Backend identifier, or {@code null} when any compatible backend is allowed.
   * @throws AnalysisException If an explicitly selected backend is invalid.
   */
  static String selectedBackend(EmbeddingSelector selector) {
    if (selector.hasBackendId()) {
      if (selector.getBackendId().isBlank()
          || !selector.getBackendId().equals(selector.getBackendId().trim())) {
        throw AnalysisException.invalidArgument(
            "embedding.backend_id must be nonblank and trimmed");
      }
      return selector.getBackendId();
    }
    if (selector.getBackend().hasCustom()) {
      final String custom = selector.getBackend().getCustom();
      if (custom.isBlank() || !custom.equals(custom.trim())) {
        throw AnalysisException.invalidArgument(
            "embedding.backend.custom must be nonblank and trimmed");
      }
      return custom;
    }
    if (!selector.getBackend().hasStandard()) {
      return null;
    }
    return switch (selector.getBackend().getStandard()) {
      case STANDARD_EMBEDDING_BACKEND_ONNX -> "onnx";
      case STANDARD_EMBEDDING_BACKEND_CUDA -> "cuda";
      case STANDARD_EMBEDDING_BACKEND_STATIC -> "static";
      case STANDARD_EMBEDDING_BACKEND_TEI -> "tei";
      case STANDARD_EMBEDDING_BACKEND_OPENVINO -> "openvino";
      case STANDARD_EMBEDDING_BACKEND_UNSPECIFIED, UNRECOGNIZED -> throw
          AnalysisException.invalidArgument("embedding.backend.standard must be specified");
    };
  }

  /**
   * Validates one selected embedding and its compatibility with the target index.
   *
   * @param embedding Embedding to validate.
   * @param existing Existing target index, or {@code null}.
   * @throws AnalysisException If the embedding is invalid or incompatible.
   */
  private static void validateEmbedding(EmbeddingResult embedding, DynamicIndex existing) {
    if (!embedding.hasRoute() || embedding.getRoute().getModelId().isBlank()
        || embedding.getRoute().getBackendId().isBlank()
        || embedding.getRoute().getVectorSpaceId().isBlank()) {
      throw AnalysisException.failedPrecondition(
          "Selected chunk embedding must carry a complete resolved route");
    }
    if (embedding.getVectorCount() < 1 || embedding.getVectorCount() > MAX_VECTOR_DIMENSION) {
      throw AnalysisException.failedPrecondition(
          "Selected chunk embedding dimension must be between 1 and " + MAX_VECTOR_DIMENSION);
    }
    double norm = 0;
    for (float value : embedding.getVectorList()) {
      if (!Float.isFinite(value)) {
        throw AnalysisException.failedPrecondition(
            "Selected chunk embedding contains a non-finite value");
      }
      norm += (double) value * value;
    }
    if (norm == 0) {
      throw AnalysisException.failedPrecondition(
          "Selected chunk embedding must not be a zero vector");
    }
    if (existing != null && (embedding.getVectorCount() != existing.dimension()
        || !compatible(existing.route(), embedding.getRoute()))) {
      throw AnalysisException.failedPrecondition(
          "Selected chunk embedding is incompatible with the existing dynamic index");
    }
  }

  /**
   * Tests whether two routes identify the same model vector space.
   *
   * @param left First route.
   * @param right Second route.
   * @return {@code true} when model and vector-space identifiers match.
   */
  private static boolean compatible(EmbeddingRoute left, EmbeddingRoute right) {
    return left.getModelId().equals(right.getModelId())
        && left.getVectorSpaceId().equals(right.getVectorSpaceId());
  }

  /**
   * Copies a protobuf vector to a primitive array.
   *
   * @param embedding Source embedding.
   * @return Independent primitive vector.
   */
  private static float[] toArray(EmbeddingResult embedding) {
    final float[] vector = new float[embedding.getVectorCount()];
    for (int index = 0; index < vector.length; index++) {
      vector[index] = embedding.getVector(index);
    }
    return vector;
  }

  /**
   * Resolves a chunk span to source text when no indexed text is present.
   *
   * @param document Source document.
   * @param span Chunk span.
   * @return Covered UTF-16 text.
   * @throws AnalysisException If the offset encoding or span is invalid.
   */
  private static String coveredText(
      OpenNlpDocument document, org.apache.opennlp.grpc.v1.AnnotationSpan span) {
    if (document.getOffsetEncoding()
        != org.apache.opennlp.grpc.v1.OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT) {
      throw AnalysisException.failedPrecondition(
          "Dynamic indexing requires text_content for non-UTF-16 document offsets");
    }
    if (span.getStart() < 0 || span.getEnd() < span.getStart()
        || span.getEnd() > document.getRawText().length()) {
      throw AnalysisException.failedPrecondition("Chunk span is outside document raw_text");
    }
    return document.getRawText().substring(span.getStart(), span.getEnd());
  }

  /**
   * Validates a display name for a new index.
   *
   * @param value Proposed display name.
   * @return The validated value.
   * @throws AnalysisException If the value is blank or padded.
   */
  private static String requireDisplayName(String value) {
    if (value == null || value.isBlank() || !value.equals(value.trim())) {
      throw AnalysisException.invalidArgument(
          "IndexDocuments display_name must be nonblank and trimmed for a new index");
    }
    return value;
  }

  /** @return A new opaque process-local index identifier. */
  private static String newIndexId() {
    return "workspace-" + UUID.randomUUID();
  }

  /** @throws IllegalStateException If this registry has been closed. */
  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("dynamic search index registry is closed");
    }
  }

  /** @throws AnalysisException If dynamic mutation is disabled. */
  private void requireEnabled() {
    if (!enabled) {
      throw AnalysisException.unimplemented(
          "Dynamic search indexing is disabled by the server operator");
    }
  }

  /**
   * One retained chunk: its search record, raw vector, and resolved route.
   *
   * @param record Validated search record.
   * @param vector Raw embedding vector, shared and never mutated.
   * @param route Resolved embedding route.
   */
  record IndexedChunk(SearchRecord record, float[] vector, EmbeddingRoute route) {
  }

  /** One retained source chunk used for drift and re-embedding without its old vector. */
  record RetainedChunk(SearchRecord record, EmbeddingRoute route) {
  }

  private static final class DynamicIndex implements SearchIndexProvider {
    private final String indexId;
    private final String displayName;
    private final EmbeddingRoute route;
    private final SearchProviderCatalog.Instance instance;
    private final SearchProviderCatalog.Instance keywordInstance;
    private volatile Snapshot snapshot = new Snapshot(List.of(), 0, 0, 0, 0,
        contentHash(List.of()), List.of(), null);
    private volatile boolean persisted;
    private volatile boolean sealed;
    private volatile String lastPersistedContentHash;

    /**
     * Creates an unpublished dynamic index.
     *
     * @param indexId Opaque identifier.
     * @param displayName User-facing name.
     * @param route Embedding route shared by all chunks.
     * @param instance Vector storage instance fixed for the index lifetime.
     * @param keywordInstance Keyword component instance, or {@code null} when none is configured.
     */
    DynamicIndex(String indexId, String displayName, EmbeddingRoute route,
        SearchProviderCatalog.Instance instance,
        SearchProviderCatalog.Instance keywordInstance) {
      this.indexId = indexId;
      this.displayName = displayName;
      this.route = route;
      this.instance = instance;
      this.keywordInstance = keywordInstance;
    }

    /** @return The vector storage instance fixed at index creation. */
    SearchProviderCatalog.Instance instance() {
      return instance;
    }

    /** @return Whether a checkpoint of this index exists. */
    boolean persisted() {
      return persisted;
    }

    /** @return Whether this index is sealed immutable. */
    boolean sealed() {
      return sealed;
    }

    /** @return Content hash of the last written checkpoint, or {@code null}. */
    String lastPersistedContentHash() {
      return lastPersistedContentHash;
    }

    /**
     * Records one successfully written checkpoint.
     *
     * @param contentHash Content hash of the persisted snapshot.
     */
    void markPersisted(String contentHash) {
      this.persisted = true;
      this.lastPersistedContentHash = contentHash;
    }

    /** Marks this index sealed immutable. */
    void markSealed() {
      this.sealed = true;
    }

    /**
     * Builds a bounded replacement snapshot without publishing it.
     *
     * @param documents Documents whose prior chunks should be replaced.
     * @param additions New indexed chunks.
     * @return Candidate immutable snapshot.
     * @throws AnalysisException If a per-index bound is exceeded.
     */
    synchronized Snapshot prepare(
        List<OpenNlpDocument> documents, List<IndexedChunk> additions) {
      final Set<String> replaced = documents.stream()
          .map(OpenNlpDocument::getDocId).collect(java.util.stream.Collectors.toSet());
      final List<StoredChunk> merged = new ArrayList<>();
      for (StoredChunk chunk : snapshot.chunks()) {
        if (!replaced.contains(chunk.record().documentId())) {
          merged.add(chunk);
        }
      }
      final List<VectorSegment> vectorSegments;
      final long vectorValues;
      if (instance.configured().retainRawVectors()) {
        final List<StoredChunk> reassigned = new ArrayList<>(merged.size() + additions.size());
        int row = 0;
        for (StoredChunk chunk : merged) {
          reassigned.add(new StoredChunk(chunk.record(), chunk.rawVector(), chunk.route(),
              "row-" + row, 0, chunk.vectorSha256()));
          row++;
        }
        for (IndexedChunk addition : additions) {
          reassigned.add(stored(addition, "row-" + row, 0, true));
          row++;
        }
        merged.clear();
        merged.addAll(reassigned);
        vectorSegments = List.of(buildVectorSegment(merged));
        vectorValues = merged.stream().mapToLong(chunk -> chunk.rawVector().length).sum();
      } else {
        final int segmentIndex = snapshot.vectorSegments().size();
        if (segmentIndex >= WorkspaceCheckpointStore.MAX_VECTOR_SEGMENTS) {
          throw AnalysisException.resourceExhausted(
              "Dynamic index provider vector segments reached "
                  + WorkspaceCheckpointStore.MAX_VECTOR_SEGMENTS);
        }
        final VectorIndex component = instance.configured()
            .createLiveVectorIndex(additions.getFirst().vector().length);
        final Set<String> ids = new HashSet<>();
        int row = 0;
        for (IndexedChunk addition : additions) {
          final String vectorId = "segment-" + segmentIndex + "-row-" + row;
          component.add(vectorId, addition.vector());
          ids.add(vectorId);
          merged.add(stored(addition, vectorId, segmentIndex, false));
          row++;
        }
        component.freeze();
        final List<VectorSegment> appended = new ArrayList<>(snapshot.vectorSegments());
        appended.add(new VectorSegment(component, Set.copyOf(ids)));
        vectorSegments = List.copyOf(appended);
        vectorValues = snapshot.vectorValues()
            + additions.stream().mapToLong(chunk -> chunk.vector().length).sum();
      }
      final int documentCount = Math.toIntExact(merged.stream()
          .map(chunk -> chunk.record().documentId()).distinct().count());
      final long sourceDocumentBytes = merged.stream()
          .collect(java.util.stream.Collectors.toMap(
              chunk -> chunk.record().documentId(),
              chunk -> chunk.record().sourceDocument().getSerializedSize(),
              (left, right) -> left))
          .values().stream().mapToLong(Integer::longValue).sum();
      if (documentCount > MAX_DOCUMENTS_PER_INDEX || merged.size() > MAX_CHUNKS_PER_INDEX
          || sourceDocumentBytes > MAX_SOURCE_DOCUMENT_BYTES_PER_INDEX) {
        throw AnalysisException.resourceExhausted(
            "Dynamic index exceeds its document, chunk, or source-document limit");
      }
      final List<StoredChunk> chunks = List.copyOf(merged);
      return new Snapshot(chunks, documentCount, sourceDocumentBytes, vectorValues,
          additions.getFirst().vector().length, contentHash(chunks), vectorSegments,
          buildKeywordLeg(chunks));
    }

    /**
     * Builds one frozen component from chunks that retain their raw vectors.
     *
     * @param chunks Published chunks with raw vectors.
     * @return Frozen provider vector segment.
     */
    private VectorSegment buildVectorSegment(List<StoredChunk> chunks) {
      final VectorIndex component = instance.configured()
          .createLiveVectorIndex(chunks.getFirst().rawVector().length);
      final Set<String> ids = new HashSet<>();
      for (StoredChunk chunk : chunks) {
        component.add(chunk.vectorId(), chunk.rawVector());
        ids.add(chunk.vectorId());
      }
      component.freeze();
      return new VectorSegment(component, Set.copyOf(ids));
    }

    /** Builds the immutable keyword component through its separately selected SPI instance. */
    private KeywordQueryIndex buildKeywordLeg(List<StoredChunk> chunks) {
      if (keywordInstance == null) {
        return null;
      }
      final List<org.apache.opennlp.grpc.spi.search.QueryCandidate> candidates = chunks.stream()
          .map(chunk -> new org.apache.opennlp.grpc.spi.search.QueryCandidate(
              chunk.record(), chunk.rawVector()))
          .toList();
      return keywordInstance.configured().createKeywordQueryIndex(candidates);
    }

    /**
     * Rebuilds immutable non-vector components around restored provider-owned segments.
     *
     * @param chunks Restored active chunks.
     * @param vectorSegments Restored provider vector segments.
     * @return Validated immutable snapshot.
     * @throws IllegalStateException If a fixed index bound is exceeded.
     */
    private Snapshot restoreSnapshot(
        List<StoredChunk> chunks, List<VectorSegment> vectorSegments) {
      final int documentCount = Math.toIntExact(chunks.stream()
          .map(chunk -> chunk.record().documentId()).distinct().count());
      final long sourceDocumentBytes = chunks.stream()
          .collect(java.util.stream.Collectors.toMap(
              chunk -> chunk.record().documentId(),
              chunk -> chunk.record().sourceDocument().getSerializedSize(),
              (left, right) -> left))
          .values().stream().mapToLong(Integer::longValue).sum();
      final long vectorValues = vectorSegments.stream()
          .mapToLong(segment -> (long) segment.index().size() * segment.index().dimension())
          .sum();
      if (documentCount > MAX_DOCUMENTS_PER_INDEX || chunks.size() > MAX_CHUNKS_PER_INDEX
          || sourceDocumentBytes > MAX_SOURCE_DOCUMENT_BYTES_PER_INDEX) {
        throw new IllegalStateException("Restored dynamic index exceeds a fixed safety bound");
      }
      return new Snapshot(List.copyOf(chunks), documentCount, sourceDocumentBytes,
          vectorValues, vectorSegments.getFirst().index().dimension(), contentHash(chunks),
          List.copyOf(vectorSegments),
          buildKeywordLeg(chunks));
    }

    /**
     * Atomically publishes a validated snapshot.
     *
     * @param candidate Snapshot to publish.
     */
    synchronized void publish(Snapshot candidate) {
      snapshot = candidate;
    }

    /** @return The current immutable snapshot. */
    Snapshot snapshot() {
      return snapshot;
    }

    /** @return The current vector dimension, or zero for an empty index. */
    int dimension() {
      return snapshot.dimension();
    }

    /** @return The index embedding route. */
    EmbeddingRoute route() {
      return route;
    }

    /** {@inheritDoc} */
    @Override
    public SearchIndexDescriptor descriptor() {
      final Snapshot current = snapshot;
      final boolean exhaustive = instance.standard()
          == StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT;
      final SearchIndexDescriptor.Builder descriptor = SearchIndexDescriptor.newBuilder()
          .setIndexId(indexId)
          .setDisplayName(displayName)
          .setProvider(instance.selector())
          .setEmbeddingRoute(route)
          .setDimension(dimension())
          .setMetric(SearchMetric.SEARCH_METRIC_COSINE)
          .setSize(current.chunks().size())
          .setImmutable(sealed)
          .setPersisted(persisted)
          .setCorpus(SearchCorpusDescriptor.newBuilder()
              .setTitle(displayName)
              .setProvenanceSummary("Server-owned in-memory workspace"))
          .setMaxTopK(exhaustive
              ? Math.max(1, current.chunks().size())
              : Math.min(DEFAULT_MAX_TOP_K, Math.max(1, current.chunks().size())))
          .setMaxQueryBytes(DEFAULT_MAX_QUERY_BYTES)
          .setBuild(SearchIndexBuildDescriptor.newBuilder()
              .setBundleFormatVersion(1)
              .setBundleArtifactHash(current.contentHash())
              .setBuilderId("opennlp-grpc-workspace")
              .setBuilderVersion("1")
              .setPreparationConfigHash(preparationHash(route,
                  instance.configured().preparationIdentity())))
          .setMaxResponseBytes(DEFAULT_MAX_RESPONSE_BYTES)
          .setSupportsAllHits(exhaustive);
      descriptor.addComponents(SearchIndexComponent.newBuilder()
          .setKind(SearchComponentKind.SEARCH_COMPONENT_KIND_VECTOR)
          .setProviderInstanceId(instance.instanceId()));
      if (keywordInstance != null) {
        descriptor.addComponents(SearchIndexComponent.newBuilder()
            .setKind(SearchComponentKind.SEARCH_COMPONENT_KIND_KEYWORD)
            .setProviderInstanceId(keywordInstance.instanceId())
            .setAnalysisChain(keywordInstance.configured().analysisChain()));
      }
      return descriptor.build();
    }

    /** {@inheritDoc} */
    @Override
    public List<org.apache.opennlp.grpc.spi.search.QueryCandidate> queryCandidates() {
      return snapshot.chunks().stream()
          .map(chunk -> new org.apache.opennlp.grpc.spi.search.QueryCandidate(
              chunk.record(), chunk.rawVector()))
          .toList();
    }

    /** {@inheritDoc} */
    @Override
    public KeywordQueryIndex keywordQueryIndex() {
      return snapshot.keywordLeg();
    }

    /** {@inheritDoc} */
    @Override
    public List<SearchResult> search(float[] queryVector, int topK) {
      final Snapshot current = snapshot;
      if (queryVector == null || queryVector.length != dimension()) {
        throw new IllegalArgumentException("queryVector must match the dynamic index dimension");
      }
      if (current.vectorSegments().isEmpty()) {
        return List.of();
      }
      final Map<String, StoredChunk> activeByVectorId = new LinkedHashMap<>();
      for (StoredChunk chunk : current.chunks()) {
        activeByVectorId.put(chunk.vectorId(), chunk);
      }
      final List<SearchResult> results = new ArrayList<>();
      for (VectorSegment segment : current.vectorSegments()) {
        for (VectorIndex.Hit hit : segment.index().topK(queryVector, segment.index().size())) {
          final StoredChunk chunk = activeByVectorId.get(hit.id());
          if (chunk == null) {
            continue;
          }
          results.add(new SearchResult(chunk.record(),
              Math.max(-1, Math.min(1, hit.score()))));
        }
      }
      return results.stream()
          .sorted(java.util.Comparator.comparingDouble(SearchResult::score).reversed()
              .thenComparing(result -> result.record().chunkId())
              .thenComparing(result -> result.record().documentId()))
          .limit(Math.min(topK, results.size()))
          .toList();
    }

  }

  private record Snapshot(
      List<StoredChunk> chunks,
      int documents,
      long sourceDocumentBytes,
      long vectorValues,
      int dimension,
      String contentHash,
      List<VectorSegment> vectorSegments,
      KeywordQueryIndex keywordLeg) {
  }

  /** One immutable provider vector component and the row ids it owns. */
  private record VectorSegment(VectorIndex index, Set<String> ids) {
  }

  /** One published source record and its provider-owned vector reference. */
  private record StoredChunk(
      SearchRecord record,
      float[] rawVector,
      EmbeddingRoute route,
      String vectorId,
      int vectorSegment,
      ByteString vectorSha256) {
  }

  /**
   * Converts one validated input chunk to its immutable published representation.
   *
   * @param chunk Validated input chunk.
   * @param vectorId Provider row identifier.
   * @param vectorSegment Provider segment number.
   * @param retainRawVector Whether to retain the original float array.
   * @return Published chunk.
   */
  private static StoredChunk stored(
      IndexedChunk chunk, String vectorId, int vectorSegment, boolean retainRawVector) {
    return new StoredChunk(chunk.record(), retainRawVector ? chunk.vector() : null,
        chunk.route(), vectorId, vectorSegment,
        ByteString.copyFrom(vectorSha256(chunk.vector())));
  }

  /**
   * Hashes the complete indexed content for descriptor provenance.
   *
   * @param chunks Published chunks.
   * @return Lowercase SHA-256 digest.
   */
  private static String contentHash(List<StoredChunk> chunks) {
    final MessageDigest digest = sha256();
    for (StoredChunk chunk : chunks) {
      updateLengthPrefixed(digest, chunk.record().documentId().getBytes(StandardCharsets.UTF_8));
      updateLengthPrefixed(digest, chunk.record().chunkId().getBytes(StandardCharsets.UTF_8));
      updateLengthPrefixed(digest, chunk.record().sourceDocument().toByteArray());
      updateLengthPrefixed(digest, chunk.record().sourceSpan().toByteArray());
      updateLengthPrefixed(digest, chunk.record().indexedText().getBytes(StandardCharsets.UTF_8));
      updateLengthPrefixed(digest, chunk.route().toByteArray());
      updateLengthPrefixed(digest, chunk.vectorSha256().toByteArray());
    }
    return hex(digest.digest());
  }

  /**
   * Returns the canonical SHA-256 of one raw float vector.
   *
   * @param vector Raw float vector.
   * @return SHA-256 bytes.
   */
  private static byte[] vectorSha256(float[] vector) {
    final MessageDigest digest = sha256();
    for (float value : vector) {
      final int bits = Float.floatToIntBits(value);
      digest.update((byte) (bits >>> 24));
      digest.update((byte) (bits >>> 16));
      digest.update((byte) (bits >>> 8));
      digest.update((byte) bits);
    }
    return digest.digest();
  }

  /**
   * Adds unambiguous length-prefixed bytes to a digest.
   *
   * @param digest Destination digest.
   * @param value Bytes to add.
   */
  private static void updateLengthPrefixed(MessageDigest digest, byte[] value) {
    final int length = value.length;
    digest.update((byte) (length >>> 24));
    digest.update((byte) (length >>> 16));
    digest.update((byte) (length >>> 8));
    digest.update((byte) length);
    digest.update(value);
  }

  /**
   * Hashes the embedding and vector-storage preparation identity. Providers whose
   * parameters change stored vectors, such as TurboQuant's bit width and seed, append
   * their declared preparation identity.
   *
   * @param route Index embedding route.
   * @param preparationIdentity Provider parameter identity, possibly empty.
   * @return Lowercase SHA-256 digest.
   */
  private static String preparationHash(EmbeddingRoute route, String preparationIdentity) {
    final MessageDigest digest = sha256();
    digest.update(route.getModelId().getBytes(StandardCharsets.UTF_8));
    digest.update((byte) 0);
    digest.update(route.getVectorSpaceId().getBytes(StandardCharsets.UTF_8));
    if (!preparationIdentity.isEmpty()) {
      digest.update((byte) 0);
      digest.update(preparationIdentity.getBytes(StandardCharsets.UTF_8));
    }
    return hex(digest.digest());
  }

  /**
   * Creates a SHA-256 digest instance.
   *
   * @return SHA-256 digest.
   * @throws IllegalStateException If the required JDK digest is unavailable.
   */
  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  /**
   * Encodes bytes as lowercase hexadecimal.
   *
   * @param bytes Bytes to encode.
   * @return Lowercase hexadecimal text.
   */
  private static String hex(byte[] bytes) {
    final char[] digits = "0123456789abcdef".toCharArray();
    final char[] result = new char[bytes.length * 2];
    for (int index = 0; index < bytes.length; index++) {
      final int value = bytes[index] & 0xff;
      result[index * 2] = digits[value >>> 4];
      result[index * 2 + 1] = digits[value & 0x0f];
    }
    return new String(result);
  }
}
