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
import java.util.List;
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
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.sink.DocumentSinkRegistry;
import org.apache.opennlp.grpc.sink.SinkTeeingDocumentAnalyzer;
import org.apache.opennlp.grpc.processor.basic.BasicDocumentAnalyzer;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.search.DynamicSearchIndexRegistry;
import org.apache.opennlp.grpc.search.IndexAliasRegistry;
import org.apache.opennlp.grpc.search.OpenNlpSearchServiceImpl;
import org.apache.opennlp.grpc.search.SearchCollectionRegistry;
import org.apache.opennlp.grpc.search.SearchIndexRegistry;
import org.apache.opennlp.grpc.search.SearchProviderCatalog;
import org.apache.opennlp.grpc.search.WorkspaceCheckpointStore;
import org.apache.opennlp.grpc.training.CatalogModelBootstrap;
import org.apache.opennlp.grpc.training.CatalogModelStore;
import org.apache.opennlp.grpc.training.DefaultStreamingTrainingPipeline;
import org.apache.opennlp.grpc.training.OpenNlpModelTrainingServiceImpl;
import org.apache.opennlp.grpc.training.StaticModelArtifactStore;
import org.apache.opennlp.grpc.training.StaticModelTrainer;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpModelTrainingServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpSearchServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpVocabularyServiceGrpc;
import org.apache.opennlp.grpc.v1.server.OpenNlpAnalysisServiceImpl;
import org.apache.opennlp.grpc.vocabulary.DictionaryFormatRegistry;
import org.apache.opennlp.grpc.vocabulary.OpenNlpVocabularyServiceImpl;
import org.apache.opennlp.grpc.vocabulary.VocabularyArtifactStore;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * gRPC server exposing the v1 {@code OpenNlpAnalysisService} document-centric API.
 */
