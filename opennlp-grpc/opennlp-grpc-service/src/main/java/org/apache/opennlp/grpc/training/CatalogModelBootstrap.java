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

import org.apache.opennlp.grpc.spi.catalog.CatalogModel;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.opennlp.grpc.v1.ModelArtifactRole;

/**
 * Adds verified, installed parser and chunker models to the immutable startup configuration.
 * No catalog path reaches a runtime registry until its exact file list, sizes, hashes, and
 * persisted descriptor have been checked.
 */
public final class CatalogModelBootstrap {

  private static final String STAGING_PREFIX = ".install-";

  private CatalogModelBootstrap() {
  }

  /**
   * Prepares server configuration from the standard immutable catalog.
   *
   * @param configuration Operator configuration.
   * @return An immutable configuration including verified parser and chunker paths.
   * @throws IOException If an installed catalog entry is invalid.
   * @throws IllegalArgumentException If configuration conflicts with an installed catalog model.
   */
  public static Map<String, String> prepare(Map<String, String> configuration)
      throws IOException {
    return prepare(configuration, ModelCatalogs.discover());
  }

  /**
   * Prepares configuration from an injectable catalog.
   *
   * @param configuration Operator configuration.
   * @param models Immutable catalog entries.
   * @return Configuration including verified parser and chunker paths.
   * @throws IOException If an installed catalog entry is invalid.
   * @throws IllegalArgumentException If configuration or catalog metadata is invalid.
   */
  static Map<String, String> prepare(
      Map<String, String> configuration, List<CatalogModel> models) throws IOException {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    // An empty catalog is legal: without the opennlp-grpc-installer add-on no provider
    // contributes entries and there is nothing to verify or publish.
    if (models == null) {
      throw new IllegalArgumentException("models must not be null");
    }
    final Map<String, String> prepared = new TreeMap<>(configuration);
    final Path root = CatalogModelStore.configuredRoot(configuration);
    if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
      return Map.copyOf(prepared);
    }
    CatalogModelStore.createRoot(root);
    final Map<String, CatalogModel> catalog = catalogById(models);
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
      for (Path entry : entries) {
        final String catalogId = entry.getFileName().toString();
        if (catalogId.startsWith(STAGING_PREFIX)) {
          continue;
        }
        final CatalogModel model = catalog.get(catalogId);
        if (model == null) {
          throw new IOException("Unknown entry in model catalog root: " + entry);
        }
        if (!Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
          throw new IOException("Catalog model entry is not a directory: " + entry);
        }
        CatalogModelStore.verifyInstalled(model, entry);
        addRestartModel(prepared, model, entry);
      }
    }
    return Map.copyOf(prepared);
  }

  /**
   * Builds a catalog lookup while rejecting duplicate public identities.
   *
   * @param models Immutable catalog entries.
   * @return Catalog entries indexed by public catalog id.
   */
  private static Map<String, CatalogModel> catalogById(List<CatalogModel> models) {
    final Map<String, CatalogModel> catalog = new HashMap<>();
    for (CatalogModel model : models) {
      final String catalogId = model.descriptor().getCatalogId();
      if (catalog.putIfAbsent(catalogId, model) != null) {
        throw new IllegalArgumentException("Duplicate catalog_id '" + catalogId + "'");
      }
    }
    return catalog;
  }

  /**
   * Adds one restart-only role to the configuration after full verification.
   *
   * @param prepared Mutable startup configuration.
   * @param model Verified catalog entry.
   * @param directory Published model directory.
   */
  private static void addRestartModel(
      Map<String, String> prepared, CatalogModel model, Path directory) {
    final String key = restartConfigurationKey(model.descriptor());
    if (key == null) {
      return;
    }
    if (model.files().size() != 1) {
      throw new IllegalArgumentException(model.descriptor().getRole()
          + " catalog entries must contain one model file");
    }
    final String value = directory.resolve(model.files().getFirst().relativePath())
        .toAbsolutePath().normalize().toString();
    final String existing = prepared.putIfAbsent(key, value);
    if (existing != null && !existing.equals(value)) {
      throw new IllegalArgumentException(key + " is already configured; a server serves one "
          + "model per language pipeline slot, so uninstall the other model or remove the "
          + "operator setting");
    }
  }

  /**
   * Returns the startup configuration key one restart-only role publishes to, or
   * {@code null} for roles that serve without a restart.
   *
   * @param descriptor The catalog descriptor.
   * @return The configuration key, or {@code null}.
   */
  private static String restartConfigurationKey(
      org.apache.opennlp.grpc.v1.ModelCatalogDescriptor descriptor) {
    return CatalogRoles.restartConfigurationKey(descriptor);
  }
}
