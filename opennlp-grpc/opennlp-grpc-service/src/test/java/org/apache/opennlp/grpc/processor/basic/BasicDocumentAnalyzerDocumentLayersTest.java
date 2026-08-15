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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.opennlp.grpc.embedding.StubEmbeddingProvider;
import org.apache.opennlp.grpc.model.ClassicNerBackendFactory;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.testing.TinyNerModel;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.LayerScope;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.StringAnnotation;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the document-shape rendering of analysis results (OPENNLP-1888): every
 * produced annotation appears once more as a typed layer under
 * {@code OpenNlpDocument.layers}, span-consistent with the classic fields.
 */
class BasicDocumentAnalyzerDocumentLayersTest {

  private static final String TEXT = "The cats sat on the mats. They were happy animals.";

  @TempDir
  static Path modelDir;

  private static Path personModelPath;

  private final ModelBundleCache modelBundleCache = new ModelBundleCache(Map.of());
  private final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(
      ProfileRegistry.createDefault(), modelBundleCache);

  @BeforeAll
  static void trainPersonModel() throws IOException {
    personModelPath = TinyNerModel.trainPersonModel(modelDir.resolve("person-ner.bin"));
  }

  private static AnalyzeDocumentRequest request(
      String text, boolean includeProbabilities, PipelineStep... steps) {
    final AnalysisProfile.Builder profile = AnalysisProfile.newBuilder().setProfileId("layers");
    for (PipelineStep step : steps) {
      profile.addSteps(step);
    }
    return AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(text).build())
        .setProfile(profile.build())
        .setOptions(AnalysisOptions.newBuilder()
            .setIncludeProbabilities(includeProbabilities)
            .build())
        .build();
  }

  private static Optional<AnnotationLayer> layer(AnalyzeDocumentResponse response, String id) {
    return response.getDocument().getLayers().getLayersList().stream()
        .filter(l -> id.equals(l.getId()))
        .findFirst();
  }

  private static AnnotationLayer requireLayer(AnalyzeDocumentResponse response, String id) {
    final Optional<AnnotationLayer> found = layer(response, id);
    assertTrue(found.isPresent(), "layer '" + id + "' is missing");
    return found.get();
  }

  @Test
  void coreLayersMirrorTheClassicResults() {
    final AnalyzeDocumentResponse response = analyzer.analyze(request(TEXT, true,
        PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
        PipelineStep.PIPELINE_STEP_TOKENIZE,
        PipelineStep.PIPELINE_STEP_POS_TAG,
        PipelineStep.PIPELINE_STEP_LEMMATIZE));

    assertTrue(response.getDocument().hasLayers(), "document carries no layers");

    final AnnotationLayer sentences = requireLayer(response, "opennlp:sentences");
    assertEquals(LayerScope.LAYER_SCOPE_POSITIONAL, sentences.getScope());
    assertEquals(response.getDocument().getSentencesCount(),
        sentences.getStringValues().getAnnotationsCount());
    for (int i = 0; i < sentences.getStringValues().getAnnotationsCount(); i++) {
      final StringAnnotation annotation = sentences.getStringValues().getAnnotations(i);
      final AnnotatedSentence sentence = response.getDocument().getSentences(i);
      assertEquals(sentence.getSentenceSpan().getStart(), annotation.getSpan().getStart());
      assertEquals(sentence.getSentenceSpan().getEnd(), annotation.getSpan().getEnd());
      assertEquals(TEXT.substring(annotation.getSpan().getStart(), annotation.getSpan().getEnd()),
          annotation.getValue());
    }

    final List<Token> allTokens = new ArrayList<>();
    for (AnnotatedSentence sentence : response.getDocument().getSentencesList()) {
      allTokens.addAll(sentence.getTokensList());
    }

    final AnnotationLayer tokens = requireLayer(response, "opennlp:tokens");
    assertEquals(allTokens.size(), tokens.getStringValues().getAnnotationsCount());
    for (int t = 0; t < allTokens.size(); t++) {
      final StringAnnotation annotation = tokens.getStringValues().getAnnotations(t);
      assertEquals(allTokens.get(t).getText(), annotation.getValue());
      assertEquals(allTokens.get(t).getAnnotationSpan().getStart(),
          annotation.getSpan().getStart());
      assertEquals(allTokens.get(t).getAnnotationSpan().getEnd(),
          annotation.getSpan().getEnd());
    }

    final AnnotationLayer pos = requireLayer(response, "opennlp:pos");
    assertEquals(allTokens.size(), pos.getStringValues().getAnnotationsCount());
    assertEquals("DET", pos.getStringValues().getAnnotations(0).getValue());
    assertEquals("NOUN", pos.getStringValues().getAnnotations(1).getValue());
    for (int t = 0; t < allTokens.size(); t++) {
      final StringAnnotation annotation = pos.getStringValues().getAnnotations(t);
      assertEquals(allTokens.get(t).getAnnotationSpan().getStart(),
          annotation.getSpan().getStart(), "pos layer is not token-aligned");
      assertTrue(annotation.getProbability() > 0.0d,
          "pos annotation carries no probability although requested");
    }

    final AnnotationLayer lemmas = requireLayer(response, "opennlp:lemmas");
    assertEquals(allTokens.size(), lemmas.getStringValues().getAnnotationsCount());
    assertEquals("cat", lemmas.getStringValues().getAnnotations(1).getValue());
  }

  @Test
  void languageLayerIsDocumentScoped() {
    final AnalyzeDocumentResponse response = analyzer.analyze(request(TEXT, false,
        PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT));

    final AnnotationLayer language = requireLayer(response, "opennlp:language");
    assertEquals(LayerScope.LAYER_SCOPE_DOCUMENT, language.getScope());
    assertEquals(1, language.getCategoryValues().getAnnotationsCount());
    assertEquals(response.getDocument().getDetectedLanguage(),
        language.getCategoryValues().getAnnotations(0).getLabel());
    assertTrue(language.getCategoryValues().getAnnotations(0).getScore() > 0.0d);
    assertFalse(language.getCategoryValues().getAnnotations(0).hasSpan(),
        "document-scoped annotation must not carry a span");
  }

  @Test
  void entitiesLayerCarriesTypeAndSpan() {
    final String text =
        "Pierre Vinken , 61 years old , will join the board as a nonexecutive director Nov. 29 . "
            + "Mr . Vinken is chairman of Elsevier N.V. , the Dutch publishing group .";
    final ModelBundleCache cache = new ModelBundleCache(Map.of(
        ClassicNerBackendFactory.KEY_PREFIX + "person" + ClassicNerBackendFactory.KEY_SUFFIX,
        personModelPath.toString()));
    final BasicDocumentAnalyzer nerAnalyzer =
        new BasicDocumentAnalyzer(ProfileRegistry.createDefault(), cache);

    final AnalyzeDocumentResponse response = nerAnalyzer.analyze(request(text, false,
        PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
        PipelineStep.PIPELINE_STEP_TOKENIZE,
        PipelineStep.PIPELINE_STEP_NER));

    int entityCount = 0;
    for (AnnotatedSentence sentence : response.getDocument().getSentencesList()) {
      entityCount += sentence.getEntitiesCount();
    }
    assertTrue(entityCount > 0, "fixture produced no entities");

    final AnnotationLayer entities = requireLayer(response, "opennlp:entities");
    assertEquals(entityCount, entities.getStringValues().getAnnotationsCount());
    for (StringAnnotation annotation : entities.getStringValues().getAnnotationsList()) {
      assertEquals("person", annotation.getValue());
      assertTrue(annotation.getSpan().getEnd() > annotation.getSpan().getStart());
    }
  }

  @Test
  void embeddingsLayerCarriesVectorsAndGranularity() {
    final StubEmbeddingProvider embeddingProvider = new StubEmbeddingProvider(Map.of("minilm", 4));
    final BasicDocumentAnalyzer embedAnalyzer = new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(), modelBundleCache, embeddingProvider);

    final AnalyzeDocumentResponse response = embedAnalyzer.analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder()
                .setRawText("One sentence. Two sentences!").build())
            .setProfile(AnalysisProfile.newBuilder()
                .setProfileId("with-embed")
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
                .addSteps(PipelineStep.PIPELINE_STEP_EMBED)
                .build())
            .setOptions(AnalysisOptions.newBuilder().setEmbeddingModelId("minilm").build())
            .build());

    final AnnotationLayer embeddings = requireLayer(response, "opennlp:embeddings");
    assertEquals(LayerScope.LAYER_SCOPE_POSITIONAL, embeddings.getScope());
    // Two sentence vectors plus the document centroid.
    assertEquals(3, embeddings.getEmbeddingValues().getAnnotationsCount());
    int sentenceVectors = 0;
    int centroids = 0;
    for (var annotation : embeddings.getEmbeddingValues().getAnnotationsList()) {
      assertEquals("minilm", annotation.getModelId());
      assertEquals(4, annotation.getVectorCount());
      if (annotation.getGranularity() == EmbeddingGranularity.EMBEDDING_GRANULARITY_SENTENCE) {
        sentenceVectors++;
      } else if (annotation.getGranularity()
          == EmbeddingGranularity.EMBEDDING_GRANULARITY_DOCUMENT) {
        centroids++;
      }
    }
    assertEquals(2, sentenceVectors);
    assertEquals(1, centroids);
  }

  @Test
  void layersOmitStepsThatDidNotRun() {
    final AnalyzeDocumentResponse response = analyzer.analyze(request(TEXT, false,
        PipelineStep.PIPELINE_STEP_SENTENCE_DETECT));

    assertTrue(layer(response, "opennlp:sentences").isPresent());
    assertTrue(layer(response, "opennlp:tokens").isEmpty());
    assertTrue(layer(response, "opennlp:pos").isEmpty());
    assertTrue(layer(response, "opennlp:lemmas").isEmpty());
    assertTrue(layer(response, "opennlp:entities").isEmpty());
    assertTrue(layer(response, "opennlp:embeddings").isEmpty());
  }

  @Test
  void layerSpansFollowTheRequestedOffsetEncoding() {
    final String text = "The café was open. It closed.";
    final AnalyzeDocumentResponse response = analyzer.analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(text).build())
            .setProfile(AnalysisProfile.newBuilder()
                .setProfileId("layers")
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
                .build())
            .setOptions(AnalysisOptions.newBuilder()
                .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE)
                .build())
            .build());

    final byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
    final AnnotationLayer tokens = requireLayer(response, "opennlp:tokens");
    assertTrue(tokens.getStringValues().getAnnotationsCount() > 0);
    for (StringAnnotation annotation : tokens.getStringValues().getAnnotationsList()) {
      final String sliced = new String(utf8,
          annotation.getSpan().getStart(),
          annotation.getSpan().getEnd() - annotation.getSpan().getStart(),
          StandardCharsets.UTF_8);
      assertEquals(annotation.getValue(), sliced,
          "layer span does not slice the UTF-8 bytes to the annotated value");
    }
  }
}
