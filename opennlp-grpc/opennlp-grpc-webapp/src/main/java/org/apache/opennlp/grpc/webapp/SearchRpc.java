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
package org.apache.opennlp.grpc.webapp;

import java.util.Iterator;

import org.apache.opennlp.grpc.v1.CollectionEvent;
import org.apache.opennlp.grpc.v1.DeleteCollectionRequest;
import org.apache.opennlp.grpc.v1.DeleteCollectionResponse;
import org.apache.opennlp.grpc.v1.DeleteIndexAliasRequest;
import org.apache.opennlp.grpc.v1.DeleteIndexAliasResponse;
import org.apache.opennlp.grpc.v1.GetCollectionRequest;
import org.apache.opennlp.grpc.v1.GetCollectionResponse;
import org.apache.opennlp.grpc.v1.ListCollectionsResponse;
import org.apache.opennlp.grpc.v1.SetCollectionRequest;
import org.apache.opennlp.grpc.v1.SetCollectionResponse;
import org.apache.opennlp.grpc.v1.WatchCollectionRequest;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexRequest;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexResponse;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.ListIndexAliasesResponse;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.ListSearchProvidersResponse;
import org.apache.opennlp.grpc.v1.PersistIndexRequest;
import org.apache.opennlp.grpc.v1.PersistIndexResponse;
import org.apache.opennlp.grpc.v1.ReindexIndexRequest;
import org.apache.opennlp.grpc.v1.ReindexIndexResponse;
import org.apache.opennlp.grpc.v1.SealIndexRequest;
import org.apache.opennlp.grpc.v1.SealIndexResponse;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;
import org.apache.opennlp.grpc.v1.SetIndexAliasRequest;
import org.apache.opennlp.grpc.v1.SetIndexAliasResponse;

interface SearchRpc {

  /** @return Descriptors for the static and dynamic indexes on the gRPC server. */
  ListSearchIndexesResponse listSearchIndexes();

  /** @return Configured search provider instances and their capabilities. */
  ListSearchProvidersResponse listSearchProviders();

  /**
   * Persists one dynamic index as a checkpoint.
   *
   * @param request Index id or alias to persist.
   * @return Descriptor with its persisted flag set.
   */
  PersistIndexResponse persist(PersistIndexRequest request);

  /**
   * Persists one dynamic index and marks it immutable.
   *
   * @param request Index id or alias to seal.
   * @return Descriptor, immutable and persisted.
   */
  SealIndexResponse seal(SealIndexRequest request);

  /**
   * Builds a new index beside an existing one under a new embedding selection.
   *
   * @param request Source, embedding selection, and optional alias swap.
   * @return The newly built index and replay counts.
   */
  ReindexIndexResponse reindex(ReindexIndexRequest request);

  /**
   * Creates or repoints one alias.
   *
   * @param request Alias name and target index id.
   * @return The stored alias.
   */
  SetIndexAliasResponse setAlias(SetIndexAliasRequest request);

  /**
   * Deletes one alias.
   *
   * @param request Alias name.
   * @return Whether the alias existed and was deleted.
   */
  DeleteIndexAliasResponse deleteAlias(DeleteIndexAliasRequest request);

  /** @return Every alias in stable alias order. */
  ListIndexAliasesResponse listAliases();

  /**
   * Searches one server-owned index.
   *
   * @param request The document-shaped query and bounded result count.
   * @return Ranked source passages.
   */
  SearchIndexResponse search(SearchIndexRequest request);

  /**
   * Adds analyzed document shapes to a server-owned dynamic index.
   *
   * @param request Documents and embedding selection.
   * @return Published index snapshot summary.
   */
  IndexDocumentsResponse index(IndexDocumentsRequest request);

  /**
   * Deletes one server-owned dynamic index.
   *
   * @param request Index identifier to delete.
   * @return Deletion result.
   */
  DeleteSearchIndexResponse delete(DeleteSearchIndexRequest request);

  /**
   * Creates or replaces one collection.
   *
   * @param request Complete configured collection state.
   * @return Descriptor with its recomputed term statistics and drift.
   */
  SetCollectionResponse setCollection(SetCollectionRequest request);

  /**
   * Reads one collection with its recomputed term statistics and drift.
   *
   * @param request Collection id.
   * @return The descriptor.
   */
  GetCollectionResponse getCollection(GetCollectionRequest request);

  /** @return Every collection, without term statistics. */
  ListCollectionsResponse listCollections();

  /**
   * Deletes one collection.
   *
   * @param request Collection id.
   * @return Whether the collection existed and was deleted.
   */
  DeleteCollectionResponse deleteCollection(DeleteCollectionRequest request);

  /**
   * Watches one collection's self-contained snapshot events.
   *
   * @param request Collection id.
   * @return Events beginning with a snapshot, until the stream ends.
   */
  Iterator<CollectionEvent> watchCollection(WatchCollectionRequest request);
}
