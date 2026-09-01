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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import java.net.URI;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.spi.catalog.CatalogFile;
import org.apache.opennlp.grpc.spi.catalog.CatalogModel;
import org.apache.opennlp.grpc.v1.DeleteStaticModelRequest;
import org.apache.opennlp.grpc.v1.DeleteStaticModelResponse;
import org.apache.opennlp.grpc.v1.InstallModelRequest;
import org.apache.opennlp.grpc.v1.InstallModelUpdate;
import org.apache.opennlp.grpc.v1.ListInstalledModelsRequest;
import org.apache.opennlp.grpc.v1.ListInstalledModelsResponse;
import org.apache.opennlp.grpc.v1.ListModelCatalogRequest;
import org.apache.opennlp.grpc.v1.ListModelCatalogResponse;
import org.apache.opennlp.grpc.v1.ModelCatalogDescriptor;
import org.apache.opennlp.grpc.v1.ListStaticModelsRequest;
import org.apache.opennlp.grpc.v1.ListStaticModelsResponse;
import org.apache.opennlp.grpc.v1.ListTeachersRequest;
import org.apache.opennlp.grpc.v1.ListTeachersResponse;
import org.apache.opennlp.grpc.v1.ModelArtifactRole;
import org.apache.opennlp.grpc.v1.StaticModelDescriptor;
import org.apache.opennlp.grpc.v1.StreamingTrainingModelPlan;
import org.apache.opennlp.grpc.v1.StreamingTrainingRequest;
import org.apache.opennlp.grpc.v1.StreamingTrainingStart;
import org.apache.opennlp.grpc.v1.StreamingTrainingUpdate;
import org.apache.opennlp.grpc.v1.TrainStaticModelRequest;
import org.apache.opennlp.grpc.v1.TrainStaticModelUpdate;
import org.apache.opennlp.grpc.vocabulary.DictionaryFormatRegistry;
import org.apache.opennlp.grpc.vocabulary.VocabularyArtifactStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenNlpModelTrainingServiceImplTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void rejectsNullCatalogStoreAtTheCatalogConstructorBoundary() throws Exception {
    final TrainedModelEmbeddingProvider registry =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    final StaticModelArtifactStore models = modelStore(Map.of(), registry);

    final IllegalArgumentException twoArgumentFailure = assertThrows(
        IllegalArgumentException.class,
        () -> new OpenNlpModelTrainingServiceImpl(models, (CatalogModelStore) null));
    final IllegalArgumentException completeFailure = assertThrows(
        IllegalArgumentException.class,
        () -> new OpenNlpModelTrainingServiceImpl(
            models, (DefaultStreamingTrainingPipeline) null, null));

    assertEquals("catalogStore must not be null", twoArgumentFailure.getMessage());
    assertEquals("catalogStore must not be null", completeFailure.getMessage());
  }

  @Test
  void listsTheDiscoveredCatalogAndNodeLocalInstallState() throws Exception {
    // The catalog is injected directly; the built-in entries ship in the
    // opennlp-grpc-installer add-on and are covered by that module's tests.
    final TrainedModelEmbeddingProvider registry =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    final StaticModelArtifactStore models = modelStore(Map.of(), registry);
    final CatalogModelStore catalog = disabledInstallCatalog(models, registry);
    final OpenNlpModelTrainingServiceImpl service =
        new OpenNlpModelTrainingServiceImpl(models, catalog);
    final TrainingTestSupport.CapturingObserver<ListModelCatalogResponse> listed =
        new TrainingTestSupport.CapturingObserver<>();
    final TrainingTestSupport.CapturingObserver<ListInstalledModelsResponse> installed =
        new TrainingTestSupport.CapturingObserver<>();

    service.listModelCatalog(ListModelCatalogRequest.getDefaultInstance(), listed);
    service.listInstalledModels(ListInstalledModelsRequest.getDefaultInstance(), installed);

    assertEquals(1, listed.values.getFirst().getModelsCount());
    assertEquals(ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER,
        listed.values.getFirst().getModels(0).getRole());
    assertFalse(listed.values.getFirst().getInstallsEnabled());
    assertEquals(0, installed.values.getFirst().getModelsCount());
    assertFalse(installed.values.getFirst().getInstallsEnabled());
  }

  @Test
  void mapsDisabledCatalogInstallToFailedPrecondition() throws Exception {
    final TrainedModelEmbeddingProvider registry =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    final StaticModelArtifactStore models = modelStore(Map.of(), registry);
    final CatalogModelStore catalog = disabledInstallCatalog(models, registry);
    final OpenNlpModelTrainingServiceImpl service =
        new OpenNlpModelTrainingServiceImpl(models, catalog);
    final var descriptor = catalog.catalogModels().getFirst();

    final TrainingTestSupport.CapturingObserver<InstallModelUpdate> noConsent =
        new TrainingTestSupport.CapturingObserver<>();
    service.installModel(InstallModelRequest.newBuilder()
        .setCatalogId(descriptor.getCatalogId())
        .setRevision(descriptor.getRevision())
        .setLicenseName(descriptor.getLicenseName())
        .build(), noConsent);
    assertEquals(Status.Code.FAILED_PRECONDITION,
        ((StatusRuntimeException) noConsent.error).getStatus().getCode());
  }

  @Test
  void mapsMissingCatalogConsentToInvalidArgumentBeforeDownloading() throws Exception {
    final TrainedModelEmbeddingProvider registry =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    final StaticModelArtifactStore models = modelStore(Map.of(), registry);
    final CatalogModelStore catalog = new CatalogModelStore(
        temporaryDirectory.resolve("catalog"), List.of(testCatalogModel()), models, registry,
        (file, target) -> {
          throw new IOException("download must not start without consent");
        });
    final OpenNlpModelTrainingServiceImpl service =
        new OpenNlpModelTrainingServiceImpl(models, catalog);
    final var descriptor = catalog.catalogModels().getFirst();
    final TrainingTestSupport.CapturingObserver<InstallModelUpdate> response =
        new TrainingTestSupport.CapturingObserver<>();

    service.installModel(InstallModelRequest.newBuilder()
        .setCatalogId(descriptor.getCatalogId())
        .setRevision(descriptor.getRevision())
        .setLicenseName(descriptor.getLicenseName())
        .build(), response);

    assertEquals(Status.Code.INVALID_ARGUMENT,
        ((StatusRuntimeException) response.error).getStatus().getCode());
  }

  @Test
  void doesNotExposeCatalogTransportDetailsToClients() throws Exception {
    final TrainedModelEmbeddingProvider registry =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    final StaticModelArtifactStore models = modelStore(Map.of(), registry);
    final CatalogModel catalogModel = testCatalogModel();
    final CatalogModelStore catalog = new CatalogModelStore(
        temporaryDirectory.resolve("catalog"), List.of(catalogModel), models, registry,
        (file, target) -> {
          throw new IOException("secret transport detail in " + target);
        });
    final OpenNlpModelTrainingServiceImpl service =
        new OpenNlpModelTrainingServiceImpl(models, catalog);
    final TrainingTestSupport.CapturingObserver<InstallModelUpdate> response =
        new TrainingTestSupport.CapturingObserver<>();

    service.installModel(InstallModelRequest.newBuilder()
        .setCatalogId(catalogModel.descriptor().getCatalogId())
        .setRevision(catalogModel.descriptor().getRevision())
        .setLicenseName(catalogModel.descriptor().getLicenseName())
        .setLicenseAcknowledged(true)
        .build(), response);

    final StatusRuntimeException failure = (StatusRuntimeException) response.error;
    assertEquals(Status.Code.UNAVAILABLE, failure.getStatus().getCode());
    assertTrue(failure.getStatus().getDescription().startsWith("Download of "),
        failure.getStatus().getDescription());
    assertTrue(failure.getStatus().getDescription().endsWith("from example.invalid failed"),
        failure.getStatus().getDescription());
    assertFalse(failure.getStatus().getDescription().contains("secret transport detail"));
  }

  @Test
  void listsTeachersEvenWhenWritesAreDisabled() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final OpenNlpModelTrainingServiceImpl service = new OpenNlpModelTrainingServiceImpl(
        StaticModelArtifactStore.fromConfiguration(
            Map.of("training.teacher.mini.ref", "minishlab/potion-base-8M"),
            VocabularyArtifactStore.fromConfiguration(Map.of(), formats),
            new TrainingTestSupport.RecordingTrainer(),
            new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider())));
    final TrainingTestSupport.CapturingObserver<ListTeachersResponse> response =
        new TrainingTestSupport.CapturingObserver<>();

    service.listTeachers(ListTeachersRequest.getDefaultInstance(), response);

    assertNull(response.error);
    assertTrue(response.completed);
    final ListTeachersResponse teachers = response.values.getFirst();
    assertEquals(1, teachers.getTeachersCount());
    assertEquals("mini", teachers.getTeachers(0).getTeacherId());
    assertEquals("minishlab/potion-base-8M", teachers.getTeachers(0).getReference());
    assertFalse(teachers.getTeachers(0).getLocal());
    assertFalse(teachers.getWritesEnabled());
    assertTrue(teachers.getMaxPcaDims() > 0);
    assertTrue(teachers.getMaxConcurrentTrainings() > 0);
  }

  @Test
  void trainsStreamsProgressThenPublishesTheServingModel() throws Exception {
    final Fixture fixture = fixture();
    final TrainingTestSupport.CapturingObserver<TrainStaticModelUpdate> updates =
        new TrainingTestSupport.CapturingObserver<>();

    fixture.service.trainStaticModel(request(fixture.vocabularyId, "mini"), updates);

    assertNull(updates.error);
    assertTrue(updates.completed);
    assertTrue(updates.values.size() >= 2);
    for (TrainStaticModelUpdate update : updates.values.subList(0, updates.values.size() - 1)) {
      assertEquals(TrainStaticModelUpdate.UpdateCase.PROGRESS, update.getUpdateCase());
    }
    final TrainStaticModelUpdate terminal = updates.values.getLast();
    assertEquals(TrainStaticModelUpdate.UpdateCase.MODEL, terminal.getUpdateCase());
    assertTrue(fixture.registry.supportsModel(terminal.getModel().getArtifactId()));

    final TrainingTestSupport.CapturingObserver<ListStaticModelsResponse> listed =
        new TrainingTestSupport.CapturingObserver<>();
    fixture.service.listStaticModels(ListStaticModelsRequest.getDefaultInstance(), listed);
    assertEquals(1, listed.values.getFirst().getModelsCount());
    assertEquals(terminal.getModel(), listed.values.getFirst().getModels(0));
    assertTrue(listed.values.getFirst().getWritesEnabled());
  }

  @Test
  void mapsFailuresToGrpcStatuses() throws Exception {
    final Fixture fixture = fixture();

    assertEquals(Status.Code.NOT_FOUND,
        trainStatus(fixture, request("vocabulary-00000000-0000-0000-0000-000000000000", "mini")));
    assertEquals(Status.Code.INVALID_ARGUMENT,
        trainStatus(fixture, request(fixture.vocabularyId, "unknown")));

    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final OpenNlpModelTrainingServiceImpl disabled = new OpenNlpModelTrainingServiceImpl(
        StaticModelArtifactStore.fromConfiguration(
            Map.of("training.teacher.mini.ref", "minishlab/potion-base-8M"),
            VocabularyArtifactStore.fromConfiguration(Map.of(), formats),
            new TrainingTestSupport.RecordingTrainer(),
            new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider())));
    final TrainingTestSupport.CapturingObserver<TrainStaticModelUpdate> updates =
        new TrainingTestSupport.CapturingObserver<>();
    disabled.trainStaticModel(request(fixture.vocabularyId, "mini"), updates);
    assertEquals(Status.Code.FAILED_PRECONDITION,
        ((StatusRuntimeException) updates.error).getStatus().getCode());
  }

  @Test
  void deletesModelsThroughTheRpc() throws Exception {
    final Fixture fixture = fixture();
    final TrainingTestSupport.CapturingObserver<TrainStaticModelUpdate> updates =
        new TrainingTestSupport.CapturingObserver<>();
    fixture.service.trainStaticModel(request(fixture.vocabularyId, "mini"), updates);
    final String artifactId = updates.values.getLast().getModel().getArtifactId();

    final TrainingTestSupport.CapturingObserver<DeleteStaticModelResponse> deleted =
        new TrainingTestSupport.CapturingObserver<>();
    fixture.service.deleteStaticModel(
        DeleteStaticModelRequest.newBuilder().setArtifactId(artifactId).build(), deleted);
    assertTrue(deleted.values.getFirst().getDeleted());
    assertFalse(fixture.registry.supportsModel(artifactId));

    final TrainingTestSupport.CapturingObserver<DeleteStaticModelResponse> again =
        new TrainingTestSupport.CapturingObserver<>();
    fixture.service.deleteStaticModel(
        DeleteStaticModelRequest.newBuilder().setArtifactId(artifactId).build(), again);
    assertFalse(again.values.getFirst().getDeleted());
  }

  @Test
  void cancellationStopsTrainingBeforePublication() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore vocabularies = VocabularyArtifactStore.fromConfiguration(
        Map.of("vocabulary.artifact_root", temporaryDirectory.toString()), formats);
    final String vocabularyId = TrainingTestSupport.vocabularyArtifact(formats, vocabularies);
    final TrainedModelEmbeddingProvider registry =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    final AtomicInteger iterations = new AtomicInteger();
    final StaticModelTrainer trainer = (teacher, output, dimensions, terms, progress) -> {
      for (int iteration = 0; iteration < 10; iteration++) {
        iterations.incrementAndGet();
        progress.progress("iteration " + iteration);
      }
      throw new AssertionError("cancelled training continued through every iteration");
    };
    final StaticModelArtifactStore store = StaticModelArtifactStore.fromConfiguration(
        Map.of(
            "vocabulary.artifact_root", temporaryDirectory.toString(),
            "training.teacher.mini.ref", "minishlab/potion-base-8M"),
        vocabularies, trainer, registry);
    final OpenNlpModelTrainingServiceImpl service =
        new OpenNlpModelTrainingServiceImpl(store);
    final CancellingObserver updates = new CancellingObserver();

    service.trainStaticModel(request(vocabularyId, "mini"), updates);

    assertEquals(1, iterations.get());
    assertEquals(1, updates.values.size());
    assertFalse(updates.completed);
    assertNull(updates.error);
    assertTrue(store.models().isEmpty());
  }

  @Test
  void streamingSessionsShareTheTrainingAdmissionBoundAndReleaseOnCancellation()
      throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore vocabularies = VocabularyArtifactStore.fromConfiguration(
        Map.of(), formats);
    final StaticModelArtifactStore store = StaticModelArtifactStore.fromConfiguration(
        Map.of(
            "training.max_concurrent_trainings", "1",
            "training.teacher.mini.ref", "minishlab/potion-base-8M"),
        vocabularies,
        new TrainingTestSupport.RecordingTrainer(),
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider()));
    final OpenNlpModelTrainingServiceImpl service =
        new OpenNlpModelTrainingServiceImpl(store, new AdmissionPipeline());
    final TrainingTestSupport.CapturingObserver<StreamingTrainingUpdate> firstResponse =
        new TrainingTestSupport.CapturingObserver<>();
    final StreamObserver<StreamingTrainingRequest> first =
        service.streamingTraining(firstResponse);

    final TrainingTestSupport.CapturingObserver<StreamingTrainingUpdate> rejected =
        new TrainingTestSupport.CapturingObserver<>();
    service.streamingTraining(rejected);
    assertEquals(Status.Code.RESOURCE_EXHAUSTED,
        ((StatusRuntimeException) rejected.error).getStatus().getCode());

    first.onError(new java.io.IOException("client cancelled"));
    final TrainingTestSupport.CapturingObserver<StreamingTrainingUpdate> admitted =
        new TrainingTestSupport.CapturingObserver<>();
    final StreamObserver<StreamingTrainingRequest> third = service.streamingTraining(admitted);
    assertNull(admitted.error);
    third.onError(new java.io.IOException("client cancelled"));
  }

  private Status.Code trainStatus(Fixture fixture, TrainStaticModelRequest request) {
    final TrainingTestSupport.CapturingObserver<TrainStaticModelUpdate> updates =
        new TrainingTestSupport.CapturingObserver<>();
    fixture.service.trainStaticModel(request, updates);
    return ((StatusRuntimeException) updates.error).getStatus().getCode();
  }

  private Fixture fixture() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore vocabularies = VocabularyArtifactStore.fromConfiguration(
        Map.of("vocabulary.artifact_root", temporaryDirectory.toString()), formats);
    final String vocabularyId = TrainingTestSupport.vocabularyArtifact(formats, vocabularies);
    final TrainedModelEmbeddingProvider registry =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    final StaticModelArtifactStore store = StaticModelArtifactStore.fromConfiguration(
        Map.of(
            "vocabulary.artifact_root", temporaryDirectory.toString(),
            "training.teacher.mini.ref", "minishlab/potion-base-8M"),
        vocabularies,
        new TrainingTestSupport.RecordingTrainer(),
        registry);
    return new Fixture(new OpenNlpModelTrainingServiceImpl(store), registry, vocabularyId);
  }

  private StaticModelArtifactStore modelStore(
      Map<String, String> configuration, TrainedModelEmbeddingProvider registry)
      throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    return StaticModelArtifactStore.fromConfiguration(configuration,
        VocabularyArtifactStore.fromConfiguration(configuration, formats),
        new TrainingTestSupport.RecordingTrainer(), registry);
  }

  private record Fixture(
      OpenNlpModelTrainingServiceImpl service,
      TrainedModelEmbeddingProvider registry,
      String vocabularyId) {
  }

  private static TrainStaticModelRequest request(String vocabularyId, String teacherId) {
    return TrainStaticModelRequest.newBuilder()
        .setVocabularyArtifactId(vocabularyId)
        .setTeacherId(teacherId)
        .setDisplayName("Legal static model")
        .setProvenanceSummary("Authored test distillation")
        .build();
  }

  private static final class CancellingObserver
      extends ServerCallStreamObserver<TrainStaticModelUpdate> {

    private final List<TrainStaticModelUpdate> values = new ArrayList<>();
    private boolean cancelled;
    private boolean completed;
    private Throwable error;
    private Runnable cancelHandler;

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public void setOnCancelHandler(Runnable handler) {
      cancelHandler = handler;
    }

    @Override
    public void setCompression(String compression) {
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setOnReadyHandler(Runnable handler) {
    }

    @Override
    public void disableAutoInboundFlowControl() {
    }

    @Override
    public void request(int count) {
    }

    @Override
    public void setMessageCompression(boolean enable) {
    }

    @Override
    public void onNext(TrainStaticModelUpdate value) {
      values.add(value);
      cancelled = true;
      if (cancelHandler != null) {
        cancelHandler.run();
      }
    }

    @Override
    public void onError(Throwable throwable) {
      error = throwable;
    }

    @Override
    public void onCompleted() {
      completed = true;
    }
  }

  /** Pipeline used only to keep admission sessions open without publishing artifacts. */
  private static final class AdmissionPipeline implements StreamingTrainingPipeline {

    @Override
    public Limits limits() {
      return new Limits(1, 1, false, false);
    }

    @Override
    public org.apache.opennlp.grpc.processor.DocumentAnalysisSession openAnalysis(
        org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration configuration) {
      throw new UnsupportedOperationException();
    }

    @Override
    public org.apache.opennlp.grpc.v1.VocabularyArtifactDescriptor learnVocabulary(
        org.apache.opennlp.grpc.v1.LearnVocabularyStart start,
        List<org.apache.opennlp.grpc.v1.OpenNlpDocument> documents) {
      throw new UnsupportedOperationException();
    }

    @Override
    public StaticModelDescriptor trainModel(
        StreamingTrainingModelPlan plan,
        String vocabularyArtifactId,
        Consumer<String> progress,
        BooleanSupplier cancelled) {
      throw new UnsupportedOperationException();
    }

    @Override
    public IndexPublication createIndex(
        StreamingTrainingStart start,
        StaticModelDescriptor model,
        List<org.apache.opennlp.grpc.v1.OpenNlpDocument> documents,
        BooleanSupplier cancelled) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteModel(String artifactId) {
    }

    @Override
    public void deleteVocabulary(String artifactId) {
    }
  }

  /** Builds a one-entry catalog store with node-local installation disabled. */
  private static CatalogModelStore disabledInstallCatalog(StaticModelArtifactStore models,
      TrainedModelEmbeddingProvider registry) throws IOException {
    return new CatalogModelStore(null, List.of(testCatalogModel()), models, registry,
        (file, target) -> {
          throw new IOException("installs are disabled in this fixture");
        });
  }

  /** Builds one format-valid catalog entry so no test depends on the installer add-on. */
  private static CatalogModel testCatalogModel() {
    final CatalogFile file = new CatalogFile(Path.of("model.bin"),
        URI.create("https://example.invalid/model.bin"), 4,
        "9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a");
    return new CatalogModel(ModelCatalogDescriptor.newBuilder()
        .setCatalogId("test-model-catalog")
        .setDisplayName("Test model")
        .setRole(ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER)
        .setModelId("test-model")
        .setSourceUri("https://example.invalid/model")
        .setRevision("0123456789abcdef0123456789abcdef01234567")
        .setLicenseName("Test-License")
        .setLicenseUri("https://example.invalid/license")
        .setByteSize(4)
        .addLanguages("en")
        .setDescription("Test model")
        .build(), List.of(file));
  }
}
