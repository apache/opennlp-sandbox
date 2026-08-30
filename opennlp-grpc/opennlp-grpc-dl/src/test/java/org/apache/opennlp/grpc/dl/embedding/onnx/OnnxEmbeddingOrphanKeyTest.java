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
package org.apache.opennlp.grpc.dl.embedding.onnx;

import java.util.Map;

import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link AbstractOnnxEmbeddingProvider}'s orphan-key validation: any model id mentioned
 * by a {@code model.embedder.<id>.*} setting but lacking this engine's ONNX path must fail
 * startup, while ids owned by the sibling CUDA engine (shared vocab/lowercase/pooling keys)
 * must not.
 */
class OnnxEmbeddingOrphanKeyTest {

  @Test
  void vocabWithoutOnnxPathFailsNamingTheMissingKey() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> new OnnxEmbeddingBackendFactory().create(
            Map.of("model.embedder.m1.vocab.path", "/tmp/vocab.txt")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("model.embedder.m1.onnx.path"),
        "the error must name the missing path key: " + error.getMessage());
  }

  @Test
  void typoedOnnxPathKeyFailsInsteadOfSilentlyDropping() {
    // "onx.path" matches no known suffix; the valid vocab key still mentions the id, so the
    // incomplete model must fail startup rather than vanish.
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> new OnnxEmbeddingBackendFactory().create(Map.of(
            "model.embedder.m1.onx.path", "/tmp/model.onnx",
            "model.embedder.m1.vocab.path", "/tmp/vocab.txt")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("model.embedder.m1.onnx.path"),
        "the error must name the missing path key: " + error.getMessage());
  }

  @Test
  void orphanLowercaseKeyFailsNamingTheMissingKey() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> new OnnxEmbeddingBackendFactory().create(
            Map.of("model.embedder.m1.lowercase", "true")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("model.embedder.m1.onnx.path"),
        "the error must name the missing path key: " + error.getMessage());
  }

  @Test
  void cudaOwnedModelDoesNotFailTheCpuEngine() {
    // vocab/lowercase/pooling keys are shared across the ONNX-family engines: an id with a
    // .cuda.path belongs to the CUDA engine and leaves the CPU engine inert, not failed.
    final EmbeddingProvider provider = assertDoesNotThrow(() ->
        new OnnxEmbeddingBackendFactory().create(Map.of(
            "model.embedder.m1.cuda.path", "/tmp/model.onnx",
            "model.embedder.m1.vocab.path", "/tmp/vocab.txt")));
    assertFalse(provider.isAvailable());
  }
}
