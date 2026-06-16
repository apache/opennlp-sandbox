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

import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Built-in document categorization backend for classic OpenNLP maxent categorizers. Reads
 * {@code model.doccat.<id>.path} entries, one serialized {@link DoccatModel} per model id.
 */
public final class ClassicDocCategorizerBackendFactory implements DocCategorizerBackendFactory {

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public ClassicDocCategorizerBackendFactory() {
  }

  /** Prefix for classic document categorizer path entries. */
  public static final String KEY_PREFIX = "model.doccat.";

  /** Suffix completing a classic path key: {@code model.doccat.<id>.path}. */
  public static final String KEY_SUFFIX = ".path";

  static final String FACTORY_ID = "classic";

  /** Backend id reported for models served by the classic OpenNLP maxent runtime. */
  static final String BACKEND_ID = "opennlp-me";

  private static final Logger logger =
      LoggerFactory.getLogger(ClassicDocCategorizerBackendFactory.class);

  @Override
  public String factoryId() {
    return FACTORY_ID;
  }

  @Override
  public List<DocCategorizerModel> create(Map<String, String> configuration) {
    final Map<String, String> paths = parseConfiguredPaths(configuration);
    final List<DocCategorizerModel> models = new ArrayList<>(paths.size());
    for (Map.Entry<String, String> entry : paths.entrySet()) {
      models.add(loadCategorizer(entry.getKey(), entry.getValue()));
    }
    return models;
  }

  private static Map<String, String> parseConfiguredPaths(Map<String, String> configuration) {
    final Map<String, String> paths = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      // The ONNX namespace is handled by its own backend; never read it here.
      if (key.startsWith(OnnxDocCategorizerBackendFactory.KEY_DL_PREFIX)) {
        continue;
      }
      // The default-model selector lives under this prefix but is not a model entry.
      if (key.equals(DocCategorizerRegistry.KEY_DEFAULT_ID)) {
        continue;
      }
      if (!key.startsWith(KEY_PREFIX) || !key.endsWith(KEY_SUFFIX)) {
        continue;
      }
      final String id = DocCategorizerRegistry.normalize(
          key.substring(KEY_PREFIX.length(), key.length() - KEY_SUFFIX.length()));
      if (id.isEmpty()) {
        throw AnalysisException.invalidArgument(
            "Invalid document categorizer configuration key '" + key + "'; id must not be blank");
      }
      final String path = entry.getValue();
      if (path == null || path.isBlank()) {
        throw AnalysisException.invalidArgument(
            "Document categorizer path for id '" + id + "' must not be blank");
      }
      if (paths.putIfAbsent(id, path.trim()) != null) {
        throw AnalysisException.invalidArgument(
            "Duplicate document categorizer configuration for id '" + id + "'");
      }
    }
    return paths;
  }

  private static DocCategorizerModel loadCategorizer(String id, String path) {
    try (InputStream input = new FileInputStream(path)) {
      final DocumentCategorizerME categorizer = new DocumentCategorizerME(new DoccatModel(input));
      logger.info("Loaded document categorizer '{}' from {}", id, path);
      return new OpenNlpDocCategorizerModel(id, BACKEND_ID, categorizer,
          OpenNlpDocCategorizerModel.InputMode.TOKENS);
    } catch (FileNotFoundException e) {
      // A missing configured path is an operator error, not an internal server fault.
      throw AnalysisException.notFound(
          "Document categorizer model file for id '" + id + "' not found: " + path);
    } catch (IOException e) {
      throw AnalysisException.internal(
          "Failed to load document categorizer model for id '" + id + "' from " + path, e);
    }
  }
}
