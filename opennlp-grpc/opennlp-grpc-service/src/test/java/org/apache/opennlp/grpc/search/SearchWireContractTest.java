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

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import org.apache.opennlp.grpc.v1.ListSearchIndexesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpSearchProto;
import org.apache.opennlp.grpc.v1.SearchHit;
import org.apache.opennlp.grpc.v1.SearchCorpusDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexBuildDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexDescriptor;
import org.apache.opennlp.grpc.v1.SearchIndexRequest;
import org.apache.opennlp.grpc.v1.SearchIndexResponse;
import org.apache.opennlp.grpc.v1.SearchMetric;
import org.apache.opennlp.grpc.v1.SearchProviderSelector;
import org.apache.opennlp.grpc.v1.StandardSearchProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchWireContractTest {

  @Test
  void exposesSeparateBoundedSearchService() {
    final var service = OpenNlpSearchProto.getDescriptor()
        .findServiceByName("OpenNlpSearchService");
    assertNotNull(service);
    assertEquals(2, service.getMethods().size());
    final MethodDescriptor list = service.findMethodByName("ListSearchIndexes");
    final MethodDescriptor search = service.findMethodByName("SearchIndex");

    assertNotNull(list);
    assertNotNull(search);
    assertEquals("ListSearchIndexesRequest", list.getInputType().getName());
    assertEquals("ListSearchIndexesResponse", list.getOutputType().getName());
    assertEquals("SearchIndexRequest", search.getInputType().getName());
    assertEquals("SearchIndexResponse", search.getOutputType().getName());
  }

  @Test
  void indexDescriptorCarriesTypedProviderMetricEmbeddingAndCorpusIdentity() {
    final Descriptor descriptor = SearchIndexDescriptor.getDescriptor();

    assertField(descriptor, "index_id", FieldDescriptor.JavaType.STRING, 1);
    assertField(descriptor, "display_name", FieldDescriptor.JavaType.STRING, 2);
    assertMessageField(descriptor, "provider", SearchProviderSelector.getDescriptor(), 3);
    assertEquals("EmbeddingRoute", descriptor.findFieldByName("embedding_route")
        .getMessageType().getName());
    assertField(descriptor, "dimension", FieldDescriptor.JavaType.INT, 5);
    assertEquals(SearchMetric.getDescriptor(), descriptor.findFieldByName("metric").getEnumType());
    assertField(descriptor, "size", FieldDescriptor.JavaType.INT, 7);
    assertEquals(FieldDescriptor.Type.UINT32, descriptor.findFieldByName("size").getType());
    assertField(descriptor, "immutable", FieldDescriptor.JavaType.BOOLEAN, 8);
    assertEquals("SearchCorpusDescriptor",
        descriptor.findFieldByName("corpus").getMessageType().getName());
    assertField(descriptor, "max_top_k", FieldDescriptor.JavaType.INT, 10);
    assertField(descriptor, "max_query_bytes", FieldDescriptor.JavaType.INT, 11);
    assertMessageField(descriptor, "build", SearchIndexBuildDescriptor.getDescriptor(), 12);
    assertField(descriptor, "max_response_bytes", FieldDescriptor.JavaType.INT, 13);
    assertEquals(FieldDescriptor.Type.UINT32,
        descriptor.findFieldByName("max_response_bytes").getType());
    assertNotNull(StandardSearchProvider.valueOf("STANDARD_SEARCH_PROVIDER_FLAT_FLOAT"));
    assertNotNull(StandardSearchProvider.valueOf("STANDARD_SEARCH_PROVIDER_TURBO_QUANT"));

    final Descriptor corpus = SearchCorpusDescriptor.getDescriptor();
    assertField(corpus, "title", FieldDescriptor.JavaType.STRING, 1);
    assertField(corpus, "provenance_summary", FieldDescriptor.JavaType.STRING, 2);
    assertField(corpus, "source_uri", FieldDescriptor.JavaType.STRING, 3);
    assertField(corpus, "license_name", FieldDescriptor.JavaType.STRING, 4);
    assertField(corpus, "license_uri", FieldDescriptor.JavaType.STRING, 5);
    assertField(corpus, "artifact_hash", FieldDescriptor.JavaType.STRING, 6);
    assertTrue(corpus.findFieldByName("source_uri").toProto().getProto3Optional());
    assertTrue(corpus.findFieldByName("license_name").toProto().getProto3Optional());
    assertTrue(corpus.findFieldByName("license_uri").toProto().getProto3Optional());
    assertTrue(corpus.findFieldByName("artifact_hash").toProto().getProto3Optional());

    final Descriptor build = SearchIndexBuildDescriptor.getDescriptor();
    assertField(build, "bundle_format_version", FieldDescriptor.JavaType.INT, 1);
    assertEquals(FieldDescriptor.Type.UINT32,
        build.findFieldByName("bundle_format_version").getType());
    assertField(build, "bundle_artifact_hash", FieldDescriptor.JavaType.STRING, 2);
    assertField(build, "builder_id", FieldDescriptor.JavaType.STRING, 3);
    assertField(build, "builder_version", FieldDescriptor.JavaType.STRING, 4);
    assertField(build, "preparation_config_hash", FieldDescriptor.JavaType.STRING, 5);
  }

  @Test
  void requestRetainsDocumentShapeAndBoundedTopK() {
    final Descriptor descriptor = SearchIndexRequest.getDescriptor();

    assertField(descriptor, "index_id", FieldDescriptor.JavaType.STRING, 1);
    assertEquals("OpenNlpDocument", descriptor.findFieldByName("query").getMessageType().getName());
    assertField(descriptor, "top_k", FieldDescriptor.JavaType.INT, 3);
  }

  @Test
  void responseAndHitsRetainSourceAndProvenance() {
    final Descriptor response = SearchIndexResponse.getDescriptor();
    assertMessageField(response, "index", SearchIndexDescriptor.getDescriptor(), 1);
    assertMessageField(response, "hits", SearchHit.getDescriptor(), 2);
    assertFalse(response.findFieldByName("index").isRepeated());
    assertTrue(response.findFieldByName("hits").isRepeated());
    assertEquals("EmbeddingRoute",
        response.findFieldByName("query_embedding_route").getMessageType().getName());
    assertField(response, "truncated", FieldDescriptor.JavaType.BOOLEAN, 4);

    final Descriptor hit = SearchHit.getDescriptor();
    assertField(hit, "document_id", FieldDescriptor.JavaType.STRING, 1);
    assertField(hit, "chunk_id", FieldDescriptor.JavaType.STRING, 2);
    assertField(hit, "score", FieldDescriptor.JavaType.DOUBLE, 3);
    assertEquals("OpenNlpDocument", hit.findFieldByName("source_document").getMessageType().getName());
    assertEquals("AnnotationSpan", hit.findFieldByName("source_span").getMessageType().getName());
    assertField(hit, "emitted_text", FieldDescriptor.JavaType.STRING, 6);
    assertEquals(null, hit.findFieldByNumber(7));
    assertEquals(null, hit.findFieldByNumber(8));
    assertEquals(null, hit.findFieldByNumber(9));

    assertMessageField(ListSearchIndexesResponse.getDescriptor(), "indexes",
        SearchIndexDescriptor.getDescriptor(), 1);
    assertTrue(ListSearchIndexesResponse.getDescriptor()
        .findFieldByName("indexes").isRepeated());
  }

  @Test
  void enumNumbersRemainStable() {
    assertEquals(0, SearchMetric.SEARCH_METRIC_UNSPECIFIED.getNumber());
    assertEquals(1, SearchMetric.SEARCH_METRIC_COSINE.getNumber());
    assertEquals(0,
        StandardSearchProvider.STANDARD_SEARCH_PROVIDER_UNSPECIFIED.getNumber());
    assertEquals(1,
        StandardSearchProvider.STANDARD_SEARCH_PROVIDER_FLAT_FLOAT.getNumber());
    assertEquals(2,
        StandardSearchProvider.STANDARD_SEARCH_PROVIDER_TURBO_QUANT.getNumber());
  }

  private static void assertField(
      Descriptor descriptor, String name, FieldDescriptor.JavaType type, int number) {
    final FieldDescriptor field = descriptor.findFieldByName(name);
    assertNotNull(field, () -> descriptor.getName() + " lacks " + name);
    assertEquals(type, field.getJavaType());
    assertEquals(number, field.getNumber());
  }

  private static void assertMessageField(
      Descriptor descriptor, String name, Descriptor type, int number) {
    final FieldDescriptor field = descriptor.findFieldByName(name);
    assertNotNull(field, () -> descriptor.getName() + " lacks " + name);
    assertEquals(FieldDescriptor.JavaType.MESSAGE, field.getJavaType());
    assertEquals(type, field.getMessageType());
    assertEquals(number, field.getNumber());
  }

}
