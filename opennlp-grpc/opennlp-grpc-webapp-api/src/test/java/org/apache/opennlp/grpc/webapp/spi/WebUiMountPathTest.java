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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebUiMountPathTest {

  @Test
  void acceptsRootAndNormalizedAbsolutePaths() {
    assertEquals("/", new WebUiMountPath("/").value());
    assertEquals("/extensions/demo-ui", new WebUiMountPath("/extensions/demo-ui").value());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("invalidMountPaths")
  void rejectsInvalidMountPaths(String value, String reason) {
    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new WebUiMountPath(value));
    assertTrue(exception.getMessage().contains("mount path"), exception::getMessage);
    assertTrue(exception.getMessage().contains(reason), exception::getMessage);
  }

  private static Stream<Arguments> invalidMountPaths() {
    return Stream.of(
        Arguments.of(null, "must not be null"),
        Arguments.of("", "must not be blank"),
        Arguments.of("extensions/demo", "must be absolute"),
        Arguments.of("/extensions/demo/", "must not end with '/'"),
        Arguments.of("/extensions//demo", "empty path segments"),
        Arguments.of("/extensions/./demo", "'.' or '..'"),
        Arguments.of("/extensions/../api", "'.' or '..'"),
        Arguments.of("/extensions\\demo", "backslashes"),
        Arguments.of("/extensions/demo ui", "whitespace"),
        Arguments.of("/extensions/demo?mode=full", "query or fragment"),
        Arguments.of("/api", "reserved '/api' namespace"),
        Arguments.of("/api/models", "reserved '/api' namespace"),
        Arguments.of("/API/models", "reserved '/api' namespace"),
        Arguments.of("/extensions/%2e%2e/api", "percent-encoded"),
        Arguments.of("/extensions/demo%2Fadmin", "percent-encoded"),
        Arguments.of("/extensions/%5cdemo", "percent-encoded"));
  }
}
