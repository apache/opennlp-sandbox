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

import java.util.Optional;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkResult;
import org.apache.opennlp.grpc.v1.ChunkSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.DocumentClassification;
import org.apache.opennlp.grpc.v1.DocumentAnalytics;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.LayerScope;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.NormalizationResult;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.ParseNode;
import org.apache.opennlp.grpc.v1.ParseNodeKind;
import org.apache.opennlp.grpc.v1.ParseTree;
import org.apache.opennlp.grpc.v1.StandardLayer;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the document-shape rendering over hand-built analysis state, covering
 * the layers whose producing steps need heavyweight model fixtures in the analyzer
 * tests (sentiment, classification, parses, syntactic chunks) and the container-backed
 * validation.
 */
class DocumentShapeAssemblerTest {

  private static final String TEXT = "Alpha beta. Gamma delta.";

  private static AnnotationSpan span(int start, int end) {
    return AnnotationSpan.newBuilder()
        .setStart(start)
        .setEnd(end)
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }

  private static OpenNlpDocument.Builder twoSentences() {
    return OpenNlpDocument.newBuilder()
        .setRawText(TEXT)
        .addSentences(AnnotatedSentence.newBuilder().setSentenceSpan(span(0, 11)))
        .addSentences(AnnotatedSentence.newBuilder().setSentenceSpan(span(12, 24)));
  }

  private static Optional<AnnotationLayer> layer(OpenNlpDocument.Builder document, String id) {
    return document.getLayers().getLayersList().stream()
        .filter(l -> id.equals(l.getId()))
        .findFirst();
  }

  @Test
  void sentimentRendersScoredLabelsOnSentenceSpans() {
    final OpenNlpDocument.Builder document = twoSentences();
    document.setSentences(0, document.getSentences(0).toBuilder()
        .setSentimentLabel("positive").setSentimentConfidence(0.9f).build());
    document.setSentences(1, document.getSentences(1).toBuilder()
        .setSentimentLabel("negative").setSentimentConfidence(0.6f).build());

    DocumentShapeAssembler.apply(document, TEXT);

    final AnnotationLayer sentiment =
        layer(document, DocumentShapeAssembler.SENTIMENT_ID).orElseThrow();
    assertEquals(LayerScope.LAYER_SCOPE_POSITIONAL, sentiment.getScope());
    assertEquals(2, sentiment.getCategoryValues().getAnnotationsCount());
    assertEquals("positive", sentiment.getCategoryValues().getAnnotations(0).getLabel());
    assertEquals(0.9d, sentiment.getCategoryValues().getAnnotations(0).getScore(), 1e-6);
    assertEquals(0, sentiment.getCategoryValues().getAnnotations(0).getSpan().getStart());
    assertEquals(11, sentiment.getCategoryValues().getAnnotations(0).getSpan().getEnd());
  }

  @Test
  void categoriesRenderTheWholeDistributionBestFirst() {
    final OpenNlpDocument.Builder document = twoSentences()
        .setClassification(DocumentClassification.newBuilder()
            .setBestCategory("sports")
            .putCategoryScores("politics", 0.3d)
            .putCategoryScores("sports", 0.5d)
            .putCategoryScores("weather", 0.2d)
            .build());

    DocumentShapeAssembler.apply(document, TEXT);

    final AnnotationLayer categories =
        layer(document, DocumentShapeAssembler.CATEGORIES_ID).orElseThrow();
    assertEquals(LayerScope.LAYER_SCOPE_DOCUMENT, categories.getScope());
    assertEquals(3, categories.getCategoryValues().getAnnotationsCount());
    assertEquals("sports", categories.getCategoryValues().getAnnotations(0).getLabel());
    assertEquals("politics", categories.getCategoryValues().getAnnotations(1).getLabel());
    assertEquals("weather", categories.getCategoryValues().getAnnotations(2).getLabel());
    assertFalse(categories.getCategoryValues().getAnnotations(0).hasSpan());
  }

  @Test
  void parsesRetainPrimaryAndAllEngineAlternatives() {
    final ParseTree tree = ParseTree.newBuilder()
        .setParserId("default")
        .setEngine("opennlp-me")
        .setRoot(ParseNode.newBuilder()
            .setLabel("TOP")
            .setSpan(span(0, 11))
            .setKind(ParseNodeKind.PARSE_NODE_KIND_NONTERMINAL)
            .build())
        .build();
    final OpenNlpDocument.Builder document = twoSentences();
    final ParseTree alternative = tree.toBuilder()
        .setParserId("alternate")
        .setEngine("onnx")
        .build();
    document.setSentences(0, document.getSentences(0).toBuilder()
        .setParseTree(tree)
        .addParseTrees(tree)
        .addParseTrees(alternative)
        .build());

    DocumentShapeAssembler.apply(document, TEXT);

    final AnnotationLayer parses =
        layer(document, DocumentShapeAssembler.PARSES_ID).orElseThrow();
    assertEquals(1, parses.getTreeValues().getAnnotationsCount());
    assertEquals(0, parses.getTreeValues().getAnnotations(0).getSpan().getStart());
    assertEquals("TOP", parses.getTreeValues().getAnnotations(0).getTree().getRoot().getLabel());
    assertEquals(2, parses.getTreeValues().getAnnotations(0).getAlternativesCount());
    assertEquals("alternate",
        parses.getTreeValues().getAnnotations(0).getAlternatives(1).getParserId());
  }

