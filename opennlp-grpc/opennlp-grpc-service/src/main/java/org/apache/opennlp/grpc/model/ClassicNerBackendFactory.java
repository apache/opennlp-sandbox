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
import java.util.List;
import java.util.Map;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.opennlp.grpc.spi.model.NerModel;
import org.apache.opennlp.grpc.spi.model.NerBackendFactory;
import org.apache.opennlp.grpc.spi.model.NerBackendContext;
import org.apache.opennlp.grpc.spi.ModelArtifactHasher;

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

  /** {@inheritDoc} */
  @Override
  public String factoryId() {
    return FACTORY_ID;
  }

  /** {@inheritDoc} */
  @Override
  public List<NerModel> create(Map<String, String> configuration, NerBackendContext context) {
    // The ONNX namespace is handled by its own backend; never read it here.
    final Map<String, NerPathConfig.Entry> entries = NerPathConfig.parse(configuration,
        KEY_PREFIX, "Name finder", List.of(NameFinderRegistry.KEY_DL_PREFIX));
    final List<NerModel> models = new ArrayList<>(entries.size());
    for (Map.Entry<String, NerPathConfig.Entry> entry : entries.entrySet()) {
      final LoadedClassicNer loaded = loadNameFinder(entry.getKey(), entry.getValue().path());
      models.add(new ClassicNerModel(entry.getKey(), loaded.nameFinder(),
          entry.getValue().priority(), loaded.artifactHash()));
    }
    return models;
  }

  /**
   * A classic name finder loaded from disk together with its artifact hash.
   *
   * @param nameFinder   The loaded recognizer. Never {@code null}.
   * @param artifactHash The lowercase hex SHA-256 digest of the model file.
   */
  private record LoadedClassicNer(NameFinderME nameFinder, String artifactHash) {
  }

  /** Loads name finder. */
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
