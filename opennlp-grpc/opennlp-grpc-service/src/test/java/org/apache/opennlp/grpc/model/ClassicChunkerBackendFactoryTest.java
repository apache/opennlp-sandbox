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
 * Hermetic unit tests for the configuration-key parsing of {@link ClassicChunkerBackendFactory};
 * the configured model files are deliberately absent, so a parsed key fails at load time with a
 * keyed {@link AnalysisException} rather than an opaque {@link StringIndexOutOfBoundsException}.
 */
class ClassicChunkerBackendFactoryTest {

  private final ClassicChunkerBackendFactory factory = new ClassicChunkerBackendFactory();

  @Test
  void noIdPathKeyRegistersTheSingleChunkerUnderTheDefaultId() {
    // The README documents the no-id form "model.chunker.path" for a single chunker.
    final AnalysisException e = assertThrows(AnalysisException.class, () -> factory.create(
        Map.of("model.chunker.path", "/nonexistent/en-chunker.bin")));

    assertEquals(AnalysisException.FailureType.NOT_FOUND, e.getFailureType());
    assertTrue(e.getMessage().contains("id 'default'"),
        e.getMessage());
    assertTrue(e.getMessage().contains("/nonexistent/en-chunker.bin"), e.getMessage());
  }

  @Test
  void noIdPathKeyCollidesWithAnExplicitDefaultId() {
    final AnalysisException e = assertThrows(AnalysisException.class, () -> factory.create(
        Map.of("model.chunker.path", "/nonexistent/a.bin",
            "model.chunker.default.path",
            "/nonexistent/b.bin")));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, e.getFailureType());
    assertTrue(e.getMessage().contains("Duplicate"), e.getMessage());
  }

  @Test
  void blankIdPathKeyIsRejectedWithAKeyedError() {
    final AnalysisException e = assertThrows(AnalysisException.class, () -> factory.create(
        Map.of("model.chunker..path", "/nonexistent/en-chunker.bin")));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, e.getFailureType());
    assertTrue(e.getMessage().contains("model.chunker..path"), e.getMessage());
  }

  @Test
  void idFormStillParses() {
    final AnalysisException e = assertThrows(AnalysisException.class, () -> factory.create(
        Map.of("model.chunker.english.path", "/nonexistent/en-chunker.bin")));

    assertEquals(AnalysisException.FailureType.NOT_FOUND, e.getFailureType());
    assertTrue(e.getMessage().contains("id 'english'"), e.getMessage());
  }
}
