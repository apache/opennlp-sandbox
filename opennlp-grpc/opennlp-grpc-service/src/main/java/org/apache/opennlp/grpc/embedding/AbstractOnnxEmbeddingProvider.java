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
package org.apache.opennlp.grpc.embedding;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import ai.onnxruntime.OrtException;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for ONNX Runtime backed {@link EmbeddingProvider} implementations.
 *
 * <p>Embedding models are declared in the server configuration with one ONNX path and
 * one vocabulary path per model id:</p>
 *
 * <pre>
 * model.embedder.&lt;model-id&gt;.onnx.path=/models/minilm.onnx
 * model.embedder.&lt;model-id&gt;.vocab.path=/models/minilm-vocab.txt
 * model.embedder.&lt;model-id&gt;.lowercase=true|false   (optional, default true)
 * model.embedder.&lt;model-id&gt;.pooling=mean|cls       (optional, default mean)
 * model.embedder.default_id=&lt;model-id&gt;          (optional, required with multiple models)
 * model.embedder.gpu_device_id=&lt;ordinal&gt;        (CUDA backends only)
 * </pre>
 *
 * <p>{@code lowercase} selects the text normalization variant and is a property of the
 * model: uncased models (such as the {@code sentence-transformers} family) require
 * {@code true}, cased models require {@code false}. {@code pooling} selects how token
 * hidden states are reduced to one sentence vector: {@code mean} (masked mean with L2
 * normalization, the sentence-transformers convention) or {@code cls} (the raw hidden
 * state of the classification token).</p>
 *
 * <p>All configured models are loaded eagerly so that misconfiguration fails at server
 * startup rather than on the first request. Subclasses only declare their
 * {@link #backendId() backend id} and execution provider.</p>
 */
abstract class AbstractOnnxEmbeddingProvider implements EmbeddingProvider, AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(AbstractOnnxEmbeddingProvider.class);

  private static final String KEY_PREFIX = "model.embedder.";
  private static final String KEY_ONNX_SUFFIX = ".onnx.path";
  private static final String KEY_VOCAB_SUFFIX = ".vocab.path";
  private static final String KEY_LOWERCASE_SUFFIX = ".lowercase";
  private static final String KEY_POOLING_SUFFIX = ".pooling";
  private static final String KEY_DEFAULT_ID = "model.embedder.default_id";
  private static final String KEY_GPU_DEVICE = "model.embedder.gpu_device_id";

  private final Map<String, OnnxSentenceEmbedder> models;
  private final String defaultModelId;

  /**
   * Loads all configured embedding models.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   * @param useCuda       Whether models run on the CUDA execution provider.
   *
   * @throws AnalysisException If the configuration is inconsistent, a referenced file is
   *                           missing, or a model fails to load.
   */
  AbstractOnnxEmbeddingProvider(Map<String, String> configuration, boolean useCuda) {
    Objects.requireNonNull(configuration, "configuration must not be null");
    final int gpuDeviceId = gpuDeviceId(configuration, useCuda);
    this.models = loadModels(configuration, useCuda, gpuDeviceId);
    this.defaultModelId = resolveDefaultModelId(configuration, models);
  }

  @Override
  public boolean isAvailable() {
    return !models.isEmpty();
  }

  @Override
  public Set<String> registeredModelIds() {
    return models.keySet();
  }

  @Override
  public boolean supportsModel(String modelId) {
    return modelId != null && !modelId.isBlank() && models.containsKey(modelId);
  }

  @Override
  public int embeddingDimension(String modelId) {
    return requireModel(modelId).embeddingDimension();
  }

  @Override
  public float[] embed(String modelId, String text) {
    Objects.requireNonNull(text, "text must not be null");
    final OnnxSentenceEmbedder embedder = requireModel(modelId);
    try {
      return embedder.embed(text);
    } catch (OrtException e) {
      throw AnalysisException.internal("Embedding inference failed for model '" + modelId + "'", e);
    }
  }

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
   * Closes all loaded ONNX sessions. Failures are logged and do not abort the shutdown
   * of the remaining models.
   */
  @Override
  public void close() {
    for (Map.Entry<String, OnnxSentenceEmbedder> entry : models.entrySet()) {
      try {
        entry.getValue().close();
      } catch (OrtException e) {
        logger.warn("Failed to close embedding model '{}'", entry.getKey(), e);
      }
    }
  }

  private OnnxSentenceEmbedder requireModel(String modelId) {
    if (modelId == null || modelId.isBlank()) {
      throw AnalysisException.invalidArgument("embedding model id is required");
    }
    final OnnxSentenceEmbedder embedder = models.get(modelId);
    if (embedder == null) {
      throw AnalysisException.notFound("Unknown embedding model '" + modelId + "'");
    }
    return embedder;
  }

  private static int gpuDeviceId(Map<String, String> configuration, boolean useCuda) {
    final String configured = configuration.get(KEY_GPU_DEVICE);
    if (configured == null || configured.isBlank()) {
      return 0;
    }
    if (!useCuda) {
      throw AnalysisException.invalidArgument(
          KEY_GPU_DEVICE + " requires model.embedder.backend=cuda");
    }
    try {
      return Integer.parseInt(configured.trim());
    } catch (NumberFormatException e) {
      throw AnalysisException.invalidArgument(
          KEY_GPU_DEVICE + " must be an integer: " + configured);
    }
  }

  private static Map<String, OnnxSentenceEmbedder> loadModels(
      Map<String, String> configuration, boolean useCuda, int gpuDeviceId) {
    final Map<String, String> onnxPaths = new HashMap<>();
    final Map<String, String> vocabPaths = new HashMap<>();
    final Map<String, Boolean> lowerCase = new HashMap<>();
    final Map<String, OnnxSentenceEmbedder.Pooling> pooling = new HashMap<>();

    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_PREFIX) || key.equals(KEY_DEFAULT_ID) || key.equals(KEY_GPU_DEVICE)) {
        continue;
      }
      final String suffix;
      if (key.endsWith(KEY_ONNX_SUFFIX)) {
        suffix = KEY_ONNX_SUFFIX;
      } else if (key.endsWith(KEY_VOCAB_SUFFIX)) {
        suffix = KEY_VOCAB_SUFFIX;
      } else if (key.endsWith(KEY_LOWERCASE_SUFFIX)) {
        suffix = KEY_LOWERCASE_SUFFIX;
      } else if (key.endsWith(KEY_POOLING_SUFFIX)) {
        suffix = KEY_POOLING_SUFFIX;
      } else {
        continue;
      }
      final String modelId = key.substring(KEY_PREFIX.length(), key.length() - suffix.length());
      final String value = entry.getValue();
      if (modelId.isBlank() || value == null || value.isBlank()) {
        continue;
      }
      switch (suffix) {
        case KEY_ONNX_SUFFIX -> onnxPaths.put(modelId, value);
        case KEY_VOCAB_SUFFIX -> vocabPaths.put(modelId, value);
        case KEY_LOWERCASE_SUFFIX -> lowerCase.put(modelId, parseLowercase(modelId, value));
        case KEY_POOLING_SUFFIX -> pooling.put(modelId, parsePooling(modelId, value));
        default -> throw new IllegalStateException("Unhandled suffix: " + suffix);
      }
    }

    final Map<String, OnnxSentenceEmbedder> loaded = new HashMap<>();
    try {
      for (Map.Entry<String, String> entry : onnxPaths.entrySet()) {
        final String modelId = entry.getKey();
        final String vocabPath = vocabPaths.get(modelId);
        if (vocabPath == null) {
          throw AnalysisException.invalidArgument(
              KEY_PREFIX + modelId + KEY_VOCAB_SUFFIX
                  + " is required when an ONNX path is configured");
        }
        loaded.put(modelId, loadModel(modelId, entry.getValue(), vocabPath, useCuda, gpuDeviceId,
            lowerCase.getOrDefault(modelId, Boolean.TRUE),
            pooling.getOrDefault(modelId, OnnxSentenceEmbedder.Pooling.MEAN)));
      }
    } catch (RuntimeException e) {
      for (OnnxSentenceEmbedder embedder : loaded.values()) {
        try {
          embedder.close();
        } catch (OrtException closeFailure) {
          e.addSuppressed(closeFailure);
        }
      }
      throw e;
    }
    return Map.copyOf(loaded);
  }

  private static boolean parseLowercase(String modelId, String value) {
    final String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("true") || normalized.equals("false")) {
      return Boolean.parseBoolean(normalized);
    }
    throw AnalysisException.invalidArgument(
        KEY_PREFIX + modelId + KEY_LOWERCASE_SUFFIX + " must be 'true' or 'false': " + value);
  }

  private static OnnxSentenceEmbedder.Pooling parsePooling(String modelId, String value) {
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "mean" -> OnnxSentenceEmbedder.Pooling.MEAN;
      case "cls" -> OnnxSentenceEmbedder.Pooling.CLS;
      default -> throw AnalysisException.invalidArgument(
          KEY_PREFIX + modelId + KEY_POOLING_SUFFIX + " must be 'mean' or 'cls': " + value);
    };
  }

  private static OnnxSentenceEmbedder loadModel(
      String modelId, String onnxPath, String vocabPath, boolean useCuda, int gpuDeviceId,
      boolean lowerCase, OnnxSentenceEmbedder.Pooling pooling) {
    final File onnxFile = new File(onnxPath);
    final File vocabFile = new File(vocabPath);
    if (!onnxFile.isFile()) {
      throw AnalysisException.notFound(
          "ONNX embedding model file not found for '" + modelId + "': " + onnxFile.getAbsolutePath());
    }
    if (!vocabFile.isFile()) {
      throw AnalysisException.notFound(
          "Embedding vocabulary file not found for '" + modelId + "': " + vocabFile.getAbsolutePath());
    }
    try {
      final OnnxSentenceEmbedder embedder =
          new OnnxSentenceEmbedder(onnxFile, vocabFile, useCuda, gpuDeviceId, lowerCase, pooling);
      logger.info("Loaded embedding model '{}' (dimension={}, backend={}, lowercase={}, pooling={})",
          modelId, embedder.embeddingDimension(), useCuda ? "CUDA" : "ONNX Runtime CPU",
          lowerCase, pooling);
      return embedder;
    } catch (OrtException | IOException e) {
      final String backend = useCuda ? "CUDA" : "ONNX Runtime CPU";
      throw AnalysisException.internal(
          "Failed to load embedding model '" + modelId + "' on " + backend, e);
    }
  }

  private static String resolveDefaultModelId(
      Map<String, String> configuration, Map<String, OnnxSentenceEmbedder> models) {
    final String configured = configuration.get(KEY_DEFAULT_ID);
    if (configured != null && !configured.isBlank()) {
      if (!models.containsKey(configured)) {
        throw AnalysisException.notFound(
            KEY_DEFAULT_ID + " '" + configured + "' is not registered");
      }
      return configured;
    }
    return models.size() == 1 ? models.keySet().iterator().next() : null;
  }
}
