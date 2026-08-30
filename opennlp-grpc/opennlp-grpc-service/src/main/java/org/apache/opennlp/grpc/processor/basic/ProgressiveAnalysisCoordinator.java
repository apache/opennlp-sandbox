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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.opennlp.grpc.processor.ProgressiveAnalysisListener;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.spi.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.v1.AnalysisLayerBatch;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisStarted;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.ChunkGroupAnnotationList;
import org.apache.opennlp.grpc.v1.DiagnosticSeverity;
import org.apache.opennlp.grpc.v1.DocumentAnalytics;
import org.apache.opennlp.grpc.v1.DocumentLayers;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.ProcessingDiagnostic;
import org.apache.opennlp.grpc.v1.Token;

/** Coordinates isolated analysis branches and serializes their completed results. */
final class ProgressiveAnalysisCoordinator {

  private static final int MAX_CONCURRENT_BRANCHES = 4;
  private static final long CANCELLATION_POLL_MILLIS = 100;
  private static final String ANALYTICS_LAYER_ID = "opennlp:analytics";
  private static final String CHUNK_GROUPS_LAYER_ID = "opennlp:chunk-groups";
  private static final String SUBWORDS_LAYER_ID = "opennlp:subwords";
  private static final String GEO_LAYER_ID = "opennlp:geo";
  private static final String STEMS_LAYER_ID = "opennlp:stems";
  private static final String TERM_VECTORS_LAYER_ID = "opennlp:term-vectors";
  private static final String EXPANSIONS_LAYER_ID = "opennlp:expansions";
  private static final String DEPENDENCIES_LAYER_ID = "opennlp:dependencies";
  private static final String RELATIONS_LAYER_ID = "opennlp:relations";
  private static final List<String> EXTRA_LAYER_ORDER = List.of(
      SUBWORDS_LAYER_ID,
      GEO_LAYER_ID,
      DEPENDENCIES_LAYER_ID,
      RELATIONS_LAYER_ID,
      STEMS_LAYER_ID,
      TERM_VECTORS_LAYER_ID,
      EXPANSIONS_LAYER_ID);

  private static final List<PipelineStep> STEP_ORDER = List.of(
      PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT,
      PipelineStep.PIPELINE_STEP_NORMALIZE,
      PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
      PipelineStep.PIPELINE_STEP_TOKENIZE,
      PipelineStep.PIPELINE_STEP_SUBWORD_TOKENIZE,
      PipelineStep.PIPELINE_STEP_NER,
      PipelineStep.PIPELINE_STEP_GEOCODE,
      PipelineStep.PIPELINE_STEP_POS_TAG,
      PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE,
      PipelineStep.PIPELINE_STEP_RELATION_EXTRACT,
      PipelineStep.PIPELINE_STEP_LEMMATIZE,
      PipelineStep.PIPELINE_STEP_STEM,
      PipelineStep.PIPELINE_STEP_TERM_VECTOR,
      PipelineStep.PIPELINE_STEP_EXPAND,
      PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE,
      PipelineStep.PIPELINE_STEP_SENTIMENT,
      PipelineStep.PIPELINE_STEP_PARSE,
      PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK,
      PipelineStep.PIPELINE_STEP_EMBED,
      PipelineStep.PIPELINE_STEP_CHUNK);

  private static final Set<PipelineStep> BACKBONE_STEPS = EnumSet.of(
      PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT,
      PipelineStep.PIPELINE_STEP_NORMALIZE,
      PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
      PipelineStep.PIPELINE_STEP_TOKENIZE);

  /** Creates a request-scoped coordinator. */
  private ProgressiveAnalysisCoordinator() {
  }

