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
package org.apache.opennlp.grpc.webapp;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.AnalyzeStreamRequest;
import org.apache.opennlp.grpc.v1.AnalyzeStreamResponse;
import org.apache.opennlp.grpc.v1.FormatDocumentRequest;
import org.apache.opennlp.grpc.v1.FormatDocumentResponse;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.apache.opennlp.grpc.v1.ListOutputFormatsResponse;

/** An analysis adapter that accepts every call and answers with empty responses. */
final class EmptyAnalysisRpc implements AnalysisRpc {

  private int analysisCalls;

  /** {@inheritDoc} */
  @Override
  public GetServiceInfoResponse getServiceInfo() {
    return GetServiceInfoResponse.getDefaultInstance();
  }

  /** {@inheritDoc} */
  @Override
  public ListModelBundlesResponse listModelBundles() {
    return ListModelBundlesResponse.getDefaultInstance();
  }

  /** {@inheritDoc} */
  @Override
  public ListOutputFormatsResponse listOutputFormats() {
    return ListOutputFormatsResponse.getDefaultInstance();
  }

  /** {@inheritDoc} */
  @Override
  public FormatDocumentResponse formatDocument(FormatDocumentRequest request) {
    return FormatDocumentResponse.getDefaultInstance();
  }

  /** {@inheritDoc} */
  @Override
  public AnalyzeDocumentResponse analyze(AnalyzeDocumentRequest request) {
    analysisCalls++;
    return AnalyzeDocumentResponse.getDefaultInstance();
  }

  /** @return The number of unary analysis calls received. */
  int analysisCalls() {
    return analysisCalls;
  }

  /** {@inheritDoc} */
  @Override
  public Iterator<AnalyzeStreamResponse> analyzeStream(List<AnalyzeStreamRequest> frames) {
    return Collections.emptyIterator();
  }
}
