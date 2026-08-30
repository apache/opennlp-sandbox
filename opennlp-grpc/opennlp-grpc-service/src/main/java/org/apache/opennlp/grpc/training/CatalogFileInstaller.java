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

import org.apache.opennlp.grpc.spi.catalog.CatalogFile;
import java.io.IOException;
import java.nio.file.Path;

/** Testable seam around one bounded, verified catalog-file download. */
@FunctionalInterface
interface CatalogFileInstaller {

  /**
   * Installs a catalog file at the exact requested target.
   *
   * @param file Immutable catalog file identity.
   * @param target Exact output path.
   * @throws IOException If the file cannot be installed.
   */
  void install(CatalogFile file, Path target) throws IOException;
}
