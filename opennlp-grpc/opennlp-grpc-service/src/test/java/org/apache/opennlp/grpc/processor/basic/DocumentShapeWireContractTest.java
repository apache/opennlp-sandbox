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
import org.apache.opennlp.grpc.v1.LayerIdentity;
import org.apache.opennlp.grpc.v1.OpenNlpDocumentProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentShapeWireContractTest {

  @Test
  void firstClassResultsHaveDedicatedTypedLayerArms() {
    final Descriptor layer = AnnotationLayer.getDescriptor();
    final Set<String> cases = layer.getOneofs().stream()
        .filter(oneof -> "values".equals(oneof.getName()))
        .flatMap(oneof -> oneof.getFields().stream())
        .map(field -> field.getName())
        .collect(Collectors.toSet());

    assertEquals(Set.of(
        "string_values", "category_values", "embedding_values", "tree_values",
        "subword_values", "geo_values", "word_type_values", "entity_values",
        "syntactic_chunk_values", "stem_values", "lexical_expansion_values",
        "normalization_values", "analytics_values", "chunk_group_values",
        "term_vector_values", "dependency_values", "relation_values"), cases);
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

  @Test
  void layerIdentityUsesAClosedStandardEnumOrAnOpenCustomId() {
    final Descriptor layer = AnnotationLayer.getDescriptor();
    assertEquals("org.apache.opennlp.grpc.v1.LayerIdentity",
        layer.findFieldByName("identity").getMessageType().getFullName());

    final Descriptor identity = LayerIdentity.getDescriptor();
    assertEquals(Set.of("standard", "custom"), identity.getOneofs().stream()
        .filter(oneof -> "kind".equals(oneof.getName()))
        .flatMap(oneof -> oneof.getFields().stream())
        .map(field -> field.getName())
        .collect(Collectors.toSet()));
    assertEquals("string", identity.findFieldByName("qualifier").getType().name().toLowerCase());

    final EnumDescriptor standardLayer = OpenNlpDocumentProto.getDescriptor()
        .findEnumTypeByName("StandardLayer");
    assertEquals(Set.of(
        "STANDARD_LAYER_UNSPECIFIED", "STANDARD_LAYER_SENTENCES", "STANDARD_LAYER_TOKENS",
        "STANDARD_LAYER_POS_TAGS", "STANDARD_LAYER_LEMMAS", "STANDARD_LAYER_ENTITIES",
        "STANDARD_LAYER_SYNTACTIC_CHUNKS", "STANDARD_LAYER_PARSES",
        "STANDARD_LAYER_SENTIMENT", "STANDARD_LAYER_LANGUAGE", "STANDARD_LAYER_CATEGORIES",
        "STANDARD_LAYER_EMBEDDINGS", "STANDARD_LAYER_WORD_TYPES", "STANDARD_LAYER_STOPWORDS",
        "STANDARD_LAYER_TERMS", "STANDARD_LAYER_SUBWORDS", "STANDARD_LAYER_STEMS",
        "STANDARD_LAYER_EXPANSIONS", "STANDARD_LAYER_GEO", "STANDARD_LAYER_NORMALIZATION",
        "STANDARD_LAYER_ANALYTICS", "STANDARD_LAYER_CHUNK_GROUPS",
        "STANDARD_LAYER_TERM_VECTORS", "STANDARD_LAYER_DEPENDENCIES",
        "STANDARD_LAYER_RELATIONS"),
        standardLayer.getValues().stream()
            .map(value -> value.getName()).collect(Collectors.toSet()));
  }
}
