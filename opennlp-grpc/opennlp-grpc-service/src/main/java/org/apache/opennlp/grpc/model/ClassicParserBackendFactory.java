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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.processor.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Built-in parser backend for classic OpenNLP constituency parsers. Reads
 * {@code model.parser.<id>.path} entries, one serialized
 * {@link opennlp.tools.parser.ParserModel} per parser id, with an optional
 * {@code model.parser.<id>.priority}.
 */
public final class ClassicParserBackendFactory implements ParserBackendFactory {

  /** Prefix for classic parser entries: {@code model.parser.<id>.<attr>}. */
  public static final String KEY_PREFIX = "model.parser.";

  /** Suffix completing a parser path key: {@code model.parser.<id>.path}. */
  public static final String KEY_SUFFIX = ".path";

  /** Suffix completing a parser priority key: {@code model.parser.<id>.priority}. */
  public static final String KEY_PRIORITY_SUFFIX = ".priority";

  static final String FACTORY_ID = "classic";

  private static final Logger logger = LoggerFactory.getLogger(ClassicParserBackendFactory.class);

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public ClassicParserBackendFactory() {
  }

  @Override
  public String factoryId() {
    return FACTORY_ID;
  }

  @Override
  public List<ParserModel> create(Map<String, String> configuration) {
    final Map<String, ClassicEntry> entries = parseConfiguredPaths(configuration);
    final List<ParserModel> models = new ArrayList<>(entries.size());
    for (Map.Entry<String, ClassicEntry> entry : entries.entrySet()) {
      models.add(new ClassicParserModel(entry.getKey(),
          loadParserModel(entry.getKey(), entry.getValue().path()), entry.getValue().priority()));
    }
    return models;
  }

  /** One classic parser's loaded configuration. */
  private record ClassicEntry(String path, int priority) {
  }

  private static Map<String, ClassicEntry> parseConfiguredPaths(Map<String, String> configuration) {
    final Map<String, ClassicEntry> entries = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_PREFIX) || !key.endsWith(KEY_SUFFIX)) {
        continue;
      }
      final String base = key.substring(0, key.length() - KEY_SUFFIX.length());
      final String id = ParserRegistry.normalize(base.substring(KEY_PREFIX.length()));
      if (id.isEmpty()) {
        throw AnalysisException.invalidArgument(
            "Invalid parser configuration key '" + key + "'; parser id must not be blank");
      }
      final String path = entry.getValue();
      if (path == null || path.isBlank()) {
        throw AnalysisException.invalidArgument("Parser path for id '" + id + "' must not be blank");
      }
      final int priority = ParserRegistry.parsePriority(
          base + KEY_PRIORITY_SUFFIX, configuration.get(base + KEY_PRIORITY_SUFFIX));
      if (entries.putIfAbsent(id, new ClassicEntry(path.trim(), priority)) != null) {
        throw AnalysisException.invalidArgument("Duplicate parser configuration for id '" + id + "'");
      }
    }
    return entries;
  }

  private static opennlp.tools.parser.ParserModel loadParserModel(String id, String path) {
    try (InputStream input = new FileInputStream(path)) {
      final opennlp.tools.parser.ParserModel model = new opennlp.tools.parser.ParserModel(input);
      logger.info("Loaded parser '{}' from {}", id, path);
      return model;
    } catch (FileNotFoundException e) {
      throw AnalysisException.notFound(
          "Configured parser model file for id '" + id + "' not found: " + path);
    } catch (IOException e) {
      throw AnalysisException.internal(
          "Failed to load parser model for id '" + id + "' from " + path, e);
    }
  }
}
