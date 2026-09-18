/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.spi.model;

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import opennlp.tools.doccat.DocumentCategorizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenNlpDocCategorizerModelTest {

  @Test
  void closeReportsTheModelAndBackendWhenResourceReleaseFails() {
    final OpenNlpDocCategorizerModel model = new OpenNlpDocCategorizerModel(
        "topic", "failing-backend", new FailingCloseCategorizer(),
        OpenNlpDocCategorizerModel.InputMode.TOKENS);

    final IllegalStateException error = assertThrows(IllegalStateException.class, model::close);

    assertEquals("Failed to close document categorizer 'topic' on backend 'failing-backend'",
        error.getMessage());
    assertEquals("simulated native close failure", error.getCause().getMessage());
  }

  /** Minimal categorizer whose resource release always fails. */
  private static final class FailingCloseCategorizer
      implements DocumentCategorizer, AutoCloseable {

    /** {@inheritDoc} */
    @Override
    public double[] categorize(String[] text, Map<String, Object> extraInformation) {
      return categorize(text);
    }

    /** {@inheritDoc} */
    @Override
    public double[] categorize(String[] text) {
      return new double[] {1.0d};
    }

    /** {@inheritDoc} */
    @Override
    public String getBestCategory(double[] outcome) {
      return "test";
    }

    /** {@inheritDoc} */
    @Override
    public int getIndex(String category) {
      return "test".equals(category) ? 0 : -1;
    }

    /** {@inheritDoc} */
    @Override
    public String getCategory(int index) {
      return "test";
    }

    /** {@inheritDoc} */
    @Override
    public int getNumberOfCategories() {
      return 1;
    }

    /** {@inheritDoc} */
    @Override
    public String getAllResults(double[] results) {
      return "test[1.0]";
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Double> scoreMap(String[] text) {
      return Map.of("test", 1.0d);
    }

    /** {@inheritDoc} */
    @Override
    public SortedMap<Double, Set<String>> sortedScoreMap(String[] text) {
      final SortedMap<Double, Set<String>> scores = new TreeMap<>();
      scores.put(1.0d, Set.of("test"));
      return scores;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
      throw new IllegalArgumentException("simulated native close failure");
    }
  }
}
