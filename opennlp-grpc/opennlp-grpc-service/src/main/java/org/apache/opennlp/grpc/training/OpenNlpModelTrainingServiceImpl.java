/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import java.io.IOException;
import java.util.concurrent.Semaphore;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.v1.DeleteStaticModelRequest;
import org.apache.opennlp.grpc.v1.DeleteStaticModelResponse;
import org.apache.opennlp.grpc.v1.ListStaticModelsRequest;
import org.apache.opennlp.grpc.v1.ListStaticModelsResponse;
import org.apache.opennlp.grpc.v1.ListTeachersRequest;
import org.apache.opennlp.grpc.v1.ListTeachersResponse;
import org.apache.opennlp.grpc.v1.OpenNlpModelTrainingServiceGrpc;
import org.apache.opennlp.grpc.v1.StaticModelDescriptor;
import org.apache.opennlp.grpc.v1.TrainStaticModelRequest;
import org.apache.opennlp.grpc.v1.TrainStaticModelUpdate;
import org.apache.opennlp.grpc.vocabulary.UnknownVocabularyArtifactException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** gRPC adapter for teacher discovery and bounded static model distillation. */
public final class OpenNlpModelTrainingServiceImpl
    extends OpenNlpModelTrainingServiceGrpc.OpenNlpModelTrainingServiceImplBase {

  private static final Logger logger =
      LoggerFactory.getLogger(OpenNlpModelTrainingServiceImpl.class);

  private final StaticModelArtifactStore store;
  private final Semaphore trainingPermits;

  /**
   * Creates the service over one model artifact store.
   *
   * @param store Bounded model store, possibly write-disabled.
   * @throws IllegalArgumentException If {@code store} is {@code null}.
   */
  public OpenNlpModelTrainingServiceImpl(StaticModelArtifactStore store) {
    if (store == null) {
      throw new IllegalArgumentException("store must not be null");
    }
    this.store = store;
    this.trainingPermits = new Semaphore(store.maxConcurrentTrainings());
  }

  /** {@inheritDoc} */
  @Override
  public void listTeachers(
      ListTeachersRequest request, StreamObserver<ListTeachersResponse> responseObserver) {
    responseObserver.onNext(ListTeachersResponse.newBuilder()
        .addAllTeachers(store.teachers())
        .setWritesEnabled(store.writesEnabled())
        .setMaxPcaDims(store.maxPcaDims())
        .setMaxConcurrentTrainings(store.maxConcurrentTrainings())
        .build());
    responseObserver.onCompleted();
  }

  /** {@inheritDoc} */
  @Override
  public void trainStaticModel(
      TrainStaticModelRequest request,
      StreamObserver<TrainStaticModelUpdate> responseObserver) {
    if (!trainingPermits.tryAcquire()) {
      responseObserver.onError(Status.RESOURCE_EXHAUSTED.withDescription(
          "concurrent trainings exceed configured maximum "
              + store.maxConcurrentTrainings()).asRuntimeException());
      return;
    }
    try {
      final StaticModelDescriptor descriptor = store.trainStaticModel(request,
          message -> responseObserver.onNext(TrainStaticModelUpdate.newBuilder()
              .setProgress(message)
              .build()));
      responseObserver.onNext(TrainStaticModelUpdate.newBuilder()
          .setModel(descriptor)
          .build());
      responseObserver.onCompleted();
    } catch (UnknownVocabularyArtifactException e) {
      responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (IllegalArgumentException e) {
      responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (IllegalStateException e) {
      responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (IOException | RuntimeException e) {
      logger.error("TrainStaticModel failed", e);
      responseObserver.onError(Status.INTERNAL.withDescription("TrainStaticModel failed")
          .asRuntimeException());
    } finally {
      trainingPermits.release();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void listStaticModels(
      ListStaticModelsRequest request,
      StreamObserver<ListStaticModelsResponse> responseObserver) {
    responseObserver.onNext(ListStaticModelsResponse.newBuilder()
        .addAllModels(store.models())
        .setWritesEnabled(store.writesEnabled())
        .build());
    responseObserver.onCompleted();
  }

  /** {@inheritDoc} */
  @Override
  public void deleteStaticModel(
      DeleteStaticModelRequest request,
      StreamObserver<DeleteStaticModelResponse> responseObserver) {
    try {
      final boolean deleted = store.deleteModel(request.getArtifactId());
      responseObserver.onNext(DeleteStaticModelResponse.newBuilder()
          .setArtifactId(request.getArtifactId())
          .setDeleted(deleted)
          .build());
      responseObserver.onCompleted();
    } catch (IllegalArgumentException e) {
      responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (IllegalStateException e) {
      responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (IOException | RuntimeException e) {
      logger.error("DeleteStaticModel failed", e);
      responseObserver.onError(Status.INTERNAL.withDescription("DeleteStaticModel failed")
          .asRuntimeException());
    }
  }
}
