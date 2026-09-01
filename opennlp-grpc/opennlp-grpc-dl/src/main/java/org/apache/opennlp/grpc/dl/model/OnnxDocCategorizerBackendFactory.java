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
package org.apache.opennlp.grpc.dl.model;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OrtException;
import opennlp.tools.util.StringUtil;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.opennlp.grpc.spi.model.DocCategorizerModel;
import org.apache.opennlp.grpc.spi.model.ModelConfigSupport;
import org.apache.opennlp.grpc.spi.model.DocCategorizerBackendFactory;

/**
 * ServiceLoader factory for ONNX document categorizers served by the add-on's batched
 * transformer classifier. Reads {@code model.doccat_dl.<id>.<attr>} entries; the categories
 * file lists one category per line in output index order.
 */
public final class OnnxDocCategorizerBackendFactory implements DocCategorizerBackendFactory {

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public OnnxDocCategorizerBackendFactory() {
  }

  /** Prefix for ONNX document categorizer entries: {@code model.doccat_dl.<id>.<attr>}. */
  public static final String KEY_DL_PREFIX = "model.doccat_dl.";

  static final String FACTORY_ID = "onnx";

  private static final String BACKEND_ONNX = "onnx";
  private static final String BACKEND_CUDA = "cuda";

  private static final Logger logger =
      LoggerFactory.getLogger(OnnxDocCategorizerBackendFactory.class);

  /** {@inheritDoc} */
  @Override
  public String factoryId() {
    return FACTORY_ID;
  }

  /** {@inheritDoc} */
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
      String backend, int gpuDeviceId, boolean lowerCase) {
  }

  /** Parses ONNX document-categorizer configuration entries. */
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
      final String id = ModelConfigSupport.normalize(remainder.substring(0, lastDot));
      if (id.isEmpty()) {
        throw AnalysisException.invalidArgument(
            "Invalid ONNX document categorizer key: " + key + "; id must not be blank");
      }
      final String attr = StringUtil.toLowerCase(remainder.substring(lastDot + 1).trim());
      byId.computeIfAbsent(id, k -> new LinkedHashMap<>()).put(attr, entry.getValue());
    }
    final Map<String, DlConfig> configs = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, String>> entry : byId.entrySet()) {
      configs.put(entry.getKey(), toDlConfig(entry.getKey(), entry.getValue()));
    }
    return configs;
  }

  /** Validates and converts one ONNX configuration entry. */
  private static DlConfig toDlConfig(String id, Map<String, String> attrs) {
    final String modelPath = requiredAttr(id, attrs, "path");
    final String vocabPath = requiredAttr(id, attrs, "vocab");
    final String categoriesPath = requiredAttr(id, attrs, "categories");
    final String backend =
        StringUtil.toLowerCase(attrs.getOrDefault("backend", BACKEND_ONNX).trim());
    if (!BACKEND_ONNX.equals(backend) && !BACKEND_CUDA.equals(backend)) {
      throw AnalysisException.invalidArgument(
          "ONNX document categorizer '" + id + "' has unsupported backend '" + backend
              + "'; expected '" + BACKEND_ONNX + "' or '" + BACKEND_CUDA + "'");
    }
    int gpuDeviceId = 0;
    final String gpu = attrs.get("gpu_device_id");
    if (gpu != null && !gpu.isBlank()) {
      if (!BACKEND_CUDA.equals(backend)) {
        throw AnalysisException.invalidArgument(
            KEY_DL_PREFIX + id + ".gpu_device_id applies only with backend '"
                + BACKEND_CUDA + "' but ONNX document categorizer '" + id + "' uses backend '"
                + backend + "'");
      }
      try {
        gpuDeviceId = Integer.parseInt(gpu.trim());
      } catch (NumberFormatException e) {
        throw AnalysisException.invalidArgument(
            "ONNX document categorizer '" + id + "' has a non-numeric gpu_device_id: " + gpu);
      }
    }
    final String lowercase = attrs.getOrDefault("lowercase", "true").trim();
    if (!"true".equalsIgnoreCase(lowercase) && !"false".equalsIgnoreCase(lowercase)) {
      throw AnalysisException.invalidArgument(
          "ONNX document categorizer '" + id + "' has a non-boolean lowercase: " + lowercase);
    }
    return new DlConfig(id, modelPath, vocabPath, categoriesPath, backend, gpuDeviceId,
        Boolean.parseBoolean(lowercase));
  }

  /** Returns a required ONNX configuration attribute. */
  private static String requiredAttr(String id, Map<String, String> attrs, String attr) {
    final String value = attrs.get(attr);
    if (value == null || value.isBlank()) {
      throw AnalysisException.invalidArgument(
          "ONNX document categorizer '" + id + "' is missing required '" + attr + "'");
    }
    return value.trim();
  }

  /** Loads one ONNX document-categorizer model. */
  private static DocCategorizerModel loadDlModel(DlConfig config) {
    final File model = requireReadable(config.id(), "path", config.modelPath());
    final File vocab = requireReadable(config.id(), "vocab", config.vocabPath());
    final File categoriesFile = requireReadable(config.id(), "categories", config.categoriesPath());
    try {
      final Map<Integer, String> categoriesByIndex = loadCategories(categoriesFile);
      final List<String> categories = new ArrayList<>(categoriesByIndex.size());
      for (int i = 0; i < categoriesByIndex.size(); i++) {
        categories.add(categoriesByIndex.get(i));
      }
      final OnnxDocumentClassifier classifier = new OnnxDocumentClassifier(model, vocab,
          categories, BACKEND_CUDA.equals(config.backend()), config.gpuDeviceId(),
          config.lowerCase());
      logger.info("Loaded ONNX document categorizer '{}' ({} categories, backend '{}') from {}",
          config.id(), categories.size(), config.backend(), config.modelPath());
      return new OnnxDocCategorizerModel(config.id(), config.backend(), classifier);
    } catch (IOException e) {
      throw AnalysisException.internal(
          "Failed to load ONNX document categorizer '" + config.id() + "'", e);
    } catch (OrtException e) {
      throw AnalysisException.internal(
          "Failed to create ONNX session for document categorizer '" + config.id() + "'", e);
    } catch (IllegalArgumentException e) {
      throw AnalysisException.invalidArgument(
          "ONNX document categorizer '" + config.id() + "' is invalid: " + e.getMessage());
    }
  }

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

  /** Returns a required readable model artifact. */
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
