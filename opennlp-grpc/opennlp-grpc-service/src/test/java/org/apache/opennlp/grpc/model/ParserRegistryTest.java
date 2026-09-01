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
package org.apache.opennlp.grpc.model;

import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.ParseTree;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.opennlp.grpc.spi.model.ParserModel;
import org.apache.opennlp.grpc.spi.model.ParserBackendFactory;

/**
 * Unit tests for {@link ParserRegistry}'s factory-discovery policy: duplicate factory ids and
 * cleanup when a factory fails after an earlier one loaded parsers. The factory set is driven
 * directly through the package-private seam, so no ServiceLoader stubs are needed.
 */
class ParserRegistryTest {

  @Test
  void duplicateFactoryIdFailsStartup() {
    final AnalysisException error = assertThrows(AnalysisException.class, () ->
        ParserRegistry.create(Map.of(), List.of(stubFactory("dup"), stubFactory("dup"))));
    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
    assertTrue(error.getMessage().contains("dup"),
        "the error must name the duplicate factory id: " + error.getMessage());
  }

  @Test
  void factoryFailureClosesModelsFromEarlierFactories() {
    // A factory that throws after an earlier factory loaded a parser holding a native resource
    // must not leak it: the half-built registry can never be closed by the caller.
    final CloseTrackingParserModel model = new CloseTrackingParserModel();
    assertThrows(AnalysisException.class, () ->
        ParserRegistry.create(Map.of(),
            List.of(stubFactory("tracking", List.of(model)), throwingFactory("zz-failing"))));
    assertTrue(model.closed, "a parser loaded before a later factory failed was leaked");
  }

  private static ParserBackendFactory stubFactory(String factoryId) {
    return stubFactory(factoryId, List.of());
  }

  private static ParserBackendFactory stubFactory(String factoryId, List<ParserModel> models) {
    return new ParserBackendFactory() {
      @Override
      public String factoryId() {
        return factoryId;
      }

      @Override
      public List<ParserModel> create(Map<String, String> configuration) {
        return models;
      }
    };
  }

  private static ParserBackendFactory throwingFactory(String factoryId) {
    return new ParserBackendFactory() {
      @Override
      public String factoryId() {
        return factoryId;
      }

      @Override
      public List<ParserModel> create(Map<String, String> configuration) {
        throw AnalysisException.internal("deliberate test factory failure", null);
      }
    };
  }

  /** A parser holding a pretend native resource, recording its release. */
  private static final class CloseTrackingParserModel implements ParserModel, AutoCloseable {

    private boolean closed;

    @Override
    public String id() {
      return "tracking";
    }

    @Override
    public String backendId() {
      return "tracking";
    }

    @Override
    public ParseTree parse(AnnotatedSentence sentence, boolean structured, boolean bracketed,
        boolean includeProbabilities) {
      return ParseTree.getDefaultInstance();
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
