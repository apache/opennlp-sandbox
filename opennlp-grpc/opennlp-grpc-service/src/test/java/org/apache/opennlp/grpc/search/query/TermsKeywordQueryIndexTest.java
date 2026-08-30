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
package org.apache.opennlp.grpc.search.query;

import java.util.List;
import java.util.concurrent.Executors;

import org.apache.opennlp.grpc.v1.TermMatchMode;
import org.junit.jupiter.api.Test;

import static org.apache.opennlp.grpc.search.query.QueryTestSupport.candidate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.apache.opennlp.grpc.spi.search.QueryCandidate;

class TermsKeywordQueryIndexTest {

  @Test
  void rejectsDuplicateCandidateIdsBeforePublication() {
    final QueryCandidate candidate = candidate("duplicate", "alpha", 1, 0);

    assertThrows(IllegalArgumentException.class,
        () -> new TermsKeywordQueryIndex(List.of(candidate, candidate)));
  }

  @Test
  void servesConcurrentQueriesFromImmutableAnalyzedTerms() throws Exception {
    final TermsKeywordQueryIndex index = new TermsKeywordQueryIndex(List.of(
        candidate("first", "alpha beta", 1, 0),
        candidate("second", "beta gamma", 0, 1)));

    try (var executor = Executors.newFixedThreadPool(8)) {
      final var tasks = java.util.stream.IntStream.range(0, 100)
          .mapToObj(ignored -> (java.util.concurrent.Callable<Integer>) () -> index.term(
              "beta", TermMatchMode.TERM_MATCH_MODE_ANY).size())
          .toList();
      for (var result : executor.invokeAll(tasks)) {
        assertEquals(2, result.get());
      }
    }
  }
}
