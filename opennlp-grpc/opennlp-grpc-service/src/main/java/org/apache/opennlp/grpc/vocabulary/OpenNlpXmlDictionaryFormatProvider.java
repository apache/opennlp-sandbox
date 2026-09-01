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

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.StringList;
import org.apache.opennlp.grpc.v1.DictionaryFormatDescriptor;
import org.apache.opennlp.grpc.v1.DictionaryFormatSelector;
import org.apache.opennlp.grpc.v1.StandardDictionaryFormat;
import org.apache.opennlp.grpc.spi.vocabulary.DictionaryFormatProvider;
import org.apache.opennlp.grpc.spi.vocabulary.DictionaryEntryConsumer;

/** Built-in provider for OpenNLP's serialized XML dictionary representation. */
public final class OpenNlpXmlDictionaryFormatProvider implements DictionaryFormatProvider {

  /** Public no-arg constructor required by {@link java.util.ServiceLoader}. */
  public OpenNlpXmlDictionaryFormatProvider() {
  }

  /** {@inheritDoc} */
  @Override
  public DictionaryFormatDescriptor descriptor() {
    return DictionaryFormatDescriptor.newBuilder()
        .setFormat(DictionaryFormatSelector.newBuilder().setStandard(
            StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_OPENNLP_XML))
        .setDisplayName("OpenNLP dictionary XML")
        .addMediaTypes("application/xml")
        .addMediaTypes("text/xml")
        .setSupportsMultiWordEntries(true)
        .build();
  }

  /** {@inheritDoc} */
  @Override
  public void read(InputStream input, DictionaryEntryConsumer entries) throws IOException {
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    if (entries == null) {
      throw new IllegalArgumentException("entries must not be null");
    }
    final Dictionary dictionary = new Dictionary(input);
    for (StringList tokens : dictionary) {
      final StringBuilder headword = new StringBuilder();
      for (int i = 0; i < tokens.size(); i++) {
        if (i > 0) {
          headword.append(' ');
        }
        headword.append(tokens.getToken(i));
      }
      entries.accept(headword.toString(), "");
    }
  }
}
