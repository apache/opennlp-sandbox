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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.opennlp.grpc.model.DocCategorizerRegistry;
import org.apache.opennlp.grpc.model.SentimentRegistry;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the ONNX document categorizer configuration validation as the server sees it: the
 * {@link OnnxDocCategorizerBackendFactory} is discovered via ServiceLoader and invalid
 * {@code model.doccat_dl.*} (or aliased {@code model.sentiment_dl.*}) entries fail registry
 * startup loudly.
 */
class DocCategorizerRegistryDlTest {

  @TempDir
  Path modelDir;

  @Test
  void rejectsDlConfigWithBlankId() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        DocCategorizerRegistry.create(Map.of(
            OnnxDocCategorizerBackendFactory.KEY_DL_PREFIX + " .path", "/tmp/model.onnx")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void rejectsDlCategoriesFileWithBlankLine() throws IOException {
    // A blank line would leave a gap in the line-number->category index map (and an NPE at load);
    // it must be rejected with a clear config error before any ONNX session is created.
    final Path model = Files.writeString(modelDir.resolve("m.onnx"), "stub");
    final Path vocab = Files.writeString(modelDir.resolve("v.txt"), "[CLS]\n[SEP]\n");
    final Path categories = Files.writeString(modelDir.resolve("cats.txt"), "weather\n\nfinance\n");
    final String prefix = OnnxDocCategorizerBackendFactory.KEY_DL_PREFIX + "topic.";

    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        DocCategorizerRegistry.create(Map.of(
            prefix + "path", model.toString(),
            prefix + "vocab", vocab.toString(),
            prefix + "categories", categories.toString())));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("blank line"));
  }

  @Test
  void rejectsDlConfigMissingRequiredAttribute() {
    // path present but vocab/categories missing.
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        DocCategorizerRegistry.create(Map.of(
            OnnxDocCategorizerBackendFactory.KEY_DL_PREFIX + "topic.path", "/tmp/model.onnx")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void rejectsSentimentDlConfigMissingRequiredAttribute() {
    // The sentiment namespace is canonicalized onto the doccat keys this backend reads.
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        SentimentRegistry.create(Map.of(
            SentimentRegistry.KEY_DL_PREFIX + "polarity.path", "/tmp/model.onnx")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void factoryPrefixMatchesTheRegistryNamespaceContract() {
    // The registry mirrors this literal for its add-on-missing diagnostics; the two must
    // never drift apart.
    assertEquals(DocCategorizerRegistry.KEY_DL_PREFIX,
        OnnxDocCategorizerBackendFactory.KEY_DL_PREFIX);
  }
}
