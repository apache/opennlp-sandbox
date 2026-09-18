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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import opennlp.embeddings.corpus.CasePassage;
import opennlp.embeddings.index.TurboQuantIndex;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingBatchResult;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.spi.search.SearchIndexBundleConfiguration;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurboQuantSearchBundleBuilderTest {

  private static final String REQUESTED_MODEL = "requested-model";
  private static final String RESOLVED_BACKEND = "actual-backend";
  private static final String VECTOR_SPACE = "space-v2";

  @TempDir
  Path tempDir;

  @Test
  void buildsLoaderCompatibleBundleWithResolvedProvenanceAndBounds() throws Exception {
    final Path passages = passages(List.of(
        passage("p-one", "abc"), passage("p-two", "defg"), passage("p-three", "hijkl")));
    final Path preparation = Files.writeString(tempDir.resolve("prepare.properties"),
        "normalizer=legal-v1\n");
    final RecordingProvider provider = new RecordingProvider();
    final TurboQuantSearchBundleConfiguration configuration = configuration(
        passages, preparation, tempDir.resolve("bundle"), 10, 1_000_000, 5, 2, 7, 1_000_000);

    final TurboQuantSearchBundleBuilder.BuildResult result =
        new TurboQuantSearchBundleBuilder(provider).build(configuration);

    assertEquals(List.of(List.of("abc", "defg"), List.of("hijkl")), provider.batches);
    assertEquals(List.of("", RESOLVED_BACKEND), provider.requestedBackends);
    assertEquals(RESOLVED_BACKEND, result.embeddingRoute().getBackendId());
    assertEquals(VECTOR_SPACE, result.embeddingRoute().getVectorSpaceId());
    assertEquals(3, result.recordCount());
    assertEquals(4, result.dimension());
    assertEquals(3, TurboQuantIndex.read(result.outputDirectory()).size());
    assertEquals(Files.readString(passages),
        Files.readString(result.outputDirectory().resolve(
            TurboQuantSearchBundleBuilder.PASSAGES_FILE)));

    final Properties descriptor = properties(result.outputDirectory());
    assertEquals("1", descriptor.getProperty("format.version"));
    assertEquals("legal", descriptor.getProperty("index.id"));
    assertEquals("Legal passages", descriptor.getProperty("display.name"));
    assertEquals("turbo_quant", descriptor.getProperty("provider.id"));
    assertEquals(REQUESTED_MODEL, descriptor.getProperty("embedding.model.id"));
    assertEquals(RESOLVED_BACKEND, descriptor.getProperty("embedding.backend.id"));
    assertEquals(VECTOR_SPACE, descriptor.getProperty("embedding.vector_space.id"));
    assertEquals("a".repeat(64), descriptor.getProperty("embedding.artifact.sha256"));
    assertEquals("4", descriptor.getProperty("dimension"));
    assertEquals("cosine", descriptor.getProperty("metric"));
    assertEquals("3", descriptor.getProperty("turbo_quant.bits"));
    assertEquals("42", descriptor.getProperty("turbo_quant.seed"));
    assertEquals("Legal cases", descriptor.getProperty("corpus.title"));
    assertEquals("Normalized reporter export", descriptor.getProperty("corpus.provenance"));
    assertEquals("https://example.test/cases", descriptor.getProperty("corpus.source.uri"));
    assertEquals("CC0-1.0", descriptor.getProperty("corpus.license.name"));
    assertEquals("https://creativecommons.org/publicdomain/zero/1.0/",
        descriptor.getProperty("corpus.license.uri"));
    assertEquals(TurboQuantBundleDigest.sha256(result.passagesFile()),
        descriptor.getProperty("corpus.artifact.sha256"));
    assertEquals(TurboQuantBundleDigest.bundleArtifactHash(
            result.outputDirectory(), result.passagesFile()),
        descriptor.getProperty("bundle.artifact.sha256"));
    assertEquals(TurboQuantSearchBundleBuilder.BUILDER_ID,
        descriptor.getProperty("builder.id"));
    assertEquals(TurboQuantSearchBundleBuilder.BUILDER_VERSION,
        descriptor.getProperty("builder.version"));
    assertEquals(result.preparationConfigHash(),
        descriptor.getProperty("preparation.config.sha256"));

    final var loaded = new TurboQuantSearchBundleLoader().load(
        new SearchIndexBundleConfiguration("legal", result.outputDirectory(),
            result.passagesFile()));
    assertEquals(result.bundleArtifactHash(),
        loaded.descriptor().getBuild().getBundleArtifactHash());
    assertEquals(VECTOR_SPACE, loaded.descriptor().getEmbeddingRoute().getVectorSpaceId());
  }

  @Test
  void rejectsExistingOutputWithoutCallingProvider() throws Exception {
    final Path output = Files.createDirectory(tempDir.resolve("existing"));
    final RecordingProvider provider = new RecordingProvider();

    assertThrows(IOException.class, () -> new TurboQuantSearchBundleBuilder(provider).build(
        configuration(passages(List.of(passage("one", "text"))),
            Files.writeString(tempDir.resolve("prep"), "x"), output,
            10, 1000, 100, 2, 100, 1000)));
    assertTrue(provider.batches.isEmpty());
  }

  @ParameterizedTest
  @CsvSource({
      "too-large-file, 10, 1, 100",
      "too-many-records, 1, 1000, 100",
      "too-large-query, 10, 1000, 5"
  })
  void rejectsInputAndQueryBoundsBeforeEmbedding(
      String outputName, int maxRecords, long maxInputBytes, int maxQueryBytes) throws Exception {
    final Path preparation = Files.writeString(tempDir.resolve("prep"), "x");
    final RecordingProvider provider = new RecordingProvider();
    final Path records = passages(List.of(passage("one", "123456"), passage("two", "x")));

    assertThrows(IOException.class, () -> new TurboQuantSearchBundleBuilder(provider).build(
        configuration(records, preparation, tempDir.resolve(outputName),
            maxRecords, maxInputBytes, maxQueryBytes, 2, 100, 1000)));
    assertTrue(provider.batches.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r", "\r\n"})
  void countsJsonLineDelimitersBeforeEmbedding(String delimiter) throws Exception {
    final Path records = Files.writeString(tempDir.resolve("delimited.jsonl"),
        jsonPassage("one", "first") + delimiter + jsonPassage("two", "second"));
    final RecordingProvider provider = new RecordingProvider();

    assertThrows(IOException.class, () -> new TurboQuantSearchBundleBuilder(provider).build(
        configuration(records, Files.writeString(tempDir.resolve("prep"), "x"),
            tempDir.resolve("too-many-delimited-records"),
            1, 1000, 100, 1, 100, 10_000)));
    assertTrue(provider.batches.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\r", "\r\n"})
  void doesNotDoubleCountTerminatedJsonLine(String delimiter) throws Exception {
    final Path records = Files.writeString(tempDir.resolve("terminated.jsonl"),
        jsonPassage("one", "first") + delimiter);

    final TurboQuantSearchBundleBuilder.BuildResult result =
        new TurboQuantSearchBundleBuilder(new RecordingProvider()).build(configuration(
            records, Files.writeString(tempDir.resolve("prep"), "x"),
            tempDir.resolve("terminated-record"), 1, 1000, 100, 1, 100, 10_000));

    assertEquals(1, result.recordCount());
  }

  @Test
  void removesStagingDirectoryWhenResolvedRouteChanges() throws Exception {
    final Path passages = passages(List.of(passage("one", "one"), passage("two", "two")));
    final Path preparation = Files.writeString(tempDir.resolve("prep"), "x");
    final RecordingProvider provider = new RecordingProvider();
    provider.changeRoute = true;
    final Path output = tempDir.resolve("route-change");

    assertThrows(IOException.class, () -> new TurboQuantSearchBundleBuilder(provider).build(
        configuration(passages, preparation, output, 10, 1000, 100, 1, 100, 1000)));

    assertFalse(Files.exists(output));
    assertNoStagingDirectories();
  }

  @Test
  void removesInputSnapshotsWhenOutputBoundFails() throws Exception {
    final Path passages = passages(List.of(passage("one", "one")));
    final Path preparation = Files.writeString(tempDir.resolve("prep"), "x");
    final Path output = tempDir.resolve("oversized-output");

    assertThrows(IOException.class, () -> new TurboQuantSearchBundleBuilder(
        new RecordingProvider()).build(configuration(
            passages, preparation, output, 10, 1000, 100, 1, 100, 1)));

    assertFalse(Files.exists(output));
    assertNoStagingDirectories();
  }

  @Test
  void buildsFromSnapshotsWhenInputsChangeDuringEmbedding() throws Exception {
    final CasePassage original = passage("one", "original text");
    final Path passages = passages(List.of(original));
    final Path preparation = Files.writeString(tempDir.resolve("prep"), "normalizer=v1\n");
    final String expectedPreparationHash =
        TurboQuantSearchBundleBuilder.preparationConfigHash(preparation, 3, 42);
    final RecordingProvider provider = new RecordingProvider();
    provider.afterFirstBatch = () -> {
      try {
        CasePassage.writeJsonl(List.of(passage("one", "changed text")), passages);
        Files.writeString(preparation, "normalizer=v2\n");
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    };

    final TurboQuantSearchBundleBuilder.BuildResult result =
        new TurboQuantSearchBundleBuilder(provider).build(configuration(
            passages, preparation, tempDir.resolve("snapshotted"),
            10, 1000, 100, 1, 100, 10_000));

    assertEquals("changed text", CasePassage.readJsonl(passages).getFirst().text());
    assertEquals("original text", CasePassage.readJsonl(result.passagesFile()).getFirst().text());
    assertEquals(expectedPreparationHash, result.preparationConfigHash());
    assertFalse(Files.exists(
        result.outputDirectory().resolve(".preparation-config.snapshot")));
  }

  @Test
  void reportsInvalidSerializedPassageAsCheckedFailureAndRemovesSnapshots() throws Exception {
    final Path passages = Files.writeString(tempDir.resolve("invalid-passages.jsonl"),
        "{\"id\":\"one\",\"case\":\"Case one\",\"cite\":\"\",\"date\":\"\","
            + "\"vol\":\"\",\"text\":\" \"}\n");
    final RecordingProvider provider = new RecordingProvider();
    final Path output = tempDir.resolve("invalid-passages");

    final IOException failure = assertThrows(IOException.class,
        () -> new TurboQuantSearchBundleBuilder(provider).build(configuration(
            passages,
            Files.writeString(tempDir.resolve("prep"), "x"),
            output, 10, 1000, 100, 1, 100, 10_000)));

    assertEquals("Invalid passages input: text must not be blank", failure.getMessage());
    assertTrue(provider.batches.isEmpty());
    assertFalse(Files.exists(output));
    assertNoStagingDirectories();
  }

  @Test
  void writesByteIdenticalDescriptorsForEquivalentBuilds() throws Exception {
    final Path passages = passages(List.of(passage("one", "one")));
    final Path preparation = Files.writeString(tempDir.resolve("prep"), "x");
    final Path first = tempDir.resolve("first");
    final Path second = tempDir.resolve("second");

    new TurboQuantSearchBundleBuilder(new RecordingProvider()).build(
        configuration(passages, preparation, first, 10, 1000, 100, 1, 100, 10_000));
    new TurboQuantSearchBundleBuilder(new RecordingProvider()).build(
        configuration(passages, preparation, second, 10, 1000, 100, 1, 100, 10_000));

    assertEquals(
        Files.readString(first.resolve(TurboQuantSearchBundleLoader.DESCRIPTOR_FILE)),
        Files.readString(second.resolve(TurboQuantSearchBundleLoader.DESCRIPTOR_FILE)));
  }

  @Test
  void preservesUnicodeAndPropertyDelimitersInDescriptorMetadata() throws Exception {
    final Path passages = passages(List.of(passage("one", "one")));
    final Path preparation = Files.writeString(tempDir.resolve("prep"), "x");
    final TurboQuantSearchBundleConfiguration base = configuration(
        passages, preparation, tempDir.resolve("metadata"),
        10, 1000, 100, 1, 100, 10_000);
    final TurboQuantSearchBundleConfiguration configuration =
        new TurboQuantSearchBundleConfiguration(
            base.passagesFile(),
            base.preparationConfigFile(),
            base.outputDirectory(),
            base.indexId(),
            "Café: #1",
            base.modelId(),
            base.backendId(),
            base.bits(),
            base.seed(),
            new TurboQuantSearchBundleConfiguration.CorpusMetadata(
                "Données = cas",
                "Line one\nline two",
                base.corpus().sourceUri(),
                base.corpus().licenseName(),
                base.corpus().licenseUri()),
            base.limits());

    final TurboQuantSearchBundleBuilder.BuildResult result =
        new TurboQuantSearchBundleBuilder(new RecordingProvider()).build(configuration);
    final Properties descriptor = properties(result.outputDirectory());

    assertEquals("Café: #1", descriptor.getProperty("display.name"));
    assertEquals("Données = cas", descriptor.getProperty("corpus.title"));
    assertEquals("Line one\nline two", descriptor.getProperty("corpus.provenance"));
  }

  @Test
  void reportsNullEmbeddingBatchAsCheckedBuildFailure() throws Exception {
    final RecordingProvider provider = new RecordingProvider();
    provider.nullBatch = true;

    final IOException failure = assertThrows(IOException.class,
        () -> new TurboQuantSearchBundleBuilder(provider).build(configuration(
            passages(List.of(passage("one", "one"))),
            Files.writeString(tempDir.resolve("prep"), "x"),
            tempDir.resolve("null-batch"), 10, 1000, 100, 1, 100, 10_000)));

    assertTrue(failure.getMessage().contains("null batch result"));
  }

  @ParameterizedTest
  @CsvSource({"1", "5"})
  void rejectsInvalidPreparationHashBitWidths(int bits) throws Exception {
    final Path preparation = Files.writeString(tempDir.resolve("prep-" + bits), "x");

    final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> TurboQuantSearchBundleBuilder.preparationConfigHash(preparation, bits, 42));

    assertEquals("bits must be from 2 through 4", failure.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"line\nbreak", "line\rbreak"})
  void rejectsPassageIdLineBreaksBeforeEmbedding(String id) throws Exception {
    final RecordingProvider provider = new RecordingProvider();

    final IOException failure = assertThrows(IOException.class,
        () -> new TurboQuantSearchBundleBuilder(provider).build(configuration(
            passages(List.of(passage(id, "text"))),
            Files.writeString(tempDir.resolve("prep"), "x"),
            tempDir.resolve("bad-id"), 10, 1000, 100, 1, 100, 10_000)));

    assertEquals("Passage id must not contain a line break: '" + id + "'",
        failure.getMessage());
    assertTrue(provider.batches.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ascii", "unicode"})
  void rejectsPassageIdsOverUtf8ByteLimitBeforeEmbedding(String kind) throws Exception {
    final String id = "ascii".equals(kind) ? "a".repeat(1_025) : "é".repeat(513);
    final int bytes = id.getBytes(StandardCharsets.UTF_8).length;
    final RecordingProvider provider = new RecordingProvider();

    final IOException failure = assertThrows(IOException.class,
        () -> new TurboQuantSearchBundleBuilder(provider).build(configuration(
            passages(List.of(passage(id, "text"))),
            Files.writeString(tempDir.resolve("prep"), "x"),
            tempDir.resolve("oversized-id"), 10, 10_000, 100, 1, 100, 1_000_000)));

    assertEquals("Passage id uses " + bytes
        + " UTF-8 bytes, exceeding maximum 1024", failure.getMessage());
    assertTrue(provider.batches.isEmpty());
  }

  @Test
  void acceptsPassageIdAtUtf8ByteLimit() throws Exception {
    final String id = "é".repeat(512);
    final RecordingProvider provider = new RecordingProvider();

    final TurboQuantSearchBundleBuilder.BuildResult result =
        new TurboQuantSearchBundleBuilder(provider).build(configuration(
            passages(List.of(passage(id, "text"))),
            Files.writeString(tempDir.resolve("prep"), "x"),
            tempDir.resolve("maximum-id"), 10, 10_000, 100, 1, 100, 1_000_000));

    assertEquals(1, result.recordCount());
    assertFalse(provider.batches.isEmpty());
  }

  private TurboQuantSearchBundleConfiguration configuration(
      Path passages,
      Path preparation,
      Path output,
      int maxRecords,
      long maxInputBytes,
      int maxQueryBytes,
      int batchSize,
      long maxBatchBytes,
      long maxOutputBytes) {
    return new TurboQuantSearchBundleConfiguration(
        passages.toAbsolutePath(),
        preparation.toAbsolutePath(),
        output.toAbsolutePath(),
        "legal",
        "Legal passages",
        REQUESTED_MODEL,
        "",
        3,
        42,
        new TurboQuantSearchBundleConfiguration.CorpusMetadata(
            "Legal cases",
            "Normalized reporter export",
            URI.create("https://example.test/cases"),
            "CC0-1.0",
            URI.create("https://creativecommons.org/publicdomain/zero/1.0/")),
        new TurboQuantSearchBundleConfiguration.Limits(
            maxRecords, maxInputBytes, maxQueryBytes, batchSize, maxBatchBytes, maxOutputBytes));
  }

  private Path passages(List<CasePassage> values) throws IOException {
    final Path file = tempDir.resolve("passages-" + System.nanoTime() + ".jsonl");
    CasePassage.writeJsonl(values, file);
    return file;
  }

  private String jsonPassage(String id, String text) {
    return "{\"id\":\"" + id + "\",\"case\":\"Case " + id
        + "\",\"cite\":\"\",\"date\":\"\",\"vol\":\"\",\"text\":\""
        + text + "\"}";
  }

  private void assertNoStagingDirectories() throws IOException {
    try (var children = Files.list(tempDir)) {
      assertFalse(children.anyMatch(path -> path.getFileName().toString()
          .startsWith(".opennlp-search-bundle-")));
    }
  }

  private CasePassage passage(String id, String text) {
    return new CasePassage(id, "Case " + id, "", "", "", text);
  }

  private Properties properties(Path outputDirectory) throws IOException {
    final Properties result = new Properties();
    try (InputStream input = Files.newInputStream(
        outputDirectory.resolve(TurboQuantSearchBundleLoader.DESCRIPTOR_FILE))) {
      result.load(input);
    }
    return result;
  }

  private final class RecordingProvider implements EmbeddingProvider {

    private final List<List<String>> batches = new ArrayList<>();
    private final List<String> requestedBackends = new ArrayList<>();
    private boolean changeRoute;
    private boolean nullBatch;
    private Runnable afterFirstBatch;

    @Override
    public String backendId() {
      return "aggregate";
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public Set<String> registeredModelIds() {
      return Set.of(REQUESTED_MODEL);
    }

    @Override
    public boolean supportsModel(String modelId) {
      return REQUESTED_MODEL.equals(modelId);
    }

    @Override
    public boolean supportsModel(String modelId, String backendId) {
      return supportsModel(modelId) && "requested-backend".equals(backendId);
    }

    @Override
    public int embeddingDimension(String modelId) {
      return 4;
    }

    @Override
    public float[] embed(String modelId, String text) {
      throw new AssertionError("Builder must use bounded batches");
    }

    @Override
    public EmbeddingBatchResult embedBatchResolved(
        String modelId, String backendId, List<String> texts) {
      requestedBackends.add(backendId);
      batches.add(List.copyOf(texts));
      if (nullBatch) {
        return null;
      }
      final int batch = batches.size();
      if (batch == 1 && afterFirstBatch != null) {
        afterFirstBatch.run();
      }
      final List<float[]> vectors = texts.stream()
          .map(text -> new float[] {text.length(), 1, batch, 0})
          .toList();
      final String resolvedBackend = changeRoute && batch > 1 ? "other-backend" : RESOLVED_BACKEND;
      return new EmbeddingBatchResult(vectors, EmbeddingRoute.newBuilder()
          .setModelId(REQUESTED_MODEL)
          .setBackendId(resolvedBackend)
          .setVectorSpaceId(VECTOR_SPACE)
          .setArtifactHash("a".repeat(64))
          .setPrimary(true)
          .build());
    }
  }
}
