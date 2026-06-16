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
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkResult;
import org.apache.opennlp.grpc.v1.ChunkSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.ParseNode;
import org.apache.opennlp.grpc.v1.ParseNodeKind;
import org.apache.opennlp.grpc.v1.ParseTree;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link DocumentOffsetEncoder}: every span the document carries — sentence, token, named
 * entity, and parse-tree node — must be converted from internal UTF-16 indices into the requested
 * {@link OffsetEncoding}, consistently. Uses non-ASCII text so UTF-16, UTF-8 byte, and codepoint
 * coordinates genuinely differ, which is where unencoded or mis-encoded spans surface.
 */
class DocumentOffsetEncoderTest {

  // "café John": é (U+00E9) is 1 UTF-16 char / 1 codepoint / 2 UTF-8 bytes, so "John" sits at
  // UTF-16 [5,9] but UTF-8 bytes [6,10].
  private static final String TEXT = "café John";

  private static AnnotationSpan span(int start, int end) {
    return AnnotationSpan.newBuilder()
        .setStart(start).setEnd(end)
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }

  private static OpenNlpDocument.Builder documentWithJohnEntity() {
    final ParseNode terminal = ParseNode.newBuilder()
        .setKind(ParseNodeKind.PARSE_NODE_KIND_TERMINAL)
        .setLabel("NNP").setSpan(span(5, 9)).setTokenIndex(1)
        .build();
    final ParseNode root = ParseNode.newBuilder()
        .setKind(ParseNodeKind.PARSE_NODE_KIND_NONTERMINAL)
        .setLabel("NP").setSpan(span(5, 9)).addChildren(terminal)
        .build();
    final AnnotatedSentence sentence = AnnotatedSentence.newBuilder()
        .setSentenceSpan(span(0, 9))
        .addTokens(Token.newBuilder().setText("café").setAnnotationSpan(span(0, 4)).build())
        .addTokens(Token.newBuilder().setText("John").setAnnotationSpan(span(5, 9)).build())
        .addEntities(NamedEntity.newBuilder()
            .setEntityType("person").setAnnotationSpan(span(5, 9)).build())
        .setParseTree(ParseTree.newBuilder().setRoot(root).build())
        .setSyntacticChunks(ChunkResult.newBuilder()
            .addChunks(ChunkSpan.newBuilder().setChunkTag("NP").setAnnotationSpan(span(5, 9)))
            .build())
        .build();
    return OpenNlpDocument.newBuilder().setRawText(TEXT).addSentences(sentence);
  }

  @Test
  void encodesNamedEntitySpansToUtf8Bytes() {
    // Regression guard: the entity span must be converted like every other span, not left in
    // UTF-16 char offsets while tokens/sentences are converted to bytes.
    final OpenNlpDocument.Builder document = documentWithJohnEntity();
    DocumentOffsetEncoder.apply(document, TEXT, OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE);

    final AnnotationSpan entity = document.getSentences(0).getEntities(0).getAnnotationSpan();
    assertEquals(6, entity.getStart());
    assertEquals(10, entity.getEnd());
  }

  @Test
  void encodesEverySpanKindConsistently() {
    final OpenNlpDocument.Builder document = documentWithJohnEntity();
    DocumentOffsetEncoder.apply(document, TEXT, OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE);

    final AnnotatedSentence sentence = document.getSentences(0);
    // Sentence "café John" is 10 UTF-8 bytes.
    assertEquals(0, sentence.getSentenceSpan().getStart());
    assertEquals(10, sentence.getSentenceSpan().getEnd());
    // "John" token, entity, and the parse nodes covering it all land on bytes [6,10].
    assertEquals(6, sentence.getTokens(1).getAnnotationSpan().getStart());
    assertEquals(10, sentence.getTokens(1).getAnnotationSpan().getEnd());
    assertEquals(6, sentence.getEntities(0).getAnnotationSpan().getStart());
    final ParseNode root = sentence.getParseTree().getRoot();
    assertEquals(6, root.getSpan().getStart());
    assertEquals(10, root.getSpan().getEnd());
    assertEquals(6, root.getChildren(0).getSpan().getStart());
    assertEquals(10, root.getChildren(0).getSpan().getEnd());
    final AnnotationSpan chunkSpan = sentence.getSyntacticChunks().getChunks(0).getAnnotationSpan();
    assertEquals(6, chunkSpan.getStart());
    assertEquals(10, chunkSpan.getEnd());
    assertEquals(OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE, document.getOffsetEncoding());
  }

  @Test
  void utf16EncodingLeavesCharOffsetsUnchanged() {
    final OpenNlpDocument.Builder document = documentWithJohnEntity();
    DocumentOffsetEncoder.apply(document, TEXT, OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT);

    final AnnotationSpan entity = document.getSentences(0).getEntities(0).getAnnotationSpan();
    assertEquals(5, entity.getStart());
    assertEquals(9, entity.getEnd());
  }

  @Test
  void codePointEncodingCollapsesSurrogatePairs() {
    // "😀 Jo": an emoji (U+1F600, a surrogate pair = 2 UTF-16 chars / 1 codepoint),
    // a space, then "Jo". "Jo" is UTF-16 [3,5] but codepoints [2,4].
    final String text = "😀 Jo";
    final AnnotatedSentence sentence = AnnotatedSentence.newBuilder()
        .setSentenceSpan(span(0, 5))
        .addEntities(NamedEntity.newBuilder()
            .setEntityType("person").setAnnotationSpan(span(3, 5)).build())
        .build();
    final OpenNlpDocument.Builder document =
        OpenNlpDocument.newBuilder().setRawText(text).addSentences(sentence);

    DocumentOffsetEncoder.apply(document, text, OffsetEncoding.OFFSET_ENCODING_UNICODE_CODE_POINT);

    final AnnotationSpan entity = document.getSentences(0).getEntities(0).getAnnotationSpan();
    assertEquals(2, entity.getStart());
    assertEquals(4, entity.getEnd());
  }
}
