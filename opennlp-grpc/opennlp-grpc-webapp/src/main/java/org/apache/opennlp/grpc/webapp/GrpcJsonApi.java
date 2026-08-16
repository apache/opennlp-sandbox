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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;

final class GrpcJsonApi {

  static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

  private final AnalysisRpc rpc;
  private final JsonFormat.Parser parser;
  private final JsonFormat.Printer printer;

  /**
   * Creates the JSON facade.
   *
   * @param rpc The analysis service adapter.
   * @throws IllegalArgumentException If {@code rpc} is {@code null}.
   */
  GrpcJsonApi(AnalysisRpc rpc) {
    if (rpc == null) {
      throw new IllegalArgumentException("rpc must not be null");
    }
    this.rpc = rpc;
    this.parser = JsonFormat.parser();
    this.printer = JsonFormat.printer();
  }

  /**
   * Handles one API request.
   *
   * @param method The HTTP method.
   * @param path The request path.
   * @param body The request body.
   * @return The HTTP response.
   * @throws IllegalArgumentException If an argument is {@code null}.
   */
  WebHttpResponse handle(String method, String path, byte[] body) {
    if (method == null) {
      throw new IllegalArgumentException("method must not be null");
    }
    if (path == null) {
      throw new IllegalArgumentException("path must not be null");
    }
    if (body == null) {
      throw new IllegalArgumentException("body must not be null");
    }
    try {
      return switch (path) {
        case "/api/v1/service-info" -> method.equals("GET")
            ? protobufJson(rpc.getServiceInfo()) : methodNotAllowed();
        case "/api/v1/model-bundles" -> method.equals("GET")
            ? protobufJson(rpc.listModelBundles()) : methodNotAllowed();
        case "/api/v1/analyze" -> method.equals("POST")
            ? analyze(body) : methodNotAllowed();
        default -> error(404, Status.Code.NOT_FOUND, "Unknown API endpoint");
      };
    } catch (StatusRuntimeException exception) {
      Status status = exception.getStatus();
      String message = status.getDescription();
      if (message == null || message.isBlank()) {
        message = status.getCode().name();
      }
      return error(GrpcHttpStatusMapper.toHttpStatus(status.getCode()),
          status.getCode(), message);
    }
  }

  /**
   * Parses and forwards an analysis request.
   *
   * @param body The protobuf JSON request body.
   * @return The encoded analysis response.
   */
  private WebHttpResponse analyze(byte[] body) {
    AnalyzeDocumentRequest.Builder request = AnalyzeDocumentRequest.newBuilder();
    final String json;
    try {
      json = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(body))
          .toString();
    } catch (CharacterCodingException exception) {
      return error(400, Status.Code.INVALID_ARGUMENT,
          "Request body must contain valid UTF-8");
    }
    try {
      parser.merge(json, request);
    } catch (InvalidProtocolBufferException exception) {
      return error(400, Status.Code.INVALID_ARGUMENT,
          "Malformed protobuf JSON request: " + exception.getMessage());
    }
    return protobufJson(rpc.analyze(request.build()));
  }

  /**
   * Encodes one protobuf message as JSON.
   *
   * @param message The protobuf message.
   * @return The encoded HTTP response.
   */
  private WebHttpResponse protobufJson(Message message) {
    try {
      return WebHttpResponse.utf8(200, JSON_CONTENT_TYPE, printer.print(message));
    } catch (InvalidProtocolBufferException exception) {
      return error(500, Status.Code.INTERNAL, "Could not encode the service response");
    }
  }

  /** @return The common method-not-allowed response. */
  private static WebHttpResponse methodNotAllowed() {
    return error(405, Status.Code.UNIMPLEMENTED, "HTTP method is not allowed for this endpoint");
  }

  /**
   * Creates a JSON error response.
   *
   * @param httpStatus The HTTP status.
   * @param code The gRPC status code.
   * @param message The caller-facing error message.
   * @return The encoded response.
   */
  static WebHttpResponse error(int httpStatus, Status.Code code, String message) {
    String json = "{\"code\":\"" + escapeJson(code.name()) + "\",\"message\":\""
        + escapeJson(message) + "\"}";
    return WebHttpResponse.utf8(httpStatus, JSON_CONTENT_TYPE, json);
  }

  /**
   * Escapes one value for a JSON string literal.
   *
   * @param value The unescaped value.
   * @return The escaped value.
   */
  private static String escapeJson(String value) {
    StringBuilder escaped = new StringBuilder(value.length() + 16);
    for (int i = 0; i < value.length(); i++) {
      char character = value.charAt(i);
      switch (character) {
        case '"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (character < 0x20) {
            escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
          } else {
            escaped.append(character);
          }
        }
      }
    }
    return escaped.toString();
  }
}
