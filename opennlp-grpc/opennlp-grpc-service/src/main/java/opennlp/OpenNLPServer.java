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

package opennlp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import opennlp.service.PosTaggerService;
import opennlp.service.SentenceDetectorService;
import opennlp.service.TokenizerService;

/**
 * The {@code OpenNLPServer} class implements a gRPC server for providing OpenNLP-based services.
 * It is a command-line application that allows configuration through command-line options and a configuration file.
 * The server hosts services such as POS tagging using OpenNLP models, and can optionally enable reflection for
 * gRPC clients.
 *
 * <p>This server listens on a configurable port (default is 7071).
 * It loads configuration settings from a configured file (if present, otherwise falls back to defaults)
 * and uses them to initialize various components such as the POS tagger service.</p>
 *
 * <p><b>Command-line Options:</b>
 * <ul>
 *   <li>{@code -p, --port}: Specifies the port on which the server should listen (default is 7071).</li>
 *   <li>{@code -c, --config}: Specifies the path to a configuration file, which contains key-value pairs.</li>
 * </ul></p>
 *
 * <p><b>Service Initialization:</b>
 * The server reads the configuration file (if provided) and uses it to load various settings.
 * It then starts the OpenNLP service, including loading models and initializing the POS tagger service.</p>
 *
 * <p>The server can also enable reflection for gRPC, which allows clients to introspect the available services
 * at runtime. This is done by setting {@code server.enable_reflection} to {@code true}.</p>
 *
 * <p><b>Lifecycle Management:</b>
 * The server includes graceful shutdown handling using a shutdown hook to ensure resources are released
 * when the server is stopped.</p>
 */
@Command(name = "OpenNLP Server", mixinStandardHelpOptions = true, version = "2.5.6-SNAPSHOT")
public class OpenNLPServer implements Callable<Integer> {

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(OpenNLPServer.class);

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
  private boolean ready;

  public static void main(String... args) {
    final CommandLine cli = new CommandLine(new OpenNLPServer());
    final int exitCode = cli.execute(args);
    System.exit(exitCode);
  }

  private static void addToConfig(final Map<String, String> config, String line) {
    if (line == null || line.trim().isEmpty()) {
      return;
    }

    final int pos = line.indexOf('=');
    config.put(
        pos == -1 ? line : line.substring(0, pos).trim(),
        pos == -1 ? null : line.substring(pos + 1).trim()
    );
  }

  @Override
  public Integer call() {
    try {
      start();
    } catch (Exception e) {
      logger.error(e.getLocalizedMessage(), e);
      return -1;
    }
    return 0;
  }

  public void start() throws Exception {
    final Map<String, String> configuration = new HashMap<>();

    if (config != null) {
      try {
        // check that the file exists
        if (!new File(config).exists()) {
          logger.error("Config file not found : {}", config);
          System.exit(-1);
        }
        // populate the config with the content of the file
        for (String line : Files.readAllLines(Paths.get(config))) {
          line = line.trim();
          if (line.startsWith("#")) {
            continue;
          }
          addToConfig(configuration, line);
        }
      } catch (Exception e) {
        logger.error("Exception caught when reading the configuration from {}", config, e);
        System.exit(-1);
      }
    }

    final boolean enableReflection =
        Boolean.parseBoolean(
            configuration.getOrDefault("server.enable_reflection", "false"));

    final int maxInboundMessageSize =
            Integer.parseInt(
                    (configuration.getOrDefault("server.max_inbound_message_size", "10485760"))); // 10 MB

    final ServerBuilder<?> builder = ServerBuilder.forPort(port)
        .addService(new PosTaggerService(configuration))
        .addService(new TokenizerService(configuration))
        .addService(new SentenceDetectorService(configuration))
        .maxInboundMessageSize(maxInboundMessageSize);

    if (enableReflection) {
      builder.addService(ProtoReflectionServiceV1.newInstance());
    }


    this.server = builder.build();
    this.server.start();
    logger.info("Started OpenNLPServer on port {}", server.getPort());

    this.ready = true;

    registerShutdownHook();

    blockUntilShutdown();
  }

  private void registerShutdownHook() {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    stop();
                  } catch (Exception e) {
                    logger.error(
                        "Error when trying to shutdown a lifecycle component: {}",
                        this.getClass().getName(),
                        e);
                  }
                }));
  }

  public void stop() {
    if (server != null) {
      try {
        logger.info("Shutting down OpenNLPServer on port {}", server.getPort());
        server.shutdown();
      } catch (RuntimeException ignored) {
      }
    }

  }

  /**
   * Await termination on the main thread since the grpc library uses daemon threads.
   */
  private void blockUntilShutdown() throws InterruptedException {
    if (server != null) {
      server.awaitTermination();
    }
  }

  public boolean isReady() {
    return ready;
  }
}
