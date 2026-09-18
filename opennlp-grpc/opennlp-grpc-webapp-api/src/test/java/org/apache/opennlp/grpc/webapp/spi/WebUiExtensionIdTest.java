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

class WebUiExtensionIdTest {

  @Test
  void acceptsStableLowerCaseIdentifiers() {
    assertEquals("demo", new WebUiExtensionId("demo").value());
    assertEquals("demo-ui", new WebUiExtensionId("demo-ui").value());
    assertEquals("org.apache.opennlp.demo-ui",
        new WebUiExtensionId("org.apache.opennlp.demo-ui").value());
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("invalidIdentifiers")
  void rejectsIdentifiersThatAreNotStableTokens(String value, String reason) {
    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new WebUiExtensionId(value));
    assertTrue(exception.getMessage().contains("extension id"));
    assertTrue(exception.getMessage().contains(reason), exception::getMessage);
  }

  private static Stream<Arguments> invalidIdentifiers() {
    return Stream.of(
        Arguments.of(null, "must not be null"),
        Arguments.of("", "must not be blank"),
        Arguments.of("Demo", "lower-case ASCII"),
        Arguments.of("demo ui", "lower-case ASCII"),
        Arguments.of("-demo", "lower-case ASCII"),
        Arguments.of("demo-", "lower-case ASCII"),
        Arguments.of("demo..ui", "lower-case ASCII"),
        Arguments.of("demo/ui", "lower-case ASCII"));
  }
}
