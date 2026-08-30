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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;

/**
 * Test double that violates the {@link EmbeddingProvider#embedBatch} contract by returning
 * {@code texts.size() + delta} vectors, so tests can verify callers reconcile the batch size
 * with the input count instead of silently dropping or overrunning embeddings.
 */
public final class MiscountingEmbeddingProvider implements EmbeddingProvider {

  private final String modelId;
  private final int dimension;
  private final int delta;

  public MiscountingEmbeddingProvider(String modelId, int dimension, int delta) {
    this.modelId = modelId;
    this.dimension = dimension;
    this.delta = delta;
  }

  @Override
  public String backendId() {
    return "miscounting";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public Set<String> registeredModelIds() {
    return Set.of(modelId);
  }

  @Override
  public boolean supportsModel(String modelId) {
    return this.modelId.equals(modelId);
  }

  @Override
  public int embeddingDimension(String modelId) {
    return dimension;
  }

  @Override
  public float[] embed(String modelId, String text) {
    return new float[dimension];
  }

  @Override
  public List<float[]> embedBatch(String modelId, List<String> texts) {
    final List<float[]> vectors = new ArrayList<>(texts.size() + delta);
    for (int i = 0; i < texts.size() + delta; i++) {
      vectors.add(new float[dimension]);
    }
    return vectors;
  }
}
