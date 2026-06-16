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
package org.apache.opennlp.grpc.embedding.onnx;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that the ONNX padded-tensor {@link AbstractOnnxEmbeddingProvider#embedBatch batch path}
 * produces exactly the same vectors as embedding each text on its own — the property that makes
 * batching a pure performance optimization. The texts deliberately differ in token length so the
 * batch is right-padded and the attention mask must exclude the padding from pooling.
 *
 * <p>Opt-in: runs only when {@code -Ddl.embedding.model.dir=<dir>} points at a directory containing
 * {@code model.onnx} and {@code vocab.txt} (a BERT-style sentence-embedding export, e.g.
 * {@code sentence-transformers/all-MiniLM-L6-v2}). The model is never bundled or redistributed.</p>
 */
class OnnxEmbeddingBatchParityTest {

  private static final String MODEL_ID = "minilm";
  private static final List<String> TEXTS = List.of(
      "hi",
      "a slightly longer sentence about embeddings",
      "George Washington was the first president of the United States of America.",
      "ok");
  private static final float TOLERANCE = 1e-4f;

  @Test
  void batchedVectorsMatchPerTextVectors() {
    final String dir = System.getProperty("dl.embedding.model.dir");
    assumeTrue(dir != null && !dir.isBlank(),
        "set -Ddl.embedding.model.dir to run the ONNX embedding batch-parity test");
    final File model = new File(dir, "model.onnx");
    final File vocab = new File(dir, "vocab.txt");
    assumeTrue(model.isFile() && vocab.isFile(),
        "model.onnx and vocab.txt must exist in " + dir);

    final Map<String, String> configuration = Map.of(
        "model.embedder." + MODEL_ID + ".onnx.path", model.getPath(),
        "model.embedder." + MODEL_ID + ".vocab.path", vocab.getPath());

    try (OnnxRuntimeEmbeddingProvider provider = new OnnxRuntimeEmbeddingProvider(configuration)) {
      final List<float[]> batched = provider.embedBatch(MODEL_ID, TEXTS);
      assertEquals(TEXTS.size(), batched.size());
      final int dimension = provider.embeddingDimension(MODEL_ID);
      for (int i = 0; i < TEXTS.size(); i++) {
        final float[] single = provider.embed(MODEL_ID, TEXTS.get(i));
        assertEquals(dimension, batched.get(i).length, "vector " + i + " has the wrong dimension");
        for (int d = 0; d < dimension; d++) {
          assertEquals(single[d], batched.get(i)[d], TOLERANCE,
              "batched vector " + i + " differs at component " + d);
        }
      }
    }
  }

  @Test
  void emptyBatchReturnsEmpty() {
    final String dir = System.getProperty("dl.embedding.model.dir");
    assumeTrue(dir != null && !dir.isBlank(),
        "set -Ddl.embedding.model.dir to run the ONNX embedding batch-parity test");
    final File model = new File(dir, "model.onnx");
    final File vocab = new File(dir, "vocab.txt");
    assumeTrue(model.isFile() && vocab.isFile(),
        "model.onnx and vocab.txt must exist in " + dir);

    final Map<String, String> configuration = Map.of(
        "model.embedder." + MODEL_ID + ".onnx.path", model.getPath(),
        "model.embedder." + MODEL_ID + ".vocab.path", vocab.getPath());

    try (OnnxRuntimeEmbeddingProvider provider = new OnnxRuntimeEmbeddingProvider(configuration)) {
      assertEquals(0, provider.embedBatch(MODEL_ID, List.of()).size());
    }
  }
}
