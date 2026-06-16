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

import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Unit tests for {@link Centroids}: the element-wise mean and its metadata. */
class CentroidsTest {

  private static final AnnotationSpan SPAN = AnnotationSpan.newBuilder().setStart(0).setEnd(9).build();

  @Test
  void averagesVectorsElementWise() {
    final EmbeddingResult centroid = Centroids.centroid("minilm",
        List.of(new float[] {0f, 2f, 4f}, new float[] {2f, 4f, 8f}),
        SPAN, EmbeddingGranularity.EMBEDDING_GRANULARITY_GROUP_CENTROID);

    assertEquals("minilm", centroid.getModelId());
    assertEquals(EmbeddingGranularity.EMBEDDING_GRANULARITY_GROUP_CENTROID,
        centroid.getGranularity());
    assertEquals(SPAN, centroid.getSourceSpan());
    assertEquals(List.of(1f, 3f, 6f), centroid.getVectorList());
  }

  @Test
  void singleVectorIsItsOwnCentroid() {
    final EmbeddingResult centroid = Centroids.centroid("m", List.of(new float[] {1.5f, -2.5f}),
        SPAN, EmbeddingGranularity.EMBEDDING_GRANULARITY_DOCUMENT);
    assertEquals(List.of(1.5f, -2.5f), centroid.getVectorList());
  }

  @Test
  void emptyInputYieldsNull() {
    assertNull(Centroids.centroid("m", List.of(), SPAN,
        EmbeddingGranularity.EMBEDDING_GRANULARITY_DOCUMENT));
  }
}
