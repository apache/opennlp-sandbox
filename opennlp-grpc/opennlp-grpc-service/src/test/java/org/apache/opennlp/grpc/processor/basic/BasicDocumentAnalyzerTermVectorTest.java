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

import java.util.Map;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.LayerIdentity;
import org.apache.opennlp.grpc.v1.LayerScope;
import org.apache.opennlp.grpc.v1.Normalizer;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.StandardLayer;
import org.apache.opennlp.grpc.v1.StandardTokenizerEngine;
import org.apache.opennlp.grpc.v1.StemmerAlgorithm;
import org.apache.opennlp.grpc.v1.StemmerSpec;
import org.apache.opennlp.grpc.v1.TermLayerSpec;
import org.apache.opennlp.grpc.v1.TermVectorMode;
import org.apache.opennlp.grpc.v1.TermVectorSpec;
import org.apache.opennlp.grpc.v1.TokenizerSelector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies aggregate term vectors are produced through the typed document shape. */
class BasicDocumentAnalyzerTermVectorTest {

  private final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

  @Test
  void tokenSourceProducesFullVectorsAndOriginalOffsets() {
    final AnnotationLayer layer = analyze("Dog dog Dog", baseProfile()
        .setTermVector(TermVectorSpec.getDefaultInstance()), null);

    assertEquals("opennlp:term-vectors", layer.getId());
    assertEquals(StandardLayer.STANDARD_LAYER_TERM_VECTORS,
        layer.getIdentity().getStandard());
    assertEquals(LayerScope.LAYER_SCOPE_DOCUMENT, layer.getScope());
    assertEquals(TermVectorMode.TERM_VECTOR_MODE_FULL, layer.getTermVectorValues().getMode());
    assertEquals(StandardLayer.STANDARD_LAYER_TOKENS,
        layer.getTermVectorValues().getSourceLayer().getStandard());
    assertEquals(2, layer.getTermVectorValues().getAnnotationsCount());
    final var upper = layer.getTermVectorValues().getAnnotations(0);
    assertEquals("Dog", upper.getTerm());
    assertEquals(2, upper.getFrequency());
    assertEquals(0, upper.getOccurrences(0).getStart());
    assertEquals(3, upper.getOccurrences(0).getEnd());
    assertEquals(8, upper.getOccurrences(1).getStart());
    assertEquals(11, upper.getOccurrences(1).getEnd());
  }

  @Test
  void qualifiedTermLayerCanProduceScoringOnlyFoldedVectors() {
    final LayerIdentity source = LayerIdentity.newBuilder()
        .setStandard(StandardLayer.STANDARD_LAYER_TERMS)
        .setQualifier("FULL_CASE_FOLD")
        .build();
    final AnnotationLayer layer = analyze("Groß GROSS groß", baseProfile()
        .addTermDimensions("FULL_CASE_FOLD")
        .setTermVector(TermVectorSpec.newBuilder()
            .setMode(TermVectorMode.TERM_VECTOR_MODE_SCORING_ONLY)
            .setSourceLayer(source)), null);

    assertEquals(source, layer.getTermVectorValues().getSourceLayer());
    assertEquals(TermVectorMode.TERM_VECTOR_MODE_SCORING_ONLY,
        layer.getTermVectorValues().getMode());
    assertEquals(1, layer.getTermVectorValues().getAnnotationsCount());
    assertEquals("gross", layer.getTermVectorValues().getAnnotations(0).getTerm());
    assertEquals(3, layer.getTermVectorValues().getAnnotations(0).getFrequency());
    assertEquals(0, layer.getTermVectorValues().getAnnotations(0).getOccurrencesCount());
  }

  @Test
  void stemLayerCanDefineBm25TermIdentity() {
    final AnalysisProfile.Builder profile = baseProfile()
        .addSteps(PipelineStep.PIPELINE_STEP_STEM)
        .setStemmer(StemmerSpec.newBuilder()
            .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_PORTER))
        .setTermVector(TermVectorSpec.newBuilder()
            .setSourceLayer(standard(StandardLayer.STANDARD_LAYER_STEMS)));
    final AnnotationLayer layer = analyze("running runs run", profile, null);

