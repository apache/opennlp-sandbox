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
package org.apache.opennlp.grpc.vocabulary;

import java.io.IOException;
import java.io.InputStream;

import org.apache.opennlp.grpc.v1.DictionaryFormatDescriptor;
import org.apache.opennlp.grpc.v1.DictionaryFormatSelector;
import org.apache.opennlp.grpc.v1.StandardDictionaryFormat;
import org.apache.opennlp.grpc.spi.vocabulary.DictionaryFormatProvider;
import org.apache.opennlp.grpc.spi.vocabulary.DictionaryEntryConsumer;

/** Built-in UTF-8 {@code HEADWORD<TAB>definition} dictionary provider. */
public final class HeadwordDefinitionTsvFormatProvider implements DictionaryFormatProvider {

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public HeadwordDefinitionTsvFormatProvider() {
  }

  /** {@inheritDoc} */
  @Override
  public DictionaryFormatDescriptor descriptor() {
    return DictionaryFormatDescriptor.newBuilder()
        .setFormat(DictionaryFormatSelector.newBuilder().setStandard(
            StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_HEADWORD_DEFINITION_TSV))
        .setDisplayName("Headword and definition TSV")
        .addMediaTypes("text/tab-separated-values")
        .setSupportsDefinitions(true)
        .setSupportsMultiWordEntries(true)
        .build();
  }

  /** {@inheritDoc} */
  @Override
  public void read(InputStream input, DictionaryEntryConsumer entries) throws IOException {
    if (entries == null) {
      throw new IllegalArgumentException("entries must not be null");
    }
    try (var reader = DictionaryFormatSupport.utf8Reader(input)) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }
        final int tab = line.indexOf('\t');
        if (tab < 1 || tab == line.length() - 1 || line.indexOf('\t', tab + 1) >= 0) {
          throw new IOException("Dictionary line " + lineNumber
              + " must contain exactly one tab between a headword and definition");
        }
        entries.accept(line.substring(0, tab), line.substring(tab + 1));
      }
    }
  }
}
