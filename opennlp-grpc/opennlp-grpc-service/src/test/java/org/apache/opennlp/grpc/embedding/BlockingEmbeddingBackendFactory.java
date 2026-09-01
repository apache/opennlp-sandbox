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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingBackendFactory;

/** Test backend whose embedding call can be held open while server shutdown begins. */
public final class BlockingEmbeddingBackendFactory implements EmbeddingBackendFactory {

  public static final String KEY_MODEL_ID = "test.embedder.blocking.model_id";

  private static volatile CountDownLatch started = new CountDownLatch(1);
  private static volatile CountDownLatch release = new CountDownLatch(1);
  private static final AtomicBoolean closed = new AtomicBoolean();

  /** Resets all lifecycle probes before constructing a server under test. */
  public static void reset() {
    started = new CountDownLatch(1);
    release = new CountDownLatch(1);
    closed.set(false);
  }

  /** Waits until the configured provider has entered its embedding call. */
  public static boolean awaitStarted(long timeout, TimeUnit unit) throws InterruptedException {
    return started.await(timeout, unit);
  }

  /** Allows the held embedding call to return. */
  public static void release() {
    release.countDown();
  }

  /** Returns whether the configured provider has been closed. */
  public static boolean wasClosed() {
    return closed.get();
  }

  @Override
  public String backendId() {
    return "blocking";
  }

  @Override
  public EmbeddingProvider create(Map<String, String> configuration) {
    return new BlockingProvider(configuration.get(KEY_MODEL_ID));
  }

  private static final class BlockingProvider implements EmbeddingProvider, AutoCloseable {

    private final String modelId;

    private BlockingProvider(String modelId) {
      this.modelId = modelId == null || modelId.isBlank() ? null : modelId;
    }

    @Override
    public String backendId() {
      return "blocking";
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
    public boolean supportsModel(String requestedModelId) {
      return modelId != null && modelId.equals(requestedModelId);
    }

    @Override
    public int embeddingDimension(String requestedModelId) {
      return supportsModel(requestedModelId) ? 3 : 0;
    }

    @Override
    public float[] embed(String requestedModelId, String text) {
      started.countDown();
      try {
        if (!release.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("blocking embedding test timed out");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("blocking embedding test interrupted", e);
      }
      return new float[] {1.0f, 2.0f, 3.0f};
    }

    @Override
    public void close() {
      if (modelId != null) {
        closed.set(true);
      }
    }
  }
}
