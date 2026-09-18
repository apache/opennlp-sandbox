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
 * A normalized absolute HTTP mount path owned by a web user interface extension.
 *
 * <p>The root path is permitted for a default user interface. The {@code /api} namespace is
 * reserved for the host. Paths containing traversal segments, encoded characters, backslashes,
 * empty segments, a query, or a fragment are rejected.</p>
 *
 * @param value The absolute mount path.
 */
public record WebUiMountPath(String value) {

  /**
   * Validates the mount path.
   *
   * @throws IllegalArgumentException If {@code value} is not a permitted mount path.
   */
  public WebUiMountPath {
    WebUiPathValidation.validateAbsolutePath(value, "mount path", true);
    if (isApiNamespace(value)) {
      throw new IllegalArgumentException(
          "mount path '" + value + "' must not use the reserved '/api' namespace");
    }
  }

  /** Returns whether a path uses the host's reserved API namespace. */
  private static boolean isApiNamespace(String value) {
    return value.length() >= 4
        && value.regionMatches(true, 0, "/api", 0, 4)
        && (value.length() == 4 || value.charAt(4) == '/');
  }
}
