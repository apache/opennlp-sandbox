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
package org.apache.opennlp.grpc.embedding;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Test double returning deterministic or caller-supplied vectors.
 */
public final class StubEmbeddingProvider implements EmbeddingProvider {

  public static final String BACKEND_ID = "stub";

  private final String backendId;
  private final Map<String, Integer> dimensions;
  private final BiFunction<String, String, float[]> embedFn;

  public StubEmbeddingProvider(Map<String, Integer> dimensions) {
    this(BACKEND_ID, dimensions, null);
  }

  public StubEmbeddingProvider(
      Map<String, Integer> dimensions,
      BiFunction<String, String, float[]> embedFn) {
    this(BACKEND_ID, dimensions, embedFn);
  }

  /** Stub with an explicit backend id, so composite tests can model several distinct engines. */
  public StubEmbeddingProvider(
      String backendId,
      Map<String, Integer> dimensions,
      BiFunction<String, String, float[]> embedFn) {
    this.backendId = backendId;
    this.dimensions = Map.copyOf(dimensions);
    this.embedFn = embedFn;
  }

  @Override
  public String backendId() {
    return backendId;
  }

  @Override
  public boolean isAvailable() {
    return !dimensions.isEmpty();
  }

  @Override
  public Set<String> registeredModelIds() {
    return dimensions.keySet();
  }

  @Override
  public boolean supportsModel(String modelId) {
    return dimensions.containsKey(modelId);
  }

  @Override
  public int embeddingDimension(String modelId) {
    return dimensions.getOrDefault(modelId, 0);
  }

  @Override
  public float[] embed(String modelId, String text) {
    if (embedFn != null) {
      return embedFn.apply(modelId, text);
    }
    final int dimension = embeddingDimension(modelId);
    final float[] vector = new float[dimension];
    final int seed = (modelId + ":" + text).hashCode();
    for (int i = 0; i < dimension; i++) {
      vector[i] = (seed + i) * 0.001f;
    }
    return vector;
  }
}
