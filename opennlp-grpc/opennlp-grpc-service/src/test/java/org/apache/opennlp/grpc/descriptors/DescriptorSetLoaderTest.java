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

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that the api jar's shipped descriptor set is loadable and complete, and that the
 * loader rejects broken sets instead of recursing or guessing.
 */
class DescriptorSetLoaderTest {

  @Test
  void loadsTheShippedContractFromTheClasspath() throws Exception {
    final DescriptorSetLoader loader = new DescriptorSetLoader();

    assertTrue(loader.isAvailable());
    final List<FileDescriptor> files = loader.loadDescriptors();
    final List<String> names = files.stream().map(FileDescriptor::getName).toList();
    assertTrue(names.contains("org/apache/opennlp/grpc/v1/opennlp_query.proto"));
    assertTrue(names.contains("org/apache/opennlp/grpc/v1/opennlp_search.proto"));
    assertTrue(names.contains("org/apache/opennlp/grpc/v1/opennlp_document.proto"));
  }

  @Test
  void resolvesAFileByNameAndATypeByFullName() throws Exception {
    final DescriptorSetLoader loader = new DescriptorSetLoader();

    final FileDescriptor query =
        loader.loadDescriptor("org/apache/opennlp/grpc/v1/opennlp_query.proto");
    assertNotNull(query);
    assertNotNull(query.findMessageTypeByName("QueryNode"));

    final FileDescriptor byType =
        loader.loadDescriptorForType("org.apache.opennlp.grpc.v1.QueryNode");
    assertNotNull(byType);
    assertEquals(query.getName(), byType.getName());
    assertNull(loader.loadDescriptor("no/such/file.proto"));
    assertNull(loader.loadDescriptorForType("org.apache.opennlp.grpc.v1.NoSuchType"));
  }

  @Test
  void reportsAMissingResourceInsteadOfGuessing() {
    final DescriptorSetLoader loader = new DescriptorSetLoader("META-INF/absent.protobin");

    assertFalse(loader.isAvailable());
    final DescriptorLoader.DescriptorLoadException failure = assertThrows(
        DescriptorLoader.DescriptorLoadException.class, loader::loadDescriptors);
    assertTrue(failure.getMessage().contains("META-INF/absent.protobin"));
  }

  @Test
  void rejectsACyclicDescriptorSet() {
    final FileDescriptorSet cyclic = FileDescriptorSet.newBuilder()
        .addFile(FileDescriptorProto.newBuilder()
            .setName("a.proto").setSyntax("proto3").addDependency("b.proto"))
        .addFile(FileDescriptorProto.newBuilder()
            .setName("b.proto").setSyntax("proto3").addDependency("a.proto"))
        .build();

    final DescriptorLoader.DescriptorLoadException failure = assertThrows(
        DescriptorLoader.DescriptorLoadException.class,
        () -> DescriptorSetLoader.fromDescriptorSet(cyclic));
    assertTrue(failure.getMessage().contains("dependency cycle"));
  }

  @Test
  void rejectsAMissingDependencyThatIsNotWellKnown() {
    final FileDescriptorSet incomplete = FileDescriptorSet.newBuilder()
        .addFile(FileDescriptorProto.newBuilder()
            .setName("a.proto").setSyntax("proto3").addDependency("absent.proto"))
        .build();

    final DescriptorLoader.DescriptorLoadException failure = assertThrows(
        DescriptorLoader.DescriptorLoadException.class,
        () -> DescriptorSetLoader.fromDescriptorSet(incomplete));
    assertTrue(failure.getMessage().contains("absent.proto"));
  }

  @Test
  void linksWellKnownDependenciesAbsentFromTheSet() throws Exception {
    final FileDescriptorSet set = FileDescriptorSet.newBuilder()
        .addFile(FileDescriptorProto.newBuilder()
            .setName("uses_struct.proto")
            .setSyntax("proto3")
            .addDependency("google/protobuf/struct.proto"))
        .build();

    final List<FileDescriptor> files = DescriptorSetLoader.fromDescriptorSet(set);
    assertEquals(1, files.size());
    assertEquals("uses_struct.proto", files.getFirst().getName());
  }
}
