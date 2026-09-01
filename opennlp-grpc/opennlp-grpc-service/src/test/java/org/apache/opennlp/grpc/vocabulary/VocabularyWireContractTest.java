/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
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
package org.apache.opennlp.grpc.vocabulary;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.opennlp.grpc.v1.DictionaryArtifactDescriptor;
import org.apache.opennlp.grpc.v1.DictionaryFormatSelector;
import org.apache.opennlp.grpc.v1.ImportDictionaryRequest;
import org.apache.opennlp.grpc.v1.LearnVocabularyRequest;
import org.apache.opennlp.grpc.v1.ListDictionaryFormatsResponse;
import org.apache.opennlp.grpc.v1.OpenNlpVocabularyProto;
import org.apache.opennlp.grpc.v1.StandardDictionaryFormat;
import org.apache.opennlp.grpc.v1.VocabularyArtifactDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabularyWireContractTest {

  @Test
  void exposesBoundedStreamingVocabularyService() {
    final var service = OpenNlpVocabularyProto.getDescriptor()
        .findServiceByName("OpenNlpVocabularyService");
    assertNotNull(service);
    assertEquals(6, service.getMethods().size());
    assertMethod(service.findMethodByName("ListDictionaryFormats"), false, false);
    assertMethod(service.findMethodByName("ListDictionaries"), false, false);
    assertMethod(service.findMethodByName("ListVocabularies"), false, false);
    assertMethod(service.findMethodByName("ImportDictionary"), true, false);
    assertMethod(service.findMethodByName("LearnVocabulary"), true, false);
    assertMethod(service.findMethodByName("DownloadVocabulary"), false, true);
  }

  @Test
  void importsASelectedFormatAsStreamedBoundedBytes() {
    final Descriptor request = ImportDictionaryRequest.getDescriptor();
    assertEquals("frame", request.getOneofs().getFirst().getName());
    assertMessageField(request, "start", "ImportDictionaryStart", 1);
    assertField(request, "data", FieldDescriptor.JavaType.BYTE_STRING, 2);
    assertEquals(2, request.getOneofs().getFirst().getFieldCount());

    final Descriptor start = request.findFieldByName("start").getMessageType();
    assertMessageField(start, "format", DictionaryFormatSelector.getDescriptor().getName(), 1);
    assertField(start, "display_name", FieldDescriptor.JavaType.STRING, 2);
    assertField(start, "provenance_summary", FieldDescriptor.JavaType.STRING, 3);
    assertField(start, "source_uri", FieldDescriptor.JavaType.STRING, 4);
    assertField(start, "license_name", FieldDescriptor.JavaType.STRING, 5);
    assertField(start, "license_uri", FieldDescriptor.JavaType.STRING, 6);
    assertTrue(start.findFieldByName("source_uri").toProto().getProto3Optional());
    assertTrue(start.findFieldByName("license_name").toProto().getProto3Optional());
    assertTrue(start.findFieldByName("license_uri").toProto().getProto3Optional());
  }

  @Test
  void learnsFromDocumentShapesAndProducesAReusableArtifact() {
    final Descriptor request = LearnVocabularyRequest.getDescriptor();
    assertEquals("frame", request.getOneofs().getFirst().getName());
    assertMessageField(request, "start", "LearnVocabularyStart", 1);
    assertMessageField(request, "document", "OpenNlpDocument", 2);

    final Descriptor start = request.findFieldByName("start").getMessageType();
    assertField(start, "dictionary_artifact_id", FieldDescriptor.JavaType.STRING, 1);
    assertField(start, "display_name", FieldDescriptor.JavaType.STRING, 2);
    assertField(start, "min_frequency", FieldDescriptor.JavaType.INT, 3);
    assertField(start, "max_terms", FieldDescriptor.JavaType.INT, 4);
    assertField(start, "provenance_summary", FieldDescriptor.JavaType.STRING, 5);

    final Descriptor artifact = VocabularyArtifactDescriptor.getDescriptor();
    assertField(artifact, "artifact_id", FieldDescriptor.JavaType.STRING, 1);
    assertField(artifact, "dictionary_artifact_id", FieldDescriptor.JavaType.STRING, 3);
    assertField(artifact, "term_count", FieldDescriptor.JavaType.INT, 4);
    assertField(artifact, "dictionary_term_count", FieldDescriptor.JavaType.INT, 5);
    assertField(artifact, "corpus_term_count", FieldDescriptor.JavaType.INT, 6);
    assertField(artifact, "artifact_hash", FieldDescriptor.JavaType.STRING, 9);
    assertMessageField(artifact, "created_at", "Timestamp", 12);
  }

  @Test
  void dictionaryFormatsAreStrongAndExtensible() {
    final Descriptor selector = DictionaryFormatSelector.getDescriptor();
    assertEquals("kind", selector.getOneofs().getFirst().getName());
    assertEquals(StandardDictionaryFormat.getDescriptor(),
        selector.findFieldByName("standard").getEnumType());
    assertField(selector, "custom", FieldDescriptor.JavaType.STRING, 2);
    assertEquals(0, StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_UNSPECIFIED.getNumber());
    assertEquals(1,
        StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_HEADWORD_DEFINITION_TSV.getNumber());
    assertEquals(2,
        StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_HEADWORD_LINES.getNumber());
    assertEquals(3,
        StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_OPENNLP_XML.getNumber());

    final Descriptor dictionary = DictionaryArtifactDescriptor.getDescriptor();
    assertField(dictionary, "artifact_id", FieldDescriptor.JavaType.STRING, 1);
    assertMessageField(dictionary, "format", DictionaryFormatSelector.getDescriptor().getName(), 3);
    assertField(dictionary, "entry_count", FieldDescriptor.JavaType.INT, 4);
    assertField(dictionary, "artifact_hash", FieldDescriptor.JavaType.STRING, 5);
    assertMessageField(dictionary, "created_at", "Timestamp", 11);

    final Descriptor limits = ListDictionaryFormatsResponse.getDescriptor();
    assertField(limits, "max_concurrent_writes", FieldDescriptor.JavaType.INT, 8);
  }

  private static void assertMethod(
      com.google.protobuf.Descriptors.MethodDescriptor method,
      boolean clientStreaming,
      boolean serverStreaming) {
    assertNotNull(method);
    assertEquals(clientStreaming, method.isClientStreaming());
    assertEquals(serverStreaming, method.isServerStreaming());
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
}
