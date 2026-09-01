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
package org.apache.opennlp.grpc.processor.basic;

import java.util.List;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.EnginePolicy;
import org.apache.opennlp.grpc.v1.EngineSelector;
import org.apache.opennlp.grpc.v1.StandardInferenceEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies typed engine selector resolution and legacy compatibility. */
class EngineSelectionsTest {

  @Test
  void resolvesStandardAndCustomSelectorsInRequestOrder() {
    final EnginePolicy policy = EnginePolicy.newBuilder()
        .addSelectors(standard(StandardInferenceEngine.STANDARD_INFERENCE_ENGINE_OPENNLP_ME))
        .addSelectors(standard(StandardInferenceEngine.STANDARD_INFERENCE_ENGINE_ONNX))
        .addSelectors(standard(StandardInferenceEngine.STANDARD_INFERENCE_ENGINE_CUDA))
        .addSelectors(EngineSelector.newBuilder().setCustom("  remote-ner  ").build())
        .build();

    assertEquals(List.of("opennlp-me", "onnx", "cuda", "remote-ner"),
        EngineSelections.ids(policy));
  }

  @Test
  void retainsLegacyEngineIds() {
    final EnginePolicy policy = EnginePolicy.newBuilder()
        .addEngines("opennlp-me")
        .addEngines("vendor-engine")
        .build();

    assertEquals(List.of("opennlp-me", "vendor-engine"), EngineSelections.ids(policy));
  }

  @Test
  void rejectsMixedTypedAndLegacySelection() {
    final EnginePolicy policy = EnginePolicy.newBuilder()
        .addEngines("opennlp-me")
        .addSelectors(standard(StandardInferenceEngine.STANDARD_INFERENCE_ENGINE_OPENNLP_ME))
        .build();

    final AnalysisException error = assertThrows(
        AnalysisException.class, () -> EngineSelections.ids(policy));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("mutually exclusive"));
  }

  @Test
  void rejectsEmptyTypedSelections() {
    final EnginePolicy unspecified = EnginePolicy.newBuilder()
        .addSelectors(standard(StandardInferenceEngine.STANDARD_INFERENCE_ENGINE_UNSPECIFIED))
        .build();
    final EnginePolicy blankCustom = EnginePolicy.newBuilder()
        .addSelectors(EngineSelector.newBuilder().setCustom("  ").build())
        .build();
    final EnginePolicy missingKind = EnginePolicy.newBuilder()
        .addSelectors(EngineSelector.getDefaultInstance())
        .build();

    assertThrows(AnalysisException.class, () -> EngineSelections.ids(unspecified));
    assertThrows(AnalysisException.class, () -> EngineSelections.ids(blankCustom));
    assertThrows(AnalysisException.class, () -> EngineSelections.ids(missingKind));
  }

  private static EngineSelector standard(StandardInferenceEngine engine) {
    return EngineSelector.newBuilder().setStandard(engine).build();
  }
}
