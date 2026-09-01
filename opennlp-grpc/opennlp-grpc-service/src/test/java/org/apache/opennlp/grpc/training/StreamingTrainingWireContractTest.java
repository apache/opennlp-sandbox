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

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import org.apache.opennlp.grpc.v1.OpenNlpTrainingProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingTrainingWireContractTest {

  @Test
  void exposesOneBidirectionalTrainingSession() {
    final var service = OpenNlpTrainingProto.getDescriptor()
        .findServiceByName("OpenNlpModelTrainingService");
    assertNotNull(service);
    assertEquals(8, service.getMethods().size());
    final MethodDescriptor method = service.findMethodByName("StreamingTraining");
    assertNotNull(method);
    assertTrue(method.isClientStreaming());
    assertTrue(method.isServerStreaming());
    assertEquals("StreamingTrainingRequest", method.getInputType().getName());
    assertEquals("StreamingTrainingUpdate", method.getOutputType().getName());
  }

  @Test
  void fixesAnalysisVocabularyModelAndIndexConfigurationInTheStartFrame() {
    final Descriptor request = message("StreamingTrainingRequest");
    assertEquals("frame", request.getOneofs().getFirst().getName());
    assertMessageField(request, "start", "StreamingTrainingStart", 1);
    assertMessageField(request, "document", "AnalyzeStreamDocument", 2);

    final Descriptor start = message("StreamingTrainingStart");
    assertMessageField(start, "analysis", "AnalyzeStreamConfiguration", 1);
    assertMessageField(start, "vocabulary", "LearnVocabularyStart", 2);
    assertMessageField(start, "model", "StreamingTrainingModelPlan", 3);
    assertMessageField(start, "index", "StreamingTrainingIndexPlan", 4);
    assertTrue(start.findFieldByName("model").toProto().getProto3Optional());
    assertTrue(start.findFieldByName("index").toProto().getProto3Optional());

    final Descriptor model = message("StreamingTrainingModelPlan");
    assertField(model, "teacher_id", FieldDescriptor.JavaType.STRING, 1);
    assertField(model, "display_name", FieldDescriptor.JavaType.STRING, 2);
    assertField(model, "pca_dims", FieldDescriptor.JavaType.INT, 3);
    assertField(model, "provenance_summary", FieldDescriptor.JavaType.STRING, 4);

    final Descriptor index = message("StreamingTrainingIndexPlan");
    assertField(index, "display_name", FieldDescriptor.JavaType.STRING, 1);
    assertMessageField(index, "provider", "SearchProviderSelector", 2);
    assertRepeatedMessageField(index, "chunk_embed_configs", "ChunkEmbedConfigEntry", 3);
    assertRepeatedMessageField(index, "category_chunk_configs", "CategoryChunkConfigEntry", 4);
    assertEnumField(index, "durability", "StreamingTrainingIndexDurability", 5);
    assertField(index, "alias", FieldDescriptor.JavaType.STRING, 6);
    assertTrue(index.findFieldByName("alias").toProto().getProto3Optional());
  }

  @Test
  void streamsAcceptedDocumentProgressAndTerminalTypedUpdates() {
    final Descriptor update = message("StreamingTrainingUpdate");
    assertEquals("update", update.getOneofs().getFirst().getName());
    assertMessageField(update, "accepted", "StreamingTrainingAccepted", 1);
    assertMessageField(update, "document", "StreamingTrainingDocumentUpdate", 2);
    assertMessageField(update, "progress", "StreamingTrainingProgress", 3);
    assertMessageField(update, "completed", "StreamingTrainingCompleted", 4);

    final Descriptor accepted = message("StreamingTrainingAccepted");
    assertField(accepted, "max_documents", FieldDescriptor.JavaType.INT, 1);
    assertField(accepted, "max_corpus_bytes", FieldDescriptor.JavaType.INT, 2);
    assertField(accepted, "model_training_enabled", FieldDescriptor.JavaType.BOOLEAN, 3);
    assertField(accepted, "indexing_enabled", FieldDescriptor.JavaType.BOOLEAN, 4);

    final Descriptor document = message("StreamingTrainingDocumentUpdate");
    assertMessageField(document, "result", "AnalyzeStreamResponse", 1);
    assertField(document, "accepted_documents", FieldDescriptor.JavaType.INT, 2);
    assertField(document, "accepted_corpus_bytes", FieldDescriptor.JavaType.INT, 3);

    final Descriptor progress = message("StreamingTrainingProgress");
    assertEnumField(progress, "stage", "StreamingTrainingStage", 1);
    assertField(progress, "message", FieldDescriptor.JavaType.STRING, 2);

    final Descriptor completed = message("StreamingTrainingCompleted");
    assertMessageField(completed, "vocabulary", "VocabularyArtifactDescriptor", 1);
    assertMessageField(completed, "model", "StaticModelDescriptor", 2);
    assertMessageField(completed, "index", "IndexDocumentsResponse", 3);
    assertField(completed, "accepted_documents", FieldDescriptor.JavaType.INT, 4);
    assertField(completed, "accepted_corpus_bytes", FieldDescriptor.JavaType.INT, 5);
    assertTrue(completed.findFieldByName("model").toProto().getProto3Optional());
    assertTrue(completed.findFieldByName("index").toProto().getProto3Optional());
  }

  @Test
  void pinsEnumNumbersForDurabilityAndProgressStages() {
    final var durability = OpenNlpTrainingProto.getDescriptor()
        .findEnumTypeByName("StreamingTrainingIndexDurability");
    assertNotNull(durability);
    assertEquals(0, durability.findValueByName(
        "STREAMING_TRAINING_INDEX_DURABILITY_UNSPECIFIED").getNumber());
    assertEquals(1, durability.findValueByName(
        "STREAMING_TRAINING_INDEX_DURABILITY_PROCESS_LOCAL").getNumber());
    assertEquals(2, durability.findValueByName(
        "STREAMING_TRAINING_INDEX_DURABILITY_PERSISTED").getNumber());
    assertEquals(3, durability.findValueByName(
        "STREAMING_TRAINING_INDEX_DURABILITY_SEALED").getNumber());

    final var stage = OpenNlpTrainingProto.getDescriptor()
        .findEnumTypeByName("StreamingTrainingStage");
    assertNotNull(stage);
    assertEquals(0, stage.findValueByName("STREAMING_TRAINING_STAGE_UNSPECIFIED").getNumber());
    assertEquals(1, stage.findValueByName("STREAMING_TRAINING_STAGE_ANALYSIS").getNumber());
    assertEquals(2, stage.findValueByName("STREAMING_TRAINING_STAGE_VOCABULARY").getNumber());
    assertEquals(3, stage.findValueByName("STREAMING_TRAINING_STAGE_MODEL").getNumber());
    assertEquals(4, stage.findValueByName("STREAMING_TRAINING_STAGE_INDEX").getNumber());
  }

  private static Descriptor message(String name) {
    final Descriptor descriptor = OpenNlpTrainingProto.getDescriptor().findMessageTypeByName(name);
    assertNotNull(descriptor, () -> "opennlp_training.proto lacks " + name);
    return descriptor;
  }

  private static void assertField(
      Descriptor descriptor, String name, FieldDescriptor.JavaType type, int number) {
    final FieldDescriptor field = descriptor.findFieldByName(name);
    assertNotNull(field, () -> descriptor.getName() + " lacks " + name);
    assertEquals(type, field.getJavaType());
    assertEquals(number, field.getNumber());
  }

  private static void assertMessageField(
      Descriptor descriptor, String name, String type, int number) {
    final FieldDescriptor field = descriptor.findFieldByName(name);
    assertNotNull(field, () -> descriptor.getName() + " lacks " + name);
    assertEquals(FieldDescriptor.JavaType.MESSAGE, field.getJavaType());
    assertEquals(type, field.getMessageType().getName());
    assertEquals(number, field.getNumber());
  }

  private static void assertRepeatedMessageField(
      Descriptor descriptor, String name, String type, int number) {
    final FieldDescriptor field = descriptor.findFieldByName(name);
    assertMessageField(descriptor, name, type, number);
    assertTrue(field.isRepeated());
  }

  private static void assertEnumField(
      Descriptor descriptor, String name, String type, int number) {
    final FieldDescriptor field = descriptor.findFieldByName(name);
    assertNotNull(field, () -> descriptor.getName() + " lacks " + name);
    assertEquals(FieldDescriptor.JavaType.ENUM, field.getJavaType());
    assertEquals(type, field.getEnumType().getName());
    assertEquals(number, field.getNumber());
  }
}
