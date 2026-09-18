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
package org.apache.opennlp.grpc.chunk;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Defines the additive protobuf contract for selecting and reporting chunking strategies. */
class ChunkingWireContractTest {

  @Test
  void chunkingSpecAddsClosedStandardAndOpenCustomStrategy() {
    final Descriptor spec = ChunkingSpec.getDescriptor();
    final FieldDescriptor legacy = requiredField(spec, "algorithm");
    assertEquals(1, legacy.getNumber());
    assertEquals(FieldDescriptor.Type.STRING, legacy.getType());

    final FieldDescriptor strategy = requiredMessageField(
        spec, "strategy", "ChunkingStrategySelector");
    assertEquals(7, strategy.getNumber());

    final Descriptor selector = strategy.getMessageType();
    assertEquals(Set.of("standard", "custom"), selector.getOneofs().stream()
        .filter(oneof -> "kind".equals(oneof.getName()))
        .flatMap(oneof -> oneof.getFields().stream())
        .map(FieldDescriptor::getName)
        .collect(Collectors.toSet()));

    final EnumDescriptor standard = selector.getFile()
        .findEnumTypeByName("StandardChunkingStrategy");
    assertNotNull(standard, "StandardChunkingStrategy is missing");
    assertEquals(Map.of(
            "STANDARD_CHUNKING_STRATEGY_UNSPECIFIED", 0,
            "STANDARD_CHUNKING_STRATEGY_SENTENCE", 1,
            "STANDARD_CHUNKING_STRATEGY_TOKEN", 2,
            "STANDARD_CHUNKING_STRATEGY_SEMANTIC", 3,
            "STANDARD_CHUNKING_STRATEGY_CATEGORY", 4),
        standard.getValues().stream().collect(Collectors.toMap(
            value -> value.getName(), value -> value.getNumber())));
  }

  @Test
  void chunkGroupReportsTheResolvedStrategy() {
    final FieldDescriptor strategy = requiredMessageField(
        ChunkEmbeddingGroup.getDescriptor(), "strategy", "ChunkingStrategySelector");
    assertEquals(9, strategy.getNumber());
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
