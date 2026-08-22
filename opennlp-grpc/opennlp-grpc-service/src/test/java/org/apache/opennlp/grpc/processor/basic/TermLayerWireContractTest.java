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

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Defines configurable, typed term analysis as a document-layer contract. */
class TermLayerWireContractTest {

  @Test
  void profileCanProduceMultipleQualifiedTermLayers() {
    final FieldDescriptor layers = requiredField(
        AnalysisProfile.getDescriptor(), "term_layers");
    assertEquals(21, layers.getNumber());
    assertTrue(layers.isRepeated());
    assertEquals(FieldDescriptor.Type.MESSAGE, layers.getType());
    assertEquals("TermLayerSpec", layers.getMessageType().getName());

    final Descriptor spec = layers.getMessageType();
    assertEquals(1, requiredField(spec, "qualifier").getNumber());

    final FieldDescriptor normalizers = requiredField(spec, "normalizers");
    assertEquals(2, normalizers.getNumber());
    assertTrue(normalizers.isRepeated());
    assertEquals("Normalizer", normalizers.getEnumType().getName());

    final FieldDescriptor stemmer = requiredField(spec, "stemmer");
    assertEquals(3, stemmer.getNumber());
    assertTrue(stemmer.hasPresence());
    assertEquals("StemmerSpec", stemmer.getMessageType().getName());
  }

  private static FieldDescriptor requiredField(Descriptor owner, String name) {
    final FieldDescriptor field = owner.findFieldByName(name);
    assertNotNull(field, () -> owner.getFullName() + " is missing field " + name);
    return field;
  }
}
