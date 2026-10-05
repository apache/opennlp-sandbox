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

class WebUiClasspathResourceTest {

  @Test
  void acceptsNormalizedAbsoluteClasspathLocations() {
    assertEquals("/index.html", new WebUiClasspathResource("/index.html").value());
    assertEquals("/META-INF/opennlp-grpc-ui/demo",
        new WebUiClasspathResource("/META-INF/opennlp-grpc-ui/demo").value());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("invalidLocations")
  void rejectsUnsafeOrNonNormalizedLocations(String value, String reason) {
    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new WebUiClasspathResource(value));
    assertTrue(exception.getMessage().contains("classpath resource"), exception::getMessage);
    assertTrue(exception.getMessage().contains(reason), exception::getMessage);
  }

  private static Stream<Arguments> invalidLocations() {
    return Stream.of(
        Arguments.of(null, "must not be null"),
        Arguments.of("", "must not be blank"),
        Arguments.of("META-INF/ui", "must be absolute"),
        Arguments.of("/", "must identify a resource"),
        Arguments.of("/META-INF/ui/", "must not end with '/'"),
        Arguments.of("/META-INF//ui", "empty path segments"),
        Arguments.of("/META-INF/./ui", "'.' or '..'"),
        Arguments.of("/META-INF/../private", "'.' or '..'"),
        Arguments.of("/META-INF\\private", "backslashes"),
        Arguments.of("/META-INF/demo ui", "whitespace"),
        Arguments.of("/META-INF/%2e%2e/private", "percent-encoded"),
        Arguments.of("/META-INF/ui?raw=true", "query or fragment"));
  }
}
