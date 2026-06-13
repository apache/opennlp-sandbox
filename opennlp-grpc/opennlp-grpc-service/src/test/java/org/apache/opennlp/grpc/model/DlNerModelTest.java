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
package org.apache.opennlp.grpc.model;

import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the {@link DlNerModel} offset remap, which maps character offsets over the
 * space-joined token text (the coordinate space of {@code NameFinderDL} output) back to
 * document offsets. Exercised directly with synthetic spans so no ONNX model is needed.
 */
class DlNerModelTest {

  // Document text (offsets in parentheses): "Mr. George Washington was president."
  //                                          0123456789...
  // Tokens with their document char spans; the sentence starts at document offset 4.
  private static final String[] TOKENS = {"George", "Washington", "was", "president"};

  private static AnnotatedSentence sentence() {
    return AnnotatedSentence.newBuilder()
        .addTokens(token("George", 4, 10))
        .addTokens(token("Washington", 11, 21))
        .addTokens(token("was", 22, 25))
        .addTokens(token("president", 26, 35))
        .build();
  }

  private static Token token(String text, int start, int end) {
    return Token.newBuilder()
        .setText(text)
        .setAnnotationSpan(AnnotationSpan.newBuilder()
            .setStart(start).setEnd(end)
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT).build())
        .build();
  }

  @Test
  void multiTokenEntityMapsToDocumentOffsets() {
    // "George Washington" is chars [0, 17) of "George Washington was president".
    final AnnotationSpan span = DlNerModel.documentSpan(sentence(), TOKENS, 0, 17);
    assertEquals(4, span.getStart());    // start of "George"
    assertEquals(21, span.getEnd());     // end of "Washington"
    assertEquals(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT, span.getSpace());
  }

  @Test
  void firstTokenEntityMapsToDocumentOffsets() {
    // "George" is chars [0, 6).
    final AnnotationSpan span = DlNerModel.documentSpan(sentence(), TOKENS, 0, 6);
    assertEquals(4, span.getStart());
    assertEquals(10, span.getEnd());
  }

  @Test
  void lastTokenEntityMapsToDocumentOffsets() {
    // "president" is chars [22, 31) of the joined text.
    final AnnotationSpan span = DlNerModel.documentSpan(sentence(), TOKENS, 22, 31);
    assertEquals(26, span.getStart());
    assertEquals(35, span.getEnd());
  }

  @Test
  void spanStartingInterTokenSpaceSnapsToNextToken() {
    // Joined "George Washington was president": char 17 is the space before "was",
    // which is the token [18, 21). A span starting there snaps forward to "was".
    final AnnotationSpan span = DlNerModel.documentSpan(sentence(), TOKENS, 17, 21);
    assertEquals(22, span.getStart());   // document start of "was"
    assertEquals(25, span.getEnd());     // document end of "was"
  }

  @Test
  void outOfRangeSpanIsRejected() {
    assertThrows(IllegalStateException.class,
        () -> DlNerModel.documentSpan(sentence(), TOKENS, 100, 110));
  }
}