  /** Runs one validated request on a virtual coordinator and a bounded branch executor. */
  static void start(
      AnalyzeDocumentRequest request,
      Set<PipelineStep> effectiveSteps,
      Executor branchExecutor,
      EmbeddingProvider embeddingProvider,
      ProgressiveAnalysisListener listener,
      BranchAnalyzer analyzer) {
    final ProgressiveAnalysisCoordinator coordinator = new ProgressiveAnalysisCoordinator();
    Thread.startVirtualThread(
        () -> coordinator.coordinate(request, effectiveSteps, branchExecutor, embeddingProvider,
            listener, analyzer));
  }

  /** Runs the backbone, fans out independent branches, and assembles the final document. */
  private void coordinate(
      AnalyzeDocumentRequest request,
      Set<PipelineStep> effectiveSteps,
      Executor branchExecutor,
      EmbeddingProvider embeddingProvider,
      ProgressiveAnalysisListener listener,
      BranchAnalyzer analyzer) {
    final String rawText = request.getDocument().getRawText();
    final OffsetEncoding requestedEncoding = request.hasOptions()
        ? request.getOptions().getOffsetEncoding()
        : OffsetEncoding.OFFSET_ENCODING_UNSPECIFIED;
    final AnalyzeDocumentRequest internalRequest = withInternalOffsets(request);
    try {
      listener.onStarted(AnalysisStarted.newBuilder()
          .setDocument(initialDocument(request.getDocument()))
          .addAllRequestedSteps(ordered(effectiveSteps))
          .build());
      if (listener.isCancelled()) {
        return;
      }

      final Set<PipelineStep> backbone = intersection(effectiveSteps, BACKBONE_STEPS);
      final AnalyzeDocumentResponse base = analyzer.analyze(
          withoutChunkConfigs(internalRequest), backbone, null);
      if (base.getDocument().hasLayers()) {
        listener.onLayersReady(layerBatch(
            terminalStep(backbone),
            base,
            backbone,
            requestedEncoding,
            rawText,
            BranchKind.BACKBONE));
      }
      if (listener.isCancelled()) {
        return;
      }

      final List<Branch> branches = branches(internalRequest, effectiveSteps);
      final CompletionService<BranchOutcome> completions =
          new ExecutorCompletionService<>(branchExecutor);
      final List<Future<BranchOutcome>> futures = new ArrayList<>(branches.size());
      int submitted = 0;
      while (submitted < Math.min(MAX_CONCURRENT_BRANCHES, branches.size())) {
        submit(branches.get(submitted++), base.getDocument(), analyzer, completions, futures);
      }

      final Map<Branch, AnalyzeDocumentResponse> completed = new HashMap<>();
      int remaining = branches.size();
      while (remaining > 0) {
        if (listener.isCancelled()) {
          cancel(futures);
          return;
        }
        final BranchOutcome outcome;
        try {
          final Future<BranchOutcome> completedFuture = completions.poll(
              CANCELLATION_POLL_MILLIS, TimeUnit.MILLISECONDS);
          if (completedFuture == null) {
            continue;
          }
          outcome = completedFuture.get();
          remaining--;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          cancel(futures);
          listener.onError(AnalysisException.internal("Progressive analysis interrupted", e));
          return;
        } catch (java.util.concurrent.ExecutionException e) {
          cancel(futures);
          listener.onError(AnalysisException.internal(
              "Progressive analysis branch coordination failed", e.getCause()));
          return;
        }
        if (listener.isCancelled()) {
          cancel(futures);
          return;
        }
        if (submitted < branches.size()) {
          submit(branches.get(submitted++), base.getDocument(), analyzer, completions, futures);
        }
        if (outcome.failure() != null) {
          listener.onStepFailed(outcome.branch().terminalStep(), outcome.failure());
          continue;
        }
        completed.put(outcome.branch(), outcome.response());
        final AnalysisLayerBatch batch = layerBatch(
            outcome.branch().terminalStep(),
            outcome.response(),
            progressiveLayers(outcome.branch(), outcome.response(), branches, completed),
            outcome.branch().ownedSteps(),
            requestedEncoding,
            rawText);
        if (batch.getLayersCount() > 0 || batch.getDiagnosticsCount() > 0) {
          listener.onLayersReady(batch);
        }
      }

      if (!listener.isCancelled()) {
        listener.onComplete(assemble(
            request, base, branches, completed, effectiveSteps, requestedEncoding,
            embeddingProvider));
      }
    } catch (RuntimeException e) {
      if (!listener.isCancelled()) {
        listener.onError(e);
      }
    }
  }

