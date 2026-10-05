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
package org.apache.opennlp.grpc.dl.model;

import java.util.ArrayList;
import java.util.List;

import ai.onnxruntime.OrtException;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.spi.model.DocCategorizerModel;
import org.apache.opennlp.grpc.v1.DocumentClassification;

/**
 * {@link DocCategorizerModel} over a batched ONNX sequence classifier. The model consumes raw
 * text (it tokenizes internally), so it needs no upstream tokenization, and it classifies a
 * whole batch of documents in a few inference calls.
 */
final class OnnxDocCategorizerModel implements DocCategorizerModel, AutoCloseable {

  private final String id;
  private final String backendId;
  private final OnnxDocumentClassifier classifier;

  /**
   * Creates a registration over an open classifier.
   *
   * @param id The logical model id. Must not be {@code null}.
   * @param backendId The serving backend id. Must not be {@code null}.
   * @param classifier The open classifier. Must not be {@code null}.
   */
  OnnxDocCategorizerModel(String id, String backendId, OnnxDocumentClassifier classifier) {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }
    if (backendId == null) {
      throw new IllegalArgumentException("backendId must not be null");
    }
    if (classifier == null) {
      throw new IllegalArgumentException("classifier must not be null");
    }
    this.id = id;
    this.backendId = backendId;
    this.classifier = classifier;
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
    return classifier.categories();
  }

  /** {@inheritDoc} The classifier tokenizes internally. */
  @Override
  public boolean requiresTokens() {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public DocumentClassification classify(String documentText, String[] documentTokens) {
    final String[] tokens = documentTokens == null ? new String[0] : documentTokens;
    return classifyBatch(List.of(documentText == null ? "" : documentText),
        java.util.Collections.singletonList(tokens)).getFirst();
  }

  /** {@inheritDoc} Scores the whole batch through a few inference calls. */
  @Override
  public List<DocumentClassification> classifyBatch(
      List<String> documentTexts, List<String[]> documentTokens) {
    if (documentTexts == null || documentTokens == null) {
      throw new IllegalArgumentException("documentTexts and documentTokens must not be null");
    }
    if (documentTexts.size() != documentTokens.size()) {
      throw new IllegalArgumentException(
          "documentTexts and documentTokens must have the same size");
    }
    final List<double[]> scores;
    try {
      scores = classifier.scoreBatch(documentTexts);
    } catch (OrtException e) {
      throw AnalysisException.internal(
          "Document categorizer '" + id + "' inference failed", e);
    }
    final List<String> categories = classifier.categories();
    final List<DocumentClassification> classifications = new ArrayList<>(scores.size());
    for (double[] distribution : scores) {
      final DocumentClassification.Builder classification =
          DocumentClassification.newBuilder();
      String best = "";
      double bestScore = Double.NEGATIVE_INFINITY;
      for (int c = 0; c < categories.size(); c++) {
        classification.putCategoryScores(categories.get(c), distribution[c]);
        if (distribution[c] > bestScore) {
          bestScore = distribution[c];
          best = categories.get(c);
        }
      }
      classifications.add(classification.setBestCategory(best).build());
    }
    return classifications;
  }

  /** {@inheritDoc} */
  @Override
  public void close() {
    try {
      classifier.close();
    } catch (OrtException e) {
      throw new IllegalStateException("Failed to close document categorizer '" + id + "'", e);
    }
  }
}