@Command(
    name = "OpenNLP gRPC Server",
    mixinStandardHelpOptions = true,
    version = OpenNlpGrpcServer.SERVER_VERSION)
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
  private java.util.concurrent.ScheduledExecutorService checkpointScheduler;
  private HealthStatusManager healthStatusManager;
  private ModelBundleCache modelBundleCache;
  private SearchIndexRegistry searchIndexRegistry;
  private DynamicSearchIndexRegistry dynamicSearchIndexRegistry;
  private int shutdownGraceSeconds = DEFAULT_SHUTDOWN_GRACE_SECONDS;
  private DocumentSinkRegistry documentSinks;
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

  /** {@inheritDoc} */
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
    try {
      startConfigured();
    } catch (Exception | Error failure) {
      stop();
      throw failure;
    }
  }

  /** Constructs and starts all configured services after public lifecycle guarding. */
  private void startConfigured() throws Exception {
    final Map<String, String> configuration =
        CatalogModelBootstrap.prepare(loadConfiguration());

    final boolean enableReflection =
        Boolean.parseBoolean(
            configuration.getOrDefault("server.enable_reflection", "false"));
    final boolean enableDynamicSearch = Boolean.parseBoolean(
        configuration.getOrDefault("search.dynamic.enabled", "true"));

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
    this.searchIndexRegistry = SearchIndexRegistry.fromConfiguration(configuration);
    final WorkspaceCheckpointStore checkpointStore =
        WorkspaceCheckpointStore.fromConfiguration(configuration);
    this.dynamicSearchIndexRegistry = enableDynamicSearch
        ? new DynamicSearchIndexRegistry(
            SearchProviderCatalog.fromConfiguration(configuration), checkpointStore)
        : DynamicSearchIndexRegistry.disabled();
    final IndexAliasRegistry indexAliasRegistry =
        IndexAliasRegistry.fromConfiguration(configuration);
    final DictionaryFormatRegistry dictionaryFormats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore vocabularyArtifacts =
        VocabularyArtifactStore.fromConfiguration(configuration, dictionaryFormats);
    final SearchCollectionRegistry collectionRegistry =
        SearchCollectionRegistry.fromConfiguration(configuration, dynamicSearchIndexRegistry,
            artifactId -> vocabularyArtifacts.readVocabularyTermRows(artifactId).stream()
                .map(VocabularyArtifactStore.TermRow::term)
                .toList());
    final int checkpointSeconds = Integer.parseInt(configuration.getOrDefault(
        "search.persist.checkpoint_seconds", "0"));
    if (checkpointSeconds < 0) {
      throw new IllegalArgumentException(
          "search.persist.checkpoint_seconds must not be negative");
    }
    if (checkpointSeconds > 0 && checkpointStore != null && enableDynamicSearch) {
      this.checkpointScheduler = Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().name("opennlp-search-checkpoint").daemon().factory());
      checkpointScheduler.scheduleWithFixedDelay(() -> {
        try {
          final List<String> rewritten =
              dynamicSearchIndexRegistry.checkpointPersistedIndexes();
          for (String indexId : rewritten) {
            collectionRegistry.notifyIndexPersisted(indexId);
          }
          if (!rewritten.isEmpty()) {
            logger.info("Auto-checkpoint rewrote {} search index checkpoint(s)",
                rewritten.size());
          }
        } catch (RuntimeException e) {
          logger.error("Auto-checkpoint of search indexes failed", e);
        }
      }, checkpointSeconds, checkpointSeconds, TimeUnit.SECONDS);
    }
    final StaticModelArtifactStore staticModelArtifacts =
        StaticModelArtifactStore.fromConfiguration(configuration, vocabularyArtifacts,
            StaticModelTrainer.distiller(), modelBundleCache.getTrainedModelRegistry());
    final CatalogModelStore catalogModels = CatalogModelStore.fromConfiguration(
        configuration, staticModelArtifacts, modelBundleCache.getTrainedModelRegistry());
    staticModelArtifacts.setPublicationListener(collectionRegistry::notifyModelPublished);
    final ProfileRegistry profileRegistry = modelBundleCache.createProfileRegistry();
    final BasicDocumentAnalyzer documentAnalyzer =
        new BasicDocumentAnalyzer(profileRegistry, modelBundleCache);
    this.documentSinks = DocumentSinkRegistry.fromConfiguration(configuration);
    // Only the analysis service tees into sinks; training-time analyses stay internal.
    final DocumentAnalyzer sinkedAnalyzer = documentSinks.isEmpty()
        ? documentAnalyzer : new SinkTeeingDocumentAnalyzer(documentAnalyzer, documentSinks);
    final DefaultStreamingTrainingPipeline streamingTraining =
        new DefaultStreamingTrainingPipeline(
            documentAnalyzer,
            vocabularyArtifacts,
            staticModelArtifacts,
            dynamicSearchIndexRegistry,
            indexAliasRegistry);

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
                sinkedAnalyzer,
                profileRegistry,
                modelBundleCache,
                SERVER_VERSION,
                analysisExecutor,
                analysisStreamWorkers,
                maxTextBytes),
            new EagerHeadersInterceptor()))
        .addService(ServerInterceptors.intercept(
            new OpenNlpSearchServiceImpl(
                searchIndexRegistry,
                dynamicSearchIndexRegistry,
                modelBundleCache.getEmbeddingProvider(),
                indexAliasRegistry,
                collectionRegistry),
            new EagerHeadersInterceptor()))
        .addService(ServerInterceptors.intercept(
            new OpenNlpVocabularyServiceImpl(dictionaryFormats, vocabularyArtifacts),
            new EagerHeadersInterceptor()))
        .addService(ServerInterceptors.intercept(
            new OpenNlpModelTrainingServiceImpl(
                staticModelArtifacts, streamingTraining, catalogModels),
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
    healthStatusManager.setStatus(
        OpenNlpSearchServiceGrpc.SERVICE_NAME,
        HealthCheckResponse.ServingStatus.SERVING);
    healthStatusManager.setStatus(
        OpenNlpVocabularyServiceGrpc.SERVICE_NAME,
        HealthCheckResponse.ServingStatus.SERVING);
    healthStatusManager.setStatus(
        OpenNlpModelTrainingServiceGrpc.SERVICE_NAME,
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

  /** Loads the optional properties file into a stable string configuration map. */
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

  /** Ensures the inbound message limit includes text and envelope headroom. */
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

  /** Registers graceful server and model shutdown. */
  private void registerShutdownHook() {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                this::stop,
                "opennlp-grpc-shutdown"));
  }

  /**
   * Injects lifecycle components into an unstarted server. Package-private test seam; the
   * production path builds these in {@link #start()}.
   */
  void injectLifecycleForTest(
      ExecutorService analysisExecutor, ModelBundleCache modelBundleCache,
      int shutdownGraceSeconds) {
    injectLifecycleForTest(
        analysisExecutor, modelBundleCache, null, shutdownGraceSeconds);
  }

  /** Injects lifecycle components including the search registry for shutdown tests. */
  void injectLifecycleForTest(
      ExecutorService analysisExecutor, ModelBundleCache modelBundleCache,
      SearchIndexRegistry searchIndexRegistry, int shutdownGraceSeconds) {
    this.analysisExecutor = analysisExecutor;
    this.modelBundleCache = modelBundleCache;
    this.searchIndexRegistry = searchIndexRegistry;
    this.shutdownGraceSeconds = shutdownGraceSeconds;
  }

  /**
   * Stops accepting calls, waits for accepted RPCs to drain up to the configured grace
   * period, and only then closes executors and model resources. If the grace period expires,
   * outstanding calls and worker tasks are cancelled before resources close. In both cases the
   * executors get one further bounded wait to quiesce before the model cache closes:
   * {@code shutdownNow()} only interrupts, and a worker inside a native (ONNX) inference call
   * cannot be interrupted, so closing the cache without that wait could free native sessions
   * under live inference. This method is idempotent and is a no-op if no lifecycle component
   * was created.
   */
  public void stop() {
    if (server == null && handlerExecutor == null && analysisExecutor == null
        && modelBundleCache == null && searchIndexRegistry == null
        && dynamicSearchIndexRegistry == null) {
      return;
    }
    if (!stopping.compareAndSet(false, true)) {
      return;
    }
    if (healthStatusManager != null) {
      healthStatusManager.enterTerminalState();
    }
    if (checkpointScheduler != null) {
      checkpointScheduler.shutdownNow();
    }
    boolean forced = false;
    boolean interrupted = false;
    if (server != null) {
      logger.info("Shutting down OpenNlpGrpcServer");
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
    interrupted |= awaitQuiescence(handlerExecutor, "handler");
    interrupted |= awaitQuiescence(analysisExecutor, "analysis");
    try {
      try {
        if (dynamicSearchIndexRegistry != null) {
          dynamicSearchIndexRegistry.close();
        }
      } finally {
        if (searchIndexRegistry != null) {
          searchIndexRegistry.close();
        }
      }
    } finally {
      try {
        if (modelBundleCache != null) {
          modelBundleCache.close();
        }
      } finally {
        // Sinks close last so every drained analysis was already teed.
        if (documentSinks != null) {
          documentSinks.close();
        }
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Waits up to the shutdown grace period for an executor's workers to quiesce before the
   * model cache closes, logging when they do not. {@code shutdown()} and
   * {@code shutdownNow()} only initiate termination; a worker that ignores interrupts (native
   * inference cannot be interrupted) is still running when they return.
   *
   * @return {@code true} when the wait itself was interrupted.
   */
  private boolean awaitQuiescence(ExecutorService executor, String name) {
    if (executor == null) {
      return false;
    }
    try {
      if (!executor.awaitTermination(shutdownGraceSeconds, TimeUnit.SECONDS)) {
        logger.warn("{} executor did not quiesce within {} second(s) during shutdown; "
            + "closing models with work possibly still in flight", name, shutdownGraceSeconds);
      }
      return false;
    } catch (InterruptedException e) {
      return true;
    }
  }

  /** Stops an executor gracefully or immediately. */
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
