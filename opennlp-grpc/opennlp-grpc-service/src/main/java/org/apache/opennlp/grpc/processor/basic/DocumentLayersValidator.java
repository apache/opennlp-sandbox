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

import java.util.HashSet;
import java.util.Set;

import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.Chunk;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.EmbeddingAnnotation;
import org.apache.opennlp.grpc.v1.LayerScope;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.ParseNode;
import org.apache.opennlp.grpc.v1.ParseTree;

/** Validates the completed document shape before it crosses the wire. */
final class DocumentLayersValidator {

  private DocumentLayersValidator() {
  }

  static void validate(OpenNlpDocument document, EmbeddingProvider embeddingProvider) {
    if (!document.hasLayers()) {
      return;
    }
    final Set<String> ids = new HashSet<>();
    final int tokenCount = annotationCount(document, "opennlp:tokens");
    final int entityCount = annotationCount(document, "opennlp:entities");
    for (AnnotationLayer layer : document.getLayers().getLayersList()) {
      if (layer.getId().isBlank()) {
        fail("layer id must not be blank");
      }
      if (!ids.add(layer.getId())) {
        fail("duplicate layer id '" + layer.getId() + "'");
      }
      if (!layer.hasIdentity()) {
        fail("layer '" + layer.getId() + "' has no typed identity");
      }
      final var expectedIdentity = DocumentShapeAssembler.layer(layer.getId()).getIdentity();
      if (!expectedIdentity.equals(layer.getIdentity())) {
        fail("layer '" + layer.getId() + "' has typed identity " + layer.getIdentity()
            + " but expected " + expectedIdentity);
      }
      if (layer.getScope() == LayerScope.LAYER_SCOPE_UNSPECIFIED) {
        fail("layer '" + layer.getId() + "' has unspecified scope");
      }
      if (layer.getValuesCase() == AnnotationLayer.ValuesCase.VALUES_NOT_SET) {
        fail("layer '" + layer.getId() + "' has no value case");
      }
      validateStandardValueArm(layer);
      validateLayer(layer, document.getRawText().length(), tokenCount, entityCount,
          embeddingProvider);
    }
  }

  /** Validates standard value case. */
  private static void validateStandardValueArm(AnnotationLayer layer) {
    if (layer.getIdentity().getKindCase()
        != org.apache.opennlp.grpc.v1.LayerIdentity.KindCase.STANDARD) {
      return;
    }
    final AnnotationLayer.ValuesCase expected = switch (layer.getIdentity().getStandard()) {
      case STANDARD_LAYER_SENTENCES, STANDARD_LAYER_TOKENS, STANDARD_LAYER_POS_TAGS,
          STANDARD_LAYER_LEMMAS, STANDARD_LAYER_STOPWORDS, STANDARD_LAYER_TERMS ->
          AnnotationLayer.ValuesCase.STRING_VALUES;
      case STANDARD_LAYER_ENTITIES -> AnnotationLayer.ValuesCase.ENTITY_VALUES;
      case STANDARD_LAYER_SYNTACTIC_CHUNKS ->
          AnnotationLayer.ValuesCase.SYNTACTIC_CHUNK_VALUES;
      case STANDARD_LAYER_PARSES -> AnnotationLayer.ValuesCase.TREE_VALUES;
      case STANDARD_LAYER_SENTIMENT, STANDARD_LAYER_LANGUAGE, STANDARD_LAYER_CATEGORIES ->
          AnnotationLayer.ValuesCase.CATEGORY_VALUES;
      case STANDARD_LAYER_EMBEDDINGS -> AnnotationLayer.ValuesCase.EMBEDDING_VALUES;
      case STANDARD_LAYER_WORD_TYPES -> AnnotationLayer.ValuesCase.WORD_TYPE_VALUES;
      case STANDARD_LAYER_SUBWORDS -> AnnotationLayer.ValuesCase.SUBWORD_VALUES;
      case STANDARD_LAYER_STEMS -> AnnotationLayer.ValuesCase.STEM_VALUES;
      case STANDARD_LAYER_EXPANSIONS -> AnnotationLayer.ValuesCase.LEXICAL_EXPANSION_VALUES;
      case STANDARD_LAYER_GEO -> AnnotationLayer.ValuesCase.GEO_VALUES;
      case STANDARD_LAYER_NORMALIZATION -> AnnotationLayer.ValuesCase.NORMALIZATION_VALUES;
      case STANDARD_LAYER_ANALYTICS -> AnnotationLayer.ValuesCase.ANALYTICS_VALUES;
      case STANDARD_LAYER_CHUNK_GROUPS -> AnnotationLayer.ValuesCase.CHUNK_GROUP_VALUES;
      case STANDARD_LAYER_TERM_VECTORS -> AnnotationLayer.ValuesCase.TERM_VECTOR_VALUES;
      case STANDARD_LAYER_DEPENDENCIES -> AnnotationLayer.ValuesCase.DEPENDENCY_VALUES;
      case STANDARD_LAYER_RELATIONS -> AnnotationLayer.ValuesCase.RELATION_VALUES;
      case STANDARD_LAYER_UNSPECIFIED, UNRECOGNIZED -> {
        fail("standard layer identity must name a recognized layer");
        yield AnnotationLayer.ValuesCase.VALUES_NOT_SET;
      }
    };
    if (layer.getValuesCase() != expected) {
      fail("layer '" + layer.getId() + "' uses " + layer.getValuesCase()
          + " but its standard identity requires " + expected);
    }
  }

