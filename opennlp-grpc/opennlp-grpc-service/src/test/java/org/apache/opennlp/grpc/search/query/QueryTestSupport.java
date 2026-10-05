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

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.apache.opennlp.grpc.spi.search.SearchRecord;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.BoostClause;
import org.apache.opennlp.grpc.v1.CelCalculatorClause;
import org.apache.opennlp.grpc.v1.CelFilterClause;
import org.apache.opennlp.grpc.v1.CelScore;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.JoinClause;
import org.apache.opennlp.grpc.v1.JoinFusion;
import org.apache.opennlp.grpc.v1.JoinOperator;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PhraseClause;
import org.apache.opennlp.grpc.v1.QueryNode;
import org.apache.opennlp.grpc.v1.ScoreNormalization;
import org.apache.opennlp.grpc.v1.SemanticClause;
import org.apache.opennlp.grpc.v1.TermClause;
import org.apache.opennlp.grpc.v1.TermMatchMode;
import org.apache.opennlp.grpc.spi.search.QueryCandidate;

/** Builders shared by the compound query engine tests. */
final class QueryTestSupport {

  private QueryTestSupport() {
  }

  static QueryNode semantic(String text) {
    return QueryNode.newBuilder()
        .setSemantic(SemanticClause.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setDocId("query").setRawText(text)))
        .build();
  }

  static QueryNode term(String text, TermMatchMode mode) {
    return QueryNode.newBuilder()
        .setTerm(TermClause.newBuilder().setText(text).setMode(mode))
        .build();
  }

  static QueryNode term(String text) {
    return term(text, TermMatchMode.TERM_MATCH_MODE_ANY);
  }

  static QueryNode phrase(String text, int slop) {
    return QueryNode.newBuilder()
        .setPhrase(PhraseClause.newBuilder().setText(text).setSlop(slop))
        .build();
  }

  static QueryNode join(JoinOperator operator, QueryNode... operands) {
    final JoinClause.Builder join = JoinClause.newBuilder().setOperator(operator);
    for (QueryNode operand : operands) {
      join.addOperands(operand);
    }
    return QueryNode.newBuilder().setJoin(join).build();
  }

  static QueryNode and(QueryNode... operands) {
    return join(JoinOperator.JOIN_OPERATOR_AND, operands);
  }

  static QueryNode or(QueryNode... operands) {
    return join(JoinOperator.JOIN_OPERATOR_OR, operands);
  }

  static QueryNode excluding(QueryNode joinNode, QueryNode... exclusions) {
    final JoinClause.Builder join = joinNode.getJoin().toBuilder();
    for (QueryNode exclusion : exclusions) {
      join.addExclusions(exclusion);
    }
    return QueryNode.newBuilder().setJoin(join).build();
  }

  static QueryNode fused(QueryNode joinNode, JoinFusion fusion) {
    return QueryNode.newBuilder()
        .setJoin(joinNode.getJoin().toBuilder().setFusion(fusion))
        .build();
  }

  static QueryNode boost(QueryNode operand, double weight) {
    return QueryNode.newBuilder()
        .setBoost(BoostClause.newBuilder().setOperand(operand).setWeight(weight))
        .build();
  }

  static QueryNode boostBy(QueryNode operand, String expression, ScoreNormalization norm) {
    return QueryNode.newBuilder()
        .setBoost(BoostClause.newBuilder()
            .setOperand(operand)
            .setCalculator(CelScore.newBuilder()
                .setExpression(expression).setNormalization(norm)))
        .build();
  }

  static QueryNode filter(String expression) {
    return QueryNode.newBuilder()
        .setCelFilter(CelFilterClause.newBuilder().setExpression(expression))
        .build();
  }

  static QueryNode calculator(String expression, ScoreNormalization norm) {
    return QueryNode.newBuilder()
        .setCelCalculator(CelCalculatorClause.newBuilder()
            .setScore(CelScore.newBuilder()
                .setExpression(expression).setNormalization(norm)))
        .build();
  }

  static QueryCandidate candidate(String documentId, String text, float... vector) {
    return candidate(documentId, documentId + ":0", text,
        Struct.getDefaultInstance(), vector);
  }

  static QueryCandidate candidate(
      String documentId, String chunkId, String text, Struct metadata, float... vector) {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setDocId(documentId)
        .setRawText(text)
        .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
        .setMetadata(metadata)
        .build();
    final AnnotationSpan span = AnnotationSpan.newBuilder()
        .setStart(0)
        .setEnd(text.length())
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
    return new QueryCandidate(
        new SearchRecord(documentId, chunkId, document, span, text), vector);
  }

  static Struct metadata(String key, double value) {
    return Struct.newBuilder()
        .putFields(key, Value.newBuilder().setNumberValue(value).build())
        .build();
  }

  static Struct metadata(String key, boolean value) {
    return Struct.newBuilder()
        .putFields(key, Value.newBuilder().setBoolValue(value).build())
        .build();
  }

  /**
   * Test stand-in for an installed CEL evaluator. Filter expressions: {@code flag:<key>}
   * tests a boolean metadata field, {@code bad-typecheck} fails compilation, and
   * {@code explode} fails at evaluation. Calculator expressions: {@code value:<key>}
   * reads a numeric field defaulting to zero, {@code const:<v>} is a constant,
   * {@code inf} returns infinity, {@code bad-typecheck} fails compilation, and
   * {@code explode} fails at evaluation.
   */
  static final class StubCelEvaluator implements CelQueryEvaluator {

    @Override
    public CompiledFilter compileFilter(String expression) {
      if ("bad-typecheck".equals(expression)) {
        throw new IllegalArgumentException("expression is not bool");
      }
      return metadata -> {
        if ("explode".equals(expression)) {
          throw new IllegalArgumentException("no such key");
        }
        final String key = expression.substring("flag:".length());
        return metadata.getFieldsOrDefault(key,
            com.google.protobuf.Value.getDefaultInstance()).getBoolValue();
      };
    }

    @Override
    public CompiledCalculator compileCalculator(String expression) {
      if ("bad-typecheck".equals(expression)) {
        throw new IllegalArgumentException("expression is not numeric");
      }
      return metadata -> {
        if ("explode".equals(expression)) {
          throw new IllegalArgumentException("no such key");
        }
        if ("inf".equals(expression)) {
          return Double.POSITIVE_INFINITY;
        }
        if (expression.startsWith("const:")) {
          return Double.parseDouble(expression.substring("const:".length()));
        }
        final String key = expression.substring("value:".length());
        return metadata.getFieldsOrDefault(key,
            com.google.protobuf.Value.getDefaultInstance()).getNumberValue();
      };
    }
  }
}
