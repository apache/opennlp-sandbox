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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads classic OpenNLP {@link NameFinderME} instances from server configuration.
 *
 * <p>Configuration keys follow the pattern {@code model.name_finder.<entity_type>.path},
 * where {@code entity_type} becomes the logical type name exposed to clients via
 * {@code AnalysisProfile.ner_entity_types} and {@code NamedEntity.entity_type}. Example:
 *
 * <pre>{@code
 * model.name_finder.person.path=/path/to/en-ner-person.bin
 * model.name_finder.organization.path=/path/to/en-ner-organization.bin
 * model.name_finder.location.path=/path/to/en-ner-location.bin
 * }</pre>
 *
 * <p>Each entry points at one Java-serialized {@link TokenNameFinderModel} ({@code .bin}).
 * ONNX-backed NER ({@code NameFinderDL}) is not handled here; it would use a separate
 * backend key in a follow-on change.</p>
 */
public final class NameFinderRegistry {

  private static final Logger logger = LoggerFactory.getLogger(NameFinderRegistry.class);

  /** Prefix for per-type name finder path entries in server configuration. */
  public static final String KEY_PREFIX = "model.name_finder.";

  /** Suffix completing a per-type path key: {@code model.name_finder.<type>.path}. */
  public static final String KEY_SUFFIX = ".path";

  private final Map<String, NameFinderME> finders;

  private NameFinderRegistry(Map<String, NameFinderME> finders) {
    this.finders = Map.copyOf(finders);
  }

  /**
   * Canonical form of an entity type: trimmed and lower-cased. Configuration keys and
   * client-supplied {@code ner_entity_types} are normalized identically so entity types
   * are matched case-insensitively on both sides.
   *
   * @return The normalized type, or {@code null} if {@code entityType} is {@code null}.
   */
  public static String normalize(String entityType) {
    return entityType == null ? null : entityType.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Parses {@code model.name_finder.<entity_type>.path} entries and loads one
   * {@link NameFinderME} per configured type.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return A registry, possibly empty when no name finder paths are configured.
   *
   * @throws AnalysisException If a configured path is blank or the model cannot be loaded.
   */
  public static NameFinderRegistry create(Map<String, String> configuration) {
    Objects.requireNonNull(configuration, "configuration");
    final Map<String, String> paths = parseConfiguredPaths(configuration);
    if (paths.isEmpty()) {
      return new NameFinderRegistry(Map.of());
    }
    final Map<String, NameFinderME> loaded = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : paths.entrySet()) {
      loaded.put(entry.getKey(), loadNameFinder(entry.getKey(), entry.getValue()));
    }
    return new NameFinderRegistry(loaded);
  }

  public boolean isAvailable() {
    return !finders.isEmpty();
  }

  /**
   * @return Configured entity types in stable registration order.
   */
  public List<String> entityTypes() {
    return List.copyOf(finders.keySet());
  }

  public boolean supportsEntityType(String entityType) {
    return entityType != null && finders.containsKey(normalize(entityType));
  }

  public NameFinderME get(String entityType) {
    final NameFinderME finder = finders.get(normalize(entityType));
    if (finder == null) {
      throw new IllegalArgumentException("No name finder configured for entity type '" + entityType + "'");
    }
    return finder;
  }

  /**
   * Resolves which entity types to run for this request: an explicit
   * {@code AnalysisProfile.ner_entity_types} filter (normalized to the canonical form),
   * or all configured types when unset.
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
    for (NameFinderME finder : finders.values()) {
      finder.clearAdaptiveData();
    }
  }

  private static Map<String, String> parseConfiguredPaths(Map<String, String> configuration) {
    final Map<String, String> paths = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_PREFIX) || !key.endsWith(KEY_SUFFIX)) {
        continue;
      }
      final String entityType =
          normalize(key.substring(KEY_PREFIX.length(), key.length() - KEY_SUFFIX.length()));
      if (entityType.isEmpty()) {
        throw AnalysisException.invalidArgument(
            "Invalid name finder configuration key '" + key + "'; entity type must not be blank");
      }
      final String path = entry.getValue();
      if (path == null || path.isBlank()) {
        throw AnalysisException.invalidArgument(
            "Name finder path for entity type '" + entityType + "' must not be blank");
      }
      if (paths.putIfAbsent(entityType, path.trim()) != null) {
        throw AnalysisException.invalidArgument(
            "Duplicate name finder configuration for entity type '" + entityType + "'");
      }
    }
    return paths;
  }

  private static NameFinderME loadNameFinder(String entityType, String path) {
    try (InputStream input = new FileInputStream(path)) {
      final TokenNameFinderModel model = new TokenNameFinderModel(input);
      logger.info("Loaded name finder for entity type '{}' from {}", entityType, path);
      return new NameFinderME(model);
    } catch (IOException e) {
      throw AnalysisException.internal(
          "Failed to load name finder model for entity type '" + entityType + "' from " + path, e);
    }
  }
}
