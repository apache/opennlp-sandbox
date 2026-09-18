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

package org.apache.opennlp.examples.grpc;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;

import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.ChunkingStrategySelector;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexRequest;
import org.apache.opennlp.grpc.v1.EmbeddingSelector;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.LayerIdentity;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.OpenNlpSearchServiceGrpc;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.SearchHit;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;
import org.apache.opennlp.grpc.v1.SearchProviderSelector;
import org.apache.opennlp.grpc.v1.StandardChunkingStrategy;
import org.apache.opennlp.grpc.v1.StandardLayer;
import org.apache.opennlp.grpc.v1.StandardSearchProvider;
import org.apache.opennlp.grpc.v1.StemmerAlgorithm;
import org.apache.opennlp.grpc.v1.StemmerSpec;
import org.apache.opennlp.grpc.v1.TermVectorSpec;
import org.apache.opennlp.grpc.v1.TermVectorMode;

/**
 * Analyzes documents, builds a TurboQuant index, and searches it through gRPC.
 *
 * <p>Java twin of the Python quickstart: it discovers the configured embedding
 * model, analyzes a three-document corpus into the typed document shape,
 * creates a process-local TurboQuant index in the server, and prints every
 * ranked hit of a document-shaped semantic query.</p>
 *
 * <p>Arguments: {@code --target host:port} (default {@code localhost:7071}),
 * {@code --embedding-model id} (inferred when exactly one is configured), and
 * {@code --cleanup} to delete the temporary index afterwards.</p>
 */
public final class AnalyzeAndSearch {

  private static final String[][] CORPUS = {
      {"habeas",
          "The writ of habeas corpus protects a prisoner from unlawful detention. "
              + "A court may order the custodian to release the prisoner."},
      {"appeal",
          "The appellate court reviews the trial record for reversible error. "
              + "A timely notice preserves the right to appeal."},
      {"zoning",
          "A city may regulate rooftop apiaries through its zoning code. "
              + "The applicant requested a variance for three beehives."},
  };

  private static final List<PipelineStep> BASE_STEPS = List.of(
      PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT,
      PipelineStep.PIPELINE_STEP_SENTENCE_DETECT,
      PipelineStep.PIPELINE_STEP_TOKENIZE,
      PipelineStep.PIPELINE_STEP_POS_TAG,
      PipelineStep.PIPELINE_STEP_LEMMATIZE,
      PipelineStep.PIPELINE_STEP_STEM,
      PipelineStep.PIPELINE_STEP_TERM_VECTOR);

  private AnalyzeAndSearch() {
  }

