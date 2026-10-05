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
package org.apache.opennlp.grpc.sink;

import org.apache.opennlp.grpc.processor.DocumentAnalysisSession;
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamConfiguration;

/**
 * {@link DocumentAnalyzer} decorator that tees every successfully analyzed document
 * into the open sinks. Delivery happens after the analysis result exists and sink
 * failures are isolated by the registry, so analysis behavior is unchanged.
 */
public final class SinkTeeingDocumentAnalyzer implements DocumentAnalyzer {

  private final DocumentAnalyzer delegate;
  private final DocumentSinkRegistry sinks;

  /**
   * Wraps an analyzer over the open sinks.
   *
   * @param delegate The analyzer producing results. Must not be {@code null}.
   * @param sinks The open sinks. Must not be {@code null}.
   */
  public SinkTeeingDocumentAnalyzer(DocumentAnalyzer delegate, DocumentSinkRegistry sinks) {
    if (delegate == null) {
      throw new IllegalArgumentException("delegate must not be null");
    }
    this.delegate = delegate;
    if (sinks == null) {
      throw new IllegalArgumentException("sinks must not be null");
    }
    this.sinks = sinks;
  }

  /** {@inheritDoc} */
  @Override
  public AnalyzeDocumentResponse analyze(AnalyzeDocumentRequest request) {
    return teed(delegate.analyze(request));
  }

  /** {@inheritDoc} */
  @Override
  public DocumentAnalysisSession openSession(AnalyzeStreamConfiguration configuration) {
    final DocumentAnalysisSession session = delegate.openSession(configuration);
    return document -> teed(session.analyze(document));
  }

  /** {@inheritDoc} */
  @Override
  public void close() {
    delegate.close();
  }

  /** Tees the analyzed document of one successful response. */
  private AnalyzeDocumentResponse teed(AnalyzeDocumentResponse response) {
    if (response.hasDocument()) {
      sinks.tee(response.getDocument());
    }
    return response;
  }
}
