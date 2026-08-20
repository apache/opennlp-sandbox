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
 * KIND, either express or implied.  See the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.search.query;

import org.apache.opennlp.grpc.search.SearchRecord;

/**
 * One indexed chunk offered to compound query execution: its retained search record and
 * its raw embedding vector. The vector is shared, not copied; callers must not mutate it.
 *
 * @param record Retained source and chunk metadata.
 * @param vector Raw indexed embedding.
 */
public record QueryCandidate(SearchRecord record, float[] vector) {

  /** Validates that both parts are present and the vector is non-empty. */
  public QueryCandidate {
    if (record == null) {
      throw new IllegalArgumentException("record must not be null");
    }
    if (vector == null || vector.length == 0) {
      throw new IllegalArgumentException("vector must not be null or empty");
    }
  }
}
