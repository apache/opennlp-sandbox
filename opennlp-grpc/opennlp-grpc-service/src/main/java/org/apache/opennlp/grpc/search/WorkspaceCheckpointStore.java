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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.apache.opennlp.grpc.v1.PersistedSearchChunk;

/**
 * Writes and reads live workspace checkpoints under one configured local directory.
 *
 * <p>Each checkpoint is a directory named by its index id, holding the versioned
 * {@code search-index.properties} descriptor beside a {@code chunks.pb} stream of
 * length-delimited {@link PersistedSearchChunk} records and provider-owned immutable
 * vector segments. Writes stage
 * into a hidden temporary directory and swap it in, so the last complete write wins and
 * readers never observe a partial checkpoint. Restore skips hidden directories left by
 * an interrupted swap and the reserved
 * {@value SearchCollectionRegistry#COLLECTIONS_DIR} directory.</p>
 */
public final class WorkspaceCheckpointStore {

  /** Configuration key naming the checkpoint root directory. */
  public static final String ROOT_KEY = "search.persist.root";

  /** Versioned checkpoint descriptor filename, shared with the bundle loader. */
  static final String DESCRIPTOR_FILE = "search-index.properties";
  /** Length-delimited chunk records filename. */
  static final String CHUNKS_FILE = "chunks.pb";
  static final String VECTOR_SEGMENTS_DIR = "vector-segments";
  static final int MAX_VECTOR_SEGMENTS = 10_000;

  private static final int FORMAT_VERSION = 3;
  private static final String KIND_CHECKPOINT = "checkpoint";
  private static final String KIND_SEALED = "sealed";
  private static final String OLD_MARKER = "-old-";
  private static final int MAX_DESCRIPTOR_BYTES = 65_536;
  private static final long MAX_CHUNKS_FILE_BYTES = 512L * 1024 * 1024;

  private final Path root;

  /**
   * Creates a store over one checkpoint root directory.
   *
   * @param root Local directory holding one subdirectory per persisted index. Created on
   *     the first write.
   * @throws IllegalArgumentException If {@code root} is {@code null}.
   */
  public WorkspaceCheckpointStore(Path root) {
    if (root == null) {
      throw new IllegalArgumentException("root must not be null");
    }
    this.root = root;
  }

  /**
   * Creates a store when the configuration names a checkpoint root.
   *
   * @param configuration Server configuration.
   * @return The configured store, or {@code null} when {@value #ROOT_KEY} is absent.
   * @throws IllegalArgumentException If the configuration is {@code null}.
   */
  public static WorkspaceCheckpointStore fromConfiguration(Map<String, String> configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    final String value = configuration.get(ROOT_KEY);
    if (value == null || value.isBlank()) {
      return null;
    }
    return new WorkspaceCheckpointStore(Path.of(value.trim()));
  }

  /**
   * Identity and integrity of one checkpoint.
   *
   * @param indexId Persisted index id, also the checkpoint directory name.
   * @param displayName User-facing index name.
   * @param providerInstanceId Configured vector provider instance behind the index.
   * @param route Embedding route shared by every chunk.
   * @param dimension Vector dimension of every chunk.
   * @param sealed Whether the index is sealed immutable.
   * @param contentHash Lowercase SHA-256 of the complete indexed content.
   */
  record CheckpointHeader(
      String indexId,
      String displayName,
      String providerInstanceId,
      EmbeddingRoute route,
      int dimension,
      boolean sealed,
      String contentHash) {
  }

  /**
   * One checkpoint read back from disk.
   *
   * @param header Declared identity and integrity.
   * @param chunks Persisted chunk records in stored order.
   */
  record RestoredCheckpoint(CheckpointHeader header, List<PersistedSearchChunk> chunks,
                            List<Path> vectorSegments) {
  }

  /** Writes one frozen provider-owned vector segment into its staging directory. */
  @FunctionalInterface
  interface VectorSegmentWriter {

    /**
     * Writes one segment.
     *
     * @param index Zero-based segment number.
     * @param directory Empty segment directory.
     * @throws IOException If the segment cannot be written.
     */
    void write(int index, Path directory) throws IOException;
  }

