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
package org.apache.opennlp.grpc.webapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexRequest;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexResponse;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.ListSearchProvidersResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.SearchHit;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;
import org.apache.opennlp.grpc.webapp.spi.WebUiClasspathResource;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtension;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionDescriptor;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionId;
import org.apache.opennlp.grpc.webapp.spi.WebUiMountPath;
import org.junit.jupiter.api.Test;

class OpenNlpGrpcWebServerTest {

  private static final String SEARCH_INDEX_ID = "legal-demo";
  private static final String SEARCH_DOCUMENT_ID = "passage-1";

  @Test
  void keepsIdleKeepAliveConnectionsWellPastAHumanPause() throws Exception {
    // The JDK server reads the property once, when it first starts; the gateway sets it
    // at run time before creating its first server (not in a static initializer, which a
    // native image may run at build time), so a browser reusing a pooled connection after
    // a pause is still served.
    System.clearProperty(OpenNlpGrpcWebServer.IDLE_INTERVAL_PROPERTY);
    try (OpenNlpGrpcWebServer server = new OpenNlpGrpcWebServer(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        new TestAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(),
        new EmptyTrainingRpc(), new WebUiExtensionRegistry(List.of()), 128)) {
      assertEquals(Long.toString(OpenNlpGrpcWebServer.IDLE_INTERVAL_SECONDS),
          System.getProperty(OpenNlpGrpcWebServer.IDLE_INTERVAL_PROPERTY));
    }
    assertTrue(OpenNlpGrpcWebServer.IDLE_INTERVAL_SECONDS >= 600,
        "ten minutes is the least a reader pauses for");
  }

