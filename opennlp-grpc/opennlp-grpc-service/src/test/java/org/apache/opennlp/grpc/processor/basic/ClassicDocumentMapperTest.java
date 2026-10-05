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

import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassicDocumentMapperTest {

  private static final String TEXT = "Cats run. Dogs nap.";

  private final ClassicDocumentMapper mapper = new ClassicDocumentMapper();

  @Test
  void mapsNestedWireAnnotationsToAlignedDocumentLayers() {
    final OpenNlpDocument.Builder wire = OpenNlpDocument.newBuilder()
        .setRawText(TEXT)
        .addSentences(sentence(0, 9,
            token("Cats", "NOUN", 0, 4), token("run", "VERB", 5, 8)))
        .addSentences(sentence(10, 19,
            token("Dogs", "NOUN", 10, 14), token("nap", "VERB", 15, 18)));

    final Document document = mapper.withPosTags(TEXT, wire);

    assertEquals(2, document.get(Layers.SENTENCES).size());
    assertEquals("Dogs nap.", document.get(Layers.SENTENCES).get(1).value());
    assertEquals(4, document.get(Layers.TOKENS).size());
    assertEquals("Dogs", document.get(Layers.TOKENS).get(2).value());
    assertEquals(10, document.get(Layers.TOKENS).get(2).span().getStart());
    assertEquals(4, document.get(Layers.POS_TAGS).size());
    assertEquals("VERB", document.get(Layers.POS_TAGS).get(3).value());
  }

  private AnnotatedSentence sentence(int start, int end, Token... tokens) {
    final AnnotatedSentence.Builder sentence = AnnotatedSentence.newBuilder()
        .setSentenceSpan(span(start, end));
    for (Token token : tokens) {
      sentence.addTokens(token);
    }
    return sentence.build();
  }

  private Token token(String text, String posTag, int start, int end) {
    return Token.newBuilder()
        .setText(text)
        .setPosTag(posTag)
        .setAnnotationSpan(span(start, end))
        .build();
  }

  private AnnotationSpan span(int start, int end) {
    return AnnotationSpan.newBuilder().setStart(start).setEnd(end).build();
  }
}
