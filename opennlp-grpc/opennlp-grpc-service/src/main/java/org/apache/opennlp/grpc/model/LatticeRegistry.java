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

import opennlp.tools.tokenize.lattice.LatticeTokenizer;
import opennlp.tools.tokenize.lattice.MecabDictionary;
import org.apache.opennlp.grpc.spi.AnalysisException;

/**
 * Catalog of lattice tokenizers over MeCab-format dictionaries, keyed by dictionary id.
 *
 * <p>Dictionaries are configured as {@code model.lattice.<id>.dir} entries naming an
 * installed dictionary directory and are loaded eagerly at startup, so a bad path or
 * malformed dictionary fails the server start instead of the first request. The
 * built {@link LatticeTokenizer} reads only immutable dictionary state and is shared.
 * When several dictionaries are configured, {@code model.lattice.default_id} selects
 * the one an unqualified request gets; with exactly one configured, it is the
 * default.</p>
 */
public final class LatticeRegistry {

  /** Configuration namespace token for lattice dictionaries. */
  static final String NAMESPACE = "lattice";

  /** Prefix for dictionary directory entries: {@code model.lattice.<id>.dir}. */
  public static final String KEY_PREFIX = "model." + NAMESPACE + ".";

  /** Suffix completing a directory key. */
  public static final String KEY_SUFFIX = ".dir";

  /** Configuration key selecting the default dictionary when several are configured. */
  public static final String KEY_DEFAULT_ID = "model." + NAMESPACE + ".default_id";

  private final Map<String, LatticeTokenizer> tokenizers;
  private final String defaultId;

  private LatticeRegistry(Map<String, LatticeTokenizer> tokenizers, String defaultId) {
    this.tokenizers = tokenizers;
    this.defaultId = defaultId;
  }

  /**
   * Loads every dictionary configured under the {@code model.lattice.*} namespace.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   * @return A registry over the configured dictionaries, possibly empty. Never
   *         {@code null}.
   * @throws AnalysisException If a configured directory does not exist or fails to
   *         load, or if {@code model.lattice.default_id} names an unconfigured id.
   */
  public static LatticeRegistry create(Map<String, String> configuration) {
    final Map<String, LatticeTokenizer> tokenizers = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_PREFIX) || !key.endsWith(KEY_SUFFIX)) {
        continue;
      }
      final String id = key.substring(KEY_PREFIX.length(), key.length() - KEY_SUFFIX.length());
      if (id.isBlank() || id.contains(".")) {
        throw AnalysisException.invalidArgument(
            "Invalid lattice dictionary id in configuration key '" + key
                + "'; ids must be non-blank and must not contain '.'");
      }
      final Path directory = Path.of(entry.getValue());
      if (!Files.isDirectory(directory)) {
        throw AnalysisException.invalidArgument(
            "Configured lattice dictionary directory not found: " + entry.getValue());
      }
      try {
        tokenizers.put(id, new LatticeTokenizer(MecabDictionary.load(directory)));
      } catch (IOException | IllegalArgumentException e) {
        throw AnalysisException.invalidArgument(
            "Failed to load lattice dictionary '" + id + "': " + e.getMessage());
      }
    }
    String defaultId = configuration.get(KEY_DEFAULT_ID);
    if (defaultId != null && !tokenizers.containsKey(defaultId)) {
      throw AnalysisException.invalidArgument(
          KEY_DEFAULT_ID + " names an unconfigured lattice dictionary: " + defaultId);
    }
    if (defaultId == null && tokenizers.size() == 1) {
      defaultId = tokenizers.keySet().iterator().next();
    }
    return new LatticeRegistry(Map.copyOf(tokenizers), defaultId);
  }

  /**
   * Reports whether any lattice dictionary is configured.
   *
   * @return {@code true} when at least one dictionary is configured.
   */
  public boolean isAvailable() {
    return !tokenizers.isEmpty();
  }

  /**
   * Returns the configured dictionary ids in stable lexical order.
   *
   * @return An immutable list, possibly empty.
   */
  public List<String> ids() {
    return tokenizers.keySet().stream().sorted().toList();
  }

  /**
   * Reports whether an unqualified request resolves to the given dictionary id.
   *
   * @param dictionaryId The configured dictionary id to inspect.
   * @return {@code true} when this is the configured or implicit default.
   */
  public boolean isDefault(String dictionaryId) {
    return dictionaryId != null && dictionaryId.equals(defaultId);
  }

  /**
   * Resolves the dictionary id to serve a request with.
   *
   * @param requested The explicitly requested id, or {@code null} for the default.
   * @return The resolved id. Never {@code null}.
   * @throws AnalysisException If the requested id is unknown, or nothing was requested
   *         and no default can be determined.
   */
  public String resolveDictionaryId(String requested) {
    if (requested != null && !requested.isBlank()) {
      if (!tokenizers.containsKey(requested)) {
        throw AnalysisException.notFound("Unknown lattice dictionary: " + requested);
      }
      return requested;
    }
    if (defaultId == null) {
      throw AnalysisException.notFound(tokenizers.isEmpty()
          ? "No lattice dictionary is configured on this server"
          : "lattice_dictionary_id is required when multiple dictionaries are configured");
    }
    return defaultId;
  }

  /**
   * Retrieves a loaded tokenizer.
   *
   * @param dictionaryId A dictionary id previously resolved by
   *                     {@link #resolveDictionaryId(String)}.
   * @return The shared tokenizer. Never {@code null}.
   * @throws AnalysisException If the id is unknown, which indicates a server-side bug
   *         since ids are validated up front.
   */
  public LatticeTokenizer get(String dictionaryId) {
    final LatticeTokenizer tokenizer = tokenizers.get(dictionaryId);
    if (tokenizer == null) {
      throw AnalysisException.internal(
          "Lattice dictionary '" + dictionaryId + "' is not registered", null);
    }
    return tokenizer;
  }
}
