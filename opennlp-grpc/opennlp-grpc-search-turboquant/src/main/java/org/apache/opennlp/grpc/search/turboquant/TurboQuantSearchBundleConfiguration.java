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

import java.net.URI;
import java.nio.file.Path;

import opennlp.embeddings.QuantizedEmbeddingMatrix;

/**
 * Complete, bounded input for one immutable TurboQuant search-bundle build.
 *
 * @param passagesFile Normalized CasePassage JSON Lines input.
 * @param preparationConfigFile Configuration used to prepare the normalized passages.
 * @param outputDirectory New bundle directory. It must not already exist.
 * @param indexId Stable index identifier.
 * @param displayName Human-readable index name.
 * @param modelId Logical embedding model requested from the provider.
 * @param backendId Concrete embedding backend, or blank to use the configured primary route.
 * @param bits TurboQuant bit width, from 2 through 4.
 * @param seed TurboQuant rotation seed.
 * @param corpus Corpus identity and license metadata.
 * @param limits Resource bounds applied before and during the build.
 */
public record TurboQuantSearchBundleConfiguration(
    Path passagesFile,
    Path preparationConfigFile,
    Path outputDirectory,
    String indexId,
    String displayName,
    String modelId,
    String backendId,
    int bits,
    long seed,
    CorpusMetadata corpus,
    Limits limits) {

  /**
   * Validates immutable build inputs.
   *
   * @throws IllegalArgumentException If a required value is absent, a path is not absolute and
   *     normalized, or a bit width is outside the supported range.
   */
  public TurboQuantSearchBundleConfiguration {
    passagesFile = absoluteNormalized(passagesFile, "passagesFile");
    preparationConfigFile = absoluteNormalized(preparationConfigFile, "preparationConfigFile");
    outputDirectory = absoluteNormalized(outputDirectory, "outputDirectory");
    indexId = required(indexId, "indexId");
    displayName = required(displayName, "displayName");
    modelId = required(modelId, "modelId");
    backendId = backendId == null ? "" : backendId.trim();
    validateBits(bits);
    if (corpus == null) {
      throw new IllegalArgumentException("corpus must not be null");
    }
    if (limits == null) {
      throw new IllegalArgumentException("limits must not be null");
    }
  }

  /**
   * Explicit corpus and license provenance stored in the bundle descriptor.
   *
   * @param title Human-readable corpus title.
   * @param provenance Human-readable corpus preparation and origin summary.
   * @param sourceUri Absolute source location.
   * @param licenseName License identifier or name.
   * @param licenseUri Absolute license text location.
   */
  public record CorpusMetadata(
      String title,
      String provenance,
      URI sourceUri,
      String licenseName,
      URI licenseUri) {

    /**
     * Validates required metadata and absolute URIs.
     *
     * @throws IllegalArgumentException If required metadata is absent or a URI is not absolute.
     */
    public CorpusMetadata {
      title = required(title, "corpus.title");
      provenance = required(provenance, "corpus.provenance");
      sourceUri = absoluteUri(sourceUri, "corpus.source.uri");
      licenseName = required(licenseName, "corpus.license.name");
      licenseUri = absoluteUri(licenseUri, "corpus.license.uri");
    }
  }

  /**
   * Bounds parsing, embedding calls, and generated output.
   *
   * @param maxRecords Maximum physical JSONL records and parsed passages.
   * @param maxInputBytes Maximum bytes in either the passages or preparation input file.
   * @param maxQueryBytes Maximum UTF-8 bytes in one passage sent to the embedding backend.
   * @param batchSize Maximum records in one embedding call.
   * @param maxBatchBytes Maximum total UTF-8 passage bytes in one embedding call.
   * @param maxOutputBytes Maximum bytes across all regular files in the staged bundle.
   */
  public record Limits(
      int maxRecords,
      long maxInputBytes,
      int maxQueryBytes,
      int batchSize,
      long maxBatchBytes,
      long maxOutputBytes) {

    /**
     * Validates positive resource bounds.
     *
     * @throws IllegalArgumentException If a bound is not positive or a batch cannot contain one
     *     maximum-sized query.
     */
    public Limits {
      positive(maxRecords, "maxRecords");
      positive(maxInputBytes, "maxInputBytes");
      positive(maxQueryBytes, "maxQueryBytes");
      positive(batchSize, "batchSize");
      positive(maxBatchBytes, "maxBatchBytes");
      positive(maxOutputBytes, "maxOutputBytes");
      if (maxBatchBytes < maxQueryBytes) {
        throw new IllegalArgumentException("maxBatchBytes must be at least maxQueryBytes");
      }
    }
  }

  /**
   * Validates and returns an absolute normalized path.
   *
   * @param path Path to validate.
   * @param name Component name used in failures.
   * @return Validated path.
   * @throws IllegalArgumentException If the path is absent, relative, or not normalized.
   */
  private static Path absoluteNormalized(Path path, String name) {
    if (path == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    final Path normalized = path.normalize();
    if (!normalized.isAbsolute() || !normalized.equals(path)) {
      throw new IllegalArgumentException(name + " must be an absolute normalized path");
    }
    return normalized;
  }

  /**
   * Validates and returns an absolute URI.
   *
   * @param uri URI to validate.
   * @param name Component name used in failures.
   * @return Validated URI.
   * @throws IllegalArgumentException If the URI is absent or relative.
   */
  private static URI absoluteUri(URI uri, String name) {
    if (uri == null || !uri.isAbsolute()) {
      throw new IllegalArgumentException(name + " must be an absolute URI");
    }
    return uri;
  }

  /**
   * Validates, trims, and returns required text.
   *
   * @param value Text to validate.
   * @param name Component name used in failures.
   * @return Trimmed text.
   * @throws IllegalArgumentException If the text is absent or blank.
   */
  private static String required(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }

  /**
   * Rejects a non-positive resource bound.
   *
   * @param value Bound value.
   * @param name Component name used in failures.
   * @throws IllegalArgumentException If the value is not positive.
   */
  private static void positive(long value, String name) {
    if (value < 1) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }

  /**
   * Validates a TurboQuant bit width against the embedding format contract.
   *
   * @param bits TurboQuant bit width.
   * @throws IllegalArgumentException If the value is outside the supported range.
   */
  static void validateBits(int bits) {
    if (bits < QuantizedEmbeddingMatrix.MIN_BITS
        || bits > QuantizedEmbeddingMatrix.MAX_BITS) {
      throw new IllegalArgumentException("bits must be from "
          + QuantizedEmbeddingMatrix.MIN_BITS + " through "
          + QuantizedEmbeddingMatrix.MAX_BITS);
    }
  }
}
