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

import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CategoryAnnotationList;
import org.apache.opennlp.grpc.v1.Chunk;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.ChunkGroupAnnotationList;
import org.apache.opennlp.grpc.v1.ChunkResult;
import org.apache.opennlp.grpc.v1.ChunkSpan;
import org.apache.opennlp.grpc.v1.DocumentLayers;
import org.apache.opennlp.grpc.v1.EmbeddingAnnotationList;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.EntityAnnotationList;
import org.apache.opennlp.grpc.v1.GeoAnnotationList;
import org.apache.opennlp.grpc.v1.LexicalExpansionAnnotationList;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.NormalizationAnnotationList;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.ParseNode;
import org.apache.opennlp.grpc.v1.ParseTree;
import org.apache.opennlp.grpc.v1.StemAnnotationList;
import org.apache.opennlp.grpc.v1.StringAnnotationList;
import org.apache.opennlp.grpc.v1.SubwordAnnotationList;
import org.apache.opennlp.grpc.v1.SyntacticChunkAnnotationList;
import org.apache.opennlp.grpc.v1.Token;
import org.apache.opennlp.grpc.v1.TreeAnnotation;
import org.apache.opennlp.grpc.v1.TreeAnnotationList;
import org.apache.opennlp.grpc.v1.WordTypeAnnotationList;

/**
 * Converts every span of a fully assembled document from Java UTF-16 indices to the
 * client-requested {@link OffsetEncoding}. This is the final pipeline pass, applied
 * once so individual steps never deal with offset encodings.
 */
final class DocumentOffsetEncoder {

  private DocumentOffsetEncoder() {
  }

