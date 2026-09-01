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
import org.apache.opennlp.grpc.v1.Normalizer;

/**
 * Maps the wire {@link Normalizer} values onto the library's
 * {@link TextNormalizer.Builder}. The canonical application order is the enum's
 * declaration order (mirroring the library's builder conventions), independent of the
 * order the request listed the normalizers in, so results are deterministic.
 */
final class Normalizers {

  /**
   * The normalizers that cannot report per-character edits (they delegate to
   * java.text.Normalizer or JDK case mapping), so a chain containing one cannot
   * produce an alignment.
   */
  static final Set<Normalizer> OFFSET_OPAQUE = EnumSet.of(
      Normalizer.NORMALIZER_NFC,
      Normalizer.NORMALIZER_NFKC,
      Normalizer.NORMALIZER_CASE_FOLD,
      Normalizer.NORMALIZER_ACCENT_FOLD,
      Normalizer.NORMALIZER_CONFUSABLE_FOLD);

  private Normalizers() {
  }

  /** {@return the requested normalizers, deduplicated, in canonical (declaration) order} */
  static List<Normalizer> canonicalOrder(List<Normalizer> requested) {
    final EnumSet<Normalizer> set = EnumSet.noneOf(Normalizer.class);
    set.addAll(requested);
    set.remove(Normalizer.NORMALIZER_UNSPECIFIED);
    set.remove(Normalizer.UNRECOGNIZED);
    return new ArrayList<>(set);
  }

  static boolean allOffsetAware(List<Normalizer> normalizers) {
    for (final Normalizer normalizer : normalizers) {
      if (OFFSET_OPAQUE.contains(normalizer)) {
        return false;
      }
    }
    return true;
  }

  static void apply(TextNormalizer.Builder builder, Normalizer normalizer) {
    switch (normalizer) {
      case NORMALIZER_STRIP_INVISIBLE -> builder.stripInvisible();
      case NORMALIZER_NFC -> builder.nfc();
      case NORMALIZER_NFKC -> builder.nfkc();
      case NORMALIZER_WHITESPACE -> builder.whitespace();
      case NORMALIZER_WHITESPACE_PRESERVE_LINE_BREAKS ->
          builder.whitespacePreservingLineBreaks();
      case NORMALIZER_WHITESPACE_PRESERVE_PARAGRAPHS ->
          builder.whitespacePreservingParagraphs();
      case NORMALIZER_QUOTES -> builder.quotes();
      case NORMALIZER_DASHES -> builder.dashes();
      case NORMALIZER_DIGITS -> builder.digits();
      case NORMALIZER_ELLIPSIS -> builder.ellipsis();
      case NORMALIZER_BULLETS -> builder.bullets();
      case NORMALIZER_CASE_FOLD -> builder.caseFold();
      case NORMALIZER_FULL_CASE_FOLD -> builder.fullCaseFold();
      case NORMALIZER_ACCENT_FOLD -> builder.accentFold();
      case NORMALIZER_EMOJI_TO_EMOTICON -> builder.emojiToEmoticon();
      case NORMALIZER_EMOTICON_TO_EMOJI -> builder.emoticonToEmoji();
      case NORMALIZER_CONFUSABLE_FOLD -> builder.with(
          opennlp.tools.util.normalizer.ConfusableSkeletonCharSequenceNormalizer.getInstance());
      default -> throw new IllegalStateException("Unmapped normalizer: " + normalizer);
    }
  }
}
