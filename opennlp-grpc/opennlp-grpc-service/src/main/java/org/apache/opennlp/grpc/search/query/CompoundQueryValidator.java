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
import org.apache.opennlp.grpc.v1.JoinClause;
import org.apache.opennlp.grpc.v1.JoinOperator;
import org.apache.opennlp.grpc.v1.QueryNode;
import org.apache.opennlp.grpc.v1.ScoreNormalization;

/**
 * Structural validation of a compound query tree before execution: every node carries
 * exactly one specified clause, text inputs are non-blank, boost factors are finite and
 * nonnegative, and the membership rules of the score algebra hold. In particular the
 * root and every join must be generative, meaning able to produce candidates from
 * content, which enforces algebra rule 8: a calculator can never be the root or the only
 * scoring operand of a join. Filters gate, so they are valid only under AND joins and as
 * exclusions; exclusions must have deterministic membership, so semantic, boost, and
 * calculator clauses are invalid anywhere inside them.
 */
public final class CompoundQueryValidator {

  /** Largest accepted number of nodes in one compound query tree. */
  static final int MAX_NODES = 128;
  /** Deepest accepted nesting of one compound query tree. */
  static final int MAX_DEPTH = 16;

  private CompoundQueryValidator() {
  }

  /**
   * Validates one compound query tree.
   *
   * @param root Root query node.
   * @throws AnalysisException If the tree violates the compound query contract.
   */
  public static void validate(QueryNode root) {
    if (root == null) {
      throw AnalysisException.invalidArgument("compound_query must not be null");
    }
    final int nodes = countNodes(root, "compound_query", 1);
    if (nodes > MAX_NODES) {
      throw AnalysisException.invalidArgument(
          "compound_query exceeds the maximum of " + MAX_NODES + " nodes");
    }
    validateNode(root, "compound_query", false, false);
    if (!isGenerative(root)) {
      throw AnalysisException.invalidArgument("compound_query root must contain at least one "
          + "semantic, term, or phrase clause; filters and calculators have no match "
          + "semantics of their own");
    }
  }

  /**
   * Tests whether a tree contains a CEL filter or calculator clause anywhere.
   *
   * @param node Query node.
   * @return {@code true} when a CEL clause is present.
   */
  public static boolean containsCelClause(QueryNode node) {
    return switch (node.getKindCase()) {
      case CEL_FILTER, CEL_CALCULATOR -> true;
      case BOOST -> node.getBoost().getFactorCase() == BoostClause.FactorCase.CALCULATOR
          || containsCelClause(node.getBoost().getOperand());
      case JOIN -> node.getJoin().getOperandsList().stream()
          .anyMatch(CompoundQueryValidator::containsCelClause)
          || node.getJoin().getExclusionsList().stream()
              .anyMatch(CompoundQueryValidator::containsCelClause);
      case SEMANTIC, TERM, PHRASE, KIND_NOT_SET -> false;
    };
  }

  /**
   * Reports whether a query tree contains a term or phrase leaf that requires a keyword
   * provider.
   *
   * @param node Query tree to inspect.
   * @return Whether keyword execution is required.
   */
  public static boolean containsKeywordClause(QueryNode node) {
    return switch (node.getKindCase()) {
      case TERM, PHRASE -> true;
      case BOOST -> containsKeywordClause(node.getBoost().getOperand());
      case JOIN -> node.getJoin().getOperandsList().stream()
          .anyMatch(CompoundQueryValidator::containsKeywordClause)
          || node.getJoin().getExclusionsList().stream()
              .anyMatch(CompoundQueryValidator::containsKeywordClause);
      case SEMANTIC, CEL_FILTER, CEL_CALCULATOR, KIND_NOT_SET -> false;
    };
  }