  /** Submits one branch and retains its future for cancellation. */
  private void submit(
      Branch branch,
      OpenNlpDocument backbone,
      BranchAnalyzer analyzer,
      CompletionService<BranchOutcome> completions,
      List<Future<BranchOutcome>> futures) {
    futures.add(completions.submit(() -> analyzeBranch(branch, backbone, analyzer)));
  }

  /** Runs one isolated branch and captures its local failure. */
  private BranchOutcome analyzeBranch(
      Branch branch, OpenNlpDocument backbone, BranchAnalyzer analyzer) {
    try {
      return new BranchOutcome(
          branch, analyzer.analyze(branch.request(), branch.runSteps(), backbone), null);
    } catch (RuntimeException e) {
      return new BranchOutcome(branch, null, e);
    }
  }

  /** Builds the independent branch plan in canonical pipeline order. */
  private List<Branch> branches(
      AnalyzeDocumentRequest request, Set<PipelineStep> effectiveSteps) {
    final List<Branch> branches = new ArrayList<>();
    addBranch(branches, request, effectiveSteps, BranchKind.SUBWORD,
        PipelineStep.PIPELINE_STEP_SUBWORD_TOKENIZE);
    addBranch(branches, request, effectiveSteps, BranchKind.POS,
        PipelineStep.PIPELINE_STEP_POS_TAG,
        PipelineStep.PIPELINE_STEP_LEMMATIZE,
        PipelineStep.PIPELINE_STEP_SYNTACTIC_CHUNK);
    addBranch(branches, request, effectiveSteps, BranchKind.TEXT_ENRICHMENT,
        PipelineStep.PIPELINE_STEP_STEM,
        PipelineStep.PIPELINE_STEP_TERM_VECTOR,
        PipelineStep.PIPELINE_STEP_EXPAND);
    addBranch(branches, request, effectiveSteps, BranchKind.DOCUMENT_CATEGORY,
        PipelineStep.PIPELINE_STEP_DOC_CATEGORIZE);
    addBranch(branches, request, effectiveSteps, BranchKind.SENTIMENT,
        PipelineStep.PIPELINE_STEP_SENTIMENT);
    addBranch(branches, request, effectiveSteps, BranchKind.PARSE,
        PipelineStep.PIPELINE_STEP_PARSE);
    addBranch(branches, request, effectiveSteps, BranchKind.EMBED,
        PipelineStep.PIPELINE_STEP_EMBED);
    if (effectiveSteps.contains(PipelineStep.PIPELINE_STEP_CHUNK)
        || request.getChunkEmbedConfigsCount() > 0) {
      addBranch(branches, request, effectiveSteps, BranchKind.CHUNK,
          PipelineStep.PIPELINE_STEP_CHUNK);
    }
    addBranch(branches, request, effectiveSteps, BranchKind.NER,
        PipelineStep.PIPELINE_STEP_NER, PipelineStep.PIPELINE_STEP_GEOCODE);
    addBranch(branches, request, effectiveSteps, BranchKind.LINGUISTIC_GRAPH,
        PipelineStep.PIPELINE_STEP_DEPENDENCY_PARSE,
        PipelineStep.PIPELINE_STEP_RELATION_EXTRACT);
    return List.copyOf(branches);
  }

