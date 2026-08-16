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

import java.nio.charset.StandardCharsets;

import io.grpc.Status;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.junit.jupiter.api.Test;

class GrpcJsonApiTest {

  @Test
  void rendersServiceInfoAsProtobufJson() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc());

    WebHttpResponse response = api.handle("GET", "/api/v1/service-info", new byte[0]);

    assertEquals(200, response.status());
    assertEquals("application/json; charset=utf-8", response.contentType());
    assertTrue(response.bodyUtf8().contains("\"opennlpVersion\": \"3.0.0\""));
  }

  @Test
  void parsesAnalyzeRequestAndRendersDocumentShape() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc());
    byte[] request = """
        {"document":{"docId":"one","rawText":"Hello world."}}
        """.getBytes(StandardCharsets.UTF_8);

    WebHttpResponse response = api.handle("POST", "/api/v1/analyze", request);

    assertEquals(200, response.status());
    assertTrue(response.bodyUtf8().contains("\"docId\": \"one\""));
    assertTrue(response.bodyUtf8().contains("\"rawText\": \"Hello world.\""));
  }

  @Test
  void rejectsMalformedJsonWithCanonicalErrorPayload() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc());

    WebHttpResponse response = api.handle("POST", "/api/v1/analyze",
        "not-json".getBytes(StandardCharsets.UTF_8));

    assertEquals(400, response.status());
    assertTrue(response.bodyUtf8().contains("\"code\":\"INVALID_ARGUMENT\""));
    assertTrue(response.bodyUtf8().contains("Malformed protobuf JSON request"));
  }

  @Test
  void rejectsMalformedUtf8BeforeParsingProtobufJson() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc());

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
    GrpcJsonApi api = new GrpcJsonApi(unavailable);

    WebHttpResponse response = api.handle("POST", "/api/v1/analyze",
        "{\"document\":{\"rawText\":\"Hello\"}}".getBytes(StandardCharsets.UTF_8));

    assertEquals(503, response.status());
    assertTrue(response.bodyUtf8().contains("\"code\":\"UNAVAILABLE\""));
    assertTrue(response.bodyUtf8().contains("embedding backend unavailable"));
    assertTrue(!response.bodyUtf8().contains("secret connection string"));
  }

  @Test
  void enforcesMethodsAndKnownApiPaths() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc());

    assertEquals(405,
        api.handle("GET", "/api/v1/analyze", new byte[0]).status());
    assertEquals(404,
        api.handle("GET", "/api/v1/not-present", new byte[0]).status());
  }

  private static class StubAnalysisRpc implements AnalysisRpc {

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
  }
}
