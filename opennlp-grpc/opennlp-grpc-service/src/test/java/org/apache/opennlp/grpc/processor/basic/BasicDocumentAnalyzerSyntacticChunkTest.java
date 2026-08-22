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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.ChunkSpan;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests shallow (syntactic) chunking end-to-end through the analyzer. The token-index-to-document
 * span conversion is unit-tested hermetically in {@code ClassicChunkerModelTest}; the
 * availability/error path is hermetic here; the actual end-to-end chunking is opt-in (a ChunkerME
 * model cannot be trained in-memory). Point {@code -Dchunker.model.path=/path/to/en-chunker.bin} at
 * a real model to run it.
 */
class BasicDocumentAnalyzerSyntacticChunkTest {

  @Test
  void rejectsSyntacticChunkWhenNoModelConfigured() {
    final BasicDocumentAnalyzer analyzer =
        new BasicDocumentAnalyzer(ProfileRegistry.createDefault(), new ModelBundleCache(Map.of()));

    final AnalysisException error = assertThrows(AnalysisException.class,
        () -> analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText("The dog barked.").build())
            .setProfile(AnalysisProfile.newBuilder()
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
                .addSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
                .addSteps(PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK)
                .build())
            .build()));
    assertEquals(AnalysisException.FailureType.NOT_FOUND, error.getFailureType());
  }

  @Test
  void chunksEachSentenceWhenModelConfigured() {
    final String modelPath = System.getProperty("chunker.model.path");
    assumeTrue(modelPath != null && Files.isRegularFile(Path.of(modelPath)),
        "set -Dchunker.model.path to a real OpenNLP chunker model to run this test");

    final BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(
        ProfileRegistry.createDefault(false, false, false, false, true),
        new ModelBundleCache(Map.of("model.chunker.default.path", modelPath)));

    final AnnotatedSentence sentence = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder()
            .setRawText("The quick brown fox jumped over the lazy dog.").build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId(ProfileRegistry.CHUNK_PROFILE_ID)
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
            .addSteps(PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK)
            .build())
        .build()).getDocument().getSentences(0);

    assertTrue(sentence.hasSyntacticChunks());
    assertTrue(sentence.getSyntacticChunks().getChunksCount() > 0);
    final ChunkSpan first = sentence.getSyntacticChunks().getChunks(0);
    // English shallow parses begin with a noun phrase.
    assertEquals("NP", first.getChunkTag());
    // The chunk carries its surface text and single-provider provenance.
    assertEquals("The quick brown fox", first.getText());
    assertEquals(1, first.getSourcesCount());
    assertEquals("default", first.getSources(0).getChunkerId());
    assertEquals("opennlp-me", first.getSources(0).getEngine());
  }
}