  /** Adds a branch when at least one of its owned steps runs. */
  private void addBranch(
      List<Branch> branches,
      AnalyzeDocumentRequest request,
      Set<PipelineStep> effectiveSteps,
      BranchKind kind,
      PipelineStep... candidates) {
    final EnumSet<PipelineStep> owned = EnumSet.noneOf(PipelineStep.class);
    for (PipelineStep candidate : candidates) {
      if (effectiveSteps.contains(candidate)) {
        owned.add(candidate);
      }
    }
    if (kind == BranchKind.SENTIMENT && request.getCategoryChunkConfigsCount() > 0) {
      owned.add(PipelineStep.PIPELINE_STEP_CHUNK);
    }
    if (kind == BranchKind.CHUNK && request.getChunkEmbedConfigsCount() > 0) {
      owned.add(PipelineStep.PIPELINE_STEP_CHUNK);
    }
    if (owned.isEmpty()) {
      return;
    }
    final EnumSet<PipelineStep> run = dependencyClosure(effectiveSteps, owned, kind);
    AnalyzeDocumentRequest branchRequest = withoutChunkConfigs(request);
    if (kind == BranchKind.SENTIMENT) {
      branchRequest = branchRequest.toBuilder()
          .addAllCategoryChunkConfigs(request.getCategoryChunkConfigsList())
          .build();
    } else if (kind == BranchKind.CHUNK) {
      branchRequest = branchRequest.toBuilder()
          .addAllChunkEmbedConfigs(request.getChunkEmbedConfigsList())
          .build();
    }
    branches.add(new Branch(kind, terminalStep(owned), Set.copyOf(owned), Set.copyOf(run),
        branchRequest));
  }

  /** Adds only the dependencies needed by a branch to its owned pipeline steps. */
  private EnumSet<PipelineStep> dependencyClosure(
      Set<PipelineStep> effectiveSteps,
      Set<PipelineStep> owned,
      BranchKind kind) {
    final EnumSet<PipelineStep> run = EnumSet.copyOf(owned);
    if (kind == BranchKind.TEXT_ENRICHMENT) {
      if (effectiveSteps.contains(PipelineStep.PIPELINE_STEP_POS_TAG)) {
        run.add(PipelineStep.PIPELINE_STEP_POS_TAG);
      }
      if (effectiveSteps.contains(PipelineStep.PIPELINE_STEP_LEMMATIZE)) {
        run.add(PipelineStep.PIPELINE_STEP_LEMMATIZE);
      }
    }
    if (kind == BranchKind.LINGUISTIC_GRAPH) {
      if (effectiveSteps.contains(PipelineStep.PIPELINE_STEP_NER)) {
        run.add(PipelineStep.PIPELINE_STEP_NER);
      }
      if (effectiveSteps.contains(PipelineStep.PIPELINE_STEP_POS_TAG)) {
        run.add(PipelineStep.PIPELINE_STEP_POS_TAG);
      }
    }
    return run;
  }

  /** Produces one client-encoded event batch for the layers owned by a branch. */
  private AnalysisLayerBatch layerBatch(
      PipelineStep terminalStep,
      AnalyzeDocumentResponse response,
      Set<PipelineStep> ownedSteps,
      OffsetEncoding requestedEncoding,
      String rawText,
      BranchKind kind) {
    final List<AnnotationLayer> selected = response.getDocument().hasLayers()
        ? response.getDocument().getLayers().getLayersList().stream()
            .filter(layer -> ownsLayer(kind, layer.getId()))
            .toList()
        : List.of();
    return layerBatch(
        terminalStep, response, selected, ownedSteps, requestedEncoding, rawText);
  }

