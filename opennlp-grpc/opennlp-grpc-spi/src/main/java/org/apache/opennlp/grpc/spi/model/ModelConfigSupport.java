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
package org.apache.opennlp.grpc.spi.model;

import opennlp.tools.util.StringUtil;
import org.apache.opennlp.grpc.spi.AnalysisException;

/**
 * Shared parsing helpers for {@code model.*} configuration entries, used by backend factories
 * and by the server's registries so both sides canonicalize identifiers identically.
 */
public final class ModelConfigSupport {

  private ModelConfigSupport() {
  }

  /**
   * Canonicalizes a configured identifier (a model id, entity type, or backend id) by trimming
   * surrounding whitespace and lower-casing it.
   *
   * @param value The raw identifier; may be {@code null}.
   *
   * @return The canonical identifier, or {@code null} when {@code value} is {@code null}.
   */
  public static String normalize(String value) {
    return value == null ? null : StringUtil.toLowerCase(value.trim());
  }

  /**
   * Parses an optional integer priority from configuration.
   *
   * @param key The configuration key, for error messages.
   * @param rawValue The configured value; {@code null} or blank yields {@code 0}.
   *
   * @return The parsed priority, or {@code 0} when unset.
   * @throws AnalysisException {@code INVALID_ARGUMENT} if {@code rawValue} is not an integer.
   */
  public static int parsePriority(String key, String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(rawValue.trim());
    } catch (NumberFormatException e) {
      throw AnalysisException.invalidArgument(
          key + " must be an integer, was '" + rawValue + "'");
    }
  }
}
