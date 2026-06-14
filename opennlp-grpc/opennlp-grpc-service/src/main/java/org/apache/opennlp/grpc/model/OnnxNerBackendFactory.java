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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ai.onnxruntime.OrtException;
import opennlp.dl.InferenceOptions;
import opennlp.dl.namefinder.NameFinderDL;
import opennlp.tools.sentdetect.SentenceDetector;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Built-in NER backend for ONNX transformer name finders ({@code opennlp-dl}'s
 * {@link NameFinderDL}). Reads {@code model.name_finder_dl.<id>.<attr>} entries; the entity
 * types each model produces are derived from its BIO labels file.
 */
public final class OnnxNerBackendFactory implements NerBackendFactory {

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public OnnxNerBackendFactory() {
  }

  /** Prefix for ONNX name finder entries: {@code model.name_finder_dl.<id>.<attr>}. */
  public static final String KEY_DL_PREFIX = "model.name_finder_dl.";

  static final String FACTORY_ID = "onnx";

  private static final String BACKEND_ONNX = "onnx";
  private static final String BACKEND_CUDA = "cuda";

  private static final Logger logger = LoggerFactory.getLogger(OnnxNerBackendFactory.class);

  @Override
  public String factoryId() {
    return FACTORY_ID;
  }

  @Override
  public List<NerModel> create(Map<String, String> configuration, NerBackendContext context) {
    final Map<String, DlConfig> configs = parseDlConfigs(configuration);
    if (configs.isEmpty()) {
      return List.of();
    }
    final SentenceDetector sentenceDetector = context.requireSentenceDetector();
    final List<NerModel> models = new ArrayList<>(configs.size());
    for (DlConfig config : configs.values()) {
      models.add(loadDlModel(config, sentenceDetector));
    }
    return models;
  }

  /** Resolved configuration for one ONNX name finder. */
  private record DlConfig(String id, String modelPath, String vocabPath, String labelsPath,
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
        throw AnalysisException.invalidArgument("Invalid ONNX name finder key: " + key);
      }
      final String id = NameFinderRegistry.normalize(remainder.substring(0, lastDot));
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
    final String labelsPath = requiredAttr(id, attrs, "labels");
    final String backend = attrs.getOrDefault("backend", BACKEND_ONNX).trim().toLowerCase(Locale.ROOT);
    if (!BACKEND_ONNX.equals(backend) && !BACKEND_CUDA.equals(backend)) {
      throw AnalysisException.invalidArgument(
          "ONNX name finder '" + id + "' has unsupported backend '" + backend
              + "'; expected '" + BACKEND_ONNX + "' or '" + BACKEND_CUDA + "'");
    }
    int gpuDeviceId = 0;
    final String gpu = attrs.get("gpu_device_id");
    if (gpu != null && !gpu.isBlank()) {
      try {
        gpuDeviceId = Integer.parseInt(gpu.trim());
      } catch (NumberFormatException e) {
        throw AnalysisException.invalidArgument(
            "ONNX name finder '" + id + "' has a non-numeric gpu_device_id: " + gpu);
      }
    }
    return new DlConfig(id, modelPath, vocabPath, labelsPath, backend, gpuDeviceId);
  }

  private static String requiredAttr(String id, Map<String, String> attrs, String attr) {
    final String value = attrs.get(attr);
    if (value == null || value.isBlank()) {
      throw AnalysisException.invalidArgument(
          "ONNX name finder '" + id + "' is missing required '" + attr + "'");
    }
    return value.trim();
  }

  private static NerModel loadDlModel(DlConfig config, SentenceDetector sentenceDetector) {
    final File model = requireReadable(config.id(), "path", config.modelPath());
    final File vocab = requireReadable(config.id(), "vocab", config.vocabPath());
    final File labels = requireReadable(config.id(), "labels", config.labelsPath());
    try {
      final Map<Integer, String> ids2Labels = loadLabels(labels);
      final Set<String> entityTypes = entityTypesFromLabels(ids2Labels);
      if (entityTypes.isEmpty()) {
        throw AnalysisException.invalidArgument(
            "ONNX name finder '" + config.id() + "' labels define no entity types (only 'O'?)");
      }
      final InferenceOptions inferenceOptions = new InferenceOptions();
      if (BACKEND_CUDA.equals(config.backend())) {
        inferenceOptions.setGpu(true);
        inferenceOptions.setGpuDeviceId(config.gpuDeviceId());
      }
      final NameFinderDL nameFinderDL =
          new NameFinderDL(model, vocab, ids2Labels, inferenceOptions, sentenceDetector);
      logger.info("Loaded ONNX name finder '{}' (entity types {}, backend '{}') from {}",
          config.id(), entityTypes, config.backend(), config.modelPath());
      return new DlNerModel(config.id(), entityTypes, config.backend(), nameFinderDL);
    } catch (IOException e) {
      throw AnalysisException.internal(
          "Failed to load ONNX name finder '" + config.id() + "'", e);
    } catch (OrtException e) {
      throw AnalysisException.internal(
          "Failed to create ONNX session for name finder '" + config.id() + "'", e);
    }
  }

  /**
   * Derives the distinct entity types a BIO label set defines, with the {@code B-}/{@code I-}
   * prefixes stripped and normalized (e.g. {@code B-PER}/{@code I-LOC} -> {@code per},
   * {@code loc}); the outside label {@code O} contributes nothing.
   */
  private static Set<String> entityTypesFromLabels(Map<Integer, String> ids2Labels) {
    final Set<String> types = new LinkedHashSet<>();
    for (String label : ids2Labels.values()) {
      final String normalized = NameFinderRegistry.normalize(label);
      if (normalized.startsWith("b-") || normalized.startsWith("i-")) {
        types.add(normalized.substring(2));
      }
    }
    return types;
  }

  /** Reads a label-per-line file, mapping each line number (0-based) to its label. */
  private static Map<Integer, String> loadLabels(File labelsFile) throws IOException {
    final List<String> lines = Files.readAllLines(labelsFile.toPath(), StandardCharsets.UTF_8);
    final Map<Integer, String> ids2Labels = new HashMap<>();
    for (int i = 0; i < lines.size(); i++) {
      final String label = lines.get(i).trim();
      if (!label.isEmpty()) {
        ids2Labels.put(i, label);
      }
    }
    if (ids2Labels.isEmpty()) {
      throw new IOException("Label file is empty: " + labelsFile);
    }
    return ids2Labels;
  }

  private static File requireReadable(String id, String attr, String path) {
    final File file = new File(path);
    if (!file.isFile() || !file.canRead()) {
      throw AnalysisException.notFound(
          "ONNX name finder '" + id + "' " + attr + " file not found or unreadable: " + path);
    }
    return file;
  }
}
