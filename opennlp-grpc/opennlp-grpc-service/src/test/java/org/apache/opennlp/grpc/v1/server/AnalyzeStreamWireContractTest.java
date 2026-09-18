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
package org.apache.opennlp.grpc.v1.server;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpServiceProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Defines the high-throughput document-analysis streaming wire contract. */
class AnalyzeStreamWireContractTest {

  @Test
  void analysisServiceExposesABidirectionalAnalyzeStreamMethod() {
    final var method = OpenNlpAnalysisServiceGrpc.getServiceDescriptor()
        .getMethods().stream()
        .filter(candidate -> candidate.getBareMethodName().equals("AnalyzeStream"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("AnalyzeStream method is missing"));

    assertEquals(MethodType.BIDI_STREAMING, method.getType());
  }

  @Test
  void requestStartsWithOneConfigurationThenCarriesSequencedDocuments() {
    final FileDescriptor file = OpenNlpServiceProto.getDescriptor();
    final Descriptor request = requiredMessage(file, "AnalyzeStreamRequest");
    final OneofDescriptor message = requiredOneof(request, "message");

    assertMessageField(request, message, "configuration", "AnalyzeStreamConfiguration");
    assertMessageField(request, message, "document", "AnalyzeStreamDocument");

    final Descriptor configuration = requiredMessage(file, "AnalyzeStreamConfiguration");
    assertMessageField(configuration, null, "profile", "AnalysisProfile");
    assertMessageField(configuration, null, "options", "AnalysisOptions");
    assertStringField(configuration, "profile_id");
    assertRepeatedMessageField(configuration, "chunk_embed_configs", "ChunkEmbedConfigEntry");
    assertRepeatedMessageField(
        configuration, "category_chunk_configs", "CategoryChunkConfigEntry");

    final Descriptor document = requiredMessage(file, "AnalyzeStreamDocument");
    assertEquals(FieldDescriptor.Type.UINT64, requiredField(document, "sequence").getType());
    assertMessageField(document, null, "document", "OpenNlpDocument");
  }

  @Test
  void responseCorrelatesCompletionOrderedSuccessesAndPerDocumentFailures() {
    final FileDescriptor file = OpenNlpServiceProto.getDescriptor();
    final Descriptor response = requiredMessage(file, "AnalyzeStreamResponse");
    final OneofDescriptor result = requiredOneof(response, "result");

    assertEquals(FieldDescriptor.Type.UINT64, requiredField(response, "sequence").getType());
    assertMessageField(response, result, "ok", "AnalyzeDocumentResponse");
    assertMessageField(response, result, "error", "AnalyzeStreamError");

    final Descriptor error = requiredMessage(file, "AnalyzeStreamError");
    final FieldDescriptor code = requiredField(error, "code");
    assertEquals(FieldDescriptor.Type.ENUM, code.getType());
    assertEquals("GrpcStatusCode", code.getEnumType().getName());
    assertStringField(error, "message");
  }

  @Test
  void progressiveAnalysisStreamsAtomicLayerUpdatesBeforeTheCanonicalDocument() {
    final var method = OpenNlpAnalysisServiceGrpc.getServiceDescriptor()
        .getMethods().stream()
        .filter(candidate -> candidate.getBareMethodName().equals("AnalyzeDocumentProgressive"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("AnalyzeDocumentProgressive method is missing"));

    assertEquals(MethodType.SERVER_STREAMING, method.getType());
    final FileDescriptor file = OpenNlpServiceProto.getDescriptor();
    final var rpc = file.findServiceByName("OpenNlpAnalysisService")
        .findMethodByName("AnalyzeDocumentProgressive");
    assertNotNull(rpc);
    assertEquals("AnalyzeDocumentRequest", rpc.getInputType().getName());
    assertEquals("AnalyzeDocumentEvent", rpc.getOutputType().getName());

    final Descriptor event = requiredMessage(file, "AnalyzeDocumentEvent");
    final OneofDescriptor update = requiredOneof(event, "update");
    assertEquals(FieldDescriptor.Type.UINT64, requiredField(event, "sequence").getType());
    assertMessageField(event, update, "started", "AnalysisStarted");
    assertMessageField(event, update, "layers_ready", "AnalysisLayerBatch");
    assertMessageField(event, update, "step_failed", "AnalysisStepFailure");
    assertMessageField(event, update, "complete", "AnalyzeDocumentResponse");

    final Descriptor started = requiredMessage(file, "AnalysisStarted");
    assertMessageField(started, null, "document", "OpenNlpDocument");
    assertRepeatedEnumField(started, "requested_steps", "PipelineStep");

    final Descriptor layers = requiredMessage(file, "AnalysisLayerBatch");
    assertEnumField(layers, "step", "PipelineStep");
    assertRepeatedMessageField(layers, "layers", "AnnotationLayer");
    assertRepeatedMessageField(layers, "diagnostics", "ProcessingDiagnostic");

    final Descriptor failure = requiredMessage(file, "AnalysisStepFailure");
    assertEnumField(failure, "step", "PipelineStep");
    assertEnumField(failure, "code", "GrpcStatusCode");
    assertStringField(failure, "message");
  }

  private static Descriptor requiredMessage(FileDescriptor file, String name) {
    final Descriptor descriptor = file.findMessageTypeByName(name);
    assertNotNull(descriptor, () -> "Missing message " + name);
    return descriptor;
  }

  private static OneofDescriptor requiredOneof(Descriptor owner, String name) {
    final OneofDescriptor oneof = owner.getOneofs().stream()
        .filter(candidate -> candidate.getName().equals(name))
        .findFirst()
        .orElse(null);
    assertNotNull(oneof, () -> owner.getFullName() + " is missing oneof " + name);
    return oneof;
  }

  private static void assertMessageField(
      Descriptor owner, OneofDescriptor oneof, String name, String typeName) {
    final FieldDescriptor field = requiredField(owner, name);
    assertEquals(FieldDescriptor.Type.MESSAGE, field.getType());
    assertEquals(typeName, field.getMessageType().getName());
    assertFalse(field.isRepeated());
    if (oneof == null) {
      assertFalse(field.getContainingOneof() != null);
    } else {
      assertEquals(oneof, field.getContainingOneof());
    }
  }

  private static void assertRepeatedMessageField(
      Descriptor owner, String name, String typeName) {
    final FieldDescriptor field = requiredField(owner, name);
    assertEquals(FieldDescriptor.Type.MESSAGE, field.getType());
    assertEquals(typeName, field.getMessageType().getName());
    assertTrue(field.isRepeated());
  }

  private static void assertEnumField(Descriptor owner, String name, String typeName) {
    final FieldDescriptor field = requiredField(owner, name);
    assertEquals(FieldDescriptor.Type.ENUM, field.getType());
    assertEquals(typeName, field.getEnumType().getName());
    assertFalse(field.isRepeated());
  }

  private static void assertRepeatedEnumField(
      Descriptor owner, String name, String typeName) {
    final FieldDescriptor field = requiredField(owner, name);
    assertEquals(FieldDescriptor.Type.ENUM, field.getType());
    assertEquals(typeName, field.getEnumType().getName());
    assertTrue(field.isRepeated());
  }

  private static void assertStringField(Descriptor owner, String name) {
    final FieldDescriptor field = requiredField(owner, name);
    assertEquals(FieldDescriptor.Type.STRING, field.getType());
    assertFalse(field.isRepeated());
  }

  private static FieldDescriptor requiredField(Descriptor owner, String name) {
    final FieldDescriptor field = owner.findFieldByName(name);
    assertNotNull(field, () -> owner.getFullName() + " is missing field " + name);
    return field;
  }
}
