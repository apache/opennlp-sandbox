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

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import org.apache.opennlp.grpc.spi.catalog.CatalogFile;
import org.apache.opennlp.grpc.spi.catalog.CatalogModel;
import org.apache.opennlp.grpc.spi.catalog.ModelCatalogProvider;
import org.apache.opennlp.grpc.v1.ModelArtifactRole;
import org.apache.opennlp.grpc.v1.ModelCatalogDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the ServiceLoader aggregation of {@link ModelCatalogProvider} contributions. */
class ModelCatalogsTest {

  @Test
  void discoveryIsEmptyWithoutProviders() {
    // The built-in catalog ships in the opennlp-grpc-installer add-on, absent here.
    assertTrue(ModelCatalogs.discover().isEmpty());
  }

  @Test
  void aggregatesProvidersInOrder() {
    final List<CatalogModel> models = ModelCatalogs.aggregate(List.of(
        () -> List.of(entry("first")), () -> List.of(entry("second"))));
    assertEquals(List.of("first", "second"),
        models.stream().map(model -> model.descriptor().getCatalogId()).toList());
  }

  @Test
  void rejectsDuplicateCatalogIdsAcrossProviders() {
    final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> ModelCatalogs.aggregate(List.of(
            () -> List.of(entry("duplicate")), () -> List.of(entry("duplicate")))));
    assertTrue(failure.getMessage().contains("duplicate"));
  }

  @Test
  void rejectsANullCatalog() {
    assertThrows(IllegalArgumentException.class,
        () -> ModelCatalogs.aggregate(List.of(() -> null)));
  }

  /** Builds one format-valid catalog entry with the given catalog id. */
  private static CatalogModel entry(String catalogId) {
    final CatalogFile file = new CatalogFile(Path.of("model.bin"),
        URI.create("https://example.invalid/model.bin"), 4,
        "9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a");
    return new CatalogModel(ModelCatalogDescriptor.newBuilder()
        .setCatalogId(catalogId)
        .setDisplayName("Test model")
        .setRole(ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER)
        .setModelId(catalogId)
        .setSourceUri("https://example.invalid/model")
        .setRevision("0123456789abcdef0123456789abcdef01234567")
        .setLicenseName("Test-License")
        .setLicenseUri("https://example.invalid/license")
        .setByteSize(4)
        .addLanguages("en")
        .setDescription("Test model")
        .build(), List.of(file));
  }
}
