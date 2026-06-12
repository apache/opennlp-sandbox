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
package org.apache.opennlp.grpc.processor;

import java.util.List;
import java.util.Map;
import org.apache.opennlp.grpc.embedding.StubEmbeddingProvider;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.SemanticChunkingConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BasicDocumentAnalyzerSemanticChunkTest {

  private static final List<Float> TOPIC_BUSINESS = List.of(1f, 0f, 0f);
  private static final List<Float> TOPIC_WEATHER = List.of(0f, 1f, 0f);

  /** Embeds any text mentioning rain as the weather topic and everything else as business. */
  private final StubEmbeddingProvider embeddingProvider = new StubEmbeddingProvider(
      Map.of("minilm", 3),
      (modelId, text) -> text.contains("rain")
          ? new float[] {0f, 1f, 0f} : new float[] {1f, 0f, 0f});

  private final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(
      ProfileRegistry.createDefault(),
      new ModelBundleCache(Map.of()),
      embeddingProvider);

  @Test
  void semanticChunkEmbedConfigSplitsAtTopicBoundary() {
    final var response = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder()
            .setRawText("The merger closed on Monday. The shareholders approved the deal. "
                + "Heavy rain flooded the valley.")
            .build())
        .addChunkEmbedConfigs(ChunkEmbedConfigEntry.newBuilder()
            .setConfigId("semantic-topics")
            .setChunking(ChunkingSpec.newBuilder()
                .setAlgorithm("semantic")
                .setSemanticConfig(SemanticChunkingConfig.newBuilder()
                    .setSimilarityThreshold(0.5f)
                    .setSemanticEmbeddingModelId("minilm")
                    .build())
                .build())
            .addEmbeddingModelIds("minilm")
            .build())
        .build());

    assertEquals(3, response.getDocument().getSentencesCount());
    assertEquals(1, response.getDocument().getChunkEmbeddingGroupsCount());

    final ChunkEmbeddingGroup group = response.getDocument().getChunkEmbeddingGroups(0);
    assertEquals(2, group.getChunksCount());
    assertEquals(List.of(0, 1), group.getChunks(0).getContainedSentenceIndicesList());
    assertEquals(List.of(2), group.getChunks(1).getContainedSentenceIndicesList());

    assertEquals(1, group.getChunks(0).getEmbeddingsCount());
    assertEquals(1, group.getChunks(1).getEmbeddingsCount());
    assertEquals("minilm", group.getChunks(0).getEmbeddings(0).getModelId());
    assertEquals(TOPIC_BUSINESS, group.getChunks(0).getEmbeddings(0).getVectorList());
    assertEquals(TOPIC_WEATHER, group.getChunks(1).getEmbeddings(0).getVectorList());
  }
}
