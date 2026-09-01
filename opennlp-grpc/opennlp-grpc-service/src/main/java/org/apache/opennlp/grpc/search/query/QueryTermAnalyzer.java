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
 * KIND, either express or implied.  See the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.search.query;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.StringUtil;

/**
 * The pinned default analysis for keyword and phrase clauses over dynamic workspace
 * indexes: maximal runs of Unicode letters and digits, scanned by code point, lowercased
 * with {@link StringUtil#toLowerCase(CharSequence)}. Query text, indexed chunk text, and
 * collection term statistics pass through the same analysis, so a term means the same thing
 * everywhere the recorded analysis-chain identity appears.
 */
public final class QueryTermAnalyzer {

  private QueryTermAnalyzer() {
  }

  /**
   * One analyzed term and its position.
   *
   * @param text Lowercased term text.
   * @param start Inclusive start offset in UTF-16 code units of the analyzed text.
   * @param end Exclusive end offset in UTF-16 code units of the analyzed text.
   * @param position Zero-based term index within the analyzed text.
   */
  public record Term(String text, int start, int end, int position) {
  }

  /**
   * Analyzes text into lowercased letter-and-digit terms with offsets.
   *
   * @param text Text to analyze; {@code null} yields no terms.
   * @return Analyzed terms in document order.
   */
  public static List<Term> analyze(String text) {
    if (text == null || text.isEmpty()) {
      return List.of();
    }
    final List<Term> terms = new ArrayList<>();
    int index = 0;
    int termStart = -1;
    while (index < text.length()) {
      final int codePoint = text.codePointAt(index);
      final int width = Character.charCount(codePoint);
      if (Character.isLetterOrDigit(codePoint)) {
        if (termStart < 0) {
          termStart = index;
        }
      } else if (termStart >= 0) {
        terms.add(term(text, termStart, index, terms.size()));
        termStart = -1;
      }
      index += width;
    }
    if (termStart >= 0) {
      terms.add(term(text, termStart, text.length(), terms.size()));
    }
    return List.copyOf(terms);
  }

  /**
   * Builds one analyzed term.
   *
   * @param text Source text.
   * @param start Inclusive start offset.
   * @param end Exclusive end offset.
   * @param position Zero-based term index.
   * @return The analyzed term.
   */
  private static Term term(String text, int start, int end, int position) {
    return new Term(StringUtil.toLowerCase(text.substring(start, end)), start, end, position);
  }
}
