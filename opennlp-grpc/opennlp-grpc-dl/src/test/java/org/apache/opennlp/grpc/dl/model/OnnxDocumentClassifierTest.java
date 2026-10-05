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

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests the batched ONNX classifier: windowing and batch planning without a model, and
 * batch-versus-single parity plus declared-input handling against a real model when
 * {@code -Ddl.doccat.model.dir} points at a directory holding {@code model.onnx},
 * {@code vocab.txt}, and {@code categories.txt}.
 */
class OnnxDocumentClassifierTest {

  /** Distribution sums are exact; probabilities are compared with {@link #PARITY_TOLERANCE}. */
  private static final double TOLERANCE = 1e-5;

  /**
   * Dynamically quantized exports compute activation scales per batch, so a text's
   * probabilities shift by up to a few hundredths with batch composition. The best category
   * must not change.
   */
  private static final double PARITY_TOLERANCE = 0.05;

  @Test
  void windowsWrapEveryContentSliceInMarkers() {
    final List<long[]> windows = OnnxDocumentClassifier.windows(
        new long[] {10, 11, 12, 13, 14}, 4, 101, 102);

    assertEquals(3, windows.size());
    assertArrayEquals(new long[] {101, 10, 11, 102}, windows.get(0));
    assertArrayEquals(new long[] {101, 12, 13, 102}, windows.get(1));
    assertArrayEquals(new long[] {101, 14, 102}, windows.get(2));
  }

  @Test
  void emptyTextStillYieldsOneMarkerOnlyWindow() {
    final List<long[]> windows = OnnxDocumentClassifier.windows(new long[0], 512, 101, 102);
    assertEquals(1, windows.size());
    assertArrayEquals(new long[] {101, 102}, windows.getFirst());
  }

  @Test
  void batchPlanningKeepsThePaddedProductWithinBudget() {
    assertArrayEquals(new int[] {2, 3},
        OnnxDocumentClassifier.planBatches(new int[] {4, 6, 5}, 12));
    assertArrayEquals(new int[] {1, 2},
        OnnxDocumentClassifier.planBatches(new int[] {100, 1}, 8));
  }

  @Test
  void softmaxIsAProbabilityDistributionPreferringTheLargestLogit() {
    final double[] probabilities = OnnxDocumentClassifier.softmax(new float[] {1f, 3f, 2f});
    assertEquals(1.0, probabilities[0] + probabilities[1] + probabilities[2], TOLERANCE);
    assertTrue(probabilities[1] > probabilities[2] && probabilities[2] > probabilities[0]);
  }

  @Test
  void batchedScoresMatchSingleScoresAgainstARealModel() throws Exception {
    final File dir = modelDirectory();
    final List<String> categories = java.nio.file.Files.readAllLines(
        new File(dir, "categories.txt").toPath());
    final List<String> texts = List.of(
        "What a wonderful, delightful book.",
        "The ending was dreadful and boring.",
        "It was fine.");
    try (OnnxDocumentClassifier classifier = new OnnxDocumentClassifier(
        new File(dir, "model.onnx"), new File(dir, "vocab.txt"), categories, false, 0, true)) {
      final List<double[]> batched = classifier.scoreBatch(texts);
      // A budget of one padded row forces every text into its own inference call.
      final List<double[]> single = classifier.scoreBatch(texts, 1);
      assertEquals(texts.size(), batched.size());
      for (int i = 0; i < texts.size(); i++) {
        assertArrayEquals(single.get(i), batched.get(i), PARITY_TOLERANCE);
        assertEquals(argmax(single.get(i)), argmax(batched.get(i)),
            "best category changed with batch composition for text " + i);
        double sum = 0;
        for (double p : batched.get(i)) {
          sum += p;
        }
        assertEquals(1.0, sum, TOLERANCE);
      }
    }
  }

  @Test
  void longTextsAverageAcrossWindowsAgainstARealModel() throws Exception {
    final File dir = modelDirectory();
    final List<String> categories = java.nio.file.Files.readAllLines(
        new File(dir, "categories.txt").toPath());
    try (OnnxDocumentClassifier classifier = new OnnxDocumentClassifier(
        new File(dir, "model.onnx"), new File(dir, "vocab.txt"), categories, false, 0, true)) {
      final String sentence = "What a wonderful, delightful book. ";
      final String longText = sentence.repeat(200);
      final double[] scores = classifier.scoreBatch(List.of(longText)).getFirst();
      double sum = 0;
      for (double p : scores) {
        sum += p;
      }
      assertEquals(1.0, sum, TOLERANCE);
    }
  }

  /** Returns the index of the largest probability. */
  private static int argmax(double[] probabilities) {
    int best = 0;
    for (int i = 1; i < probabilities.length; i++) {
      if (probabilities[i] > probabilities[best]) {
        best = i;
      }
    }
    return best;
  }

  private static File modelDirectory() {
    final String dir = System.getProperty("dl.doccat.model.dir");
    assumeTrue(dir != null && !dir.isBlank(),
        "set -Ddl.doccat.model.dir to run the ONNX document classifier tests");
    final File directory = new File(dir);
    assumeTrue(new File(directory, "model.onnx").isFile()
            && new File(directory, "vocab.txt").isFile()
            && new File(directory, "categories.txt").isFile(),
        "model.onnx, vocab.txt, and categories.txt must exist in " + dir);
    return directory;
  }
}
