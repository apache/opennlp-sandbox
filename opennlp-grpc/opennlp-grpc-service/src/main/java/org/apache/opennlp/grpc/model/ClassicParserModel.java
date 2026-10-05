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

import java.util.function.Supplier;

import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import opennlp.tools.parser.ParserFactory;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.ParseTree;
import org.apache.opennlp.grpc.spi.model.ParserModel;

/**
 * A {@link ParserModel} backed by a classic OpenNLP constituency parser. OpenNLP's parser is
 * <b>not</b> thread-safe (its beam search mutates per-instance state), so each thread gets its own
 * {@link Parser} from a {@link ThreadLocal}, all built from the shared, immutable
 * {@link opennlp.tools.parser.ParserModel}. {@link #clearThreadLocalState()} drops the calling
 * thread's parser; the per-document cleanup in the analyzer calls it so pooled workers do not
 * retain parser instances for the pool's lifetime.
 */
final class ClassicParserModel implements ParserModel {

  /** Backend id reported for parsers served by the classic OpenNLP maxent runtime. */
  static final String BACKEND_ID = "opennlp-me";

  private final String id;
  private final int priority;
  private final ThreadLocal<Parser> parser;

  /**
   * Creates a classic parser registration with one decoder per calling thread.
   *
   * @param id The logical parser id.
   * @param model The immutable OpenNLP parser model.
   * @param priority The selection priority among engines serving {@code id}.
   */
  ClassicParserModel(String id, opennlp.tools.parser.ParserModel model, int priority) {
    this(id, priority, () -> ParserFactory.create(requireModel(model)));
  }

  /**
   * Creates a classic parser registration minting per-thread parsers from the given supplier.
   * Package-private test seam; production registrations are built from a parser model.
   *
   * @param id The logical parser id.
   * @param priority The selection priority among engines serving {@code id}.
   * @param parserSupplier Mints the per-thread {@link Parser}.
   */
  ClassicParserModel(String id, int priority, Supplier<Parser> parserSupplier) {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }
    this.id = id;
    this.priority = priority;
    this.parser = ThreadLocal.withInitial(parserSupplier);
  }

  /** Validates the model argument before it is captured by the parser supplier. */
  private static opennlp.tools.parser.ParserModel requireModel(
      opennlp.tools.parser.ParserModel model) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    return model;
  }

  /** {@inheritDoc} */
  @Override
  public String id() {
    return id;
  }

  /** {@inheritDoc} */
  @Override
  public String backendId() {
    return BACKEND_ID;
  }

  /** {@inheritDoc} */
  @Override
  public int priority() {
    return priority;
  }

  /** {@inheritDoc} */
  @Override
  public void clearThreadLocalState() {
    parser.remove();
  }

  /** {@inheritDoc} */
  @Override
  public ParseTree parse(AnnotatedSentence sentence, boolean structured, boolean bracketed,
      boolean includeProbabilities) {
    final String[] tokens = new String[sentence.getTokensCount()];
    for (int t = 0; t < tokens.length; t++) {
      tokens[t] = sentence.getTokens(t).getText();
    }
    final Parse parse = parser.get().parse(Parse.createFromTokens(tokens));
    return ParseTreeConverter.toParseTree(parse, sentence, structured, bracketed,
        includeProbabilities);
  }
}
