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

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.testing.StubSentenceDetectorBackendFactory;
import org.apache.opennlp.grpc.testing.StubTokenizerBackendFactory;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.StandardLayer;
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
    final CapturingObserver<AnalyzeDocumentResponse> observer = new CapturingObserver<>();

    serviceWith(req -> response).analyzeDocument(request(), observer);

    assertNotNull(observer.value);
    assertTrue(observer.completed);
    assertNull(observer.error);
  }

  @Test
  void mapsAnalysisExceptionToItsStatusWithMessage() {
    final CapturingObserver<AnalyzeDocumentResponse> observer = new CapturingObserver<>();

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
    final CapturingObserver<AnalyzeDocumentResponse> observer = new CapturingObserver<>();

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

  @Test
  void serviceInfoAdvertisesEveryStandardDocumentLayer() {
    final CapturingObserver<GetServiceInfoResponse> observer = new CapturingObserver<>();

    serviceWith(request -> AnalyzeDocumentResponse.getDefaultInstance())
        .getServiceInfo(GetServiceInfoRequest.getDefaultInstance(), observer);

    assertNotNull(observer.value);
    assertEquals(Set.of(StandardLayer.values()).stream()
            .filter(layer -> layer != StandardLayer.STANDARD_LAYER_UNSPECIFIED
                && layer != StandardLayer.UNRECOGNIZED)
            .collect(java.util.stream.Collectors.toSet()),
        Set.copyOf(observer.value.getSupportedLayersList()));
    assertEquals(List.of(StubTokenizerBackendFactory.ENGINE_ID),
        observer.value.getCustomTokenizerIdsList());
    assertEquals(List.of(StubSentenceDetectorBackendFactory.ENGINE_ID),
        observer.value.getCustomSentenceDetectorIdsList());
    assertTrue(observer.completed);
    assertNull(observer.error);
  }

  /** Captures the terminal callback the service makes on the response stream. */
  private static final class CapturingObserver<T> implements StreamObserver<T> {
    private T value;
    private Throwable error;
    private boolean completed;

    @Override
    public void onNext(T value) {
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
