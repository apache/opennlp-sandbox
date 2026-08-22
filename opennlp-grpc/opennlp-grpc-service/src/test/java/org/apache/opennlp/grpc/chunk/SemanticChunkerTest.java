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

import java.util.List;
import java.util.Map;
import org.apache.opennlp.grpc.embedding.StubEmbeddingProvider;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.SemanticChunkingConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SemanticChunkerTest {

  private static final float[] TOPIC_A = {1f, 0f, 0f};
  private static final float[] TOPIC_B = {0f, 1f, 0f};

  private final StubEmbeddingProvider provider = new StubEmbeddingProvider(
      Map.of("minilm", 3),
      (modelId, text) -> text.startsWith("A") ? TOPIC_A : TOPIC_B);

  @Test
  void splitsWhenAdjacentSentenceSimilarityIsLow() {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setRawText("Aa.Ab.Bc.")
        .addSentences(sentence(0, 3))
        .addSentences(sentence(3, 6))
        .addSentences(sentence(6, 9))
        .build();

    final var chunks = SemanticChunker.chunk(
        document.getRawText(),
        document,
        SemanticChunkingConfig.newBuilder().setSimilarityThreshold(0.9f).build(),
        provider,
        "minilm");

    assertEquals(2, chunks.size());
    assertEquals(List.of(0, 1), chunks.get(0).sentenceIndices());
    assertEquals(List.of(2), chunks.get(1).sentenceIndices());
  }

  @Test
  void mergesUndersizedTrailingChunkIntoPrecedingChunk() {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setRawText("Aa.Ab.Bc.")
        .addSentences(sentence(0, 3))
        .addSentences(sentence(3, 6))
        .addSentences(sentence(6, 9))
        .build();

    final var chunks = SemanticChunker.chunk(
        document.getRawText(),
        document,
        SemanticChunkingConfig.newBuilder()
            .setSimilarityThreshold(0.9f)
            .setMinChunkSentences(2)
            .build(),
        provider,
        "minilm");

    assertEquals(1, chunks.size());
    assertEquals(List.of(0, 1, 2), chunks.get(0).sentenceIndices());
  }

  @Test
  void mergesUndersizedLeadingChunkWithFollowingChunk() {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setRawText("Ba.Ab.Ac.")
        .addSentences(sentence(0, 3))
        .addSentences(sentence(3, 6))
        .addSentences(sentence(6, 9))
        .build();

    final var chunks = SemanticChunker.chunk(
        document.getRawText(),
        document,
        SemanticChunkingConfig.newBuilder()
            .setSimilarityThreshold(0.9f)
            .setMinChunkSentences(2)
            .build(),
        provider,
        "minilm");

    assertEquals(1, chunks.size());
    assertEquals(List.of(0, 1, 2), chunks.get(0).sentenceIndices());
  }

  @Test
  void splitsChunksLargerThanMaxChunkSentences() {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setRawText("Aa.Ab.Ac.Ad.")
        .addSentences(sentence(0, 3))
        .addSentences(sentence(3, 6))
        .addSentences(sentence(6, 9))
        .addSentences(sentence(9, 12))
        .build();

    final var chunks = SemanticChunker.chunk(
        document.getRawText(),
        document,
        SemanticChunkingConfig.newBuilder()
            .setSimilarityThreshold(0.9f)
            .setMaxChunkSentences(2)
            .build(),
        provider,
        "minilm");

    assertEquals(2, chunks.size());
    assertEquals(List.of(0, 1), chunks.get(0).sentenceIndices());
    assertEquals(List.of(2, 3), chunks.get(1).sentenceIndices());
  }

  @Test
  void cosineSimilarityIsOneForIdenticalVectors() {
    assertEquals(1f, SemanticChunker.cosineSimilarity(TOPIC_A, TOPIC_A), 0.0001f);
  }

  private static AnnotatedSentence sentence(int start, int end) {
    return AnnotatedSentence.newBuilder()
        .setSentenceSpan(AnnotationSpan.newBuilder()
            .setStart(start)
            .setEnd(end)
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
            .build())
        .build();
  }
}
