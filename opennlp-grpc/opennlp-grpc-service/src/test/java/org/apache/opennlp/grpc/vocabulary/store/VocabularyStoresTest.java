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
import java.nio.file.Path;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.opennlp.grpc.spi.vocabulary.VocabularyStoreProvider;

class VocabularyStoresTest {

  @TempDir
  Path root;

  @Test
  void plainPathsOpenTheFilesystemStore() throws IOException {
    assertInstanceOf(FileSystemVocabularyStore.class,
        VocabularyStores.open(root.toString()));
  }

  @Test
  void fileUrisOpenTheFilesystemStore() throws IOException {
    assertInstanceOf(FileSystemVocabularyStore.class,
        VocabularyStores.open(root.toUri().toString()));
  }

  @Test
  void unknownSchemesFailLoudWithTheScheme() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> VocabularyStores.open("s3://bucket/vocabularies"));
    assertTrue(e.getMessage().contains("s3"), e.getMessage());
    assertTrue(e.getMessage().contains("provider"), e.getMessage());
  }

  @Test
  void theFilesystemProviderIsDiscoverable() {
    final long fileProviders = ServiceLoader.load(VocabularyStoreProvider.class).stream()
        .map(ServiceLoader.Provider::get)
        .filter(provider -> provider.scheme().equals("file"))
        .count();
    assertEquals(1, fileProviders);
  }
}
