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

import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.processor.AnalysisException;
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
    for (AnnotationLayer layer : document.getLayers().getLayersList()) {
      if (layer.getId().isBlank()) {
        fail("layer id must not be blank");
      }
      if (!ids.add(layer.getId())) {
        fail("duplicate layer id '" + layer.getId() + "'");
      }
      if (layer.getScope() == LayerScope.LAYER_SCOPE_UNSPECIFIED) {
        fail("layer '" + layer.getId() + "' has unspecified scope");
      }
      if (layer.getValuesCase() == AnnotationLayer.ValuesCase.VALUES_NOT_SET) {
        fail("layer '" + layer.getId() + "' has no value arm");
      }
      validateLayer(layer, document.getRawText().length(), embeddingProvider);
    }
  }

  private static void validateLayer(
      AnnotationLayer layer, int textLength, EmbeddingProvider embeddingProvider) {
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
          requireSpan(annotation.getSpan(), positional, textLength));
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
      case VALUES_NOT_SET -> fail("layer value arm is missing");
    }
  }

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
  }

  private static void validateChunkGroup(
      ChunkEmbeddingGroup group, int textLength, EmbeddingProvider embeddingProvider) {
    nonBlank(group.getGroupId(), "chunk group id");
    for (Chunk chunk : group.getChunksList()) {
      span(chunk.getAnnotationSpan(), textLength);
      chunk.getEmbeddingsList().forEach(embedding -> {
        span(embedding.getSourceSpan(), textLength);
        validateVector(embedding.getModelId(), embedding.getVectorList(), embeddingProvider);
      });
    }
    group.getCentroidsList().forEach(centroid -> {
      if (centroid.hasSourceSpan()) {
        span(centroid.getSourceSpan(), textLength);
      }
      validateVector(centroid.getModelId(), centroid.getVectorList(), embeddingProvider);
    });
  }

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

  private static void validateParseTree(ParseTree tree, int textLength) {
    if (tree.hasRoot()) {
      validateParseNode(tree.getRoot(), textLength);
    }
  }

  private static void validateParseNode(ParseNode node, int textLength) {
    span(node.getSpan(), textLength);
    if (node.hasProbability()) {
      probability(node.getProbability(), "parse node probability");
    }
    node.getChildrenList().forEach(child -> validateParseNode(child, textLength));
  }

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

  private static void requireSpan(
      AnnotationSpan annotationSpan, boolean positional, int textLength) {
    if (!positional) {
      fail("span-bearing annotation is in a document-scoped layer");
    }
    span(annotationSpan, textLength);
  }

  private static void span(AnnotationSpan span, int textLength) {
    if (span.getStart() < 0 || span.getEnd() <= span.getStart() || span.getEnd() > textLength) {
      fail("invalid annotation span [" + span.getStart() + "," + span.getEnd()
          + ") for text length " + textLength);
    }
  }

  private static void requireDocumentScope(AnnotationLayer layer) {
    if (layer.getScope() != LayerScope.LAYER_SCOPE_DOCUMENT) {
      fail("layer '" + layer.getId() + "' must be document-scoped");
    }
  }

  private static void probability(double value, String name) {
    if (!Double.isFinite(value) || value < 0d || value > 1d) {
      fail(name + " must be finite and in [0,1]");
    }
  }

  private static void finite(double value, String name) {
    if (!Double.isFinite(value)) {
      fail(name + " must be finite");
    }
  }

  private static void nonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      fail(name + " must not be blank");
    }
  }

  private static void fail(String message) {
    throw AnalysisException.internal("Invalid document shape: " + message, null);
  }
}
