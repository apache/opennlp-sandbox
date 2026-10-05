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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import opennlp.embeddings.corpus.CasePassage;
import opennlp.embeddings.index.TurboQuantIndex;
import org.apache.opennlp.grpc.spi.ModelArtifactHasher;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingBatchResult;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;

/** Builds a bounded, immutable TurboQuant search bundle from normalized CasePassage JSONL. */
public final class TurboQuantSearchBundleBuilder {

  /** Stable builder identity stored in every descriptor. */
  public static final String BUILDER_ID = "opennlp-grpc-turbo-quant-bundle";

  /** Version of the builder's descriptor and preparation-hash behavior. */
  public static final String BUILDER_VERSION = "1";

  /** Deployed normalized passage filename within a bundle. */
  public static final String PASSAGES_FILE = "passages.jsonl";

  private static final String PROVIDER_ID = "turbo_quant";
  private static final String FORMAT_VERSION_PROPERTY = "format.version";
  private static final String BITS_PROPERTY = "turbo_quant.bits";
  private static final String SEED_PROPERTY = "turbo_quant.seed";
  private static final String PREPARATION_SNAPSHOT_FILE = ".preparation-config.snapshot";
  private static final int FORMAT_VERSION = 1;
  private static final int MAX_ID_BYTES = 1_024;
  private static final int COPY_BUFFER_BYTES = 16_384;

  private final EmbeddingProvider embeddingProvider;

  /**
   * Creates a reusable builder backed by an injected embedding provider.
   *
   * @param embeddingProvider Provider used for bounded batch embedding.
   * @throws IllegalArgumentException If {@code embeddingProvider} is {@code null}.
   */
  public TurboQuantSearchBundleBuilder(EmbeddingProvider embeddingProvider) {
    if (embeddingProvider == null) {
      throw new IllegalArgumentException("embeddingProvider must not be null");
    }
    this.embeddingProvider = embeddingProvider;
  }

