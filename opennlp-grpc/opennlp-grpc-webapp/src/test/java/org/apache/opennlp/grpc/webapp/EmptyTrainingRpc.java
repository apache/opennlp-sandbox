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
import java.util.List;

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

/** A write-disabled training adapter for tests that never exercise it. */
final class EmptyTrainingRpc implements TrainingRpc {

  @Override
  public ListTeachersResponse listTeachers() {
    return ListTeachersResponse.getDefaultInstance();
  }

  @Override
  public ListModelCatalogResponse listModelCatalog() {
    return ListModelCatalogResponse.getDefaultInstance();
  }

  @Override
  public ListInstalledModelsResponse listInstalledModels() {
    return ListInstalledModelsResponse.getDefaultInstance();
  }

  @Override
  public Iterator<InstallModelUpdate> installModel(InstallModelRequest request) {
    return List.<InstallModelUpdate>of().iterator();
  }

  @Override
  public Iterator<TrainStaticModelUpdate> trainStaticModel(TrainStaticModelRequest request) {
    return List.<TrainStaticModelUpdate>of().iterator();
  }

  @Override
  public ListStaticModelsResponse listStaticModels() {
    return ListStaticModelsResponse.getDefaultInstance();
  }

  @Override
  public DeleteStaticModelResponse deleteStaticModel(DeleteStaticModelRequest request) {
    return DeleteStaticModelResponse.getDefaultInstance();
  }
}
