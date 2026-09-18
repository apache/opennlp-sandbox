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
package org.apache.opennlp.grpc.spi.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.NamedEntity;

/**
 * {@link NerModel} backed by a stateless model-free {@link TokenNameFinder}, such as a
 * dictionary or regex name finder. Each instance serves exactly one entity type and keeps
 * no adaptive state. Matches are deterministic, so a requested probability is exactly 1.
 */
public final class StatelessNerModel implements NerModel {

  private final String entityType;
  private final TokenNameFinder nameFinder;
  private final String backendId;
  private final int priority;
  private final String artifactHash;

  /**
   * Creates a stateless name-finder registration.
   *
   * @param entityType The logical entity type and model id. Must not be {@code null}.
   * @param nameFinder The initialized finder. Must not be {@code null}.
   * @param backendId The open backend id reported for this model. Must not be {@code null}.
   * @param priority The selection priority among engines serving {@code entityType}.
   * @param artifactHash The source file hash, or blank when unavailable.
   *
   * @throws IllegalArgumentException If {@code entityType}, {@code nameFinder}, or
   *     {@code backendId} is {@code null}.
   */
  public StatelessNerModel(String entityType, TokenNameFinder nameFinder, String backendId,
      int priority, String artifactHash) {
    if (entityType == null) {
      throw new IllegalArgumentException("entityType must not be null");
    }
    if (nameFinder == null) {
      throw new IllegalArgumentException("nameFinder must not be null");
    }
    if (backendId == null) {
      throw new IllegalArgumentException("backendId must not be null");
    }
    this.entityType = entityType;
    this.nameFinder = nameFinder;
    this.backendId = backendId;
    this.priority = priority;
    this.artifactHash = artifactHash == null ? "" : artifactHash;
  }

  /** {@inheritDoc} */
  @Override
  public String id() {
    return entityType;
  }

  /** {@inheritDoc} */
  @Override
  public String backendId() {
    return backendId;
  }

  /** {@inheritDoc} */
  @Override
  public String artifactHash() {
    return artifactHash;
  }

  /** {@inheritDoc} */
  @Override
  public int priority() {
    return priority;
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> entityTypes() {
    return Set.of(entityType);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isStateful() {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void clearAdaptiveData() {
    // Dictionary and regex finders keep no adaptive state.
  }

  /** {@inheritDoc} */
  @Override
  public List<NamedEntity> recognize(AnnotatedSentence sentence, boolean includeProbabilities) {
    if (sentence.getTokensCount() == 0) {
      return List.of();
    }
    final Span[] spans = nameFinder.find(NerSpans.tokenTexts(sentence));
    final List<NamedEntity> entities = new ArrayList<>(spans.length);
    for (Span span : spans) {
      final NamedEntity.Builder entity = NamedEntity.newBuilder()
          .setAnnotationSpan(NerSpans.tokenSpanToDocumentSpan(sentence, span))
          .setEntityType(NerSpans.resolveEntityType(entityType, span));
      if (includeProbabilities) {
        // A dictionary or regex match either holds exactly or not at all.
        entity.setProbability(1.0);
      }
      entities.add(entity.build());
    }
    return entities;
  }
}
