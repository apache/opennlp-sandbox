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

import java.util.Set;

import opennlp.embeddings.index.FlatFloatIndex;
import opennlp.embeddings.index.VectorIndex;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;

/** ServiceLoader factory for exact in-memory flat float vector components. */
public final class FlatFloatSearchIndexProviderFactory implements SearchIndexProviderFactory {

  /** Stable configuration identifier for this provider. */
  public static final String PROVIDER_ID = "flat_float";

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
        SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_LIVE);
  }

  /** {@inheritDoc} */
  @Override
  public VectorIndex createLiveVectorIndex(int dimension) {
    return new FlatFloatIndex(dimension);
  }
}
