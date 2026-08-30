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

import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import opennlp.embeddings.corpus.CasePassage;
import opennlp.embeddings.index.TurboQuantIndex;
import org.apache.opennlp.grpc.search.turboquant.TurboQuantBundleDigest;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;
import org.apache.opennlp.grpc.v1.AnalyzeStreamDocument;
import org.apache.opennlp.grpc.v1.AnalyzeStreamRequest;
import org.apache.opennlp.grpc.v1.AnalyzeStreamResponse;
import org.apache.opennlp.grpc.v1.GrpcStatusCode;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.ChunkEmbedConfigEntry;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.ChunkingSpec;
import org.apache.opennlp.grpc.v1.ComponentType;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.LayerIdentity;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.ListDictionaryFormatsRequest;
import org.apache.opennlp.grpc.v1.ModelDescriptor;
import org.apache.opennlp.grpc.v1.Normalizer;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.ListSearchIndexesRequest;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.SentenceDetectorSelector;
import org.apache.opennlp.grpc.v1.StandardLayer;
import org.apache.opennlp.grpc.v1.StandardResource;
import org.apache.opennlp.grpc.v1.StandardSentenceDetectorEngine;
import org.apache.opennlp.grpc.v1.StandardDictionaryFormat;
import org.apache.opennlp.grpc.v1.StandardTokenizerEngine;
import org.apache.opennlp.grpc.v1.StemmerAlgorithm;
import org.apache.opennlp.grpc.v1.StemmerSpec;
import org.apache.opennlp.grpc.v1.TermLayerSpec;
import org.apache.opennlp.grpc.v1.TermVectorSpec;
import org.apache.opennlp.grpc.v1.TokenizerSelector;
import org.apache.opennlp.grpc.v1.VectorNormalization;
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
 * <p>No classic model paths are configured, so the test also covers loading the
 * bundled sentence detector and tokenizer models from the shaded jar itself. A tiny
 * hunspell dictionary is configured to exercise non-model resource discovery through
 * the deployable artifact.</p>
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
    teiServer = StubTeiBackend.start();

    final Properties config = new Properties();
    config.setProperty("server.max_text_bytes", "4096");
    config.setProperty("model.embedder.minilm.tei.target", "localhost:" + teiServer.getPort());
    config.setProperty("model.embedder.minilm.tei.vector_space_id", "minilm-live-v1");
    config.setProperty("model.embedder.tei.deadline_ms", "10000");
    configureSearchBundle(config);
    final Path hunspellDir = Files.createTempDirectory("opennlp-grpc-live-hunspell-");
    final Path affix = hunspellDir.resolve("tiny.aff");
    final Path words = hunspellDir.resolve("tiny.dic");
    Files.writeString(affix, String.join("\n",
        "SET UTF-8",
        "SFX S Y 1",
        "SFX S 0 s ."));
    Files.writeString(words, String.join("\n", "2", "cat/S", "dog/S"));
    config.setProperty("model.hunspell.tiny.affix_path", affix.toString());
    config.setProperty("model.hunspell.tiny.dictionary_path", words.toString());
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
    assertEquals("3.0.0-SNAPSHOT", info.getServiceVersion());
    assertTrue(info.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_LANGUAGE_DETECT));
    assertTrue(info.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT));
    assertTrue(info.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_TOKENIZE));
    assertTrue(info.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_POS_TAG));
    assertTrue(info.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_LEMMATIZE));
    assertTrue(info.getSupportedStepsList().contains(PipelineStep.PIPELINE_STEP_EMBED));
    assertTrue(info.getSupportedLayersList().contains(StandardLayer.STANDARD_LAYER_SENTENCES));
    assertTrue(info.getSupportedLayersList().contains(StandardLayer.STANDARD_LAYER_TOKENS));
    assertTrue(info.getSupportedLayersList().contains(StandardLayer.STANDARD_LAYER_STEMS));
    assertEquals(4096, info.getMaxTextBytes());
    final var hunspell = info.getConfiguredResourcesList().stream()
        .filter(resource -> resource.getIdentity().getStandard()
            == StandardResource.STANDARD_RESOURCE_HUNSPELL_DICTIONARY)
        .findFirst().orElseThrow();
    assertEquals("tiny", hunspell.getResourceId());
    assertTrue(hunspell.getIsDefault());
  }

  @Test
  void searchesPersistedTurboQuantBundleThroughShadedServer() {
    final var searchClient = harness.searchClient();
    final var indexes = searchClient.listSearchIndexes(
        ListSearchIndexesRequest.getDefaultInstance());

    assertEquals(1, indexes.getIndexesCount());
    assertEquals("legal-demo", indexes.getIndexes(0).getIndexId());
    assertEquals("minilm-live-v1",
        indexes.getIndexes(0).getEmbeddingRoute().getVectorSpaceId());
    assertTrue(indexes.getIndexes(0).getSupportsAllHits());

    final var response = searchClient.searchIndex(SearchIndexRequest.newBuilder()
        .setIndexId("legal-demo")
        .setQuery(OpenNlpDocument.newBuilder()
            .setDocId("live-query")
            .setRawText("remedy"))
        .setAllHits(true)
        .build());

    assertEquals("legal-demo", response.getIndex().getIndexId());
    assertEquals(2, response.getHitsCount());
    assertEquals(2, response.getSourceDocumentsCount());
    assertEquals("remedy", response.getHits(0).getDocumentId());
    assertEquals(response.getHits(0).getDocumentId(),
        response.getSourceDocuments(0).getDocId());
    assertEquals("A remedy follows a violation.",
        response.getSourceDocuments(0).getRawText());
    assertEquals("minilm-live-v1", response.getQueryEmbeddingRoute().getVectorSpaceId());
  }

  @Test
  void discoversServiceLoadedDictionaryFormatsThroughShadedServer() {
    final var response = harness.vocabularyClient().listDictionaryFormats(
        ListDictionaryFormatsRequest.getDefaultInstance());

    assertFalse(response.getWritesEnabled());
    assertEquals(List.of(
        StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_HEADWORD_DEFINITION_TSV,
        StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_HEADWORD_LINES,
        StandardDictionaryFormat.STANDARD_DICTIONARY_FORMAT_OPENNLP_XML),
        response.getFormatsList().stream()
            .map(format -> format.getFormat().getStandard())
            .toList());
    assertEquals(1, response.getMaxConcurrentWrites());
  }

  @Test
  void servesSearchWorkbenchAndJsonSearchThroughShadedWebapp() throws Exception {
    try (LiveWebAppHarness webapp = LiveWebAppHarness.start(harness.grpcTarget())) {
      final HttpClient http = HttpClient.newHttpClient();
      final HttpResponse<String> page = http.send(HttpRequest.newBuilder(
          URI.create(webapp.baseUri() + "/")).GET().build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, page.statusCode());
      assertTrue(page.body().contains("Search, then inspect the evidence behind every score"));
      assertTrue(page.body().contains("document-window-position"));
      assertTrue(page.body().contains("Download JSON"));

      final HttpResponse<String> indexes = http.send(HttpRequest.newBuilder(
          URI.create(webapp.baseUri() + "/api/v1/search-indexes")).GET().build(),
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, indexes.statusCode());
      assertTrue(indexes.body().contains("\"indexId\":\"legal-demo\""));
      assertTrue(indexes.body().contains("\"supportsAllHits\":true"));

      final HttpResponse<String> search = http.send(HttpRequest.newBuilder(
          URI.create(webapp.baseUri() + "/api/v1/search"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("""
              {"indexId":"legal-demo","query":{"docId":"web-query",\
              "rawText":"remedy"},"allHits":true}
              """))
          .build(), HttpResponse.BodyHandlers.ofString());
      assertEquals(200, search.statusCode());
      assertTrue(search.body().contains("\"documentId\":\"remedy\""));
      assertTrue(search.body().contains("\"rawText\":\"A remedy follows a violation.\""),
          search.body());
    }
  }

  private static void configureSearchBundle(Properties config) throws Exception {
    final Path indexDirectory = Files.createTempDirectory("opennlp-grpc-live-search-index-")
        .toAbsolutePath().normalize();
    final TurboQuantIndex index = new TurboQuantIndex(EMBEDDING_DIMENSION, 4, 42);
    index.add("remedy", new float[] {6, 1, 1});
    index.add("longer", new float[] {24, 1, 1});
    index.freeze();
    index.write(indexDirectory);

    final Path passages = Files.createTempFile("opennlp-grpc-live-passages-", ".jsonl")
        .toAbsolutePath().normalize();
    CasePassage.writeJsonl(List.of(
        new CasePassage("remedy", "Remedy v. State", "1 Test 1", "2026", "1",
            "A remedy follows a violation."),
        new CasePassage("longer", "Length v. State", "2 Test 2", "2026", "2",
            "A much longer unrelated source passage.")), passages);

    final Properties descriptor = new Properties();
    descriptor.setProperty("format.version", "1");
    descriptor.setProperty("index.id", "legal-demo");
    descriptor.setProperty("display.name", "Legal demo");
    descriptor.setProperty("provider.id", "turbo_quant");
    descriptor.setProperty("embedding.model.id", "minilm");
    descriptor.setProperty("embedding.backend.id", "tei");
    descriptor.setProperty("embedding.vector_space.id", "minilm-live-v1");
    descriptor.setProperty("dimension", Integer.toString(EMBEDDING_DIMENSION));
    descriptor.setProperty("metric", "cosine");
    descriptor.setProperty("corpus.title", "Hermetic legal examples");
    descriptor.setProperty("corpus.provenance", "Generated only for the live integration test");
    descriptor.setProperty("corpus.license.name", "Apache-2.0");
    descriptor.setProperty("corpus.artifact.sha256",
        TurboQuantBundleDigest.sha256(passages));
    descriptor.setProperty("bundle.artifact.sha256",
        TurboQuantBundleDigest.bundleArtifactHash(indexDirectory, passages));
    descriptor.setProperty("builder.id", "opennlp-live-it");
    descriptor.setProperty("builder.version", "1");
    descriptor.setProperty("preparation.config.sha256", "b".repeat(64));
    try (OutputStream output = Files.newOutputStream(
        indexDirectory.resolve("search-index.properties"))) {
      descriptor.store(output, null);
    }

    config.setProperty("search.indexes", "legal-demo");
    config.setProperty("search.index.legal-demo.provider", "turbo_quant");
    config.setProperty("search.index.legal-demo.directory", indexDirectory.toString());
    config.setProperty("search.index.legal-demo.passages", passages.toString());
  }

  @Test
  void configuredHunspellResourceRunsInShadedServer() {
    final String text = "cats dogs";
    final var response = client.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText(text))
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("live-hunspell")
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addSteps(PipelineStep.PIPELINE_STEP_STEM)
            .setTokenizer(TokenizerSelector.newBuilder()
                .setStandard(StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_WHITESPACE))
            .setStemmer(StemmerSpec.newBuilder()
                .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_HUNSPELL)))
        .setOptions(AnalysisOptions.newBuilder()
            .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT))
        .build());

    final AnnotationLayer stems = assertStandardLayer(response.getDocument(),
        StandardLayer.STANDARD_LAYER_STEMS, "opennlp:stems");
    assertEquals(List.of("cat", "dog"), stems.getStemValues().getAnnotationsList().stream()
        .map(stem -> stem.getStem()).toList());
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
    final AnnotationLayer termVectors = assertStandardLayer(document,
        StandardLayer.STANDARD_LAYER_TERM_VECTORS, "opennlp:term-vectors");
    assertEquals(StandardLayer.STANDARD_LAYER_TERMS,
        termVectors.getTermVectorValues().getSourceLayer().getStandard());
    assertEquals("court-folded",
        termVectors.getTermVectorValues().getSourceLayer().getQualifier());
    final AnnotationLayer terms = assertStandardLayer(document,
        StandardLayer.STANDARD_LAYER_TERMS, "opennlp:terms:court-folded");
    assertEquals("court-folded", terms.getIdentity().getQualifier());
    assertTrue(termVectors.getTermVectorValues().getAnnotationsList().stream()
        .anyMatch(vector -> "cat".equals(vector.getTerm()) && vector.getFrequency() == 1));
  }

  @Test
  void omitsInvisibleOnlyTermsInShadedServer() {
    final AnalyzeDocumentResponse response = client.analyzeDocument(
        AnalyzeDocumentRequest.newBuilder()
            .setDocument(OpenNlpDocument.newBuilder()
                .setDocId("empty-normalized-term")
                .setRawText("court \u200B law"))
            .setProfile(AnalysisProfile.newBuilder()
                .setProfileId("empty-normalized-term")
                .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
                .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
                .addSteps(PipelineStep.PIPELINE_STEP_TERM_VECTOR)
                .setTokenizer(TokenizerSelector.newBuilder()
                    .setStandard(StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_WHITESPACE))
                .addTermLayers(TermLayerSpec.newBuilder()
                    .setQualifier("folded")
                    .addNormalizers(
                        Normalizer.NORMALIZER_STRIP_INVISIBLE))
                .setTermVector(TermVectorSpec.newBuilder()
                    .setSourceLayer(LayerIdentity.newBuilder()
                        .setStandard(StandardLayer.STANDARD_LAYER_TERMS)
                        .setQualifier("folded"))))
            .setOptions(AnalysisOptions.newBuilder()
                .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT))
            .build());

    final AnnotationLayer terms = assertStandardLayer(response.getDocument(),
        StandardLayer.STANDARD_LAYER_TERMS, "opennlp:terms:folded");
    assertEquals(List.of("court", "law"), terms.getStringValues().getAnnotationsList().stream()
        .map(annotation -> annotation.getValue()).toList());

    final AnnotationLayer vectors = assertStandardLayer(response.getDocument(),
        StandardLayer.STANDARD_LAYER_TERM_VECTORS, "opennlp:term-vectors");
    assertEquals(List.of("court", "law"),
        vectors.getTermVectorValues().getAnnotationsList().stream()
            .map(annotation -> annotation.getTerm()).toList());
  }

  @Test
  void typedModelFreeSegmentationProducesExactDocumentLayers() {
    final String text = "First line, here\n\nSecond 123!";
    final var response = client.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder()
            .setDocId("typed-segmentation")
            .setRawText(text)
            .build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("typed-segmentation")
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .setSentenceDetector(SentenceDetectorSelector.newBuilder()
                .setStandard(StandardSentenceDetectorEngine
                    .STANDARD_SENTENCE_DETECTOR_ENGINE_NEWLINE))
            .setTokenizer(TokenizerSelector.newBuilder()
                .setStandard(StandardTokenizerEngine
                    .STANDARD_TOKENIZER_ENGINE_WHITESPACE))
            .build())
        .setOptions(AnalysisOptions.newBuilder()
            .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
            .build())
        .build());
    final OpenNlpDocument document = response.getDocument();

    assertEquals(2, document.getSentencesCount());
    assertEquals("First line, here",
        slice(text, document.getSentences(0).getSentenceSpan()));
    assertEquals("Second 123!",
        slice(text, document.getSentences(1).getSentenceSpan()));
    assertEquals(List.of("First", "line,", "here"), document.getSentences(0)
        .getTokensList().stream().map(token -> token.getText()).toList());
    assertEquals(List.of("Second", "123!"), document.getSentences(1)
        .getTokensList().stream().map(token -> token.getText()).toList());
    assertStandardLayer(document, StandardLayer.STANDARD_LAYER_SENTENCES,
        "opennlp:sentences");
    assertStandardLayer(document, StandardLayer.STANDARD_LAYER_TOKENS,
        "opennlp:tokens");
  }

  @Test
  void termIdentityKeepsOriginalOffsetsInTheDocumentShape() {
    final String text = "Groß GROSS";
    final OpenNlpDocument document = client.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder()
            .setDocId("term-identity")
            .setRawText(text)
            .build())
        .setProfile(AnalysisProfile.newBuilder()
            .setProfileId("term-identity")
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addTermDimensions("FULL_CASE_FOLD")
            .setTokenizer(TokenizerSelector.newBuilder()
                .setStandard(StandardTokenizerEngine
                    .STANDARD_TOKENIZER_ENGINE_WHITESPACE))
            .build())
        .setOptions(AnalysisOptions.newBuilder()
            .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
            .build())
        .build()).getDocument();

    assertEquals(List.of("gross", "gross"), document.getSentences(0)
        .getTokensList().stream()
        .map(token -> token.getTermLayersOrThrow("FULL_CASE_FOLD"))
        .toList());
    final AnnotationLayer terms = assertStandardLayer(document,
        StandardLayer.STANDARD_LAYER_TERMS, "opennlp:terms:FULL_CASE_FOLD");
    assertEquals("FULL_CASE_FOLD", terms.getIdentity().getQualifier());
    assertEquals(List.of("gross", "gross"), terms.getStringValues().getAnnotationsList()
        .stream().map(annotation -> annotation.getValue()).toList());
    assertEquals(List.of("Groß", "GROSS"), terms.getStringValues().getAnnotationsList()
        .stream().map(annotation -> slice(text, annotation.getSpan())).toList());
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
    assertEquals(GrpcStatusCode.GRPC_STATUS_CODE_INVALID_ARGUMENT,
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
        .setOptions(AnalysisOptions.newBuilder()
            .setEmbeddingModelId("minilm")
            .setIncludeDocumentCentroid(true)
            .setDocumentCentroidNormalization(
                VectorNormalization.VECTOR_NORMALIZATION_L2)
            .build())
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
    final AnnotationLayer embeddings = assertStandardLayer(response.getDocument(),
        StandardLayer.STANDARD_LAYER_EMBEDDINGS, "opennlp:embeddings");
    assertEquals(2, embeddings.getEmbeddingValues().getAnnotationsList().stream()
        .filter(annotation -> annotation.getGranularity()
            == EmbeddingGranularity.EMBEDDING_GRANULARITY_SENTENCE)
        .count());
    assertEquals(1, embeddings.getEmbeddingValues().getAnnotationsList().stream()
        .filter(annotation -> annotation.getGranularity()
            == EmbeddingGranularity.EMBEDDING_GRANULARITY_DOCUMENT)
        .count());
    final var centroid = response.getDocument().getDocumentCentroids(0);
    double squaredNorm = 0.0d;
    for (float value : centroid.getVectorList()) {
      squaredNorm += value * value;
    }
    assertEquals(1.0d, Math.sqrt(squaredNorm), 1.0e-5d);
    assertEquals(VectorNormalization.VECTOR_NORMALIZATION_L2,
        centroid.getVectorNormalization());
    assertEquals(VectorNormalization.VECTOR_NORMALIZATION_L2,
        embeddings.getEmbeddingValues().getAnnotationsList().stream()
            .filter(annotation -> annotation.getGranularity()
                == EmbeddingGranularity.EMBEDDING_GRANULARITY_DOCUMENT)
            .findFirst().orElseThrow().getVectorNormalization());
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
    final AnnotationLayer chunkGroups = assertStandardLayer(response.getDocument(),
        StandardLayer.STANDARD_LAYER_CHUNK_GROUPS, "opennlp:chunk-groups");
    assertEquals(1, chunkGroups.getChunkGroupValues().getAnnotationsCount());
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
            .addSteps(PipelineStep.PIPELINE_STEP_TERM_VECTOR)
            .setTokenizer(TokenizerSelector.newBuilder()
                .setStandard(StandardTokenizerEngine.STANDARD_TOKENIZER_ENGINE_UAX29))
            .setStemmer(StemmerSpec.newBuilder()
                .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_SNOWBALL)
                .setLanguage("en")
                .build())
            .addTermLayers(TermLayerSpec.newBuilder()
                .setQualifier("court-folded")
                .addNormalizers(
                    Normalizer.NORMALIZER_STRIP_INVISIBLE)
                .addNormalizers(Normalizer.NORMALIZER_WHITESPACE)
                .addNormalizers(Normalizer.NORMALIZER_FULL_CASE_FOLD)
                .addNormalizers(Normalizer.NORMALIZER_ACCENT_FOLD)
                .setStemmer(StemmerSpec.newBuilder()
                    .setAlgorithm(StemmerAlgorithm.STEMMER_ALGORITHM_PORTER)))
            .setTermVector(TermVectorSpec.newBuilder()
                .setSourceLayer(LayerIdentity.newBuilder()
                    .setStandard(StandardLayer.STANDARD_LAYER_TERMS)
                    .setQualifier("court-folded")))
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

}
