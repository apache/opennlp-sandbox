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

/** ServiceLoader test backend that records whether its provider was closed. */
public final class TrackingEmbeddingBackendFactory implements EmbeddingBackendFactory {

  static final String KEY_ENABLED = "test.embedder.tracking.enabled";

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
    final boolean enabled = Boolean.parseBoolean(configuration.get(KEY_ENABLED));
    return new TrackingProvider(enabled);
  }

  private static final class TrackingProvider implements EmbeddingProvider, AutoCloseable {

    private final boolean enabled;

    private TrackingProvider(boolean enabled) {
      this.enabled = enabled;
    }

    @Override
    public String backendId() {
      return "tracking";
    }

    @Override
    public boolean isAvailable() {
      return enabled;
    }

    @Override
    public Set<String> registeredModelIds() {
      return enabled ? Set.of("tracking-model") : Set.of();
    }

    @Override
    public boolean supportsModel(String modelId) {
      return enabled && "tracking-model".equals(modelId);
    }

    @Override
    public int embeddingDimension(String modelId) {
      return supportsModel(modelId) ? 1 : 0;
    }

    @Override
    public float[] embed(String modelId, String text) {
      return new float[] {1.0f};
    }

    @Override
    public void close() {
      CLOSED.set(true);
    }
  }
}
