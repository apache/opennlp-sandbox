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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import opennlp.tools.sentdetect.SentenceDetector;
import org.apache.opennlp.grpc.backend.RankedBackends;
import org.apache.opennlp.grpc.backend.RankedBackends.Registration;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Catalog of {@link NerModel} recognizers. A recognizer is identified by a logical
 * <em>recognizer id</em>; the same id may be served by several engines at once (e.g. a classic
 * maxent model and an ONNX model both registered as {@code person}), so the catalog groups
 * recognizers into a {@link RankedBackends} keyed by id with each engine's priority. A separate
 * index maps each entity type to the recognizer ids that can emit it.
 *
 * <p>Models are produced by {@link NerBackendFactory} backends discovered via
 * {@link ServiceLoader}: the built-in classic ({@code model.name_finder.<id>.path}) and ONNX
 * ({@code model.name_finder_dl.<id>.*}) backends, plus any third-party backend whose jar registers
 * a {@code NerBackendFactory}. Registering the same id under two backends is how a recognizer
 * becomes multi-engine; registering it twice under the <em>same</em> backend is an error.</p>
 *
 * <p><b>Concurrency:</b> each recognizer is loaded once and shared across requests. Classic
 * {@code NameFinderME} models are {@code @ThreadSafe} (per-thread adaptive state) and
 * {@link #clearAdaptiveData()} resets only the calling thread's state after each document;
 * ONNX models are stateless.</p>
 */
public final class NameFinderRegistry implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(NameFinderRegistry.class);

  /** Recognizers grouped by logical id, each id's engines priority-sorted. */
  private final RankedBackends<NerModel> recognizers;
  /** Index from normalized entity type to the recognizer ids that can emit it, in order. */
  private final Map<String, List<String>> recognizerIdsByType;
  /** Every backend/engine id present, for validating an engine pin. */
  private final Set<String> knownEngines;

  private NameFinderRegistry(RankedBackends<NerModel> recognizers,
      Map<String, List<String>> recognizerIdsByType, Set<String> knownEngines) {
    this.recognizers = recognizers;
    final Map<String, List<String>> index = new LinkedHashMap<>();
    recognizerIdsByType.forEach((type, ids) -> index.put(type, List.copyOf(ids)));
    this.recognizerIdsByType = Map.copyOf(index);
    this.knownEngines = Set.copyOf(knownEngines);
  }

  /**
   * Canonical form of an entity type or engine id: trimmed and lower-cased. Configuration keys
   * and client-supplied values are normalized identically so they are matched case-insensitively
   * on both sides.
   *
   * @param value The raw value to normalize. May be {@code null}.
   *
   * @return The normalized value, or {@code null} if {@code value} is {@code null}.
   */
  public static String normalize(String value) {
    return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Parses an optional integer priority from configuration.
   *
   * @param key The configuration key, for error messages.
   * @param rawValue The configured value; {@code null} or blank yields {@code 0}.
   *
   * @return The parsed priority, or {@code 0} when unset.
   * @throws AnalysisException {@code INVALID_ARGUMENT} if {@code rawValue} is not an integer.
   */
  public static int parsePriority(String key, String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(rawValue.trim());
    } catch (NumberFormatException e) {
      throw AnalysisException.invalidArgument(
          key + " must be an integer, was '" + rawValue + "'");
    }
  }

  /**
   * Loads all name finder models with no sentence detector available, equivalent to
   * {@link #create(Map, SentenceDetector)} with a {@code null} detector.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return A registry, possibly empty when no name finder is configured.
   *
   * @throws AnalysisException If a backend's configuration is invalid or a model fails to load.
   */
  public static NameFinderRegistry create(Map<String, String> configuration) {
    return create(configuration, null);
  }

  /**
   * Loads all name finder models by discovering {@link NerBackendFactory} backends via
   * {@link ServiceLoader} and grouping the models each contributes by recognizer id.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   * @param sentenceDetector The sentence detector ONNX name finders need internally; may be
   *     {@code null} when no ONNX name finder is configured.
   *
   * @return A registry, possibly empty when no name finder is configured.
   *
   * @throws AnalysisException If a backend's configuration is invalid, a model fails to load, or
   *     the same recognizer id is registered twice by the same engine.
   */
  public static NameFinderRegistry create(
      Map<String, String> configuration, SentenceDetector sentenceDetector) {
    if (configuration == null) {
      throw new NullPointerException("configuration");
    }
    final NerBackendContext context = new NerBackendContext(sentenceDetector);
    final RankedBackends.Builder<NerModel> builder = RankedBackends.builder();
    final Map<String, List<String>> recognizerIdsByType = new LinkedHashMap<>();
    final Set<String> knownEngines = new LinkedHashSet<>();
    final Set<String> seenFactories = new HashSet<>();
    for (NerBackendFactory factory : ServiceLoader.load(
        NerBackendFactory.class, NameFinderRegistry.class.getClassLoader())) {
      if (!seenFactories.add(factory.factoryId())) {
        logger.warn("Ignoring duplicate NER backend factory '{}' ({})",
            factory.factoryId(), factory.getClass().getName());
        continue;
      }
      for (NerModel model : factory.create(configuration, context)) {
        // RankedBackends rejects a duplicate (id, engine); distinct engines for one id are the
        // multi-engine case and are kept, priority-sorted.
        builder.add(model.id(), model.backendId(), model.priority(), model);
        knownEngines.add(model.backendId());
        for (String type : model.entityTypes()) {
          final List<String> ids = recognizerIdsByType.computeIfAbsent(type, k -> new ArrayList<>());
          if (!ids.contains(model.id())) {
            ids.add(model.id());
          }
        }
      }
    }
    return new NameFinderRegistry(builder.build(), recognizerIdsByType, knownEngines);
  }

  /**
   * Reports whether any name finder is configured.
   *
   * @return {@code true} when at least one recognizer is registered.
   */
  public boolean isAvailable() {
    return !recognizers.isEmpty();
  }

  /**
   * Returns the recognizers grouped by id, for the orchestrator to apply an engine policy.
   *
   * @return The ranked recognizer registry.
   */
  public RankedBackends<NerModel> recognizers() {
    return recognizers;
  }

  /**
   * Returns all configured recognizers across every engine, for catalog reporting.
   *
   * @return An immutable list of the registered models.
   */
  public List<NerModel> allModels() {
    final List<NerModel> models = new ArrayList<>();
    for (String id : recognizers.ids()) {
      for (Registration<NerModel> registration : recognizers.resolve(id)) {
        models.add(registration.value());
      }
    }
    return List.copyOf(models);
  }

  /**
   * Returns the configured entity types in stable registration order.
   *
   * @return An immutable copy of the registered, normalized entity types.
   */
  public List<String> entityTypes() {
    return List.copyOf(recognizerIdsByType.keySet());
  }

  /**
   * Reports whether any recognizer emits the given entity type.
   *
   * @param entityType The entity type to check. May be {@code null}; matched after normalization.
   *
   * @return {@code true} when a recognizer is registered for the normalized type.
   */
  public boolean supportsEntityType(String entityType) {
    return entityType != null && recognizerIdsByType.containsKey(normalize(entityType));
  }

  /**
   * Reports whether the named engine serves any recognizer.
   *
   * @param engine The engine/backend id; matched after normalization. May be {@code null}.
   *
   * @return {@code true} when the engine is registered.
   */
  public boolean knowsEngine(String engine) {
    return engine != null && knownEngines.contains(normalize(engine));
  }

  /**
   * Resolves the distinct recognizer ids that must run for the requested entity types: every
   * recognizer that can emit at least one of them, each listed once in registration order. An
   * empty or {@code null} request selects all configured recognizers.
   *
   * @param requestedTypes The requested entity types; {@code null} or empty selects all
   *     recognizers. Each type is matched after normalization.
   *
   * @return An immutable list of the distinct recognizer ids to run.
   */
  public List<String> recognizerIdsForTypes(List<String> requestedTypes) {
    if (requestedTypes == null || requestedTypes.isEmpty()) {
      return List.copyOf(recognizers.ids());
    }
    final LinkedHashSet<String> selected = new LinkedHashSet<>();
    for (String requestedType : requestedTypes) {
      final List<String> ids = recognizerIdsByType.get(normalize(requestedType));
      if (ids != null) {
        selected.addAll(ids);
      }
    }
    return List.copyOf(selected);
  }

  /**
   * Resolves which entity types to run for this request: an explicit
   * {@code AnalysisProfile.ner_entity_types} filter (normalized to the canonical form), or all
   * configured types when unset.
   *
   * @param requestedTypes The requested entity types; {@code null} or empty resolves to all
   *     configured types. Otherwise each requested type is normalized.
   *
   * @return An immutable list of the entity types to run, in request or registration order.
   */
  public List<String> resolveEntityTypes(List<String> requestedTypes) {
    if (requestedTypes == null || requestedTypes.isEmpty()) {
      return entityTypes();
    }
    final List<String> normalized = new ArrayList<>(requestedTypes.size());
    for (String requestedType : requestedTypes) {
      normalized.add(normalize(requestedType));
    }
    return List.copyOf(normalized);
  }

  /**
   * Clears adaptive feature-generator state on every loaded finder, as required by the OpenNLP
   * Name Finder API after each document when stateless RPC semantics are desired.
   */
  public void clearAdaptiveData() {
    for (NerModel model : allModels()) {
      if (model.isStateful()) {
        model.clearAdaptiveData();
      }
    }
  }

  /**
   * Closes any recognizer that holds native resources (e.g. an ONNX session in a DL name finder).
   * Classic {@code NameFinderME} models hold none. A failure closing one model is logged and does
   * not stop the others from being released.
   */
  @Override
  public void close() {
    for (NerModel model : allModels()) {
      if (model instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception e) {
          logger.warn("Failed to close name finder '{}'", model.id(), e);
        }
      }
    }
  }
}
