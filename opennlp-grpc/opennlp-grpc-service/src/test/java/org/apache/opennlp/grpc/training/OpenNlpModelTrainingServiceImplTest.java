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

import java.nio.file.Path;
import java.util.Map;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.opennlp.grpc.v1.DeleteStaticModelRequest;
import org.apache.opennlp.grpc.v1.DeleteStaticModelResponse;
import org.apache.opennlp.grpc.v1.ListStaticModelsRequest;
import org.apache.opennlp.grpc.v1.ListStaticModelsResponse;
import org.apache.opennlp.grpc.v1.ListTeachersRequest;
import org.apache.opennlp.grpc.v1.ListTeachersResponse;
import org.apache.opennlp.grpc.v1.TrainStaticModelRequest;
import org.apache.opennlp.grpc.v1.TrainStaticModelUpdate;
import org.apache.opennlp.grpc.vocabulary.DictionaryFormatRegistry;
import org.apache.opennlp.grpc.vocabulary.VocabularyArtifactStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenNlpModelTrainingServiceImplTest {

  @TempDir
  Path temporaryDirectory;

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
        trainStatus(fixture, request("vocabulary-" + "0".repeat(36), "mini")));
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
}
