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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import ai.onnxruntime.OrtException;
import opennlp.dl.InferenceOptions;
import opennlp.dl.namefinder.NameFinderDL;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetector;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads classic OpenNLP {@link NameFinderME} instances from server configuration.
 *
 * <p>Configuration keys follow the pattern {@code model.name_finder.<entity_type>.path},
 * where {@code entity_type} becomes the logical type name exposed to clients via
 * {@code AnalysisProfile.ner_entity_types} and {@code NamedEntity.entity_type}. Example:
 *
 * <pre>{@code
 * model.name_finder.person.path=/path/to/en-ner-person.bin
 * model.name_finder.organization.path=/path/to/en-ner-organization.bin
 * model.name_finder.location.path=/path/to/en-ner-location.bin
 * }</pre>
 *
 * <p>Each entry points at one Java-serialized {@link TokenNameFinderModel} ({@code .bin}).
 * ONNX-backed NER ({@code NameFinderDL}) is not handled here; it would use a separate
 * backend key in a follow-on change.</p>
 *
 * <p><b>Concurrency:</b> one {@link NameFinderME} instance per entity type is loaded once
 * and shared across all requests. This is safe because OpenNLP 3.0's {@code NameFinderME}
 * is {@code @ThreadSafe} (per-thread adaptive state). {@link #clearAdaptiveData()} is still
 * invoked after each document by the analyzer to enforce stateless RPC semantics: it resets
 * only the calling thread's adaptive state, so repeated requests never influence one
 * another. A non-thread-safe name finder implementation would invalidate this sharing.</p>
 */
public final class NameFinderRegistry {

  private static final Logger logger = LoggerFactory.getLogger(NameFinderRegistry.class);

  /** Prefix for per-type classic name finder path entries in server configuration. */
  public static final String KEY_PREFIX = "model.name_finder.";

  /** Suffix completing a per-type classic path key: {@code model.name_finder.<type>.path}. */
  public static final String KEY_SUFFIX = ".path";

  /** Prefix for ONNX name finder entries: {@code model.name_finder_dl.<id>.<attr>}. */
  public static final String KEY_DL_PREFIX = "model.name_finder_dl.";

  /** Backend ids reported for ONNX name finder models. */
  private static final String BACKEND_ONNX = "onnx";
  private static final String BACKEND_CUDA = "cuda";

  /** All recognizers keyed by model id (for classic models, the entity type). */
  private final Map<String, NerModel> modelsById;
  /** Index from normalized entity type to the models that can emit it. */
  private final Map<String, List<NerModel>> byEntityType;

  private NameFinderRegistry(Map<String, NerModel> modelsById,
      Map<String, List<NerModel>> byEntityType) {
    this.modelsById = Map.copyOf(modelsById);
    final Map<String, List<NerModel>> index = new LinkedHashMap<>();
    byEntityType.forEach((type, models) -> index.put(type, List.copyOf(models)));
    this.byEntityType = Map.copyOf(index);
  }

  /**
   * Canonical form of an entity type: trimmed and lower-cased. Configuration keys and
   * client-supplied {@code ner_entity_types} are normalized identically so entity types
   * are matched case-insensitively on both sides.
   *
   * @return The normalized type, or {@code null} if {@code entityType} is {@code null}.
   */
  public static String normalize(String entityType) {
    return entityType == null ? null : entityType.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Parses {@code model.name_finder.<entity_type>.path} entries and loads one
   * {@link NameFinderME} per configured type.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @return A registry, possibly empty when no name finder paths are configured.
   *
   * @throws AnalysisException If a configured path is blank or the model cannot be loaded.
   */
  public static NameFinderRegistry create(Map<String, String> configuration) {
    return create(configuration, null);
  }

  /**
   * Loads classic ({@code model.name_finder.*}) and ONNX ({@code model.name_finder_dl.*})
   * name finder models.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   * @param sentenceDetector The sentence detector ONNX name finders need internally; may be
   *     {@code null} when no ONNX name finder is configured.
   *
   * @return A registry, possibly empty when no name finder is configured.
   *
   * @throws AnalysisException If a configured path is blank/missing, a model cannot be
   *     loaded, or an ONNX name finder is configured without a sentence detector.
   */
  public static NameFinderRegistry create(
      Map<String, String> configuration, SentenceDetector sentenceDetector) {
    Objects.requireNonNull(configuration, "configuration");
    final Map<String, String> classicPaths = parseConfiguredPaths(configuration);
    final Map<String, DlConfig> dlConfigs = parseDlConfigs(configuration);
    if (classicPaths.isEmpty() && dlConfigs.isEmpty()) {
      return new NameFinderRegistry(Map.of(), Map.of());
    }
    final Map<String, NerModel> modelsById = new LinkedHashMap<>();
    final Map<String, List<NerModel>> byEntityType = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : classicPaths.entrySet()) {
      register(modelsById, byEntityType,
          new ClassicNerModel(entry.getKey(), loadNameFinder(entry.getKey(), entry.getValue())));
    }
    for (DlConfig dlConfig : dlConfigs.values()) {
      if (sentenceDetector == null) {
        throw AnalysisException.invalidArgument(
            "ONNX name finder '" + dlConfig.id() + "' is configured but no sentence detector "
                + "is available to drive it");
      }
      register(modelsById, byEntityType, loadDlModel(dlConfig, sentenceDetector));
    }
    return new NameFinderRegistry(modelsById, byEntityType);
  }

  private static void register(Map<String, NerModel> modelsById,
      Map<String, List<NerModel>> byEntityType, NerModel model) {
    if (modelsById.putIfAbsent(model.id(), model) != null) {
      throw AnalysisException.invalidArgument("Duplicate name finder model id: " + model.id());
    }
    for (String type : model.entityTypes()) {
      byEntityType.computeIfAbsent(type, key -> new ArrayList<>()).add(model);
    }
  }

  public boolean isAvailable() {
    return !modelsById.isEmpty();
  }

  /** @return All configured recognizers, in registration order, for catalog reporting. */
  public List<NerModel> allModels() {
    return List.copyOf(modelsById.values());
  }

  /**
   * @return Configured entity types in stable registration order.
   */
  public List<String> entityTypes() {
    return List.copyOf(byEntityType.keySet());
  }

  public boolean supportsEntityType(String entityType) {
    return entityType != null && byEntityType.containsKey(normalize(entityType));
  }

  /**
   * Resolves the distinct {@link NerModel}s that must run for the requested entity types:
   * every model that can emit at least one of them, each listed once. An empty or
   * {@code null} request selects all configured models. Running a model once and filtering
   * its output avoids invoking a multi-type model repeatedly.
   */
  public List<NerModel> modelsForTypes(List<String> requestedTypes) {
    if (requestedTypes == null || requestedTypes.isEmpty()) {
      return List.copyOf(modelsById.values());
    }
    final LinkedHashSet<NerModel> selected = new LinkedHashSet<>();
    for (String requestedType : requestedTypes) {
      final List<NerModel> models = byEntityType.get(normalize(requestedType));
      if (models != null) {
        selected.addAll(models);
      }
    }
    return List.copyOf(selected);
  }

  /**
   * Resolves which entity types to run for this request: an explicit
   * {@code AnalysisProfile.ner_entity_types} filter (normalized to the canonical form),
   * or all configured types when unset.
   */
  public List<String> resolveEntityTypes(List<String> requestedTypes) {
    if (requestedTypes == null || requestedTypes.isEmpty()) {
      return entityTypes();
    }
    final List<String> normalized = new ArrayList<>(requestedTypes.size());
    for (String requestedType : requestedTypes) {
      normalized.add(normalize(requestedType));
    }
    return List.copyOf(normalized);
  }

  /**
   * Clears adaptive feature-generator state on every loaded finder, as required by the
   * OpenNLP Name Finder API after each document when stateless RPC semantics are desired.
   */
  public void clearAdaptiveData() {
    for (NerModel model : modelsById.values()) {
      if (model.isStateful()) {
        model.clearAdaptiveData();
      }
    }
  }

  private static Map<String, String> parseConfiguredPaths(Map<String, String> configuration) {
    final Map<String, String> paths = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      // The ONNX namespace ('model.name_finder_dl.') is handled separately; never let it be
      // misread as a classic '<type>' (it does not start with KEY_PREFIX, but be explicit).
      if (key.startsWith(KEY_DL_PREFIX)) {
        continue;
      }
      if (!key.startsWith(KEY_PREFIX) || !key.endsWith(KEY_SUFFIX)) {
        continue;
      }
      final String entityType =
          normalize(key.substring(KEY_PREFIX.length(), key.length() - KEY_SUFFIX.length()));
      if (entityType.isEmpty()) {
        throw AnalysisException.invalidArgument(
            "Invalid name finder configuration key '" + key + "'; entity type must not be blank");
      }
      final String path = entry.getValue();
      if (path == null || path.isBlank()) {
        throw AnalysisException.invalidArgument(
            "Name finder path for entity type '" + entityType + "' must not be blank");
      }
      if (paths.putIfAbsent(entityType, path.trim()) != null) {
        throw AnalysisException.invalidArgument(
            "Duplicate name finder configuration for entity type '" + entityType + "'");
      }
    }
    return paths;
  }

  private static NameFinderME loadNameFinder(String entityType, String path) {
    try (InputStream input = new FileInputStream(path)) {
      final TokenNameFinderModel model = new TokenNameFinderModel(input);
      logger.info("Loaded name finder for entity type '{}' from {}", entityType, path);
      return new NameFinderME(model);
    } catch (FileNotFoundException e) {
      // A missing configured path is an operator error, not an internal server fault.
      throw AnalysisException.notFound(
          "Name finder model file for entity type '" + entityType + "' not found: " + path);
    } catch (IOException e) {
      throw AnalysisException.internal(
          "Failed to load name finder model for entity type '" + entityType + "' from " + path, e);
    }
  }

  /** Resolved configuration for one ONNX name finder. */
  private record DlConfig(String id, String modelPath, String vocabPath, String labelsPath,
      String backend, int gpuDeviceId) {
  }

  /**
   * Parses {@code model.name_finder_dl.<id>.<attr>} entries into one {@link DlConfig} per id.
   * Required attributes: {@code path}, {@code vocab}, {@code labels}. Optional:
   * {@code entity_type} (defaults to the id), {@code backend} ({@code onnx} or {@code cuda},
   * default {@code onnx}) and {@code gpu_device_id} (default {@code 0}).
   */
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
      final String id = normalize(remainder.substring(0, lastDot));
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
      final String normalized = normalize(label);
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
