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
 * Hermetic unit tests for the configuration-key parsing of {@link ClassicParserBackendFactory};
 * the configured model files are deliberately absent, so a parsed key fails at load time with a
 * keyed {@link AnalysisException} rather than an opaque {@link StringIndexOutOfBoundsException}.
 */
class ClassicParserBackendFactoryTest {

  private final ClassicParserBackendFactory factory = new ClassicParserBackendFactory();

  @Test
  void noIdPathKeyRegistersTheSingleParserUnderTheDefaultId() {
    // The README documents the no-id form "model.parser.path" for a single parser.
    final AnalysisException e = assertThrows(AnalysisException.class, () -> factory.create(
        Map.of("model.parser.path", "/nonexistent/en-parser-chunking.bin")));

    assertEquals(AnalysisException.FailureType.NOT_FOUND, e.getFailureType());
    assertTrue(e.getMessage().contains("id 'default'"),
        e.getMessage());
    assertTrue(e.getMessage().contains("/nonexistent/en-parser-chunking.bin"), e.getMessage());
  }

  @Test
  void noIdPathKeyCollidesWithAnExplicitDefaultId() {
    final AnalysisException e = assertThrows(AnalysisException.class, () -> factory.create(
        Map.of("model.parser.path", "/nonexistent/a.bin",
            "model.parser.default.path",
            "/nonexistent/b.bin")));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, e.getFailureType());
    assertTrue(e.getMessage().contains("Duplicate"), e.getMessage());
  }

  @Test
  void blankIdPathKeyIsRejectedWithAKeyedError() {
    final AnalysisException e = assertThrows(AnalysisException.class, () -> factory.create(
        Map.of("model.parser..path", "/nonexistent/en-parser-chunking.bin")));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, e.getFailureType());
    assertTrue(e.getMessage().contains("model.parser..path"), e.getMessage());
  }

  @Test
  void idFormStillParses() {
    final AnalysisException e = assertThrows(AnalysisException.class, () -> factory.create(
        Map.of("model.parser.english.path", "/nonexistent/en-parser-chunking.bin")));

    assertEquals(AnalysisException.FailureType.NOT_FOUND, e.getFailureType());
    assertTrue(e.getMessage().contains("id 'english'"), e.getMessage());
  }
}
