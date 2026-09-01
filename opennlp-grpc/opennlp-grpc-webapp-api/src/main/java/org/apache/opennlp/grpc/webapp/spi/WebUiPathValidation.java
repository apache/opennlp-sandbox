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

/** Shared validation for paths supplied by extensions. */
final class WebUiPathValidation {

  /** Prevents instantiation. */
  private WebUiPathValidation() {
  }

  /**
   * Validates one absolute, normalized path.
   *
   * @param value The path value.
   * @param label The argument label used in error messages.
   * @param allowRoot Whether {@code /} is permitted.
   * @throws IllegalArgumentException If an argument or path is invalid.
   */
  static void validateAbsolutePath(String value, String label, boolean allowRoot) {
    if (value == null) {
      throw new IllegalArgumentException(label + " must not be null");
    }
    if (value.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    if (value.charAt(0) != '/') {
      throw new IllegalArgumentException(label + " '" + value + "' must be absolute");
    }
    if (!allowRoot && value.equals("/")) {
      throw new IllegalArgumentException(label + " '/' must identify a resource below root");
    }
    if (value.length() > 1 && value.endsWith("/")) {
      throw new IllegalArgumentException(label + " '" + value + "' must not end with '/'");
    }
    if (value.indexOf('\\') >= 0) {
      throw new IllegalArgumentException(label + " '" + value + "' must not contain backslashes");
    }
    if (value.indexOf('?') >= 0 || value.indexOf('#') >= 0) {
      throw new IllegalArgumentException(
          label + " '" + value + "' must not contain a query or fragment");
    }
    if (value.indexOf('%') >= 0) {
      throw new IllegalArgumentException(
          label + " '" + value + "' must not contain percent-encoded characters");
    }
    for (int i = 0; i < value.length(); i++) {
      final char character = value.charAt(i);
      if (Character.isISOControl(character)) {
        throw new IllegalArgumentException(label + " must not contain control characters");
      }
      if (Character.isWhitespace(character)) {
        throw new IllegalArgumentException(
            label + " '" + value + "' must not contain whitespace");
      }
    }
    if (value.length() == 1) {
      return;
    }
    int segmentStart = 1;
    for (int i = 1; i <= value.length(); i++) {
      if (i == value.length() || value.charAt(i) == '/') {
        if (i == segmentStart) {
          throw new IllegalArgumentException(
              label + " '" + value + "' must not contain empty path segments");
        }
        final String segment = value.substring(segmentStart, i);
        if (segment.equals(".") || segment.equals("..")) {
          throw new IllegalArgumentException(
              label + " '" + value + "' must not contain '.' or '..' path segments");
        }
        segmentStart = i + 1;
      }
    }
  }
}
