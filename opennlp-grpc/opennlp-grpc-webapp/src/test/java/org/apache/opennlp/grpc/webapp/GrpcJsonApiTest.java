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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.Status;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentEvent;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalysisStarted;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.junit.jupiter.api.Test;

class GrpcJsonApiTest {

  @Test
  void closesProgressiveEventsWhenTheHttpSinkDisconnects() {
    final AtomicBoolean closed = new AtomicBoolean();
    final AnalysisRpc rpc = new StubAnalysisRpc() {
      @Override
      public ProgressiveEvents analyzeProgressively(AnalyzeDocumentRequest request) {
        return new ProgressiveEvents() {
          private boolean delivered;

          @Override
          public boolean hasNext() {
            return !delivered;
          }

          @Override
          public AnalyzeDocumentEvent next() {
            if (delivered) {
              throw new NoSuchElementException();
            }
            delivered = true;
            return AnalyzeDocumentEvent.newBuilder()
                .setSequence(1)
                .setStarted(AnalysisStarted.newBuilder().setDocument(request.getDocument()))
                .build();
          }

          @Override
          public void close() {
            closed.set(true);
          }
        };
      }
    };
    final GrpcJsonApi api = new GrpcJsonApi(
        rpc, new EmptySearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());
    final byte[] request = "{\"document\":{\"rawText\":\"Hello\"}}"
        .getBytes(StandardCharsets.UTF_8);

    assertThrows(IOException.class,
        () -> api.analyzeProgressively(request, line -> {
          throw new IOException("browser disconnected");
        }));
    assertTrue(closed.get());
  }

  @Test
  void rendersServiceInfoAsProtobufJson() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    WebHttpResponse response = api.handle("GET", "/api/v1/service-info", new byte[0]);

