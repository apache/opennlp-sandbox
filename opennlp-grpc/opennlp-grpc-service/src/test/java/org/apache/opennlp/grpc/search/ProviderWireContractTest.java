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
package org.apache.opennlp.grpc.search;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import org.apache.opennlp.grpc.v1.OpenNlpSearchProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the search provider contract's wire shapes: capability and instance
 * declarations listed through ListSearchProviders, and the per-modality legs
 * with a recorded analysis-chain identity on the index descriptor.
 */
class ProviderWireContractTest {

  @Test
  void serviceListsProviderInstances() {
    final ServiceDescriptor service = OpenNlpSearchProto.getDescriptor()
        .findServiceByName("OpenNlpSearchService");
    assertNotNull(service);
    final MethodDescriptor list = service.findMethodByName("ListSearchProviders");
    assertNotNull(list);
    assertEquals("ListSearchProvidersRequest", list.getInputType().getName());
    assertEquals("ListSearchProvidersResponse", list.getOutputType().getName());

    final Descriptor response = OpenNlpSearchProto.getDescriptor()
        .findMessageTypeByName("ListSearchProvidersResponse");
    final FieldDescriptor providers = response.findFieldByName("providers");
    assertNotNull(providers);
    assertTrue(providers.isRepeated());
    assertEquals("SearchProviderInstance", providers.getMessageType().getName());
  }

  @Test
  void providerInstancesDeclareIdentityAndCapabilities() {
    final Descriptor instance = OpenNlpSearchProto.getDescriptor()
        .findMessageTypeByName("SearchProviderInstance");
    assertNotNull(instance);
    assertEquals(FieldDescriptor.JavaType.STRING,
        instance.findFieldByName("instance_id").getJavaType());
    assertEquals(FieldDescriptor.JavaType.STRING,
        instance.findFieldByName("provider_id").getJavaType());

    final FieldDescriptor capabilities = instance.findFieldByName("capabilities");
    assertNotNull(capabilities);
    assertTrue(capabilities.isRepeated());
    final EnumDescriptor capability = capabilities.getEnumType();
    assertEquals("SearchProviderCapability", capability.getName());
    assertNotNull(capability.findValueByName("SEARCH_PROVIDER_CAPABILITY_VECTOR"));
    assertNotNull(capability.findValueByName("SEARCH_PROVIDER_CAPABILITY_KEYWORD"));
    assertNotNull(capability.findValueByName("SEARCH_PROVIDER_CAPABILITY_LIVE"));
    assertNotNull(capability.findValueByName("SEARCH_PROVIDER_CAPABILITY_BUNDLE"));
    assertNotNull(capability.findValueByName("SEARCH_PROVIDER_CAPABILITY_PERSISTENT"));

    final FieldDescriptor standard = instance.findFieldByName("standard");
    assertNotNull(standard);
    assertTrue(standard.hasPresence());
    assertEquals("StandardSearchProvider", standard.getEnumType().getName());
  }

  @Test
  void indexDescriptorsNameTheirLegs() {
    final Descriptor descriptor = OpenNlpSearchProto.getDescriptor()
        .findMessageTypeByName("SearchIndexDescriptor");
    final FieldDescriptor legs = descriptor.findFieldByName("legs");
    assertNotNull(legs);
    assertTrue(legs.isRepeated());
    assertEquals("SearchIndexLeg", legs.getMessageType().getName());

    final Descriptor leg = OpenNlpSearchProto.getDescriptor()
        .findMessageTypeByName("SearchIndexLeg");
    final EnumDescriptor kind = leg.findFieldByName("kind").getEnumType();
    assertEquals("SearchLegKind", kind.getName());
    assertNotNull(kind.findValueByName("SEARCH_LEG_KIND_VECTOR"));
    assertNotNull(kind.findValueByName("SEARCH_LEG_KIND_KEYWORD"));
    assertEquals(FieldDescriptor.JavaType.STRING,
        leg.findFieldByName("provider_instance_id").getJavaType());
    assertEquals("AnalysisChainDescriptor",
        leg.findFieldByName("analysis_chain").getMessageType().getName());
  }

  @Test
  void keywordLegsRecordTheirAnalysisChainIdentity() {
    final Descriptor chain = OpenNlpSearchProto.getDescriptor()
        .findMessageTypeByName("AnalysisChainDescriptor");
    assertNotNull(chain);
    assertEquals(FieldDescriptor.JavaType.STRING,
        chain.findFieldByName("chain_id").getJavaType());
    assertEquals(FieldDescriptor.JavaType.STRING,
        chain.findFieldByName("chain_version").getJavaType());
    final FieldDescriptor configurationHash = chain.findFieldByName("configuration_hash");
    assertNotNull(configurationHash);
    assertTrue(configurationHash.hasPresence());
    assertEquals(FieldDescriptor.JavaType.STRING, configurationHash.getJavaType());
  }
}
