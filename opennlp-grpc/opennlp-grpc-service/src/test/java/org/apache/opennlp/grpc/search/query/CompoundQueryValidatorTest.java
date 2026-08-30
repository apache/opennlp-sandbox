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

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.BoostClause;
import org.apache.opennlp.grpc.v1.CelScore;
import org.apache.opennlp.grpc.v1.JoinFusion;
import org.apache.opennlp.grpc.v1.JoinOperator;
import org.apache.opennlp.grpc.v1.QueryNode;
import org.apache.opennlp.grpc.v1.ScoreNormalization;
import org.junit.jupiter.api.Test;

import static org.apache.opennlp.grpc.search.query.QueryTestSupport.and;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.boost;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.boostBy;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.calculator;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.excluding;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.filter;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.fused;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.or;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.phrase;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.semantic;
import static org.apache.opennlp.grpc.search.query.QueryTestSupport.term;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the structural rules of the compound query contract. */
class CompoundQueryValidatorTest {

  @Test
  void acceptsTheFullGrammar() {
    final QueryNode tree = excluding(
        and(
            or(semantic("habeas corpus"), term("habeas")),
            phrase("writ of habeas corpus", 1),
            filter("flag:published"),
            calculator("value:recency", ScoreNormalization.SCORE_NORMALIZATION_LOGISTIC),
            boost(term("corpus"), 2.0),
            boostBy(phrase("habeas corpus", 0), "value:citations",
                ScoreNormalization.SCORE_NORMALIZATION_MINMAX)),
        term("dissent"), filter("flag:sealed"));

    assertDoesNotThrow(() -> CompoundQueryValidator.validate(tree));
    assertDoesNotThrow(() -> CompoundQueryValidator.validate(
        fused(or(term("alpha"), term("beta")), JoinFusion.JOIN_FUSION_RECIPROCAL_RANK)));
  }

  @Test
  void rejectsAnUnsetNodeAndBlankLeafText() {
    assertInvalid(QueryNode.getDefaultInstance(), "exactly one clause kind");
    assertInvalid(term(" "), "term.text");
    assertInvalid(phrase(" ", 0), "phrase.text");
    assertInvalid(semantic(" "), "raw_text");
  }

  @Test
  void rejectsNonGenerativeRootsPerRuleEight() {
    assertInvalid(calculator("value:x", ScoreNormalization.SCORE_NORMALIZATION_CLAMP),
        "match semantics");
    assertInvalid(filter("flag:x"), "match semantics");
    assertInvalid(and(filter("flag:x"),
            calculator("value:x", ScoreNormalization.SCORE_NORMALIZATION_CLAMP)),
        "match semantics");
  }

  @Test
  void rejectsMalformedJoins() {
    assertInvalid(QueryNode.newBuilder()
        .setJoin(org.apache.opennlp.grpc.v1.JoinClause.newBuilder()
            .setOperator(JoinOperator.JOIN_OPERATOR_AND))
        .build(), "operands");
    assertInvalid(QueryNode.newBuilder()
        .setJoin(org.apache.opennlp.grpc.v1.JoinClause.newBuilder()
            .addOperands(term("alpha")))
        .build(), "operator");
    assertInvalid(or(term("alpha"), filter("flag:x")), "OR join");
  }

  @Test
  void rejectsMalformedBoosts() {
    assertInvalid(boost(filter("flag:x"), 2.0), "cannot be boosted");
    assertInvalid(boost(calculator("value:x", ScoreNormalization.SCORE_NORMALIZATION_CLAMP),
        2.0), "cannot be boosted");
    assertInvalid(boost(term("alpha"), -0.5), "nonnegative");
    assertInvalid(boost(term("alpha"), Double.NaN), "finite");
    assertInvalid(QueryNode.newBuilder()
        .setBoost(BoostClause.newBuilder().setOperand(term("alpha")))
        .build(), "weight or calculator");
    assertInvalid(QueryNode.newBuilder()
        .setBoost(BoostClause.newBuilder()
            .setOperand(term("alpha"))
            .setCalculator(CelScore.newBuilder().setExpression("value:x")))
        .build(), "normalization");
  }

  @Test
  void rejectsNondeterministicExclusions() {
    assertInvalid(excluding(or(term("alpha")), semantic("beta")), "exclusion");
    assertInvalid(excluding(or(term("alpha")), boost(term("beta"), 2.0)), "exclusion");
    assertInvalid(excluding(or(term("alpha")),
        calculator("value:x", ScoreNormalization.SCORE_NORMALIZATION_CLAMP)), "exclusion");
  }

  @Test
  void boundsTreeSizeAndDepth() {
    QueryNode nested = term("alpha");
    for (int depth = 0; depth < CompoundQueryValidator.MAX_DEPTH; depth++) {
      nested = boost(nested, 1.0);
    }
    final QueryNode tooDeep = nested;
    assertInvalid(tooDeep, "nesting depth");

    final org.apache.opennlp.grpc.v1.JoinClause.Builder wide =
        org.apache.opennlp.grpc.v1.JoinClause.newBuilder()
            .setOperator(JoinOperator.JOIN_OPERATOR_OR);
    for (int index = 0; index < CompoundQueryValidator.MAX_NODES; index++) {
      wide.addOperands(term("term" + index));
    }
    assertInvalid(QueryNode.newBuilder().setJoin(wide).build(), "maximum of");
  }

  private static void assertInvalid(QueryNode tree, String messagePart) {
    final AnalysisException failure = assertThrows(AnalysisException.class,
        () -> CompoundQueryValidator.validate(tree));
    assertTrue(failure.getMessage().contains(messagePart),
        "expected '" + messagePart + "' in: " + failure.getMessage());
  }
}
