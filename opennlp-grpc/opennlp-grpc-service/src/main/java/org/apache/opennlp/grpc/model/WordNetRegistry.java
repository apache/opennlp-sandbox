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

import opennlp.wordnet.LexicalExpander;
import opennlp.wordnet.WnLmfReader;
import org.apache.opennlp.grpc.spi.AnalysisException;

/**
 * Catalog of lexical expanders over WordNet-style knowledge bases, keyed by lexicon id.
 *
 * <p>Lexicons are configured as {@code model.wordnet.<id>.path} entries naming a
 * WN-LMF file, plain or gzipped, and are loaded eagerly at startup, so a bad path or malformed lexicon
 * fails the server start instead of the first request. The built
 * {@link LexicalExpander} is thread-safe and shared. When several lexicons are
 * configured, {@code model.wordnet.default_id} selects the one an unqualified request
 * gets; with exactly one configured, it is the default.</p>
 */
public final class WordNetRegistry {

  /** Configuration namespace token for lexical knowledge bases. */
  static final String NAMESPACE = "wordnet";

  /** Prefix for lexicon path entries: {@code model.wordnet.<id>.path}. */
  public static final String KEY_PREFIX = "model." + NAMESPACE + ".";

  /** Suffix completing a path key. */
  public static final String KEY_SUFFIX = ".path";

  /** Configuration key selecting the default lexicon when several are configured. */
  public static final String KEY_DEFAULT_ID = "model." + NAMESPACE + ".default_id";

  private final Map<String, LexicalExpander> expanders;
  private final String defaultId;

  /**
   * Reads a WN-LMF lexicon, inflating it first when the file name ends in {@code .gz}
   * so the distributed Open English WordNet archive is served as published.
   *
   * @param path The lexicon file.
   * @return The parsed knowledge base.
   * @throws IOException If the file cannot be read or parsed.
   */
  private static opennlp.tools.wordnet.LexicalKnowledgeBase readLexicon(Path path) throws IOException {
    final String name = path.getFileName().toString();
    if (!name.endsWith(".gz")) {
      return WnLmfReader.read(path);
    }
    try (java.io.InputStream in = new java.util.zip.GZIPInputStream(
        new java.io.BufferedInputStream(Files.newInputStream(path)))) {
      return WnLmfReader.read(in, name);
    }
  }

  /**
   * Lists the configured lexicon ids.
   *
   * @return The ids in configuration order.
   */
  public java.util.Set<String> lexiconIds() {
    return java.util.Collections.unmodifiableSet(expanders.keySet());
  }

  private WordNetRegistry(Map<String, LexicalExpander> expanders, String defaultId) {
    this.expanders = expanders;
    this.defaultId = defaultId;
  }

  /**
   * Loads every lexicon configured under the {@code model.wordnet.*} namespace.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   * @return A registry over the configured lexicons, possibly empty. Never
   *         {@code null}.
   * @throws AnalysisException If a configured path does not exist or fails to load, or
   *         if {@code model.wordnet.default_id} names an unconfigured id.
   */
  public static WordNetRegistry create(Map<String, String> configuration) {
    final Map<String, LexicalExpander> expanders = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_PREFIX) || !key.endsWith(KEY_SUFFIX)) {
        continue;
      }
      final String id = key.substring(KEY_PREFIX.length(), key.length() - KEY_SUFFIX.length());
      if (id.isBlank() || id.contains(".")) {
        throw AnalysisException.invalidArgument(
            "Invalid WordNet lexicon id in configuration key '" + key
                + "'; ids must be non-blank and must not contain '.'");
      }
      final Path path = Path.of(entry.getValue());
      if (!Files.isRegularFile(path)) {
        throw AnalysisException.invalidArgument(
            "Configured WordNet lexicon file not found: " + entry.getValue());
      }
      try {
        expanders.put(id, LexicalExpander.builder(readLexicon(path)).build());
      } catch (IOException | IllegalArgumentException e) {
        throw AnalysisException.invalidArgument(
            "Failed to load WordNet lexicon '" + id + "': " + e.getMessage());
      }
    }
    String defaultId = configuration.get(KEY_DEFAULT_ID);
    if (defaultId != null && !expanders.containsKey(defaultId)) {
      throw AnalysisException.invalidArgument(
          KEY_DEFAULT_ID + " names an unconfigured WordNet lexicon: " + defaultId);
    }
    if (defaultId == null && expanders.size() == 1) {
      defaultId = expanders.keySet().iterator().next();
    }
    return new WordNetRegistry(Map.copyOf(expanders), defaultId);
  }

  /**
   * Reports whether any WordNet lexicon is configured.
   *
   * @return {@code true} when at least one lexicon is configured.
   */
  public boolean isAvailable() {
    return !expanders.isEmpty();
  }

  /**
   * Returns the configured lexicon ids in stable lexical order.
   *
   * @return An immutable list, possibly empty.
   */
  public List<String> ids() {
    return expanders.keySet().stream().sorted().toList();
  }

  /**
   * Reports whether an unqualified request resolves to the given lexicon id.
   *
   * @param lexiconId The configured lexicon id to inspect.
   * @return {@code true} when this is the configured or implicit default.
   */
  public boolean isDefault(String lexiconId) {
    return lexiconId != null && lexiconId.equals(defaultId);
  }

  /**
   * Resolves the lexicon id to serve a request with.
   *
   * @param requested The explicitly requested id, or {@code null} for the default.
   * @return The resolved id. Never {@code null}.
   * @throws AnalysisException If the requested id is unknown, or nothing was requested
   *         and no default can be determined.
   */
  public String resolveLexiconId(String requested) {
    if (requested != null && !requested.isBlank()) {
      if (!expanders.containsKey(requested)) {
        throw AnalysisException.notFound("Unknown WordNet lexicon: " + requested);
      }
      return requested;
    }
    if (defaultId == null) {
      throw AnalysisException.notFound(expanders.isEmpty()
          ? "PIPELINE_STEP_EXPAND requested but no WordNet lexicon is configured on this server; "
              + "set model.wordnet.<id>.path"
          : "wordnet_lexicon_id is required when multiple lexicons are configured");
    }
    return defaultId;
  }

  /**
   * Retrieves a loaded expander.
   *
   * @param lexiconId A lexicon id previously resolved by {@link #resolveLexiconId(String)}.
   * @return The shared, thread-safe expander. Never {@code null}.
   * @throws AnalysisException If the id is unknown, which indicates a server-side bug
   *         since ids are validated up front.
   */
  public LexicalExpander get(String lexiconId) {
    final LexicalExpander expander = expanders.get(lexiconId);
    if (expander == null) {
      throw AnalysisException.internal(
          "WordNet lexicon '" + lexiconId + "' is not registered", null);
    }
    return expander;
  }
}
