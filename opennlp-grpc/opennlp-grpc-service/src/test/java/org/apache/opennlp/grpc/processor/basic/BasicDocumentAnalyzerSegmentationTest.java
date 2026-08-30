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

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.testing.StubSentenceDetectorBackendFactory;
import org.apache.opennlp.grpc.testing.StubTokenizerBackendFactory;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.SentenceDetectorSelector;
import org.apache.opennlp.grpc.v1.StandardSentenceDetectorEngine;
import org.apache.opennlp.grpc.v1.StandardTokenizerEngine;
import org.apache.opennlp.grpc.v1.TokenizerSelector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BasicDocumentAnalyzerSegmentationTest {

  private final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

  @Test
  void newlineSentenceDetectorTreatsEachNonEmptyLineAsASentence() {
    final AnalyzeDocumentResponse response = analyze("First line\n\nSecond line",
        AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .setSentenceDetector(SentenceDetectorSelector.newBuilder()
                .setStandard(StandardSentenceDetectorEngine
                    .STANDARD_SENTENCE_DETECTOR_ENGINE_NEWLINE)));

    assertEquals(2, response.getDocument().getSentencesCount());
    assertEquals(0, response.getDocument().getSentences(0).getSentenceSpan().getStart());
    assertEquals(10, response.getDocument().getSentences(0).getSentenceSpan().getEnd());
    assertEquals(12, response.getDocument().getSentences(1).getSentenceSpan().getStart());
    assertEquals(23, response.getDocument().getSentences(1).getSentenceSpan().getEnd());
  }

  @Test
  void whitespaceTokenizerRetainsAttachedPunctuation() {
    final AnalyzeDocumentResponse response = analyze("Hello, world!",
        tokenizingProfile(StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_WHITESPACE));

    assertEquals(List.of("Hello,", "world!"), tokenTexts(response));
  }

  @Test
  void simpleTokenizerSplitsCharacterClassTransitions() {
    final AnalyzeDocumentResponse response = analyze("abc123!",
        tokenizingProfile(StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_SIMPLE));

    assertEquals(List.of("abc", "123", "!"), tokenTexts(response));
  }

  @Test
  void typedAndLegacyTokenizerSelectorsAreMutuallyExclusive() {
    final AnalysisProfile.Builder profile = tokenizingProfile(
        StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_WHITESPACE)
        .setTokenizerEngine("model");

    final AnalysisException error = assertThrows(
        AnalysisException.class, () -> analyze("Hello", profile));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void unknownCustomTokenizerIsRejected() {
    final AnalysisProfile.Builder profile = AnalysisProfile.newBuilder()
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .setTokenizer(TokenizerSelector.newBuilder().setCustom("missing-tokenizer"));

    final AnalysisException error = assertThrows(
        AnalysisException.class, () -> analyze("Hello", profile));

    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }

  @Test
  void customTokenizerIsDiscoveredThroughServiceLoader() {
    final AnalysisProfile.Builder profile = AnalysisProfile.newBuilder()
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .setTokenizer(TokenizerSelector.newBuilder()
            .setCustom(StubTokenizerBackendFactory.ENGINE_ID));

    final AnalyzeDocumentResponse response = analyze("red|blue", profile);

    assertEquals(List.of("red", "blue"), tokenTexts(response));
  }

  @Test
  void customSentenceDetectorIsDiscoveredThroughServiceLoader() {
    final AnalysisProfile.Builder profile = AnalysisProfile.newBuilder()
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .setSentenceDetector(SentenceDetectorSelector.newBuilder()
            .setCustom(StubSentenceDetectorBackendFactory.ENGINE_ID));

    final AnalyzeDocumentResponse response = analyze("First|Second", profile);

    assertEquals(2, response.getDocument().getSentencesCount());
  }

  private AnalyzeDocumentResponse analyze(String text, AnalysisProfile.Builder profile) {
    return analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(text))
        .setProfile(profile.setProfileId("segmentation-test"))
        .build());
  }

  private static AnalysisProfile.Builder tokenizingProfile(StandardTokenizerEngine engine) {
    return AnalysisProfile.newBuilder()
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .setTokenizer(TokenizerSelector.newBuilder().setStandard(engine));
  }

  private static List<String> tokenTexts(AnalyzeDocumentResponse response) {
    return response.getDocument().getSentences(0).getTokensList().stream()
        .map(token -> token.getText())
        .toList();
  }
}
