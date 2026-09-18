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
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.function.Function;

import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.embedding.EmbeddingProviderFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Builds one immutable TurboQuant search bundle from normalized CasePassage JSONL. */
@Command(
    name = "opennlp-build-turbo-quant-search-bundle",
    mixinStandardHelpOptions = true,
    version = TurboQuantSearchBundleBuilder.BUILDER_VERSION,
    description = "Build a bounded TurboQuant search bundle using a configured embedding backend.")
public final class TurboQuantSearchBundleCommand implements Callable<Integer> {

  private static final long MAX_SERVER_CONFIG_BYTES = 16L * 1024 * 1024;

  @Option(names = "--server-config", required = true,
      description = "Existing OpenNLP gRPC server properties file.")
  private Path serverConfig;

  @Option(names = "--passages", required = true,
      description = "Normalized CasePassage JSON Lines input.")
  private Path passagesFile;

  @Option(names = "--preparation-config", required = true,
      description = "Configuration file that prepared the normalized passages.")
  private Path preparationConfigFile;

  @Option(names = "--output-dir", required = true,
      description = "New bundle directory. Existing paths are never replaced.")
  private Path outputDirectory;

  @Option(names = "--index-id", required = true, description = "Stable index identifier.")
  private String indexId;

  @Option(names = "--display-name", required = true, description = "Human-readable index name.")
  private String displayName;

  @Option(names = "--model-id", required = true,
      description = "Logical model id from the server configuration.")
  private String modelId;

  @Option(names = "--backend-id", defaultValue = "",
      description = "Concrete backend id. Blank selects the configured primary route.")
  private String backendId;

  @Option(names = "--bits", defaultValue = "4",
      description = "TurboQuant bit width from 2 through 4.")
  private int bits;

  @Option(names = "--seed", defaultValue = "42", description = "TurboQuant rotation seed.")
  private long seed;

  @Option(names = "--corpus-title", required = true, description = "Corpus title.")
  private String corpusTitle;

  @Option(names = "--corpus-provenance", required = true,
      description = "Human-readable corpus origin and preparation summary.")
  private String corpusProvenance;

  @Option(names = "--corpus-source-uri", required = true,
      description = "Absolute corpus source URI.")
  private URI corpusSourceUri;

  @Option(names = "--license-name", required = true,
      description = "Corpus license identifier or name.")
  private String licenseName;

  @Option(names = "--license-uri", required = true,
      description = "Absolute corpus license URI.")
  private URI licenseUri;

  @Option(names = "--max-records", defaultValue = "100000",
      description = "Maximum input records.")
  private int maxRecords;

  @Option(names = "--max-input-bytes", defaultValue = "1073741824",
      description = "Maximum bytes in either input file.")
  private long maxInputBytes;

  @Option(names = "--max-query-bytes", defaultValue = "1048576",
      description = "Maximum UTF-8 bytes in one passage embedding query.")
  private int maxQueryBytes;

  @Option(names = "--batch-size", defaultValue = "32",
      description = "Maximum records per embedding call.")
  private int batchSize;

  @Option(names = "--max-batch-bytes", defaultValue = "8388608",
      description = "Maximum UTF-8 passage bytes per embedding call.")
  private long maxBatchBytes;

  @Option(names = "--max-output-bytes", defaultValue = "8589934592",
      description = "Maximum bytes in the generated bundle.")
  private long maxOutputBytes;

  @Spec
  private CommandSpec commandSpec;

  private final Function<Map<String, String>, EmbeddingProvider> providerFactory;

  /** Creates the production command backed by {@link EmbeddingProviderFactory}. */
  public TurboQuantSearchBundleCommand() {
    this(EmbeddingProviderFactory::create);
  }

  /**
   * Creates a command with an injected provider factory for testing.
   *
   * @param providerFactory Factory initialized from server properties.
   * @throws IllegalArgumentException If {@code providerFactory} is {@code null}.
   */
  TurboQuantSearchBundleCommand(
      Function<Map<String, String>, EmbeddingProvider> providerFactory) {
    if (providerFactory == null) {
      throw new IllegalArgumentException("providerFactory must not be null");
    }
    this.providerFactory = providerFactory;
  }

  /**
   * Runs this command and exits the JVM with picocli's status code.
   *
   * @param args Command-line options.
   */
  public static void main(String... args) {
    final int exitCode = new CommandLine(new TurboQuantSearchBundleCommand()).execute(args);
    System.exit(exitCode);
  }

