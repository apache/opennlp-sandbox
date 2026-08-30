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
package org.apache.opennlp.grpc.webapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.CollectionDescriptor;
import org.apache.opennlp.grpc.v1.CollectionDriftStats;
import org.apache.opennlp.grpc.v1.CollectionEvent;
import org.apache.opennlp.grpc.v1.CollectionEventKind;
import org.apache.opennlp.grpc.v1.DeleteCollectionRequest;
import org.apache.opennlp.grpc.v1.DeleteCollectionResponse;
import org.apache.opennlp.grpc.v1.GetCollectionRequest;
import org.apache.opennlp.grpc.v1.GetCollectionResponse;
import org.apache.opennlp.grpc.v1.ListCollectionsResponse;
import org.apache.opennlp.grpc.v1.SetCollectionRequest;
import org.apache.opennlp.grpc.v1.SetCollectionResponse;
import org.apache.opennlp.grpc.v1.TermStatistic;
import org.apache.opennlp.grpc.v1.WatchCollectionRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexRequest;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexResponse;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.DeleteIndexAliasRequest;
import org.apache.opennlp.grpc.v1.DeleteIndexAliasResponse;
import org.apache.opennlp.grpc.v1.IndexAlias;
import org.apache.opennlp.grpc.v1.ListIndexAliasesResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.ListSearchProvidersResponse;
import org.apache.opennlp.grpc.v1.PersistIndexRequest;
import org.apache.opennlp.grpc.v1.PersistIndexResponse;
import org.apache.opennlp.grpc.v1.ReindexIndexRequest;
import org.apache.opennlp.grpc.v1.ReindexIndexResponse;
import org.apache.opennlp.grpc.v1.SealIndexRequest;
import org.apache.opennlp.grpc.v1.SealIndexResponse;
import org.apache.opennlp.grpc.v1.SetIndexAliasRequest;
import org.apache.opennlp.grpc.v1.SetIndexAliasResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.SearchProviderCapability;
import org.apache.opennlp.grpc.v1.SearchProviderInstance;
import org.apache.opennlp.grpc.v1.StandardSearchProvider;
import org.apache.opennlp.grpc.v1.SearchHit;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;
import org.junit.jupiter.api.Test;

class GrpcJsonSearchApiTest {

  private static final String INDEX_ID = "legal-demo";
  private static final String DOCUMENT_ID = "passage-1";

  @Test
  void listsSearchIndexesAsProtobufJson() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new StubSearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    WebHttpResponse response = api.handle("GET", "/api/v1/search-indexes", new byte[0]);

