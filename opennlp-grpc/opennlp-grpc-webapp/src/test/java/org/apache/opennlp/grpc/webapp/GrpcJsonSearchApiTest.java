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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexRequest;
import org.apache.opennlp.grpc.v1.DeleteSearchIndexResponse;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.IndexDocumentsResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
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
  void parsesDocumentShapedSearchRequestAndRendersSourceHit() {
    GrpcJsonApi api = new GrpcJsonApi(new StubAnalysisRpc(), new StubSearchRpc(), new EmptyVocabularyRpc(), new EmptyTrainingRpc());
    byte[] request = """
        {"indexId":"%s","query":{"docId":"query-1","rawText":"habeas corpus"},"topK":7}
        """.formatted(INDEX_ID).getBytes(StandardCharsets.UTF_8);

    WebHttpResponse response = api.handle("POST", "/api/v1/search", request);

    assertEquals(200, response.status());
    assertTrue(response.bodyUtf8().contains("\"documentId\":\"" + DOCUMENT_ID + "\""));
    assertTrue(response.bodyUtf8().contains("\"score\":0.75"));
    assertTrue(response.bodyUtf8().contains("\"rawText\":\"The writ must issue.\""));
    assertTrue(response.bodyUtf8().contains("\"truncated\":true"));
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
          .setScore(0.75)
          .setSourceDocument(OpenNlpDocument.newBuilder()
              .setDocId(DOCUMENT_ID)
              .setRawText("The writ must issue."));
      if (request.hasCompoundQuery()) {
        hit.addMatchedSpans(org.apache.opennlp.grpc.v1.MatchedSpan.newBuilder()
            .setStart(4).setEnd(10).setTerm("habeas"));
      }
      return SearchIndexResponse.newBuilder()
          .setIndex(SearchIndexDescriptor.newBuilder().setIndexId(request.getIndexId()))
          .setTruncated(true)
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
    public DeleteSearchIndexResponse delete(DeleteSearchIndexRequest request) {
      return DeleteSearchIndexResponse.newBuilder()
          .setIndexId(request.getIndexId())
          .setDeleted(true)
          .build();
    }
  }

  private static final class StubAnalysisRpc implements AnalysisRpc {

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
