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
package org.apache.opennlp.grpc.search;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.embeddings.index.FlatFloatIndex;
import opennlp.embeddings.index.VectorIndex;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;
import org.apache.opennlp.grpc.spi.search.SearchIndexProviderFactory;

/**
 * ServiceLoader factory for exact in-memory flat float vector components.
 *
 * <p>The components persist: a checkpoint writes the full-precision rows and their ids
 * through {@link FlatFloatIndex#write(Path)} and restores them with
 * {@link FlatFloatIndex#read(Path)}, so a live index built on exact storage survives a
 * restart and can be made read-only like any other.</p>
 */
public final class FlatFloatSearchIndexProviderFactory implements SearchIndexProviderFactory {

  /** Stable configuration identifier for this provider. */
  public static final String PROVIDER_ID = "flat_float";

  /** Largest accepted id file of one persisted vector segment, in bytes. */
  static final long MAX_SEGMENT_IDS_BYTES = 64L * 1024 * 1024;

  /** Public constructor required by {@link java.util.ServiceLoader}. */
  public FlatFloatSearchIndexProviderFactory() {
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
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_PERSISTENT);
  }

  /** {@inheritDoc} */
  @Override
  public VectorIndex createLiveVectorIndex(int dimension) {
    return new FlatFloatIndex(dimension);
  }

  /** {@inheritDoc} */
  @Override
  public ConfiguredProvider configureInstance(String instanceId, Map<String, String> options) {
    if (options == null) {
      throw new IllegalArgumentException("provider options must not be null");
    }
    if (!options.isEmpty()) {
      throw new IllegalArgumentException("search provider instance '" + instanceId
          + "' does not support options " + options.keySet());
    }
    return new FlatFloatConfiguredProvider();
  }

  /** The single configuration of exact storage: full-precision rows that persist as written. */
  private static final class FlatFloatConfiguredProvider implements ConfiguredProvider {

    /** {@inheritDoc} */
    @Override
    public VectorIndex createLiveVectorIndex(int dimension) {
      return new FlatFloatIndex(dimension);
    }

    /** {@inheritDoc} */
    @Override
    public void writeLiveVectorIndex(VectorIndex index, Path directory) throws IOException {
      if (!(index instanceof FlatFloatIndex flatFloatIndex)) {
        throw new IOException("flat float provider received a different vector type");
      }
      flatFloatIndex.write(directory);
    }

    /** {@inheritDoc} */
    @Override
    public RestoredVectorIndex readLiveVectorIndex(Path directory) throws IOException {
      if (directory == null) {
        throw new IllegalArgumentException("directory must not be null");
      }
      final Path idsFile = directory.resolve(FlatFloatIndex.IDS_FILE);
      if (Files.size(idsFile) > MAX_SEGMENT_IDS_BYTES) {
        throw new IOException(idsFile + " exceeds " + MAX_SEGMENT_IDS_BYTES + " bytes");
      }
      final FlatFloatIndex index;
      try {
        index = FlatFloatIndex.read(directory);
      } catch (IllegalArgumentException e) {
        // A missing or incomplete segment directory is an unreadable segment, not a caller bug.
        throw new IOException(e.getMessage(), e);
      }
      final List<String> ids = Files.readAllLines(idsFile);
      return new RestoredVectorIndex(index, ids);
    }
  }
}
