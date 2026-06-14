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
package org.apache.opennlp.grpc.v1.server;

import java.util.Objects;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.opennlp.grpc.model.ModelBundleCache;
import org.apache.opennlp.grpc.processor.AnalysisException;
import org.apache.opennlp.grpc.processor.DocumentAnalyzer;
import org.apache.opennlp.grpc.processor.PipelineStepPolicy;
import org.apache.opennlp.grpc.profile.ProfileRegistry;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentRequest;
import org.apache.opennlp.grpc.v1.AnalyzeDocumentResponse;
import org.apache.opennlp.grpc.v1.GetServiceInfoRequest;
import org.apache.opennlp.grpc.v1.GetServiceInfoResponse;
import org.apache.opennlp.grpc.v1.ListModelBundlesRequest;
import org.apache.opennlp.grpc.v1.ListModelBundlesResponse;
import org.apache.opennlp.grpc.v1.OpenNlpAnalysisServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC adapter for the v1 document-centric API.
 */
public class OpenNlpAnalysisServiceImpl extends OpenNlpAnalysisServiceGrpc.OpenNlpAnalysisServiceImplBase {

  private static final Logger logger = LoggerFactory.getLogger(OpenNlpAnalysisServiceImpl.class);

  private static final String API_VERSION = "v1";

  private final DocumentAnalyzer documentAnalyzer;
  private final ProfileRegistry profileRegistry;
  private final ModelBundleCache modelBundleCache;
  private final String opennlpVersion;

  public OpenNlpAnalysisServiceImpl(
      DocumentAnalyzer documentAnalyzer,
      ProfileRegistry profileRegistry,
      ModelBundleCache modelBundleCache,
      String opennlpVersion) {
    this.documentAnalyzer = Objects.requireNonNull(documentAnalyzer, "documentAnalyzer");
    this.profileRegistry = Objects.requireNonNull(profileRegistry, "profileRegistry");
    this.modelBundleCache = Objects.requireNonNull(modelBundleCache, "modelBundleCache");
    this.opennlpVersion = opennlpVersion == null ? "unknown" : opennlpVersion;
  }

  @Override
  public void analyzeDocument(
      AnalyzeDocumentRequest request,
      StreamObserver<AnalyzeDocumentResponse> responseObserver) {
    try {
      responseObserver.onNext(documentAnalyzer.analyze(request));
      responseObserver.onCompleted();
    } catch (AnalysisException e) {
      final Status status = GrpcStatusMapper.toStatus(e);
      responseObserver.onError(
          status.withDescription(e.getMessage()).withCause(e.getCause()).asRuntimeException());
    } catch (RuntimeException e) {
      // Any non-AnalysisException is an unexpected server fault. Without this it would escape the
      // handler and gRPC would close the call with an opaque UNKNOWN (and risk leaking the raw
      // exception); map it to a clean INTERNAL, logging the detail server-side only.
      logger.error("Unexpected error handling AnalyzeDocument", e);
      responseObserver.onError(Status.INTERNAL
          .withDescription("Internal server error").withCause(e).asRuntimeException());
    }
  }

  @Override
  public void getServiceInfo(
      GetServiceInfoRequest request,
      StreamObserver<GetServiceInfoResponse> responseObserver) {
    responseObserver.onNext(GetServiceInfoResponse.newBuilder()
        .setOpennlpVersion(opennlpVersion)
        .setApiVersion(API_VERSION)
        .addAllAvailableProfileIds(profileRegistry.getProfiles().keySet())
        .addAllSupportedSteps(PipelineStepPolicy.implementedSteps())
        .build());
    responseObserver.onCompleted();
  }

  @Override
  public void listModelBundles(
      ListModelBundlesRequest request,
      StreamObserver<ListModelBundlesResponse> responseObserver) {
    responseObserver.onNext(ListModelBundlesResponse.newBuilder()
        .addAllBundles(modelBundleCache.listBundles())
        .build());
    responseObserver.onCompleted();
  }
}
