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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.opennlp.grpc.v1.IndexAlias;
import org.apache.opennlp.grpc.v1.PersistedIndexAliases;

/**
 * Bounded registry of logical index aliases.
 *
 * <p>An alias resolves to a current index id and is accepted wherever an index id is.
 * When constructed over a file, the complete alias set is rewritten atomically on every
 * mutation and reloaded at construction, with the last write winning.</p>
 */
public final class IndexAliasRegistry {

  /** Alias file name within the configured persistence root. */
  public static final String ALIASES_FILE = "aliases.pb";

  /** Fixed safety ceiling for the alias count. */
  static final int MAX_ALIASES = 256;

  private static final long MAX_FILE_BYTES = 1024 * 1024;

  private final SortedMap<String, String> aliases = new TreeMap<>();
  private final Path file;

  private IndexAliasRegistry(Path file) {
    this.file = file;
  }

  /**
   * Creates a registry that keeps aliases only for the server process lifetime.
   *
   * @return Empty in-memory registry.
   */
  public static IndexAliasRegistry inMemory() {
    return new IndexAliasRegistry(null);
  }

  /**
   * Creates a registry persisted to one local file.
   *
   * @param file Alias file, loaded when it exists.
   * @return Registry holding the file's aliases.
   * @throws IllegalArgumentException If {@code file} is {@code null}.
   * @throws IllegalStateException If the file is unreadable or malformed.
   */
  public static IndexAliasRegistry at(Path file) {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    final IndexAliasRegistry registry = new IndexAliasRegistry(file);
    registry.load();
    return registry;
  }

  /**
   * Creates a registry from configuration: persisted under the checkpoint root when
   * {@value WorkspaceCheckpointStore#ROOT_KEY} is set, in memory otherwise.
   *
   * @param configuration Server configuration.
   * @return The configured registry.
   * @throws IllegalArgumentException If the configuration is {@code null}.
   */
  public static IndexAliasRegistry fromConfiguration(Map<String, String> configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    final String root = configuration.get(WorkspaceCheckpointStore.ROOT_KEY);
    if (root == null || root.isBlank()) {
      return inMemory();
    }
    return at(Path.of(root.trim()).resolve(ALIASES_FILE));
  }

  /**
   * Creates or repoints one alias.
   *
   * @param alias Logical alias name.
   * @param indexId Index id the alias resolves to.
   * @return The stored alias.
   * @throws IllegalArgumentException If a value is not a stable identifier or the alias
   *     count would exceed {@link #MAX_ALIASES}.
   */
  public synchronized IndexAlias set(String alias, String indexId) {
    SearchIndexRegistry.requireStableId(alias, "alias");
    SearchIndexRegistry.requireStableId(indexId, "alias index id");
    if (!aliases.containsKey(alias) && aliases.size() >= MAX_ALIASES) {
      throw new IllegalArgumentException("alias count reached " + MAX_ALIASES);
    }
    aliases.put(alias, indexId);
    write();
    return IndexAlias.newBuilder().setAlias(alias).setIndexId(indexId).build();
  }

  /**
   * Deletes one alias.
   *
   * @param alias Logical alias name.
   * @return {@code true} when the alias existed and was removed.
   */
  public synchronized boolean delete(String alias) {
    if (aliases.remove(alias) == null) {
      return false;
    }
    write();
    return true;
  }

  /**
   * Deletes every alias that resolves to one index, for use when the index itself is deleted.
   *
   * @param indexId Index id the aliases point at.
   * @return The removed alias names in stable order; empty when none pointed at the index.
   * @throws IllegalArgumentException If {@code indexId} is not a stable identifier.
   */
  public synchronized List<String> deleteByIndex(String indexId) {
    SearchIndexRegistry.requireStableId(indexId, "index id");
    final List<String> removed = new ArrayList<>();
    for (Map.Entry<String, String> entry : aliases.entrySet()) {
      if (entry.getValue().equals(indexId)) {
        removed.add(entry.getKey());
      }
    }
    if (removed.isEmpty()) {
      return List.of();
    }
    removed.forEach(aliases::remove);
    write();
    return List.copyOf(removed);
  }

  /**
   * Returns every alias in stable alias order.
   *
   * @return Immutable alias list.
   */
  public synchronized List<IndexAlias> aliases() {
    final List<IndexAlias> result = new ArrayList<>(aliases.size());
    for (Map.Entry<String, String> entry : aliases.entrySet()) {
      result.add(IndexAlias.newBuilder()
          .setAlias(entry.getKey())
          .setIndexId(entry.getValue())
          .build());
    }
    return List.copyOf(result);
  }

  /**
   * Resolves one alias or index id.
   *
   * @param idOrAlias Index id or alias.
   * @return The aliased index id, or {@code idOrAlias} unchanged when it is not an alias.
   */
  public synchronized String resolve(String idOrAlias) {
    final String resolved = idOrAlias == null ? null : aliases.get(idOrAlias);
    return resolved == null ? idOrAlias : resolved;
  }

  /**
   * Tests whether a name is currently an alias.
   *
   * @param alias Candidate name.
   * @return {@code true} when the name is an alias.
   */
  public synchronized boolean isAlias(String alias) {
    return alias != null && aliases.containsKey(alias);
  }

  /**
   * Loads the alias file when it exists.
   *
   * @throws IllegalStateException If the file is unreadable, malformed, or oversized.
   */
  private void load() {
    if (!Files.isRegularFile(file)) {
      return;
    }
    try {
      if (Files.size(file) > MAX_FILE_BYTES) {
        throw new IllegalStateException(file + " exceeds " + MAX_FILE_BYTES + " bytes");
      }
      final PersistedIndexAliases persisted;
      try (InputStream input = Files.newInputStream(file)) {
        persisted = PersistedIndexAliases.parseFrom(input);
      }
      if (persisted.getAliasesCount() > MAX_ALIASES) {
        throw new IllegalStateException(file + " declares more than "
            + MAX_ALIASES + " aliases");
      }
      for (IndexAlias alias : persisted.getAliasesList()) {
        SearchIndexRegistry.requireStableId(alias.getAlias(), "persisted alias");
        SearchIndexRegistry.requireStableId(alias.getIndexId(), "persisted alias index id");
        if (aliases.putIfAbsent(alias.getAlias(), alias.getIndexId()) != null) {
          throw new IllegalStateException(file + " declares alias '" + alias.getAlias()
              + "' more than once");
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load index aliases from " + file, e);
    }
  }

  /**
   * Rewrites the alias file atomically when this registry is persisted.
   *
   * @throws UncheckedIOException If the write fails.
   */
  private void write() {
    if (file == null) {
      return;
    }
    final PersistedIndexAliases.Builder persisted = PersistedIndexAliases.newBuilder();
    for (Map.Entry<String, String> entry : aliases.entrySet()) {
      persisted.addAliases(IndexAlias.newBuilder()
          .setAlias(entry.getKey())
          .setIndexId(entry.getValue()));
    }
    try {
      final Path parent = file.toAbsolutePath().getParent();
      Files.createDirectories(parent);
      final Path staging = Files.createTempFile(parent, ".opennlp-aliases-", ".tmp");
      try (OutputStream output = Files.newOutputStream(staging)) {
        persisted.build().writeTo(output);
      }
      Files.move(staging, file, StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to persist index aliases to " + file, e);
    }
  }
}
