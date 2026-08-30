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
package org.apache.opennlp.grpc.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.ChunkSpan;
import org.apache.opennlp.grpc.spi.model.ChunkerModel;
import org.apache.opennlp.grpc.spi.model.ChunkerBackendFactory;

/**
 * Test-only {@link ChunkerBackendFactory} registered via {@code META-INF/services}. It is
 * activated by a {@code model.chunker_stub.id=<id>} configuration entry and otherwise
 * contributes nothing, so it stays inert for every other test. The contributed chunker records
 * per-thread-state clearances so tests can assert the cleanup wiring reaches chunkers without
 * loading a real chunker model.
 */
public final class StubChunkerBackendFactory implements ChunkerBackendFactory {

  public static final String FACTORY_ID = "stub";
  public static final String KEY_ID = "model.chunker_stub.id";

  private static final AtomicInteger CLEAR_COUNT = new AtomicInteger();

  /** @return How many times a stub chunker's per-thread state was cleared since the reset. */
  public static int clearCount() {
    return CLEAR_COUNT.get();
  }

  /** Resets the clearance counter so a test starts from a known state. */
  public static void resetClearCount() {
    CLEAR_COUNT.set(0);
  }

  @Override
  public String factoryId() {
    return FACTORY_ID;
  }

  @Override
  public List<ChunkerModel> create(Map<String, String> configuration) {
    final String id = configuration.get(KEY_ID);
    if (id == null || id.isBlank()) {
      return List.of();
    }
    return List.of(new StubChunkerModel(ChunkerRegistry.normalize(id)));
  }

  /** A chunker that finds nothing and records per-thread-state clearances. */
  private record StubChunkerModel(String id) implements ChunkerModel {

    @Override
    public String backendId() {
      return FACTORY_ID;
    }

    @Override
    public List<ChunkSpan> chunk(AnnotatedSentence sentence) {
      return List.of();
    }

    @Override
    public void clearThreadLocalState() {
      CLEAR_COUNT.incrementAndGet();
    }
  }
}
