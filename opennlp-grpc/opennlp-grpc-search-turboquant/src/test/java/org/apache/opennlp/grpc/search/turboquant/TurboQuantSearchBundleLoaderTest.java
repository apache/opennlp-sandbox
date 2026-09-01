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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import opennlp.embeddings.corpus.CasePassage;
import opennlp.embeddings.index.TurboQuantIndex;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.StandardSearchProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.opennlp.grpc.spi.search.SearchIndexProvider;
import org.apache.opennlp.grpc.spi.search.SearchIndexBundleConfiguration;
import org.apache.opennlp.grpc.spi.search.SearchResult;
import org.apache.opennlp.grpc.spi.search.SearchRecord;

class TurboQuantSearchBundleLoaderTest {

  @TempDir
  Path tempDir;

  @Test
  void loadsIndexAndMapsEveryIdToCasePassageProvenance() throws Exception {
    final Path indexDir = index(List.of("p-one", "p-two"));
    final Path passages = passages(List.of(
        new CasePassage("p-two", "Emoji v. Text", "2 U.S. 2", "1902", "2",
            "Due process protects café 😀."),
        new CasePassage("p-one", "Alpha v. Beta", "1 U.S. 1", "1901", "1",
            "Equal protection applies.")));
    writeDescriptor(indexDir, properties());

    final SearchIndexProvider provider = load(
        new SearchIndexBundleConfiguration("legal", indexDir, passages));
    final SearchRecord emoji = provider.search(new float[] {0, 1, 0, 0}, 2).stream()
        .map(SearchResult::record)
        .filter(record -> record.chunkId().equals("p-two"))
        .findFirst().orElseThrow();

    assertEquals("legal", provider.descriptor().getIndexId());
    assertEquals(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT,
        provider.descriptor().getProvider().getStandard());
    assertEquals(2, provider.descriptor().getSize());
    assertEquals(SearchIndexBundleConfiguration.DEFAULT_MAX_TOP_K,
        provider.descriptor().getMaxTopK());
    assertEquals("p-two", emoji.documentId());
    assertEquals("p-two", emoji.chunkId());
    assertEquals(OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE,
        emoji.sourceDocument().getOffsetEncoding());
    assertEquals(emoji.sourceDocument().getRawText().getBytes(StandardCharsets.UTF_8).length,
        emoji.sourceSpan().getEnd());
    assertEquals("Emoji v. Text",
        emoji.sourceDocument().getMetadata().getFieldsOrThrow("case_name").getStringValue());
    assertEquals("2 U.S. 2",
        emoji.sourceDocument().getMetadata().getFieldsOrThrow("citation").getStringValue());
  }

  @Test
  void advertisesExhaustiveSearchIndependentlyOfTheOrdinaryTopKLimit() throws Exception {
    final Path indexDir = index(List.of("p-one", "p-two"));
    final Path passageFile = passages(List.of(passage("p-one"), passage("p-two")));
    writeDescriptor(indexDir, properties());
    final SearchIndexBundleConfiguration configuration = new SearchIndexBundleConfiguration(
        "legal", indexDir, passageFile, 1,
        SearchIndexBundleConfiguration.DEFAULT_MAX_QUERY_BYTES,
        SearchIndexBundleConfiguration.DEFAULT_MAX_RESPONSE_BYTES,
        2, SearchIndexBundleConfiguration.DEFAULT_MAX_SOURCE_DOCUMENT_BYTES,
        SearchIndexBundleConfiguration.DEFAULT_MAX_INDEXED_TEXT_BYTES,
        SearchIndexBundleConfiguration.DEFAULT_MAX_BUNDLE_BYTES, Map.of());

    final SearchIndexProvider provider = load(configuration);

    assertEquals(1, provider.descriptor().getMaxTopK());
    assertTrue(provider.descriptor().getSupportsAllHits());
  }

  @Test
  void rejectsUnknownBundleVersion() throws Exception {
    final Path indexDir = index(List.of("p-one"));
    final Properties descriptor = properties();
    descriptor.setProperty("format.version", "2");
    writeDescriptor(indexDir, descriptor);

    final IOException exception = assertThrows(IOException.class,
        () -> load(new SearchIndexBundleConfiguration(
            "legal", indexDir, passages(List.of(passage("p-one"))))));
    assertTrue(exception.getMessage().contains("format.version"));
  }

