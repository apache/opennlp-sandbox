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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import org.apache.opennlp.grpc.webapp.spi.WebUiClasspathResource;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtension;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionDescriptor;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionId;
import org.apache.opennlp.grpc.webapp.spi.WebUiMountPath;
import org.junit.jupiter.api.Test;

class WebUiCatalogJsonTest {

  @Test
  void exposesNavigationMetadataInStableMountOrder() throws Exception {
    WebUiExtensionRegistry registry = new WebUiExtensionRegistry(List.of(
        extension("search", "Search \"lab\"", "/search", "/ui/search"),
        extension("default", "OpenNLP", "/", "/ui/default")));

    WebHttpResponse response = new WebUiCatalogJson(registry).handle("GET");

    assertEquals(200, response.status());
    assertEquals(GrpcJsonApi.JSON_CONTENT_TYPE, response.contentType());
    Struct.Builder parsed = Struct.newBuilder();
    JsonFormat.parser().merge(new String(response.body(), StandardCharsets.UTF_8), parsed);
    List<com.google.protobuf.Value> extensions = parsed.getFieldsOrThrow("extensions")
        .getListValue().getValuesList();
    assertEquals(2, extensions.size());
    assertEquals("default", stringField(extensions.get(0), "id"));
    assertEquals("/", stringField(extensions.get(0), "mountPath"));
    assertEquals("Search \"lab\"", stringField(extensions.get(1), "title"));
    assertEquals("/search", stringField(extensions.get(1), "mountPath"));
  }

  @Test
  void rejectsUnsupportedMethods() {
    WebUiCatalogJson catalog = new WebUiCatalogJson(new WebUiExtensionRegistry(List.of()));

    WebHttpResponse response = catalog.handle("POST");

    assertEquals(405, response.status());
    assertTrue(new String(response.body(), StandardCharsets.UTF_8)
        .contains("HTTP method is not allowed"));
  }

  private static String stringField(com.google.protobuf.Value value, String field) {
    return value.getStructValue().getFieldsOrThrow(field).getStringValue();
  }

  private static WebUiExtension extension(String id, String title, String mount, String root) {
    WebUiExtensionDescriptor descriptor = new WebUiExtensionDescriptor(
        new WebUiExtensionId(id), title, new WebUiMountPath(mount),
        new WebUiClasspathResource(root));
    return () -> descriptor;
  }
}
