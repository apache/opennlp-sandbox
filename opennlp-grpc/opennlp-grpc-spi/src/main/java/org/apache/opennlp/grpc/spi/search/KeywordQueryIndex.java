/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.spi.search;

import java.util.List;

import org.apache.opennlp.grpc.v1.MatchedSpan;
import org.apache.opennlp.grpc.v1.TermMatchMode;

/** Provider-owned execution of typed term and phrase query leaves. */
public interface KeywordQueryIndex {

  /**
   * One normalized keyword result with provider-defined matching spans.
   *
   * @param record Matched source record.
   * @param score Normalized score in {@code [0, 1]}.
   * @param matchedSpans Match locations in indexed text.
   */
  record Hit(SearchRecord record, double score, List<MatchedSpan> matchedSpans) {

    /** Validates the provider result shape. */
    public Hit {
      if (record == null || matchedSpans == null) {
        throw new IllegalArgumentException("keyword hit fields must not be null");
      }
      matchedSpans = List.copyOf(matchedSpans);
    }
  }

  /**
   * Executes one typed term leaf.
   *
   * @param text Query text.
   * @param mode Term combination mode.
   * @return Results with scores normalized to {@code [0, 1]}.
   */
  List<Hit> term(String text, TermMatchMode mode);

  /**
   * Executes one typed phrase leaf.
   *
   * @param text Query text.
   * @param slop Maximum extra positions between adjacent query terms.
   * @return Results with scores normalized to {@code [0, 1]}.
   */
  List<Hit> phrase(String text, int slop);
}
