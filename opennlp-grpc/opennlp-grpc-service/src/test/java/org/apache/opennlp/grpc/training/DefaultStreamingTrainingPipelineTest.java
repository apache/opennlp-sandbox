/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;
import org.apache.opennlp.grpc.v1.CategoryChunkConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.StreamingTrainingIndexDurability;
import org.apache.opennlp.grpc.v1.StreamingTrainingIndexPlan;
import org.apache.opennlp.grpc.v1.StreamingTrainingStart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultStreamingTrainingPipelineTest {

  @Test
  void finalIndexAnalysisPreservesBaseOptionsAndInjectsOnlyThePublishedModel() {
    final StreamingTrainingStart start = StreamingTrainingStart.newBuilder()
        .setAnalysis(AnalyzeStreamConfiguration.newBuilder()
            .setProfileId("complete")
            .setOptions(AnalysisOptions.newBuilder().setIncludeProbabilities(true))
            .addChunkEmbedConfigs(ChunkEmbedConfigEntry.newBuilder()
                .setConfigId("preview")
                .addEmbeddingModelIds("preview-model")))
        .setIndex(StreamingTrainingIndexPlan.newBuilder()
            .setDisplayName("Session index")
            .setDurability(StreamingTrainingIndexDurability
                .STREAMING_TRAINING_INDEX_DURABILITY_PROCESS_LOCAL)
            .addChunkEmbedConfigs(ChunkEmbedConfigEntry.newBuilder()
                .setConfigId("sentences"))
            .addCategoryChunkConfigs(CategoryChunkConfigEntry.newBuilder()
                .setConfigId("sentiment")))
        .build();

    final AnalyzeStreamConfiguration result =
        DefaultStreamingTrainingPipeline.indexAnalysis(start, "static-model-published");

    assertEquals("complete", result.getProfileId());
    assertEquals(start.getAnalysis().getOptions(), result.getOptions());
    assertEquals(1, result.getChunkEmbedConfigsCount());
    assertEquals("sentences", result.getChunkEmbedConfigs(0).getConfigId());
    assertEquals("static-model-published", result.getChunkEmbedConfigs(0)
        .getEmbeddingSelectors(0).getModelId());
    assertEquals(0, result.getChunkEmbedConfigs(0).getEmbeddingModelIdsCount());
    assertEquals(1, result.getCategoryChunkConfigsCount());
    assertEquals("static-model-published", result.getCategoryChunkConfigs(0)
        .getEmbeddingSelectors(0).getModelId());
  }
}