  /**
   * Tests whether a node can produce candidates from content.
   *
   * @param node Query node.
   * @return {@code true} for semantic, term, and phrase clauses and for composites
   *     containing one.
   */
  static boolean isGenerative(QueryNode node) {
    return switch (node.getKindCase()) {
      case SEMANTIC, TERM, PHRASE -> true;
      case BOOST -> isGenerative(node.getBoost().getOperand());
      case JOIN -> node.getJoin().getOperandsList().stream()
          .anyMatch(CompoundQueryValidator::isGenerative);
      case CEL_FILTER, CEL_CALCULATOR, KIND_NOT_SET -> false;
    };
  }

  /**
   * Counts tree nodes while enforcing the depth bound.
   *
   * @param node Query node.
   * @param path Message path for failures.
   * @param depth Current nesting depth, root at one.
   * @return Number of nodes in this subtree.
   * @throws AnalysisException If the depth bound is exceeded.
   */
  private static int countNodes(QueryNode node, String path, int depth) {
    if (depth > MAX_DEPTH) {
      throw AnalysisException.invalidArgument(
          path + " exceeds the maximum nesting depth of " + MAX_DEPTH);
    }
    int count = 1;
    if (node.getKindCase() == QueryNode.KindCase.JOIN) {
      final JoinClause join = node.getJoin();
      for (int index = 0; index < join.getOperandsCount(); index++) {
        count += countNodes(join.getOperands(index),
            path + ".join.operands[" + index + "]", depth + 1);
      }
      for (int index = 0; index < join.getExclusionsCount(); index++) {
        count += countNodes(join.getExclusions(index),
            path + ".join.exclusions[" + index + "]", depth + 1);
      }
    } else if (node.getKindCase() == QueryNode.KindCase.BOOST) {
      count += countNodes(node.getBoost().getOperand(), path + ".boost.operand", depth + 1);
    }
    return count;
  }

  /**
   * Validates one node and its children.
   *
   * @param node Query node.
   * @param path Message path for failures.
   * @param underOr Whether the node is a direct operand of an OR join.
   * @param inExclusion Whether the node sits anywhere inside an exclusion list.
   * @throws AnalysisException If the node violates the contract.
   */
  private static void validateNode(
      QueryNode node, String path, boolean underOr, boolean inExclusion) {
    switch (node.getKindCase()) {
      case SEMANTIC -> {
        if (inExclusion) {
          throw AnalysisException.invalidArgument(path + ": a semantic clause has top-k "
              + "membership and cannot be an exclusion");
        }
        if (node.getSemantic().getDocument().getRawText().isBlank()) {
          throw AnalysisException.invalidArgument(
              path + ".semantic.document.raw_text must not be blank");
        }
      }
      case TERM -> {
        if (node.getTerm().getText().isBlank()) {
          throw AnalysisException.invalidArgument(path + ".term.text must not be blank");
        }
        if (node.getTerm().getMode() == org.apache.opennlp.grpc.v1.TermMatchMode.UNRECOGNIZED) {
          throw AnalysisException.invalidArgument(path + ".term.mode is unrecognized");
        }
      }
      case PHRASE -> {
        if (node.getPhrase().getText().isBlank()) {
          throw AnalysisException.invalidArgument(path + ".phrase.text must not be blank");
        }
      }
      case JOIN -> validateJoin(node.getJoin(), path + ".join", inExclusion);
      case BOOST -> validateBoost(node.getBoost(), path + ".boost", inExclusion);
      case CEL_FILTER -> {
        if (underOr) {
          throw AnalysisException.invalidArgument(path + ": a cel_filter gates membership "
              + "and cannot be a direct operand of an OR join; nest it under an AND join");
        }
        if (node.getCelFilter().getExpression().isBlank()) {
          throw AnalysisException.invalidArgument(
              path + ".cel_filter.expression must not be blank");
        }
      }
      case CEL_CALCULATOR -> {
        if (inExclusion) {
          throw AnalysisException.invalidArgument(
              path + ": a cel_calculator never decides membership and cannot be an exclusion");
        }
        validateCelScore(node.getCelCalculator().getScore(), path + ".cel_calculator.score");
      }
      case KIND_NOT_SET -> throw AnalysisException.invalidArgument(
          path + " must set exactly one clause kind");
    }
  }

