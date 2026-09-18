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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import opennlp.tools.namefind.RegexNameFinder;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.opennlp.grpc.spi.model.NerModel;
import org.apache.opennlp.grpc.spi.model.NerBackendFactory;
import org.apache.opennlp.grpc.spi.model.NerBackendContext;
import org.apache.opennlp.grpc.spi.model.StatelessNerModel;
import org.apache.opennlp.grpc.spi.ModelArtifactHasher;

/**
 * Built-in model-free NER backend for OpenNLP regex name finders. Reads
 * {@code model.name_finder_regex.<entity_type>.path} entries, one pattern file per entity
 * type: one Java regular expression per line, with blank lines and lines starting with
 * {@code #} ignored (match a literal leading {@code #} with {@code [#]}).
 */
public final class RegexNerBackendFactory implements NerBackendFactory {

  /** Prefix for per-type regex name finder path entries. */
  public static final String KEY_PREFIX = "model.name_finder_regex.";

  static final String FACTORY_ID = "regex";

  private static final char COMMENT_MARKER = '#';

  private static final Logger logger = LoggerFactory.getLogger(RegexNerBackendFactory.class);

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public RegexNerBackendFactory() {
  }

  /** {@inheritDoc} */
  @Override
  public String factoryId() {
    return FACTORY_ID;
  }

  /** {@inheritDoc} */
  @Override
  public List<NerModel> create(Map<String, String> configuration, NerBackendContext context) {
    final Map<String, NerPathConfig.Entry> entries = NerPathConfig.parse(
        configuration, KEY_PREFIX, "Regex name finder", List.of());
    final List<NerModel> models = new ArrayList<>(entries.size());
    for (Map.Entry<String, NerPathConfig.Entry> entry : entries.entrySet()) {
      models.add(load(entry.getKey(), entry.getValue()));
    }
    return models;
  }

  /** Loads one pattern file and wraps it as a stateless recognizer. */
  private static NerModel load(String entityType, NerPathConfig.Entry entry) {
    final byte[] bytes;
    try {
      bytes = Files.readAllBytes(Path.of(entry.path()));
    } catch (NoSuchFileException e) {
      throw AnalysisException.notFound("Regex name finder file for entity type '"
          + entityType + "' not found: " + entry.path());
    } catch (IOException e) {
      throw AnalysisException.internal("Failed to read regex name finder file for "
          + "entity type '" + entityType + "' from " + entry.path(), e);
    }
    final Pattern[] patterns = parsePatterns(entityType, entry.path(), bytes);
    logger.info("Loaded regex name finder for entity type '{}' from {} ({} patterns)",
        entityType, entry.path(), patterns.length);
    return new StatelessNerModel(entityType,
        new RegexNameFinder(patterns, entityType), FACTORY_ID,
        entry.priority(), ModelArtifactHasher.sha256Hex(bytes));
  }

  /** Parses the pattern file, failing loud with the offending line number. */
  private static Pattern[] parsePatterns(String entityType, String path, byte[] bytes) {
    final List<Pattern> patterns = new ArrayList<>();
    final String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n", -1);
    for (int index = 0; index < lines.length; index++) {
      final String line = lines[index].strip();
      if (line.isEmpty() || line.charAt(0) == COMMENT_MARKER) {
        continue;
      }
      try {
        patterns.add(Pattern.compile(line));
      } catch (PatternSyntaxException e) {
        throw AnalysisException.invalidArgument("Regex name finder file for entity type '"
            + entityType + "' has an invalid pattern at line " + (index + 1) + ": "
            + e.getDescription());
      }
    }
    if (patterns.isEmpty()) {
      throw AnalysisException.invalidArgument("Regex name finder file for entity type '"
          + entityType + "' contains no patterns: " + path);
    }
    return patterns.toArray(new Pattern[0]);
  }
}
