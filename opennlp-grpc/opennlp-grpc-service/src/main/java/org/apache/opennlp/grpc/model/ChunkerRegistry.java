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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.opennlp.grpc.backend.RankedBackends;
import org.apache.opennlp.grpc.backend.RankedBackends.Registration;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Catalog of {@link ChunkerModel} chunkers grouped by logical id into a {@link RankedBackends}, so
 * the same chunker id may be served by several engines (each with a priority) and the orchestrator
 * picks among them by the request's engine policy.
 *
 * <p>Chunkers are produced by {@link ChunkerBackendFactory} backends discovered via
 * {@link ServiceLoader}: the built-in classic backend ({@code model.chunker.<id>.path}) plus any
 * third-party backend whose jar registers one. Registering the same id under two backends is the
 * multi-engine case; registering it twice under the same backend is an error.</p>
 */
public final class ChunkerRegistry implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(ChunkerRegistry.class);

  private final RankedBackends<ChunkerModel> chunkers;
  private final Set<String> knownEngines;

  private ChunkerRegistry(RankedBackends<ChunkerModel> chunkers, Set<String> knownEngines) {
    this.chunkers = chunkers;
    this.knownEngines = Set.copyOf(knownEngines);
  }

  /**
   * Canonical form of a chunker id or engine id: trimmed and lower-cased.
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
      throw AnalysisException.invalidArgument(key + " must be an integer, was '" + rawValue + "'");
    }
  }

  /**
   * Loads all chunkers by discovering {@link ChunkerBackendFactory} backends via
   * {@link ServiceLoader} and grouping the chunkers each contributes by id.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return A registry, possibly empty when no chunker is configured.
   *
   * @throws AnalysisException If a backend's configuration is invalid, a model fails to load, or the
   *     same chunker id is registered twice by the same engine.
   */
  public static ChunkerRegistry create(Map<String, String> configuration) {
    if (configuration == null) {
      throw new NullPointerException("configuration");
    }
    final RankedBackends.Builder<ChunkerModel> builder = RankedBackends.builder();
    final Set<String> knownEngines = new LinkedHashSet<>();
    final Set<String> seenFactories = new HashSet<>();
    for (ChunkerBackendFactory factory : ServiceLoader.load(
        ChunkerBackendFactory.class, ChunkerRegistry.class.getClassLoader())) {
      if (!seenFactories.add(factory.factoryId())) {
        logger.warn("Ignoring duplicate chunker backend factory '{}' ({})",
            factory.factoryId(), factory.getClass().getName());
        continue;
      }
      for (ChunkerModel model : factory.create(configuration)) {
        builder.add(model.id(), model.backendId(), model.priority(), model);
        knownEngines.add(model.backendId());
      }
    }
    return new ChunkerRegistry(builder.build(), knownEngines);
  }

  /**
   * Reports whether any chunker is configured.
   *
   * @return {@code true} when at least one chunker is registered.
   */
  public boolean isAvailable() {
    return !chunkers.isEmpty();
  }

  /**
   * Returns the chunkers grouped by id, for the orchestrator to apply an engine policy.
   *
   * @return The ranked chunker registry.
   */
  public RankedBackends<ChunkerModel> chunkers() {
    return chunkers;
  }

  /**
   * Returns the configured chunker ids, in registration order.
   *
   * @return An immutable list of the chunker ids.
   */
  public List<String> chunkerIds() {
    return List.copyOf(chunkers.ids());
  }

  /**
   * Reports whether the named engine serves any chunker.
   *
   * @param engine The engine/backend id; matched after normalization. May be {@code null}.
   *
   * @return {@code true} when the engine is registered.
   */
  public boolean knowsEngine(String engine) {
    return engine != null && knownEngines.contains(normalize(engine));
  }

  /**
   * Closes any chunker that holds native resources; classic {@code ChunkerME} models hold none.
   * A failure closing one is logged and does not stop the others.
   */
  @Override
  public void close() {
    for (String id : chunkers.ids()) {
      for (Registration<ChunkerModel> registration : chunkers.resolve(id)) {
        if (registration.value() instanceof AutoCloseable closeable) {
          try {
            closeable.close();
          } catch (Exception e) {
            logger.warn("Failed to close chunker '{}'", id, e);
          }
        }
      }
    }
  }
}
