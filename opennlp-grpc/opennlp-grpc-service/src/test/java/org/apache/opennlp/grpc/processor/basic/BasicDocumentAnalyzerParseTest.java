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

import java.nio.file.Files;
import java.nio.file.Path;
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
import org.apache.opennlp.grpc.v1.ParseFormat;
import org.apache.opennlp.grpc.v1.ParseNodeKind;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Analyzer-level tests for {@code PIPELINE_STEP_PARSE}. The availability/error path is hermetic;
 * the actual end-to-end parse is opt-in because a constituency parser model cannot be trained
 * in-memory and is too large to bundle. Point {@code -Dparser.model.path=/path/to/en-parser.bin}
 * at a real OpenNLP parser model to exercise it; without the property the parse test is skipped.
 */
class BasicDocumentAnalyzerParseTest {

  private static final String TEXT = "The dog barked loudly.";

  private static AnalyzeDocumentRequest parseRequest(ParseFormat... formats) {
    final AnalysisProfile.Builder profile = AnalysisProfile.newBuilder()
        .setProfileId(ProfileRegistry.PARSE_PROFILE_ID)
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSteps(PipelineStep.PIPELINE_STEP_PARSE);
    final AnalyzeDocumentRequest.Builder request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .setProfile(profile.build());
    if (formats.length > 0) {
      final AnalysisOptions.Builder options = AnalysisOptions.newBuilder();
      for (ParseFormat format : formats) {
        options.addParseFormats(format);
      }
      request.setOptions(options.build());
    }
    return request.build();
  }

  @Test
  void rejectsParseWhenNoModelConfigured() {
    final ModelBundleCache modelBundleCache = new ModelBundleCache(Map.of());
    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(false, false, false, false), modelBundleCache);

    final AnalysisException error =
        assertThrows(AnalysisException.class, () -> analyzer.analyze(parseRequest()));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }

  @Test
  void parsesEachSentenceWhenModelConfigured() {
    final String modelPath = System.getProperty("parser.model.path");
    assumeTrue(modelPath != null && Files.isRegularFile(Path.of(modelPath)),
        "set -Dparser.model.path to a real OpenNLP parser model to run this test");

    final ModelBundleCache modelBundleCache =
        new ModelBundleCache(Map.of("model.parser.path", modelPath));
    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(false, false, false, true), modelBundleCache);

    // Default formats: both structured tree and bracketed string.
    final AnalyzeDocumentResponse response = analyzer.analyze(parseRequest());
    final AnnotatedSentence sentence = response.getDocument().getSentences(0);
    assertTrue(sentence.hasParseTree());
    assertTrue(sentence.getParseTree().hasRoot());
    assertEquals(ParseNodeKind.PARSE_NODE_KIND_NONTERMINAL,
        sentence.getParseTree().getRoot().getKind());
    assertFalse(sentence.getParseTree().getPennTreebank().isBlank());
    assertTrue(response.getDiagnosticsList().stream()
        .anyMatch(d -> d.getStep() == PipelineStep.PIPELINE_STEP_PARSE));

    // Format selector: bracketed only -> structured tree is left empty.
    final AnnotatedSentence bracketedOnly = analyzer
        .analyze(parseRequest(ParseFormat.PARSE_FORMAT_BRACKETED))
        .getDocument().getSentences(0);
    assertFalse(bracketedOnly.getParseTree().hasRoot());
    assertFalse(bracketedOnly.getParseTree().getPennTreebank().isBlank());
  }
}
