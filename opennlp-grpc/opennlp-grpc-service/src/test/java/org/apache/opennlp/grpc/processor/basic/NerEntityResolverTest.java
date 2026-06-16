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
import java.util.Set;

import org.apache.opennlp.grpc.backend.RankedBackends;
import org.apache.opennlp.grpc.model.NerModel;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.EntitySource;
import org.apache.opennlp.grpc.v1.MergeStrategy;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link NerEntityResolver}: the count-driven engine policy (default/pin/union),
 * provenance and matched-text attachment, RAW vs CONSENSUS merging, and priority fallback. Uses
 * fake recognizers so the behavior is exercised without any model files.
 */
class NerEntityResolverTest {

  private static final String TEXT = "the United States of America";
  //                                  0123456789...           ^17        ^28
  private static final AnnotatedSentence SENTENCE =
      AnnotatedSentence.newBuilder().addTokens(Token.newBuilder().setText("x")).build();

  @Test
  void defaultRunsPrimaryEngineWithProvenanceAndText() {
    final RankedBackends<NerModel> recognizers = RankedBackends.<NerModel>builder()
        .add("location", "opennlp-me", 0, model("location", "opennlp-me", 0,
            entity("location", 4, 17, null)))
        .build();
    final NerEntityResolver resolver = resolver(recognizers, List.of("location"), List.of(),
        MergeStrategy.MERGE_STRATEGY_UNSPECIFIED, Set.of("location"), false);

    final List<NamedEntity> entities = resolver.resolve(SENTENCE);
    assertEquals(1, entities.size());
    final NamedEntity entity = entities.get(0);
    assertEquals("location", entity.getEntityType());
    assertEquals("United States", entity.getText());
    assertFalse(entity.hasProbability());
    assertEquals(1, entity.getSourcesCount());
    final EntitySource source = entity.getSources(0);
    assertEquals("location", source.getRecognizerId());
    assertEquals("opennlp-me", source.getEngine());
    assertFalse(source.hasAnnotationSpan());
  }

  @Test
  void pinnedEngineRunsOnlyThatEngineAndSkipsRecognizersItDoesNotServe() {
    final RankedBackends<NerModel> recognizers = RankedBackends.<NerModel>builder()
        .add("location", "opennlp-me", 0, model("location", "opennlp-me", 0,
            entity("location", 4, 17, null)))
        .add("location", "onnx", 10, model("location", "onnx", 10,
            entity("location", 4, 17, null)))
        .add("money", "opennlp-me", 0, model("money", "opennlp-me", 0,
            entity("money", 0, 3, null)))
        .build();
    // Pin onnx: 'location' runs on onnx; 'money' (served only by opennlp-me) is skipped.
    final NerEntityResolver resolver = resolver(recognizers, List.of("location", "money"),
        List.of("onnx"), MergeStrategy.MERGE_STRATEGY_UNSPECIFIED, Set.of("location", "money"),
        false);

    final List<NamedEntity> entities = resolver.resolve(SENTENCE);
    assertEquals(1, entities.size());
    assertEquals("location", entities.get(0).getEntityType());
    assertEquals("onnx", entities.get(0).getSources(0).getEngine());
  }

  @Test
  void unionMergesOverlappingSameTypeIntoConsensusWithAllSources() {
    final RankedBackends<NerModel> recognizers = RankedBackends.<NerModel>builder()
        .add("location", "opennlp-me", 0, model("location", "opennlp-me", 0,
            entity("location", 4, 17, 0.7)))
        .add("location", "onnx", 10, model("location", "onnx", 10,
            entity("location", 4, 17, 0.9)))
        .build();
    final NerEntityResolver resolver = resolver(recognizers, List.of("location"),
        List.of("opennlp-me", "onnx"), MergeStrategy.MERGE_STRATEGY_CONSENSUS,
        Set.of("location"), true);

    final List<NamedEntity> entities = resolver.resolve(SENTENCE);
    assertEquals(1, entities.size());
    final NamedEntity entity = entities.get(0);
    assertEquals("United States", entity.getText());
    // Canonical probability is the highest of the producers'.
    assertEquals(0.9, entity.getProbability(), 1e-9);
    assertEquals(2, entity.getSourcesCount());
    assertEquals(Set.of("opennlp-me", "onnx"),
        Set.of(entity.getSources(0).getEngine(), entity.getSources(1).getEngine()));
    for (EntitySource source : entity.getSourcesList()) {
      assertFalse(source.hasAnnotationSpan(), "spans match the canonical, so none is recorded");
    }
  }

  @Test
  void unionWithDivergentOffsetsRecordsEachProvidersOwnSpan() {
    // opennlp-me finds "United States" [4,17); the higher-priority onnx finds "the United States
    // of America" [0,28). They overlap and merge; the canonical span is onnx's; opennlp-me's
    // divergent span is recorded on its source.
    final RankedBackends<NerModel> recognizers = RankedBackends.<NerModel>builder()
        .add("location", "opennlp-me", 0, model("location", "opennlp-me", 0,
            entity("location", 4, 17, null)))
        .add("location", "onnx", 10, model("location", "onnx", 10,
            entity("location", 0, 28, null)))
        .build();
    final NerEntityResolver resolver = resolver(recognizers, List.of("location"),
        List.of("opennlp-me", "onnx"), MergeStrategy.MERGE_STRATEGY_CONSENSUS,
        Set.of("location"), false);

    final NamedEntity entity = resolver.resolve(SENTENCE).get(0);
    assertEquals(0, entity.getAnnotationSpan().getStart());
    assertEquals(28, entity.getAnnotationSpan().getEnd());
    assertEquals(TEXT, entity.getText());
    final EntitySource me = sourceForEngine(entity, "opennlp-me");
    assertTrue(me.hasAnnotationSpan());
    assertEquals(4, me.getAnnotationSpan().getStart());
    assertEquals(17, me.getAnnotationSpan().getEnd());
    assertFalse(sourceForEngine(entity, "onnx").hasAnnotationSpan());
  }

