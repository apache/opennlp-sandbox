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

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.Span;
import opennlp.tools.util.normalizer.Alignment;
import org.apache.opennlp.grpc.v1.AlignmentRun;

/**
 * Reconstructs the ordered edit runs of a library {@link Alignment} for the wire
 * ({@link AlignmentRun}), in Java UTF-16 units. The library does not expose its runs
 * directly, but they are fully recoverable: each normalized unit maps to its original
 * block via {@code toOriginalSpan(i, i + 1)}; contiguous 1:1 blocks form equal runs,
 * shared or empty blocks form replace runs, and gaps between consecutive blocks (or at
 * the edges) are deletions. The final offset-encoding pass rescales run lengths to the
 * client's requested unit.
 */
final class AlignmentRuns {

  private AlignmentRuns() {
  }

  static List<AlignmentRun> from(Alignment alignment) {
    final List<AlignmentRun> runs = new ArrayList<>();
    final int normalizedLength = alignment.normalizedLength();
    final int originalLength = alignment.originalLength();
    int originalPos = 0;
    int equalUnits = 0;
    int i = 0;
    while (i < normalizedLength) {
      final Span block = alignment.toOriginalSpan(i, i + 1);
      // Count how many normalized units share this exact original block.
      int j = i + 1;
      while (j < normalizedLength) {
        final Span next = alignment.toOriginalSpan(j, j + 1);
        if (next.getStart() != block.getStart() || next.getEnd() != block.getEnd()) {
          break;
        }
        j++;
      }
      final int producedUnits = j - i;
      final int blockLength = block.length();
      if (block.getStart() > originalPos) {
        // Deleted original text between the previous block and this one.
        equalUnits = flushEqual(runs, equalUnits);
        runs.add(replace(block.getStart() - originalPos, 0));
      }
      if (blockLength == 1 && producedUnits == 1) {
        equalUnits++; // a 1:1 copy; merged into one equal run with its neighbors
      } else {
        equalUnits = flushEqual(runs, equalUnits);
        runs.add(replace(blockLength, producedUnits));
      }
      originalPos = Math.max(originalPos, block.getEnd());
      i = j;
    }
    equalUnits = flushEqual(runs, equalUnits);
    if (originalPos < originalLength) {
      // Deleted original text after the last normalized unit (e.g. a trailing trim).
      runs.add(replace(originalLength - originalPos, 0));
    }
    return runs;
  }

  private static int flushEqual(List<AlignmentRun> runs, int equalUnits) {
    if (equalUnits > 0) {
      runs.add(AlignmentRun.newBuilder()
          .setOriginalUnits(equalUnits)
          .setNormalizedUnits(equalUnits)
          .setEqual(true)
          .build());
    }
    return 0;
  }

  private static AlignmentRun replace(int originalUnits, int normalizedUnits) {
    return AlignmentRun.newBuilder()
        .setOriginalUnits(originalUnits)
        .setNormalizedUnits(normalizedUnits)
        .setEqual(false)
        .build();
  }
}
