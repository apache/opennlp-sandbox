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

import java.util.Set;
import java.util.stream.Collectors;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import org.apache.opennlp.grpc.v1.ConfiguredResource;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.OpenNlpServiceProto;
import org.apache.opennlp.grpc.v1.ResourceIdentity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pins the additive capability-discovery wire contract used by replacement clients. */
class ServiceCapabilityWireContractTest {

  @Test
  void configuredResourcesUseClosedStandardOrOpenCustomIdentity() {
    final Descriptor serviceInfo = GetServiceInfoResponse.getDescriptor();
    assertEquals(8, serviceInfo.findFieldByName("configured_resources").getNumber());
    assertEquals("org.apache.opennlp.grpc.v1.ConfiguredResource",
        serviceInfo.findFieldByName("configured_resources").getMessageType().getFullName());

    final Descriptor resource = ConfiguredResource.getDescriptor();
    assertEquals("org.apache.opennlp.grpc.v1.ResourceIdentity",
        resource.findFieldByName("identity").getMessageType().getFullName());
    assertEquals("string", resource.findFieldByName("resource_id").getType().name().toLowerCase());
    assertEquals("bool", resource.findFieldByName("is_default").getType().name().toLowerCase());

    final Descriptor identity = ResourceIdentity.getDescriptor();
    assertEquals(Set.of("standard", "custom"), identity.getOneofs().stream()
        .filter(oneof -> "kind".equals(oneof.getName()))
        .flatMap(oneof -> oneof.getFields().stream())
        .map(field -> field.getName())
        .collect(Collectors.toSet()));

    final EnumDescriptor standard = OpenNlpServiceProto.getDescriptor()
        .findEnumTypeByName("StandardResource");
    assertEquals(Set.of(
        "STANDARD_RESOURCE_UNSPECIFIED",
        "STANDARD_RESOURCE_SUBWORD_MODEL",
        "STANDARD_RESOURCE_HUNSPELL_DICTIONARY",
        "STANDARD_RESOURCE_WORDNET_LEXICON",
        "STANDARD_RESOURCE_LATTICE_DICTIONARY"),
        standard.getValues().stream().map(value -> value.getName()).collect(Collectors.toSet()));
  }
}
