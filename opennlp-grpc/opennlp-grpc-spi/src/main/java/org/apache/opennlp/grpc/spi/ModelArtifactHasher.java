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
package org.apache.opennlp.grpc.spi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes SHA-256 digests of model artifact bytes for catalog reporting and
 * {@code ModelBundleRef.component_models} pinning.
 */
public final class ModelArtifactHasher {

  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  /** Prevents instantiation. */
  private ModelArtifactHasher() {
  }

  /**
   * Validates a lowercase hexadecimal SHA-256 value.
   *
   * @param value The value to validate.
   * @param name The value's name, for the error message.
   *
   * @throws IllegalArgumentException If {@code value} is not a 64-character lowercase
   *     hexadecimal SHA-256 digest.
   */
  public static void requireSha256Hex(String value, String name) {
    if (value == null || value.length() != 64) {
      throw new IllegalArgumentException(name + " must be a 64-character lowercase SHA-256");
    }
    for (int i = 0; i < value.length(); i++) {
      final char character = value.charAt(i);
      if (!((character >= '0' && character <= '9')
          || (character >= 'a' && character <= 'f'))) {
        throw new IllegalArgumentException(name + " must be a 64-character lowercase SHA-256");
      }
    }
  }

  /**
   * Returns the lowercase hex SHA-256 digest of {@code bytes}.
   *
   * @param bytes The artifact bytes. Must not be {@code null}.
   *
   * @return The digest as lowercase hex. Never {@code null} or blank.
   */
  public static String sha256Hex(byte[] bytes) {
    if (bytes == null) {
      throw new IllegalArgumentException("bytes must not be null");
    }
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      final byte[] hashed = digest.digest(bytes);
      final StringBuilder builder = new StringBuilder(hashed.length * 2);
      for (byte value : hashed) {
        builder.append(HEX_DIGITS[(value >> 4) & 0xF]).append(HEX_DIGITS[value & 0xF]);
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  /**
   * Returns the lowercase hex SHA-256 digest of the file at {@code path}.
   *
   * @param path The model artifact path. Must not be {@code null}.
   *
   * @return The digest as lowercase hex.
   *
   * @throws IOException If the file cannot be read.
   */
  public static String sha256Hex(Path path) throws IOException {
    return sha256Hex(Files.readAllBytes(path));
  }

  /**
   * Returns the lowercase hex SHA-256 digest of the stream contents.
   *
   * @param input The artifact stream. Must not be {@code null}.
   *
   * @return The digest as lowercase hex.
   *
   * @throws IOException If the stream cannot be read.
   */
  public static String sha256Hex(InputStream input) throws IOException {
    return sha256Hex(input.readAllBytes());
  }
}
