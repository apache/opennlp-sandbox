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

import org.apache.opennlp.grpc.embedding.StubEmbeddingProvider;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.SemanticChunkingConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkEmbedProcessorSemanticTest {

  private static final float[] TOPIC_A = {1f, 0f, 0f};
  private static final float[] TOPIC_B = {0f, 1f, 0f};

  private final StubEmbeddingProvider provider = new StubEmbeddingProvider(
      Map.of("minilm", 3),
      (modelId, text) -> text.startsWith("A") ? TOPIC_A : TOPIC_B);

  @Test
  void buildsSemanticGroupWithEmbeddings() {
    final String rawText = "Aa.Ab.Bc.";
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setRawText(rawText)
        .addSentences(sentence(0, 3))
        .addSentences(sentence(3, 6))
        .addSentences(sentence(6, 9))
        .build();
    final ChunkEmbedConfigEntry entry = ChunkEmbedConfigEntry.newBuilder()
        .setConfigId("semantic-topics")
        .setChunking(ChunkingSpec.newBuilder()
            .setAlgorithm("semantic")
            .setSemanticConfig(SemanticChunkingConfig.newBuilder()
                .setSimilarityThreshold(0.9f)
                .setSemanticEmbeddingModelId("minilm")
                .build())
            .build())
        .addEmbeddingModelIds("minilm")
        .build();

    final var group = ChunkEmbedProcessor.buildGroup(rawText, document, entry, provider);

    assertEquals(2, group.getChunksCount());
    assertEquals(1, group.getChunks(0).getEmbeddingsCount());
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
