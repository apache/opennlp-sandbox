/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import java.util.List;

import org.apache.opennlp.grpc.v1.ModelArtifactRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardModelCatalogTest {

  @Test
  void catalogsThePinnedTeacherAndThreePublishedModel2VecTables() {
    final List<CatalogModel> models = StandardModelCatalog.models();

    assertEquals(List.of(
        "all-minilm-l6-v2-teacher",
        "potion-base-8m",
        "potion-multilingual-128m",
        "potion-retrieval-32m"),
        models.stream().map(model -> model.descriptor().getCatalogId()).toList());
    assertEquals(ModelArtifactRole.MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER,
        models.getFirst().descriptor().getRole());
    assertTrue(models.subList(1, models.size()).stream().allMatch(model ->
        model.descriptor().getRole()
            == ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING));
    assertEquals(256, models.get(1).descriptor().getDimension());
    assertEquals(256, models.get(2).descriptor().getDimension());
    assertEquals(512, models.get(3).descriptor().getDimension());
  }

  @Test
  void everyCatalogFileHasAnExactSizeAndSha256() {
    for (CatalogModel model : StandardModelCatalog.models()) {
      long total = 0;
      for (CatalogFile file : model.files()) {
        assertTrue(file.relativePath().getNameCount() <= 2);
        assertTrue(file.byteSize() > 0);
        assertEquals(64, file.sha256().length());
        total += file.byteSize();
      }
      assertEquals(total, model.descriptor().getByteSize());
    }
  }
}
