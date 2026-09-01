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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import opennlp.embeddings.index.TurboQuantIndex;

/** Computes stable SHA-256 identities for TurboQuant search-bundle artifacts. */
public final class TurboQuantBundleDigest {

  private static final int BUFFER_BYTES = 16_384;
  private static final String DIGEST_ALGORITHM = "SHA-256";
  private static final String HEX_DIGITS = "0123456789abcdef";

  /** Prevents instantiation of this digest utility. */
  private TurboQuantBundleDigest() {
  }

  /**
   * Hashes one regular file with SHA-256.
   *
   * @param file File to hash.
   * @return Lowercase hexadecimal SHA-256 digest.
   * @throws IllegalArgumentException If {@code file} is {@code null}.
   * @throws IOException If the file cannot be read.
   */
  public static String sha256(Path file) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    return hex(digest(file));
  }

  /**
   * Computes the canonical bundle artifact identity.
   *
   * <p>The input to SHA-256 is three entries in this fixed order:
   * {@code vectors.onq}, {@code ids.txt}, and the deployed passages file. Each entry is the
   * unsigned 64-bit big-endian value {@code 32}, followed by the raw 32-byte SHA-256 digest of
   * that file. The descriptor is deliberately excluded, so deployment metadata may change
   * without misidentifying the vector, id, and passage payload.</p>
   *
   * @param indexDirectory Directory containing {@code vectors.onq} and {@code ids.txt}.
   * @param passagesFile Deployed CasePassage JSON Lines file.
   * @return Lowercase hexadecimal SHA-256 digest.
   * @throws IllegalArgumentException If either path is {@code null}.
   * @throws IOException If an artifact cannot be read.
   */
  public static String bundleArtifactHash(Path indexDirectory, Path passagesFile)
      throws IOException {
    if (indexDirectory == null) {
      throw new IllegalArgumentException("indexDirectory must not be null");
    }
    if (passagesFile == null) {
      throw new IllegalArgumentException("passagesFile must not be null");
    }
    final MessageDigest bundle = newDigest();
    addFramed(bundle, digest(indexDirectory.resolve(TurboQuantIndex.VECTORS_FILE)));
    addFramed(bundle, digest(indexDirectory.resolve(TurboQuantIndex.IDS_FILE)));
    addFramed(bundle, digest(passagesFile));
    return hex(bundle.digest());
  }

  /**
   * Reads a file and returns its raw SHA-256 digest.
   *
   * @param file File to hash.
   * @return Raw SHA-256 digest.
   * @throws IOException If the file cannot be read.
   */
  private static byte[] digest(Path file) throws IOException {
    final MessageDigest digest = newDigest();
    final byte[] buffer = new byte[BUFFER_BYTES];
    try (InputStream input = Files.newInputStream(file)) {
      int count;
      while ((count = input.read(buffer)) != -1) {
        digest.update(buffer, 0, count);
      }
    }
    return digest.digest();
  }

  /**
   * Adds one length-prefixed component digest to a bundle digest.
   *
   * @param digest Bundle digest to update.
   * @param componentDigest Component digest to frame.
   */
  private static void addFramed(MessageDigest digest, byte[] componentDigest) {
    final long length = componentDigest.length;
    for (int shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
      digest.update((byte) (length >>> shift));
    }
    digest.update(componentDigest);
  }

  /**
   * Creates the SHA-256 implementation required by the Java platform.
   *
   * @return New SHA-256 digest.
   */
  private static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance(DIGEST_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(DIGEST_ALGORITHM + " is required by the Java platform", e);
    }
  }

  /**
   * Encodes a digest as lowercase hexadecimal text.
   *
   * @param bytes Raw digest bytes.
   * @return Lowercase hexadecimal text.
   */
  private static String hex(byte[] bytes) {
    final StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      final int unsigned = value & 0xff;
      result.append(HEX_DIGITS.charAt(unsigned >>> 4));
      result.append(HEX_DIGITS.charAt(unsigned & 0xf));
    }
    return result.toString();
  }

  /**
   * Hashes an in-memory canonical representation with SHA-256.
   *
   * @param bytes Canonical bytes to hash.
   * @return Lowercase hexadecimal SHA-256 digest.
   * @throws IllegalArgumentException If {@code bytes} is {@code null}.
   */
  static String sha256(byte[] bytes) {
    if (bytes == null) {
      throw new IllegalArgumentException("bytes must not be null");
    }
    return hex(newDigest().digest(bytes));
  }

  /**
   * Appends one UTF-16 code unit as four lowercase hexadecimal digits.
   *
   * @param target Destination builder.
   * @param value UTF-16 code unit.
   */
  static void appendHex16(StringBuilder target, char value) {
    target.append(HEX_DIGITS.charAt((value >>> 12) & 0xf));
    target.append(HEX_DIGITS.charAt((value >>> 8) & 0xf));
    target.append(HEX_DIGITS.charAt((value >>> 4) & 0xf));
    target.append(HEX_DIGITS.charAt(value & 0xf));
  }
}
