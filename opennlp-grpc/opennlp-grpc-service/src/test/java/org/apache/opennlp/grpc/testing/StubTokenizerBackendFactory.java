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
package org.apache.opennlp.grpc.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.spi.model.TokenizerBackendFactory;

/** Test-only custom tokenizer that treats a vertical bar as a token boundary. */
public final class StubTokenizerBackendFactory implements TokenizerBackendFactory {

  /** Engine id used by segmentation tests. */
  public static final String ENGINE_ID = "test-pipe";

  /** Public no-argument constructor for ServiceLoader. */
  public StubTokenizerBackendFactory() {
  }

  @Override
  public String engineId() {
    return ENGINE_ID;
  }

  @Override
  public Optional<Tokenizer> create(Map<String, String> configuration) {
    return Optional.of(new Tokenizer() {
      @Override
      public String[] tokenize(String text) {
        return Span.spansToStrings(tokenizePos(text), text);
      }

      @Override
      public Span[] tokenizePos(String text) {
        return delimiterSpans(text, '|');
      }
    });
  }

  private static Span[] delimiterSpans(String text, char delimiter) {
    final List<Span> spans = new ArrayList<>();
    int start = 0;
    for (int index = 0; index <= text.length(); index++) {
      if (index == text.length() || text.charAt(index) == delimiter) {
        if (index > start) {
          spans.add(new Span(start, index));
        }
        start = index + 1;
      }
    }
    return spans.toArray(Span[]::new);
  }
}
