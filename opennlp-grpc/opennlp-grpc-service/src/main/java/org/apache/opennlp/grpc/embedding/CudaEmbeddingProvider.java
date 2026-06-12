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
 * ONNX Runtime embedding provider running on the CUDA execution provider.
 *
 * <p>Requires a server built with the {@code gpu} Maven profile, which replaces
 * the {@code onnxruntime} jar with {@code onnxruntime_gpu}, and a CUDA capable device at
 * runtime. The device is selected with {@code model.embedder.gpu_device_id}. See
 * {@link AbstractOnnxEmbeddingProvider} for the model configuration keys.</p>
 */
public final class CudaEmbeddingProvider extends AbstractOnnxEmbeddingProvider {

  /**
   * Loads all configured embedding models on the CUDA device.
   *
   * @param configuration The server configuration. Must not be {@code null}.
   */
  public CudaEmbeddingProvider(Map<String, String> configuration) {
    super(configuration, true);
  }

  @Override
  public String backendId() {
    return CudaEmbeddingBackendFactory.BACKEND_ID;
  }
}
