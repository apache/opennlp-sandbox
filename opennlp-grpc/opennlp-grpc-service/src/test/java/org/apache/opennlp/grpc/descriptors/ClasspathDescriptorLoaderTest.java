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

import com.google.protobuf.Descriptors.FileDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins on-demand resolution of generated classes by proto type name. */
class ClasspathDescriptorLoaderTest {

  @Test
  void resolvesAGeneratedTypeByItsProtoName() {
    final ClasspathDescriptorLoader loader = new ClasspathDescriptorLoader();

    final FileDescriptor file =
        loader.loadDescriptorForType("org.apache.opennlp.grpc.v1.QueryNode");
    assertNotNull(file);
    assertEquals("org/apache/opennlp/grpc/v1/opennlp_query.proto", file.getName());
    // Documented limitation: a java_package differing from the proto package, as the
    // well-known types use, is not recoverable from the proto name alone.
    assertNull(loader.loadDescriptorForType("google.protobuf.Struct"));
  }

  @Test
  void returnsNullForUnknownAndBlankNamesAndNeverEnumerates() {
    final ClasspathDescriptorLoader loader = new ClasspathDescriptorLoader();

    assertNull(loader.loadDescriptorForType("no.such.pkg.NoSuchType"));
    assertNull(loader.loadDescriptorForType(""));
    assertNull(loader.loadDescriptorForType(null));
    assertTrue(loader.loadDescriptors().isEmpty());
    assertTrue(loader.isAvailable());
  }
}
