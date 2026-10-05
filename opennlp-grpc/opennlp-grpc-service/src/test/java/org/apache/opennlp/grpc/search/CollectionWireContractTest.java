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
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import org.apache.opennlp.grpc.v1.OpenNlpSearchProto;
import org.apache.opennlp.grpc.v1.OpenNlpSearchStorageProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the collection wire shapes: the descriptor drift tracking scopes to, its
 * recomputed term statistics and drift statistics, the collection CRUD calls, the
 * server-streaming watch whose events are self-contained snapshots, and the
 * persisted collection storage record.
 */
class CollectionWireContractTest {

  @Test
  void collectionCallsCompleteTheBoundedService() {
    final ServiceDescriptor service = searchService();
    assertEquals(16, service.getMethods().size());

    final MethodDescriptor set = service.findMethodByName("SetCollection");
    assertNotNull(set);
    assertEquals("SetCollectionRequest", set.getInputType().getName());
    assertEquals("CollectionDescriptor",
        set.getOutputType().findFieldByName("collection").getMessageType().getName());

    final MethodDescriptor get = service.findMethodByName("GetCollection");
    assertNotNull(get);
    assertEquals(FieldDescriptor.JavaType.STRING,
        get.getInputType().findFieldByName("collection_id").getJavaType());
    assertEquals("CollectionDescriptor",
        get.getOutputType().findFieldByName("collection").getMessageType().getName());

    final MethodDescriptor list = service.findMethodByName("ListCollections");
    assertNotNull(list);
    final FieldDescriptor collections =
        list.getOutputType().findFieldByName("collections");
    assertTrue(collections.isRepeated());
    assertEquals("CollectionDescriptor", collections.getMessageType().getName());

    final MethodDescriptor delete = service.findMethodByName("DeleteCollection");
    assertNotNull(delete);
    assertEquals(FieldDescriptor.JavaType.STRING,
        delete.getInputType().findFieldByName("collection_id").getJavaType());
    assertEquals(FieldDescriptor.JavaType.BOOLEAN,
        delete.getOutputType().findFieldByName("deleted").getJavaType());
  }

  @Test
  void setRequestsCarryTheCompleteConfiguredState() {
    final Descriptor request = OpenNlpSearchProto.getDescriptor()
        .findMessageTypeByName("SetCollectionRequest");
    assertNotNull(request);
    assertEquals(FieldDescriptor.JavaType.STRING,
        request.findFieldByName("collection_id").getJavaType());
    assertEquals(FieldDescriptor.JavaType.STRING,
        request.findFieldByName("display_name").getJavaType());
    final FieldDescriptor members = request.findFieldByName("member_index_ids");
    assertTrue(members.isRepeated());
    assertEquals(FieldDescriptor.JavaType.STRING, members.getJavaType());
    assertTrue(request.findFieldByName("dictionary_artifact_id").hasPresence());
    assertTrue(request.findFieldByName("vocabulary_artifact_id").hasPresence());
    assertTrue(request.findFieldByName("model_artifact_id").hasPresence());
    assertEquals(FieldDescriptor.JavaType.INT,
        request.findFieldByName("drift_new_term_threshold").getJavaType());
  }

