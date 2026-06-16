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
package org.apache.opennlp.grpc.chunk;

import java.util.List;
import java.util.Map;

import org.apache.opennlp.grpc.embedding.StubEmbeddingProvider;
import org.apache.opennlp.grpc.v1.AnnotatedSentence;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.CategoryChunkConfigEntry;
import org.apache.opennlp.grpc.v1.Chunk;
import org.apache.opennlp.grpc.v1.ChunkEmbeddingGroup;
import org.apache.opennlp.grpc.v1.CoordinateSpace;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link ChunkEmbedProcessor#buildCategoryGroup}: grouping the document's
 * sentences by their sentiment label, concatenating each group, embedding it, and attaching a
 * centroid. Sentences are hand-built with labels so no sentiment model is required.
 */
class CategoryChunkProcessorTest {

  // Indices: "I love this."=0..12  "It is terrible."=13..28  "Great product."=29..43
  //          "Awful service."=44..58
  private static final String TEXT =
      "I love this. It is terrible. Great product. Awful service.";

  private final StubEmbeddingProvider embeddingProvider =
      new StubEmbeddingProvider(Map.of("minilm", 3));

  private static AnnotatedSentence sentence(int start, int end, String label) {
    return AnnotatedSentence.newBuilder()
        .setSentenceSpan(AnnotationSpan.newBuilder().setStart(start).setEnd(end)
            .setSpace(CoordinateSpace.COORDINATE_SPACE_CHAR_DOCUMENT))
        .setSentimentLabel(label)
        .build();
  }

  private static OpenNlpDocument labelledDocument() {
    return OpenNlpDocument.newBuilder()
        .setRawText(TEXT)
        .addSentences(sentence(0, 12, "Positive"))
        .addSentences(sentence(13, 28, "Negative"))
        .addSentences(sentence(29, 43, "positive"))   // different case: same category as sentence 0
        .addSentences(sentence(44, 58, "Negative"))
        .build();
  }

  private static CategoryChunkConfigEntry entry(String... categories) {
    final CategoryChunkConfigEntry.Builder builder = CategoryChunkConfigEntry.newBuilder()
        .setConfigId("by-sentiment")
        .addEmbeddingModelIds("minilm");
    for (String category : categories) {
      builder.addCategories(category);
    }
    return builder.build();
  }

  @Test
  void groupsSentencesByCategoryConcatenatingTextAndEmbedding() {
    final ChunkEmbeddingGroup group = ChunkEmbedProcessor.buildCategoryGroup(
        TEXT, labelledDocument(), entry(), embeddingProvider);

    assertEquals("by-sentiment", group.getGroupId());
    // First-appearance order: Positive then Negative.
    assertEquals(2, group.getChunksCount());

    final Chunk positive = group.getChunks(0);
    assertEquals("Positive", positive.getChunkTag());
    assertEquals("I love this. Great product.", positive.getTextContent());
    assertEquals(List.of(0, 2), positive.getContainedSentenceIndicesList());
    assertEquals(1, positive.getEmbeddingsCount());
    assertEquals("minilm", positive.getEmbeddings(0).getModelId());

    final Chunk negative = group.getChunks(1);
    assertEquals("Negative", negative.getChunkTag());
    assertEquals("It is terrible. Awful service.", negative.getTextContent());
    assertEquals(List.of(1, 3), negative.getContainedSentenceIndicesList());

    // One centroid per model: the mean of the category chunk vectors.
    assertEquals(1, group.getCentroidsCount());
    assertEquals(EmbeddingGranularity.EMBEDDING_GRANULARITY_GROUP_CENTROID,
        group.getCentroids(0).getGranularity());
    final int dimension = group.getCentroids(0).getVectorCount();
    for (int d = 0; d < dimension; d++) {
      final float expected = (positive.getEmbeddings(0).getVector(d)
          + negative.getEmbeddings(0).getVector(d)) / 2f;
      assertEquals(expected, group.getCentroids(0).getVector(d), 1e-5f);
    }
  }

  @Test
  void allowlistRestrictsAndOrdersCategoriesCaseInsensitively() {
    final ChunkEmbeddingGroup group = ChunkEmbedProcessor.buildCategoryGroup(
        TEXT, labelledDocument(), entry("NEGATIVE"), embeddingProvider);

    assertEquals(1, group.getChunksCount());
    assertEquals("Negative", group.getChunks(0).getChunkTag());
  }

  @Test
  void sentencesWithoutACategoryAreIgnored() {
    final OpenNlpDocument document = OpenNlpDocument.newBuilder()
        .setRawText(TEXT)
        .addSentences(sentence(0, 12, "Positive"))
        .addSentences(AnnotatedSentence.newBuilder()
            .setSentenceSpan(AnnotationSpan.newBuilder().setStart(13).setEnd(28))
            .build())  // no sentiment label
        .build();

    final ChunkEmbeddingGroup group = ChunkEmbedProcessor.buildCategoryGroup(
        TEXT, document, entry(), embeddingProvider);

    assertEquals(1, group.getChunksCount());
    assertEquals(List.of(0), group.getChunks(0).getContainedSentenceIndicesList());
    assertTrue(group.getStats().getChunkCount() == 1);
  }
}
