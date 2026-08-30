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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.embeddings.index.TurboQuantIndex;
import opennlp.embeddings.index.VectorIndex;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;
import org.apache.opennlp.grpc.spi.search.SearchIndexProviderFactory;
import org.apache.opennlp.grpc.spi.search.SearchIndexProvider;
import org.apache.opennlp.grpc.spi.search.SearchIndexBundleConfiguration;

/** ServiceLoader factory for TurboQuant bundles and live vector components. */
public final class TurboQuantSearchIndexProviderFactory implements SearchIndexProviderFactory {

  /** Stable configuration identifier for this provider. */
  public static final String PROVIDER_ID = "turbo_quant";

  /** Bit width used for every live TurboQuant component. */
  static final int TURBO_QUANT_BITS = 4;
  /** Fixed rotation seed, so equal content quantizes identically across processes. */
  static final long TURBO_QUANT_SEED = 1833L;
  private static final String BITS_OPTION = "bits";
  private static final String SEED_OPTION = "seed";
  private static final long MAX_SEGMENT_IDS_BYTES = 1_048_576;

  /**
   * Immutable typed configuration of one TurboQuant provider instance.
   *
   * @param bits Quantization bits per dimension.
   * @param seed Rotation seed.
   */
  public record Configuration(int bits, long seed) implements ConfiguredProvider {

    /** Validates the provider's typed parameters once during server startup. */
    public Configuration {
      if (bits < opennlp.embeddings.QuantizedEmbeddingMatrix.MIN_BITS
          || bits > opennlp.embeddings.QuantizedEmbeddingMatrix.MAX_BITS) {
        throw new IllegalArgumentException("TurboQuant bits must be between "
            + opennlp.embeddings.QuantizedEmbeddingMatrix.MIN_BITS + " and "
            + opennlp.embeddings.QuantizedEmbeddingMatrix.MAX_BITS + ", was " + bits);
      }
    }

    /** {@inheritDoc} */
    @Override
    public TurboQuantIndex createLiveVectorIndex(int dimension) {
      return new TurboQuantIndex(dimension, bits, seed);
    }

    /** {@inheritDoc} */
    @Override
    public boolean retainRawVectors() {
      return false;
    }

    /** {@inheritDoc} */
    @Override
    public void writeLiveVectorIndex(VectorIndex index, Path directory) throws IOException {
      if (!(index instanceof TurboQuantIndex turboQuantIndex)) {
        throw new IOException("TurboQuant configured provider received a different vector type");
      }
      turboQuantIndex.write(directory);
    }

    /** {@inheritDoc} */
    @Override
    public RestoredVectorIndex readLiveVectorIndex(Path directory) throws IOException {
      final Path idsFile = directory.resolve(TurboQuantIndex.IDS_FILE);
      if (Files.size(idsFile) > MAX_SEGMENT_IDS_BYTES) {
        throw new IOException(idsFile + " exceeds " + MAX_SEGMENT_IDS_BYTES + " bytes");
      }
      final TurboQuantIndex index = TurboQuantIndex.read(directory);
      final List<String> ids = Files.readAllLines(idsFile);
      return new RestoredVectorIndex(index, ids);
    }

    /** {@inheritDoc} */
    @Override
    public String preparationIdentity() {
      return "turbo_quant:" + bits + ":" + seed;
    }
  }

  /** Public constructor required by {@link java.util.ServiceLoader}. */
  public TurboQuantSearchIndexProviderFactory() {
  }

  /** {@inheritDoc} */
  @Override
  public String providerId() {
    return PROVIDER_ID;
  }

  /** {@inheritDoc} */
  @Override
  public Set<SearchProviderCapability> capabilities() {
    return Set.of(
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_VECTOR,
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_LIVE,
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_BUNDLE,
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_PERSISTENT);
  }

  /** {@inheritDoc} */
  @Override
  public ConfiguredProvider configureInstance(
      String instanceId, Map<String, String> options) {
    for (String option : options.keySet()) {
      if (!option.equals(BITS_OPTION) && !option.equals(SEED_OPTION)) {
        throw new IllegalArgumentException("search provider instance '" + instanceId
            + "' has unsupported TurboQuant option '" + option + "'");
      }
    }
    return new Configuration(
        integerOption(options, BITS_OPTION, TURBO_QUANT_BITS),
        longOption(options, SEED_OPTION, TURBO_QUANT_SEED));
  }

  /** {@inheritDoc} */
  @Override
  public SearchIndexProvider load(SearchIndexBundleConfiguration configuration)
      throws IOException {
    return new TurboQuantSearchBundleLoader().load(configuration);
  }

  /** {@inheritDoc} */
  @Override
  public VectorIndex createLiveVectorIndex(int dimension) {
    return new Configuration(TURBO_QUANT_BITS, TURBO_QUANT_SEED)
        .createLiveVectorIndex(dimension);
  }

  /** {@inheritDoc} */
  @Override
  public String preparationIdentity() {
    return new Configuration(TURBO_QUANT_BITS, TURBO_QUANT_SEED).preparationIdentity();
  }

  /**
   * Parses one integer option with a provider default.
   *
   * @param options Provider options.
   * @param key Option name.
   * @param defaultValue Value used when absent.
   * @return Parsed value.
   * @throws IllegalArgumentException If the configured value is not an integer.
   */
  private int integerOption(Map<String, String> options, String key, int defaultValue) {
    final String value = options.get(key);
    try {
      return value == null ? defaultValue : Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("TurboQuant option '" + key
          + "' must be an integer", e);
    }
  }

  /**
   * Parses one long option with a provider default.
   *
   * @param options Provider options.
   * @param key Option name.
   * @param defaultValue Value used when absent.
   * @return Parsed value.
   * @throws IllegalArgumentException If the configured value is not a long integer.
   */
  private long longOption(Map<String, String> options, String key, long defaultValue) {
    final String value = options.get(key);
    try {
      return value == null ? defaultValue : Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("TurboQuant option '" + key
          + "' must be a long integer", e);
    }
  }
}
