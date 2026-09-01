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
import java.util.concurrent.CancellationException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.v1.DeleteStaticModelRequest;
import org.apache.opennlp.grpc.v1.DeleteStaticModelResponse;
import org.apache.opennlp.grpc.v1.InstallModelRequest;
import org.apache.opennlp.grpc.v1.InstallModelUpdate;
import org.apache.opennlp.grpc.v1.ListInstalledModelsRequest;
import org.apache.opennlp.grpc.v1.ListInstalledModelsResponse;
import org.apache.opennlp.grpc.v1.ListModelCatalogRequest;
import org.apache.opennlp.grpc.v1.ListModelCatalogResponse;
import org.apache.opennlp.grpc.v1.ListStaticModelsRequest;
import org.apache.opennlp.grpc.v1.ListStaticModelsResponse;
import org.apache.opennlp.grpc.v1.ListTeachersRequest;
import org.apache.opennlp.grpc.v1.ListTeachersResponse;
import org.apache.opennlp.grpc.v1.OpenNlpModelTrainingServiceGrpc;
import org.apache.opennlp.grpc.v1.StaticModelDescriptor;
import org.apache.opennlp.grpc.v1.StreamingTrainingRequest;
import org.apache.opennlp.grpc.v1.StreamingTrainingUpdate;
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
  private final CatalogModelStore catalogStore;
  private final Semaphore trainingPermits;
  private final StreamingTrainingPipeline streamingPipeline;

  /**
   * Creates the service over one model artifact store.
   *
   * @param store Bounded model store, possibly write-disabled.
   * @throws IllegalArgumentException If {@code store} is {@code null}.
   */
  public OpenNlpModelTrainingServiceImpl(StaticModelArtifactStore store) {
    this(store, (StreamingTrainingPipeline) null, null);
  }

  /**
   * Creates the service with node-local catalog installation enabled.
   *
   * @param store Bounded model store, possibly write-disabled.
   * @param catalogStore Node-local catalog store.
   * @throws IllegalArgumentException If {@code store} or {@code catalogStore} is {@code null}.
   */
  public OpenNlpModelTrainingServiceImpl(
      StaticModelArtifactStore store, CatalogModelStore catalogStore) {
    this(store, null, catalogStore);
    if (catalogStore == null) {
      throw new IllegalArgumentException("catalogStore must not be null");
    }
  }

  /**
   * Creates the service with bidirectional document-to-index orchestration enabled.
   *
   * @param store Bounded model store, possibly write-disabled.
   * @param streamingPipeline Production streaming pipeline.
   * @throws IllegalArgumentException If an argument is {@code null}.
   */
  public OpenNlpModelTrainingServiceImpl(
      StaticModelArtifactStore store,
      DefaultStreamingTrainingPipeline streamingPipeline) {
    this(store, (StreamingTrainingPipeline) streamingPipeline);
  }

  /**
   * Creates the complete training service with streaming orchestration and model catalog.
   *
   * @param store Bounded model store, possibly write-disabled.
   * @param streamingPipeline Production streaming pipeline.
   * @param catalogStore Node-local catalog store.
   * @throws IllegalArgumentException If {@code store} or {@code catalogStore} is {@code null}.
   */
  public OpenNlpModelTrainingServiceImpl(
      StaticModelArtifactStore store,
      DefaultStreamingTrainingPipeline streamingPipeline,
      CatalogModelStore catalogStore) {
    this(store, (StreamingTrainingPipeline) streamingPipeline, catalogStore);
    if (catalogStore == null) {
      throw new IllegalArgumentException("catalogStore must not be null");
    }
  }

  /** Package-private seam for deterministic transport and admission tests. */
  OpenNlpModelTrainingServiceImpl(
      StaticModelArtifactStore store,
      StreamingTrainingPipeline streamingPipeline) {
    this(store, streamingPipeline, null);
  }

  /** Package-private complete constructor for deterministic transport tests. */
  OpenNlpModelTrainingServiceImpl(
      StaticModelArtifactStore store,
      StreamingTrainingPipeline streamingPipeline,
      CatalogModelStore catalogStore) {
    if (store == null) {
      throw new IllegalArgumentException("store must not be null");
    }
    this.store = store;
    this.catalogStore = catalogStore;
    this.streamingPipeline = streamingPipeline;
    this.trainingPermits = new Semaphore(store.maxConcurrentTrainings());
  }

  /** {@inheritDoc} */
  @Override
  public void listModelCatalog(
      ListModelCatalogRequest request,
      StreamObserver<ListModelCatalogResponse> responseObserver) {
    final ListModelCatalogResponse.Builder response = ListModelCatalogResponse.newBuilder();
    if (catalogStore != null) {
      response.addAllModels(catalogStore.catalogModels())
          .setInstallsEnabled(catalogStore.installsEnabled());
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  /** {@inheritDoc} */
  @Override
  public void listInstalledModels(
      ListInstalledModelsRequest request,
      StreamObserver<ListInstalledModelsResponse> responseObserver) {
    final ListInstalledModelsResponse.Builder response =
        ListInstalledModelsResponse.newBuilder();
    if (catalogStore != null) {
      response.addAllModels(catalogStore.installedModels())
          .setInstallsEnabled(catalogStore.installsEnabled());
    }
    responseObserver.onNext(response.build());
    responseObserver.onCompleted();
  }

  /** {@inheritDoc} */
  @Override
  public void installModel(
      InstallModelRequest request,
      StreamObserver<InstallModelUpdate> responseObserver) {
    if (catalogStore == null || !catalogStore.installsEnabled()) {
      responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(
          CatalogModelStore.CATALOG_ROOT_KEY
              + " is not configured; catalog installation is disabled")
          .asRuntimeException());
      return;
    }
    final AtomicBoolean cancelled = new AtomicBoolean();
    final ServerCallStreamObserver<InstallModelUpdate> serverCall =
        responseObserver instanceof ServerCallStreamObserver<InstallModelUpdate> call
            ? call : null;
    if (serverCall != null) {
      serverCall.setOnCancelHandler(() -> cancelled.set(true));
      if (serverCall.isCancelled()) {
        return;
      }
    }
    try {
      final var installed = catalogStore.install(request, update -> {
        requireActive(serverCall, cancelled);
        responseObserver.onNext(InstallModelUpdate.newBuilder().setProgress(update).build());
        requireActive(serverCall, cancelled);
      }, () -> cancelled.get() || (serverCall != null && serverCall.isCancelled()));
      requireActive(serverCall, cancelled);
      responseObserver.onNext(InstallModelUpdate.newBuilder().setModel(installed).build());
      requireActive(serverCall, cancelled);
      responseObserver.onCompleted();
    } catch (CancellationException e) {
      logger.info("InstallModel cancelled by the client");
    } catch (ConcurrentModelInstallException e) {
      responseObserver.onError(Status.RESOURCE_EXHAUSTED.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (IllegalArgumentException e) {
      responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (IllegalStateException e) {
      responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (InsufficientDiskSpaceException e) {
      responseObserver.onError(Status.RESOURCE_EXHAUSTED.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (CatalogChecksumException e) {
      logger.warn("Catalog model failed verification", e);
      responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (CatalogDownloadException e) {
      logger.warn("Catalog model download failed", e);
      responseObserver.onError(Status.UNAVAILABLE.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (IOException e) {
      logger.error("Catalog model installation failed", e);
      responseObserver.onError(Status.INTERNAL.withDescription("Catalog model installation failed")
          .withCause(e).asRuntimeException());
    }
  }

  /** {@inheritDoc} */
  @Override
  public StreamObserver<StreamingTrainingRequest> streamingTraining(
      StreamObserver<StreamingTrainingUpdate> responseObserver) {
    if (streamingPipeline == null) {
      responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(
          "StreamingTraining is not configured").asRuntimeException());
      return ignoredObserver();
    }
    if (!trainingPermits.tryAcquire()) {
      responseObserver.onError(Status.RESOURCE_EXHAUSTED.withDescription(
          "concurrent trainings exceed configured maximum "
              + store.maxConcurrentTrainings()).asRuntimeException());
      return ignoredObserver();
    }
    return new StreamingTrainingSession(
        streamingPipeline, responseObserver, trainingPermits::release);
  }

  /** Returns a sink used after an RPC is rejected before its first input frame. */
  private static StreamObserver<StreamingTrainingRequest> ignoredObserver() {
    return new StreamObserver<>() {
      @Override
      public void onNext(StreamingTrainingRequest request) {
      }

      @Override
      public void onError(Throwable throwable) {
      }

      @Override
      public void onCompleted() {
      }
    };
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
    final AtomicBoolean cancelled = new AtomicBoolean();
    final ServerCallStreamObserver<TrainStaticModelUpdate> serverCall =
        responseObserver instanceof ServerCallStreamObserver<TrainStaticModelUpdate> call
            ? call : null;
    if (serverCall != null) {
      serverCall.setOnCancelHandler(() -> cancelled.set(true));
      if (serverCall.isCancelled()) {
        return;
      }
    }
    if (!trainingPermits.tryAcquire()) {
      responseObserver.onError(Status.RESOURCE_EXHAUSTED.withDescription(
          "concurrent trainings exceed configured maximum "
              + store.maxConcurrentTrainings()).asRuntimeException());
      return;
    }
    try {
      final StaticModelDescriptor descriptor = store.trainStaticModel(request,
          message -> {
            requireActive(serverCall, cancelled);
            responseObserver.onNext(TrainStaticModelUpdate.newBuilder()
                .setProgress(message)
                .build());
            requireActive(serverCall, cancelled);
          }, () -> cancelled.get() || (serverCall != null && serverCall.isCancelled()));
      requireActive(serverCall, cancelled);
      responseObserver.onNext(TrainStaticModelUpdate.newBuilder()
          .setModel(descriptor)
          .build());
      requireActive(serverCall, cancelled);
      responseObserver.onCompleted();
    } catch (CancellationException e) {
      logger.info("TrainStaticModel cancelled by the client");
    } catch (UnknownVocabularyArtifactException e) {
      responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (IllegalArgumentException e) {
      responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (IllegalStateException e) {
      responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage())
          .asRuntimeException());
    } catch (IOException e) {
      logger.error("TrainStaticModel failed", e);
      responseObserver.onError(Status.INTERNAL.withDescription("TrainStaticModel failed")
          .asRuntimeException());
    } finally {
      trainingPermits.release();
    }
  }

  private static <T> void requireActive(
      ServerCallStreamObserver<T> serverCall,
      AtomicBoolean cancelled) {
    if (cancelled.get() || (serverCall != null && serverCall.isCancelled())) {
      throw new CancellationException("TrainStaticModel call is cancelled");
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
    } catch (IOException e) {
      logger.error("DeleteStaticModel failed", e);
      responseObserver.onError(Status.INTERNAL.withDescription("DeleteStaticModel failed")
          .asRuntimeException());
    }
  }
}
