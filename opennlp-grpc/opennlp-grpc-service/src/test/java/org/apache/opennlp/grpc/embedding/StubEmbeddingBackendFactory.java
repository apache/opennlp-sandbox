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

  @Override
  public String backendId() {
    return BACKEND_ID;
  }

  @Override
  public EmbeddingProvider create(Map<String, String> configuration) {
    return new StubEmbeddingProvider(Map.of("stub-model", 3));
  }
}
