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
package org.apache.opennlp.grpc.backend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic registry for the server's multi-backend pattern: one <em>logical</em> id (an embedding
 * model, an entity type, a categorizer, ...) may be served by several <em>engines</em> at once,
 * each registered with a priority. Resolution is the same everywhere and is <b>strongly typed</b>
 * because the logical id and engine are always separate arguments, never a parsed string:
 *
 * <ul>
 *   <li>{@link #invoke(String, Function)} runs against the engines for an id in <b>descending
 *       priority</b> order, <b>falling back</b> to the next engine when one fails;</li>
 *   <li>{@link #invoke(String, String, Function)} pins exactly one engine (no fallback);</li>
 *   <li>{@link #ids()} advertises the logical ids; {@link #enginesFor(String)} lists the engines
 *       serving one id in priority order.</li>
 * </ul>
 *
 * <p>{@code T} is whatever a single engine contributes for one id (e.g. an {@code EmbeddingProvider}
 * or a recognizer). This class owns only the routing/fallback policy; capability-specific checks
 * (e.g. embedding-dimension agreement) are the caller's job after {@link Builder#build()}.</p>
 *
 * @param <T> The per-engine handler type for one logical id.
 */
public final class RankedBackends<T> {

  private static final Logger logger = LoggerFactory.getLogger(RankedBackends.class);

  /**
   * One engine's registration of a logical id.
   *
   * @param <T> The per-engine handler type.
   * @param logicalId The id clients request.
   * @param engineId The backend/engine that serves it.
   * @param priority The selection priority (higher is preferred).
   * @param value The engine's handler for this id.
   */
  public record Registration<T>(String logicalId, String engineId, int priority, T value) {
  }

  /**
   * One successful invocation together with the registration that handled it.
   *
   * @param <T> The per-engine handler type.
   * @param <R> The invocation result type.
   * @param registration The registration that handled the invocation.
   * @param result The successful invocation result.
   */
  public record Invocation<T, R>(Registration<T> registration, R result) {
  }

  /** Logical id -> registrations, highest priority first. */
  private final Map<String, List<Registration<T>>> byLogicalId;

  private RankedBackends(Map<String, List<Registration<T>>> byLogicalId) {
    this.byLogicalId = byLogicalId;
  }

  /**
   * Starts building a registry.
   *
   * @param <T> The per-engine handler type.
   *
   * @return A new, empty builder.
   */
  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  /**
   * Returns the logical ids this registry serves.
   *
   * @return The logical ids (no engine qualifiers).
   */
  public Set<String> ids() {
    return byLogicalId.keySet();
  }

  /**
   * Reports whether no id is registered.
   *
   * @return {@code true} when nothing is registered.
   */
  public boolean isEmpty() {
    return byLogicalId.isEmpty();
  }

  /**
   * Reports whether any engine serves the id.
   *
   * @param id The logical id. May be {@code null}.
   *
   * @return {@code true} when at least one engine serves {@code id}.
   */
  public boolean supports(String id) {
    return id != null && byLogicalId.containsKey(id);
  }

  /**
   * Reports whether the named engine serves the id.
   *
   * @param id The logical id. May be {@code null}.
   * @param engine The engine/backend id. May be {@code null}.
   *
   * @return {@code true} when {@code engine} serves {@code id}.
   */
  public boolean supports(String id, String engine) {
    if (id == null || engine == null) {
      return false;
    }
    final List<Registration<T>> ranked = byLogicalId.get(id);
    return ranked != null && ranked.stream().anyMatch(r -> r.engineId().equals(engine));
  }

  /**
   * Lists the engines serving an id, in descending-priority order.
   *
   * @param id The logical id.
   *
   * @return The engine ids, highest priority first.
   * @throws AnalysisException {@code NOT_FOUND} if no engine serves {@code id}.
   */
  public List<String> enginesFor(String id) {
    return resolve(id).stream().map(Registration::engineId).toList();
  }

  /**
   * Returns the registrations for an id in fallback order (descending priority).
   *
   * @param id The logical id.
   *
   * @return The registrations, highest priority first.
   * @throws AnalysisException {@code NOT_FOUND} if no engine serves {@code id}.
   */
  public List<Registration<T>> resolve(String id) {
    final List<Registration<T>> ranked = id == null ? null : byLogicalId.get(id);
    if (ranked == null) {
      throw AnalysisException.notFound("No engine serves '" + id + "'");
    }
    return ranked;
  }

  /**
   * Returns the single registration of an id on a named engine.
   *
   * @param id The logical id.
   * @param engine The engine/backend id to pin.
   *
   * @return The registration on {@code engine}.
   * @throws AnalysisException {@code NOT_FOUND} if that engine does not serve {@code id}.
   */
  public Registration<T> resolve(String id, String engine) {
    for (Registration<T> registration : resolve(id)) {
      if (registration.engineId().equals(engine)) {
        return registration;
      }
    }
    throw AnalysisException.notFound("Engine '" + engine + "' does not serve '" + id + "'");
  }

  /**
   * Returns the highest-priority registration for an id (the engine a bare request resolves to).
   *
   * @param id The logical id.
   *
   * @return The primary registration.
   * @throws AnalysisException {@code NOT_FOUND} if no engine serves {@code id}.
   */
  public Registration<T> primary(String id) {
    return resolve(id).get(0);
  }

  /**
   * Runs {@code op} against each engine for {@code id} in priority order, returning the first
   * success and falling back to the next engine on an explicitly retryable
   * {@link AnalysisException}. Re-throws the last failure when every engine fails. Client errors,
   * failed preconditions, internal failures, and unexpected runtime exceptions are not retried.
   *
   * @param <R> The operation's result type.
   * @param id The logical id.
   * @param op The operation to run against an engine's registration.
   *
   * @return The first engine's successful result.
   * @throws AnalysisException {@code NOT_FOUND} if no engine serves {@code id}.
   */
  public <R> R invoke(String id, Function<Registration<T>, R> op) {
    return invokeResolved(id, op).result();
  }

  /**
   * Runs with safe fallback and retains the registration that succeeded.
   *
   * @param <R> The operation's result type.
   * @param id The logical id.
   * @param op The operation to run against an engine's registration.
   * @return The successful result and the registration that produced it.
   * @throws AnalysisException {@code NOT_FOUND} if no engine serves {@code id}, or when every
   *                           eligible engine fails.
   */
  public <R> Invocation<T, R> invokeResolved(
      String id, Function<Registration<T>, R> op) {
    final List<Registration<T>> registrations = resolve(id);
    AnalysisException last = null;
    for (Registration<T> registration : registrations) {
      try {
        return new Invocation<>(registration, op.apply(registration));
      } catch (AnalysisException e) {
        if (!isRetryable(e)) {
          throw e;
        }
        last = e;
        if (registrations.size() > 1) {
          logger.warn("'{}' failed on engine '{}'; falling back to the next engine",
              id, registration.engineId(), e);
        }
      }
    }
    throw last;
  }

  /**
   * Returns whether a backend failure permits fallback to the next engine: only
   * {@link AnalysisException.FailureType#UNAVAILABLE} and
   * {@link AnalysisException.FailureType#RESOURCE_EXHAUSTED} are retryable.
   *
   * @param failure The failure an engine raised. Must not be {@code null}.
   *
   * @return {@code true} when the failure is transient and the next engine may be tried.
   */
  public static boolean isRetryable(AnalysisException failure) {
    return failure.getFailureType() == AnalysisException.FailureType.UNAVAILABLE
        || failure.getFailureType() == AnalysisException.FailureType.RESOURCE_EXHAUSTED;
  }

  /**
   * Runs {@code op} against the pinned engine for {@code id}; no fallback.
   *
   * @param <R> The operation's result type.
   * @param id The logical id.
   * @param engine The engine/backend id to pin.
   * @param op The operation to run against the pinned engine's registration.
   *
   * @return The operation's result.
   * @throws AnalysisException {@code NOT_FOUND} if {@code engine} does not serve {@code id}.
   */
  public <R> R invoke(String id, String engine, Function<Registration<T>, R> op) {
    return op.apply(resolve(id, engine));
  }

  /**
   * Accumulates registrations and produces an immutable, priority-sorted {@link RankedBackends}.
   *
   * @param <T> The per-engine handler type.
   */
  public static final class Builder<T> {

    private final Map<String, List<Registration<T>>> byLogicalId = new LinkedHashMap<>();

    private Builder() {
    }

    /**
     * Registers one engine's handler for a logical id.
     *
     * @param logicalId The id clients request.
     * @param engineId The engine/backend serving it.
     * @param priority The selection priority (higher is preferred).
     * @param value The engine's handler for this id.
     *
     * @return This builder.
     * @throws AnalysisException If the same {@code (logicalId, engineId)} pair is registered twice.
     * @throws IllegalArgumentException If {@code logicalId}, {@code engineId}, or {@code value}
     *         is {@code null}.
     */
    public Builder<T> add(String logicalId, String engineId, int priority, T value) {
      if (logicalId == null) {
        throw new IllegalArgumentException("logicalId must not be null");
      }
      if (engineId == null) {
        throw new IllegalArgumentException("engineId must not be null");
      }
      if (value == null) {
        throw new IllegalArgumentException("value must not be null");
      }
      final List<Registration<T>> ranked =
          byLogicalId.computeIfAbsent(logicalId, k -> new ArrayList<>());
      if (ranked.stream().anyMatch(r -> r.engineId().equals(engineId))) {
        throw AnalysisException.invalidArgument(
            "Duplicate registration for '" + logicalId + "' on engine '" + engineId + "'");
      }
      ranked.add(new Registration<>(logicalId, engineId, priority, value));
      return this;
    }

    /**
     * Builds the immutable registry, sorting each id's engines by descending priority.
     *
     * @return The registry.
     */
    public RankedBackends<T> build() {
      final Map<String, List<Registration<T>>> sorted = new LinkedHashMap<>();
      byLogicalId.forEach((id, registrations) -> {
        final List<Registration<T>> ranked = new ArrayList<>(registrations);
        // Descending priority; ties keep registration (engine discovery) order via a stable sort.
        ranked.sort(Comparator.comparingInt((Registration<T> r) -> r.priority()).reversed());
        sorted.put(id, List.copyOf(ranked));
      });
      return new RankedBackends<>(Map.copyOf(sorted));
    }
  }
}
