/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.opennlp.grpc.processor.basic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.opennlp.grpc.processor.ProgressiveAnalysisListener;
import org.apache.opennlp.grpc.v1.AnalysisLayerBatch;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalysisStarted;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.StemmerAlgorithm;
import org.apache.opennlp.grpc.v1.StemmerSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The progressive path must produce the same text-enrichment layers as the canonical path:
 * stemming runs in a branch that reuses the backbone's tokens instead of tokenizing again.
 */
class BasicDocumentAnalyzerProgressiveEnrichmentTest {

  @Test
  void stemBranchReusesBackboneTokensAndDeliversTheStemsLayer() throws Exception {
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder()
            .setRawText("George Washington visited Paris in the spring of 1789."))
        .setProfile(AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
            .addSteps(PipelineStep.PIPELINE_STEP_LEMMATIZE)
            .addSteps(PipelineStep.PIPELINE_STEP_STEM)
            .setStemmer(StemmerSpec.newBuilder()
                .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_SNOWBALL)
                .setLanguage("en")))
        .build();
    final CountDownLatch terminal = new CountDownLatch(1);
    final AtomicReference<AnalyzeDocumentResponse> completed = new AtomicReference<>();
    final List<String> failures = new ArrayList<>();
    try (BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of());
         var executor = Executors.newFixedThreadPool(4)) {
      analyzer.analyzeProgressively(request, executor, new ProgressiveAnalysisListener() {
        @Override
        public void onStarted(AnalysisStarted started) {
        }

        @Override
        public void onLayersReady(AnalysisLayerBatch layers) {
        }

        @Override
        public void onStepFailed(PipelineStep step, RuntimeException branchFailure) {
          failures.add(step.name() + ": " + branchFailure.getMessage());
        }

        @Override
        public void onComplete(AnalyzeDocumentResponse response) {
          completed.set(response);
          terminal.countDown();
        }

        @Override
        public void onError(RuntimeException terminalFailure) {
          failures.add("terminal: " + terminalFailure.getMessage());
          terminal.countDown();
        }

        @Override
        public boolean isCancelled() {
          return false;
        }
      });
      assertTrue(terminal.await(30, TimeUnit.SECONDS));
    }
    assertEquals(List.of(), failures);
    assertNotNull(completed.get());
    final List<String> layerIds = completed.get().getDocument().getLayers().getLayersList()
        .stream().map(AnnotationLayer::getId).toList();
    assertTrue(layerIds.contains("opennlp:stems"), layerIds.toString());
    assertTrue(layerIds.contains("opennlp:tokens"), layerIds.toString());
  }
}
