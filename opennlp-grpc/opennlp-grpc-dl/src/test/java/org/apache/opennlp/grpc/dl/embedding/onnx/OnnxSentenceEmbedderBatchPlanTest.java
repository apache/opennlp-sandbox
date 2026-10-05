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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the sub-batch planning that keeps one inference call within the token-slot
 * budget: the padded size of every sub-batch stays bounded, order is preserved, and an
 * oversized single input still runs alone instead of being dropped.
 */
class OnnxSentenceEmbedderBatchPlanTest {

  @Test
  void keepsEveryInputInOneBatchWhenTheBudgetAllows() {
    assertArrayEquals(new int[] {3},
        OnnxSentenceEmbedder.planBatches(new int[] {4, 6, 5}, 3 * 6));
  }

  @Test
  void cutsWhenThePaddedProductWouldExceedTheBudget() {
    // Padded size is count * longest: [4, 6] fits (2 * 6 = 12), adding 5 would be 3 * 6 = 18.
    assertArrayEquals(new int[] {2, 3},
        OnnxSentenceEmbedder.planBatches(new int[] {4, 6, 5}, 12));
  }

  @Test
  void aLongerLaterInputRaisesThePaddedSizeOfTheWholeBatch() {
    // Two short inputs pad to 2 * 3 = 6; the 10-token input would pad all three to 30.
    assertArrayEquals(new int[] {2, 3},
        OnnxSentenceEmbedder.planBatches(new int[] {3, 3, 10}, 12));
  }

  @Test
  void anInputLargerThanTheBudgetStillRunsAlone() {
    assertArrayEquals(new int[] {1, 2},
        OnnxSentenceEmbedder.planBatches(new int[] {100, 1}, 8));
  }

  @Test
  void manyUniformInputsSplitEvenly() {
    final int[] lengths = new int[1_463];
    java.util.Arrays.fill(lengths, 32);
    final int[] ends = OnnxSentenceEmbedder.planBatches(lengths, OnnxSentenceEmbedder.MAX_BATCH_TOKENS);
    assertEquals(1_463, ends[ends.length - 1]);
    int previous = 0;
    for (int end : ends) {
      assertEquals(true, (end - previous) * 32 <= OnnxSentenceEmbedder.MAX_BATCH_TOKENS);
      previous = end;
    }
    assertEquals(3, ends.length);
  }

  @Test
  void emptyInputPlansNoBatches() {
    assertArrayEquals(new int[0], OnnxSentenceEmbedder.planBatches(new int[0], 8));
  }
}
