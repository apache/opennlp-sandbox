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

import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.v1.DocumentClassification;

/**
 * Test-only {@link DocCategorizerBackendFactory} registered via {@code META-INF/services},
 * proving an external jar can contribute a document categorizer backend without changes to the
 * server. It is activated by a {@code model.doccat_stub.category=<label>} configuration entry and
 * otherwise contributes nothing, so it stays inert for every other test.
 */
public final class StubDocCategorizerBackendFactory implements DocCategorizerBackendFactory {

  public static final String FACTORY_ID = "stub";
  public static final String KEY_CATEGORY = "model.doccat_stub.category";

  @Override
  public String factoryId() {
    return FACTORY_ID;
  }

  @Override
  public List<DocCategorizerModel> create(Map<String, String> configuration) {
    final String category = configuration.get(KEY_CATEGORY);
    if (category == null || category.isBlank()) {
      return List.of();
    }
    return List.of(new StubDocCategorizerModel(DocCategorizerRegistry.normalize(category)));
  }

  /** A categorizer that always returns its single fixed category with score 1.0. */
  private record StubDocCategorizerModel(String category) implements DocCategorizerModel {

    @Override
    public String id() {
      return FACTORY_ID + ":" + category;
    }

    @Override
    public String backendId() {
      return FACTORY_ID;
    }

    @Override
    public List<String> categories() {
      return List.of(category);
    }

    @Override
    public DocumentClassification classify(String documentText, String[] documentTokens) {
      return DocumentClassification.newBuilder()
          .setBestCategory(category)
          .putCategoryScores(category, 1.0d)
          .build();
    }
  }
}