  /** Produces one client-encoded event batch from selected layers. */
  private AnalysisLayerBatch layerBatch(
      PipelineStep terminalStep,
      AnalyzeDocumentResponse response,
      List<AnnotationLayer> selected,
      Set<PipelineStep> ownedSteps,
      OffsetEncoding requestedEncoding,
      String rawText) {
    final OpenNlpDocument.Builder encoded = OpenNlpDocument.newBuilder()
        .setRawText(rawText)
        .setLayers(DocumentLayers.newBuilder().addAllLayers(selected).build());
    DocumentOffsetEncoder.apply(encoded, rawText, requestedEncoding);
    return AnalysisLayerBatch.newBuilder()
        .setStep(terminalStep)
        .addAllLayers(encoded.getLayers().getLayersList())
        .addAllDiagnostics(response.getDiagnosticsList().stream()
            .filter(diagnostic -> ownedSteps.contains(diagnostic.getStep()))
            .toList())
        .build();
  }

  /** Returns branch layers with a complete snapshot of all ready chunk groups. */
  private List<AnnotationLayer> progressiveLayers(
      Branch current,
      AnalyzeDocumentResponse response,
      List<Branch> branches,
      Map<Branch, AnalyzeDocumentResponse> completed) {
    final List<AnnotationLayer> selected = response.getDocument().hasLayers()
        ? new ArrayList<>(response.getDocument().getLayers().getLayersList().stream()
            .filter(layer -> ownsLayer(current.kind(), layer.getId()))
            .toList())
        : new ArrayList<>();
    int chunkLayerIndex = -1;
    for (int index = 0; index < selected.size(); index++) {
      if (selected.get(index).getId().equals(CHUNK_GROUPS_LAYER_ID)) {
        chunkLayerIndex = index;
        break;
      }
    }
    if (chunkLayerIndex < 0) {
      return selected;
    }

    final AnnotationLayer.Builder cumulative = selected.get(chunkLayerIndex).toBuilder();
    final ChunkGroupAnnotationList.Builder groups =
        cumulative.getChunkGroupValues().toBuilder().clearAnnotations();
    addReadyChunkGroups(groups, BranchKind.CHUNK, branches, completed);
    addReadyChunkGroups(groups, BranchKind.SENTIMENT, branches, completed);
    cumulative.setChunkGroupValues(groups);
    selected.set(chunkLayerIndex, cumulative.build());
    return selected;
  }

  /** Appends ready chunk groups in the same normal-then-category order as the final response. */
  private void addReadyChunkGroups(
      ChunkGroupAnnotationList.Builder target,
      BranchKind kind,
      List<Branch> branches,
      Map<Branch, AnalyzeDocumentResponse> completed) {
    for (Branch branch : branches) {
      if (branch.kind() != kind) {
        continue;
      }
      final AnalyzeDocumentResponse response = completed.get(branch);
      if (response == null || !response.getDocument().hasLayers()) {
        continue;
      }
      for (AnnotationLayer layer : response.getDocument().getLayers().getLayersList()) {
        if (layer.getId().equals(CHUNK_GROUPS_LAYER_ID)) {
          target.addAllAnnotations(layer.getChunkGroupValues().getAnnotationsList());
        }
      }
    }
  }

  /** Returns whether a layer belongs to the finished branch rather than its dependencies. */
  private boolean ownsLayer(BranchKind kind, String id) {
    return switch (kind) {
      case BACKBONE -> id.equals("opennlp:sentences")
          || id.equals("opennlp:tokens")
          || id.equals("opennlp:word-types")
          || id.equals("opennlp:stopwords")
          || id.equals("opennlp:language")
          || id.equals("opennlp:normalization")
          || id.equals(ANALYTICS_LAYER_ID)
          || id.startsWith("opennlp:terms:");
      case SUBWORD -> id.equals(SUBWORDS_LAYER_ID);
      case NER -> id.equals("opennlp:entities") || id.equals(GEO_LAYER_ID);
      case POS -> id.equals("opennlp:pos")
          || id.equals("opennlp:lemmas")
          || id.equals("opennlp:chunks")
          || id.equals(ANALYTICS_LAYER_ID);
      case LINGUISTIC_GRAPH -> id.equals(DEPENDENCIES_LAYER_ID)
          || id.equals(RELATIONS_LAYER_ID);
      case TEXT_ENRICHMENT -> id.equals(STEMS_LAYER_ID)
          || id.equals(TERM_VECTORS_LAYER_ID)
          || id.equals(EXPANSIONS_LAYER_ID);
      case DOCUMENT_CATEGORY -> id.equals("opennlp:categories");
      case SENTIMENT -> id.equals("opennlp:sentiment")
          || id.equals(CHUNK_GROUPS_LAYER_ID);
      case PARSE -> id.equals("opennlp:parses");
      case EMBED -> id.equals("opennlp:embeddings");
      case CHUNK -> id.equals(CHUNK_GROUPS_LAYER_ID);
    };
  }