  /**
   * Builds one deployable bundle without replacing any existing path.
   *
   * @param configuration Validated build metadata and resource bounds.
   * @return Built artifact paths, resolved route, and digests.
   * @throws IllegalArgumentException If {@code configuration} is {@code null}.
   * @throws IOException If input is malformed, boundedness is exceeded, the output already
   *     exists, or persistence fails.
   */
  public BuildResult build(TurboQuantSearchBundleConfiguration configuration) throws IOException {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    preflight(configuration);
    final Path output = configuration.outputDirectory();
    final Path parent = output.getParent();
    if (parent == null) {
      throw new IOException("outputDirectory must have a parent");
    }
    Files.createDirectories(parent);
    final Path staging = Files.createTempDirectory(parent, ".opennlp-search-bundle-");
    boolean published = false;
    Throwable failure = null;
    try {
      final Path deployedPassages = staging.resolve(PASSAGES_FILE);
      final Path preparationSnapshot = staging.resolve(PREPARATION_SNAPSHOT_FILE);
      snapshotInput(configuration.passagesFile(), deployedPassages,
          configuration.limits().maxInputBytes(), "passagesFile");
      snapshotInput(configuration.preparationConfigFile(), preparationSnapshot,
          configuration.limits().maxInputBytes(), "preparationConfigFile");
      enforcePhysicalRecordBounds(deployedPassages, configuration.limits());
      final List<CasePassage> passages = readPassages(deployedPassages);
      validatePassages(passages, configuration);

      final int dimension = embeddingProvider.embeddingDimension(configuration.modelId());
      if (dimension < 1) {
        throw new IOException("Embedding model '" + configuration.modelId()
            + "' reports a non-positive dimension " + dimension);
      }
      final TurboQuantIndex index = new TurboQuantIndex(
          dimension, configuration.bits(), configuration.seed());
      EmbeddingRoute resolvedRoute = null;
      for (List<CasePassage> batch : batches(passages, configuration.limits())) {
        final List<String> texts = batch.stream().map(CasePassage::text).toList();
        final String selectedBackend = resolvedRoute == null
            ? configuration.backendId() : resolvedRoute.getBackendId();
        final EmbeddingBatchResult embedded = embeddingProvider.embedBatchResolved(
            configuration.modelId(), selectedBackend, texts);
        if (embedded == null) {
          throw new IOException("Embedding provider returned a null batch result");
        }
        if (embedded.vectors() == null) {
          throw new IOException("Embedding provider returned a null vector list");
        }
        final EmbeddingRoute batchRoute = validateRoute(embedded.route(), configuration);
        if (resolvedRoute == null) {
          resolvedRoute = batchRoute;
        } else if (!resolvedRoute.equals(batchRoute)) {
          throw new IOException("Embedding route changed during bundle build from "
              + routeIdentity(resolvedRoute) + " to " + routeIdentity(batchRoute));
        }
        if (embedded.vectors().size() != batch.size()) {
          throw new IOException("Embedding provider returned " + embedded.vectors().size()
              + " vectors for a batch of " + batch.size() + " passages");
        }
        for (int i = 0; i < batch.size(); i++) {
          final float[] vector = embedded.vectors().get(i);
          validateVector(vector, dimension, batch.get(i).id());
          index.add(batch.get(i).id(), vector);
        }
      }
      if (resolvedRoute == null) {
        throw new IOException("Passages input must contain at least one record");
      }
      index.freeze();
      index.write(staging);
      final String corpusHash = TurboQuantBundleDigest.sha256(deployedPassages);
      final String preparationHash = preparationConfigHash(
          preparationSnapshot, configuration.bits(), configuration.seed());
      final String bundleHash = TurboQuantBundleDigest.bundleArtifactHash(
          staging, deployedPassages);
      writeDescriptor(staging, configuration, dimension, resolvedRoute,
          corpusHash, preparationHash, bundleHash);
      Files.delete(preparationSnapshot);
      enforceOutputBound(staging, configuration.limits().maxOutputBytes());
      publish(staging, output);
      published = true;
      return new BuildResult(
          output,
          output.resolve(PASSAGES_FILE),
          passages.size(),
          dimension,
          resolvedRoute,
          corpusHash,
          preparationHash,
          bundleHash);
    } catch (IOException | RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      if (!published) {
        try {
          deleteTree(staging);
        } catch (IOException cleanupFailure) {
          if (failure != null) {
            failure.addSuppressed(cleanupFailure);
          } else {
            throw cleanupFailure;
          }
        }
      }
    }
  }

  /**
   * Hashes the canonical preparation identity used in the bundle descriptor.
   *
   * <p>The SHA-256 input consists of four UTF-8 lines in this fixed order, each terminated by
   * LF: {@code format.version=1},
   * {@code source.preparation.sha256=<SHA-256 of the preparation file bytes>},
   * {@code turbo_quant.bits=<decimal bits>}, and
   * {@code turbo_quant.seed=<decimal seed>}. Runtime resource bounds and batch sizes are excluded
   * because they do not define the intended vectors.</p>
   *
   * @param preparationConfigFile Source preparation configuration.
   * @param bits TurboQuant bit width.
   * @param seed TurboQuant rotation seed.
   * @return Lowercase hexadecimal SHA-256 digest.
   * @throws IllegalArgumentException If {@code preparationConfigFile} is {@code null} or
   *     {@code bits} is outside the supported range.
   * @throws IOException If the preparation file cannot be read.
   */
  static String preparationConfigHash(Path preparationConfigFile, int bits, long seed)
      throws IOException {
    if (preparationConfigFile == null) {
      throw new IllegalArgumentException("preparationConfigFile must not be null");
    }
    TurboQuantSearchBundleConfiguration.validateBits(bits);
    final String sourceHash = TurboQuantBundleDigest.sha256(preparationConfigFile);
    final String canonical = FORMAT_VERSION_PROPERTY + "=" + FORMAT_VERSION + "\n"
        + "source.preparation.sha256=" + sourceHash + "\n"
        + BITS_PROPERTY + "=" + bits + "\n"
        + SEED_PROPERTY + "=" + seed + "\n";
    return TurboQuantBundleDigest.sha256(canonical.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Validates paths and physical input bounds before loading passage objects.
   *
   * @param configuration Build configuration.
   * @throws IOException If a path or bound is invalid.
   */
  private void preflight(TurboQuantSearchBundleConfiguration configuration)
      throws IOException {
    final Path output = configuration.outputDirectory();
    if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Refusing to overwrite existing output path " + output);
    }
    checkInputFile(configuration.passagesFile(), "passagesFile",
        configuration.limits().maxInputBytes());
    checkInputFile(configuration.preparationConfigFile(), "preparationConfigFile",
        configuration.limits().maxInputBytes());
  }

  /**
   * Validates one bounded regular input file.
   *
   * @param path Input file.
   * @param name Option name used in failures.
   * @param maxBytes Maximum allowed file size.
   * @throws IOException If the path is not a regular file or exceeds the bound.
   */
  private void checkInputFile(Path path, String name, long maxBytes) throws IOException {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException(name + " must be a regular file: " + path);
    }
    final long size = Files.size(path);
    if (size > maxBytes) {
      throw new IOException(name + " uses " + size + " bytes, exceeding maximum " + maxBytes);
    }
  }

