/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import org.apache.opennlp.grpc.spi.catalog.CatalogFile;
import org.apache.opennlp.grpc.spi.catalog.CatalogModel;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import opennlp.tools.util.ResourceInstaller;
import org.apache.opennlp.grpc.v1.InstallModelRequest;
import org.apache.opennlp.grpc.v1.InstallModelStage;
import org.apache.opennlp.grpc.v1.InstallModelProgress;
import org.apache.opennlp.grpc.v1.InstalledModelDescriptor;
import org.apache.opennlp.grpc.v1.ModelArtifactRole;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.ModelArtifactFormat;
import org.apache.opennlp.grpc.v1.ModelCatalogDescriptor;
import org.apache.opennlp.grpc.vocabulary.DictionaryFormatRegistry;
import org.apache.opennlp.grpc.vocabulary.VocabularyArtifactStore;
import org.apache.opennlp.grpc.vocabulary.store.ArtifactDigests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogModelStoreTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void installsVerifiesPublishesAndReloadsAStaticEmbeddingModel() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("source"));
    TrainingTestSupport.writeStaticModelDirectory(source);
    final CatalogModel model = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING, "catalog-static");
    final TrainedModelEmbeddingProvider registry = registry();
    final StaticModelArtifactStore training = trainingStore(registry);
    final List<InstallModelProgress> progress = new ArrayList<>();
    final CatalogModelStore store = new CatalogModelStore(
        temporaryDirectory.resolve("catalog"), List.of(model), training, registry,
        copyingInstaller(source));

    final InstalledModelDescriptor installed = store.install(request(model), progress::add,
        () -> false);

    assertEquals("catalog-static", installed.getCatalog().getModelId());
    assertTrue(installed.getLoaded());
    assertTrue(registry.supportsModel("catalog-static"));
    assertTrue(registry.supportsModel("base"));
    assertEquals(List.of("base", "catalog-static"),
        registry.registeredModelIds().stream().toList());
    assertEquals(TrainingTestSupport.DIMENSION,
        registry.embeddingDimension("catalog-static"));
    assertEquals(InstallModelStage.INSTALL_MODEL_STAGE_PUBLISHED,
        progress.getLast().getStage());
    assertEquals(model.files().size(), progress.getLast().getCompletedFiles());
    assertEquals(List.of(installed), store.installedModels());

    final TrainedModelEmbeddingProvider restartedRegistry = registry();
    final CatalogModelStore restarted = new CatalogModelStore(
        temporaryDirectory.resolve("catalog"), List.of(model), trainingStore(restartedRegistry),
        restartedRegistry, (file, target) -> {
          throw new AssertionError("restart must not download an installed model");
        });
    assertTrue(restartedRegistry.supportsModel("catalog-static"));
    assertEquals(installed.getArtifactHash(),
        restarted.installedModels().getFirst().getArtifactHash());
  }

  @Test
  void installingATeacherMakesItImmediatelySelectableForTraining() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("teacher-source"));
    Files.writeString(source.resolve("tokenizer.json"), "{}");
    final Path onnx = Files.createDirectories(source.resolve("onnx")).resolve("model.onnx");
    Files.write(onnx, new byte[] {1, 2, 3, 4});
    final CatalogModel model = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER, "catalog-teacher");
    final TrainedModelEmbeddingProvider registry = registry();
    final StaticModelArtifactStore training = trainingStore(registry);
    final CatalogModelStore store = new CatalogModelStore(
        temporaryDirectory.resolve("teacher-catalog"), List.of(model), training, registry,
        copyingInstaller(source));

    store.install(request(model), ignored -> { }, () -> false);

    assertTrue(training.teachers().stream().anyMatch(teacher ->
        "catalog-teacher".equals(teacher.getTeacherId()) && teacher.getLocal()));
    assertFalse(registry.supportsModel("catalog-teacher"));
  }

  @Test
  void aModelDistilledFromACatalogTeacherRecordsTheTeachersLicenseAndOrigin() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("lineage-source"));
    Files.writeString(source.resolve("tokenizer.json"), "{}");
    Files.write(Files.createDirectories(source.resolve("onnx")).resolve("model.onnx"),
        new byte[] {1, 2, 3, 4});
    final CatalogModel teacher = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_DISTILLATION_TEACHER, "lineage-teacher");
    final TrainedModelEmbeddingProvider registry = registry();
    final StaticModelArtifactStore training = trainingStore(registry);
    final CatalogModelStore store = new CatalogModelStore(
        temporaryDirectory.resolve("lineage-catalog"), List.of(teacher), training, registry,
        copyingInstaller(source));
    store.install(request(teacher), ignored -> { }, () -> false);

    final TeacherProvenance provenance = training.teacherProvenance("lineage-teacher");

    assertEquals(teacher.descriptor().getSourceUri(), provenance.reference());
    assertEquals(teacher.descriptor().getRevision(), provenance.revision());
    assertEquals(teacher.descriptor().getLicenseName(), provenance.licenseName());
    assertEquals(teacher.descriptor().getLicenseUri(), provenance.licenseUri());
    assertEquals(teacher.descriptor().getLanguagesList(), provenance.languages());
  }

  @Test
  void parserAndChunkerInstallationsWaitForAValidatedRestart() throws Exception {
    for (ModelArtifactRole role : List.of(
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_CHUNKER)) {
      final Path source = Files.createDirectories(
          temporaryDirectory.resolve(role.name() + "-source"));
      Files.write(source.resolve("model.bin"), new byte[] {1, 2, 3, 4});
      final String modelId = role == ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER
          ? "catalog-parser" : "catalog-chunker";
      final CatalogModel model = testModel(source, role, modelId);
      final TrainedModelEmbeddingProvider registry = registry();
      final List<InstallModelProgress> progress = new ArrayList<>();
      final CatalogModelStore store = new CatalogModelStore(
          temporaryDirectory.resolve(role.name() + "-catalog"), List.of(model),
          trainingStore(registry), registry, copyingInstaller(source));

      final InstalledModelDescriptor installed =
          store.install(request(model), progress::add, () -> false);

      assertFalse(installed.getLoaded());
      assertTrue(progress.getLast().getMessage().contains("restart required"));
      assertFalse(registry.supportsModel(model.descriptor().getModelId()));
    }
  }

  @Test
  void subwordWordnetAndDoccatInstallationsWaitForARestart() throws Exception {
    for (ModelArtifactRole role : List.of(
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_SUBWORD_MODEL,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_WORDNET_LEXICON,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_DOC_CATEGORIZER)) {
      final Path source = Files.createDirectories(
          temporaryDirectory.resolve(role.name() + "-source"));
      Files.write(source.resolve("model.bin"), new byte[] {1, 2, 3, 4});
      final CatalogModel model = testModel(source, role,
          "catalog-" + role.name().substring("MODEL_ARTIFACT_ROLE_".length())
              .toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
      final TrainedModelEmbeddingProvider registry = registry();
      final CatalogModelStore store = new CatalogModelStore(
          temporaryDirectory.resolve(role.name() + "-catalog"), List.of(model),
          trainingStore(registry), registry, copyingInstaller(source));
      final InstalledModelDescriptor installed =
          store.install(request(model), ignored -> { }, () -> false);
      assertFalse(installed.getLoaded());
      assertTrue(store.installedModels().getFirst().getCatalog().getRequiresRestart());
    }
  }

  @Test
  void catalogListingsCarryFormatUnlocksRestartAndFiles() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("ner-source"));
    Files.write(source.resolve("en-ner-person.bin"), new byte[] {1, 2, 3, 4});
    final CatalogModel model = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_NAME_FINDER, "person");
    final TrainedModelEmbeddingProvider registry = registry();
    final CatalogModelStore store = new CatalogModelStore(
        temporaryDirectory.resolve("ner-catalog"), List.of(model), trainingStore(registry),
        registry, copyingInstaller(source));

    final ModelCatalogDescriptor listed = store.catalogModels().getFirst();

    assertEquals(ModelArtifactFormat.MODEL_ARTIFACT_FORMAT_OPENNLP_BIN, listed.getFormat());
    assertEquals(List.of(PipelineStep.PIPELINE_STEP_NER, PipelineStep.PIPELINE_STEP_GEOCODE),
        listed.getUnlocksList());
    assertTrue(listed.getRequiresRestart());
    assertEquals(1, listed.getFilesCount());
    assertEquals("en-ner-person.bin", listed.getFiles(0).getRelativePath());
    assertEquals(4, listed.getFiles(0).getByteSize());
    assertEquals(model.files().getFirst().sha256(), listed.getFiles(0).getSha256Hex());
    // The installed record carries the same decorated descriptor.
    store.install(request(model), ignored -> { }, () -> false);
    assertEquals(listed, store.installedModels().getFirst().getCatalog());
  }

  @Test
  void refusesToInstallWhenTheCatalogRootLacksFreeSpace() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("space-source"));
    Files.write(source.resolve("model.bin"), new byte[] {1, 2, 3, 4});
    final CatalogModel model = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER, "space-parser");
    final TrainedModelEmbeddingProvider registry = registry();
    final CatalogModelStore store = new CatalogModelStore(
        temporaryDirectory.resolve("space-catalog"), List.of(model), trainingStore(registry),
        registry, copyingInstaller(source), CatalogModelStore::deleteTree, () -> 3L);

    final InsufficientDiskSpaceException failure = assertThrows(
        InsufficientDiskSpaceException.class,
        () -> store.install(request(model), ignored -> { }, () -> false));

    assertTrue(failure.getMessage().contains("free space"), failure.getMessage());
    assertTrue(store.installedModels().isEmpty());
    try (var entries = Files.list(temporaryDirectory.resolve("space-catalog"))) {
      assertEquals(0, entries.count(), "nothing may be staged when space is short");
    }
  }

  @Test
  void aCorruptDownloadIsReportedAsAChecksumFailure() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("corrupt-source"));
    Files.write(source.resolve("model.bin"), new byte[] {1, 2, 3, 4});
    final CatalogModel model = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER, "corrupt-parser");
    final TrainedModelEmbeddingProvider registry = registry();
    final CatalogModelStore store = new CatalogModelStore(
        temporaryDirectory.resolve("corrupt-catalog"), List.of(model), trainingStore(registry),
        registry, (file, target) -> {
          Files.createDirectories(target.getParent());
          Files.write(target, new byte[] {9, 9, 9, 9});
        });

    final CatalogChecksumException failure = assertThrows(CatalogChecksumException.class,
        () -> store.install(request(model), ignored -> { }, () -> false));

    assertTrue(failure.getMessage().contains("model.bin"), failure.getMessage());
    assertTrue(store.installedModels().isEmpty());
  }

  @Test
  void aFailedDownloadNamesItsSource() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("offline-source"));
    Files.write(source.resolve("model.bin"), new byte[] {1, 2, 3, 4});
    final CatalogModel model = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER, "offline-parser");
    final TrainedModelEmbeddingProvider registry = registry();
    final CatalogModelStore store = new CatalogModelStore(
        temporaryDirectory.resolve("offline-catalog"), List.of(model), trainingStore(registry),
        registry, (file, target) -> {
          throw new IOException("connection reset");
        });

    final CatalogDownloadException failure = assertThrows(CatalogDownloadException.class,
        () -> store.install(request(model), ignored -> { }, () -> false));

    assertTrue(failure.getMessage().contains("example.invalid"), failure.getMessage());
    assertTrue(failure.getMessage().contains("model.bin"), failure.getMessage());
    // The transport detail is for the log, not the client.
    assertFalse(failure.getMessage().contains("connection reset"), failure.getMessage());
  }

  @Test
  void refusesASecondModelForAnOccupiedPipelineSlot() throws Exception {
    final Path first = Files.createDirectories(temporaryDirectory.resolve("slot-first"));
    Files.write(first.resolve("a.bin"), new byte[] {1, 2, 3, 4});
    final Path second = Files.createDirectories(temporaryDirectory.resolve("slot-second"));
    Files.write(second.resolve("b.bin"), new byte[] {5, 6, 7, 8});
    // Two catalog entries with one model id claim the same model.parser.<id>.path slot.
    final CatalogModel one = testModel(first,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER, "shared-parser");
    final CatalogModel two = new CatalogModel(testModel(second,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER, "shared-parser").descriptor().toBuilder()
        .setCatalogId("shared-parser-alternative").build(),
        testModel(second, ModelArtifactRole.MODEL_ARTIFACT_ROLE_PARSER, "shared-parser").files());
    final TrainedModelEmbeddingProvider registry = registry();
    final CatalogModelStore store = new CatalogModelStore(
        temporaryDirectory.resolve("slot-catalog"), List.of(one, two), trainingStore(registry),
        registry, (file, target) -> {
          Files.createDirectories(target.getParent());
          Files.copy((file.relativePath().toString().equals("a.bin") ? first : second)
              .resolve(file.relativePath()), target);
        });
    store.install(request(one), ignored -> { }, () -> false);

    final IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> store.install(request(two), ignored -> { }, () -> false));

    assertTrue(failure.getMessage().contains("model.parser.shared-parser.path"),
        failure.getMessage());
    assertTrue(failure.getMessage().contains("shared-parser-catalog"), failure.getMessage());
    assertEquals(1, store.installedModels().size());
  }

  @Test
  void consentAndPinnedIdentityAreRequiredBeforeAnyDownload() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("identity-source"));
    TrainingTestSupport.writeStaticModelDirectory(source);
    final CatalogModel model = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING, "identity-static");
    final int[] downloads = {0};
    final TrainedModelEmbeddingProvider registry = registry();
    final CatalogModelStore store = new CatalogModelStore(
        temporaryDirectory.resolve("identity-catalog"), List.of(model), trainingStore(registry),
        registry, (file, target) -> downloads[0]++);

    assertThrows(IllegalArgumentException.class, () -> store.install(
        request(model).toBuilder().setLicenseAcknowledged(false).build(), ignored -> { },
        () -> false));
    assertThrows(IllegalArgumentException.class, () -> store.install(
        request(model).toBuilder().setRevision("moving-main").build(), ignored -> { },
        () -> false));
    assertThrows(IllegalArgumentException.class, () -> store.install(
        request(model).toBuilder().setLicenseName("Unknown").build(), ignored -> { },
        () -> false));
    assertEquals(0, downloads[0]);
  }

  @Test
  void allowsLargeCatalogFilesToKeepMakingProgressForThirtyMinutes() {
    final ResourceInstaller.Limits limits = CatalogModelStore.downloadLimits(512_361_560L);

    assertEquals(Duration.ofMinutes(30), limits.readTimeout());
    assertEquals(512_361_560L, limits.maxDownloadBytes());
    assertEquals(512_361_560L, limits.maxExpandedBytes());
  }

  @Test
  void acceptsTheInstallDirectoryReturnedByResourceInstaller() {
    final Path directory = temporaryDirectory.resolve("installer-target").toAbsolutePath();
    final Path target = directory.resolve("model.safetensors");

    assertDoesNotThrow(() -> CatalogModelStore.verifyInstallDirectory(target, directory));
    assertThrows(IOException.class,
        () -> CatalogModelStore.verifyInstallDirectory(target, target));
  }

  @Test
  void startupRejectsTamperedInstalledBytes() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("tamper-source"));
    TrainingTestSupport.writeStaticModelDirectory(source);
    final CatalogModel model = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING, "tamper-static");
    final Path root = temporaryDirectory.resolve("tamper-catalog");
    final TrainedModelEmbeddingProvider registry = registry();
    new CatalogModelStore(root, List.of(model), trainingStore(registry), registry,
        copyingInstaller(source)).install(request(model), ignored -> { }, () -> false);
    Files.writeString(root.resolve(model.descriptor().getCatalogId()).resolve("config.json"),
        "tampered");

    final TrainedModelEmbeddingProvider restartedRegistry = registry();
    assertThrows(IOException.class, () -> new CatalogModelStore(
        root, List.of(model), trainingStore(restartedRegistry), restartedRegistry,
        copyingInstaller(source)));
  }

  @Test
  void rejectsAStaticTableWhoseLoadedDimensionContradictsTheCatalog() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("dimension-source"));
    TrainingTestSupport.writeStaticModelDirectory(source);
    final CatalogModel valid = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING, "dimension-static");
    final CatalogModel wrong = new CatalogModel(valid.descriptor().toBuilder()
        .setDimension(TrainingTestSupport.DIMENSION + 1).build(), valid.files());
    final TrainedModelEmbeddingProvider registry = registry();
    final Path root = temporaryDirectory.resolve("dimension-catalog");
    final CatalogModelStore store = new CatalogModelStore(
        root, List.of(wrong), trainingStore(registry), registry, copyingInstaller(source));

    assertThrows(IOException.class, () ->
        store.install(request(wrong), ignored -> { }, () -> false));
    assertFalse(Files.exists(root.resolve(wrong.descriptor().getCatalogId())));
    assertFalse(registry.supportsModel("dimension-static"));
  }

  @Test
  void cleanupFailureDoesNotMaskTheInstallationFailure() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("failed-source"));
    TrainingTestSupport.writeStaticModelDirectory(source);
    final CatalogModel model = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING, "failed-static");
    final TrainedModelEmbeddingProvider registry = registry();
    final CatalogModelStore store = new CatalogModelStore(
        temporaryDirectory.resolve("failed-catalog"), List.of(model), trainingStore(registry),
        registry, (file, target) -> {
          throw new IOException("download failure");
        }, tree -> {
          throw new IOException("cleanup failure");
        });

    final IOException failure = assertThrows(IOException.class,
        () -> store.install(request(model), ignored -> { }, () -> false));

    assertTrue(failure.getMessage().contains("example.invalid"), failure.getMessage());
    assertEquals("download failure", failure.getCause().getMessage());
    assertEquals(1, failure.getSuppressed().length);
    assertEquals("cleanup failure", failure.getSuppressed()[0].getMessage());
  }

  @Test
  void rollbackFailureDoesNotMaskTheActivationFailure() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("collision-source"));
    TrainingTestSupport.writeStaticModelDirectory(source);
    final CatalogModel model = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING, "base");
    final TrainedModelEmbeddingProvider registry = registry();
    final CatalogModelStore store = new CatalogModelStore(
        temporaryDirectory.resolve("collision-catalog"), List.of(model),
        trainingStore(registry), registry, copyingInstaller(source), tree -> {
          if (tree.getFileName().toString().equals(model.descriptor().getCatalogId())) {
            throw new IOException("rollback failure");
          }
        });

    final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> store.install(request(model), ignored -> { }, () -> false));

    assertTrue(failure.getMessage().contains("collides"));
    assertEquals(1, failure.getSuppressed().length);
    assertEquals("rollback failure", failure.getSuppressed()[0].getMessage());
  }

  @Test
  void cancellationRemovesTheUnpublishedStagingDirectory() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("cancel-source"));
    TrainingTestSupport.writeStaticModelDirectory(source);
    final CatalogModel model = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING, "cancel-static");
    final TrainedModelEmbeddingProvider registry = registry();
    final AtomicBoolean cancelled = new AtomicBoolean();
    final Path root = temporaryDirectory.resolve("cancel-catalog");
    final CatalogModelStore store = new CatalogModelStore(
        root, List.of(model), trainingStore(registry), registry, (file, target) -> {
          Files.createDirectories(target.getParent());
          Files.copy(source.resolve(file.relativePath()), target);
          cancelled.set(true);
        });

    assertThrows(CancellationException.class,
        () -> store.install(request(model), ignored -> { }, cancelled::get));

    assertFalse(Files.exists(root.resolve(model.descriptor().getCatalogId())));
    try (var entries = Files.list(root)) {
      assertEquals(0, entries.count());
    }
    assertFalse(registry.supportsModel("cancel-static"));
  }

  @Test
  void onlyOneCatalogInstallationRunsAtATime() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("concurrent-source"));
    TrainingTestSupport.writeStaticModelDirectory(source);
    final CatalogModel model = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING, "concurrent-static");
    final TrainedModelEmbeddingProvider registry = registry();
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    final CatalogModelStore store = new CatalogModelStore(
        temporaryDirectory.resolve("concurrent-catalog"), List.of(model),
        trainingStore(registry), registry, (file, target) -> {
          entered.countDown();
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("test installation interrupted", e);
          }
          Files.createDirectories(target.getParent());
          Files.copy(source.resolve(file.relativePath()), target);
        });
    final FutureTask<InstalledModelDescriptor> first = new FutureTask<>(
        () -> store.install(request(model), ignored -> { }, () -> false));
    Thread.ofVirtual().start(first);
    entered.await();

    try {
      assertThrows(ConcurrentModelInstallException.class,
          () -> store.install(request(model), ignored -> { }, () -> false));
    } finally {
      release.countDown();
    }
    assertTrue(first.get().getLoaded());
  }

  @Test
  void startupRemovesAnInterruptedStagingDirectory() throws Exception {
    final Path source = Files.createDirectories(temporaryDirectory.resolve("stale-source"));
    TrainingTestSupport.writeStaticModelDirectory(source);
    final CatalogModel model = testModel(source,
        ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING, "stale-static");
    final Path root = Files.createDirectories(temporaryDirectory.resolve("stale-catalog"));
    final Path stale = Files.createDirectories(root.resolve(".install-stale"));
    Files.writeString(stale.resolve("partial"), "partial");
    final TrainedModelEmbeddingProvider registry = registry();

    new CatalogModelStore(root, List.of(model), trainingStore(registry), registry,
        copyingInstaller(source));

    assertFalse(Files.exists(stale));
  }

  private StaticModelArtifactStore trainingStore(TrainedModelEmbeddingProvider registry)
      throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    return StaticModelArtifactStore.fromConfiguration(
        Map.of("vocabulary.artifact_root", temporaryDirectory.resolve("artifacts").toString()),
        VocabularyArtifactStore.fromConfiguration(
            Map.of("vocabulary.artifact_root",
                temporaryDirectory.resolve("artifacts").toString()), formats),
        new TrainingTestSupport.RecordingTrainer(), registry);
  }

  @Test
  void servesAnEmptyCatalogWithoutTheInstallerAddOn() throws Exception {
    // No ModelCatalogProvider is on this module's classpath: the built-in catalog ships in
    // the opennlp-grpc-installer add-on, so discovery yields an empty, honest catalog.
    final TrainedModelEmbeddingProvider registry = registry();
    final CatalogModelStore store = CatalogModelStore.fromConfiguration(
        Map.of(CatalogModelStore.CATALOG_ROOT_KEY,
            temporaryDirectory.resolve("empty-catalog").toString()),
        trainingStore(registry), registry);

    assertTrue(store.catalogModels().isEmpty());
    assertTrue(store.installsEnabled());
    final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
        () -> store.install(InstallModelRequest.newBuilder().setCatalogId("gum-parser").build(),
            ignored -> { }, () -> false));
    assertTrue(failure.getMessage().contains("opennlp-grpc-installer"));
  }

  private static TrainedModelEmbeddingProvider registry() {
    return new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
  }

  private static InstallModelRequest request(CatalogModel model) {
    return InstallModelRequest.newBuilder()
        .setCatalogId(model.descriptor().getCatalogId())
        .setRevision(model.descriptor().getRevision())
        .setLicenseName(model.descriptor().getLicenseName())
        .setLicenseAcknowledged(true)
        .build();
  }

  private static CatalogFileInstaller copyingInstaller(Path source) {
    return (file, target) -> {
      Files.createDirectories(target.getParent());
      Files.copy(source.resolve(file.relativePath()), target);
    };
  }

  private static CatalogModel testModel(
      Path source, ModelArtifactRole role, String modelId) throws IOException {
    final List<CatalogFile> files = new ArrayList<>();
    try (var paths = Files.walk(source)) {
      for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
        final Path relative = source.relativize(path);
        try (InputStream input = Files.newInputStream(path)) {
          final ArtifactDigests.SizedDigest digest = ArtifactDigests.digest(input);
          files.add(new CatalogFile(relative, URI.create(
              "https://example.invalid/" + relative), digest.size(), digest.hexDigest()));
        }
      }
    }
    final long bytes = files.stream().mapToLong(CatalogFile::byteSize).sum();
    return new CatalogModel(ModelCatalogDescriptor.newBuilder()
        .setCatalogId(modelId + "-catalog")
        .setDisplayName(modelId)
        .setRole(role)
        .setModelId(modelId)
        .setSourceUri("https://example.invalid/model")
        .setRevision("0123456789abcdef0123456789abcdef01234567")
        .setLicenseName("Test-License")
        .setLicenseUri("https://example.invalid/license")
        .setByteSize(bytes)
        .setDimension(role == ModelArtifactRole.MODEL_ARTIFACT_ROLE_STATIC_EMBEDDING
            ? TrainingTestSupport.DIMENSION : 0)
        .addLanguages("en")
        .setDescription("Test model")
        .build(), files);
  }
}
