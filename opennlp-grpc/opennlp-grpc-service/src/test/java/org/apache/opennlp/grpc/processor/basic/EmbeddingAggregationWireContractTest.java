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
package org.apache.opennlp.grpc.processor.basic;

import java.util.Map;
import java.util.stream.Collectors;

import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.OpenNlpDocumentProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Defines typed post-aggregation normalization and its result provenance. */
class EmbeddingAggregationWireContractTest {

  @Test
  void documentCentroidCanRequestTypedVectorNormalization() {
    final FieldDescriptor request = requiredField(
        AnalysisOptions.getDescriptor(), "document_centroid_normalization");
    assertEquals(10, request.getNumber());
    assertEquals("VectorNormalization", request.getEnumType().getName());

    final EnumDescriptor normalization = OpenNlpDocumentProto.getDescriptor()
        .findEnumTypeByName("VectorNormalization");
    assertNotNull(normalization);
    assertEquals(Map.of(
            "VECTOR_NORMALIZATION_UNSPECIFIED", 0,
            "VECTOR_NORMALIZATION_NONE", 1,
            "VECTOR_NORMALIZATION_L2", 2),
        normalization.getValues().stream().collect(Collectors.toMap(
            value -> value.getName(), value -> value.getNumber())));
  }

  @Test
  void embeddingResultReportsAppliedVectorNormalization() {
    final FieldDescriptor result = requiredField(
        EmbeddingResult.getDescriptor(), "vector_normalization");
    assertEquals(6, result.getNumber());
    assertEquals("VectorNormalization", result.getEnumType().getName());
  }

  private static FieldDescriptor requiredField(
      com.google.protobuf.Descriptors.Descriptor owner, String name) {
    final FieldDescriptor field = owner.findFieldByName(name);
    assertNotNull(field, () -> owner.getFullName() + " is missing field " + name);
    return field;
  }
}
