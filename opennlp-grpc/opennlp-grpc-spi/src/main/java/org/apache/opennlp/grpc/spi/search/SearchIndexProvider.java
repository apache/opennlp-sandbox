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
package org.apache.opennlp.grpc.spi.search;

import java.util.List;

import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;

/**
 * Read-only search provider over one immutable, startup-loaded index.
 *
 * <p>The service owns query embedding and passes a checked vector whose dimension and vector
 * space match {@link #descriptor()}. Implementations do not receive model objects, network
 * callbacks, or mutation operations.</p>
 *
 * <p>The service may invoke {@link #descriptor()} and {@link #search(float[], int)} concurrently.
 * Implementations must support those calls until shutdown begins. The server calls
 * {@link #close()} only after admitted RPCs drain.</p>
 */
public interface SearchIndexProvider extends AutoCloseable {

  /**
   * Returns the immutable index descriptor.
   *
   * @return The descriptor, never {@code null}.
   */
  SearchIndexDescriptor descriptor();

  /**
   * Searches the immutable index.
   *
   * @param queryVector A checked query vector matching the descriptor dimension and vector space.
   * @param topK Maximum number of results to return, at least one.
   * @return At most {@code topK} ranked stored records, never {@code null} and containing no
   *     {@code null} values.
   */
  List<SearchResult> search(float[] queryVector, int topK);

  /**
   * Returns the retained candidates offered to compound query execution.
   *
   * <p>The default returns {@code null}: the index does not execute compound queries,
   * and SearchIndex reports UNIMPLEMENTED for its typed query trees. Providers that
   * retain records return every candidate in stable index order. The optional vector is
   * present only when the provider's execution path needs host-owned raw vectors.</p>
   *
   * @return Candidates in stable index order, or {@code null} when unsupported.
   */
  default List<QueryCandidate> queryCandidates() {
    return null;
  }

  /**
   * Returns the provider-owned keyword component used by compound query term and phrase leaves.
   *
   * @return Immutable keyword component, or {@code null} when compound keyword execution is absent.
   */
  default KeywordQueryIndex keywordQueryIndex() {
    return null;
  }

  /**
   * Releases provider-owned mapped files or native resources.
   *
   * <p>The default implementation is a no-op for providers backed only by ordinary managed
   * Java objects.</p>
   */
  @Override
  default void close() {
  }
}
