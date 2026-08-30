/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.vocabulary.store;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.opennlp.grpc.spi.vocabulary.VocabularyStore;

/**
 * The {@link VocabularyStore} over one local directory: staged artifacts live in a
 * hidden directory beneath the root and publish through one atomic directory move, so
 * readers never observe a partial artifact and a crashed write leaves the published
 * tree untouched.
 */
public final class FileSystemVocabularyStore implements VocabularyStore {

  private static final String STAGING_PREFIX = ".staging-";

  private final Path root;

  /**
   * Opens the store rooted at the given directory, creating it when absent.
   *
   * @param root The artifact root directory. Must not be {@code null}.
   * @throws IOException Thrown if the root cannot be created or is not a real directory.
   * @throws IllegalArgumentException Thrown if {@code root} is {@code null}.
   */
  public FileSystemVocabularyStore(Path root) throws IOException {
    if (root == null) {
      throw new IllegalArgumentException("root must not be null");
    }
    this.root = root.toAbsolutePath().normalize();
    createStorageDirectory(this.root);
  }

  /** {@inheritDoc} */
  @Override
  public List<String> list(String kind) throws IOException {
    final Path directory = root.resolve(plainName(kind, "kind"));
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      return List.of();
    }
    final List<String> ids = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
      for (Path entry : stream) {
        if (!Files.isDirectory(entry) || Files.isSymbolicLink(entry)) {
          throw new IOException("Unexpected artifact entry: " + entry);
        }
        ids.add(entry.getFileName().toString());
      }
    }
    Collections.sort(ids);
    return ids;
  }

  /** {@inheritDoc} */
  @Override
  public InputStream read(String kind, String artifactId, String entryName)
      throws IOException {
    final Path file = root.resolve(plainName(kind, "kind"))
        .resolve(plainName(artifactId, "artifactId"))
        .resolve(plainName(entryName, "entryName"));
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("No readable entry '" + entryName + "' in artifact '"
          + artifactId + "'");
    }
    return Files.newInputStream(file);
  }

  /** {@inheritDoc} */
  @Override
  public ArtifactWriter write(String kind, String artifactId) throws IOException {
    final Path kindDirectory = root.resolve(plainName(kind, "kind"));
    createStorageDirectory(kindDirectory);
    final String id = plainName(artifactId, "artifactId");
    final Path staging = Files.createDirectory(root.resolve(STAGING_PREFIX + id));
    return new FileSystemArtifactWriter(staging, kindDirectory.resolve(id), id);
  }

  /** {@inheritDoc} */
  @Override
  public boolean delete(String kind, String artifactId) throws IOException {
    final Path directory = root.resolve(plainName(kind, "kind"))
        .resolve(plainName(artifactId, "artifactId"));
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    deleteTree(directory);
    return true;
  }

  /** Creates and validates one real storage directory. */
  private static void createStorageDirectory(Path directory) throws IOException {
    Files.createDirectories(directory);
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Artifact path must be a real directory: " + directory);
    }
  }

  /**
   * Requires a name usable as one path element: nonblank, no separator, no traversal.
   *
   * @param name The name to validate.
   * @param what The parameter name for the failure message.
   * @return The validated name.
   */
  private static String plainName(String name, String what) {
    if (name == null || name.isBlank() || name.equals(".") || name.equals("..")) {
      throw new IllegalArgumentException(what + " must be a plain name, was '" + name + "'");
    }
    for (int i = 0; i < name.length(); i++) {
      final char character = name.charAt(i);
      if (character == '/' || character == '\\' || character == 0) {
        throw new IllegalArgumentException(what + " must be a plain name, was '" + name + "'");
      }
    }
    return name;
  }

  /** Deletes one staged tree, deepest entries first. */
  private static void deleteTree(Path tree) throws IOException {
    if (!Files.exists(tree, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    Files.walkFileTree(tree, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
          throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path directory, IOException error)
          throws IOException {
        if (error != null) {
          throw error;
        }
        Files.delete(directory);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /** One staged artifact directory published by an atomic move. */
  private static final class FileSystemArtifactWriter implements ArtifactWriter {

    private final Path staging;
    private final Path destination;
    private final String artifactId;
    private boolean committed;

    private FileSystemArtifactWriter(Path staging, Path destination, String artifactId) {
      this.staging = staging;
      this.destination = destination;
      this.artifactId = artifactId;
    }

    @Override
    public OutputStream entry(String entryName) throws IOException {
      return Files.newOutputStream(staging.resolve(plainName(entryName, "entryName")),
          StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Override
    public void commit() throws IOException {
      if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("Artifact '" + artifactId + "' is already published");
      }
      try {
        Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        throw new IOException("Artifact root does not support atomic directory publication", e);
      }
      committed = true;
    }

    @Override
    public void close() throws IOException {
      if (!committed) {
        deleteTree(staging);
      }
    }
  }
}
