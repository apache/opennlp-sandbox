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

import java.nio.charset.StandardCharsets;

record WebHttpResponse(int status, String contentType, byte[] body) {

  /**
   * Validates and copies one HTTP response.
   *
   * @throws IllegalArgumentException If the status is invalid or an argument is {@code null}.
   */
  WebHttpResponse {
    if (status < 100 || status > 599) {
      throw new IllegalArgumentException("HTTP status must be between 100 and 599: " + status);
    }
    if (contentType == null) {
      throw new IllegalArgumentException("contentType must not be null");
    }
    if (body == null) {
      throw new IllegalArgumentException("body must not be null");
    }
    body = body.clone();
  }

  /**
   * Returns a defensive copy of the response body.
   *
   * @return The copied body.
   */
  @Override
  public byte[] body() {
    return body.clone();
  }

  /**
   * Decodes the response body as UTF-8.
   *
   * @return The decoded body.
   */
  String bodyUtf8() {
    return new String(body, StandardCharsets.UTF_8);
  }

  /**
   * Creates a response from a UTF-8 string.
   *
   * @param status The HTTP status.
   * @param contentType The response content type.
   * @param body The response body.
   * @return The encoded response.
   */
  static WebHttpResponse utf8(int status, String contentType, String body) {
    return new WebHttpResponse(status, contentType, body.getBytes(StandardCharsets.UTF_8));
  }
}
