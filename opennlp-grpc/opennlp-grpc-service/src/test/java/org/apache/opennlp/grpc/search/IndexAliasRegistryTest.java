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
package org.apache.opennlp.grpc.search;

import java.nio.file.Path;
import java.util.List;

import org.apache.opennlp.grpc.v1.IndexAlias;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexAliasRegistryTest {

  @Test
  void resolvesAliasesAndPassesPlainIdsThrough() {
    final IndexAliasRegistry registry = IndexAliasRegistry.inMemory();
    registry.set("legal-current", "workspace-1");

    assertEquals("workspace-1", registry.resolve("legal-current"));
    assertEquals("workspace-2", registry.resolve("workspace-2"));
    assertTrue(registry.isAlias("legal-current"));
    assertFalse(registry.isAlias("workspace-1"));
  }

  @Test
  void repointsListsAndDeletesInStableOrder() {
    final IndexAliasRegistry registry = IndexAliasRegistry.inMemory();
    registry.set("zeta", "workspace-1");
    registry.set("alpha", "workspace-2");
    registry.set("zeta", "workspace-3");

    assertEquals(List.of("alpha", "zeta"), registry.aliases().stream()
        .map(IndexAlias::getAlias).toList());
    assertEquals("workspace-3", registry.resolve("zeta"));
    assertTrue(registry.delete("zeta"));
    assertFalse(registry.delete("zeta"));
    assertEquals("zeta", registry.resolve("zeta"));
  }

  @Test
  void persistsAliasesAcrossInstances(@TempDir Path root) {
    final Path file = root.resolve(IndexAliasRegistry.ALIASES_FILE);
    final IndexAliasRegistry first = IndexAliasRegistry.at(file);
    first.set("legal-current", "workspace-1");
    first.set("removed", "workspace-2");
    first.delete("removed");

    final IndexAliasRegistry second = IndexAliasRegistry.at(file);

    assertEquals("workspace-1", second.resolve("legal-current"));
    assertEquals(1, second.aliases().size());
  }

  @Test
  void rejectsUnstableNames() {
    final IndexAliasRegistry registry = IndexAliasRegistry.inMemory();

    assertThrows(IllegalArgumentException.class, () -> registry.set("Bad Alias", "workspace-1"));
    assertThrows(IllegalArgumentException.class, () -> registry.set("alias", " padded "));
    assertThrows(IllegalArgumentException.class, () -> registry.deleteByIndex(null));
    assertThrows(IllegalArgumentException.class, () -> registry.deleteByIndex("bad index"));
  }
}
