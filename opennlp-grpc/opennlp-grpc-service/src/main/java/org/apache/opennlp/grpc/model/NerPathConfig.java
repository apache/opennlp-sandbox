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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.spi.AnalysisException;

/**
 * Parses the per-entity-type path configuration shape the file-backed NER backends share:
 * {@code <prefix><entity_type>.path} with an optional {@code <prefix><entity_type>.priority}.
 */
final class NerPathConfig {

  /** Suffix completing a per-type path key. */
  static final String PATH_SUFFIX = ".path";

  /** Suffix completing a per-type priority key. */
  static final String PRIORITY_SUFFIX = ".priority";

  private NerPathConfig() {
  }

  /** One configured recognizer: its file path and selection priority. */
  record Entry(String path, int priority) {
  }

  /**
   * Reads every {@code <keyPrefix><entity_type>.path} entry of one backend's namespace.
   *
   * @param configuration The full server configuration. Must not be {@code null}.
   * @param keyPrefix The backend's key prefix, ending with a dot.
   * @param description The backend's name for error messages, e.g. "dictionary name finder".
   * @param skipPrefixes Longer key prefixes of other backends to leave untouched.
   *
   * @return Entries keyed by normalized entity type, in configuration order. Never {@code null}.
   * @throws AnalysisException {@code INVALID_ARGUMENT} on a blank entity type, a blank path,
   *     a duplicate entity type, or a non-integer priority.
   */
  static Map<String, Entry> parse(Map<String, String> configuration, String keyPrefix,
      String description, List<String> skipPrefixes) {
    final Map<String, Entry> entries = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (skipPrefixes.stream().anyMatch(key::startsWith)) {
        continue;
      }
      if (!key.startsWith(keyPrefix) || !key.endsWith(PATH_SUFFIX)) {
        continue;
      }
      final String base = key.substring(0, key.length() - PATH_SUFFIX.length());
      final String entityType = NameFinderRegistry.normalize(base.substring(keyPrefix.length()));
      if (entityType.isEmpty()) {
        throw AnalysisException.invalidArgument("Invalid " + description
            + " configuration key '" + key + "'; entity type must not be blank");
      }
      final String path = entry.getValue();
      if (path == null || path.isBlank()) {
        throw AnalysisException.invalidArgument(description + " path for entity type '"
            + entityType + "' must not be blank");
      }
      final int priority = NameFinderRegistry.parsePriority(
          base + PRIORITY_SUFFIX, configuration.get(base + PRIORITY_SUFFIX));
      if (entries.putIfAbsent(entityType, new Entry(path.trim(), priority)) != null) {
        throw AnalysisException.invalidArgument("Duplicate " + description
            + " configuration for entity type '" + entityType + "'");
      }
    }
    return entries;
  }
}