  /** Merges successful branch documents, then rebuilds canonical layers once. */
  private AnalyzeDocumentResponse assemble(
      AnalyzeDocumentRequest request,
      AnalyzeDocumentResponse base,
      List<Branch> branches,
      Map<Branch, AnalyzeDocumentResponse> completed,
      Set<PipelineStep> effectiveSteps,
      OffsetEncoding requestedEncoding,
      EmbeddingProvider embeddingProvider) {
    final OpenNlpDocument.Builder document = base.getDocument().toBuilder()
        .clearLayers()
        .clearAnalytics();
    final Map<String, AnnotationLayer> extraLayers = new LinkedHashMap<>();
    final List<ChunkEmbeddingGroup> normalChunkGroups = new ArrayList<>();
    final List<ChunkEmbeddingGroup> categoryChunkGroups = new ArrayList<>();
    final Map<PipelineStep, List<ProcessingDiagnostic>> diagnostics = new HashMap<>();
    collectDiagnostics(diagnostics, base, intersection(effectiveSteps, BACKBONE_STEPS));

    for (Branch branch : branches) {
      final AnalyzeDocumentResponse response = completed.get(branch);
      if (response == null) {
        for (PipelineStep step : branch.ownedSteps()) {
          diagnostics.put(step, List.of(ProcessingDiagnostic.newBuilder()
              .setStep(step)
              .setSeverity(DiagnosticSeverity.DIAGNOSTIC_SEVERITY_ERROR)
              .setMessage(step.name() + " did not produce a result")
              .build()));
        }
        continue;
      }
      collectDiagnostics(diagnostics, response, branch.ownedSteps());
      mergeStructured(document, response.getDocument(), branch.kind());
      if (response.getDocument().hasLayers()) {
        for (AnnotationLayer layer : response.getDocument().getLayers().getLayersList()) {
          if (isExtraLayer(layer.getId())) {
            extraLayers.put(layer.getId(), layer);
          }
        }
      }
      if (branch.kind() == BranchKind.CHUNK) {
        normalChunkGroups.addAll(response.getDocument().getChunkEmbeddingGroupsList());
      } else if (branch.kind() == BranchKind.SENTIMENT) {
        categoryChunkGroups.addAll(response.getDocument().getChunkEmbeddingGroupsList());
      }
    }

    document.clearChunkEmbeddingGroups()
        .addAllChunkEmbeddingGroups(normalChunkGroups)
        .addAllChunkEmbeddingGroups(categoryChunkGroups);
    final DocumentAnalytics analytics = DocumentAnalyticsComputer.compute(document.build());
    if (analytics != null) {
      document.setAnalytics(analytics);
    }
    final List<AnnotationLayer> orderedExtras = extraLayers.values().stream()
        .sorted((left, right) -> Integer.compare(
            extraLayerRank(left.getId()), extraLayerRank(right.getId())))
        .toList();
    DocumentShapeAssembler.apply(document, request.getDocument().getRawText(), orderedExtras);
    DocumentLayersValidator.validate(document.build(), embeddingProvider);
    DocumentOffsetEncoder.apply(
        document, request.getDocument().getRawText(), requestedEncoding);

    final AnalyzeDocumentResponse.Builder response = AnalyzeDocumentResponse.newBuilder()
        .setDocument(document.build());
    for (PipelineStep step : STEP_ORDER) {
      final List<ProcessingDiagnostic> stepDiagnostics = diagnostics.get(step);
      if (stepDiagnostics != null) {
        response.addAllDiagnostics(stepDiagnostics);
      } else {
        response.addDiagnostics(StepDiagnostics.skipped(step));
      }
    }
    return response.build();
  }

