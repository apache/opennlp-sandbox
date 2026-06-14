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

import java.util.Objects;

import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import opennlp.tools.parser.ParserFactory;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.ParseTree;

/**
 * A {@link ParserModel} backed by a classic OpenNLP constituency parser. OpenNLP's parser is
 * <b>not</b> thread-safe (its beam search mutates per-instance state), so each thread gets its own
 * {@link Parser} from a {@link ThreadLocal}, all built from the shared, immutable
 * {@link opennlp.tools.parser.ParserModel}.
 */
final class ClassicParserModel implements ParserModel {

  /** Backend id reported for parsers served by the classic OpenNLP maxent runtime. */
  static final String BACKEND_ID = "opennlp-me";

  private final String id;
  private final int priority;
  private final ThreadLocal<Parser> parser;

  ClassicParserModel(String id, opennlp.tools.parser.ParserModel model, int priority) {
    this.id = Objects.requireNonNull(id, "id");
    Objects.requireNonNull(model, "model");
    this.priority = priority;
    this.parser = ThreadLocal.withInitial(() -> ParserFactory.create(model));
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public String backendId() {
    return BACKEND_ID;
  }

  @Override
  public int priority() {
    return priority;
  }

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
