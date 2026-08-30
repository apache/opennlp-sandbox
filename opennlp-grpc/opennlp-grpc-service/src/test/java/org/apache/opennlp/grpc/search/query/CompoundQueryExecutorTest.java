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

import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.search.query.CompoundQueryExecutor.QueryHit;
import org.apache.opennlp.grpc.v1.JoinFusion;
import org.apache.opennlp.grpc.v1.MatchedSpan;
import org.apache.opennlp.grpc.v1.ScoreNormalization;
import org.apache.opennlp.grpc.v1.TermMatchMode;
import org.junit.jupiter.api.Test;

import static org.apache.opennlp.grpc.search.query.QueryTestSupport.and;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.boost;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.boostBy;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.calculator;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.candidate;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.excluding;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.filter;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.fused;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.metadata;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.or;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.phrase;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.semantic;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.term;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.opennlp.grpc.spi.search.QueryCandidate;
import org.apache.opennlp.grpc.spi.search.KeywordQueryIndex;

/** Pins the normative score algebra, the terms executor, and the CEL seam. */
class CompoundQueryExecutorTest {

  private static final CompoundQueryExecutor WITH_CEL =
      new CompoundQueryExecutor(new QueryTestSupport.StubCelEvaluator());
  private static final CompoundQueryExecutor WITHOUT_CEL = new CompoundQueryExecutor(null);
  private static final CompoundQueryExecutor.QueryEmbedder UNIT_X =
      document -> new float[] {1, 0};

  @Test
  void semanticScoresMapCosineIntoTheUnitInterval() {
    final List<QueryCandidate> candidates = List.of(
        candidate("aligned", "aligned text", 1, 0),
        candidate("orthogonal", "orthogonal text", 0, 1),
        candidate("opposed", "opposed text", -1, 0));

    final List<QueryHit> hits = WITHOUT_CEL.execute(semantic("anything"), candidates, UNIT_X, 3);

    assertEquals(List.of("aligned:0", "orthogonal:0", "opposed:0"),
        hits.stream().map(hit -> hit.candidate().record().chunkId()).toList());
    assertEquals(1.0, hits.get(0).score(), 1e-9);
    assertEquals(0.5, hits.get(1).score(), 1e-9);
    assertEquals(0.0, hits.get(2).score(), 1e-9);
  }

  @Test
  void termAnyNormalizesByTheQueryTopScoreAndReportsSpans() {
    final List<QueryCandidate> candidates = List.of(
        candidate("both", "Alpha beta alpha", 1, 0),
        candidate("one", "alpha gamma", 1, 0),
        candidate("none", "delta", 1, 0));

    final List<QueryHit> hits = WITHOUT_CEL.execute(
        term("Alpha BETA"), candidates, UNIT_X, 10);

    assertEquals(2, hits.size());
    assertEquals("both:0", hits.get(0).candidate().record().chunkId());
    assertEquals(1.0, hits.get(0).score(), 1e-9);
    final double expected = 1 / (2 + Math.log(2));
    assertEquals(expected, hits.get(1).score(), 1e-9);
    final List<MatchedSpan> spans = hits.get(0).matchedSpans();
    assertEquals(3, spans.size());
    assertEquals(0, spans.get(0).getStart());
    assertEquals(5, spans.get(0).getEnd());
    assertEquals("alpha", spans.get(0).getTerm());
    assertEquals("beta", spans.get(1).getTerm());
    assertEquals(11, spans.get(2).getStart());
    assertEquals(16, spans.get(2).getEnd());
  }

  @Test
  void delegatesKeywordLeavesToTheSelectedProviderIndex() {
    final List<QueryCandidate> candidates = List.of(
        candidate("first", "ignored", 1, 0),
        candidate("second", "also ignored", 0, 1));
    final KeywordQueryIndex replacement = new KeywordQueryIndex() {
      @Override
      public List<Hit> term(String text, TermMatchMode mode) {
        return List.of(new Hit(candidates.get(1).record(), 0.25, List.of(
            MatchedSpan.newBuilder().setStart(0).setEnd(4).setTerm("custom").build())));
      }

      @Override
      public List<Hit> phrase(String text, int slop) {
        return List.of();
      }
    };

    final List<QueryHit> hits = WITHOUT_CEL.execute(
        term("provider owned"), candidates, UNIT_X,
        (query, topK) -> List.of(), replacement, 10);

    assertEquals(1, hits.size());
    assertEquals("second", hits.getFirst().candidate().record().documentId());
    assertEquals(0.25, hits.getFirst().score(), 1e-9);
    assertEquals("custom", hits.getFirst().matchedSpans().getFirst().getTerm());
  }

  @Test
  void termAllRequiresEveryAnalyzedTerm() {
    final List<QueryCandidate> candidates = List.of(
        candidate("both", "alpha beta", 1, 0),
        candidate("one", "alpha gamma", 1, 0));

    final List<QueryHit> hits = WITHOUT_CEL.execute(
        term("alpha beta", TermMatchMode.TERM_MATCH_MODE_ALL), candidates, UNIT_X, 10);

    assertEquals(1, hits.size());
    assertEquals("both:0", hits.getFirst().candidate().record().chunkId());
  }

