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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import opennlp.embeddings.corpus.CasePassage;
import opennlp.embeddings.index.TurboQuantIndex;
import opennlp.embeddings.index.VectorIndex.Hit;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.SearchCorpusDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexBuildDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.SearchMetric;
import org.apache.opennlp.grpc.v1.SearchProviderSelector;
import org.apache.opennlp.grpc.v1.StandardSearchProvider;
import org.apache.opennlp.grpc.spi.ModelArtifactHasher;
import org.apache.opennlp.grpc.spi.search.SearchResult;
import org.apache.opennlp.grpc.spi.search.SearchRecord;
import org.apache.opennlp.grpc.spi.search.SearchIndexProvider;
import org.apache.opennlp.grpc.spi.search.SearchIndexBundleConfiguration;

/** Loads a persisted TurboQuant index and its authoritative {@link CasePassage} records. */
public final class TurboQuantSearchBundleLoader {

  /** Versioned bundle descriptor filename within the TurboQuant directory. */
  public static final String DESCRIPTOR_FILE = "search-index.properties";

  private static final int FORMAT_VERSION = 1;
  private static final String PROVIDER_ID = "turbo_quant";
  private static final int MAX_DESCRIPTOR_BYTES = 65_536;
  private static final int MAX_ID_BYTES = 1_024;
  private static final int MAX_PASSAGE_JSON_OVERHEAD_BYTES = 262_144;

  /** Creates a loader for one configured TurboQuant search bundle. */
  public TurboQuantSearchBundleLoader() {
  }

  /**
   * Loads and fully validates one immutable bundle.
   *
   * @param configuration Validated paths and operator bounds.
   * @return A safely published, read-only provider.
   * @throws IOException If files are unreadable, malformed, incompatible, or exceed a bound.
   */
  public SearchIndexProvider load(SearchIndexBundleConfiguration configuration)
      throws IOException {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    final Path descriptorFile = configuration.indexDirectory().resolve(DESCRIPTOR_FILE);
    preflight(configuration, descriptorFile);
    final Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(descriptorFile)) {
      properties.load(input);
    }
    final int version = positiveInt(properties, "format.version");
    if (version != FORMAT_VERSION) {
      throw new IOException("Unsupported search bundle format.version " + version
          + "; expected " + FORMAT_VERSION);
    }
    final String descriptorId = require(properties, "index.id");
    if (!configuration.indexId().equals(descriptorId)) {
      throw new IOException("Configured search index id '" + configuration.indexId()
          + "' does not match bundle index.id '" + descriptorId + "'");
    }
    final String providerId = require(properties, "provider.id");
    if (!PROVIDER_ID.equals(providerId)) {
      throw new IOException("TurboQuant bundle provider.id must be '" + PROVIDER_ID
          + "', was '" + providerId + "'");
    }
    verifyArtifactHash(
        "corpus.artifact.sha256",
        requiredSha256(properties, "corpus.artifact.sha256"),
        TurboQuantBundleDigest.sha256(configuration.passagesFile()));
    verifyArtifactHash(
        "bundle.artifact.sha256",
        requiredSha256(properties, "bundle.artifact.sha256"),
        TurboQuantBundleDigest.bundleArtifactHash(
            configuration.indexDirectory(), configuration.passagesFile()));
    final List<String> indexIds = Files.readAllLines(
        configuration.indexDirectory().resolve(TurboQuantIndex.IDS_FILE), StandardCharsets.UTF_8);
    final Set<String> uniqueIndexIds = new HashSet<>();
    for (String id : indexIds) {
      if (!uniqueIndexIds.add(id)) {
        throw new IOException("TurboQuant index id '" + id + "' occurs more than once in "
            + TurboQuantIndex.IDS_FILE);
      }
    }

