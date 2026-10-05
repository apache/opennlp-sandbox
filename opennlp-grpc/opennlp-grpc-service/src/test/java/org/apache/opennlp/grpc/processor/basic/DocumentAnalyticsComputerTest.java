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

import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.DocumentAnalytics;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the POS-based document statistics in {@link DocumentAnalyticsComputer}. */
class DocumentAnalyticsComputerTest {

  private static DocumentAnalytics analyze(Token... tokens) {
    return DocumentAnalyticsComputer.compute(OpenNlpDocument.newBuilder()
        .setRawText("text")
        .addSentences(AnnotatedSentence.newBuilder().addAllTokens(java.util.List.of(tokens)))
        .build());
  }

  private static Token token(String posTag) {
    return Token.newBuilder().setPosTag(posTag).build();
  }

  @Test
  void lexicalDensityCountsOverAllTokensNotOnlyTaggedOnes() {
    // Two content words (noun + verb), one tagged function word, one untagged token.
    final DocumentAnalytics analytics = analyze(
        token("NN"), token("VBZ"), token("DT"), Token.newBuilder().build());

    assertEquals(4, analytics.getTotalTokens());
    // Content words among POS-tagged tokens.
    assertEquals(2.0f / 3, analytics.getContentWordRatio(), 1e-6);
    // Lexical density over the whole token stream must differ from the tagged-token ratio.
    assertEquals(2.0f / 4, analytics.getLexicalDensity(), 1e-6);
    assertTrue(analytics.getLexicalDensity() < analytics.getContentWordRatio(),
        "lexical_density must weigh untagged tokens, not duplicate content_word_ratio");
  }

  @Test
  void properNounTagsAreNouns() {
    final DocumentAnalytics analytics = analyze(token("NNP"), token("NNPS"), token("VB"));

    assertEquals(2.0f / 3, analytics.getNounDensity(), 1e-6);
    assertEquals(1.0f, analytics.getContentWordRatio(), 1e-6);
  }
}