  @Test
  void rejectsMissingAndExtraPassageIds() throws Exception {
    final Path indexDir = index(List.of("p-one", "p-two"));
    writeDescriptor(indexDir, properties());

    final IOException exception = assertThrows(IOException.class,
        () -> load(new SearchIndexBundleConfiguration(
            "legal", indexDir,
            passages(List.of(passage("p-one"), passage("p-extra"))))));
    assertTrue(exception.getMessage().contains("p-two"));
    assertTrue(exception.getMessage().contains("p-extra"));
  }

  @Test
  void rejectsDuplicatePassageIds() throws Exception {
    final Path indexDir = index(List.of("p-one"));
    writeDescriptor(indexDir, properties());

    final IOException exception = assertThrows(IOException.class,
        () -> load(new SearchIndexBundleConfiguration(
            "legal", indexDir,
            passages(List.of(passage("p-one"), passage("p-one"))))));
    assertTrue(exception.getMessage().contains("more than once"));
  }

  @Test
  void rejectsDuplicateTurboQuantIndexIds() throws Exception {
    final Path indexDir = index(List.of("p-one"));
    Files.write(indexDir.resolve(TurboQuantIndex.IDS_FILE), List.of("p-one", "p-one"));
    final Path passages = passages(List.of(passage("p-one")));
    writeDescriptor(indexDir, properties());

    final IOException exception = assertThrows(IOException.class,
        () -> load(new SearchIndexBundleConfiguration("legal", indexDir, passages)));

    assertTrue(exception.getMessage().contains("p-one"));
    assertTrue(exception.getMessage().contains("more than once"));
  }

  @Test
  void rejectsDimensionMismatch() throws Exception {
    final Path indexDir = index(List.of("p-one"));
    final Properties descriptor = properties();
    descriptor.setProperty("dimension", "3");
    writeDescriptor(indexDir, descriptor);

    final IOException exception = assertThrows(IOException.class,
        () -> load(new SearchIndexBundleConfiguration(
            "legal", indexDir, passages(List.of(passage("p-one"))))));
    assertTrue(exception.getMessage().contains("dimension"));
  }

  @Test
  void rejectsMalformedUtf8Passages() throws Exception {
    final Path indexDir = index(List.of("p-one"));
    writeDescriptor(indexDir, properties());
    final Path passages = tempDir.resolve("malformed.jsonl");
    Files.write(passages, new byte[] {(byte) 0xc3, (byte) 0x28});

    assertThrows(IOException.class, () -> load(
        new SearchIndexBundleConfiguration("legal", indexDir, passages)));
  }

  @Test
  void rejectsConfiguredRecordAndTextBounds() throws Exception {
    final Path twoRecordIndex = index(List.of("p-one", "p-two"));
    writeDescriptor(twoRecordIndex, properties());
    final Path twoPassages = passages(List.of(passage("p-one"), passage("p-two")));
    final SearchIndexBundleConfiguration oneRecord = configuration(
        twoRecordIndex, twoPassages, 1, 1_000, 1_000);
    assertThrows(IOException.class,
        () -> load(oneRecord));

    final Path oneRecordIndex = index(List.of("p-long"));
    writeDescriptor(oneRecordIndex, properties());
    final Path onePassage = passages(List.of(new CasePassage(
        "p-long", "Case", "", "", "", "five!")));
    final SearchIndexBundleConfiguration sourceBound =
        configuration(oneRecordIndex, onePassage, 10, 4, 1_000);
    final SearchIndexBundleConfiguration indexedTextBound =
        configuration(oneRecordIndex, onePassage, 10, 1_000, 4);
    assertEquals(4, sourceBound.maxSourceDocumentBytes());
    assertEquals(4, indexedTextBound.maxIndexedTextBytes());
    assertThrows(IOException.class,
        () -> load(sourceBound));
    assertThrows(IOException.class,
        () -> load(indexedTextBound));
  }

