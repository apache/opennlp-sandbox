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

package opennlp.service;

import opennlp.OpenNLPService;
import opennlp.tools.models.ClassPathModel;

/**
 * The {@code ModelAware} interface defines behavior for services that are aware of model management
 * and provides functionalities for accessing and handling cached models.
 *
 * @param <T> The type of service that is managed and cached.
 */
public interface ModelAware<T> extends CacheAware<T>, ExceptionAware {

  /**
   * Retrieves a list of available models from the model cache and streams them to the client.
   *
   * <p>This method iterates through all models stored in the model cache and builds a list of
   * {@link OpenNLPService.Model} objects. Each model includes its SHA-256 hash, name, and locale
   * (language). The resulting list is sent to the client using the provided gRPC response observer.</p>
   *
   * @param responseObserver  The gRPC stream observer for sending the list of available models
   *                          back to the client.   *
   */
  default void returnAvailableModels(io.grpc.stub.StreamObserver<opennlp.OpenNLPService.AvailableModels> responseObserver) {

    try {
      final OpenNLPService.AvailableModels.Builder response = OpenNLPService.AvailableModels.newBuilder();
      for (ClassPathModel model : getModelCache().values()) {
        final OpenNLPService.Model m = OpenNLPService.Model.newBuilder()
            .setHash(model.getModelSHA256())
            .setName(model.getModelName())
            .setLocale(model.getModelLanguage())
            .build();

        response.addModels(m);

      }

      responseObserver.onNext(response.build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      handleException(e, responseObserver);
    }
  }
}
