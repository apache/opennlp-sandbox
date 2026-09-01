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
 * KIND, either express or implied.  See the specific
 * language governing permissions and limitations under the License.
 */
package org.apache.opennlp.grpc.descriptors;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import org.apache.opennlp.grpc.v1.QueryNode;
import org.apache.opennlp.grpc.v1.SearchHit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins registry lookup, loader-backed resolution, and the negative-lookup reset rules. */
class DescriptorRegistryTest {

  @Test
  void registersAndFindsByFullAndSimpleName() {
    final DescriptorRegistry registry = new DescriptorRegistry();
    registry.registerFromMessage(QueryNode.getDefaultInstance());

    assertSame(QueryNode.getDescriptor(),
        registry.findDescriptorByFullName("org.apache.opennlp.grpc.v1.QueryNode"));
    assertSame(QueryNode.getDescriptor(), registry.findDescriptorBySimpleName("QueryNode"));
    assertSame(QueryNode.getDescriptor(), registry.findDescriptor("QueryNode"));
    assertTrue(registry.isRegistered("org.apache.opennlp.grpc.v1.QueryNode"));
  }

  @Test
  void seedsWellKnownTypesAndSurvivesClear() {
    final DescriptorRegistry registry = new DescriptorRegistry();
    registry.registerFromMessage(SearchHit.getDefaultInstance());

    registry.clear();

    assertNotNull(registry.findDescriptorByFullName("google.protobuf.Struct"));
    assertNull(registry.findDescriptorByFullName("org.apache.opennlp.grpc.v1.SearchHit"));
  }

  @Test
  void registerFileIncludesNestedTypes() throws Exception {
    final DescriptorRegistry registry = new DescriptorRegistry();
    final FileDescriptor structFile = com.google.protobuf.Struct.getDescriptor().getFile();

    registry.registerFile(structFile);

    // FieldsEntry is the nested map-entry type of Struct.
    assertNotNull(
        registry.findDescriptorByFullName("google.protobuf.Struct.FieldsEntry"));
  }

  @Test
  void resolvesOnDemandThroughALoaderAndNegativeCachesMisses() throws Exception {
    final AtomicInteger consultations = new AtomicInteger();
    final DescriptorRegistry registry = new DescriptorRegistry();
    registry.addLoader(new DescriptorLoader() {
      @Override
      public List<FileDescriptor> loadDescriptors() {
        return List.of(QueryNode.getDescriptor().getFile());
      }

      @Override
      public FileDescriptor loadDescriptor(String fileName) {
        return null;
      }

      @Override
      public FileDescriptor loadDescriptorForType(String fullTypeName) {
        consultations.incrementAndGet();
        return fullTypeName.startsWith("org.apache.opennlp.grpc.v1.")
            ? QueryNode.getDescriptor().getFile() : null;
      }

      @Override
      public boolean isAvailable() {
        return true;
      }

      @Override
      public String getLoaderType() {
        return "test";
      }
    });

    final Descriptor resolved =
        registry.findDescriptorByFullName("org.apache.opennlp.grpc.v1.JoinClause");
    assertNotNull(resolved);
    assertEquals("JoinClause", resolved.getName());

    assertNull(registry.findDescriptorByFullName("absent.Type"));
    final int consultedOnFirstMiss = consultations.get();
    assertNull(registry.findDescriptorByFullName("absent.Type"));
    assertEquals(consultedOnFirstMiss, consultations.get());
  }

  @Test
  void addingALoaderRetriesPreviousMisses() {
    final DescriptorRegistry registry = new DescriptorRegistry();
    assertNull(registry.findDescriptorByFullName("org.apache.opennlp.grpc.v1.QueryNode"));

    registry.addLoader(new ClasspathDescriptorLoader());

    assertNotNull(registry.findDescriptorByFullName("org.apache.opennlp.grpc.v1.QueryNode"));
  }

  @Test
  void firstSimpleNameRegistrationWins() throws Exception {
    final DescriptorRegistry registry = new DescriptorRegistry();
    registry.loadFrom(new DescriptorSetLoader());

    final Descriptor bySimpleName = registry.findDescriptorBySimpleName("QueryNode");
    assertNotNull(bySimpleName);
    assertEquals("org.apache.opennlp.grpc.v1.QueryNode", bySimpleName.getFullName());
    assertTrue(registry.size() > 0);
    assertTrue(registry.registeredDescriptors().contains(bySimpleName));
  }
}
