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
import java.util.Set;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.apache.opennlp.grpc.v1.Token;

/**
 * {@link NerModel} backed by a classic OpenNLP {@link NameFinderME}. Each instance serves
 * exactly one entity type. {@code find} returns token-index spans, which this model maps
 * to document character offsets using the tokens' own spans.
 */
final class ClassicNerModel implements NerModel {

  /** Backend id reported for models served by the classic OpenNLP maxent runtime. */
  static final String BACKEND_ID = "opennlp-me";

  private final String entityType;
  private final NameFinderME nameFinder;

  ClassicNerModel(String entityType, NameFinderME nameFinder) {
    this.entityType = Objects.requireNonNull(entityType, "entityType");
    this.nameFinder = Objects.requireNonNull(nameFinder, "nameFinder");
  }

  @Override
  public String id() {
    return entityType;
  }

  @Override
  public String backendId() {
    return BACKEND_ID;
  }

  @Override
  public Set<String> entityTypes() {
    return Set.of(entityType);
  }

  @Override
  public boolean isStateful() {
    return true;
  }

  @Override
  public void clearAdaptiveData() {
    nameFinder.clearAdaptiveData();
  }

  @Override
  public List<NamedEntity> recognize(AnnotatedSentence sentence, boolean includeProbabilities) {
    if (sentence.getTokensCount() == 0) {
      return List.of();
    }
    final String[] tokens = tokenTexts(sentence);
    final Span[] spans = nameFinder.find(tokens);
    final double[] probabilities = includeProbabilities ? nameFinder.probs(spans) : null;
    final List<NamedEntity> entities = new ArrayList<>(spans.length);
    for (int e = 0; e < spans.length; e++) {
      final NamedEntity.Builder entity = NamedEntity.newBuilder()
          .setAnnotationSpan(tokenSpanToDocumentSpan(sentence, spans[e]))
          .setEntityType(resolveEntityType(entityType, spans[e]));
      if (probabilities != null && e < probabilities.length) {
        entity.setProbability(probabilities[e]);
      }
      entities.add(entity.build());
    }
    return entities;
  }

  private static String[] tokenTexts(AnnotatedSentence sentence) {
    final String[] tokens = new String[sentence.getTokensCount()];
    for (int t = 0; t < tokens.length; t++) {
      tokens[t] = sentence.getTokens(t).getText();
    }
    return tokens;
  }

  private static AnnotationSpan tokenSpanToDocumentSpan(AnnotatedSentence sentence, Span tokenSpan) {
    final int startToken = tokenSpan.getStart();
    final int endToken = tokenSpan.getEnd();
    if (startToken < 0 || endToken <= startToken || endToken > sentence.getTokensCount()) {
      throw new IllegalStateException("Name finder span is out of token bounds: " + tokenSpan);
    }
    final Token first = sentence.getTokens(startToken);
    final Token last = sentence.getTokens(endToken - 1);
    return AnnotationSpan.newBuilder()
        .setStart(first.getAnnotationSpan().getStart())
        .setEnd(last.getAnnotationSpan().getEnd())
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }

  /**
   * The authoritative entity type is the one the model emits on the span (set by
   * multi-class models). Single-type models leave it unset, so we fall back to the
   * configured type the finder was registered under — this is the intended label for
   * such models, not a guess.
   */
  private static String resolveEntityType(String configuredType, Span span) {
    final String spanType = span.getType();
    if (spanType != null && !spanType.isBlank()) {
      return spanType;
    }
    return configuredType;
  }
}