  @Test
  void rejectsOversizedIdLinesBeforeReadingTheTurboQuantIndex() throws Exception {
    final Path indexDir = index(List.of("p-one"));
    writeDescriptor(indexDir, properties());
    Files.writeString(indexDir.resolve(TurboQuantIndex.IDS_FILE), "x".repeat(1_025));
    Files.write(indexDir.resolve(TurboQuantIndex.VECTORS_FILE), new byte[] {1, 2, 3});

    final IOException exception = assertThrows(IOException.class,
        () -> load(new SearchIndexBundleConfiguration(
            "legal", indexDir, passages(List.of(passage("p-one"))))));

    assertTrue(exception.getMessage().contains("line"));
    assertTrue(exception.getMessage().contains("1024"));
  }

  @Test
  void rejectsBlankIdLinesBeforeReadingTheTurboQuantIndex() throws Exception {
    final Path indexDir = index(List.of("p-one"));
    writeDescriptor(indexDir, properties());
    Files.writeString(indexDir.resolve(TurboQuantIndex.IDS_FILE), "p-one\n\n");
    Files.write(indexDir.resolve(TurboQuantIndex.VECTORS_FILE), new byte[] {1, 2, 3});

    final IOException exception = assertThrows(IOException.class,
        () -> load(new SearchIndexBundleConfiguration(
            "legal", indexDir, passages(List.of(passage("p-one"))))));

    assertTrue(exception.getMessage().contains("blank id line"));
  }

  @Test
  void rejectsOversizedPassageLinesBeforeAllocatingCasePassages() throws Exception {
    final Path indexDir = index(List.of("p-one"));
    writeDescriptor(indexDir, properties());
    final Path passages = passages(List.of(new CasePassage(
        "p-one", "Case", "", "", "", "x".repeat(300_000))));
    Files.write(indexDir.resolve(TurboQuantIndex.VECTORS_FILE), new byte[] {1, 2, 3});
    final SearchIndexBundleConfiguration bounded = configuration(
        indexDir, passages, 1, 5, 5);

    final IOException exception = assertThrows(IOException.class,
        () -> load(bounded));

    assertTrue(exception.getMessage().contains("line"));
    assertTrue(exception.getMessage().contains("source document"));
  }

  @Test
  void rejectsFalseCorpusAndBundleArtifactHashes() throws Exception {
    final Path indexDir = index(List.of("p-one"));
    final Path passages = passages(List.of(passage("p-one")));
    writeDescriptor(indexDir, properties());
    final SearchIndexBundleConfiguration configuration =
        new SearchIndexBundleConfiguration("legal", indexDir, passages);
    updateDescriptorHashes(configuration);

    Properties descriptor = readDescriptor(indexDir);
    descriptor.setProperty("corpus.artifact.sha256", "0".repeat(64));
    writeDescriptor(indexDir, descriptor);
    final IOException corpusFailure = assertThrows(IOException.class,
        () -> new TurboQuantSearchBundleLoader().load(configuration));
    assertTrue(corpusFailure.getMessage().contains("corpus.artifact.sha256"));

    updateDescriptorHashes(configuration);
    descriptor = readDescriptor(indexDir);
    descriptor.setProperty("bundle.artifact.sha256", "0".repeat(64));
    writeDescriptor(indexDir, descriptor);
    final IOException bundleFailure = assertThrows(IOException.class,
        () -> new TurboQuantSearchBundleLoader().load(configuration));
    assertTrue(bundleFailure.getMessage().contains("bundle.artifact.sha256"));
  }

  @Test
  void rejectsRelativeAndTraversalConfigurationPaths() {
    assertThrows(IllegalArgumentException.class,
        () -> new SearchIndexBundleConfiguration(
            "legal", Path.of("relative"), tempDir.resolve("passages.jsonl")));
    assertThrows(IllegalArgumentException.class,
        () -> new SearchIndexBundleConfiguration(
            "legal", tempDir.resolve("index/../escape"), tempDir.resolve("passages.jsonl")));
    assertThrows(IllegalArgumentException.class,
        () -> new SearchIndexBundleConfiguration(
            "legal", tempDir, tempDir.resolve("passages.jsonl"),
            SearchIndexBundleConfiguration.MAX_TOP_K_LIMIT + 1,
            1, 1, 1, 1, 1, 1, Map.of()));
    assertThrows(IllegalArgumentException.class,
        () -> new SearchIndexBundleConfiguration(
            "legal", tempDir, tempDir.resolve("passages.jsonl"),
            1, 1, 1, SearchIndexBundleConfiguration.MAX_RECORDS_LIMIT + 1,
            1, 1, 1, Map.of()));
  }

