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

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that {@link ModelBundleCache#clearThreadLocalState()} reaches every component family
 * that keeps per-thread state on the pooled analysis workers. The chunker and parser are
 * contributed by stub backends (registered via test {@code META-INF/services}) that record
 * their clearance, so no real chunker or parser model is needed.
 */
class ModelBundleCacheThreadLocalStateTest {

  @Test
  void clearThreadLocalStateReleasesChunkerAndParserPerThreadState() {
    StubChunkerBackendFactory.resetClearCount();
    StubParserBackendFactory.resetClearCount();
    final ModelBundleCache cache = new ModelBundleCache(Map.of(
        StubChunkerBackendFactory.KEY_ID, "chunky",
        StubParserBackendFactory.KEY_ID, "parsy"));

    cache.clearThreadLocalState();

    assertEquals(1, StubChunkerBackendFactory.clearCount(),
        "ChunkerME per-thread state survives clearThreadLocalState on the shared cache");
    assertEquals(1, StubParserBackendFactory.clearCount(),
        "the per-thread Parser of a classic parser model survives clearThreadLocalState");
  }
}
