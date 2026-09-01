/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
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
package org.apache.opennlp.grpc.vocabulary;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.v1.DictionaryArtifactDescriptor;
import org.apache.opennlp.grpc.v1.DictionaryFormatSelector;
import org.apache.opennlp.grpc.v1.ImportDictionaryStart;
import org.apache.opennlp.grpc.v1.LearnVocabularyStart;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.StandardDictionaryFormat;
import org.apache.opennlp.grpc.v1.VocabularyArtifactDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabularyArtifactStoreTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void importsLearnsPublishesAndReloadsImmutableArtifacts() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore store = enabledStore(formats, Map.of());
    final DictionaryArtifactDescriptor dictionary = store.importDictionary(
        importStart("Legal dictionary"),
        "HABEAS CORPUS\tA writ.\ndue process\tA protection.\n"
            .getBytes(StandardCharsets.UTF_8));
    assertEquals(List.of(dictionary), store.listDictionaries());

    final VocabularyArtifactDescriptor vocabulary = store.learnVocabulary(
        LearnVocabularyStart.newBuilder()
            .setDictionaryArtifactId(dictionary.getArtifactId())
            .setDisplayName("Legal vocabulary")
            .setMinFrequency(2)
            .setMaxTerms(20)
            .setProvenanceSummary("Authored test corpus")
            .build(),
        List.of(
            document("Due process protects liberty. Habeas corpus protects liberty."),
            document("Liberty and due process matter.")));

    assertEquals(4, vocabulary.getTermCount());
    assertEquals(List.of(vocabulary), store.listVocabularies());
    assertEquals(2, vocabulary.getDictionaryTermCount());
    assertEquals(2, vocabulary.getCorpusTermCount());
    assertEquals(64, vocabulary.getArtifactHash().length());
    final String tsv = new String(store.readVocabulary(vocabulary.getArtifactId()),
        StandardCharsets.UTF_8);
    assertTrue(tsv.contains("due process\t2\tdictionary"));
    assertTrue(tsv.contains("liberty\t3\tcorpus"));

    final VocabularyArtifactStore reloaded = enabledStore(formats, Map.of());
    assertEquals(dictionary, reloaded.requireDictionary(dictionary.getArtifactId()));
    assertEquals(vocabulary, reloaded.requireVocabulary(vocabulary.getArtifactId()));
    assertEquals(tsv, new String(reloaded.readVocabulary(vocabulary.getArtifactId()),
        StandardCharsets.UTF_8));
    try (var children = Files.list(temporaryDirectory)) {
      assertFalse(children.anyMatch(path ->
          path.getFileName().toString().startsWith(".staging-")));
    }
  }

  @Test
  void learnsCorpusOnlyVocabularyWithoutDictionary() throws Exception {
    final VocabularyArtifactStore store = enabledStore(DictionaryFormatRegistry.discover(), Map.of());

    final VocabularyArtifactDescriptor vocabulary = store.learnVocabulary(
        LearnVocabularyStart.newBuilder()
            .setDisplayName("Corpus vocabulary")
            .setMinFrequency(2)
            .setMaxTerms(20)
            .setProvenanceSummary("Pasted workflow corpus")
            .build(),
        List.of(
            document("Liberty protects people."),
            document("People value liberty.")));

    assertEquals("", vocabulary.getDictionaryArtifactId());
    assertEquals(0, vocabulary.getDictionaryTermCount());
    assertEquals(2, vocabulary.getCorpusTermCount());
    final String tsv = new String(store.readVocabulary(vocabulary.getArtifactId()),
        StandardCharsets.UTF_8);
    assertTrue(tsv.contains("liberty\t2\tcorpus"));
    assertTrue(tsv.contains("people\t2\tcorpus"));

    final VocabularyArtifactStore reloaded = enabledStore(
        DictionaryFormatRegistry.discover(), Map.of());
    assertEquals(vocabulary, reloaded.requireVocabulary(vocabulary.getArtifactId()));
  }

  @Test
  void rejectsWritesWithoutAnArtifactRoot() throws Exception {
    final VocabularyArtifactStore store = VocabularyArtifactStore.fromConfiguration(
        Map.of(), DictionaryFormatRegistry.discover());

    assertFalse(store.writesEnabled());
    assertThrows(IllegalStateException.class,
        () -> store.importDictionary(importStart("disabled"), new byte[] {1}));
  }

  @Test
  void enforcesInputAndLearningBoundsBeforePublishing() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore store = enabledStore(formats, Map.of(
        "vocabulary.max_dictionary_bytes", "16",
        "vocabulary.max_corpus_documents", "1",
        "vocabulary.max_corpus_bytes", "16",
        "vocabulary.max_vocabulary_terms", "10"));

    assertThrows(IllegalArgumentException.class,
        () -> store.importDictionary(importStart("large"), new byte[17]));
    final DictionaryArtifactDescriptor dictionary = store.importDictionary(
        importStart("small"), "term\tdefinition\n".getBytes(StandardCharsets.UTF_8));
    final LearnVocabularyStart start = LearnVocabularyStart.newBuilder()
        .setDictionaryArtifactId(dictionary.getArtifactId())
        .setDisplayName("bounded")
        .setMinFrequency(1)
        .setMaxTerms(10)
        .setProvenanceSummary("test")
        .build();
    assertThrows(IllegalArgumentException.class,
        () -> store.learnVocabulary(start, List.of(document("one"), document("two"))));
    assertThrows(IllegalArgumentException.class,
        () -> store.learnVocabulary(start, List.of(document("a text longer than sixteen"))));
  }

  @Test
  void refusesAVocabularyThatLearnedNoTerms() throws Exception {
    final VocabularyArtifactStore store = enabledStore(DictionaryFormatRegistry.discover(), Map.of());
    final LearnVocabularyStart start = LearnVocabularyStart.newBuilder()
        .setDisplayName("too strict")
        .setMinFrequency(999)
        .setMaxTerms(10)
        .setProvenanceSummary("test")
        .build();
    final IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> store.learnVocabulary(start, List.of(document("the quick brown fox"))));
    assertTrue(refused.getMessage().contains("999"), refused.getMessage());
    assertTrue(store.listVocabularies().isEmpty(), "no artifact is published");
  }

  @Test
  void enforcesCanonicalEntryLimitBeforePublishing() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore store = enabledStore(formats,
        Map.of("vocabulary.max_dictionary_entries", "1"));

    assertThrows(java.io.IOException.class, () -> store.importDictionary(
        importStart("too many"), "one\tfirst\ntwo\tsecond\n".getBytes(StandardCharsets.UTF_8)));
    // Kind directories appear on the first successful publication, so a rejected
    // import leaves the directory absent or empty, and never a partial artifact.
    final Path dictionaries = temporaryDirectory.resolve("dictionaries");
    if (Files.exists(dictionaries)) {
      try (var artifacts = Files.list(dictionaries)) {
        assertEquals(0, artifacts.count());
      }
    }
  }

  @Test
  void rejectsTamperedVocabularyBeforeOpeningOrReloading() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore store = enabledStore(formats, Map.of());
    final DictionaryArtifactDescriptor dictionary = store.importDictionary(
        importStart("Legal dictionary"), "liberty\tA right.\n".getBytes(StandardCharsets.UTF_8));
    final VocabularyArtifactDescriptor vocabulary = store.learnVocabulary(
        LearnVocabularyStart.newBuilder()
            .setDictionaryArtifactId(dictionary.getArtifactId())
            .setDisplayName("Legal vocabulary")
            .setMinFrequency(1)
            .setMaxTerms(20)
            .setProvenanceSummary("Authored test corpus")
            .build(),
        List.of(document("Liberty matters.")));
    final Path data = temporaryDirectory.resolve("vocabularies")
        .resolve(vocabulary.getArtifactId()).resolve("vocabulary.tsv");
    Files.writeString(data, "tampered\n", StandardCharsets.UTF_8,
        java.nio.file.StandardOpenOption.APPEND);

    assertThrows(java.io.IOException.class,
        () -> store.openVocabulary(vocabulary.getArtifactId()));
    assertThrows(java.io.IOException.class, () -> enabledStore(formats, Map.of()));
  }

  @Test
  void deletesPublishedVocabularyFromMemoryAndDurableStorage() throws Exception {
    final DictionaryFormatRegistry formats = DictionaryFormatRegistry.discover();
    final VocabularyArtifactStore store = enabledStore(formats, Map.of());
    final DictionaryArtifactDescriptor dictionary = store.importDictionary(
        importStart("Legal dictionary"), "liberty\tA right.\n".getBytes(StandardCharsets.UTF_8));
    final VocabularyArtifactDescriptor vocabulary = store.learnVocabulary(
        LearnVocabularyStart.newBuilder()
            .setDictionaryArtifactId(dictionary.getArtifactId())
            .setDisplayName("Legal vocabulary")
            .setMinFrequency(1)
            .setMaxTerms(20)
            .setProvenanceSummary("Authored test corpus")
            .build(),
        List.of(document("Liberty matters.")));

    assertTrue(store.deleteVocabulary(vocabulary.getArtifactId()));
    assertThrows(UnknownVocabularyArtifactException.class,
        () -> store.requireVocabulary(vocabulary.getArtifactId()));
    assertFalse(Files.exists(temporaryDirectory.resolve("vocabularies")
        .resolve(vocabulary.getArtifactId())));
    assertFalse(store.deleteVocabulary(vocabulary.getArtifactId()));
  }

  private VocabularyArtifactStore enabledStore(
      DictionaryFormatRegistry formats, Map<String, String> overrides) throws Exception {
    final java.util.HashMap<String, String> configuration = new java.util.HashMap<>(overrides);
    configuration.put("vocabulary.artifact_root", temporaryDirectory.toString());
    return VocabularyArtifactStore.fromConfiguration(configuration, formats);
  }

  private static ImportDictionaryStart importStart(String displayName) {
    return ImportDictionaryStart.newBuilder()
        .setFormat(DictionaryFormatSelector.newBuilder().setStandard(
            StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_HEADWORD_DEFINITION_TSV))
        .setDisplayName(displayName)
        .setProvenanceSummary("Authored fixture")
        .build();
  }

  private static OpenNlpDocument document(String text) {
    return OpenNlpDocument.newBuilder().setRawText(text).build();
  }
}
