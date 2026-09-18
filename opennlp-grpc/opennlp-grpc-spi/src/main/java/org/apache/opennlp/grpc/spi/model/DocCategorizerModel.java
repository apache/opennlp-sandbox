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

import java.util.List;

import org.apache.opennlp.grpc.v1.DocumentClassification;

/**
 * A whole-document classifier keyed by a model id. One model emits a fixed set of categories
 * with a score for each. The classification input provides both raw text and tokens so the model
 * can select the representation it requires.
 *
 * <p>Thread safety is implementation specific.</p>
 */
public interface DocCategorizerModel {

  /**
   * Returns the stable identifier this model is registered and selected under.
   *
   * @return The model id. Never {@code null}.
   */
  String id();

  /**
   * Returns the open identifier of the backend serving this model.
   *
   * @return The backend id, e.g. {@code "opennlp-me"}. Never {@code null}.
   */
  String backendId();

  /**
   * Returns the categories this model can emit, in the model's own index order.
   *
   * @return The category labels in index order. Never {@code null}.
   */
  List<String> categories();

  /**
   * Reports whether tokenization must run before this model. A raw-text model may classify under
   * a {@code DOC_CATEGORIZE}-only profile without {@code TOKENIZE}. The default is {@code true}.
   *
   * @return {@code true} if the model must be given tokens; {@code false} if the raw text
   *     suffices.
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

  /**
   * Classifies several documents, in order. The default classifies one at a time; backends
   * that batch inference (transformer models on an accelerator) override it so a document
   * of thousands of sentences costs a handful of calls instead of one call per sentence.
   *
   * @param documentTexts The documents' whole texts. Must not be {@code null}.
   * @param documentTokens The documents' tokens, one array per document in the same order;
   *     each may be empty if no tokenizer ran. Must not be {@code null} and must match
   *     {@code documentTexts} in size.
   *
   * @return One classification per document, in input order; never {@code null}.
   *
   * @throws IllegalArgumentException If the lists are {@code null} or differ in size.
   */
  default List<DocumentClassification> classifyBatch(
      List<String> documentTexts, List<String[]> documentTokens) {
    if (documentTexts == null || documentTokens == null) {
      throw new IllegalArgumentException("documentTexts and documentTokens must not be null");
    }
    if (documentTexts.size() != documentTokens.size()) {
      throw new IllegalArgumentException(
          "documentTexts and documentTokens must have the same size");
    }
    final List<DocumentClassification> classifications =
        new java.util.ArrayList<>(documentTexts.size());
    for (int i = 0; i < documentTexts.size(); i++) {
      classifications.add(classify(documentTexts.get(i), documentTokens.get(i)));
    }
    return classifications;
  }
}
