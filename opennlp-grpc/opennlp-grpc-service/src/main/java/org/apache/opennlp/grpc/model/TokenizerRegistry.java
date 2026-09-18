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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.SortedMap;
import java.util.TreeMap;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.StringUtil;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.spi.model.TokenizerBackendFactory;

/** Discovers and owns custom tokenizer engines contributed through ServiceLoader. */
public final class TokenizerRegistry implements AutoCloseable {

  private final SortedMap<String, Tokenizer> tokenizers;

  private TokenizerRegistry(SortedMap<String, Tokenizer> tokenizers) {
    this.tokenizers = tokenizers;
  }

  /**
   * Discovers tokenizer factories and creates the engines enabled by this configuration.
   *
   * @param configuration The complete server configuration. Must not be {@code null}.
   *
   * @return The custom tokenizer registry. Never {@code null}.
   */
  public static TokenizerRegistry create(Map<String, String> configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    final SortedMap<String, TokenizerBackendFactory> factories = new TreeMap<>();
    for (TokenizerBackendFactory factory : ServiceLoader.load(TokenizerBackendFactory.class)) {
      final String id = validId(factory.engineId(), factory.getClass().getName());
      final TokenizerBackendFactory duplicate = factories.putIfAbsent(id, factory);
      if (duplicate != null) {
        throw AnalysisException.invalidArgument(
            "Tokenizer engine id '" + id + "' is declared by both "
                + duplicate.getClass().getName() + " and " + factory.getClass().getName());
      }
    }

    final SortedMap<String, Tokenizer> tokenizers = new TreeMap<>();
    try {
      for (Map.Entry<String, TokenizerBackendFactory> entry : factories.entrySet()) {
        final Optional<Tokenizer> tokenizer = entry.getValue().create(configuration);
        if (tokenizer == null) {
          throw new IllegalStateException(
              entry.getValue().getClass().getName() + ".create returned null");
        }
        tokenizer.ifPresent(value -> tokenizers.put(entry.getKey(), value));
      }
      return new TokenizerRegistry(tokenizers);
    } catch (RuntimeException e) {
      try {
        closeAll(tokenizers.values());
      } catch (RuntimeException closeFailure) {
        e.addSuppressed(closeFailure);
      }
      throw e;
    }
  }

  /**
   * Resolves one custom tokenizer.
   *
   * @param id The custom tokenizer id. Must not be {@code null}.
   *
   * @return The configured tokenizer. Never {@code null}.
   * @throws AnalysisException If no configured custom tokenizer has that id.
   */
  public Tokenizer get(String id) {
    final Tokenizer tokenizer = tokenizers.get(id);
    if (tokenizer == null) {
      throw AnalysisException.notFound(
          "Unknown custom tokenizer '" + id + "'; configured: " + ids());
    }
    return tokenizer;
  }

  /**
   * Lists the configured custom tokenizer ids.
   *
   * @return The ids in stable order.
   */
  public List<String> ids() {
    return List.copyOf(tokenizers.keySet());
  }

  /** {@inheritDoc} */
  @Override
  public void close() {
    closeAll(tokenizers.values());
  }

  /** Returns a normalized, validated provider id. Package-private for tests. */
  static String validId(String id, String owner) {
    if (id == null || id.isBlank() || !id.equals(StringUtil.toLowerCase(id))
        || id.chars().anyMatch(Character::isWhitespace)) {
      throw AnalysisException.invalidArgument(
          owner + " declares invalid tokenizer engine id '" + id
              + "'; ids must be non-blank, lower-case, and contain no whitespace");
    }
    return id;
  }

  /** Closes every configured provider. */
  private static void closeAll(Iterable<Tokenizer> tokenizers) {
    final List<Exception> failures = new ArrayList<>();
    for (Tokenizer tokenizer : tokenizers) {
      if (tokenizer instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception e) {
          failures.add(e);
        }
      }
    }
    if (!failures.isEmpty()) {
      final IllegalStateException error =
          new IllegalStateException("Failed to close " + failures.size() + " tokenizer engine(s)");
      failures.forEach(error::addSuppressed);
      throw error;
    }
  }
}
