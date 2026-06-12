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
package org.apache.opennlp.grpc.it;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.tei.v1.EmbedGrpc;
import org.apache.opennlp.grpc.tei.v1.EmbedRequest;
import org.apache.opennlp.grpc.tei.v1.EmbedResponse;
import org.apache.opennlp.grpc.tei.v1.InfoGrpc;
import org.apache.opennlp.grpc.tei.v1.InfoRequest;
import org.apache.opennlp.grpc.tei.v1.InfoResponse;
import org.apache.opennlp.grpc.tei.v1.ModelType;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.ComponentType;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.ModelDescriptor;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Black-box integration test against the deployable server: launches the shaded
 * {@code opennlp-grpc-server} SNAPSHOT jar as a separate JVM process, with the
 * {@code opennlp-grpc-backend-tei} module on its classpath and a stub TEI gRPC server
 * running inside this test JVM as the remote embedding backend.
 *
 * <p>This exercises the full deployment topology over real network sockets:</p>
 *
 * <pre>
 * test client --gRPC--&gt; opennlp-grpc-server process --gRPC--&gt; stub TEI server
 * </pre>
 *
 * <p>No model paths are configured, so the test also covers loading the bundled
 * sentence detector and tokenizer models from the shaded jar itself.</p>
 */
class OpenNlpGrpcServerLiveIT {

  private static final int EMBEDDING_DIMENSION = 3;
  private static final String TEXT =
      "The driver got badly injured by the accident. He was taken to the hospital!";

  private static Server teiServer;
  private static LiveServerHarness harness;
  private static OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceBlockingStub client;

  @BeforeAll
  static void startTopology() throws Exception {
    teiServer = ServerBuilder.forPort(0)
        .addService(new StubTeiInfoService())
        .addService(new StubTeiEmbedService())
        .build()
        .start();

    final Properties config = new Properties();
    config.setProperty("model.embedder.backend", "tei");
    config.setProperty("model.embedder.minilm.tei.target", "localhost:" + teiServer.getPort());
    config.setProperty("model.embedder.tei.deadline_ms", "10000");
    harness = LiveServerHarness.start(config);
    client = harness.client();
  }

