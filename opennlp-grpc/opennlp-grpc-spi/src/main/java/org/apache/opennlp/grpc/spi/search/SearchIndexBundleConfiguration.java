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
package org.apache.opennlp.grpc.spi.search;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Validated operator configuration for one immutable search bundle.
 *
 * @param indexId Stable configured index id.
 * @param indexDirectory Absolute normalized provider index directory.
 * @param passagesFile Absolute normalized-passage JSON Lines file.
 * @param maxTopK Largest result count accepted for this index.
 * @param maxQueryBytes Largest UTF-8 query accepted by the service.
 * @param maxResponseBytes Largest serialized response emitted by the service.
 * @param maxRecords Largest record count accepted during startup.
 * @param maxSourceDocumentBytes Largest retained source document accepted during startup.
 * @param maxIndexedTextBytes Largest indexed chunk text accepted during startup.
 * @param maxBundleBytes Largest aggregate byte size of bundle files read at startup.
 * @param providerOptions Immutable provider-specific options from the explicit namespace.
 */
public record SearchIndexBundleConfiguration(
    String indexId,
    Path indexDirectory,
    Path passagesFile,
    int maxTopK,
    int maxQueryBytes,
    int maxResponseBytes,
    int maxRecords,
    int maxSourceDocumentBytes,
    int maxIndexedTextBytes,
    int maxBundleBytes,
    Map<String, String> providerOptions) {

  /**
   * Default result limit when {@code max_top_k} is not configured, matching the dynamic
   * workspace default. The response byte budget still bounds what one reply carries;
   * operators raise the limit per index up to {@link #MAX_TOP_K_LIMIT}.
   */
  public static final int DEFAULT_MAX_TOP_K = 1_000;
  /** Absolute safety ceiling for one remotely requested result count. */
  public static final int MAX_TOP_K_LIMIT = 50_000;
  /** Absolute safety ceiling for one exhaustive result. */
  public static final int MAX_ALL_HITS_LIMIT = 50_000;
  /** Conservative default maximum query size in UTF-8 bytes. */
  public static final int DEFAULT_MAX_QUERY_BYTES = 16_384;
  /** Conservative default maximum serialized search response size. */
  public static final int DEFAULT_MAX_RESPONSE_BYTES = 8_388_608;
  /** Conservative default maximum number of records loaded in one bundle. */
  public static final int DEFAULT_MAX_RECORDS = 100_000;
  /** Absolute safety ceiling for records loaded into one Java int-addressed index. */
  public static final int MAX_RECORDS_LIMIT = 10_000_000;
  /** Conservative default maximum retained source-document size. */
  public static final int DEFAULT_MAX_SOURCE_DOCUMENT_BYTES = 10_485_760;
  /** Conservative default maximum indexed chunk size. */
  public static final int DEFAULT_MAX_INDEXED_TEXT_BYTES = 1_048_576;
  /** Conservative default aggregate byte limit for one bundle. */
  public static final int DEFAULT_MAX_BUNDLE_BYTES = 536_870_912;

  /**
   * Creates a configuration with the documented default bounds.
   *
   * @param indexId Stable configured index id.
   * @param indexDirectory Absolute normalized provider index directory.
   * @param passagesFile Absolute normalized-passage JSON Lines file.
   */
  public SearchIndexBundleConfiguration(
      String indexId, Path indexDirectory, Path passagesFile) {
    this(indexId, indexDirectory, passagesFile,
        DEFAULT_MAX_TOP_K,
        DEFAULT_MAX_QUERY_BYTES,
        DEFAULT_MAX_RESPONSE_BYTES,
        DEFAULT_MAX_RECORDS,
        DEFAULT_MAX_SOURCE_DOCUMENT_BYTES,
        DEFAULT_MAX_INDEXED_TEXT_BYTES,
        DEFAULT_MAX_BUNDLE_BYTES,
        Map.of());
  }

  /** Validates identifiers, paths, and the result limit. */
  public SearchIndexBundleConfiguration {
    if (!isStableId(indexId)) {
      throw new IllegalArgumentException("search index id must be a trimmed lower-case ASCII "
          + "identifier using letters, digits, dots, hyphens, or underscores, was '"
          + indexId + "'");
    }
    validatePath(indexDirectory, "indexDirectory");
    validatePath(passagesFile, "passagesFile");
    if (maxTopK < 1) {
      throw new IllegalArgumentException("maxTopK must be positive");
    }
    if (maxTopK > MAX_TOP_K_LIMIT) {
      throw new IllegalArgumentException("maxTopK must not exceed fixed safety ceiling "
          + MAX_TOP_K_LIMIT);
    }
    requirePositive(maxQueryBytes, "maxQueryBytes");
    requirePositive(maxResponseBytes, "maxResponseBytes");
    requirePositive(maxRecords, "maxRecords");
    if (maxRecords > MAX_RECORDS_LIMIT) {
      throw new IllegalArgumentException("maxRecords must not exceed fixed safety ceiling "
          + MAX_RECORDS_LIMIT);
    }
    requirePositive(maxSourceDocumentBytes, "maxSourceDocumentBytes");
    requirePositive(maxIndexedTextBytes, "maxIndexedTextBytes");
    requirePositive(maxBundleBytes, "maxBundleBytes");
    if (providerOptions == null) {
      throw new IllegalArgumentException("providerOptions must not be null");
    }
    for (Map.Entry<String, String> option : providerOptions.entrySet()) {
      if (!isStableId(option.getKey())) {
        throw new IllegalArgumentException("provider option name must be a trimmed lower-case "
            + "ASCII identifier using letters, digits, dots, hyphens, or underscores, was '"
            + option.getKey() + "'");
      }
      if (option.getValue() == null || option.getValue().isBlank()
          || !option.getValue().equals(option.getValue().trim())) {
        throw new IllegalArgumentException("provider option '" + option.getKey()
            + "' must have a nonblank trimmed value");
      }
    }
    providerOptions = Map.copyOf(new TreeMap<>(providerOptions));
  }

  private static boolean isStableId(String value) {
    if (value == null || value.isEmpty() || !value.equals(value.trim())) {
      return false;
    }
    boolean previousSeparator = true;
    for (int index = 0; index < value.length(); index++) {
      final char character = value.charAt(index);
      final boolean alphanumeric = (character >= 'a' && character <= 'z')
          || (character >= '0' && character <= '9');
      final boolean separator = character == '-' || character == '_' || character == '.';
      if ((!alphanumeric && !separator) || (separator && previousSeparator)) {
        return false;
      }
      previousSeparator = separator;
    }
    return !previousSeparator;
  }

  private static void validatePath(Path path, String name) {
    if (path == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    if (!path.isAbsolute()) {
      throw new IllegalArgumentException(name + " must be absolute: " + path);
    }
    if (!path.equals(path.normalize())) {
      throw new IllegalArgumentException(name + " must be normalized without traversal: " + path);
    }
  }

  private static void requirePositive(int value, String name) {
    if (value < 1) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }
}
