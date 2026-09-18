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
package org.apache.opennlp.grpc.dl.model;

import java.util.HashMap;
import java.util.Map;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link OnnxDocCategorizerBackendFactory} configuration validation:
 * {@code gpu_device_id} only applies with {@code backend=cuda} and must be rejected
 * otherwise instead of being silently ignored.
 */
class OnnxDocCategorizerBackendFactoryTest {

  private static Map<String, String> configWithGpu(String backendKey, String backendValue) {
    final HashMap<String, String> configuration = new HashMap<>(Map.of(
        "model.doccat_dl.topics.path", "/tmp/model.onnx",
        "model.doccat_dl.topics.vocab", "/tmp/vocab.txt",
        "model.doccat_dl.topics.categories", "/tmp/categories.txt",
        "model.doccat_dl.topics.gpu_device_id", "1"));
    if (backendKey != null) {
      configuration.put(backendKey, backendValue);
    }
    return configuration;
  }

  @Test
  void gpuDeviceIdWithDefaultBackendIsRejected() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> new OnnxDocCategorizerBackendFactory().create(configWithGpu(null, null)));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("model.doccat_dl.topics.gpu_device_id"),
        "the error must name the offending key: " + error.getMessage());
  }

  @Test
  void gpuDeviceIdWithOnnxBackendIsRejected() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> new OnnxDocCategorizerBackendFactory().create(
            configWithGpu("model.doccat_dl.topics.backend", "onnx")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("model.doccat_dl.topics.gpu_device_id"),
        "the error must name the offending key: " + error.getMessage());
  }
}
