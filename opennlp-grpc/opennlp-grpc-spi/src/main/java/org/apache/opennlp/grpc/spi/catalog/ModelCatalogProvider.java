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
package org.apache.opennlp.grpc.spi.catalog;

import java.util.List;

/**
 * Contributes installable model catalog entries, discovered via
 * {@link java.util.ServiceLoader}.
 *
 * <p>Each entry is immutable metadata only: a public descriptor plus the
 * checksum-pinned files to download. Providers never ship model bytes.
 * Catalog ids must be unique across all discovered providers; the server
 * rejects duplicates at startup. Thread safety is implementation specific.</p>
 */
public interface ModelCatalogProvider {

  /**
   * Lists this provider's catalog entries.
   *
   * @return All entries in stable catalog-id order. Never {@code null}.
   */
  List<CatalogModel> models();
}
