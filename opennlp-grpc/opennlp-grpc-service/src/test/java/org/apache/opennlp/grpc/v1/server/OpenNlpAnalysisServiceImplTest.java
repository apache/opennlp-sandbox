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

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.processor.basic.BasicDocumentAnalyzer;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.testing.StubSentenceDetectorBackendFactory;
import org.apache.opennlp.grpc.testing.StubTokenizerBackendFactory;
import org.apache.opennlp.grpc.v1.FormatDocumentRequest;
import org.apache.opennlp.grpc.v1.FormatDocumentResponse;
import org.apache.opennlp.grpc.v1.ListOutputFormatsRequest;
import org.apache.opennlp.grpc.v1.ListOutputFormatsResponse;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentEvent;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalysisProfile;
import org.apache.opennlp.grpc.v1.ComponentType;
import org.apache.opennlp.grpc.v1.ConfiguredResource;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.PipelineStep;
import org.apache.opennlp.grpc.v1.StandardLayer;
import org.apache.opennlp.grpc.v1.StandardResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests the gRPC service boundary in {@link OpenNlpAnalysisServiceImpl}: that successful analyses
 * are delivered, that {@link AnalysisException}s map to their status carrying the message, and
 * crucially that an <em>unexpected</em> exception is turned into a clean INTERNAL status (logged
 * server-side) rather than escaping the handler as an opaque UNKNOWN or leaking internals.
 */
class OpenNlpAnalysisServiceImplTest {

  private static OpenNlpAnalysisServiceImpl serviceWith(DocumentAnalyzer analyzer) {
    return serviceWith(analyzer, new ModelBundleCache(Map.of()));
  }

  private static OpenNlpAnalysisServiceImpl serviceWith(
      DocumentAnalyzer analyzer, ModelBundleCache modelBundleCache) {
    return new OpenNlpAnalysisServiceImpl(
        analyzer, ProfileRegistry.createDefault(), modelBundleCache, "test");
  }

  @Test
  void listsTheBuiltInOutputFormats() {
    final OpenNlpAnalysisServiceImpl service = serviceWith(request -> {
      throw new IllegalStateException("analysis must not run");
    });
    final CapturingObserver<ListOutputFormatsResponse> observer = new CapturingObserver<>();

    service.listOutputFormats(ListOutputFormatsRequest.getDefaultInstance(), observer);

    assertNull(observer.error);
    assertEquals(List.of("proto", "protojson", "tsv"),
        observer.value.getFormatsList().stream()
            .map(format -> format.getFormatId()).toList());
  }

  @Test
  void formatsADocumentThroughADeployedFormatter() {
    final OpenNlpAnalysisServiceImpl service = serviceWith(request -> {
      throw new IllegalStateException("analysis must not run");
    });
    final OpenNlpDocument document =
        OpenNlpDocument.newBuilder().setDocId("doc-1").setRawText("Alpha.").build();
    final CapturingObserver<FormatDocumentResponse> observer = new CapturingObserver<>();

    service.formatDocument(FormatDocumentRequest.newBuilder()
        .setDocument(document).setFormatId("proto").build(), observer);

    assertNull(observer.error);
    assertEquals("application/x-protobuf", observer.value.getMediaType());
    assertEquals(document.toByteString(), observer.value.getContent());
  }

  @Test
  void unknownOutputFormatMapsToNotFound() {
    final OpenNlpAnalysisServiceImpl service = serviceWith(request -> {
      throw new IllegalStateException("analysis must not run");
    });
    final CapturingObserver<FormatDocumentResponse> observer = new CapturingObserver<>();

    service.formatDocument(FormatDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.getDefaultInstance())
        .setFormatId("warc-missing").build(), observer);

