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
import java.util.List;
import java.util.Map;

import com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.testing.TinyPosLemmaModels;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.ModelBundleInfo;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins per-language classic pipeline routing: {@code model.pipeline.<lang>.*} model sets
 * are advertised as bundles, requests route to them by detected language, and the additive
 * {@code AnalysisProfile.pipeline_language} field selects one explicitly. The probe
 * lemmatizer marks every lemma with the serving pipeline, so the output alone proves which
 * pipeline ran.
 */
class LanguagePipelineRoutingTest {

  private static final String TEXT = "The cats sleep.";

  @TempDir
  static Path dir;

  private static Map<String, String> pipelineConfiguration;

  @BeforeAll
  static void trainPipelineModels() throws IOException {
    pipelineConfiguration = Map.of(
        "model.pipeline.en.sentence_detector.path",
        TinyPosLemmaModels.trainSentenceModel(dir.resolve("xx-sent.bin")).toString(),
        "model.pipeline.en.tokenizer.path",
        TinyPosLemmaModels.trainTokenizerModel(dir.resolve("xx-token.bin")).toString(),
        "model.pipeline.en.pos_tagger.path",
        TinyPosLemmaModels.trainPosModel(dir.resolve("xx-pos.bin")).toString(),
        "model.pipeline.en.lemmatizer.path",
        TinyPosLemmaModels.trainMarkerLemmaModel(dir.resolve("xx-lemma.bin"), "alt").toString());
  }

  @Test
  void analysisProfileCarriesThePipelineLanguage() {
    final FieldDescriptor field =
        AnalysisProfile.getDescriptor().findFieldByName("pipeline_language");
    assertNotNull(field, "AnalysisProfile.pipeline_language is missing");
    assertEquals(22, field.getNumber());
    assertEquals(FieldDescriptor.Type.STRING, field.getType());
    assertTrue(field.hasPresence());
  }

  @Test
  void advertisesConfiguredLanguagePipelinesAsBundles() {
    try (ModelBundleCache cache = new ModelBundleCache(pipelineConfiguration)) {
      final List<ModelBundleInfo> bundles = cache.listBundles();
      final ModelBundleInfo pipeline = bundles.stream()
          .filter(bundle -> "pipeline-en".equals(bundle.getBundleId()))
          .findFirst().orElse(null);
      assertNotNull(pipeline, "configured language pipeline is not advertised as a bundle");
      assertEquals(List.of("en"), pipeline.getSupportedLanguagesList());
      assertTrue(pipeline.getSupportedStepsList().containsAll(List.of(
          PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
          PipelineStep.PIPELINE_STEP_TOKENIZE,
          PipelineStep.PIPELINE_STEP_POS_TAG,
          PipelineStep.PIPELINE_STEP_LEMMATIZE)));
    }
  }

  @Test
  void routesByDetectedLanguageToTheConfiguredPipeline() {
    try (BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(pipelineConfiguration)) {
      final OpenNlpDocument document = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
          .setDocument(OpenNlpDocument.newBuilder().setDocId("route").setRawText(TEXT))
          .setProfile(AnalysisProfile.newBuilder()
              .addSteps(PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT)
              .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
              .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
              .addSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
              .addSteps(PipelineStep.PIPELINE_STEP_LEMMATIZE))
          .build()).getDocument();

      // The detector reports eng for this text, which resolves to the configured
      // "en" pipeline; its probe lemmatizer marks every lemma.
      assertEquals("eng", document.getDetectedLanguage());
      assertEquals("cats-alt", document.getSentences(0).getTokens(1).getLemma());
    }
  }

  @Test
  void explicitPipelineLanguageSelectsWithoutLanguageDetection() {
    try (BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(pipelineConfiguration)) {
      final OpenNlpDocument document = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
          .setDocument(OpenNlpDocument.newBuilder().setDocId("explicit").setRawText(TEXT))
          .setProfile(AnalysisProfile.newBuilder()
              .setPipelineLanguage("en")
              .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
              .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
              .addSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
              .addSteps(PipelineStep.PIPELINE_STEP_LEMMATIZE))
          .build()).getDocument();

      assertEquals("cats-alt", document.getSentences(0).getTokens(1).getLemma());
    }
  }

  @Test
  void unknownPipelineLanguageFailsLoudNamingTheConfiguredOnes() {
    try (BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(pipelineConfiguration)) {
      final AnalysisException e = assertThrows(AnalysisException.class,
          () -> analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
                  .setDocument(OpenNlpDocument.newBuilder().setDocId("bad").setRawText(TEXT))
                  .setProfile(AnalysisProfile.newBuilder()
                      .setPipelineLanguage("tlh")
                      .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT))
                  .build()));
      assertTrue(e.getMessage().contains("en"));
    }
  }

  @Test
  void detectionFallsBackToTheDefaultPipelineWithoutAMatch() {
    try (BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of())) {
      final OpenNlpDocument document = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
          .setDocument(OpenNlpDocument.newBuilder().setDocId("default").setRawText(TEXT))
          .setProfile(AnalysisProfile.newBuilder()
              .addSteps(PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT)
              .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
              .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
              .addSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
              .addSteps(PipelineStep.PIPELINE_STEP_LEMMATIZE))
          .build()).getDocument();

      assertEquals("cat", document.getSentences(0).getTokens(1).getLemma());
    }
  }
}
