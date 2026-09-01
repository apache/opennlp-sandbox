/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import java.io.IOException;

/** A downloaded catalog file did not match its pinned size or SHA-256: the download is corrupt or the source changed. Nothing was published. */
public final class CatalogChecksumException extends IOException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the failure.
   *
   * @param message What failed, for the operator; must not be null.
   */
  public CatalogChecksumException(String message) {
    super(message);
  }

  /**
   * Creates the failure with its cause.
   *
   * @param message What failed, for the operator; must not be null.
   * @param cause The underlying failure.
   */
  public CatalogChecksumException(String message, Throwable cause) {
    super(message, cause);
  }
}
