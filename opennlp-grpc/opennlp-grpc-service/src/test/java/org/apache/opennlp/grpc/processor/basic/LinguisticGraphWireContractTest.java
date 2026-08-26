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

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.ComponentType;
import org.apache.opennlp.grpc.v1.DependencyAnnotation;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.RelationAnnotation;
import org.apache.opennlp.grpc.v1.StandardLayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LinguisticGraphWireContractTest {

  @Test
  void appendsDependencyAndRelationStepsWithoutRenumberingExistingValues() {
    final EnumDescriptor steps = PipelineStep.getDescriptor();
    assertEquals(18, steps.findValueByName("PIPELINE_STEP_TERM_VECTOR").getNumber());
    assertEquals(19, steps.findValueByName("PIPELINE_STEP_DEPENDENCY_PARSE").getNumber());
    assertEquals(20, steps.findValueByName("PIPELINE_STEP_RELATION_EXTRACT").getNumber());
  }

  @Test
  void dependencyParserHasItsOwnModelComponentType() {
    assertEquals(12, ComponentType.COMPONENT_TYPE_DEPENDENCY_PARSER.getNumber());
  }

  @Test
  void exposesTypedDependencyAndRelationLayers() {
    final EnumDescriptor layers = StandardLayer.getDescriptor();
    assertEquals(22, layers.findValueByName("STANDARD_LAYER_TERM_VECTORS").getNumber());
    assertEquals(23, layers.findValueByName("STANDARD_LAYER_DEPENDENCIES").getNumber());
    assertEquals(24, layers.findValueByName("STANDARD_LAYER_RELATIONS").getNumber());

    assertValueArm("dependency_values", "DependencyAnnotationList");
    assertValueArm("relation_values", "RelationAnnotationList");
  }

  @Test
  void keepsGraphReferencesTypedAsLayerIndexes() {
    final Descriptor dependency = DependencyAnnotation.getDescriptor();
    assertNotNull(dependency.findFieldByName("span"));
    assertNotNull(dependency.findFieldByName("head_token_index"));
    assertNotNull(dependency.findFieldByName("dependent_token_index"));
    assertNotNull(dependency.findFieldByName("relation"));

    final Descriptor relation = RelationAnnotation.getDescriptor();
    assertNotNull(relation.findFieldByName("span"));
    assertNotNull(relation.findFieldByName("type"));
    assertNotNull(relation.findFieldByName("subject_entity_index"));
    assertNotNull(relation.findFieldByName("object_entity_index"));
  }

  private static void assertValueArm(String fieldName, String messageName) {
    final var field = AnnotationLayer.getDescriptor().findFieldByName(fieldName);
    assertNotNull(field);
    assertEquals(messageName, field.getMessageType().getName());
  }
}
