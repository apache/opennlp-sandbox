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

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.thetaphi.forbiddenapis.SuppressForbidden;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// jdk.httpserver is an exported, supported JDK module. Its com.sun package name triggers the
// intentionally broad Java 8-era jdk-internal forbidden-APIs signature used by the sandbox.
@SuppressForbidden
final class OpenNlpGrpcWebServer implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenNlpGrpcWebServer.class);
  private static final String TEXT_CONTENT_TYPE = "text/plain; charset=utf-8";

  private final HttpServer server;
  private final ExecutorService executor;
  private final CountDownLatch terminated = new CountDownLatch(1);
  private final AtomicBoolean stopped = new AtomicBoolean();

  /**
   * Creates a web server without starting it.
   *
   * @param address The HTTP bind address.
   * @param analysisRpc The analysis service adapter.
   * @param searchRpc The search service adapter.
   * @param extensionRegistry The static UI extensions.
   * @param maxRequestBytes The largest accepted request body.
   * @throws IOException If the HTTP listener cannot be created.
   * @throws IllegalArgumentException If an argument is invalid.
   */
  OpenNlpGrpcWebServer(
      InetSocketAddress address,
      AnalysisRpc analysisRpc,
      SearchRpc searchRpc,
      WebUiExtensionRegistry extensionRegistry,
      int maxRequestBytes) throws IOException {
    if (address == null) {
      throw new IllegalArgumentException("address must not be null");
    }
    if (analysisRpc == null) {
      throw new IllegalArgumentException("analysisRpc must not be null");
    }
    if (searchRpc == null) {
      throw new IllegalArgumentException("searchRpc must not be null");
    }
    if (extensionRegistry == null) {
      throw new IllegalArgumentException("extensionRegistry must not be null");
    }
    if (maxRequestBytes < 1 || maxRequestBytes == Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "maxRequestBytes must be between 1 and " + (Integer.MAX_VALUE - 1));
    }
    this.server = HttpServer.create(address, 0);
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
    server.setExecutor(executor);
    server.createContext("/", new WebHandler(
        new GrpcJsonApi(analysisRpc, searchRpc), new WebUiCatalogJson(extensionRegistry),
        new WebUiAssetResolver(extensionRegistry), maxRequestBytes));
  }

  /** Starts accepting HTTP requests. */
  void start() {
    server.start();
  }

  /**
   * Returns the bound address, including an assigned ephemeral port.
   *
   * @return The bound address.
   */
  InetSocketAddress address() {
    return server.getAddress();
  }

  /**
   * Waits until the server stops.
   *
   * @throws InterruptedException If the waiting thread is interrupted.
   */
  void awaitTermination() throws InterruptedException {
    terminated.await();
  }

  /** {@inheritDoc} */
  @Override
  public void close() {
    stop();
  }

  /** Stops the listener and its request executor. Repeated calls have no effect. */
  void stop() {
    if (!stopped.compareAndSet(false, true)) {
      return;
    }
    server.stop(0);
    executor.shutdownNow();
    terminated.countDown();
  }

  @SuppressForbidden
  private static final class WebHandler implements HttpHandler {

    private static final String HTTP_GET = "GET";
    private static final String HTTP_HEAD = "HEAD";
    private static final String HTTP_POST = "POST";
    private static final String JSON_MEDIA_TYPE = "application/json";
    private static final String METHOD_NOT_ALLOWED =
        "HTTP method is not allowed for this endpoint";

    private final GrpcJsonApi api;
    private final WebUiCatalogJson catalog;
    private final WebUiAssetResolver assets;
    private final int maxRequestBytes;

    /**
     * Creates the single host request handler.
     *
     * @param api The protobuf JSON API.
     * @param catalog The UI extension catalog.
     * @param assets The static asset resolver.
     * @param maxRequestBytes The largest accepted request body.
     */
    private WebHandler(
        GrpcJsonApi api,
        WebUiCatalogJson catalog,
        WebUiAssetResolver assets,
        int maxRequestBytes) {
      this.api = api;
      this.catalog = catalog;
      this.assets = assets;
      this.maxRequestBytes = maxRequestBytes;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
      try (exchange) {
        addSecurityHeaders(exchange.getResponseHeaders());
        try {
          handleRequest(exchange);
        } catch (RuntimeException exception) {
          LOGGER.error("Unexpected HTTP gateway failure", exception);
          send(exchange, GrpcJsonApi.error(500, Status.Code.INTERNAL,
              "Unexpected HTTP gateway failure"));
        }
      }
    }

    /**
     * Routes one request after common response headers are installed.
     *
     * @param exchange The active HTTP exchange.
     * @throws IOException If the request or response body cannot be transferred.
     */
    private void handleRequest(HttpExchange exchange) throws IOException {
      String method = exchange.getRequestMethod();
      String rawPath = exchange.getRequestURI().getRawPath();
      if (rawPath.equals("/healthz")) {
        if (!method.equals(HTTP_GET) && !method.equals(HTTP_HEAD)) {
          send(exchange, GrpcJsonApi.error(405, Status.Code.UNIMPLEMENTED,
              METHOD_NOT_ALLOWED));
          return;
        }
        send(exchange, WebHttpResponse.utf8(200, TEXT_CONTENT_TYPE, "ok\n"));
        return;
      }
      if (rawPath.equals("/api") || rawPath.startsWith("/api/")) {
        byte[] body;
        try {
          body = readBody(exchange, maxRequestBytes);
        } catch (RequestTooLargeException exception) {
          send(exchange, GrpcJsonApi.error(413, Status.Code.RESOURCE_EXHAUSTED,
              "HTTP request body exceeds " + maxRequestBytes + " bytes"));
          return;
        }
        if (method.equals(HTTP_POST)) {
          final String required = requiredContentType(rawPath);
          if (!hasContentType(exchange.getRequestHeaders(), required)) {
            send(exchange, GrpcJsonApi.error(415, Status.Code.INVALID_ARGUMENT,
                "Content-Type must be " + required));
            return;
          }
        }
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        WebHttpResponse response = rawPath.equals("/api/v1/ui-extensions")
            ? catalog.handle(method) : api.handle(method, rawPath, body);
        send(exchange, response);
        return;
      }
      if (!method.equals(HTTP_GET) && !method.equals(HTTP_HEAD)) {
        send(exchange, GrpcJsonApi.error(405, Status.Code.UNIMPLEMENTED,
            "Static assets support only GET and HEAD"));
        return;
      }
      Optional<WebUiAsset> asset = assets.resolve(rawPath);
      if (asset.isEmpty()) {
        send(exchange, GrpcJsonApi.error(404, Status.Code.NOT_FOUND, "Static asset not found"));
        return;
      }
      exchange.getResponseHeaders().set("Cache-Control", "no-cache");
      WebUiAsset resolved = asset.orElseThrow();
      send(exchange, new WebHttpResponse(200, resolved.contentType(), resolved.content()));
    }

    /**
     * Reads a bounded request body.
     *
     * @param exchange The active HTTP exchange.
     * @param maxRequestBytes The largest accepted body.
     * @return The request body.
     * @throws IOException If the body cannot be read.
     * @throws RequestTooLargeException If the body exceeds the configured limit.
     */
    private byte[] readBody(HttpExchange exchange, int maxRequestBytes)
        throws IOException, RequestTooLargeException {
      String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
      if (contentLength != null) {
        try {
          if (Long.parseLong(contentLength) > maxRequestBytes) {
            throw new RequestTooLargeException();
          }
        } catch (NumberFormatException exception) {
          throw new IOException("Invalid Content-Length header", exception);
        }
      }
      try (InputStream requestBody = exchange.getRequestBody()) {
        byte[] body = requestBody.readNBytes(maxRequestBytes + 1);
        if (body.length > maxRequestBytes) {
          throw new RequestTooLargeException();
        }
        return body;
      }
    }

    /**
     * Returns whether the request declares the required media type.
     *
     * @param headers The request headers.
     * @param required The media type the path requires.
     * @return {@code true} when the declared media type matches.
     */
    private boolean hasContentType(Headers headers, String required) {
      String contentType = headers.getFirst("Content-Type");
      if (contentType == null) {
        return false;
      }
      int parameterStart = contentType.indexOf(';');
      String mediaType = parameterStart < 0
          ? contentType : contentType.substring(0, parameterStart);
      return equalsIgnoreCaseAscii(mediaType.strip(), required);
    }

    /**
     * The content type one API path requires: serialized protobuf for the saved
     * response decoder, protobuf JSON everywhere else.
     *
     * @param path The request path.
     * @return The required media type.
     */
    private static String requiredContentType(String path) {
      return path.equals("/api/v1/response/decode")
          ? GrpcJsonApi.PROTOBUF_CONTENT_TYPE : JSON_MEDIA_TYPE;
    }

    /**
     * Returns whether two media type tokens match case-insensitively. Media types
     * are ASCII, so a cursor compare replaces the locale-aware equalsIgnoreCase.
     *
     * @param value The token to test.
     * @param expected The expected token, in lower case.
     * @return {@code true} when the tokens match.
     */
    private static boolean equalsIgnoreCaseAscii(String value, String expected) {
      if (value.length() != expected.length()) {
        return false;
      }
      for (int index = 0; index < value.length(); index++) {
        if (asciiLower(value.charAt(index)) != asciiLower(expected.charAt(index))) {
          return false;
        }
      }
      return true;
    }

    /** Folds ASCII upper case to lower case, leaving every other character untouched. */
    private static char asciiLower(char character) {
      return character >= 'A' && character <= 'Z' ? (char) (character + ('a' - 'A')) : character;
    }

    /**
     * Adds security headers shared by API and asset responses.
     *
     * @param headers The response headers.
     */
    private void addSecurityHeaders(Headers headers) {
      headers.set("X-Content-Type-Options", "nosniff");
      headers.set("Referrer-Policy", "no-referrer");
      headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
      headers.set("Content-Security-Policy",
          "default-src 'self'; connect-src 'self'; img-src 'self' data:; "
              + "style-src 'self'; script-src 'self'; object-src 'none'; base-uri 'none'");
    }

    /**
     * Writes one response and suppresses the body for HEAD requests.
     *
     * @param exchange The active HTTP exchange.
     * @param response The response to write.
     * @throws IOException If the response cannot be written.
     */
    private void send(HttpExchange exchange, WebHttpResponse response) throws IOException {
      byte[] body = response.body();
      exchange.getResponseHeaders().set("Content-Type", response.contentType());
      boolean head = exchange.getRequestMethod().equals(HTTP_HEAD);
      exchange.sendResponseHeaders(response.status(), head ? -1 : body.length);
      if (!head) {
        exchange.getResponseBody().write(body);
      }
    }
  }

  private static final class RequestTooLargeException extends Exception {

    private static final long serialVersionUID = 2698191624076880196L;
  }
}
