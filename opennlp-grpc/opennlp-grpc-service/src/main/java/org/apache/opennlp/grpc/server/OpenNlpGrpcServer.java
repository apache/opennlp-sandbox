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
package org.apache.opennlp.grpc.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.processor.basic.BasicDocumentAnalyzer;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.server.OpenNlpAnalysisServiceImpl;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * gRPC server exposing the v1 {@code OpenNlpAnalysisService} document-centric API.
 */
@Command(name = "OpenNLP gRPC Server", mixinStandardHelpOptions = true, version = OpenNlpGrpcServer.SERVER_VERSION)
public class OpenNlpGrpcServer implements Callable<Integer> {

  /** Server build version, also reported as {@code GetServiceInfoResponse.opennlp_version}. */
  static final String SERVER_VERSION = "3.0.0-SNAPSHOT";

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OpenNlpGrpcServer.class);

  private static final int INBOUND_MESSAGE_HEADROOM_BYTES = 1_048_576;
  private static final int DEFAULT_SHUTDOWN_GRACE_SECONDS = 5;

  @Option(
      names = {"-p", "--port"},
      defaultValue = "7071",
      paramLabel = "NUM",
      description = "port (default to 7071)")
  int port;

  @Option(
      names = {"-c", "--config"},
      paramLabel = "STRING",
      description = "key value configuration file")
  String config;

  private Server server;
  private ExecutorService handlerExecutor;
  private ExecutorService analysisExecutor;
  private HealthStatusManager healthStatusManager;
  private ModelBundleCache modelBundleCache;
  private int shutdownGraceSeconds = DEFAULT_SHUTDOWN_GRACE_SECONDS;
  private final AtomicBoolean stopping = new AtomicBoolean();

  /** Creates an unstarted server; picocli populates the options before {@link #call()} runs. */
  public OpenNlpGrpcServer() {
  }

  /**
   * Command-line entry point. Parses the arguments with picocli, runs the server, and exits
   * the JVM with the resulting status code.
   *
   * @param args The command-line arguments (port and config file options).
   */
  public static void main(String... args) {
    final CommandLine cli = new CommandLine(new OpenNlpGrpcServer());
    final int exitCode = cli.execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() {
    try {
      start();
      awaitTermination();
    } catch (Exception e) {
      stop();
      logger.error(e.getLocalizedMessage(), e);
      return 1;
    }
    return 0;
  }

  /**
   * Loads the configuration, builds the model cache, profiles, and analyzer, then starts the
   * gRPC server on the configured port and registers a shutdown hook that closes the models.
   * Optionally enables server reflection per the {@code server.enable_reflection} setting.
   *
   * @throws Exception If the configuration cannot be read, a model fails to load, or the
   *     server fails to bind or start.
   */
  public void start() throws Exception {
    final Map<String, String> configuration = loadConfiguration();

    final boolean enableReflection =
        Boolean.parseBoolean(
            configuration.getOrDefault("server.enable_reflection", "false"));

    final int configuredMaxInboundMessageSize =
        Integer.parseInt(
            configuration.getOrDefault("server.max_inbound_message_size", "10485760"));

    final int maxTextBytes = Integer.parseInt(configuration.getOrDefault(
        "server.max_text_bytes",
        Integer.toString(OpenNlpAnalysisServiceImpl.DEFAULT_MAX_TEXT_BYTES)));
    final int maxInboundMessageSize = maxInboundMessageSize(
        configuredMaxInboundMessageSize, maxTextBytes);
    if (maxInboundMessageSize != configuredMaxInboundMessageSize) {
      logger.info(
          "Raised server.max_inbound_message_size from {} to {} so max_text_bytes {} "
              + "remains reachable",
          configuredMaxInboundMessageSize, maxInboundMessageSize, maxTextBytes);
    }

    final int analysisStreamWorkers = Integer.parseInt(configuration.getOrDefault(
        "server.analysis_stream_workers",
        Integer.toString(Math.max(2, Runtime.getRuntime().availableProcessors()))));
    if (analysisStreamWorkers < 1) {
      throw new IllegalArgumentException("server.analysis_stream_workers must be positive");
    }

    this.shutdownGraceSeconds = Integer.parseInt(configuration.getOrDefault(
        "server.shutdown_grace_seconds",
        Integer.toString(DEFAULT_SHUTDOWN_GRACE_SECONDS)));
    if (shutdownGraceSeconds < 0) {
      throw new IllegalArgumentException("server.shutdown_grace_seconds must not be negative");
    }

    this.modelBundleCache = new ModelBundleCache(configuration);
    final ProfileRegistry profileRegistry = modelBundleCache.createProfileRegistry();
    final BasicDocumentAnalyzer documentAnalyzer =
        new BasicDocumentAnalyzer(profileRegistry, modelBundleCache);

    // Run each RPC handler on a virtual thread, so a request that blocks on a remote backend
    // (TEI/OpenVINO), a streaming batch's latch, or native inference unmounts its carrier
    // instead of pinning a platform thread. The Netty event-loop threads stay platform threads;
    // only the application-callback executor is virtual.
    this.handlerExecutor = Executors.newVirtualThreadPerTaskExecutor();
    this.analysisExecutor = Executors.newFixedThreadPool(
        analysisStreamWorkers,
        Thread.ofVirtual().name("opennlp-analysis-stream-", 0).factory());
    this.healthStatusManager = new HealthStatusManager();

    final ServerBuilder<?> builder = ServerBuilder.forPort(port)
        .executor(handlerExecutor)
        .addService(ServerInterceptors.intercept(
            new OpenNlpAnalysisServiceImpl(
                documentAnalyzer,
                profileRegistry,
                modelBundleCache,
                SERVER_VERSION,
                analysisExecutor,
                analysisStreamWorkers,
                maxTextBytes),
            new EagerHeadersInterceptor()))
        .addService(healthStatusManager.getHealthService())
        .maxInboundMessageSize(maxInboundMessageSize);

    if (enableReflection) {
      builder.addService(ProtoReflectionServiceV1.newInstance());
    }

    this.server = builder.build();
    this.server.start();
    healthStatusManager.setStatus(
        HealthStatusManager.SERVICE_NAME_ALL_SERVICES,
        HealthCheckResponse.ServingStatus.SERVING);
    healthStatusManager.setStatus(
        OpenNlpAnalysisServiceGrpc.SERVICE_NAME,
        HealthCheckResponse.ServingStatus.SERVING);
    logger.info("Started OpenNlpGrpcServer on port {}", server.getPort());

    registerShutdownHook();
  }

  /**
   * Blocks the calling thread until the server terminates. Returns immediately if the server
   * has not been started.
   *
   * @throws InterruptedException If the calling thread is interrupted while waiting.
   */
  public void awaitTermination() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  /**
   * Returns the port the server is listening on.
   *
   * @return The bound port once started, otherwise the configured port (which may be {@code 0}
   *     to request an ephemeral port).
   */
  public int getPort() {
    return server != null ? server.getPort() : port;
  }

  private Map<String, String> loadConfiguration() throws IOException {
    final Map<String, String> configuration = new HashMap<>();

    if (config == null) {
      return configuration;
    }

    final File configFile = new File(config);
    if (!configFile.exists()) {
      throw new IOException("Config file not found: " + config);
    }

    final Properties properties = new Properties();
    try (InputStream input = new FileInputStream(configFile)) {
      properties.load(input);
    }
    for (String name : properties.stringPropertyNames()) {
      configuration.put(name, properties.getProperty(name));
    }
    return configuration;
  }

  private static int maxInboundMessageSize(int configuredSize, int maxTextBytes) {
    if (configuredSize < 1) {
      throw new IllegalArgumentException("server.max_inbound_message_size must be positive");
    }
    if (maxTextBytes < 1) {
      throw new IllegalArgumentException("server.max_text_bytes must be positive");
    }
    final long requiredSize = (long) maxTextBytes + INBOUND_MESSAGE_HEADROOM_BYTES;
    if (requiredSize > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "server.max_text_bytes leaves no room for the protobuf request envelope");
    }
    return Math.max(configuredSize, (int) requiredSize);
  }

  private void registerShutdownHook() {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                this::stop,
                "opennlp-grpc-shutdown"));
  }

  /**
   * Stops accepting calls, waits for accepted RPCs to drain up to the configured grace
   * period, and only then closes executors and model resources. If the grace period expires,
   * outstanding calls and worker tasks are cancelled before resources close. This method is
   * idempotent and is a no-op if no lifecycle component was created.
   */
  public void stop() {
    if (server == null && handlerExecutor == null && analysisExecutor == null
        && modelBundleCache == null) {
      return;
    }
    if (!stopping.compareAndSet(false, true)) {
      return;
    }
    if (healthStatusManager != null) {
      healthStatusManager.enterTerminalState();
    }
    boolean forced = false;
    boolean interrupted = false;
    if (server != null) {
      logger.info("Shutting down OpenNlpGrpcServer on port {}", server.getPort());
      server.shutdown();
      try {
        if (!server.awaitTermination(shutdownGraceSeconds, TimeUnit.SECONDS)) {
          forced = true;
          logger.warn("Forcing OpenNlpGrpcServer shutdown after {} seconds",
              shutdownGraceSeconds);
          server.shutdownNow();
          server.awaitTermination(1, TimeUnit.SECONDS);
        }
      } catch (InterruptedException e) {
        interrupted = true;
        forced = true;
        server.shutdownNow();
      }
    }
    stopExecutor(handlerExecutor, forced);
    stopExecutor(analysisExecutor, forced);
    if (modelBundleCache != null) {
      modelBundleCache.close();
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private static void stopExecutor(ExecutorService executor, boolean forced) {
    if (executor == null) {
      return;
    }
    if (forced) {
      executor.shutdownNow();
    } else {
      executor.shutdown();
    }
  }
}
