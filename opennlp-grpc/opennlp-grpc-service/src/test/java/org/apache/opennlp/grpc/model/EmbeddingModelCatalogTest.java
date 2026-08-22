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

import java.util.Map;

import org.apache.opennlp.grpc.embedding.StubEmbeddingBackendFactory;
import org.apache.opennlp.grpc.embedding.TrackingEmbeddingBackendFactory;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.ComponentType;
import org.apache.opennlp.grpc.v1.ModelBundleInfo;
import org.apache.opennlp.grpc.v1.ModelDescriptor;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingModelCatalogTest {

  @Test
  void exposesEveryRouteForOneLogicalEmbeddingModelInPriorityOrder() {
    final ModelBundleCache cache = new ModelBundleCache(Map.of(
        StubEmbeddingBackendFactory.KEY_MODEL_ID, "mini",
        TrackingEmbeddingBackendFactory.KEY_MODEL_ID, "mini",
        "model.embedder.mini.stub.priority", "100",
        "model.embedder.mini.tracking.priority", "50",
        "model.embedder.mini.stub.vector_space_id", "mini-v1",
        "model.embedder.mini.tracking.vector_space_id", "mini-v1"));
    try {
      final ModelBundleInfo defaultBundle = cache.listBundles().stream()
          .filter(bundle -> ProfileRegistry.DEFAULT_BUNDLE_ID.equals(bundle.getBundleId()))
          .findFirst()
          .orElseThrow();
      final ModelDescriptor model = defaultBundle.getModelsList().stream()
          .filter(candidate -> candidate.getComponentType()
              == ComponentType.COMPONENT_TYPE_EMBEDDER)
          .filter(candidate -> "mini".equals(candidate.getName()))
          .findFirst()
          .orElseThrow();

      assertEquals(2, model.getEmbeddingRoutesCount());
      assertEquals("stub", model.getEmbeddingRoutes(0).getBackendId());
      assertEquals(100, model.getEmbeddingRoutes(0).getPriority());
      assertEquals("mini-v1", model.getEmbeddingRoutes(0).getVectorSpaceId());
      assertTrue(model.getEmbeddingRoutes(0).getPrimary());
      assertEquals("tracking", model.getEmbeddingRoutes(1).getBackendId());
      assertEquals(50, model.getEmbeddingRoutes(1).getPriority());
      assertEquals("mini-v1", model.getEmbeddingRoutes(1).getVectorSpaceId());
    } finally {
      cache.close();
    }
  }

  @Test
  void profileRegistryAdvertisesEmbedProfileOnlyWhenAnEmbeddingModelIsConfigured() {
    try (ModelBundleCache cache = new ModelBundleCache(Map.of())) {
      assertFalse(cache.createProfileRegistry()
          .find(ProfileRegistry.EMBED_PROFILE_ID).isPresent());
    }
    try (ModelBundleCache cache = new ModelBundleCache(Map.of(
        StubEmbeddingBackendFactory.KEY_MODEL_ID, "mini"))) {
      assertTrue(cache.createProfileRegistry()
          .find(ProfileRegistry.EMBED_PROFILE_ID).isPresent());
      // The profile catalog and the bundle catalog stay consistent: the default bundle the
      // en-embed profile rides lists EMBED among its supported steps.
      final ModelBundleInfo defaultBundle = cache.listBundles().stream()
          .filter(bundle -> ProfileRegistry.DEFAULT_BUNDLE_ID.equals(bundle.getBundleId()))
          .findFirst()
          .orElseThrow();
      assertTrue(defaultBundle.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_EMBED));
    }
  }
}
