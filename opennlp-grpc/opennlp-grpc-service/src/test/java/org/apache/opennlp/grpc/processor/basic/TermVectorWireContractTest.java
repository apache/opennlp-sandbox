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
 * KIND, either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.processor.basic;

import java.util.Map;
import java.util.stream.Collectors;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.OpenNlpDocumentProto;
import org.apache.opennlp.grpc.v1.OpenNlpPipelineProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Defines the typed document-layer contract for aggregate term vectors. */
class TermVectorWireContractTest {

  @Test
  void pipelineConfigSelectsModeAndSourceDocumentLayer() {
    final EnumDescriptor steps = OpenNlpPipelineProto.getDescriptor()
        .findEnumTypeByName("PipelineStep");
    assertEquals(18, steps.findValueByName("PIPELINE_STEP_TERM_VECTOR").getNumber());

    final FieldDescriptor config = requiredMessageField(
        AnalysisProfile.getDescriptor(), "term_vector", "TermVectorSpec");
    assertEquals(20, config.getNumber());
    final Descriptor spec = config.getMessageType();
    assertEquals("TermVectorMode", requiredField(spec, "mode").getEnumType().getName());
    assertEquals("LayerIdentity", requiredMessageField(
        spec, "source_layer", "LayerIdentity").getMessageType().getName());

    final EnumDescriptor mode = OpenNlpDocumentProto.getDescriptor()
        .findEnumTypeByName("TermVectorMode");
    assertEquals(Map.of(
            "TERM_VECTOR_MODE_UNSPECIFIED", 0,
            "TERM_VECTOR_MODE_FULL", 1,
            "TERM_VECTOR_MODE_SCORING_ONLY", 2),
        mode.getValues().stream().collect(Collectors.toMap(
            value -> value.getName(), value -> value.getNumber())));
  }

  @Test
  void resultIsAStronglyTypedDocumentLayerWithProvenance() {
    final EnumDescriptor layers = OpenNlpDocumentProto.getDescriptor()
        .findEnumTypeByName("StandardLayer");
    assertEquals(22, layers.findValueByName("STANDARD_LAYER_TERM_VECTORS").getNumber());

    final FieldDescriptor values = requiredMessageField(
        AnnotationLayer.getDescriptor(), "term_vector_values", "TermVectorAnnotationList");
    assertEquals(18, values.getNumber());
    final Descriptor list = values.getMessageType();
    assertEquals("TermVectorAnnotation", requiredField(list, "annotations")
        .getMessageType().getName());
    assertEquals("TermVectorMode", requiredField(list, "mode").getEnumType().getName());
    assertEquals("LayerIdentity", requiredMessageField(
        list, "source_layer", "LayerIdentity").getMessageType().getName());

    final Descriptor annotation = requiredField(list, "annotations").getMessageType();
    assertEquals(1, requiredField(annotation, "term").getNumber());
    assertEquals(2, requiredField(annotation, "frequency").getNumber());
    assertEquals(3, requiredField(annotation, "occurrences").getNumber());
  }

  private static FieldDescriptor requiredMessageField(
      Descriptor owner, String name, String typeName) {
    final FieldDescriptor field = requiredField(owner, name);
    assertEquals(FieldDescriptor.Type.MESSAGE, field.getType());
    assertEquals(typeName, field.getMessageType().getName());
    return field;
  }

  private static FieldDescriptor requiredField(Descriptor owner, String name) {
    final FieldDescriptor field = owner.findFieldByName(name);
    assertNotNull(field, () -> owner.getFullName() + " is missing field " + name);
    return field;
  }
}