  /** Validates layer. */
  private static void validateLayer(
      AnnotationLayer layer, int textLength, int tokenCount, int entityCount,
      EmbeddingProvider embeddingProvider) {
    final boolean positional = layer.getScope() == LayerScope.LAYER_SCOPE_POSITIONAL;
    switch (layer.getValuesCase()) {
      case STRING_VALUES -> layer.getStringValues().getAnnotationsList().forEach(annotation -> {
        validateOptionalSpan(annotation.hasSpan(), annotation.getSpan(), positional, textLength);
        nonBlank(annotation.getValue(), "string annotation value");
        if (annotation.hasProbability()) {
          probability(annotation.getProbability(), "string annotation probability");
        }
      });
      case CATEGORY_VALUES -> layer.getCategoryValues().getAnnotationsList().forEach(annotation -> {
        validateOptionalSpan(annotation.hasSpan(), annotation.getSpan(), positional, textLength);
        nonBlank(annotation.getLabel(), "category label");
        probability(annotation.getScore(), "category score");
      });
      case EMBEDDING_VALUES -> layer.getEmbeddingValues().getAnnotationsList().forEach(annotation ->
          validateEmbedding(annotation, positional, textLength, embeddingProvider));
      case TREE_VALUES -> layer.getTreeValues().getAnnotationsList().forEach(annotation -> {
        requireSpan(annotation.getSpan(), positional, textLength);
        validateParseTree(annotation.getTree(), textLength);
        annotation.getAlternativesList().forEach(tree -> validateParseTree(tree, textLength));
      });
      case SUBWORD_VALUES -> layer.getSubwordValues().getAnnotationsList().forEach(annotation ->
          requireSubwordSpan(annotation.getSpan(), positional, textLength));
      case GEO_VALUES -> layer.getGeoValues().getAnnotationsList().forEach(annotation -> {
        requireSpan(annotation.getSpan(), positional, textLength);
        probability(annotation.getResolution().getConfidence(), "geocode confidence");
        finite(annotation.getResolution().getLatitude(), "latitude");
        finite(annotation.getResolution().getLongitude(), "longitude");
      });
      case WORD_TYPE_VALUES -> layer.getWordTypeValues().getAnnotationsList().forEach(annotation ->
          requireSpan(annotation.getSpan(), positional, textLength));
      case ENTITY_VALUES -> layer.getEntityValues().getAnnotationsList().forEach(entity -> {
        requireSpan(entity.getAnnotationSpan(), positional, textLength);
        nonBlank(entity.getEntityType(), "entity type");
        if (entity.hasProbability()) {
          probability(entity.getProbability(), "entity probability");
        }
        entity.getSourcesList().forEach(source -> {
          if (source.hasProbability()) {
            probability(source.getProbability(), "entity source probability");
          }
          if (source.hasAnnotationSpan()) {
            span(source.getAnnotationSpan(), textLength);
          }
        });
      });
      case SYNTACTIC_CHUNK_VALUES -> layer.getSyntacticChunkValues().getAnnotationsList()
          .forEach(chunk -> {
            requireSpan(chunk.getAnnotationSpan(), positional, textLength);
            nonBlank(chunk.getChunkTag(), "syntactic chunk tag");
            chunk.getSourcesList().forEach(source -> {
              if (source.hasAnnotationSpan()) {
                span(source.getAnnotationSpan(), textLength);
              }
            });
          });
      case STEM_VALUES -> layer.getStemValues().getAnnotationsList().forEach(annotation -> {
        requireSpan(annotation.getSpan(), positional, textLength);
        nonBlank(annotation.getStem(), "stem");
      });
      case LEXICAL_EXPANSION_VALUES -> layer.getLexicalExpansionValues().getAnnotationsList()
          .forEach(annotation -> {
            requireSpan(annotation.getSpan(), positional, textLength);
            nonBlank(annotation.getTerm(), "lexical expansion term");
            nonBlank(annotation.getLexiconId(), "lexical expansion lexicon id");
            probability(annotation.getWeight(), "lexical expansion weight");
          });
      case NORMALIZATION_VALUES -> requireDocumentScope(layer);
      case ANALYTICS_VALUES -> {
        requireDocumentScope(layer);
        layer.getAnalyticsValues().getAnnotationsList().forEach(analytics -> {
          probability(analytics.getNounDensity(), "noun density");
          probability(analytics.getVerbDensity(), "verb density");
          probability(analytics.getAdjectiveDensity(), "adjective density");
          probability(analytics.getAdverbDensity(), "adverb density");
          probability(analytics.getContentWordRatio(), "content word ratio");
          probability(analytics.getLexicalDensity(), "lexical density");
        });
      }
      case CHUNK_GROUP_VALUES -> {
        requireDocumentScope(layer);
        layer.getChunkGroupValues().getAnnotationsList().forEach(
            group -> validateChunkGroup(group, textLength, embeddingProvider));
      }
      case TERM_VECTOR_VALUES -> {
        requireDocumentScope(layer);
        final var values = layer.getTermVectorValues();
        if (!values.hasSourceLayer()) {
          fail("term-vector layer has no source identity");
        }
        if (values.getMode()
            == org.apache.opennlp.grpc.v1.TermVectorMode.TERM_VECTOR_MODE_UNSPECIFIED
            || values.getMode() == org.apache.opennlp.grpc.v1.TermVectorMode.UNRECOGNIZED) {
          fail("term-vector layer has no recognized mode");
        }
        values.getAnnotationsList().forEach(annotation -> {
          if (annotation.getFrequency() < 1) {
            fail("term-vector frequency must be at least one");
          }
          final int occurrences = annotation.getOccurrencesCount();
          final boolean full = values.getMode()
              == org.apache.opennlp.grpc.v1.TermVectorMode.TERM_VECTOR_MODE_FULL;
          if ((full && occurrences != annotation.getFrequency())
              || (!full && occurrences != 0)) {
            fail("term-vector occurrences contradict the selected mode");
          }
          annotation.getOccurrencesList().forEach(span -> span(span, textLength));
        });
      }
      case DEPENDENCY_VALUES -> {
        final var values = layer.getDependencyValues();
        nonBlank(values.getParserId(), "dependency parser id");
        nonBlank(values.getEngine(), "dependency parser engine");
        if (values.getAnnotationsCount() != tokenCount) {
          fail("dependency layer must contain one arc per token");
        }
        final boolean[] dependents = new boolean[tokenCount];
        values.getAnnotationsList().forEach(annotation -> {
          requireSpan(annotation.getSpan(), positional, textLength);
          nonBlank(annotation.getRelation(), "dependency relation");
          final int dependent = annotation.getDependentTokenIndex();
          final int head = annotation.getHeadTokenIndex();
          if (dependent < 0 || dependent >= tokenCount || dependents[dependent]) {
            fail("dependency layer has an invalid or duplicate dependent index " + dependent);
          }
          if (head < -1 || head >= tokenCount || head == dependent) {
            fail("dependency layer has invalid head index " + head);
          }
          dependents[dependent] = true;
        });
      }
      case RELATION_VALUES -> layer.getRelationValues().getAnnotationsList()
          .forEach(annotation -> {
            requireSpan(annotation.getSpan(), positional, textLength);
            nonBlank(annotation.getType(), "relation type");
            final int subject = annotation.getSubjectEntityIndex();
            final int object = annotation.getObjectEntityIndex();
            if (subject < 0 || subject >= entityCount || object < 0 || object >= entityCount
                || subject == object) {
              fail("relation layer has invalid entity indexes " + subject + ", " + object);
            }
          });
      case VALUES_NOT_SET -> fail("layer value case is missing");
    }
  }

