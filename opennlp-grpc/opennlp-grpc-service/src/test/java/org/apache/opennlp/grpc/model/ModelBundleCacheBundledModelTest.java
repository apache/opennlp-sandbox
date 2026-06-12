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
 * KIND, either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the bundled-model scan that backs the executable-jar deployment, where the
 * {@code opennlp-models-*} artifacts are merged into the server jar and classpath
 * discovery (which matches model jar file names) cannot find them.
 */
class ModelBundleCacheBundledModelTest {

  private static final byte[] SENTENCE_BYTES = "sentence-model".getBytes(StandardCharsets.UTF_8);
  private static final byte[] TOKENS_BYTES = "tokens-model".getBytes(StandardCharsets.UTF_8);

  @TempDir
  private Path tempDir;

  private Path writeJar(Map<String, byte[]> entries) throws IOException {
    final Path jar = tempDir.resolve("server.jar");
    try (OutputStream out = Files.newOutputStream(jar);
         JarOutputStream jarOut = new JarOutputStream(out)) {
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        jarOut.putNextEntry(new JarEntry(entry.getKey()));
        jarOut.write(entry.getValue());
        jarOut.closeEntry();
      }
    }
    return jar;
  }

  @Test
  void findsModelsByNameFragmentAtJarRoot() throws IOException {
    final Path jar = writeJar(Map.of(
        "opennlp-en-ud-ewt-sentence-1.3-2.5.4.bin", SENTENCE_BYTES,
        "opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin", TOKENS_BYTES,
        "org/example/SomeClass.class", new byte[] {1, 2, 3}));

    try (InputStream sentence = ModelBundleCache.findBundledModel(jar, "-sentence-");
         InputStream tokens = ModelBundleCache.findBundledModel(jar, "-tokens-")) {
      assertNotNull(sentence);
      assertNotNull(tokens);
      assertArrayEquals(SENTENCE_BYTES, sentence.readAllBytes());
      assertArrayEquals(TOKENS_BYTES, tokens.readAllBytes());
    }
  }

  @Test
  void ignoresNestedEntries() throws IOException {
    final Path jar = writeJar(Map.of(
        "models/opennlp-en-ud-ewt-sentence-1.3-2.5.4.bin", SENTENCE_BYTES));

    assertNull(ModelBundleCache.findBundledModel(jar, "-sentence-"));
  }

  @Test
  void ignoresEntriesWithoutModelSuffix() throws IOException {
    final Path jar = writeJar(Map.of(
        "opennlp-en-ud-ewt-sentence-readme.txt", SENTENCE_BYTES));

    assertNull(ModelBundleCache.findBundledModel(jar, "-sentence-"));
  }

  @Test
  void returnsNullWhenNoEntryMatches() throws IOException {
    final Path jar = writeJar(Map.of(
        "opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin", TOKENS_BYTES));

    assertNull(ModelBundleCache.findBundledModel(jar, "-sentence-"));
  }
}
