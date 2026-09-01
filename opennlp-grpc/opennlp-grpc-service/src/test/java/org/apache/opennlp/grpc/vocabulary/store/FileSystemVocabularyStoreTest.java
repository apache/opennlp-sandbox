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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.opennlp.grpc.spi.vocabulary.VocabularyStore;

class FileSystemVocabularyStoreTest {

  private static final String KIND = "vocabularies";
  private static final String ARTIFACT = "vocabulary-test";
  private static final byte[] DATA = "law\t3\tcorpus\n".getBytes(StandardCharsets.UTF_8);

  @TempDir
  Path root;

  @Test
  void stagedEntriesAreInvisibleUntilCommit() throws IOException {
    final VocabularyStore store = new FileSystemVocabularyStore(root);
    try (VocabularyStore.ArtifactWriter writer = store.write(KIND, ARTIFACT)) {
      try (OutputStream entry = writer.entry("vocabulary.tsv")) {
        entry.write(DATA);
      }
      assertEquals(0, store.list(KIND).size());
      assertThrows(IOException.class, () -> store.read(KIND, ARTIFACT, "vocabulary.tsv"));
      writer.commit();
    }
    assertEquals(java.util.List.of(ARTIFACT), store.list(KIND));
    try (InputStream entry = store.read(KIND, ARTIFACT, "vocabulary.tsv")) {
      assertArrayEquals(DATA, entry.readAllBytes());
    }
  }

  @Test
  void closingWithoutCommitDiscardsTheStagedArtifact() throws IOException {
    final VocabularyStore store = new FileSystemVocabularyStore(root);
    try (VocabularyStore.ArtifactWriter writer = store.write(KIND, ARTIFACT)) {
      try (OutputStream entry = writer.entry("vocabulary.tsv")) {
        entry.write(DATA);
      }
    }
    assertEquals(0, store.list(KIND).size());
    try (var files = Files.list(root)) {
      assertTrue(files.noneMatch(path ->
          path.getFileName().toString().startsWith(".staging")), "staging left behind");
    }
  }

  @Test
  void commitRefusesAnAlreadyPublishedArtifactId() throws IOException {
    final VocabularyStore store = new FileSystemVocabularyStore(root);
    publish(store, DATA);
    try (VocabularyStore.ArtifactWriter writer = store.write(KIND, ARTIFACT)) {
      try (OutputStream entry = writer.entry("vocabulary.tsv")) {
        entry.write("other\t1\tcorpus\n".getBytes(StandardCharsets.UTF_8));
      }
      assertThrows(IOException.class, writer::commit);
    }
    try (InputStream entry = store.read(KIND, ARTIFACT, "vocabulary.tsv")) {
      assertArrayEquals(DATA, entry.readAllBytes());
    }
  }

  @Test
  void entryNamesAreConfinedToTheArtifact() throws IOException {
    final VocabularyStore store = new FileSystemVocabularyStore(root);
    try (VocabularyStore.ArtifactWriter writer = store.write(KIND, ARTIFACT)) {
      assertThrows(IllegalArgumentException.class, () -> writer.entry("../escape.tsv"));
      assertThrows(IllegalArgumentException.class, () -> writer.entry("nested/entry.tsv"));
      assertThrows(IllegalArgumentException.class, () -> writer.entry(""));
    }
    assertThrows(IllegalArgumentException.class,
        () -> store.read(KIND, ARTIFACT, "../escape.tsv"));
    assertThrows(IllegalArgumentException.class, () -> store.read("../kind", ARTIFACT, "a"));
    assertThrows(IllegalArgumentException.class, () -> store.read(KIND, "../artifact", "a"));
  }

  @Test
  void readRefusesASymbolicLinkEntry() throws IOException {
    final VocabularyStore store = new FileSystemVocabularyStore(root);
    publish(store, DATA);
    final Path artifact = root.resolve(KIND).resolve(ARTIFACT);
    final Path outside = Files.writeString(root.resolve("outside.tsv"), "planted");
    Files.delete(artifact.resolve("vocabulary.tsv"));
    Files.createSymbolicLink(artifact.resolve("vocabulary.tsv"), outside);
    assertThrows(IOException.class, () -> store.read(KIND, ARTIFACT, "vocabulary.tsv"));
  }

  private void publish(VocabularyStore store, byte[] data) throws IOException {
    try (VocabularyStore.ArtifactWriter writer = store.write(KIND, ARTIFACT)) {
      try (OutputStream entry = writer.entry("vocabulary.tsv")) {
        entry.write(data);
      }
      writer.commit();
    }
  }
}
