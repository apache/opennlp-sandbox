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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.opennlp.grpc.spi.search.KeywordQueryIndex;
import org.apache.opennlp.grpc.spi.search.QueryCandidate;
import org.apache.opennlp.grpc.search.query.TermsKeywordQueryIndex;
import org.apache.opennlp.grpc.v1.AnalysisChainDescriptor;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;
import org.apache.opennlp.grpc.spi.search.SearchIndexProviderFactory;

/**
 * ServiceLoader factory for the built-in keyword component executing term and phrase clauses
 * over retained indexed text.
 */
public final class TermsSearchIndexProviderFactory implements SearchIndexProviderFactory {

  /** Typed stateless configuration of the built-in term provider. */
  public record Configuration() implements ConfiguredProvider {

    /** {@inheritDoc} */
    @Override
    public AnalysisChainDescriptor analysisChain() {
      return chain();
    }

    /** {@inheritDoc} */
    @Override
    public KeywordQueryIndex createKeywordQueryIndex(List<QueryCandidate> candidates) {
      return new TermsKeywordQueryIndex(candidates);
    }
  }

  /** Stable configuration identifier for this provider. */
  public static final String PROVIDER_ID = "terms";

  /**
   * Identity of the built-in analysis chain: terms are maximal runs of Unicode
   * letter-or-digit code points, lower-cased with locale-independent case folding.
   */
  public static final String CHAIN_ID = "opennlp-terms-codepoint-lower";

  /** Version of the built-in analysis chain's behavior. */
  public static final String CHAIN_VERSION = "1";

  /** Public constructor required by {@link java.util.ServiceLoader}. */
  public TermsSearchIndexProviderFactory() {
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
    if (!options.isEmpty()) {
      throw new IllegalArgumentException("search provider instance '" + instanceId
          + "' does not support term-provider options " + options.keySet());
    }
    return new Configuration();
  }

  /** {@inheritDoc} */
  @Override
  public AnalysisChainDescriptor analysisChain() {
    return chain();
  }

  /** Returns the built-in chain descriptor shared by default and configured instances. */
  private static AnalysisChainDescriptor chain() {
    return AnalysisChainDescriptor.newBuilder()
        .setChainId(CHAIN_ID)
        .setChainVersion(CHAIN_VERSION)
        .build();
  }
}
