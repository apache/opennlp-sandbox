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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.apache.opennlp.grpc.model.ClassicDocCategorizerBackendFactory;
import org.apache.opennlp.grpc.model.DocCategorizerRegistry;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.model.StubDocCategorizerBackendFactory;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.testing.TinyDoccatModel;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.DocumentClassification;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies classic {@link opennlp.tools.doccat.DocumentCategorizerME} integration through the
 * analyzer, using a two-category topic model trained in-memory from a fixture corpus
 * (see {@link TinyDoccatModel}). Fully offline; no model is downloaded.
 */
class BasicDocumentAnalyzerDocCategorizeTest {

  private static final String WEATHER_TEXT =
      "A powerful storm brought heavy rain and strong wind to the coast. "
          + "Forecasters expect freezing temperature and snow through the weekend.";
  private static final String FINANCE_TEXT =
      "The company reported record quarterly earnings, lifting its share price. "
          + "Investors bought bonds and shares after the central bank cut interest rates.";

  @TempDir
  static Path modelDir;

  private static Path topicModelPath;

  @BeforeAll
  static void trainTopicModel() throws IOException {
    topicModelPath = TinyDoccatModel.trainTopicModel(modelDir.resolve("topic-doccat.bin"));
  }

  private static String pathKey(String id) {
    return ClassicDocCategorizerBackendFactory.KEY_PREFIX + id
        + ClassicDocCategorizerBackendFactory.KEY_SUFFIX;
  }

  private static BasicDocumentAnalyzer analyzerWithTopicModel() {
    final ModelBundleCache modelBundleCache =
        new ModelBundleCache(Map.of(pathKey("topic"), topicModelPath.toString()));
    return new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(false, true), modelBundleCache);
  }

  private static AnalyzeDocumentRequest categorizeRequest(String text) {
    return AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(text).build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId(ProfileRegistry.DOCCAT_PROFILE_ID)
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addSteps(PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE)
            .build())
        .build();
  }

  @Test
  void classifiesDocumentWithConfiguredModel() {
    final AnalyzeDocumentResponse weather =
        analyzerWithTopicModel().analyze(categorizeRequest(WEATHER_TEXT));
    final DocumentClassification classification = weather.getDocument().getClassification();
    assertEquals("weather", classification.getBestCategory());
    assertEquals(2, classification.getCategoryScoresCount());
    assertTrue(classification.getCategoryScoresMap().containsKey("finance"));
    // Scores are probabilities in [0,1] that sum to ~1 across the two categories.
    final double sum = classification.getCategoryScoresMap().values().stream()
        .mapToDouble(Double::doubleValue).sum();
    assertEquals(1.0d, sum, 1.0e-6);
    assertTrue(weather.getDiagnosticsList().stream()
        .anyMatch(d -> d.getStep() == PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE));
  }

  @Test
  void separatesTheTwoFixtureClasses() {
    assertEquals("weather", analyzerWithTopicModel().analyze(categorizeRequest(WEATHER_TEXT))
        .getDocument().getClassification().getBestCategory());
    assertEquals("finance", analyzerWithTopicModel().analyze(categorizeRequest(FINANCE_TEXT))
        .getDocument().getClassification().getBestCategory());
  }

  @Test
  void doccatBundleIsListedWhenModelsConfigured() {
    final ModelBundleCache modelBundleCache =
        new ModelBundleCache(Map.of(pathKey("topic"), topicModelPath.toString()));
    assertTrue(modelBundleCache.listBundles().stream()
        .anyMatch(bundle -> ProfileRegistry.DOCCAT_BUNDLE_ID.equals(bundle.getBundleId())));
    assertTrue(modelBundleCache.getDocCategorizerRegistry().supportsModel("topic"));
  }

  @Test
  void rejectsDocCategorizeWhenNoModelConfigured() {
    final ModelBundleCache modelBundleCache = new ModelBundleCache(Map.of());
    final BasicDocumentAnalyzer analyzer =
        new BasicDocumentAnalyzer(ProfileRegistry.createDefault(false, false), modelBundleCache);

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(WEATHER_TEXT).build())
            .setProfile(AnalysisProfile.newBuilder()
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
                .addSteps(PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE)
                .build())
            .build()));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }

  @Test
  void classifiesRawTextModelWithoutTokenizationStep() {
    // A raw-text backend (the stub stands in for an ONNX DocumentCategorizerDL) reports
    // requiresTokens()==false, so a DOC_CATEGORIZE-only profile with no SENTENCE_DETECT/TOKENIZE
    // is valid and must classify the document text directly.
    final ModelBundleCache modelBundleCache =
        new ModelBundleCache(Map.of(StubDocCategorizerBackendFactory.KEY_CATEGORY, "spam"));
    final BasicDocumentAnalyzer analyzer =
        new BasicDocumentAnalyzer(ProfileRegistry.createDefault(false, true), modelBundleCache);

    final AnalyzeDocumentResponse response = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(WEATHER_TEXT).build())
        .setProfile(AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE)
            .build())
        .build());

    assertEquals("spam", response.getDocument().getClassification().getBestCategory());
    assertEquals(0, response.getDocument().getSentencesCount());
  }

  @Test
  void rejectsDocCategorizeWhenSelectionIsAmbiguous() {
    final ModelBundleCache modelBundleCache = new ModelBundleCache(Map.of(
        pathKey("topic"), topicModelPath.toString(),
        pathKey("topic2"), topicModelPath.toString()));
    final BasicDocumentAnalyzer analyzer =
        new BasicDocumentAnalyzer(ProfileRegistry.createDefault(false, true), modelBundleCache);

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(categorizeRequest(WEATHER_TEXT)));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains(DocCategorizerRegistry.KEY_DEFAULT_ID));
  }
}
