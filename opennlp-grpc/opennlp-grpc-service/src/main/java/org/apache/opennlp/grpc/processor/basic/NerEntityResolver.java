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
import java.util.Set;

import org.apache.opennlp.grpc.backend.RankedBackends;
import org.apache.opennlp.grpc.backend.RankedBackends.Registration;
import org.apache.opennlp.grpc.model.NameFinderRegistry;
import org.apache.opennlp.grpc.model.NerModel;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.EntitySource;
import org.apache.opennlp.grpc.v1.MergeStrategy;
import org.apache.opennlp.grpc.v1.NamedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies a NER engine policy to one sentence at a time: it decides which engine(s) run for each
 * selected recognizer, attaches provenance ({@link EntitySource}) and the matched text to every
 * entity, and reconciles overlapping hits per the {@link MergeStrategy}.
 *
 * <p>The number of {@code engines} requested drives the behavior (matching
 * {@code NerEnginePolicy}):</p>
 * <ul>
 *   <li><b>none</b> — each recognizer runs its highest-priority engine, falling back to the next on
 *       failure;</li>
 *   <li><b>one</b> — that engine is pinned (no fallback); recognizers it does not serve are
 *       skipped;</li>
 *   <li><b>two or more</b> — all listed engines run and their entities are unioned, each tagged with
 *       its producer.</li>
 * </ul>
 *
 * <p>{@link MergeStrategy#MERGE_STRATEGY_RAW} keeps every hit as its own entity; the default
 * ({@code UNSPECIFIED}/{@code CONSENSUS}) collapses same-type overlapping hits into one entity whose
 * canonical span comes from the highest-priority producer and whose {@code sources} list every
 * producer that found it.</p>
 */
final class NerEntityResolver {

  private static final Logger logger = LoggerFactory.getLogger(NerEntityResolver.class);

  private final RankedBackends<NerModel> recognizers;
  private final List<String> recognizerIds;
  private final List<String> engines;
  private final boolean raw;
  private final Set<String> requestedTypes;
  private final String rawText;
  private final boolean includeProbabilities;

  /**
   * @param recognizers The recognizer registry grouped by id.
   * @param recognizerIds The recognizer ids selected for the requested types, in run order.
   * @param engines The engine ids from the policy (already normalized): none/one/many.
   * @param merge The merge strategy.
   * @param requestedTypes The normalized entity types to keep in the output.
   * @param rawText The document text, for extracting each entity's surface text.
   * @param includeProbabilities Whether to attach probabilities.
   */
  NerEntityResolver(RankedBackends<NerModel> recognizers, List<String> recognizerIds,
      List<String> engines, MergeStrategy merge, Set<String> requestedTypes, String rawText,
      boolean includeProbabilities) {
    this.recognizers = recognizers;
    this.recognizerIds = recognizerIds;
    this.engines = engines;
    this.raw = merge == MergeStrategy.MERGE_STRATEGY_RAW;
    this.requestedTypes = requestedTypes;
    this.rawText = rawText;
    this.includeProbabilities = includeProbabilities;
  }

  /** One recognizer/engine's recognition of one entity, before merging. */
  private record Hit(String recognizerId, NerModel model, NamedEntity entity) {

    AnnotationSpan span() {
      return entity.getAnnotationSpan();
    }

    String type() {
      return NameFinderRegistry.normalize(entity.getEntityType());
    }
  }

  /**
   * Resolves the entities for one sentence: runs the policy, filters to the requested types, and
   * merges. Returns entities sorted by span.
   *
   * @param sentence The tokenized sentence.
   *
   * @return The recognized entities, with provenance and text, in span order.
   */
  List<NamedEntity> resolve(AnnotatedSentence sentence) {
    if (sentence.getTokensCount() == 0) {
      return List.of();
    }
    final List<Hit> hits = new ArrayList<>();
    for (String recognizerId : recognizerIds) {
      collectHits(recognizerId, sentence, hits);
    }
    hits.removeIf(hit -> !requestedTypes.contains(hit.type()));
    return raw ? mergeRaw(hits) : mergeConsensus(hits);
  }

  private void collectHits(String recognizerId, AnnotatedSentence sentence, List<Hit> hits) {
    if (engines.isEmpty()) {
      runWithFallback(recognizerId, sentence, hits);
    } else if (engines.size() == 1) {
      final String engine = engines.get(0);
      if (recognizers.supports(recognizerId, engine)) {
        addHits(recognizerId, recognizers.resolve(recognizerId, engine).value(), sentence, hits);
      }
    } else {
      for (String engine : engines) {
        if (recognizers.supports(recognizerId, engine)) {
          addHits(recognizerId, recognizers.resolve(recognizerId, engine).value(), sentence, hits);
        }
      }
    }
  }

  /** Runs the highest-priority engine, falling back to the next on failure; rethrows if all fail. */
  private void runWithFallback(String recognizerId, AnnotatedSentence sentence, List<Hit> hits) {
    final List<Registration<NerModel>> ranked = recognizers.resolve(recognizerId);
    RuntimeException last = null;
    for (Registration<NerModel> registration : ranked) {
      final int before = hits.size();
      try {
        addHits(recognizerId, registration.value(), sentence, hits);
        return;
      } catch (RuntimeException e) {
        // Drop any partial hits from the failed engine before trying the next one.
        hits.subList(before, hits.size()).clear();
        last = e;
        if (ranked.size() > 1) {
          logger.warn("NER recognizer '{}' failed on engine '{}'; falling back",
              recognizerId, registration.engineId(), e);
        }
      }
    }
    if (last != null) {
      throw last;
    }
  }

  private void addHits(String recognizerId, NerModel model, AnnotatedSentence sentence,
      List<Hit> hits) {
    for (NamedEntity entity : model.recognize(sentence, includeProbabilities)) {
      hits.add(new Hit(recognizerId, model, entity));
    }
  }

  private List<NamedEntity> mergeRaw(List<Hit> hits) {
    final List<Hit> sorted = sortedBySpan(hits);
    final List<NamedEntity> entities = new ArrayList<>(sorted.size());
    for (Hit hit : sorted) {
      final NamedEntity.Builder entity = NamedEntity.newBuilder()
          .setAnnotationSpan(hit.span())
          .setEntityType(hit.entity().getEntityType())
          .setText(textOf(hit.span()))
          .addSources(sourceOf(hit, hit.span()));
      if (includeProbabilities && hit.entity().hasProbability()) {
        entity.setProbability(hit.entity().getProbability());
      }
      entities.add(entity.build());
    }
    return entities;
  }

  private List<NamedEntity> mergeConsensus(List<Hit> hits) {
    final Map<String, List<Hit>> byType = new LinkedHashMap<>();
    for (Hit hit : hits) {
      byType.computeIfAbsent(hit.type(), k -> new ArrayList<>()).add(hit);
    }
    final List<NamedEntity> entities = new ArrayList<>();
    byType.forEach((type, typeHits) -> {
      for (List<Hit> cluster : clusterByOverlap(typeHits)) {
        entities.add(consensusEntity(cluster));
      }
    });
    entities.sort(Comparator
        .comparingInt((NamedEntity e) -> e.getAnnotationSpan().getStart())
        .thenComparingInt(e -> e.getAnnotationSpan().getEnd())
        .thenComparing(NamedEntity::getEntityType));
    return entities;
  }

  /** Groups same-type hits whose spans overlap transitively (sorted sweep). */
  private List<List<Hit>> clusterByOverlap(List<Hit> typeHits) {
    final List<Hit> sorted = sortedBySpan(typeHits);
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

  /** Collapses one overlap cluster into a single entity carrying every producer as a source. */
  private NamedEntity consensusEntity(List<Hit> cluster) {
    Hit canonical = cluster.get(0);
    double maxProbability = probabilityOrNaN(canonical);
    for (Hit hit : cluster) {
      if (hit.model().priority() > canonical.model().priority()) {
        canonical = hit;
      }
      final double probability = probabilityOrNaN(hit);
      if (!Double.isNaN(probability) && (Double.isNaN(maxProbability) || probability > maxProbability)) {
        maxProbability = probability;
      }
    }
    final AnnotationSpan canonicalSpan = canonical.span();
    final NamedEntity.Builder entity = NamedEntity.newBuilder()
        .setAnnotationSpan(canonicalSpan)
        .setEntityType(canonical.entity().getEntityType())
        .setText(textOf(canonicalSpan));
    if (includeProbabilities && !Double.isNaN(maxProbability)) {
      entity.setProbability(maxProbability);
    }
    for (Hit hit : cluster) {
      entity.addSources(sourceOf(hit, canonicalSpan));
    }
    return entity.build();
  }

  private EntitySource sourceOf(Hit hit, AnnotationSpan canonicalSpan) {
    final EntitySource.Builder source = EntitySource.newBuilder()
        .setRecognizerId(hit.recognizerId())
        .setEngine(hit.model().backendId());
    if (includeProbabilities && hit.entity().hasProbability()) {
      source.setProbability(hit.entity().getProbability());
    }
    // Record this provider's own span only when it diverges from the canonical one.
    if (!hit.span().equals(canonicalSpan)) {
      source.setAnnotationSpan(hit.span());
    }
    return source.build();
  }

  private static double probabilityOrNaN(Hit hit) {
    return hit.entity().hasProbability() ? hit.entity().getProbability() : Double.NaN;
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
