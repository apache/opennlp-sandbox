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
package org.apache.opennlp.grpc.spi.model;

import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.ParseTree;

/**
 * A constituency parser keyed by a logical parser id. One id may be served by several engines.
 * Multi-engine selection returns one tree per engine. Returned spans use document-relative Java
 * UTF-16 offsets and are converted to the requested wire encoding later.
 *
 * <p>Thread safety is implementation specific.</p>
 */
public interface ParserModel {

  /**
   * Returns the stable identifier for this parser.
   *
   * @return The parser id. Never {@code null}.
   */
  String id();

  /**
   * Returns the open identifier of the backend serving this parser.
   *
   * @return The backend id, e.g. {@code "opennlp-me"}. Never {@code null}.
   */
  String backendId();

  /**
   * Returns this parser's selection priority among engines serving the same logical {@link #id()
   * parser id}. Higher wins when a request resolves to a single engine; ties keep configuration
   * order.
   *
   * @return The priority; {@code 0} by default.
   */
  default int priority() {
    return 0;
  }

  /** Releases caller-specific inference state held for the current thread. */
  default void clearThreadLocalState() {
    // Stateless backends hold no caller-specific inference state.
  }

  /**
   * Parses one tokenized sentence into the requested constituency-parse views.
   *
   * @param sentence The tokenized sentence; terminals link back to its tokens by order.
   * @param structured Whether to populate the nested {@code ParseNode} tree.
   * @param bracketed Whether to populate the Penn-Treebank string.
   * @param includeProbabilities Whether to attach per-node probabilities.
   *
   * @return The parse, without provenance (the orchestrator adds the parser id and engine).
   */
  ParseTree parse(AnnotatedSentence sentence, boolean structured, boolean bracketed,
      boolean includeProbabilities);
}
