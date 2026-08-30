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

import java.util.Map;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link WordNetRegistry} configuration-key validation: a malformed id in a
 * {@code model.wordnet.<id>.path} key must fail startup naming the key instead of silently
 * dropping the entry.
 */
class WordNetRegistryTest {

  @Test
  void dottedLexiconIdIsRejectedNamingTheKey() {
    final String key = "model.wordnet.my.lexicon.path";
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> WordNetRegistry.create(Map.of(key, "/tmp/whatever.xml")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains(key),
        "the error must name the offending key: " + error.getMessage());
  }

  @Test
  void blankLexiconIdIsRejectedNamingTheKey() {
    final String key = "model.wordnet..path";
    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> WordNetRegistry.create(Map.of(key, "/tmp/whatever.xml")));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains(key),
        "the error must name the offending key: " + error.getMessage());
  }

  @Test
  void loadsAGzippedLexicon(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
      throws Exception {
    final java.nio.file.Path plain = java.nio.file.Path.of(
        WordNetRegistryTest.class.getResource("/wordnet/mini-wn-lmf.xml").toURI());
    final java.nio.file.Path gzipped = directory.resolve("mini-wn-lmf.xml.gz");
    try (var out = new java.util.zip.GZIPOutputStream(java.nio.file.Files.newOutputStream(gzipped))) {
      java.nio.file.Files.copy(plain, out);
    }

    final WordNetRegistry registry = WordNetRegistry.create(
        Map.of("model.wordnet.mini.path", gzipped.toString()));

    assertEquals(java.util.Set.of("mini"), registry.lexiconIds());
  }
}
