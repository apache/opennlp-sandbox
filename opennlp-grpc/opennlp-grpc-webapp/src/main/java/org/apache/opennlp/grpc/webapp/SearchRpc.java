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

import org.apache.opennlp.grpc.v1.DeleteSearchIndexRequest;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexResponse;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;

interface SearchRpc {

  /** @return Descriptors for the static and dynamic indexes on the gRPC server. */
  ListSearchIndexesResponse listSearchIndexes();

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
}
