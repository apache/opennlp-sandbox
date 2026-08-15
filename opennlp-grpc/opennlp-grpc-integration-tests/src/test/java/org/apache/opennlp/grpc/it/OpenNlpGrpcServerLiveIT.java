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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
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
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;
import org.apache.opennlp.grpc.v1.AnalyzeStreamDocument;
import org.apache.opennlp.grpc.v1.AnalyzeStreamRequest;
import org.apache.opennlp.grpc.v1.AnalyzeStreamResponse;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.ComponentType;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.ModelDescriptor;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.StandardLayer;
import org.apache.opennlp.grpc.v1.StemmerAlgorithm;
import org.apache.opennlp.grpc.v1.StemmerSpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    assertTrue(info.getSupportedLayersList().contains(StandardLayer.STANDARD_LAYER_SENTENCES));
    assertTrue(info.getSupportedLayersList().contains(StandardLayer.STANDARD_LAYER_TOKENS));
    assertTrue(info.getSupportedLayersList().contains(StandardLayer.STANDARD_LAYER_STEMS));
  }

  @Test
  void preservesDocumentShapeAndUtf16Spans() {
    final AnalyzeDocumentRequest request = documentRequest("contract-unary", unicodeText());
    final AnalyzeDocumentResponse response = client.analyzeDocument(request);
    final OpenNlpDocument document = response.getDocument();

    assertEquals(request.getDocument().getDocId(), document.getDocId());
    assertEquals(request.getDocument().getMetadata(), document.getMetadata());
    assertEquals(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT,
        document.getOffsetEncoding());
    for (var sentence : document.getSentencesList()) {
      assertSpanSlicesText(document.getRawText(), sentence.getSentenceSpan());
      for (var token : sentence.getTokensList()) {
        assertEquals(token.getText(), slice(document.getRawText(), token.getAnnotationSpan()));
      }
    }

    assertStandardLayer(document, StandardLayer.STANDARD_LAYER_SENTENCES,
        "opennlp:sentences");
    assertStandardLayer(document, StandardLayer.STANDARD_LAYER_TOKENS, "opennlp:tokens");
    assertStandardLayer(document, StandardLayer.STANDARD_LAYER_WORD_TYPES,
        "opennlp:word-types");
    final AnnotationLayer stems = assertStandardLayer(document,
        StandardLayer.STANDARD_LAYER_STEMS, "opennlp:stems");
    assertTrue(stems.getStemValues().getAnnotationsList().stream()
        .anyMatch(stem -> "cats".equals(slice(document.getRawText(), stem.getSpan()))
            && "cat".equals(stem.getStem())));
  }

  @Test
  void analysisStreamMatchesUnaryAndContinuesAfterADocumentError() throws Exception {
    final AnalyzeDocumentRequest firstRequest = documentRequest("contract-stream-1",
        unicodeText());
    final AnalyzeDocumentRequest secondRequest = documentRequest("contract-stream-2",
        "Dogs bark.");
    final AnalyzeDocumentResponse firstUnary = client.analyzeDocument(firstRequest);
    final AnalyzeDocumentResponse secondUnary = client.analyzeDocument(secondRequest);
    final var responses = new CopyOnWriteArrayList<AnalyzeStreamResponse>();
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    final CountDownLatch done = new CountDownLatch(1);

    final StreamObserver<AnalyzeStreamRequest> requests = harness.asyncClient()
        .analyzeStream(new StreamObserver<>() {
          @Override
          public void onNext(AnalyzeStreamResponse response) {
            responses.add(response);
          }

          @Override
          public void onError(Throwable error) {
            failure.set(error);
            done.countDown();
          }

          @Override
          public void onCompleted() {
            done.countDown();
          }
        });

    requests.onNext(AnalyzeStreamRequest.newBuilder()
        .setConfiguration(streamConfiguration(firstRequest))
        .build());
    requests.onNext(streamDocument(41, firstRequest.getDocument()));
    requests.onNext(streamDocument(42, OpenNlpDocument.newBuilder()
        .setDocId("too-long")
        .setRawText("x".repeat(80))
        .build()));
    requests.onNext(streamDocument(43, secondRequest.getDocument()));
    requests.onCompleted();

    assertTrue(done.await(10, TimeUnit.SECONDS));
    assertNull(failure.get());
    assertEquals(3, responses.size());
    assertEquals(firstUnary, responseFor(responses, 41).getOk());
    assertEquals(Status.Code.INVALID_ARGUMENT.value(),
        responseFor(responses, 42).getError().getCode());
    assertEquals(secondUnary, responseFor(responses, 43).getOk());
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

  private String unicodeText() {
    return "Running cats \ud83d\ude00 jump. Dogs bark.";
  }

  private AnalyzeDocumentRequest documentRequest(String docId, String text) {
    return AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder()
            .setDocId(docId)
            .setRawText(text)
            .setMetadata(Struct.newBuilder()
                .putFields("source", Value.newBuilder().setStringValue("live-it").build())
                .build())
            .build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("document-contract")
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addSteps(PipelineStep.PIPELINE_STEP_STEM)
            .setTokenizerEngine("uax29")
            .setStemmer(StemmerSpec.newBuilder()
                .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_SNOWBALL)
                .setLanguage("en")
                .build())
            .build())
        .setOptions(AnalysisOptions.newBuilder()
            .setMaxTextLength(64)
            .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
            .build())
        .build();
  }

  private AnalyzeStreamConfiguration streamConfiguration(AnalyzeDocumentRequest request) {
    return AnalyzeStreamConfiguration.newBuilder()
        .setProfile(request.getProfile())
        .setOptions(request.getOptions())
        .build();
  }

  private AnalyzeStreamRequest streamDocument(long sequence, OpenNlpDocument document) {
    return AnalyzeStreamRequest.newBuilder()
        .setDocument(AnalyzeStreamDocument.newBuilder()
            .setSequence(sequence)
            .setDocument(document)
            .build())
        .build();
  }

  private AnalyzeStreamResponse responseFor(List<AnalyzeStreamResponse> responses,
      long sequence) {
    return responses.stream()
        .filter(response -> response.getSequence() == sequence)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No response for sequence " + sequence));
  }

  private AnnotationLayer assertStandardLayer(OpenNlpDocument document,
      StandardLayer standard, String id) {
    final AnnotationLayer layer = document.getLayers().getLayersList().stream()
        .filter(candidate -> candidate.getIdentity().getStandard() == standard)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No layer for " + standard));
    assertEquals(id, layer.getId());
    return layer;
  }

  private void assertSpanSlicesText(String text, AnnotationSpan span) {
    assertTrue(span.getStart() >= 0);
    assertTrue(span.getEnd() <= text.length());
    assertTrue(span.getStart() < span.getEnd());
    assertFalse(slice(text, span).isBlank());
  }

  private String slice(String text, AnnotationSpan span) {
    return text.substring(span.getStart(), span.getEnd());
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
    private static EmbedResponse embedding(EmbedRequest request) {
      return EmbedResponse.newBuilder()
          .addEmbeddings(request.getInputs().length())
          .addEmbeddings(1f)
          .addEmbeddings(1f)
          .build();
    }

    @Override
    public void embed(EmbedRequest request, StreamObserver<EmbedResponse> observer) {
      observer.onNext(embedding(request));
      observer.onCompleted();
    }

    @Override
    public StreamObserver<EmbedRequest> embedStream(StreamObserver<EmbedResponse> observer) {
      // The provider now batches via the bidi EmbedStream RPC; echo one response per request.
      return new StreamObserver<>() {
        @Override
        public void onNext(EmbedRequest request) {
          observer.onNext(embedding(request));
        }

        @Override
        public void onError(Throwable t) {
          observer.onError(t);
        }

        @Override
        public void onCompleted() {
          observer.onCompleted();
        }
      };
    }
  }
}
