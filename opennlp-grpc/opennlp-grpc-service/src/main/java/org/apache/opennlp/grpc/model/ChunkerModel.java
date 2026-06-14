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

import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.ChunkSpan;

/**
 * A shallow (syntactic) chunker keyed by a logical chunker id. One id may be served by several
 * engines at once (e.g. a classic maxent chunker and a future neural one); the orchestrator picks
 * among them by the request's engine policy.
 *
 * <p>Each implementation owns its own coordinate mapping: it returns {@link ChunkSpan} records
 * whose {@link org.apache.opennlp.grpc.v1.AnnotationSpan} is in document character offsets (Java
 * UTF-16 indices, later converted to the client encoding). The orchestrator attaches each chunk's
 * provenance and surface text and merges across engines.</p>
 */
public interface ChunkerModel {

  /**
   * Returns the stable identifier for this chunker.
   *
   * @return The chunker id. Never {@code null}.
   */
  String id();

  /**
   * Returns the open identifier of the backend serving this chunker.
   *
   * @return The backend id, e.g. {@code "opennlp-me"}. Never {@code null}.
   */
  String backendId();

  /**
   * Returns this chunker's selection priority among engines serving the same logical {@link #id()
   * chunker id}. Higher wins when a request resolves to a single engine; ties keep configuration
   * order.
   *
   * @return The priority; {@code 0} by default.
   */
  default int priority() {
    return 0;
  }

  /**
   * Groups the sentence's tokens into base phrases, returning one {@link ChunkSpan} per chunk with
   * a document-relative span and its phrase tag. The orchestrator adds provenance and text.
   *
   * @param sentence The POS-tagged sentence with its tokens and their document spans.
   *
   * @return The chunks found, in order; never {@code null}.
   */
  List<ChunkSpan> chunk(AnnotatedSentence sentence);
}
