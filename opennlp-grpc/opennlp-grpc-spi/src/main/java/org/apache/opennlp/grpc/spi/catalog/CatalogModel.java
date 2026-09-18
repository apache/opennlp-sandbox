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
package org.apache.opennlp.grpc.spi.catalog;

import java.util.List;

import org.apache.opennlp.grpc.v1.ModelArtifactRole;
import org.apache.opennlp.grpc.v1.ModelCatalogDescriptor;

/**
 * One catalog entry: the public immutable descriptor paired with its download files.
 *
 * @param descriptor The public catalog descriptor. Must not be {@code null}.
 * @param files The immutable, checksum-pinned files of the entry. Must not be empty.
 */
public record CatalogModel(ModelCatalogDescriptor descriptor, List<CatalogFile> files) {

  /**
   * Validates the public descriptor against its immutable file list.
   *
   * @throws IllegalArgumentException If the descriptor or file list is invalid.
   */
  public CatalogModel {
    if (descriptor == null) {
      throw new IllegalArgumentException("descriptor must not be null");
    }
    if (descriptor.getRole() == ModelArtifactRole.MODEL_ARTIFACT_ROLE_UNSPECIFIED) {
      throw new IllegalArgumentException("descriptor role must be specified");
    }
    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException("files must not be null or empty");
    }
    files = List.copyOf(files);
    long total = 0;
    for (CatalogFile file : files) {
      total = Math.addExact(total, file.byteSize());
    }
    if (descriptor.getByteSize() != total) {
      throw new IllegalArgumentException("descriptor byte_size does not match catalog files");
    }
  }
}