  /** Counts annotations in the named layer, or returns zero when it is absent. */
  private static int annotationCount(OpenNlpDocument document, String id) {
    for (AnnotationLayer layer : document.getLayers().getLayersList()) {
      if (id.equals(layer.getId())) {
        return switch (layer.getValuesCase()) {
          case STRING_VALUES -> layer.getStringValues().getAnnotationsCount();
          case ENTITY_VALUES -> layer.getEntityValues().getAnnotationsCount();
          default -> 0;
        };
      }
    }
    return 0;
  }

  /** Validates embedding. */
  private static void validateEmbedding(
      EmbeddingAnnotation annotation,
      boolean positional,
      int textLength,
      EmbeddingProvider embeddingProvider) {
    final boolean documentVector = annotation.getGranularity()
        == org.apache.opennlp.grpc.v1.EmbeddingGranularity.EMBEDDING_GRANULARITY_DOCUMENT;
    if (documentVector) {
      if (annotation.hasSpan()) {
        span(annotation.getSpan(), textLength);
      }
    } else {
      validateOptionalSpan(annotation.hasSpan(), annotation.getSpan(), positional, textLength);
    }
    validateVector(annotation.getModelId(), annotation.getVectorList(), embeddingProvider);
    validateVectorNormalization(
        annotation.getVectorNormalization(), annotation.getVectorList());
  }

