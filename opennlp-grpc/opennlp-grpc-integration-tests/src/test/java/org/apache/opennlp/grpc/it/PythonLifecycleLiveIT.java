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

import java.io.File;
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
 * Cross-language end-to-end tests for standard Python clients against the
 * deployable server.
 *
 * <p>Opt-in because it needs the {@code uv} launcher on the PATH: set
 * {@code OPENNLP_PYTHON_E2E=1}. The descriptor-driven lifecycle client imports
 * a dictionary, learns a vocabulary, analyzes and indexes identified documents,
 * aliases the workspace, scopes it into a collection, reads drift and persistence events
 * from the watch stream, rebuilds blue/green with an alias swap, runs a
 * compound query with matched spans, seals, and cleans up. Setting
 * {@code OPENNLP_E2E_TEACHER_REF} to a teacher model reference additionally
 * distills a static model and reindexes into its vector space. A shorter
 * generated-stub client proves the documented newcomer path through analysis,
 * process-local TurboQuant indexing, and exhaustive server-side search.</p>
 */
class PythonLifecycleLiveIT {

  private static final String DESCRIPTOR_RESOURCE =
      "META-INF/opennlp/descriptors/opennlp-grpc-v1.protobin";
  private static final String QUICKSTART_CONNECTED = "Connected to OpenNLP";
  private static final String QUICKSTART_ANALYZED = "Analyzed 3 documents";
  private static final String QUICKSTART_INDEXED = "TurboQuant index";
  private static final String QUICKSTART_SEARCHED = "Search results (16 exhaustive hits)";
  private static final String PYTHON_E2E_ENV = "OPENNLP_PYTHON_E2E";
  private static final String TEACHER_REF_ENV = "OPENNLP_E2E_TEACHER_REF";

  private static Server teiServer;
  private static LiveServerHarness harness;

  /**
   * Starts the test embedding backend and packaged gRPC server.
   *
   * @throws Exception If the topology cannot start.
   */
  @BeforeAll
  static void startTopology() throws Exception {
    assumeTrue(System.getenv(PYTHON_E2E_ENV) != null,
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
    final String teacherRef = System.getenv(TEACHER_REF_ENV);
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

  /**
   * Proves the shipped descriptor set supports the complete lifecycle from Python.
   *
   * @throws Exception If the client or test topology fails.
   */
  @Test
  void pythonClientDrivesTheFullLifecycleFromShippedDescriptors() throws Exception {
    final Path script = Path.of("scripts", "lifecycle_e2e.py").toAbsolutePath();
    assertTrue(Files.isRegularFile(script), "missing " + script);

    final List<String> command = new ArrayList<>(List.of(
        "uv", "run", script.toString(),
        "--target", harness.grpcTarget(),
        "--descriptors", extractShippedDescriptors().toString()));
    final String teacherRef = System.getenv(TEACHER_REF_ENV);
    if (teacherRef != null && !teacherRef.isBlank()) {
      command.add("--teacher-id");
      command.add("e2e");
    }

    // The first run also resolves the script's pinned dependencies through uv.
    runProcess(command, Path.of("").toAbsolutePath(), null, 15, TimeUnit.MINUTES);
  }

  /**
   * Proves the generated-stub quickstart performs analysis and server-side search.
   *
   * @throws Exception If stub generation, the client, or the test topology fails.
   */
  @Test
  void pythonQuickstartAnalyzesIndexesAndSearches() throws Exception {
    final Path grpcRoot = Path.of("..").toAbsolutePath().normalize();
    final Path clientRoot = grpcRoot.resolve(Path.of("examples", "python-client"));
    final Path script = clientRoot.resolve("analyze_and_search.py");
    final Path trainingScript = clientRoot.resolve("train_and_search.py");
    assertTrue(Files.isRegularFile(script), "missing " + script);
    assertTrue(Files.isRegularFile(trainingScript), "missing " + trainingScript);

    final Path protoRoot = grpcRoot.resolve(Path.of(
        "opennlp-grpc-api", "src", "main", "proto"));
    final Path protoPackage = protoRoot.resolve(Path.of(
        "org", "apache", "opennlp", "grpc", "v1"));
    final Path generated = Files.createTempDirectory("opennlp-grpc-python-stubs-");
    final List<String> generate = new ArrayList<>(List.of(
        "uv", "run", "--project", clientRoot.toString(),
        "python", "-m", "grpc_tools.protoc",
        "-I", protoRoot.toString(),
        "--python_out=" + generated,
        "--grpc_python_out=" + generated));
    try (var protos = Files.list(protoPackage)) {
      protos.filter(path -> path.getFileName().toString().endsWith(".proto"))
          .sorted()
          .map(Path::toString)
          .forEach(generate::add);
    }
    runProcess(generate, clientRoot, null, 2, TimeUnit.MINUTES);

    final List<String> command = List.of(
        "uv", "run", "--project", clientRoot.toString(),
        "python", script.toString(),
        "--target", harness.grpcTarget(),
        "--embedding-model", "minilm",
        "--cleanup");
    final String output = runProcess(
        command, clientRoot, generated.toString(), 2, TimeUnit.MINUTES);
    assertTrue(output.contains(QUICKSTART_CONNECTED), output);
    assertTrue(output.contains(QUICKSTART_ANALYZED), output);
    assertTrue(output.contains(QUICKSTART_INDEXED), output);
    assertTrue(output.contains(QUICKSTART_SEARCHED), output);

    final String trainingHelp = runProcess(List.of(
        "uv", "run", "--project", clientRoot.toString(),
        "python", trainingScript.toString(), "--help"),
        clientRoot, generated.toString(), 1, TimeUnit.MINUTES);
    assertTrue(trainingHelp.contains("--teacher-id"), trainingHelp);
    assertTrue(trainingHelp.contains("--cleanup-index"), trainingHelp);
  }

  /**
   * Runs one bounded child process and returns its merged output.
   *
   * @param command Child command and arguments.
   * @param workingDirectory Child working directory.
   * @param pythonPath Optional generated-stub directory.
   * @param timeout Maximum wait amount.
   * @param unit Unit of {@code timeout}.
   * @return Merged standard output and standard error.
   * @throws IOException If process creation or output reading fails.
   * @throws InterruptedException If the current thread is interrupted while waiting.
   */
  private String runProcess(
      List<String> command,
      Path workingDirectory,
      String pythonPath,
      long timeout,
      TimeUnit unit) throws IOException, InterruptedException {
    final Path log = Files.createTempFile("opennlp-grpc-python-command-", ".log");
    final ProcessBuilder builder = new ProcessBuilder(command)
        .directory(workingDirectory.toFile())
        .redirectErrorStream(true)
        .redirectOutput(log.toFile());
    if (pythonPath != null) {
      final String inherited = builder.environment().get("PYTHONPATH");
      builder.environment().put("PYTHONPATH", inherited == null || inherited.isBlank()
          ? pythonPath
          : pythonPath + File.pathSeparator + inherited);
    }
    final Process process = builder.start();
    if (!process.waitFor(timeout, unit)) {
      process.destroyForcibly();
      process.waitFor(10, TimeUnit.SECONDS);
      throw new IOException("Python command timed out; log:\n" + Files.readString(log));
    }
    final String output = Files.readString(log);
    assertEquals(0, process.exitValue(), "Python command failed; log:\n" + output);
    return output;
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
