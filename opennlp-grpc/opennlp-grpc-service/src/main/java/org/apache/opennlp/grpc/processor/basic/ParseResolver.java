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
import java.util.List;

import org.apache.opennlp.grpc.backend.RankedBackends;
import org.apache.opennlp.grpc.backend.RankedBackends.Registration;
import org.apache.opennlp.grpc.model.ParserModel;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies a parser engine policy to one sentence at a time, the parsing analogue of
 * {@link NerEntityResolver}. Because a parse is a tree, not a set of spans, a union produces one
 * tree per engine (each stamped with its producer) rather than a merged tree; {@code MergeStrategy}
 * does not apply.
 *
 * <p>The number of {@code engines} requested drives the behavior (matching {@code EnginePolicy}):
 * none → each parser's highest-priority engine with fallback; one → that engine pinned; two or more
 * → one tree per listed engine. The first returned tree is the primary (highest-priority); a caller
 * stores it on {@code parse_tree} and, when more than one tree is produced, the full list on
 * {@code parse_trees}.</p>
 */
final class ParseResolver {

  private static final Logger logger = LoggerFactory.getLogger(ParseResolver.class);

  private final RankedBackends<ParserModel> parsers;
  private final List<String> parserIds;
  private final List<String> engines;
  private final boolean structured;
  private final boolean bracketed;
  private final boolean includeProbabilities;

  /**
   * @param parsers The parser registry grouped by id.
   * @param parserIds The parser ids to run, in order.
   * @param engines The engine ids from the policy (already normalized): none/one/many.
   * @param structured Whether to populate the structured tree.
   * @param bracketed Whether to populate the Penn-Treebank string.
   * @param includeProbabilities Whether to attach per-node probabilities.
   */
  ParseResolver(RankedBackends<ParserModel> parsers, List<String> parserIds, List<String> engines,
      boolean structured, boolean bracketed, boolean includeProbabilities) {
    this.parsers = parsers;
    this.parserIds = parserIds;
    this.engines = engines;
    this.structured = structured;
    this.bracketed = bracketed;
    this.includeProbabilities = includeProbabilities;
  }

  /**
   * Parses one sentence, returning a tree per produced (parser, engine), each tagged with its
   * producer; the highest-priority tree is first.
   *
   * @param sentence The tokenized sentence.
   *
   * @return The parse trees with provenance; empty for an empty sentence.
   */
  List<ParseTree> resolve(AnnotatedSentence sentence) {
    if (sentence.getTokensCount() == 0) {
      return List.of();
    }
    final List<ParseTree> trees = new ArrayList<>();
    for (String parserId : parserIds) {
      collectTrees(parserId, sentence, trees);
    }
    return trees;
  }

  private void collectTrees(String parserId, AnnotatedSentence sentence, List<ParseTree> trees) {
    if (engines.isEmpty()) {
      runWithFallback(parserId, sentence, trees);
    } else if (engines.size() == 1) {
      final String engine = engines.get(0);
      if (parsers.supports(parserId, engine)) {
        trees.add(stamp(parserId, parsers.resolve(parserId, engine).value(), sentence));
      }
    } else {
      for (String engine : engines) {
        if (parsers.supports(parserId, engine)) {
          trees.add(stamp(parserId, parsers.resolve(parserId, engine).value(), sentence));
        }
      }
    }
  }

  /** Parses on the highest-priority engine, falling back to the next on failure; rethrows if all fail. */
  private void runWithFallback(String parserId, AnnotatedSentence sentence, List<ParseTree> trees) {
    final List<Registration<ParserModel>> ranked = parsers.resolve(parserId);
    RuntimeException last = null;
    for (Registration<ParserModel> registration : ranked) {
      try {
        trees.add(stamp(parserId, registration.value(), sentence));
        return;
      } catch (RuntimeException e) {
        last = e;
        if (ranked.size() > 1) {
          logger.warn("Parser '{}' failed on engine '{}'; falling back",
              parserId, registration.engineId(), e);
        }
      }
    }
    if (last != null) {
      throw last;
    }
  }

  private ParseTree stamp(String parserId, ParserModel model, AnnotatedSentence sentence) {
    return model.parse(sentence, structured, bracketed, includeProbabilities).toBuilder()
        .setParserId(parserId)
        .setEngine(model.backendId())
        .build();
  }
}
