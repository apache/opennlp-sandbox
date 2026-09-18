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

import opennlp.tools.stemmer.hunspell.HunspellDictionary;
import opennlp.tools.stemmer.hunspell.HunspellStemmer;
import org.apache.opennlp.grpc.spi.AnalysisException;

/**
 * Catalog of hunspell affix dictionaries for the STEM step, keyed by dictionary id.
 *
 * <p>Dictionaries are configured as {@code model.hunspell.<id>.affix_path} (the
 * {@code .aff} file) and {@code model.hunspell.<id>.dictionary_path} (the {@code .dic}
 * file) pairs and are loaded eagerly at startup, so a bad path or a rejected directive
 * fails the server start instead of the first request. The loaded
 * {@link HunspellStemmer} is thread-safe and shared. When several dictionaries are
 * configured, {@code model.hunspell.default_id} selects the one an unqualified request
 * gets; with exactly one configured, it is the default.</p>
 */
public final class HunspellRegistry {

  /** Configuration namespace token for hunspell dictionaries. */
  static final String NAMESPACE = "hunspell";

  /** Prefix for hunspell entries: {@code model.hunspell.<id>.affix_path}. */
  public static final String KEY_PREFIX = "model." + NAMESPACE + ".";

  /** Suffix completing an affix-file key. */
  public static final String KEY_AFFIX_SUFFIX = ".affix_path";

  /** Suffix completing a word-list key. */
  public static final String KEY_DICTIONARY_SUFFIX = ".dictionary_path";

  /** Configuration key selecting the default dictionary when several are configured. */
  public static final String KEY_DEFAULT_ID = "model." + NAMESPACE + ".default_id";

  private final Map<String, HunspellStemmer> stemmers;
  private final String defaultId;

  private HunspellRegistry(Map<String, HunspellStemmer> stemmers, String defaultId) {
    this.stemmers = stemmers;
    this.defaultId = defaultId;
  }

  /**
   * Loads every hunspell dictionary configured under the {@code model.hunspell.*}
   * namespace.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   * @return A registry over the configured dictionaries, possibly empty. Never
   *         {@code null}.
   * @throws AnalysisException If an entry is missing its companion path, a file does
   *         not exist, the dictionary fails to load, or
   *         {@code model.hunspell.default_id} names an unconfigured id.
   */
  public static HunspellRegistry create(Map<String, String> configuration) {
    final Map<String, HunspellStemmer> stemmers = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_PREFIX) || !key.endsWith(KEY_AFFIX_SUFFIX)) {
        continue;
      }
      final String id =
          key.substring(KEY_PREFIX.length(), key.length() - KEY_AFFIX_SUFFIX.length());
      if (id.isBlank() || id.contains(".")) {
        throw AnalysisException.invalidArgument(
            "Invalid hunspell dictionary id in configuration key '" + key
                + "'; ids must be non-blank and must not contain '.'");
      }
      final String wordsValue = configuration.get(KEY_PREFIX + id + KEY_DICTIONARY_SUFFIX);
      if (wordsValue == null) {
        throw AnalysisException.invalidArgument(
            "Hunspell dictionary '" + id + "' declares an affix file but no "
                + KEY_PREFIX + id + KEY_DICTIONARY_SUFFIX);
      }
      final Path affix = Path.of(entry.getValue());
      final Path words = Path.of(wordsValue);
      if (!Files.isRegularFile(affix) || !Files.isRegularFile(words)) {
        throw AnalysisException.invalidArgument(
            "Configured hunspell dictionary file not found for '" + id + "'");
      }
      try {
        stemmers.put(id, new HunspellStemmer(HunspellDictionary.load(affix, words)));
      } catch (IOException | IllegalArgumentException e) {
        throw AnalysisException.invalidArgument(
            "Failed to load hunspell dictionary '" + id + "': " + e.getMessage());
      }
    }
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_PREFIX) || !key.endsWith(KEY_DICTIONARY_SUFFIX)) {
        continue;
      }
      final String id =
          key.substring(KEY_PREFIX.length(), key.length() - KEY_DICTIONARY_SUFFIX.length());
      if (id.isBlank() || id.contains(".")) {
        throw AnalysisException.invalidArgument(
            "Invalid hunspell dictionary id in configuration key '" + key
                + "'; ids must be non-blank and must not contain '.'");
      }
      if (!configuration.containsKey(KEY_PREFIX + id + KEY_AFFIX_SUFFIX)) {
        throw AnalysisException.invalidArgument(
            "Hunspell dictionary '" + id + "' declares " + key + " but no "
                + KEY_PREFIX + id + KEY_AFFIX_SUFFIX);
      }
    }
    String defaultId = configuration.get(KEY_DEFAULT_ID);
    if (defaultId != null && !stemmers.containsKey(defaultId)) {
      throw AnalysisException.invalidArgument(
          KEY_DEFAULT_ID + " names an unconfigured hunspell dictionary: " + defaultId);
    }
    if (defaultId == null && stemmers.size() == 1) {
      defaultId = stemmers.keySet().iterator().next();
    }
    return new HunspellRegistry(Map.copyOf(stemmers), defaultId);
  }

  /**
   * Reports whether any hunspell dictionary is configured.
   *
   * @return {@code true} when at least one hunspell dictionary is configured.
   */
  public boolean isAvailable() {
    return !stemmers.isEmpty();
  }

  /**
   * Returns the configured dictionary ids in stable lexical order.
   *
   * @return An immutable list, possibly empty.
   */
  public List<String> ids() {
    return stemmers.keySet().stream().sorted().toList();
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
      if (!stemmers.containsKey(requested)) {
        throw AnalysisException.notFound("Unknown hunspell dictionary: " + requested);
      }
      return requested;
    }
    if (defaultId == null) {
      throw AnalysisException.notFound(stemmers.isEmpty()
          ? "No hunspell dictionary is configured on this server"
          : "hunspell_dictionary_id is required when multiple dictionaries are configured");
    }
    return defaultId;
  }

  /**
   * Retrieves a loaded stemmer.
   *
   * @param dictionaryId A dictionary id previously resolved by
   *                     {@link #resolveDictionaryId(String)}.
   * @return The shared, thread-safe stemmer. Never {@code null}.
   * @throws AnalysisException If the id is unknown, which indicates a server-side bug
   *         since ids are validated up front.
   */
  public HunspellStemmer get(String dictionaryId) {
    final HunspellStemmer stemmer = stemmers.get(dictionaryId);
    if (stemmer == null) {
      throw AnalysisException.internal(
          "Hunspell dictionary '" + dictionaryId + "' is not registered", null);
    }
    return stemmer;
  }
}
