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
package org.apache.opennlp.grpc.search;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.embedding.EmbeddingBatchResult;
import org.apache.opennlp.grpc.embedding.EmbeddingProvider;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.apache.opennlp.grpc.v1.ListSearchIndexesRequest;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenNlpSearchServiceImplTest {

  @Test
  void listsConfiguredIndexesInStableOrder() {
    final OpenNlpSearchServiceImpl service = service(
        provider(SearchIndexRegistryTest.descriptor("zeta"), List.of()),
        provider(SearchIndexRegistryTest.descriptor("alpha"), List.of()));
    final CapturingObserver<ListSearchIndexesResponse> observer = new CapturingObserver<>();

    service.listSearchIndexes(ListSearchIndexesRequest.getDefaultInstance(), observer);

    assertEquals(List.of("alpha", "zeta"), observer.value.getIndexesList().stream()
        .map(SearchIndexDescriptor::getIndexId).toList());
    assertTrue(observer.completed);
    assertNull(observer.error);
  }

  @Test
  void rejectsUnknownIndexBlankQueryAndInvalidTopK() {
    final OpenNlpSearchServiceImpl service = service(
        provider(SearchIndexRegistryTest.descriptor("legal"), List.of()));

    assertStatus(service, request("missing", "query", 1), Status.Code.NOT_FOUND);
    assertStatus(service, request("legal", " ", 1), Status.Code.INVALID_ARGUMENT);
    assertStatus(service, request("legal", "query", 0), Status.Code.INVALID_ARGUMENT);
    assertStatus(service, request("legal", "query", 51), Status.Code.INVALID_ARGUMENT);
    assertStatus(service, request("legal", "x".repeat(1025), 1), Status.Code.INVALID_ARGUMENT);
  }

  @Test
  void embedsOnDeclaredRouteAndReturnsStableNegativeScoresWithProvenance() {
    final SearchIndexDescriptor descriptor = SearchIndexRegistryTest.descriptor("legal");
    final SearchIndexProvider provider = provider(descriptor, List.of(
        result("doc-b", "chunk-b", -0.5),
        result("doc-a", "chunk-a", 0.75),
        result("doc-c", "chunk-c", -0.5)));
    final OpenNlpSearchServiceImpl service = service(provider);
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service.searchIndex(request("legal", "constitutional liberty", 3), observer);

    assertNull(observer.error);
    assertTrue(observer.completed);
    assertEquals(List.of("chunk-a", "chunk-b", "chunk-c"), observer.value.getHitsList().stream()
        .map(hit -> hit.getChunkId()).toList());
    assertEquals(-0.5, observer.value.getHits(1).getScore());
    assertEquals("mini-v1", observer.value.getQueryEmbeddingRoute().getVectorSpaceId());
    assertEquals(observer.value.getHits(0).getDocumentId(),
        observer.value.getHits(0).getSourceDocument().getDocId());
    assertEquals(descriptor, observer.value.getIndex());
  }

  @Test
  void acceptsDifferentBackendServingTheSameVectorSpace() {
    final SearchIndexProvider provider = provider(
        SearchIndexRegistryTest.descriptor("legal"), List.of(result("doc", "chunk", 1)));
    final EmbeddingRoute alternate = route().toBuilder().setBackendId("onnx").build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of(provider)), new StubEmbeddingProvider(alternate, 4));
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service.searchIndex(request("legal", "query", 1), observer);

    assertNull(observer.error);
    assertEquals("onnx", observer.value.getQueryEmbeddingRoute().getBackendId());
  }

  @Test
  void selectsACompatibleSecondaryRouteWhenTheDefaultVectorSpaceIsIncompatible() {
    final SearchIndexProvider provider = provider(
        SearchIndexRegistryTest.descriptor("legal"), List.of(result("doc", "chunk", 1)));
    final EmbeddingRoute incompatibleDefault = route().toBuilder()
        .setBackendId("default")
        .setVectorSpaceId("other-space")
        .build();
    final EmbeddingRoute compatibleSecondary = route().toBuilder()
        .setBackendId("secondary")
        .setPrimary(false)
        .build();
    final StubEmbeddingProvider embeddings = new StubEmbeddingProvider(
        compatibleSecondary, 4, List.of(incompatibleDefault, compatibleSecondary));
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of(provider)), embeddings);
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service.searchIndex(request("legal", "query", 1), observer);

    assertNull(observer.error);
    assertEquals("secondary", embeddings.requestedBackend.get());
    assertEquals("secondary", observer.value.getQueryEmbeddingRoute().getBackendId());
  }

  @Test
  void rejectsEmbeddingRouteDriftBeforeSearching() {
    final SearchIndexProvider provider = provider(
        SearchIndexRegistryTest.descriptor("legal"), List.of(result("doc", "chunk", 1)));
    final EmbeddingRoute drifted = route().toBuilder().setVectorSpaceId("other-space").build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of(provider)), new StubEmbeddingProvider(drifted, 4));
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service.searchIndex(request("legal", "query", 1), observer);

    assertEquals(Status.Code.FAILED_PRECONDITION,
        Status.fromThrowable(observer.error).getCode());
  }

  @Test
  void truncatesDeterministicallyBeforeExceedingTheResponseByteLimit() {
    final SearchIndexDescriptor descriptor = SearchIndexRegistryTest.descriptor("bounded")
        .toBuilder().setMaxResponseBytes(1_000).build();
    final String largeText = "bounded response text ".repeat(100);
    final SearchIndexProvider provider = provider(descriptor, List.of(
        result("doc-a", "chunk-a", 0.9, largeText),
        result("doc-b", "chunk-b", 0.8, largeText)));
    final OpenNlpSearchServiceImpl service = service(provider);
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service.searchIndex(request("bounded", "query", 2), observer);

    assertNull(observer.error);
    assertTrue(observer.value.getTruncated());
    assertTrue(observer.value.getSerializedSize() <= descriptor.getMaxResponseBytes());
    assertEquals(0, observer.value.getHitsCount());
  }

  @Test
  void rejectsAnActualEmbeddingRouteThatMakesEvenAnEmptyResponseOversized() {
    final SearchIndexDescriptor descriptor = SearchIndexRegistryTest.descriptor("bounded")
        .toBuilder().setMaxResponseBytes(700).build();
    final SearchIndexProvider provider = provider(descriptor, List.of());
    final EmbeddingRoute oversizedActualRoute = route().toBuilder()
        .setBackendId("x".repeat(1_000))
        .build();
    final OpenNlpSearchServiceImpl service = new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of(provider)),
        new StubEmbeddingProvider(oversizedActualRoute, 4));
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service.searchIndex(request("bounded", "query", 1), observer);

    assertEquals(Status.Code.FAILED_PRECONDITION,
        Status.fromThrowable(observer.error).getCode());
    assertTrue(Status.fromThrowable(observer.error).getDescription()
        .contains("max_response_bytes"));
  }

  @Test
  void rejectsConfiguredDimensionMismatchAtStartup() {
    final SearchIndexProvider provider = provider(
        SearchIndexRegistryTest.descriptor("legal"), List.of());

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new OpenNlpSearchServiceImpl(new SearchIndexRegistry(List.of(provider)),
            new StubEmbeddingProvider(route(), 3)));
    assertTrue(exception.getMessage().contains("dimension"));
  }

  @Test
  void mapsUnexpectedProviderFailureToInternalWithoutLeakingDetail() {
    final SearchIndexDescriptor descriptor = SearchIndexRegistryTest.descriptor("legal");
    final SearchIndexProvider provider = new SearchIndexProvider() {
      @Override
      public SearchIndexDescriptor descriptor() {
        return descriptor;
      }

      @Override
      public List<SearchResult> search(float[] queryVector, int topK) {
        throw new IllegalStateException("secret provider detail");
      }
    };
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service(provider).searchIndex(request("legal", "query", 1), observer);

    final Status status = Status.fromThrowable(observer.error);
    assertEquals(Status.Code.INTERNAL, status.getCode());
    assertEquals("Internal server error", status.getDescription());
    assertFalse(observer.completed);
  }

  @Test
  void rejectsAProviderThatReturnsMoreThanTopK() {
    final SearchIndexDescriptor descriptor = SearchIndexRegistryTest.descriptor("legal");
    final SearchIndexProvider provider = new SearchIndexProvider() {
      @Override
      public SearchIndexDescriptor descriptor() {
        return descriptor;
      }

      @Override
      public List<SearchResult> search(float[] queryVector, int topK) {
        return List.of(
            result("doc-a", "chunk-a", 1),
            result("doc-b", "chunk-b", 0.5));
      }
    };
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();

    service(provider).searchIndex(request("legal", "query", 1), observer);

    final Status status = Status.fromThrowable(observer.error);
    assertEquals(Status.Code.INTERNAL, status.getCode());
    assertEquals("Internal server error", status.getDescription());
  }

  private static OpenNlpSearchServiceImpl service(SearchIndexProvider... providers) {
    return new OpenNlpSearchServiceImpl(
        new SearchIndexRegistry(List.of(providers)), new StubEmbeddingProvider(route(), 4));
  }

  private static SearchIndexProvider provider(
      SearchIndexDescriptor descriptor, List<SearchResult> results) {
    return new SearchIndexProvider() {
      @Override
      public SearchIndexDescriptor descriptor() {
        return descriptor;
      }

      @Override
      public List<SearchResult> search(float[] queryVector, int topK) {
        return results.subList(0, Math.min(topK, results.size()));
      }
    };
  }

  private static SearchResult result(String documentId, String chunkId, double score) {
    return result(documentId, chunkId, score, "Retained source for " + chunkId);
  }

  private static SearchResult result(
      String documentId, String chunkId, double score, String text) {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setDocId(documentId)
        .setRawText(text)
        .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
        .build();
    return new SearchResult(new SearchRecord(documentId, chunkId, document,
        AnnotationSpan.newBuilder()
            .setStart(0)
            .setEnd(text.length())
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
            .build(), text), score);
  }

  private static SearchIndexRequest request(String indexId, String query, int topK) {
    return SearchIndexRequest.newBuilder()
        .setIndexId(indexId)
        .setQuery(OpenNlpDocument.newBuilder().setRawText(query))
        .setTopK(topK)
        .build();
  }

  private static EmbeddingRoute route() {
    return EmbeddingRoute.newBuilder()
        .setModelId("mini")
        .setBackendId("static")
        .setVectorSpaceId("mini-v1")
        .setArtifactHash("a".repeat(64))
        .setPrimary(true)
        .build();
  }

  private static void assertStatus(
      OpenNlpSearchServiceImpl service, SearchIndexRequest request, Status.Code expected) {
    final CapturingObserver<SearchIndexResponse> observer = new CapturingObserver<>();
    service.searchIndex(request, observer);
    assertNotNull(observer.error);
    assertEquals(expected, Status.fromThrowable(observer.error).getCode());
  }

  private static final class StubEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingRoute resultRoute;
    private final int dimension;
    private final List<EmbeddingRoute> routes;
    private final AtomicReference<String> requestedBackend = new AtomicReference<>();

    private StubEmbeddingProvider(EmbeddingRoute resultRoute, int dimension) {
      this(resultRoute, dimension, List.of(route()));
    }

    private StubEmbeddingProvider(
        EmbeddingRoute resultRoute, int dimension, List<EmbeddingRoute> routes) {
      this.resultRoute = resultRoute;
      this.dimension = dimension;
      this.routes = routes;
    }

    @Override
    public String backendId() {
      return "static";
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public Set<String> registeredModelIds() {
      return Set.of("mini");
    }

    @Override
    public boolean supportsModel(String modelId) {
      return modelId.equals("mini");
    }

    @Override
    public int embeddingDimension(String modelId) {
      return dimension;
    }

    @Override
    public float[] embed(String modelId, String text) {
      return new float[dimension];
    }

    @Override
    public EmbeddingBatchResult embedBatchResolved(
        String modelId, String backendId, List<String> texts) {
      requestedBackend.set(backendId);
      return new EmbeddingBatchResult(List.of(new float[dimension]), resultRoute);
    }

    @Override
    public List<EmbeddingRoute> routesForModel(String modelId) {
      return routes;
    }

    @Override
    public String modelArtifactHash(String modelId) {
      return "a".repeat(64);
    }
  }

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
      completed = true;
    }
  }
}
