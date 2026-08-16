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

import opennlp.tools.util.normalizer.TextNormalizer;
import org.apache.opennlp.grpc.v1.AlignmentRun;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AlignmentRuns}, the reconstruction of a library {@code Alignment}'s edit
 * runs for the wire. The wire contract defines {@code equal} as "the source and normalized
 * units are identical", so positional one-to-one mapping alone is not enough: the content
 * must match.
 */
class AlignmentRunsTest {

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  @Test
  void supplementaryCharacterCopiedThroughFormsAnEqualRun() {
    // The emoji is one code point but two UTF-16 units; an offset-transparent rung copies it
    // unchanged, so the run covering it must be equal regardless of its unit length.
    final String rawText = "a " + cp(0x1F642) + " b";
    final var aligned = TextNormalizer.builder().whitespace().buildAligned()
        .normalizeAligned(rawText);

    assertEquals(rawText, aligned.normalizedString());
    final List<AlignmentRun> runs = AlignmentRuns.from(aligned);

    assertEquals(1, runs.size(), "identity normalization should yield one equal run");
    assertTrue(runs.getFirst().getEqual());
    assertEquals(rawText.length(), runs.getFirst().getOriginalUnits());
    assertEquals(rawText.length(), runs.getFirst().getNormalizedUnits());
  }

  @Test
  void oneToOneReplacementIsNotAnEqualRun() {
    // The en dash is replaced by a hyphen one-to-one: same unit counts, different content.
    // The wire contract reserves equal runs for identical text, so this is a replace run.
    final var aligned = TextNormalizer.builder().dashes().buildAligned()
        .normalizeAligned("a" + cp(0x2013) + "b");

    assertEquals("a-b", aligned.normalizedString());
    final List<AlignmentRun> runs = AlignmentRuns.from(aligned);

    assertEquals(3, runs.size(), "expected equal/replace/equal runs, got: " + runs);
    assertTrue(runs.get(0).getEqual());
    assertFalse(runs.get(1).getEqual());
    assertEquals(1, runs.get(1).getOriginalUnits());
    assertEquals(1, runs.get(1).getNormalizedUnits());
    assertTrue(runs.get(2).getEqual());
  }

  @Test
  void oneToOneCaseFoldIsNotAnEqualRun() {
    final var aligned = TextNormalizer.builder().fullCaseFold().buildAligned()
        .normalizeAligned("AB");

    assertEquals("ab", aligned.normalizedString());
    for (final AlignmentRun run : AlignmentRuns.from(aligned)) {
      assertFalse(run.getEqual(), "folded text must not be labeled equal: " + run);
    }
  }

  @Test
  void twoToTwoReplacementStaysAReplaceRun() {
    // An emoji (2 UTF-16 units) folded to a 2-unit emoticon: equal unit counts on both
    // sides, but the content differs, so the run must remain a replace run.
    final var aligned = TextNormalizer.builder().emojiToEmoticon().buildAligned()
        .normalizeAligned(cp(0x1F600));

    assertEquals(":D", aligned.normalizedString());
    final List<AlignmentRun> runs = AlignmentRuns.from(aligned);
    assertEquals(1, runs.size());
    assertFalse(runs.getFirst().getEqual());
    assertEquals(2, runs.getFirst().getOriginalUnits());
    assertEquals(2, runs.getFirst().getNormalizedUnits());
  }
}
