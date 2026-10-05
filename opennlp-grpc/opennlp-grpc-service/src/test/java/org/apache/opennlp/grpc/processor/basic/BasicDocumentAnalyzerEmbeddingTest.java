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

import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.embedding.CompositeEmbeddingProvider;
import org.apache.opennlp.grpc.embedding.MiscountingEmbeddingProvider;
import org.apache.opennlp.grpc.embedding.StubEmbeddingProvider;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.EmbeddingBackendSelector;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.EmbeddingSelector;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.VectorNormalization;
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
            .setIncludeDocumentCentroid(true)
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
    assertEquals(VectorNormalization.VECTOR_NORMALIZATION_NONE,
        centroid.getVectorNormalization());
    assertEquals(4, centroid.getVectorCount());
    for (int d = 0; d < centroid.getVectorCount(); d++) {
      final float expected = (response.getDocument().getEmbeddings(0).getVector(d)
          + response.getDocument().getEmbeddings(1).getVector(d)) / 2f;
      assertEquals(expected, centroid.getVector(d), 1e-5f);
    }
  }

  @Test
  void l2NormalizesDocumentCentroidAndRetainsDocumentLayerProvenance() {
    final var response = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT))
        .setProfile(embedProfile())
        .setOptions(AnalysisOptions.newBuilder()
            .setEmbeddingModelId("minilm")
            .setIncludeDocumentCentroid(true)
            .setDocumentCentroidNormalization(
                VectorNormalization.VECTOR_NORMALIZATION_L2))
        .build());

    final var centroid = response.getDocument().getDocumentCentroids(0);
    double squaredNorm = 0.0d;
    for (float value : centroid.getVectorList()) {
      squaredNorm += value * value;
    }
    assertEquals(1.0d, Math.sqrt(squaredNorm), 1.0e-5d);
    assertEquals(VectorNormalization.VECTOR_NORMALIZATION_L2,
        centroid.getVectorNormalization());
    final var layerCentroid = response.getDocument().getLayers().getLayersList().stream()
        .filter(layer -> "opennlp:embeddings".equals(layer.getId()))
        .findFirst().orElseThrow()
        .getEmbeddingValues().getAnnotationsList().stream()
        .filter(annotation -> annotation.getGranularity()
            == EmbeddingGranularity.EMBEDDING_GRANULARITY_DOCUMENT)
        .findFirst().orElseThrow();
    assertEquals(VectorNormalization.VECTOR_NORMALIZATION_L2,
        layerCentroid.getVectorNormalization());
  }

  @Test
  void centroidNormalizationRequiresTheCentroid() {
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT))
        .setProfile(embedProfile())
        .setOptions(AnalysisOptions.newBuilder()
            .setEmbeddingModelId("minilm")
            .setDocumentCentroidNormalization(
                VectorNormalization.VECTOR_NORMALIZATION_L2))
        .build();

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(request));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void omitsDocumentCentroidUnlessRequested() {
    final var response = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .setProfile(embedProfile())
        .setOptions(AnalysisOptions.newBuilder()
            .setEmbeddingModelId("minilm")
            .build())
        .build());

    assertEquals(2, response.getDocument().getEmbeddingsCount());
    assertEquals(0, response.getDocument().getDocumentCentroidsCount());
    assertEquals(2, response.getDocument().getLayers().getLayersList().stream()
        .filter(layer -> "opennlp:embeddings".equals(layer.getId()))
        .findFirst().orElseThrow()
        .getEmbeddingValues().getAnnotationsCount());
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

  @Test
  void shortEmbeddingBatchFailsWithExpectedAndActualCounts() {
    // The provider returns one vector for two sentences; the trailing embedding must not
    // vanish silently.
    final BasicDocumentAnalyzer miscounting = new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(), modelBundleCache,
        new MiscountingEmbeddingProvider("minilm", 4, -1));

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> miscounting.analyze(AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
            .setProfile(embedProfile())
            .setOptions(AnalysisOptions.newBuilder().setEmbeddingModelId("minilm").build())
            .build()));

    assertEquals(AnalysisException.FailureType.INTERNAL, error.getFailureType());
    assertTrue(error.getMessage().contains("2"), "message names expected count: "
        + error.getMessage());
    assertTrue(error.getMessage().contains("1"), "message names actual count: "
        + error.getMessage());
  }

  @Test
  void longEmbeddingBatchFailsWithExpectedAndActualCounts() {
    final BasicDocumentAnalyzer miscounting = new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(), modelBundleCache,
        new MiscountingEmbeddingProvider("minilm", 4, 1));

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> miscounting.analyze(AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
            .setProfile(embedProfile())
            .setOptions(AnalysisOptions.newBuilder().setEmbeddingModelId("minilm").build())
            .build()));

    assertEquals(AnalysisException.FailureType.INTERNAL, error.getFailureType());
    assertTrue(error.getMessage().contains("2"), "message names expected count: "
        + error.getMessage());
    assertTrue(error.getMessage().contains("3"), "message names actual count: "
        + error.getMessage());
  }

  @Test
  void pinsBackendAndReportsActualRoute() {
    final CompositeEmbeddingProvider composite = twoRouteProvider();
    final BasicDocumentAnalyzer routedAnalyzer = new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(), modelBundleCache, composite);

    final var response = routedAnalyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .setProfile(embedProfile())
        .setOptions(AnalysisOptions.newBuilder()
            .setEmbeddingSelector(EmbeddingSelector.newBuilder()
                .setModelId("minilm")
                .setBackend(EmbeddingBackendSelector.newBuilder().setCustom("slow")))
            .setIncludeDocumentCentroid(true))
        .build());

    assertEquals(2.0f, response.getDocument().getEmbeddings(0).getVector(0));
    assertEquals("slow", response.getDocument().getEmbeddings(0).getRoute().getBackendId());
    assertEquals("minilm-v1", response.getDocument().getEmbeddings(0).getRoute().getVectorSpaceId());
    assertEquals("slow", response.getDocument().getDocumentCentroids(0).getRoute().getBackendId());
    assertEquals("slow", response.getDocument().getLayers().getLayersList().stream()
        .filter(layer -> "opennlp:embeddings".equals(layer.getId()))
        .findFirst().orElseThrow()
        .getEmbeddingValues().getAnnotations(0).getRoute().getBackendId());
  }

  @Test
  void rejectsUnknownPinnedBackend() {
    final BasicDocumentAnalyzer routedAnalyzer = new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(), modelBundleCache, twoRouteProvider());
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .setProfile(embedProfile())
        .setOptions(AnalysisOptions.newBuilder()
            .setEmbeddingSelector(EmbeddingSelector.newBuilder()
                .setModelId("minilm")
                .setBackendId("missing")))
        .build();

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> routedAnalyzer.analyze(request));

    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }

  @Test
  void rejectsLegacyModelIdTogetherWithSelector() {
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .setProfile(embedProfile())
        .setOptions(AnalysisOptions.newBuilder()
            .setEmbeddingModelId("minilm")
            .setEmbeddingSelector(EmbeddingSelector.newBuilder().setModelId("minilm")))
        .build();

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(request));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  private static AnalysisProfile embedProfile() {
    return AnalysisProfile.newBuilder()
        .setProfileId("with-embed")
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSteps(PipelineStep.PIPELINE_STEP_EMBED)
        .build();
  }

  private static CompositeEmbeddingProvider twoRouteProvider() {
    final StubEmbeddingProvider fast = new StubEmbeddingProvider(
        "fast", Map.of("minilm", 4), (model, text) -> new float[] {1f, 1f, 1f, 1f});
    final StubEmbeddingProvider slow = new StubEmbeddingProvider(
        "slow", Map.of("minilm", 4), (model, text) -> new float[] {2f, 2f, 2f, 2f});
    return new CompositeEmbeddingProvider(List.of(fast, slow), Map.of(
        "model.embedder.minilm.fast.priority", "100",
        "model.embedder.minilm.slow.priority", "50",
        "model.embedder.minilm.fast.vector_space_id", "minilm-v1",
        "model.embedder.minilm.slow.vector_space_id", "minilm-v1"));
  }
}
