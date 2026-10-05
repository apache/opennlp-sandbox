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

import java.util.ArrayList;
import java.util.List;

import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.ProcessingDiagnostic;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stopword marking is annotation, not removal: every token survives, matching tokens
 * carry is_stopword, and nothing else changes.
 */
class StopwordMarkingTest {

  private static Token token(String text) {
    return Token.newBuilder().setText(text).build();
  }

  @Test
  void marksBundledStopwordsWithoutRemovingTokens() {
    OpenNlpDocument.Builder document = OpenNlpDocument.newBuilder()
        .addSentences(AnnotatedSentence.newBuilder()
            .addTokens(token("the"))
            .addTokens(token("quick"))
            .addTokens(token("fox")));
    List<ProcessingDiagnostic> diagnostics = new ArrayList<>();

    ClassicStepRunner.markStopwords(document, "en", diagnostics);

    AnnotatedSentence sentence = document.getSentences(0);
    assertTrue(sentence.getTokensCount() == 3, "annotation must not remove tokens");
    assertTrue(sentence.getTokens(0).getIsStopword(), "'the' is an English stopword");
    assertFalse(sentence.getTokens(1).getIsStopword());
    assertFalse(sentence.getTokens(2).getIsStopword());
    assertFalse(diagnostics.isEmpty());
  }
}
