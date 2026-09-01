/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import opennlp.embeddings.StaticEmbeddingModel;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingBatchResult;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;

/**
 * An {@link EmbeddingProvider} that serves models trained at runtime in front of the
 * startup-configured provider. Trained models resolve first by model id; every other
 * id delegates unchanged, so analysis, indexing, and search see one model catalog.
 *
 * <p>Registration and lookup are thread safe; a registered model is immutable and
 * shared by every request.</p>
 */
public final class TrainedModelEmbeddingProvider implements EmbeddingProvider, AutoCloseable {

  /** Backend id reported for trained models, which are in-process static tables. */
  public static final String TRAINED_BACKEND_ID = "static";

  private final EmbeddingProvider delegate;
  private final Map<String, TrainedModel> models = new ConcurrentHashMap<>();

  /**
   * Wraps the startup-configured provider.
   *
   * @param delegate Provider serving every model that was not trained at runtime.
   * @throws IllegalArgumentException If {@code delegate} is {@code null}.
   */
  public TrainedModelEmbeddingProvider(EmbeddingProvider delegate) {
    if (delegate == null) {
      throw new IllegalArgumentException("delegate must not be null");
    }
    this.delegate = delegate;
  }

  /**
   * Starts serving one trained model.
   *
   * @param modelId The model id, unique across trained and configured models.
   * @param model The loaded immutable model.
   * @param artifactHash Lowercase SHA-256 identifying the published artifact.
   * @throws IllegalArgumentException If an argument is {@code null} or blank, or the id
   *     is already served by a trained or configured model.
   */
  public void register(String modelId, StaticEmbeddingModel model, String artifactHash) {
    if (modelId == null || modelId.isBlank()) {
      throw new IllegalArgumentException("modelId must not be null or blank");
    }
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    if (artifactHash == null || artifactHash.isBlank()) {
      throw new IllegalArgumentException("artifactHash must not be null or blank");
    }
    if (delegate.supportsModel(modelId)) {
      throw new IllegalArgumentException("Embedding model id '" + modelId
          + "' collides with a configured model");
    }
    if (models.putIfAbsent(modelId, new TrainedModel(model, artifactHash)) != null) {
      throw new IllegalArgumentException("Embedding model id '" + modelId
          + "' is already registered");
    }
  }

  /**
   * Stops serving one trained model.
   *
   * @param modelId The trained model id; unknown ids are ignored.
   */
  public void unregister(String modelId) {
    if (modelId != null) {
      models.remove(modelId);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String backendId() {
    return delegate.backendId();
  }

  /** {@inheritDoc} */
  @Override
  public String backendId(String modelId) {
    return models.containsKey(modelId) ? TRAINED_BACKEND_ID : delegate.backendId(modelId);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAvailable() {
    return !models.isEmpty() || delegate.isAvailable();
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> registeredModelIds() {
    final Set<String> ids = new TreeSet<>(delegate.registeredModelIds());
    ids.addAll(models.keySet());
    return ids;
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsModel(String modelId) {
    return modelId != null && (models.containsKey(modelId) || delegate.supportsModel(modelId));
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsModel(String modelId, String backendId) {
    if (modelId != null && models.containsKey(modelId)) {
      return TRAINED_BACKEND_ID.equals(backendId);
    }
    return delegate.supportsModel(modelId, backendId);
  }

  /** {@inheritDoc} */
  @Override
  public int embeddingDimension(String modelId) {
    final TrainedModel trained = models.get(modelId);
    return trained != null ? trained.model().dimension()
        : delegate.embeddingDimension(modelId);
  }

  /** {@inheritDoc} */
  @Override
  public float[] embed(String modelId, String text) {
    final TrainedModel trained = models.get(modelId);
    if (trained == null) {
      return delegate.embed(modelId, text);
    }
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    return trained.model().embed(text);
  }

  /** {@inheritDoc} */
  @Override
  public List<float[]> embedBatch(String modelId, List<String> texts) {
    final TrainedModel trained = models.get(modelId);
    if (trained == null) {
      return delegate.embedBatch(modelId, texts);
    }
    if (texts == null) {
      throw new IllegalArgumentException("texts must not be null");
    }
    final List<float[]> vectors = new ArrayList<>(texts.size());
    for (String text : texts) {
      if (text == null) {
        throw new IllegalArgumentException("texts must not contain null");
      }
      vectors.add(trained.model().embed(text));
    }
    return vectors;
  }

  /** {@inheritDoc} */
  @Override
  public EmbeddingBatchResult embedBatchResolved(
      String modelId, String backendId, List<String> texts) {
    final TrainedModel trained = models.get(modelId);
    if (trained == null) {
      return delegate.embedBatchResolved(modelId, backendId, texts);
    }
    if (backendId != null && !backendId.isBlank() && !TRAINED_BACKEND_ID.equals(backendId)) {
      throw AnalysisException.notFound(
          "Engine '" + backendId + "' does not serve embedding model '" + modelId + "'");
    }
    return new EmbeddingBatchResult(embedBatch(modelId, texts), trainedRoute(modelId, trained));
  }

  /** {@inheritDoc} */
  @Override
  public List<EmbeddingRoute> routesForModel(String modelId) {
    final TrainedModel trained = models.get(modelId);
    return trained != null
        ? List.of(trainedRoute(modelId, trained)) : delegate.routesForModel(modelId);
  }

  /** {@inheritDoc} */
  @Override
  public String modelArtifactHash(String modelId) {
    final TrainedModel trained = models.get(modelId);
    return trained != null ? trained.artifactHash() : delegate.modelArtifactHash(modelId);
  }

  /** {@inheritDoc} */
  @Override
  public String resolveModelId(String requestedModelId) {
    if (requestedModelId != null && !requestedModelId.isBlank()) {
      return requestedModelId;
    }
    return delegate.resolveModelId(requestedModelId);
  }

  /** {@inheritDoc} Closes the wrapped provider; trained models hold no native resources. */
  @Override
  public void close() throws Exception {
    if (delegate instanceof AutoCloseable closeable) {
      closeable.close();
    }
  }

  /**
   * Builds the single serving route of one trained model. The vector space id derives
   * from the artifact hash, so an index built with the model stays queryable by exactly
   * this artifact and never by a retrained model that happens to reuse a display name.
   */
  private static EmbeddingRoute trainedRoute(String modelId, TrainedModel trained) {
    return EmbeddingRoute.newBuilder()
        .setModelId(modelId)
        .setBackendId(TRAINED_BACKEND_ID)
        .setVectorSpaceId(modelId + "-sha256-" + trained.artifactHash())
        .setArtifactHash(trained.artifactHash())
        .setPrimary(true)
        .build();
  }

  /** One served trained model and the SHA-256 of its published artifact. */
  private record TrainedModel(StaticEmbeddingModel model, String artifactHash) {
  }
}
