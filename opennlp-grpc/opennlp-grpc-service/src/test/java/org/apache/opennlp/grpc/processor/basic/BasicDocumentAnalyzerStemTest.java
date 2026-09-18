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
import java.nio.file.Files;
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
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.StemmerAlgorithm;
import org.apache.opennlp.grpc.v1.StemmerSpec;
import org.apache.opennlp.grpc.v1.StemAnnotation;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies PIPELINE_STEP_STEM: per-token stems rendered as the {@code opennlp:stems}
 * document-shape layer, across the snowball, minimal, and hunspell stemmer families.
 * The hunspell dictionary is a project-authored miniature fixture written by the test.
 */
class BasicDocumentAnalyzerStemTest {

  private static final String TEXT = "The cats sat on the mats.";

  @TempDir
  static Path dictionaryDir;

  private static Path affixPath;
  private static Path wordsPath;

  private final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(
      ProfileRegistry.createDefault(), new ModelBundleCache(Map.of()));

  @BeforeAll
  static void writeHunspellFixture() throws IOException {
    affixPath = dictionaryDir.resolve("tiny.aff");
    wordsPath = dictionaryDir.resolve("tiny.dic");
    Files.writeString(affixPath, String.join("\n",
        "SET UTF-8",
        "SFX S Y 1",
        "SFX S 0 s ."));
    Files.writeString(wordsPath, String.join("\n",
        "2",
        "cat/S",
        "mat/S"));
  }

  private static BasicDocumentAnalyzer analyzerWithHunspell() {
    return new BasicDocumentAnalyzer(ProfileRegistry.createDefault(), new ModelBundleCache(Map.of(
        "model.hunspell.tiny.affix_path", affixPath.toString(),
        "model.hunspell.tiny.dictionary_path", wordsPath.toString())));
  }

  private static AnalyzeDocumentRequest request(StemmerSpec spec, PipelineStep... steps) {
    final AnalysisProfile.Builder profile = AnalysisProfile.newBuilder().setProfileId("stems");
    for (PipelineStep step : steps) {
      profile.addSteps(step);
    }
    profile.setStemmer(spec);
    return AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
        .setProfile(profile.build())
        .build();
  }

  private static AnalyzeDocumentRequest fullRequest(StemmerSpec spec) {
    return request(spec,
        PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
        PipelineStep.PIPELINE_STEP_TOKENIZE,
        PipelineStep.PIPELINE_STEP_STEM);
  }

  private static Optional<AnnotationLayer> stemsLayer(AnalyzeDocumentResponse response) {
    return response.getDocument().getLayers().getLayersList().stream()
        .filter(l -> "opennlp:stems".equals(l.getId()))
        .findFirst();
  }

  private static List<Token> allTokens(AnalyzeDocumentResponse response) {
    final List<Token> tokens = new ArrayList<>();
    for (AnnotatedSentence sentence : response.getDocument().getSentencesList()) {
      tokens.addAll(sentence.getTokensList());
    }
    return tokens;
  }

  @Test
  void snowballStemsAlignWithTokens() {
    final AnalyzeDocumentResponse response = analyzer.analyze(fullRequest(
        StemmerSpec.newBuilder()
            .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_SNOWBALL)
            .setLanguage("en")
            .build()));

    final Optional<AnnotationLayer> found = stemsLayer(response);
    assertTrue(found.isPresent(), "opennlp:stems layer is missing");
    final AnnotationLayer stems = found.get();
    assertEquals(LayerScope.LAYER_SCOPE_POSITIONAL, stems.getScope());

    final List<Token> tokens = allTokens(response);
    assertEquals(tokens.size(), stems.getStemValues().getAnnotationsCount());
    for (int t = 0; t < tokens.size(); t++) {
      final StemAnnotation annotation = stems.getStemValues().getAnnotations(t);
      assertEquals(tokens.get(t).getAnnotationSpan().getStart(),
          annotation.getSpan().getStart(), "stem layer is not token-aligned");
      assertEquals(StemmerAlgorithm.STEMMER_ALGORITHM_SNOWBALL, annotation.getAlgorithm());
      assertEquals("en", annotation.getLanguage());
    }
    // "cats" is the second token; snowball English reduces it to "cat".
    assertEquals("cats", tokens.get(1).getText());
    assertEquals("cat", stems.getStemValues().getAnnotations(1).getStem());
  }

  @Test
  void minimalEnglishTierStems() {
    final AnalyzeDocumentResponse response = analyzer.analyze(fullRequest(
        StemmerSpec.newBuilder()
            .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_MINIMAL)
            .setLanguage("en")
            .build()));

    final AnnotationLayer stems = stemsLayer(response).orElseThrow();
    assertEquals("cat", stems.getStemValues().getAnnotations(1).getStem());
    assertEquals(StemmerAlgorithm.STEMMER_ALGORITHM_MINIMAL,
        stems.getStemValues().getAnnotations(1).getAlgorithm());
  }

  @Test
  void hunspellStemsAgainstTheConfiguredDictionary() {
    final AnalyzeDocumentResponse response = analyzerWithHunspell().analyze(fullRequest(
        StemmerSpec.newBuilder()
            .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_HUNSPELL)
            .build()));

    final AnnotationLayer stems = stemsLayer(response).orElseThrow();
    final List<Token> tokens = allTokens(response);
    assertEquals(tokens.size(), stems.getStemValues().getAnnotationsCount());
    assertEquals("cats", tokens.get(1).getText());
    assertEquals("cat", stems.getStemValues().getAnnotations(1).getStem());
    assertEquals(StemmerAlgorithm.STEMMER_ALGORITHM_HUNSPELL,
        stems.getStemValues().getAnnotations(1).getAlgorithm());
  }

  @Test
  void stemWithoutTokenizeFails() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(request(
            StemmerSpec.newBuilder().setLanguage("en").build(),
            PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
            PipelineStep.PIPELINE_STEP_STEM)));
    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, error.getFailureType());
  }

  @Test
  void snowballWithoutLanguageFails() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(fullRequest(StemmerSpec.getDefaultInstance())));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void unsupportedSnowballLanguageFails() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(fullRequest(StemmerSpec.newBuilder()
            .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_SNOWBALL)
            .setLanguage("zz")
            .build())));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }

  @Test
  void hunspellWithoutConfiguredDictionaryFails() {
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(fullRequest(StemmerSpec.newBuilder()
            .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_HUNSPELL)
            .build())));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }
}