  @Test
  void phraseMatchesInOrderWithinSlop() {
    final List<QueryCandidate> candidates = List.of(
        candidate("exact", "new york city", 1, 0),
        candidate("gapped", "new big york", 1, 0),
        candidate("reversed", "york new", 1, 0));

    final List<QueryHit> exact = WITHOUT_CEL.execute(
        phrase("New York", 0), candidates, UNIT_X, 10);
    assertEquals(List.of("exact:0"),
        exact.stream().map(hit -> hit.candidate().record().chunkId()).toList());
    assertEquals("new york", exact.getFirst().matchedSpans().getFirst().getTerm());
    assertEquals(0, exact.getFirst().matchedSpans().getFirst().getStart());
    assertEquals(8, exact.getFirst().matchedSpans().getFirst().getEnd());

    final List<QueryHit> slopped = WITHOUT_CEL.execute(
        phrase("new york", 1), candidates, UNIT_X, 10);
    assertEquals(List.of("exact:0", "gapped:0"),
        slopped.stream().map(hit -> hit.candidate().record().chunkId()).toList());
    assertEquals(12, slopped.get(1).matchedSpans().getFirst().getEnd());
  }

  @Test
  void andAveragesAndOrTakesTheMaximum() {
    final List<QueryCandidate> candidates = List.of(
        candidate("both", "x x y", 1, 0),
        candidate("onlyX", "x", 1, 0),
        candidate("onlyY", "y", 1, 0));

    final List<QueryHit> orHits = WITHOUT_CEL.execute(
        or(term("x"), term("y")), candidates, UNIT_X, 10);
    final Map<String, Double> orScores = scoresByDocument(orHits);
    assertEquals(1.0, orScores.get("both"), 1e-9);
    assertEquals(1 / (1 + Math.log(2)), orScores.get("onlyX"), 1e-9);
    assertEquals(1.0, orScores.get("onlyY"), 1e-9);

    final List<QueryHit> andHits = WITHOUT_CEL.execute(
        and(term("x"), term("y")), candidates, UNIT_X, 10);
    assertEquals(1, andHits.size());
    assertEquals("both", andHits.getFirst().candidate().record().documentId());
    assertEquals(1.0, andHits.getFirst().score(), 1e-9);
  }

  @Test
  void exclusionsRemoveMembershipWithoutScoring() {
    final List<QueryCandidate> candidates = List.of(
        candidate("kept", "x x", 1, 0),
        candidate("dropped", "x y", 1, 0));

    final List<QueryHit> hits = WITHOUT_CEL.execute(
        excluding(or(term("x")), term("y")), candidates, UNIT_X, 10);

    assertEquals(List.of("kept"),
        hits.stream().map(hit -> hit.candidate().record().documentId()).toList());
  }

  @Test
  void reciprocalRankFusionNormalizesTheSummedRanks() {
    final List<QueryCandidate> candidates = List.of(
        candidate("both", "x x y", 1, 0),
        candidate("onlyX", "x", 1, 0),
        candidate("onlyY", "y y", 1, 0));

    final List<QueryHit> hits = WITHOUT_CEL.execute(
        fused(or(term("x"), term("y")), JoinFusion.JOIN_FUSION_RECIPROCAL_RANK),
        candidates, UNIT_X, 10);

    final Map<String, Double> scores = scoresByDocument(hits);
    // both: rank 1 on the x component and rank 2 on the y component.
    final double both = 1d / 61 + 1d / 62;
    final double onlyY = 1d / 61;
    assertEquals(1.0, scores.get("both"), 1e-9);
    assertEquals(onlyY / both, scores.get("onlyY"), 1e-9);
    assertEquals((1d / 62) / both, scores.get("onlyX"), 1e-9);
  }

  @Test
  void boostMultipliesAndClampsWithoutChangingMembership() {
    final List<QueryCandidate> candidates = List.of(
        candidate("strong", "x x", 1, 0),
        candidate("weak", "x", 1, 0));

    final List<QueryHit> boosted = WITHOUT_CEL.execute(
        boost(term("x"), 3.0), candidates, UNIT_X, 10);
    final Map<String, Double> scores = scoresByDocument(boosted);
    assertEquals(2, boosted.size());
    assertEquals(1.0, scores.get("strong"), 1e-9);
    assertEquals(Math.min(1, 3.0 / (1 + Math.log(2))), scores.get("weak"), 1e-9);

    final Map<String, Double> halved = scoresByDocument(WITHOUT_CEL.execute(
        boost(term("x"), 0.5), candidates, UNIT_X, 10));
    assertEquals(0.5, halved.get("strong"), 1e-9);
  }

