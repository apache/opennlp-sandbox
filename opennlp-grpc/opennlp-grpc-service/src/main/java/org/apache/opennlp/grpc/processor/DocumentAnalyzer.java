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
}
