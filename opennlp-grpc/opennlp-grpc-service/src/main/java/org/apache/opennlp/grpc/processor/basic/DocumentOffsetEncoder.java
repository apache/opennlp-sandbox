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
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.Chunk;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.ChunkResult;
import org.apache.opennlp.grpc.v1.ChunkSpan;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.ParseNode;
import org.apache.opennlp.grpc.v1.ParseTree;
import org.apache.opennlp.grpc.v1.Token;

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
      document.setEmbeddings(e, embedding.toBuilder()
          .setSourceSpan(remap(embedding.getSourceSpan(), mapper))
          .build());
    }
    for (int e = 0; e < document.getDocumentCentroidsCount(); e++) {
      document.setDocumentCentroids(e, document.getDocumentCentroids(e).toBuilder()
          .setSourceSpan(remap(document.getDocumentCentroids(e).getSourceSpan(), mapper))
          .build());
    }
    for (int g = 0; g < document.getChunkEmbeddingGroupsCount(); g++) {
      final ChunkEmbeddingGroup.Builder group = document.getChunkEmbeddingGroups(g).toBuilder();
      for (int c = 0; c < group.getChunksCount(); c++) {
        final Chunk.Builder chunk = group.getChunks(c).toBuilder();
        chunk.setAnnotationSpan(remap(chunk.getAnnotationSpan(), mapper));
        for (int e = 0; e < chunk.getEmbeddingsCount(); e++) {
          final EmbeddingResult embedding = chunk.getEmbeddings(e);
          chunk.setEmbeddings(e, embedding.toBuilder()
              .setSourceSpan(remap(embedding.getSourceSpan(), mapper))
              .build());
        }
        group.setChunks(c, chunk.build());
      }
      for (int e = 0; e < group.getCentroidsCount(); e++) {
        group.setCentroids(e, group.getCentroids(e).toBuilder()
            .setSourceSpan(remap(group.getCentroids(e).getSourceSpan(), mapper))
            .build());
      }
      document.setChunkEmbeddingGroups(g, group.build());
    }
    document.setOffsetEncoding(mapper.encoding());
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

  private static AnnotationSpan remap(AnnotationSpan span, OffsetMapper mapper) {
    return span.toBuilder()
        .setStart(mapper.toTarget(span.getStart()))
        .setEnd(mapper.toTarget(span.getEnd()))
        .build();
  }

  // Rescales NormalizationResult alignment runs from Java UTF-16 units to the requested
  // encoding. Run boundaries are exact span boundaries on both texts, so each side converts
  // through its own OffsetMapper by differencing cumulative positions.
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
