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
 * A normalized absolute location in an extension's classpath.
 *
 * <p>The leading slash makes the location independent of the provider class's package. Locations
 * containing traversal segments, encoded characters, backslashes, empty segments, a query, or a
 * fragment are rejected. A location identifies either an asset root or one concrete resource,
 * depending on the descriptor field in which it is used.</p>
 *
 * @param value The absolute classpath location.
 */
public record WebUiClasspathResource(String value) {

  /**
   * Validates the classpath location.
   *
   * @throws IllegalArgumentException If {@code value} is not a normalized absolute resource path.
   */
  public WebUiClasspathResource {
    WebUiPathValidation.validateAbsolutePath(value, "classpath resource", false);
  }
}
