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
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.apache.opennlp.grpc.webapp.spi.WebUiClasspathResource;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtension;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionDescriptor;
import org.apache.opennlp.grpc.webapp.spi.WebUiExtensionId;
import org.apache.opennlp.grpc.webapp.spi.WebUiMountPath;
import org.junit.jupiter.api.Test;

class OpenNlpGrpcWebServerTest {

  @Test
  void servesHealthApiAndSpiAssetsOverHttp() throws Exception {
    WebUiExtensionRegistry registry = new WebUiExtensionRegistry(List.of(testExtension()));
    try (OpenNlpGrpcWebServer server = new OpenNlpGrpcWebServer(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        new TestAnalysisRpc(), registry, 128)) {
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
      assertTrue(analysis.body().contains("\"docId\": \"http\""));
    }
  }

  @Test
  void rejectsOversizedBodiesAndUnsupportedMethods() throws Exception {
    try (OpenNlpGrpcWebServer server = new OpenNlpGrpcWebServer(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        new TestAnalysisRpc(), new WebUiExtensionRegistry(List.of(testExtension())), 16)) {
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
        new TestAnalysisRpc(), new WebUiExtensionRegistry(List.of(testExtension())), 128)) {
      server.start();
      HttpClient client = HttpClient.newHttpClient();

      assertEquals(200, postAnalyze(client, server, "Application/JSON; charset=utf-8"));
      assertEquals(200, postAnalyze(client, server, "APPLICATION/JSON ; charset=utf-8"));
      assertEquals(415, postAnalyze(client, server, "application/jsonx"));
    }
  }

  @Test
  void returnsGenericInternalErrorForUnexpectedGatewayFailure() throws Exception {    AnalysisRpc failing = new TestAnalysisRpc() {
      @Override
      public GetServiceInfoResponse getServiceInfo() {
        throw new IllegalStateException("internal implementation detail");
      }
    };
    try (OpenNlpGrpcWebServer server = new OpenNlpGrpcWebServer(
        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        failing, new WebUiExtensionRegistry(List.of(testExtension())), 128)) {
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
  }
}
