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

import java.util.ArrayList;
import java.util.List;

import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.EmbeddingResult;

/**
 * Computes centroid (mean) embedding vectors from a set of member vectors — a single representative
 * vector for a group of chunks or sentences. Pure CPU: no inference, just an element-wise average.
 */
public final class Centroids {

  private Centroids() {
  }

  /**
   * Builds a centroid {@link EmbeddingResult} from member vectors of equal length.
   *
   * @param modelId The embedding model the vectors came from.
   * @param vectors The member vectors to average; must be non-empty and of equal length.
   * @param span The span the centroid represents (e.g. the group's or document's span).
   * @param granularity The granularity to stamp on the centroid.
   *
   * @return The centroid result, or {@code null} when {@code vectors} is empty.
   */
  public static EmbeddingResult centroid(String modelId, List<float[]> vectors, AnnotationSpan span,
      EmbeddingGranularity granularity) {
    if (vectors.isEmpty()) {
      return null;
    }
    final int dimension = vectors.get(0).length;
    final double[] sums = new double[dimension];
    for (float[] vector : vectors) {
      // Equal length is an invariant: every vector here came from the same model.
      for (int i = 0; i < dimension; i++) {
        sums[i] += vector[i];
      }
    }
    final List<Float> mean = new ArrayList<>(dimension);
    for (int i = 0; i < dimension; i++) {
      mean.add((float) (sums[i] / vectors.size()));
    }
    return EmbeddingResult.newBuilder()
        .setModelId(modelId)
        .addAllVector(mean)
        .setSourceSpan(span)
        .setGranularity(granularity)
        .build();
  }
}
