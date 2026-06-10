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
import java.util.Set;

import org.apache.opennlp.grpc.v1.InferenceBackend;

/**
 * ONNX Runtime embedding provider running on the CPU execution provider.
 *
 * <p>Serves {@code INFERENCE_BACKEND_ONNX_RUNTIME} requests. See
 * {@link AbstractOnnxEmbeddingProvider} for the model configuration keys.</p>
 */
public final class OnnxRuntimeEmbeddingProvider extends AbstractOnnxEmbeddingProvider {

  /**
   * Loads all configured embedding models on the CPU.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   */
  public OnnxRuntimeEmbeddingProvider(Map<String, String> configuration) {
    super(configuration, false);
  }

  @Override
  Set<InferenceBackend> supportedBackends() {
    return Set.of(InferenceBackend.INFERENCE_BACKEND_ONNX_RUNTIME);
  }
}