  @Test
  void rawMergeKeepsEachProvidersHitSeparate() {
    final RankedBackends<NerModel> recognizers = RankedBackends.<NerModel>builder()
        .add("location", "opennlp-me", 0, model("location", "opennlp-me", 0,
            entity("location", 4, 17, null)))
        .add("location", "onnx", 10, model("location", "onnx", 10,
            entity("location", 4, 17, null)))
        .build();
    final NerEntityResolver resolver = resolver(recognizers, List.of("location"),
        List.of("opennlp-me", "onnx"), MergeStrategy.MERGE_STRATEGY_RAW, Set.of("location"), false);

    final List<NamedEntity> entities = resolver.resolve(SENTENCE);
    assertEquals(2, entities.size());
    for (NamedEntity entity : entities) {
      assertEquals(1, entity.getSourcesCount());
      assertEquals("United States", entity.getText());
    }
  }

  @Test
  void defaultFallsBackToNextEngineWhenTopPriorityFails() {
    final RankedBackends<NerModel> recognizers = RankedBackends.<NerModel>builder()
        .add("location", "opennlp-me", 0, model("location", "opennlp-me", 0,
            entity("location", 4, 17, null)))
        .add("location", "onnx", 10, failingModel("location", "onnx", 10))
        .build();
    final NerEntityResolver resolver = resolver(recognizers, List.of("location"), List.of(),
        MergeStrategy.MERGE_STRATEGY_UNSPECIFIED, Set.of("location"), false);

    final List<NamedEntity> entities = resolver.resolve(SENTENCE);
    assertEquals(1, entities.size());
    assertEquals("opennlp-me", entities.get(0).getSources(0).getEngine());
  }

  @Test
  void rethrowsWhenEveryEngineFails() {
    final RankedBackends<NerModel> recognizers = RankedBackends.<NerModel>builder()
        .add("location", "onnx", 0, failingModel("location", "onnx", 0))
        .build();
    final NerEntityResolver resolver = resolver(recognizers, List.of("location"), List.of(),
        MergeStrategy.MERGE_STRATEGY_UNSPECIFIED, Set.of("location"), false);

    assertThrows(RuntimeException.class, () -> resolver.resolve(SENTENCE));
  }

  @Test
  void keepsOnlyRequestedTypes() {
    final RankedBackends<NerModel> recognizers = RankedBackends.<NerModel>builder()
        .add("multi", "onnx", 0, model("multi", "onnx", 0,
            entity("location", 4, 17, null), entity("money", 0, 3, null)))
        .build();
    final NerEntityResolver resolver = resolver(recognizers, List.of("multi"), List.of(),
        MergeStrategy.MERGE_STRATEGY_UNSPECIFIED, Set.of("location"), false);

    final List<NamedEntity> entities = resolver.resolve(SENTENCE);
    assertEquals(1, entities.size());
    assertEquals("location", entities.get(0).getEntityType());
  }

  private static EntitySource sourceForEngine(NamedEntity entity, String engine) {
    return entity.getSourcesList().stream()
        .filter(s -> s.getEngine().equals(engine))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no source for engine " + engine));
  }

  private static NerEntityResolver resolver(RankedBackends<NerModel> recognizers,
      List<String> recognizerIds, List<String> engines, MergeStrategy merge,
      Set<String> requestedTypes, boolean includeProbabilities) {
    return new NerEntityResolver(recognizers, recognizerIds, engines, merge, requestedTypes, TEXT,
        includeProbabilities);
  }

  private static NamedEntity entity(String type, int start, int end, Double probability) {
    final NamedEntity.Builder builder = NamedEntity.newBuilder()
        .setEntityType(type)
        .setAnnotationSpan(AnnotationSpan.newBuilder()
            .setStart(start)
            .setEnd(end)
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT));
    if (probability != null) {
      builder.setProbability(probability);
    }
    return builder.build();
  }

  private static NerModel model(String id, String engine, int priority, NamedEntity... entities) {
    return new FakeNerModel(id, engine, priority, List.of(entities), false);
  }

  private static NerModel failingModel(String id, String engine, int priority) {
    return new FakeNerModel(id, engine, priority, List.of(), true);
  }

  /** A recognizer that returns preset entities (or always throws), ignoring the sentence text. */
  private record FakeNerModel(String id, String backendId, int priority, List<NamedEntity> entities,
      boolean fail) implements NerModel {

    @Override
    public Set<String> entityTypes() {
      // Not consulted by the resolver, which is given the recognizer ids to run directly.
      return Set.of();
    }

    @Override
    public boolean isStateful() {
      return false;
    }

    @Override
    public void clearAdaptiveData() {
    }

    @Override
    public List<NamedEntity> recognize(AnnotatedSentence sentence, boolean includeProbabilities) {
      if (fail) {
        throw new IllegalStateException("engine '" + backendId + "' failed");
      }
      return entities;
    }
  }
}
