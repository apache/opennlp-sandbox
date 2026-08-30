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

import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.AnnotationSpan;
import org.apache.opennlp.grpc.v1.EmbeddingGranularity;
import org.apache.opennlp.grpc.v1.EmbeddingResult;
import org.apache.opennlp.grpc.v1.EmbeddingRoute;
import org.apache.opennlp.grpc.v1.VectorNormalization;

/**
 * Computes centroid (mean) embedding vectors from a set of member vectors, a single representative
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
    return centroid(modelId, vectors, span, granularity,
        VectorNormalization.VECTOR_NORMALIZATION_NONE, null);
  }

  /**
   * Builds a centroid with an explicit service-side normalization.
   *
   * @param modelId The embedding model the vectors came from.
   * @param vectors The member vectors to average; must be non-empty and of equal length.
   * @param span The span the centroid represents.
   * @param granularity The granularity to stamp on the centroid.
   * @param normalization The post-aggregation normalization.
   * @return The centroid result, or {@code null} when {@code vectors} is empty.
   */
  public static EmbeddingResult centroid(String modelId, List<float[]> vectors, AnnotationSpan span,
      EmbeddingGranularity granularity, VectorNormalization normalization) {
    return centroid(modelId, vectors, span, granularity, normalization, null);
  }

  /**
   * Builds a centroid and retains the concrete route that produced its member vectors.
   *
   * @param modelId The embedding model the vectors came from.
   * @param vectors The member vectors to average; must be non-empty and of equal length.
   * @param span The span the centroid represents.
   * @param granularity The granularity to stamp on the centroid.
   * @param route The concrete route that produced the member vectors, or {@code null}.
   * @return The centroid result, or {@code null} when {@code vectors} is empty.
   */
  public static EmbeddingResult centroid(String modelId, List<float[]> vectors, AnnotationSpan span,
      EmbeddingGranularity granularity, EmbeddingRoute route) {
    return centroid(modelId, vectors, span, granularity,
        VectorNormalization.VECTOR_NORMALIZATION_NONE, route);
  }

  /** Computes the centroid vector and its provenance. */
  private static EmbeddingResult centroid(
      String modelId,
      List<float[]> vectors,
      AnnotationSpan span,
      EmbeddingGranularity granularity,
      VectorNormalization normalization,
      EmbeddingRoute route) {
    if (vectors.isEmpty()) {
      return null;
    }
    final VectorNormalization resolved = switch (normalization) {
      case VECTOR_NORMALIZATION_UNSPECIFIED, VECTOR_NORMALIZATION_NONE ->
          VectorNormalization.VECTOR_NORMALIZATION_NONE;
      case VECTOR_NORMALIZATION_L2 -> VectorNormalization.VECTOR_NORMALIZATION_L2;
      case UNRECOGNIZED -> throw AnalysisException.invalidArgument(
          "vector normalization must be recognized");
    };
    final int dimension = vectors.get(0).length;
    final double[] sums = new double[dimension];
    for (float[] vector : vectors) {
      // Equal length is an invariant: every vector here came from the same model.
      for (int i = 0; i < dimension; i++) {
        sums[i] += vector[i];
      }
    }
    final double[] mean = new double[dimension];
    for (int i = 0; i < dimension; i++) {
      mean[i] = sums[i] / vectors.size();
    }
    double divisor = 1.0d;
    if (resolved == VectorNormalization.VECTOR_NORMALIZATION_L2) {
      double squaredNorm = 0.0d;
      for (double value : mean) {
        squaredNorm += value * value;
      }
      divisor = Math.sqrt(squaredNorm);
      if (divisor == 0.0d) {
        throw AnalysisException.failedPrecondition(
            "embedding centroid has zero norm and cannot be L2-normalized");
      }
    }
    final List<Float> output = new ArrayList<>(dimension);
    for (double value : mean) {
      output.add((float) (value / divisor));
    }
    final EmbeddingResult.Builder result = EmbeddingResult.newBuilder()
        .setModelId(modelId)
        .addAllVector(output)
        .setSourceSpan(span)
        .setGranularity(granularity)
        .setVectorNormalization(resolved);
    if (route != null) {
      result.setRoute(route);
    }
    return result.build();
  }
}
