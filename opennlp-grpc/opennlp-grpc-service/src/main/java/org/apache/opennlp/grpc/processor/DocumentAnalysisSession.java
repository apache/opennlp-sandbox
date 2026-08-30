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

import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.OpenNlpDocument;
import org.apache.opennlp.grpc.spi.AnalysisException;

/**
 * A document analyzer prepared for one fixed streaming configuration.
 *
 * <p>Thread safety is implementation specific.</p>
 */
@FunctionalInterface
public interface DocumentAnalysisSession {

  /**
   * Analyzes one document under this session's fixed configuration.
   *
   * @param document Document to analyze. Must not be {@code null}.
   *
   * @return Analysis response. Never {@code null}.
   *
   * @throws AnalysisException If the document is invalid or analysis fails.
   * @throws IllegalArgumentException If {@code document} is {@code null}.
   */
  AnalyzeDocumentResponse analyze(OpenNlpDocument document);
}
