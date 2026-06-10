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
 * {@link EmbeddingBackendFactory} for the default {@code onnx} backend
 * (ONNX Runtime on the CPU execution provider).
 */
public final class OnnxEmbeddingBackendFactory implements EmbeddingBackendFactory {

  /** The backend id: {@value}. */
  public static final String BACKEND_ID = "onnx";

  @Override
  public String backendId() {
    return BACKEND_ID;
  }

  @Override
  public EmbeddingProvider create(Map<String, String> configuration) {
    return new OnnxRuntimeEmbeddingProvider(configuration);
  }
}
