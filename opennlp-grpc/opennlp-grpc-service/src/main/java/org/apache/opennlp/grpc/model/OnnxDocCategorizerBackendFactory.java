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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.onnxruntime.OrtException;
import opennlp.dl.InferenceOptions;
import opennlp.dl.doccat.DocumentCategorizerDL;
import opennlp.dl.doccat.scoring.AverageClassificationScoringStrategy;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Built-in document categorization backend for ONNX transformer categorizers ({@code opennlp-dl}'s
 * {@link DocumentCategorizerDL}). Reads {@code model.doccat_dl.<id>.<attr>} entries; the categories
 * a model emits are read from its categories file (one category per line, line number = output
 * index).
 */
public final class OnnxDocCategorizerBackendFactory implements DocCategorizerBackendFactory {

  /** Prefix for ONNX document categorizer entries: {@code model.doccat_dl.<id>.<attr>}. */
  public static final String KEY_DL_PREFIX = "model.doccat_dl.";

  static final String FACTORY_ID = "onnx";

  private static final String BACKEND_ONNX = "onnx";
  private static final String BACKEND_CUDA = "cuda";

  private static final Logger logger =
      LoggerFactory.getLogger(OnnxDocCategorizerBackendFactory.class);

  @Override
  public String factoryId() {
    return FACTORY_ID;
  }

  @Override
  public List<DocCategorizerModel> create(Map<String, String> configuration) {
    final Map<String, DlConfig> configs = parseDlConfigs(configuration);
    final List<DocCategorizerModel> models = new ArrayList<>(configs.size());
    for (DlConfig config : configs.values()) {
      models.add(loadDlModel(config));
    }
    return models;
  }

  /** Resolved configuration for one ONNX document categorizer. */
  private record DlConfig(String id, String modelPath, String vocabPath, String categoriesPath,
      String backend, int gpuDeviceId) {
  }

  private static Map<String, DlConfig> parseDlConfigs(Map<String, String> configuration) {
    final Map<String, Map<String, String>> byId = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_DL_PREFIX)) {
        continue;
      }
      final String remainder = key.substring(KEY_DL_PREFIX.length());
      final int lastDot = remainder.lastIndexOf('.');
      if (lastDot <= 0 || lastDot == remainder.length() - 1) {
        throw AnalysisException.invalidArgument("Invalid ONNX document categorizer key: " + key);
      }
      final String id = DocCategorizerRegistry.normalize(remainder.substring(0, lastDot));
      if (id.isEmpty()) {
        throw AnalysisException.invalidArgument(
            "Invalid ONNX document categorizer key: " + key + "; id must not be blank");
      }
      final String attr = remainder.substring(lastDot + 1).trim().toLowerCase(Locale.ROOT);
      byId.computeIfAbsent(id, k -> new LinkedHashMap<>()).put(attr, entry.getValue());
    }
    final Map<String, DlConfig> configs = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, String>> entry : byId.entrySet()) {
      configs.put(entry.getKey(), toDlConfig(entry.getKey(), entry.getValue()));
    }
    return configs;
  }

  private static DlConfig toDlConfig(String id, Map<String, String> attrs) {
    final String modelPath = requiredAttr(id, attrs, "path");
    final String vocabPath = requiredAttr(id, attrs, "vocab");
    final String categoriesPath = requiredAttr(id, attrs, "categories");
    final String backend =
        attrs.getOrDefault("backend", BACKEND_ONNX).trim().toLowerCase(Locale.ROOT);
    if (!BACKEND_ONNX.equals(backend) && !BACKEND_CUDA.equals(backend)) {
      throw AnalysisException.invalidArgument(
          "ONNX document categorizer '" + id + "' has unsupported backend '" + backend
              + "'; expected '" + BACKEND_ONNX + "' or '" + BACKEND_CUDA + "'");
    }
    int gpuDeviceId = 0;
    final String gpu = attrs.get("gpu_device_id");
    if (gpu != null && !gpu.isBlank()) {
      try {
        gpuDeviceId = Integer.parseInt(gpu.trim());
      } catch (NumberFormatException e) {
        throw AnalysisException.invalidArgument(
            "ONNX document categorizer '" + id + "' has a non-numeric gpu_device_id: " + gpu);
      }
    }
    return new DlConfig(id, modelPath, vocabPath, categoriesPath, backend, gpuDeviceId);
  }

  private static String requiredAttr(String id, Map<String, String> attrs, String attr) {
    final String value = attrs.get(attr);
    if (value == null || value.isBlank()) {
      throw AnalysisException.invalidArgument(
          "ONNX document categorizer '" + id + "' is missing required '" + attr + "'");
    }
    return value.trim();
  }

  private static DocCategorizerModel loadDlModel(DlConfig config) {
    final File model = requireReadable(config.id(), "path", config.modelPath());
    final File vocab = requireReadable(config.id(), "vocab", config.vocabPath());
    final File categoriesFile = requireReadable(config.id(), "categories", config.categoriesPath());
    try {
      final Map<Integer, String> categories = loadCategories(categoriesFile);
      final InferenceOptions inferenceOptions = new InferenceOptions();
      if (BACKEND_CUDA.equals(config.backend())) {
        inferenceOptions.setGpu(true);
        inferenceOptions.setGpuDeviceId(config.gpuDeviceId());
      }
      final DocumentCategorizerDL categorizer = new DocumentCategorizerDL(
          model, vocab, categories, new AverageClassificationScoringStrategy(), inferenceOptions);
      logger.info("Loaded ONNX document categorizer '{}' ({} categories, backend '{}') from {}",
          config.id(), categories.size(), config.backend(), config.modelPath());
      return new OpenNlpDocCategorizerModel(config.id(), config.backend(), categorizer,
          OpenNlpDocCategorizerModel.InputMode.RAW_TEXT);
    } catch (IOException e) {
      throw AnalysisException.internal(
          "Failed to load ONNX document categorizer '" + config.id() + "'", e);
    } catch (OrtException e) {
      throw AnalysisException.internal(
          "Failed to create ONNX session for document categorizer '" + config.id() + "'", e);
    }
  }

  /**
   * Reads a category-per-line file, mapping each line number (0-based) to its category. A blank
   * line is rejected rather than skipped: skipping would leave a gap in the index map, and since
   * the line number is the model's output index, a gap silently misaligns scores to categories
   * (and yields a null category / NPE at model load).
   */
  private static Map<Integer, String> loadCategories(File categoriesFile) throws IOException {
    final List<String> lines = Files.readAllLines(categoriesFile.toPath(), StandardCharsets.UTF_8);
    final Map<Integer, String> categories = new HashMap<>();
    for (int i = 0; i < lines.size(); i++) {
      final String category = lines.get(i).trim();
      if (category.isEmpty()) {
        throw AnalysisException.invalidArgument("Categories file " + categoriesFile
            + " has a blank line at line " + (i + 1) + "; every line must name exactly one "
            + "category (line number = output index)");
      }
      categories.put(i, category);
    }
    if (categories.isEmpty()) {
      throw new IOException("Categories file is empty: " + categoriesFile);
    }
    return categories;
  }

  private static File requireReadable(String id, String attr, String path) {
    final File file = new File(path);
    if (!file.isFile() || !file.canRead()) {
      throw AnalysisException.notFound(
          "ONNX document categorizer '" + id + "' " + attr
              + " file not found or unreadable: " + path);
    }
    return file;
  }
}
