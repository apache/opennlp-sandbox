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
package org.apache.opennlp.grpc.embedding;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the default method contracts of {@link EmbeddingProvider}.
 */
class EmbeddingProviderDefaultsTest {

  private final EmbeddingProvider provider = new StubEmbeddingProvider(Map.of("minilm", 4));

  @Test
  void embedBatchMatchesPerTextEmbedInInputOrder() {
    final List<String> texts = List.of("First sentence.", "Second sentence.", "Third one.");

    final List<float[]> batch = provider.embedBatch("minilm", texts);

    assertEquals(texts.size(), batch.size());
    for (int i = 0; i < texts.size(); i++) {
      assertArrayEquals(provider.embed("minilm", texts.get(i)), batch.get(i));
    }
  }

  @Test
  void embedBatchOfEmptyListReturnsEmptyList() {
    assertTrue(provider.embedBatch("minilm", List.of()).isEmpty());
  }

  @Test
  void embedBatchRejectsNullTexts() {
    assertThrows(NullPointerException.class, () -> provider.embedBatch("minilm", null));
  }
}
