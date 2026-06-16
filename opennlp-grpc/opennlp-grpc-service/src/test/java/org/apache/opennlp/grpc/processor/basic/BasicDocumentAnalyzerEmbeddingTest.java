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
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicDocumentAnalyzerEmbeddingTest {

  private static final String TEXT = "One sentence. Two sentences!";

  private final ModelBundleCache modelBundleCache = new ModelBundleCache(Map.of());
  private final StubEmbeddingProvider embeddingProvider =
      new StubEmbeddingProvider(Map.of("minilm", 4));
  private final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(
      ProfileRegistry.createDefault(), modelBundleCache, embeddingProvider);

  @Test
  void generatesSentenceEmbeddingsWhenEmbedStepRequested() {
    final var response = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("with-embed")
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addSteps(PipelineStep.PIPELINE_STEP_EMBED)
            .build())
        .setOptions(AnalysisOptions.newBuilder()
            .setEmbeddingModelId("minilm")
            .build())
        .build());

    assertEquals(2, response.getDocument().getSentencesCount());
    assertEquals(2, response.getDocument().getEmbeddingsCount());
    assertEquals("minilm", response.getDocument().getEmbeddings(0).getModelId());
    assertEquals(4, response.getDocument().getEmbeddings(0).getVectorCount());
    assertEquals(
        EmbeddingGranularity.EMBEDDING_GRANULARITY_SENTENCE,
        response.getDocument().getEmbeddings(0).getGranularity());
    assertTrue(response.getDiagnosticsList().stream()
        .anyMatch(d -> d.getStep() == PipelineStep.PIPELINE_STEP_EMBED));

    // A document centroid: the element-wise mean of the sentence vectors, granularity DOCUMENT.
    assertEquals(1, response.getDocument().getDocumentCentroidsCount());
    final var centroid = response.getDocument().getDocumentCentroids(0);
    assertEquals("minilm", centroid.getModelId());
    assertEquals(EmbeddingGranularity.EMBEDDING_GRANULARITY_DOCUMENT, centroid.getGranularity());
    assertEquals(4, centroid.getVectorCount());
    for (int d = 0; d < centroid.getVectorCount(); d++) {
      final float expected = (response.getDocument().getEmbeddings(0).getVector(d)
          + response.getDocument().getEmbeddings(1).getVector(d)) / 2f;
      assertEquals(expected, centroid.getVector(d), 1e-5f);
    }
  }

  @Test
  void rejectsUnknownEmbeddingModel() {
    final AnalysisException error = assertThrows(AnalysisException.class, () -> analyzer.analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
            .setProfile(AnalysisProfile.newBuilder()
                .setProfileId("with-embed")
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
                .addSteps(PipelineStep.PIPELINE_STEP_EMBED)
                .build())
            .setOptions(AnalysisOptions.newBuilder().setEmbeddingModelId("missing").build())
            .build()));

    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }
}
