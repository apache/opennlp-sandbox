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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.model;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

/** Verifies that the thread-safe OpenNLP decoders are shared across analysis workers. */
class ModelBundleCacheConcurrencyTest {

  @Test
  void sharesThreadSafeDecodersAcrossWorkers() throws Exception {
    final ModelBundleCache cache = new ModelBundleCache(Map.of());
    try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
      assertSame(cache.getSentenceDetector(), worker.submit(cache::getSentenceDetector).get());
      assertSame(cache.getTokenizer(), worker.submit(cache::getTokenizer).get());
      assertSame(cache.getPosTagger(), worker.submit(cache::getPosTagger).get());
      assertSame(cache.getLemmatizer(), worker.submit(cache::getLemmatizer).get());
      assertSame(cache.getLanguageDetector(), worker.submit(cache::getLanguageDetector).get());
    } finally {
      cache.close();
    }
  }

  @Test
  void clearsDecoderStateForTheCallingWorker() {
    final ModelBundleCache cache = new ModelBundleCache(Map.of());
    try {
      cache.getPosTagger().tag(new String[] {"A", "test"});
      cache.clearThreadLocalState();

      assertThrows(IllegalStateException.class, cache.getPosTagger()::probs);
    } finally {
      cache.close();
    }
  }
}