  /** Validates chunk group. */
  private static void validateChunkGroup(
      ChunkEmbeddingGroup group, int textLength, EmbeddingProvider embeddingProvider) {
    nonBlank(group.getGroupId(), "chunk group id");
    for (Chunk chunk : group.getChunksList()) {
      span(chunk.getAnnotationSpan(), textLength);
      chunk.getEmbeddingsList().forEach(embedding -> {
        span(embedding.getSourceSpan(), textLength);
        validateVector(embedding.getModelId(), embedding.getVectorList(), embeddingProvider);
        validateVectorNormalization(
            embedding.getVectorNormalization(), embedding.getVectorList());
      });
    }
    group.getCentroidsList().forEach(centroid -> {
      if (centroid.hasSourceSpan()) {
        span(centroid.getSourceSpan(), textLength);
      }
      validateVector(centroid.getModelId(), centroid.getVectorList(), embeddingProvider);
      validateVectorNormalization(
          centroid.getVectorNormalization(), centroid.getVectorList());
    });
  }

  /** Validates vector normalization. */
  private static void validateVectorNormalization(
      org.apache.opennlp.grpc.v1.VectorNormalization normalization,
      java.util.List<Float> vector) {
    switch (normalization) {
      case VECTOR_NORMALIZATION_UNSPECIFIED, VECTOR_NORMALIZATION_NONE -> {
        // Backend vectors can leave normalization unspecified. NONE is the
        // explicit provenance for an unnormalized service-side centroid.
      }
      case VECTOR_NORMALIZATION_L2 -> {
        double squaredNorm = 0.0d;
        for (float value : vector) {
          squaredNorm += value * value;
        }
        if (Math.abs(Math.sqrt(squaredNorm) - 1.0d) > 1.0e-5d) {
          fail("embedding marked L2-normalized does not have unit norm");
        }
      }
      case UNRECOGNIZED -> fail("embedding vector normalization is not recognized");
    }
  }

