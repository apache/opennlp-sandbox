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
package org.apache.opennlp.grpc.search.turboquant;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import opennlp.embeddings.index.TurboQuantIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TurboQuantBundleDigestTest {

  @TempDir
  Path tempDir;

  @Test
  void hashesLengthFramedComponentDigestsInCanonicalOrder() throws Exception {
    final Path indexDirectory = Files.createDirectory(tempDir.resolve("index"));
    final Path vectors = Files.writeString(
        indexDirectory.resolve(TurboQuantIndex.VECTORS_FILE), "vectors");
    final Path ids = Files.writeString(
        indexDirectory.resolve(TurboQuantIndex.IDS_FILE), "one\ntwo\n");
    final Path passages = Files.writeString(tempDir.resolve("passages.jsonl"), "passages\n");

    final MessageDigest expected = sha256Digest();
    addFramed(expected, digest(vectors));
    addFramed(expected, digest(ids));
    addFramed(expected, digest(passages));

    assertEquals(hex(expected.digest()),
        TurboQuantBundleDigest.bundleArtifactHash(indexDirectory, passages));
    assertEquals(hex(digest(passages)), TurboQuantBundleDigest.sha256(passages));
  }

  @Test
  void excludesDescriptorButIncludesPassages() throws IOException {
    final Path indexDirectory = Files.createDirectory(tempDir.resolve("index"));
    Files.writeString(indexDirectory.resolve(TurboQuantIndex.VECTORS_FILE), "vectors");
    Files.writeString(indexDirectory.resolve(TurboQuantIndex.IDS_FILE), "one\n");
    final Path passages = Files.writeString(tempDir.resolve("passages.jsonl"), "first\n");
    final String initial = TurboQuantBundleDigest.bundleArtifactHash(indexDirectory, passages);

    Files.writeString(indexDirectory.resolve("search-index.properties"), "display.name=changed\n");
    assertEquals(initial,
        TurboQuantBundleDigest.bundleArtifactHash(indexDirectory, passages));

    Files.writeString(passages, "second\n");
    assertNotEquals(initial,
        TurboQuantBundleDigest.bundleArtifactHash(indexDirectory, passages));
  }

  private byte[] digest(Path path) throws IOException {
    return sha256Digest().digest(Files.readAllBytes(path));
  }

  private void addFramed(MessageDigest digest, byte[] component) {
    digest.update(ByteBuffer.allocate(Long.BYTES).putLong(component.length).array());
    digest.update(component);
  }

  private MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by the Java platform", e);
    }
  }

  private String hex(byte[] bytes) {
    final StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      final int unsigned = value & 0xff;
      if (unsigned < 16) {
        result.append('0');
      }
      result.append(Integer.toHexString(unsigned));
    }
    return result.toString();
  }
}
