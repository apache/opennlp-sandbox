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

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.CategoryChunkConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.EmbedTextRequest;
import org.apache.opennlp.grpc.v1.EmbedTextResponse;
import org.apache.opennlp.grpc.v1.EmbeddingAnnotation;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.ModelDescriptor;
import org.apache.opennlp.grpc.v1.SemanticChunkingConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Defines the additive protobuf contract for selecting and reporting embedding routes. */
class EmbeddingWireContractTest {

  @Test
  void selectorKeepsOpenModelAndBackendIdsSeparate() {
    final FieldDescriptor selector = requiredMessageField(
        AnalysisOptions.getDescriptor(), "embedding_selector", "EmbeddingSelector", false);
    final Descriptor type = selector.getMessageType();

    final FieldDescriptor modelId = type.findFieldByName("model_id");
    final FieldDescriptor backendId = type.findFieldByName("backend_id");
    assertNotNull(modelId);
    assertNotNull(backendId);
    assertEquals(FieldDescriptor.Type.STRING, modelId.getType());
    assertEquals(FieldDescriptor.Type.STRING, backendId.getType());
    assertFalse(modelId.isRepeated());
    assertFalse(backendId.isRepeated());
  }

  @Test
  void everyEmbeddingRequestSurfaceAcceptsSelectors() {
    requiredMessageField(AnalysisOptions.getDescriptor(),
        "embedding_selector", "EmbeddingSelector", false);
    requiredMessageField(ChunkEmbedConfigEntry.getDescriptor(),
        "embedding_selectors", "EmbeddingSelector", true);
    requiredMessageField(CategoryChunkConfigEntry.getDescriptor(),
        "embedding_selectors", "EmbeddingSelector", true);
    requiredMessageField(SemanticChunkingConfig.getDescriptor(),
        "semantic_embedding_selector", "EmbeddingSelector", false);
    requiredMessageField(EmbedTextRequest.getDescriptor(),
        "embedding_selector", "EmbeddingSelector", false);
  }

  @Test
  void resultsAndCatalogExposeActualRoutes() {
    final FieldDescriptor resultRoute = requiredMessageField(
        EmbeddingResult.getDescriptor(), "route", "EmbeddingRoute", false);
    requiredMessageField(EmbeddingAnnotation.getDescriptor(),
        "route", "EmbeddingRoute", false);
    requiredMessageField(ModelDescriptor.getDescriptor(),
        "embedding_routes", "EmbeddingRoute", true);
    requiredMessageField(EmbedTextResponse.getDescriptor(),
        "route", "EmbeddingRoute", false);

    final Descriptor route = resultRoute.getMessageType();
    assertStringField(route, "model_id");
    assertStringField(route, "backend_id");
    assertStringField(route, "vector_space_id");
    assertStringField(route, "artifact_hash");
    assertEquals(FieldDescriptor.Type.INT32,
        requiredField(route, "priority").getType());
    assertEquals(FieldDescriptor.Type.BOOL,
        requiredField(route, "primary").getType());
  }

  private static FieldDescriptor requiredMessageField(
      Descriptor owner, String name, String typeName, boolean repeated) {
    final FieldDescriptor field = requiredField(owner, name);
    assertEquals(FieldDescriptor.Type.MESSAGE, field.getType());
    assertEquals(typeName, field.getMessageType().getName());
    assertEquals(repeated, field.isRepeated());
    return field;
  }

  private static void assertStringField(Descriptor owner, String name) {
    final FieldDescriptor field = requiredField(owner, name);
    assertEquals(FieldDescriptor.Type.STRING, field.getType());
    assertFalse(field.isRepeated());
  }

  private static FieldDescriptor requiredField(Descriptor owner, String name) {
    final FieldDescriptor field = owner.findFieldByName(name);
    assertNotNull(field, () -> owner.getFullName() + " is missing field " + name);
    return field;
  }
}
