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

import java.util.List;

import org.apache.opennlp.grpc.backend.RankedBackends;
import org.apache.opennlp.grpc.model.ChunkerModel;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkResult;
import org.apache.opennlp.grpc.v1.ChunkSource;
import org.apache.opennlp.grpc.v1.ChunkSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.MergeStrategy;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ChunkResolver}: the count-driven engine policy (default/pin/union),
 * provenance and matched-text attachment, RAW vs CONSENSUS merging, and priority fallback. Uses
 * fake chunkers so the behavior is exercised without a chunker model.
 */
class ChunkResolverTest {

  private static final String TEXT = "The quick brown fox";
  private static final AnnotatedSentence SENTENCE =
      AnnotatedSentence.newBuilder().addTokens(Token.newBuilder().setText("x")).build();

  @Test
  void defaultRunsPrimaryEngineWithProvenanceAndText() {
    final RankedBackends<ChunkerModel> chunkers = RankedBackends.<ChunkerModel>builder()
        .add("default", "opennlp-me", 0, model("default", "opennlp-me", 0, chunk("NP", 0, 19)))
        .build();
    final ChunkResult result = resolver(chunkers, List.of(), MergeStrategy.MERGE_STRATEGY_UNSPECIFIED)
        .resolve(SENTENCE);

    assertEquals(1, result.getChunksCount());
    final ChunkSpan chunk = result.getChunks(0);
    assertEquals("NP", chunk.getChunkTag());
    assertEquals("The quick brown fox", chunk.getText());
    assertEquals(1, chunk.getSourcesCount());
    assertEquals("default", chunk.getSources(0).getChunkerId());
    assertEquals("opennlp-me", chunk.getSources(0).getEngine());
    assertFalse(chunk.getSources(0).hasAnnotationSpan());
  }

  @Test
  void pinnedEngineRunsOnlyThatEngineAndSkipsChunkersItDoesNotServe() {
    final RankedBackends<ChunkerModel> chunkers = RankedBackends.<ChunkerModel>builder()
        .add("a", "opennlp-me", 0, model("a", "opennlp-me", 0, chunk("NP", 0, 19)))
        .add("a", "neural", 10, model("a", "neural", 10, chunk("NP", 0, 19)))
        .add("b", "opennlp-me", 0, model("b", "opennlp-me", 0, chunk("VP", 0, 3)))
        .build();
    // Pin neural: 'a' runs on neural; 'b' (served only by opennlp-me) is skipped.
    final ChunkResolver resolver = new ChunkResolver(chunkers, List.of("a", "b"),
        List.of("neural"), MergeStrategy.MERGE_STRATEGY_UNSPECIFIED, TEXT);

    final ChunkResult result = resolver.resolve(SENTENCE);
    assertEquals(1, result.getChunksCount());
    assertEquals("neural", result.getChunks(0).getSources(0).getEngine());
  }

  @Test
  void unionMergesOverlappingSameTagWithAllSources() {
    final RankedBackends<ChunkerModel> chunkers = RankedBackends.<ChunkerModel>builder()
        .add("default", "opennlp-me", 0, model("default", "opennlp-me", 0, chunk("NP", 0, 19)))
        .add("default", "neural", 10, model("default", "neural", 10, chunk("NP", 0, 19)))
        .build();
    final ChunkResolver resolver = new ChunkResolver(chunkers, List.of("default"),
        List.of("opennlp-me", "neural"), MergeStrategy.MERGE_STRATEGY_CONSENSUS, TEXT);

    final ChunkResult result = resolver.resolve(SENTENCE);
    assertEquals(1, result.getChunksCount());
    final ChunkSpan chunk = result.getChunks(0);
    assertEquals(2, chunk.getSourcesCount());
    for (ChunkSource source : chunk.getSourcesList()) {
      assertFalse(source.hasAnnotationSpan(), "spans match the canonical, so none is recorded");
    }
  }

