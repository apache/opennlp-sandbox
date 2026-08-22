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
import com.google.protobuf.Descriptors.ServiceDescriptor;
import org.apache.opennlp.grpc.v1.OpenNlpSearchProto;
import org.apache.opennlp.grpc.v1.OpenNlpSearchStorageProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the index lifecycle wire shapes: explicit persist and seal, blue/green
 * reindex replaying retained source, alias upserts, and the persisted chunk
 * storage record that keeps raw vectors beside the quantized form.
 */
class LifecycleWireContractTest {

  @Test
  void persistAndSealAreExplicitUnaryCalls() {
    final ServiceDescriptor service = searchService();

    final MethodDescriptor persist = service.findMethodByName("PersistIndex");
    assertNotNull(persist);
    assertEquals("PersistIndexRequest", persist.getInputType().getName());
    assertEquals("PersistIndexResponse", persist.getOutputType().getName());
    assertEquals(FieldDescriptor.JavaType.STRING,
        persist.getInputType().findFieldByName("index_id").getJavaType());
    assertEquals("SearchIndexDescriptor",
        persist.getOutputType().findFieldByName("index").getMessageType().getName());

    final MethodDescriptor seal = service.findMethodByName("SealIndex");
    assertNotNull(seal);
    assertEquals("SealIndexRequest", seal.getInputType().getName());
    assertEquals("SealIndexResponse", seal.getOutputType().getName());
    assertEquals(FieldDescriptor.JavaType.STRING,
        seal.getInputType().findFieldByName("index_id").getJavaType());
    assertEquals("SearchIndexDescriptor",
        seal.getOutputType().findFieldByName("index").getMessageType().getName());
  }

  @Test
  void descriptorsReportPersistedState() {
    final Descriptor descriptor = OpenNlpSearchProto.getDescriptor()
        .findMessageTypeByName("SearchIndexDescriptor");
    final FieldDescriptor persisted = descriptor.findFieldByName("persisted");
    assertNotNull(persisted);
    assertEquals(FieldDescriptor.JavaType.BOOLEAN, persisted.getJavaType());
  }

  @Test
  void reindexRunsBlueGreenWithAnOptionalAliasSwap() {
    final MethodDescriptor reindex = searchService().findMethodByName("ReindexIndex");
    assertNotNull(reindex);
    assertEquals("ReindexIndexRequest", reindex.getInputType().getName());
    assertEquals("ReindexIndexResponse", reindex.getOutputType().getName());

    final Descriptor request = reindex.getInputType();
    assertEquals(FieldDescriptor.JavaType.STRING,
        request.findFieldByName("index_id").getJavaType());
    assertEquals("EmbeddingSelector",
        request.findFieldByName("embedding").getMessageType().getName());
    assertEquals("SearchProviderSelector",
        request.findFieldByName("provider").getMessageType().getName());
    final FieldDescriptor alias = request.findFieldByName("alias");
    assertNotNull(alias);
    assertTrue(alias.hasPresence());

    final Descriptor response = reindex.getOutputType();
    assertEquals("SearchIndexDescriptor",
        response.findFieldByName("index").getMessageType().getName());
    assertEquals(FieldDescriptor.JavaType.STRING,
        response.findFieldByName("source_index_id").getJavaType());
    assertEquals(FieldDescriptor.JavaType.INT,
        response.findFieldByName("reindexed_documents").getJavaType());
    assertEquals(FieldDescriptor.JavaType.INT,
        response.findFieldByName("reindexed_chunks").getJavaType());
  }

  @Test
  void aliasesResolveLogicalNamesToIndexIds() {
    final ServiceDescriptor service = searchService();

    final MethodDescriptor set = service.findMethodByName("SetIndexAlias");
    assertNotNull(set);
    assertEquals(FieldDescriptor.JavaType.STRING,
        set.getInputType().findFieldByName("alias").getJavaType());
    assertEquals(FieldDescriptor.JavaType.STRING,
        set.getInputType().findFieldByName("index_id").getJavaType());
    assertEquals("IndexAlias",
        set.getOutputType().findFieldByName("alias").getMessageType().getName());

    final MethodDescriptor delete = service.findMethodByName("DeleteIndexAlias");
    assertNotNull(delete);
    assertEquals(FieldDescriptor.JavaType.STRING,
        delete.getInputType().findFieldByName("alias").getJavaType());
    assertEquals(FieldDescriptor.JavaType.BOOLEAN,
        delete.getOutputType().findFieldByName("deleted").getJavaType());

    final MethodDescriptor list = service.findMethodByName("ListIndexAliases");
    assertNotNull(list);
    final FieldDescriptor aliases = list.getOutputType().findFieldByName("aliases");
    assertTrue(aliases.isRepeated());
    assertEquals("IndexAlias", aliases.getMessageType().getName());

    final Descriptor alias = OpenNlpSearchProto.getDescriptor()
        .findMessageTypeByName("IndexAlias");
    assertEquals(FieldDescriptor.JavaType.STRING,
        alias.findFieldByName("alias").getJavaType());
    assertEquals(FieldDescriptor.JavaType.STRING,
        alias.findFieldByName("index_id").getJavaType());
  }

  @Test
  void persistedChunksCanReferenceProviderOwnedVectorSegments() {
    final Descriptor chunk = OpenNlpSearchStorageProto.getDescriptor()
        .findMessageTypeByName("PersistedSearchChunk");
    assertNotNull(chunk);
    assertEquals(FieldDescriptor.JavaType.STRING,
        chunk.findFieldByName("document_id").getJavaType());
    assertEquals(FieldDescriptor.JavaType.STRING,
        chunk.findFieldByName("chunk_id").getJavaType());
    assertEquals("OpenNlpDocument",
        chunk.findFieldByName("source_document").getMessageType().getName());
    assertEquals("AnnotationSpan",
        chunk.findFieldByName("source_span").getMessageType().getName());
    assertEquals(FieldDescriptor.JavaType.STRING,
        chunk.findFieldByName("indexed_text").getJavaType());
    assertEquals("EmbeddingRoute",
        chunk.findFieldByName("route").getMessageType().getName());
    final FieldDescriptor vector = chunk.findFieldByName("vector");
    assertTrue(vector.isRepeated());
    assertEquals(FieldDescriptor.JavaType.FLOAT, vector.getJavaType());
    assertEquals(FieldDescriptor.JavaType.STRING,
        chunk.findFieldByName("vector_id").getJavaType());
    assertEquals(FieldDescriptor.JavaType.INT,
        chunk.findFieldByName("vector_segment").getJavaType());
    assertEquals(FieldDescriptor.JavaType.BYTE_STRING,
        chunk.findFieldByName("vector_sha256").getJavaType());
  }

  private static ServiceDescriptor searchService() {
    final ServiceDescriptor service = OpenNlpSearchProto.getDescriptor()
        .findServiceByName("OpenNlpSearchService");
    assertNotNull(service);
    return service;
  }
}
