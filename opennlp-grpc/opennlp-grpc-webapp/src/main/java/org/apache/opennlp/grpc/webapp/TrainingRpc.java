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
package org.apache.opennlp.grpc.webapp;

import java.util.Iterator;

import org.apache.opennlp.grpc.v1.DeleteStaticModelRequest;
import org.apache.opennlp.grpc.v1.DeleteStaticModelResponse;
import org.apache.opennlp.grpc.v1.InstallModelRequest;
import org.apache.opennlp.grpc.v1.InstallModelUpdate;
import org.apache.opennlp.grpc.v1.ListInstalledModelsResponse;
import org.apache.opennlp.grpc.v1.ListModelCatalogResponse;
import org.apache.opennlp.grpc.v1.ListStaticModelsResponse;
import org.apache.opennlp.grpc.v1.ListTeachersResponse;
import org.apache.opennlp.grpc.v1.TrainStaticModelRequest;
import org.apache.opennlp.grpc.v1.TrainStaticModelUpdate;

interface TrainingRpc {

  /** @return The configured teachers and the effective training limits. */
  ListTeachersResponse listTeachers();

  /**
   * Lists the standard model catalog.
   *
   * @return The immutable catalog and node installation availability.
   */
  ListModelCatalogResponse listModelCatalog();

  /**
   * Lists installed models.
   *
   * @return Models installed and activated on this server node.
   */
  ListInstalledModelsResponse listInstalledModels();

  /**
   * Installs one acknowledged catalog model.
   *
   * @param request Pinned catalog identity and license acknowledgement.
   * @return Progress and the terminal installed-model descriptor.
   */
  Iterator<InstallModelUpdate> installModel(InstallModelRequest request);

  /**
   * Starts one distillation and returns its blocking update stream.
   *
   * @param request Teacher, vocabulary artifact, and training controls.
   * @return Progress updates ending with the terminal model descriptor. Advancing the
   *     iterator may throw {@link io.grpc.StatusRuntimeException} at any point.
   */
  Iterator<TrainStaticModelUpdate> trainStaticModel(TrainStaticModelRequest request);

  /** @return Every published static model on the gRPC server. */
  ListStaticModelsResponse listStaticModels();

  /**
   * Deletes one published static model.
   *
   * @param request Model artifact identifier.
   * @return Deletion result.
   */
  DeleteStaticModelResponse deleteStaticModel(DeleteStaticModelRequest request);
}
