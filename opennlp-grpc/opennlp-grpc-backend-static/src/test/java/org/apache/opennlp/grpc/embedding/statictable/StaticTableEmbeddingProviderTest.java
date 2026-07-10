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
package org.apache.opennlp.grpc.embedding.statictable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.opennlp.grpc.embedding.EmbeddingBackendFactory;
import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticTableEmbeddingProviderTest {

  // Fixture vocabulary: [CLS]=0, [SEP]=1, [UNK]=2, hello=3, world=4, cat=5. Row i is
  // [i, i*10, i*100], so expected pooled vectors are hand-computable: "hello world" with
  // normalize=false mean-pools rows 3 and 4 to [3.5, 35, 350].
  private static final List<String> VOCAB_TOKENS =
      List.of("[CLS]", "[SEP]", "[UNK]", "hello", "world", "cat");
  private static final int DIMENSION = 3;

  private static void writeModelFiles(Path dir) throws IOException {
    Files.write(dir.resolve("vocab.txt"), VOCAB_TOKENS);

    final ByteBuffer buffer = ByteBuffer
        .allocate(VOCAB_TOKENS.size() * DIMENSION * Float.BYTES)
        .order(ByteOrder.LITTLE_ENDIAN);
    for (int row = 0; row < VOCAB_TOKENS.size(); row++) {
      for (int d = 0; d < DIMENSION; d++) {
        buffer.putFloat(row * (float) Math.pow(10, d));
      }
    }
    final byte[] data = buffer.array();
    final String header = "{\"embeddings\":{\"dtype\":\"F32\",\"shape\":["
        + VOCAB_TOKENS.size() + "," + DIMENSION + "],\"data_offsets\":[0," + data.length + "]}}";
    final byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        .putLong(headerBytes.length).array());
    out.write(headerBytes);
    out.write(data);
    Files.write(dir.resolve("model.safetensors"), out.toByteArray());
  }

  private static void writeModelDirectory(Path dir) throws IOException {
    writeModelFiles(dir);
    Files.writeString(dir.resolve("config.json"),
        "{\"model_type\":\"model2vec\",\"normalize\":false,\"hidden_dim\":" + DIMENSION + "}");
    Files.writeString(dir.resolve("tokenizer_config.json"),
        "{\"do_lower_case\":true,\"strip_accents\":null,\"tokenizer_class\":\"BertTokenizer\"}");
  }

  @Test
  void testLoadsFromAModelDirectory(@TempDir Path dir) throws IOException {
    writeModelDirectory(dir);

    final StaticTableEmbeddingProvider provider = new StaticTableEmbeddingProvider(
        Map.of("model.embedder.potion.static.dir", dir.toString()));

    assertTrue(provider.isAvailable());
    assertEquals(Set.of("potion"), provider.registeredModelIds());
    assertTrue(provider.supportsModel("potion"));
    assertEquals(DIMENSION, provider.embeddingDimension("potion"));
    // Upper-cased input proves do_lower_case was read from tokenizer_config.json.
    assertArrayEquals(new float[] {3.5f, 35f, 350f},
        provider.embed("potion", "HELLO WORLD"), 1e-5f);
    assertEquals(64, provider.modelArtifactHash("potion").length());
    assertEquals("static", provider.backendId());
  }

  @Test
  void testLoadsFromExplicitFiles(@TempDir Path dir) throws IOException {
    writeModelFiles(dir);

    final StaticTableEmbeddingProvider provider = new StaticTableEmbeddingProvider(Map.of(
        "model.embedder.m.static.safetensors.path", dir.resolve("model.safetensors").toString(),
        "model.embedder.m.static.vocab.path", dir.resolve("vocab.txt").toString(),
        "model.embedder.m.static.lowercase", "true",
        "model.embedder.m.static.normalize", "false"));

    assertArrayEquals(new float[] {3.5f, 35f, 350f}, provider.embed("m", "hello world"), 1e-5f);
  }

  @Test
  void testEmbedBatchMatchesEmbed(@TempDir Path dir) throws IOException {
    writeModelDirectory(dir);
    final StaticTableEmbeddingProvider provider = new StaticTableEmbeddingProvider(
        Map.of("model.embedder.potion.static.dir", dir.toString()));

    final List<float[]> vectors =
        provider.embedBatch("potion", List.of("hello world", "cat"));

    assertEquals(2, vectors.size());
    assertArrayEquals(provider.embed("potion", "hello world"), vectors.get(0));
    assertArrayEquals(provider.embed("potion", "cat"), vectors.get(1));
  }

  @Test
  void testRejectsMixingTheTwoForms(@TempDir Path dir) throws IOException {
    writeModelDirectory(dir);

    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> new StaticTableEmbeddingProvider(Map.of(
            "model.embedder.m.static.dir", dir.toString(),
            "model.embedder.m.static.vocab.path", dir.resolve("vocab.txt").toString())));
    assertTrue(e.getMessage().contains("exactly one of the two forms"));
  }

  @Test
  void testRejectsSwitchKeysNextToTheDirectoryForm(@TempDir Path dir) throws IOException {
    writeModelDirectory(dir);

    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> new StaticTableEmbeddingProvider(Map.of(
            "model.embedder.m.static.dir", dir.toString(),
            "model.embedder.m.static.lowercase", "true")));
    assertTrue(e.getMessage().contains("model's own configuration files"));
  }

  @Test
  void testRejectsAnIncompleteModelSource(@TempDir Path dir) throws IOException {
    writeModelFiles(dir);

    // A vocab path without a safetensors path is a typo, not a model; it must fail loud.
    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> new StaticTableEmbeddingProvider(Map.of(
            "model.embedder.m.static.vocab.path", dir.resolve("vocab.txt").toString())));
    assertTrue(e.getMessage().contains("complete model source"));
  }

  @Test
  void testRejectsAMissingDirectoryAtStartup(@TempDir Path dir) {
    assertThrows(AnalysisException.class, () -> new StaticTableEmbeddingProvider(
        Map.of("model.embedder.m.static.dir", dir.resolve("absent").toString())));
  }

  @Test
  void testRejectsAMalformedBooleanKey(@TempDir Path dir) throws IOException {
    writeModelFiles(dir);

    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> new StaticTableEmbeddingProvider(Map.of(
            "model.embedder.m.static.safetensors.path", dir.resolve("model.safetensors").toString(),
            "model.embedder.m.static.vocab.path", dir.resolve("vocab.txt").toString(),
            "model.embedder.m.static.normalize", "yes")));
    assertTrue(e.getMessage().contains("'true' or 'false'"));
  }

  @Test
  void testUnknownModelIdFailsOnEmbed(@TempDir Path dir) throws IOException {
    writeModelDirectory(dir);
    final StaticTableEmbeddingProvider provider = new StaticTableEmbeddingProvider(
        Map.of("model.embedder.potion.static.dir", dir.toString()));

    assertThrows(AnalysisException.class, () -> provider.embed("missing", "text"));
  }

  @Test
  void testDefaultIdOfThisEngineResolves(@TempDir Path dir) throws IOException {
    final Path a = Files.createDirectory(dir.resolve("a"));
    final Path b = Files.createDirectory(dir.resolve("b"));
    writeModelDirectory(a);
    writeModelDirectory(b);

    final StaticTableEmbeddingProvider provider = new StaticTableEmbeddingProvider(Map.of(
        "model.embedder.first.static.dir", a.toString(),
        "model.embedder.second.static.dir", b.toString(),
        "model.embedder.default_id", "second"));

    assertEquals("second", provider.resolveModelId(null));
    assertEquals("first", provider.resolveModelId("first"));
  }

  @Test
  void testForeignDefaultIdIsIgnoredNotRejected(@TempDir Path dir) throws IOException {
    // The composite provider validates default_id against the union of all engines; an
    // engine-level provider must not crash the server because the default names a model
    // served by a different engine.
    final Path a = Files.createDirectory(dir.resolve("a"));
    final Path b = Files.createDirectory(dir.resolve("b"));
    writeModelDirectory(a);
    writeModelDirectory(b);

    final StaticTableEmbeddingProvider provider = new StaticTableEmbeddingProvider(Map.of(
        "model.embedder.first.static.dir", a.toString(),
        "model.embedder.second.static.dir", b.toString(),
        "model.embedder.default_id", "some-onnx-model"));

    assertNull(provider.resolveModelId(null));
  }

  @Test
  void testNoModelsConfiguredIsUnavailableNotAnError() {
    final StaticTableEmbeddingProvider provider =
        new StaticTableEmbeddingProvider(Map.of("model.embedder.other.onnx.path", "/x.onnx"));

    assertTrue(provider.registeredModelIds().isEmpty());
    assertEquals(false, provider.isAvailable());
  }

  @Test
  void testFactoryIsDiscoverableViaServiceLoader(@TempDir Path dir) throws IOException {
    writeModelDirectory(dir);

    EmbeddingBackendFactory found = null;
    for (EmbeddingBackendFactory factory : ServiceLoader.load(EmbeddingBackendFactory.class)) {
      if ("static".equals(factory.backendId())) {
        found = factory;
      }
    }

    assertTrue(found != null, "static backend factory not discovered via ServiceLoader");
    final EmbeddingProvider provider =
        found.create(Map.of("model.embedder.potion.static.dir", dir.toString()));
    assertTrue(provider.isAvailable());
    assertEquals("static", provider.backendId());
  }
}