  /**
   * Runs the example.
   *
   * @param args Command-line options described in the class documentation.
   */
  public static void main(String[] args) {
    String target = "localhost:7071";
    String requestedModel = null;
    boolean cleanup = false;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--target" -> target = args[++i];
        case "--embedding-model" -> requestedModel = args[++i];
        case "--cleanup" -> cleanup = true;
        default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
      }
    }
    ManagedChannel channel =
        Grpc.newChannelBuilder(target, InsecureChannelCredentials.create()).build();
    try {
      run(channel, requestedModel, cleanup);
    } catch (StatusRuntimeException e) {
      System.err.println("gRPC " + e.getStatus().getCode() + ": "
          + (e.getStatus().getDescription() == null
              ? "request failed" : e.getStatus().getDescription()));
      System.exit(1);
    } finally {
      channel.shutdownNow();
      try {
        channel.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void run(ManagedChannel channel, String requestedModel, boolean cleanup) {
    OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub analysis =
        OpenNlpAnalysisServiceGrpc.newBlockingStub(channel);
    OpenNlpSearchServiceGrpc.OpenNlpSearchServiceBlockingStub search =
        OpenNlpSearchServiceGrpc.newBlockingStub(channel);

    GetServiceInfoResponse info = analysis.withDeadlineAfter(10, TimeUnit.SECONDS)
        .getServiceInfo(GetServiceInfoRequest.getDefaultInstance());
    System.out.printf("Connected to OpenNLP %s, API %s, service %s%n",
        info.getOpennlpVersion(), info.getApiVersion(), info.getServiceVersion());
    System.out.printf("Server text limit: %,d UTF-8 bytes%n", info.getMaxTextBytes());

    String modelId = selectEmbeddingModel(analysis, requestedModel);
    System.out.println("Embedding model: " + modelId);

    List<OpenNlpDocument> documents = analyzeDocuments(analysis, modelId);
    System.out.println("Analyzed " + documents.size() + " documents");
    printAnalysis(documents);

    IndexDocumentsResponse created = search.withDeadlineAfter(60, TimeUnit.SECONDS)
        .indexDocuments(IndexDocumentsRequest.newBuilder()
            .setDisplayName("Java quickstart corpus")
            .addAllDocuments(documents)
            .setEmbedding(EmbeddingSelector.newBuilder().setModelId(modelId))
            .setProvider(SearchProviderSelector.newBuilder()
                .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT))
            .build());
    String indexId = created.getIndex().getIndexId();
    System.out.printf("%nTurboQuant index %s: %d documents, %d chunks%n",
        indexId, created.getIndexedDocuments(), created.getIndexedChunks());
    if (!created.getIndex().getSupportsAllHits()) {
      throw new IllegalStateException("the TurboQuant index did not advertise exhaustive search");
    }

    try {
      SearchIndexResponse response = search.withDeadlineAfter(60, TimeUnit.SECONDS)
          .searchIndex(SearchIndexRequest.newBuilder()
              .setIndexId(indexId)
              .setQuery(OpenNlpDocument.newBuilder()
                  .setDocId("query-1")
                  .setRawText("Which court remedy protects a prisoner from unlawful custody?"))
              .setAllHits(true)
              .build());
      System.out.printf("%nSearch results (%d exhaustive hits)%n", response.getHitsCount());
      int rank = 1;
      for (SearchHit hit : response.getHitsList()) {
        System.out.printf("  %2d. %+.4f  %s/%s: %s%n", rank++, hit.getScore(),
            hit.getDocumentId(), hit.getChunkGroupId(), hit.getIndexedText());
      }
      if (response.getTruncated()) {
        System.out.println("  Results were truncated by the server response-byte limit.");
      }
    } finally {
      if (cleanup) {
        boolean deleted = search.withDeadlineAfter(10, TimeUnit.SECONDS)
            .deleteSearchIndex(DeleteSearchIndexRequest.newBuilder()
                .setIndexId(indexId).build())
            .getDeleted();
        if (!deleted) {
          throw new IllegalStateException("temporary index " + indexId + " was not deleted");
        }
        System.out.printf("%nDeleted temporary index %s%n", indexId);
      }
    }
  }

  private static String selectEmbeddingModel(
      OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub analysis,
      String requested) {
    List<String> models = analysis.withDeadlineAfter(10, TimeUnit.SECONDS)
        .listModelBundles(ListModelBundlesRequest.getDefaultInstance())
        .getBundlesList().stream()
        .flatMap(bundle -> bundle.getModelsList().stream())
        .filter(model -> model.getEmbeddingDimension() > 0)
        .map(model -> model.getName())
        .distinct()
        .sorted()
        .collect(Collectors.toList());
    if (requested != null) {
      if (!models.contains(requested)) {
        throw new IllegalArgumentException("embedding model '" + requested
            + "' is not configured; available: "
            + (models.isEmpty() ? "none" : String.join(", ", models)));
      }
      return requested;
    }
    if (models.size() == 1) {
      return models.get(0);
    }
    if (models.isEmpty()) {
      throw new IllegalStateException(
          "the server has no embedding model; configure one before running search");
    }
    throw new IllegalArgumentException(
        "the server has several embedding models; pass --embedding-model with one of: "
            + String.join(", ", models));
  }

  private static List<OpenNlpDocument> analyzeDocuments(
      OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub analysis,
      String modelId) {
    AnalysisProfile profile = AnalysisProfile.newBuilder()
        .addAllSteps(BASE_STEPS)
        .setStemmer(StemmerSpec.newBuilder()
            .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_SNOWBALL)
            .setLanguage("en"))
        .setTermVector(TermVectorSpec.newBuilder()
            .setMode(TermVectorMode.TERM_VECTOR_MODE_FULL)
            .setSourceLayer(LayerIdentity.newBuilder()
                .setStandard(StandardLayer.STANDARD_LAYER_LEMMAS)))
        .build();
    List<ChunkEmbedConfigEntry> chunkConfigs = List.of(
        ChunkEmbedConfigEntry.newBuilder()
            .setConfigId("sentences")
            .setResultSetName("Sentence chunks")
            .addEmbeddingModelIds(modelId)
            .setChunking(ChunkingSpec.newBuilder()
                .setStrategy(ChunkingStrategySelector.newBuilder()
                    .setStandard(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_SENTENCE)))
            .build(),
        ChunkEmbedConfigEntry.newBuilder()
            .setConfigId("token-windows")
            .setResultSetName("Eight-token windows")
            .addEmbeddingModelIds(modelId)
            .setChunking(ChunkingSpec.newBuilder()
                .setStrategy(ChunkingStrategySelector.newBuilder()
                    .setStandard(StandardChunkingStrategy.STANDARD_CHUNKING_STRATEGY_TOKEN))
                .setChunkSize(8)
                .setChunkOverlap(2))
            .build());
    return java.util.Arrays.stream(CORPUS)
        .map(entry -> analysis.withDeadlineAfter(60, TimeUnit.SECONDS)
            .analyzeDocument(AnalyzeDocumentRequest.newBuilder()
                .setDocument(OpenNlpDocument.newBuilder()
                    .setDocId(entry[0])
                    .setRawText(entry[1]))
                .setProfile(profile)
                .setOptions(AnalysisOptions.newBuilder().setIncludeProbabilities(true))
                .addAllChunkEmbedConfigs(chunkConfigs)
                .build())
            .getDocument())
        .collect(Collectors.toList());
  }

  private static void printAnalysis(List<OpenNlpDocument> documents) {
    System.out.printf("%nDocument analysis%n");
    for (OpenNlpDocument document : documents) {
      String layerIds = document.getLayers().getLayersList().stream()
          .map(layer -> layer.getId())
          .collect(Collectors.joining(", "));
      System.out.printf("  %s: %d sentences, %d tokens, %d unique lemmas%n",
          document.getDocId(),
          document.getAnalytics().getTotalSentences(),
          document.getAnalytics().getTotalTokens(),
          document.getAnalytics().getUniqueLemmaCount());
      String tokenSummary = document.getSentences(0).getTokensList().stream()
          .limit(8)
          .map(token -> token.getText() + "/" + token.getPosTag() + "/" + token.getLemma())
          .collect(Collectors.joining(" "));
      System.out.println("    first tokens: " + tokenSummary);
      System.out.println("    layers: " + layerIds);
    }
  }
}
