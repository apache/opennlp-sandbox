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

import java.util.List;

import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hermetic unit tests for the token-index-to-document-span conversion in {@link ClassicChunkerModel}
 * (a {@code ChunkerME} model cannot be trained in-memory, so the inference path itself is opt-in,
 * exercised by {@code BasicDocumentAnalyzerSyntacticChunkTest}).
 */
class ClassicChunkerModelTest {

  private static AnnotationSpan span(int start, int end) {
    return AnnotationSpan.newBuilder().setStart(start).setEnd(end)
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT).build();
  }

  // "The dog barked" tokenized.
  private static AnnotatedSentence theDogBarked() {
    return AnnotatedSentence.newBuilder()
        .addTokens(Token.newBuilder().setText("The").setAnnotationSpan(span(0, 3)).build())
        .addTokens(Token.newBuilder().setText("dog").setAnnotationSpan(span(4, 7)).build())
        .addTokens(Token.newBuilder().setText("barked").setAnnotationSpan(span(8, 14)).build())
        .build();
  }

  @Test
  void mapsTokenIndexChunkSpansToDocumentSpans() {
    // NP covers tokens [0,2) = "The dog"; VP covers token [2,3) = "barked".
    final List<ChunkSpan> chunks = ClassicChunkerModel.toChunkSpans(
        new Span[] {new Span(0, 2, "NP"), new Span(2, 3, "VP")}, theDogBarked());

    assertEquals(2, chunks.size());
    assertEquals("NP", chunks.get(0).getChunkTag());
    assertEquals(0, chunks.get(0).getAnnotationSpan().getStart());
    assertEquals(7, chunks.get(0).getAnnotationSpan().getEnd());
    assertEquals("VP", chunks.get(1).getChunkTag());
    assertEquals(8, chunks.get(1).getAnnotationSpan().getStart());
    assertEquals(14, chunks.get(1).getAnnotationSpan().getEnd());
  }

  @Test
  void singleTokenChunkSpansOneToken() {
    final List<ChunkSpan> chunks = ClassicChunkerModel.toChunkSpans(
        new Span[] {new Span(1, 2, "NP")}, theDogBarked());

    assertEquals(1, chunks.size());
    assertEquals(4, chunks.get(0).getAnnotationSpan().getStart());
    assertEquals(7, chunks.get(0).getAnnotationSpan().getEnd());
  }
}
