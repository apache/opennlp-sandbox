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

import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the language detection pipeline step against the bundled
 * language detector model (ISO 639-3 predictions).
 */
class BasicDocumentAnalyzerLanguageDetectTest {

  private static final String ENGLISH_TEXT =
      "The driver was taken to the hospital after the accident on the highway. "
          + "Doctors expect a full recovery within several weeks.";
  private static final String GERMAN_TEXT =
      "Der Fahrer wurde nach dem Unfall auf der Autobahn ins Krankenhaus gebracht. "
          + "Die Ärzte erwarten eine vollständige Genesung innerhalb weniger Wochen.";

  private final ModelBundleCache modelBundleCache = new ModelBundleCache(Map.of());
  private final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(
      ProfileRegistry.createDefault(), modelBundleCache);

  @Test
  void detectsEnglish() {
    final AnalyzeDocumentResponse response = analyzer.analyze(request(ENGLISH_TEXT, true));
    assertEquals("eng", response.getDocument().getDetectedLanguage());
    assertTrue(response.getDocument().getLanguageConfidence() > 0.0f);
  }

  @Test
  void detectsGerman() {
    final AnalyzeDocumentResponse response = analyzer.analyze(request(GERMAN_TEXT, true));
    assertEquals("deu", response.getDocument().getDetectedLanguage());
    assertTrue(response.getDocument().getLanguageConfidence() > 0.0f);
  }

  @Test
  void languageIsNotSetWhenStepIsNotRequested() {
    final AnalyzeDocumentResponse response = analyzer.analyze(request(ENGLISH_TEXT, false));
    assertFalse(response.getDocument().hasDetectedLanguage());
    assertFalse(response.getDocument().hasLanguageConfidence());
  }

  private static AnalyzeDocumentRequest request(String text, boolean detectLanguage) {
    final AnalysisProfile.Builder profile = AnalysisProfile.newBuilder()
        .setProfileId("langdetect-test");
    if (detectLanguage) {
      profile.addSteps(PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT);
    }
    profile.addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT);
    return AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder()
            .setDocId("doc-langdetect")
            .setRawText(text)
            .build())
        .setProfile(profile.build())
        .build();
  }
}