    assertEquals(StandardLayer.STANDARD_LAYER_STEMS,
        layer.getTermVectorValues().getSourceLayer().getStandard());
    assertEquals(1, layer.getTermVectorValues().getAnnotationsCount());
    assertEquals("run", layer.getTermVectorValues().getAnnotations(0).getTerm());
    assertEquals(3, layer.getTermVectorValues().getAnnotations(0).getFrequency());
  }

  @Test
  void occurrenceOffsetsFollowTheRequestedWireEncoding() {
    final AnnotationLayer layer = analyze("𝕏 𝕏", baseProfile()
        .setTermVector(TermVectorSpec.getDefaultInstance()),
        OffsetEncoding.OFFSET_ENCODING_UTF8_BYTE);

    final var vector = layer.getTermVectorValues().getAnnotations(0);
    assertEquals(0, vector.getOccurrences(0).getStart());
    assertEquals(4, vector.getOccurrences(0).getEnd());
    assertEquals(5, vector.getOccurrences(1).getStart());
    assertEquals(9, vector.getOccurrences(1).getEnd());
  }

  @Test
  void configurableTermLayersPreserveExactFoldedAndCasedIdentities() {
    final TermLayerSpec folded = TermLayerSpec.newBuilder()
        .setQualifier("court-folded")
        .addNormalizers(Normalizer.NORMALIZER_STRIP_INVISIBLE)
        .addNormalizers(Normalizer.NORMALIZER_WHITESPACE)
        .addNormalizers(Normalizer.NORMALIZER_FULL_CASE_FOLD)
        .addNormalizers(Normalizer.NORMALIZER_ACCENT_FOLD)
        .setStemmer(StemmerSpec.newBuilder()
            .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_PORTER))
        .build();
    final TermLayerSpec cased = TermLayerSpec.newBuilder()
        .setQualifier("court-cased")
        .setStemmer(StemmerSpec.newBuilder()
            .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_PORTER))
        .build();
    final LayerIdentity source = LayerIdentity.newBuilder()
        .setStandard(StandardLayer.STANDARD_LAYER_TERMS)
        .setQualifier("court-folded")
        .build();
    final AnalyzeDocumentResponse response = analyzer.analyze(request(
        "Groß GROSS Rodríguez RODRÍGUEZ Running running Court court",
        baseProfile()
            .addTermLayers(folded)
            .addTermLayers(cased)
            .setTermVector(TermVectorSpec.newBuilder().setSourceLayer(source)),
        OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT));

    final AnnotationLayer vectors = layer(
        response, StandardLayer.STANDARD_LAYER_TERM_VECTORS, "");
    assertEquals(source, vectors.getTermVectorValues().getSourceLayer());
    assertEquals(Map.of("gross", 2, "rodriguez", 2, "run", 2, "court", 2),
        vectors.getTermVectorValues().getAnnotationsList().stream()
            .collect(java.util.stream.Collectors.toMap(
                annotation -> annotation.getTerm(), annotation -> annotation.getFrequency())));

    final AnnotationLayer foldedLayer =
        layer(response, StandardLayer.STANDARD_LAYER_TERMS, "court-folded");
    assertEquals("opennlp:terms:court-folded", foldedLayer.getId());
    assertEquals("court-folded", foldedLayer.getIdentity().getQualifier());
    assertEquals("gross", foldedLayer.getStringValues().getAnnotations(0).getValue());
    assertEquals("gross", foldedLayer.getStringValues().getAnnotations(1).getValue());

    final AnnotationLayer casedLayer =
        layer(response, StandardLayer.STANDARD_LAYER_TERMS, "court-cased");
    assertEquals("Court", casedLayer.getStringValues().getAnnotations(6).getValue());
    assertEquals("court", casedLayer.getStringValues().getAnnotations(7).getValue());
  }

  @Test
  void configurableTermLayersDropTokensThatNormalizeToEmpty() {
    final TermLayerSpec folded = TermLayerSpec.newBuilder()
        .setQualifier("folded")
        .addNormalizers(Normalizer.NORMALIZER_STRIP_INVISIBLE)
        .build();
    final LayerIdentity source = LayerIdentity.newBuilder()
        .setStandard(StandardLayer.STANDARD_LAYER_TERMS)
        .setQualifier("folded")
        .build();
    final AnalyzeDocumentResponse response = analyzer.analyze(request(
        "court \u200B law",
        baseProfile()
            .addTermLayers(folded)
            .setTermVector(TermVectorSpec.newBuilder().setSourceLayer(source)),
        OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT));

    final AnnotationLayer terms = layer(response, StandardLayer.STANDARD_LAYER_TERMS, "folded");
    assertEquals(java.util.List.of("court", "law"),
        terms.getStringValues().getAnnotationsList().stream()
            .map(annotation -> annotation.getValue())
            .toList());

    final AnnotationLayer vectors = layer(
        response, StandardLayer.STANDARD_LAYER_TERM_VECTORS, "");
    assertEquals(java.util.List.of("court", "law"),
        vectors.getTermVectorValues().getAnnotationsList().stream()
            .map(annotation -> annotation.getTerm())
            .toList());
    assertEquals(0, vectors.getTermVectorValues().getAnnotations(0).getOccurrences(0).getStart());
    assertEquals(5, vectors.getTermVectorValues().getAnnotations(0).getOccurrences(0).getEnd());
    assertEquals(8, vectors.getTermVectorValues().getAnnotations(1).getOccurrences(0).getStart());
    assertEquals(11, vectors.getTermVectorValues().getAnnotations(1).getOccurrences(0).getEnd());
  }

  @Test
  void configurableTermLayersRejectAmbiguousIdentity() {
    final TermLayerSpec empty = TermLayerSpec.newBuilder()
        .setQualifier("court")
        .build();
    final AnalysisException noOperation = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(request("court", baseProfile()
            .addTermLayers(empty)
            .setTermVector(TermVectorSpec.getDefaultInstance()), null)));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, noOperation.getFailureType());

    final TermLayerSpec duplicate = TermLayerSpec.newBuilder()
        .setQualifier("FULL_CASE_FOLD")
        .addNormalizers(Normalizer.NORMALIZER_FULL_CASE_FOLD)
        .build();
    final AnalysisException duplicateIdentity = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(request("court", baseProfile()
            .addTermDimensions("FULL_CASE_FOLD")
            .addTermLayers(duplicate)
            .setTermVector(TermVectorSpec.getDefaultInstance()), null)));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT,
        duplicateIdentity.getFailureType());
  }

  @Test
  void rejectsMissingConfigAndUnproducedSourceLayers() {
    final AnalysisException missing = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(request("dog", baseProfile(), null)));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, missing.getFailureType());

    final AnalysisProfile.Builder stems = baseProfile()
        .setTermVector(TermVectorSpec.newBuilder()
            .setSourceLayer(standard(StandardLayer.STANDARD_LAYER_STEMS)));
    final AnalysisException absentStem = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(request("dog", stems, null)));
    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, absentStem.getFailureType());
  }

  private AnnotationLayer analyze(
      String text, AnalysisProfile.Builder profile, OffsetEncoding encoding) {
    final AnalyzeDocumentResponse response = analyzer.analyze(request(text, profile, encoding));
    return response.getDocument().getLayers().getLayersList().stream()
        .filter(layer -> layer.getIdentity().getKindCase() == LayerIdentity.KindCase.STANDARD)
        .filter(layer -> layer.getIdentity().getStandard()
            == StandardLayer.STANDARD_LAYER_TERM_VECTORS)
        .findFirst()
        .orElseThrow();
  }

  private static AnnotationLayer layer(
      AnalyzeDocumentResponse response, StandardLayer standard, String qualifier) {
    return response.getDocument().getLayers().getLayersList().stream()
        .filter(value -> value.getIdentity().getKindCase() == LayerIdentity.KindCase.STANDARD)
        .filter(value -> value.getIdentity().getStandard() == standard)
        .filter(value -> value.getIdentity().getQualifier().equals(qualifier))
        .findFirst()
        .orElseThrow();
  }

  private static AnalyzeDocumentRequest request(
      String text, AnalysisProfile.Builder profile, OffsetEncoding encoding) {
    final AnalyzeDocumentRequest.Builder request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(text))
        .setProfile(profile);
    if (encoding != null) {
      request.setOptions(AnalysisOptions.newBuilder().setOffsetEncoding(encoding));
    }
    return request.build();
  }

  private static AnalysisProfile.Builder baseProfile() {
    return AnalysisProfile.newBuilder()
        .setProfileId("term-vectors")
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSteps(PipelineStep.PIPELINE_STEP_TERM_VECTOR)
        .setTokenizer(TokenizerSelector.newBuilder()
            .setStandard(StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_WHITESPACE));
  }

  private static LayerIdentity standard(StandardLayer layer) {
    return LayerIdentity.newBuilder().setStandard(layer).build();
  }
}
