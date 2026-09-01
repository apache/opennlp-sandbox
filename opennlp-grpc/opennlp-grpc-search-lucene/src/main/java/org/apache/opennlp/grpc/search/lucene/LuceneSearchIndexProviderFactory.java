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
package org.apache.opennlp.grpc.search.lucene;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.opennlp.grpc.spi.search.KeywordQueryIndex;
import org.apache.opennlp.grpc.spi.search.QueryCandidate;
import org.apache.opennlp.grpc.spi.search.SearchIndexProviderFactory;
import org.apache.opennlp.grpc.v1.AnalysisChainDescriptor;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;

/**
 * ServiceLoader factory for the Lucene keyword component: BM25-scored term and phrase
 * execution over an in-memory index built per candidate snapshot, analyzed with the
 * Lucene standard analyzer.
 */
public final class LuceneSearchIndexProviderFactory implements SearchIndexProviderFactory {

  /** Typed stateless configuration of the Lucene keyword provider. */
  public record Configuration() implements ConfiguredProvider {

    /** {@inheritDoc} */
    @Override
    public AnalysisChainDescriptor analysisChain() {
      return chain();
    }

    /** {@inheritDoc} */
    @Override
    public KeywordQueryIndex createKeywordQueryIndex(List<QueryCandidate> candidates) {
      return new LuceneKeywordQueryIndex(candidates);
    }
  }

  /** Stable configuration identifier for this provider. */
  public static final String PROVIDER_ID = "lucene";

  /**
   * Identity of the analysis chain: the Lucene standard analyzer, Unicode
   * word-boundary tokenization with locale-independent lower-casing.
   */
  public static final String CHAIN_ID = "lucene-standard";

  /** Version of the analysis chain's behavior, following the Lucene major version. */
  public static final String CHAIN_VERSION = "10";

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public LuceneSearchIndexProviderFactory() {
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
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_KEYWORD,
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_LIVE);
  }

  /** {@inheritDoc} */
  @Override
  public ConfiguredProvider configureInstance(
      String instanceId, Map<String, String> options) {
    if (options == null) {
      throw new IllegalArgumentException("provider options must not be null");
    }
    if (!options.isEmpty()) {
      throw new IllegalArgumentException("search provider instance '" + instanceId
          + "' does not support Lucene provider options " + options.keySet());
    }
    return new Configuration();
  }

  /** {@inheritDoc} */
  @Override
  public AnalysisChainDescriptor analysisChain() {
    return chain();
  }

  /** Returns the chain descriptor shared by default and configured instances. */
  private static AnalysisChainDescriptor chain() {
    return AnalysisChainDescriptor.newBuilder()
        .setChainId(CHAIN_ID)
        .setChainVersion(CHAIN_VERSION)
        .build();
  }
}
