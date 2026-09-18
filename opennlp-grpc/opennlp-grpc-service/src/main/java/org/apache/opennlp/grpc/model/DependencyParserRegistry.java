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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.depparse.DependencyModel;
import opennlp.tools.depparse.DependencyParser;
import opennlp.tools.depparse.DependencyParserME;
import org.apache.opennlp.grpc.spi.AnalysisException;

/** Catalog of eagerly loaded dependency parsers, keyed by model id. */
public final class DependencyParserRegistry {

  private static final String NAMESPACE = "dependency_parser";

  /** Prefix for dependency parser model paths. */
  public static final String KEY_PREFIX = "model." + NAMESPACE + ".";

  /** Suffix completing a dependency parser path key. */
  public static final String KEY_SUFFIX = ".path";

  /** Configuration key selecting the default dependency parser. */
  public static final String KEY_DEFAULT_ID = "model." + NAMESPACE + ".default_id";

  private final Map<String, DependencyParser> parsers;
  private final String defaultId;

  private DependencyParserRegistry(Map<String, DependencyParser> parsers, String defaultId) {
    this.parsers = parsers;
    this.defaultId = defaultId;
  }

  /**
   * Loads models configured as {@code model.dependency_parser.<id>.path}.
   *
   * @param configuration The server configuration.
   * @return A registry containing the configured parsers.
   * @throws AnalysisException If a model id, path, artifact, or default id is invalid.
   */
  public static DependencyParserRegistry create(Map<String, String> configuration) {
    final Map<String, DependencyParser> parsers = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_PREFIX) || !key.endsWith(KEY_SUFFIX)) {
        continue;
      }
      final String id = key.substring(KEY_PREFIX.length(), key.length() - KEY_SUFFIX.length());
      if (id.isBlank() || id.contains(".")) {
        throw AnalysisException.invalidArgument(
            "Invalid dependency parser id in configuration key '" + key
                + "'; ids must be non-blank and must not contain '.'");
      }
      final Path path = Path.of(entry.getValue());
      if (!Files.isRegularFile(path)) {
        throw AnalysisException.invalidArgument(
            "Configured dependency parser model file not found: " + entry.getValue());
      }
      try {
        parsers.put(id, new DependencyParserME(new DependencyModel(path)));
      } catch (IOException | IllegalArgumentException e) {
        throw AnalysisException.internal(
            "Failed to load dependency parser '" + id + "' from " + entry.getValue(), e);
      }
    }
    String defaultId = configuration.get(KEY_DEFAULT_ID);
    if (defaultId != null && !parsers.containsKey(defaultId)) {
      throw AnalysisException.invalidArgument(
          KEY_DEFAULT_ID + " names an unconfigured dependency parser: " + defaultId);
    }
    if (defaultId == null && parsers.size() == 1) {
      defaultId = parsers.keySet().iterator().next();
    }
    return new DependencyParserRegistry(Map.copyOf(parsers), defaultId);
  }

  /**
   * Reports whether the registry can serve dependency parsing.
   *
   * @return {@code true} when at least one dependency parser is configured.
   */
  public boolean isAvailable() {
    return !parsers.isEmpty();
  }

  /**
   * Lists the configured parser ids.
   *
   * @return Configured model ids in stable lexical order.
   */
  public List<String> ids() {
    return parsers.keySet().stream().sorted().toList();
  }

  /**
   * Reports whether an unqualified request selects a model.
   *
   * @param modelId The model id to test.
   * @return {@code true} when an unqualified request resolves to this model.
   */
  public boolean isDefault(String modelId) {
    return modelId != null && modelId.equals(defaultId);
  }

  /**
   * Resolves an explicit parser id or the configured default.
   *
   * @param requested The requested parser id, or {@code null} for the default.
   * @return The resolved parser id.
   * @throws AnalysisException If the requested parser is unknown or no default is available.
   */
  public String resolveModelId(String requested) {
    if (requested != null && !requested.isBlank()) {
      if (!parsers.containsKey(requested)) {
        throw AnalysisException.notFound("Unknown dependency parser: " + requested);
      }
      return requested;
    }
    if (defaultId == null) {
      throw AnalysisException.notFound(parsers.isEmpty()
          ? "No dependency parser is configured on this server"
          : "dependency_parser_id is required when multiple dependency parsers are configured");
    }
    return defaultId;
  }

  /**
   * Retrieves a parser whose id was resolved by {@link #resolveModelId(String)}.
   *
   * @param modelId The resolved parser id.
   * @return The configured parser.
   * @throws AnalysisException If the resolved id is not registered.
   */
  public DependencyParser get(String modelId) {
    final DependencyParser parser = parsers.get(modelId);
    if (parser == null) {
      throw AnalysisException.internal(
          "Dependency parser '" + modelId + "' is not registered", null);
    }
    return parser;
  }
}
