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

import java.nio.file.Path;
import java.util.List;

import opennlp.embeddings.StaticEmbeddingModel;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingBatchResult;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainedModelEmbeddingProviderTest {

  private static final String MODEL_ID = "static-model-test";
  private static final String HASH = "ab".repeat(32);

  @Test
  void delegatesUnknownModelsToTheBaseProvider() {
    final TrainedModelEmbeddingProvider provider =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());

    assertEquals("fake", provider.backendId());
    assertTrue(provider.isAvailable());
    assertTrue(provider.supportsModel("base"));
    assertEquals(2, provider.embeddingDimension("base"));
    assertArrayEquals(new float[] {1f, 0f}, provider.embed("base", "anything"), 1e-6f);
    assertFalse(provider.supportsModel(MODEL_ID));
  }

  @Test
  void servesRegisteredTrainedModelsBeforeTheBaseProvider(@TempDir Path dir) throws Exception {
    TrainingTestSupport.writeStaticModelDirectory(dir);
    final TrainedModelEmbeddingProvider provider =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    provider.register(MODEL_ID, StaticEmbeddingModel.load(dir), HASH);

    assertTrue(provider.supportsModel(MODEL_ID));
    assertTrue(provider.supportsModel(MODEL_ID, "static"));
    assertEquals("static", provider.backendId(MODEL_ID));
    assertEquals(TrainingTestSupport.DIMENSION, provider.embeddingDimension(MODEL_ID));
    assertArrayEquals(new float[] {3.5f, 35f, 350f},
        provider.embed(MODEL_ID, "HELLO WORLD"), 1e-5f);
    assertEquals(HASH, provider.modelArtifactHash(MODEL_ID));
    assertTrue(provider.registeredModelIds().containsAll(List.of("base", MODEL_ID)));

    final List<EmbeddingRoute> routes = provider.routesForModel(MODEL_ID);
    assertEquals(1, routes.size());
    assertEquals(MODEL_ID, routes.getFirst().getModelId());
    assertEquals("static", routes.getFirst().getBackendId());
    assertEquals(MODEL_ID + "-sha256-" + HASH, routes.getFirst().getVectorSpaceId());
    assertEquals(HASH, routes.getFirst().getArtifactHash());
    assertTrue(routes.getFirst().getPrimary());

    final EmbeddingBatchResult resolved =
        provider.embedBatchResolved(MODEL_ID, "", List.of("hello world", "liberty"));
    assertEquals(2, resolved.vectors().size());
    assertEquals("static", resolved.route().getBackendId());
    assertEquals(HASH, resolved.route().getArtifactHash());
  }

  @Test
  void unregisterStopsServingAndDuplicatesAreRejected(@TempDir Path dir) throws Exception {
    TrainingTestSupport.writeStaticModelDirectory(dir);
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(dir);
    final TrainedModelEmbeddingProvider provider =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    provider.register(MODEL_ID, model, HASH);

    assertThrows(IllegalArgumentException.class,
        () -> provider.register(MODEL_ID, model, HASH));
    assertThrows(IllegalArgumentException.class,
        () -> provider.register("base", model, HASH));
    provider.unregister(MODEL_ID);
    assertFalse(provider.supportsModel(MODEL_ID));
    assertTrue(provider.supportsModel("base"));
  }
}
