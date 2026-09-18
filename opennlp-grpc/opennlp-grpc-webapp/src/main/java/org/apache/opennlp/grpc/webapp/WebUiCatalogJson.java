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

import java.util.Comparator;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import io.grpc.Status;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtension;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionDescriptor;

final class WebUiCatalogJson {

  private final WebHttpResponse response;

  /**
   * Creates a stable JSON view of the discovered UI extensions.
   *
   * @param registry The validated UI extension registry.
   * @throws IllegalArgumentException If {@code registry} is {@code null}.
   */
  WebUiCatalogJson(WebUiExtensionRegistry registry) {
    if (registry == null) {
      throw new IllegalArgumentException("registry must not be null");
    }
    this.response = encode(registry);
  }

  /**
   * Handles a request for the immutable extension catalog.
   *
   * @param method The HTTP request method.
   * @return The JSON catalog or a method-not-allowed response.
   * @throws IllegalArgumentException If {@code method} is {@code null}.
   */
  WebHttpResponse handle(String method) {
    if (method == null) {
      throw new IllegalArgumentException("method must not be null");
    }
    if (!method.equals("GET")) {
      return GrpcJsonApi.error(405, Status.Code.UNIMPLEMENTED,
          "HTTP method is not allowed for this endpoint");
    }
    return response;
  }

  /**
   * Encodes the registry as a JSON response.
   *
   * @param registry The validated UI extension registry.
   * @return The encoded response.
   */
  private WebHttpResponse encode(WebUiExtensionRegistry registry) {
    ListValue.Builder extensions = ListValue.newBuilder();
    registry.extensions().stream()
        .sorted(Comparator
            .comparing((WebUiExtension extension) ->
                extension.descriptor().mountPath().value())
            .thenComparing(extension -> extension.descriptor().id().value()))
        .map(this::extensionValue)
        .forEach(extensions::addValues);
    Struct catalog = Struct.newBuilder()
        .putFields("extensions", Value.newBuilder().setListValue(extensions).build())
        .build();
    try {
      return WebHttpResponse.utf8(200, GrpcJsonApi.JSON_CONTENT_TYPE,
          JsonFormat.printer().print(catalog));
    } catch (InvalidProtocolBufferException exception) {
      throw new IllegalStateException("Could not encode the UI extension catalog", exception);
    }
  }

  /**
   * Encodes one extension descriptor.
   *
   * @param extension The extension to encode.
   * @return The encoded descriptor.
   */
  private Value extensionValue(WebUiExtension extension) {
    WebUiExtensionDescriptor descriptor = extension.descriptor();
    Struct value = Struct.newBuilder()
        .putFields("id", stringValue(descriptor.id().value()))
        .putFields("title", stringValue(descriptor.title()))
        .putFields("mountPath", stringValue(descriptor.mountPath().value()))
        .build();
    return Value.newBuilder().setStructValue(value).build();
  }

  /**
   * Encodes one string field.
   *
   * @param value The field value.
   * @return The protobuf JSON value.
   */
  private Value stringValue(String value) {
    return Value.newBuilder().setStringValue(value).build();
  }
}
