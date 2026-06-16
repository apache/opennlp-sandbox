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

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Built-in NER backend for classic OpenNLP maxent name finders. Reads
 * {@code model.name_finder.<entity_type>.path} entries, one Java-serialized
 * {@link TokenNameFinderModel} per entity type.
 */
public final class ClassicNerBackendFactory implements NerBackendFactory {

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public ClassicNerBackendFactory() {
  }

  /** Prefix for per-type classic name finder path entries. */
  public static final String KEY_PREFIX = "model.name_finder.";

  /** Suffix completing a per-type path key: {@code model.name_finder.<type>.path}. */
  public static final String KEY_SUFFIX = ".path";

  static final String FACTORY_ID = "classic";

  private static final Logger logger = LoggerFactory.getLogger(ClassicNerBackendFactory.class);

  @Override
  public String factoryId() {
    return FACTORY_ID;
  }

  /** Suffix completing a per-type priority key: {@code model.name_finder.<type>.priority}. */
  public static final String KEY_PRIORITY_SUFFIX = ".priority";

  @Override
  public List<NerModel> create(Map<String, String> configuration, NerBackendContext context) {
    final Map<String, ClassicEntry> entries = parseConfiguredPaths(configuration);
    final List<NerModel> models = new ArrayList<>(entries.size());
    for (Map.Entry<String, ClassicEntry> entry : entries.entrySet()) {
      final LoadedClassicNer loaded = loadNameFinder(entry.getKey(), entry.getValue().path());
      models.add(new ClassicNerModel(entry.getKey(), loaded.nameFinder(),
          entry.getValue().priority(), loaded.artifactHash()));
    }
    return models;
  }

  /** One classic recognizer's loaded configuration. */
  private record ClassicEntry(String path, int priority) {
  }

  /**
   * A classic name finder loaded from disk together with its artifact hash.
   *
   * @param nameFinder   The loaded recognizer. Never {@code null}.
   * @param artifactHash The lowercase hex SHA-256 digest of the model file.
   */
  private record LoadedClassicNer(NameFinderME nameFinder, String artifactHash) {
  }

  private static Map<String, ClassicEntry> parseConfiguredPaths(Map<String, String> configuration) {
    final Map<String, ClassicEntry> entries = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      // The ONNX namespace is handled by its own backend; never read it here.
      if (key.startsWith(OnnxNerBackendFactory.KEY_DL_PREFIX)) {
        continue;
      }
      if (!key.startsWith(KEY_PREFIX) || !key.endsWith(KEY_SUFFIX)) {
        continue;
      }
      final String base = key.substring(0, key.length() - KEY_SUFFIX.length());
      final String entityType = NameFinderRegistry.normalize(base.substring(KEY_PREFIX.length()));
      if (entityType.isEmpty()) {
        throw AnalysisException.invalidArgument(
            "Invalid name finder configuration key '" + key + "'; entity type must not be blank");
      }
      final String path = entry.getValue();
      if (path == null || path.isBlank()) {
        throw AnalysisException.invalidArgument(
            "Name finder path for entity type '" + entityType + "' must not be blank");
      }
      final int priority = NameFinderRegistry.parsePriority(
          base + KEY_PRIORITY_SUFFIX, configuration.get(base + KEY_PRIORITY_SUFFIX));
      if (entries.putIfAbsent(entityType, new ClassicEntry(path.trim(), priority)) != null) {
        throw AnalysisException.invalidArgument(
            "Duplicate name finder configuration for entity type '" + entityType + "'");
      }
    }
    return entries;
  }

  private static LoadedClassicNer loadNameFinder(String entityType, String path) {
    try {
      final byte[] bytes = Files.readAllBytes(Path.of(path));
      final TokenNameFinderModel model = new TokenNameFinderModel(new ByteArrayInputStream(bytes));
      logger.info("Loaded name finder for entity type '{}' from {}", entityType, path);
      return new LoadedClassicNer(new NameFinderME(model), ModelArtifactHasher.sha256Hex(bytes));
    } catch (NoSuchFileException e) {
      throw AnalysisException.notFound(
          "Name finder model file for entity type '" + entityType + "' not found: " + path);
    } catch (FileNotFoundException e) {
      // A missing configured path is an operator error, not an internal server fault.
      throw AnalysisException.notFound(
          "Name finder model file for entity type '" + entityType + "' not found: " + path);
    } catch (IOException e) {
      throw AnalysisException.internal(
          "Failed to load name finder model for entity type '" + entityType + "' from " + path, e);
    }
  }
}
