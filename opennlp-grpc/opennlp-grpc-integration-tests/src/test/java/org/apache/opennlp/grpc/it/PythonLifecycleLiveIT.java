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
package org.apache.opennlp.grpc.it;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import io.grpc.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Cross-language end-to-end test: a Python client drives the complete training
 * lifecycle against the deployable server using only the shipped
 * FileDescriptorSet, proving the wire contract needs no generated code.
 *
 * <p>Opt-in because it needs the {@code uv} launcher on the PATH: set
 * {@code OPENNLP_PYTHON_E2E=1}. The Python script imports a dictionary, learns
 * a vocabulary, analyzes and indexes identified documents, aliases the
 * workspace, scopes it into a collection, reads drift and persistence events
 * from the watch stream, rebuilds blue/green with an alias swap, runs a
 * compound query with matched spans, seals, and cleans up. Setting
 * {@code OPENNLP_E2E_TEACHER_REF} to a teacher model reference additionally
 * distills a static model and reindexes into its vector space.</p>
 */
class PythonLifecycleLiveIT {

  private static final String DESCRIPTOR_RESOURCE =
      "META-INF/opennlp/descriptors/opennlp-grpc-v1.protobin";

  private static Server teiServer;
  private static LiveServerHarness harness;

  @BeforeAll
  static void startTopology() throws Exception {
    assumeTrue(System.getenv("OPENNLP_PYTHON_E2E") != null,
        "Set OPENNLP_PYTHON_E2E=1 (and install uv) to run the Python lifecycle e2e");
    teiServer = StubTeiBackend.start();

    final Properties config = new Properties();
    config.setProperty("model.embedder.minilm.tei.target", "localhost:" + teiServer.getPort());
    config.setProperty("model.embedder.minilm.tei.vector_space_id", "minilm-live-v1");
    config.setProperty("model.embedder.tei.deadline_ms", "10000");
    config.setProperty("vocabulary.artifact_root",
        Files.createTempDirectory("opennlp-grpc-e2e-artifacts-").toString());
    config.setProperty("search.persist.root",
        Files.createTempDirectory("opennlp-grpc-e2e-persist-").toString());
    final String teacherRef = System.getenv("OPENNLP_E2E_TEACHER_REF");
    if (teacherRef != null && !teacherRef.isBlank()) {
      config.setProperty("training.teacher.e2e.ref", teacherRef.trim());
      config.setProperty("training.teacher.e2e.display_name", "E2E teacher");
    }
    harness = LiveServerHarness.start(config);
  }

  @AfterAll
  static void stopTopology() throws Exception {
    if (harness != null) {
      harness.close();
    }
    if (teiServer != null) {
      teiServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void pythonClientDrivesTheFullLifecycleFromShippedDescriptors() throws Exception {
    final Path script = Path.of("scripts", "lifecycle_e2e.py").toAbsolutePath();
    assertTrue(Files.isRegularFile(script), "missing " + script);

    final List<String> command = new ArrayList<>(List.of(
        "uv", "run", script.toString(),
        "--target", harness.grpcTarget(),
        "--descriptors", extractShippedDescriptors().toString()));
    if (System.getenv("OPENNLP_E2E_TEACHER_REF") != null) {
      command.add("--teacher-id");
      command.add("e2e");
    }

    final Path log = Files.createTempFile("opennlp-grpc-python-e2e-", ".log");
    final Process process = new ProcessBuilder(command)
        .redirectErrorStream(true)
        .redirectOutput(log.toFile())
        .start();
    // The first run also resolves the script's pinned dependencies through uv.
    assertTrue(process.waitFor(15, TimeUnit.MINUTES),
        "Python e2e timed out; log:\n" + Files.readString(log));
    assertEquals(0, process.exitValue(),
        "Python e2e failed; log:\n" + Files.readString(log));
  }

  /**
   * Extracts the FileDescriptorSet the shaded server jar ships, so the Python
   * client consumes exactly the deployed contract.
   *
   * @return Path of the extracted descriptor set.
   * @throws IOException If the jar or resource cannot be read.
   */
  private static Path extractShippedDescriptors() throws IOException {
    final String serverJar = System.getProperty("opennlp.grpc.server.jar");
    try (JarFile jar = new JarFile(serverJar)) {
      final ZipEntry entry = jar.getEntry(DESCRIPTOR_RESOURCE);
      assertTrue(entry != null, "server jar lacks " + DESCRIPTOR_RESOURCE);
      final Path descriptors = Files.createTempFile("opennlp-grpc-v1-", ".protobin");
      try (InputStream input = jar.getInputStream(entry)) {
        Files.copy(input, descriptors, StandardCopyOption.REPLACE_EXISTING);
      }
      return descriptors;
    }
  }
}