  /**
   * Remaps all sentence, token, embedding and chunk spans in place and records the
   * chosen encoding on the document.
   */
  static void apply(OpenNlpDocument.Builder document, String rawText, OffsetEncoding requested) {
    final OffsetMapper mapper = OffsetMapper.forText(rawText, requested);
    if (document.hasNormalization() && document.getNormalization().getAlignmentCount() > 0) {
      document.setNormalization(rescaleAlignment(document.getNormalization(), mapper, requested));
    }
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence.Builder sentence = document.getSentences(i).toBuilder();
      sentence.setSentenceSpan(remap(sentence.getSentenceSpan(), mapper));
      for (int t = 0; t < sentence.getTokensCount(); t++) {
        final Token.Builder token = sentence.getTokens(t).toBuilder();
        token.setAnnotationSpan(remap(token.getAnnotationSpan(), mapper));
        sentence.setTokens(t, token.build());
      }
      for (int en = 0; en < sentence.getEntitiesCount(); en++) {
        final NamedEntity.Builder entity = sentence.getEntities(en).toBuilder()
            .setAnnotationSpan(remap(sentence.getEntities(en).getAnnotationSpan(), mapper));
        // Per-source spans (a provider's own offsets, set only when they diverge) remap too.
        for (int s = 0; s < entity.getSourcesCount(); s++) {
          if (entity.getSources(s).hasAnnotationSpan()) {
            entity.setSources(s, entity.getSources(s).toBuilder()
                .setAnnotationSpan(remap(entity.getSources(s).getAnnotationSpan(), mapper))
                .build());
          }
        }
        sentence.setEntities(en, entity.build());
      }
      if (sentence.hasParseTree() && sentence.getParseTree().hasRoot()) {
        sentence.setParseTree(remapParseTree(sentence.getParseTree(), mapper));
      }
      // Union parses (one tree per engine) carry their own structured roots; remap each.
      for (int p = 0; p < sentence.getParseTreesCount(); p++) {
        if (sentence.getParseTrees(p).hasRoot()) {
          sentence.setParseTrees(p, remapParseTree(sentence.getParseTrees(p), mapper));
        }
      }
      if (sentence.hasSyntacticChunks()) {
        final ChunkResult.Builder chunks = sentence.getSyntacticChunks().toBuilder();
        for (int c = 0; c < chunks.getChunksCount(); c++) {
          final ChunkSpan.Builder chunk = chunks.getChunks(c).toBuilder()
              .setAnnotationSpan(remap(chunks.getChunks(c).getAnnotationSpan(), mapper));
          // Per-source spans (a provider's own offsets, set only when they diverge) remap too.
          for (int s = 0; s < chunk.getSourcesCount(); s++) {
            if (chunk.getSources(s).hasAnnotationSpan()) {
              chunk.setSources(s, chunk.getSources(s).toBuilder()
                  .setAnnotationSpan(remap(chunk.getSources(s).getAnnotationSpan(), mapper))
                  .build());
            }
          }
          chunks.setChunks(c, chunk.build());
        }
        sentence.setSyntacticChunks(chunks.build());
      }
      document.setSentences(i, sentence.build());
    }
    for (int e = 0; e < document.getEmbeddingsCount(); e++) {
      final EmbeddingResult embedding = document.getEmbeddings(e);
      if (embedding.hasSourceSpan()) {
        document.setEmbeddings(e, embedding.toBuilder()
            .setSourceSpan(remap(embedding.getSourceSpan(), mapper))
            .build());
      }
    }
    for (int e = 0; e < document.getDocumentCentroidsCount(); e++) {
      final EmbeddingResult centroid = document.getDocumentCentroids(e);
      if (centroid.hasSourceSpan()) {
        document.setDocumentCentroids(e, centroid.toBuilder()
            .setSourceSpan(remap(centroid.getSourceSpan(), mapper))
            .build());
      }
    }
    for (int g = 0; g < document.getChunkEmbeddingGroupsCount(); g++) {
      final ChunkEmbeddingGroup.Builder group = document.getChunkEmbeddingGroups(g).toBuilder();
      for (int c = 0; c < group.getChunksCount(); c++) {
        final Chunk.Builder chunk = group.getChunks(c).toBuilder();
        chunk.setAnnotationSpan(remap(chunk.getAnnotationSpan(), mapper));
        for (int e = 0; e < chunk.getEmbeddingsCount(); e++) {
          final EmbeddingResult embedding = chunk.getEmbeddings(e);
          if (embedding.hasSourceSpan()) {
            chunk.setEmbeddings(e, embedding.toBuilder()
                .setSourceSpan(remap(embedding.getSourceSpan(), mapper))
                .build());
          }
        }
        group.setChunks(c, chunk.build());
      }
      for (int e = 0; e < group.getCentroidsCount(); e++) {
        final EmbeddingResult centroid = group.getCentroids(e);
        if (centroid.hasSourceSpan()) {
          group.setCentroids(e, centroid.toBuilder()
              .setSourceSpan(remap(centroid.getSourceSpan(), mapper))
              .build());
        }
      }
      document.setChunkEmbeddingGroups(g, group.build());
    }
    if (document.hasLayers()) {
      document.setLayers(remapLayers(document.getLayers(), mapper));
    }
    document.setOffsetEncoding(mapper.encoding());
  }

  /** Remaps every span in the document-shape layers into the target encoding. */
  private static DocumentLayers remapLayers(DocumentLayers layers, OffsetMapper mapper) {
    final DocumentLayers.Builder remapped = layers.toBuilder();
    for (int i = 0; i < remapped.getLayersCount(); i++) {
      final AnnotationLayer.Builder layer = remapped.getLayers(i).toBuilder();
      switch (layer.getValuesCase()) {
        case STRING_VALUES -> {
          final StringAnnotationList.Builder list = layer.getStringValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            if (list.getAnnotations(a).hasSpan()) {
              list.setAnnotations(a, list.getAnnotations(a).toBuilder()
                  .setSpan(remap(list.getAnnotations(a).getSpan(), mapper)).build());
            }
          }
          layer.setStringValues(list.build());
        }
        case CATEGORY_VALUES -> {
          final CategoryAnnotationList.Builder list = layer.getCategoryValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            if (list.getAnnotations(a).hasSpan()) {
              list.setAnnotations(a, list.getAnnotations(a).toBuilder()
                  .setSpan(remap(list.getAnnotations(a).getSpan(), mapper)).build());
            }
          }
          layer.setCategoryValues(list.build());
        }
        case EMBEDDING_VALUES -> {
          final EmbeddingAnnotationList.Builder list = layer.getEmbeddingValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            if (list.getAnnotations(a).hasSpan()) {
              list.setAnnotations(a, list.getAnnotations(a).toBuilder()
                  .setSpan(remap(list.getAnnotations(a).getSpan(), mapper)).build());
            }
          }
          layer.setEmbeddingValues(list.build());
        }
        case GEO_VALUES -> {
          final GeoAnnotationList.Builder list = layer.getGeoValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            list.setAnnotations(a, list.getAnnotations(a).toBuilder()
                .setSpan(remap(list.getAnnotations(a).getSpan(), mapper)).build());
          }
          layer.setGeoValues(list.build());
        }
        case SUBWORD_VALUES -> {
          final SubwordAnnotationList.Builder list = layer.getSubwordValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            list.setAnnotations(a, list.getAnnotations(a).toBuilder()
                .setSpan(remap(list.getAnnotations(a).getSpan(), mapper)).build());
          }
          layer.setSubwordValues(list.build());
        }
        case TREE_VALUES -> {
          final TreeAnnotationList.Builder list = layer.getTreeValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            final TreeAnnotation.Builder tree = list.getAnnotations(a).toBuilder()
                .setSpan(remap(list.getAnnotations(a).getSpan(), mapper));
            if (tree.getTree().hasRoot()) {
              tree.setTree(remapParseTree(tree.getTree(), mapper));
            }
            for (int alternative = 0; alternative < tree.getAlternativesCount(); alternative++) {
              if (tree.getAlternatives(alternative).hasRoot()) {
                tree.setAlternatives(alternative,
                    remapParseTree(tree.getAlternatives(alternative), mapper));
              }
            }
            list.setAnnotations(a, tree.build());
          }
          layer.setTreeValues(list.build());
        }
        case WORD_TYPE_VALUES -> {
          final WordTypeAnnotationList.Builder list = layer.getWordTypeValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            list.setAnnotations(a, list.getAnnotations(a).toBuilder()
                .setSpan(remap(list.getAnnotations(a).getSpan(), mapper)).build());
          }
          layer.setWordTypeValues(list.build());
        }
        case ENTITY_VALUES -> {
          final EntityAnnotationList.Builder list = layer.getEntityValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            list.setAnnotations(a, remapEntity(list.getAnnotations(a), mapper));
          }
          layer.setEntityValues(list.build());
        }
        case SYNTACTIC_CHUNK_VALUES -> {
          final SyntacticChunkAnnotationList.Builder list =
              layer.getSyntacticChunkValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            list.setAnnotations(a, remapSyntacticChunk(list.getAnnotations(a), mapper));
          }
          layer.setSyntacticChunkValues(list.build());
        }
        case STEM_VALUES -> {
          final StemAnnotationList.Builder list = layer.getStemValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            list.setAnnotations(a, list.getAnnotations(a).toBuilder()
                .setSpan(remap(list.getAnnotations(a).getSpan(), mapper)).build());
          }
          layer.setStemValues(list.build());
        }
        case LEXICAL_EXPANSION_VALUES -> {
          final LexicalExpansionAnnotationList.Builder list =
              layer.getLexicalExpansionValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            list.setAnnotations(a, list.getAnnotations(a).toBuilder()
                .setSpan(remap(list.getAnnotations(a).getSpan(), mapper)).build());
          }
          layer.setLexicalExpansionValues(list.build());
        }
        case NORMALIZATION_VALUES -> {
          final NormalizationAnnotationList.Builder list =
              layer.getNormalizationValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            list.setAnnotations(a,
                rescaleAlignment(list.getAnnotations(a), mapper, mapper.encoding()));
          }
          layer.setNormalizationValues(list.build());
        }
        case CHUNK_GROUP_VALUES -> {
          final ChunkGroupAnnotationList.Builder list = layer.getChunkGroupValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            list.setAnnotations(a, remapChunkGroup(list.getAnnotations(a), mapper));
          }
          layer.setChunkGroupValues(list.build());
        }
        case TERM_VECTOR_VALUES -> {
          final var list = layer.getTermVectorValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            final var annotation = list.getAnnotations(a).toBuilder();
            for (int occurrence = 0;
                occurrence < annotation.getOccurrencesCount(); occurrence++) {
              annotation.setOccurrences(occurrence,
                  remap(annotation.getOccurrences(occurrence), mapper));
            }
            list.setAnnotations(a, annotation.build());
          }
          layer.setTermVectorValues(list.build());
        }
        case DEPENDENCY_VALUES -> {
          final var list = layer.getDependencyValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            list.setAnnotations(a, list.getAnnotations(a).toBuilder()
                .setSpan(remap(list.getAnnotations(a).getSpan(), mapper)).build());
          }
          layer.setDependencyValues(list.build());
        }
        case RELATION_VALUES -> {
          final var list = layer.getRelationValues().toBuilder();
          for (int a = 0; a < list.getAnnotationsCount(); a++) {
            list.setAnnotations(a, list.getAnnotations(a).toBuilder()
                .setSpan(remap(list.getAnnotations(a).getSpan(), mapper)).build());
          }
          layer.setRelationValues(list.build());
        }
        default -> {
          // A layer without annotations carries no spans to remap.
        }
      }
      remapped.setLayers(i, layer.build());
    }
    return remapped.build();
  }

  /** Remaps entity. */
  private static NamedEntity remapEntity(NamedEntity entity, OffsetMapper mapper) {
    final NamedEntity.Builder remapped = entity.toBuilder()
        .setAnnotationSpan(remap(entity.getAnnotationSpan(), mapper));
    for (int source = 0; source < remapped.getSourcesCount(); source++) {
      if (remapped.getSources(source).hasAnnotationSpan()) {
        remapped.setSources(source, remapped.getSources(source).toBuilder()
            .setAnnotationSpan(remap(remapped.getSources(source).getAnnotationSpan(), mapper)));
      }
    }
    return remapped.build();
  }

  /** Remaps syntactic chunk. */
  private static ChunkSpan remapSyntacticChunk(ChunkSpan chunk, OffsetMapper mapper) {
    final ChunkSpan.Builder remapped = chunk.toBuilder()
        .setAnnotationSpan(remap(chunk.getAnnotationSpan(), mapper));
    for (int source = 0; source < remapped.getSourcesCount(); source++) {
      if (remapped.getSources(source).hasAnnotationSpan()) {
        remapped.setSources(source, remapped.getSources(source).toBuilder()
            .setAnnotationSpan(remap(remapped.getSources(source).getAnnotationSpan(), mapper)));
      }
    }
    return remapped.build();
  }

  /** Remaps chunk group. */
  private static ChunkEmbeddingGroup remapChunkGroup(
      ChunkEmbeddingGroup group, OffsetMapper mapper) {
    final ChunkEmbeddingGroup.Builder remapped = group.toBuilder();
    for (int chunkIndex = 0; chunkIndex < remapped.getChunksCount(); chunkIndex++) {
      final Chunk.Builder chunk = remapped.getChunks(chunkIndex).toBuilder()
          .setAnnotationSpan(remap(remapped.getChunks(chunkIndex).getAnnotationSpan(), mapper));
      for (int embedding = 0; embedding < chunk.getEmbeddingsCount(); embedding++) {
        chunk.setEmbeddings(embedding, chunk.getEmbeddings(embedding).toBuilder()
            .setSourceSpan(remap(chunk.getEmbeddings(embedding).getSourceSpan(), mapper)));
      }
      remapped.setChunks(chunkIndex, chunk);
    }
    for (int centroid = 0; centroid < remapped.getCentroidsCount(); centroid++) {
      remapped.setCentroids(centroid, remapped.getCentroids(centroid).toBuilder()
          .setSourceSpan(remap(remapped.getCentroids(centroid).getSourceSpan(), mapper)));
    }
    return remapped.build();
  }

  /** Remaps a parse tree's structured root (and all descendants) into the target encoding. */
  private static ParseTree remapParseTree(ParseTree tree, OffsetMapper mapper) {
    return tree.toBuilder().setRoot(remapParseNode(tree.getRoot(), mapper)).build();
  }

  /** Remaps a parse node's span and all its descendants' spans, depth-first. */
  private static ParseNode remapParseNode(ParseNode node, OffsetMapper mapper) {
    final ParseNode.Builder builder = node.toBuilder().setSpan(remap(node.getSpan(), mapper));
    for (int i = 0; i < builder.getChildrenCount(); i++) {
      builder.setChildren(i, remapParseNode(builder.getChildren(i), mapper));
    }
    return builder.build();
  }

  /** Remaps one annotation span to the requested coordinate space. */
  private static AnnotationSpan remap(AnnotationSpan span, OffsetMapper mapper) {
    return span.toBuilder()
        .setStart(mapper.toTarget(span.getStart()))
        .setEnd(mapper.toTarget(span.getEnd()))
        .build();
  }

  // Rescales NormalizationResult alignment runs from Java UTF-16 units to the requested
  // encoding. Run boundaries are exact span boundaries on both texts, so each side converts
  // through its own OffsetMapper by differencing cumulative positions.
  /** Rescales normalization alignment lengths to the requested encoding. */
  private static org.apache.opennlp.grpc.v1.NormalizationResult rescaleAlignment(
      org.apache.opennlp.grpc.v1.NormalizationResult normalization,
      OffsetMapper originalMapper,
      OffsetEncoding requested) {
    final OffsetMapper normalizedMapper =
        OffsetMapper.forText(normalization.getNormalizedText(), requested);
    final org.apache.opennlp.grpc.v1.NormalizationResult.Builder rescaled =
        normalization.toBuilder().clearAlignment();
    int originalPos = 0;
    int normalizedPos = 0;
    for (final org.apache.opennlp.grpc.v1.AlignmentRun run : normalization.getAlignmentList()) {
      final int originalEnd = originalPos + run.getOriginalUnits();
      final int normalizedEnd = normalizedPos + run.getNormalizedUnits();
      rescaled.addAlignment(run.toBuilder()
          .setOriginalUnits(
              originalMapper.toTarget(originalEnd) - originalMapper.toTarget(originalPos))
          .setNormalizedUnits(
              normalizedMapper.toTarget(normalizedEnd) - normalizedMapper.toTarget(normalizedPos))
          .build());
      originalPos = originalEnd;
      normalizedPos = normalizedEnd;
    }
    return rescaled.build();
  }
}