    assertEquals(Status.Code.NOT_FOUND,
        Status.fromThrowable(observer.error).getCode());
  }

  @Test
  void formatWithoutADocumentMapsToInvalidArgument() {
    final OpenNlpAnalysisServiceImpl service = serviceWith(request -> {
      throw new IllegalStateException("analysis must not run");
    });
    final CapturingObserver<FormatDocumentResponse> observer = new CapturingObserver<>();

    service.formatDocument(FormatDocumentRequest.newBuilder()
        .setFormatId("tsv").build(), observer);

    assertEquals(Status.Code.INVALID_ARGUMENT,
        Status.fromThrowable(observer.error).getCode());
  }

  private static String resourcePath(String name) {
    try {
      return Path.of(OpenNlpAnalysisServiceImplTest.class.getResource(name).toURI()).toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
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
  void progressiveAnalysisPublishesBackboneLayersBeforeTheCanonicalResponse()
      throws InterruptedException {
    final AnalyzeDocumentRequest request = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder()
            .setDocId("doc-progressive")
            .setRawText("Hello world. A second sentence.")
            .build())
        .setProfile(AnalysisProfile.newBuilder()
            .addSteps(PipelineStep.PIPELINE_STEP_SENTENCE_DETECT)
            .addSteps(PipelineStep.PIPELINE_STEP_TOKENIZE)
            .addSteps(PipelineStep.PIPELINE_STEP_POS_TAG)
            .addSteps(PipelineStep.PIPELINE_STEP_LEMMATIZE)
            .build())
        .build();
    final ListCapturingObserver<AnalyzeDocumentEvent> observer =
        new ListCapturingObserver<>();

    try (BasicDocumentAnalyzer analyzer = new BasicDocumentAnalyzer(Map.of())) {
      serviceWith(analyzer).analyzeDocumentProgressive(request, observer);

      assertTrue(observer.await(), "progressive analysis did not terminate");
      assertNull(observer.error);
      assertTrue(observer.completed);
      assertTrue(observer.values.size() >= 3);
      assertEquals(AnalyzeDocumentEvent.UpdateCase.STARTED,
          observer.values.getFirst().getUpdateCase());
      assertEquals(AnalyzeDocumentEvent.UpdateCase.COMPLETE,
          observer.values.getLast().getUpdateCase());
      for (int i = 0; i < observer.values.size(); i++) {
        assertEquals(i + 1L, observer.values.get(i).getSequence());
      }
      final int completeIndex = observer.values.size() - 1;
      final int backboneIndex = observer.values.stream()
          .filter(event -> event.hasLayersReady())
          .filter(event -> event.getLayersReady().getLayersList().stream()
              .anyMatch(layer -> layer.getId().equals("opennlp:tokens")))
          .mapToInt(observer.values::indexOf)
          .findFirst()
          .orElseThrow();
      assertTrue(backboneIndex < completeIndex);
      assertTrue(observer.values.stream()
          .filter(event -> event.hasLayersReady())
          .flatMap(event -> event.getLayersReady().getLayersList().stream())
          .anyMatch(layer -> layer.getId().equals("opennlp:pos")));
      assertEquals(2, observer.values.stream()
          .filter(event -> event.hasLayersReady())
          .flatMap(event -> event.getLayersReady().getLayersList().stream())
          .filter(layer -> layer.getId().equals("opennlp:analytics"))
          .count());
      assertEquals(analyzer.analyze(request), observer.values.getLast().getComplete());
    }
  }

  @Test
  void progressiveAnalysisRejectsANullObserver() {
    final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> serviceWith(request -> AnalyzeDocumentResponse.getDefaultInstance())
            .analyzeDocumentProgressive(request(), null));

    assertEquals("responseObserver must not be null", error.getMessage());
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
    assertTrue(observer.value.getConfiguredResourcesList().isEmpty());
    assertEquals(1_048_576, observer.value.getMaxTextBytes());
    assertTrue(observer.completed);
    assertNull(observer.error);
  }

  @Test
  void serviceInfoReportsTheServiceBuildVersionOnItsPinnedField() {
    final var serviceVersion = GetServiceInfoResponse.getDescriptor()
        .findFieldByName("service_version");
    assertNotNull(serviceVersion, "GetServiceInfoResponse.service_version is missing");
    assertEquals(10, serviceVersion.getNumber());
    final CapturingObserver<GetServiceInfoResponse> observer = new CapturingObserver<>();

    serviceWith(request -> AnalyzeDocumentResponse.getDefaultInstance())
        .getServiceInfo(GetServiceInfoRequest.getDefaultInstance(), observer);

    assertNotNull(observer.value);
    assertEquals("dev", observer.value.getField(serviceVersion));
    assertTrue(observer.completed);
    assertNull(observer.error);
  }

  @Test
  void rejectsTextBeyondTheOperatorLimitBeforeCallingTheAnalyzer() {
    final AtomicBoolean analyzed = new AtomicBoolean();
    final CapturingObserver<AnalyzeDocumentResponse> observer = new CapturingObserver<>();
    final AnalyzeDocumentRequest oversized = AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText("é".repeat(524_289)))
        .build();

    serviceWith(request -> {
      analyzed.set(true);
      return AnalyzeDocumentResponse.getDefaultInstance();
    }).analyzeDocument(oversized, observer);

    assertFalse(analyzed.get());
    assertNotNull(observer.error);
    assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(observer.error).getCode());
    assertTrue(Status.fromThrowable(observer.error).getDescription().contains("1048576"));
  }

  @Test
  void countsSupplementaryTextAsFourUtf8Bytes() {
    final ModelBundleCache cache = new ModelBundleCache(Map.of());
    final AtomicInteger analyzed = new AtomicInteger();
    final OpenNlpAnalysisServiceImpl service = new OpenNlpAnalysisServiceImpl(
        request -> {
          analyzed.incrementAndGet();
          return AnalyzeDocumentResponse.getDefaultInstance();
        },
        ProfileRegistry.createDefault(),
        cache,
        "test",
        ForkJoinPool.commonPool(),
        1,
        4);
    final CapturingObserver<AnalyzeDocumentResponse> accepted = new CapturingObserver<>();
    final CapturingObserver<AnalyzeDocumentResponse> rejected = new CapturingObserver<>();

    service.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText("😀"))
        .build(), accepted);
    service.analyzeDocument(AnalyzeDocumentRequest.newBuilder()
        .setDocument(OpenNlpDocument.newBuilder().setRawText("😀a"))
        .build(), rejected);

    assertEquals(1, analyzed.get());
    assertNull(accepted.error);
    assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(rejected.error).getCode());
  }

  @Test
  void rejectsANonPositiveOperatorByteLimit() {
    final ModelBundleCache cache = new ModelBundleCache(Map.of());

    final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> new OpenNlpAnalysisServiceImpl(
            request -> AnalyzeDocumentResponse.getDefaultInstance(),
            ProfileRegistry.createDefault(),
            cache,
            "test",
            ForkJoinPool.commonPool(),
            1,
            0));

    assertEquals("maxTextBytes must be positive", error.getMessage());
  }

  @Test
  void serviceInfoAdvertisesConfiguredNonModelResources() {
    final ModelBundleCache cache = new ModelBundleCache(Map.of(
        "model.subword.tiny.path", resourcePath("/subword/tiny-unigram-bytefb.model"),
        "model.wordnet.mini.path", resourcePath("/wordnet/mini-wn-lmf.xml")));
    final CapturingObserver<GetServiceInfoResponse> observer = new CapturingObserver<>();

    serviceWith(request -> AnalyzeDocumentResponse.getDefaultInstance(), cache)
        .getServiceInfo(GetServiceInfoRequest.getDefaultInstance(), observer);

    assertNotNull(observer.value);
    assertEquals(2, observer.value.getConfiguredResourcesCount());
    assertResource(observer.value, StandardResource.STANDARD_RESOURCE_SUBWORD_MODEL, "tiny");
    assertResource(observer.value, StandardResource.STANDARD_RESOURCE_WORDNET_LEXICON, "mini");
    assertTrue(observer.completed);
    assertNull(observer.error);
  }

  @Test
  void modelBundlesAdvertiseConfiguredParserAndChunkerModels() {
    final String parserPath = System.getProperty("parser.model.path");
    final String chunkerPath = System.getProperty("chunker.model.path");
    assumeTrue(parserPath != null && Files.isRegularFile(Path.of(parserPath))
            && chunkerPath != null && Files.isRegularFile(Path.of(chunkerPath)),
        "set parser.model.path and chunker.model.path to real OpenNLP models");

    try (ModelBundleCache cache = new ModelBundleCache(Map.of(
        "model.parser.gum-cc-by-4.path", parserPath,
        "model.chunker.gum-cc-by-4.path", chunkerPath))) {
      final CapturingObserver<ListModelBundlesResponse> observer = new CapturingObserver<>();

      serviceWith(request -> AnalyzeDocumentResponse.getDefaultInstance(), cache)
          .listModelBundles(ListModelBundlesRequest.getDefaultInstance(), observer);

      assertTrue(observer.completed);
      assertNull(observer.error);
      final Set<ComponentType> componentTypes = observer.value.getBundlesList().stream()
          .flatMap(bundle -> bundle.getModelsList().stream())
          .map(model -> model.getComponentType())
          .collect(java.util.stream.Collectors.toSet());
      assertTrue(componentTypes.contains(ComponentType.COMPONENT_TYPE_PARSER));
      assertTrue(componentTypes.contains(ComponentType.COMPONENT_TYPE_CHUNKER));
    }
  }

  private static void assertResource(
      GetServiceInfoResponse response, StandardResource type, String id) {
    final ConfiguredResource resource = response.getConfiguredResourcesList().stream()
        .filter(candidate -> candidate.getIdentity().getStandard() == type)
        .findFirst().orElseThrow();
    assertEquals(id, resource.getResourceId());
    assertTrue(resource.getIsDefault());
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

  /** Captures every callback from an asynchronous response stream. */
  private static final class ListCapturingObserver<T> implements StreamObserver<T> {
    private final List<T> values = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final CountDownLatch terminal = new CountDownLatch(1);
    private volatile Throwable error;
    private volatile boolean completed;

    @Override
    public void onNext(T value) {
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

    private boolean await() throws InterruptedException {
      return terminal.await(10, TimeUnit.SECONDS);
    }
  }
}
