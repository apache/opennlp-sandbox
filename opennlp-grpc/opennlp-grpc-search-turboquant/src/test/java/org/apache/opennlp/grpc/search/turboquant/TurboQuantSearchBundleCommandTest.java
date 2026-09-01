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
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import opennlp.embeddings.corpus.CasePassage;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingBatchResult;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurboQuantSearchBundleCommandTest {

  private static final String VECTOR_SPACE = "space-v1";

  @TempDir
  Path tempDir;

  @Test
  void buildsFromExistingServerConfigurationAndClosesProvider() throws Exception {
    final Path serverConfig = Files.writeString(tempDir.resolve("server.properties"),
        "model.embedder.demo.static.vector_space_id=" + VECTOR_SPACE + "\n");
    final Path preparation = Files.writeString(tempDir.resolve("preparation.properties"),
        "normalizer=legal-v1\n");
    final Path passages = passages();
    final Path output = tempDir.resolve("bundle");
    final AtomicReference<Map<String, String>> seenConfiguration = new AtomicReference<>();
    final TestProvider provider = new TestProvider();
    final CommandLine command = new CommandLine(new TurboQuantSearchBundleCommand(configuration -> {
      seenConfiguration.set(configuration);
      return provider;
    }));

    final int exit = command.execute(arguments(serverConfig, preparation, passages, output));

    assertEquals(0, exit);
    assertEquals(VECTOR_SPACE, seenConfiguration.get().get(
        "model.embedder.demo.static.vector_space_id"));
    assertTrue(provider.closed);
    assertTrue(Files.isRegularFile(output.resolve("search-index.properties")));
    assertTrue(Files.isRegularFile(output.resolve("vectors.onq")));
    assertTrue(Files.isRegularFile(output.resolve("ids.txt")));
    assertTrue(Files.isRegularFile(output.resolve(TurboQuantSearchBundleBuilder.PASSAGES_FILE)));
  }

  @Test
  void refusesExistingOutputBeforeLoadingEmbeddingBackends() throws Exception {
    final Path output = Files.createDirectory(tempDir.resolve("existing"));
    final AtomicBoolean providerLoaded = new AtomicBoolean();
    final CommandLine command = new CommandLine(new TurboQuantSearchBundleCommand(configuration -> {
      providerLoaded.set(true);
      return new TestProvider();
    }));
    command.setErr(new java.io.PrintWriter(new StringWriter()));

    final int exit = command.execute(arguments(
        Files.writeString(tempDir.resolve("server.properties"), "x=y\n"),
        Files.writeString(tempDir.resolve("preparation.properties"), "x=y\n"),
        passages(),
        output));

    assertEquals(1, exit);
    assertFalse(providerLoaded.get());
  }

  @Test
  void preservesBuildFailureWhenProviderCloseAlsoFails() throws Exception {
    final TestProvider provider = new TestProvider();
    provider.failClose = true;
    provider.nullBatch = true;
    final TurboQuantSearchBundleCommand command =
        new TurboQuantSearchBundleCommand(configuration -> provider);
    final CommandLine commandLine = new CommandLine(command);
    commandLine.parseArgs(arguments(
        Files.writeString(tempDir.resolve("server.properties"), "x=y\n"),
        Files.writeString(tempDir.resolve("preparation.properties"), "x=y\n"),
        passages(),
        tempDir.resolve("bundle")));

    final IOException failure = assertThrows(IOException.class, command::call);

    assertTrue(failure.getMessage().contains("null batch result"));
    assertEquals(1, failure.getSuppressed().length);
    assertEquals("close failed", failure.getSuppressed()[0].getMessage());
  }

  private Path passages() throws IOException {
    final Path file = tempDir.resolve("passages.jsonl");
    CasePassage.writeJsonl(List.of(
        new CasePassage("p-one", "Case one", "", "", "", "First passage")), file);
    return file;
  }

  private String[] arguments(
      Path serverConfig, Path preparation, Path passages, Path output) {
    return new String[] {
        "--server-config", serverConfig.toString(),
        "--passages", passages.toString(),
        "--preparation-config", preparation.toString(),
        "--output-dir", output.toString(),
        "--index-id", "legal",
        "--display-name", "Legal passages",
        "--model-id", "demo",
        "--corpus-title", "Legal cases",
        "--corpus-provenance", "Normalized reporter export",
        "--corpus-source-uri", "https://example.test/cases",
        "--license-name", "CC0-1.0",
        "--license-uri", "https://creativecommons.org/publicdomain/zero/1.0/"
    };
  }

  private final class TestProvider implements EmbeddingProvider, AutoCloseable {

    private boolean closed;
    private boolean failClose;
    private boolean nullBatch;

    @Override
    public String backendId() {
      return "static";
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public Set<String> registeredModelIds() {
      return Set.of("demo");
    }

    @Override
    public boolean supportsModel(String modelId) {
      return "demo".equals(modelId);
    }

    @Override
    public int embeddingDimension(String modelId) {
      return 4;
    }

    @Override
    public float[] embed(String modelId, String text) {
      return new float[] {1, 0, 0, 0};
    }

    @Override
    public EmbeddingBatchResult embedBatchResolved(
        String modelId, String backendId, List<String> texts) {
      if (nullBatch) {
        return null;
      }
      return new EmbeddingBatchResult(
          texts.stream().map(text -> embed(modelId, text)).toList(),
          EmbeddingRoute.newBuilder()
              .setModelId(modelId)
              .setBackendId("static")
              .setVectorSpaceId(VECTOR_SPACE)
              .setArtifactHash("e".repeat(64))
              .setPrimary(true)
              .build());
    }

    @Override
    public void close() throws IOException {
      closed = true;
      if (failClose) {
        throw new IOException("close failed");
      }
    }
  }
}
