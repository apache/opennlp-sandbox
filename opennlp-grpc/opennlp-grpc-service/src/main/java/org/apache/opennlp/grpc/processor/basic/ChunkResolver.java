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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.backend.RankedBackends;
import org.apache.opennlp.grpc.backend.RankedBackends.Registration;
import org.apache.opennlp.grpc.model.ChunkerModel;
import org.apache.opennlp.grpc.model.ChunkerRegistry;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkResult;
import org.apache.opennlp.grpc.v1.ChunkSource;
import org.apache.opennlp.grpc.v1.ChunkSpan;
import org.apache.opennlp.grpc.v1.MergeStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies a chunker engine policy to one sentence at a time, the syntactic-chunking analogue of
 * {@link NerEntityResolver}: it decides which engine(s) run for each configured chunker, attaches
 * provenance ({@link ChunkSource}) and the matched text to every chunk, and reconciles overlapping
 * same-tag chunks per the {@link MergeStrategy}.
 *
 * <p>The number of {@code engines} requested drives the behavior (matching {@code EnginePolicy}):
 * none → each chunker's highest-priority engine with fallback; one → that engine pinned; two or
 * more → union of those engines. {@link MergeStrategy#MERGE_STRATEGY_RAW} keeps every hit; the
 * default collapses same-tag overlapping chunks into one whose canonical span comes from the
 * highest-priority producer and whose {@code sources} list every contributor.</p>
 */
final class ChunkResolver {

  private static final Logger logger = LoggerFactory.getLogger(ChunkResolver.class);

  private final RankedBackends<ChunkerModel> chunkers;
  private final List<String> chunkerIds;
  private final List<String> engines;
  private final boolean raw;
  private final String rawText;

  /**
   * @param chunkers The chunker registry grouped by id.
   * @param chunkerIds The chunker ids to run, in order.
   * @param engines The engine ids from the policy (already normalized): none/one/many.
   * @param merge The merge strategy.
   * @param rawText The document text, for extracting each chunk's surface text.
   */
  ChunkResolver(RankedBackends<ChunkerModel> chunkers, List<String> chunkerIds,
      List<String> engines, MergeStrategy merge, String rawText) {
    this.chunkers = chunkers;
    this.chunkerIds = chunkerIds;
    this.engines = engines;
    this.raw = merge == MergeStrategy.MERGE_STRATEGY_RAW;
    this.rawText = rawText;
  }

  /** One chunker/engine's production of one chunk, before merging. */
  private record Hit(String chunkerId, ChunkerModel model, ChunkSpan chunk) {

    AnnotationSpan span() {
      return chunk.getAnnotationSpan();
    }

    String tag() {
      return ChunkerRegistry.normalize(chunk.getChunkTag());
    }
  }

  /**
   * Resolves the chunks for one sentence: runs the policy and merges. Returns a {@link ChunkResult}
   * with chunks in span order.
   *
   * @param sentence The POS-tagged sentence.
   *
   * @return The chunk result, with provenance and text.
   */
  ChunkResult resolve(AnnotatedSentence sentence) {
    final ChunkResult.Builder result = ChunkResult.newBuilder();
    if (sentence.getTokensCount() == 0) {
      return result.build();
    }
    final List<Hit> hits = new ArrayList<>();
    for (String chunkerId : chunkerIds) {
      collectHits(chunkerId, sentence, hits);
    }
    for (ChunkSpan chunk : raw ? mergeRaw(hits) : mergeConsensus(hits)) {
      result.addChunks(chunk);
    }
    return result.build();
  }

  private void collectHits(String chunkerId, AnnotatedSentence sentence, List<Hit> hits) {
    if (engines.isEmpty()) {
      runWithFallback(chunkerId, sentence, hits);
    } else if (engines.size() == 1) {
      final String engine = engines.get(0);
      if (chunkers.supports(chunkerId, engine)) {
        addHits(chunkerId, chunkers.resolve(chunkerId, engine).value(), sentence, hits);
      }
    } else {
      for (String engine : engines) {
        if (chunkers.supports(chunkerId, engine)) {
          addHits(chunkerId, chunkers.resolve(chunkerId, engine).value(), sentence, hits);
        }
      }
    }
  }

  /** Runs the highest-priority engine, falling back to the next on failure; rethrows if all fail. */
  private void runWithFallback(String chunkerId, AnnotatedSentence sentence, List<Hit> hits) {
    final List<Registration<ChunkerModel>> ranked = chunkers.resolve(chunkerId);
    RuntimeException last = null;
    for (Registration<ChunkerModel> registration : ranked) {
      final int before = hits.size();
      try {
        addHits(chunkerId, registration.value(), sentence, hits);
        return;
      } catch (RuntimeException e) {
        hits.subList(before, hits.size()).clear();
        last = e;
        if (ranked.size() > 1) {
          logger.warn("Chunker '{}' failed on engine '{}'; falling back",
              chunkerId, registration.engineId(), e);
        }
      }
    }
    if (last != null) {
      throw last;
    }
  }

  private void addHits(String chunkerId, ChunkerModel model, AnnotatedSentence sentence,
      List<Hit> hits) {
    for (ChunkSpan chunk : model.chunk(sentence)) {
      hits.add(new Hit(chunkerId, model, chunk));
    }
  }

  private List<ChunkSpan> mergeRaw(List<Hit> hits) {
    final List<Hit> sorted = sortedBySpan(hits);
    final List<ChunkSpan> chunks = new ArrayList<>(sorted.size());
    for (Hit hit : sorted) {
      chunks.add(ChunkSpan.newBuilder()
          .setAnnotationSpan(hit.span())
          .setChunkTag(hit.chunk().getChunkTag())
          .setText(textOf(hit.span()))
          .addSources(sourceOf(hit, hit.span()))
          .build());
    }
    return chunks;
  }

  private List<ChunkSpan> mergeConsensus(List<Hit> hits) {
    final Map<String, List<Hit>> byTag = new LinkedHashMap<>();
    for (Hit hit : hits) {
      byTag.computeIfAbsent(hit.tag(), k -> new ArrayList<>()).add(hit);
    }
    final List<ChunkSpan> chunks = new ArrayList<>();
    byTag.forEach((tag, tagHits) -> {
      for (List<Hit> cluster : clusterByOverlap(tagHits)) {
        chunks.add(consensusChunk(cluster));
      }
    });
    chunks.sort(Comparator
        .comparingInt((ChunkSpan c) -> c.getAnnotationSpan().getStart())
        .thenComparingInt(c -> c.getAnnotationSpan().getEnd())
        .thenComparing(ChunkSpan::getChunkTag));
    return chunks;
  }

  /** Groups same-tag hits whose spans overlap transitively (sorted sweep). */
  private List<List<Hit>> clusterByOverlap(List<Hit> tagHits) {
    final List<Hit> sorted = sortedBySpan(tagHits);
    final List<List<Hit>> clusters = new ArrayList<>();
    List<Hit> current = null;
    int clusterEnd = -1;
    for (Hit hit : sorted) {
      final int start = hit.span().getStart();
      final int end = hit.span().getEnd();
      if (current == null || start >= clusterEnd) {
        current = new ArrayList<>();
        clusters.add(current);
        clusterEnd = end;
      } else {
        clusterEnd = Math.max(clusterEnd, end);
      }
      current.add(hit);
    }
    return clusters;
  }

  /** Collapses one overlap cluster into a single chunk carrying every producer as a source. */
  private ChunkSpan consensusChunk(List<Hit> cluster) {
    Hit canonical = cluster.get(0);
    for (Hit hit : cluster) {
      if (hit.model().priority() > canonical.model().priority()) {
        canonical = hit;
      }
    }
    final AnnotationSpan canonicalSpan = canonical.span();
    final ChunkSpan.Builder chunk = ChunkSpan.newBuilder()
        .setAnnotationSpan(canonicalSpan)
        .setChunkTag(canonical.chunk().getChunkTag())
        .setText(textOf(canonicalSpan));
    for (Hit hit : cluster) {
      chunk.addSources(sourceOf(hit, canonicalSpan));
    }
    return chunk.build();
  }

  private ChunkSource sourceOf(Hit hit, AnnotationSpan canonicalSpan) {
    final ChunkSource.Builder source = ChunkSource.newBuilder()
        .setChunkerId(hit.chunkerId())
        .setEngine(hit.model().backendId());
    if (!hit.span().equals(canonicalSpan)) {
      source.setAnnotationSpan(hit.span());
    }
    return source.build();
  }

  private static List<Hit> sortedBySpan(List<Hit> hits) {
    final List<Hit> sorted = new ArrayList<>(hits);
    sorted.sort(Comparator
        .comparingInt((Hit h) -> h.span().getStart())
        .thenComparingInt(h -> h.span().getEnd())
        .thenComparing(h -> h.model().backendId()));
    return sorted;
  }

  private String textOf(AnnotationSpan span) {
    final int start = span.getStart();
    final int end = span.getEnd();
    if (rawText == null || start < 0 || end > rawText.length() || start > end) {
      return "";
    }
    return rawText.substring(start, end);
  }
}
