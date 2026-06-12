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

import java.util.Map;

import org.apache.opennlp.grpc.embedding.StubEmbeddingProvider;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicDocumentAnalyzerChunkEmbedTest {

  private static final String TEXT = "First sentence. Second sentence!";

  private final ModelBundleCache modelBundleCache = new ModelBundleCache(Map.of());
  private final StubEmbeddingProvider embeddingProvider =
      new StubEmbeddingProvider(Map.of("minilm", 3, "e5", 3));
  private final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(
      ProfileRegistry.createDefault(), modelBundleCache, embeddingProvider);

  @Test
  void chunkEmbedConfigsProduceGroupsWithEmbeddings() {
    final var response = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .addChunkEmbedConfigs(ChunkEmbedConfigEntry.newBuilder()
            .setConfigId("sentence-chunks")
            .setChunking(ChunkingSpec.newBuilder().setAlgorithm("sentence").build())
            .addEmbeddingModelIds("minilm")
            .addEmbeddingModelIds("e5")
            .build())
        .build());

    assertEquals(2, response.getDocument().getSentencesCount());
    assertEquals(1, response.getDocument().getChunkEmbeddingGroupsCount());
    final var group = response.getDocument().getChunkEmbeddingGroups(0);
    assertEquals("sentence-chunks", group.getGroupId());
    assertEquals(2, group.getChunksCount());
    assertEquals(2, group.getChunks(0).getEmbeddingsCount());
    assertEquals("minilm", group.getChunks(0).getEmbeddings(0).getModelId());
    assertEquals(
        EmbeddingGranularity.EMBEDDING_GRANULARITY_CHUNK_LEVEL,
        group.getChunks(0).getEmbeddings(0).getGranularity());
    assertTrue(group.getStats().getChunkCount() > 0);
  }

  @Test
  void profileChunkStepProducesSentenceGroupsWithoutEmbeddings() {
    final var response = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("chunk-only")
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_CHUNK)
            .build())
        .build());

    assertEquals(1, response.getDocument().getChunkEmbeddingGroupsCount());
    assertEquals(2, response.getDocument().getChunkEmbeddingGroups(0).getChunksCount());
    assertEquals(0, response.getDocument().getChunkEmbeddingGroups(0).getChunks(0).getEmbeddingsCount());
  }

  @Test
  void tokenChunkingAutoRunsTokenizationBackbone() {
    final var response = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText("one two three four five").build())
        .addChunkEmbedConfigs(ChunkEmbedConfigEntry.newBuilder()
            .setConfigId("token-chunks")
            .setChunking(ChunkingSpec.newBuilder()
                .setAlgorithm("token")
                .setChunkSize(2)
                .setChunkOverlap(0)
                .build())
            .addEmbeddingModelIds("minilm")
            .build())
        .build());

    assertTrue(response.getDocument().getSentences(0).getTokensCount() > 0);
    assertEquals(3, response.getDocument().getChunkEmbeddingGroups(0).getChunksCount());
  }
}
