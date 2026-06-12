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
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the POS tagging and lemmatization pipeline steps against the bundled
 * English UD models.
 */
class BasicDocumentAnalyzerPosLemmaTest {

  private static final String TEXT = "The cats sat on the mats. They were happy animals.";

  private final ModelBundleCache modelBundleCache = new ModelBundleCache(Map.of());
  private final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(
      ProfileRegistry.createDefault(), modelBundleCache);

  @Test
  void posTaggingAnnotatesEveryToken() {
    final AnalyzeDocumentResponse response = analyzer.analyze(request(
        profile(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
            PipelineStep.PIPELINE_STEP_TOKENIZE,
            PipelineStep.PIPELINE_STEP_POS_TAG),
        false));

    assertEquals(2, response.getDocument().getSentencesCount());
    for (AnnotatedSentence sentence : response.getDocument().getSentencesList()) {
      assertTrue(sentence.getTokensCount() > 0);
      for (Token token : sentence.getTokensList()) {
        assertTrue(token.hasPosTag(), "token '" + token.getText() + "' has no POS tag");
        assertFalse(token.getPosTag().isBlank());
        assertFalse(token.hasLemma(), "lemma must not be set without LEMMATIZE");
      }
    }
    // UD tagset spot checks: a determiner and a noun in "The cats".
    final AnnotatedSentence first = response.getDocument().getSentences(0);
    assertEquals("DET", first.getTokens(0).getPosTag());
    assertEquals("NOUN", first.getTokens(1).getPosTag());
  }

  @Test
  void posTaggingIncludesProbabilitiesWhenRequested() {
    final AnalyzeDocumentResponse response = analyzer.analyze(request(
        profile(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
            PipelineStep.PIPELINE_STEP_TOKENIZE,
            PipelineStep.PIPELINE_STEP_POS_TAG),
        true));

    for (AnnotatedSentence sentence : response.getDocument().getSentencesList()) {
      for (Token token : sentence.getTokensList()) {
        assertTrue(token.getPosProbability() > 0.0f,
            "token '" + token.getText() + "' has no POS probability");
      }
    }
  }

  @Test
  void lemmatizationAnnotatesEveryToken() {
    final AnalyzeDocumentResponse response = analyzer.analyze(request(
        profile(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
            PipelineStep.PIPELINE_STEP_TOKENIZE,
            PipelineStep.PIPELINE_STEP_POS_TAG,
            PipelineStep.PIPELINE_STEP_LEMMATIZE),
        false));

    for (AnnotatedSentence sentence : response.getDocument().getSentencesList()) {
      for (Token token : sentence.getTokensList()) {
        assertTrue(token.hasLemma(), "token '" + token.getText() + "' has no lemma");
        assertFalse(token.getLemma().isBlank());
      }
    }
    // The plural noun is reduced to its lemma.
    final AnnotatedSentence first = response.getDocument().getSentences(0);
    assertEquals("cats", first.getTokens(1).getText());
    assertEquals("cat", first.getTokens(1).getLemma());
  }

  @Test
  void posTaggingWithoutTokenizationIsRejected() {
    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(request(
            profile(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
                PipelineStep.PIPELINE_STEP_POS_TAG),
            false)));
    assertTrue(e.getMessage().contains(PipelineStep.PIPELINE_STEP_TOKENIZE.name()));
  }

  @Test
  void lemmatizationWithoutPosTaggingIsRejected() {
    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(request(
            profile(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
                PipelineStep.PIPELINE_STEP_TOKENIZE,
                PipelineStep.PIPELINE_STEP_LEMMATIZE),
            false)));
    assertTrue(e.getMessage().contains(PipelineStep.PIPELINE_STEP_POS_TAG.name()));
  }

  private static AnalysisProfile profile(PipelineStep... steps) {
    final AnalysisProfile.Builder profile = AnalysisProfile.newBuilder()
        .setProfileId("pos-lemma-test");
    for (PipelineStep step : steps) {
      profile.addSteps(step);
    }
    return profile.build();
  }

  private static AnalyzeDocumentRequest request(AnalysisProfile profile,
      boolean includeProbabilities) {
    return AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder()
            .setDocId("doc-pos-lemma")
            .setRawText(TEXT)
            .build())
        .setProfile(profile)
        .setOptions(AnalysisOptions.newBuilder()
            .setIncludeProbabilities(includeProbabilities)
            .build())
        .build();
  }
}
