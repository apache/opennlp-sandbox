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
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.opennlp.grpc.embedding.StubEmbeddingProvider;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CategoryAnnotation;
import org.apache.opennlp.grpc.v1.CategoryAnnotationList;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.DocumentLayers;
import org.apache.opennlp.grpc.v1.EmbeddingAnnotation;
import org.apache.opennlp.grpc.v1.EmbeddingAnnotationList;
import org.apache.opennlp.grpc.v1.LayerIdentity;
import org.apache.opennlp.grpc.v1.LayerScope;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.StandardLayer;
import org.apache.opennlp.grpc.v1.StringAnnotation;
import org.apache.opennlp.grpc.v1.StringAnnotationList;
import org.apache.opennlp.grpc.v1.SubwordAnnotation;
import org.apache.opennlp.grpc.v1.SubwordAnnotationList;
import org.apache.opennlp.grpc.v1.TermVectorAnnotation;
import org.apache.opennlp.grpc.v1.TermVectorAnnotationList;
import org.apache.opennlp.grpc.v1.TermVectorMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentLayersValidatorTest {

  private static final StubEmbeddingProvider EMBEDDINGS =
      new StubEmbeddingProvider(Map.of("mini", 3));
  /** Chapter 1 of Pride and Prejudice (public domain), a multi-paragraph regression text. */
  private static final String NOVEL = novelFixture();

  private static String novelFixture() {
    try (InputStream input = DocumentLayersValidatorTest.class
        .getResourceAsStream("/document/pride-and-prejudice-chapter-1.txt")) {
      if (input == null) {
        throw new IllegalStateException("Novel regression fixture is missing");
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Could not read novel regression fixture", e);
    }
  }

  private static AnnotationSpan span(int start, int end) {
    return AnnotationSpan.newBuilder().setStart(start).setEnd(end)
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT).build();
  }

  private static AnnotationLayer strings(
      String id, LayerScope scope, StringAnnotation... annotations) {
    return DocumentShapeAssembler.layer(id).setScope(scope)
        .setStringValues(StringAnnotationList.newBuilder().addAllAnnotations(
            java.util.List.of(annotations))).build();
  }

  private static OpenNlpDocument document(AnnotationLayer... layers) {
    return document("café", layers);
  }

  private static OpenNlpDocument document(String text, AnnotationLayer... layers) {
    return OpenNlpDocument.newBuilder().setRawText(text)
        .setLayers(DocumentLayers.newBuilder().addAllLayers(java.util.List.of(layers)))
        .build();
  }

  private static AnalysisException invalid(AnnotationLayer... layers) {
    return assertThrows(AnalysisException.class,
        () -> DocumentLayersValidator.validate(document(layers), EMBEDDINGS));
  }

  @Test
  void rejectsDuplicateLayerIds() {
    final AnnotationLayer layer = strings("opennlp:tokens", LayerScope.LAYER_SCOPE_POSITIONAL,
        StringAnnotation.newBuilder().setSpan(span(0, 4)).setValue("café").build());
    assertInternal(invalid(layer, layer));
  }

  @Test
  void rejectsMissingValueArm() {
    assertInternal(invalid(DocumentShapeAssembler.layer("opennlp:empty")
        .setScope(LayerScope.LAYER_SCOPE_DOCUMENT).build()));
  }

  @Test
  void rejectsMissingOrContradictoryLayerIdentity() {
    final StringAnnotationList values = StringAnnotationList.newBuilder()
        .addAnnotations(StringAnnotation.newBuilder().setSpan(span(0, 4)).setValue("café"))
        .build();
    assertInternal(invalid(AnnotationLayer.newBuilder()
        .setId("opennlp:tokens")
        .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
        .setStringValues(values)
        .build()));
    assertInternal(invalid(AnnotationLayer.newBuilder()
        .setId("opennlp:tokens")
        .setIdentity(LayerIdentity.newBuilder()
            .setStandard(StandardLayer.STANDARD_LAYER_SENTENCES))
        .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
        .setStringValues(values)
        .build()));
  }

  @Test
  void rejectsAValueArmThatContradictsTheStandardLayer() {
    final AnnotationLayer tokensWithEmbeddings = DocumentShapeAssembler.layer("opennlp:tokens")
        .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
        .setEmbeddingValues(EmbeddingAnnotationList.newBuilder()
            .addAnnotations(EmbeddingAnnotation.newBuilder()
                .setModelId("mini")
                .setSpan(span(0, 4))
                .addAllVector(java.util.List.of(1f, 2f, 3f))))
        .build();
    assertInternal(invalid(tokensWithEmbeddings));

    final AnnotationLayer analyticsWithStrings =
        DocumentShapeAssembler.layer("opennlp:analytics")
            .setScope(LayerScope.LAYER_SCOPE_DOCUMENT)
            .setStringValues(StringAnnotationList.newBuilder()
                .addAnnotations(StringAnnotation.newBuilder().setValue("not analytics")))
            .build();
    assertInternal(invalid(analyticsWithStrings));
  }

  @Test
  void rejectsMissingOrContradictoryTermQualifier() {
    final StringAnnotation annotation = StringAnnotation.newBuilder()
        .setSpan(span(0, 4)).setValue("café").build();
    final AnnotationLayer.Builder terms = DocumentShapeAssembler.layer("opennlp:terms:NFC")
        .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
        .setStringValues(StringAnnotationList.newBuilder().addAnnotations(annotation));
    assertInternal(invalid(terms.clone().setIdentity(LayerIdentity.newBuilder()
        .setStandard(StandardLayer.STANDARD_LAYER_TERMS)).build()));
    assertInternal(invalid(terms.clone().setIdentity(LayerIdentity.newBuilder()
        .setStandard(StandardLayer.STANDARD_LAYER_TERMS).setQualifier("CASE_FOLD")).build()));
  }

  @Test
  void rejectsScopeAndSpanMismatch() {
    assertInternal(invalid(strings("opennlp:tokens", LayerScope.LAYER_SCOPE_POSITIONAL,
        StringAnnotation.newBuilder().setValue("café").build())));
    assertInternal(invalid(strings("opennlp:language", LayerScope.LAYER_SCOPE_DOCUMENT,
        StringAnnotation.newBuilder().setSpan(span(0, 4)).setValue("fra").build())));
  }

  @Test
  void rejectsOutOfBoundsSpans() {
    assertInternal(invalid(strings("opennlp:tokens", LayerScope.LAYER_SCOPE_POSITIONAL,
        StringAnnotation.newBuilder().setSpan(span(0, 8)).setValue("bad").build())));
  }

  @Test
  void acceptsZeroWidthSubwordSpans() {
    final AnnotationLayer subwords = DocumentShapeAssembler.layer("opennlp:subwords")
        .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
        .setSubwordValues(SubwordAnnotationList.newBuilder()
            .addAnnotations(SubwordAnnotation.newBuilder()
                .setSpan(span(0, 0)).setPiece("▁").setVocabularyId(4)))
        .build();

    DocumentLayersValidator.validate(document(NOVEL, subwords), EMBEDDINGS);
  }

  @Test
  void rejectsZeroWidthSpansForOtherPositionalAnnotations() {
    assertInternal(invalid(strings("opennlp:tokens", LayerScope.LAYER_SCOPE_POSITIONAL,
        StringAnnotation.newBuilder().setSpan(span(0, 0)).setValue("Good").build())));
  }

  @Test
  void rejectsInvalidProbabilitiesAndScores() {
    assertInternal(invalid(strings("opennlp:tokens", LayerScope.LAYER_SCOPE_POSITIONAL,
        StringAnnotation.newBuilder().setSpan(span(0, 4)).setValue("café")
            .setProbability(1.1d).build())));
    assertInternal(invalid(DocumentShapeAssembler.layer("opennlp:category")
        .setScope(LayerScope.LAYER_SCOPE_DOCUMENT)
        .setCategoryValues(CategoryAnnotationList.newBuilder()
            .addAnnotations(CategoryAnnotation.newBuilder()
                .setLabel("bad").setScore(Double.NaN))).build()));
  }

  @Test
  void rejectsNonFiniteAndWrongDimensionVectors() {
    assertInternal(invalid(embeddingLayer(Float.POSITIVE_INFINITY, 0f, 0f)));
    assertInternal(invalid(embeddingLayer(1f, 2f)));
  }

  @Test
  void rejectsTermVectorShapesThatContradictTheirMode() {
    assertInternal(invalid(termVectors(TermVectorMode.TERM_VECTOR_MODE_FULL,
        TermVectorAnnotation.newBuilder().setTerm("café").setFrequency(1).build())));
    assertInternal(invalid(termVectors(TermVectorMode.TERM_VECTOR_MODE_SCORING_ONLY,
        TermVectorAnnotation.newBuilder().setTerm("café").setFrequency(1)
            .addOccurrences(span(0, 4)).build())));
  }

  @Test
  void acceptsAWellFormedLayerSet() {
    final OpenNlpDocument valid = document(
        strings("opennlp:tokens", LayerScope.LAYER_SCOPE_POSITIONAL,
            StringAnnotation.newBuilder().setSpan(span(0, 4)).setValue("café")
                .setProbability(0.9d).build()),
        embeddingLayer(1f, 2f, 3f));
    DocumentLayersValidator.validate(valid, EMBEDDINGS);
  }

  private static AnnotationLayer termVectors(
      TermVectorMode mode, TermVectorAnnotation annotation) {
    return DocumentShapeAssembler.layer("opennlp:term-vectors")
        .setScope(LayerScope.LAYER_SCOPE_DOCUMENT)
        .setTermVectorValues(TermVectorAnnotationList.newBuilder()
            .setMode(mode)
            .setSourceLayer(LayerIdentity.newBuilder()
                .setStandard(StandardLayer.STANDARD_LAYER_TOKENS))
            .addAnnotations(annotation))
        .build();
  }

  private static AnnotationLayer embeddingLayer(float... vector) {
    final EmbeddingAnnotation.Builder embedding = EmbeddingAnnotation.newBuilder()
        .setModelId("mini").setSpan(span(0, 4));
    for (float value : vector) {
      embedding.addVector(value);
    }
    return DocumentShapeAssembler.layer("opennlp:embeddings")
        .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
        .setEmbeddingValues(EmbeddingAnnotationList.newBuilder().addAnnotations(embedding))
        .build();
  }

  private static void assertInternal(AnalysisException error) {
    assertEquals(AnalysisException.FailureType.INTERNAL, error.getFailureType());
  }
}
