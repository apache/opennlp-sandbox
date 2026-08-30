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
import org.apache.opennlp.grpc.model.StubNerBackendFactory;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.SentenceDetectorSelector;
import org.apache.opennlp.grpc.v1.StandardSentenceDetectorEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that a NER step failing mid-document still clears the name finders' adaptive data,
 * so the next document on the same thread sees pristine recognizer state. Uses the stateful
 * failing stub recognizer (see {@link StubNerBackendFactory#KEY_FAILING_TYPE}), whose entity
 * span shifts right by the leaked adaptive-data depth, making a leak observable in output.
 */
class BasicDocumentAnalyzerNerAdaptiveDataTest {

  private static final String FAILING_TEXT = "Calm session here\nBoom goes now";
  private static final String QUIET_TEXT = "Calm session here";

  @BeforeEach
  void resetStub() {
    StubNerBackendFactory.resetFailingState();
  }

  private static BasicDocumentAnalyzer analyzerWithFailingRecognizer() {
    final ModelBundleCache modelBundleCache = new ModelBundleCache(
        Map.of(StubNerBackendFactory.KEY_FAILING_TYPE, "exploding"));
    return new BasicDocumentAnalyzer(ProfileRegistry.createDefault(true), modelBundleCache);
  }

  private static AnalyzeDocumentRequest request(String text) {
    return AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(text).build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("ner-adaptive-data-test")
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addSteps(PipelineStep.PIPELINE_STEP_NER)
            .setSentenceDetector(SentenceDetectorSelector.newBuilder()
                .setStandard(StandardSentenceDetectorEngine
                    .STANDARD_SENTENCE_DETECTOR_ENGINE_NEWLINE))
            .addNerEntityTypes("exploding")
            .build())
        .build();
  }

  @Test
  void failedNerStepLeavesNoAdaptiveDataBehind() {
    final BasicDocumentAnalyzer analyzer = analyzerWithFailingRecognizer();

    // Sentence one accumulates adaptive data; sentence two ("Boom") fails the step.
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(request(FAILING_TEXT)));
    assertEquals(AnalysisException.FailureType.INTERNAL, error.getFailureType());

    // The failure path must clear adaptive data exactly like the success path.
    assertEquals(1, StubNerBackendFactory.clearCount());
    assertEquals(0, StubNerBackendFactory.adaptiveDepth());

    // A second document on the same thread produces the entities of a pristine run.
    final AnnotatedSentence rerun =
        analyzer.analyze(request(QUIET_TEXT)).getDocument().getSentences(0);
    final AnnotatedSentence baseline =
        analyzerWithFailingRecognizer().analyze(request(QUIET_TEXT)).getDocument()
            .getSentences(0);
    StubNerBackendFactory.resetFailingState();
    assertEquals(baseline.getEntitiesList(), rerun.getEntitiesList());
  }

  @Test
  void clearAdaptiveDataOptOutAppliesOnTheFailurePathToo() {
    final BasicDocumentAnalyzer analyzer = analyzerWithFailingRecognizer();
    final AnalyzeDocumentRequest optOut = request(FAILING_TEXT).toBuilder()
        .setOptions(org.apache.opennlp.grpc.v1.AnalysisOptions.newBuilder()
            .setClearAdaptiveData(false)
            .build())
        .build();

    assertThrows(AnalysisException.class, () -> analyzer.analyze(optOut));

    // clear_adaptive_data=false still opts out, failure or not.
    assertEquals(0, StubNerBackendFactory.clearCount());
    assertEquals(1, StubNerBackendFactory.adaptiveDepth());
  }
}