  /**
   * Writes one checkpoint, replacing any previous checkpoint of the same index.
   *
   * @param header Checkpoint identity and integrity.
   * @param chunks Chunk records in snapshot order, at least one.
   * @param vectorSegmentCount Number of provider vector segments.
   * @param segmentWriter Provider segment writer.
   * @throws IOException If staging or the swap fails.
   */
  void write(CheckpointHeader header, List<PersistedSearchChunk> chunks,
      int vectorSegmentCount, VectorSegmentWriter segmentWriter) throws IOException {
    if (chunks.isEmpty()) {
      throw new IOException("checkpoint for index '" + header.indexId()
          + "' must contain at least one chunk");
    }
    if (vectorSegmentCount < 1 || segmentWriter == null) {
      throw new IOException("checkpoint for index '" + header.indexId()
          + "' requires at least one provider vector segment");
    }
    if (vectorSegmentCount > MAX_VECTOR_SEGMENTS) {
      throw new IOException("checkpoint vector.segment.count exceeds "
          + MAX_VECTOR_SEGMENTS);
    }
    Files.createDirectories(root);
    final Path target = root.resolve(header.indexId());
    final Path staging = Files.createTempDirectory(root, ".opennlp-checkpoint-");
    boolean swapped = false;
    try {
      try (OutputStream output = new BufferedOutputStream(
          Files.newOutputStream(staging.resolve(CHUNKS_FILE)))) {
        for (PersistedSearchChunk chunk : chunks) {
          chunk.writeDelimitedTo(output);
        }
      }
      final Path segmentsDirectory = staging.resolve(VECTOR_SEGMENTS_DIR);
      Files.createDirectory(segmentsDirectory);
      for (int index = 0; index < vectorSegmentCount; index++) {
        final Path segment = segmentsDirectory.resolve(Integer.toString(index));
        Files.createDirectory(segment);
        segmentWriter.write(index, segment);
      }
      final Properties properties = new Properties();
      properties.setProperty("format.version", Integer.toString(FORMAT_VERSION));
      properties.setProperty("bundle.kind", header.sealed() ? KIND_SEALED : KIND_CHECKPOINT);
      properties.setProperty("index.id", header.indexId());
      properties.setProperty("display.name", header.displayName());
      properties.setProperty("provider.instance", header.providerInstanceId());
      properties.setProperty("embedding.model.id", header.route().getModelId());
      properties.setProperty("embedding.backend.id", header.route().getBackendId());
      properties.setProperty("embedding.vector_space.id", header.route().getVectorSpaceId());
      if (header.route().hasArtifactHash()) {
        properties.setProperty("embedding.artifact.sha256", header.route().getArtifactHash());
      }
      properties.setProperty("dimension", Integer.toString(header.dimension()));
      properties.setProperty("size", Integer.toString(chunks.size()));
      properties.setProperty("vector.segment.count", Integer.toString(vectorSegmentCount));
      properties.setProperty("content.sha256", header.contentHash());
      try (OutputStream output = Files.newOutputStream(staging.resolve(DESCRIPTOR_FILE))) {
        properties.store(output, "OpenNLP live search index checkpoint");
      }
      swap(staging, target);
      swapped = true;
    } finally {
      if (!swapped) {
        deleteRecursively(staging);
      }
    }
  }

