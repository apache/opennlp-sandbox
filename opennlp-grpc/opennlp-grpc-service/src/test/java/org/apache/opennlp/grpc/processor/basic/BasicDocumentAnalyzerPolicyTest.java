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
package org.apache.opennlp.grpc.processor.basic;

import java.util.Map;

import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.DiagnosticSeverity;
import org.apache.opennlp.grpc.v1.ModelBundleRef;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.SemanticChunkingConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicDocumentAnalyzerPolicyTest {

  @Test
  void rejectsSemanticChunkingWithoutEmbeddingModelSelection() {
    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

    final AnalysisException error = assertThrows(AnalysisException.class, () -> analyzer.analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText("Hello world.").build())
            .addChunkEmbedConfigs(ChunkEmbedConfigEntry.newBuilder()
                .setConfigId("semantic")
                .setChunking(ChunkingSpec.newBuilder()
                    .setAlgorithm("semantic")
                    .setSemanticConfig(SemanticChunkingConfig.newBuilder()
                        .setSimilarityThreshold(0.5f)
                        .build())
                    .build())
                .addEmbeddingModelIds("minilm")
                .addEmbeddingModelIds("e5")
                .build())
            .build()));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void rejectsUnimplementedProfileSteps() {
    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

    final AnalysisException error = assertThrows(AnalysisException.class, () -> analyzer.analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText("John works at OpenNLP.").build())
            .setProfile(AnalysisProfile.newBuilder()
                .setProfileId("ner-profile")
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
                .addSteps(PipelineStep.PIPELINE_STEP_NER)
                .build())
            .build()));

    assertEquals(AnalysisException.FailureType.UNIMPLEMENTED, error.getFailureType());
  }

  @Test
  void rejectsSemanticChunkEmbedConfigsWithoutEmbeddingModel() {
    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

    final AnalysisException error = assertThrows(AnalysisException.class, () -> analyzer.analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText("Hello world.").build())
            .addChunkEmbedConfigs(ChunkEmbedConfigEntry.newBuilder()
                .setConfigId("semantic")
                .setChunking(ChunkingSpec.newBuilder()
                    .setAlgorithm("semantic")
                    .setSemanticConfig(SemanticChunkingConfig.newBuilder()
                        .setSimilarityThreshold(0.5f)
                        .build())
                    .build())
                .addEmbeddingModelIds("minilm")
                .addEmbeddingModelIds("e5")
                .build())
            .build()));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void skipsTokenizationWhenProfileRequestsSentenceDetectOnly() {
    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

    final var response = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText("One. Two!").build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("sentences-only")
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .build())
        .build());

    assertEquals(2, response.getDocument().getSentencesCount());
    assertTrue(response.getDocument().getSentences(0).getTokensList().isEmpty());
    assertTrue(response.getDiagnosticsList().stream()
        .anyMatch(d -> d.getStep() == PipelineStep.PIPELINE_STEP_TOKENIZE
            && d.getSeverity() == DiagnosticSeverity.DIAGNOSTIC_SEVERITY_INFO
            && d.getMessage().contains("skipped")));
  }

  @Test
  void rejectsTextExceedingMaxTextLength() {
    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

    final AnalysisException error = assertThrows(AnalysisException.class, () -> analyzer.analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText("This is too long.").build())
            .setOptions(AnalysisOptions.newBuilder().setMaxTextLength(4).build())
            .build()));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void rejectsEmbeddingModelIdWithoutEmbedStep() {
    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

    final AnalysisException error = assertThrows(AnalysisException.class, () -> analyzer.analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText("Hello world.").build())
            .setOptions(AnalysisOptions.newBuilder().setEmbeddingModelId("minilm").build())
            .build()));

    assertEquals(AnalysisException.FailureType.INVALID_ARGUMENT, error.getFailureType());
  }

  @Test
  void rejectsEmbedStepWhenNoModelsConfigured() {
    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

    final AnalysisException error = assertThrows(AnalysisException.class, () -> analyzer.analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText("Hello world.").build())
            .setProfile(AnalysisProfile.newBuilder()
                .setProfileId("with-embed")
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
                .addSteps(PipelineStep.PIPELINE_STEP_EMBED)
                .build())
            .build()));

    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }

  @Test
  void rejectsUnknownModelBundle() {
    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());

    final AnalysisException error = assertThrows(AnalysisException.class, () -> analyzer.analyze(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText("Hello world.").build())
            .setProfile(AnalysisProfile.newBuilder()
                .setProfileId("custom")
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .setModelBundle(ModelBundleRef.newBuilder().setBundleId("de-custom").build())
                .build())
            .build()));

    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }
}