  @Test
  void servesHealthApiAndSpiAssetsOverHttp() throws Exception {
    WebUiExtensionRegistry registry = new WebUiExtensionRegistry(List.of(testExtension()));
    try (OpenNlpGrpcWebServer server = new OpenNlpGrpcWebServer(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        new TestAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(),
        new EmptyTrainingRpc(), registry, 128)) {
      server.start();
      HttpClient client = HttpClient.newHttpClient();

      HttpResponse<String> health = get(client, server, "/healthz");
      assertEquals(200, health.statusCode());
      assertEquals("ok\n", health.body());

      HttpResponse<String> page = get(client, server, "/console");
      assertEquals(200, page.statusCode());
      assertEquals("nosniff", page.headers().firstValue("x-content-type-options").orElseThrow());
      assertTrue(page.body().endsWith("test console\n"));

      HttpResponse<String> extensions = get(client, server, "/api/v1/ui-extensions");
      assertEquals(200, extensions.statusCode());
      assertTrue(extensions.body().contains("\"id\": \"test-console\""));
      assertTrue(extensions.body().contains("\"mountPath\": \"/console\""));

      HttpRequest analyze = request(server, "/api/v1/analyze")
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(
              "{\"document\":{\"docId\":\"http\",\"rawText\":\"Hello\"}}"))
          .build();
      HttpResponse<String> analysis = client.send(analyze,
          HttpResponse.BodyHandlers.ofString());
      assertEquals(200, analysis.statusCode());
      assertTrue(analysis.body().contains("\"docId\":\"http\""));
    }
  }

  @Test
  void streamsBatchAnalysisAsNdjson() throws Exception {
    WebUiExtensionRegistry registry = new WebUiExtensionRegistry(List.of(testExtension()));
    try (OpenNlpGrpcWebServer server = new OpenNlpGrpcWebServer(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        new TestAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(),
        new EmptyTrainingRpc(), registry, 4096)) {
      server.start();
      HttpClient client = HttpClient.newHttpClient();

      // The body is the AnalyzeStream frame sequence: one configuration frame,
      // then one frame per document.
      final String frames = "["
          + "{\"configuration\":{\"profile\":{\"steps\":[\"PIPELINE_STEP_SENTENCE_DETECT\"]}}},"
          + "{\"document\":{\"sequence\":\"1\",\"document\":{\"docId\":\"a\",\"rawText\":\"Hello\"}}},"
          + "{\"document\":{\"sequence\":\"2\",\"document\":{\"docId\":\"b\",\"rawText\":\"World\"}}}"
          + "]";
      HttpRequest analyzeStream = request(server, "/api/v1/analyze-stream")
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(frames))
          .build();
      HttpResponse<String> response = client.send(analyzeStream,
          HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      String[] lines = response.body().strip().split("\n");
      assertEquals(2, lines.length);
      assertTrue(lines[0].contains("\"sequence\": \"1\"") || lines[0].contains("\"sequence\":\"1\""));
      assertTrue(lines[0].contains("\"docId\": \"a\"") || lines[0].contains("\"docId\":\"a\""));
      assertTrue(lines[1].contains("\"docId\": \"b\"") || lines[1].contains("\"docId\":\"b\""));
    }
  }

  @Test
  void streamsProgressiveAnalysisAsNdjson() throws Exception {
    WebUiExtensionRegistry registry = new WebUiExtensionRegistry(List.of(testExtension()));
    try (OpenNlpGrpcWebServer server = new OpenNlpGrpcWebServer(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        new TestAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(),
        new EmptyTrainingRpc(), registry, 4096)) {
      server.start();
      HttpClient client = HttpClient.newHttpClient();

      HttpRequest analyze = request(server, "/api/v1/analyze-progressive")
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(
              "{\"document\":{\"docId\":\"progressive\",\"rawText\":\"Hello\"}}"))
          .build();
      HttpResponse<String> response = client.send(analyze,
          HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertEquals("application/x-ndjson; charset=utf-8",
          response.headers().firstValue("content-type").orElseThrow());
      String[] lines = response.body().strip().split("\n");
      assertTrue(lines.length >= 2);
      assertTrue(lines[0].contains("\"started\""));
      assertTrue(lines[lines.length - 1].contains("\"complete\""));
      assertTrue(lines[lines.length - 1].contains("\"docId\": \"progressive\"")
          || lines[lines.length - 1].contains("\"docId\":\"progressive\""));
    }
  }

  @Test
  void transcodesSavedResponsesOverHttp() throws Exception {
    WebUiExtensionRegistry registry = new WebUiExtensionRegistry(List.of(testExtension()));
    try (OpenNlpGrpcWebServer server = new OpenNlpGrpcWebServer(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        new TestAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(),
        new EmptyTrainingRpc(), registry, 4096)) {
      server.start();
      HttpClient client = HttpClient.newHttpClient();

      HttpRequest encode = request(server, "/api/v1/response/encode")
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(
              "{\"document\":{\"docId\":\"saved\",\"rawText\":\"Hello\"}}"))
          .build();
      HttpResponse<byte[]> encoded = client.send(encode, HttpResponse.BodyHandlers.ofByteArray());
      assertEquals(200, encoded.statusCode());
      assertEquals("application/x-protobuf",
          encoded.headers().firstValue("content-type").orElseThrow());
      assertEquals("saved",
          AnalyzeDocumentResponse.parseFrom(encoded.body()).getDocument().getDocId());

      HttpRequest decode = request(server, "/api/v1/response/decode")
          .header("Content-Type", "application/x-protobuf")
          .POST(HttpRequest.BodyPublishers.ofByteArray(encoded.body()))
          .build();
      HttpResponse<String> decoded = client.send(decode, HttpResponse.BodyHandlers.ofString());
      assertEquals(200, decoded.statusCode());
      assertTrue(decoded.body().contains("\"docId\":\"saved\""));

      HttpRequest wrongType = request(server, "/api/v1/response/decode")
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofByteArray(encoded.body()))
          .build();
      assertEquals(415, client.send(wrongType, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
  }

  @Test
  void streamsTrainingUpdatesAsNdjsonOverHttp() throws Exception {
    try (OpenNlpGrpcWebServer server = new OpenNlpGrpcWebServer(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        new TestAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(),
        new StreamingTrainingRpc(), new WebUiExtensionRegistry(List.of(testExtension())),
        4096)) {
      server.start();
      HttpClient client = HttpClient.newHttpClient();

      HttpRequest train = request(server, "/api/v1/train-static-model")
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(
              "{\"vocabularyArtifactId\":\"vocabulary-1\",\"teacherId\":\"mini\","
                  + "\"displayName\":\"m\",\"provenanceSummary\":\"p\"}"))
          .build();
      HttpResponse<String> streamed = client.send(train, HttpResponse.BodyHandlers.ofString());
      assertEquals(200, streamed.statusCode());
      assertEquals("application/x-ndjson; charset=utf-8",
          streamed.headers().firstValue("content-type").orElseThrow());
      String[] lines = streamed.body().split("\n");
      assertEquals(2, lines.length);
      assertTrue(lines[0].contains("\"progress\":\"distilling\""));
      assertTrue(lines[1].contains("\"artifactId\":\"static-model-1\""));

      HttpRequest install = request(server, "/api/v1/install-model")
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(
              "{\"catalogId\":\"potion-base-8m\",\"revision\":\"revision-1\","
                  + "\"licenseName\":\"MIT\",\"licenseAcknowledged\":true}"))
          .build();
      HttpResponse<String> installed = client.send(install, HttpResponse.BodyHandlers.ofString());
      assertEquals(200, installed.statusCode());
      assertTrue(installed.body().contains("INSTALL_MODEL_STAGE_DOWNLOADING"));
      assertTrue(installed.body().contains("\"catalogId\":\"potion-base-8m\""));
    }
  }

  @Test
  void servesSearchCatalogAndDocumentShapedHitsOverHttp() throws Exception {
    try (OpenNlpGrpcWebServer server = new OpenNlpGrpcWebServer(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        new TestAnalysisRpc(), new TestSearchRpc(), new EmptyVocabularyRpc(),
        new EmptyTrainingRpc(), new WebUiExtensionRegistry(List.of(testExtension())), 512)) {
      server.start();
      HttpClient client = HttpClient.newHttpClient();

      HttpResponse<String> indexes = get(client, server, "/api/v1/search-indexes");
      assertEquals(200, indexes.statusCode());
      assertTrue(indexes.body().contains("\"indexId\":\"" + SEARCH_INDEX_ID + "\""));

      HttpRequest search = request(server, "/api/v1/search")
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("""
              {"indexId":"%s","query":{"docId":"query","rawText":"habeas"},"topK":3}
              """.formatted(SEARCH_INDEX_ID)))
          .build();
      HttpResponse<String> response = client.send(search, HttpResponse.BodyHandlers.ofString());

      assertEquals(200, response.statusCode());
      assertTrue(response.body().contains("\"documentId\":\"" + SEARCH_DOCUMENT_ID + "\""));
      assertTrue(response.body().contains("\"rawText\":\"The writ must issue.\""));
    }
  }

  @Test
  void rejectsOversizedBodiesAndUnsupportedMethods() throws Exception {
    try (OpenNlpGrpcWebServer server = new OpenNlpGrpcWebServer(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        new TestAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(),
        new EmptyTrainingRpc(), new WebUiExtensionRegistry(List.of(testExtension())), 16)) {
      server.start();
      HttpClient client = HttpClient.newHttpClient();

      HttpRequest oversized = request(server, "/api/v1/analyze")
          .POST(HttpRequest.BodyPublishers.ofString("x".repeat(17)))
          .build();
      assertEquals(413, client.send(oversized,
          HttpResponse.BodyHandlers.discarding()).statusCode());

      HttpRequest staticPost = request(server, "/console")
          .POST(HttpRequest.BodyPublishers.noBody())
          .build();
      assertEquals(405, client.send(staticPost,
          HttpResponse.BodyHandlers.discarding()).statusCode());

      HttpRequest misleadingContentType = request(server, "/api/v1/analyze")
          .header("Content-Type", "application/jsonp")
          .POST(HttpRequest.BodyPublishers.ofString("{}"))
          .build();
      assertEquals(415, client.send(misleadingContentType,
          HttpResponse.BodyHandlers.discarding()).statusCode());

      assertEquals(404, get(client, server, "/api").statusCode());

      HttpRequest catalogPost = request(server, "/api/v1/ui-extensions")
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{}"))
          .build();
      assertEquals(405, client.send(catalogPost,
          HttpResponse.BodyHandlers.discarding()).statusCode());
    }
  }

  @Test
  void acceptsTheJsonMediaTypeCaseInsensitivelyWithParameters() throws Exception {
    // Characterization: the media type is matched case-insensitively after
    // parameters and surrounding whitespace are stripped.
    try (OpenNlpGrpcWebServer server = new OpenNlpGrpcWebServer(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        new TestAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(),
        new EmptyTrainingRpc(), new WebUiExtensionRegistry(List.of(testExtension())), 128)) {
      server.start();
      HttpClient client = HttpClient.newHttpClient();

      assertEquals(200, postAnalyze(client, server, "Application/JSON; charset=utf-8"));
      assertEquals(200, postAnalyze(client, server, "APPLICATION/JSON ; charset=utf-8"));
      assertEquals(415, postAnalyze(client, server, "application/jsonx"));
    }
  }

  @Test
  void returnsGenericInternalErrorForUnexpectedGatewayFailure() throws Exception {
    AnalysisRpc failing = new TestAnalysisRpc() {
      @Override
      public GetServiceInfoResponse getServiceInfo() {
        throw new IllegalStateException("internal implementation detail");
      }
    };
    try (OpenNlpGrpcWebServer server = new OpenNlpGrpcWebServer(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        failing, new EmptySearchRpc(), new EmptyVocabularyRpc(),
        new EmptyTrainingRpc(), new WebUiExtensionRegistry(List.of(testExtension())), 128)) {
      server.start();

      HttpResponse<String> response = get(HttpClient.newHttpClient(), server,
          "/api/v1/service-info");

      assertEquals(500, response.statusCode());
      assertTrue(response.body().contains("Unexpected HTTP gateway failure"));
      assertTrue(!response.body().contains("internal implementation detail"));
    }
  }

  private static HttpResponse<String> get(
      HttpClient client, OpenNlpGrpcWebServer server, String path) throws Exception {
    return client.send(request(server, path).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static int postAnalyze(
      HttpClient client, OpenNlpGrpcWebServer server, String contentType) throws Exception {
    HttpRequest request = request(server, "/api/v1/analyze")
        .header("Content-Type", contentType)
        .POST(HttpRequest.BodyPublishers.ofString("{\"document\":{\"rawText\":\"Hello\"}}"))
        .build();
    return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
  }

  private static HttpRequest.Builder request(OpenNlpGrpcWebServer server, String path) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
        + server.address().getPort() + path));
  }

  private static WebUiExtension testExtension() {
    WebUiExtensionDescriptor descriptor = new WebUiExtensionDescriptor(
        new WebUiExtensionId("test-console"),
        "Test console",
        new WebUiMountPath("/console"),
        new WebUiClasspathResource("/test-web-ui"));
    return new WebUiExtension() {
      @Override
      public WebUiExtensionDescriptor descriptor() {
        return descriptor;
      }

      @Override
      public ClassLoader resourceClassLoader() {
        return OpenNlpGrpcWebServerTest.class.getClassLoader();
      }
    };
  }

  private static class TestAnalysisRpc implements AnalysisRpc {

    @Override
    public org.apache.opennlp.grpc.v1.ListOutputFormatsResponse listOutputFormats() {
      return org.apache.opennlp.grpc.v1.ListOutputFormatsResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.FormatDocumentResponse formatDocument(
        org.apache.opennlp.grpc.v1.FormatDocumentRequest request) {
      return org.apache.opennlp.grpc.v1.FormatDocumentResponse.getDefaultInstance();
    }

    @Override
    public GetServiceInfoResponse getServiceInfo() {
      return GetServiceInfoResponse.newBuilder().setApiVersion("v1").build();
    }

    @Override
    public ListModelBundlesResponse listModelBundles() {
      return ListModelBundlesResponse.getDefaultInstance();
    }

    @Override
    public AnalyzeDocumentResponse analyze(AnalyzeDocumentRequest request) {
      return AnalyzeDocumentResponse.newBuilder().setDocument(request.getDocument()).build();
    }

    @Override
    public java.util.Iterator<org.apache.opennlp.grpc.v1.AnalyzeStreamResponse> analyzeStream(
        java.util.List<org.apache.opennlp.grpc.v1.AnalyzeStreamRequest> frames) {
      // Echo every document frame in order, as the real stream would for tiny inputs.
      return frames.stream()
          .filter(org.apache.opennlp.grpc.v1.AnalyzeStreamRequest::hasDocument)
          .map(frame -> org.apache.opennlp.grpc.v1.AnalyzeStreamResponse.newBuilder()
              .setSequence(frame.getDocument().getSequence())
              .setOk(AnalyzeDocumentResponse.newBuilder()
                  .setDocument(frame.getDocument().getDocument()))
              .build())
          .iterator();
    }
  }

  private static final class TestSearchRpc implements SearchRpc {

    @Override
    public ListSearchProvidersResponse listSearchProviders() {
      return ListSearchProvidersResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.PersistIndexResponse persist(
        org.apache.opennlp.grpc.v1.PersistIndexRequest request) {
      return org.apache.opennlp.grpc.v1.PersistIndexResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.SealIndexResponse seal(
        org.apache.opennlp.grpc.v1.SealIndexRequest request) {
      return org.apache.opennlp.grpc.v1.SealIndexResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.ReindexIndexResponse reindex(
        org.apache.opennlp.grpc.v1.ReindexIndexRequest request) {
      return org.apache.opennlp.grpc.v1.ReindexIndexResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.SetIndexAliasResponse setAlias(
        org.apache.opennlp.grpc.v1.SetIndexAliasRequest request) {
      return org.apache.opennlp.grpc.v1.SetIndexAliasResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.DeleteIndexAliasResponse deleteAlias(
        org.apache.opennlp.grpc.v1.DeleteIndexAliasRequest request) {
      return org.apache.opennlp.grpc.v1.DeleteIndexAliasResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.ListIndexAliasesResponse listAliases() {
      return org.apache.opennlp.grpc.v1.ListIndexAliasesResponse.getDefaultInstance();
    }

    @Override
    public ListSearchIndexesResponse listSearchIndexes() {
      return ListSearchIndexesResponse.newBuilder()
          .addIndexes(SearchIndexDescriptor.newBuilder()
              .setIndexId(SEARCH_INDEX_ID)
              .setDisplayName("Legal demo")
              .setMaxTopK(25))
          .build();
    }

    @Override
    public SearchIndexResponse search(SearchIndexRequest request) {
      assertEquals(SEARCH_INDEX_ID, request.getIndexId());
      assertEquals("query", request.getQuery().getDocId());
      assertEquals("habeas", request.getQuery().getRawText());
      assertEquals(3, request.getTopK());
      return SearchIndexResponse.newBuilder()
          .setIndex(SearchIndexDescriptor.newBuilder().setIndexId(request.getIndexId()))
          .addSourceDocuments(OpenNlpDocument.newBuilder()
              .setDocId(SEARCH_DOCUMENT_ID)
              .setRawText("The writ must issue."))
          .addHits(SearchHit.newBuilder()
              .setDocumentId(SEARCH_DOCUMENT_ID)
              .setChunkId(SEARCH_DOCUMENT_ID)
              .setScore(0.75))
          .build();
    }

    @Override
    public IndexDocumentsResponse index(IndexDocumentsRequest request) {
      return IndexDocumentsResponse.getDefaultInstance();
    }

    @Override
    public DeleteSearchIndexResponse delete(DeleteSearchIndexRequest request) {
      return DeleteSearchIndexResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.SetCollectionResponse setCollection(
        org.apache.opennlp.grpc.v1.SetCollectionRequest request) {
      return org.apache.opennlp.grpc.v1.SetCollectionResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.GetCollectionResponse getCollection(
        org.apache.opennlp.grpc.v1.GetCollectionRequest request) {
      return org.apache.opennlp.grpc.v1.GetCollectionResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.ListCollectionsResponse listCollections() {
      return org.apache.opennlp.grpc.v1.ListCollectionsResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.DeleteCollectionResponse deleteCollection(
        org.apache.opennlp.grpc.v1.DeleteCollectionRequest request) {
      return org.apache.opennlp.grpc.v1.DeleteCollectionResponse.getDefaultInstance();
    }

    @Override
    public java.util.Iterator<org.apache.opennlp.grpc.v1.CollectionEvent> watchCollection(
        org.apache.opennlp.grpc.v1.WatchCollectionRequest request) {
      return java.util.Collections.emptyIterator();
    }
  }

  private static final class StreamingTrainingRpc implements TrainingRpc {

    @Override
    public org.apache.opennlp.grpc.v1.ListTeachersResponse listTeachers() {
      return org.apache.opennlp.grpc.v1.ListTeachersResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.ListModelCatalogResponse listModelCatalog() {
      return org.apache.opennlp.grpc.v1.ListModelCatalogResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.ListInstalledModelsResponse listInstalledModels() {
      return org.apache.opennlp.grpc.v1.ListInstalledModelsResponse.getDefaultInstance();
    }

    @Override
    public java.util.Iterator<org.apache.opennlp.grpc.v1.InstallModelUpdate> installModel(
        org.apache.opennlp.grpc.v1.InstallModelRequest request) {
      return List.of(
          org.apache.opennlp.grpc.v1.InstallModelUpdate.newBuilder()
              .setProgress(org.apache.opennlp.grpc.v1.InstallModelProgress.newBuilder()
                  .setStage(org.apache.opennlp.grpc.v1.InstallModelStage
                      .INSTALL_MODEL_STAGE_DOWNLOADING))
              .build(),
          org.apache.opennlp.grpc.v1.InstallModelUpdate.newBuilder()
              .setModel(org.apache.opennlp.grpc.v1.InstalledModelDescriptor.newBuilder()
                  .setCatalog(org.apache.opennlp.grpc.v1.ModelCatalogDescriptor.newBuilder()
                      .setCatalogId("potion-base-8m")))
              .build()).iterator();
    }

    @Override
    public java.util.Iterator<org.apache.opennlp.grpc.v1.TrainStaticModelUpdate>
        trainStaticModel(org.apache.opennlp.grpc.v1.TrainStaticModelRequest request) {
      return List.of(
          org.apache.opennlp.grpc.v1.TrainStaticModelUpdate.newBuilder()
              .setProgress("distilling").build(),
          org.apache.opennlp.grpc.v1.TrainStaticModelUpdate.newBuilder()
              .setModel(org.apache.opennlp.grpc.v1.StaticModelDescriptor.newBuilder()
                  .setArtifactId("static-model-1")).build()).iterator();
    }

    @Override
    public org.apache.opennlp.grpc.v1.ListStaticModelsResponse listStaticModels() {
      return org.apache.opennlp.grpc.v1.ListStaticModelsResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.DeleteStaticModelResponse deleteStaticModel(
        org.apache.opennlp.grpc.v1.DeleteStaticModelRequest request) {
      return org.apache.opennlp.grpc.v1.DeleteStaticModelResponse.getDefaultInstance();
    }
  }
}
