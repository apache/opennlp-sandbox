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
package org.apache.opennlp.grpc.embedding.statictable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.embeddings.StaticEmbeddingModel;
import opennlp.tools.util.StringUtil;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.spi.ModelArtifactHasher;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link EmbeddingProvider} serving static (non-contextual) embedding tables through the
 * {@code opennlp-embeddings} extension module: a per-token vector table plus WordPiece
 * tokenization, distilled from a sentence-transformer. Embedding is tokenize, gather,
 * mean-pool, and normalize; no model forward pass and no native runtime, so this backend
 * runs anywhere the server's JVM runs.
 *
 * <p>Each model is one immutable, thread-safe {@link StaticEmbeddingModel} shared by every
 * request thread; the provider holds no native resources and needs no shutdown hook.</p>
 *
 * <p>Embedding models are declared in the server configuration in one of two forms.
 * The directory form points at a published model directory and reads the tokenizer and
 * pooling switches from the model's own {@code config.json} and
 * {@code tokenizer_config.json}:</p>
 *
 * <pre>
 * model.embedder.&lt;model-id&gt;.static.dir=/models/my-static-model
 * </pre>
 *
 * <p>The explicit form names the two data files and the two switches directly, for models
 * laid out differently:</p>
 *
 * <pre>
 * model.embedder.&lt;model-id&gt;.static.safetensors.path=/models/table.safetensors
 * model.embedder.&lt;model-id&gt;.static.vocab.path=/models/vocab.txt
 * model.embedder.&lt;model-id&gt;.static.lowercase=true|false   (optional, default true)
 * model.embedder.&lt;model-id&gt;.static.normalize=true|false   (optional, default true)
 * model.embedder.default_id=&lt;model-id&gt;                 (optional, shared across engines)
 * </pre>
 *
 * <p>Mixing the two forms for one model id fails at startup, as does a {@code lowercase}
 * or {@code normalize} key next to the directory form (the directory's own configuration
 * governs there) and any {@code .static.*} key whose model id declares no model source
 * (a typo would otherwise be ignored silently). All configured models load eagerly so
 * misconfiguration fails at server startup rather than on the first request.</p>
 *
 * <p>A {@code model.embedder.default_id} naming a model of another engine is ignored here:
 * the composite provider validates the default against the union of all engines, so an
 * engine-level provider must not reject ids it does not serve.</p>
 */
public final class StaticTableEmbeddingProvider implements EmbeddingProvider {

  private static final Logger logger =
      LoggerFactory.getLogger(StaticTableEmbeddingProvider.class);

  private static final String KEY_PREFIX = "model.embedder.";
  private static final String KEY_DIR_SUFFIX = ".static.dir";
  private static final String KEY_SAFETENSORS_SUFFIX = ".static.safetensors.path";
  private static final String KEY_VOCAB_SUFFIX = ".static.vocab.path";
  private static final String KEY_LOWERCASE_SUFFIX = ".static.lowercase";
  private static final String KEY_NORMALIZE_SUFFIX = ".static.normalize";
  private static final String KEY_DEFAULT_ID = "model.embedder.default_id";

  private static final String SAFETENSORS_FILE_NAME = "model.safetensors";
  private static final String QUANTIZED_FILE_NAME = "model.quantized";

  private final Map<String, LoadedModel> models;
  private final String defaultModelId;

  /**
   * Loads all configured static embedding models.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @throws AnalysisException If the configuration is inconsistent, a referenced file or
   *                           directory is missing, or a model fails to load.
   */
  public StaticTableEmbeddingProvider(Map<String, String> configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    this.models = loadModels(configuration);
    this.defaultModelId = resolveDefaultModelId(configuration, models);
  }

  /**
   * A loaded model and the SHA-256 digest of its matrix artifact.
   *
   * @param model        The loaded, immutable embedding model.
   * @param artifactHash The lowercase hex digest of the model's matrix file.
   */
  private record LoadedModel(StaticEmbeddingModel model, String artifactHash) {
  }

