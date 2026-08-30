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

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import opennlp.tools.termvector.TermVector;
import opennlp.tools.termvector.TermVectorAnnotator;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.LayerIdentity;
import org.apache.opennlp.grpc.v1.LayerScope;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.StandardLayer;
import org.apache.opennlp.grpc.v1.StemAnnotation;
import org.apache.opennlp.grpc.v1.TermVectorAnnotation;
import org.apache.opennlp.grpc.v1.TermVectorAnnotationList;
import org.apache.opennlp.grpc.v1.TermVectorMode;
import org.apache.opennlp.grpc.v1.TermVectorSpec;
import org.apache.opennlp.grpc.v1.Token;

/** Runs the OpenNLP term-vector annotator over a selected document layer. */
final class TermVectorStepRunner {

  private TermVectorStepRunner() {
  }

  static AnnotationLayer aggregate(
      String rawText,
      OpenNlpDocument.Builder document,
      List<AnnotationLayer> extraLayers,
      TermVectorSpec spec) {
    final LayerIdentity source = sourceIdentity(spec);
    final List<Annotation<String>> values = sourceValues(document, extraLayers, source);
    final Document sourceDocument = Document.of(rawText).with(Layers.TOKENS, values);
    final TermVectorMode mode = resolvedMode(spec);
    final TermVectorAnnotator.Mode libraryMode = mode == TermVectorMode.TERM_VECTOR_MODE_FULL
        ? TermVectorAnnotator.Mode.FULL : TermVectorAnnotator.Mode.SCORING_ONLY;
    final Document result = new TermVectorAnnotator(libraryMode).annotate(sourceDocument);
    final TermVectorAnnotationList.Builder rendered = TermVectorAnnotationList.newBuilder()
        .setMode(mode)
        .setSourceLayer(source);
    for (Annotation<TermVector> annotation : result.get(TermVectorAnnotator.TERM_VECTORS)) {
      final TermVector vector = annotation.value();
      final TermVectorAnnotation.Builder value = TermVectorAnnotation.newBuilder()
          .setTerm(vector.term())
          .setFrequency(vector.frequency());
      for (Span occurrence : vector.spans()) {
        value.addOccurrences(span(occurrence));
      }
      rendered.addAnnotations(value);
    }
    return DocumentShapeAssembler.layer(DocumentShapeAssembler.TERM_VECTORS_ID)
        .setScope(LayerScope.LAYER_SCOPE_DOCUMENT)
        .setTermVectorValues(rendered)
        .build();
  }

  static LayerIdentity sourceIdentity(TermVectorSpec spec) {
    if (!spec.hasSourceLayer()) {
      return standard(StandardLayer.STANDARD_LAYER_TOKENS);
    }
    final LayerIdentity source = spec.getSourceLayer();
    if (source.getKindCase() == LayerIdentity.KindCase.CUSTOM) {
      if (source.getCustom().isBlank()) {
        throw AnalysisException.invalidArgument(
            "term_vector.source_layer custom id must not be blank");
      }
      throw AnalysisException.unimplemented(
          "term-vector source layer '" + source.getCustom() + "' is not implemented");
    }
    if (source.getKindCase() != LayerIdentity.KindCase.STANDARD) {
      throw AnalysisException.invalidArgument(
          "term_vector.source_layer must select a standard or custom layer");
    }
    return switch (source.getStandard()) {
      case STANDARD_LAYER_TOKENS, STANDARD_LAYER_LEMMAS, STANDARD_LAYER_STEMS -> {
        if (source.hasQualifier()) {
          throw AnalysisException.invalidArgument(
              "term_vector.source_layer qualifier is only valid for STANDARD_LAYER_TERMS");
        }
        yield source;
      }
      case STANDARD_LAYER_TERMS -> {
        if (!source.hasQualifier() || source.getQualifier().isBlank()) {
          throw AnalysisException.invalidArgument(
              "STANDARD_LAYER_TERMS requires a non-blank Dimension qualifier");
        }
        yield source;
      }
      case STANDARD_LAYER_UNSPECIFIED, UNRECOGNIZED ->
          throw AnalysisException.invalidArgument(
              "term_vector.source_layer standard value must be recognized");
      default -> throw AnalysisException.invalidArgument(
          "term-vector source must be TOKENS, LEMMAS, STEMS, or TERMS");
    };
  }

