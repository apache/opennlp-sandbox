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

import java.util.concurrent.atomic.AtomicInteger;

import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the per-thread {@link Parser} lifecycle of {@link ClassicParserModel} through a
 * counting parser supplier: the per-thread parser is reused within a thread and dropped by
 * {@link ClassicParserModel#clearThreadLocalState()}.
 */
class ClassicParserModelTest {

  @Test
  void clearThreadLocalStateDropsTheCallingThreadsParser() {
    final AtomicInteger created = new AtomicInteger();
    final ClassicParserModel model = new ClassicParserModel("test", 0, () -> {
      created.incrementAndGet();
      return new EchoParser();
    });
    final AnnotatedSentence sentence = oneTokenSentence();

    model.parse(sentence, false, true, false);
    model.parse(sentence, false, true, false);
    assertEquals(1, created.get(), "the per-thread parser must be reused within a thread");

    model.clearThreadLocalState();

    model.parse(sentence, false, true, false);
    assertEquals(2, created.get(),
        "clearThreadLocalState must drop the calling thread's parser so a pooled worker "
            + "does not retain it for the pool's lifetime");
  }

  /** Returns a one-token sentence whose token carries a document span. */
  private static AnnotatedSentence oneTokenSentence() {
    return AnnotatedSentence.newBuilder()
        .addTokens(Token.newBuilder()
            .setText("a")
            .setAnnotationSpan(AnnotationSpan.newBuilder()
                .setStart(0)
                .setEnd(1)
                .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)))
        .build();
  }

  /** A parser returning its input; only its construction matters to this test. */
  private static final class EchoParser implements Parser {

    @Override
    public Parse[] parse(Parse tokens, int numParses) {
      return new Parse[] {tokens};
    }

    @Override
    public Parse parse(Parse tokens) {
      return tokens;
    }
  }
}