  @AfterAll
  static void stopTopology() throws Exception {
    if (harness != null) {
      harness.close();
    }
    if (teiServer != null) {
      teiServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void serviceInfoReportsEmbeddingSupport() {
    final var info = client.getServiceInfo(GetServiceInfoRequest.getDefaultInstance());
    assertEquals("v1", info.getApiVersion());
    assertTrue(info.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT));
    assertTrue(info.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT));
    assertTrue(info.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_TOKENIZE));
    assertTrue(info.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_POS_TAG));
    assertTrue(info.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_LEMMATIZE));
    assertTrue(info.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_EMBED));
  }

  @Test
  void modelCatalogReportsBackendIds() {
    final var bundles = client.listModelBundles(ListModelBundlesRequest.getDefaultInstance());
    assertEquals(1, bundles.getBundlesCount());
    final List<ModelDescriptor> models = bundles.getBundles(0).getModelsList();

    final ModelDescriptor embedder = models.stream()
        .filter(m -> m.getComponentType() == ComponentType.COMPONENT_TYPE_EMBEDDER)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no embedder in catalog: " + models));
    assertEquals("minilm", embedder.getName());
    assertEquals("tei", embedder.getBackendId());
    assertEquals(EMBEDDING_DIMENSION, embedder.getEmbeddingDimension());

    for (ComponentType classicType : List.of(
        ComponentType.COMPONENT_TYPE_SENTENCE_DETECTOR,
        ComponentType.COMPONENT_TYPE_TOKENIZER,
        ComponentType.COMPONENT_TYPE_POS_TAGGER,
        ComponentType.COMPONENT_TYPE_LEMMATIZER)) {
      final ModelDescriptor descriptor = models.stream()
          .filter(m -> m.getComponentType() == classicType)
          .findFirst()
          .orElseThrow(() -> new AssertionError("no " + classicType + " in catalog: " + models));
      assertEquals("opennlp-me", descriptor.getBackendId());
    }
  }

  @Test
  void analyzesDocumentWithBundledModels() {
    final var response = client.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setDocId("live-1").setRawText(TEXT).build())
        .build());

    assertEquals("live-1", response.getDocument().getDocId());
    assertEquals(2, response.getDocument().getSentencesCount());
    assertTrue(response.getDocument().getSentences(0).getTokensCount() > 0);
  }

  /**
   * Language detection runs from the model bundled inside the shaded server jar,
   * exercised across the process boundary.
   */
  @Test
  void detectsLanguageWithBundledModel() {
    final var response = client.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setDocId("live-lang").setRawText(TEXT).build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("lang")
            .addSteps(PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .build())
        .build());

    assertEquals("eng", response.getDocument().getDetectedLanguage());
    assertTrue(response.getDocument().getLanguageConfidence() > 0.0f);
  }

  /**
   * POS tagging and lemmatization run from models bundled inside the shaded server jar,
   * exercised across the process boundary.
   */
  @Test
  void posTagsAndLemmatizesWithBundledModels() {
    final var response = client.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setDocId("live-pos").setRawText(TEXT).build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("pos-lemma")
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
            .addSteps(PipelineStep.PIPELINE_STEP_LEMMATIZE)
            .build())
        .build());

    assertEquals(2, response.getDocument().getSentencesCount());
    for (var sentence : response.getDocument().getSentencesList()) {
      assertTrue(sentence.getTokensCount() > 0);
      for (var token : sentence.getTokensList()) {
        assertTrue(token.hasPosTag(), "token '" + token.getText() + "' has no POS tag");
        assertTrue(token.hasLemma(), "token '" + token.getText() + "' has no lemma");
      }
    }
    // "got" lemmatizes to "get" with the bundled English UD lemmatizer.
    final var firstSentence = response.getDocument().getSentences(0);
    boolean sawGot = false;
    for (var token : firstSentence.getTokensList()) {
      if ("got".equals(token.getText())) {
        sawGot = true;
        assertEquals("VERB", token.getPosTag());
        assertEquals("get", token.getLemma());
      }
    }
    assertTrue(sawGot, "expected token 'got' in: " + firstSentence);
  }

  @Test
  void embedsSentencesThroughRemoteTeiBackend() {
    final var response = client.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setDocId("live-2").setRawText(TEXT).build())
        .setProfile(embedProfile())
        .setOptions(AnalysisOptions.newBuilder().setEmbeddingModelId("minilm").build())
        .build());

    assertEquals(2, response.getDocument().getSentencesCount());
    assertEquals(2, response.getDocument().getEmbeddingsCount());
    for (var embedding : response.getDocument().getEmbeddingsList()) {
      assertEquals("minilm", embedding.getModelId());
      assertEquals(EMBEDDING_DIMENSION, embedding.getVectorCount());
      // The stub returns length(text) as the first component; TEXT is pure ASCII so the
      // (default UTF-8) span width equals the character count.
      final AnnotationSpan span = embedding.getSourceSpan();
      assertEquals(span.getEnd() - span.getStart(), (int) embedding.getVector(0));
    }
  }

  @Test
  void buildsChunkEmbeddingGroupsThroughRemoteTeiBackend() {
    final var response = client.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setDocId("live-3").setRawText(TEXT).build())
        .addChunkEmbedConfigs(ChunkEmbedConfigEntry.newBuilder()
            .setConfigId("rag-sentences")
            .setChunking(ChunkingSpec.newBuilder().setAlgorithm("sentence").build())
            .addEmbeddingModelIds("minilm")
            .build())
        .build());

    assertEquals(1, response.getDocument().getChunkEmbeddingGroupsCount());
    final ChunkEmbeddingGroup group = response.getDocument().getChunkEmbeddingGroups(0);
    assertEquals("rag-sentences", group.getGroupId());
    assertEquals(2, group.getChunksCount());
    for (var chunk : group.getChunksList()) {
      assertEquals(1, chunk.getEmbeddingsCount());
      assertEquals("minilm", chunk.getEmbeddings(0).getModelId());
      assertEquals(EMBEDDING_DIMENSION, chunk.getEmbeddings(0).getVectorCount());
      assertEquals(chunk.getTextContent().length(), (int) chunk.getEmbeddings(0).getVector(0));
    }
  }

  @Test
  void unknownEmbeddingModelIsRejectedWithNotFound() {
    final StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
        () -> client.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder().setRawText(TEXT).build())
            .setProfile(embedProfile())
            .setOptions(AnalysisOptions.newBuilder().setEmbeddingModelId("missing").build())
            .build()));
    assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode());
  }

  private static AnalysisProfile embedProfile() {
    return AnalysisProfile.newBuilder()
        .setProfileId("live-embed")
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
        .addSteps(PipelineStep.PIPELINE_STEP_EMBED)
        .build();
  }

  /** TEI Info stub reporting an embedding model. */
  private static final class StubTeiInfoService extends InfoGrpc.InfoImplBase {
    @Override
    public void info(InfoRequest request, StreamObserver<InfoResponse> observer) {
      observer.onNext(InfoResponse.newBuilder()
          .setVersion("live-it")
          .setModelId("stub/live-model")
          .setModelDtype("float32")
          .setModelType(ModelType.MODEL_TYPE_EMBEDDING)
          .build());
      observer.onCompleted();
    }
  }

  /** TEI Embed stub returning {@code [length(inputs), 1, 1]} for every request. */
  private static final class StubTeiEmbedService extends EmbedGrpc.EmbedImplBase {
    @Override
    public void embed(EmbedRequest request, StreamObserver<EmbedResponse> observer) {
      observer.onNext(EmbedResponse.newBuilder()
          .addEmbeddings(request.getInputs().length())
          .addEmbeddings(1f)
          .addEmbeddings(1f)
          .build());
      observer.onCompleted();
    }
  }
}
