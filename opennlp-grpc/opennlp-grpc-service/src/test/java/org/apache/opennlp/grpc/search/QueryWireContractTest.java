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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.search;

import java.util.List;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.opennlp.grpc.v1.OpenNlpQueryProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the compound query tree's wire shapes: the clause oneof, the
 * join-vs-boost split, and the two constrained CEL roles. The normative score
 * algebra is documented in opennlp_query.proto and pinned by executor tests
 * once execution lands.
 */
class QueryWireContractTest {

  @Test
  void queryNodeExposesExactlyTheSevenClauses() {
    final Descriptor node = OpenNlpQueryProto.getDescriptor()
        .findMessageTypeByName("QueryNode");
    assertNotNull(node);
    assertEquals(1, node.getOneofs().size());
    assertEquals("kind", node.getOneofs().getFirst().getName());
    assertEquals(
        List.of("semantic", "term", "phrase", "join", "boost",
            "cel_filter", "cel_calculator"),
        node.getOneofs().getFirst().getFields().stream()
            .map(FieldDescriptor::getName).toList());
    assertEquals("SemanticClause", node.findFieldByName("semantic").getMessageType().getName());
    assertEquals("TermClause", node.findFieldByName("term").getMessageType().getName());
    assertEquals("PhraseClause", node.findFieldByName("phrase").getMessageType().getName());
    assertEquals("JoinClause", node.findFieldByName("join").getMessageType().getName());
    assertEquals("BoostClause", node.findFieldByName("boost").getMessageType().getName());
    assertEquals("CelFilterClause",
        node.findFieldByName("cel_filter").getMessageType().getName());
    assertEquals("CelCalculatorClause",
        node.findFieldByName("cel_calculator").getMessageType().getName());
  }

  @Test
  void semanticTermAndPhraseClausesCarryAnalyzableInput() {
    final Descriptor semantic = OpenNlpQueryProto.getDescriptor()
        .findMessageTypeByName("SemanticClause");
    assertEquals("OpenNlpDocument",
        semantic.findFieldByName("document").getMessageType().getName());

    final Descriptor term = OpenNlpQueryProto.getDescriptor()
        .findMessageTypeByName("TermClause");
    assertEquals(FieldDescriptor.JavaType.STRING,
        term.findFieldByName("text").getJavaType());
    assertEquals("TermMatchMode", term.findFieldByName("mode").getEnumType().getName());
    assertNotNull(term.findFieldByName("mode").getEnumType()
        .findValueByName("TERM_MATCH_MODE_ANY"));
    assertNotNull(term.findFieldByName("mode").getEnumType()
        .findValueByName("TERM_MATCH_MODE_ALL"));

    final Descriptor phrase = OpenNlpQueryProto.getDescriptor()
        .findMessageTypeByName("PhraseClause");
    assertEquals(FieldDescriptor.JavaType.STRING,
        phrase.findFieldByName("text").getJavaType());
    assertEquals(FieldDescriptor.JavaType.INT,
        phrase.findFieldByName("slop").getJavaType());
  }

  @Test
  void joinComposesMembershipAndBoostShapesRelevancy() {
    final Descriptor join = OpenNlpQueryProto.getDescriptor()
        .findMessageTypeByName("JoinClause");
    assertEquals("JoinOperator", join.findFieldByName("operator").getEnumType().getName());
    assertNotNull(join.findFieldByName("operator").getEnumType()
        .findValueByName("JOIN_OPERATOR_AND"));
    assertNotNull(join.findFieldByName("operator").getEnumType()
        .findValueByName("JOIN_OPERATOR_OR"));
    assertTrue(join.findFieldByName("operands").isRepeated());
    assertEquals("QueryNode", join.findFieldByName("operands").getMessageType().getName());
    assertTrue(join.findFieldByName("exclusions").isRepeated());
    assertEquals("JoinFusion", join.findFieldByName("fusion").getEnumType().getName());
    assertNotNull(join.findFieldByName("fusion").getEnumType()
        .findValueByName("JOIN_FUSION_RECIPROCAL_RANK"));

    final Descriptor boost = OpenNlpQueryProto.getDescriptor()
        .findMessageTypeByName("BoostClause");
    assertEquals("QueryNode", boost.findFieldByName("operand").getMessageType().getName());
    assertEquals(1, boost.getOneofs().size());
    assertEquals("factor", boost.getOneofs().getFirst().getName());
    assertEquals(List.of("weight", "calculator"),
        boost.getOneofs().getFirst().getFields().stream()
            .map(FieldDescriptor::getName).toList());
    assertEquals(FieldDescriptor.JavaType.DOUBLE,
        boost.findFieldByName("weight").getJavaType());
    assertEquals("CelScore", boost.findFieldByName("calculator").getMessageType().getName());
  }

  @Test
  void celSplitsIntoABoolFilterAndANormalizedCalculator() {
    final Descriptor filter = OpenNlpQueryProto.getDescriptor()
        .findMessageTypeByName("CelFilterClause");
    assertEquals(FieldDescriptor.JavaType.STRING,
        filter.findFieldByName("expression").getJavaType());

    final Descriptor calculator = OpenNlpQueryProto.getDescriptor()
        .findMessageTypeByName("CelCalculatorClause");
    assertEquals("CelScore", calculator.findFieldByName("score").getMessageType().getName());

    final Descriptor score = OpenNlpQueryProto.getDescriptor()
        .findMessageTypeByName("CelScore");
    assertEquals(FieldDescriptor.JavaType.STRING,
        score.findFieldByName("expression").getJavaType());
    final var normalization = score.findFieldByName("normalization").getEnumType();
    assertEquals("ScoreNormalization", normalization.getName());
    assertNotNull(normalization.findValueByName("SCORE_NORMALIZATION_CLAMP"));
    assertNotNull(normalization.findValueByName("SCORE_NORMALIZATION_MINMAX"));
    assertNotNull(normalization.findValueByName("SCORE_NORMALIZATION_LOGISTIC"));
  }
}
