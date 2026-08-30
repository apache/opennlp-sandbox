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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.opennlp.grpc.v1.DictionaryFormatDescriptor;
import org.apache.opennlp.grpc.v1.DictionaryFormatSelector;
import org.apache.opennlp.grpc.v1.StandardDictionaryFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.opennlp.grpc.spi.vocabulary.DictionaryFormatProvider;
import org.apache.opennlp.grpc.spi.vocabulary.DictionaryEntryConsumer;

class DictionaryFormatRegistryTest {

  @Test
  void discoversTheThreeBuiltInFormatsThroughServiceLoader() {
    final DictionaryFormatRegistry registry = DictionaryFormatRegistry.discover();

    assertEquals(List.of(
        StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_HEADWORD_DEFINITION_TSV,
        StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_HEADWORD_LINES,
        StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_OPENNLP_XML),
        registry.descriptors().stream()
            .map(descriptor -> descriptor.getFormat().getStandard())
            .toList());
  }

  @Test
  void parsesDefinitionsLinesAndOpenNlpXmlToTheSameCanonicalShape() throws Exception {
    final DictionaryFormatRegistry registry = DictionaryFormatRegistry.discover();

    assertEquals(List.of(
        new DictionaryEntryData("HABEAS CORPUS", "A writ."),
        new DictionaryEntryData("due process", "A protection.")),
        read(registry.require(standard(
                StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_HEADWORD_DEFINITION_TSV)),
            "HABEAS CORPUS\tA writ.\ndue process\tA protection.\n"));
    assertEquals(List.of(
        new DictionaryEntryData("HABEAS CORPUS", ""),
        new DictionaryEntryData("due process", "")),
        read(registry.require(standard(
                StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_HEADWORD_LINES)),
            "HABEAS CORPUS\ndue process\n"));
    assertEquals(List.of(new DictionaryEntryData("habeas corpus", "")),
        read(registry.require(standard(
                StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_OPENNLP_XML)),
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<dictionary case_sensitive=\"false\"><entry>"
                + "<token>habeas</token><token>corpus</token></entry></dictionary>"));
  }

  @Test
  void rejectsDuplicateProviderIdsAndUnspecifiedSelectors() {
    final DictionaryFormatProvider first = provider("custom-format");
    final DictionaryFormatProvider duplicate = provider("custom-format");

    assertThrows(IllegalArgumentException.class,
        () -> new DictionaryFormatRegistry(List.of(first, duplicate)));
    final DictionaryFormatRegistry registry = new DictionaryFormatRegistry(List.of(first));
    assertThrows(IllegalArgumentException.class,
        () -> registry.require(DictionaryFormatSelector.getDefaultInstance()));
  }

  @Test
  void rejectsMalformedUtf8AndXmlDocumentTypes() {
    final DictionaryFormatRegistry registry = DictionaryFormatRegistry.discover();
    final DictionaryFormatProvider lines = registry.require(standard(
        StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_HEADWORD_LINES));
    assertThrows(IOException.class,
        () -> lines.read(new ByteArrayInputStream(new byte[] {(byte) 0xc3, 0x28}),
            (headword, definition) -> { }));

    final DictionaryFormatProvider xml = registry.require(standard(
        StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_OPENNLP_XML));
    final String documentType = "<?xml version=\"1.0\"?>"
        + "<!DOCTYPE dictionary [<!ENTITY injected \"unsafe\">]>"
        + "<dictionary case_sensitive=\"false\"><entry>"
        + "<token>&injected;</token></entry></dictionary>";
    assertThrows(IOException.class,
        () -> xml.read(new ByteArrayInputStream(documentType.getBytes(StandardCharsets.UTF_8)),
            (headword, definition) -> { }));
  }

  private static DictionaryFormatProvider provider(String id) {
    return new DictionaryFormatProvider() {
      @Override
      public DictionaryFormatDescriptor descriptor() {
        return DictionaryFormatDescriptor.newBuilder()
            .setFormat(DictionaryFormatSelector.newBuilder().setCustom(id))
            .setDisplayName(id)
            .addMediaTypes("text/plain")
            .setSupportsMultiWordEntries(true)
            .build();
      }

      @Override
      public void read(java.io.InputStream input, DictionaryEntryConsumer entries) {
      }
    };
  }

  private static DictionaryFormatSelector standard(StandardDictionaryFormat format) {
    return DictionaryFormatSelector.newBuilder().setStandard(format).build();
  }

  private static List<DictionaryEntryData> read(
      DictionaryFormatProvider provider, String content) throws IOException {
    final List<DictionaryEntryData> entries = new ArrayList<>();
    provider.read(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
        (headword, definition) -> entries.add(new DictionaryEntryData(headword, definition)));
    assertTrue(entries.stream().noneMatch(entry -> entry.headword().isBlank()));
    return entries;
  }
}
