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

import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.model.SentimentRegistry;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.testing.TinySentimentModel;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies sentence-level sentiment through the analyzer, using a two-class polarity model
 * trained in-memory from a fixture corpus (see {@link TinySentimentModel}). Fully offline; no
 * model is downloaded.
 */
class BasicDocumentAnalyzerSentimentTest {

  // Two sentences with deliberately opposite, fixture-vocabulary polarity.
  private static final String MIXED_TEXT =
      "The wonderful staff gave us an excellent and delightful welcome. "
          + "The terrible delays and awful rooms were a horrible disappointing letdown.";

  @TempDir
  static Path modelDir;

  private static Path polarityModelPath;

  @BeforeAll
  static void trainPolarityModel() throws IOException {
    polarityModelPath = TinySentimentModel.trainPolarityModel(modelDir.resolve("polarity.bin"));
  }

  private static String pathKey(String id) {
    return SentimentRegistry.KEY_PREFIX + id + SentimentRegistry.KEY_SUFFIX;
  }

  private static BasicDocumentAnalyzer analyzerWithPolarityModel() {
    final ModelBundleCache modelBundleCache =
        new ModelBundleCache(Map.of(pathKey("polarity"), polarityModelPath.toString()));
    return new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(false, false, true), modelBundleCache);
  }

  private static AnalyzeDocumentRequest sentimentRequest(String text) {
    return AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(text).build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId(ProfileRegistry.SENTIMENT_PROFILE_ID)
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addSteps(PipelineStep.PIPELINE_STEP_SENTIMENT)
            .build())
        .build();
  }

  @Test
  void scoresSentimentPerSentence() {
    final AnalyzeDocumentResponse response =
        analyzerWithPolarityModel().analyze(sentimentRequest(MIXED_TEXT));
    final OpenNlpDocument document = response.getDocument();
    assertEquals(2, document.getSentencesCount());

    final AnnotatedSentence first = document.getSentences(0);
    final AnnotatedSentence second = document.getSentences(1);
    assertEquals("positive", first.getSentimentLabel());
    assertEquals("negative", second.getSentimentLabel());

    // Confidence is the winning category's probability, in (0, 1].
    assertTrue(first.getSentimentConfidence() > 0.0f && first.getSentimentConfidence() <= 1.0f);
    assertTrue(second.getSentimentConfidence() > 0.0f && second.getSentimentConfidence() <= 1.0f);

    assertTrue(response.getDiagnosticsList().stream()
        .anyMatch(d -> d.getStep() == PipelineStep.PIPELINE_STEP_SENTIMENT));
  }

  @Test
  void sentimentBundleIsListedWhenModelsConfigured() {
    final ModelBundleCache modelBundleCache =
        new ModelBundleCache(Map.of(pathKey("polarity"), polarityModelPath.toString()));
    assertTrue(modelBundleCache.listBundles().stream()
        .anyMatch(bundle -> ProfileRegistry.SENTIMENT_BUNDLE_ID.equals(bundle.getBundleId())));
    assertTrue(modelBundleCache.getSentimentRegistry().supportsModel("polarity"));
  }

  @Test
  void scoresRawTextModelWithoutTokenizationStep() {
    // A raw-text backend (the stub stands in for an ONNX sentiment model) reports
    // requiresTokens()==false, so SENTIMENT runs with SENTENCE_DETECT but no TOKENIZE, scoring
    // each sentence's text directly. The key is the stub's sentiment-namespace form.
    final ModelBundleCache modelBundleCache =
        new ModelBundleCache(Map.of("model.sentiment_stub.category", "neutral"));
    final BasicDocumentAnalyzer analyzer =
        new BasicDocumentAnalyzer(ProfileRegistry.createDefault(false, false, true),
            modelBundleCache);

    final AnalyzeDocumentResponse response = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(MIXED_TEXT).build())
        .setProfile(AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_SENTIMENT)
            .build())
        .build());

    final OpenNlpDocument document = response.getDocument();
    assertEquals(2, document.getSentencesCount());
    for (AnnotatedSentence sentence : document.getSentencesList()) {
      assertEquals("neutral", sentence.getSentimentLabel());
      assertEquals(0, sentence.getTokensCount());
    }
  }

  @Test
  void rejectsSentimentWhenNoModelConfigured() {
    final ModelBundleCache modelBundleCache = new ModelBundleCache(Map.of());
    final BasicDocumentAnalyzer analyzer =
        new BasicDocumentAnalyzer(ProfileRegistry.createDefault(false, false, false),
            modelBundleCache);

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(MIXED_TEXT).build())
            .setProfile(AnalysisProfile.newBuilder()
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
                .addSteps(PipelineStep.PIPELINE_STEP_SENTIMENT)
                .build())
            .build()));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }

  @Test
  void rejectsSentimentWhenSelectionIsAmbiguous() {
    final ModelBundleCache modelBundleCache = new ModelBundleCache(Map.of(
        pathKey("polarity"), polarityModelPath.toString(),
        pathKey("polarity2"), polarityModelPath.toString()));
    final BasicDocumentAnalyzer analyzer =
        new BasicDocumentAnalyzer(ProfileRegistry.createDefault(false, false, true),
            modelBundleCache);

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(sentimentRequest(MIXED_TEXT)));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains(SentimentRegistry.KEY_DEFAULT_ID));
  }
}
