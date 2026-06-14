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

import opennlp.tools.util.Span;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkResult;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests shallow (syntactic) chunking. The token-index-to-document-span conversion is unit-tested
 * hermetically from hand-built chunker spans; the availability/error path is hermetic; the actual
 * end-to-end chunking is opt-in (a ChunkerME model cannot be trained in-memory). Point
 * {@code -Dchunker.model.path=/path/to/en-chunker.bin} at a real model to run it.
 */
class BasicDocumentAnalyzerSyntacticChunkTest {

  private static AnnotationSpan span(int start, int end) {
    return AnnotationSpan.newBuilder().setStart(start).setEnd(end)
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT).build();
  }

  // "The dog barked" tokenized.
  private static AnnotatedSentence theDogBarked() {
    return AnnotatedSentence.newBuilder()
        .addTokens(Token.newBuilder().setText("The").setAnnotationSpan(span(0, 3)).build())
        .addTokens(Token.newBuilder().setText("dog").setAnnotationSpan(span(4, 7)).build())
        .addTokens(Token.newBuilder().setText("barked").setAnnotationSpan(span(8, 14)).build())
        .build();
  }

  @Test
  void mapsTokenIndexChunkSpansToDocumentSpans() {
    // NP covers tokens [0,2) = "The dog"; VP covers token [2,3) = "barked".
    final Span[] chunks = {new Span(0, 2, "NP"), new Span(2, 3, "VP")};

    final ChunkResult result = ClassicStepRunner.toChunkResult(chunks, theDogBarked());

    assertEquals(2, result.getChunksCount());
    assertEquals("NP", result.getChunks(0).getChunkTag());
    assertEquals(0, result.getChunks(0).getAnnotationSpan().getStart());
    assertEquals(7, result.getChunks(0).getAnnotationSpan().getEnd());
    assertEquals("VP", result.getChunks(1).getChunkTag());
    assertEquals(8, result.getChunks(1).getAnnotationSpan().getStart());
    assertEquals(14, result.getChunks(1).getAnnotationSpan().getEnd());
  }

  @Test
  void singleTokenChunkSpansOneToken() {
    final ChunkResult result =
        ClassicStepRunner.toChunkResult(new Span[] {new Span(1, 2, "NP")}, theDogBarked());
    assertEquals(1, result.getChunksCount());
    assertEquals(4, result.getChunks(0).getAnnotationSpan().getStart());
    assertEquals(7, result.getChunks(0).getAnnotationSpan().getEnd());
  }

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
        new ModelBundleCache(Map.of("model.chunker.path", modelPath)));

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
    // English shallow parses begin with a noun phrase.
    assertEquals("NP", sentence.getSyntacticChunks().getChunks(0).getChunkTag());
  }
}
