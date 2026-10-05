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
package org.apache.opennlp.grpc.webapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.opennlp.grpc.webapp.spi.WebUiClasspathResource;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtension;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionDescriptor;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionId;
import org.apache.opennlp.grpc.webapp.spi.WebUiMountPath;
import org.junit.jupiter.api.Test;

class WebUiExtensionRegistryTest {

  @Test
  void ordersMostSpecificMountFirst() {
    WebUiExtension root = extension("root", "/", "/ui/root");
    WebUiExtension admin = extension("admin", "/admin", "/ui/admin");

    WebUiExtensionRegistry registry = new WebUiExtensionRegistry(List.of(root, admin));

    assertEquals(List.of(admin, root), registry.extensions());
  }

  @Test
  void rejectsDuplicateIds() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new WebUiExtensionRegistry(List.of(
            extension("same", "/one", "/ui/one"),
            extension("same", "/two", "/ui/two"))));

    assertTrue(exception.getMessage().contains("duplicate extension id"));
    assertTrue(exception.getMessage().contains("same"));
  }

  @Test
  void rejectsDuplicateMountPaths() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new WebUiExtensionRegistry(List.of(
            extension("one", "/same", "/ui/one"),
            extension("two", "/same", "/ui/two"))));

    assertTrue(exception.getMessage().contains("duplicate mount path"));
    assertTrue(exception.getMessage().contains("/same"));
  }

  private static WebUiExtension extension(String id, String mount, String root) {
    WebUiExtensionDescriptor descriptor = new WebUiExtensionDescriptor(
        new WebUiExtensionId(id),
        id,
        new WebUiMountPath(mount),
        new WebUiClasspathResource(root));
    return () -> descriptor;
  }
}
