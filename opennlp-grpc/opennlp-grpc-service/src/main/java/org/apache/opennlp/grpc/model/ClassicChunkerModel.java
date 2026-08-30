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

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.spi.model.ChunkerModel;

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

  /**
   * Creates a classic chunker registration.
   *
   * @param id The logical chunker id.
   * @param chunker The initialized OpenNLP chunker.
   * @param priority The selection priority among engines serving {@code id}.
   */
  ClassicChunkerModel(String id, ChunkerME chunker, int priority) {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }
    this.id = id;
    if (chunker == null) {
      throw new IllegalArgumentException("chunker must not be null");
    }
    this.chunker = chunker;
    this.priority = priority;
  }

  /** {@inheritDoc} */
  @Override
  public String id() {
    return id;
  }

  /** {@inheritDoc} */
  @Override
  public String backendId() {
    return BACKEND_ID;
  }

  /** {@inheritDoc} */
  @Override
  public int priority() {
    return priority;
  }

  /** {@inheritDoc} */
  @Override
  public void clearThreadLocalState() {
    chunker.clearThreadLocalState();
  }

  /** {@inheritDoc} */
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
