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

import java.util.Map;

import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.InferenceBackend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingProviderFactoryTest {

  @Test
  void defaultsToCpuProvider() {
    final EmbeddingProvider provider = EmbeddingProviderFactory.create(Map.of());
    assertInstanceOf(OnnxRuntimeEmbeddingProvider.class, provider);
    assertTrue(provider.supportsInferenceBackend(InferenceBackend.INFERENCE_BACKEND_ONNX_RUNTIME));
    assertFalse(provider.supportsInferenceBackend(InferenceBackend.INFERENCE_BACKEND_CUDA));
  }

  @Test
  void selectsCudaProviderFromConfig() {
    final EmbeddingProvider provider =
        EmbeddingProviderFactory.create(Map.of("model.embedder.backend", "cuda"));
    assertInstanceOf(CudaEmbeddingProvider.class, provider);
    assertTrue(provider.supportsInferenceBackend(InferenceBackend.INFERENCE_BACKEND_CUDA));
    assertTrue(provider.supportsInferenceBackend(InferenceBackend.INFERENCE_BACKEND_ONNX_RUNTIME_GPU));
  }

  @Test
  void rejectsUnknownBackendAndListsRegisteredBackends() {
    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> EmbeddingProviderFactory.create(Map.of("model.embedder.backend", "openvino")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, e.getFailureType());
    assertTrue(e.getMessage().contains("registered backends:"),
        "error should list the discovered backends: " + e.getMessage());
    assertTrue(e.getMessage().contains("onnx"), e.getMessage());
    assertTrue(e.getMessage().contains("cuda"), e.getMessage());
  }

  @Test
  void discoversExternalBackendThroughServiceLoader() {
    final EmbeddingProvider provider =
        EmbeddingProviderFactory.create(Map.of("model.embedder.backend", "stub"));
    assertInstanceOf(StubEmbeddingProvider.class, provider);
    assertTrue(provider.supportsModel("stub-model"));
    assertEquals(3, provider.embeddingDimension("stub-model"));
  }

  @Test
  void backendSelectionIsCaseInsensitive() {
    final EmbeddingProvider provider =
        EmbeddingProviderFactory.create(Map.of("model.embedder.backend", " ONNX "));
    assertInstanceOf(OnnxRuntimeEmbeddingProvider.class, provider);
  }

  @Test
  void rejectsGpuDeviceIdWithoutCudaBackend() {
    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> EmbeddingProviderFactory.create(Map.of("model.embedder.gpu_device_id", "1")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, e.getFailureType());
  }
}
