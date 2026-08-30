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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.opennlp.grpc.backend.RankedBackends;
import org.apache.opennlp.grpc.spi.model.ParserModel;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.ParseTree;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link ParseResolver}: the count-driven engine policy (default/pin/union) and the
 * tree-per-engine semantics (a union returns one tree per engine, each tagged with its producer; no
 * merge). Uses fake parsers so the behavior is exercised without a parser model.
 */
class ParseResolverTest {

  private static final AnnotatedSentence SENTENCE =
      AnnotatedSentence.newBuilder().addTokens(Token.newBuilder().setText("x")).build();

  @Test
  void defaultRunsPrimaryEngineWithProvenance() {
    final RankedBackends<ParserModel> parsers = RankedBackends.<ParserModel>builder()
        .add("default", "opennlp-me", 0, model("default", "opennlp-me", 0))
        .build();
    final List<ParseTree> trees = resolver(parsers, List.of()).resolve(SENTENCE);

    assertEquals(1, trees.size());
    assertEquals("default", trees.get(0).getParserId());
    assertEquals("opennlp-me", trees.get(0).getEngine());
  }

  @Test
  void pinnedEngineRunsOnlyThatEngineAndSkipsParsersItDoesNotServe() {
    final RankedBackends<ParserModel> parsers = RankedBackends.<ParserModel>builder()
        .add("a", "opennlp-me", 0, model("a", "opennlp-me", 0))
        .add("a", "neural", 10, model("a", "neural", 10))
        .add("b", "opennlp-me", 0, model("b", "opennlp-me", 0))
        .build();
    final ParseResolver resolver =
        new ParseResolver(parsers, List.of("a", "b"), List.of("neural"), true, false, false);

    final List<ParseTree> trees = resolver.resolve(SENTENCE);
    assertEquals(1, trees.size());
    assertEquals("a", trees.get(0).getParserId());
    assertEquals("neural", trees.get(0).getEngine());
  }

  @Test
  void unionReturnsOneTreePerEngineEachTagged() {
    final RankedBackends<ParserModel> parsers = RankedBackends.<ParserModel>builder()
        .add("default", "opennlp-me", 0, model("default", "opennlp-me", 0))
        .add("default", "neural", 10, model("default", "neural", 10))
        .build();
    final ParseResolver resolver = new ParseResolver(parsers, List.of("default"),
        List.of("opennlp-me", "neural"), true, false, false);

    final List<ParseTree> trees = resolver.resolve(SENTENCE);
    assertEquals(2, trees.size());
    assertEquals("opennlp-me", trees.get(0).getEngine());
    assertEquals("neural", trees.get(1).getEngine());
  }

  @Test
  void invalidArgumentFailurePropagatesWithoutFallback() {
    // A deterministic client error from the top-priority engine is not retryable: it must
    // surface to the caller and the next engine must never run.
    final AtomicInteger fallbackCalls = new AtomicInteger();
    final RankedBackends<ParserModel> parsers = RankedBackends.<ParserModel>builder()
        .add("default", "opennlp-me", 0,
            new FakeParserModel("default", "opennlp-me", 0, null, fallbackCalls))
        .add("default", "neural", 10, failingModel("default", "neural", 10,
            AnalysisException.invalidArgument("bad parse request")))
        .build();
    final ParseResolver resolver = resolver(parsers, List.of());

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> resolver.resolve(SENTENCE));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertEquals(0, fallbackCalls.get(), "a non-retryable failure must not fall back");
  }

  @Test
  void defaultFallsBackToNextEngineWhenTopPriorityFails() {
    final RankedBackends<ParserModel> parsers = RankedBackends.<ParserModel>builder()
        .add("default", "opennlp-me", 0, model("default", "opennlp-me", 0))
        .add("default", "neural", 10, failingModel("default", "neural", 10,
            AnalysisException.unavailable("engine unreachable", null)))
        .build();
    final List<ParseTree> trees = resolver(parsers, List.of()).resolve(SENTENCE);

    assertEquals(1, trees.size());
    assertEquals("opennlp-me", trees.get(0).getEngine());
  }

  @Test
  void rethrowsWhenEveryEngineFails() {
    final RankedBackends<ParserModel> parsers = RankedBackends.<ParserModel>builder()
        .add("default", "neural", 0, failingModel("default", "neural", 0,
            AnalysisException.unavailable("engine unreachable", null)))
        .build();
    final ParseResolver resolver = resolver(parsers, List.of());

    assertThrows(AnalysisException.class, () -> resolver.resolve(SENTENCE));
  }

  private static ParseResolver resolver(RankedBackends<ParserModel> parsers, List<String> engines) {
    return new ParseResolver(parsers, List.of("default"), engines, true, false, false);
  }

  private static ParserModel model(String id, String engine, int priority) {
    return new FakeParserModel(id, engine, priority, null, new AtomicInteger());
  }

  private static ParserModel failingModel(String id, String engine, int priority,
      RuntimeException failure) {
    return new FakeParserModel(id, engine, priority, failure, new AtomicInteger());
  }

  /** A parser that returns a fixed (empty) tree, or always throws, ignoring the sentence. */
  private record FakeParserModel(String id, String backendId, int priority, RuntimeException failure,
      AtomicInteger invocations) implements ParserModel {

    @Override
    public ParseTree parse(AnnotatedSentence sentence, boolean structured, boolean bracketed,
        boolean includeProbabilities) {
      invocations.incrementAndGet();
      if (failure != null) {
        throw failure;
      }
      return ParseTree.newBuilder().setPennTreebank("(TOP)").build();
    }
  }
}
