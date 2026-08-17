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
package org.apache.opennlp.grpc.it;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Spawns the shaded loopback web application for end-to-end HTTP tests. */
final class LiveWebAppHarness implements AutoCloseable {

  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);

  private final Process process;
  private final Path log;
  private final String baseUri;

  private LiveWebAppHarness(Process process, Path log, String baseUri) {
    this.process = process;
    this.log = log;
    this.baseUri = baseUri;
  }

  /** Starts the shaded webapp against one already-running gRPC target. */
  static LiveWebAppHarness start(String grpcTarget) throws Exception {
    if (grpcTarget == null || grpcTarget.isBlank()) {
      throw new IllegalArgumentException("grpcTarget must not be blank");
    }
    final Path webappJar = requiredJar();
    final Path log = Files.createTempFile("opennlp-grpc-webapp-live-it-", ".log");
    final Path readinessDirectory = Files.createTempDirectory(
        "opennlp-grpc-webapp-live-it-ready-");
    final Path boundPortFile = readinessDirectory.resolve("bound-port");
    readinessDirectory.toFile().deleteOnExit();
    boundPortFile.toFile().deleteOnExit();
    final String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    final Process process = new ProcessBuilder(
        javaBin, "-jar", webappJar.toString(),
        "--http-port", "0",
        "--bound-port-file", boundPortFile.toString(),
        "--grpc-target", grpcTarget)
        .redirectErrorStream(true)
        .redirectOutput(log.toFile())
        .start();
    final int httpPort;
    try {
      httpPort = LiveProcessHarnessSupport.awaitBoundPortFile(
          process, boundPortFile, log, STARTUP_TIMEOUT, "Webapp");
    } catch (Exception e) {
      process.destroyForcibly();
      throw e;
    }
    final LiveWebAppHarness harness = new LiveWebAppHarness(
        process, log, "http://127.0.0.1:" + httpPort);
    try {
      harness.awaitReady();
      return harness;
    } catch (Exception e) {
      harness.close();
      throw e;
    }
  }

  /** Returns the HTTP origin of the spawned webapp. */
  String baseUri() {
    return baseUri;
  }

  @Override
  public void close() {
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

  private void awaitReady() throws Exception {
    final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();
    final long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
    Exception lastFailure = null;
    while (System.nanoTime() < deadline) {
      if (!process.isAlive()) {
        throw new IllegalStateException("Webapp process exited with code "
            + process.exitValue() + "; log:\n" + Files.readString(log));
      }
      try {
        final HttpResponse<String> response = client.send(HttpRequest.newBuilder(
            URI.create(baseUri + "/healthz"))
            .timeout(Duration.ofSeconds(2))
            .GET()
            .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
          return;
        }
      } catch (IOException e) {
        lastFailure = e;
      }
      Thread.sleep(100);
    }
    throw new IllegalStateException("Webapp did not become ready within " + STARTUP_TIMEOUT
        + " (last failure: " + lastFailure + "); log:\n" + Files.readString(log));
  }

  private static Path requiredJar() {
    final String value = System.getProperty("opennlp.grpc.webapp.jar");
    if (value == null || !Files.isRegularFile(Path.of(value))) {
      throw new IllegalStateException("System property 'opennlp.grpc.webapp.jar' must point "
          + "to the shaded webapp jar, but was: " + value);
    }
    return Path.of(value);
  }

}
