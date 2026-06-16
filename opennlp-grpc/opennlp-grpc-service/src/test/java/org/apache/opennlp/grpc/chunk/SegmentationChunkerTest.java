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
package org.apache.opennlp.grpc.chunk;

import java.util.Map;

import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.embedding.StubEmbeddingProvider;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SegmentationChunkerTest {

  private static final EmbeddingProvider NO_MODELS = new StubEmbeddingProvider(Map.of());

  @Test
  void sentenceAlgorithmCreatesOneChunkPerSentence() {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setRawText("One. Two!")
        .addSentences(sentence(0, 4))
        .addSentences(sentence(5, 9))
        .build();

    final var chunks = SegmentationChunker.segment(document.getRawText(), document,
        ChunkingSpec.newBuilder().setAlgorithm("sentence").build(), NO_MODELS);

    assertEquals(2, chunks.size());
    assertEquals(0, chunks.get(0).start());
    assertEquals(4, chunks.get(0).end());
    assertEquals(1, chunks.get(1).sentenceIndices().size());
  }

  @Test
  void tokenAlgorithmCreatesOverlappingWindows() {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setRawText("a b c d e")
        .addSentences(AnnotatedSentence.newBuilder()
            .setSentenceSpan(span(0, 9))
            .addTokens(token("a", 0, 1))
            .addTokens(token("b", 2, 3))
            .addTokens(token("c", 4, 5))
            .addTokens(token("d", 6, 7))
            .addTokens(token("e", 8, 9))
            .build())
        .build();

    final var chunks = SegmentationChunker.segment(document.getRawText(), document,
        ChunkingSpec.newBuilder()
            .setAlgorithm("token")
            .setChunkSize(3)
            .setChunkOverlap(1)
            .build(),
        NO_MODELS);

    assertEquals(2, chunks.size());
    assertEquals(0, chunks.get(0).start());
    assertEquals(5, chunks.get(0).end());
    assertEquals(4, chunks.get(1).start());
    assertEquals(9, chunks.get(1).end());
  }

  private static AnnotatedSentence sentence(int start, int end) {
    return AnnotatedSentence.newBuilder()
        .setSentenceSpan(span(start, end))
        .build();
  }

  private static Token token(String text, int start, int end) {
    return Token.newBuilder()
        .setText(text)
        .setAnnotationSpan(span(start, end))
        .build();
  }

  private static AnnotationSpan span(int start, int end) {
    return AnnotationSpan.newBuilder()
        .setStart(start)
        .setEnd(end)
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }
}
