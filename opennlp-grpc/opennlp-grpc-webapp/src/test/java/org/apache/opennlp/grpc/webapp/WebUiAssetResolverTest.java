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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.apache.opennlp.grpc.webapp.spi.WebUiClasspathResource;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtension;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionDescriptor;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionId;
import org.apache.opennlp.grpc.webapp.spi.WebUiMountPath;
import org.junit.jupiter.api.Test;

class WebUiAssetResolverTest {

  private final WebUiAssetResolver resolver = new WebUiAssetResolver(
      new WebUiExtensionRegistry(List.of(extension())));

  @Test
  void resolvesMountRootToIndex() {
    Optional<WebUiAsset> result = resolver.resolve("/console");

    assertTrue(result.isPresent());
    assertEquals("text/html; charset=utf-8", result.orElseThrow().contentType());
    assertTrue(new String(result.orElseThrow().content(), StandardCharsets.UTF_8)
        .endsWith("test console\n"));
  }

  @Test
  void resolvesNestedStaticAsset() {
    Optional<WebUiAsset> result = resolver.resolve("/console/assets/app.js");

    assertTrue(result.isPresent());
    assertEquals("text/javascript; charset=utf-8", result.orElseThrow().contentType());
  }

  @Test
  void doesNotMatchMountPathPrefixes() {
    assertFalse(resolver.resolve("/console-other/index.html").isPresent());
  }

  @Test
  void rejectsTraversalAndEncodedSeparators() {
    assertFalse(resolver.resolve("/console/../private.txt").isPresent());
    assertFalse(resolver.resolve("/console/%2e%2e/private.txt").isPresent());
    assertFalse(resolver.resolve("/console/assets%2fapp.js").isPresent());
    assertFalse(resolver.resolve("/console/assets\\app.js").isPresent());
  }

  private static WebUiExtension extension() {
    WebUiExtensionDescriptor descriptor = new WebUiExtensionDescriptor(
        new WebUiExtensionId("test-console"),
        "Test console",
        new WebUiMountPath("/console"),
        new WebUiClasspathResource("/test-web-ui"));
    return new WebUiExtension() {
      @Override
      public WebUiExtensionDescriptor descriptor() {
        return descriptor;
      }

      @Override
      public ClassLoader resourceClassLoader() {
        return WebUiAssetResolverTest.class.getClassLoader();
      }
    };
  }
}