  /**
   * Copies one input into private staging while enforcing its byte bound.
   *
   * @param source Validated source file.
   * @param snapshot Private snapshot path.
   * @param maxBytes Maximum bytes to copy.
   * @param name Option name used in failures.
   * @throws IOException If the source changes to an invalid path, exceeds the bound, or cannot be
   *     copied.
   */
  private void snapshotInput(Path source, Path snapshot, long maxBytes, String name)
      throws IOException {
    final byte[] buffer = new byte[COPY_BUFFER_BYTES];
    long bytes = 0;
    try (InputStream input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS);
         OutputStream output = Files.newOutputStream(snapshot)) {
      int count;
      while ((count = input.read(buffer)) != -1) {
        if (count > maxBytes - bytes) {
          throw new IOException(name + " exceeds maximum " + maxBytes + " bytes");
        }
        output.write(buffer, 0, count);
        bytes += count;
      }
    }
  }

  /**
   * Parses the private passage snapshot as a checked build operation.
   *
   * @param snapshot Private CasePassage JSON Lines snapshot.
   * @return Parsed passages.
   * @throws IOException If the snapshot cannot be parsed into valid passages.
   */
  private List<CasePassage> readPassages(Path snapshot) throws IOException {
    try {
      return CasePassage.readJsonl(snapshot);
    } catch (IllegalArgumentException e) {
      throw new IOException("Invalid passages input: " + e.getMessage(), e);
    }
  }

  /**
   * Rejects excessive physical records before parsing.
   *
   * @param file CasePassage JSON Lines file.
   * @param limits Build limits.
   * @throws IOException If the record bound is exceeded or the snapshot cannot be read.
   */
  private void enforcePhysicalRecordBounds(
      Path file, TurboQuantSearchBundleConfiguration.Limits limits) throws IOException {
    int lines = 0;
    boolean hasBytes = false;
    boolean previousCarriageReturn = false;
    final byte[] buffer = new byte[COPY_BUFFER_BYTES];
    try (InputStream input = Files.newInputStream(file)) {
      int count;
      while ((count = input.read(buffer)) != -1) {
        for (int index = 0; index < count; index++) {
          final byte value = buffer[index];
          if (value == '\r') {
            lines++;
            if (lines > limits.maxRecords()) {
              throw new IOException("passagesFile exceeds maxRecords " + limits.maxRecords());
            }
            hasBytes = false;
            previousCarriageReturn = true;
          } else if (value == '\n') {
            if (!previousCarriageReturn) {
              lines++;
              if (lines > limits.maxRecords()) {
                throw new IOException(
                    "passagesFile exceeds maxRecords " + limits.maxRecords());
              }
            }
            hasBytes = false;
            previousCarriageReturn = false;
          } else {
            hasBytes = true;
            previousCarriageReturn = false;
          }
        }
      }
    }
    if (hasBytes && ++lines > limits.maxRecords()) {
      throw new IOException("passagesFile exceeds maxRecords " + limits.maxRecords());
    }
  }

  /**
   * Validates parsed passage identities, required fields, and query sizes.
   *
   * @param passages Parsed passages.
   * @param configuration Build configuration.
   * @throws IOException If a passage violates the bundle contract.
   */
  private void validatePassages(
      List<CasePassage> passages, TurboQuantSearchBundleConfiguration configuration)
      throws IOException {
    if (passages.isEmpty()) {
      throw new IOException("Passages input must contain at least one record");
    }
    if (passages.size() > configuration.limits().maxRecords()) {
      throw new IOException("Passage record count " + passages.size() + " exceeds maximum "
          + configuration.limits().maxRecords());
    }
    final Set<String> ids = new HashSet<>();
    for (CasePassage passage : passages) {
      if (passage == null) {
        throw new IOException("Passages input contains a null record");
      }
      final String id = passage.id();
      if (id == null || id.isBlank() || !id.equals(id.trim())) {
        throw new IOException("Passage id must be nonblank and trimmed");
      }
      if (id.indexOf('\n') >= 0 || id.indexOf('\r') >= 0) {
        throw new IOException("Passage id must not contain a line break: '" + id + "'");
      }
      final int idBytes = id.getBytes(StandardCharsets.UTF_8).length;
      if (idBytes > MAX_ID_BYTES) {
        throw new IOException("Passage id uses " + idBytes
            + " UTF-8 bytes, exceeding maximum " + MAX_ID_BYTES);
      }
      if (!ids.add(id)) {
        throw new IOException("Passage id '" + id + "' occurs more than once");
      }
      requireValue(passage.caseName(), id, "caseName");
      requireValue(passage.cite(), id, "cite");
      requireValue(passage.date(), id, "date");
      requireValue(passage.volume(), id, "volume");
      requireValue(passage.text(), id, "text");
      final int queryBytes = passage.text().getBytes(StandardCharsets.UTF_8).length;
      if (queryBytes > configuration.limits().maxQueryBytes()) {
        throw new IOException("Passage '" + id + "' uses " + queryBytes
            + " UTF-8 query bytes, exceeding maximum "
            + configuration.limits().maxQueryBytes());
      }
    }
  }

  /**
   * Rejects a null required CasePassage field.
   *
   * @param value Field value.
   * @param id Passage identifier.
   * @param field Field name.
   * @throws IOException If the value is {@code null}.
   */
  private void requireValue(String value, String id, String field) throws IOException {
    if (value == null) {
      throw new IOException("Passage '" + id + "' has null " + field);
    }
  }

  /**
   * Partitions passages by both record count and UTF-8 byte count.
   *
   * @param passages Validated passages.
   * @param limits Build limits.
   * @return Immutable batches.
   */
  private List<List<CasePassage>> batches(
      List<CasePassage> passages, TurboQuantSearchBundleConfiguration.Limits limits) {
    final List<List<CasePassage>> batches = new ArrayList<>();
    List<CasePassage> current = new ArrayList<>();
    long currentBytes = 0;
    for (CasePassage passage : passages) {
      final int bytes = passage.text().getBytes(StandardCharsets.UTF_8).length;
      if (!current.isEmpty()
          && (current.size() == limits.batchSize()
              || currentBytes > limits.maxBatchBytes() - bytes)) {
        batches.add(List.copyOf(current));
        current = new ArrayList<>();
        currentBytes = 0;
      }
      current.add(passage);
      currentBytes += bytes;
    }
    if (!current.isEmpty()) {
      batches.add(List.copyOf(current));
    }
    return List.copyOf(batches);
  }

  /**
   * Validates the concrete embedding route returned for a batch.
   *
   * @param route Resolved route.
   * @param configuration Build configuration.
   * @return Validated route.
   * @throws IOException If required provenance is absent or differs from the request.
   */
  private EmbeddingRoute validateRoute(
      EmbeddingRoute route, TurboQuantSearchBundleConfiguration configuration) throws IOException {
    if (route == null) {
      throw new IOException("Embedding provider returned a null route");
    }
    if (!configuration.modelId().equals(route.getModelId())) {
      throw new IOException("Embedding provider resolved unexpected model '" + route.getModelId()
          + "'; expected '" + configuration.modelId() + "'");
    }
    requiredRouteText(route.getBackendId(), "backend_id");
    requiredRouteText(route.getVectorSpaceId(), "vector_space_id");
    if (!configuration.backendId().isEmpty()
        && !configuration.backendId().equals(route.getBackendId())) {
      throw new IOException("Embedding provider resolved backend '" + route.getBackendId()
          + "'; expected requested backend '" + configuration.backendId() + "'");
    }
    if (route.hasArtifactHash()) {
      requireSha256(route.getArtifactHash(), "embedding artifact hash");
    }
    return route;
  }

  /**
   * Rejects a blank or untrimmed embedding-route field.
   *
   * @param value Field value.
   * @param name Field name.
   * @throws IOException If the value is blank or untrimmed.
   */
  private void requiredRouteText(String value, String name) throws IOException {
    if (value == null || value.isBlank() || !value.equals(value.trim())) {
      throw new IOException("Resolved embedding route " + name + " must be nonblank and trimmed");
    }
  }

  /**
   * Validates a vector's dimension and finite values.
   *
   * @param vector Vector to validate.
   * @param dimension Required dimension.
   * @param id Passage identifier.
   * @throws IOException If the vector is absent, has the wrong dimension, or is non-finite.
   */
  private void validateVector(float[] vector, int dimension, String id) throws IOException {
    if (vector == null || vector.length != dimension) {
      throw new IOException("Embedding vector for passage '" + id + "' must have dimension "
          + dimension);
    }
    for (float value : vector) {
      if (!Float.isFinite(value)) {
        throw new IOException("Embedding vector for passage '" + id
            + "' contains a non-finite value");
      }
    }
  }

  /**
   * Writes deterministic deployment metadata for a completed staged bundle.
   *
   * @param staging Staged bundle directory.
   * @param configuration Build configuration.
   * @param dimension Vector dimension.
   * @param route Resolved embedding route.
   * @param corpusHash Corpus artifact hash.
   * @param preparationHash Preparation configuration hash.
   * @param bundleHash Bundle artifact hash.
   * @throws IOException If the descriptor cannot be written.
   */
  private void writeDescriptor(
      Path staging,
      TurboQuantSearchBundleConfiguration configuration,
      int dimension,
      EmbeddingRoute route,
      String corpusHash,
      String preparationHash,
      String bundleHash) throws IOException {
    final Map<String, String> properties = new TreeMap<>();
    properties.put(FORMAT_VERSION_PROPERTY, Integer.toString(FORMAT_VERSION));
    properties.put("index.id", configuration.indexId());
    properties.put("display.name", configuration.displayName());
    properties.put("provider.id", PROVIDER_ID);
    properties.put("embedding.model.id", route.getModelId());
    properties.put("embedding.backend.id", route.getBackendId());
    properties.put("embedding.vector_space.id", route.getVectorSpaceId());
    if (route.hasArtifactHash()) {
      properties.put("embedding.artifact.sha256", route.getArtifactHash());
    }
    properties.put("dimension", Integer.toString(dimension));
    properties.put("metric", "cosine");
    properties.put(BITS_PROPERTY, Integer.toString(configuration.bits()));
    properties.put(SEED_PROPERTY, Long.toString(configuration.seed()));
    properties.put("corpus.title", configuration.corpus().title());
    properties.put("corpus.provenance", configuration.corpus().provenance());
    properties.put("corpus.source.uri", configuration.corpus().sourceUri().toASCIIString());
    properties.put("corpus.license.name", configuration.corpus().licenseName());
    properties.put("corpus.license.uri",
        configuration.corpus().licenseUri().toASCIIString());
    properties.put("corpus.artifact.sha256", corpusHash);
    properties.put("bundle.artifact.sha256", bundleHash);
    properties.put("builder.id", BUILDER_ID);
    properties.put("builder.version", BUILDER_VERSION);
    properties.put("preparation.config.sha256", preparationHash);
    try (BufferedWriter writer = Files.newBufferedWriter(
        staging.resolve(TurboQuantSearchBundleLoader.DESCRIPTOR_FILE),
        StandardCharsets.US_ASCII)) {
      for (Map.Entry<String, String> property : properties.entrySet()) {
        writer.write(propertyText(property.getKey()));
        writer.write('=');
        writer.write(propertyText(property.getValue()));
        writer.write('\n');
      }
    }
  }

  /**
   * Encodes Java properties text deterministically without timestamps or platform encoding.
   *
   * @param value Property key or value.
   * @return Escaped property text.
   */
  private String propertyText(String value) {
    final StringBuilder escaped = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      final char character = value.charAt(i);
      switch (character) {
        case ' ' -> escaped.append("\\ ");
        case '\\' -> escaped.append("\\\\");
        case '\t' -> escaped.append("\\t");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\f' -> escaped.append("\\f");
        case '=', ':', '#', '!' -> escaped.append('\\').append(character);
        default -> {
          if (character < 0x20 || character > 0x7e) {
            escaped.append("\\u");
            TurboQuantBundleDigest.appendHex16(escaped, character);
          } else {
            escaped.append(character);
          }
        }
      }
    }
    return escaped.toString();
  }

  /**
   * Rejects a staged bundle whose regular files exceed the configured total.
   *
   * @param staging Staged bundle directory.
   * @param maxOutputBytes Maximum total regular-file bytes.
   * @throws IOException If the staged bundle exceeds the bound or cannot be inspected.
   */
  private void enforceOutputBound(Path staging, long maxOutputBytes) throws IOException {
    long bytes = 0;
    try (var paths = Files.walk(staging)) {
      for (Path path : paths.filter(Files::isRegularFile).toList()) {
        final long fileBytes = Files.size(path);
        if (fileBytes > maxOutputBytes - bytes) {
          throw new IOException("Generated bundle uses more than maxOutputBytes " + maxOutputBytes);
        }
        bytes += fileBytes;
      }
    }
  }

  /**
   * Publishes the complete staging directory at its final path.
   *
   * @param staging Staged bundle directory.
   * @param output Final output path.
   * @throws IOException If the directory cannot be moved.
   */
  private void publish(Path staging, Path output) throws IOException {
    Files.move(staging, output);
  }

  /**
   * Deletes an unpublished staging tree without following symbolic links.
   *
   * @param root Staging root.
   * @throws IOException If cleanup fails.
   */
  private void deleteTree(Path root) throws IOException {
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    Files.walkFileTree(root, new SimpleFileVisitor<>() {
      /** {@inheritDoc} */
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
          throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      /** {@inheritDoc} */
      @Override
      public FileVisitResult postVisitDirectory(Path directory, IOException failure)
          throws IOException {
        if (failure != null) {
          throw failure;
        }
        Files.delete(directory);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Returns the stable model, backend, and vector-space route identity.
   *
   * @param route Embedding route.
   * @return Slash-separated route identity.
   */
  private String routeIdentity(EmbeddingRoute route) {
    return route.getModelId() + "/" + route.getBackendId() + "/" + route.getVectorSpaceId();
  }

  /**
   * Validates a lowercase SHA-256 value returned by an embedding provider.
   *
   * @param value Hash value.
   * @param name Value name used in failures.
   * @throws IOException If the value is not a lowercase SHA-256 hash.
   */
  private void requireSha256(String value, String name) throws IOException {
    try {
      ModelArtifactHasher.requireSha256Hex(value, name);
    } catch (IllegalArgumentException e) {
      throw new IOException(e.getMessage(), e);
    }
  }

  /**
   * Successful immutable bundle output.
   *
   * @param outputDirectory Published bundle directory.
   * @param passagesFile Deployed normalized passages file.
   * @param recordCount Number of indexed passages.
   * @param dimension Vector dimension.
   * @param embeddingRoute Concrete route that produced every vector.
   * @param corpusArtifactHash SHA-256 of the deployed passages file.
   * @param preparationConfigHash Canonical preparation configuration SHA-256.
   * @param bundleArtifactHash Canonical vectors, ids, and passages SHA-256.
   */
  public record BuildResult(
      Path outputDirectory,
      Path passagesFile,
      int recordCount,
      int dimension,
      EmbeddingRoute embeddingRoute,
      String corpusArtifactHash,
      String preparationConfigHash,
      String bundleArtifactHash) {
  }
}
