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

import java.util.Map;

/**
 * Test-only {@link EmbeddingBackendFactory} registered via {@code META-INF/services},
 * proving that external jars can contribute embedding backends without changes to the
 * server. Selected with {@code model.embedder.backend=stub}.
 */
public final class StubEmbeddingBackendFactory implements EmbeddingBackendFactory {

  public static final String BACKEND_ID = "stub";
  /** When set, the stub contributes one model with this id; otherwise it stays inert. */
  public static final String KEY_MODEL_ID = "model.embedder.stub.model_id";

  @Override
  public String backendId() {
    return BACKEND_ID;
  }

  @Override
  public EmbeddingProvider create(Map<String, String> configuration) {
    final String modelId = configuration.get(KEY_MODEL_ID);
    if (modelId == null || modelId.isBlank()) {
      // Inert unless explicitly activated, so the composite stays empty in unrelated tests.
      return new StubEmbeddingProvider(Map.of());
    }
    return new StubEmbeddingProvider(Map.of(modelId, 3));
  }
}
