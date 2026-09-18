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
package org.apache.opennlp.grpc.webapp.spi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebUiExtensionDescriptorTest {

  @Test
  void retainsValidatedMetadata() {
    final WebUiExtensionId id = new WebUiExtensionId("org.apache.opennlp.demo-ui");
    final WebUiMountPath mountPath = new WebUiMountPath("/extensions/demo");
    final WebUiClasspathResource resourceRoot =
        new WebUiClasspathResource("/META-INF/opennlp-grpc-ui/demo");
    final WebUiExtensionDescriptor descriptor = new WebUiExtensionDescriptor(
        id, "Demo UI", mountPath, resourceRoot);

    assertEquals(id, descriptor.id());
    assertEquals("Demo UI", descriptor.title());
    assertEquals(mountPath, descriptor.mountPath());
    assertEquals(resourceRoot, descriptor.resourceRoot());
  }

  @Test
  void rejectsInvalidDescriptorFieldsAtBoundary() {
    final WebUiExtensionId id = new WebUiExtensionId("demo");
    final WebUiMountPath mountPath = new WebUiMountPath("/extensions/demo");
    final WebUiClasspathResource resourceRoot =
        new WebUiClasspathResource("/META-INF/opennlp-grpc-ui/demo");

    assertMessageContains(assertThrows(IllegalArgumentException.class,
        () -> new WebUiExtensionDescriptor(null, "Demo", mountPath, resourceRoot)),
        "id", "must not be null");
    assertMessageContains(assertThrows(IllegalArgumentException.class,
        () -> new WebUiExtensionDescriptor(id, " ", mountPath, resourceRoot)),
        "title", "must not be blank");
    assertMessageContains(assertThrows(IllegalArgumentException.class,
        () -> new WebUiExtensionDescriptor(id, " Demo", mountPath, resourceRoot)),
        "title", "leading or trailing whitespace");
    assertMessageContains(assertThrows(IllegalArgumentException.class,
        () -> new WebUiExtensionDescriptor(id, "Demo", null, resourceRoot)),
        "mountPath", "must not be null");
    assertMessageContains(assertThrows(IllegalArgumentException.class,
        () -> new WebUiExtensionDescriptor(id, "Demo", mountPath, null)),
        "resourceRoot", "must not be null");
  }

  private static void assertMessageContains(
      IllegalArgumentException exception, String... fragments) {
    for (String fragment : fragments) {
      assertTrue(exception.getMessage().contains(fragment),
          () -> "Expected message to contain '" + fragment + "' but was: "
              + exception.getMessage());
    }
  }
}
