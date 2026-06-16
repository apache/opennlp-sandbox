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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.opennlp.grpc.v1.ChunkingSpec;

/**
 * Applies optional chunking-time text normalization from {@link ChunkingSpec}.
 *
 * <p>Normalization affects the chunk {@code text_content} and embedding inputs only; document
 * annotation spans continue to reference the original {@code raw_text}.</p>
 */
public final class ChunkTextPreprocessor {

  private static final Pattern URL_PATTERN = Pattern.compile(
      "https?://\\S+|www\\.\\S+",
      Pattern.CASE_INSENSITIVE);

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
    final Matcher matcher = URL_PATTERN.matcher(text);
    final StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      preserved.add(matcher.group());
      matcher.appendReplacement(buffer, " \0URL" + preserved.size() + "\0 ");
    }
    matcher.appendTail(buffer);
    String cleaned = collapseWhitespace(buffer.toString()).trim();
    for (int i = 0; i < preserved.size(); i++) {
      cleaned = cleaned.replace("\0URL" + (i + 1) + "\0", preserved.get(i));
    }
    return collapseWhitespace(cleaned).trim();
  }

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