  @Test
  void filtersGateWithinAndJoins() {
    final List<QueryCandidate> candidates = List.of(
        candidate("kept", "kept:0", "x", metadata("published", true), 1, 0),
        candidate("gated", "gated:0", "x", metadata("published", false), 1, 0));

    final List<QueryHit> hits = WITH_CEL.execute(
        and(term("x"), filter("flag:published")), candidates, UNIT_X, 10);

    assertEquals(List.of("kept"),
        hits.stream().map(hit -> hit.candidate().record().documentId()).toList());
    assertEquals(1.0, hits.getFirst().score(), 1e-9);
  }

  @Test
  void calculatorsScoreTheSiblingAdmittedSet() {
    final List<QueryCandidate> candidates = List.of(
        candidate("newest", "newest:0", "x", metadata("year", 2024), 1, 0),
        candidate("oldest", "oldest:0", "x", metadata("year", 2004), 1, 0));

    final List<QueryHit> hits = WITH_CEL.execute(
        and(term("x"),
            calculator("value:year", ScoreNormalization.SCORE_NORMALIZATION_MINMAX)),
        candidates, UNIT_X, 10);

    final Map<String, Double> scores = scoresByDocument(hits);
    // Mean of the term score (1.0 for both) and the min-max year (1 and 0).
    assertEquals(1.0, scores.get("newest"), 1e-9);
    assertEquals(0.5, scores.get("oldest"), 1e-9);
  }

  @Test
  void calculatorNormalizationsFollowTheDeclaredMapping() {
    final List<QueryCandidate> candidates = List.of(candidate("only", "only:0", "x",
        metadata("year", 2024), 1, 0));

    final Map<String, Double> clamp = scoresByDocument(WITH_CEL.execute(
        and(term("x"), calculator("const:2.5", ScoreNormalization.SCORE_NORMALIZATION_CLAMP)),
        candidates, UNIT_X, 10));
    assertEquals(1.0, clamp.get("only"), 1e-9);

    final Map<String, Double> logistic = scoresByDocument(WITH_CEL.execute(
        and(term("x"),
            calculator("const:0", ScoreNormalization.SCORE_NORMALIZATION_LOGISTIC)),
        candidates, UNIT_X, 10));
    assertEquals((1.0 + 0.5) / 2, logistic.get("only"), 1e-9);
  }

  @Test
  void boostCalculatorsShapeRelevancyPerCandidate() {
    final List<QueryCandidate> candidates = List.of(
        candidate("hot", "hot:0", "x", metadata("heat", 1000), 1, 0),
        candidate("cold", "cold:0", "x", metadata("heat", -1000), 1, 0));

    final Map<String, Double> scores = scoresByDocument(WITH_CEL.execute(
        boostBy(term("x"), "value:heat", ScoreNormalization.SCORE_NORMALIZATION_LOGISTIC),
        candidates, UNIT_X, 10));

    assertEquals(1.0, scores.get("hot"), 1e-6);
    assertEquals(0.0, scores.get("cold"), 1e-6);
  }

  @Test
  void celFailuresReportTheirCause() {
    final List<QueryCandidate> candidates =
        List.of(candidate("only", "only:0", "x", metadata("year", 2024), 1, 0));

    assertTrue(assertThrows(AnalysisException.class, () -> WITHOUT_CEL.execute(
        and(term("x"), filter("flag:published")), candidates, UNIT_X, 10))
        .getMessage().contains("No CEL evaluator"));
    assertTrue(assertThrows(AnalysisException.class, () -> WITH_CEL.execute(
        and(term("x"), filter("bad-typecheck")), candidates, UNIT_X, 10))
        .getMessage().contains("type-check"));
    assertTrue(assertThrows(AnalysisException.class, () -> WITH_CEL.execute(
        and(term("x"), filter("explode")), candidates, UNIT_X, 10))
        .getMessage().contains("evaluation failed"));
    assertTrue(assertThrows(AnalysisException.class, () -> WITH_CEL.execute(
        and(term("x"), calculator("inf", ScoreNormalization.SCORE_NORMALIZATION_CLAMP)),
        candidates, UNIT_X, 10))
        .getMessage().contains("non-finite"));
  }

  @Test
  void ranksDeterministicallyAndCutsAtTopK() {
    final List<QueryCandidate> candidates = List.of(
        candidate("bravo", "bravo:0", "x", metadata("year", 1), 1, 0),
        candidate("alpha", "alpha:0", "x", metadata("year", 1), 1, 0),
        candidate("charlie", "charlie:0", "x x", metadata("year", 1), 1, 0));

    final List<QueryHit> hits = WITHOUT_CEL.execute(term("x"), candidates, UNIT_X, 2);

    assertEquals(List.of("charlie:0", "alpha:0"),
        hits.stream().map(hit -> hit.candidate().record().chunkId()).toList());
  }

  @Test
  void emptyCandidatesYieldNoHits() {
    assertEquals(List.of(), WITHOUT_CEL.execute(term("x"), List.of(), UNIT_X, 5));
  }

  private static Map<String, Double> scoresByDocument(List<QueryHit> hits) {
    return hits.stream().collect(java.util.stream.Collectors.toMap(
        hit -> hit.candidate().record().documentId(), QueryHit::score));
  }
}
