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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.LayerScope;
import org.apache.opennlp.grpc.v1.LexicalExpansionAnnotation;
import org.apache.opennlp.grpc.v1.LexicalExpansionKind;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies PIPELINE_STEP_EXPAND: lexical expansions over a configured WN-LMF
 * knowledge base rendered as the {@code opennlp:expansions} document-shape layer,
 * against the miniature WordNet fixture.
 */
class BasicDocumentAnalyzerExpandTest {

  private static final String TEXT = "The big dog runs.";

  private static String fixtureLexiconPath() {
    try {
      return Path.of(BasicDocumentAnalyzerExpandTest.class
          .getResource("/wordnet/mini-wn-lmf.xml").toURI()).toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  private static BasicDocumentAnalyzer analyzerWithLexicon() {
    return new BasicDocumentAnalyzer(ProfileRegistry.createDefault(), new ModelBundleCache(
        Map.of("model.wordnet.mini.path", fixtureLexiconPath())));
  }

  private static AnalyzeDocumentRequest request(PipelineStep... steps) {
    final AnalysisProfile.Builder profile = AnalysisProfile.newBuilder().setProfileId("expand");
    for (PipelineStep step : steps) {
      profile.addSteps(step);
    }
    return AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .setProfile(profile.build())
        .build();
  }

  private static Optional<AnnotationLayer> expansionsLayer(AnalyzeDocumentResponse response) {
    return response.getDocument().getLayers().getLayersList().stream()
        .filter(l -> "opennlp:expansions".equals(l.getId()))
        .findFirst();
  }

  @Test
  void expansionsAnchorScoredLabelsOnTokenSpans() {
    final AnalyzeDocumentResponse response = analyzerWithLexicon().analyze(request(
        PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
        PipelineStep.PIPELINE_STEP_TOKENIZE,
        PipelineStep.PIPELINE_STEP_EXPAND));

    final Optional<AnnotationLayer> found = expansionsLayer(response);
    assertTrue(found.isPresent(), "opennlp:expansions layer is missing");
    final AnnotationLayer expansions = found.get();
    assertEquals(LayerScope.LAYER_SCOPE_POSITIONAL, expansions.getScope());
    assertTrue(expansions.getLexicalExpansionValues().getAnnotationsCount() > 0);

    final List<Token> tokens = new ArrayList<>();
    for (AnnotatedSentence sentence : response.getDocument().getSentencesList()) {
      tokens.addAll(sentence.getTokensList());
    }
    final Token dog = tokens.stream()
        .filter(t -> "dog".equals(t.getText()))
        .findFirst().orElseThrow();

    final List<String> dogExpansions = new ArrayList<>();
    for (LexicalExpansionAnnotation annotation
        : expansions.getLexicalExpansionValues().getAnnotationsList()) {
      assertFalse(annotation.getTerm().isBlank());
      assertTrue(annotation.getWeight() > 0.0d, "expansion carries no weight");
      assertEquals("mini", annotation.getLexiconId());
      if (annotation.getSpan().getStart() == dog.getAnnotationSpan().getStart()) {
        dogExpansions.add(annotation.getTerm());
        if ("domestic dog".equals(annotation.getTerm())) {
          assertEquals(LexicalExpansionKind.LEXICAL_EXPANSION_KIND_SYNONYM,
              annotation.getKind());
          assertEquals(0, annotation.getDepth());
        }
      }
    }
    assertTrue(dogExpansions.contains("domestic dog"),
        "expansions of 'dog' miss its synonym: " + dogExpansions);
  }

  @Test
  void expandWithoutTokenizeFails() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzerWithLexicon().analyze(request(
            PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
            PipelineStep.PIPELINE_STEP_EXPAND)));
    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, error.getFailureType());
  }

  @Test
  void expandWithoutConfiguredLexiconFails() {
    final BasicDocumentAnalyzer bare = new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(), new ModelBundleCache(Map.of()));
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> bare.analyze(request(
            PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
            PipelineStep.PIPELINE_STEP_TOKENIZE,
            PipelineStep.PIPELINE_STEP_EXPAND)));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }

  @Test
  void unknownLexiconIdFails() {
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("expand")
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addSteps(PipelineStep.PIPELINE_STEP_EXPAND)
            .setWordnetLexiconId("missing")
            .build())
        .build();
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzerWithLexicon().analyze(request));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }
}
