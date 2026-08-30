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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.opennlp.grpc.spi.vocabulary.VocabularyStore;

/** SHA-256 helpers shared by the artifact stores built over a {@link VocabularyStore}. */
public final class ArtifactDigests {

  private static final int STREAM_BUFFER_BYTES = 8192;

  private ArtifactDigests() {
  }

  /**
   * Creates one SHA-256 digest.
   *
   * @return A fresh digest instance.
   */
  public static MessageDigest newSha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JVM lacks SHA-256", e);
    }
  }

  /**
   * Encodes digest bytes as lowercase hexadecimal.
   *
   * @param bytes The digest bytes.
   * @return The lowercase hexadecimal representation.
   */
  public static String hex(byte[] bytes) {
    final char[] alphabet = "0123456789abcdef".toCharArray();
    final StringBuilder hex = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      hex.append(alphabet[(value >>> 4) & 0x0f]);
      hex.append(alphabet[value & 0x0f]);
    }
    return hex.toString();
  }

  /**
   * Digests and counts one complete stream.
   *
   * @param input The stream to consume. It is not closed.
   * @return The byte count and lowercase hexadecimal SHA-256 of the consumed bytes.
   * @throws IOException Thrown if reading fails.
   */
  public static SizedDigest digest(InputStream input) throws IOException {
    final MessageDigest digest = newSha256();
    final byte[] buffer = new byte[STREAM_BUFFER_BYTES];
    long size = 0;
    int read;
    while ((read = input.read(buffer)) >= 0) {
      if (read > 0) {
        digest.update(buffer, 0, read);
        size += read;
      }
    }
    return new SizedDigest(size, hex(digest.digest()));
  }

  /**
   * The size and lowercase hexadecimal SHA-256 of one byte sequence.
   *
   * @param size The byte count.
   * @param hexDigest The lowercase hexadecimal SHA-256.
   */
  public record SizedDigest(long size, String hexDigest) {
  }

  /** Counts and digests the exact bytes written through to one target stream. */
  public static final class HashingOutputStream extends OutputStream {

    private final OutputStream target;
    private final MessageDigest digest = newSha256();
    private long count;

    /**
     * Wraps one target stream.
     *
     * @param target The stream every written byte is forwarded to.
     */
    public HashingOutputStream(OutputStream target) {
      this.target = target;
    }

    /** {@inheritDoc} */
    @Override
    public void write(int value) throws IOException {
      target.write(value);
      digest.update((byte) value);
      count++;
    }

    /** {@inheritDoc} */
    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
      target.write(buffer, offset, length);
      digest.update(buffer, offset, length);
      count += length;
    }

    /** {@inheritDoc} */
    @Override
    public void flush() throws IOException {
      target.flush();
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws IOException {
      target.close();
    }

    /** Reports the running byte count.
     *
     * @return Bytes written so far. */
    public long count() {
      return count;
    }

    /** Reports the running digest.
     *
     * @return The lowercase hexadecimal SHA-256 of the bytes written so far. */
    public String hexDigest() {
      return hex(digest.digest());
    }
  }
}
