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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The full BERT tokenization pipeline: basic tokenization (text normalization)
 * followed by greedy wordpiece tokenization, matching the reference BERT
 * implementation and the HuggingFace {@code tokenizers} library.
 *
 * <p>The {@code opennlp-tools} {@code WordpieceTokenizer} performs only the
 * wordpiece stage and does not normalize text. With uncased models that maps
 * every capitalized or accented word to the unknown token and severely degrades
 * embedding quality. This class adds the missing basic tokenization stage:
 * control character removal, whitespace normalization, CJK ideograph isolation,
 * optional lower casing with accent stripping, and punctuation isolation.</p>
 *
 * <p>This is a temporary self-contained copy of the {@code BertTokenizer}
 * contributed to {@code opennlp-tools}; it should be removed in favor of the
 * upstream class once a release containing it is available.</p>
 */
final class BertTokenizer {

  /**
   * Maximum characters per word before the word is replaced with the unknown
   * token, matching the reference BERT implementation.
   */
  private static final int MAX_WORD_CHARACTERS = 100;

  private final Set<String> vocabulary;
  private final boolean lowerCase;
  private final String classificationToken;
  private final String separatorToken;
  private final String unknownToken;

  /**
   * Initializes a {@link BertTokenizer}.
   *
   * @param vocabulary          The wordpiece vocabulary. Must not be {@code null}.
   * @param lowerCase           {@code true} for uncased models (lower casing and
   *                            accent stripping), {@code false} for cased models.
   * @param classificationToken The CLS token.
   * @param separatorToken      The SEP token.
   * @param unknownToken        The UNK token.
   */
  BertTokenizer(Set<String> vocabulary, boolean lowerCase,
      String classificationToken, String separatorToken, String unknownToken) {
    this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary must not be null");
    this.lowerCase = lowerCase;
    this.classificationToken = classificationToken;
    this.separatorToken = separatorToken;
    this.unknownToken = unknownToken;
  }

  /**
   * Tokenizes the given text into wordpieces, surrounded by the classification
   * and separator tokens.
   *
   * @param text The text to tokenize. Must not be {@code null}.
   *
   * @return The wordpiece tokens.
   */
  String[] tokenize(String text) {
    final List<String> tokens = new ArrayList<>();
    tokens.add(classificationToken);
    for (String word : normalize(text).split(" ")) {
      if (!word.isEmpty()) {
        wordpiece(word, tokens);
      }
    }
    tokens.add(separatorToken);
    return tokens.toArray(new String[0]);
  }

  /**
   * Greedy longest-match-first wordpiece splitting. If any part of the word
   * cannot be matched, the whole word becomes a single unknown token, as in
   * the reference BERT implementation.
   */
  private void wordpiece(String word, List<String> tokens) {
    if (word.length() > MAX_WORD_CHARACTERS) {
      tokens.add(unknownToken);
      return;
    }
    final List<String> pieces = new ArrayList<>();
    int start = 0;
    while (start < word.length()) {
      int end = word.length();
      String piece = null;
      while (start < end) {
        String candidate = word.substring(start, end);
        if (start > 0) {
          candidate = "##" + candidate;
        }
        if (vocabulary.contains(candidate)) {
          piece = candidate;
          break;
        }
        end--;
      }
      if (piece == null) {
        tokens.add(unknownToken);
        return;
      }
      pieces.add(piece);
      start = end;
    }
    tokens.addAll(pieces);
  }

  /**
   * Applies the BERT basic tokenization (normalization) stage.
   */
  private String normalize(String text) {
    Objects.requireNonNull(text, "text must not be null");
    String normalized = cleanText(text);
    normalized = isolateCjkCharacters(normalized);
    if (lowerCase) {
      normalized = stripAccents(normalized.toLowerCase(Locale.ROOT));
    }
    return isolatePunctuation(normalized);
  }

  /**
   * Removes invalid and control characters and normalizes all whitespace
   * characters to plain spaces.
   */
  private static String cleanText(String text) {
    final StringBuilder cleaned = new StringBuilder(text.length());
    text.codePoints().forEach(codePoint -> {
      if (codePoint == 0 || codePoint == 0xFFFD || isControl(codePoint)) {
        return;
      }
      if (isWhitespace(codePoint)) {
        cleaned.append(' ');
      } else {
        cleaned.appendCodePoint(codePoint);
      }
    });
    return cleaned.toString();
  }

