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

import org.apache.opennlp.grpc.v1.DeleteIndexAliasRequest;
import org.apache.opennlp.grpc.v1.DeleteIndexAliasResponse;
import org.apache.opennlp.grpc.v1.ListIndexAliasesResponse;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.ListSearchProvidersResponse;
import org.apache.opennlp.grpc.v1.PersistIndexRequest;
import org.apache.opennlp.grpc.v1.PersistIndexResponse;
import org.apache.opennlp.grpc.v1.ReindexIndexRequest;
import org.apache.opennlp.grpc.v1.ReindexIndexResponse;
import org.apache.opennlp.grpc.v1.SealIndexRequest;
import org.apache.opennlp.grpc.v1.SealIndexResponse;
import org.apache.opennlp.grpc.v1.SetIndexAliasRequest;
import org.apache.opennlp.grpc.v1.SetIndexAliasResponse;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexRequest;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexResponse;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;

final class EmptySearchRpc implements SearchRpc {

  @Override
  public ListSearchIndexesResponse listSearchIndexes() {
    return ListSearchIndexesResponse.getDefaultInstance();
  }

  @Override
  public ListSearchProvidersResponse listSearchProviders() {
    return ListSearchProvidersResponse.getDefaultInstance();
  }

  @Override
  public SearchIndexResponse search(SearchIndexRequest request) {
    return SearchIndexResponse.getDefaultInstance();
  }

  @Override
  public IndexDocumentsResponse index(IndexDocumentsRequest request) {
    return IndexDocumentsResponse.getDefaultInstance();
  }

  @Override
  public DeleteSearchIndexResponse delete(DeleteSearchIndexRequest request) {
    return DeleteSearchIndexResponse.getDefaultInstance();
  }

  @Override
  public PersistIndexResponse persist(PersistIndexRequest request) {
    return PersistIndexResponse.getDefaultInstance();
  }

  @Override
  public SealIndexResponse seal(SealIndexRequest request) {
    return SealIndexResponse.getDefaultInstance();
  }

  @Override
  public ReindexIndexResponse reindex(ReindexIndexRequest request) {
    return ReindexIndexResponse.getDefaultInstance();
  }

  @Override
  public SetIndexAliasResponse setAlias(SetIndexAliasRequest request) {
    return SetIndexAliasResponse.getDefaultInstance();
  }

  @Override
  public DeleteIndexAliasResponse deleteAlias(DeleteIndexAliasRequest request) {
    return DeleteIndexAliasResponse.getDefaultInstance();
  }

  @Override
  public ListIndexAliasesResponse listAliases() {
    return ListIndexAliasesResponse.getDefaultInstance();
  }
}
