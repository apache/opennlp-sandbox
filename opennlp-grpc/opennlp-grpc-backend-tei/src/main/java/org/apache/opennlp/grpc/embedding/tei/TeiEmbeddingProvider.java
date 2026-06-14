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
package org.apache.opennlp.grpc.embedding.tei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.tei.v1.EmbedGrpc;
import org.apache.opennlp.grpc.tei.v1.EmbedRequest;
import org.apache.opennlp.grpc.tei.v1.EmbedResponse;
import org.apache.opennlp.grpc.tei.v1.InfoGrpc;
import org.apache.opennlp.grpc.tei.v1.InfoRequest;
import org.apache.opennlp.grpc.tei.v1.InfoResponse;
import org.apache.opennlp.grpc.tei.v1.ModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remote {@link EmbeddingProvider} delegating inference to HuggingFace
 * <a href="https://github.com/huggingface/text-embeddings-inference">Text Embeddings
 * Inference</a> (TEI) instances over their native gRPC API ({@code tei.v1.Embed}).
 *
 * <p>A TEI instance serves exactly one model, so each registered model id maps to one
 * TEI endpoint:</p>
 *
 * <pre>
 * model.embedder.backend=tei
 * model.embedder.&lt;model-id&gt;.tei.target=host:port          (required)
 * model.embedder.&lt;model-id&gt;.tei.use_tls=true|false        (optional, default false)
 * model.embedder.&lt;model-id&gt;.tei.truncate=true|false       (optional, default true)
 * model.embedder.&lt;model-id&gt;.tei.normalize=true|false      (optional, default true)
 * model.embedder.tei.deadline_ms=&lt;millis&gt;                 (optional, default 30000)
 * model.embedder.default_id=&lt;model-id&gt;        (optional, required with multiple models)
 * </pre>
 *
 * <p>All endpoints are contacted eagerly at construction time: the TEI {@code Info} RPC
 * verifies that the served model is an embedding model, and one probe embedding
 * determines the vector dimension (TEI does not expose it as metadata). Misconfigured or
 * unreachable endpoints therefore fail at server startup rather than on the first
 * request.</p>
 *
 * <p>{@link #embedBatch(String, List)} fans the batch out as concurrent unary calls on
 * the multiplexed HTTP/2 connection and preserves input order; TEI performs its own
 * server-side request batching. Tokenization, truncation, pooling and normalization all
 * happen inside TEI.</p>
 */
public final class TeiEmbeddingProvider implements EmbeddingProvider, AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(TeiEmbeddingProvider.class);

  private static final String KEY_PREFIX = "model.embedder.";
  private static final String KEY_TARGET_SUFFIX = ".tei.target";
  private static final String KEY_USE_TLS_SUFFIX = ".tei.use_tls";
  private static final String KEY_TRUNCATE_SUFFIX = ".tei.truncate";
  private static final String KEY_NORMALIZE_SUFFIX = ".tei.normalize";
  private static final String KEY_DEADLINE = "model.embedder.tei.deadline_ms";
  private static final String KEY_DEFAULT_ID = "model.embedder.default_id";

  private static final long DEFAULT_DEADLINE_MS = 30_000L;
  private static final long SHUTDOWN_WAIT_MS = 5_000L;
  private static final String PROBE_TEXT = "dimension probe";

  private final Map<String, TeiEndpoint> models;
  private final String defaultModelId;
  private final long deadlineMs;

  /**
   * Connects to all configured TEI endpoints and validates them.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @throws AnalysisException If a configured endpoint is unreachable or a served model is not an
   *                           embedding model. When no {@code .tei.target} is configured at all the
   *                           provider is inert ({@link #isAvailable()} is {@code false}) rather
   *                           than failing, so it can coexist with other engines in the composite.
   */
  public TeiEmbeddingProvider(Map<String, String> configuration) {
    Objects.requireNonNull(configuration, "configuration must not be null");
    this.deadlineMs = parseDeadline(configuration);
    this.models = connectAll(configuration, deadlineMs);
    this.defaultModelId = resolveDefaultModelId(configuration, models);
  }

  @Override
  public String backendId() {
    return TeiEmbeddingBackendFactory.BACKEND_ID;
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
    return requireModel(modelId).dimension;
  }

  @Override
  public float[] embed(String modelId, String text) {
    Objects.requireNonNull(text, "text must not be null");
    final TeiEndpoint endpoint = requireModel(modelId);
    try {
      final EmbedResponse response = endpoint.blockingStub
          .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
          .embed(embedRequest(endpoint, text));
      return toVector(response);
    } catch (StatusRuntimeException e) {
      throw remoteFailure("Embedding call", modelId, endpoint.target, e);
    }
  }

  @Override
  public List<float[]> embedBatch(String modelId, List<String> texts) {
    Objects.requireNonNull(texts, "texts must not be null");
    final TeiEndpoint endpoint = requireModel(modelId);
    final List<Future<EmbedResponse>> pending = new ArrayList<>(texts.size());
    final EmbedGrpc.EmbedFutureStub stub =
        endpoint.futureStub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);
    for (String text : texts) {
      Objects.requireNonNull(text, "texts must not contain null elements");
      pending.add(stub.embed(embedRequest(endpoint, text)));
    }
    final List<float[]> vectors = new ArrayList<>(texts.size());
    for (Future<EmbedResponse> future : pending) {
      try {
        vectors.add(toVector(future.get()));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw AnalysisException.internal(
            "Interrupted while waiting for TEI batch embeddings from '" + endpoint.target + "'", e);
      } catch (ExecutionException e) {
        throw remoteFailure("Batch embedding call", modelId, endpoint.target, e.getCause());
      }
    }
    return vectors;
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
   * Shuts down all TEI channels. Failures are logged and do not abort the shutdown of
   * the remaining channels.
   */
  @Override
  public void close() {
    for (Map.Entry<String, TeiEndpoint> entry : models.entrySet()) {
      shutdownChannel(entry.getKey(), entry.getValue().channel);
    }
  }

  private TeiEndpoint requireModel(String modelId) {
    if (modelId == null || modelId.isBlank()) {
      throw AnalysisException.invalidArgument("embedding model id is required");
    }
    final TeiEndpoint endpoint = models.get(modelId);
    if (endpoint == null) {
      throw AnalysisException.notFound("Unknown embedding model '" + modelId + "'");
    }
    return endpoint;
  }

  private static EmbedRequest embedRequest(TeiEndpoint endpoint, String text) {
    return EmbedRequest.newBuilder()
        .setInputs(text)
        .setTruncate(endpoint.truncate)
        .setNormalize(endpoint.normalize)
        .build();
  }

  private static float[] toVector(EmbedResponse response) {
    final float[] vector = new float[response.getEmbeddingsCount()];
    for (int i = 0; i < vector.length; i++) {
      vector[i] = response.getEmbeddings(i);
    }
    return vector;
  }

  private static AnalysisException remoteFailure(
      String operation, String modelId, String target, Throwable cause) {
    // A remote backend that is unreachable, times out, or returns a transport error is an
    // upstream availability problem (retryable), not an internal server bug, so surface it as
    // UNAVAILABLE rather than collapsing every remote fault to INTERNAL.
    return AnalysisException.unavailable(
        operation + " to TEI backend '" + target + "' failed for model '" + modelId + "'",
        cause);
  }

  private static Map<String, TeiEndpoint> connectAll(
      Map<String, String> configuration, long deadlineMs) {
    final Map<String, String> targets = new HashMap<>();
    final Map<String, Boolean> useTls = new HashMap<>();
    final Map<String, Boolean> truncate = new HashMap<>();
    final Map<String, Boolean> normalize = new HashMap<>();

    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_PREFIX) || key.equals(KEY_DEFAULT_ID) || key.equals(KEY_DEADLINE)) {
        continue;
      }
      final String suffix;
      if (key.endsWith(KEY_TARGET_SUFFIX)) {
        suffix = KEY_TARGET_SUFFIX;
      } else if (key.endsWith(KEY_USE_TLS_SUFFIX)) {
        suffix = KEY_USE_TLS_SUFFIX;
      } else if (key.endsWith(KEY_TRUNCATE_SUFFIX)) {
        suffix = KEY_TRUNCATE_SUFFIX;
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
        case KEY_TARGET_SUFFIX -> targets.put(modelId, value.trim());
        case KEY_USE_TLS_SUFFIX -> useTls.put(modelId, parseBoolean(key, value));
        case KEY_TRUNCATE_SUFFIX -> truncate.put(modelId, parseBoolean(key, value));
        case KEY_NORMALIZE_SUFFIX -> normalize.put(modelId, parseBoolean(key, value));
        default -> throw new IllegalStateException("Unhandled suffix: " + suffix);
      }
    }

    final Map<String, TeiEndpoint> connected = new HashMap<>();
    try {
      for (Map.Entry<String, String> entry : targets.entrySet()) {
        final String modelId = entry.getKey();
        connected.put(modelId, connect(modelId, entry.getValue(),
            useTls.getOrDefault(modelId, Boolean.FALSE),
            truncate.getOrDefault(modelId, Boolean.TRUE),
            normalize.getOrDefault(modelId, Boolean.TRUE),
            deadlineMs));
      }
    } catch (RuntimeException e) {
      for (Map.Entry<String, TeiEndpoint> entry : connected.entrySet()) {
        shutdownChannel(entry.getKey(), entry.getValue().channel);
      }
      throw e;
    }
    return Map.copyOf(connected);
  }

  private static TeiEndpoint connect(
      String modelId, String target, boolean useTls, boolean truncate, boolean normalize,
      long deadlineMs) {
    final ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(target);
    if (useTls) {
      builder.useTransportSecurity();
    } else {
      builder.usePlaintext();
    }
    final ManagedChannel channel = builder.build();
    try {
      final InfoResponse info;
      try {
        info = InfoGrpc.newBlockingStub(channel)
            .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
            .info(InfoRequest.getDefaultInstance());
      } catch (StatusRuntimeException e) {
        throw AnalysisException.internal(
            "Cannot reach TEI backend '" + target + "' for model '" + modelId + "'", e);
      }
      if (info.getModelType() != ModelType.MODEL_TYPE_EMBEDDING) {
        throw AnalysisException.failedPrecondition(
            "TEI backend '" + target + "' serves a " + info.getModelType().name()
                + " model ('" + info.getModelId() + "'), not an embedding model");
      }

      final EmbedGrpc.EmbedBlockingStub blockingStub = EmbedGrpc.newBlockingStub(channel);
      final EmbedResponse probe;
      try {
        probe = blockingStub
            .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
            .embed(EmbedRequest.newBuilder()
                .setInputs(PROBE_TEXT)
                .setTruncate(truncate)
                .setNormalize(normalize)
                .build());
      } catch (StatusRuntimeException e) {
        throw AnalysisException.internal(
            "Probe embedding against TEI backend '" + target + "' failed for model '"
                + modelId + "'", e);
      }
      if (probe.getEmbeddingsCount() == 0) {
        throw AnalysisException.failedPrecondition(
            "TEI backend '" + target + "' returned an empty embedding for model '"
                + modelId + "'");
      }

      logger.info("Connected embedding model '{}' to TEI backend '{}' (served model='{}',"
              + " dimension={}, truncate={}, normalize={})",
          modelId, target, info.getModelId(), probe.getEmbeddingsCount(), truncate, normalize);
      return new TeiEndpoint(channel, blockingStub, EmbedGrpc.newFutureStub(channel),
          target, truncate, normalize, probe.getEmbeddingsCount());
    } catch (RuntimeException e) {
      shutdownChannel(modelId, channel);
      throw e;
    }
  }

  private static void shutdownChannel(String modelId, ManagedChannel channel) {
    channel.shutdown();
    try {
      if (!channel.awaitTermination(SHUTDOWN_WAIT_MS, TimeUnit.MILLISECONDS)) {
        channel.shutdownNow();
      }
    } catch (InterruptedException e) {
      channel.shutdownNow();
      Thread.currentThread().interrupt();
      logger.warn("Interrupted while closing TEI channel for model '{}'", modelId, e);
    }
  }

  private static long parseDeadline(Map<String, String> configuration) {
    final String configured = configuration.get(KEY_DEADLINE);
    if (configured == null || configured.isBlank()) {
      return DEFAULT_DEADLINE_MS;
    }
    try {
      final long parsed = Long.parseLong(configured.trim());
      if (parsed <= 0) {
        throw AnalysisException.invalidArgument(KEY_DEADLINE + " must be positive: " + configured);
      }
      return parsed;
    } catch (NumberFormatException e) {
      throw AnalysisException.invalidArgument(KEY_DEADLINE + " must be an integer: " + configured);
    }
  }

  private static boolean parseBoolean(String key, String value) {
    final String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (normalized.equals("true") || normalized.equals("false")) {
      return Boolean.parseBoolean(normalized);
    }
    throw AnalysisException.invalidArgument(key + " must be 'true' or 'false': " + value);
  }

  private static String resolveDefaultModelId(
      Map<String, String> configuration, Map<String, TeiEndpoint> models) {
    final String configured = configuration.get(KEY_DEFAULT_ID);
    if (configured != null && !configured.isBlank()) {
      if (!models.containsKey(configured)) {
        throw AnalysisException.notFound(KEY_DEFAULT_ID + " '" + configured + "' is not registered");
      }
      return configured;
    }
    return models.size() == 1 ? models.keySet().iterator().next() : null;
  }

  /** One TEI endpoint serving one registered model id. */
  private record TeiEndpoint(
      ManagedChannel channel,
      EmbedGrpc.EmbedBlockingStub blockingStub,
      EmbedGrpc.EmbedFutureStub futureStub,
      String target,
      boolean truncate,
      boolean normalize,
      int dimension) {
  }
}
