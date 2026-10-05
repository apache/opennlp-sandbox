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
 * Validated, immutable metadata for a {@link WebUiExtension}.
 *
 * @param id A stable identifier used to distinguish the extension from other providers.
 * @param title A human-readable title for navigation and diagnostics.
 * @param mountPath The absolute HTTP path below which the host exposes the extension.
 * @param resourceRoot The classpath directory containing the extension's static assets.
 */
public record WebUiExtensionDescriptor(
    WebUiExtensionId id,
    String title,
    WebUiMountPath mountPath,
    WebUiClasspathResource resourceRoot) {

  /**
   * Validates every descriptor field at the provider boundary.
   *
   * @throws IllegalArgumentException If a field is {@code null} or the title is invalid.
   */
  public WebUiExtensionDescriptor {
    if (id == null) {
      throw new IllegalArgumentException("extension descriptor id must not be null");
    }
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("extension descriptor title must not be blank");
    }
    if (!title.equals(title.strip())) {
      throw new IllegalArgumentException(
          "extension descriptor title must not contain leading or trailing whitespace: '"
              + title + "'");
    }
    for (int i = 0; i < title.length(); i++) {
      if (Character.isISOControl(title.charAt(i))) {
        throw new IllegalArgumentException(
            "extension descriptor title must not contain control characters");
      }
    }
    if (mountPath == null) {
      throw new IllegalArgumentException("extension descriptor mountPath must not be null");
    }
    if (resourceRoot == null) {
      throw new IllegalArgumentException("extension descriptor resourceRoot must not be null");
    }
  }
}
