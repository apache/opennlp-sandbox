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

import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests ranked language detection: a positive {@code ranked_language_count} reports that
 * many predictions, best first, on the document and in the {@code opennlp:language} layer,
 * while the default keeps the single best prediction.
 */
class RankedLanguageDetectionTest {

  private static final String TEXT =
      "The quick brown fox jumps over the lazy dog near the river bank.";

  private static BasicDocumentAnalyzer analyzer;

  @BeforeAll
  static void createAnalyzer() {
    analyzer = new BasicDocumentAnalyzer(Map.of());
  }

  @AfterAll
  static void closeAnalyzer() {
    analyzer.close();
  }

  private static OpenNlpDocument analyze(AnalysisOptions options) {
    return analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setDocId("langs").setRawText(TEXT))
        .setProfile(AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT))
        .setOptions(options)
        .build()).getDocument();
  }

  private static AnnotationLayer languageLayer(OpenNlpDocument document) {
    final List<AnnotationLayer> layers = document.getLayers().getLayersList().stream()
        .filter(layer -> "opennlp:language".equals(layer.getId()))
        .toList();
    assertEquals(1, layers.size());
    return layers.get(0);
  }

  @Test
  void reportsTheRequestedRankedPredictionsBestFirst() {
    final OpenNlpDocument document = analyze(
        AnalysisOptions.newBuilder().setRankedLanguageCount(3).build());

    assertEquals(3, document.getRankedLanguagesCount());
    assertEquals(document.getDetectedLanguage(), document.getRankedLanguages(0).getLanguage());
    assertEquals(document.getLanguageConfidence(),
        document.getRankedLanguages(0).getConfidence());
    for (int i = 1; i < document.getRankedLanguagesCount(); i++) {
      assertTrue(document.getRankedLanguages(i - 1).getConfidence()
          >= document.getRankedLanguages(i).getConfidence());
    }

    final AnnotationLayer layer = languageLayer(document);
    assertEquals(3, layer.getCategoryValues().getAnnotationsCount());
    assertEquals(document.getDetectedLanguage(),
        layer.getCategoryValues().getAnnotations(0).getLabel());
  }

  @Test
  void defaultKeepsTheSingleBestPrediction() {
    final OpenNlpDocument document = analyze(AnalysisOptions.getDefaultInstance());

    assertEquals(0, document.getRankedLanguagesCount());
    assertEquals(1, languageLayer(document).getCategoryValues().getAnnotationsCount());
  }

  @Test
  void countAboveThePredictorRangeReturnsEveryPrediction() {
    final OpenNlpDocument document = analyze(
        AnalysisOptions.newBuilder().setRankedLanguageCount(100_000).build());

    assertTrue(document.getRankedLanguagesCount() > 3);
    assertEquals(document.getRankedLanguagesCount(),
        languageLayer(document).getCategoryValues().getAnnotationsCount());
  }
}
