/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.vocabulary.store;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import org.apache.opennlp.grpc.spi.vocabulary.VocabularyStoreProvider;
import org.apache.opennlp.grpc.spi.vocabulary.VocabularyStore;

/** The {@link VocabularyStoreProvider} for {@code file} URIs. */
public final class FileSystemVocabularyStoreProvider implements VocabularyStoreProvider {

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public FileSystemVocabularyStoreProvider() {
  }

  /** {@inheritDoc} */
  @Override
  public String scheme() {
    return "file";
  }

  /** {@inheritDoc} */
  @Override
  public VocabularyStore open(URI root) throws IOException {
    if (root == null) {
      throw new IllegalArgumentException("root must not be null");
    }
    return new FileSystemVocabularyStore(Path.of(root));
  }
}
