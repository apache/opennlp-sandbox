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
package org.apache.opennlp.grpc.spi.embedding;

import java.util.List;

import org.apache.opennlp.grpc.v1.EmbeddingRoute;

/**
 * One batch of vectors together with the concrete route that produced them.
 *
 * @param vectors One vector per input text, in input order.
 * @param route The concrete embedding route that produced the vectors.
 */
public record EmbeddingBatchResult(List<float[]> vectors, EmbeddingRoute route) {

  /** Validates and defensively copies the batch result. */
  public EmbeddingBatchResult {
    if (vectors == null) {
      throw new IllegalArgumentException("vectors must not be null");
    }
    vectors = List.copyOf(vectors);
    if (route == null) {
      throw new IllegalArgumentException("route must not be null");
    }
  }
}
