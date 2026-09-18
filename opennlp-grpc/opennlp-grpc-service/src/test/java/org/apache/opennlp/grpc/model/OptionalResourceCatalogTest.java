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
package org.apache.opennlp.grpc.model;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.opennlp.grpc.v1.ConfiguredResource;
import org.apache.opennlp.grpc.v1.StandardResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies capability discovery for every configured non-model resource family. */
class OptionalResourceCatalogTest {

  @TempDir
  static Path workDir;

  private static Path affixPath;
  private static Path wordsPath;
  private static Path latticeDir;

  @BeforeAll
  static void writeDictionaryFixtures() throws IOException {
    affixPath = workDir.resolve("tiny.aff");
    wordsPath = workDir.resolve("tiny.dic");
    Files.writeString(affixPath, String.join("\n",
        "SET UTF-8",
        "SFX S Y 1",
        "SFX S 0 s ."));
    Files.writeString(wordsPath, String.join("\n", "2", "cat/S", "mat/S"));

    latticeDir = workDir.resolve("lattice");
    Files.createDirectory(latticeDir);
    Files.writeString(latticeDir.resolve("lexicon.csv"), String.join("\n",
        "東京,0,0,3000,noun,proper",
        "都,0,0,4000,noun,suffix",
        ""));
    Files.writeString(latticeDir.resolve("matrix.def"), "1 1\n0 0 0\n");
    Files.writeString(latticeDir.resolve("char.def"), String.join("\n",
        "DEFAULT 0 1 0",
        "KANJI 0 0 2",
        "",
        "0x4E00..0x9FFF KANJI",
        ""));
    Files.writeString(latticeDir.resolve("unk.def"), String.join("\n",
        "DEFAULT,0,0,10000,symbol,unknown",
        "KANJI,0,0,8000,noun,unknown",
        ""));
  }

  @Test
  void catalogsEveryLoadedResourceWithTypedIdentityAndImplicitDefault() {
    final ModelBundleCache cache = new ModelBundleCache(Map.of(
        "model.subword.tiny.path", resourcePath("/subword/tiny-unigram-bytefb.model"),
        "model.hunspell.tiny.affix_path", affixPath.toString(),
        "model.hunspell.tiny.dictionary_path", wordsPath.toString(),
        "model.wordnet.mini.path", resourcePath("/wordnet/mini-wn-lmf.xml"),
        "model.lattice.mini.dir", latticeDir.toString()));
    try {
      final var resources = cache.listConfiguredResources();
      assertEquals(4, resources.size());
      assertResource(resources.get(0), StandardResource.STANDARD_RESOURCE_SUBWORD_MODEL,
          "tiny", true);
      assertResource(resources.get(1), StandardResource.STANDARD_RESOURCE_HUNSPELL_DICTIONARY,
          "tiny", true);
      assertResource(resources.get(2), StandardResource.STANDARD_RESOURCE_WORDNET_LEXICON,
          "mini", true);
      assertResource(resources.get(3), StandardResource.STANDARD_RESOURCE_LATTICE_DICTIONARY,
          "mini", true);
    } finally {
      cache.close();
    }
  }

  @Test
  void severalResourcesWithoutAnOperatorDefaultAreAllAdvertisedAsExplicitOnly() {
    final String lexicon = resourcePath("/wordnet/mini-wn-lmf.xml");
    final ModelBundleCache cache = new ModelBundleCache(Map.of(
        "model.wordnet.beta.path", lexicon,
        "model.wordnet.alpha.path", lexicon));
    try {
      final var resources = cache.listConfiguredResources();
      assertEquals(2, resources.size());
      assertEquals("alpha", resources.get(0).getResourceId());
      assertEquals("beta", resources.get(1).getResourceId());
      assertTrue(resources.stream().allMatch(resource ->
          resource.getIdentity().getStandard()
              == StandardResource.STANDARD_RESOURCE_WORDNET_LEXICON));
      assertFalse(resources.stream().anyMatch(ConfiguredResource::getIsDefault));
    } finally {
      cache.close();
    }
  }

  private static void assertResource(
      ConfiguredResource resource, StandardResource type, String id, boolean isDefault) {
    assertEquals(type, resource.getIdentity().getStandard());
    assertEquals(id, resource.getResourceId());
    assertEquals(isDefault, resource.getIsDefault());
  }

  private static String resourcePath(String name) {
    try {
      return Path.of(OptionalResourceCatalogTest.class.getResource(name).toURI()).toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
