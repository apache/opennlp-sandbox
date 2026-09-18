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

/**
 * A stable identifier for a web user interface extension.
 *
 * <p>An identifier consists of lower-case ASCII letters and digits separated by single hyphens
 * or dots. This form is safe for configuration keys and diagnostics while permitting both short
 * names and reverse-domain-style names.</p>
 *
 * @param value The identifier text.
 */
public record WebUiExtensionId(String value) {

  /**
   * Validates the identifier.
   *
   * @throws IllegalArgumentException If {@code value} is not a valid extension identifier.
   */
  public WebUiExtensionId {
    if (value == null) {
      throw new IllegalArgumentException("extension id must not be null");
    }
    if (value.isBlank()) {
      throw new IllegalArgumentException("extension id must not be blank");
    }
    if (!isValid(value)) {
      throw new IllegalArgumentException(
          "extension id '" + value + "' must contain lower-case ASCII letters or digits "
              + "separated by single hyphens or dots");
    }
  }

  /** Returns whether a value follows the extension identifier grammar. */
  private static boolean isValid(String value) {
    boolean previousWasSeparator = true;
    for (int i = 0; i < value.length(); i++) {
      final char character = value.charAt(i);
      final boolean isLetter = character >= 'a' && character <= 'z';
      final boolean isDigit = character >= '0' && character <= '9';
      final boolean isSeparator = character == '-' || character == '.';
      if ((!isLetter && !isDigit && !isSeparator) || (isSeparator && previousWasSeparator)) {
        return false;
      }
      previousWasSeparator = isSeparator;
    }
    return !previousWasSeparator;
  }
}