  private Path index(List<String> ids) throws IOException {
    final TurboQuantIndex index = new TurboQuantIndex(4, 4, 42);
    for (int i = 0; i < ids.size(); i++) {
      final float[] vector = new float[4];
      vector[i % vector.length] = 1;
      index.add(ids.get(i), vector);
    }
    index.freeze();
    final Path directory = tempDir.resolve("index-" + System.nanoTime());
    index.write(directory);
    return directory;
  }

  private Path passages(List<CasePassage> values) throws IOException {
    final Path file = tempDir.resolve("passages-" + System.nanoTime() + ".jsonl");
    CasePassage.writeJsonl(values, file);
    return file;
  }

  private static CasePassage passage(String id) {
    return new CasePassage(id, "Case " + id, "", "", "", "Passage " + id);
  }

  private SearchIndexProvider load(SearchIndexBundleConfiguration configuration)
      throws IOException {
    updateDescriptorHashes(configuration);
    return new TurboQuantSearchBundleLoader().load(configuration);
  }

  private static void updateDescriptorHashes(SearchIndexBundleConfiguration configuration)
      throws IOException {
    final Properties descriptor = readDescriptor(configuration.indexDirectory());
    descriptor.setProperty("corpus.artifact.sha256",
        TurboQuantBundleDigest.sha256(configuration.passagesFile()));
    descriptor.setProperty("bundle.artifact.sha256",
        TurboQuantBundleDigest.bundleArtifactHash(
            configuration.indexDirectory(), configuration.passagesFile()));
    writeDescriptor(configuration.indexDirectory(), descriptor);
  }

  private static Properties readDescriptor(Path indexDir) throws IOException {
    final Properties descriptor = new Properties();
    try (InputStream input = Files.newInputStream(
        indexDir.resolve(TurboQuantSearchBundleLoader.DESCRIPTOR_FILE))) {
      descriptor.load(input);
    }
    return descriptor;
  }

  private static SearchIndexBundleConfiguration configuration(
      Path indexDir, Path passages, int maxRecords, int maxSourceBytes, int maxIndexedTextBytes) {
    return new SearchIndexBundleConfiguration(
        "legal",
        indexDir,
        passages,
        SearchIndexBundleConfiguration.DEFAULT_MAX_TOP_K,
        SearchIndexBundleConfiguration.DEFAULT_MAX_QUERY_BYTES,
        SearchIndexBundleConfiguration.DEFAULT_MAX_RESPONSE_BYTES,
        maxRecords,
        maxSourceBytes,
        maxIndexedTextBytes,
        SearchIndexBundleConfiguration.DEFAULT_MAX_BUNDLE_BYTES,
        Map.of());
  }

  private static Properties properties() {
    final Properties properties = new Properties();
    properties.setProperty("format.version", "1");
    properties.setProperty("index.id", "legal");
    properties.setProperty("display.name", "Legal passages");
    properties.setProperty("provider.id", "turbo_quant");
    properties.setProperty("embedding.model.id", "mini");
    properties.setProperty("embedding.backend.id", "static");
    properties.setProperty("embedding.vector_space.id", "mini-v1");
    properties.setProperty("embedding.artifact.sha256", "a".repeat(64));
    properties.setProperty("dimension", "4");
    properties.setProperty("metric", "cosine");
    properties.setProperty("corpus.title", "Legal cases");
    properties.setProperty("corpus.provenance", "Normalized test reporter");
    properties.setProperty("corpus.source.uri", "https://example.test/corpus");
    properties.setProperty("corpus.license.name", "CC0-1.0");
    properties.setProperty("corpus.license.uri", "https://creativecommons.org/publicdomain/zero/1.0/");
    properties.setProperty("corpus.artifact.sha256", "b".repeat(64));
    properties.setProperty("bundle.artifact.sha256", "c".repeat(64));
    properties.setProperty("builder.id", "opennlp-test-builder");
    properties.setProperty("builder.version", "1");
    properties.setProperty("preparation.config.sha256", "d".repeat(64));
    return properties;
  }

  private static void writeDescriptor(Path indexDir, Properties descriptor) throws IOException {
    try (OutputStream output = Files.newOutputStream(
        indexDir.resolve(TurboQuantSearchBundleLoader.DESCRIPTOR_FILE))) {
      descriptor.store(output, null);
    }
  }
}
