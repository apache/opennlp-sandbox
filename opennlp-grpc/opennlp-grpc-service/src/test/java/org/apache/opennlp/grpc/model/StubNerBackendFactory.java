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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.NamedEntity;

/**
 * Test-only {@link NerBackendFactory} registered via {@code META-INF/services}, proving an
 * external jar can contribute a name finder backend without changes to the server. It is
 * activated by a {@code model.name_finder_stub.type=<entity-type>} configuration entry and
 * otherwise contributes nothing, so it stays inert for every other test.
 *
 * <p>A second key, {@code model.name_finder_stub.closeable_type=<entity-type>}, contributes an
 * {@link AutoCloseable} recognizer that records how many times it was closed (via
 * {@link #closeCount()}). It stands in for a DL name finder that holds a native session, so tests
 * can assert the registry releases such models on {@code close()} without loading an ONNX
 * model.</p>
 */
public final class StubNerBackendFactory implements NerBackendFactory {

  public static final String FACTORY_ID = "stub";
  public static final String KEY_TYPE = "model.name_finder_stub.type";
  public static final String KEY_CLOSEABLE_TYPE = "model.name_finder_stub.closeable_type";

  private static final AtomicInteger CLOSE_COUNT = new AtomicInteger();

  /** @return How many times a closeable stub recognizer has been closed since the last reset. */
  public static int closeCount() {
    return CLOSE_COUNT.get();
  }

  /** Resets the close counter so a test starts from a known state. */
  public static void resetCloseCount() {
    CLOSE_COUNT.set(0);
  }

  @Override
  public String factoryId() {
    return FACTORY_ID;
  }

  @Override
  public List<NerModel> create(Map<String, String> configuration, NerBackendContext context) {
    final List<NerModel> models = new ArrayList<>();
    final String type = configuration.get(KEY_TYPE);
    if (type != null && !type.isBlank()) {
      models.add(new StubNerModel(NameFinderRegistry.normalize(type)));
    }
    final String closeableType = configuration.get(KEY_CLOSEABLE_TYPE);
    if (closeableType != null && !closeableType.isBlank()) {
      models.add(new CloseableStubNerModel(NameFinderRegistry.normalize(closeableType)));
    }
    return models;
  }

  /** A recognizer that reports an entity type but finds nothing; enough to prove discovery. */
  private record StubNerModel(String type) implements NerModel {

    @Override
    public String id() {
      return FACTORY_ID + ":" + type;
    }

    @Override
    public String backendId() {
      return FACTORY_ID;
    }

    @Override
    public Set<String> entityTypes() {
      return Set.of(type);
    }

    @Override
    public boolean isStateful() {
      return false;
    }

    @Override
    public void clearAdaptiveData() {
      // Stateless.
    }

    @Override
    public List<NamedEntity> recognize(AnnotatedSentence sentence, boolean includeProbabilities) {
      return List.of();
    }
  }

  /** A recognizer that holds a (pretend) native resource and records its release on close. */
  private static final class CloseableStubNerModel implements NerModel, AutoCloseable {

    private final String type;

    CloseableStubNerModel(String type) {
      this.type = type;
    }

    @Override
    public String id() {
      return FACTORY_ID + ":closeable:" + type;
    }

    @Override
    public String backendId() {
      return FACTORY_ID;
    }

    @Override
    public Set<String> entityTypes() {
      return Set.of(type);
    }

    @Override
    public boolean isStateful() {
      return false;
    }

    @Override
    public void clearAdaptiveData() {
      // Stateless.
    }

    @Override
    public List<NamedEntity> recognize(AnnotatedSentence sentence, boolean includeProbabilities) {
      return List.of();
    }

    @Override
    public void close() {
      CLOSE_COUNT.incrementAndGet();
    }
  }
}
