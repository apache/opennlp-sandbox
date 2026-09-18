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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import opennlp.tools.util.StringUtil;
import org.apache.opennlp.grpc.backend.RankedBackends;
import org.apache.opennlp.grpc.backend.RankedBackends.Registration;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.spi.model.ParserModel;
import org.apache.opennlp.grpc.spi.model.ParserBackendFactory;

/**
 * Catalog of {@link ParserModel} parsers grouped by logical id into a {@link RankedBackends}, so the
 * same parser id may be served by several engines (each with a priority) and the orchestrator picks
 * among them by the request's engine policy. Unlike the span producers, a parser union returns one
 * tree per engine rather than a merged tree.
 *
 * <p>Parsers are produced by {@link ParserBackendFactory} backends discovered via
 * {@link ServiceLoader}: the built-in classic backend ({@code model.parser.<id>.path}) plus any
 * third-party backend whose jar registers one. Registering the same id under two backends is the
 * multi-engine case; registering it twice under the same backend is an error.</p>
 */
public final class ParserRegistry {

  private final RankedBackends<ParserModel> parsers;
  private final Set<String> knownEngines;

  private ParserRegistry(RankedBackends<ParserModel> parsers, Set<String> knownEngines) {
    this.parsers = parsers;
    this.knownEngines = Set.copyOf(knownEngines);
  }

  /**
   * Canonical form of a parser id or engine id: trimmed and lower-cased.
   *
   * @param value The raw value to normalize. May be {@code null}.
   *
   * @return The normalized value, or {@code null} if {@code value} is {@code null}.
   */
  public static String normalize(String value) {
    return value == null ? null : StringUtil.toLowerCase(value.trim());
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
   * Loads all parsers by discovering {@link ParserBackendFactory} backends via
   * {@link ServiceLoader} and grouping the parsers each contributes by id.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return A registry, possibly empty when no parser is configured.
   *
   * @throws AnalysisException If two factories declare the same factory id, a backend's
   *     configuration is invalid, a model fails to load, or the same parser id is registered
   *     twice by the same engine.
   */
  public static ParserRegistry create(Map<String, String> configuration) {
    return create(configuration, ServiceLoader.load(ParserBackendFactory.class));
  }

  /**
   * Loads from the given factories instead of {@link ServiceLoader} discovery; package-private
   * so tests can drive the factory set directly.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   * @param factories The backend factories to load parsers from.
   *
   * @return A registry, possibly empty when no parser is configured.
   *
   * @throws AnalysisException If two factories declare the same factory id, a backend's
   *     configuration is invalid, a model fails to load, or the same parser id is registered
   *     twice by the same engine.
   */
  static ParserRegistry create(
      Map<String, String> configuration, Iterable<ParserBackendFactory> factories) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    final RankedBackends.Builder<ParserModel> builder = RankedBackends.builder();
    final Set<String> knownEngines = new LinkedHashSet<>();
    final Map<String, String> seenFactories = new LinkedHashMap<>();
    final List<ParserModel> loaded = new ArrayList<>();
    try {
      for (ParserBackendFactory factory : factories) {
        final String previous =
            seenFactories.putIfAbsent(factory.factoryId(), factory.getClass().getName());
        if (previous != null) {
          throw AnalysisException.invalidArgument(
              "Parser backend factory id '" + factory.factoryId() + "' is declared by both "
                  + previous + " and " + factory.getClass().getName());
        }
        for (ParserModel model : factory.create(configuration)) {
          loaded.add(model);
          builder.add(model.id(), model.backendId(), model.priority(), model);
          knownEngines.add(model.backendId());
        }
      }
      return new ParserRegistry(builder.build(), knownEngines);
    } catch (RuntimeException e) {
      // A factory that throws after earlier factories loaded parsers must not leak the native
      // resources those parsers hold; the half-built registry can never be closed by the caller.
      try {
        closeModels(loaded);
      } catch (RuntimeException closeFailure) {
        e.addSuppressed(closeFailure);
      }
      throw e;
    }
  }

  /** Closes every closeable parser, aggregating failures; used when startup fails partway. */
  private static void closeModels(List<ParserModel> models) {
    final List<Exception> failures = new ArrayList<>();
    for (ParserModel model : models) {
      if (model instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception e) {
          failures.add(e);
        }
      }
    }
    if (!failures.isEmpty()) {
      final IllegalStateException error =
          new IllegalStateException("Failed to close " + failures.size() + " parser(s)");
      failures.forEach(error::addSuppressed);
      throw error;
    }
  }

  /**
   * Reports whether any parser is configured.
   *
   * @return {@code true} when at least one parser is registered.
   */
  public boolean isAvailable() {
    return !parsers.isEmpty();
  }

  /**
   * Returns the parsers grouped by id, for the orchestrator to apply an engine policy.
   *
   * @return The ranked parser registry.
   */
  public RankedBackends<ParserModel> parsers() {
    return parsers;
  }

  /**
   * Returns the configured parser ids, in registration order.
   *
   * @return An immutable list of the parser ids.
   */
  public List<String> parserIds() {
    return List.copyOf(parsers.ids());
  }

  /**
   * Reports whether the named engine serves any parser.
   *
   * @param engine The engine/backend id; matched after normalization. May be {@code null}.
   *
   * @return {@code true} when the engine is registered.
   */
  public boolean knowsEngine(String engine) {
    return engine != null && knownEngines.contains(normalize(engine));
  }

  /** Releases caller-specific inference state for every parser on the current thread. */
  public void clearThreadLocalState() {
    for (String id : parsers.ids()) {
      for (Registration<ParserModel> registration : parsers.resolve(id)) {
        registration.value().clearThreadLocalState();
      }
    }
  }
}
