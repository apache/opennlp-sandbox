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
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingBackendFactory;

/** ServiceLoader test backend that records whether its provider was closed. */
public final class TrackingEmbeddingBackendFactory implements EmbeddingBackendFactory {

  public static final String KEY_MODEL_ID = "test.embedder.tracking.model_id";

  private static final AtomicBoolean CLOSED = new AtomicBoolean();

  static void reset() {
    CLOSED.set(false);
  }

  static boolean wasClosed() {
    return CLOSED.get();
  }

  @Override
  public String backendId() {
    return "tracking";
  }

  @Override
  public EmbeddingProvider create(Map<String, String> configuration) {
    final String modelId = configuration.get(KEY_MODEL_ID);
    return new TrackingProvider(modelId == null || modelId.isBlank() ? null : modelId);
  }

  private static final class TrackingProvider implements EmbeddingProvider, AutoCloseable {

    private final String modelId;

    private TrackingProvider(String modelId) {
      this.modelId = modelId;
    }

    @Override
    public String backendId() {
      return "tracking";
    }

    @Override
    public boolean isAvailable() {
      return modelId != null;
    }

    @Override
    public Set<String> registeredModelIds() {
      return modelId == null ? Set.of() : Set.of(modelId);
    }

    @Override
    public boolean supportsModel(String modelId) {
      return this.modelId != null && this.modelId.equals(modelId);
    }

    @Override
    public int embeddingDimension(String modelId) {
      return supportsModel(modelId) ? 3 : 0;
    }

    @Override
    public float[] embed(String modelId, String text) {
      return new float[] {9.0f, 9.0f, 9.0f};
    }

    @Override
    public void close() {
      CLOSED.set(true);
    }
  }
}
