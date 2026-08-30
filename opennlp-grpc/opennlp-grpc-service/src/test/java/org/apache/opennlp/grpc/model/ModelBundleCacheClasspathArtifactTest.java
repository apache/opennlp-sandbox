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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.opennlp.grpc.model.ModelBundleCache.ClasspathArtifact;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.opennlp.grpc.spi.ModelArtifactHasher;

/**
 * Tests {@link ModelBundleCache}'s classpath artifact resolution against an isolated
 * descriptor classpath: a {@code model.properties} that declares a {@code model.sha256}
 * must match the bytes actually served, or startup must fail instead of pinning a hash the
 * artifact does not have.
 */
class ModelBundleCacheClasspathArtifactTest {

  private static final byte[] MODEL_BYTES = "not a real model".getBytes(StandardCharsets.UTF_8);
  private static final String WRONG_HASH = "0".repeat(64);

  @TempDir
  Path directory;

  @Test
  void mismatchedDeclaredHashFailsIntegrityCheck() throws IOException {
    final ClassLoader classLoader = artifactClassLoader(
        "fake-sentence-en.bin", "en", WRONG_HASH);

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> ModelBundleCache.findClasspathArtifact(classLoader, "en", "-sentence-"));
    assertTrue(error.getMessage().contains("fake-sentence-en.bin"),
        "the integrity error must name the artifact: " + error.getMessage());
  }

  @Test
  void matchingDeclaredHashIsUsed() throws IOException {
    final String declared = ModelArtifactHasher.sha256Hex(MODEL_BYTES);
    final ClassLoader classLoader = artifactClassLoader("fake-sentence-en.bin", "en", declared);

    final ClasspathArtifact artifact =
        ModelBundleCache.findClasspathArtifact(classLoader, "en", "-sentence-");
    assertEquals(declared, artifact.hash());
  }

  @Test
  void absentDeclaredHashIsComputed() throws IOException {
    final ClassLoader classLoader = artifactClassLoader("fake-sentence-en.bin", "en", null);

    final ClasspathArtifact artifact =
        ModelBundleCache.findClasspathArtifact(classLoader, "en", "-sentence-");
    assertEquals(ModelArtifactHasher.sha256Hex(MODEL_BYTES), artifact.hash());
  }

  @Test
  void languageDetectorMismatchedDeclaredHashFailsIntegrityCheck() throws IOException {
    final ClassLoader classLoader = artifactClassLoader("langdetect.bin", "root", WRONG_HASH);

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> ModelBundleCache.findClasspathLanguageDetectorArtifact(classLoader));
    assertTrue(error.getMessage().contains("langdetect.bin"),
        "the integrity error must name the artifact: " + error.getMessage());
  }

  @Test
  void languageDetectorMatchingDeclaredHashIsUsed() throws IOException {
    final String declared = ModelArtifactHasher.sha256Hex(MODEL_BYTES);
    final ClassLoader classLoader = artifactClassLoader("langdetect.bin", "root", declared);

    final ClasspathArtifact artifact =
        ModelBundleCache.findClasspathLanguageDetectorArtifact(classLoader);
    assertEquals(declared, artifact.hash());
  }

  /**
   * Writes a {@code model.properties} descriptor and the model binary it names into the temp
   * directory and returns a parentless classloader over it, so the test sees exactly one
   * descriptor.
   */
  private ClassLoader artifactClassLoader(String modelName, String language, String declaredHash)
      throws IOException {
    final StringBuilder descriptor = new StringBuilder()
        .append("model.language=").append(language).append('\n')
        .append("model.name=").append(modelName).append('\n');
    if (declaredHash != null) {
      descriptor.append("model.sha256=").append(declaredHash).append('\n');
    }
    Files.writeString(directory.resolve("model.properties"), descriptor.toString());
    Files.write(directory.resolve(modelName), MODEL_BYTES);
    return new URLClassLoader(new URL[] {directory.toUri().toURL()}, null);
  }
}