    final TurboQuantIndex index = TurboQuantIndex.read(configuration.indexDirectory());
    if (index.size() > configuration.maxRecords()) {
      throw new IOException("Search bundle record count " + index.size() + " exceeds maximum "
          + configuration.maxRecords());
    }
    final int declaredDimension = positiveInt(properties, "dimension");
    if (declaredDimension != index.dimension()) {
      throw new IOException("Search bundle dimension " + declaredDimension
          + " does not match TurboQuant dimension " + index.dimension());
    }
    final String metric = require(properties, "metric");
    if (!"cosine".equals(metric)) {
      throw new IOException("Unsupported search metric '" + metric + "'; expected 'cosine'");
    }

    final List<CasePassage> passages = CasePassage.readJsonl(configuration.passagesFile());
    if (passages.size() > configuration.maxRecords()) {
      throw new IOException("Passage record count " + passages.size() + " exceeds maximum "
          + configuration.maxRecords());
    }
    final Map<String, CasePassage> passageById = new HashMap<>();
    for (CasePassage passage : passages) {
      if (passageById.putIfAbsent(passage.id(), passage) != null) {
        throw new IOException("Passage id '" + passage.id() + "' occurs more than once in "
            + configuration.passagesFile());
      }
    }
    final Set<String> missing = new HashSet<>(indexIds);
    missing.removeAll(passageById.keySet());
    final Set<String> extra = new HashSet<>(passageById.keySet());
    extra.removeAll(indexIds);
    if (!missing.isEmpty() || !extra.isEmpty()) {
      throw new IOException("TurboQuant ids and passages must map exactly once; missing passages "
          + missing + ", extra passages " + extra);
    }

