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
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the one piece of guidance a caller gets when a supported step has no model on the
 * server: the error names the configuration key that would serve it. The workbench
 * transcribes these keys into its browned-out feature panel, so a drift here is a wrong
 * instruction on screen.
 */
class BasicDocumentAnalyzerUnconfiguredStepTest {

  @ParameterizedTest(name = "{0} names {1}")
  @CsvSource({
      "PIPELINE_STEP_NER, model.name_finder.",
      "PIPELINE_STEP_PARSE, model.parser.",
      "PIPELINE_STEP_SYNTACTIC_CHUNK, model.chunker.",
      "PIPELINE_STEP_DOC_CATEGORIZE, model.doccat.",
      "PIPELINE_STEP_SENTIMENT, model.sentiment.",
      "PIPELINE_STEP_SUBWORD_TOKENIZE, model.subword.",
      "PIPELINE_STEP_EXPAND, model.wordnet.",
      "PIPELINE_STEP_EMBED, model.embedder.",
  })
  void unconfiguredStepsNameTheirConfigurationKey(String step, String key) {
    final BasicDocumentAnalyzer bare = new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(), new ModelBundleCache(Map.of()));
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText("The cats sat on the mats."))
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("unconfigured")
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addSteps(PipelineStep.valueOf(step)))
        .build();

    final AnalysisException failure =
        assertThrows(AnalysisException.class, () -> bare.analyze(request));

    assertEquals(AnalysisException.FailureType.NOT_FOUND, failure.getFailureType());
    assertTrue(failure.getMessage().contains(key),
        step + " must name " + key + " but said: " + failure.getMessage());
  }
}
