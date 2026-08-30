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
package org.apache.opennlp.grpc.installer;

import org.apache.opennlp.grpc.spi.catalog.CatalogModel;
import java.util.List;

import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import org.apache.opennlp.grpc.v1.ModelArtifactRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the additive catalog contract for the classic pipeline models: the four new
 * artifact roles and the pinned Apache UD 1.3 language packs for German, French, and
 * Spanish, each with a sentence detector, tokenizer, POS tagger, and lemmatizer.
 */
class UdLanguageModelCatalogTest {

  @Test
  void modelArtifactRolesCoverTheClassicPipelineModels() {
    final EnumDescriptor role = ModelArtifactRole.getDescriptor();
    assertRole(role, "MODEL_ARTIFACT_ROLE_SENTENCE_DETECTOR", 5);
    assertRole(role, "MODEL_ARTIFACT_ROLE_TOKENIZER", 6);
    assertRole(role, "MODEL_ARTIFACT_ROLE_POS_TAGGER", 7);
    assertRole(role, "MODEL_ARTIFACT_ROLE_LEMMATIZER", 8);
  }

  @Test
  void catalogsCompleteUdPipelinesForGermanFrenchAndSpanish() {
    final List<CatalogModel> models = new StandardModelCatalog().models();
    for (String modelId : List.of("de-ud-gsd", "fr-ud-gsd", "es-ud-gsd")) {
      final List<CatalogModel> pack = models.stream()
          .filter(model -> modelId.equals(model.descriptor().getModelId()))
          .toList();
      assertEquals(4, pack.size(), "expected a complete pipeline pack for " + modelId);
      for (CatalogModel model : pack) {
        assertEquals(1, model.files().size());
        assertEquals("Apache-2.0", model.descriptor().getLicenseName());
        assertEquals(1, model.descriptor().getLanguagesCount());
        assertTrue(model.files().getFirst().source().toString()
            .startsWith("https://downloads.apache.org/opennlp/models/ud-models-1.3/"));
      }
    }
  }

  private static void assertRole(EnumDescriptor role, String name, int number) {
    final EnumValueDescriptor value = role.findValueByName(name);
    assertNotNull(value, name + " is missing");
    assertEquals(number, value.getNumber());
  }
}