    assertEquals(200, response.status());
    assertEquals("application/json; charset=utf-8", response.contentType());
    assertTrue(response.bodyUtf8().contains("\"opennlpVersion\":\"3.0.0\""));
    assertTrue(!response.bodyUtf8().contains("\n"));
  }

  @Test
  void servesOutputFormatsAndFormatDocumentRoutes() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    WebHttpResponse listed = api.handle("GET", "/api/v1/output-formats", new byte[0]);
    assertEquals(200, listed.status());

    byte[] request = """
        {"document":{"docId":"one","rawText":"Hello."},"formatId":"tsv"}
        """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    WebHttpResponse formatted = api.handle("POST", "/api/v1/format-document", request);
    assertEquals(200, formatted.status());
    assertTrue(formatted.bodyUtf8().contains("\"fileExtension\":\"tsv\""));
  }

  @Test
  void parsesAnalyzeRequestAndRendersDocumentShape() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());
    byte[] request = """
        {"document":{"docId":"one","rawText":"Hello world."}}
        """.getBytes(StandardCharsets.UTF_8);

    WebHttpResponse response = api.handle("POST", "/api/v1/analyze", request);

    assertEquals(200, response.status());
    assertTrue(response.bodyUtf8().contains("\"docId\":\"one\""));
    assertTrue(response.bodyUtf8().contains("\"rawText\":\"Hello world.\""));
  }


  @Test
  void encodesAnalyzeResponseJsonAsProtobufBytes() throws Exception {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());
    byte[] json = """
        {"document":{"docId":"one","rawText":"Hello world."}}
        """.getBytes(StandardCharsets.UTF_8);

    WebHttpResponse response = api.handle("POST", "/api/v1/response/encode", json);

    assertEquals(200, response.status());
    assertEquals("application/x-protobuf", response.contentType());
    AnalyzeDocumentResponse decoded = AnalyzeDocumentResponse.parseFrom(response.body());
    assertEquals("one", decoded.getDocument().getDocId());
    assertEquals("Hello world.", decoded.getDocument().getRawText());
  }

  @Test
  void analyzesToProtobufBytesWithoutPrintingJson() throws Exception {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());
    byte[] json = """
        {"document":{"docId":"one","rawText":"Hello world."}}
        """.getBytes(StandardCharsets.UTF_8);

    WebHttpResponse response = api.handle("POST", "/api/v1/analyze-protobuf", json);

    assertEquals(200, response.status());
    assertEquals("application/x-protobuf", response.contentType());
    AnalyzeDocumentResponse decoded = AnalyzeDocumentResponse.parseFrom(response.body());
    assertEquals("one", decoded.getDocument().getDocId());
    assertEquals("Hello world.", decoded.getDocument().getRawText());
    assertEquals(405, api.handle("GET", "/api/v1/analyze-protobuf", new byte[0]).status());
    WebHttpResponse malformed = api.handle("POST", "/api/v1/analyze-protobuf",
        "not-json".getBytes(StandardCharsets.UTF_8));
    assertEquals(400, malformed.status());
    assertTrue(malformed.bodyUtf8().contains("Malformed protobuf JSON request"));
  }

  @Test
  void decodesProtobufBytesBackToAnalyzeResponseJson() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());
    byte[] bytes = AnalyzeDocumentResponse.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setDocId("one").setRawText("Hello world."))
        .build()
        .toByteArray();

    WebHttpResponse response = api.handle("POST", "/api/v1/response/decode", bytes);

    assertEquals(200, response.status());
    assertEquals("application/json; charset=utf-8", response.contentType());
    assertTrue(response.bodyUtf8().contains("\"docId\":\"one\""));
    assertTrue(response.bodyUtf8().contains("\"rawText\":\"Hello world.\""));
  }

  @Test
  void rejectsMalformedResponseBytesLoudly() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    WebHttpResponse response = api.handle("POST", "/api/v1/response/decode",
        new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF});

    assertEquals(400, response.status());
    assertTrue(response.bodyUtf8().contains("\"code\":\"INVALID_ARGUMENT\""));
    assertTrue(response.bodyUtf8().contains("Malformed protobuf response bytes"));
  }

  @Test
  void rejectsMalformedResponseJsonBeforeEncoding() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    WebHttpResponse response = api.handle("POST", "/api/v1/response/encode",
        "not-json".getBytes(StandardCharsets.UTF_8));

    assertEquals(400, response.status());
    assertTrue(response.bodyUtf8().contains("\"code\":\"INVALID_ARGUMENT\""));
  }

  @Test
  void transcodeEndpointsRejectNonPostMethods() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    assertEquals(405, api.handle("GET", "/api/v1/response/encode", new byte[0]).status());
    assertEquals(405, api.handle("GET", "/api/v1/response/decode", new byte[0]).status());
  }

  @Test
  void rejectsMalformedJsonWithCanonicalErrorPayload() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    WebHttpResponse response = api.handle("POST", "/api/v1/analyze",
        "not-json".getBytes(StandardCharsets.UTF_8));

    assertEquals(400, response.status());
    assertTrue(response.bodyUtf8().contains("\"code\":\"INVALID_ARGUMENT\""));
    assertTrue(response.bodyUtf8().contains("Malformed protobuf JSON request"));
  }

  @Test
  void rejectsMalformedUtf8BeforeParsingProtobufJson() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    WebHttpResponse response = api.handle("POST", "/api/v1/analyze",
        new byte[] {(byte) 0xc3, (byte) 0x28});

    assertEquals(400, response.status());
    assertTrue(response.bodyUtf8().contains("\"code\":\"INVALID_ARGUMENT\""));
    assertTrue(response.bodyUtf8().contains("valid UTF-8"));
  }

  @Test
  void mapsGrpcFailuresWithoutLeakingCauseDetails() {
    AnalysisRpc unavailable = new StubAnalysisRpc() {
      @Override
      public AnalyzeDocumentResponse analyze(AnalyzeDocumentRequest request) {
        throw Status.UNAVAILABLE.withDescription("embedding backend unavailable")
            .withCause(new IllegalStateException("secret connection string"))
            .asRuntimeException();
      }
    };
    GrpcJsonApi api = new GrpcJsonApi(unavailable, new EmptySearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    WebHttpResponse response = api.handle("POST", "/api/v1/analyze",
        "{\"document\":{\"rawText\":\"Hello\"}}".getBytes(StandardCharsets.UTF_8));

    assertEquals(503, response.status());
    assertTrue(response.bodyUtf8().contains("\"code\":\"UNAVAILABLE\""));
    assertTrue(response.bodyUtf8().contains("embedding backend unavailable"));
    assertTrue(!response.bodyUtf8().contains("secret connection string"));
  }

  @Test
  void enforcesMethodsAndKnownApiPaths() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new EmptySearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    assertEquals(405,
        api.handle("GET", "/api/v1/analyze", new byte[0]).status());
    assertEquals(404,
        api.handle("GET", "/api/v1/not-present", new byte[0]).status());
  }

  @Test
  void escapesJsonSpecialCharactersExactly() {
    // Characterization: named escapes for the quote, backslash, and the C0
    // controls with short forms; every other character below 0x20 becomes a
    // lowercase four digit \\u00XX sequence; DEL, non-ASCII, and supplementary
    // plane characters pass through unescaped.
    WebHttpResponse response = GrpcJsonApi.error(400, Status.Code.INVALID_ARGUMENT,
        "q\"\\\b\f\n\r\t\u0001\u001F\u007F\u00E9\uD83D\uDE00");

    assertEquals("{\"code\":\"INVALID_ARGUMENT\",\"message\":"
        + "\"q\\\"\\\\\\b\\f\\n\\r\\t\\u0001\\u001f\u007F\u00E9\uD83D\uDE00\"}",
        response.bodyUtf8());
  }

  private static class StubAnalysisRpc implements AnalysisRpc {

    @Override
    public org.apache.opennlp.grpc.v1.ListOutputFormatsResponse listOutputFormats() {
      return org.apache.opennlp.grpc.v1.ListOutputFormatsResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.FormatDocumentResponse formatDocument(
        org.apache.opennlp.grpc.v1.FormatDocumentRequest request) {
      return org.apache.opennlp.grpc.v1.FormatDocumentResponse.newBuilder()
          .setMediaType("text/plain")
          .setFileExtension(request.getFormatId())
          .build();
    }

    @Override
    public GetServiceInfoResponse getServiceInfo() {
      return GetServiceInfoResponse.newBuilder()
          .setOpennlpVersion("3.0.0")
          .setApiVersion("v1")
          .build();
    }

    @Override
    public ListModelBundlesResponse listModelBundles() {
      return ListModelBundlesResponse.getDefaultInstance();
    }

    @Override
    public AnalyzeDocumentResponse analyze(AnalyzeDocumentRequest request) {
      return AnalyzeDocumentResponse.newBuilder()
          .setDocument(request.getDocument())
          .build();
    }

    @Override
    public java.util.Iterator<org.apache.opennlp.grpc.v1.AnalyzeStreamResponse> analyzeStream(
        java.util.List<org.apache.opennlp.grpc.v1.AnalyzeStreamRequest> frames) {
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
}
