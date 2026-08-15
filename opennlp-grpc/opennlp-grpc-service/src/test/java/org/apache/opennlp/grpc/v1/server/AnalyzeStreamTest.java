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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.opennlp.grpc.v1.server;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalysisOptions;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;
import org.apache.opennlp.grpc.v1.AnalyzeStreamDocument;
import org.apache.opennlp.grpc.v1.AnalyzeStreamRequest;
import org.apache.opennlp.grpc.v1.AnalyzeStreamResponse;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the concurrent, options-first full document analysis stream. */
class AnalyzeStreamTest {

  private static OpenNlpAnalysisServiceImpl serviceWith(DocumentAnalyzer analyzer) {
    return new OpenNlpAnalysisServiceImpl(
        analyzer, ProfileRegistry.createDefault(), new ModelBundleCache(Map.of()), "test");
  }

  private static AnalyzeStreamRequest configuration() {
    return AnalyzeStreamRequest.newBuilder()
        .setConfiguration(AnalyzeStreamConfiguration.newBuilder().build())
        .build();
  }

  private static AnalyzeStreamRequest document(long sequence, String text) {
    return AnalyzeStreamRequest.newBuilder()
        .setDocument(AnalyzeStreamDocument.newBuilder()
            .setSequence(sequence)
            .setDocument(OpenNlpDocument.newBuilder()
                .setDocId("doc-" + sequence)
                .setRawText(text)))
        .build();
  }

  private static AnalyzeDocumentResponse echo(org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest r) {
    return AnalyzeDocumentResponse.newBuilder().setDocument(r.getDocument()).build();
  }

  @Test
  void delegatesEveryDocumentThroughTheGenericAnalyzerAndCompletesAfterHalfClose()
      throws Exception {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(AnalyzeStreamTest::echo)
        .analyzeStream(responses);

    requests.onNext(configuration());
    requests.onNext(document(7, "first"));
    requests.onNext(document(8, "second"));
    requests.onCompleted();

    assertTrue(responses.awaitTerminal());
    assertNull(responses.error);
    assertTrue(responses.completed);
    assertEquals("first", responses.bySequence(7).getOk().getDocument().getRawText());
    assertEquals("second", responses.bySequence(8).getOk().getDocument().getRawText());
  }

  @Test
  void copiesTheFixedConfigurationOntoEveryAnalyzerRequest() throws Exception {
    final BlockingQueue<org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest> analyzed =
        new LinkedBlockingQueue<>();
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(request -> {
      analyzed.add(request);
      return echo(request);
    }).analyzeStream(responses);
    final AnalysisProfile profile = AnalysisProfile.newBuilder()
        .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
        .build();
    final AnalysisOptions options = AnalysisOptions.newBuilder()
        .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
        .build();

    requests.onNext(AnalyzeStreamRequest.newBuilder()
        .setConfiguration(AnalyzeStreamConfiguration.newBuilder()
            .setProfile(profile)
            .setOptions(options)
            .setProfileId("bulk"))
        .build());
    requests.onNext(document(1, "configured"));
    requests.onCompleted();

    assertTrue(responses.awaitTerminal());
    final var request = analyzed.poll(5, TimeUnit.SECONDS);
    assertNotNull(request);
    assertEquals(profile, request.getProfile());
    assertEquals(options, request.getOptions());
    assertEquals("bulk", request.getProfileId());
  }

  @Test
  void returnsDocumentFailuresWithoutTerminatingTheStream() throws Exception {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(request -> {
      if (request.getDocument().getRawText().equals("bad")) {
        throw AnalysisException.invalidArgument("bad document");
      }
      return echo(request);
    }).analyzeStream(responses);

    requests.onNext(configuration());
    requests.onNext(document(1, "good"));
    requests.onNext(document(2, "bad"));
    requests.onNext(document(3, "also good"));
    requests.onCompleted();

    assertTrue(responses.awaitTerminal());
    assertTrue(responses.completed);
    assertNull(responses.error);
    assertTrue(responses.bySequence(1).hasOk());
    assertEquals(Status.Code.INVALID_ARGUMENT.value(),
        responses.bySequence(2).getError().getCode());
    assertTrue(responses.bySequence(2).getError().getMessage().contains("bad document"));
    assertTrue(responses.bySequence(3).hasOk());
  }

