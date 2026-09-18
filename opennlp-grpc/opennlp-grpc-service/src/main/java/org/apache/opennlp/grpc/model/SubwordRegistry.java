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

import opennlp.subword.sentencepiece.SentencePieceTokenizer;
import org.apache.opennlp.grpc.spi.AnalysisException;

/**
 * Catalog of subword tokenizers, keyed by model id.
 *
 * <p>Models are configured as {@code model.subword.<id>.path} entries naming a
 * SentencePiece {@code .model} file and are loaded eagerly at startup, so a bad path
 * fails the server start instead of the first request. The loaded
 * {@link SentencePieceTokenizer} is thread-safe and shared. When several models are
 * configured, {@code model.subword.default_id} selects the one an unqualified request
 * gets; with exactly one model configured, it is the default.</p>
 */
public final class SubwordRegistry {

  /** Configuration namespace token for subword models. */
  static final String NAMESPACE = "subword";

  /** Prefix for subword model path entries: {@code model.subword.<id>.path}. */
  public static final String KEY_PREFIX = "model." + NAMESPACE + ".";

  /** Suffix completing a path key. */
  public static final String KEY_SUFFIX = ".path";

  /** Configuration key selecting the default subword model when several are configured. */
  public static final String KEY_DEFAULT_ID = "model." + NAMESPACE + ".default_id";

  private final Map<String, SentencePieceTokenizer> tokenizers;
  private final String defaultId;

  private SubwordRegistry(Map<String, SentencePieceTokenizer> tokenizers, String defaultId) {
    this.tokenizers = tokenizers;
    this.defaultId = defaultId;
  }

  /**
   * Loads every subword model configured under the {@code model.subword.*} namespace.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   * @return A registry over the configured models, possibly empty. Never {@code null}.
   * @throws AnalysisException If a configured path does not exist or fails to load, or
   *         if {@code model.subword.default_id} names an unconfigured id.
   */
  public static SubwordRegistry create(Map<String, String> configuration) {
    final Map<String, SentencePieceTokenizer> tokenizers = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_PREFIX) || !key.endsWith(KEY_SUFFIX)) {
        continue;
      }
      final String id = key.substring(KEY_PREFIX.length(), key.length() - KEY_SUFFIX.length());
      if (id.isBlank() || id.contains(".")) {
        throw AnalysisException.invalidArgument(
            "Invalid subword model id in configuration key '" + key
                + "'; ids must be non-blank and must not contain '.'");
      }
      final Path path = Path.of(entry.getValue());
      if (!Files.isRegularFile(path)) {
        throw AnalysisException.invalidArgument(
            "Configured subword model file not found: " + entry.getValue());
      }
      try {
        tokenizers.put(id, SentencePieceTokenizer.load(path));
      } catch (IOException | IllegalArgumentException e) {
        throw AnalysisException.internal(
            "Failed to load subword model '" + id + "' from " + entry.getValue(), e);
      }
    }
    String defaultId = configuration.get(KEY_DEFAULT_ID);
    if (defaultId != null && !tokenizers.containsKey(defaultId)) {
      throw AnalysisException.invalidArgument(
          KEY_DEFAULT_ID + " names an unconfigured subword model: " + defaultId);
    }
    if (defaultId == null && tokenizers.size() == 1) {
      defaultId = tokenizers.keySet().iterator().next();
    }
    return new SubwordRegistry(Map.copyOf(tokenizers), defaultId);
  }

  /**
   * Reports whether any subword tokenizer is configured.
   *
   * @return {@code true} when at least one subword model is configured.
   */
  public boolean isAvailable() {
    return !tokenizers.isEmpty();
  }

  /**
   * Returns the configured model ids in stable lexical order.
   *
   * @return An immutable list, possibly empty.
   */
  public List<String> ids() {
    return tokenizers.keySet().stream().sorted().toList();
  }

  /**
   * Reports whether an unqualified request resolves to the given model id.
   *
   * @param modelId The configured model id to inspect.
   * @return {@code true} when this is the configured or implicit default.
   */
  public boolean isDefault(String modelId) {
    return modelId != null && modelId.equals(defaultId);
  }

  /**
   * Resolves the model id to serve a request with.
   *
   * @param requested The explicitly requested id, or {@code null} for the default.
   * @return The resolved id. Never {@code null}.
   * @throws AnalysisException If the requested id is unknown, or nothing was requested
   *         and no default can be determined.
   */
  public String resolveModelId(String requested) {
    if (requested != null && !requested.isBlank()) {
      if (!tokenizers.containsKey(requested)) {
        throw AnalysisException.notFound("Unknown subword model: " + requested);
      }
      return requested;
    }
    if (defaultId == null) {
      throw AnalysisException.notFound(tokenizers.isEmpty()
          ? "PIPELINE_STEP_SUBWORD_TOKENIZE requested but no subword model is configured on this "
              + "server; set model.subword.<id>.path"
          : "subword_model_id is required when multiple subword models are configured");
    }
    return defaultId;
  }

  /**
   * Retrieves a loaded tokenizer.
   *
   * @param modelId A model id previously resolved by {@link #resolveModelId(String)}.
   * @return The shared, thread-safe tokenizer. Never {@code null}.
   * @throws AnalysisException If the id is unknown, which indicates a server-side bug
   *         since ids are validated up front.
   */
  public SentencePieceTokenizer get(String modelId) {
    final SentencePieceTokenizer tokenizer = tokenizers.get(modelId);
    if (tokenizer == null) {
      throw AnalysisException.internal(
          "Subword model '" + modelId + "' is not registered", null);
    }
    return tokenizer;
  }
}