  /** Collects only diagnostics owned by the branch. */
  private void collectDiagnostics(
      Map<PipelineStep, List<ProcessingDiagnostic>> target,
      AnalyzeDocumentResponse response,
      Set<PipelineStep> ownedSteps) {
    for (PipelineStep step : ownedSteps) {
      target.put(step, response.getDiagnosticsList().stream()
          .filter(diagnostic -> diagnostic.getStep() == step)
          .toList());
    }
  }

  /** Merges the structured fields owned by one branch. */
  private void mergeStructured(
      OpenNlpDocument.Builder target, OpenNlpDocument source, BranchKind kind) {
    switch (kind) {
      case NER -> mergeSentences(target, source, true, false, false, false);
      case POS -> mergeSentences(target, source, false, true, false, false);
      case SENTIMENT -> mergeSentences(target, source, false, false, true, false);
      case PARSE -> mergeSentences(target, source, false, false, false, true);
      case DOCUMENT_CATEGORY -> {
        if (source.hasClassification()) {
          target.setClassification(source.getClassification());
        }
      }
      case EMBED -> target.clearEmbeddings()
          .addAllEmbeddings(source.getEmbeddingsList())
          .clearDocumentCentroids()
          .addAllDocumentCentroids(source.getDocumentCentroidsList());
      default -> {
        // The remaining branches publish document layers only or merge chunk groups separately.
      }
    }
  }

  /** Merges sentence-local fields without replacing the shared sentence and token backbone. */
  private void mergeSentences(
      OpenNlpDocument.Builder target,
      OpenNlpDocument source,
      boolean entities,
      boolean tokenAnnotations,
      boolean sentiment,
      boolean parse) {
    final int count = Math.min(target.getSentencesCount(), source.getSentencesCount());
    for (int index = 0; index < count; index++) {
      final AnnotatedSentence branchSentence = source.getSentences(index);
      final AnnotatedSentence.Builder merged = target.getSentences(index).toBuilder();
      if (entities) {
        merged.clearEntities().addAllEntities(branchSentence.getEntitiesList());
      }
      if (tokenAnnotations) {
        final int tokens = Math.min(merged.getTokensCount(), branchSentence.getTokensCount());
        for (int tokenIndex = 0; tokenIndex < tokens; tokenIndex++) {
          final Token branchToken = branchSentence.getTokens(tokenIndex);
          final Token.Builder token = merged.getTokens(tokenIndex).toBuilder();
          if (branchToken.hasPosTag()) {
            token.setPosTag(branchToken.getPosTag());
          }
          if (branchToken.hasPosProbability()) {
            token.setPosProbability(branchToken.getPosProbability());
          }
          if (branchToken.hasLemma()) {
            token.setLemma(branchToken.getLemma());
          }
          merged.setTokens(tokenIndex, token.build());
        }
        if (branchSentence.hasSyntacticChunks()) {
          merged.setSyntacticChunks(branchSentence.getSyntacticChunks());
        }
      }
      if (sentiment && branchSentence.hasSentimentLabel()) {
        merged.setSentimentLabel(branchSentence.getSentimentLabel());
        if (branchSentence.hasSentimentConfidence()) {
          merged.setSentimentConfidence(branchSentence.getSentimentConfidence());
        }
      }
      if (parse) {
        if (branchSentence.hasParseTree()) {
          merged.setParseTree(branchSentence.getParseTree());
        }
        merged.clearParseTrees().addAllParseTrees(branchSentence.getParseTreesList());
      }
      target.setSentences(index, merged.build());
    }
  }

