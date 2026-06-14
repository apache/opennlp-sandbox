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
package org.apache.opennlp.grpc.v1.server;

import java.util.Map;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the gRPC service boundary in {@link OpenNlpAnalysisServiceImpl}: that successful analyses
 * are delivered, that {@link AnalysisException}s map to their status carrying the message, and
 * crucially that an <em>unexpected</em> exception is turned into a clean INTERNAL status (logged
 * server-side) rather than escaping the handler as an opaque UNKNOWN or leaking internals.
 */
class OpenNlpAnalysisServiceImplTest {

  private static OpenNlpAnalysisServiceImpl serviceWith(DocumentAnalyzer analyzer) {
    return new OpenNlpAnalysisServiceImpl(
        analyzer, ProfileRegistry.createDefault(), new ModelBundleCache(Map.of()), "test");
  }

  private static AnalyzeDocumentRequest request() {
    return AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText("hello").build())
        .build();
  }

  @Test
  void deliversResponseOnSuccess() {
    final AnalyzeDocumentResponse response = AnalyzeDocumentResponse.newBuilder().build();
    final CapturingObserver observer = new CapturingObserver();

    serviceWith(req -> response).analyzeDocument(request(), observer);

    assertNotNull(observer.value);
    assertTrue(observer.completed);
    assertNull(observer.error);
  }

  @Test
  void mapsAnalysisExceptionToItsStatusWithMessage() {
    final CapturingObserver observer = new CapturingObserver();

    serviceWith(req -> {
      throw AnalysisException.invalidArgument("ner_entity_types must not contain blank values");
    }).analyzeDocument(request(), observer);

    assertNotNull(observer.error);
    final Status status = Status.fromThrowable(observer.error);
    assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
    assertTrue(status.getDescription().contains("ner_entity_types"));
    assertFalse(observer.completed);
  }

  @Test
  void mapsUnexpectedExceptionToInternalWithoutLeakingDetail() {
    final CapturingObserver observer = new CapturingObserver();

    serviceWith(req -> {
      throw new IllegalStateException("secret internal stack detail");
    }).analyzeDocument(request(), observer);

    assertNotNull(observer.error);
    final Status status = Status.fromThrowable(observer.error);
    assertEquals(Status.Code.INTERNAL, status.getCode());
    // The raw exception message must not be surfaced to the client.
    assertEquals("Internal server error", status.getDescription());
    assertFalse(observer.completed);
  }

  /** Captures the terminal callback the service makes on the response stream. */
  private static final class CapturingObserver implements StreamObserver<AnalyzeDocumentResponse> {
    private AnalyzeDocumentResponse value;
    private Throwable error;
    private boolean completed;

    @Override
    public void onNext(AnalyzeDocumentResponse value) {
      this.value = value;
    }

    @Override
    public void onError(Throwable error) {
      this.error = error;
    }

    @Override
    public void onCompleted() {
      this.completed = true;
    }
  }
}
