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

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.doccat.DocumentCategorizer;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.DocumentClassification;

/**
 * {@link DocCategorizerModel} backed by an OpenNLP {@link DocumentCategorizer}, ready for any
 * backend whose categorizer implements that interface.
 *
 * <p>Categorizers differ only in the shape of the input they expect, captured by
 * {@link InputMode}: a classic maxent categorizer scores a token array, whereas a transformer
 * categorizer scores the whole document text (which it splits and re-tokenizes internally).
 * Everything downstream, including category enumeration, score extraction, and best-category
 * selection, is uniform.</p>
 *
 * <p>Implementations of {@link DocumentCategorizer} are not declared thread-safe in general;
 * only wrap a categorizer that is stateless at inference time, because a single shared
 * instance serves concurrent requests.</p>
 */
public final class OpenNlpDocCategorizerModel implements DocCategorizerModel, AutoCloseable {

  /** How a backend's {@link DocumentCategorizer} expects its input. */
  public enum InputMode {
    /** Pass the document tokens (classic maxent categorizer). */
    TOKENS,
    /** Pass the whole document text as the sole element (transformer categorizer). */
    RAW_TEXT
  }

  private final String id;
  private final String backendId;
  private final DocumentCategorizer categorizer;
  private final InputMode inputMode;
  private final List<String> categories;

  /**
   * Creates a document-categorizer registration.
   *
   * @param id The logical model id.
   * @param backendId The serving backend id.
   * @param categorizer The initialized categorizer.
   * @param inputMode Whether the model consumes tokens or raw text.
   */
  public OpenNlpDocCategorizerModel(String id, String backendId,
      DocumentCategorizer categorizer, InputMode inputMode) {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }
    this.id = id;
    if (backendId == null) {
      throw new IllegalArgumentException("backendId must not be null");
    }
    this.backendId = backendId;
    if (categorizer == null) {
      throw new IllegalArgumentException("categorizer must not be null");
    }
    this.categorizer = categorizer;
    if (inputMode == null) {
      throw new IllegalArgumentException("inputMode must not be null");
    }
    this.inputMode = inputMode;
    final List<String> labels = new ArrayList<>(categorizer.getNumberOfCategories());
    for (int i = 0; i < categorizer.getNumberOfCategories(); i++) {
      labels.add(categorizer.getCategory(i));
    }
    this.categories = List.copyOf(labels);
  }

  /** {@inheritDoc} */
  @Override
  public String id() {
    return id;
  }

  /** {@inheritDoc} */
  @Override
  public String backendId() {
    return backendId;
  }

  /** {@inheritDoc} */
  @Override
  public List<String> categories() {
    return categories;
  }

  /** {@inheritDoc} */
  @Override
  public boolean requiresTokens() {
    // Classic maxent categorizers classify the token bag; transformer models re-tokenize the
    // raw text internally and need no upstream tokenization.
    return inputMode == InputMode.TOKENS;
  }

  /** {@inheritDoc} */
  @Override
  public DocumentClassification classify(String documentText, String[] documentTokens) {
    final String[] input = inputMode == InputMode.RAW_TEXT
        ? new String[] {documentText == null ? "" : documentText}
        : documentTokens;
    final double[] scores = categorizer.categorize(input);
    // DocumentCategorizerDL returns an empty array when inference fails; surface that as an
    // INTERNAL error rather than emitting an empty, misleading classification.
    if (scores.length != categories.size()) {
      throw AnalysisException.internal("Document categorizer '" + id + "' returned "
          + scores.length + " score(s) for " + categories.size() + " categor"
          + (categories.size() == 1 ? "y" : "ies"), null);
    }
    final DocumentClassification.Builder classification = DocumentClassification.newBuilder();
    String bestCategory = "";
    double bestScore = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < scores.length; i++) {
      final String category = categories.get(i);
      classification.putCategoryScores(category, scores[i]);
      if (scores[i] > bestScore) {
        bestScore = scores[i];
        bestCategory = category;
      }
    }
    return classification.setBestCategory(bestCategory).build();
  }

  /** Closes the underlying categorizer when it holds native resources (e.g. an ONNX session). */
  @Override
  public void close() {
    if (categorizer instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new IllegalStateException("Failed to close document categorizer '" + id
            + "' on backend '" + backendId + "'", e);
      }
    }
  }
}