  /** Returns a request that keeps all execution options but asks branches for UTF-16 spans. */
  private AnalyzeDocumentRequest withInternalOffsets(AnalyzeDocumentRequest request) {
    final AnalysisOptions.Builder options = request.hasOptions()
        ? request.getOptions().toBuilder()
        : AnalysisOptions.newBuilder();
    options.setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT);
    return request.toBuilder().setOptions(options.build()).build();
  }

  /** Removes request-side chunk work from a branch unless explicitly restored. */
  private AnalyzeDocumentRequest withoutChunkConfigs(AnalyzeDocumentRequest request) {
    return request.toBuilder().clearChunkEmbedConfigs().clearCategoryChunkConfigs().build();
  }

  /** Retains only caller-owned input fields for the first event. */
  private OpenNlpDocument initialDocument(OpenNlpDocument input) {
    final OpenNlpDocument.Builder document = OpenNlpDocument.newBuilder()
        .setDocId(input.getDocId())
        .setRawText(input.getRawText());
    if (input.hasMetadata()) {
      document.setMetadata(input.getMetadata());
    }
    return document.build();
  }

  /** Returns the effective steps in the analyzer's execution order. */
  private List<PipelineStep> ordered(Set<PipelineStep> steps) {
    return STEP_ORDER.stream().filter(steps::contains).toList();
  }

  /** Returns the latest step in canonical execution order. */
  private PipelineStep terminalStep(Set<PipelineStep> steps) {
    PipelineStep terminal = PipelineStep.PIPELINE_STEP_UNSPECIFIED;
    for (PipelineStep step : STEP_ORDER) {
      if (steps.contains(step)) {
        terminal = step;
      }
    }
    return terminal;
  }

  /** Returns an enum-set intersection. */
  private EnumSet<PipelineStep> intersection(
      Set<PipelineStep> left, Set<PipelineStep> right) {
    final EnumSet<PipelineStep> result = EnumSet.noneOf(PipelineStep.class);
    result.addAll(left);
    result.retainAll(right);
    return result;
  }

  /** Cancels submitted work after client cancellation or coordinator failure. */
  private void cancel(List<Future<BranchOutcome>> futures) {
    for (Future<BranchOutcome> future : futures) {
      future.cancel(true);
    }
  }

  /** Returns whether a layer must be supplied directly to the shape assembler. */
  private boolean isExtraLayer(String id) {
    return EXTRA_LAYER_ORDER.contains(id);
  }

  /** Reproduces the original pipeline order for step-emitted layers. */
  private int extraLayerRank(String id) {
    final int rank = EXTRA_LAYER_ORDER.indexOf(id);
    return rank < 0 ? EXTRA_LAYER_ORDER.size() : rank;
  }

  /** An isolated unit of pipeline work. */
  private record Branch(
      BranchKind kind,
      PipelineStep terminalStep,
      Set<PipelineStep> ownedSteps,
      Set<PipelineStep> runSteps,
      AnalyzeDocumentRequest request) {
  }

  /** A completed branch or its local failure. */
  private record BranchOutcome(
      Branch branch, AnalyzeDocumentResponse response, RuntimeException failure) {
  }

  /** Groups pipeline steps whose structured results are merged together. */
  private enum BranchKind {
    BACKBONE,
    SUBWORD,
    NER,
    POS,
    LINGUISTIC_GRAPH,
    TEXT_ENRICHMENT,
    DOCUMENT_CATEGORY,
    SENTIMENT,
    PARSE,
    EMBED,
    CHUNK
  }

  /** Executes one branch against an isolated copy of the completed backbone. */
  @FunctionalInterface
  interface BranchAnalyzer {
    AnalyzeDocumentResponse analyze(
        AnalyzeDocumentRequest request,
        Set<PipelineStep> steps,
        OpenNlpDocument backbone);
  }
}
