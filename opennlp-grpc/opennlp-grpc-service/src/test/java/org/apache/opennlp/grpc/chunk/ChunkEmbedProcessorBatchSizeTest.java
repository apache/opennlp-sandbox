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

import org.apache.opennlp.grpc.embedding.MiscountingEmbeddingProvider;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.StandardChunkingStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link ChunkEmbedProcessor#buildGroup} reconciles the provider's batch size with
 * the chunk count instead of indexing blindly (short batch) or dropping vectors (long batch).
 */
class ChunkEmbedProcessorBatchSizeTest {

  private static final String TEXT = "One. Two.";

  private static OpenNlpDocument document() {
    return OpenNlpDocument.newBuilder()
        .setRawText(TEXT)
        .addSentences(sentence(0, 4))
        .addSentences(sentence(5, 9))
        .build();
  }

  private static AnnotatedSentence sentence(int start, int end) {
    return AnnotatedSentence.newBuilder()
        .setSentenceSpan(AnnotationSpan.newBuilder().setStart(start).setEnd(end)
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT))
        .build();
  }

  private static ChunkEmbedConfigEntry entry() {
    return ChunkEmbedConfigEntry.newBuilder()
        .setConfigId("sentences")
        .setChunking(ChunkingSpec.newBuilder()
            .setStrategy(ChunkingStrategies.standard(
                StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_SENTENCE)))
        .addEmbeddingModelIds("minilm")
        .build();
  }

  @Test
  void shortBatchFailsWithExpectedAndActualCounts() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> ChunkEmbedProcessor.buildGroup(TEXT, document(), entry(),
            new MiscountingEmbeddingProvider("minilm", 3, -1)));

    assertEquals(AnalysisException.FailureType.INTERNAL, error.getFailureType());
    assertTrue(error.getMessage().contains("2"), "message names expected count: "
        + error.getMessage());
    assertTrue(error.getMessage().contains("1"), "message names actual count: "
        + error.getMessage());
  }

  @Test
  void longBatchFailsWithExpectedAndActualCounts() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> ChunkEmbedProcessor.buildGroup(TEXT, document(), entry(),
            new MiscountingEmbeddingProvider("minilm", 3, 1)));

    assertEquals(AnalysisException.FailureType.INTERNAL, error.getFailureType());
    assertTrue(error.getMessage().contains("2"), "message names expected count: "
        + error.getMessage());
    assertTrue(error.getMessage().contains("3"), "message names actual count: "
        + error.getMessage());
  }
}