  /** Validates vector. */
  private static void validateVector(
      String modelId, java.util.List<Float> vector, EmbeddingProvider embeddingProvider) {
    nonBlank(modelId, "embedding model id");
    if (!embeddingProvider.supportsModel(modelId)) {
      fail("embedding layer names unknown model '" + modelId + "'");
    }
    final int dimension = embeddingProvider.embeddingDimension(modelId);
    if (vector.size() != dimension) {
      fail("embedding model '" + modelId + "' requires dimension " + dimension
          + " but layer vector has " + vector.size());
    }
    for (Float value : vector) {
      if (value == null || !Float.isFinite(value)) {
        fail("embedding vector contains a non-finite value");
      }
    }
  }

  /** Validates parse tree. */
  private static void validateParseTree(ParseTree tree, int textLength) {
    if (tree.hasRoot()) {
      validateParseNode(tree.getRoot(), textLength);
    }
  }

  /** Validates parse node. */
  private static void validateParseNode(ParseNode node, int textLength) {
    span(node.getSpan(), textLength);
    if (node.hasProbability()) {
      probability(node.getProbability(), "parse node probability");
    }
    node.getChildrenList().forEach(child -> validateParseNode(child, textLength));
  }

  /** Validates optional span. */
  private static void validateOptionalSpan(
      boolean hasSpan, AnnotationSpan annotationSpan, boolean positional, int textLength) {
    if (positional != hasSpan) {
      fail(positional ? "positional annotation has no span"
          : "document annotation unexpectedly has a span");
    }
    if (hasSpan) {
      span(annotationSpan, textLength);
    }
  }

  /** Returns a required span after validating its bounds and coordinate space. */
  private static void requireSpan(
      AnnotationSpan annotationSpan, boolean positional, int textLength) {
    if (!positional) {
      fail("span-bearing annotation is in a document-scoped layer");
    }
    span(annotationSpan, textLength);
  }

  /** Validates a subword span, which may cover no source characters. */
  private static void requireSubwordSpan(
      AnnotationSpan annotationSpan, boolean positional, int textLength) {
    if (!positional) {
      fail("span-bearing annotation is in a document-scoped layer");
    }
    if (annotationSpan.getStart() < 0 || annotationSpan.getEnd() < annotationSpan.getStart()
        || annotationSpan.getEnd() > textLength) {
      fail("invalid subword annotation span [" + annotationSpan.getStart() + ","
          + annotationSpan.getEnd() + ") for text length " + textLength);
    }
  }

  /** Converts a document-container span to the wire value. */
  private static void span(AnnotationSpan span, int textLength) {
    if (span.getStart() < 0 || span.getEnd() <= span.getStart() || span.getEnd() > textLength) {
      fail("invalid annotation span [" + span.getStart() + "," + span.getEnd()
          + ") for text length " + textLength);
    }
  }

  /** Validates that a layer contains one document-scoped annotation. */
  private static void requireDocumentScope(AnnotationLayer layer) {
    if (layer.getScope() != LayerScope.LAYER_SCOPE_DOCUMENT) {
      fail("layer '" + layer.getId() + "' must be document-scoped");
    }
  }

  /** Validates a probability in the inclusive unit interval. */
  private static void probability(double value, String name) {
    if (!Double.isFinite(value) || value < 0d || value > 1d) {
      fail(name + " must be finite and in [0,1]");
    }
  }

  /** Validates a finite numeric value. */
  private static void finite(double value, String name) {
    if (!Double.isFinite(value)) {
      fail(name + " must be finite");
    }
  }

  /** Validates a required non-blank string. */
  private static void nonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      fail(name + " must not be blank");
    }
  }

  /** Raises a document-layer invariant violation. */
  private static void fail(String message) {
    throw AnalysisException.internal("Invalid document shape: " + message, null);
  }
}