  @Test
  void unionWithDivergentOffsetsRecordsEachProvidersOwnSpan() {
    final RankedBackends<ChunkerModel> chunkers = RankedBackends.<ChunkerModel>builder()
        .add("default", "opennlp-me", 0, model("default", "opennlp-me", 0, chunk("NP", 0, 9)))
        .add("default", "neural", 10, model("default", "neural", 10, chunk("NP", 0, 19)))
        .build();
    final ChunkResolver resolver = new ChunkResolver(chunkers, List.of("default"),
        List.of("opennlp-me", "neural"), MergeStrategy.MERGE_STRATEGY_CONSENSUS, TEXT);

    final ChunkSpan chunk = resolver.resolve(SENTENCE).getChunks(0);
    // Canonical span is the higher-priority neural's [0,19); the divergent opennlp-me span is kept.
    assertEquals(19, chunk.getAnnotationSpan().getEnd());
    assertEquals(TEXT, chunk.getText());
    final ChunkSource me = sourceForEngine(chunk, "opennlp-me");
    assertTrue(me.hasAnnotationSpan());
    assertEquals(9, me.getAnnotationSpan().getEnd());
    assertFalse(sourceForEngine(chunk, "neural").hasAnnotationSpan());
  }

  @Test
  void rawMergeKeepsEachProvidersChunkSeparate() {
    final RankedBackends<ChunkerModel> chunkers = RankedBackends.<ChunkerModel>builder()
        .add("default", "opennlp-me", 0, model("default", "opennlp-me", 0, chunk("NP", 0, 19)))
        .add("default", "neural", 10, model("default", "neural", 10, chunk("NP", 0, 19)))
        .build();
    final ChunkResolver resolver = new ChunkResolver(chunkers, List.of("default"),
        List.of("opennlp-me", "neural"), MergeStrategy.MERGE_STRATEGY_RAW, TEXT);

    final ChunkResult result = resolver.resolve(SENTENCE);
    assertEquals(2, result.getChunksCount());
    for (ChunkSpan chunk : result.getChunksList()) {
      assertEquals(1, chunk.getSourcesCount());
    }
  }

  @Test
  void defaultFallsBackToNextEngineWhenTopPriorityFails() {
    final RankedBackends<ChunkerModel> chunkers = RankedBackends.<ChunkerModel>builder()
        .add("default", "opennlp-me", 0, model("default", "opennlp-me", 0, chunk("NP", 0, 19)))
        .add("default", "neural", 10, failingModel("default", "neural", 10))
        .build();
    final ChunkResult result = resolver(chunkers, List.of(), MergeStrategy.MERGE_STRATEGY_UNSPECIFIED)
        .resolve(SENTENCE);

    assertEquals(1, result.getChunksCount());
    assertEquals("opennlp-me", result.getChunks(0).getSources(0).getEngine());
  }

  @Test
  void rethrowsWhenEveryEngineFails() {
    final RankedBackends<ChunkerModel> chunkers = RankedBackends.<ChunkerModel>builder()
        .add("default", "neural", 0, failingModel("default", "neural", 0))
        .build();
    final ChunkResolver resolver =
        resolver(chunkers, List.of(), MergeStrategy.MERGE_STRATEGY_UNSPECIFIED);

    assertThrows(RuntimeException.class, () -> resolver.resolve(SENTENCE));
  }

  private static ChunkSource sourceForEngine(ChunkSpan chunk, String engine) {
    return chunk.getSourcesList().stream()
        .filter(s -> s.getEngine().equals(engine))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no source for engine " + engine));
  }

  private static ChunkResolver resolver(RankedBackends<ChunkerModel> chunkers, List<String> engines,
      MergeStrategy merge) {
    return new ChunkResolver(chunkers, List.of("default"), engines, merge, TEXT);
  }

  private static ChunkSpan chunk(String tag, int start, int end) {
    return ChunkSpan.newBuilder()
        .setChunkTag(tag)
        .setAnnotationSpan(AnnotationSpan.newBuilder()
            .setStart(start)
            .setEnd(end)
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT))
        .build();
  }

  private static ChunkerModel model(String id, String engine, int priority, ChunkSpan... chunks) {
    return new FakeChunkerModel(id, engine, priority, List.of(chunks), false);
  }

  private static ChunkerModel failingModel(String id, String engine, int priority) {
    return new FakeChunkerModel(id, engine, priority, List.of(), true);
  }

  /** A chunker that returns preset chunks (or always throws), ignoring the sentence text. */
  private record FakeChunkerModel(String id, String backendId, int priority, List<ChunkSpan> chunks,
      boolean fail) implements ChunkerModel {

    @Override
    public List<ChunkSpan> chunk(AnnotatedSentence sentence) {
      if (fail) {
        throw new IllegalStateException("engine '" + backendId + "' failed");
      }
      return chunks;
    }
  }
}
