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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicDocumentAnalyzerTest {

  private static final String TEXT =
      "The driver got badly injured by the accident. He was taken to the hospital!";

  private final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

  private AnalyzeDocumentResponse analyze(String text, AnalysisOptions options) {
    final AnalyzeDocumentRequest.Builder request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setDocId("doc-1").setRawText(text).build());
    if (options != null) {
      request.setOptions(options);
    }
    return analyzer.analyze(request.build());
  }

  @Test
  void analyzesSentencesAndTokens() {
    final AnalyzeDocumentResponse response = analyze(TEXT, null);

    assertEquals("doc-1", response.getDocument().getDocId());
    assertEquals(2, response.getDocument().getSentencesCount());
    assertFalse(response.getDocument().getSentences(0).getTokensList().isEmpty());
    assertTrue(response.getDiagnosticsList().stream()
        .anyMatch(d -> d.getStep() == PipelineStep.PIPELINE_STEP_SENTENCE_DETECT));
    assertTrue(response.getDiagnosticsList().stream()
        .anyMatch(d -> d.getStep() == PipelineStep.PIPELINE_STEP_TOKENIZE));
  }

  @Test
  void defaultsToUtf8ByteOffsetsForMultibyteText() {
    // One sentence containing a supplementary character (emoji = 2 UTF-16 units, 4 UTF-8 bytes).
    final String text = "Hi there 😀.";
    final AnalyzeDocumentResponse response = analyze(text, null);

    assertEquals(OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE, response.getDocument().getOffsetEncoding());
    final AnnotatedSentence sentence = response.getDocument().getSentences(0);
    // The sentence covers the whole text; its end offset must be the UTF-8 byte length,
    // not the (smaller) UTF-16 length, proving the conversion ran.
    assertEquals(text.getBytes(StandardCharsets.UTF_8).length, sentence.getSentenceSpan().getEnd());
    assertTrue(sentence.getSentenceSpan().getEnd() > text.length());
  }

  @Test
  void honorsUtf16OffsetEncodingWhenRequested() {
    final String text = "Hi there 😀.";
    final AnalyzeDocumentResponse response = analyze(text,
        AnalysisOptions.newBuilder()
            .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
            .build());

    assertEquals(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT, response.getDocument().getOffsetEncoding());
    assertEquals(text.length(), response.getDocument().getSentences(0).getSentenceSpan().getEnd());
  }

  @Test
  void includeProbabilitiesPopulatesSpanProbability() {
    final AnalyzeDocumentResponse withProbs = analyze(TEXT,
        AnalysisOptions.newBuilder().setIncludeProbabilities(true).build());
    final AnnotatedSentence sentence = withProbs.getDocument().getSentences(0);
    assertTrue(sentence.getSentenceSpan().hasProbability());
    assertTrue(sentence.getTokens(0).getAnnotationSpan().hasProbability());

    // Default (off) leaves probabilities unset.
    final AnnotatedSentence noProbs = analyze(TEXT, null).getDocument().getSentences(0);
    assertFalse(noProbs.getSentenceSpan().hasProbability());
  }
}
