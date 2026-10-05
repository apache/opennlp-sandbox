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
package org.apache.opennlp.grpc.spi.vocabulary;

import java.io.IOException;
import java.io.InputStream;

import org.apache.opennlp.grpc.v1.DictionaryFormatDescriptor;

/**
 * Service provider interface for one encoded dictionary input format.
 *
 * <p>Implementations are discovered once at server startup through {@link java.util.ServiceLoader}.
 * A provider jar registers its implementation in
 * {@code META-INF/services/org.apache.opennlp.grpc.spi.vocabulary.DictionaryFormatProvider}.</p>
 */
public interface DictionaryFormatProvider {

  /**
   * Describes the stable wire selector and user-facing capabilities of this format.
   *
   * @return Immutable non-null descriptor.
   */
  DictionaryFormatDescriptor descriptor();

  /**
   * Decodes the complete bounded input and emits canonical entries in source order.
   *
   * @param input Encoded dictionary bytes. Must not be {@code null}; the caller closes it.
   * @param entries Entry destination. Must not be {@code null}.
   * @throws IOException If the input is malformed or cannot be read.
   * @throws IllegalArgumentException If either argument is {@code null}.
   */
  void read(InputStream input, DictionaryEntryConsumer entries) throws IOException;
}
