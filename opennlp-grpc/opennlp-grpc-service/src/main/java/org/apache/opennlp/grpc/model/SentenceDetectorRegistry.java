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

import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.util.StringUtil;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.spi.model.SentenceDetectorBackendFactory;

/** Discovers and owns custom sentence detectors contributed through ServiceLoader. */
public final class SentenceDetectorRegistry implements AutoCloseable {

  private final SortedMap<String, SentenceDetector> detectors;

  private SentenceDetectorRegistry(SortedMap<String, SentenceDetector> detectors) {
    this.detectors = detectors;
  }

  /**
   * Discovers sentence-detector factories and creates the engines enabled by this configuration.
   *
   * @param configuration The complete server configuration. Must not be {@code null}.
   *
   * @return The custom sentence-detector registry. Never {@code null}.
   */
  public static SentenceDetectorRegistry create(Map<String, String> configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    final SortedMap<String, SentenceDetectorBackendFactory> factories = new TreeMap<>();
    for (SentenceDetectorBackendFactory factory
        : ServiceLoader.load(SentenceDetectorBackendFactory.class)) {
      final String id = validId(factory.engineId(), factory.getClass().getName());
      final SentenceDetectorBackendFactory duplicate = factories.putIfAbsent(id, factory);
      if (duplicate != null) {
        throw AnalysisException.invalidArgument(
            "Sentence detector engine id '" + id + "' is declared by both "
                + duplicate.getClass().getName() + " and " + factory.getClass().getName());
      }
    }

    final SortedMap<String, SentenceDetector> detectors = new TreeMap<>();
    try {
      for (Map.Entry<String, SentenceDetectorBackendFactory> entry : factories.entrySet()) {
        final Optional<SentenceDetector> detector = entry.getValue().create(configuration);
        if (detector == null) {
          throw new IllegalStateException(
              entry.getValue().getClass().getName() + ".create returned null");
        }
        detector.ifPresent(value -> detectors.put(entry.getKey(), value));
      }
      return new SentenceDetectorRegistry(detectors);
    } catch (RuntimeException e) {
      try {
        closeAll(detectors.values());
      } catch (RuntimeException closeFailure) {
        e.addSuppressed(closeFailure);
      }
      throw e;
    }
  }

  /**
   * Resolves one custom detector.
   *
   * @param id The custom detector id. Must not be {@code null}.
   *
   * @return The configured detector. Never {@code null}.
   * @throws AnalysisException If no configured custom detector has that id.
   */
  public SentenceDetector get(String id) {
    final SentenceDetector detector = detectors.get(id);
    if (detector == null) {
      throw AnalysisException.notFound(
          "Unknown custom sentence detector '" + id + "'; configured: " + ids());
    }
    return detector;
  }

  /**
   * Lists the configured custom sentence-detector ids.
   *
   * @return The ids in stable order.
   */
  public List<String> ids() {
    return List.copyOf(detectors.keySet());
  }

  /** {@inheritDoc} */
  @Override
  public void close() {
    closeAll(detectors.values());
  }

  /** Returns a normalized, validated provider id. Package-private for tests. */
  static String validId(String id, String owner) {
    if (id == null || id.isBlank() || !id.equals(StringUtil.toLowerCase(id))
        || id.chars().anyMatch(Character::isWhitespace)) {
      throw AnalysisException.invalidArgument(
          owner + " declares invalid sentence detector engine id '" + id
              + "'; ids must be non-blank, lower-case, and contain no whitespace");
    }
    return id;
  }

  /** Closes every configured provider. */
  private static void closeAll(Iterable<SentenceDetector> detectors) {
    final List<Exception> failures = new ArrayList<>();
    for (SentenceDetector detector : detectors) {
      if (detector instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception e) {
          failures.add(e);
        }
      }
    }
    if (!failures.isEmpty()) {
      final IllegalStateException error = new IllegalStateException(
          "Failed to close " + failures.size() + " sentence detector engine(s)");
      failures.forEach(error::addSuppressed);
      throw error;
    }
  }
}
