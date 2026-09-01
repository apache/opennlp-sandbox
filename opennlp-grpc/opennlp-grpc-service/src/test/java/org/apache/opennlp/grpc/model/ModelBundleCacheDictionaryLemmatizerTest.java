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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the dictionary-backed lemmatizer configured through
 * {@code model.lemmatizer.dictionary}: a tab-separated {@code word<TAB>postag<TAB>lemma}
 * file served instead of the statistical lemmatizer model.
 */
class ModelBundleCacheDictionaryLemmatizerTest {

  @TempDir
  private Path dir;

  @Test
  void dictionaryLemmatizerServesConfiguredEntries() throws IOException {
    // A nonsense surface form pins that the lemma comes from the dictionary, not from the
    // bundled statistical model's learned rules.
    final Path dictionary = Files.write(dir.resolve("lemmas.tsv"),
        List.of("zzqx\tNOUN\tbanana"));
    try (ModelBundleCache cache = new ModelBundleCache(
        Map.of("model.lemmatizer.dictionary", dictionary.toString()))) {
      assertArrayEquals(new String[] {"banana"},
          cache.getLemmatizer().lemmatize(new String[] {"zzqx"}, new String[] {"NOUN"}));
    }
  }

  @Test
  void bothLemmatizerSourcesFailLoud() throws IOException {
    final Path dictionary = Files.write(dir.resolve("lemmas.tsv"),
        List.of("zzqx\tNOUN\tbanana"));
    final AnalysisException e = assertThrows(AnalysisException.class,
        () -> new ModelBundleCache(Map.of(
            "model.lemmatizer.dictionary", dictionary.toString(),
            "model.lemmatizer.path", dir.resolve("model.bin").toString())));
    assertTrue(e.getMessage().contains("mutually exclusive"));
  }

  @Test
  void missingDictionaryFileFailsLoud() {
    assertThrows(AnalysisException.class, () -> new ModelBundleCache(
        Map.of("model.lemmatizer.dictionary", dir.resolve("absent.tsv").toString())));
  }
}