  @Test
  void syntacticChunksRenderTheirPhraseTags() {
    final OpenNlpDocument.Builder document = twoSentences();
    document.setSentences(0, document.getSentences(0).toBuilder()
        .setSyntacticChunks(ChunkResult.newBuilder()
            .addChunks(ChunkSpan.newBuilder()
                .setAnnotationSpan(span(0, 10))
                .setChunkTag("NP")
                .setText("Alpha beta")
                .build())
            .build())
        .build());

    DocumentShapeAssembler.apply(document, TEXT);

    final AnnotationLayer chunks =
        layer(document, DocumentShapeAssembler.CHUNKS.id()).orElseThrow();
    assertEquals(1, chunks.getSyntacticChunkValues().getAnnotationsCount());
    assertEquals("NP", chunks.getSyntacticChunkValues().getAnnotations(0).getChunkTag());
    assertEquals(10, chunks.getSyntacticChunkValues().getAnnotations(0)
        .getAnnotationSpan().getEnd());
  }

  @Test
  void documentLevelFirstClassResultsEachHaveATypedLayer() {
    final OpenNlpDocument.Builder document = twoSentences()
        .setAnalytics(DocumentAnalytics.newBuilder()
            .setTotalSentences(2).setTotalTokens(4).build())
        .setNormalization(NormalizationResult.newBuilder()
            .setNormalizedText(TEXT.toLowerCase(java.util.Locale.ROOT)).build())
        .addChunkEmbeddingGroups(ChunkEmbeddingGroup.newBuilder()
            .setGroupId("sentences").build());

    DocumentShapeAssembler.apply(document, TEXT);

    final AnnotationLayer analytics = layer(document, "opennlp:analytics").orElseThrow();
    assertEquals(LayerScope.LAYER_SCOPE_DOCUMENT, analytics.getScope());
    assertEquals(4, analytics.getAnalyticsValues().getAnnotations(0).getTotalTokens());
    final AnnotationLayer normalization =
        layer(document, "opennlp:normalization").orElseThrow();
    assertEquals(TEXT.toLowerCase(java.util.Locale.ROOT),
        normalization.getNormalizationValues().getAnnotations(0).getNormalizedText());
    final AnnotationLayer groups = layer(document, "opennlp:chunk-groups").orElseThrow();
    assertEquals("sentences", groups.getChunkGroupValues().getAnnotations(0).getGroupId());
  }

  @Test
  void spanlessCentroidStaysSpanlessInTheEmbeddingLayer() {
    final OpenNlpDocument.Builder document = twoSentences()
        .addDocumentCentroids(EmbeddingResult.newBuilder()
            .setModelId("minilm")
            .addVector(1.0f)
            .setGranularity(EmbeddingGranularity.EMBEDDING_GRANULARITY_DOCUMENT)
            .build());

    DocumentShapeAssembler.apply(document, TEXT);

    final AnnotationLayer embeddings =
        layer(document, DocumentShapeAssembler.EMBEDDINGS_ID).orElseThrow();
    assertEquals(1, embeddings.getEmbeddingValues().getAnnotationsCount());
    assertFalse(embeddings.getEmbeddingValues().getAnnotations(0).hasSpan());
  }

  @Test
  void noLayersFieldWhenNothingWasProduced() {
    final OpenNlpDocument.Builder document = OpenNlpDocument.newBuilder().setRawText(TEXT);

    DocumentShapeAssembler.apply(document, TEXT);

    assertFalse(document.hasLayers());
  }

  @Test
  void containerValidationRejectsAnOutOfBoundsSpan() {
    final OpenNlpDocument.Builder document = OpenNlpDocument.newBuilder()
        .setRawText(TEXT)
        .addSentences(AnnotatedSentence.newBuilder()
            .setSentenceSpan(span(0, TEXT.length() + 5)));

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> DocumentShapeAssembler.apply(document, TEXT));
    assertEquals(AnalysisException.FailureType.INTERNAL, error.getFailureType());
  }

  @Test
  void layerOrderFollowsThePipeline() {
    final OpenNlpDocument.Builder document = twoSentences()
        .setDetectedLanguage("eng")
        .setLanguageConfidence(0.8f);

    DocumentShapeAssembler.apply(document, TEXT);

    assertEquals(2, document.getLayers().getLayersCount());
    assertEquals("opennlp:sentences", document.getLayers().getLayers(0).getId());
    assertEquals("opennlp:language", document.getLayers().getLayers(1).getId());
    assertTrue(document.getLayers().getLayers(1).getCategoryValues().getAnnotationsCount() > 0);
  }

  @Test
  void builtInLayersCarryTypedIdentityAndTermDimensionsUseAQualifier() {
    final OpenNlpDocument.Builder document = OpenNlpDocument.newBuilder()
        .setRawText("Alpha")
        .addSentences(AnnotatedSentence.newBuilder()
            .setSentenceSpan(span(0, 5))
            .addTokens(Token.newBuilder()
                .setText("Alpha")
                .setAnnotationSpan(span(0, 5))
                .putTermLayers("FULL_CASE_FOLD", "alpha")));

    DocumentShapeAssembler.apply(document, "Alpha");

    final AnnotationLayer sentences = layer(document, "opennlp:sentences").orElseThrow();
    assertEquals(StandardLayer.STANDARD_LAYER_SENTENCES,
        sentences.getIdentity().getStandard());
    assertFalse(sentences.getIdentity().hasQualifier());

    final AnnotationLayer terms =
        layer(document, "opennlp:terms:FULL_CASE_FOLD").orElseThrow();
    assertEquals(StandardLayer.STANDARD_LAYER_TERMS, terms.getIdentity().getStandard());
    assertEquals("FULL_CASE_FOLD", terms.getIdentity().getQualifier());
  }
}
