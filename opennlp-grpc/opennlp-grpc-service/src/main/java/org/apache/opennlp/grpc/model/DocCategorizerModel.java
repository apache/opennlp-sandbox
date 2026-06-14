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

import org.apache.opennlp.grpc.v1.DocumentClassification;

/**
 * A whole-document classifier keyed by a model id. One model emits a fixed set of categories
 * with a score for each; the orchestrator runs the selected model once per document and stores
 * the result in {@link org.apache.opennlp.grpc.v1.OpenNlpDocument#getClassification()}.
 *
 * <p>This is the document-categorization analogue of {@link NerModel}: built-in backends wrap
 * OpenNLP's classic {@code DocumentCategorizerME} and ONNX {@code DocumentCategorizerDL}, while
 * a third-party backend (e.g. a remote classifier) implements this interface directly. The
 * interface deliberately accepts both the raw document text and its tokens so an implementation
 * can use whichever its model expects — classic maxent consumes tokens, transformer models
 * re-tokenize the raw text internally.</p>
 */
public interface DocCategorizerModel {

  /** Stable identifier this model is registered and selected under. */
  String id();

  /** Open identifier of the backend serving this model, e.g. {@code "opennlp-me"}. */
  String backendId();

  /** The categories this model can emit, in the model's own index order. */
  List<String> categories();

  /**
   * Whether this model needs tokens (classic maxent, which classifies a bag of tokens) rather
   * than only the raw text (transformer models that re-tokenize the text internally). The
   * orchestrator uses this to decide whether tokenization must run before the model: a
   * raw-text model can therefore classify under a {@code DOC_CATEGORIZE}-only profile without
   * {@code TOKENIZE}.
   *
   * <p>Defaults to {@code true} so existing backends keep their conservative tokenized behavior
   * unless they explicitly opt into raw-text classification.</p>
   */
  default boolean requiresTokens() {
    return true;
  }

  /**
   * Classifies one document into the model's categories.
   *
   * @param documentText The whole document text.
   * @param documentTokens The document's tokens, in order; may be empty if no tokenizer ran.
   *
   * @return The classification with the best category and the full per-category score map;
   *     never {@code null}.
   */
  DocumentClassification classify(String documentText, String[] documentTokens);
}
