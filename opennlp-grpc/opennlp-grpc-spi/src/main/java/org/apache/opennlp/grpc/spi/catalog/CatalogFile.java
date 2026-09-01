/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.spi.catalog;

import java.net.URI;
import java.nio.file.Path;

/**
 * One immutable, checksum-pinned file of a catalog model.
 *
 * @param relativePath The safe relative installation path of the file.
 * @param source The https download source.
 * @param byteSize The exact expected file size in bytes.
 * @param sha256 The lowercase hexadecimal SHA-256 digest of the file.
 */
public record CatalogFile(Path relativePath, URI source, long byteSize, String sha256) {

  /**
   * Validates one immutable file entry.
   *
   * @throws IllegalArgumentException If the path, source, size, or digest is invalid.
   */
  public CatalogFile {
    if (relativePath == null || relativePath.isAbsolute() || relativePath.getNameCount() < 1
        || relativePath.normalize().startsWith("..")) {
      throw new IllegalArgumentException("relativePath must be a safe relative path");
    }
    if (source == null || !"https".equals(source.getScheme())) {
      throw new IllegalArgumentException("source must use https");
    }
    if (byteSize < 1) {
      throw new IllegalArgumentException("byteSize must be positive");
    }
    if (!isSha256(sha256)) {
      throw new IllegalArgumentException("sha256 must be lowercase hexadecimal SHA-256");
    }
  }

  /** Returns whether the value is one lowercase SHA-256 digest. */
  private boolean isSha256(String value) {
    if (value == null || value.length() != 64) {
      return false;
    }
    for (int index = 0; index < value.length(); index++) {
      final char character = value.charAt(index);
      if (!((character >= '0' && character <= '9')
          || (character >= 'a' && character <= 'f'))) {
        return false;
      }
    }
    return true;
  }
}