  /** {@inheritDoc} */
  @Override
  public String backendId() {
    return StaticTableEmbeddingBackendFactory.BACKEND_ID;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAvailable() {
    return !models.isEmpty();
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> registeredModelIds() {
    return models.keySet();
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsModel(String modelId) {
    return modelId != null && !modelId.isBlank() && models.containsKey(modelId);
  }

  /** {@inheritDoc} */
  @Override
  public int embeddingDimension(String modelId) {
    return requireModel(modelId).model().dimension();
  }

  /** {@inheritDoc} */
  @Override
  public float[] embed(String modelId, String text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    return requireModel(modelId).model().embed(text);
  }

  /** {@inheritDoc} */
  @Override
  public List<float[]> embedBatch(String modelId, List<String> texts) {
    if (texts == null) {
      throw new IllegalArgumentException("texts must not be null");
    }
    // Table lookup has no batched-inference advantage; this override only hoists the model
    // resolution out of the per-text loop.
    final StaticEmbeddingModel model = requireModel(modelId).model();
    final List<float[]> vectors = new ArrayList<>(texts.size());
    for (String text : texts) {
      if (text == null) {
        throw new IllegalArgumentException("texts must not contain null elements");
      }
      vectors.add(model.embed(text));
    }
    return vectors;
  }

  /** {@inheritDoc} */
  @Override
  public String resolveModelId(String requestedModelId) {
    if (requestedModelId != null && !requestedModelId.isBlank()) {
      return requestedModelId;
    }
    if (defaultModelId != null) {
      return defaultModelId;
    }
    return models.size() == 1 ? models.keySet().iterator().next() : null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String modelArtifactHash(String modelId) {
    if (modelId == null || modelId.isBlank()) {
      return "";
    }
    final LoadedModel loaded = models.get(modelId);
    return loaded == null ? "" : loaded.artifactHash();
  }

  /** Returns the loaded table for a required registered model id. */
  private LoadedModel requireModel(String modelId) {
    if (modelId == null || modelId.isBlank()) {
      throw AnalysisException.invalidArgument("embedding model id is required");
    }
    final LoadedModel loaded = models.get(modelId);
    if (loaded == null) {
      throw AnalysisException.notFound("Unknown embedding model '" + modelId + "'");
    }
    return loaded;
  }

  /** Parses and loads every statically configured embedding table. */
  private static Map<String, LoadedModel> loadModels(Map<String, String> configuration) {
    final Map<String, String> dirs = new HashMap<>();
    final Map<String, String> safetensorsPaths = new HashMap<>();
    final Map<String, String> vocabPaths = new HashMap<>();
    final Map<String, Boolean> lowerCase = new HashMap<>();
    final Map<String, Boolean> normalize = new HashMap<>();

    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_PREFIX) || key.equals(KEY_DEFAULT_ID)) {
        continue;
      }
      final String suffix;
      if (key.endsWith(KEY_DIR_SUFFIX)) {
        suffix = KEY_DIR_SUFFIX;
      } else if (key.endsWith(KEY_SAFETENSORS_SUFFIX)) {
        suffix = KEY_SAFETENSORS_SUFFIX;
      } else if (key.endsWith(KEY_VOCAB_SUFFIX)) {
        suffix = KEY_VOCAB_SUFFIX;
      } else if (key.endsWith(KEY_LOWERCASE_SUFFIX)) {
        suffix = KEY_LOWERCASE_SUFFIX;
      } else if (key.endsWith(KEY_NORMALIZE_SUFFIX)) {
        suffix = KEY_NORMALIZE_SUFFIX;
      } else {
        continue;
      }
      final String modelId = key.substring(KEY_PREFIX.length(), key.length() - suffix.length());
      final String value = entry.getValue();
      if (modelId.isBlank() || value == null || value.isBlank()) {
        continue;
      }
      switch (suffix) {
        case KEY_DIR_SUFFIX -> dirs.put(modelId, value);
        case KEY_SAFETENSORS_SUFFIX -> safetensorsPaths.put(modelId, value);
        case KEY_VOCAB_SUFFIX -> vocabPaths.put(modelId, value);
        case KEY_LOWERCASE_SUFFIX ->
            lowerCase.put(modelId, parseBoolean(modelId, KEY_LOWERCASE_SUFFIX, value));
        default -> normalize.put(modelId, parseBoolean(modelId, KEY_NORMALIZE_SUFFIX, value));
      }
    }

    final Map<String, LoadedModel> loaded = new HashMap<>();
    for (String modelId : allModelIds(dirs, safetensorsPaths, vocabPaths, lowerCase, normalize)) {
      loaded.put(modelId, loadModel(modelId, dirs.get(modelId), safetensorsPaths.get(modelId),
          vocabPaths.get(modelId), lowerCase.get(modelId), normalize.get(modelId)));
    }
    return Map.copyOf(loaded);
  }

  // Every id that mentioned any .static.* key takes part in validation, so an orphaned key
  // (a typo'd model id, or a form mismatch) fails loud in loadModel instead of being dropped
  // silently.
  /** Returns the union of configured model ids. */
  private static Set<String> allModelIds(Map<String, String> dirs,
                                         Map<String, String> safetensorsPaths,
                                         Map<String, String> vocabPaths,
                                         Map<String, Boolean> lowerCase,
                                         Map<String, Boolean> normalize) {
    final Set<String> union = new HashSet<>(dirs.keySet());
    union.addAll(safetensorsPaths.keySet());
    union.addAll(vocabPaths.keySet());
    union.addAll(lowerCase.keySet());
    union.addAll(normalize.keySet());
    return union;
  }

  /** Validates one model's source form and dispatches to the matching loader. */
  private static LoadedModel loadModel(String modelId, String dir, String safetensorsPath,
                                       String vocabPath, Boolean lowerCase, Boolean normalize) {
    if (dir != null && (safetensorsPath != null || vocabPath != null)) {
      throw AnalysisException.invalidArgument(KEY_PREFIX + modelId + KEY_DIR_SUFFIX
          + " cannot be combined with " + KEY_PREFIX + modelId + KEY_SAFETENSORS_SUFFIX
          + " or " + KEY_PREFIX + modelId + KEY_VOCAB_SUFFIX
          + "; declare the model with exactly one of the two forms");
    }
    if (dir != null && (lowerCase != null || normalize != null)) {
      throw AnalysisException.invalidArgument(KEY_PREFIX + modelId + KEY_DIR_SUFFIX
          + " reads lowercase and normalize from the model's own configuration files; "
          + "remove the explicit keys or use the explicit form");
    }
    if (dir != null) {
      return loadFromDirectory(modelId, dir);
    }
    if (safetensorsPath == null || vocabPath == null) {
      throw AnalysisException.invalidArgument("Model '" + modelId + "' declares .static.* keys "
          + "but not a complete model source; either " + KEY_PREFIX + modelId + KEY_DIR_SUFFIX
          + " or both " + KEY_PREFIX + modelId + KEY_SAFETENSORS_SUFFIX + " and "
          + KEY_PREFIX + modelId + KEY_VOCAB_SUFFIX + " are required");
    }
    return loadFromFiles(modelId, safetensorsPath, vocabPath,
        lowerCase == null || lowerCase, normalize == null || normalize);
  }

  /** Loads a standard model directory and hashes its selected matrix artifact. */
  private static LoadedModel loadFromDirectory(String modelId, String dir) {
    final Path directory = Path.of(dir);
    if (!Files.isDirectory(directory)) {
      throw AnalysisException.notFound("Static embedding model directory not found for '"
          + modelId + "': " + directory.toAbsolutePath());
    }
    final StaticEmbeddingModel model;
    try {
      model = StaticEmbeddingModel.load(directory);
    } catch (IllegalArgumentException e) {
      throw AnalysisException.invalidArgument(
          "Invalid static embedding model directory for '" + modelId + "': " + e.getMessage());
    } catch (IOException | UncheckedIOException e) {
      throw AnalysisException.internal(
          "Failed to read static embedding model for '" + modelId + "'", e);
    }
    return loaded(modelId, model, matrixArtifact(directory), dir);
  }

  /** Returns the matrix artifact selected by the directory loader. */
  private static Path matrixArtifact(Path directory) {
    final Path quantized = directory.resolve(QUANTIZED_FILE_NAME);
    return Files.isRegularFile(quantized)
        ? quantized : directory.resolve(SAFETENSORS_FILE_NAME);
  }

  /** Loads explicitly named vocabulary and safetensors files with caller-selected transforms. */
  private static LoadedModel loadFromFiles(String modelId, String safetensorsPath,
                                           String vocabPath, boolean lowerCase,
                                           boolean normalize) {
    final Path safetensors = Path.of(safetensorsPath);
    final Path vocab = Path.of(vocabPath);
    if (!Files.isRegularFile(safetensors)) {
      throw AnalysisException.notFound("Static embedding safetensors file not found for '"
          + modelId + "': " + safetensors.toAbsolutePath());
    }
    if (!Files.isRegularFile(vocab)) {
      throw AnalysisException.notFound("Static embedding vocabulary file not found for '"
          + modelId + "': " + vocab.toAbsolutePath());
    }
    final StaticEmbeddingModel model;
    try {
      model = StaticEmbeddingModel.load(vocab, safetensors,
          lowerCase ? StaticEmbeddingModel.Casing.UNCASED : StaticEmbeddingModel.Casing.CASED,
          normalize ? StaticEmbeddingModel.Normalization.L2
                    : StaticEmbeddingModel.Normalization.NONE);
    } catch (IllegalArgumentException e) {
      throw AnalysisException.invalidArgument(
          "Invalid static embedding model files for '" + modelId + "': " + e.getMessage());
    } catch (IOException | UncheckedIOException e) {
      throw AnalysisException.internal(
          "Failed to read static embedding model for '" + modelId + "'", e);
    }
    return loaded(modelId, model, safetensors, safetensorsPath);
  }

  /** Creates a loaded-model descriptor and validates its metadata. */
  private static LoadedModel loaded(String modelId, StaticEmbeddingModel model,
                                    Path artifact, String source) {
    final String hash;
    try {
      hash = ModelArtifactHasher.sha256Hex(artifact);
    } catch (IOException e) {
      throw AnalysisException.internal(
          "Failed to hash embedding model artifact for '" + modelId + "'", e);
    }
    logger.info("Loaded static embedding model '{}' (dimension={}, vocabulary={}, source={})",
        modelId, model.dimension(), model.vocabularySize(), source);
    return new LoadedModel(model, hash);
  }

  /** Parses a strict, case-insensitive per-model boolean setting. */
  private static boolean parseBoolean(String modelId, String suffix, String value) {
    final String normalized = StringUtil.toLowerCase(value.trim());
    if (normalized.equals("true") || normalized.equals("false")) {
      return Boolean.parseBoolean(normalized);
    }
    throw AnalysisException.invalidArgument(
        KEY_PREFIX + modelId + suffix + " must be 'true' or 'false': " + value);
  }

  // Lenient by design: a default_id naming another engine's model resolves to no engine-level
  // default here; CompositeEmbeddingProvider validates the id against the union of engines.
  /** Resolves this backend's default only when the shared id belongs to this backend. */
  private static String resolveDefaultModelId(
      Map<String, String> configuration, Map<String, LoadedModel> models) {
    final String configured = configuration.get(KEY_DEFAULT_ID);
    if (configured != null && !configured.isBlank() && models.containsKey(configured.trim())) {
      return configured.trim();
    }
    return models.size() == 1 ? models.keySet().iterator().next() : null;
  }
}
