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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.opennlp.grpc.v1.StaticModelDescriptor;
import org.apache.opennlp.grpc.v1.TrainStaticModelRequest;
import opennlp.embeddings.ModelDistiller;
import org.apache.opennlp.grpc.vocabulary.DictionaryFormatRegistry;
import org.apache.opennlp.grpc.vocabulary.UnknownVocabularyArtifactException;
import org.apache.opennlp.grpc.vocabulary.VocabularyArtifactStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StaticModelArtifactStoreTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void trainsPublishesServesAndReloadsImmutableModels() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore vocabularies = enabledVocabularies(formats);
    final String vocabularyId = TrainingTestSupport.vocabularyArtifact(formats, vocabularies);
    final TrainingTestSupport.RecordingTrainer trainer =
        new TrainingTestSupport.RecordingTrainer();
    final TrainedModelEmbeddingProvider registry =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    final StaticModelArtifactStore store = trainingStore(Map.of(), vocabularies,
        trainer, registry);
    final List<String> progress = new ArrayList<>();

    final StaticModelDescriptor descriptor = store.trainStaticModel(
        request(vocabularyId, "mini", 0), progress::add);

    assertTrue(descriptor.getArtifactId().startsWith("static-model-"));
    assertEquals("Legal static model", descriptor.getDisplayName());
    assertEquals(vocabularyId, descriptor.getVocabularyArtifactId());
    assertEquals("mini", descriptor.getTeacherId());
    assertEquals("WordPiece", descriptor.getFamily());
    assertEquals(TrainingTestSupport.DIMENSION, descriptor.getDimension());
    assertEquals(64, descriptor.getArtifactHash().length());
    assertTrue(descriptor.getByteSize() > 0);
    assertEquals("minishlab/potion-base-8M", trainer.teacherReference);
    // An operator-configured teacher carries only its reference.
    assertEquals("minishlab/potion-base-8M", descriptor.getTeacherReference());
    assertEquals("", descriptor.getLicenseName());
    assertEquals(0, descriptor.getLanguagesCount());
    assertEquals(256, trainer.pcaDims);
    assertEquals("liberty", trainer.terms.getFirst());
    assertTrue(trainer.terms.contains("habeas corpus"));
    assertEquals(List.of("resolving teacher", "distilling term rows"), progress);
    assertTrue(registry.supportsModel(descriptor.getArtifactId()));
    assertArrayEquals(new float[] {3.5f, 35f, 350f},
        registry.embed(descriptor.getArtifactId(), "HELLO WORLD"), 1e-5f);

    final TrainedModelEmbeddingProvider reloadedRegistry =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    final StaticModelArtifactStore reloaded = trainingStore(Map.of(), vocabularies,
        new TrainingTestSupport.RecordingTrainer(), reloadedRegistry);
    assertEquals(List.of(descriptor), reloaded.models());
    assertArrayEquals(new float[] {3.5f, 35f, 350f},
        reloadedRegistry.embed(descriptor.getArtifactId(), "hello world"), 1e-5f);
    try (var children = Files.list(temporaryDirectory)) {
      assertFalse(children.anyMatch(path ->
          path.getFileName().toString().startsWith(".staging-")));
    }
  }

  @Test
  void rejectsTrainingWithoutAnArtifactRoot() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore vocabularies =
        VocabularyArtifactStore.fromConfiguration(Map.of(), formats);
    final StaticModelArtifactStore store = StaticModelArtifactStore.fromConfiguration(
        Map.of("training.teacher.mini.ref", "minishlab/potion-base-8M"),
        vocabularies,
        new TrainingTestSupport.RecordingTrainer(),
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider()));

    assertFalse(store.writesEnabled());
    assertEquals(1, store.teachers().size());
    assertThrows(IllegalStateException.class,
        () -> store.trainStaticModel(request("vocabulary-x", "mini", 0), message -> { }));
  }

  @Test
  void enforcesTeacherVocabularyAndPcaBoundsBeforeTraining() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore vocabularies = enabledVocabularies(formats);
    final String vocabularyId = TrainingTestSupport.vocabularyArtifact(formats, vocabularies);
    final StaticModelArtifactStore store = trainingStore(
        Map.of("training.max_pca_dims", "8"), vocabularies,
        new TrainingTestSupport.RecordingTrainer(),
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider()));

    assertThrows(IllegalArgumentException.class,
        () -> store.trainStaticModel(request(vocabularyId, "unknown", 0), message -> { }));
    assertThrows(UnknownVocabularyArtifactException.class,
        () -> store.trainStaticModel(request("vocabulary-00000000-0000-0000-0000-000000000000", "mini", 0),
            message -> { }));
    assertThrows(IllegalArgumentException.class,
        () -> store.trainStaticModel(request(vocabularyId, "mini", 9), message -> { }));
    assertThrows(IllegalArgumentException.class,
        () -> store.trainStaticModel(TrainStaticModelRequest.newBuilder()
            .setVocabularyArtifactId(vocabularyId)
            .setTeacherId("mini")
            .setDisplayName(" ")
            .setProvenanceSummary("test")
            .build(), message -> { }));
  }

  @Test
  void rejectsTamperedModelsBeforeReloading() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore vocabularies = enabledVocabularies(formats);
    final String vocabularyId = TrainingTestSupport.vocabularyArtifact(formats, vocabularies);
    final StaticModelArtifactStore store = trainingStore(Map.of(), vocabularies,
        new TrainingTestSupport.RecordingTrainer(),
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider()));
    final StaticModelDescriptor descriptor =
        store.trainStaticModel(request(vocabularyId, "mini", 0), message -> { });
    Files.write(temporaryDirectory.resolve("models")
            .resolve(descriptor.getArtifactId()).resolve("model.safetensors"),
        "tampered".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);

    assertThrows(IOException.class, () -> trainingStore(Map.of(), vocabularies,
        new TrainingTestSupport.RecordingTrainer(),
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider())));
  }

  @Test
  void deletesTheModelAndStopsServingIt() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore vocabularies = enabledVocabularies(formats);
    final String vocabularyId = TrainingTestSupport.vocabularyArtifact(formats, vocabularies);
    final TrainedModelEmbeddingProvider registry =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    final StaticModelArtifactStore store = trainingStore(Map.of(), vocabularies,
        new TrainingTestSupport.RecordingTrainer(), registry);
    final StaticModelDescriptor descriptor =
        store.trainStaticModel(request(vocabularyId, "mini", 0), message -> { });

    assertTrue(store.deleteModel(descriptor.getArtifactId()));
    assertFalse(registry.supportsModel(descriptor.getArtifactId()));
    assertTrue(store.models().isEmpty());
    assertFalse(store.deleteModel(descriptor.getArtifactId()));
    assertFalse(Files.exists(
        temporaryDirectory.resolve("models").resolve(descriptor.getArtifactId())));
  }

  @Test
  void failedArtifactDeletionKeepsTheModelRegisteredAndServing() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore vocabularies = enabledVocabularies(formats);
    final String vocabularyId = TrainingTestSupport.vocabularyArtifact(formats, vocabularies);
    final TrainedModelEmbeddingProvider registry =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    final StaticModelArtifactStore store = trainingStore(Map.of(), vocabularies,
        new TrainingTestSupport.RecordingTrainer(), registry);
    final StaticModelDescriptor descriptor =
        store.trainStaticModel(request(vocabularyId, "mini", 0), message -> { });
    final Path artifact = temporaryDirectory.resolve("models")
        .resolve(descriptor.getArtifactId());
    assumeTrue(Files.getFileStore(artifact).supportsFileAttributeView("posix"));
    final Set<PosixFilePermission> original = Files.getPosixFilePermissions(artifact);
    Files.setPosixFilePermissions(artifact, Set.of(
        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
    try {
      assertThrows(IOException.class, () -> store.deleteModel(descriptor.getArtifactId()));
      assertEquals(List.of(descriptor), store.models());
      assertTrue(registry.supportsModel(descriptor.getArtifactId()));
    } finally {
      Files.setPosixFilePermissions(artifact, original);
    }
  }

  @Test
  void publicationNotificationFailureDoesNotTurnACommittedModelIntoAnRpcFailure()
      throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore vocabularies = enabledVocabularies(formats);
    final String vocabularyId = TrainingTestSupport.vocabularyArtifact(formats, vocabularies);
    final TrainedModelEmbeddingProvider registry =
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider());
    final StaticModelArtifactStore store = trainingStore(Map.of(), vocabularies,
        new TrainingTestSupport.RecordingTrainer(), registry);
    store.setPublicationListener((artifactId, parentVocabularyId) -> {
      throw new IllegalStateException("collection notification failed");
    });

    final StaticModelDescriptor descriptor = store.trainStaticModel(
        request(vocabularyId, "mini", 0), message -> { });

    assertEquals(List.of(descriptor), store.models());
    assertTrue(registry.supportsModel(descriptor.getArtifactId()));
  }

  @Test
  void servingFailureRollsBackTheNewDurableModelArtifact() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore vocabularies = enabledVocabularies(formats);
    final String vocabularyId = TrainingTestSupport.vocabularyArtifact(formats, vocabularies);
    final StaticModelTrainer invalidTrainer = (teacher, output, dimensions, terms, progress) -> {
      Files.writeString(output.resolve("invalid-model.txt"), "not a static model");
      return new ModelDistiller.Result("WordPiece", 1, 0, 3, 3, 1.0d);
    };
    final StaticModelArtifactStore store = trainingStore(Map.of(), vocabularies,
        invalidTrainer,
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider()));

    assertThrows(IOException.class, () -> store.trainStaticModel(
        request(vocabularyId, "mini", 0), message -> { }));

    final Path models = temporaryDirectory.resolve("models");
    if (Files.exists(models)) {
      try (var entries = Files.list(models)) {
        assertTrue(entries.findAny().isEmpty());
      }
    }
    assertTrue(store.models().isEmpty());
  }

  @Test
  void cancellationAfterDistillationPreventsArtifactPublication() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore vocabularies = enabledVocabularies(formats);
    final String vocabularyId = TrainingTestSupport.vocabularyArtifact(formats, vocabularies);
    final AtomicBoolean cancelled = new AtomicBoolean();
    final StaticModelTrainer trainer = (teacher, output, dimensions, terms, progress) -> {
      TrainingTestSupport.writeStaticModelDirectory(output);
      cancelled.set(true);
      return new ModelDistiller.Result("WordPiece", 6, 0, 3, 3, 1.0d);
    };
    final StaticModelArtifactStore store = trainingStore(Map.of(), vocabularies, trainer,
        new TrainedModelEmbeddingProvider(TrainingTestSupport.baseProvider()));

    assertThrows(CancellationException.class, () -> store.trainStaticModel(
        request(vocabularyId, "mini", 0), message -> { }, cancelled::get));

    assertTrue(store.models().isEmpty());
    final Path models = temporaryDirectory.resolve("models");
    if (Files.exists(models)) {
      try (var entries = Files.list(models)) {
        assertTrue(entries.findAny().isEmpty());
      }
    }
  }

  private VocabularyArtifactStore enabledVocabularies(DictionaryFormatRegistry formats)
      throws Exception {
    return VocabularyArtifactStore.fromConfiguration(
        Map.of("vocabulary.artifact_root", temporaryDirectory.toString()), formats);
  }

  private StaticModelArtifactStore trainingStore(
      Map<String, String> overrides,
      VocabularyArtifactStore vocabularies,
      StaticModelTrainer trainer,
      TrainedModelEmbeddingProvider registry) throws Exception {
    final Map<String, String> configuration = new HashMap<>(overrides);
    configuration.put("vocabulary.artifact_root", temporaryDirectory.toString());
    configuration.put("training.teacher.mini.ref", "minishlab/potion-base-8M");
    configuration.put("training.teacher.mini.display_name", "Potion base 8M");
    return StaticModelArtifactStore.fromConfiguration(
        configuration, vocabularies, trainer, registry);
  }

  private static TrainStaticModelRequest request(
      String vocabularyId, String teacherId, int pcaDims) {
    return TrainStaticModelRequest.newBuilder()
        .setVocabularyArtifactId(vocabularyId)
        .setTeacherId(teacherId)
        .setDisplayName("Legal static model")
        .setPcaDims(pcaDims)
        .setProvenanceSummary("Authored test distillation")
        .build();
  }
}