  @Test
  void returnsUnexpectedDocumentFailuresWithoutLeakingTheirDetails() throws Exception {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(request -> {
      throw new IllegalStateException("secret implementation detail");
    }).analyzeStream(responses);

    requests.onNext(configuration());
    requests.onNext(document(4, "boom"));
    requests.onCompleted();

    assertTrue(responses.awaitTerminal());
    final var error = responses.bySequence(4).getError();
    assertEquals(Status.Code.INTERNAL.value(), error.getCode());
    assertEquals("Internal server error", error.getMessage());
    assertFalse(error.getMessage().contains("secret"));
  }

  @Test
  void rejectsADocumentBeforeTheConfiguration() throws Exception {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(AnalyzeStreamTest::echo)
        .analyzeStream(responses);

    requests.onNext(document(1, "too early"));

    assertTrue(responses.awaitTerminal());
    assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(responses.error).getCode());
    assertFalse(responses.completed);
  }

  @Test
  void rejectsASecondConfiguration() throws Exception {
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(AnalyzeStreamTest::echo)
        .analyzeStream(responses);

    requests.onNext(configuration());
    requests.onNext(configuration());

    assertTrue(responses.awaitTerminal());
    assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(responses.error).getCode());
    assertFalse(responses.completed);
  }

  @Test
  void emitsResponsesInCompletionOrderWithoutHeadOfLineBlocking() throws Exception {
    final CountDownLatch slowStarted = new CountDownLatch(1);
    final CountDownLatch releaseSlow = new CountDownLatch(1);
    final CapturingObserver responses = new CapturingObserver();
    final StreamObserver<AnalyzeStreamRequest> requests = serviceWith(request -> {
      if (request.getDocument().getRawText().equals("slow")) {
        slowStarted.countDown();
        await(releaseSlow);
      } else {
        await(slowStarted);
      }
      return echo(request);
    }).analyzeStream(responses);

    requests.onNext(configuration());
    requests.onNext(document(1, "slow"));
    requests.onNext(document(2, "fast"));

    final AnalyzeStreamResponse first = responses.next();
    assertNotNull(first);
    assertEquals(2, first.getSequence());

    releaseSlow.countDown();
    requests.onCompleted();
    assertTrue(responses.awaitTerminal());
    assertEquals(1, responses.next().getSequence());
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("test synchronization timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("test synchronization interrupted", e);
    }
  }

  /** Captures stream responses and terminal callbacks across worker threads. */
  private static final class CapturingObserver implements StreamObserver<AnalyzeStreamResponse> {
    private final BlockingQueue<AnalyzeStreamResponse> values = new LinkedBlockingQueue<>();
    private final CountDownLatch terminal = new CountDownLatch(1);
    private volatile Throwable error;
    private volatile boolean completed;

    @Override
    public void onNext(AnalyzeStreamResponse value) {
      values.add(value);
    }

    @Override
    public void onError(Throwable error) {
      this.error = error;
      terminal.countDown();
    }

    @Override
    public void onCompleted() {
      completed = true;
      terminal.countDown();
    }

    private boolean awaitTerminal() throws InterruptedException {
      return terminal.await(5, TimeUnit.SECONDS);
    }

    private AnalyzeStreamResponse next() throws InterruptedException {
      return values.poll(5, TimeUnit.SECONDS);
    }

    private AnalyzeStreamResponse bySequence(long sequence) {
      return values.stream()
          .filter(value -> value.getSequence() == sequence)
          .findFirst()
          .orElseThrow(() -> new AssertionError("Missing response for sequence " + sequence));
    }
  }
}
