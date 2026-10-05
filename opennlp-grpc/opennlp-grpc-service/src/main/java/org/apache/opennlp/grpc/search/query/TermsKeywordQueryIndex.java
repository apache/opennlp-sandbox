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
package org.apache.opennlp.grpc.search.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.search.query.QueryTermAnalyzer.Term;
import org.apache.opennlp.grpc.v1.MatchedSpan;
import org.apache.opennlp.grpc.v1.TermMatchMode;
import org.apache.opennlp.grpc.spi.search.QueryCandidate;
import org.apache.opennlp.grpc.spi.search.KeywordQueryIndex;

/** Built-in locale-independent code-point term and phrase execution. */
public final class TermsKeywordQueryIndex implements KeywordQueryIndex {

  private final List<QueryCandidate> candidates;
  private final Map<String, List<Term>> termsByChunkId;

  /**
   * Creates an immutable logical keyword component over retained candidate records.
   *
   * @param candidates Candidate records in index order.
   * @throws IllegalArgumentException If {@code candidates} is {@code null}.
   */
  public TermsKeywordQueryIndex(List<QueryCandidate> candidates) {
    if (candidates == null) {
      throw new IllegalArgumentException("candidates must not be null");
    }
    final List<QueryCandidate> copied = List.copyOf(candidates);
    final Map<String, List<Term>> analyzed = new LinkedHashMap<>();
    for (QueryCandidate candidate : copied) {
      final String chunkId = candidate.record().chunkId();
      if (analyzed.putIfAbsent(chunkId,
          QueryTermAnalyzer.analyze(candidate.record().indexedText())) != null) {
        throw new IllegalArgumentException("candidates contain duplicate chunk id '"
            + chunkId + "'");
      }
    }
    this.candidates = copied;
    this.termsByChunkId = Map.copyOf(analyzed);
  }

  /** {@inheritDoc} */
  @Override
  public List<Hit> term(String text, TermMatchMode mode) {
    final List<Term> analyzed = QueryTermAnalyzer.analyze(text);
    if (analyzed.isEmpty()) {
      throw AnalysisException.invalidArgument(
          "term.text contains no analyzable terms: '" + text + "'");
    }
    final Set<String> queryTerms = new LinkedHashSet<>();
    for (Term term : analyzed) {
      queryTerms.add(term.text());
    }
    final boolean requireAll = mode == TermMatchMode.TERM_MATCH_MODE_ALL;
    final Map<QueryCandidate, Double> raw = new LinkedHashMap<>();
    final Map<QueryCandidate, List<MatchedSpan>> spans = new LinkedHashMap<>();
    for (QueryCandidate candidate : candidates) {
      final Map<String, Integer> frequency = new HashMap<>();
      final List<MatchedSpan> matched = new ArrayList<>();
      for (Term term : terms(candidate)) {
        if (queryTerms.contains(term.text())) {
          frequency.merge(term.text(), 1, Integer::sum);
          matched.add(span(term.start(), term.end(), term.text()));
        }
      }
      if (requireAll ? frequency.size() == queryTerms.size() : !frequency.isEmpty()) {
        double score = 0;
        for (int count : frequency.values()) {
          score += 1 + Math.log(count);
        }
        raw.put(candidate, score);
        spans.put(candidate, matched);
      }
    }
    return normalized(raw, spans);
  }

  /** {@inheritDoc} */
  @Override
  public List<Hit> phrase(String text, int slop) {
    final List<Term> analyzed = QueryTermAnalyzer.analyze(text);
    if (analyzed.isEmpty()) {
      throw AnalysisException.invalidArgument(
          "phrase.text contains no analyzable terms: '" + text + "'");
    }
    final List<String> phrase = analyzed.stream().map(Term::text).toList();
    final String label = String.join(" ", phrase);
    final Map<QueryCandidate, Double> raw = new LinkedHashMap<>();
    final Map<QueryCandidate, List<MatchedSpan>> spans = new LinkedHashMap<>();
    for (QueryCandidate candidate : candidates) {
      final List<Term> terms = terms(candidate);
      final List<MatchedSpan> matched = new ArrayList<>();
      for (int start = 0; start < terms.size(); start++) {
        final int end = matchPhraseAt(terms, start, phrase, slop);
        if (end >= 0) {
          matched.add(span(terms.get(start).start(), terms.get(end).end(), label));
        }
      }
      if (!matched.isEmpty()) {
        raw.put(candidate, (double) matched.size());
        spans.put(candidate, matched);
      }
    }
    return normalized(raw, spans);
  }

  /**
   * Returns the immutable analyzed terms of one retained indexed-text value.
   *
   * @param candidate Candidate to analyze.
   * @return Analyzed terms.
   */
  private List<Term> terms(QueryCandidate candidate) {
    return termsByChunkId.get(candidate.record().chunkId());
  }

  /**
   * Attempts one in-order phrase match from a candidate term position.
   *
   * @param terms Candidate terms.
   * @param start Candidate start position.
   * @param phrase Query terms.
   * @param slop Maximum extra positions.
   * @return Inclusive candidate end position, or {@code -1} when unmatched.
   */
  private int matchPhraseAt(
      List<Term> terms, int start, List<String> phrase, int slop) {
    if (!terms.get(start).text().equals(phrase.getFirst())) {
      return -1;
    }
    int previous = start;
    for (int phraseIndex = 1; phraseIndex < phrase.size(); phraseIndex++) {
      int found = -1;
      final int limit = Math.min(terms.size() - 1, previous + 1 + slop);
      for (int position = previous + 1; position <= limit; position++) {
        if (terms.get(position).text().equals(phrase.get(phraseIndex))) {
          found = position;
          break;
        }
      }
      if (found < 0) {
        return -1;
      }
      previous = found;
    }
    return previous;
  }

  /**
   * Converts raw occurrence relevance into provider results normalized by the maximum.
   *
   * @param raw Raw relevance by candidate.
   * @param spans Matched spans by candidate.
   * @return Normalized provider results.
   */
  private List<Hit> normalized(
      Map<QueryCandidate, Double> raw,
      Map<QueryCandidate, List<MatchedSpan>> spans) {
    final double maximum = raw.values().stream().mapToDouble(Double::doubleValue)
        .max().orElse(0);
    final List<Hit> hits = new ArrayList<>(raw.size());
    for (Map.Entry<QueryCandidate, Double> entry : raw.entrySet()) {
      hits.add(new Hit(entry.getKey().record(), entry.getValue() / maximum,
          spans.get(entry.getKey())));
    }
    return List.copyOf(hits);
  }

  /**
   * Builds one indexed-text match span.
   *
   * @param start Inclusive UTF-16 offset.
   * @param end Exclusive UTF-16 offset.
   * @param term Matched query term.
   * @return Match span.
   */
  private MatchedSpan span(int start, int end, String term) {
    return MatchedSpan.newBuilder().setStart(start).setEnd(end).setTerm(term).build();
  }
}
