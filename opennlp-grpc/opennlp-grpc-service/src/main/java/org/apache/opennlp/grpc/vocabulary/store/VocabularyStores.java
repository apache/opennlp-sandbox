/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.vocabulary.store;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ServiceLoader;

import opennlp.tools.util.StringUtil;
import org.apache.opennlp.grpc.spi.vocabulary.VocabularyStore;
import org.apache.opennlp.grpc.spi.vocabulary.VocabularyStoreProvider;

/** Resolves the configured artifact root to a {@link VocabularyStore} by URI scheme. */
public final class VocabularyStores {

  private VocabularyStores() {
  }

  /**
   * Opens the store for one configured artifact root. A plain path or a {@code file}
   * URI opens the filesystem store; any other scheme is served by the
   * {@link VocabularyStoreProvider} registered for it, so a remote tier plugs in by
   * adding its JAR to the classpath.
   *
   * @param configuredRoot The configured artifact root. Must not be blank.
   * @return The opened store. Never {@code null}.
   * @throws IOException Thrown if the root cannot be created or verified.
   * @throws IllegalArgumentException Thrown if the root is blank or no provider serves
   *         its scheme.
   */
  public static VocabularyStore open(String configuredRoot) throws IOException {
    if (configuredRoot == null || configuredRoot.isBlank()) {
      throw new IllegalArgumentException("configuredRoot must not be blank");
    }
    final URI parsed = parse(configuredRoot);
    if (parsed == null || parsed.getScheme() == null) {
      return new FileSystemVocabularyStore(Path.of(configuredRoot));
    }
    final String scheme = StringUtil.toLowerCase(parsed.getScheme());
    for (VocabularyStoreProvider provider
        : ServiceLoader.load(VocabularyStoreProvider.class)) {
      if (provider.scheme().equals(scheme)) {
        return provider.open(parsed);
      }
    }
    throw new IllegalArgumentException("No vocabulary store provider for scheme '"
        + scheme + "'; add the JAR that provides it to the classpath");
  }

  /** Parses the root as a URI, or returns {@code null} for a plain path. */
  private static URI parse(String configuredRoot) {
    try {
      return new URI(configuredRoot);
    } catch (URISyntaxException e) {
      return null;
    }
  }
}
