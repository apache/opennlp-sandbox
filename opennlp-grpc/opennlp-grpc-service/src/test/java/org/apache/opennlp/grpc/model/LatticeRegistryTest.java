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

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link LatticeRegistry} configuration-key validation: a malformed id in a
 * {@code model.lattice.<id>.dir} key must fail startup naming the key instead of silently
 * dropping the entry.
 */
class LatticeRegistryTest {

  @Test
  void dottedDictionaryIdIsRejectedNamingTheKey() {
    final String key = "model.lattice.my.dict.dir";
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> LatticeRegistry.create(Map.of(key, "/tmp/whatever")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains(key),
        "the error must name the offending key: " + error.getMessage());
  }

  @Test
  void blankDictionaryIdIsRejectedNamingTheKey() {
    final String key = "model.lattice..dir";
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> LatticeRegistry.create(Map.of(key, "/tmp/whatever")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains(key),
        "the error must name the offending key: " + error.getMessage());
  }
}
