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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.processor.basic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.opennlp.grpc.processor.ProgressiveAnalysisListener;
import org.apache.opennlp.grpc.v1.AnalysisLayerBatch;
import org.apache.opennlp.grpc.v1.AnalysisStarted;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.CategoryChunkConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressiveAnalysisCoordinatorTest {

  private static final String SHORT_TEXT = "One short sentence.";
  private static final String SENTENCE_GROUP_ID = "sentences";
  private static final String SENTIMENT_GROUP_ID = "sentiment";
  private static final String CHUNK_GROUPS_LAYER_ID = "opennlp:chunk-groups";
  private static final String BRANCH_WAIT_INTERRUPTED = "branch wait was interrupted";

  @Test
  void independentBranchesRunAtTheSameTime() throws InterruptedException {
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(SHORT_TEXT).build())
        .build();
    final AnalyzeDocumentResponse base;
    try (BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of())) {
      base = analyzer.analyze(request);
    }
    final CountDownLatch bothBranchesStarted = new CountDownLatch(2);
    final CountDownLatch terminal = new CountDownLatch(1);
    final AtomicReference<RuntimeException> failure = new AtomicReference<>();
    final List<PipelineStep> finishedBranches =
        java.util.Collections.synchronizedList(new ArrayList<>());

    try (var executor = Executors.newFixedThreadPool(2)) {
      ProgressiveAnalysisCoordinator.start(
          request,
          EnumSet.of(
              PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
              PipelineStep.PIPELINE_STEP_TOKENIZE,
              PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE,
              PipelineStep.PIPELINE_STEP_PARSE),
          executor,
          null,
          new ProgressiveAnalysisListener() {
            @Override
            public void onStarted(AnalysisStarted started) {
            }

            @Override
            public void onLayersReady(AnalysisLayerBatch layers) {
              if (layers.getStep() == PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE
                  || layers.getStep() == PipelineStep.PIPELINE_STEP_PARSE) {
                finishedBranches.add(layers.getStep());
              }
            }

            @Override
            public void onStepFailed(PipelineStep step, RuntimeException branchFailure) {
              failure.set(branchFailure);
            }

            @Override
            public void onComplete(AnalyzeDocumentResponse response) {
              terminal.countDown();
            }

            @Override
            public void onError(RuntimeException terminalFailure) {
              failure.set(terminalFailure);
              terminal.countDown();
            }

            @Override
            public boolean isCancelled() {
              return false;
            }
          },
          (branchRequest, steps, backbone) -> {
            if (steps.contains(PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE)
                || steps.contains(PipelineStep.PIPELINE_STEP_PARSE)) {
              bothBranchesStarted.countDown();
              try {
                if (!bothBranchesStarted.await(5, TimeUnit.SECONDS)) {
                  throw new IllegalStateException(
                      "independent branch did not start concurrently");
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(BRANCH_WAIT_INTERRUPTED, e);
              }
            }
            return base;
          });

      assertTrue(terminal.await(10, TimeUnit.SECONDS));
    }

    assertNull(failure.get());
    assertTrue(finishedBranches.contains(PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE));
    assertTrue(finishedBranches.contains(PipelineStep.PIPELINE_STEP_PARSE));
  }

  @Test
  void documentCategoryBranchReusesDetectedSentencesWhenItTokenizes()
      throws InterruptedException {
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(SHORT_TEXT).build())
        .build();
    final AnalyzeDocumentResponse base;
    try (BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of())) {
      base = analyzer.analyze(request);
    }
    final CountDownLatch terminal = new CountDownLatch(1);
    final AtomicReference<RuntimeException> failure = new AtomicReference<>();
    final AtomicReference<Set<PipelineStep>> categorySteps = new AtomicReference<>();
    final AtomicReference<OpenNlpDocument> categoryBackbone = new AtomicReference<>();

    try (var executor = Executors.newSingleThreadExecutor()) {
      ProgressiveAnalysisCoordinator.start(
          request,
          EnumSet.of(
              PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
              PipelineStep.PIPELINE_STEP_TOKENIZE,
              PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE),
          executor,
          null,
          listener(terminal, failure, new ArrayList<>()),
          (branchRequest, steps, backbone) -> {
            if (steps.contains(PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE)) {
              categorySteps.set(steps);
              categoryBackbone.set(backbone);
            }
            return base;
          });

      assertTrue(terminal.await(10, TimeUnit.SECONDS));
    }

    assertNull(failure.get());
    assertFalse(categorySteps.get().contains(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT));
    assertTrue(categoryBackbone.get().getSentencesCount() > 0);
  }

  @Test
  void linguisticGraphBranchIncludesNerAndPosDependencies() throws InterruptedException {
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(SHORT_TEXT).build())
        .build();
    final AnalyzeDocumentResponse base;
    try (BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of())) {
      base = analyzer.analyze(request);
    }
    final CountDownLatch terminal = new CountDownLatch(1);
    final AtomicReference<RuntimeException> failure = new AtomicReference<>();
    final AtomicReference<Set<PipelineStep>> graphSteps = new AtomicReference<>();

    try (var executor = Executors.newSingleThreadExecutor()) {
      ProgressiveAnalysisCoordinator.start(
          request,
          EnumSet.of(
              PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
              PipelineStep.PIPELINE_STEP_TOKENIZE,
              PipelineStep.PIPELINE_STEP_NER,
              PipelineStep.PIPELINE_STEP_POS_TAG,
              PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE,
              PipelineStep.PIPELINE_STEP_RELATION_EXTRACT),
          executor,
          null,
          listener(terminal, failure, new ArrayList<>()),
          (branchRequest, steps, backbone) -> {
            if (steps.contains(PipelineStep.PIPELINE_STEP_RELATION_EXTRACT)) {
              graphSteps.set(steps);
            }
            return base;
          });

      assertTrue(terminal.await(10, TimeUnit.SECONDS));
    }

    assertNull(failure.get());
    assertNotNull(graphSteps.get());
    assertTrue(graphSteps.get().contains(PipelineStep.PIPELINE_STEP_NER));
    assertTrue(graphSteps.get().contains(PipelineStep.PIPELINE_STEP_POS_TAG));
    assertTrue(graphSteps.get().contains(PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE));
  }

  @Test
  void cancellationInterruptsRunningBranches() throws InterruptedException {
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(SHORT_TEXT).build())
        .build();
    final AnalyzeDocumentResponse base;
    try (BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of())) {
      base = analyzer.analyze(request);
    }
    final CountDownLatch branchStarted = new CountDownLatch(1);
    final CountDownLatch branchInterrupted = new CountDownLatch(1);
    final CountDownLatch releaseBranch = new CountDownLatch(1);
    final AtomicBoolean cancelled = new AtomicBoolean();

    try (var executor = Executors.newSingleThreadExecutor()) {
      ProgressiveAnalysisCoordinator.start(
          request,
          EnumSet.of(
              PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
              PipelineStep.PIPELINE_STEP_TOKENIZE,
              PipelineStep.PIPELINE_STEP_NER),
          executor,
          null,
          new ProgressiveAnalysisListener() {
            @Override
            public void onStarted(AnalysisStarted started) {
            }

            @Override
            public void onLayersReady(AnalysisLayerBatch layers) {
            }

            @Override
            public void onStepFailed(PipelineStep step, RuntimeException branchFailure) {
            }

            @Override
            public void onComplete(AnalyzeDocumentResponse response) {
            }

            @Override
            public void onError(RuntimeException terminalFailure) {
            }

            @Override
            public boolean isCancelled() {
              return cancelled.get();
            }
          },
          (branchRequest, steps, backbone) -> {
            if (isBackbone(steps)) {
              return base;
            }
            branchStarted.countDown();
            try {
              releaseBranch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              branchInterrupted.countDown();
              Thread.currentThread().interrupt();
              throw new IllegalStateException("branch was cancelled", e);
            }
            return base;
          });

      assertTrue(branchStarted.await(5, TimeUnit.SECONDS));
      cancelled.set(true);
      assertTrue(branchInterrupted.await(1, TimeUnit.SECONDS));
    } finally {
      releaseBranch.countDown();
    }
  }

  @Test
  void admitsAtMostFourHeavyBranchesAtOnce() throws InterruptedException {
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(SHORT_TEXT).build())
        .build();
    final AnalyzeDocumentResponse base;
    try (BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of())) {
      base = analyzer.analyze(request);
    }
    final CountDownLatch firstWindowStarted = new CountDownLatch(4);
    final CountDownLatch fifthBranchStarted = new CountDownLatch(1);
    final CountDownLatch releaseBranches = new CountDownLatch(1);
    final CountDownLatch terminal = new CountDownLatch(1);
    final AtomicInteger active = new AtomicInteger();
    final AtomicInteger maximumActive = new AtomicInteger();
    final AtomicInteger started = new AtomicInteger();
    final AtomicReference<RuntimeException> failure = new AtomicReference<>();

    try (var executor = Executors.newFixedThreadPool(8)) {
      ProgressiveAnalysisCoordinator.start(
          request,
          EnumSet.of(
              PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
              PipelineStep.PIPELINE_STEP_TOKENIZE,
              PipelineStep.PIPELINE_STEP_SUBWORD_TOKENIZE,
              PipelineStep.PIPELINE_STEP_NER,
              PipelineStep.PIPELINE_STEP_POS_TAG,
              PipelineStep.PIPELINE_STEP_STEM,
              PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE,
              PipelineStep.PIPELINE_STEP_SENTIMENT),
          executor,
          null,
          listener(terminal, failure, new ArrayList<>()),
          (branchRequest, steps, backbone) -> {
            if (isBackbone(steps)) {
              return base;
            }
            if (started.incrementAndGet() == 5) {
              fifthBranchStarted.countDown();
            }
            final int nowActive = active.incrementAndGet();
            maximumActive.accumulateAndGet(nowActive, Math::max);
            firstWindowStarted.countDown();
            try {
              if (!releaseBranches.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("branch admission window was not released");
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(BRANCH_WAIT_INTERRUPTED, e);
            } finally {
              active.decrementAndGet();
            }
            return base;
          });

      assertTrue(firstWindowStarted.await(5, TimeUnit.SECONDS));
      assertFalse(fifthBranchStarted.await(250, TimeUnit.MILLISECONDS));
      assertEquals(4, started.get());
      releaseBranches.countDown();
      assertTrue(terminal.await(10, TimeUnit.SECONDS));
    } finally {
      releaseBranches.countDown();
    }

    assertNull(failure.get());
    assertEquals(4, maximumActive.get());
  }

  @Test
  void reusesTheBackboneAcrossIndependentBranches() throws InterruptedException {
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(SHORT_TEXT).build())
        .build();
    final AnalyzeDocumentResponse base;
    try (BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of())) {
      base = analyzer.analyze(request);
    }
    final CountDownLatch terminal = new CountDownLatch(1);
    final AtomicReference<RuntimeException> failure = new AtomicReference<>();
    final AtomicInteger sentenceDetectionRuns = new AtomicInteger();
    final AtomicInteger tokenizationRuns = new AtomicInteger();

    try (var executor = Executors.newFixedThreadPool(4)) {
      ProgressiveAnalysisCoordinator.start(
          request,
          EnumSet.of(
              PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
              PipelineStep.PIPELINE_STEP_TOKENIZE,
              PipelineStep.PIPELINE_STEP_NER,
              PipelineStep.PIPELINE_STEP_POS_TAG,
              PipelineStep.PIPELINE_STEP_STEM,
              PipelineStep.PIPELINE_STEP_PARSE),
          executor,
          null,
          listener(terminal, failure, new ArrayList<>()),
          (branchRequest, steps, backbone) -> {
            if (steps.contains(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)) {
              sentenceDetectionRuns.incrementAndGet();
            }
            if (steps.contains(PipelineStep.PIPELINE_STEP_TOKENIZE)) {
              tokenizationRuns.incrementAndGet();
            }
            return base;
          });

      assertTrue(terminal.await(10, TimeUnit.SECONDS));
    }

    assertNull(failure.get());
    assertEquals(1, sentenceDetectionRuns.get());
    assertEquals(1, tokenizationRuns.get());
  }

  @Test
  void startsNerAfterTheInitialBranchWindow() throws InterruptedException {
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(SHORT_TEXT).build())
        .build();
    final AnalyzeDocumentResponse base;
    try (BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of())) {
      base = analyzer.analyze(request);
    }
    final CountDownLatch firstWindowStarted = new CountDownLatch(4);
    final CountDownLatch releaseBranches = new CountDownLatch(1);
    final CountDownLatch terminal = new CountDownLatch(1);
    final AtomicBoolean nerStarted = new AtomicBoolean();
    final AtomicReference<RuntimeException> failure = new AtomicReference<>();

    try (var executor = Executors.newFixedThreadPool(8)) {
      ProgressiveAnalysisCoordinator.start(
          request,
          EnumSet.of(
              PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
              PipelineStep.PIPELINE_STEP_TOKENIZE,
              PipelineStep.PIPELINE_STEP_SUBWORD_TOKENIZE,
              PipelineStep.PIPELINE_STEP_NER,
              PipelineStep.PIPELINE_STEP_POS_TAG,
              PipelineStep.PIPELINE_STEP_STEM,
              PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE),
          executor,
          null,
          listener(terminal, failure, new ArrayList<>()),
          (branchRequest, steps, backbone) -> {
            if (isBackbone(steps)) {
              return base;
            }
            if (steps.contains(PipelineStep.PIPELINE_STEP_NER)) {
              nerStarted.set(true);
            }
            firstWindowStarted.countDown();
            try {
              if (!releaseBranches.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("initial branch window was not released");
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(BRANCH_WAIT_INTERRUPTED, e);
            }
            return base;
          });

      assertTrue(firstWindowStarted.await(5, TimeUnit.SECONDS));
      assertFalse(nerStarted.get());
      releaseBranches.countDown();
      assertTrue(terminal.await(10, TimeUnit.SECONDS));
    } finally {
      releaseBranches.countDown();
    }

    assertNull(failure.get());
    assertTrue(nerStarted.get());
  }

  @Test
  void chunkLayerUpdatesIncludeGroupsFromEveryCompletedBranch() throws InterruptedException {
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(SHORT_TEXT).build())
        .addChunkEmbedConfigs(ChunkEmbedConfigEntry.newBuilder().setConfigId(SENTENCE_GROUP_ID))
        .addCategoryChunkConfigs(
            CategoryChunkConfigEntry.newBuilder().setConfigId(SENTIMENT_GROUP_ID))
        .build();
    final AnalyzeDocumentResponse base;
    try (BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of())) {
      base = analyzer.analyze(AnalyzeDocumentRequest.newBuilder()
          .setDocument(request.getDocument())
          .build());
    }
    final CountDownLatch terminal = new CountDownLatch(1);
    final AtomicReference<RuntimeException> failure = new AtomicReference<>();
    final List<AnalysisLayerBatch> batches =
        java.util.Collections.synchronizedList(new ArrayList<>());

    try (var executor = Executors.newFixedThreadPool(2)) {
      ProgressiveAnalysisCoordinator.start(
          request,
          EnumSet.of(
              PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
              PipelineStep.PIPELINE_STEP_TOKENIZE,
              PipelineStep.PIPELINE_STEP_SENTIMENT,
              PipelineStep.PIPELINE_STEP_CHUNK),
          executor,
          null,
          listener(terminal, failure, batches),
          (branchRequest, steps, backbone) -> {
            if (branchRequest.getCategoryChunkConfigsCount() > 0) {
              return responseWithChunkGroup(base, SENTIMENT_GROUP_ID);
            }
            if (branchRequest.getChunkEmbedConfigsCount() > 0) {
              return responseWithChunkGroup(base, SENTENCE_GROUP_ID);
            }
            return base;
          });

      assertTrue(terminal.await(10, TimeUnit.SECONDS));
    }

    assertNull(failure.get());
    final List<AnalysisLayerBatch> chunkBatches = batches.stream()
        .filter(batch -> batch.getLayersList().stream()
            .anyMatch(layer -> layer.getId().equals(CHUNK_GROUPS_LAYER_ID)))
        .toList();
    assertEquals(2, chunkBatches.size());
    assertEquals(2, chunkBatches.get(1).getLayersList().stream()
        .filter(layer -> layer.getId().equals(CHUNK_GROUPS_LAYER_ID))
        .findFirst()
        .orElseThrow()
        .getChunkGroupValues()
        .getAnnotationsCount());
  }

  private boolean isBackbone(java.util.Set<PipelineStep> steps) {
    return steps.stream().allMatch(step -> step == PipelineStep.PIPELINE_STEP_SENTENCE_DETECT
        || step == PipelineStep.PIPELINE_STEP_TOKENIZE);
  }

  private AnalyzeDocumentResponse responseWithChunkGroup(
      AnalyzeDocumentResponse base, String groupId) {
    final OpenNlpDocument.Builder document = base.getDocument().toBuilder()
        .clearLayers()
        .clearChunkEmbeddingGroups()
        .addChunkEmbeddingGroups(ChunkEmbeddingGroup.newBuilder().setGroupId(groupId));
    DocumentShapeAssembler.apply(document, document.getRawText());
    return AnalyzeDocumentResponse.newBuilder().setDocument(document).build();
  }

  private ProgressiveAnalysisListener listener(
      CountDownLatch terminal,
      AtomicReference<RuntimeException> failure,
      List<AnalysisLayerBatch> batches) {
    return new ProgressiveAnalysisListener() {
      @Override
      public void onStarted(AnalysisStarted started) {
      }

      @Override
      public void onLayersReady(AnalysisLayerBatch layers) {
        batches.add(layers);
      }

      @Override
      public void onStepFailed(PipelineStep step, RuntimeException branchFailure) {
        failure.set(branchFailure);
      }

      @Override
      public void onComplete(AnalyzeDocumentResponse response) {
        terminal.countDown();
      }

      @Override
      public void onError(RuntimeException terminalFailure) {
        failure.set(terminalFailure);
        terminal.countDown();
      }

      @Override
      public boolean isCancelled() {
        return false;
      }
    };
  }
}
