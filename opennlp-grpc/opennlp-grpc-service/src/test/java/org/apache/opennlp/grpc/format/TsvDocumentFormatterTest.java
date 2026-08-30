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
package org.apache.opennlp.grpc.format;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Golden tests for the built-in token TSV formatter. */
class TsvDocumentFormatterTest {

  /** Builds one token with a document span and optional annotations. */
  static Token token(String text, int start, int end, String pos, String lemma) {
    final Token.Builder token = Token.newBuilder()
        .setText(text)
        .setAnnotationSpan(AnnotationSpan.newBuilder()
            .setStart(start).setEnd(end)
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT));
    if (pos != null) {
      token.setPosTag(pos);
    }
    if (lemma != null) {
      token.setLemma(lemma);
    }
    return token.build();
  }

  /** Renders one document through the formatter under test. */
  static String render(org.apache.opennlp.grpc.spi.format.OutputFormatter<OpenNlpDocument>
      formatter, OpenNlpDocument document) throws IOException {
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    formatter.format(document, output);
    return output.toString(StandardCharsets.UTF_8);
  }

  @Test
  void rendersOneRowPerTokenWithEmptyCellsForAbsentAnnotations() throws IOException {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setDocId("doc-1")
        .setRawText("Alpha beta. Gamma.")
        .addSentences(AnnotatedSentence.newBuilder()
            .addTokens(token("Alpha", 0, 5, "NNP", "alpha"))
            .addTokens(token("beta", 6, 10, null, null)))
        .addSentences(AnnotatedSentence.newBuilder()
            .addTokens(token("Gamma", 12, 17, "NNP", null)))
        .build();

    assertEquals("""
        sentence\ttoken\tstart\tend\ttext\tpos\tlemma
        0\t0\t0\t5\tAlpha\tNNP\talpha
        0\t1\t6\t10\tbeta\t\t
        1\t0\t12\t17\tGamma\tNNP\t
        """, render(new TsvDocumentFormatter(), document));
  }

  @Test
  void replacesCellAndRowSeparatorsInsideTokenText() throws IOException {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .addSentences(AnnotatedSentence.newBuilder()
            .addTokens(token("odd\ttoken\nvalue", 0, 15, null, null)))
        .build();

    assertEquals("""
        sentence\ttoken\tstart\tend\ttext\tpos\tlemma
        0\t0\t0\t15\todd token value\t\t
        """, render(new TsvDocumentFormatter(), document));
  }

  @Test
  void rejectsANullDocument() {
    assertThrows(IllegalArgumentException.class,
        () -> new TsvDocumentFormatter().format(null, new ByteArrayOutputStream()));
  }
}