  /**
   * Surrounds every CJK ideograph with spaces, so each ideograph becomes its
   * own token, matching the reference BERT treatment of Chinese text.
   */
  private static String isolateCjkCharacters(String text) {
    final StringBuilder spaced = new StringBuilder(text.length());
    text.codePoints().forEach(codePoint -> {
      if (isCjk(codePoint)) {
        spaced.append(' ').appendCodePoint(codePoint).append(' ');
      } else {
        spaced.appendCodePoint(codePoint);
      }
    });
    return spaced.toString();
  }

  /**
   * Removes accents by Unicode NFD decomposition followed by removal of
   * combining marks ({@code Mn}).
   */
  private static String stripAccents(String text) {
    final String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
    final StringBuilder stripped = new StringBuilder(decomposed.length());
    decomposed.codePoints().forEach(codePoint -> {
      if (Character.getType(codePoint) != Character.NON_SPACING_MARK) {
        stripped.appendCodePoint(codePoint);
      }
    });
    return stripped.toString();
  }

  /**
   * Surrounds every punctuation character with spaces, so each punctuation
   * character becomes its own token.
   */
  private static String isolatePunctuation(String text) {
    final StringBuilder spaced = new StringBuilder(text.length());
    text.codePoints().forEach(codePoint -> {
      if (isPunctuation(codePoint)) {
        spaced.append(' ').appendCodePoint(codePoint).append(' ');
      } else {
        spaced.appendCodePoint(codePoint);
      }
    });
    return spaced.toString();
  }

  /**
   * A control character in the BERT sense: any {@code C*} category
   * (control, format, surrogate, private use, unassigned), except the
   * characters treated as whitespace by {@link #isWhitespace(int)}.
   */
  private static boolean isControl(int codePoint) {
    if (codePoint == '\t' || codePoint == '\n' || codePoint == '\r') {
      return false;
    }
    return switch (Character.getType(codePoint)) {
      case Character.CONTROL, Character.FORMAT, Character.SURROGATE,
           Character.PRIVATE_USE, Character.UNASSIGNED -> true;
      default -> false;
    };
  }

  /**
   * A whitespace character in the BERT sense: space, tab, newline, carriage
   * return, or Unicode space separators ({@code Zs}).
   */
  private static boolean isWhitespace(int codePoint) {
    if (codePoint == ' ' || codePoint == '\t' || codePoint == '\n' || codePoint == '\r') {
      return true;
    }
    return Character.getType(codePoint) == Character.SPACE_SEPARATOR;
  }

  /**
   * A punctuation character in the BERT sense: any non-alphanumeric ASCII
   * character that is not whitespace, or any Unicode punctuation category.
   */
  private static boolean isPunctuation(int codePoint) {
    if ((codePoint >= 33 && codePoint <= 47) || (codePoint >= 58 && codePoint <= 64)
        || (codePoint >= 91 && codePoint <= 96) || (codePoint >= 123 && codePoint <= 126)) {
      return true;
    }
    return switch (Character.getType(codePoint)) {
      case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION,
           Character.START_PUNCTUATION, Character.END_PUNCTUATION,
           Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
           Character.OTHER_PUNCTUATION -> true;
      default -> false;
    };
  }

  /**
   * A CJK ideograph as defined by the reference BERT implementation: the CJK
   * Unified Ideographs blocks and their extensions. This intentionally does
   * not cover Japanese kana or Korean hangul, matching the reference.
   */
  private static boolean isCjk(int codePoint) {
    return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
        || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
        || (codePoint >= 0x20000 && codePoint <= 0x2A6DF)
        || (codePoint >= 0x2A700 && codePoint <= 0x2B73F)
        || (codePoint >= 0x2B740 && codePoint <= 0x2B81F)
        || (codePoint >= 0x2B820 && codePoint <= 0x2CEAF)
        || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
        || (codePoint >= 0x2F800 && codePoint <= 0x2FA1F);
  }

}