    assertEquals(200, response.status());
    assertTrue(response.bodyUtf8().contains("\"indexId\":\"" + INDEX_ID + "\""));
    assertTrue(response.bodyUtf8().contains("\"maxTopK\":25"));
    assertTrue(response.bodyUtf8().contains("\"maxResponseBytes\":1048576"));
  }

  @Test
  void listsSearchProviderInstancesAsProtobufJson() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new StubSearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    WebHttpResponse response = api.handle("GET", "/api/v1/search-providers", new byte[0]);

    assertEquals(200, response.status());
    assertTrue(response.bodyUtf8().contains("\"instanceId\":\"flat_float\""));
    assertTrue(response.bodyUtf8().contains("\"SEARCH_PROVIDER_CAPABILITY_VECTOR\""));
    assertTrue(response.bodyUtf8().contains(
        "\"standard\":\"STANDARD_SEARCH_PROVIDER_FLAT_FLOAT\""));
    assertEquals(405, api.handle("POST", "/api/v1/search-providers", new byte[0]).status());
  }

  @Test
  void parsesExhaustiveDocumentSearchAndRendersDeduplicatedSource() {
    StubSearchRpc searchRpc = new StubSearchRpc();
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), searchRpc,
        new EmptyVocabularyRpc(), new EmptyTrainingRpc());
    byte[] request = """
        {"indexId":"%s","query":{"docId":"query-1",\
        "rawText":"habeas corpus"},"allHits":true}
        """.formatted(INDEX_ID).getBytes(StandardCharsets.UTF_8);

    WebHttpResponse response = api.handle("POST", "/api/v1/search", request);

    assertEquals(200, response.status());
    assertTrue(response.bodyUtf8().contains("\"documentId\":\"" + DOCUMENT_ID + "\""));
    assertTrue(response.bodyUtf8().contains("\"score\":0.75"));
    assertTrue(response.bodyUtf8().contains("\"rawText\":\"The writ must issue.\""));
    assertTrue(response.bodyUtf8().contains("\"truncated\":true"));
    assertTrue(searchRpc.lastSearch.getAllHits());
  }

  @Test
  void parsesCompoundQueryRequestsAndRendersMatchedSpans() {
    StubSearchRpc searchRpc = new StubSearchRpc();
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), searchRpc, new EmptyVocabularyRpc(), new EmptyTrainingRpc());
    byte[] request = """
        {"indexId":"%s","compoundQuery":{"join":{"operator":"JOIN_OPERATOR_AND",\
        "operands":[{"term":{"text":"habeas"}},\
        {"semantic":{"document":{"rawText":"habeas corpus"}}}]}},"topK":5}
        """.formatted(INDEX_ID).getBytes(StandardCharsets.UTF_8);

    WebHttpResponse response = api.handle("POST", "/api/v1/search", request);

    assertEquals(200, response.status());
    assertTrue(searchRpc.lastSearch.hasCompoundQuery());
    assertEquals("habeas", searchRpc.lastSearch.getCompoundQuery().getJoin()
        .getOperands(0).getTerm().getText());
    assertTrue(response.bodyUtf8().contains(
        "\"matchedSpans\":[{\"start\":4,\"end\":10,\"term\":\"habeas\"}]"));
  }

  @Test
  void drivesTheIndexLifecycleThroughProtobufJson() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new StubSearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());
    byte[] indexBody = ("{\"indexId\":\"" + INDEX_ID + "\"}").getBytes(StandardCharsets.UTF_8);

    WebHttpResponse persisted = api.handle("POST", "/api/v1/persist-index", indexBody);
    assertEquals(200, persisted.status());
    assertTrue(persisted.bodyUtf8().contains("\"persisted\":true"));

    WebHttpResponse sealed = api.handle("POST", "/api/v1/seal-index", indexBody);
    assertEquals(200, sealed.status());
    assertTrue(sealed.bodyUtf8().contains("\"immutable\":true"));

    WebHttpResponse reindexed = api.handle("POST", "/api/v1/reindex-index", """
        {"indexId":"%s","embedding":{"modelId":"demo"},"alias":"legal-current"}
        """.formatted(INDEX_ID).getBytes(StandardCharsets.UTF_8));
    assertEquals(200, reindexed.status());
    assertTrue(reindexed.bodyUtf8().contains("\"sourceIndexId\":\"" + INDEX_ID + "\""));
    assertTrue(reindexed.bodyUtf8().contains("\"reindexedChunks\":1"));

    WebHttpResponse aliasSet = api.handle("POST", "/api/v1/set-index-alias", """
        {"alias":"legal-current","indexId":"%s"}
        """.formatted(INDEX_ID).getBytes(StandardCharsets.UTF_8));
    assertEquals(200, aliasSet.status());
    assertTrue(aliasSet.bodyUtf8().contains("\"alias\":\"legal-current\""));

    WebHttpResponse aliases = api.handle("GET", "/api/v1/index-aliases", new byte[0]);
    assertEquals(200, aliases.status());
    assertTrue(aliases.bodyUtf8().contains("\"indexId\":\"" + INDEX_ID + "\""));

    WebHttpResponse aliasDeleted = api.handle("POST", "/api/v1/delete-index-alias",
        "{\"alias\":\"legal-current\"}".getBytes(StandardCharsets.UTF_8));
    assertEquals(200, aliasDeleted.status());
    assertTrue(aliasDeleted.bodyUtf8().contains("\"deleted\":true"));

    assertEquals(405, api.handle("GET", "/api/v1/persist-index", new byte[0]).status());
    assertEquals(405, api.handle("POST", "/api/v1/index-aliases", new byte[0]).status());
  }

  @Test
  void drivesTheCollectionLifecycleThroughProtobufJson() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new StubSearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    WebHttpResponse set = api.handle("POST", "/api/v1/set-collection", """
        {"collectionId":"legal","displayName":"Legal corpus","memberIndexIds":["%s"]}
        """.formatted(INDEX_ID).getBytes(StandardCharsets.UTF_8));
    assertEquals(200, set.status());
    assertTrue(set.bodyUtf8().contains("\"collectionId\":\"legal\""));

    WebHttpResponse got = api.handle("POST", "/api/v1/get-collection",
        "{\"collectionId\":\"legal\"}".getBytes(StandardCharsets.UTF_8));
    assertEquals(200, got.status());
    assertTrue(got.bodyUtf8().contains("\"termStatistics\""));
    assertTrue(got.bodyUtf8().contains("\"newTerms\":\"2\""));

    WebHttpResponse listed = api.handle("GET", "/api/v1/collections", new byte[0]);
    assertEquals(200, listed.status());
    assertTrue(listed.bodyUtf8().contains("\"collectionId\":\"legal\""));

    WebHttpResponse deleted = api.handle("POST", "/api/v1/delete-collection",
        "{\"collectionId\":\"legal\"}".getBytes(StandardCharsets.UTF_8));
    assertEquals(200, deleted.status());
    assertTrue(deleted.bodyUtf8().contains("\"deleted\":true"));

    assertEquals(405, api.handle("POST", "/api/v1/collections", new byte[0]).status());
    assertEquals(405, api.handle("GET", "/api/v1/set-collection", new byte[0]).status());
  }

  @Test
  void streamsCollectionWatchEventsAsNdjsonLinesUntilTheDeadline() throws IOException {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new StubSearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());
    List<String> lines = new ArrayList<>();

    WebHttpResponse buffered = api.watchCollection(
        "{\"collectionId\":\"legal\"}".getBytes(StandardCharsets.UTF_8), lines::add);

    // The stub ends the stream with DEADLINE_EXCEEDED after two events; the
    // bounded gateway watch lifetime closes quietly and the client reconnects.
    assertNull(buffered);
    assertEquals(2, lines.size());
    assertTrue(lines.get(0).contains("COLLECTION_EVENT_KIND_SNAPSHOT"));
    assertTrue(lines.get(1).contains("COLLECTION_EVENT_KIND_INDEX_PERSISTED"));
    assertTrue(lines.get(1).contains("\"indexId\":\"" + INDEX_ID + "\""));
  }

  @Test
  void rejectsMalformedSearchProtobufJson() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new StubSearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    WebHttpResponse response = api.handle("POST", "/api/v1/search",
        "{\"indexId\":{\"bad\":true}}".getBytes(StandardCharsets.UTF_8));

    assertEquals(400, response.status());
    assertTrue(response.bodyUtf8().contains("\"code\":\"INVALID_ARGUMENT\""));
    assertTrue(response.bodyUtf8().contains("Malformed protobuf JSON request"));
  }

  @Test
  void enforcesSearchEndpointMethods() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new StubSearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());

    assertEquals(405, api.handle("POST", "/api/v1/search-indexes", new byte[0]).status());
    assertEquals(405, api.handle("GET", "/api/v1/search", new byte[0]).status());
  }

  @Test
  void indexesAndDeletesAWorkspaceThroughProtobufJson() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new StubSearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());
    byte[] indexRequest = """
        {"displayName":"Workbench","documents":[{"docId":"passage-1",\
        "rawText":"The writ must issue."}],"embedding":{"modelId":"demo"}}
        """.getBytes(StandardCharsets.UTF_8);

    WebHttpResponse indexed = api.handle("POST", "/api/v1/index-documents", indexRequest);
    WebHttpResponse deleted = api.handle("POST", "/api/v1/delete-search-index",
        ("{\"indexId\":\"" + INDEX_ID + "\"}").getBytes(StandardCharsets.UTF_8));

    assertEquals(200, indexed.status());
    assertTrue(indexed.bodyUtf8().contains("\"indexId\":\"" + INDEX_ID + "\""));
    assertEquals(200, deleted.status());
    assertTrue(deleted.bodyUtf8().contains("\"deleted\":true"));
  }

  private static final class StubSearchRpc implements SearchRpc {

    private SearchIndexRequest lastSearch;

    @Override
    public ListSearchProvidersResponse listSearchProviders() {
      return ListSearchProvidersResponse.newBuilder()
          .addProviders(SearchProviderInstance.newBuilder()
              .setInstanceId("flat_float")
              .setProviderId("flat_float")
              .addCapabilities(SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_VECTOR)
              .addCapabilities(SearchProviderCapability.SEARCH_PROVIDER_CAPABILITY_LIVE)
              .setStandard(StandardSearchProvider.STANDARD_SEARCH_PROVIDER_FLAT_FLOAT))
          .build();
    }

    @Override
    public ListSearchIndexesResponse listSearchIndexes() {
      return ListSearchIndexesResponse.newBuilder()
          .addIndexes(SearchIndexDescriptor.newBuilder()
              .setIndexId(INDEX_ID)
              .setDisplayName("Legal demo")
              .setMaxTopK(25)
              .setMaxResponseBytes(1_048_576))
          .build();
    }

    @Override
    public SearchIndexResponse search(SearchIndexRequest request) {
      lastSearch = request;
      final SearchHit.Builder hit = SearchHit.newBuilder()
          .setDocumentId(DOCUMENT_ID)
          .setChunkId(DOCUMENT_ID)
          .setScore(0.75);
      if (request.hasCompoundQuery()) {
        hit.addMatchedSpans(org.apache.opennlp.grpc.v1.MatchedSpan.newBuilder()
            .setStart(4).setEnd(10).setTerm("habeas"));
      }
      return SearchIndexResponse.newBuilder()
          .setIndex(SearchIndexDescriptor.newBuilder().setIndexId(request.getIndexId()))
          .setTruncated(true)
          .addSourceDocuments(OpenNlpDocument.newBuilder()
              .setDocId(DOCUMENT_ID)
              .setRawText("The writ must issue."))
          .addHits(hit)
          .build();
    }

    @Override
    public IndexDocumentsResponse index(IndexDocumentsRequest request) {
      return IndexDocumentsResponse.newBuilder()
          .setIndex(SearchIndexDescriptor.newBuilder().setIndexId(INDEX_ID).setImmutable(false))
          .setIndexedDocuments(request.getDocumentsCount())
          .build();
    }

    @Override
    public PersistIndexResponse persist(PersistIndexRequest request) {
      return PersistIndexResponse.newBuilder()
          .setIndex(SearchIndexDescriptor.newBuilder()
              .setIndexId(request.getIndexId())
              .setPersisted(true))
          .build();
    }

    @Override
    public SealIndexResponse seal(SealIndexRequest request) {
      return SealIndexResponse.newBuilder()
          .setIndex(SearchIndexDescriptor.newBuilder()
              .setIndexId(request.getIndexId())
              .setPersisted(true)
              .setImmutable(true))
          .build();
    }

    @Override
    public ReindexIndexResponse reindex(ReindexIndexRequest request) {
      return ReindexIndexResponse.newBuilder()
          .setIndex(SearchIndexDescriptor.newBuilder().setIndexId("workspace-reindexed"))
          .setSourceIndexId(request.getIndexId())
          .setReindexedDocuments(1)
          .setReindexedChunks(1)
          .build();
    }

    @Override
    public SetIndexAliasResponse setAlias(SetIndexAliasRequest request) {
      return SetIndexAliasResponse.newBuilder()
          .setAlias(IndexAlias.newBuilder()
              .setAlias(request.getAlias())
              .setIndexId(request.getIndexId()))
          .build();
    }

    @Override
    public DeleteIndexAliasResponse deleteAlias(DeleteIndexAliasRequest request) {
      return DeleteIndexAliasResponse.newBuilder()
          .setAlias(request.getAlias())
          .setDeleted(true)
          .build();
    }

    @Override
    public ListIndexAliasesResponse listAliases() {
      return ListIndexAliasesResponse.newBuilder()
          .addAliases(IndexAlias.newBuilder()
              .setAlias("legal-current")
              .setIndexId(INDEX_ID))
          .build();
    }

    @Override
    public DeleteSearchIndexResponse delete(DeleteSearchIndexRequest request) {
      return DeleteSearchIndexResponse.newBuilder()
          .setIndexId(request.getIndexId())
          .setDeleted(true)
          .build();
    }

    @Override
    public SetCollectionResponse setCollection(SetCollectionRequest request) {
      return SetCollectionResponse.newBuilder()
          .setCollection(collection(request.getCollectionId()))
          .build();
    }

    @Override
    public GetCollectionResponse getCollection(GetCollectionRequest request) {
      return GetCollectionResponse.newBuilder()
          .setCollection(collection(request.getCollectionId()))
          .build();
    }

    @Override
    public ListCollectionsResponse listCollections() {
      return ListCollectionsResponse.newBuilder()
          .addCollections(collection("legal").toBuilder().clearTermStatistics())
          .build();
    }

    @Override
    public DeleteCollectionResponse deleteCollection(DeleteCollectionRequest request) {
      return DeleteCollectionResponse.newBuilder()
          .setCollectionId(request.getCollectionId())
          .setDeleted(true)
          .build();
    }

    @Override
    public Iterator<CollectionEvent> watchCollection(WatchCollectionRequest request) {
      final List<CollectionEvent> events = List.of(
          CollectionEvent.newBuilder()
              .setKind(CollectionEventKind.COLLECTION_EVENT_KIND_SNAPSHOT)
              .setCollection(collection(request.getCollectionId()))
              .build(),
          CollectionEvent.newBuilder()
              .setKind(CollectionEventKind.COLLECTION_EVENT_KIND_INDEX_PERSISTED)
              .setCollection(collection(request.getCollectionId()))
              .setIndexId(INDEX_ID)
              .build());
      final Iterator<CollectionEvent> delegate = events.iterator();
      return new Iterator<>() {
        @Override
        public boolean hasNext() {
          if (!delegate.hasNext()) {
            throw new StatusRuntimeException(Status.DEADLINE_EXCEEDED);
          }
          return true;
        }

        @Override
        public CollectionEvent next() {
          return delegate.next();
        }
      };
    }

    private static CollectionDescriptor collection(String collectionId) {
      return CollectionDescriptor.newBuilder()
          .setCollectionId(collectionId)
          .setDisplayName("Legal corpus")
          .addMemberIndexIds(INDEX_ID)
          .addTermStatistics(TermStatistic.newBuilder().setTerm("writ").setOccurrences(2))
          .setDrift(CollectionDriftStats.newBuilder()
              .setDistinctTerms(2)
              .setTermOccurrences(3)
              .setNewTerms(2)
              .setNewTermOccurrences(3))
          .build();
    }
  }

  private static final class StubAnalysisRpc implements AnalysisRpc {

    @Override
    public org.apache.opennlp.grpc.v1.ListOutputFormatsResponse listOutputFormats() {
      return org.apache.opennlp.grpc.v1.ListOutputFormatsResponse.getDefaultInstance();
    }

    @Override
    public org.apache.opennlp.grpc.v1.FormatDocumentResponse formatDocument(
        org.apache.opennlp.grpc.v1.FormatDocumentRequest request) {
      return org.apache.opennlp.grpc.v1.FormatDocumentResponse.getDefaultInstance();
    }
    @Override
    public java.util.Iterator<org.apache.opennlp.grpc.v1.AnalyzeStreamResponse> analyzeStream(
        java.util.List<org.apache.opennlp.grpc.v1.AnalyzeStreamRequest> frames) {
      return java.util.Collections.emptyIterator();
    }


    @Override
    public GetServiceInfoResponse getServiceInfo() {
      return GetServiceInfoResponse.getDefaultInstance();
    }

    @Override
    public ListModelBundlesResponse listModelBundles() {
      return ListModelBundlesResponse.getDefaultInstance();
    }

    @Override
    public AnalyzeDocumentResponse analyze(AnalyzeDocumentRequest request) {
      return AnalyzeDocumentResponse.getDefaultInstance();
    }
  }
}
