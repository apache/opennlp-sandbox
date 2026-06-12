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
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;

/**
 * Spawns the shaded {@code opennlp-grpc-server} SNAPSHOT jar as a separate JVM process
 * (with the TEI and OpenVINO backend modules on its classpath) and provides a blocking
 * client to it.
 *
 * <p>The jar locations are injected by the failsafe configuration via the
 * {@code opennlp.grpc.server.jar}, {@code opennlp.grpc.tei.backend.jar} and
 * {@code opennlp.grpc.openvino.backend.jar} system properties; building the reactor
 * first guarantees they exist.</p>
 */
final class LiveServerHarness implements AutoCloseable {

  private static final long STARTUP_TIMEOUT_MS = 120_000L;

  private final Process process;
  private final Path log;
  private final ManagedChannel channel;
  private final OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub client;

  private LiveServerHarness(Process process, Path log, ManagedChannel channel) {
    this.process = process;
    this.log = log;
    this.channel = channel;
    this.client = OpenNlpAnalysisServiceGrpc.newBlockingStub(channel);
  }

  /**
   * Starts the server process with the given configuration entries (in addition to
   * {@code server.enable_reflection=false}) and blocks until it answers
   * {@code GetServiceInfo} or the startup timeout elapses.
   *
   * @param serverConfig Extra server configuration. Must not be {@code null}.
   *
   * @return The running harness. Callers own it and must {@link #close()} it.
   */
  static LiveServerHarness start(Properties serverConfig) throws IOException, InterruptedException {
    final int serverPort = freePort();
    final Properties properties = new Properties();
    properties.putAll(serverConfig);
    properties.setProperty("server.enable_reflection", "false");

    final Path config = Files.createTempFile("opennlp-grpc-live-it-", ".ini");
    config.toFile().deleteOnExit();
    // Written with Properties.store so backslashes in paths survive Properties.load.
    try (OutputStream out = Files.newOutputStream(config)) {
      properties.store(out, null);
    }

    final Path log = Files.createTempFile("opennlp-grpc-live-it-", ".log");
    final String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    final String classpath = requiredJar("opennlp.grpc.server.jar")
        + File.pathSeparator + requiredJar("opennlp.grpc.tei.backend.jar")
        + File.pathSeparator + requiredJar("opennlp.grpc.openvino.backend.jar");
    final Process process = new ProcessBuilder(
        javaBin, "-cp", classpath, "org.apache.opennlp.grpc.server.OpenNlpGrpcServer",
        "-p", Integer.toString(serverPort), "-c", config.toString())
        .redirectErrorStream(true)
        .redirectOutput(log.toFile())
        .start();

    final ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", serverPort)
        .usePlaintext()
        .build();
    final LiveServerHarness harness = new LiveServerHarness(process, log, channel);
    try {
      harness.awaitReady();
    } catch (RuntimeException | InterruptedException e) {
      harness.close();
      throw e;
    }
    return harness;
  }

  OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub client() {
    return client;
  }

  @Override
  public void close() {
    channel.shutdown();
    process.destroy();
    try {
      if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly();
      }
    } catch (InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
    }
  }

  private void awaitReady() throws IOException, InterruptedException {
    final long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
    StatusRuntimeException lastFailure = null;
    while (System.currentTimeMillis() < deadline) {
      if (!process.isAlive()) {
        throw new IllegalStateException("Server process exited with code "
            + process.exitValue() + "; log:\n" + Files.readString(log));
      }
      try {
        client.withDeadlineAfter(2, TimeUnit.SECONDS)
            .getServiceInfo(GetServiceInfoRequest.getDefaultInstance());
        return;
      } catch (StatusRuntimeException e) {
        lastFailure = e;
        Thread.sleep(250);
      }
    }
    throw new IllegalStateException("Server did not become ready within " + STARTUP_TIMEOUT_MS
        + " ms (last failure: " + lastFailure + "); log:\n" + Files.readString(log));
  }

  private static String requiredJar(String systemProperty) {
    final String path = System.getProperty(systemProperty);
    if (path == null || !Files.isRegularFile(Path.of(path))) {
      throw new IllegalStateException(
          "System property '" + systemProperty + "' must point to an existing jar but was: "
              + path + ". Build the reactor (mvn verify) so the server and backend jars exist.");
    }
    return path;
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
