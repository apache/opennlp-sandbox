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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.processor;

import java.util.Map;

import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicDocumentAnalyzerTest {

  private static final String TEXT =
      "The driver got badly injured by the accident. He was taken to the hospital!";

  @Test
  void analyzesSentencesAndTokens() {
    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

    final AnalyzeDocumentResponse response = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder()
            .setDocId("doc-1")
            .setRawText(TEXT)
            .build())
        .build());

    assertEquals("doc-1", response.getDocument().getDocId());
    assertEquals(2, response.getDocument().getSentencesCount());
    assertFalse(response.getDocument().getSentences(0).getTokensList().isEmpty());
    assertTrue(response.getDiagnosticsList().stream()
        .anyMatch(d -> d.getStep() == PipelineStep.PIPELINE_STEP_SENTENCE_DETECT));
    assertTrue(response.getDiagnosticsList().stream()
        .anyMatch(d -> d.getStep() == PipelineStep.PIPELINE_STEP_TOKENIZE));
  }
}