  static TermVectorMode resolvedMode(TermVectorSpec spec) {
    return switch (spec.getMode()) {
      case TERM_VECTOR_MODE_UNSPECIFIED, TERM_VECTOR_MODE_FULL ->
          TermVectorMode.TERM_VECTOR_MODE_FULL;
      case TERM_VECTOR_MODE_SCORING_ONLY -> TermVectorMode.TERM_VECTOR_MODE_SCORING_ONLY;
      case UNRECOGNIZED -> throw AnalysisException.invalidArgument(
          "term_vector.mode must be recognized");
    };
  }

  /** Returns annotations from the selected source layer. */
  private static List<Annotation<String>> sourceValues(
      OpenNlpDocument.Builder document,
      List<AnnotationLayer> extraLayers,
      LayerIdentity source) {
    return switch (source.getStandard()) {
      case STANDARD_LAYER_TOKENS -> tokenValues(document, SourceValue.TOKEN, null);
      case STANDARD_LAYER_LEMMAS -> tokenValues(document, SourceValue.LEMMA, null);
      case STANDARD_LAYER_TERMS ->
          tokenValues(document, SourceValue.TERM, source.getQualifier());
      case STANDARD_LAYER_STEMS -> stemValues(extraLayers);
      default -> throw new IllegalStateException("validated term-vector source changed");
    };
  }

  /** Returns token annotations for term-vector aggregation. */
  private static List<Annotation<String>> tokenValues(
      OpenNlpDocument.Builder document, SourceValue source, String qualifier) {
    final List<Annotation<String>> values = new ArrayList<>();
    for (AnnotatedSentence sentence : document.getSentencesList()) {
      for (Token token : sentence.getTokensList()) {
        if (source == SourceValue.TERM && !token.containsTermLayers(qualifier)) {
          continue;
        }
        final String value = switch (source) {
          case TOKEN -> token.getText();
          case LEMMA -> {
            if (!token.hasLemma()) {
              throw AnalysisException.failedPrecondition(
                  "term-vector lemma source requires PIPELINE_STEP_LEMMATIZE");
            }
            yield token.getLemma();
          }
          case TERM -> token.getTermLayersOrThrow(qualifier);
        };
        if (!value.isEmpty()) {
          values.add(annotation(token.getAnnotationSpan(), value));
        }
      }
    }
    return values;
  }

  /** Returns stem annotations from produced layers. */
  private static List<Annotation<String>> stemValues(List<AnnotationLayer> extraLayers) {
    for (AnnotationLayer layer : extraLayers) {
      if (layer.getIdentity().getKindCase() == LayerIdentity.KindCase.STANDARD
          && layer.getIdentity().getStandard() == StandardLayer.STANDARD_LAYER_STEMS) {
        final List<Annotation<String>> values = new ArrayList<>();
        for (StemAnnotation stem : layer.getStemValues().getAnnotationsList()) {
          values.add(annotation(stem.getSpan(), stem.getStem()));
        }
        return values;
      }
    }
    throw AnalysisException.failedPrecondition(
        "term-vector stem source requires PIPELINE_STEP_STEM");
  }

  /** Builds a document-container annotation. */
  private static Annotation<String> annotation(AnnotationSpan span, String value) {
    return new Annotation<>(new Span(span.getStart(), span.getEnd()), value);
  }

  /** Converts a document-container span to the wire value. */
  private static AnnotationSpan span(Span span) {
    return AnnotationSpan.newBuilder()
        .setStart(span.getStart())
        .setEnd(span.getEnd())
        .setSpace(org.apache.opennlp.grpc.v1.CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }

  /** Builds a standard layer identity. */
  private static LayerIdentity standard(StandardLayer layer) {
    return LayerIdentity.newBuilder().setStandard(layer).build();
  }

  private enum SourceValue {
    TOKEN,
    LEMMA,
    TERM
  }
}
