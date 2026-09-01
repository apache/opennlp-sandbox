/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.training;

import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.apache.opennlp.grpc.processor.DocumentAnalysisSession;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.LearnVocabularyStart;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.StaticModelDescriptor;
import org.apache.opennlp.grpc.v1.StreamingTrainingModelPlan;
import org.apache.opennlp.grpc.v1.StreamingTrainingStart;
import org.apache.opennlp.grpc.v1.VocabularyArtifactDescriptor;

/** Internal stages used by one bidirectional training session. */
interface StreamingTrainingPipeline {

  /**
   * One published index and the exact compensation needed before commit.
   *
   * @param response Published index response.
   * @param rollback Operation that removes this publication.
   */
  record IndexPublication(IndexDocumentsResponse response, Rollback rollback) {

    public IndexPublication {
      if (response == null || rollback == null) {
        throw new IllegalArgumentException("index publication fields must not be null");
      }
    }
  }

  /** Checked compensation for one published terminal stage. */
  @FunctionalInterface
  interface Rollback {

    /**
     * Reverts the publication when the enclosing session does not commit.
     *
     * @throws IOException If the publication cannot be removed.
     */
    void run() throws IOException;
  }

  /**
   * Fixed operator limits and enabled stages reported at session admission.
   *
   * @param maxDocuments Maximum accepted documents.
   * @param maxCorpusBytes Maximum retained corpus bytes.
   * @param modelTrainingEnabled Whether model publication is configured.
   * @param indexingEnabled Whether dynamic index publication is configured.
   */
  record Limits(
      int maxDocuments, int maxCorpusBytes,
      boolean modelTrainingEnabled, boolean indexingEnabled) {

    public Limits {
      if (maxDocuments < 1 || maxCorpusBytes < 1) {
        throw new IllegalArgumentException("streaming training limits must be positive");
      }
    }
  }

  /** @return Fixed limits and capabilities for every opened session. */
  Limits limits();

  /**
   * Validates resources and provider selections before session admission.
   *
   * @param start Proposed session configuration.
   * @throws IllegalArgumentException If the configuration is invalid.
   * @throws IllegalStateException If an operator-controlled resource is unavailable.
   */
  default void validateStart(StreamingTrainingStart start) {
  }

  /**
   * Opens the fixed preview analysis configuration.
   *
   * @param configuration Analysis configuration.
   * @return Prepared analysis session.
   */
  DocumentAnalysisSession openAnalysis(AnalyzeStreamConfiguration configuration);

  /**
   * Learns and publishes the terminal vocabulary.
   *
   * @param start Vocabulary controls.
   * @param documents Accepted source documents.
   * @return Published vocabulary descriptor.
   * @throws IOException If publication fails.
   */
  VocabularyArtifactDescriptor learnVocabulary(
      LearnVocabularyStart start, List<OpenNlpDocument> documents) throws IOException;

  /**
   * Distills and publishes a model over the session vocabulary.
   *
   * @param plan Model controls.
   * @param vocabularyArtifactId Published session vocabulary.
   * @param progress Progress receiver.
   * @param cancelled Cancellation state.
   * @return Published model descriptor.
   * @throws IOException If training or publication fails.
   */
  StaticModelDescriptor trainModel(
      StreamingTrainingModelPlan plan, String vocabularyArtifactId,
      Consumer<String> progress, BooleanSupplier cancelled) throws IOException;

  /**
   * Re-analyzes retained inputs with the published model and creates the index.
   *
   * @param start Complete session configuration.
   * @param model Published session model.
   * @param documents Accepted source documents.
   * @param cancelled Cancellation state.
   * @return Published index and rollback operation.
   * @throws IOException If publication fails.
   */
  IndexPublication createIndex(
      StreamingTrainingStart start, StaticModelDescriptor model,
      List<OpenNlpDocument> documents, BooleanSupplier cancelled) throws IOException;

  /**
   * Removes a partially published model during reverse-order rollback.
   *
   * @param artifactId Published model artifact id.
   * @throws IOException If deletion fails.
   */
  void deleteModel(String artifactId) throws IOException;

  /**
   * Removes a partially published vocabulary during reverse-order rollback.
   *
   * @param artifactId Published vocabulary artifact id.
   * @throws IOException If deletion fails.
   */
  void deleteVocabulary(String artifactId) throws IOException;
}
