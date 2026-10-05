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
package org.apache.opennlp.grpc.processor.basic;

import java.util.List;

import opennlp.tools.depparse.DependencyGraph;
import opennlp.tools.depparse.DependencyParser;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.RelationPatternSpec;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinguisticGraphRendererTest {

  @Test
  void rendersDependencyArcsAndEntityIndexedRelations() {
    final OpenNlpDocument document = document();
    final DependencyParser parser = (tokens, tags) -> DependencyGraph.of(
        new int[] {1, -1, 1, 1}, new String[] {"nsubj", "root", "obj", "obl"});

    final var dependency = LinguisticGraphRenderer.parse(document, parser, "test-parser");
    assertEquals("opennlp:dependencies", dependency.layer().getId());
    assertEquals("test-parser", dependency.layer().getDependencyValues().getParserId());
    assertEquals(4, dependency.layer().getDependencyValues().getAnnotationsCount());
    assertEquals(1, dependency.layer().getDependencyValues().getAnnotations(0)
        .getHeadTokenIndex());
    assertEquals(-1, dependency.layer().getDependencyValues().getAnnotations(1)
        .getHeadTokenIndex());

    final var relation = LinguisticGraphRenderer.relations(dependency.document(), List.of(
        RelationPatternSpec.newBuilder()
            .setType("acquisition")
            .setPath("<nsubj >obj")
            .setTrigger("acquired")
            .build()));
    assertEquals("opennlp:relations", relation.getId());
    assertEquals(1, relation.getRelationValues().getAnnotationsCount());
    assertEquals(0, relation.getRelationValues().getAnnotations(0).getSubjectEntityIndex());
    assertEquals(1, relation.getRelationValues().getAnnotations(0).getObjectEntityIndex());
  }

  private static OpenNlpDocument document() {
    final String text = "Acme acquired Bolt in 2024.";
    final AnnotatedSentence.Builder sentence = AnnotatedSentence.newBuilder()
        .setSentenceSpan(span(0, text.length()));
    addToken(sentence, "Acme", 0, 4, "NNP");
    addToken(sentence, "acquired", 5, 13, "VBD");
    addToken(sentence, "Bolt", 14, 18, "NNP");
    addToken(sentence, "2024", 22, 26, "CD");
    sentence.addEntities(NamedEntity.newBuilder()
        .setAnnotationSpan(span(0, 4)).setEntityType("organization").setText("Acme"));
    sentence.addEntities(NamedEntity.newBuilder()
        .setAnnotationSpan(span(14, 18)).setEntityType("organization").setText("Bolt"));
    return OpenNlpDocument.newBuilder().setRawText(text).addSentences(sentence).build();
  }

  private static void addToken(
      AnnotatedSentence.Builder sentence, String text, int start, int end, String tag) {
    sentence.addTokens(Token.newBuilder().setText(text).setPosTag(tag)
        .setAnnotationSpan(span(start, end)));
  }

  private static AnnotationSpan span(int start, int end) {
    return AnnotationSpan.newBuilder().setStart(start).setEnd(end).build();
  }
}
