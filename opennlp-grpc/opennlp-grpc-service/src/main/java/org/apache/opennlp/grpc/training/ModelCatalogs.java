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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.opennlp.grpc.spi.catalog.CatalogModel;
import org.apache.opennlp.grpc.spi.catalog.ModelCatalogProvider;

/**
 * Aggregates the installable model catalog from every {@link ModelCatalogProvider} discovered
 * via {@link ServiceLoader}. The built-in catalog ships in the {@code opennlp-grpc-installer}
 * add-on; without a provider on the classpath the catalog is empty and installs are refused
 * honestly.
 */
public final class ModelCatalogs {

  private ModelCatalogs() {
  }

  /**
   * Discovers all registered catalog providers and aggregates their entries.
   *
   * @return All discovered entries in provider registration order, possibly empty.
   *
   * @throws IllegalArgumentException If two providers declare the same catalog id or a
   *     provider returns an invalid catalog.
   */
  public static List<CatalogModel> discover() {
    return aggregate(ServiceLoader.load(ModelCatalogProvider.class));
  }

  /**
   * Aggregates from the given providers instead of {@link ServiceLoader} discovery;
   * package-private so tests can drive the provider set directly.
   *
   * @param providers The catalog providers to aggregate.
   *
   * @return All contributed entries in provider order, possibly empty.
   *
   * @throws IllegalArgumentException If two providers declare the same catalog id or a
   *     provider returns an invalid catalog.
   */
  static List<CatalogModel> aggregate(Iterable<ModelCatalogProvider> providers) {
    final List<CatalogModel> models = new ArrayList<>();
    final Map<String, String> owners = new LinkedHashMap<>();
    for (ModelCatalogProvider provider : providers) {
      final List<CatalogModel> contributed = provider.models();
      if (contributed == null) {
        throw new IllegalArgumentException(
            provider.getClass().getName() + " returned a null catalog");
      }
      for (CatalogModel model : contributed) {
        final String id = model.descriptor().getCatalogId();
        final String previous = owners.putIfAbsent(id, provider.getClass().getName());
        if (previous != null) {
          throw new IllegalArgumentException("Catalog id '" + id + "' is declared by both "
              + previous + " and " + provider.getClass().getName());
        }
        models.add(model);
      }
    }
    return List.copyOf(models);
  }
}
