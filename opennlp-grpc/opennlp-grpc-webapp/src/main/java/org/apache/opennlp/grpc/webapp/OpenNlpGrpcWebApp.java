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
package org.apache.opennlp.grpc.webapp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;

/** Runs the optional browser interface and JSON gateway for an OpenNLP gRPC service. */
@Command(
    name = "opennlp-grpc-webapp",
    description = "Serve Web UI extensions backed by an OpenNLP gRPC service.",
    mixinStandardHelpOptions = true,
    versionProvider = OpenNlpGrpcWebApp.VersionProvider.class)
public final class OpenNlpGrpcWebApp implements Callable<Integer> {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenNlpGrpcWebApp.class);

  @Option(names = "--http-host", defaultValue = "127.0.0.1",
      description = "HTTP bind address. Default: ${DEFAULT-VALUE}")
  private String httpHost;

  @Option(names = "--http-port", defaultValue = "7072",
      description = "HTTP port. Default: ${DEFAULT-VALUE}")
  private int httpPort;

  @Option(names = "--grpc-target", defaultValue = "127.0.0.1:7071",
      description = "OpenNLP gRPC target. Default: ${DEFAULT-VALUE}")
  private String grpcTarget;

  @Option(names = "--grpc-plaintext", defaultValue = "true", negatable = true,
      description = "Use a plaintext gRPC connection. Default: ${DEFAULT-VALUE}")
  private boolean grpcPlaintext;

  @Option(names = "--grpc-max-inbound-message-bytes", defaultValue = "104857600",
      description = "Maximum gRPC message size in bytes accepted from the server."
          + " Default: ${DEFAULT-VALUE}")
  private int grpcMaxInboundMessageBytes;

  @Option(names = "--request-timeout-seconds", defaultValue = "30",
      description = "Per-RPC deadline in seconds. Default: ${DEFAULT-VALUE}")
  private int requestTimeoutSeconds;

  @Option(names = "--request-timeout-per-megabyte-seconds", defaultValue = "120",
      description = "Extra analysis and formatting deadline per mebibyte of submitted document"
          + " text, capped by the long-running timeout; 0 disables scaling."
          + " Default: ${DEFAULT-VALUE}")
  private int requestTimeoutPerMegabyteSeconds;

  @Option(names = "--long-running-timeout-seconds", defaultValue = "1800",
      description = "Training and catalog-install deadline in seconds. Default: ${DEFAULT-VALUE}")
  private int longRunningTimeoutSeconds;

  @Option(names = "--max-request-bytes", defaultValue = "104857600",
      description = "Maximum JSON request body size. Default: ${DEFAULT-VALUE}")
  private int maxRequestBytes;

  @Option(names = "--allow-remote",
      description = "Allow binding HTTP to a non-loopback address.")
  private boolean allowRemote;

  @Option(names = "--bound-port-file",
      description = "Create this readiness file with the bound HTTP port after startup.")
  private Path boundPortFile;

  /** Creates the command with its documented defaults. */
  public OpenNlpGrpcWebApp() {
  }

  /** Supplies the artifact version recorded in this application's manifest. */
  public static final class VersionProvider implements IVersionProvider {

    /** Creates the version provider used by picocli. */
    public VersionProvider() {
    }

    /** {@inheritDoc} */
    @Override
    public String[] getVersion() {
      String implementationVersion = OpenNlpGrpcWebApp.class.getPackage()
          .getImplementationVersion();
      return new String[] {"opennlp-grpc-webapp "
          + (implementationVersion == null ? "development" : implementationVersion)};
    }
  }

  /**
   * Runs the web application until the process receives a shutdown signal.
   *
   * @return Zero after an orderly shutdown.
   * @throws Exception If configuration, startup, or shutdown fails.
   */
  @Override
  public Integer call() throws Exception {
    if (httpPort < 0 || httpPort > 65535) {
      throw new IllegalArgumentException("http port must be between 0 and 65535");
    }
    if (requestTimeoutSeconds < 1) {
      throw new IllegalArgumentException("request timeout must be positive");
    }
    if (longRunningTimeoutSeconds < 1) {
      throw new IllegalArgumentException("long-running timeout must be positive");
    }
    if (requestTimeoutPerMegabyteSeconds < 0) {
      throw new IllegalArgumentException("request timeout per megabyte must not be negative");
    }
    if (grpcMaxInboundMessageBytes < 1) {
      throw new IllegalArgumentException("grpc max inbound message bytes must be positive");
    }
    InetAddress bindAddress = InetAddress.getByName(httpHost);
    validateBindAddress(bindAddress, allowRemote);

    ManagedChannel channel = newChannel(grpcTarget, grpcPlaintext, grpcMaxInboundMessageBytes);
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    ClassLoader extensionClassLoader = contextClassLoader == null
        ? OpenNlpGrpcWebApp.class.getClassLoader() : contextClassLoader;
    WebUiExtensionRegistry registry = WebUiExtensionRegistry.load(extensionClassLoader);
    Duration requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
    Duration longRunningTimeout = Duration.ofSeconds(longRunningTimeoutSeconds);
    GrpcAnalysisRpc analysisRpc = new GrpcAnalysisRpc(channel, requestTimeout,
        longRunningTimeout, Duration.ofSeconds(requestTimeoutPerMegabyteSeconds));
    GrpcSearchRpc searchRpc = new GrpcSearchRpc(channel, requestTimeout, longRunningTimeout,
        Duration.ofSeconds(requestTimeoutPerMegabyteSeconds));
    GrpcVocabularyRpc vocabularyRpc = new GrpcVocabularyRpc(channel, requestTimeout);
    GrpcTrainingRpc trainingRpc = new GrpcTrainingRpc(
        channel, requestTimeout, longRunningTimeout);
    try (OpenNlpGrpcWebServer server = new OpenNlpGrpcWebServer(
        new InetSocketAddress(bindAddress, httpPort), analysisRpc, searchRpc,
        vocabularyRpc, trainingRpc, registry, maxRequestBytes)) {
      Thread shutdownHook = new Thread(() -> {
        server.stop();
        channel.shutdown();
      }, "opennlp-grpc-webapp-shutdown");
      Runtime.getRuntime().addShutdownHook(shutdownHook);
      try {
        server.start();
        if (boundPortFile != null) {
          writeBoundPortFile(boundPortFile, server.address().getPort());
        }
        LOGGER.info("OpenNLP gRPC web application listening on http://{}:{} with {} UI extension(s)",
            server.address().getHostString(), server.address().getPort(), registry.extensions().size());
        server.awaitTermination();
      } finally {
        try {
          Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
          // The JVM is already running shutdown hooks.
        }
      }
    } finally {
      channel.shutdown();
      if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
        channel.shutdownNow();
        channel.awaitTermination(5, TimeUnit.SECONDS);
      }
    }
    return 0;
  }

  /** Default cap in bytes for gRPC messages accepted from the server: 100 MiB. */
  static final int DEFAULT_GRPC_MAX_INBOUND_MESSAGE_BYTES = 100 * 1024 * 1024;

  /**
   * Builds the channel to the analysis service.
   *
   * @param grpcTarget The gRPC target string.
   * @param grpcPlaintext Whether to use a plaintext connection.
   * @param maxInboundMessageBytes Cap in bytes for messages accepted from the server.
   * @return The connected channel.
   */
  static ManagedChannel newChannel(String grpcTarget, boolean grpcPlaintext,
      int maxInboundMessageBytes) {
    ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forTarget(grpcTarget);
    if (grpcPlaintext) {
      channelBuilder.usePlaintext();
    } else {
      channelBuilder.useTransportSecurity();
    }
    return channelBuilder.maxInboundMessageSize(maxInboundMessageBytes).build();
  }

  /**
   * Validates the HTTP bind policy.
   *
   * @param address The resolved bind address.
   * @param allowRemote Whether a non-loopback address is allowed.
   * @throws IllegalArgumentException If the address is {@code null} or violates the bind policy.
   */
  static void validateBindAddress(InetAddress address, boolean allowRemote) {
    if (address == null) {
      throw new IllegalArgumentException("address must not be null");
    }
    if (!allowRemote && !address.isLoopbackAddress()) {
      throw new IllegalArgumentException(
          "refusing non-loopback HTTP bind without --allow-remote: " + address.getHostAddress());
    }
  }

  /**
   * Creates an interprocess readiness file after the HTTP listener owns its port.
   *
   * @param readinessFile File to create. An existing path is rejected.
   * @param port Bound listener port.
   * @throws IOException If the file cannot be written.
   * @throws IllegalArgumentException If the path exists or the port is invalid.
   */
  static void writeBoundPortFile(Path readinessFile, int port) throws IOException {
    if (readinessFile == null) {
      throw new IllegalArgumentException("readinessFile must not be null");
    }
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("bound port must be between 1 and 65535");
    }
    try {
      Files.writeString(readinessFile, Integer.toString(port) + "\n", StandardCharsets.US_ASCII,
          StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    } catch (FileAlreadyExistsException e) {
      throw new IllegalArgumentException("bound port readiness file already exists: "
          + readinessFile, e);
    }
  }

  /**
   * Starts the command-line application.
   *
   * @param args Command-line arguments.
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new OpenNlpGrpcWebApp()).execute(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }
}
