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
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Catalog of {@link NerModel} recognizers, keyed by model id with an index from entity type
 * to the models that emit it.
 *
 * <p>Models are produced by {@link NerBackendFactory} backends discovered via
 * {@link ServiceLoader}: the built-in classic ({@code model.name_finder.<type>.path}) and
 * ONNX ({@code model.name_finder_dl.<id>.*}) backends, plus any third-party backend whose jar
 * registers a {@code NerBackendFactory}. Several backends may contribute models at once.</p>
 *
 * <p><b>Concurrency:</b> each recognizer is loaded once and shared across requests. Classic
 * {@code NameFinderME} models are {@code @ThreadSafe} (per-thread adaptive state) and
 * {@link #clearAdaptiveData()} resets only the calling thread's state after each document;
 * ONNX models are stateless.</p>
 */
public final class NameFinderRegistry implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(NameFinderRegistry.class);

  /** All recognizers keyed by model id (for classic models, the entity type). */
  private final Map<String, NerModel> modelsById;
  /** Index from normalized entity type to the models that can emit it. */
  private final Map<String, List<NerModel>> byEntityType;

  private NameFinderRegistry(Map<String, NerModel> modelsById,
      Map<String, List<NerModel>> byEntityType) {
    this.modelsById = Map.copyOf(modelsById);
    final Map<String, List<NerModel>> index = new LinkedHashMap<>();
    byEntityType.forEach((type, models) -> index.put(type, List.copyOf(models)));
    this.byEntityType = Map.copyOf(index);
  }

  /**
   * Canonical form of an entity type: trimmed and lower-cased. Configuration keys and
   * client-supplied {@code ner_entity_types} are normalized identically so entity types
   * are matched case-insensitively on both sides.
   *
   * @param entityType The raw entity type to normalize. May be {@code null}.
   *
   * @return The normalized type, or {@code null} if {@code entityType} is {@code null}.
   */
  public static String normalize(String entityType) {
    return entityType == null ? null : entityType.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Loads all name finder models with no sentence detector available, equivalent to
   * {@link #create(Map, SentenceDetector)} with a {@code null} detector.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return A registry, possibly empty when no name finder is configured.
   *
   * @throws AnalysisException If a backend's configuration is invalid or a model fails to
   *     load.
   */
  public static NameFinderRegistry create(Map<String, String> configuration) {
    return create(configuration, null);
  }

  /**
   * Loads all name finder models by discovering {@link NerBackendFactory} backends via
   * {@link ServiceLoader} and aggregating the models each contributes.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   * @param sentenceDetector The sentence detector ONNX name finders need internally; may be
   *     {@code null} when no ONNX name finder is configured.
   *
   * @return A registry, possibly empty when no name finder is configured.
   *
   * @throws AnalysisException If a backend's configuration is invalid or a model fails to
   *     load.
   */
  public static NameFinderRegistry create(
      Map<String, String> configuration, SentenceDetector sentenceDetector) {
    if (configuration == null) {
      throw new NullPointerException("configuration");
    }
    final NerBackendContext context = new NerBackendContext(sentenceDetector);
    final Map<String, NerModel> modelsById = new LinkedHashMap<>();
    final Map<String, List<NerModel>> byEntityType = new LinkedHashMap<>();
    final Set<String> seenFactories = new HashSet<>();
    for (NerBackendFactory factory : ServiceLoader.load(
        NerBackendFactory.class, NameFinderRegistry.class.getClassLoader())) {
      if (!seenFactories.add(factory.factoryId())) {
        logger.warn("Ignoring duplicate NER backend factory '{}' ({})",
            factory.factoryId(), factory.getClass().getName());
        continue;
      }
      for (NerModel model : factory.create(configuration, context)) {
        register(modelsById, byEntityType, model);
      }
    }
    return new NameFinderRegistry(modelsById, byEntityType);
  }

  private static void register(Map<String, NerModel> modelsById,
      Map<String, List<NerModel>> byEntityType, NerModel model) {
    if (modelsById.putIfAbsent(model.id(), model) != null) {
      throw AnalysisException.invalidArgument("Duplicate name finder model id: " + model.id());
    }
    for (String type : model.entityTypes()) {
      byEntityType.computeIfAbsent(type, key -> new ArrayList<>()).add(model);
    }
  }

  /**
   * Reports whether any name finder is configured.
   *
   * @return {@code true} when at least one model is registered.
   */
  public boolean isAvailable() {
    return !modelsById.isEmpty();
  }

  /**
   * Returns all configured recognizers, in registration order, for catalog reporting.
   *
   * @return An immutable copy of the registered models.
   */
  public List<NerModel> allModels() {
    return List.copyOf(modelsById.values());
  }

  /**
   * Returns the configured entity types in stable registration order.
   *
   * @return An immutable copy of the registered, normalized entity types.
   */
  public List<String> entityTypes() {
    return List.copyOf(byEntityType.keySet());
  }

  /**
   * Reports whether any model emits the given entity type.
   *
   * @param entityType The entity type to check. May be {@code null}; matched after
   *     normalization.
   *
   * @return {@code true} when a model is registered for the normalized type.
   */
  public boolean supportsEntityType(String entityType) {
    return entityType != null && byEntityType.containsKey(normalize(entityType));
  }

  /**
   * Resolves the distinct {@link NerModel}s that must run for the requested entity types:
   * every model that can emit at least one of them, each listed once. An empty or
   * {@code null} request selects all configured models. Running a model once and filtering
   * its output avoids invoking a multi-type model repeatedly.
   *
   * @param requestedTypes The requested entity types; {@code null} or empty selects all
   *     models. Each type is matched after normalization.
   *
   * @return An immutable list of the distinct models to run, in registration order.
   */
  public List<NerModel> modelsForTypes(List<String> requestedTypes) {
    if (requestedTypes == null || requestedTypes.isEmpty()) {
      return List.copyOf(modelsById.values());
    }
    final LinkedHashSet<NerModel> selected = new LinkedHashSet<>();
    for (String requestedType : requestedTypes) {
      final List<NerModel> models = byEntityType.get(normalize(requestedType));
      if (models != null) {
        selected.addAll(models);
      }
    }
    return List.copyOf(selected);
  }

  /**
   * Resolves which entity types to run for this request: an explicit
   * {@code AnalysisProfile.ner_entity_types} filter (normalized to the canonical form),
   * or all configured types when unset.
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
   * Clears adaptive feature-generator state on every loaded finder, as required by the
   * OpenNLP Name Finder API after each document when stateless RPC semantics are desired.
   */
  public void clearAdaptiveData() {
    for (NerModel model : modelsById.values()) {
      if (model.isStateful()) {
        model.clearAdaptiveData();
      }
    }
  }

  /**
   * Closes any recognizer that holds native resources (e.g. an ONNX session in a DL name
   * finder). Classic {@code NameFinderME} models hold none. A failure closing one model is
   * logged and does not stop the others from being released.
   */
  @Override
  public void close() {
    for (NerModel model : modelsById.values()) {
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
