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
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.CategoryChunkConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    // One centroid per model, each the element-wise mean of that model's chunk vectors.
    assertEquals(2, group.getCentroidsCount());
    for (final org.apache.opennlp.grpc.v1.EmbeddingResult centroid : group.getCentroidsList()) {
      assertEquals(EmbeddingGranularity.EMBEDDING_GRANULARITY_GROUP_CENTROID,
          centroid.getGranularity());
      final int dimension = centroid.getVectorCount();
      for (int d = 0; d < dimension; d++) {
        double sum = 0;
        for (final var chunk : group.getChunksList()) {
          sum += chunkVector(chunk, centroid.getModelId()).getVector(d);
        }
        assertEquals((float) (sum / group.getChunksCount()), centroid.getVector(d), 1e-5f,
            "centroid component " + d + " for model " + centroid.getModelId());
      }
    }
  }

  private static org.apache.opennlp.grpc.v1.EmbeddingResult chunkVector(
      org.apache.opennlp.grpc.v1.Chunk chunk, String modelId) {
    return chunk.getEmbeddingsList().stream()
        .filter(e -> e.getModelId().equals(modelId))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void categoryChunkConfigsRequireSentimentInProfile() {
    // Category grouping keys on the per-sentence sentiment label, so without SENTIMENT in the
    // profile the request is rejected up front rather than producing empty groups.
    final AnalysisException error = assertThrows(AnalysisException.class, () -> analyzer.analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
            .setProfile(AnalysisProfile.newBuilder()
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .build())
            .addCategoryChunkConfigs(CategoryChunkConfigEntry.newBuilder()
                .setConfigId("by-sentiment")
                .addEmbeddingModelIds("minilm")
                .build())
            .build()));

    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, error.getFailureType());
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

  @Test
  void overlappingTokenChunksCountEachTokenOnceInGroupTotal() {
    // "one two three four five" = 5 tokens; size 2, overlap 1 (step 1) yields overlapping
    // chunks [0,1],[1,2],[2,3],[3,4] (the chunker stops once a window reaches the last token).
    // total_tokens must be the 5 distinct tokens, not the inflated per-chunk sum (8).
    final var response = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText("one two three four five").build())
        .addChunkEmbedConfigs(ChunkEmbedConfigEntry.newBuilder()
            .setConfigId("overlap-chunks")
            .setChunking(ChunkingSpec.newBuilder()
                .setAlgorithm("token")
                .setChunkSize(2)
                .setChunkOverlap(1)
                .build())
            .addEmbeddingModelIds("minilm")
            .build())
        .build());

    final var group = response.getDocument().getChunkEmbeddingGroups(0);
    assertEquals(4, group.getChunksCount());
    assertEquals(5, group.getStats().getTotalTokens());
  }
}
