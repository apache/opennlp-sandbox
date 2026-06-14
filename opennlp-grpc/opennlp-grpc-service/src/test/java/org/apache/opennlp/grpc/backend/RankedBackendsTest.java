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
package org.apache.opennlp.grpc.backend;

import java.util.List;
import java.util.Set;

import org.apache.opennlp.grpc.backend.RankedBackends.Registration;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the generic multi-backend resolver {@link RankedBackends}: priority ordering,
 * fallback, strongly-typed engine pinning (id and engine are separate arguments — no string
 * parsing), advertised ids, and registration validation.
 */
class RankedBackendsTest {

  private static RankedBackends<String> twoEngineModel() {
    // logical "a" on e1 (priority 10) and e2 (priority 20); "b" only on e1.
    return RankedBackends.<String>builder()
        .add("a", "e1", 10, "A-on-e1")
        .add("a", "e2", 20, "A-on-e2")
        .add("b", "e1", 0, "B-on-e1")
        .build();
  }

  @Test
  void resolvesByDescendingPriority() {
    final List<Registration<String>> ranked = twoEngineModel().resolve("a");
    assertEquals(2, ranked.size());
    assertEquals("e2", ranked.get(0).engineId());   // priority 20 first
    assertEquals("e1", ranked.get(1).engineId());
    assertEquals(List.of("e2", "e1"), twoEngineModel().enginesFor("a"));
  }

  @Test
  void resolvesAPinnedEngineByTypedArgument() {
    final Registration<String> pinned = twoEngineModel().resolve("a", "e1");
    assertEquals("A-on-e1", pinned.value());
  }

  @Test
  void advertisesLogicalIdsAndEngines() {
    final RankedBackends<String> ranked = twoEngineModel();
    assertEquals(Set.of("a", "b"), ranked.ids());
    assertTrue(ranked.supports("a"));
    assertTrue(ranked.supports("a", "e2"));
    assertFalse(ranked.supports("a", "e9"));
  }

  @Test
  void invokeUsesPrimaryThenFallsBackOnFailure() {
    final RankedBackends<String> ranked = twoEngineModel();
    assertEquals("A-on-e2", ranked.invoke("a", Registration::value));
    final String result = ranked.invoke("a", reg -> {
      if (reg.engineId().equals("e2")) {
        throw new IllegalStateException("e2 down");
      }
      return reg.value();
    });
    assertEquals("A-on-e1", result);
  }

  @Test
  void invokeRethrowsWhenEveryEngineFails() {
    assertThrows(IllegalStateException.class, () -> twoEngineModel().invoke("a", reg -> {
      throw new IllegalStateException("all down");
    }));
  }

  @Test
  void pinnedEngineDoesNotFallBack() {
    assertThrows(IllegalStateException.class, () -> twoEngineModel().invoke("a", "e2", reg -> {
      throw new IllegalStateException("e2 down");
    }));
  }

  @Test
  void resolveUnknownIdThrowsNotFound() {
    final AnalysisException error =
        assertThrows(AnalysisException.class, () -> twoEngineModel().resolve("missing"));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }

  @Test
  void resolveUnknownEngineForKnownIdThrowsNotFound() {
    final AnalysisException error =
        assertThrows(AnalysisException.class, () -> twoEngineModel().resolve("a", "e9"));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }

  @Test
  void duplicateRegistrationIsRejected() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        RankedBackends.<String>builder().add("a", "e1", 0, "x").add("a", "e1", 5, "y"));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }
}
