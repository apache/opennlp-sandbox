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
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.namefind.DictionaryNameFinder;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.opennlp.grpc.spi.model.NerModel;
import org.apache.opennlp.grpc.spi.model.NerBackendFactory;
import org.apache.opennlp.grpc.spi.model.NerBackendContext;
import org.apache.opennlp.grpc.spi.model.StatelessNerModel;
import org.apache.opennlp.grpc.spi.ModelArtifactHasher;

/**
 * Built-in model-free NER backend for OpenNLP dictionary name finders. Reads
 * {@code model.name_finder_dictionary.<entity_type>.path} entries, one dictionary file per
 * entity type: either a serialized OpenNLP dictionary (its XML declares case sensitivity)
 * or a plain wordlist with one entry per line, matched case-insensitively.
 */
public final class DictionaryNerBackendFactory implements NerBackendFactory {

  /** Prefix for per-type dictionary name finder path entries. */
  public static final String KEY_PREFIX = "model.name_finder_dictionary.";

  static final String FACTORY_ID = "dictionary";

  private static final String XML_DECLARATION = "<?xml";

  private static final Logger logger = LoggerFactory.getLogger(DictionaryNerBackendFactory.class);

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public DictionaryNerBackendFactory() {
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
        configuration, KEY_PREFIX, "Dictionary name finder", List.of());
    final List<NerModel> models = new ArrayList<>(entries.size());
    for (Map.Entry<String, NerPathConfig.Entry> entry : entries.entrySet()) {
      models.add(load(entry.getKey(), entry.getValue()));
    }
    return models;
  }

  /** Loads one dictionary file and wraps it as a stateless recognizer. */
  private static NerModel load(String entityType, NerPathConfig.Entry entry) {
    final byte[] bytes;
    try {
      bytes = Files.readAllBytes(Path.of(entry.path()));
    } catch (NoSuchFileException e) {
      throw AnalysisException.notFound("Dictionary name finder file for entity type '"
          + entityType + "' not found: " + entry.path());
    } catch (IOException e) {
      throw AnalysisException.internal("Failed to read dictionary name finder file for "
          + "entity type '" + entityType + "' from " + entry.path(), e);
    }
    final Dictionary dictionary = parseDictionary(entityType, entry.path(), bytes);
    if (dictionary.getMaxTokenCount() == 0) {
      throw AnalysisException.invalidArgument("Dictionary name finder file for entity type '"
          + entityType + "' contains no entries: " + entry.path());
    }
    logger.info("Loaded dictionary name finder for entity type '{}' from {} ({} max tokens)",
        entityType, entry.path(), dictionary.getMaxTokenCount());
    return new StatelessNerModel(entityType,
        new DictionaryNameFinder(dictionary, entityType), FACTORY_ID,
        entry.priority(), ModelArtifactHasher.sha256Hex(bytes));
  }

  /** Parses either a serialized OpenNLP dictionary or a one-entry-per-line wordlist. */
  private static Dictionary parseDictionary(String entityType, String path, byte[] bytes) {
    try {
      final String text = new String(bytes, StandardCharsets.UTF_8);
      if (text.stripLeading().startsWith(XML_DECLARATION)) {
        return new Dictionary(new ByteArrayInputStream(bytes));
      }
      return Dictionary.parseOneEntryPerLine(new StringReader(text));
    } catch (IOException e) {
      throw AnalysisException.invalidArgument("Dictionary name finder file for entity type '"
          + entityType + "' is not a valid dictionary: " + path + " (" + e.getMessage() + ")");
    }
  }
}
