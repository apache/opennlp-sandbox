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

import opennlp.tools.depparse.DependencyAnnotator;
import opennlp.tools.depparse.DependencyArc;
import opennlp.tools.depparse.DependencyParser;
import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import opennlp.tools.relation.RelationAnnotator;
import opennlp.tools.relation.RelationMention;
import opennlp.tools.relation.RelationPattern;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.DependencyAnnotation;
import org.apache.opennlp.grpc.v1.DependencyAnnotationList;
import org.apache.opennlp.grpc.v1.LayerScope;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.RelationAnnotation;
import org.apache.opennlp.grpc.v1.RelationAnnotationList;
import org.apache.opennlp.grpc.v1.RelationPatternSpec;

/**
 * Adapts the helper stack's dependency and relation document layers to their typed
 * gRPC document-shape representations.
 */
final class LinguisticGraphRenderer {

  private static final String ENGINE_ID = "opennlp-me";

  private LinguisticGraphRenderer() {
  }

  /** Parses the document and returns both the validated container and its wire layer. */
  static DependencyResult parse(
      OpenNlpDocument wireDocument, DependencyParser parser, String parserId) {
    final Document parsed = new DependencyAnnotator(parser).annotate(container(wireDocument));
    final DependencyAnnotationList.Builder values = DependencyAnnotationList.newBuilder()
        .setParserId(parserId)
        .setEngine(ENGINE_ID);
    for (Annotation<DependencyArc> annotation : parsed.get(DependencyAnnotator.DEPENDENCIES)) {
      values.addAnnotations(DependencyAnnotation.newBuilder()
          .setSpan(wireSpan(annotation.span()))
          .setHeadTokenIndex(annotation.value().head())
          .setDependentTokenIndex(annotation.value().dependent())
          .setRelation(annotation.value().relation()));
    }
    final AnnotationLayer layer = DocumentShapeAssembler.layer("opennlp:dependencies")
        .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
        .setDependencyValues(values)
        .build();
    return new DependencyResult(parsed, layer);
  }

  /** Extracts relations over an already dependency-parsed document. */
  static AnnotationLayer relations(Document parsed, List<RelationPatternSpec> specifications) {
    final List<RelationPattern> patterns = new ArrayList<>(specifications.size());
    for (RelationPatternSpec specification : specifications) {
      patterns.add(new RelationPattern(
          specification.getType(),
          specification.getPath(),
          specification.hasTrigger() ? specification.getTrigger() : null));
    }
    final Document related = new RelationAnnotator(patterns).annotate(parsed);
    final RelationAnnotationList.Builder values = RelationAnnotationList.newBuilder();
    for (Annotation<RelationMention> annotation : related.get(RelationAnnotator.RELATIONS)) {
      values.addAnnotations(RelationAnnotation.newBuilder()
          .setSpan(wireSpan(annotation.span()))
          .setType(annotation.value().type())
          .setSubjectEntityIndex(annotation.value().subject())
          .setObjectEntityIndex(annotation.value().object()));
    }
    return DocumentShapeAssembler.layer("opennlp:relations")
        .setScope(LayerScope.LAYER_SCOPE_POSITIONAL)
        .setRelationValues(values)
        .build();
  }

  /** Rebuilds the helper stack's four required base layers from the wire document. */
  private static Document container(OpenNlpDocument wireDocument) {
    final String text = wireDocument.getRawText();
    final List<Annotation<String>> sentences = new ArrayList<>();
    final List<Annotation<String>> tokens = new ArrayList<>();
    final List<Annotation<String>> tags = new ArrayList<>();
    final List<Annotation<String>> entities = new ArrayList<>();
    for (AnnotatedSentence sentence : wireDocument.getSentencesList()) {
      final Span sentenceSpan = span(sentence.getSentenceSpan());
      sentences.add(new Annotation<>(sentenceSpan,
          text.substring(sentenceSpan.getStart(), sentenceSpan.getEnd())));
      sentence.getTokensList().forEach(token -> {
        final Span tokenSpan = span(token.getAnnotationSpan());
        tokens.add(new Annotation<>(tokenSpan, token.getText()));
        if (token.hasPosTag()) {
          tags.add(new Annotation<>(tokenSpan, token.getPosTag()));
        }
      });
      sentence.getEntitiesList().forEach(entity -> entities.add(
          new Annotation<>(span(entity.getAnnotationSpan()), entity.getEntityType())));
    }
    return Document.of(text)
        .with(Layers.SENTENCES, sentences)
        .with(Layers.TOKENS, tokens)
        .with(Layers.POS_TAGS, tags)
        .with(Layers.ENTITIES, entities);
  }

  /** Converts a wire span to the helper document coordinate type. */
  private static Span span(AnnotationSpan span) {
    return new Span(span.getStart(), span.getEnd());
  }

  /** Converts a helper document span to the wire coordinate type. */
  private static AnnotationSpan wireSpan(Span span) {
    return AnnotationSpan.newBuilder()
        .setStart(span.getStart())
        .setEnd(span.getEnd())
        .setSpace(org.apache.opennlp.grpc.v1.CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }

  /** Dependency output plus the container used by relation extraction. */
  record DependencyResult(Document document, AnnotationLayer layer) {
  }
}