  /**
   * {@inheritDoc}
   *
   * @throws Exception If configuration loading, bundle construction, or provider cleanup fails.
   */
  @Override
  public Integer call() throws Exception {
    final Path output = absoluteNormalized(outputDirectory);
    if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Refusing to overwrite existing output path " + output);
    }
    final Map<String, String> serverConfiguration = loadServerConfiguration(
        absoluteNormalized(serverConfig));
    final EmbeddingProvider provider = providerFactory.apply(serverConfiguration);
    if (provider == null) {
      throw new IOException("Embedding provider factory returned null");
    }
    Throwable failure = null;
    try {
      if (!provider.isAvailable()) {
        throw new IOException("Server configuration loads no available embedding backend");
      }
      if (!provider.supportsModel(modelId)) {
        throw new IOException("Server configuration does not provide embedding model '"
            + modelId + "'");
      }
      if (backendId != null && !backendId.isBlank()
          && !provider.supportsModel(modelId, backendId.trim())) {
        throw new IOException("Embedding backend '" + backendId
            + "' does not serve model '" + modelId + "'");
      }
      final TurboQuantSearchBundleConfiguration configuration =
          new TurboQuantSearchBundleConfiguration(
              absoluteNormalized(passagesFile),
              absoluteNormalized(preparationConfigFile),
              output,
              indexId,
              displayName,
              modelId,
              backendId,
              bits,
              seed,
              new TurboQuantSearchBundleConfiguration.CorpusMetadata(
                  corpusTitle, corpusProvenance, corpusSourceUri, licenseName, licenseUri),
              new TurboQuantSearchBundleConfiguration.Limits(
                  maxRecords,
                  maxInputBytes,
                  maxQueryBytes,
                  batchSize,
                  maxBatchBytes,
                  maxOutputBytes));
      final TurboQuantSearchBundleBuilder.BuildResult result =
          new TurboQuantSearchBundleBuilder(provider).build(configuration);
      commandSpec.commandLine().getOut().println("Built " + result.recordCount()
          + " records in " + result.outputDirectory());
      commandSpec.commandLine().getOut().println("Embedding route: "
          + result.embeddingRoute().getModelId() + "/"
          + result.embeddingRoute().getBackendId() + "/"
          + result.embeddingRoute().getVectorSpaceId());
      commandSpec.commandLine().getOut().println(
          "Bundle SHA-256: " + result.bundleArtifactHash());
      return 0;
    } catch (Exception | Error e) {
      failure = e;
      throw e;
    } finally {
      try {
        close(provider);
      } catch (Exception | Error closeFailure) {
        if (failure != null) {
          failure.addSuppressed(closeFailure);
        } else {
          throw closeFailure;
        }
      }
    }
  }

  /**
   * Loads one bounded server properties file into an immutable map.
   *
   * @param file Server properties file.
   * @return Immutable server configuration.
   * @throws IOException If the file is invalid, too large, or cannot be read.
   */
  private Map<String, String> loadServerConfiguration(Path file) throws IOException {
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("server-config must be a regular file: " + file);
    }
    final long bytes = Files.size(file);
    if (bytes > MAX_SERVER_CONFIG_BYTES) {
      throw new IOException("server-config exceeds " + MAX_SERVER_CONFIG_BYTES + " bytes");
    }
    final Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
    }
    final Map<String, String> result = new HashMap<>();
    for (String name : properties.stringPropertyNames()) {
      result.put(name, properties.getProperty(name));
    }
    return Map.copyOf(result);
  }

  /**
   * Resolves a command-line path to its absolute normalized form.
   *
   * @param path Command-line path.
   * @return Absolute normalized path.
   * @throws IllegalArgumentException If {@code path} is {@code null}.
   */
  private Path absoluteNormalized(Path path) {
    if (path == null) {
      throw new IllegalArgumentException("Path option must not be null");
    }
    return path.toAbsolutePath().normalize();
  }

  /**
   * Closes a provider when its implementation has a lifecycle.
   *
   * @param provider Provider to close.
   * @throws Exception If provider cleanup fails.
   */
  private void close(EmbeddingProvider provider) throws Exception {
    if (provider instanceof AutoCloseable closeable) {
      closeable.close();
    }
  }
}
