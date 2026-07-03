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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import opennlp.tools.util.normalizer.TextNormalizer;
import org.apache.opennlp.grpc.v1.NormalizationRung;

/**
 * Maps the wire {@link NormalizationRung} values onto the library's
 * {@link TextNormalizer.Builder}. The canonical application order is the enum's
 * declaration order (mirroring the library's builder conventions), independent of the
 * order the request listed the rungs in, so results are deterministic.
 */
final class NormalizationRungs {

  /**
   * The rungs that cannot report per-character edits (they delegate to
   * java.text.Normalizer or JDK case mapping), so a chain containing one cannot
   * produce an alignment.
   */
  static final Set<NormalizationRung> OFFSET_OPAQUE = EnumSet.of(
      NormalizationRung.NORMALIZATION_RUNG_NFC,
      NormalizationRung.NORMALIZATION_RUNG_NFKC,
      NormalizationRung.NORMALIZATION_RUNG_CASE_FOLD,
      NormalizationRung.NORMALIZATION_RUNG_ACCENT_FOLD,
      NormalizationRung.NORMALIZATION_RUNG_CONFUSABLE_FOLD);

  private NormalizationRungs() {
  }

  /** {@return the requested rungs, deduplicated, in canonical (declaration) order} */
  static List<NormalizationRung> canonicalOrder(List<NormalizationRung> requested) {
    final EnumSet<NormalizationRung> set = EnumSet.noneOf(NormalizationRung.class);
    set.addAll(requested);
    set.remove(NormalizationRung.NORMALIZATION_RUNG_UNSPECIFIED);
    set.remove(NormalizationRung.UNRECOGNIZED);
    return new ArrayList<>(set);
  }

  static boolean allOffsetAware(List<NormalizationRung> rungs) {
    for (final NormalizationRung rung : rungs) {
      if (OFFSET_OPAQUE.contains(rung)) {
        return false;
      }
    }
    return true;
  }

  static void apply(TextNormalizer.Builder builder, NormalizationRung rung) {
    switch (rung) {
      case NORMALIZATION_RUNG_STRIP_INVISIBLE -> builder.stripInvisible();
      case NORMALIZATION_RUNG_NFC -> builder.nfc();
      case NORMALIZATION_RUNG_NFKC -> builder.nfkc();
      case NORMALIZATION_RUNG_WHITESPACE -> builder.whitespace();
      case NORMALIZATION_RUNG_WHITESPACE_PRESERVE_LINE_BREAKS ->
          builder.whitespacePreservingLineBreaks();
      case NORMALIZATION_RUNG_QUOTES -> builder.quotes();
      case NORMALIZATION_RUNG_DASHES -> builder.dashes();
      case NORMALIZATION_RUNG_DIGITS -> builder.digits();
      case NORMALIZATION_RUNG_ELLIPSIS -> builder.ellipsis();
      case NORMALIZATION_RUNG_BULLETS -> builder.bullets();
      case NORMALIZATION_RUNG_CASE_FOLD -> builder.caseFold();
      case NORMALIZATION_RUNG_FULL_CASE_FOLD -> builder.fullCaseFold();
      case NORMALIZATION_RUNG_ACCENT_FOLD -> builder.accentFold();
      case NORMALIZATION_RUNG_EMOJI_TO_EMOTICON -> builder.emojiToEmoticon();
      case NORMALIZATION_RUNG_EMOTICON_TO_EMOJI -> builder.emoticonToEmoji();
      case NORMALIZATION_RUNG_CONFUSABLE_FOLD -> builder.with(
          opennlp.tools.util.normalizer.ConfusableSkeletonCharSequenceNormalizer.getInstance());
      default -> throw new IllegalStateException("Unmapped normalization rung: " + rung);
    }
  }
}
