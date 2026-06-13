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
import java.util.Set;

import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.NamedEntity;

/**
 * A named entity recognizer keyed by a model id rather than by entity type. One model may
 * produce several entity types (e.g. a transformer NER model emitting PER/ORG/LOC), so the
 * orchestrator runs each model once and filters the emitted entities by the requested
 * types, instead of running one model per requested type.
 *
 * <p>Each implementation owns its own coordinate mapping: it returns {@link NamedEntity}
 * records whose {@link org.apache.opennlp.grpc.v1.AnnotationSpan} is in document character
 * offsets (Java UTF-16 indices, later converted to the client encoding). This is the seam
 * that absorbs the difference between classic token-index spans and ONNX models that
 * re-tokenize internally.</p>
 */
public interface NerModel {

  /** Stable identifier for this model (for classic per-type models, the entity type). */
  String id();

  /** Open identifier of the backend serving this model, e.g. {@code "opennlp-me"}. */
  String backendId();

  /** The entity types this model can emit, in normalized (lower-case) form. */
  Set<String> entityTypes();

  /**
   * Whether this model carries document-level adaptive state that must be reset between
   * documents for stateless RPC semantics. Stateless models (e.g. transformer NER) return
   * {@code false}.
   */
  boolean isStateful();

  /** Resets the calling thread's adaptive state; a no-op for stateless models. */
  void clearAdaptiveData();

  /**
   * Recognizes entities in one already-tokenized sentence, returning {@link NamedEntity}
   * records with document-relative spans. The orchestrator applies the requested-type
   * filter and deduplication; an implementation returns everything it finds.
   *
   * @param sentence The sentence with its tokens and their document spans.
   * @param includeProbabilities Whether to attach model probabilities.
   *
   * @return The entities found, possibly empty; never {@code null}.
   */
  List<NamedEntity> recognize(AnnotatedSentence sentence, boolean includeProbabilities);
}
