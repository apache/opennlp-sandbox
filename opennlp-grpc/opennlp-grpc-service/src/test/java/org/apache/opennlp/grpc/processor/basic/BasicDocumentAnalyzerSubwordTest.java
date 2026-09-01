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
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.LayerScope;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.SubwordAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies PIPELINE_STEP_SUBWORD_TOKENIZE: the SentencePiece encoding of the document
 * text rendered as the {@code opennlp:subwords} document-shape layer, against the
 * tiny byte-fallback unigram fixture model.
 */
class BasicDocumentAnalyzerSubwordTest {

  private static final String TEXT = "The cats sat on the mats.";
  /**
   * Reads one public-domain chapter fixture (Pride and Prejudice or Alice's Adventures in
   * Wonderland), multi-paragraph regression texts for the subword layer.
   */
  private static String novelFixture(String resource) {
    try (InputStream input = BasicDocumentAnalyzerSubwordTest.class
        .getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalStateException("Novel regression fixture is missing");
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Could not read novel regression fixture", e);
    }
  }

  private static String fixtureModelPath() {
    try {
      return Path.of(BasicDocumentAnalyzerSubwordTest.class
          .getResource("/subword/tiny-unigram-bytefb.model").toURI()).toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  private static BasicDocumentAnalyzer analyzerWithSubwordModel() {
    final ModelBundleCache cache = new ModelBundleCache(Map.of(
        "model.subword.tiny.path", fixtureModelPath()));
    return new BasicDocumentAnalyzer(ProfileRegistry.createDefault(), cache);
  }

  private static AnalyzeDocumentRequest request(String text, AnalysisProfile profile) {
    return AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(text).build())
        .setProfile(profile)
        .build();
  }

  private static AnalysisProfile subwordProfile(String modelId) {
    final AnalysisProfile.Builder profile = AnalysisProfile.newBuilder()
        .setProfileId("subword")
        .addSteps(PipelineStep.PIPELINE_STEP_SUBWORD_TOKENIZE);
    if (modelId != null) {
      profile.setSubwordModelId(modelId);
    }
    return profile.build();
  }

  private static Optional<AnnotationLayer> layer(AnalyzeDocumentResponse response, String id) {
    return response.getDocument().getLayers().getLayersList().stream()
        .filter(l -> id.equals(l.getId()))
        .findFirst();
  }

  @Test
  void subwordLayerRendersPiecesWithVocabularyIdsAndSpans() {
    final AnalyzeDocumentResponse response =
        analyzerWithSubwordModel().analyze(request(TEXT, subwordProfile(null)));

    final Optional<AnnotationLayer> found = layer(response, "opennlp:subwords");
    assertTrue(found.isPresent(), "opennlp:subwords layer is missing");
    final AnnotationLayer subwords = found.get();
    assertEquals(LayerScope.LAYER_SCOPE_POSITIONAL, subwords.getScope());
    assertTrue(subwords.getSubwordValues().getAnnotationsCount() > 0);

    int previousStart = -1;
    for (SubwordAnnotation annotation : subwords.getSubwordValues().getAnnotationsList()) {
      assertFalse(annotation.getPiece().isBlank(), "piece is blank");
      assertTrue(annotation.getVocabularyId() >= 0, "vocabulary id is negative");
      assertTrue(annotation.getSpan().getStart() >= 0);
      assertTrue(annotation.getSpan().getEnd() <= TEXT.length());
      assertTrue(annotation.getSpan().getEnd() > annotation.getSpan().getStart());
      assertTrue(annotation.getSpan().getStart() >= previousStart,
          "subword spans are not in text order");
      previousStart = annotation.getSpan().getStart();
    }

    // The step is independent of sentence detection and word tokenization.
    assertEquals(0, response.getDocument().getSentencesCount());
    assertTrue(response.getDiagnosticsList().stream()
        .anyMatch(d -> d.getStep() == PipelineStep.PIPELINE_STEP_SUBWORD_TOKENIZE));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "/document/pride-and-prejudice-chapter-1.txt",
      "/document/alice-in-wonderland-chapter-1.txt"})
  void subwordLayerPreservesPiecesWithoutSourceSurface(String resource) {
    final String novel = novelFixture(resource);
    final AnalyzeDocumentResponse response =
        analyzerWithSubwordModel().analyze(request(novel, subwordProfile(null)));

    final AnnotationLayer subwords = layer(response, "opennlp:subwords").orElseThrow();
    assertTrue(subwords.getSubwordValues().getAnnotationsList().stream()
        .anyMatch(annotation -> annotation.getSpan().getStart()
            == annotation.getSpan().getEnd()),
        "fixture did not exercise a model piece without source surface");
    assertEquals(novel, response.getDocument().getRawText());
  }

  @Test
  void subwordSpansFollowTheRequestedOffsetEncoding() {
    final String text = "The café was open.";
    final AnalyzeDocumentResponse response = analyzerWithSubwordModel().analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(text).build())
            .setProfile(subwordProfile(null))
            .setOptions(AnalysisOptions.newBuilder()
                .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE)
                .build())
            .build());

    final int utf8Length = text.getBytes(StandardCharsets.UTF_8).length;
    final AnnotationLayer subwords = layer(response, "opennlp:subwords").orElseThrow();
    int maxEnd = 0;
    for (SubwordAnnotation annotation : subwords.getSubwordValues().getAnnotationsList()) {
      assertTrue(annotation.getSpan().getEnd() <= utf8Length);
      maxEnd = Math.max(maxEnd, annotation.getSpan().getEnd());
    }
    // The encoding covers past the multibyte character, so the furthest span end can
    // only be expressed in byte units: it exceeds the UTF-16 length.
    assertTrue(maxEnd > text.length(),
        "subword spans were not remapped to UTF-8 bytes (max end " + maxEnd + ")");
  }

  @Test
  void subwordStepWithoutConfiguredModelFails() {
    final BasicDocumentAnalyzer bare = new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(), new ModelBundleCache(Map.of()));

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> bare.analyze(request(TEXT, subwordProfile(null))));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }

  @Test
  void unknownSubwordModelIdFails() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzerWithSubwordModel().analyze(request(TEXT, subwordProfile("missing"))));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }
}
