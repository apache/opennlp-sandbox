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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.EmbedTextRequest;
import org.apache.opennlp.grpc.v1.EmbedTextResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the EmbedText streaming boundary: vectors come back in request order with echoed
 * sequences, the stream's model is fixed by the first message, and every failure mode
 * (unknown model, mid-stream model switch, blank text, unresolvable default) terminates the
 * stream with its status instead of a per-message error payload.
 */
class EmbedTextStreamTest {

  // The stub embedding backend (registered via test META-INF/services) contributes one
  // 3-dimensional model named "mini" when activated through this configuration.
  private static OpenNlpAnalysisServiceImpl serviceWithStubModel() {
    return new OpenNlpAnalysisServiceImpl(
        req -> {
          throw new UnsupportedOperationException("not under test");
        },
        ProfileRegistry.createDefault(),
        new ModelBundleCache(Map.of("model.embedder.stub.model_id", "mini")),
        "test");
  }

  private static OpenNlpAnalysisServiceImpl serviceWithNoModels() {
    return new OpenNlpAnalysisServiceImpl(
        req -> {
          throw new UnsupportedOperationException("not under test");
        },
        ProfileRegistry.createDefault(),
        new ModelBundleCache(Map.of()),
        "test");
  }

  private static EmbedTextRequest text(long sequence, String text) {
    return EmbedTextRequest.newBuilder().setSequence(sequence).setText(text).build();
  }

  private static EmbedTextRequest text(long sequence, String text, String modelId) {
    return EmbedTextRequest.newBuilder()
        .setSequence(sequence).setText(text).setModelId(modelId).build();
  }

  @Test
  void streamsVectorsInOrderWithEchoedSequences() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithStubModel().embedText(responses);

    requests.onNext(text(7, "one sentence"));
    requests.onNext(text(8, "another sentence"));
    requests.onNext(text(9, "a third"));
    requests.onCompleted();

    assertNull(responses.error);
    assertTrue(responses.completed);
    assertEquals(3, responses.values.size());
    assertEquals(7, responses.values.get(0).getSequence());
    assertEquals(8, responses.values.get(1).getSequence());
    assertEquals(9, responses.values.get(2).getSequence());
    assertEquals(3, responses.values.get(0).getVectorCount());
  }

  @Test
  void acceptsAConsistentExplicitModelIdOnLaterMessages() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithStubModel().embedText(responses);

    requests.onNext(text(1, "first", "mini"));
    requests.onNext(text(2, "second", "mini"));
    requests.onCompleted();

    assertNull(responses.error);
    assertTrue(responses.completed);
    assertEquals(2, responses.values.size());
  }

  @Test
  void rejectsAnUnknownModelId() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithStubModel().embedText(responses);

    requests.onNext(text(1, "first", "no-such-model"));

    assertNotNull(responses.error);
    assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(responses.error).getCode());
    assertFalse(responses.completed);
    assertTrue(responses.values.isEmpty());
  }

  @Test
  void rejectsAModelSwitchMidStreamAndIgnoresLaterMessages() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithStubModel().embedText(responses);

    requests.onNext(text(1, "first", "mini"));
    requests.onNext(text(2, "second", "other-model"));
    requests.onNext(text(3, "after the failure"));
    requests.onCompleted();

    assertNotNull(responses.error);
    final Status status = Status.fromThrowable(responses.error);
    assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
    assertTrue(status.getDescription().contains("one model"));
    // The first message succeeded; nothing after the failure produced output or completion.
    assertEquals(1, responses.values.size());
    assertFalse(responses.completed);
  }

  @Test
  void rejectsBlankTextNamingTheSequence() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithStubModel().embedText(responses);

    requests.onNext(text(42, "   "));

    assertNotNull(responses.error);
    final Status status = Status.fromThrowable(responses.error);
    assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
    assertTrue(status.getDescription().contains("42"));
  }

  @Test
  void rejectsAStreamWithNoResolvableModel() {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<EmbedTextRequest> requests =
        serviceWithNoModels().embedText(responses);

    requests.onNext(text(1, "first"));

    assertNotNull(responses.error);
    assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(responses.error).getCode());
  }

  /** Captures everything the service emits on the response stream. */
  private static final class CapturingObserver implements StreamObserver<EmbedTextResponse> {
    private final List<EmbedTextResponse> values = new ArrayList<>();
    private Throwable error;
    private boolean completed;

    @Override
    public void onNext(EmbedTextResponse value) {
      values.add(value);
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