  /**
   * Reads every complete checkpoint under the root.
   *
   * @return Checkpoints in stable index-id order; empty when the root does not exist.
   * @throws IOException If a checkpoint is unreadable, malformed, or exceeds a bound.
   */
  List<RestoredCheckpoint> restoreAll() throws IOException {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    recoverInterruptedSwaps();
    final List<Path> directories = new ArrayList<>();
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
      for (Path entry : entries) {
        if (Files.isDirectory(entry)
            && !entry.getFileName().toString().startsWith(".")
            && !SearchCollectionRegistry.COLLECTIONS_DIR
                .equals(entry.getFileName().toString())) {
          directories.add(entry);
        }
      }
    }
    directories.sort(java.util.Comparator.comparing(path -> path.getFileName().toString()));
    final List<RestoredCheckpoint> checkpoints = new ArrayList<>(directories.size());
    for (Path directory : directories) {
      checkpoints.add(read(directory));
    }
    return List.copyOf(checkpoints);
  }

  /**
   * Deletes one checkpoint directory.
   *
   * @param indexId Persisted index id.
   * @return {@code true} when a checkpoint existed and was removed.
   * @throws IOException If deletion fails.
   */
  boolean delete(String indexId) throws IOException {
    final Path target = root.resolve(indexId);
    if (!Files.isDirectory(target)) {
      return false;
    }
    deleteRecursively(target);
    return true;
  }

  /**
   * Reads and validates one checkpoint directory.
   *
   * @param directory Checkpoint directory named by its index id.
   * @return The parsed checkpoint.
   * @throws IOException If the descriptor or chunk stream is invalid.
   */
  private static RestoredCheckpoint read(Path directory) throws IOException {
    final Path descriptorFile = directory.resolve(DESCRIPTOR_FILE);
    if (!Files.isRegularFile(descriptorFile)) {
      throw new IOException("checkpoint " + directory + " lacks " + DESCRIPTOR_FILE);
    }
    if (Files.size(descriptorFile) > MAX_DESCRIPTOR_BYTES) {
      throw new IOException(descriptorFile + " exceeds " + MAX_DESCRIPTOR_BYTES + " bytes");
    }
    final Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(descriptorFile)) {
      properties.load(input);
    }
    final int version = positiveInt(properties, "format.version");
    if (version != FORMAT_VERSION) {
      throw new IOException("Unsupported checkpoint format.version " + version
          + " in " + directory + "; expected " + FORMAT_VERSION);
    }
    final String indexId = require(properties, "index.id");
    if (!directory.getFileName().toString().equals(indexId)) {
      throw new IOException("checkpoint directory " + directory
          + " does not match its declared index.id '" + indexId + "'");
    }
    final String kind = require(properties, "bundle.kind");
    if (!KIND_CHECKPOINT.equals(kind) && !KIND_SEALED.equals(kind)) {
      throw new IOException("checkpoint " + directory + " declares unknown bundle.kind '"
          + kind + "'");
    }
    final int dimension = positiveInt(properties, "dimension");
    final int size = positiveInt(properties, "size");
    final int vectorSegmentCount = positiveInt(properties, "vector.segment.count");
    if (size > DynamicSearchIndexRegistry.MAX_CHUNKS_PER_INDEX) {
      throw new IOException("checkpoint size exceeds "
          + DynamicSearchIndexRegistry.MAX_CHUNKS_PER_INDEX);
    }
    if (vectorSegmentCount > MAX_VECTOR_SEGMENTS) {
      throw new IOException("checkpoint vector.segment.count exceeds "
          + MAX_VECTOR_SEGMENTS);
    }
    final String contentHash = require(properties, "content.sha256");
    try {
      SearchIndexRegistry.requireSha256(contentHash, "content.sha256");
    } catch (IllegalArgumentException e) {
      throw new IOException(e.getMessage(), e);
    }
    final EmbeddingRoute.Builder route = EmbeddingRoute.newBuilder()
        .setModelId(require(properties, "embedding.model.id"))
        .setBackendId(require(properties, "embedding.backend.id"))
        .setVectorSpaceId(require(properties, "embedding.vector_space.id"));
    final String artifactHash = properties.getProperty("embedding.artifact.sha256");
    if (artifactHash != null && !artifactHash.isBlank()) {
      route.setArtifactHash(artifactHash.trim());
    }

    final Path chunksFile = directory.resolve(CHUNKS_FILE);
    if (!Files.isRegularFile(chunksFile)) {
      throw new IOException("checkpoint " + directory + " lacks " + CHUNKS_FILE);
    }
    if (Files.size(chunksFile) > MAX_CHUNKS_FILE_BYTES) {
      throw new IOException(chunksFile + " exceeds " + MAX_CHUNKS_FILE_BYTES + " bytes");
    }
    final List<PersistedSearchChunk> chunks = new ArrayList<>(size);
    try (InputStream input = new BufferedInputStream(Files.newInputStream(chunksFile))) {
      PersistedSearchChunk chunk;
      while ((chunk = PersistedSearchChunk.parseDelimitedFrom(input)) != null) {
        if (chunks.size() >= size) {
          throw new IOException(chunksFile + " contains more than the declared "
              + size + " chunks");
        }
        chunks.add(chunk);
      }
    }
    if (chunks.size() != size) {
      throw new IOException(chunksFile + " contains " + chunks.size()
          + " chunks; descriptor declares " + size);
    }
    final List<Path> vectorSegments = new ArrayList<>(vectorSegmentCount);
    final Path segmentsDirectory = directory.resolve(VECTOR_SEGMENTS_DIR);
    for (int index = 0; index < vectorSegmentCount; index++) {
      final Path segment = segmentsDirectory.resolve(Integer.toString(index));
      if (!Files.isDirectory(segment)) {
        throw new IOException("checkpoint " + directory + " lacks vector segment " + index);
      }
      vectorSegments.add(segment);
    }
    return new RestoredCheckpoint(
        new CheckpointHeader(indexId, require(properties, "display.name"),
            require(properties, "provider.instance"), route.build(), dimension,
            KIND_SEALED.equals(kind), contentHash),
        List.copyOf(chunks), List.copyOf(vectorSegments));
  }

  /**
   * Replaces the target directory with the staged one, keeping the last complete write.
   *
   * @param staging Complete staged checkpoint.
   * @param target Final checkpoint directory.
   * @throws IOException If a move fails; the previous checkpoint is restored when possible.
   */
  private static void swap(Path staging, Path target) throws IOException {
    Path previous = null;
    if (Files.exists(target)) {
      previous = target.resolveSibling("." + target.getFileName() + "-old-" + UUID.randomUUID());
      Files.move(target, previous, StandardCopyOption.ATOMIC_MOVE);
    }
    try {
      Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      if (previous != null) {
        try {
          Files.move(previous, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException restoreFailure) {
          e.addSuppressed(restoreFailure);
        }
      }
      throw e;
    }
    if (previous != null) {
      try {
        deleteRecursively(previous);
      } catch (IOException ignored) {
        // The new checkpoint is already published. Restore removes this stale generation.
      }
    }
  }

  /**
   * Recovers an old generation left after the previous target was moved aside but before
   * its replacement was published. Old generations beside an already published target
   * are stale cleanup work.
   *
   * @throws IOException If recovery of the authoritative generation fails.
   */
  private void recoverInterruptedSwaps() throws IOException {
    final List<Path> oldGenerations = new ArrayList<>();
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
      for (Path entry : entries) {
        if (Files.isDirectory(entry) && interruptedSwapIndexId(entry) != null) {
          oldGenerations.add(entry);
        }
      }
    }
    oldGenerations.sort(java.util.Comparator.comparing(path -> path.getFileName().toString()));
    for (Path oldGeneration : oldGenerations) {
      final String indexId = interruptedSwapIndexId(oldGeneration);
      final Path target = root.resolve(indexId);
      if (Files.exists(target)) {
        try {
          deleteRecursively(oldGeneration);
        } catch (IOException ignored) {
          // The published target is authoritative; stale cleanup can be retried later.
        }
      } else {
        Files.move(oldGeneration, target, StandardCopyOption.ATOMIC_MOVE);
      }
    }
  }

  /** Returns the index id encoded by one hidden old-generation directory. */
  private static String interruptedSwapIndexId(Path directory) {
    final String name = directory.getFileName().toString();
    final int marker = name.lastIndexOf(OLD_MARKER);
    if (!name.startsWith(".") || marker <= 1) {
      return null;
    }
    try {
      UUID.fromString(name.substring(marker + OLD_MARKER.length()));
      final String indexId = name.substring(1, marker);
      SearchIndexRegistry.requireStableId(indexId, "interrupted checkpoint index id");
      return indexId;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Deletes a directory tree.
   *
   * @param directory Directory to remove.
   * @throws IOException If a deletion fails.
   */
  static void deleteRecursively(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }
    Files.walkFileTree(directory, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
          throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path visited, IOException failure)
          throws IOException {
        if (failure != null) {
          throw failure;
        }
        Files.delete(visited);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static int positiveInt(Properties properties, String key) throws IOException {
    final String value = require(properties, key);
    try {
      final int parsed = Integer.parseInt(value);
      if (parsed < 1) {
        throw new NumberFormatException();
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new IOException(key + " must be a positive integer, was '" + value + "'");
    }
  }

  private static String require(Properties properties, String key) throws IOException {
    final String value = properties.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IOException("checkpoint property " + key + " must not be blank");
    }
    if (!value.equals(value.trim())) {
      throw new IOException("checkpoint property " + key + " must be trimmed");
    }
    return value;
  }
}