  /**
   * Validates one join clause.
   *
   * @param join Join clause.
   * @param path Message path for failures.
   * @param inExclusion Whether the join sits inside an exclusion list.
   * @throws AnalysisException If the join violates the contract.
   */
  private static void validateJoin(JoinClause join, String path, boolean inExclusion) {
    if (join.getOperator() != JoinOperator.JOIN_OPERATOR_AND
        && join.getOperator() != JoinOperator.JOIN_OPERATOR_OR) {
      throw AnalysisException.invalidArgument(path + ".operator must be AND or OR");
    }
    if (join.getOperandsCount() < 1) {
      throw AnalysisException.invalidArgument(path + ".operands must not be empty");
    }
    final boolean or = join.getOperator() == JoinOperator.JOIN_OPERATOR_OR;
    boolean generative = false;
    for (int index = 0; index < join.getOperandsCount(); index++) {
      final QueryNode operand = join.getOperands(index);
      validateNode(operand, path + ".operands[" + index + "]", or, inExclusion);
      generative |= isGenerative(operand);
    }
    if (!generative) {
      throw AnalysisException.invalidArgument(path + " requires at least one semantic, "
          + "term, or phrase operand; filters and calculators have no match semantics "
          + "of their own");
    }
    for (int index = 0; index < join.getExclusionsCount(); index++) {
      validateNode(join.getExclusions(index), path + ".exclusions[" + index + "]", false, true);
    }
  }

  /**
   * Validates one boost clause.
   *
   * @param boost Boost clause.
   * @param path Message path for failures.
   * @param inExclusion Whether the boost sits inside an exclusion list.
   * @throws AnalysisException If the boost violates the contract.
   */
  private static void validateBoost(BoostClause boost, String path, boolean inExclusion) {
    if (inExclusion) {
      throw AnalysisException.invalidArgument(
          path + ": a boost shapes relevancy and cannot be an exclusion");
    }
    if (!boost.hasOperand()) {
      throw AnalysisException.invalidArgument(path + ".operand must be set");
    }
    if (boost.getOperand().getKindCase() == QueryNode.KindCase.CEL_FILTER) {
      throw AnalysisException.invalidArgument(
          path + ".operand: a cel_filter never scores, so it cannot be boosted");
    }
    if (boost.getOperand().getKindCase() == QueryNode.KindCase.CEL_CALCULATOR) {
      throw AnalysisException.invalidArgument(path + ".operand: a cel_calculator matches "
          + "only candidates admitted by join siblings, so it cannot be boosted directly; "
          + "boost the enclosing join instead");
    }
    validateNode(boost.getOperand(), path + ".operand", false, false);
    switch (boost.getFactorCase()) {
      case WEIGHT -> {
        final double weight = boost.getWeight();
        if (!Double.isFinite(weight) || weight < 0) {
          throw AnalysisException.invalidArgument(
              path + ".weight must be finite and nonnegative, was " + weight);
        }
      }
      case CALCULATOR -> validateCelScore(boost.getCalculator(), path + ".calculator");
      case FACTOR_NOT_SET -> throw AnalysisException.invalidArgument(
          path + " must set either weight or calculator");
    }
  }

  /**
   * Validates one CEL scoring expression declaration.
   *
   * @param score CEL score message.
   * @param path Message path for failures.
   * @throws AnalysisException If the expression or normalization is missing.
   */
  private static void validateCelScore(CelScore score, String path) {
    if (score.getExpression().isBlank()) {
      throw AnalysisException.invalidArgument(path + ".expression must not be blank");
    }
    if (score.getNormalization() == ScoreNormalization.SCORE_NORMALIZATION_UNSPECIFIED
        || score.getNormalization() == ScoreNormalization.UNRECOGNIZED) {
      throw AnalysisException.invalidArgument(
          path + ".normalization must be CLAMP, MINMAX, or LOGISTIC");
    }
  }
}
