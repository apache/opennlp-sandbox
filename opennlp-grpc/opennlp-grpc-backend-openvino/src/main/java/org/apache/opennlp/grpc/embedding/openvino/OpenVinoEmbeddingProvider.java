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
package org.apache.opennlp.grpc.embedding.openvino;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.kserve.v2.GRPCInferenceServiceGrpc;
import org.apache.opennlp.grpc.kserve.v2.InferTensorContents;
import org.apache.opennlp.grpc.kserve.v2.ModelInferRequest;
import org.apache.opennlp.grpc.kserve.v2.ModelInferResponse;
import org.apache.opennlp.grpc.kserve.v2.ModelMetadataRequest;
import org.apache.opennlp.grpc.kserve.v2.ModelMetadataResponse;
import org.apache.opennlp.grpc.kserve.v2.ModelReadyRequest;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remote {@link EmbeddingProvider} delegating inference to an
 * <a href="https://docs.openvino.ai/2026/model-server/ovms_what_is_openvino_model_server.html">
 * OpenVINO Model Server</a> — or any other server implementing the KServe v2
 * "open inference protocol" gRPC API ({@code inference.GRPCInferenceService}).
 *
 * <p>The served model (or OVMS MediaPipe graph) must accept a {@code BYTES} string
 * tensor and return a {@code FP32} embedding matrix, i.e. tokenization runs server-side
 * (for OpenVINO, models converted with {@code openvino_tokenizers} or an OVMS graph
 * chaining tokenizer and embedding model). Each registered model id maps to one served
 * model:</p>
 *
 * <pre>
 * model.embedder.backend=openvino
 * model.embedder.&lt;model-id&gt;.openvino.target=host:port        (required)
 * model.embedder.&lt;model-id&gt;.openvino.model_name=&lt;name&gt;      (required, served name)
 * model.embedder.&lt;model-id&gt;.openvino.model_version=&lt;v&gt;      (optional)
 * model.embedder.&lt;model-id&gt;.openvino.input_name=&lt;tensor&gt;    (optional with one input)
 * model.embedder.&lt;model-id&gt;.openvino.output_name=&lt;tensor&gt;   (optional with one output)
 * model.embedder.&lt;model-id&gt;.openvino.use_tls=true|false     (optional, default false)
 * model.embedder.openvino.deadline_ms=&lt;millis&gt;              (optional, default 30000)
 * model.embedder.default_id=&lt;model-id&gt;       (optional, required with multiple models)
 * </pre>
 *
 * <p>All endpoints are validated eagerly at construction time: model readiness and
 * tensor metadata are checked, and one probe inference determines the embedding
 * dimension. Misconfigured or unreachable endpoints therefore fail at server startup
 * rather than on the first request.</p>
 *
 * <p>{@link #embedBatch(String, List)} sends the whole batch as a single
 * {@code ModelInfer} call with a leading batch dimension, which is the native KServe
 * batching model.</p>
 */
public final class OpenVinoEmbeddingProvider implements EmbeddingProvider, AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(OpenVinoEmbeddingProvider.class);

  private static final String KEY_PREFIX = "model.embedder.";
  private static final String KEY_TARGET_SUFFIX = ".openvino.target";
  private static final String KEY_MODEL_NAME_SUFFIX = ".openvino.model_name";
  private static final String KEY_MODEL_VERSION_SUFFIX = ".openvino.model_version";
  private static final String KEY_INPUT_NAME_SUFFIX = ".openvino.input_name";
  private static final String KEY_OUTPUT_NAME_SUFFIX = ".openvino.output_name";
  private static final String KEY_USE_TLS_SUFFIX = ".openvino.use_tls";
  private static final String KEY_DEADLINE = "model.embedder.openvino.deadline_ms";
  private static final String KEY_DEFAULT_ID = "model.embedder.default_id";

  private static final String DATATYPE_BYTES = "BYTES";
  private static final String DATATYPE_FP32 = "FP32";
  private static final long DEFAULT_DEADLINE_MS = 30_000L;
  private static final long SHUTDOWN_WAIT_MS = 5_000L;
  private static final String PROBE_TEXT = "dimension probe";

  private final Map<String, OvmsEndpoint> models;
  private final String defaultModelId;
  private final long deadlineMs;

  /**
   * Connects to all configured KServe endpoints and validates them.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   *
   * @throws AnalysisException If no endpoint is configured, an endpoint is unreachable,
   *                           a model is not ready, or the served model does not have a
   *                           string-in / float-out signature.
   */
  public OpenVinoEmbeddingProvider(Map<String, String> configuration) {
    Objects.requireNonNull(configuration, "configuration must not be null");
    this.deadlineMs = parseDeadline(configuration);
    this.models = connectAll(configuration, deadlineMs);
    if (models.isEmpty()) {
      throw AnalysisException.invalidArgument(
          "The 'openvino' embedding backend requires at least one model: configure "
              + KEY_PREFIX + "<model-id>" + KEY_TARGET_SUFFIX + " and "
              + KEY_PREFIX + "<model-id>" + KEY_MODEL_NAME_SUFFIX);
    }
    this.defaultModelId = resolveDefaultModelId(configuration, models);
  }

  @Override
  public String backendId() {
    return OpenVinoEmbeddingBackendFactory.BACKEND_ID;
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
    return embedBatch(modelId, List.of(text)).get(0);
  }

  @Override
  public List<float[]> embedBatch(String modelId, List<String> texts) {
    Objects.requireNonNull(texts, "texts must not be null");
    final OvmsEndpoint endpoint = requireModel(modelId);
    if (texts.isEmpty()) {
      return List.of();
    }
    final float[] flat;
    try {
      flat = infer(endpoint, texts, deadlineMs);
    } catch (StatusRuntimeException e) {
      throw remoteFailure("Embedding call", modelId, endpoint.target, e);
    }
    if (flat.length != texts.size() * endpoint.dimension) {
      throw AnalysisException.internal(
          "KServe backend '" + endpoint.target + "' returned " + flat.length
              + " values for model '" + modelId + "', expected "
              + texts.size() * endpoint.dimension, null);
    }
    final List<float[]> vectors = new ArrayList<>(texts.size());
    for (int i = 0; i < texts.size(); i++) {
      final float[] vector = new float[endpoint.dimension];
      System.arraycopy(flat, i * endpoint.dimension, vector, 0, endpoint.dimension);
      vectors.add(vector);
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
   * Shuts down all KServe channels. Failures are logged and do not abort the shutdown
   * of the remaining channels.
   */
  @Override
  public void close() {
    for (Map.Entry<String, OvmsEndpoint> entry : models.entrySet()) {
      shutdownChannel(entry.getKey(), entry.getValue().channel);
    }
  }

  private OvmsEndpoint requireModel(String modelId) {
    if (modelId == null || modelId.isBlank()) {
      throw AnalysisException.invalidArgument("embedding model id is required");
    }
    final OvmsEndpoint endpoint = models.get(modelId);
    if (endpoint == null) {
      throw AnalysisException.notFound("Unknown embedding model '" + modelId + "'");
    }
    return endpoint;
  }

  /** Runs one batched inference and returns the flattened FP32 output tensor. */
  private static float[] infer(OvmsEndpoint endpoint, List<String> texts, long deadlineMs) {
    final InferTensorContents.Builder contents = InferTensorContents.newBuilder();
    for (String text : texts) {
      Objects.requireNonNull(text, "texts must not contain null elements");
      contents.addBytesContents(ByteString.copyFrom(text, StandardCharsets.UTF_8));
    }
    final ModelInferRequest.Builder request = ModelInferRequest.newBuilder()
        .setModelName(endpoint.modelName)
        .addInputs(ModelInferRequest.InferInputTensor.newBuilder()
            .setName(endpoint.inputName)
            .setDatatype(DATATYPE_BYTES)
            .addShape(texts.size())
            .setContents(contents)
            .build())
        .addOutputs(ModelInferRequest.InferRequestedOutputTensor.newBuilder()
            .setName(endpoint.outputName)
            .build());
    if (!endpoint.modelVersion.isEmpty()) {
      request.setModelVersion(endpoint.modelVersion);
    }
    final ModelInferResponse response = endpoint.stub
        .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
        .modelInfer(request.build());
    return extractFloats(response, endpoint.outputName, endpoint.target);
  }

  /**
   * Extracts the named FP32 output tensor, supporting both the typed
   * {@code InferTensorContents} representation and {@code raw_output_contents}
   * (little-endian, the KServe raw convention).
   */
  private static float[] extractFloats(ModelInferResponse response, String outputName,
      String target) {
    for (int i = 0; i < response.getOutputsCount(); i++) {
      final ModelInferResponse.InferOutputTensor output = response.getOutputs(i);
      if (!output.getName().equals(outputName)) {
        continue;
      }
      if (response.getRawOutputContentsCount() > i) {
        final ByteBuffer buffer = response.getRawOutputContents(i).asReadOnlyByteBuffer()
            .order(ByteOrder.LITTLE_ENDIAN);
        final FloatBuffer floats = buffer.asFloatBuffer();
        final float[] values = new float[floats.remaining()];
        floats.get(values);
        return values;
      }
      final int count = output.getContents().getFp32ContentsCount();
      final float[] values = new float[count];
      for (int j = 0; j < count; j++) {
        values[j] = output.getContents().getFp32Contents(j);
      }
      return values;
    }
    throw AnalysisException.internal(
        "KServe backend '" + target + "' response is missing output tensor '"
            + outputName + "'", null);
  }

  private static AnalysisException remoteFailure(
      String operation, String modelId, String target, Throwable cause) {
    return AnalysisException.internal(
        operation + " to KServe backend '" + target + "' failed for model '" + modelId + "'",
        cause);
  }

  private static Map<String, OvmsEndpoint> connectAll(
      Map<String, String> configuration, long deadlineMs) {
    final Map<String, Map<String, String>> settings = new HashMap<>();

    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      final String key = entry.getKey();
      if (!key.startsWith(KEY_PREFIX) || key.equals(KEY_DEFAULT_ID) || key.equals(KEY_DEADLINE)) {
        continue;
      }
      final String suffix = matchSuffix(key);
      if (suffix == null) {
        continue;
      }
      final String modelId = key.substring(KEY_PREFIX.length(), key.length() - suffix.length());
      final String value = entry.getValue();
      if (modelId.isBlank() || value == null || value.isBlank()) {
        continue;
      }
      settings.computeIfAbsent(modelId, id -> new HashMap<>()).put(suffix, value.trim());
    }

    final Map<String, OvmsEndpoint> connected = new HashMap<>();
    try {
      for (Map.Entry<String, Map<String, String>> entry : settings.entrySet()) {
        connected.put(entry.getKey(), connect(entry.getKey(), entry.getValue(), deadlineMs));
      }
    } catch (RuntimeException e) {
      for (Map.Entry<String, OvmsEndpoint> entry : connected.entrySet()) {
        shutdownChannel(entry.getKey(), entry.getValue().channel);
      }
      throw e;
    }
    return Map.copyOf(connected);
  }

  private static String matchSuffix(String key) {
    if (key.endsWith(KEY_TARGET_SUFFIX)) {
      return KEY_TARGET_SUFFIX;
    }
    if (key.endsWith(KEY_MODEL_NAME_SUFFIX)) {
      return KEY_MODEL_NAME_SUFFIX;
    }
    if (key.endsWith(KEY_MODEL_VERSION_SUFFIX)) {
      return KEY_MODEL_VERSION_SUFFIX;
    }
    if (key.endsWith(KEY_INPUT_NAME_SUFFIX)) {
      return KEY_INPUT_NAME_SUFFIX;
    }
    if (key.endsWith(KEY_OUTPUT_NAME_SUFFIX)) {
      return KEY_OUTPUT_NAME_SUFFIX;
    }
    if (key.endsWith(KEY_USE_TLS_SUFFIX)) {
      return KEY_USE_TLS_SUFFIX;
    }
    return null;
  }

  private static OvmsEndpoint connect(
      String modelId, Map<String, String> settings, long deadlineMs) {
    final String target = settings.get(KEY_TARGET_SUFFIX);
    final String modelName = settings.get(KEY_MODEL_NAME_SUFFIX);
    if (target == null) {
      throw AnalysisException.invalidArgument(
          KEY_PREFIX + modelId + KEY_TARGET_SUFFIX + " is required");
    }
    if (modelName == null) {
      throw AnalysisException.invalidArgument(
          KEY_PREFIX + modelId + KEY_MODEL_NAME_SUFFIX + " is required");
    }
    final String modelVersion = settings.getOrDefault(KEY_MODEL_VERSION_SUFFIX, "");
    final boolean useTls = settings.containsKey(KEY_USE_TLS_SUFFIX)
        && parseBoolean(KEY_PREFIX + modelId + KEY_USE_TLS_SUFFIX, settings.get(KEY_USE_TLS_SUFFIX));

    final ManagedChannelBuilder<?> builder = ManagedChannelBuilder.forTarget(target);
    if (useTls) {
      builder.useTransportSecurity();
    } else {
      builder.usePlaintext();
    }
    final ManagedChannel channel = builder.build();
    try {
      final GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub stub =
          GRPCInferenceServiceGrpc.newBlockingStub(channel);

      checkModelReady(stub, modelId, modelName, modelVersion, target, deadlineMs);
      final ModelMetadataResponse metadata =
          fetchMetadata(stub, modelId, modelName, modelVersion, target, deadlineMs);
      final String inputName = resolveTensorName(metadata.getInputsList().stream()
              .map(t -> new TensorSignature(t.getName(), t.getDatatype())).toList(),
          settings.get(KEY_INPUT_NAME_SUFFIX), DATATYPE_BYTES, "input", modelId, modelName);
      final String outputName = resolveTensorName(metadata.getOutputsList().stream()
              .map(t -> new TensorSignature(t.getName(), t.getDatatype())).toList(),
          settings.get(KEY_OUTPUT_NAME_SUFFIX), DATATYPE_FP32, "output", modelId, modelName);

      final OvmsEndpoint probeEndpoint = new OvmsEndpoint(
          channel, stub, target, modelName, modelVersion, inputName, outputName, -1);
      final float[] probe;
      try {
        probe = infer(probeEndpoint, List.of(PROBE_TEXT), deadlineMs);
      } catch (StatusRuntimeException e) {
        throw AnalysisException.internal(
            "Probe inference against KServe backend '" + target + "' failed for model '"
                + modelId + "'", e);
      }
      if (probe.length == 0) {
        throw AnalysisException.failedPrecondition(
            "KServe backend '" + target + "' returned an empty embedding for model '"
                + modelId + "'");
      }

      logger.info("Connected embedding model '{}' to KServe backend '{}' (served model='{}',"
              + " input='{}', output='{}', dimension={})",
          modelId, target, modelName, inputName, outputName, probe.length);
      return new OvmsEndpoint(
          channel, stub, target, modelName, modelVersion, inputName, outputName, probe.length);
    } catch (RuntimeException e) {
      shutdownChannel(modelId, channel);
      throw e;
    }
  }

  private static void checkModelReady(
      GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub stub, String modelId,
      String modelName, String modelVersion, String target, long deadlineMs) {
    final boolean ready;
    try {
      ready = stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
          .modelReady(ModelReadyRequest.newBuilder()
              .setName(modelName)
              .setVersion(modelVersion)
              .build())
          .getReady();
    } catch (StatusRuntimeException e) {
      throw AnalysisException.internal(
          "Cannot reach KServe backend '" + target + "' for model '" + modelId + "'", e);
    }
    if (!ready) {
      throw AnalysisException.failedPrecondition(
          "Model '" + modelName + "' is not ready on KServe backend '" + target + "'");
    }
  }

  private static ModelMetadataResponse fetchMetadata(
      GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub stub, String modelId,
      String modelName, String modelVersion, String target, long deadlineMs) {
    try {
      return stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
          .modelMetadata(ModelMetadataRequest.newBuilder()
              .setName(modelName)
              .setVersion(modelVersion)
              .build());
    } catch (StatusRuntimeException e) {
      throw AnalysisException.internal(
          "Cannot fetch metadata for model '" + modelName + "' from KServe backend '"
              + target + "' (model id '" + modelId + "')", e);
    }
  }

  /**
   * Resolves the tensor to use: an explicitly configured name must exist, otherwise the
   * model must expose exactly one tensor of that direction. The resolved tensor must
   * have the expected datatype, which for inputs enforces server-side tokenization.
   */
  private static String resolveTensorName(
      List<TensorSignature> tensors, String configuredName, String expectedDatatype,
      String direction, String modelId, String modelName) {
    final TensorSignature resolved;
    if (configuredName != null) {
      resolved = tensors.stream()
          .filter(t -> t.name.equals(configuredName))
          .findFirst()
          .orElseThrow(() -> AnalysisException.notFound(
              "Model '" + modelName + "' has no " + direction + " tensor '" + configuredName
                  + "' (model id '" + modelId + "')"));
    } else if (tensors.size() == 1) {
      resolved = tensors.get(0);
    } else {
      throw AnalysisException.invalidArgument(
          "Model '" + modelName + "' exposes " + tensors.size() + " " + direction
              + " tensors; configure " + KEY_PREFIX + modelId
              + (direction.equals("input") ? KEY_INPUT_NAME_SUFFIX : KEY_OUTPUT_NAME_SUFFIX));
    }
    if (!expectedDatatype.equals(resolved.datatype)) {
      throw AnalysisException.failedPrecondition(
          "Tensor '" + resolved.name + "' of model '" + modelName + "' has datatype "
              + resolved.datatype + ", expected " + expectedDatatype
              + (expectedDatatype.equals(DATATYPE_BYTES)
                  ? ". The served model must accept raw text; serve a model with"
                      + " server-side tokenization (e.g. converted with openvino_tokenizers)"
                  : ""));
    }
    return resolved.name;
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
      logger.warn("Interrupted while closing KServe channel for model '{}'", modelId, e);
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
      Map<String, String> configuration, Map<String, OvmsEndpoint> models) {
    final String configured = configuration.get(KEY_DEFAULT_ID);
    if (configured != null && !configured.isBlank()) {
      if (!models.containsKey(configured)) {
        throw AnalysisException.notFound(KEY_DEFAULT_ID + " '" + configured + "' is not registered");
      }
      return configured;
    }
    return models.size() == 1 ? models.keySet().iterator().next() : null;
  }

  /** One KServe endpoint serving one registered model id. */
  private record OvmsEndpoint(
      ManagedChannel channel,
      GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub stub,
      String target,
      String modelName,
      String modelVersion,
      String inputName,
      String outputName,
      int dimension) {
  }

  /** Name and datatype of a tensor from model metadata. */
  private record TensorSignature(String name, String datatype) {
  }
}
