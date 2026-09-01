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
package org.apache.opennlp.grpc.webapp;

record WebUiAsset(String contentType, byte[] content) {

  /**
   * Validates and copies one static asset.
   *
   * @throws IllegalArgumentException If an argument is {@code null}.
   */
  WebUiAsset {
    if (contentType == null) {
      throw new IllegalArgumentException("contentType must not be null");
    }
    if (content == null) {
      throw new IllegalArgumentException("content must not be null");
    }
    content = content.clone();
  }

  /**
   * Returns a defensive copy of the asset content.
   *
   * @return The copied content.
   */
  @Override
  public byte[] content() {
    return content.clone();
  }
}
