/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.vocabulary;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Shared validation and strict UTF-8 decoding for built-in formats. */
final class DictionaryFormatSupport {

  /** Prevents utility class construction. */
  private DictionaryFormatSupport() {
  }

  /** Returns a reader that reports malformed or unmappable UTF-8 input. */
  static BufferedReader utf8Reader(InputStream input) {
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    final var decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    return new BufferedReader(new InputStreamReader(input, decoder));
  }

  /** Validates one canonical TSV cell. */
  static void requireCell(String value, String name, boolean allowEmpty) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    if (!allowEmpty && value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    if (value.indexOf('\t') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
      throw new IllegalArgumentException(name + " must not contain a tab or line break");
    }
  }
}
