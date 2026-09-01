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
package org.apache.opennlp.grpc.v1.server;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.opennlp.grpc.v1.EnginePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins typed standard and open custom engine selection for model-backed steps. */
class EnginePolicyWireContractTest {

  @Test
  void enginePolicyAddsTypedSelectorsWithoutReusingTheLegacyField() {
    final Descriptor policy = EnginePolicy.getDescriptor();
    final FieldDescriptor selectors = policy.findFieldByName("selectors");

    assertNotNull(selectors, "EnginePolicy.selectors is missing");
    assertEquals(3, selectors.getNumber());
    assertTrue(selectors.isRepeated());
    assertEquals("org.apache.opennlp.grpc.v1.EngineSelector",
        selectors.getMessageType().getFullName());
    assertEquals(1, policy.findFieldByName("engines").getNumber());
  }

  @Test
  void engineSelectorUsesClosedStandardOrOpenCustomIdentity() {
    final Descriptor selector = EnginePolicy.getDescriptor().getFile()
        .findMessageTypeByName("EngineSelector");
    assertNotNull(selector, "EngineSelector is missing");
    assertEquals(Set.of("standard", "custom"), selector.getOneofs().stream()
        .filter(oneof -> "kind".equals(oneof.getName()))
        .flatMap(oneof -> oneof.getFields().stream())
        .map(FieldDescriptor::getName)
        .collect(Collectors.toSet()));

    final EnumDescriptor standard = selector.getFile()
        .findEnumTypeByName("StandardInferenceEngine");
    assertNotNull(standard, "StandardInferenceEngine is missing");
    assertEquals(Map.of(
            "STANDARD_INFERENCE_ENGINE_UNSPECIFIED", 0,
            "STANDARD_INFERENCE_ENGINE_OPENNLP_ME", 1,
            "STANDARD_INFERENCE_ENGINE_ONNX", 2,
            "STANDARD_INFERENCE_ENGINE_CUDA", 3),
        standard.getValues().stream().collect(Collectors.toMap(
            value -> value.getName(), value -> value.getNumber())));
  }
}
