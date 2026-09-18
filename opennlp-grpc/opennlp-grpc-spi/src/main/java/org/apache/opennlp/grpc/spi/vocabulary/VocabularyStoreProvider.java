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
import java.net.URI;

/**
 * ServiceLoader contract for {@link VocabularyStore} implementations, keyed by the
 * scheme of the configured artifact root. The filesystem provider ships with the
 * service; a durable remote tier such as S3 registers its own provider from its own
 * JAR, so the service never grows a cloud dependency.
 */
public interface VocabularyStoreProvider {

  /**
   * {@return the lowercase URI scheme this provider serves, for example {@code file}}
   */
  String scheme();

  /**
   * Opens the store rooted at the given location.
   *
   * @param root The artifact root as an absolute URI in this provider's scheme.
   * @return The opened store. Never {@code null}.
   * @throws IOException Thrown if the root cannot be created or verified.
   * @throws IllegalArgumentException Thrown if the URI does not fit the scheme.
   */
  VocabularyStore open(URI root) throws IOException;
}
