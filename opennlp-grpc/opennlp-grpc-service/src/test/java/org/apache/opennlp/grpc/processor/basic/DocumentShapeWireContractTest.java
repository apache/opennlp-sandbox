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
package org.apache.opennlp.grpc.processor.basic;

import java.util.Set;
import java.util.stream.Collectors;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import org.apache.opennlp.grpc.v1.AnnotationLayer;
import org.apache.opennlp.grpc.v1.OpenNlpDocumentProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentShapeWireContractTest {

  @Test
  void firstClassResultsHaveDedicatedTypedLayerArms() {
    final Descriptor layer = AnnotationLayer.getDescriptor();
    final Set<String> arms = layer.getOneofs().stream()
        .filter(oneof -> "values".equals(oneof.getName()))
        .flatMap(oneof -> oneof.getFields().stream())
        .map(field -> field.getName())
        .collect(Collectors.toSet());

    assertEquals(Set.of(
        "string_values", "category_values", "embedding_values", "tree_values",
        "subword_values", "geo_values", "word_type_values", "entity_values",
        "syntactic_chunk_values", "stem_values", "lexical_expansion_values",
        "normalization_values", "analytics_values", "chunk_group_values"), arms);
    assertEquals("string", layer.findFieldByName("id").getType().name().toLowerCase());
  }

  @Test
  void closedWordAndExpansionConceptsAreEnums() {
    final EnumDescriptor wordType = OpenNlpDocumentProto.getDescriptor()
        .findEnumTypeByName("DocumentWordType");
    assertEquals(Set.of(
        "DOCUMENT_WORD_TYPE_UNSPECIFIED", "DOCUMENT_WORD_TYPE_ALPHANUMERIC",
        "DOCUMENT_WORD_TYPE_NUMERIC", "DOCUMENT_WORD_TYPE_IDEOGRAPHIC",
        "DOCUMENT_WORD_TYPE_HIRAGANA", "DOCUMENT_WORD_TYPE_KATAKANA",
        "DOCUMENT_WORD_TYPE_HANGUL", "DOCUMENT_WORD_TYPE_SOUTHEAST_ASIAN",
        "DOCUMENT_WORD_TYPE_EMOJI"),
        wordType.getValues().stream().map(value -> value.getName()).collect(Collectors.toSet()));

    final EnumDescriptor expansionKind = OpenNlpDocumentProto.getDescriptor()
        .findEnumTypeByName("LexicalExpansionKind");
    assertEquals(Set.of(
        "LEXICAL_EXPANSION_KIND_UNSPECIFIED", "LEXICAL_EXPANSION_KIND_SYNONYM",
        "LEXICAL_EXPANSION_KIND_HYPERNYM", "LEXICAL_EXPANSION_KIND_HYPONYM"),
        expansionKind.getValues().stream()
            .map(value -> value.getName()).collect(Collectors.toSet()));
  }
}