  @Test
  void descriptorsCarryTermStatisticsDriftAndArtifactLineage() {
    final Descriptor descriptor = OpenNlpSearchProto.getDescriptor()
        .findMessageTypeByName("CollectionDescriptor");
    assertNotNull(descriptor);
    assertEquals(FieldDescriptor.JavaType.STRING,
        descriptor.findFieldByName("collection_id").getJavaType());
    assertEquals(FieldDescriptor.JavaType.STRING,
        descriptor.findFieldByName("display_name").getJavaType());
    assertTrue(descriptor.findFieldByName("member_index_ids").isRepeated());
    assertTrue(descriptor.findFieldByName("dictionary_artifact_id").hasPresence());
    assertTrue(descriptor.findFieldByName("vocabulary_artifact_id").hasPresence());
    assertTrue(descriptor.findFieldByName("model_artifact_id").hasPresence());
    assertEquals(FieldDescriptor.JavaType.INT,
        descriptor.findFieldByName("drift_new_term_threshold").getJavaType());
    assertEquals("AnalysisChainDescriptor",
        descriptor.findFieldByName("analysis_chain").getMessageType().getName());
    final FieldDescriptor termStatistics = descriptor.findFieldByName("term_statistics");
    assertTrue(termStatistics.isRepeated());
    assertEquals("TermStatistic", termStatistics.getMessageType().getName());
    assertEquals(FieldDescriptor.JavaType.INT,
        descriptor.findFieldByName("omitted_term_count").getJavaType());
    assertEquals("CollectionDriftStats",
        descriptor.findFieldByName("drift").getMessageType().getName());
    assertEquals(FieldDescriptor.JavaType.STRING,
        descriptor.findFieldByName("integrity_hash").getJavaType());

    final Descriptor entry = OpenNlpSearchProto.getDescriptor()
        .findMessageTypeByName("TermStatistic");
    assertEquals(FieldDescriptor.JavaType.STRING,
        entry.findFieldByName("term").getJavaType());
    assertEquals(FieldDescriptor.JavaType.LONG,
        entry.findFieldByName("occurrences").getJavaType());
    assertEquals(FieldDescriptor.JavaType.BOOLEAN,
        entry.findFieldByName("in_vocabulary").getJavaType());

    final Descriptor drift = OpenNlpSearchProto.getDescriptor()
        .findMessageTypeByName("CollectionDriftStats");
    assertEquals(FieldDescriptor.JavaType.LONG,
        drift.findFieldByName("distinct_terms").getJavaType());
    assertEquals(FieldDescriptor.JavaType.LONG,
        drift.findFieldByName("term_occurrences").getJavaType());
    assertEquals(FieldDescriptor.JavaType.LONG,
        drift.findFieldByName("new_terms").getJavaType());
    assertEquals(FieldDescriptor.JavaType.LONG,
        drift.findFieldByName("new_term_occurrences").getJavaType());
    assertEquals(FieldDescriptor.JavaType.DOUBLE,
        drift.findFieldByName("vocabulary_coverage").getJavaType());
  }

  @Test
  void watchStreamsSelfContainedSnapshotEvents() {
    final MethodDescriptor watch = searchService().findMethodByName("WatchCollection");
    assertNotNull(watch);
    assertFalse(watch.isClientStreaming());
    assertTrue(watch.isServerStreaming());
    assertEquals(FieldDescriptor.JavaType.STRING,
        watch.getInputType().findFieldByName("collection_id").getJavaType());

    final Descriptor event = watch.getOutputType();
    assertEquals("CollectionEvent", event.getName());
    assertEquals("CollectionDescriptor",
        event.findFieldByName("collection").getMessageType().getName());
    assertTrue(event.findFieldByName("index_id").hasPresence());
    assertTrue(event.findFieldByName("model_artifact_id").hasPresence());

    final EnumDescriptor kind = event.findFieldByName("kind").getEnumType();
    assertEquals("CollectionEventKind", kind.getName());
    assertNotNull(kind.findValueByName("COLLECTION_EVENT_KIND_UNSPECIFIED"));
    assertNotNull(kind.findValueByName("COLLECTION_EVENT_KIND_SNAPSHOT"));
    assertNotNull(kind.findValueByName("COLLECTION_EVENT_KIND_DRIFT_THRESHOLD_CROSSED"));
    assertNotNull(kind.findValueByName("COLLECTION_EVENT_KIND_INDEX_PERSISTED"));
    assertNotNull(kind.findValueByName("COLLECTION_EVENT_KIND_MODEL_PUBLISHED"));
  }

  @Test
  void persistedCollectionsWrapTheDescriptorWithAFormatVersion() {
    final Descriptor persisted = OpenNlpSearchStorageProto.getDescriptor()
        .findMessageTypeByName("PersistedCollection");
    assertNotNull(persisted);
    assertEquals(FieldDescriptor.JavaType.INT,
        persisted.findFieldByName("format_version").getJavaType());
    assertEquals("CollectionDescriptor",
        persisted.findFieldByName("collection").getMessageType().getName());
  }

  private static ServiceDescriptor searchService() {
    final ServiceDescriptor service = OpenNlpSearchProto.getDescriptor()
        .findServiceByName("OpenNlpSearchService");
    assertNotNull(service);
    return service;
  }
}
