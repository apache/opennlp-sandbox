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

import opennlp.dl.namefinder.NameFinderDL;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.NamedEntity;

/**
 * {@link NerModel} backed by an ONNX {@link NameFinderDL}. The transformer model is
 * stateless, so a single instance is shared across requests (it is {@code @ThreadSafe}).
 *
 * <p>Coordinate mapping is the subtlety here: {@code NameFinderDL.find} joins the input
 * tokens with single spaces, runs its own WordPiece tokenization, and returns spans as
 * <em>character offsets into that joined string</em> — not token indices and not document
 * offsets. {@link #documentSpan} maps those joined-text character offsets back to the
 * tokens' document character offsets, which the offset-encoding pass then converts to the
 * client encoding.</p>
 *
 * <p>The model emits whatever entity types its label set defines (PER, ORG, LOC, ...);
 * {@link #recognize} reports each entity under the model's own label and the registry
 * indexes the model under all {@link #entityTypes() types} it can produce.</p>
 */
final class DlNerModel implements NerModel, AutoCloseable {

  private final String id;
  private final Set<String> entityTypes;
  private final String backendId;
  private final NameFinderDL nameFinderDL;

  DlNerModel(String id, Set<String> entityTypes, String backendId, NameFinderDL nameFinderDL) {
    this.id = Objects.requireNonNull(id, "id");
    this.entityTypes = Set.copyOf(Objects.requireNonNull(entityTypes, "entityTypes"));
    this.backendId = Objects.requireNonNull(backendId, "backendId");
    this.nameFinderDL = Objects.requireNonNull(nameFinderDL, "nameFinderDL");
  }

  /** Releases the underlying ONNX session held by the {@link NameFinderDL}. */
  @Override
  public void close() throws Exception {
    nameFinderDL.close();
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public String backendId() {
    return backendId;
  }

  @Override
  public Set<String> entityTypes() {
    return entityTypes;
  }

  @Override
  public boolean isStateful() {
    return false;
  }

  @Override
  public void clearAdaptiveData() {
    // Transformer inference holds no document-level adaptive state.
  }

  @Override
  public List<NamedEntity> recognize(AnnotatedSentence sentence, boolean includeProbabilities) {
    if (sentence.getTokensCount() == 0) {
      return List.of();
    }
    final String[] tokens = tokenTexts(sentence);
    final Span[] spans = nameFinderDL.find(tokens);
    final List<NamedEntity> entities = new ArrayList<>(spans.length);
    for (Span span : spans) {
      // The entity type is the model's own label (PER, ORG, LOC, ...), normalized; the
      // orchestrator filters these against the requested ner_entity_types.
      final String entityType = NameFinderRegistry.normalize(span.getType());
      if (entityType == null || entityType.isEmpty()) {
        continue;
      }
      final NamedEntity.Builder entity = NamedEntity.newBuilder()
          .setAnnotationSpan(documentSpan(sentence, tokens, span.getStart(), span.getEnd()))
          .setEntityType(entityType);
      if (includeProbabilities) {
        // NameFinderDL reports the raw model score (not normalized to 0..1).
        entity.setProbability(span.getProb());
      }
      entities.add(entity.build());
    }
    return entities;
  }

  /**
   * Maps a character span over {@code String.join(" ", tokens)} (the coordinate space of
   * {@link NameFinderDL} output) to a document {@link AnnotationSpan}, by finding the tokens
   * the span overlaps and using their document offsets. Snaps to whole-token boundaries,
   * which is correct because entity spans cover sequences of input tokens.
   *
   * @throws IllegalStateException If the span overlaps no token (i.e. it is out of range
   *     for the joined text), which would indicate an inconsistency in the model output.
   */
  static AnnotationSpan documentSpan(AnnotatedSentence sentence, String[] tokens,
      int charStart, int charEnd) {
    int pos = 0;
    int firstToken = -1;
    int lastToken = -1;
    for (int i = 0; i < tokens.length; i++) {
      final int tokenStart = pos;
      final int tokenEnd = pos + tokens[i].length();
      if (firstToken == -1 && tokenEnd > charStart) {
        firstToken = i;
      }
      if (tokenStart < charEnd) {
        lastToken = i;
      }
      pos = tokenEnd + 1; // account for the single joining space
    }
    if (firstToken == -1 || lastToken < firstToken) {
      throw new IllegalStateException(
          "Name finder span [" + charStart + ", " + charEnd + ") overlaps no token");
    }
    return AnnotationSpan.newBuilder()
        .setStart(sentence.getTokens(firstToken).getAnnotationSpan().getStart())
        .setEnd(sentence.getTokens(lastToken).getAnnotationSpan().getEnd())
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
  }

  private static String[] tokenTexts(AnnotatedSentence sentence) {
    final String[] tokens = new String[sentence.getTokensCount()];
    for (int t = 0; t < tokens.length; t++) {
      tokens[t] = sentence.getTokens(t).getText();
    }
    return tokens;
  }
}
