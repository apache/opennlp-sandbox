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

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link SentenceDetectorRegistry}'s engine-id validation: ids containing whitespace
 * must be rejected at startup instead of being advertised verbatim.
 */
class SentenceDetectorRegistryTest {

  @Test
  void rejectsWhitespaceInEngineId() {
    for (String id : List.of("onnx ", " onnx", "on nx", "onnx\t")) {
      final AnalysisException error = assertThrows(AnalysisException.class,
          () -> SentenceDetectorRegistry.validId(id, "test"), "id '" + id + "'");
      assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    }
  }

  @Test
  void acceptsPlainLowercaseId() {
    assertEquals("onnx", SentenceDetectorRegistry.validId("onnx", "test"));
  }
}
