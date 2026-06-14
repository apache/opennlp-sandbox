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
    for (int i = 0; i < document.getSentencesCount(); i++) {
      final AnnotatedSentence.Builder sentence = document.getSentences(i).toBuilder();
      sentence.setSentenceSpan(remap(sentence.getSentenceSpan(), mapper));
      for (int t = 0; t < sentence.getTokensCount(); t++) {
        final Token.Builder token = sentence.getTokens(t).toBuilder();
        token.setAnnotationSpan(remap(token.getAnnotationSpan(), mapper));
        sentence.setTokens(t, token.build());
      }
      for (int en = 0; en < sentence.getEntitiesCount(); en++) {
        final NamedEntity entity = sentence.getEntities(en);
        sentence.setEntities(en, entity.toBuilder()
            .setAnnotationSpan(remap(entity.getAnnotationSpan(), mapper))
            .build());
      }
      if (sentence.hasParseTree() && sentence.getParseTree().hasRoot()) {
        final ParseTree.Builder tree = sentence.getParseTree().toBuilder();
        tree.setRoot(remapParseNode(tree.getRoot(), mapper));
        sentence.setParseTree(tree.build());
      }
      document.setSentences(i, sentence.build());
    }
    for (int e = 0; e < document.getEmbeddingsCount(); e++) {
      final EmbeddingResult embedding = document.getEmbeddings(e);
      document.setEmbeddings(e, embedding.toBuilder()
          .setSourceSpan(remap(embedding.getSourceSpan(), mapper))
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
      document.setChunkEmbeddingGroups(g, group.build());
    }
    document.setOffsetEncoding(mapper.encoding());
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
}
