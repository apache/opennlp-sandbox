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

import java.util.Objects;

import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;

/**
 * Pure-Java document analysis orchestrator. Implementations are gRPC-free and may be
 * used in-process or behind the v1 {@code OpenNlpAnalysisService} gRPC adapter.
 */
public interface DocumentAnalyzer {

  /**
   * Runs the configured analysis pipeline over the given request and returns the
   * annotated document together with any processing diagnostics.
   *
   * @param request The analysis request, carrying the document text, requested profile,
   *               and pipeline steps. Must not be {@code null}.
   *
   * @return The analysis response with the annotated document. Never {@code null}.
   *
   * @throws org.apache.opennlp.grpc.processor.AnalysisException If the request is invalid
   *         or a required step fails.
   */
  AnalyzeDocumentResponse analyze(AnalyzeDocumentRequest request);

  /**
   * Opens an analysis session for a streaming call's fixed configuration.
   * Implementations that can compile or validate a plan once may override this
   * method. The default implementation preserves exact unary behavior by building
   * an {@link AnalyzeDocumentRequest} for each submitted document.
   *
   * @param configuration Fixed stream configuration. Must not be {@code null}.
   *
   * @return A thread-safe session for analyzing documents concurrently.
   *
   * @throws AnalysisException If the configuration cannot be prepared.
   */
  default DocumentAnalysisSession openSession(AnalyzeStreamConfiguration configuration) {
    Objects.requireNonNull(configuration, "configuration");
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
    return document -> analyze(template.toBuilder().setDocument(document).build());
  }
}
