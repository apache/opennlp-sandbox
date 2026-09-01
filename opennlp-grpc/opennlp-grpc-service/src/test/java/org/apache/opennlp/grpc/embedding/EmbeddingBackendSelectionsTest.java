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

import java.util.List;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.EmbeddingBackendSelector;
import org.apache.opennlp.grpc.v1.EmbeddingSelector;
import org.apache.opennlp.grpc.v1.StandardEmbeddingBackend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies typed embedding backend resolution and string-field compatibility. */
class EmbeddingBackendSelectionsTest {

  @Test
  void resolvesEveryStandardBackend() {
    assertEquals(List.of("onnx", "cuda", "static", "tei", "openvino"), List.of(
        selected(StandardEmbeddingBackend.STANDARD_EMBEDDING_BACKEND_ONNX),
        selected(StandardEmbeddingBackend.STANDARD_EMBEDDING_BACKEND_CUDA),
        selected(StandardEmbeddingBackend.STANDARD_EMBEDDING_BACKEND_STATIC),
        selected(StandardEmbeddingBackend.STANDARD_EMBEDDING_BACKEND_TEI),
        selected(StandardEmbeddingBackend.STANDARD_EMBEDDING_BACKEND_OPENVINO)));
  }

  @Test
  void resolvesOpenCustomBackendAndTrimsIt() {
    final EmbeddingSelector selector = EmbeddingSelector.newBuilder()
        .setBackend(EmbeddingBackendSelector.newBuilder().setCustom("  extension  "))
        .build();

    assertEquals("extension", EmbeddingBackendSelections.selectedId(selector));
  }

  @Test
  void retainsCompatibilityBackendAndUnpinnedRouting() {
    assertEquals("legacy", EmbeddingBackendSelections.selectedId(
        EmbeddingSelector.newBuilder().setBackendId("  legacy  ").build()));
    assertNull(EmbeddingBackendSelections.selectedId(EmbeddingSelector.getDefaultInstance()));
    assertNull(EmbeddingBackendSelections.selectedId(
        EmbeddingSelector.newBuilder().setBackendId("  ").build()));
  }

  @Test
  void rejectsMixedOrEmptyTypedBackendSelection() {
    final EmbeddingSelector mixed = EmbeddingSelector.newBuilder()
        .setBackendId("onnx")
        .setBackend(standard(StandardEmbeddingBackend.STANDARD_EMBEDDING_BACKEND_ONNX))
        .build();
    final EmbeddingSelector unspecified = EmbeddingSelector.newBuilder()
        .setBackend(standard(StandardEmbeddingBackend.STANDARD_EMBEDDING_BACKEND_UNSPECIFIED))
        .build();
    final EmbeddingSelector blankCustom = EmbeddingSelector.newBuilder()
        .setBackend(EmbeddingBackendSelector.newBuilder().setCustom("  "))
        .build();
    final EmbeddingSelector missingKind = EmbeddingSelector.newBuilder()
        .setBackend(EmbeddingBackendSelector.getDefaultInstance())
        .build();

    final AnalysisException mixedError = assertThrows(
        AnalysisException.class, () -> EmbeddingBackendSelections.selectedId(mixed));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, mixedError.getFailureType());
    assertTrue(mixedError.getMessage().contains("mutually exclusive"));
    assertThrows(AnalysisException.class,
        () -> EmbeddingBackendSelections.selectedId(unspecified));
    assertThrows(AnalysisException.class,
        () -> EmbeddingBackendSelections.selectedId(blankCustom));
    assertThrows(AnalysisException.class,
        () -> EmbeddingBackendSelections.selectedId(missingKind));
  }

  private static String selected(StandardEmbeddingBackend backend) {
    return EmbeddingBackendSelections.selectedId(
        EmbeddingSelector.newBuilder().setBackend(standard(backend)).build());
  }

  private static EmbeddingBackendSelector standard(StandardEmbeddingBackend backend) {
    return EmbeddingBackendSelector.newBuilder().setStandard(backend).build();
  }
}
