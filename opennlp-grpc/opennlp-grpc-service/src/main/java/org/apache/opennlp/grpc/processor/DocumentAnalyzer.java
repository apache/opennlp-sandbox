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
package org.apache.opennlp.grpc.processor;

import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;
import org.apache.opennlp.grpc.spi.AnalysisException;

/**
 * Analyzes OpenNLP documents from typed pipeline requests.
 */
public interface DocumentAnalyzer extends AutoCloseable {

  /**
   * Runs the configured analysis pipeline over the given request and returns the
   * annotated document together with any processing diagnostics.
   *
   * @param request The analysis request, carrying the document text, requested profile,
   *               and pipeline steps. Must not be {@code null}.
   *
   * @return The analysis response with the annotated document. Never {@code null}.
   *
   * @throws org.apache.opennlp.grpc.spi.AnalysisException If the request is invalid
   *         or a required step fails.
   * @throws IllegalArgumentException If {@code request} is {@code null}.
   */
  AnalyzeDocumentResponse analyze(AnalyzeDocumentRequest request);

  /**
   * Opens an analysis session for a fixed stream configuration.
   *
   * @param configuration Fixed stream configuration. Must not be {@code null}.
   *
   * @return A thread-safe session for analyzing documents concurrently.
   *
   * @throws AnalysisException If the configuration cannot be prepared.
   * @throws IllegalArgumentException If {@code configuration} is {@code null}.
   */
  default DocumentAnalysisSession openSession(AnalyzeStreamConfiguration configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration must not be null");
    }
    final AnalyzeDocumentRequest.Builder fixed = AnalyzeDocumentRequest.newBuilder();
    if (configuration.hasProfile()) {
      fixed.setProfile(configuration.getProfile());
    }
    if (configuration.hasOptions()) {
      fixed.setOptions(configuration.getOptions());
    }
    if (configuration.hasProfileId()) {
      fixed.setProfileId(configuration.getProfileId());
    }
    fixed.addAllChunkEmbedConfigs(configuration.getChunkEmbedConfigsList());
    fixed.addAllCategoryChunkConfigs(configuration.getCategoryChunkConfigsList());
    final AnalyzeDocumentRequest template = fixed.build();
    return document -> {
      if (document == null) {
        throw new IllegalArgumentException("document must not be null");
      }
      return analyze(template.toBuilder().setDocument(document).build());
    };
  }

  /**
   * Releases resources owned by this analyzer. Calling this method more than once has no
   * additional effect.
   */
  @Override
  default void close() {
  }
}
