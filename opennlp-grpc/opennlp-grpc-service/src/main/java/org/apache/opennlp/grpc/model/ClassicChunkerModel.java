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
package org.apache.opennlp.grpc.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;

/**
 * A {@link ChunkerModel} backed by a classic OpenNLP {@link ChunkerME}. {@code ChunkerME} is
 * {@code @ThreadSafe} (per-thread state over a shared, immutable model), so one instance is shared
 * across requests.
 */
final class ClassicChunkerModel implements ChunkerModel {

  /** Backend id reported for chunkers served by the classic OpenNLP maxent runtime. */
  static final String BACKEND_ID = "opennlp-me";

  private final String id;
  private final ChunkerME chunker;
  private final int priority;

  ClassicChunkerModel(String id, ChunkerME chunker, int priority) {
    this.id = Objects.requireNonNull(id, "id");
    this.chunker = Objects.requireNonNull(chunker, "chunker");
    this.priority = priority;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public String backendId() {
    return BACKEND_ID;
  }

  @Override
  public int priority() {
    return priority;
  }

  @Override
  public List<ChunkSpan> chunk(AnnotatedSentence sentence) {
    if (sentence.getTokensCount() == 0) {
      return List.of();
    }
    final String[] tokens = new String[sentence.getTokensCount()];
    final String[] posTags = new String[sentence.getTokensCount()];
    for (int t = 0; t < tokens.length; t++) {
      tokens[t] = sentence.getTokens(t).getText();
      posTags[t] = sentence.getTokens(t).getPosTag();
    }
    return toChunkSpans(chunker.chunkAsSpans(tokens, posTags), sentence);
  }

  /**
   * Maps the chunker's token-index spans to document-span {@link ChunkSpan}s: each chunk covers
   * tokens {@code [span.getStart(), span.getEnd())}, so its document span runs from the first
   * token's start to the last token's end, with the chunk tag as {@code chunk_tag}.
   *
   * @param spans The chunker's token-index spans.
   * @param sentence The sentence whose tokens carry the document offsets.
   *
   * @return One document-span chunk per input span (without provenance/text, added downstream).
   */
  static List<ChunkSpan> toChunkSpans(Span[] spans, AnnotatedSentence sentence) {
    final List<ChunkSpan> chunks = new ArrayList<>(spans.length);
    for (Span span : spans) {
      final AnnotationSpan first = sentence.getTokens(span.getStart()).getAnnotationSpan();
      final AnnotationSpan last = sentence.getTokens(span.getEnd() - 1).getAnnotationSpan();
      chunks.add(ChunkSpan.newBuilder()
          .setChunkTag(span.getType())
          .setAnnotationSpan(AnnotationSpan.newBuilder()
              .setStart(first.getStart())
              .setEnd(last.getEnd())
              .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
              .build())
          .build());
    }
    return chunks;
  }
}
