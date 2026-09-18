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

import java.util.Map;

import org.apache.opennlp.grpc.model.NameFinderRegistry;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the ONNX name finder configuration validation as the server sees it: the
 * {@link OnnxNerBackendFactory} is discovered via ServiceLoader and the invalid
 * {@code model.name_finder_dl.*} entries fail {@link NameFinderRegistry} startup loudly.
 */
class NameFinderRegistryDlTest {

  @Test
  void rejectsDlConfigMissingRequiredAttribute() {
    // path present but vocab/labels missing.
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        NameFinderRegistry.create(Map.of(
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.path", "/tmp/model.onnx")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void rejectsDlConfigUnsupportedBackend() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        NameFinderRegistry.create(Map.of(
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.path", "/tmp/model.onnx",
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.vocab", "/tmp/vocab.txt",
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.labels", "/tmp/labels.txt",
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.backend", "tpu")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void rejectsDlConfigWithoutSentenceDetector() {
    // A complete ONNX config still needs a sentence detector; create(config) supplies none.
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        NameFinderRegistry.create(Map.of(
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.path", "/tmp/model.onnx",
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.vocab", "/tmp/vocab.txt",
            OnnxNerBackendFactory.KEY_DL_PREFIX + "person.labels", "/tmp/labels.txt")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("sentence detector"));
  }

  @Test
  void factoryPrefixMatchesTheRegistryNamespaceContract() {
    // The registry mirrors this literal for its add-on-missing diagnostics; the two must
    // never drift apart.
    assertEquals(NameFinderRegistry.KEY_DL_PREFIX, OnnxNerBackendFactory.KEY_DL_PREFIX);
  }
}
