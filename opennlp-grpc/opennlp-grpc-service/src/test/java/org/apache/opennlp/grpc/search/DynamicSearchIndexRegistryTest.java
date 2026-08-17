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

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.Chunk;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.apache.opennlp.grpc.v1.EmbeddingSelector;
import org.apache.opennlp.grpc.v1.EmbeddingBackendSelector;
import org.apache.opennlp.grpc.v1.IndexDocumentsRequest;
import org.apache.opennlp.grpc.v1.OffsetEncoding;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.v1.StandardEmbeddingBackend;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicSearchIndexRegistryTest {

  @Test
  void createsExtendsSearchesAndDeletesAnInMemoryIndex() {
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry();
    final var created = registry.index(request(null, "doc-1", "alpha", 1, 0));

    assertFalse(created.getIndex().getImmutable());
    assertEquals(1, created.getIndexedDocuments());
    assertEquals(1, created.getIndexedChunks());
    assertEquals(1, created.getIndex().getSize());

    final String indexId = created.getIndex().getIndexId();
    final var extended = registry.index(request(indexId, "doc-2", "beta", 0, 1));
    assertEquals(2, extended.getIndex().getSize());
    final List<SearchResult> hits = registry.require(indexId).search(new float[] {1, 0}, 2);
    assertEquals(List.of("doc-1", "doc-2"), hits.stream()
        .map(result -> result.record().documentId()).toList());

    assertTrue(registry.delete(indexId));
    assertFalse(registry.delete(indexId));
    assertThrows(RuntimeException.class, () -> registry.require(indexId));
  }

  @Test
  void storesOnlyTheSourceIdentityTextMetadataAndOffsetsInSearchHits() {
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry();
    final var created = registry.index(request(null, "doc-1", "alpha", 1, 0));

    final OpenNlpDocument source = registry.require(created.getIndex().getIndexId())
        .search(new float[] {1, 0}, 1).getFirst().record().sourceDocument();

    assertEquals("doc-1", source.getDocId());
    assertEquals("alpha", source.getRawText());
    assertEquals(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT, source.getOffsetEncoding());
    assertEquals(0, source.getChunkEmbeddingGroupsCount());
    assertFalse(source.hasLayers());
  }

  @Test
  void rejectsDocumentsWithoutASelectedChunkEmbedding() {
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry();
    final IndexDocumentsRequest request = request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setEmbedding(EmbeddingSelector.newBuilder().setModelId("other"))
        .build();

    assertThrows(RuntimeException.class, () -> registry.index(request));
    assertTrue(registry.descriptors().isEmpty());
  }

  @Test
  void rejectsAnIndexThatExceedsTheVectorMemoryBudgetWithoutPublishingIt() {
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry(2, 1, 1024);

    final AnalysisException failure = assertThrows(AnalysisException.class,
        () -> registry.index(request(null, "doc-1", "alpha", 1, 0)));

    assertEquals(AnalysisException.FailureType.RESOURCE_EXHAUSTED, failure.getFailureType());
    assertTrue(failure.getMessage().contains("vector"));
    assertTrue(registry.descriptors().isEmpty());
  }

  @Test
  void countsTheCompleteSerializedDocumentAgainstTheSourceBudget() {
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry(2, 1024, 128);
    final IndexDocumentsRequest oversized = request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setDocuments(0, request(null, "doc-1", "alpha", 1, 0).getDocuments(0).toBuilder()
            .setMetadata(Struct.newBuilder().putFields("payload", Value.newBuilder()
                .setStringValue("x".repeat(256)).build())))
        .build();

    final AnalysisException failure = assertThrows(AnalysisException.class,
        () -> registry.index(oversized));

    assertEquals(AnalysisException.FailureType.RESOURCE_EXHAUSTED, failure.getFailureType());
    assertTrue(failure.getMessage().contains("document"));
    assertTrue(registry.descriptors().isEmpty());
  }

  @Test
  void preservesThePreviousSnapshotWhenAnExtensionExceedsTheGlobalBudget() {
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry(2, 2, 1024);
    final var created = registry.index(request(null, "doc-1", "alpha", 1, 0));

    final AnalysisException failure = assertThrows(AnalysisException.class,
        () -> registry.index(request(created.getIndex().getIndexId(), "doc-2", "beta", 0, 1)));

    assertEquals(AnalysisException.FailureType.RESOURCE_EXHAUSTED, failure.getFailureType());
    assertEquals(1, registry.require(created.getIndex().getIndexId()).descriptor().getSize());
  }

  @Test
  void disabledRegistryRejectsMutationWithoutPublishingDescriptors() {
    final DynamicSearchIndexRegistry registry = DynamicSearchIndexRegistry.disabled();

    final AnalysisException failure = assertThrows(AnalysisException.class,
        () -> registry.index(request(null, "doc-1", "alpha", 1, 0)));

    assertEquals(AnalysisException.FailureType.UNIMPLEMENTED, failure.getFailureType());
    assertTrue(registry.descriptors().isEmpty());
  }

  @Test
  void mapsMalformedDocumentShapeToAClientPreconditionFailure() {
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry();
    final IndexDocumentsRequest malformed = request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setDocuments(0, request(null, "doc-1", "alpha", 1, 0).getDocuments(0).toBuilder()
            .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UNSPECIFIED))
        .build();

    final AnalysisException failure = assertThrows(AnalysisException.class,
        () -> registry.index(malformed));

    assertEquals(AnalysisException.FailureType.FAILED_PRECONDITION, failure.getFailureType());
    assertTrue(failure.getMessage().contains("offset_encoding"));
  }

  @Test
  void acceptsTheTypedStaticEmbeddingBackendSelector() {
    final DynamicSearchIndexRegistry registry = new DynamicSearchIndexRegistry();
    final IndexDocumentsRequest typed = request(null, "doc-1", "alpha", 1, 0).toBuilder()
        .setEmbedding(EmbeddingSelector.newBuilder()
            .setModelId("demo")
            .setBackend(EmbeddingBackendSelector.newBuilder()
                .setStandard(StandardEmbeddingBackend.STANDARD_EMBEDDING_BACKEND_STATIC)))
        .build();

    assertEquals(1, registry.index(typed).getIndex().getSize());
  }

  static IndexDocumentsRequest request(
      String indexId, String documentId, String text, float x, float y) {
    final AnnotationSpan span = AnnotationSpan.newBuilder()
        .setStart(0)
        .setEnd(text.length())
        .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT)
        .build();
    final EmbeddingRoute route = EmbeddingRoute.newBuilder()
        .setModelId("demo")
        .setBackendId("static")
        .setVectorSpaceId("demo-space")
        .build();
    final EmbeddingResult embedding = EmbeddingResult.newBuilder()
        .setModelId("demo")
        .addVector(x)
        .addVector(y)
        .setSourceSpan(span)
        .setGranularity(EmbeddingGranularity.EMBEDDING_GRANULARITY_CHUNK_LEVEL)
        .setRoute(route)
        .build();
    final Chunk chunk = Chunk.newBuilder()
        .setAnnotationSpan(span)
        .setTextContent(text)
        .addEmbeddings(embedding)
        .build();
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setDocId(documentId)
        .setRawText(text)
        .setOffsetEncoding(OffsetEncoding.OFFSET_ENCODING_UTF16_CODE_UNIT)
        .addChunkEmbeddingGroups(ChunkEmbeddingGroup.newBuilder()
            .setGroupId("sentence")
            .addEmbeddingModelIds("demo")
            .addChunks(chunk))
        .build();
    final IndexDocumentsRequest.Builder request = IndexDocumentsRequest.newBuilder()
        .setDisplayName("Workspace")
        .setEmbedding(EmbeddingSelector.newBuilder().setModelId("demo"))
        .addDocuments(document);
    if (indexId != null) {
      request.setIndexId(indexId);
    }
    return request.build();
  }
}
