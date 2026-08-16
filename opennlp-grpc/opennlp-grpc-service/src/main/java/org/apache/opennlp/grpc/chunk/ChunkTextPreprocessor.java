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
package org.apache.opennlp.grpc.chunk;

import java.util.ArrayList;
import java.util.List;

import org.apache.opennlp.grpc.v1.ChunkingSpec;

/**
 * Applies optional chunking-time text normalization from {@link ChunkingSpec}.
 *
 * <p>Normalization affects the chunk {@code text_content} and embedding inputs only; document
 * annotation spans continue to reference the original {@code raw_text}.</p>
 */
public final class ChunkTextPreprocessor {

  /** Prevents instantiation. */
  private ChunkTextPreprocessor() {
  }

  /**
   * Returns the chunk text to embed and return in {@code Chunk.text_content}.
   *
   * @param rawText The document text. Must not be {@code null}.
   * @param start   The inclusive chunk start offset in {@code rawText}.
   * @param end     The exclusive chunk end offset in {@code rawText}.
   * @param spec    The chunking spec carrying normalization flags.
   *
   * @return The chunk text, normalized when requested.
   */
  public static String chunkText(String rawText, int start, int end, ChunkingSpec spec) {
    final String slice = rawText.substring(start, end);
    if (!spec.getCleanText()) {
      return slice;
    }
    return clean(slice, spec.getPreserveUrls());
  }

  /**
   * Collapses whitespace runs and trims ends. When {@code preserveUrls} is {@code true}, URL
   * substrings are left untouched while the surrounding text is normalized.
   *
   * @param text          The text to normalize. Must not be {@code null}.
   * @param preserveUrls  Whether URL substrings should be preserved verbatim.
   *
   * @return The normalized text. Never {@code null}.
   */
  static String clean(String text, boolean preserveUrls) {
    if (text.isEmpty()) {
      return text;
    }
    if (!preserveUrls) {
      return collapseWhitespace(text).trim();
    }

    final List<String> preserved = new ArrayList<>();
    final StringBuilder buffer = new StringBuilder(text.length());
    int pos = 0;
    int urlStart = findUrlStart(text, 0);
    while (urlStart >= 0) {
      final int urlEnd = urlEnd(text, urlStart);
      preserved.add(text.substring(urlStart, urlEnd));
      buffer.append(text, pos, urlStart).append(" \0URL").append(preserved.size()).append("\0 ");
      pos = urlEnd;
      urlStart = findUrlStart(text, urlEnd);
    }
    buffer.append(text, pos, text.length());
    String cleaned = collapseWhitespace(buffer.toString()).trim();
    for (int i = 0; i < preserved.size(); i++) {
      cleaned = cleaned.replace("\0URL" + (i + 1) + "\0", preserved.get(i));
    }
    return collapseWhitespace(cleaned).trim();
  }

  /**
   * Returns the start of the next URL run at or after {@code from}: an ASCII
   * case-insensitive {@code http://}, {@code https://}, or {@code www.} prefix directly
   * followed by a non-whitespace character, or {@code -1} when none remains.
   */
  private static int findUrlStart(String text, int from) {
    for (int i = from; i < text.length(); i++) {
      final int prefixEnd = urlPrefixEnd(text, i);
      if (prefixEnd > 0 && prefixEnd < text.length() && isUrlChar(text.charAt(prefixEnd))) {
        return i;
      }
    }
    return -1;
  }

  /** Returns the exclusive end of the URL run starting at {@code start}. */
  private static int urlEnd(String text, int start) {
    int end = start;
    while (end < text.length() && isUrlChar(text.charAt(end))) {
      end++;
    }
    return end;
  }

  /**
   * Returns the exclusive end of the URL prefix at {@code start}, or {@code -1} when no
   * {@code http://}, {@code https://}, or {@code www.} prefix starts there.
   */
  private static int urlPrefixEnd(String text, int start) {
    if (startsWithIgnoreCase(text, start, "https://")) {
      return start + 8;
    }
    if (startsWithIgnoreCase(text, start, "http://")) {
      return start + 7;
    }
    if (startsWithIgnoreCase(text, start, "www.")) {
      return start + 4;
    }
    return -1;
  }

  /**
   * ASCII case-insensitive literal match at {@code start}, equivalent to
   * {@code Pattern.CASE_INSENSITIVE} without unicode case folding.
   */
  private static boolean startsWithIgnoreCase(String text, int start, String literal) {
    if (start + literal.length() > text.length()) {
      return false;
    }
    for (int i = 0; i < literal.length(); i++) {
      if (asciiLower(text.charAt(start + i)) != asciiLower(literal.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /** Folds ASCII upper case to lower case, leaving every other character untouched. */
  private static char asciiLower(char ch) {
    return ch >= 'A' && ch <= 'Z' ? (char) (ch + ('a' - 'A')) : ch;
  }

  /**
   * A non-whitespace character in the regex {@code \S} sense: anything but space, tab,
   * newline, vertical tab, form feed, or carriage return.
   */
  private static boolean isUrlChar(char ch) {
    return ch != ' ' && ch != '\t' && ch != '\n' && ch != '\u000B' && ch != '\f' && ch != '\r';
  }

  /** Collapses whitespace. */
  private static String collapseWhitespace(String text) {
    final StringBuilder builder = new StringBuilder(text.length());
    boolean previousWhitespace = false;
    for (int i = 0; i < text.length(); i++) {
      final char ch = text.charAt(i);
      if (Character.isWhitespace(ch)) {
        if (!previousWhitespace) {
          builder.append(' ');
          previousWhitespace = true;
        }
      } else {
        builder.append(ch);
        previousWhitespace = false;
      }
    }
    return builder.toString();
  }
}