    final Map<String, SearchRecord> records = new HashMap<>(indexIds.size() * 2);
    for (String id : indexIds) {
      final CasePassage passage = passageById.get(id);
      final int textBytes = passage.text().getBytes(StandardCharsets.UTF_8).length;
      if (textBytes > configuration.maxSourceDocumentBytes()) {
        throw new IOException("Passage '" + id + "' source document uses " + textBytes
            + " UTF-8 bytes, exceeding maximum " + configuration.maxSourceDocumentBytes());
      }
      if (textBytes > configuration.maxIndexedTextBytes()) {
        throw new IOException("Passage '" + id + "' indexed text uses " + textBytes
            + " UTF-8 bytes, exceeding maximum " + configuration.maxIndexedTextBytes());
      }
      final OpenNlpDocument document = OpenNlpDocument.newBuilder()
          .setDocId(id)
          .setRawText(passage.text())
          .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE)
          .setMetadata(metadata(passage))
          .build();
      records.put(id, new SearchRecord(id, id, document,
          AnnotationSpan.newBuilder()
              .setStart(0)
              .setEnd(textBytes)
              .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
              .build(),
          passage.text()));
    }

    final EmbeddingRoute.Builder route = EmbeddingRoute.newBuilder()
        .setModelId(require(properties, "embedding.model.id"))
        .setBackendId(require(properties, "embedding.backend.id"))
        .setVectorSpaceId(require(properties, "embedding.vector_space.id"));
    final String embeddingHash = optional(properties, "embedding.artifact.sha256");
    if (embeddingHash != null) {
      requireSha256(embeddingHash, "embedding.artifact.sha256");
      route.setArtifactHash(embeddingHash);
    }
    final SearchCorpusDescriptor corpus = corpus(properties);
    final SearchIndexBuildDescriptor build = SearchIndexBuildDescriptor.newBuilder()
        .setBundleFormatVersion(version)
        .setBundleArtifactHash(requiredSha256(properties, "bundle.artifact.sha256"))
        .setBuilderId(require(properties, "builder.id"))
        .setBuilderVersion(require(properties, "builder.version"))
        .setPreparationConfigHash(requiredSha256(properties, "preparation.config.sha256"))
        .build();
    final SearchIndexDescriptor descriptor = SearchIndexDescriptor.newBuilder()
        .setIndexId(descriptorId)
        .setDisplayName(require(properties, "display.name"))
        .setProvider(SearchProviderSelector.newBuilder()
            .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
        .setEmbeddingRoute(route)
        .setDimension(index.dimension())
        .setMetric(SearchMetric.SEARCH_METRIC_COSINE)
        .setSize(index.size())
        .setImmutable(true)
        .setCorpus(corpus)
        .setMaxTopK(configuration.maxTopK())
        .setMaxQueryBytes(configuration.maxQueryBytes())
        .setMaxResponseBytes(configuration.maxResponseBytes())
        .setSupportsAllHits(index.size() <= SearchIndexBundleConfiguration.MAX_ALL_HITS_LIMIT)
        .setBuild(build)
        .build();
    return new TurboQuantProvider(index, Map.copyOf(records), descriptor);
  }

  private static void preflight(
      SearchIndexBundleConfiguration configuration, Path descriptorFile) throws IOException {
    final Path vectorsFile = configuration.indexDirectory().resolve(TurboQuantIndex.VECTORS_FILE);
    final Path idsFile = configuration.indexDirectory().resolve(TurboQuantIndex.IDS_FILE);
    final Path passagesFile = configuration.passagesFile();
    final List<Path> files = List.of(descriptorFile, vectorsFile, idsFile, passagesFile);
    long totalBytes = 0;
    for (Path file : files) {
      if (!Files.isRegularFile(file)) {
        throw new IOException("Search bundle lacks regular file " + file);
      }
      final long size = Files.size(file);
      totalBytes = Math.addExact(totalBytes, size);
      if (totalBytes > configuration.maxBundleBytes()) {
        throw new IOException("Search bundle uses more than configured maxBundleBytes "
            + configuration.maxBundleBytes());
      }
    }
    if (Files.size(descriptorFile) > MAX_DESCRIPTOR_BYTES) {
      throw new IOException(DESCRIPTOR_FILE + " exceeds " + MAX_DESCRIPTOR_BYTES + " bytes");
    }
    final long maxIdsBytes = Math.multiplyExact(
        (long) configuration.maxRecords(), MAX_ID_BYTES + 1L);
    if (Files.size(idsFile) > maxIdsBytes) {
      throw new IOException(TurboQuantIndex.IDS_FILE + " exceeds the configured record bound");
    }
    enforcePhysicalLineBounds(
        idsFile, configuration.maxRecords(), MAX_ID_BYTES, "id", false);
    enforcePhysicalLineBounds(
        passagesFile,
        configuration.maxRecords(),
        Math.addExact((long) configuration.maxSourceDocumentBytes(),
            MAX_PASSAGE_JSON_OVERHEAD_BYTES),
        "source document JSON",
        true);
  }

  private static void enforcePhysicalLineBounds(
      Path file, int maxLines, long maxLineBytes, String lineKind, boolean allowBlankLines)
      throws IOException {
    int lines = 0;
    long lineBytes = 0;
    boolean hasContent = false;
    try (InputStream input = new BufferedInputStream(Files.newInputStream(file))) {
      int next;
      while ((next = input.read()) != -1) {
        if (next == '\n') {
          if (hasContent) {
            lines++;
            if (lines > maxLines) {
              throw new IOException(file + " exceeds configured maxRecords " + maxLines);
            }
          } else if (!allowBlankLines) {
            throw new IOException(file + " contains a blank " + lineKind + " line");
          }
          hasContent = false;
          lineBytes = 0;
        } else {
          lineBytes++;
          if (lineBytes > maxLineBytes) {
            throw new IOException(file + " " + lineKind + " line exceeds "
                + maxLineBytes + " bytes");
          }
          if (next != '\r') {
            hasContent = true;
          }
        }
      }
    }
    if (hasContent) {
      if (++lines > maxLines) {
        throw new IOException(file + " exceeds configured maxRecords " + maxLines);
      }
    } else if (lineBytes > 0 && !allowBlankLines) {
      throw new IOException(file + " contains a blank " + lineKind + " line");
    }
  }

  private static SearchCorpusDescriptor corpus(Properties properties) throws IOException {
    final SearchCorpusDescriptor.Builder corpus = SearchCorpusDescriptor.newBuilder()
        .setTitle(require(properties, "corpus.title"))
        .setProvenanceSummary(require(properties, "corpus.provenance"));
    setOptionalUri(properties, "corpus.source.uri", corpus::setSourceUri);
    final String licenseName = optional(properties, "corpus.license.name");
    if (licenseName != null) {
      corpus.setLicenseName(licenseName);
    }
    setOptionalUri(properties, "corpus.license.uri", corpus::setLicenseUri);
    final String artifactHash = optional(properties, "corpus.artifact.sha256");
    if (artifactHash != null) {
      requireSha256(artifactHash, "corpus.artifact.sha256");
      corpus.setArtifactHash(artifactHash);
    }
    return corpus.build();
  }

  private static void setOptionalUri(
      Properties properties, String key, java.util.function.Consumer<String> setter)
      throws IOException {
    final String value = optional(properties, key);
    if (value == null) {
      return;
    }
    try {
      final URI uri = new URI(value);
      if (!uri.isAbsolute()) {
        throw new URISyntaxException(value, "URI must be absolute");
      }
    } catch (URISyntaxException e) {
      throw new IOException(key + " must be an absolute URI, was '" + value + "'", e);
    }
    setter.accept(value);
  }

  private static Struct metadata(CasePassage passage) {
    return Struct.newBuilder()
        .putFields("case_name", stringValue(passage.caseName()))
        .putFields("citation", stringValue(passage.cite()))
        .putFields("date", stringValue(passage.date()))
        .putFields("volume", stringValue(passage.volume()))
        .build();
  }

  private static Value stringValue(String value) {
    return Value.newBuilder().setStringValue(value).build();
  }

  private static int positiveInt(Properties properties, String key) throws IOException {
    final String value = require(properties, key);
    try {
      final int parsed = Integer.parseInt(value);
      if (parsed < 1) {
        throw new NumberFormatException();
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw new IOException(key + " must be a positive integer, was '" + value + "'");
    }
  }

  private static String requiredSha256(Properties properties, String key) throws IOException {
    final String value = require(properties, key);
    requireSha256(value, key);
    return value;
  }

  private static void requireSha256(String value, String key) throws IOException {
    try {
      ModelArtifactHasher.requireSha256Hex(value, key);
    } catch (IllegalArgumentException e) {
      throw new IOException(e.getMessage(), e);
    }
  }

  private static void verifyArtifactHash(String key, String declared, String actual)
      throws IOException {
    if (!declared.equals(actual)) {
      throw new IOException(key + " does not match the deployed search bundle artifact; declared "
          + declared + ", actual " + actual);
    }
  }

  private static String require(Properties properties, String key) throws IOException {
    final String value = optional(properties, key);
    if (value == null) {
      throw new IOException("Search bundle property " + key + " must not be blank");
    }
    return value;
  }

  private static String optional(Properties properties, String key) throws IOException {
    final String value = properties.getProperty(key);
    if (value == null || value.isBlank()) {
      return null;
    }
    if (!value.equals(value.trim())) {
      throw new IOException("Search bundle property " + key + " must be trimmed");
    }
    return value;
  }

  /** Immutable TurboQuant-backed provider. */
  private record TurboQuantProvider(
      TurboQuantIndex index,
      Map<String, SearchRecord> records,
      SearchIndexDescriptor descriptor) implements SearchIndexProvider {

    @Override
    public List<SearchResult> search(float[] queryVector, int topK) {
      return index.topK(queryVector, topK).stream().map(this::result).toList();
    }

    private SearchResult result(Hit hit) {
      final SearchRecord record = records.get(hit.id());
      if (record == null) {
        throw new IllegalStateException("TurboQuant returned unvalidated id '" + hit.id() + "'");
      }
      final double score = Math.max(-1, Math.min(1, hit.score()));
      return new SearchResult(record, score);
    }
  }
}
