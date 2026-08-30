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
package org.apache.opennlp.grpc.embedding;


import org.apache.opennlp.grpc.spi.AnalysisException;
import org.apache.opennlp.grpc.v1.EmbeddingBackendSelector;
import org.apache.opennlp.grpc.v1.EmbeddingSelector;
import org.apache.opennlp.grpc.v1.StandardEmbeddingBackend;

/** Resolves typed and compatibility embedding backend selectors to provider ids. */
public final class EmbeddingBackendSelections {

  private static final String ONNX = "onnx";
  private static final String CUDA = "cuda";
  private static final String STATIC = "static";
  private static final String TEI = "tei";
  private static final String OPENVINO = "openvino";

  private EmbeddingBackendSelections() {
  }

  /**
   * Returns the pinned backend id, or {@code null} when routing should use priority and fallback.
   *
   * @param selector The embedding selector to resolve.
   * @return The selected provider id, or {@code null} for server-selected routing.
   * @throws AnalysisException If typed and compatibility fields are mixed or typed input is empty.
   */
  public static String selectedId(EmbeddingSelector selector) {
    if (selector == null) {
      throw new IllegalArgumentException("selector must not be null");
    }
    if (selector.hasBackendId() && selector.hasBackend()) {
      throw AnalysisException.invalidArgument(
          "EmbeddingSelector.backend_id and EmbeddingSelector.backend are mutually exclusive");
    }
    if (!selector.hasBackend()) {
      if (!selector.hasBackendId() || selector.getBackendId().isBlank()) {
        return null;
      }
      return selector.getBackendId().trim();
    }

    final EmbeddingBackendSelector backend = selector.getBackend();
    switch (backend.getKindCase()) {
      case STANDARD:
        return standardId(backend.getStandard());
      case CUSTOM:
        final String custom = backend.getCustom().trim();
        if (custom.isEmpty()) {
          throw AnalysisException.invalidArgument(
              "EmbeddingSelector.backend custom id must not be blank");
        }
        return custom;
      case KIND_NOT_SET:
        throw AnalysisException.invalidArgument(
            "EmbeddingSelector.backend must select a standard or custom backend");
      default:
        throw AnalysisException.invalidArgument("Unknown EmbeddingBackendSelector kind");
    }
  }

  /** Returns the open id for a standard enum value. */
  private static String standardId(StandardEmbeddingBackend backend) {
    switch (backend) {
      case STANDARD_EMBEDDING_BACKEND_ONNX:
        return ONNX;
      case STANDARD_EMBEDDING_BACKEND_CUDA:
        return CUDA;
      case STANDARD_EMBEDDING_BACKEND_STATIC:
        return STATIC;
      case STANDARD_EMBEDDING_BACKEND_TEI:
        return TEI;
      case STANDARD_EMBEDDING_BACKEND_OPENVINO:
        return OPENVINO;
      case STANDARD_EMBEDDING_BACKEND_UNSPECIFIED:
      case UNRECOGNIZED:
      default:
        throw AnalysisException.invalidArgument(
            "EmbeddingSelector.backend standard value must not be unspecified or unrecognized");
    }
  }
}
